package com.ieps.crawler

import akka.actor.CoordinatedShutdown.JvmExitReason
import akka.actor.{ActorRef, ActorSystem, CoordinatedShutdown, Kill}
import com.ieps.crawler.actors.PageWorkerActor
import com.ieps.crawler.actors.PageWorkerActor.StartWorker
import com.ieps.crawler.queue.Queue.QueuePageEntry
import com.ieps.crawler.queue.{DataQueue, PageQueue}
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api.Database
import sun.misc.{Signal, SignalHandler}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}

object CrawlerApp extends App with StrictLogging {

  import db.Tables._
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  val actorSystem: ActorSystem = ActorSystem("ieps-crawler")
  val dbConnection = Database.forConfig("local")


  val pageQueue = new PageQueue("./queue")
  val dataQueue = new DataQueue("./queue")
  var workers: List[ActorRef] = (1 to 8).toList.map(id =>
    actorSystem.actorOf(PageWorkerActor.props(s"$id", dbConnection, pageQueue, dataQueue).withDispatcher("thread-pool-dispatcher"))
  )
//  val crawlerWorker: ActorRef = actorSystem.actorOf(PageWorkerActor.props("1", dbConnection, pageQueue, dataQueue).withDispatcher("thread-pool-dispatcher"))
//  val crawlerWorker2: ActorRef = actorSystem.actorOf(PageWorkerActor.props("2", dbConnection, pageQueue, dataQueue).withDispatcher("thread-pool-dispatcher"))
//  crawlerWorker ! StartWorker
//  Thread.sleep(2000)
//  crawlerWorker2 ! StartWorker

  pageQueue.enqueueAll(List(
    QueuePageEntry(PageRow(id = -1, url=Some("http://www.e-prostor.gov.si/"))),
    QueuePageEntry(PageRow(id = -1, url=Some("http://www.e-prostor.gov.si/"))),
    QueuePageEntry(PageRow(id = -1, url=Some("http://evem.gov.si/"))),
    QueuePageEntry(PageRow(id = -1, url=Some("https://e-uprava.gov.si/"))),
    QueuePageEntry(PageRow(id = -1, url=Some("https://podatki.gov.si/")))
  ))
  workers.foreach(worker => {
    worker ! StartWorker
    Thread.sleep(5000) // add some time between spawning new actors
  })
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
      workers.foreach(worker => worker ! Kill)
      dbConnection.close()
      Await.result(CoordinatedShutdown(actorSystem).run(JvmExitReason), 30 seconds)
    }
  })
}
