package com.ieps.crawler.db

import com.ieps.crawler.db.Tables._
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext

object CrawlerDIO {
  implicit val ec: ExecutionContext = ExecutionContext.global

  def findSiteById(id: Int): DBIO[Option[SiteRow]] = Query.siteById(id).result.headOption
  def findPageByIdOption(id: Int): DBIO[Option[PageRow]] = Page.filter(_.id === id).result.headOption
  def findPageByUrl(page: PageRow): DBIO[PageRow] = Page.filter(_.url === page.url).result.head
  def findPageByUrlOption(page: PageRow): DBIO[Option[PageRow]] = Page.filter(_.url === page.url).result.headOption
  def findPageById(id: Int): DBIO[PageRow] = Page.filter(_.id === id).result.head
  def findPageByIds(ids: Seq[Int]): DBIO[Seq[PageRow]] = Page.filter(_.id inSet ids).result
  def findPageByLinkTarget(links: Seq[LinkRow]): DBIO[Seq[PageRow]] = Page.filter(_.id inSet links.map(_.toPage)).result
  def findSiteWithPages: DBIO[Seq[(SiteRow, PageRow)]] = Query.getSiteWithPages.result
  def findImagesByPageId(id: Int): DBIO[Seq[ImageRow]] = Image.filter(_.pageId === id).result
  def findPageDatumByPageId(id: Int): DBIO[Seq[PageDataRow]] = PageData.filter(_.pageId === id).result
  def findLinksBySourceId(id: Int): DBIO[Seq[LinkRow]] = Query.linksByFromId(id).result

  def getPageLinksById(id: Int): DBIO[(PageRow, Seq[PageRow])] = for {
    sourcePage <- findPageById(id)
    links <- findLinksBySourceId(id)
    targetPages <- findPageByLinkTarget(links)
  } yield (sourcePage, targetPages)

  def getPageContents(id: Int): DBIO[(Seq[ImageRow], Seq[PageDataRow])] = for {
    images <- findImagesByPageId(id)
    pageDatum <- findPageDatumByPageId(id)
  } yield (images, pageDatum)

  // get static tables
  def getPageTypes: DBIO[Seq[PageTypeRow]] = Query.pageTypes
  def getDataTypes: DBIO[Seq[DataTypeRow]] = Query.dataTypes

  // insert statements
  def insertSite(site: SiteRow): DBIO[SiteRow] = (Query.writeSite += site)
  def insertPage(page: PageRow): DBIO[PageRow] = (Query.writePage += page)
  def insertPage(pages: Seq[PageRow]): DBIO[Seq[PageRow]] = (Query.writePage ++= pages)
  def insertPage(pages: List[PageRow]): DBIO[Seq[PageRow]] = insertPage(pages.toSeq)
//  def insertPage(pages: List[PageRow]): DBIO[List[PageRow]] = DBIO.sequence(pages.map(page => Query.writePage += page)).transactionally
  def insertOrUpdatePage(page: PageRow): DBIO[PageRow] = {
    val query = Page.filter(existing => existing.id === page.id)
    val existsAction = query.exists.result
    (for {
      exists <- existsAction
      result <- exists match {
        case true =>
          query.update(page).flatMap{ _ => findPageById(page.id)}
        case false => {
          insertPage(page).flatMap(pageRow => findPageById(pageRow.id)) //transactionally is important
        }
      }
    } yield result).transactionally
  }
  def insertIfNotExistsByUrl(page: PageRow): DBIO[PageRow] =
    Page.filter(p => p.url === page.url).result.headOption.flatMap {
      case Some(foundPage) => findPageByUrl(page)
      case None => insertPage(page)
    }.transactionally

  def insertIfNotExistsByUrl(pages: List[PageRow]): DBIO[List[PageRow]] =
    DBIO.sequence(pages.map(page => insertIfNotExistsByUrl(page))).transactionally
  def insertIfNotExistsByUrl(pages: Seq[PageRow]): DBIO[List[PageRow]] =
    insertIfNotExistsByUrl(pages.toList)
  def insertOrUpdatePage(pages: List[PageRow]): DBIO[List[PageRow]] =
    DBIO.sequence(pages.map(page => insertOrUpdatePage(page))).transactionally
  def insertOrUpdatePage(pages: Seq[PageRow]): DBIO[List[PageRow]] =
    insertOrUpdatePage(pages.toList)
  def pageExists(pageRow: PageRow): DBIO[Boolean] =
    Page.filter(page => page.url.isDefined && page.url === pageRow.url).exists.result
  def pageExists(pageRow: List[PageRow]): DBIO[Seq[PageRow]] = {
    val urls = pageRow.filter(_.url.isEmpty).map(_.url.get)
    Page.filter(row => row.url.inSet(urls)).result
  }

  def insertLink(link: LinkRow): DBIO[LinkRow] = Query.writeLink += link
  def insertLinkIfNotExists(link: LinkRow): DBIO[LinkRow] =
    Link.filter(l => l.fromPage === link.fromPage && l.toPage === link.toPage).result.headOption.flatMap {
      case Some(foundLink) => DBIO.successful(foundLink)
      case None => insertLink(link)
    }.transactionally
  def linkPages(fromPage: PageRow, toPage: PageRow): DBIO[LinkRow] = insertLinkIfNotExists(LinkRow(fromPage.id, toPage.id))
  def linkPages(fromPage: PageRow, toPages: List[PageRow]): DBIO[List[LinkRow]] =
    DBIO.sequence(toPages.map(toPage => insertLinkIfNotExists(LinkRow(fromPage.id, toPage.id))))
  def linkPages(fromPage: PageRow, toPages: Seq[PageRow]): DBIO[List[LinkRow]] = linkPages(fromPage, toPages.toList)
  def insertImage(image: ImageRow): DBIO[ImageRow] = Query.writeImage += image
  def insertImages(images: Seq[ImageRow]): DBIO[Seq[ImageRow]] = Query.writeImage ++= images
  def insertPageData(pageData: PageDataRow): DBIO[PageDataRow] = Query.writePageData += pageData
  def insertPageData(pageData: Seq[PageDataRow]): DBIO[Seq[PageDataRow]] = Query.writePageData ++= pageData

  // insert site with pages
  def insertSiteWithPage(
     site: SiteRow,
     page: PageRow
  ): DBIO[(SiteRow, PageRow)] =
    for {
      site <- insertSite(site)
      page <- insertPage(page.copy(siteId = Some(site.id)))
    } yield (site, page)

  def insertSiteWithPages(site: SiteRow, pages: Seq[PageRow]): DBIO[(SiteRow, Seq[PageRow])] = for {
    site <- insertSite(site)
    page <- insertPage(pages.map(page => page.copy(siteId = Some(site.id))))
  } yield (site, page)

  def insertPageWithContent(
     page: PageRow,
     images: Seq[ImageRow],
     pageDatum: Seq[PageDataRow],
     pageLinks: Seq[PageRow]
  ): DBIO[(PageRow, Seq[ImageRow], Seq[PageDataRow], Seq[PageRow])] =(
      for {
        insertedPage      <- insertIfNotExistsByUrl(page).transactionally // might cause a problem cause it's of type Option[PageRow] --> CAREFUL
        insertedImages    <- insertImages(images.map(image => image.copy(pageId = Option(insertedPage.id)))).transactionally
        insertedPageData  <- insertPageData(pageDatum.map(pageData => pageData.copy(pageId = Option(insertedPage.id)))).transactionally
        insertedLinks     <- insertIfNotExistsByUrl(pageLinks).transactionally
        _ <- linkPages(insertedPage, insertedLinks).transactionally // link the inserted pages
      } yield(insertedPage, insertedImages, insertedPageData, insertedLinks)
    ).transactionally

  object Query {
    import Tables._

    val writeSite = Site returning Site.map(_.id) into((site, id) => site.copy(id))
    val writePage = Page returning Page.map(_.id) into((page, id) => page.copy(id))
    val writeLink = Link returning Link
    val writePageData = PageData returning PageData.map(_.id) into((pageData, id) => pageData.copy(id))
    val writeImage = Image returning Image.map(_.id) into((image, id) => image.copy(id))

    val siteById = Site.findBy(_.id)
    val pageById = Page.findBy(_.id)
    val linksByFromId = Link.findBy(_.fromPage)
    val pageTypes = PageType.result
    val dataTypes = DataType.result

    // get site with pages
    val getSiteWithPages = for {
      site <- Site
      page <- Page if page.siteId === site.id
    } yield (site, page)
  }

}
