package com.ieps.crawler.utils

import java.io.IOException
import java.nio.charset.StandardCharsets

import com.ieps.crawler.db.Tables.PageRow
import com.leansoft.bigqueue.BigQueueImpl
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.format.ISODateTimeFormat
import argonaut._
import Argonaut._
import org.joda.time.DateTime

trait Queue {
  def close(): Unit
  def enqueue(item: PageRow): Unit
  def dequeue(): Option[PageRow]
  def size(): Long
  def isEmpty: Boolean
}

object DateTimeISO8601CodecJsons {
  lazy val FULL_ISO8601_FORMAT = ISODateTimeFormat.dateTimeNoMillis

  implicit def DateTimeAsISO8601EncodeJson: EncodeJson[DateTime] =
    EncodeJson(s => jString(s.toString(FULL_ISO8601_FORMAT)))

  implicit def DateTimeAsISO8601DecodeJson: DecodeJson[DateTime] =
    implicitly[DecodeJson[String]].map(FULL_ISO8601_FORMAT.parseDateTime) setName "org.joda.time.DateTime"
}

object BigQueue {
  private val commitTrigger = 1000
}

class BigQueue(folder: String, bigQueuePageSize: Integer = 32 * 1024 * 1024) extends Queue with LazyLogging {
  import DateTimeISO8601CodecJsons._
  implicit def PageRowCodecJson: CodecJson[PageRow] = casecodec9(PageRow.apply, PageRow.unapply)("id", "siteId", "pageTypeCode", "url", "htmlContent", "httpStatusCode", "loadTime", "accessedTime", "storedTime")


  private val queue: BigQueueImpl = new BigQueueImpl(folder, "crawlerQueue", bigQueuePageSize) // default page size is 128MB
  private var uncommittedChanges = 0


  override def close(): Unit = {
    if (!isEmpty) {
      logger.warn("Queue is not empty on close.")
    } else {
      queue.removeAll()
    }
    queue.close()
  }

  override def enqueue(item: PageRow): Unit = {
    queue.enqueue(item.asJson.toString().getBytes(StandardCharsets.UTF_8))
    uncommittedChanges += 1
    commitIfNecessary()
  }

  override def dequeue(): Option[PageRow] = try {
    if (queue.isEmpty) {
      queue.removeAll()
      None
    } else {
      val item = new String(queue.dequeue(), StandardCharsets.UTF_8)
      var rowItem: Option[PageRow] = None
      if (!item.isEmpty) {
        rowItem = item.decodeOption[PageRow]
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
    if (uncommittedChanges >= BigQueue.commitTrigger) {
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
