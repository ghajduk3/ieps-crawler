package com.ieps.crawler.actors

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import com.ieps.crawler.db.DBService
import com.ieps.crawler.db.Tables.{ImageRow, PageDataRow, PageRow, SiteRow}
import com.ieps.crawler.utils.{BigQueue, DuplicateLinks, ExtractFromHTML, HeadlessBrowser}
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime
import slick.jdbc.PostgresProfile.api.Database

import scala.concurrent.duration.{FiniteDuration, _}

object CrawlerWorkerActor {
  def props(workerId: String, db: Database, queue: BigQueue) = Props(new CrawlerWorkerActor(workerId, db, queue))

  case object WorkerStatusRequest
  case class WorkerStatusResponse(isIdle: Boolean, idleTime: FiniteDuration)
}

class CrawlerWorkerActor(
    workerId: String,
    db: Database,
    queue: BigQueue,
    debug: Boolean = false
  ) extends Actor
    with StrictLogging {

  import CrawlerWorkerActor._
  import WorkDelegatorActor._
  private implicit val delay: FiniteDuration = 4 seconds

  private val logInstanceIdentifier = s"CrawlerWorker_$workerId:"
  private var inIdleState: DateTime = DateTime.now()
  private val browser = new HeadlessBrowser()
  private val dbService = new DBService(db)
  private val duplicate = new DuplicateLinks(db)

  private def getDuration(dateTime: DateTime): FiniteDuration = FiniteDuration(DateTime.now().minus(dateTime.getMillis).getMillis, MILLISECONDS)

  override def receive: Receive = LoggingReceive {

    case WorkerStatusRequest => sender() ! (inIdleState match {
      case null => WorkerStatusResponse(isIdle = false, null)
      case dateTime: DateTime => WorkerStatusResponse(isIdle = true, getDuration(dateTime))
    })

    case ProcessNextPage(pageRow, site) =>
      logger.info(s"$logInstanceIdentifier Got a new pageRow to process: $pageRow")
      // TODO: wait until duration > idleState
      //  remove the inIdleState
      inIdleState = null
      var page: PageRow = pageRow.copy() // TODO: add SHA hash as a field in `page` table
      if (page.siteId.isDefined) {
        if (pageRow.siteId.get != site.id) {
          logger.info(s"$logInstanceIdentifier Monkey business: page is not associated to site.")
          throw new Exception(s"$logInstanceIdentifier Monkey business: page is not associated to site.")
        }
      } else {
        page = pageRow.copy(siteId = Some(site.id))
      }
      val links: Seq[PageRow] = processPage(page, site, downloadData = false)
      if(links.nonEmpty) {
        logger.info(s"${links.head}")
        links.foreach(queue.enqueue)
      }
      val items = for {
        item <- queue.dequeue()
      } yield item
      items.foreach(item => logger.info(s"next item: $item"))
//      if (debug) links.foreach(link => logger.debug(s"$logInstanceIdentifier got link: $link"))
      // after wrapping up with processing, update the idle state
      inIdleState = DateTime.now()

    case any: Any => logger.error(s"$logInstanceIdentifier Unknown message: $any")
  }

  def processPage(inputPage: PageRow, site:SiteRow, downloadData: Boolean): Seq[PageRow] = {
    // TODO: Process the url:
    //  [x] extract the HTML code via Selenium
    //  [x] extract:
    //      [x] urls (<a>, location) - add them into the frontier
    //      [x] images (<img>)
    //  [x] filter out binary content from the extracted urls
    //  [x] store content into the DB
    //  [?] request next url
    val obtainedPage = browser.getPageSource(inputPage)
    logger.debug(s"$obtainedPage")
    val extractor = new ExtractFromHTML(obtainedPage, site)
    var pageImages: Seq[ImageRow] = Seq()
    var pageData: Seq[PageDataRow] = Seq()
    if (downloadData) {
      logger.info(s"$logInstanceIdentifier Getting images...")
      pageImages = browser.getImageData(extractor.getImages)
      logger.info(s"$logInstanceIdentifier Getting page data...")
      pageData = browser.getPageData(extractor.getPageData)
    }
    logger.info(s"$logInstanceIdentifier Getting page links...")
    val pageLinks = duplicate.deduplicatePages(extractor.getPageLinks)
    if (debug) {
      logger.info(s"Unique pageLinks size: ${pageLinks.size}")
      pageLinks.foreach(link => logger.info(s"$link"))
    }
    logger.info(s"$logInstanceIdentifier Storing into the database...")
    val (page, imgs, data, links) = dbService.insertPageWithContent(obtainedPage, pageImages, pageData, pageLinks)
    logger.info(s"$logInstanceIdentifier Done, continuing...")
    links
  }
}
