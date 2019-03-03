package com.ieps.crawler

import com.ieps.crawler.db.Tables.{PageRow, SiteRow}
import com.ieps.crawler.db.{CrawlerDIO, Tables}
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

object CrawlerApp extends App with StrictLogging {
  implicit val ec = ExecutionContext.global
//  val db = Database.forConfig("slick.postgres.local")
  val db = Database.forConfig("local")
  try {
    // list entities
//    val resultFuture = db.run(Tables.PageType.result)
//    var result = Await.result(resultFuture, Duration.Inf).toList
//    result.foreach(res => logger.info(s"$res"))

//    val ins1: DBIO[Int] = entityCollection.page += (1L, 1L, "HTML", "google.com/search", "<html>", 200, new DateTime())
//    val ins2: DBIO[Int] = entityCollection.page += (2L, 1L, "HTML", "google.com/search/yes", "<html>", 200, new DateTime())
    val newSiteWithId = (Tables.Site returning Tables.Site.map(_.id)) +=
            SiteRow(-1, Some("pr3mar.net"), Some("robots.txt content"))
//    newSiteWithId andThen
//    val newPage =
//    val insSite: DBIO[Int] = entityCollection.site += (3L, "google.com/yes")
//    val setup = DBIO.seq(insSite)
//    val setupFuture = db.run(newSiteWithId)
//    val siteWithId = Await.result(setupFuture, Duration.Inf)
//    logger.info(s"$siteWithId")
//    val page = PageRow(-1, Some(-1), Some("HTML"))
//    val site = SiteRow(-1, Some("newsite.com"))
    val crawlerDIO = new CrawlerDIO
//    val resultFuture = db.run(crawlerDIO.findSiteById(2))
    val resultFuture = db.run(crawlerDIO.findSiteWithPages)

//    val action = for {
//      site <- crawlerDIO.insertSite(site)
//      page <- crawlerDIO.insertPage(page.copy(siteId = Some(site.id)))
//    } yield (site, page)
//
//    val resultFuture = db.run(action.transactionally)
//
    val result: Seq[(SiteRow, PageRow)] = Await.result(resultFuture, Duration.Inf)
    logger.info(s"The result is: ${result.head._1}")
    logger.info(s"The result is: ${result.head._2}")

  } catch {
    case e: Exception =>
      logger.error(e.getMessage)
      e.printStackTrace()
  } finally db.close()
}
