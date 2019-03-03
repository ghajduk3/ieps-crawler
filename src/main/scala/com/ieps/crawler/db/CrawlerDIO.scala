package com.ieps.crawler.db

import com.ieps.crawler.db.Tables._
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

class CrawlerDIO {

  def findSiteById(id: Int): DBIO[Option[SiteRow]] = Query.siteById(id).result.headOption
  def findPageById(id: Int): DBIO[Option[PageRow]] = Query.pageById(id).result.headOption
  def findSiteWithPages: DBIO[Seq[(SiteRow, PageRow)]] = Query.siteWithPages.result

  def insertSite(site: SiteRow): DBIO[SiteRow] = Query.writeSite += site
  def insertPage(page: PageRow): DBIO[PageRow] = Query.writePage += page

//  def sitesWithPages() = Query.siteWithPages.result

  object Query {
    import Tables._

    val writeSite = Site returning Site.map(_.id) into((site, id) => site.copy(id))
    val writePage = Page returning Page.map(_.id) into((page, id) => page.copy(id))

    val siteById = Site.findBy(_.id)
    val pageById = Page.findBy(_.id)

    val siteWithPages = for {
      site <- Site
      page <- Page if page.siteId === site.id
    } yield (site, page)

  }

}
