package com.ieps.crawler

import java.net.URL

import crawlercommons.sitemaps.{SiteMap, SiteMapParser}


class CrawlerSiteMap {

  def getSiteMapUrls(urlSiteMap: String): Unit ={
    val parser = new SiteMapParser()
    val siteMap: SiteMap = parser.parseSiteMap(new URL(urlSiteMap)).asInstanceOf[SiteMap]
    val urls = siteMap.getSiteMapUrls
    urls.forEach(url => {
            print(s"$url")
    })
  }

}
