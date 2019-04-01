package com.ieps.crawler.utils

import java.net.{URI, URL}

import com.ieps.crawler
import com.ieps.crawler.db
import com.ieps.crawler.db.Tables
import com.ieps.crawler.db.Tables.{ImageRow, PageRow, SiteRow}
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.{DateTime, DateTimeZone}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.collection.JavaConverters._


class ExtractFromHTML(pageSource: PageRow, siteSource: SiteRow) extends StrictLogging {
//  logger.info(s"Extracting for: ${pageSource.url}")
  private val nonLinkExtensions = Array(".pdf",".doc",".docx",".ppt",".pptx", ".zip", ".jpg", "jpeg", ".png")
  private val pageDataExtensions = Array(".pdf",".doc",".docx",".ppt",".pptx")

  private val document: Option[Document] = pageSource.htmlContent.map(htmlContent => Jsoup.parse(htmlContent))

  //method that gets src from <img> tags
  def getImages: Option[List[PageRow]] = {
    document.map( doc => {
      val imgs = doc.select("img[src]").asScala
      var newImages = List.empty[PageRow]
      imgs.foreach(img => {
        try {
          val imageLink = imgLink(img.attr("src"))
          newImages = newImages :+ PageRow(
            id = -1,
            siteId = Some(pageSource.id),
            pageTypeCode = conType(imageLink),
            url = imageLink,
            storedTime = Some(DateTime.now(DateTimeZone.UTC))
          )
        }
        catch {
          case e: Exception =>
//            logger.error(s"Error occurred while extracting image: ${e.getMessage}")
        }
      })
      newImages
    })
  }

  private def getAllLinks: Option[List[PageRow]] = {
    document.map(doc => {
      var allLinks = List.empty[PageRow]
      doc.select("a[href]").asScala.foreach(link => {
        try {
          val actualLink = extractLink(link.attr("href"))
          allLinks = allLinks :+ PageRow(
            id = -1,
            pageTypeCode = Some("FRONTIER"),
            url = Some(actualLink),
            storedTime = Some(DateTime.now(DateTimeZone.UTC))
          )
        } catch {
          case e: Exception =>
//            logger.error(s"Error occurred while extracting link: ${e.getMessage}")
        }
      })
      doc.select("*").asScala.foreach(click => {
        try {
          val actualClick = extractLink(click.attr("onclick"))
          allLinks = allLinks :+ PageRow(
            id = -1,
            pageTypeCode = Some("FRONTIER"),
            url = Some(actualClick),
            storedTime = Some(DateTime.now(DateTimeZone.UTC))
          )
        }
        catch {
          case e: Exception =>
//            logger.error(s"Error occurred while extracting link: ${e.getMessage}")
        }
      })
      logger.debug(s"links size: ${allLinks.size}")
      allLinks
    })
  }

  def getPageLinks: Option[List[PageRow]] = {
    getAllLinks.map(_.filter(pageRow => !nonLinkExtensions.exists(e => pageRow.url.get.endsWith(e))).distinct.filter(_.url.get.contains("gov.si")).filter(!_.url.get.contains("///")))
  }

  def getPageData: Option[List[Tables.PageRow]] = {
    getAllLinks.map(_.filter(pageRow => pageDataExtensions.exists(e => pageRow.url.get.endsWith(e))).map(_.copy(siteId = Some(pageSource.id))))
  }

  private def extractLink(url: String): String = {
    try {
      Canonical.getCanonical(url).get
    } catch {
      case _: Exception =>
        Canonical.getCanonical(siteSource.domain.get + url).get
    }
  }

  private def imgLink(url: String): Option[String] = {
    try {
      Canonical.getCanonical(url)
    }
    catch {
      case _: Exception =>
        var url1: String = if (url.contains(siteSource.domain.get) && (url.contains("http://") || url.contains("https://"))) {
          url: String
        } else if (url.contains(siteSource.domain.get)) {
          "http://" + url
        } else {
          siteSource.domain.get + url
        }
        Canonical.getCanonical(url1)
    }
  }

  private def conType(url: Option[String]):Option[String] = url.map(url => url.slice(url.lastIndexOf(".") + 1, url.last).toUpperCase())

}
