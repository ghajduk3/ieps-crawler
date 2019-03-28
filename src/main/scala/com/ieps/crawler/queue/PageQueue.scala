package com.ieps.crawler.queue

import java.io.IOException
import java.nio.charset.StandardCharsets

import argonaut.Argonaut._
import argonaut._
import com.ieps.crawler.db.Tables._
import com.ieps.crawler.queue.Queue.{Queue, QueuePageEntry}
import com.leansoft.bigqueue.BigQueueImpl
import com.typesafe.scalalogging.LazyLogging

class PageQueue(folder: String, bigQueuePageSize: Integer = 32 * 1024 * 1024, clearState: Boolean = false) extends Queue[QueuePageEntry] with LazyLogging {
  import Queue.DateTimeISO8601CodecJsons._

  implicit def PageRowCodecJson: CodecJson[PageRow] = casecodec10(PageRow.apply, PageRow.unapply)("id", "siteId", "pageTypeCode", "url", "htmlContent", "hash", "httpStatusCode", "loadTime", "accessedTime", "storedTime")
  implicit def QueuePageEntryCodecJson: CodecJson[QueuePageEntry] = casecodec2(QueuePageEntry.apply, QueuePageEntry.unapply)("id", "siteId")

  private val queue: BigQueueImpl = new BigQueueImpl(folder, "pageQueue", bigQueuePageSize) // default page size is 128MB
  if (clearState) queue.removeAll()

  private var uncommittedChanges = 0

  override def close(): Unit = {
    if (!isEmpty) {
      logger.warn("Queue is not empty on close.")
    } else {
      queue.removeAll()
    }
    queue.close()
  }

  override def enqueue(item: QueuePageEntry): Unit = {
    queue.enqueue(item.asJson.toString().getBytes(StandardCharsets.UTF_8))
    uncommittedChanges += 1
    commitIfNecessary()
  }

  override def enqueueAll(items: List[QueuePageEntry]): Unit = {
//    logger.info(s"Enqueue size: ${items.size}")
    items.foreach(enqueue)
  }

  override def dequeue(): Option[QueuePageEntry] = try {
    if (queue.isEmpty) {
      queue.removeAll()
      None
    } else {
      val item = new String(queue.dequeue(), StandardCharsets.UTF_8)
      var rowItem: Option[QueuePageEntry] = None
      if (!item.isEmpty) {
        rowItem = item.decodeOption[QueuePageEntry]
      }
      uncommittedChanges += 1
      commitIfNecessary()
      rowItem
    }
  } catch {
    case e: IOException =>
      logger.error(s"IOException (removeAll): ${e.getMessage}") // if removeAll cannot delete any files will throw IOException
      None
    case e: Exception =>
//      logger.error(s"Unknown Exception: ${e.getMessage}")
//      e.printStackTrace()
      None
  }

  private def commitIfNecessary(): Unit = this.synchronized {
    if (uncommittedChanges >= Queue.commitTrigger) {
      queue.flush()
      queue.gc()
      uncommittedChanges = 0
    }
  }

  override def size(): Long = queue.size()
  override def isEmpty: Boolean = {
    if (queue.isEmpty) queue.removeAll()
    queue.isEmpty
  }
}
