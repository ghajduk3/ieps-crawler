package com.ieps.crawler

import com.ieps.crawler
import com.ieps.crawler.db.Tables
import com.ieps.crawler.db.Tables.{ImageRow, PageRow, SiteRow}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.collection.JavaConverters._


class ExtractFromHTM(pageSource: PageRow, siteSource: SiteRow) {
  var doc: Document = Jsoup.parse(pageSource.htmlContent.get)

  //method that gets src from <img> tags
  def getImgs: List[ImageRow] = {
    val imgs = doc.select("img[src]").asScala
    var newImages = List.empty[ImageRow]
    imgs.foreach(img => {
      try {

        val actualImg = imgLink(img.attr("src"))
        newImages = newImages :+ ImageRow(-1, Some(pageSource.id), Some(actualImg), Some(conType(actualImg)))
      }
      catch {
        case _: Exception =>
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
        allLinks = allLinks :+ PageRow(-1, Some(siteSource.id), Some("FRONTIER"), Some(actualLink))
      } catch {
        case _: Exception =>
      }
    })
    val onclick = doc.select("*")
    onclick.forEach(click => {
      try {
        val actualClick = extractLink(click.attr("onclick"))
        allLinks = allLinks :+ PageRow(-1, Some(siteSource.id), Some("FRONTIER"), Some(actualClick))
      }
      catch {
        case _: Exception =>
      }

    })
    allLinks
  }

  def getPageLinks: List[crawler.db.Tables.PageRow] = {
    getAllLinks.filter(pageRow => {
      !pageRow.url.get.endsWith(".pdf") //TODO: fix this to work with all binary data types
    })
  }

  def getPageDATA: List[Tables.PageRow] = {
    getAllLinks.filter(pageRow => {
      !pageRow.url.get.endsWith(".pdf") //TODO: fix this to work with all binary data types
    })
  }

  def extractLink(url: String): String = {
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
        var url1: String = if (url.contains(siteSource.domain.get) && (url.contains("http://") || url.contains("http://"))) {
          url: String
        } else if (url.contains(siteSource.domain.get)) {
          "http://" + url
        } else {
          "http://" + siteSource.domain.get + url
        }
        url1 = Canonical.getCanonical(url1)
        url1
    }
  }

  def conType(url: String):String = url.slice(url.lastIndexOf(".")+1, url.lastIndexOf("/"))

}
