package com.ieps.crawler.utils

import com.ieps.crawler
import com.ieps.crawler.db.Tables
import com.ieps.crawler.db.Tables.{ImageRow, PageRow, SiteRow}
import com.typesafe.scalalogging.StrictLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.collection.JavaConverters._


class ExtractFromHTML(pageSource: PageRow, siteSource: SiteRow) extends StrictLogging {
  logger.info(s"Extracting for: ${pageSource.url}")

  var doc: Document = Jsoup.parse(pageSource.htmlContent.get)
  val extensions = Array(".pdf",".doc",".docx",".ppt",".pptx")

  //method that gets src from <img> tags
  def getImages: List[ImageRow] = {
    val imgs = doc.select("img[src]").asScala
    var newImages = List.empty[ImageRow]
    imgs.foreach(img => {
      try {
        val actualImg = imgLink(img.attr("src"))
        newImages = newImages :+ ImageRow(-1, Some(pageSource.id), Some(actualImg), Some(conType(actualImg)))
      }
      catch {
        case e: Exception =>
          logger.error(s"Error occurred while extracting image: ${e.getMessage}")
      }
    })
    newImages
  }

  private def getAllLinks: List[PageRow] = {
    var allLinks = List.empty[PageRow]
    val links = doc.select("a[href]").asScala
    links.foreach(link => {
      try {
        val actualLink = extractLink(link.attr("href"))
        allLinks = allLinks :+ PageRow(-1, None, Some("FRONTIER"), Some(actualLink))
      } catch {
        case e: Exception =>
          logger.error(s"Error occurred while extracting link: ${e.getMessage}")
      }
    })
    val onclick = doc.select("*").asScala
    onclick.foreach(click => {
      try {
        val actualClick = extractLink(click.attr("onclick"))
        allLinks = allLinks :+ PageRow(-1, None, Some("FRONTIER"), Some(actualClick))
      }
      catch {
        case e: Exception =>
          logger.error(s"Error occurred while extracting link: ${e.getMessage}")
      }
    })
    allLinks
  }

  def getPageLinks: List[crawler.db.Tables.PageRow] = {
    getAllLinks.filter(pageRow => !extensions.exists(e => pageRow.url.get.endsWith(e))).distinct
  }

  def getPageData: List[Tables.PageRow] = {
    getAllLinks.filter(pageRow => extensions.exists(e => pageRow.url.get.endsWith(e)))
  }

  private def extractLink(url: String): String = {
    try {
      Canonical.getCanonical(url)
    }
    catch {
      case _: Exception =>
        Canonical.getCanonical(siteSource.domain.get + url)
    }
  }

  def imgLink(url: String): String = {
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

  def conType(url: String):String = url.slice(url.lastIndexOf(".")+1, url.last)

}
