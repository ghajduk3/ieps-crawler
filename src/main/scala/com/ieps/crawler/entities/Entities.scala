package com.ieps.crawler.entities

import org.joda.time.DateTime
import slick.lifted.Tag
import slick.jdbc.PostgresProfile.api._
import com.github.tototoshi.slick.PostgresJodaSupport._

class Entities(schema: String) {
  class Site(tag: Tag) extends Table[(Long, String, String, String)](tag, Some(schema), "site") {
    def id = column[Long]("id", O.PrimaryKey)
    def domain = column[String]("domain")
    def robots_content = column[String]("robots_content")
    def sitemap_content = column[String]("sitemap_content")
    override def * = (id, domain, robots_content, sitemap_content)
  }
  val sites = TableQuery[Site]

  class Page(tag: Tag) extends Table[(Long, Long, String, String, String, DateTime)](tag, Some(schema), "page") {
    def id = column[Long]("id", O.PrimaryKey)
    def siteId = column[Long]("site_id")
    def pageTypeCode = column[String]("page_type_code")
    def url = column[String]("url")
    def httpStatusCode = column[String]("sitemap_content")
    def accessedTime = column[DateTime]("accessed_time")
    override def * = (id, siteId, pageTypeCode, url, httpStatusCode, accessedTime)
  }
  val pages = TableQuery[Site]

  class Link(tag: Tag) extends Table[(Long, Long)](tag, Some(schema), "link") {
    def fromPage = column[Long]("from_page", O.PrimaryKey)
    def toPage = column[Long]("to_page", O.PrimaryKey)

    override def * = (fromPage, toPage)
  }
  val link = TableQuery[Link]

  class PageType(tag: Tag) extends Table[(String)](tag, Some(schema), "page_type") {
    def code = column[String]("code")
    override def * = (code)
  }
  val pageTypes = TableQuery[PageType]

  class PageData(tag: Tag) extends Table[(Long, Long, String, Array[Byte])](tag, Some(schema), "page_data") {
    def id = column[Long]("id")
    def pageId = column[Long]("page_id")
    def pageTypeCode = column[String]("page_id")
    def data = column[Array[Byte]]("data")
    override def * = (id, pageId, pageTypeCode, data)
  }
  val pageData = TableQuery[PageData]

  class DataType(tag: Tag) extends Table[String](tag, Some(schema), "data_type") {
    def code = column[String]("code")
    override def * = code
  }
  val dataType = TableQuery[DataType]

  class Image(tag: Tag) extends Table[(Long, Long, String, String, Array[Byte], DateTime)](tag, Some(schema), "image") {
    def id = column[Long]("id")
    def pageId = column[Long]("page_id")
    def filename = column[String]("filename")
    def contentType = column[String]("content_type")
    def data = column[Array[Byte]]("data")
    def accessedTime = column[DateTime]("accessed_time")
    override def * = (id, pageId, filename, contentType, data, accessedTime)
  }
  val image = TableQuery[Image]
}
