package com.ieps.crawler.db

import com.ieps.crawler.db.Tables._
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext

object CrawlerDIO {
  implicit val ec: ExecutionContext = ExecutionContext.global

  def findSiteById(id: Int): DBIO[SiteRow] = Query.siteById(id).result.head
  def findSiteByIdOption(id: Int): DBIO[Option[SiteRow]] = Query.siteById(id).result.headOption
  def findPageByIdOption(id: Int): DBIO[Option[PageRow]] = Page.filter(_.id === id).result.headOption
  def findSiteByDomain(domain: String): DBIO[SiteRow] = Site.filter(_.domain === domain).result.head
  def findPageByDomainOption(domain: String): DBIO[Option[SiteRow]] = Site.filter(_.domain === domain).result.headOption
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
  def insertOrUpdateSite(site: SiteRow): DBIO[SiteRow] = {
    val query = Site.filter(existing => existing.id === site.id)
    val existsAction = query.exists.result
    (for {
      exists <- existsAction
      result <- if (exists) {
        query.update(site).flatMap { _ => findSiteById(site.id) }
      } else {
        insertSite(site).flatMap(pageRow => findSiteById(pageRow.id)) //transactionally is important
      }
    } yield result).transactionally
  }
  def insertPage(page: PageRow): DBIO[PageRow] = (Query.writePage += page)
  def insertPage(pages: Seq[PageRow]): DBIO[Seq[PageRow]] = (Query.writePage ++= pages)
  def insertPage(pages: List[PageRow]): DBIO[Seq[PageRow]] = insertPage(pages.toSeq)
//  def insertPage(pages: List[PageRow]): DBIO[List[PageRow]] = DBIO.sequence(pages.map(page => Query.writePage += page)).transactionally
  def insertOrUpdatePage(page: PageRow): DBIO[PageRow] = {
    val query = Page.filter(existing => existing.id === page.id)
    val existsAction = query.exists.result
    (for {
      exists <- existsAction
      result <- if (exists) {
        query.update(page).flatMap { _ => findPageById(page.id) }
      } else {
        insertPage(page).flatMap(pageRow => findPageById(pageRow.id)) //transactionally is important
      }
    } yield result).transactionally
  }

  def insertIfNotExists(page: PageRow): DBIO[PageRow] =
    Page.filter(p => p.url === page.url).result.headOption.flatMap {
      case Some(foundPage) => DBIO.successful(foundPage) //insertPage(page.copy(pageTypeCode = Some("DUPLICATE")))
      case None => insertIfNotExistsByHash(page)
    }.transactionally

  def insertIfNotExists(pages: List[PageRow]): DBIO[List[PageRow]] =
    DBIO.sequence(pages.map(insertIfNotExists)).transactionally

  def insertIfNotExistsByHash(page: PageRow): DBIO[PageRow] =
    Page.filter(p => p.hash === page.hash).result.headOption.flatMap {
      case Some(foundPage) => insertPage(page.copy(pageTypeCode = Some("DUPLICATE"))) // is duplicate
      case None => insertPage(page)
    }.transactionally

  def insertIfNotExistsByDomain(site: SiteRow): DBIO[SiteRow] =
    Site.filter(p => p.domain === site.domain).result.headOption.flatMap {
      case Some(foundPage) => DBIO.successful(foundPage)
      case None => insertSite(site)
    }.transactionally

  def insertIfNotExistsByUrl(pages: List[PageRow]): DBIO[List[PageRow]] =
    DBIO.sequence(pages.map(page => insertIfNotExists(page))).transactionally
  def insertIfNotExistsByUrl(pages: Seq[PageRow]): DBIO[List[PageRow]] =
    insertIfNotExistsByUrl(pages.toList)
  def insertOrUpdatePage(pages: List[PageRow]): DBIO[List[PageRow]] =
    DBIO.sequence(pages.map(page => insertOrUpdatePage(page))).transactionally
  def insertOrUpdatePage(pages: Seq[PageRow]): DBIO[List[PageRow]] =
    insertOrUpdatePage(pages.toList)
  def pageExistsByUrl(pageRow: PageRow): DBIO[Boolean] =
    Page.filter(page => (page.url.isDefined && page.url === pageRow.url) || (page.hash.isDefined && page.hash === pageRow.hash)).exists.result

  def pageExistsByUrl(pageRow: List[PageRow]): DBIO[Seq[PageRow]] = {
    val urls = pageRow.filter(_.url.isEmpty).map(_.url.get)
    Page.filter(row => row.url.inSet(urls)).result
  }

  def pageExistsByHash(pageRow: PageRow): DBIO[Boolean] =
    Page.filter(page => page.hash.isDefined && page.hash === pageRow.hash).exists.result

  def pageExistsByHash(pageRow: List[PageRow]): DBIO[Seq[PageRow]] = {
    val hashes = pageRow.filter(_.hash.isEmpty).map(_.hash.get)
    Page.filter(row => row.hash.inSet(hashes)).result
  }

  def pageExists(pageRow: PageRow): DBIO[Boolean] = {
    (pageRow.url, pageRow.hash) match {
      case (Some(url), Some(hash)) =>
        Page.filter(row =>
          row.hash.inSet(List(hash))
            || row.url.inSet(List(url))).exists.result
      case (Some(_), None) =>
        pageExistsByUrl(pageRow)
      case (None, Some(_)) =>
        pageExistsByHash(pageRow)
      case (None, None) =>
        DBIO.successful(false)
    }
  }

  def pageExists(pageRow: List[PageRow]): DBIO[List[Boolean]] =
    DBIO.sequence(pageRow.map(pageExists))


  def insertLink(link: LinkRow): DBIO[LinkRow] = Query.writeLink += link
  def insertLinkIfNotExists(link: LinkRow): DBIO[LinkRow] =
    Link.filter(l => l.fromPage === link.fromPage && l.toPage === link.toPage).result.headOption.flatMap {
      case Some(foundLink) => DBIO.successful(foundLink)
      case None => insertLink(link)
    }.transactionally
  def linkPages(fromPage: PageRow, toPage: PageRow): DBIO[LinkRow] = insertLinkIfNotExists(LinkRow(fromPage.id, toPage.id))

  def linkPages(fromPage: PageRow, toPages: List[PageRow]): DBIO[List[LinkRow]] =
    DBIO.sequence(toPages.map(toPage => insertLinkIfNotExists(LinkRow(fromPage.id, toPage.id))))
  def insertLinkIfNotExists(
                   fromPage: PageRow,
                   toPages: List[PageRow]
                 ): DBIO[Seq[LinkRow]] = (for {
    insertedFromPage <- insertIfNotExists(fromPage)
    insertedToPages  <- insertIfNotExists(toPages)
    links            <- linkPages(insertedFromPage, insertedToPages)
  } yield links).transactionally

  def linkPages(fromPage: PageRow, toPages: Seq[PageRow]): DBIO[List[LinkRow]] = linkPages(fromPage, toPages.toList)
  def insertImage(image: ImageRow): DBIO[ImageRow] = Query.writeImage += image
  def insertImages(images: Seq[ImageRow]): DBIO[Seq[ImageRow]] = Query.writeImage ++= images
  def insertIfNotExists(image: ImageRow): DBIO[ImageRow] =
    Image.filter(p => p.filename === image.filename).result.headOption.flatMap {
      case Some(foundImage) => insertImage(foundImage.copy(id = -1, pageId = foundImage.pageId)) //insertPage(page.copy(pageTypeCode = Some("DUPLICATE")))
      case None => insertImage(image)
    }.transactionally

  def imageExists(imageRow: ImageRow): DBIO[Boolean] =
    Image.filter(p => p.filename === imageRow.filename).exists.result

  def insertPageData(pageData: PageDataRow): DBIO[PageDataRow] = Query.writePageData += pageData
  def insertPageData(pageData: Seq[PageDataRow]): DBIO[Seq[PageDataRow]] = Query.writePageData ++= pageData

  def insertIfNotExists(pageData: PageDataRow): DBIO[PageDataRow] =
    PageData.filter(p => p.filename === pageData.filename).result.headOption.flatMap {
      case Some(foundPageData) => insertPageData(foundPageData.copy(id = -1, pageId = pageData.pageId)) //insertPage(page.copy(pageTypeCode = Some("DUPLICATE")))
      case None => insertPageData(pageData)
    }.transactionally

  def pageDataExists(pageDataRow: PageDataRow): DBIO[Boolean] =
    PageData.filter(p => p.filename === pageDataRow.filename).exists.result

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
        insertedPage      <- insertIfNotExists(page).transactionally // might cause a problem cause it's of type Option[PageRow] --> CAREFUL
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
