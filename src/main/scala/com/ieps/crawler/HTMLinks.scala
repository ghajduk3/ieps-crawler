package com.ieps.crawler

import com.ieps.crawler.db.Tables.{ImageRow, PageRow, SiteRow}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import scala.collection.JavaConverters._


class HTMLinks(kod: String, page: PageRow, site: SiteRow) {
  val html: String = kod
  var doc: Document = new Document(html)
  doc = Jsoup.parse(html)

  //method that gets src from <img> tags
  def getImg: List[ImageRow] = {
    val imgs = doc.select("img[src]").asScala
    var newImages = List.empty[ImageRow]
    imgs.foreach(img => {
      try {

        val actualImg = imgLink(img.attr("src"))
        newImages = newImages :+ ImageRow(-1, Some(page.id), Some(actualImg), Some(conType(actualImg)))
      }
      catch {
        case _: Exception =>
      }
    })
    newImages
  }

  def getLinks: List[PageRow] = {
    var allLinks = List.empty[PageRow]
    val links = doc.select("a[href]").asScala
    links.foreach(link => {
      try {
        val actualLink = extractLink(link.attr("href"))
        allLinks = allLinks :+ PageRow(-1, Some(site.id), Some("FRONTIER"), Some(actualLink))
      } catch {
        case _: Exception =>
      }
    })
    val onclick = doc.select("*")
    onclick.forEach(click => {
      try {
        val actualClick = extractLink(click.attr("onclick"))
        allLinks = allLinks :+ PageRow(-1, Some(site.id), Some("FRONTIER"), Some(actualClick))
      }
      catch {
        case _: Exception =>
      }

    })
    allLinks
  }

  def extractLink(url: String): String = {
    try {
      Canonical.getCanonical(url)
    }
    catch {
      case e: Exception =>
        Canonical.getCanonical(site.domain.get + url)
    }
  }

  def imgLink(url: String): String = {
    try {
      Canonical.getCanonical(url)
    }
    catch {
      case e: Exception =>
        var url1: String = if (url.contains(site.domain.get) && (url.contains("http://") || url.contains("http://"))) {
          url: String
        }
        else if (url.contains(site.domain.get)) {
          "http://" + url
        }
        else {
          "http://" + site.domain.get + url
        }
        url1 = Canonical.getCanonical(url1)
        url1
    }
  }

  def conType(url: String):String = url.slice(url.lastIndexOf(".")+1, url.lastIndexOf("/"))

}
