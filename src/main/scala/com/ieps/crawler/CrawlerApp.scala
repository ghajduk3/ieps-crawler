package com.ieps.crawler

import com.ieps.crawler.db.DBService
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext


object CrawlerApp extends App with StrictLogging {
  //import db.Tables._
  implicit val ec = ExecutionContext.global
  val dbService = new DBService("local")
  try {
    logger.info(Canonical.getCanonical("http://stackoveRRRflow.com:80/"))
    // list entities
//    val resultFuture = db.run(Tables.PageType.result)
//    var result = Await.result(resultFuture, Duration.Inf).toList
//    result.foreach(res => logger.info(s"$res"))

//    val ins1: DBIO[Int] = entityCollection.page += (1L, 1L, "HTML", "google.com/search", "<html>", 200, new DateTime())
//    val ins2: DBIO[Int] = entityCollection.page += (2L, 1L, "HTML", "google.com/search/yes", "<html>", 200, new DateTime())
//    val newSiteWithId = (Tables.Site returning Tables.Site.map(_.id)) +=
//            SiteRow(-1, Some("pr3mar.net"), Some("robots.txt content"))
//    newSiteWithId andThen
//    val newPage =
//    val insSite: DBIO[Int] = entityCollection.site += (3L, "google.com/yes")
//    val setup = DBIO.seq(insSite)
//    val setupFuture = db.run(newSiteWithId)
//    val siteWithId = Await.result(setupFuture, Duration.Inf)
//    logger.info(s"$siteWithId")
//    val page = PageRow(-1, Some(-1), Some("HTML"))
//    val site = SiteRow(-1, Some("newsite.com"))
//    val crawlerDIO = new CrawlerDIO
//    val resultFuture = db.run(crawlerDIO.findSiteById(2))
//    val resultFuture = db.run(crawlerDIO.findSiteWithPages)

//    val action = for {
//      site <- crawlerDIO.insertSite(site)
//      page <- crawlerDIO.insertPage(page.copy(siteId = Some(site.id)))
//    } yield (site, page)
//
//    val resultFuture = db.run(action.transactionally)
//
//    val result: Seq[(SiteRow, PageRow)] = Await.result(resultFuture, Duration.Inf)
//    logger.info(s"The result is: ${result.head._1}")
//    logger.info(s"The result is: ${result.head._2}")
//    logger.info(s"${dbService.getPageById(4)}")
//    logger.info(s"${dbService.getSiteById(1)}")

//    val site = SiteRow(-1, Some("twitter1.com"), Some("robots.txt"), Some("sitemap.xml"))
//    val page1 = PageRow(-1, Some(-1), Some("HTML"), Some("url3"))
//    val page2 = PageRow(-1, Some(-1), Some("HTML"), Some("url2"))
//    logger.info(s"${dbService.insertSiteWithPages(site, Seq(page1, page2))}")
    //val page1 = dbService.getPageById(11)
    //val result = dbService.insertPageWithContent(page1.copy(page1.id, page1.siteId, page1.pageTypeCode, Some("url7")), Seq(), Seq())
    //logger.info(s"$result")
//    val page2 = dbService.getPageById(11).get
//    logger.info(s"${dbService.linkPages(page1, page2)}")
//    logger.info(s"${dbService.getPageLinks(page1)}")
//    val (images, data) = dbService.getPageContent(page1)
//    logger.info(s"Images: $images")
//    logger.info(s"PageData: $data")
//    val newPage = PageRow(-1, page.get.siteId, Some("HTML"), Some("url4"))
//    val image1 = ImageRow(-1, Some(-1), Some("img1.jpg"), Some("binary"), Some(Array.emptyByteArray), Some(new DateTime))
//    val image2 = ImageRow(-1, Some(-1), Some("img2.jpg"), Some("binary"), Some(Array.emptyByteArray), Some(new DateTime))
//    val data1 = PageDataRow(-1, Some(-1), Some("DOC"), Some(Array.emptyByteArray))
//    val data2 = PageDataRow(-1, Some(-1), Some("PDF"), Some(Array.emptyByteArray))
//    logger.info(s"${dbService.insertPageWithContent(newPage, Seq(image1, image2), Seq(data1, data2))}")

  } catch {
    case e: Exception =>
      logger.error(e.getMessage)
      e.printStackTrace()
  } finally dbService.closeDb
}
