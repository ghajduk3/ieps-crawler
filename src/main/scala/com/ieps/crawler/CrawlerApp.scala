package com.ieps.crawler

import java.io._

import akka.actor.{ActorRef, ActorSystem}
import com.ieps.crawler.actors.CrawlerWorkerActor
import com.ieps.crawler.actors.WorkDelegatorActor.ProcessNextPage
import com.ieps.crawler.db.DBService
import com.ieps.crawler.utils.HeadlessBrowser
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object CrawlerApp extends App with StrictLogging {

  import db.Tables._
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  val actorSystem: ActorSystem = ActorSystem("crawler")
  val crawlerWorker: ActorRef = actorSystem.actorOf(CrawlerWorkerActor.props("1"))

  val siteRow = SiteRow(1, Some("https://e-uprava.gov.si"))
  val pageRow = PageRow(id = -1, url=Some("https://e-uprava.gov.si"))

  crawlerWorker ! ProcessNextPage(pageRow, siteRow)
  /*
  try {
    val dbService = new DBService("local")
    val browser = new HeadlessBrowser
    val result1: PageRow = browser.getPageSource(PageRow(id = -1, url=Some("https://e-uprava.gov.com.si")))
    logger.info(s"result1 status code = ${result1.httpStatusCode}, type = ${result1.pageTypeCode.getOrElse("")}, content size = ${result1.htmlContent.getOrElse("").length}")
    val result2: PageDataRow = browser.getPageData(PageRow(-1, url = Some("https://api.libreoffice.org/examples/cpp/DocumentLoader/test.odt")))

    logger.info(s"result2, type = ${result2.dataTypeCode.getOrElse("")}")
    try {
      val bw = new BufferedOutputStream(new FileOutputStream("./sample.odt"))
      bw.write(result2.data.get)
      bw.close()
    } catch {
      case e: Exception => logger.error(s"FAILED> $e")
    }


    dbService.closeDb()
  } catch {
    case e: Exception =>
      logger.error(e.getMessage)
      e.printStackTrace()
  }
  */
}
