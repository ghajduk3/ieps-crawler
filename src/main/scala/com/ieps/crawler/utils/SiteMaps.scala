package com.ieps.crawler.utils

import java.net.URL

import com.typesafe.scalalogging.StrictLogging
import crawlercommons.sitemaps.{SiteMap, SiteMapIndex, SiteMapParser}

object SiteMaps extends StrictLogging {
  import com.ieps.crawler.db.Tables._

  private def getSiteMapUrls(siteMap: SiteMap, site: SiteRow): List[PageRow] = {
    var pages = List.empty[PageRow]
    val siteMapURLs = siteMap.getSiteMapUrls // TODO: needs to be fixed.
    siteMapURLs.forEach(siteMapUrl => {
      pages = pages :+ PageRow(-1, Some(site.id), Some("FRONTIER"), Some(Canonical.getCanonical(site.domain.get + siteMapUrl.getUrl.toString.trim)))
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

  def getSiteMapUrls(siteMapUrl: String, site: SiteRow): List[PageRow] ={
    val parser = new SiteMapParser()
    logger.info(s"sitemap url = $siteMapUrl")
    parser.parseSiteMap(new URL(siteMapUrl)) match {
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
