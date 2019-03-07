package com.ieps.crawler.db

import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class DBService(dbConfig: String) {
  import Tables._

  val db = Database.forConfig(dbConfig)
  implicit val timeout: FiniteDuration = new FiniteDuration(1, MINUTES)

  // Site
  def getSiteByIdFuture(id: Int): Future[Option[SiteRow]] =
    db.run(CrawlerDIO.findSiteById(id))

  def getSiteById(id: Int): Option[SiteRow] =
    Await.result(getSiteByIdFuture(id), timeout)

  def insertSiteFuture(site: SiteRow): Future[SiteRow] =
    db.run(CrawlerDIO.insertSite(site))

  def insertSite(site: SiteRow): SiteRow =
    Await.result(insertSiteFuture(site), timeout)

  // Page
  def getPageByIdFuture(id: Int): Future[Option[PageRow]] =
    db.run(CrawlerDIO.findPageById(id))

  def getPageById(id: Int): Option[PageRow] =
    Await.result(getPageByIdFuture(id), timeout)

  def insertPageFuture(page: PageRow): Future[PageRow] =
    db.run(CrawlerDIO.insertPage(page))

  def insertPage(page: PageRow): PageRow =
    Await.result(insertPageFuture(page), timeout)

  // bulk insert
  def insertSiteWithPageFuture(site: SiteRow, page: PageRow): Future[(SiteRow, PageRow)] =
    db.run(CrawlerDIO.insertSiteWithPage(site, page))

  def insertSiteWithPage(site: SiteRow, page: PageRow): (SiteRow, PageRow) =
    Await.result(insertSiteWithPageFuture(site, page), timeout)

  def insertSiteWithPagesFuture(site: SiteRow, page: Seq[PageRow]): Future[(SiteRow, Seq[PageRow])] =
    db.run(CrawlerDIO.insertSiteWithPages(site, page))

  def insertSiteWithPages(site: SiteRow, page: Seq[PageRow]): (SiteRow, Seq[PageRow]) =
    Await.result(insertSiteWithPagesFuture(site, page), timeout)

  def insertPageWithContentFuture(page: PageRow, images: Seq[ImageRow], pageDatum: Seq[PageDataRow]): Future[(PageRow, Seq[ImageRow], Seq[PageDataRow])] =
    db.run(CrawlerDIO.insertPageWithContent(page, images, pageDatum))

  def insertPageWithContent(page: PageRow, images: Seq[ImageRow], pageDatum: Seq[PageDataRow]): (PageRow, Seq[ImageRow], Seq[PageDataRow]) =
    Await.result(insertPageWithContentFuture(page, images, pageDatum), timeout)

  def linkPagesFuture(fromPage: PageRow, toPage: PageRow): Future[LinkRow] =
    db.run(CrawlerDIO.linkPages(fromPage, toPage))

  def linkPages(fromPage: PageRow, toPage: PageRow): LinkRow =
    Await.result(linkPagesFuture(fromPage, toPage), timeout)

  // bulk read
  def getPageLinksFuture(id: Int): Future[(Option[PageRow], Seq[PageRow])] =
    db.run(CrawlerDIO.getPageLinksById(id))

  def findPagesByLinkTarget(links: Seq[LinkRow]): Future[Seq[PageRow]] =
    db.run(CrawlerDIO.findPageByLinkTarget(links))

  def getPageLinks(id: Int): Seq[PageRow] =
    Await.result(getPageLinksFuture(id), timeout)._2

  def getPageLinks(page: PageRow): Seq[PageRow] =
    getPageLinks(page.id)

  def getPageContentFuture(id: Int): Future[(Seq[ImageRow], Seq[PageDataRow])] =
    db.run(CrawlerDIO.getPageContents(id))

  def getPageContent(id: Int): (Seq[ImageRow], Seq[PageDataRow]) =
    Await.result(getPageContentFuture(id), timeout)

  def getPageContent(page: PageRow): (Seq[ImageRow], Seq[PageDataRow]) =
    Await.result(getPageContentFuture(page.id), timeout)

  def closeDb(): Unit = db.close()
}
