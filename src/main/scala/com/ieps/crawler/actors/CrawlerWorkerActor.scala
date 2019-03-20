package com.ieps.crawler.actors

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import com.ieps.crawler.db.{DBService, Tables}
import com.ieps.crawler.db.Tables.{LinkRow, PageRow, SiteRow}
import com.ieps.crawler.utils.{ExtractFromHTML, HeadlessBrowser}
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime
import slick.jdbc.PostgresProfile.api.Database

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object CrawlerWorkerActor {
  def props(workerId: String, db: Database) = Props(new CrawlerWorkerActor(workerId, db))

  case object WorkerStatusRequest
  case class WorkerStatusResponse(isIdle: Boolean, idleTime: FiniteDuration)
}

class CrawlerWorkerActor(
    workerId: String,
    db: Database
  ) extends Actor
    with StrictLogging {

  import CrawlerWorkerActor._
  import WorkDelegatorActor._
  private implicit val delay: FiniteDuration = 4 seconds

  private val logInstanceIdentifier = s"CrawlerWorker_$workerId:"
  private var inIdleState: DateTime = DateTime.now()
  private val browser = new HeadlessBrowser()
  private val dbService = new DBService(db)

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
      var page: PageRow = pageRow.copy()
      if (page.siteId.isDefined) {
        if (pageRow.siteId.get != site.id) {
          logger.info(s"$logInstanceIdentifier Monkey business: page is not associated to site.")
          throw new Exception(s"$logInstanceIdentifier Monkey business: page is not associated to site.")
        }
      } else {
        page = pageRow.copy(siteId = Some(site.id))
      }
      val links: Seq[PageRow] = processPage(page, site)
      links.foreach(link => logger.info(s"$logInstanceIdentifier got link: $link"))

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
    logger.info(s"$obtainedPage")
    val extractor = new ExtractFromHTML(obtainedPage, site)
    logger.info(s"$logInstanceIdentifier Getting images:")
    val pageImages = browser.getImageData(extractor.getImages)
//    pageImages.foreach(image => logger.info(s"$image"))
    logger.info(s"$logInstanceIdentifier Getting page data:")
    val pageData = browser.getPageData(extractor.getPageData)
    logger.info(s"$logInstanceIdentifier Getting page links:")
    val pageLinks = extractor.getPageLinks
    // TODO: filter duplicate pages here
    logger.info(s"$logInstanceIdentifier Storing into the database:")
    val (page, imgs, data, links) = dbService.insertPageWithContent(obtainedPage, pageImages, pageData, pageLinks)
    logger.info(s"$logInstanceIdentifier Done, continuing...")
    links
  }
}
