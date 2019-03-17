package com.ieps.crawler.utils

import java.net.URL

import com.typesafe.scalalogging.StrictLogging
import crawlercommons.sitemaps.{SiteMap, SiteMapIndex, SiteMapParser}

object CrawlerSiteMap extends StrictLogging {
  import com.ieps.crawler.db.Tables._

  private def getSiteMapUrls(siteMap: SiteMap, site: SiteRow): List[PageRow] = {
    var pages = List.empty[PageRow]
    val siteMapURLs = siteMap.getSiteMapUrls
    siteMapURLs.forEach(siteMapUrl => {
      pages = pages :+ PageRow(-1, Some(site.id), Some("FRONTIER"), Some(siteMapUrl.getUrl.toString))
    })
    pages
  }

  private def getSiteMapUrls(siteMapIndex: SiteMapIndex, site: SiteRow): List[PageRow] = {
    var pages = List.empty[PageRow]
    val siteMapURLs = siteMapIndex.getSitemaps
    siteMapURLs.forEach {
      case siteMap: SiteMap =>
        pages = pages ++ getSiteMapUrls(siteMap, site)
      case siteMapIndex: SiteMapIndex =>
        pages = pages ++ getSiteMapUrls(siteMapIndex, site)
      case unknown: Any =>
        logger.error(s"Unknown sitemap type? $unknown")
    }
    pages
  }

  def getSiteMapUrls(urlSiteMap: String, site: SiteRow): List[PageRow] ={
    val parser = new SiteMapParser()
    parser.parseSiteMap(new URL(urlSiteMap)) match {
      case siteMap: SiteMap =>
        getSiteMapUrls(siteMap, site)

      case siteMapIndex: SiteMapIndex =>
        getSiteMapUrls(siteMapIndex, site)

      case unknown: Any =>
        logger.error(s"Unknown sitemap type? $unknown")
        null
    }
  }
}
