package com.ieps.crawler.actors

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import com.ieps.crawler.db.DBService
import com.ieps.crawler.db.Tables.{LinkRow, PageRow, SiteRow}
import com.ieps.crawler.utils.{ExtractFromHTML, HeadlessBrowser}
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object CrawlerWorkerActor {
  def props(workerId: String) = Props(new CrawlerWorkerActor(workerId))

  case object WorkerStatusRequest
  case class WorkerStatusResponse(isIdle: Boolean, idleTime: FiniteDuration)
}

class CrawlerWorkerActor(
    workerId: String
  ) extends Actor
    with StrictLogging {

  import CrawlerWorkerActor._
  import WorkDelegatorActor._
  private implicit val delay: FiniteDuration = 4 seconds

  private val logInstanceIdentifier = s"CrawlerWorker_$workerId:"
  private var inIdleState: DateTime = DateTime.now()
  private val browser = new HeadlessBrowser()
  private val dbService = new DBService()

  private def getDuration(dateTime: DateTime): FiniteDuration = FiniteDuration(DateTime.now().minus(dateTime.getMillis).getMillis, MILLISECONDS)

  override def receive: Receive = LoggingReceive {

    case WorkerStatusRequest => sender() ! (inIdleState match {
      case null => WorkerStatusResponse(isIdle = false, null)
      case dateTime: DateTime => WorkerStatusResponse(isIdle = true, getDuration(dateTime))
    })

    case ProcessNextPage(pageRow, site) =>
      logger.info(s"$logInstanceIdentifier Got a new pageRow to process: $pageRow")
      //  remove the inIdleState
      inIdleState = null
      processPage(pageRow, site)
      // after wrapping up with processing, update the idle state
      inIdleState = DateTime.now()

    case any: Any => logger.error(s"$logInstanceIdentifier Unknown message: $any")
  }

  def processPage(inputPage: PageRow, site:SiteRow): Seq[PageRow] = {
    // TODO: Process the url:
    //  1. extract the HTML code via Selenium
    //  2. extract:
    //      - urls (<a>, location) - add them into the frontier
    //      - images (<img>)
    //  3. filter out binary content from the extracted urls
    //  4. store content into the DB
    //  5. request next url
    val obtainedPage = browser.getPageSource(inputPage)
    logger.info(s"obtained page: $obtainedPage ")
    val extractor = new ExtractFromHTML(obtainedPage, site)
    val pageImages = extractor.getImgs
//    val page = extractor.getPageLinks
//    page.foreach(link => logger.info(s"$link"))

    val pageLinks = dbService.insertPage(extractor.getPageLinks) // TODO: continue storing precedure, idea: make this into a single transaction
//    val pageData = browser.getPageData(extractor.getPageData)
//    val (page, imgs, data) = dbService.insertPageWithContent(obtainedPage, pageImages, pageData)
//    val links: List[LinkRow] = dbService.linkPages(page, pageLinks)
//    links.foreach(link => logger.info(s"$link"))
    pageLinks
  }
}
