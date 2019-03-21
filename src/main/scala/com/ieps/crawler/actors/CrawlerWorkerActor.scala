package com.ieps.crawler.actors

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import com.ieps.crawler.db.DBService
import com.ieps.crawler.db.Tables.{ImageRow, PageDataRow, PageRow, SiteRow}
import com.ieps.crawler.utils.{BigQueue, DuplicateLinks, ExtractFromHTML, HeadlessBrowser}
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime
import slick.jdbc.PostgresProfile.api.Database

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps

object CrawlerWorkerActor {
  def props(workerId: String, db: Database, queue: BigQueue) = Props(new CrawlerWorkerActor(workerId, db, queue))

  case object WorkerStatusRequest
  case class WorkerStatusResponse(isIdle: Boolean, idleTime: FiniteDuration)
}

class CrawlerWorkerActor(
    workerId: String,
    db: Database,
    queue: BigQueue,
    debug: Boolean = true
  ) extends Actor
    with StrictLogging {

  import CrawlerWorkerActor._
  import WorkDelegatorActor._
  private implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup("thread-pool-dispatcher")
  private implicit val delay: FiniteDuration = 4 seconds

  private val logInstanceIdentifier = s"CrawlerWorker_$workerId:"
  private var inIdleState: DateTime = DateTime.now()
  private val browser = new HeadlessBrowser()
//  private val db = Database.forConfig("local")
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
      if (links.nonEmpty) {
        links.foreach(queue.enqueue)
      }
      /*while(!queue.isEmpty) {
        logger.info(s"$logInstanceIdentifier next item: ${queue.dequeue()}")
        Thread.sleep(200)
      }*/
//      if (debug) links.foreach(link => logger.debug(s"$logInstanceIdentifier got link: $link"))
      // after wrapping up with processing, update the idle state
      inIdleState = DateTime.now()
      logger.info(s"$logInstanceIdentifier Queue is empty.")
    case any: Any => logger.error(s"$logInstanceIdentifier Unknown message: $any")
  }

  def processPage(inputPage: PageRow, site:SiteRow, downloadData: Boolean): Seq[PageRow] = {
    val obtainedPage = browser.getPageSource(inputPage)
    logger.info(s"$logInstanceIdentifier Status code: ${obtainedPage.httpStatusCode}")
    if(obtainedPage.httpStatusCode.get >= 200 && obtainedPage.httpStatusCode.get < 300) {
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
        logger.info(s"$logInstanceIdentifier Unique pageLinks size: ${pageLinks.size}")
        //      pageLinks.foreach(link => logger.debug(s"$link"))
      }
      logger.info(s"$logInstanceIdentifier Storing into the database...")
      Thread.sleep(ThreadLocalRandom.current().nextLong(10000))
      val (page, imgs, data, links) = dbService.insertPageWithContent(obtainedPage, pageImages, pageData, pageLinks)
      logger.info(s"$logInstanceIdentifier Done, continuing...")
      links
    } else List.empty
  }
}
