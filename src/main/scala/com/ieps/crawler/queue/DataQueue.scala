package com.ieps.crawler.queue

import java.io.IOException
import java.nio.charset.StandardCharsets

import argonaut.Argonaut._
import argonaut._
import com.ieps.crawler.queue.Queue._
import com.leansoft.bigqueue.BigQueueImpl
import com.typesafe.scalalogging.LazyLogging

class DataQueue(folder: String, bigQueuePageSize: Integer = 32 * 1024 * 1024, clearState: Boolean = false) extends Queue[QueueDataEntry] with LazyLogging {
  import Queue._

  implicit def QueueDataRowCodecJson: CodecJson[QueueDataEntry] = casecodec3(QueueDataEntry.apply, QueueDataEntry.unapply)("isData","pageId", "url")

  private val queue: BigQueueImpl = new BigQueueImpl(folder, "dataQueue", bigQueuePageSize) // default page size is 128MB
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

  override def enqueue(item: QueueDataEntry): Unit = {
    queue.enqueue(item.asJson.toString().getBytes(StandardCharsets.UTF_8))
    uncommittedChanges += 1
    commitIfNecessary()
  }

  override def enqueueAll(items: List[QueueDataEntry]): Unit = {
    items.foreach(enqueue)
  }

  override def dequeue(): Option[QueueDataEntry] = try {
    if (queue.isEmpty) {
      queue.removeAll()
      None
    } else {
      val item = new String(queue.dequeue(), StandardCharsets.UTF_8)
      var rowItem: Option[QueueDataEntry] = None
      if (!item.isEmpty) {
        rowItem = item.decodeOption[QueueDataEntry]
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
      logger.error(s"Unknown Exception: ${e.getMessage}")
      e.printStackTrace()
      None
  }

  private def commitIfNecessary(): Unit = this.synchronized {
    if (uncommittedChanges >= commitTrigger) {
      queue.flush()
      queue.gc()
      uncommittedChanges = 0
    }
  }

  override def size(): Long = queue.size()
  override def isEmpty: Boolean = {
    if(queue.isEmpty) queue.removeAll()
    queue.isEmpty
  }
}
