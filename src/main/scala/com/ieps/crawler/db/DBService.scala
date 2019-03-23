package com.ieps.crawler.db

import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class DBService(db: Database) {
  import Tables._

  implicit val timeout: FiniteDuration = new FiniteDuration(1, MINUTES)

  // Site
  def getSiteByIdFuture(id: Int): Future[Option[SiteRow]] =
    db.run(CrawlerDIO.findSiteByIdOption(id))

  def getSiteById(id: Int): Option[SiteRow] =
    Await.result(getSiteByIdFuture(id), timeout)

  def getSiteByDomainFuture(domain: String): Future[Option[SiteRow]] =
    db.run(CrawlerDIO.findPageByDomainOption(domain))

  def getSiteByDomain(domain: String): Option[SiteRow] =
    Await.result(getSiteByDomainFuture(domain), timeout)

  def insertSiteFuture(site: SiteRow): Future[SiteRow] =
    db.run(CrawlerDIO.insertSite(site))

  def insertSite(site: SiteRow): SiteRow =
    Await.result(insertSiteFuture(site), timeout)

  def insertOrUpdateSiteFuture(site: SiteRow): Future[SiteRow] =
    db.run(CrawlerDIO.insertOrUpdateSite(site))

  def insertOrUpdateSite(site: SiteRow): Option[SiteRow] =
    Await.ready[SiteRow](insertOrUpdateSiteFuture(site), timeout).value.get match {
      case Success(result) => Some(result)
      case Failure(exception) =>
        exception.printStackTrace()
        None
    }

  def insertIfNotExistsByDomainFuture(siteRow: SiteRow): Future[SiteRow] =
    db.run(CrawlerDIO.insertIfNotExistsByDomain(siteRow))

  def insertIfNotExistsByDomain(siteRow: SiteRow): SiteRow =
    Await.result(insertIfNotExistsByDomainFuture(siteRow), timeout)

  // Page
  def getPageByIdFuture(id: Int): Future[PageRow] =
    db.run(CrawlerDIO.findPageById(id))

  def getPageById(id: Int): PageRow =
    Await.result(getPageByIdFuture(id), timeout)

  def insertPageFuture(page: PageRow): Future[PageRow] =
    db.run(CrawlerDIO.insertPage(page))

  def insertPage(page: PageRow): PageRow =
    Await.result(insertPageFuture(page), timeout)

  def insertPageFuture(pages: List[PageRow]): Future[Seq[PageRow]] =
    db.run(CrawlerDIO.insertPage(pages))

  def insertPage(pages: List[PageRow]): Seq[PageRow] =
    Await.result(insertPageFuture(pages), timeout)

  def insertOrUpdatePageFuture(pages: List[PageRow]): Future[Seq[PageRow]] =
    db.run(CrawlerDIO.insertOrUpdatePage(pages))

  def insertOrUpdatePage(pages: List[PageRow]): Seq[PageRow] =
    Await.result(insertOrUpdatePageFuture(pages), timeout)

  def insertIfNotExistsByUrlFuture(page: PageRow): Future[PageRow] =
    db.run(CrawlerDIO.insertIfNotExistsByUrl(page))

  def insertIfNotExistsByUrl(page: PageRow): PageRow =
    Await.result(insertIfNotExistsByUrlFuture(page), timeout)

  // bulk insert
  def insertSiteWithPageFuture(site: SiteRow, page: PageRow): Future[(SiteRow, PageRow)] =
    db.run(CrawlerDIO.insertSiteWithPage(site, page))

  def insertSiteWithPage(site: SiteRow, page: PageRow): (SiteRow, PageRow) =
    Await.result(insertSiteWithPageFuture(site, page), timeout)

  def insertSiteWithPagesFuture(site: SiteRow, page: Seq[PageRow]): Future[(SiteRow, Seq[PageRow])] =
    db.run(CrawlerDIO.insertSiteWithPages(site, page))

  def insertSiteWithPages(site: SiteRow, page: Seq[PageRow]): (SiteRow, Seq[PageRow]) =
    Await.result(insertSiteWithPagesFuture(site, page), timeout)

  def insertPageWithContentFuture(page: PageRow, images: Seq[ImageRow], pageDatum: Seq[PageDataRow], pageLinks: Seq[PageRow]): Future[(PageRow, Seq[ImageRow], Seq[PageDataRow], Seq[PageRow])] =
    db.run(CrawlerDIO.insertPageWithContent(page, images, pageDatum, pageLinks))

  def insertPageWithContent(page: PageRow, images: Seq[ImageRow], pageDatum: Seq[PageDataRow], pageLinks: Seq[PageRow]): (PageRow, Seq[ImageRow], Seq[PageDataRow], Seq[PageRow]) =
    Await.result(insertPageWithContentFuture(page, images, pageDatum, pageLinks), timeout)

  def pageExistsFuture(page: PageRow): Future[Boolean] =
    db.run(CrawlerDIO.pageExists(page))

  def pageExists(page: PageRow): Boolean =
    Await.result(pageExistsFuture(page), timeout)

  def pageExistsFuture(page: List[PageRow]): Future[Seq[PageRow]] =
    db.run(CrawlerDIO.pageExists(page))

  def pageExists(page: List[PageRow]): Seq[PageRow] =
    Await.result(pageExistsFuture(page), timeout)

  def linkPagesFuture(fromPage: PageRow, toPage: PageRow): Future[LinkRow] =
    db.run(CrawlerDIO.linkPages(fromPage, toPage))

  def linkPagesFuture(fromPage: PageRow, toPages: List[PageRow]): Future[List[LinkRow]] =
    db.run(CrawlerDIO.linkPages(fromPage, toPages))

  def linkPages(fromPage: PageRow, toPage: PageRow): LinkRow =
    Await.result(linkPagesFuture(fromPage, toPage), timeout)

  def linkPages(fromPage: PageRow, toPages: List[PageRow]): List[LinkRow] =
    Await.result(linkPagesFuture(fromPage, toPages), timeout)

  def linkPages(fromPage: PageRow, toPages: Seq[PageRow]): List[LinkRow] =
    Await.result(linkPagesFuture(fromPage, toPages.toList), timeout)

  // bulk read
  def getPageLinksFuture(id: Int): Future[(PageRow, Seq[PageRow])] =
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
}
