package com.ieps.crawler

import akka.actor.CoordinatedShutdown.JvmExitReason
import akka.actor.{ActorRef, ActorSystem, CoordinatedShutdown}
import com.ieps.crawler.actors.CrawlerWorkerActor
import com.ieps.crawler.actors.WorkDelegatorActor.ProcessNextPage
import com.ieps.crawler.utils.{BigQueue, Canonical}
import com.typesafe.scalalogging.StrictLogging
import sun.misc.{Signal, SignalHandler}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import slick.jdbc.PostgresProfile.api.Database

object CrawlerApp extends App with StrictLogging {


  import db.Tables._
  val noviCanonical = "https://www.vijesti.me/gojko/marko.html/"
  val proba  = Canonical.extractDomain(noviCanonical)
  logger.info(s"$proba")




  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  val actorSystem: ActorSystem = ActorSystem("ieps-crawler")
  val dbConnection = Database.forConfig("local")
  val queue = new BigQueue("./queue")
  val crawlerWorker: ActorRef = actorSystem.actorOf(CrawlerWorkerActor.props("1", dbConnection, queue))

  val siteRow = SiteRow(1, Some("https://www.vijesti.me"))
  val pageRow = PageRow(id = -1, url=Some("https://e-uprava.gov.si/"))

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

  Signal.handle(new Signal("INT"), new SignalHandler() {
    def handle(sig: Signal) {
      logger.info(s"Shutting down.")
      // TODO: graceful actor shut-down
      dbConnection.close()
      Await.result(CoordinatedShutdown(actorSystem).run(JvmExitReason), 30 seconds)
    }
  })
}
