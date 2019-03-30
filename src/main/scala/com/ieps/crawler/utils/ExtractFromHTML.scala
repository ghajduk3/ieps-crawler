package com.ieps.crawler.utils

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
  private val extensions = Array(".pdf",".doc",".docx",".ppt",".pptx", ".zip", ".jpg", "jpeg", ".png")

  private val document: Option[Document] = pageSource.htmlContent.map(htmlContent => Jsoup.parse(htmlContent))

  //method that gets src from <img> tags
  def getImages: Option[List[ImageRow]] = {
    document.map( doc => {
      val imgs = doc.select("img[src]").asScala
      var newImages = List.empty[ImageRow]
      imgs.foreach(img => {
        try {
          val actualImg = imgLink(img.attr("src"))
          newImages = newImages :+ ImageRow(-1, Some(pageSource.id), Some(actualImg), Some(conType(actualImg)))
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
    getAllLinks.map(_.filter(pageRow => !extensions.exists(e => pageRow.url.get.endsWith(e))).distinct.filter(_.url.get.contains("gov.si")))
  }

  def getPageData: Option[List[Tables.PageRow]] = {
    getAllLinks.map(_.filter(pageRow => extensions.exists(e => pageRow.url.get.endsWith(e))))
  }

  private def extractLink(url: String): String = {
    try {
      Canonical.getCanonical(url)
    } catch {
      case _: Exception =>
        Canonical.getCanonical(siteSource.domain.get + url)
    }
  }

  private def imgLink(url: String): String = {
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
        url1 = Canonical.getCanonical(url1)
        url1
    }
  }

  private def conType(url: String):String = url.slice(url.lastIndexOf(".")+1, url.last)

}
