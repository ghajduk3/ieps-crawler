package com.ieps.crawler.queue

import argonaut.Argonaut.jString
import argonaut.{DecodeJson, EncodeJson}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

object Queue {
  import com.ieps.crawler.db.Tables._
  val commitTrigger: Int = 1000

  trait Queue[A] {
    def close(): Unit

    def enqueue(item: A): Unit

    def enqueueAll(items: List[A]): Unit

    def dequeue(): Option[A]

    def size(): Long

    def isEmpty: Boolean

    def hasMorePages: Boolean = !isEmpty
  }

  object DateTimeISO8601CodecJsons {
    lazy val FULL_ISO8601_FORMAT = ISODateTimeFormat.dateTimeNoMillis

    implicit def DateTimeAsISO8601EncodeJson: EncodeJson[DateTime] =
      EncodeJson(s => jString(s.toString(FULL_ISO8601_FORMAT)))

    implicit def DateTimeAsISO8601DecodeJson: DecodeJson[DateTime] =
      implicitly[DecodeJson[String]].map(FULL_ISO8601_FORMAT.parseDateTime) setName "org.joda.time.DateTime"
  }

  case class QueuePageEntry(pageInQueue: PageRow, referencePage: Option[PageRow] = None)

  case class QueueDataEntry(isData: Boolean, pageId: Int, url: String) // isData: true -> PageDataRow, else -> ImageRow

}