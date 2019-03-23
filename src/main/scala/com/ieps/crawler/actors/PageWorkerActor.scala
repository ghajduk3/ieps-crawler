package com.ieps.crawler.actors

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import com.ieps.crawler.db.DBService
import com.ieps.crawler.db.Tables.{ImageRow, PageDataRow, PageRow, SiteRow}
import com.ieps.crawler.queue.Queue.{QueueDataEntry, QueuePageEntry}
import com.ieps.crawler.queue.{DataQueue, PageQueue}
import com.ieps.crawler.utils._
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime
import slick.jdbc.PostgresProfile.api.Database

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object PageWorkerActor {
  def props(workerId: String, db: Database, pageQueue: PageQueue, dataQueue: DataQueue) = Props(new PageWorkerActor(workerId, db, pageQueue, dataQueue))

  case object StartWorker
  case object WorkerStatusRequest
  case class WorkerStatusResponse(isIdle: Boolean, idleTime: FiniteDuration)
}

class PageWorkerActor(
    workerId: String,
    db: Database,
    pageQueue: PageQueue,
    dataQueue: DataQueue,
    debug: Boolean = true
  ) extends Actor
    with StrictLogging {

  import PageWorkerActor._
  private implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup("thread-pool-dispatcher")
  private implicit val delay: FiniteDuration = 4 seconds

  private val logInstanceIdentifier = s"CrawlerWorker_$workerId:"
  private var inIdleState: Option[DateTime] = Some(DateTime.now())
  private val browser = new HeadlessBrowser()
//  private val db = Database.forConfig("local")
  private val dbService = new DBService(db)
  private val duplicate = new DuplicateLinks(db)

  private def getDuration(dateTime: DateTime): FiniteDuration = FiniteDuration(DateTime.now().minus(dateTime.getMillis).getMillis, MILLISECONDS)

  override def receive: Receive = LoggingReceive {

    case WorkerStatusRequest => sender() ! (inIdleState match {
      case Some(dateTime) => WorkerStatusResponse(isIdle = true, getDuration(dateTime))
      case None => WorkerStatusResponse(isIdle = false, null)
    })

    case StartWorker =>
      while(pageQueue.hasMorePages) {
        pageQueue.dequeue().map(queueEntry => {
          inIdleState = None
          // TODO: wait until duration > idleState
          //  remove the inIdleState

          val queuedPage = queueEntry.pageInQueue
          val referencePage = queueEntry.referencePage
          val site = inferSite(queuedPage)
          logger.info(s"$logInstanceIdentifier Processing: ${queuedPage.url.get}")
          var page: PageRow = queuedPage.copy() // TODO: add SHA hash as a field in `page` table
          val links = List.empty[QueuePageEntry]
          val data = List.empty[QueueDataEntry]
          pageQueue.enqueueAll(links)
          dataQueue.enqueueAll(data)

          inIdleState = Some(DateTime.now())
          None
        })
      }
      logger.info(s"$logInstanceIdentifier Queue is empty.")
      // TODO: send self a waking signal after 30s
    case any: Any => logger.error(s"$logInstanceIdentifier Unknown message: $any")
  }

  def inferSite(page: PageRow): Option[SiteRow] = {
    dbService.getSiteByDomain(Canonical.extractDomain(page.url.get)) match {
      case Some(site) => Some(site)
      case None =>
        val domain = Some(Canonical.extractDomain(page.url.get))
        logger.info (s"$logInstanceIdentifier domain = $domain")
        var site = SiteRow (- 1, domain)
        val robotsContent = browser.getRobotsTxt(Canonical.getCanonical(site.domain.get))
        robotsContent.map (content => {
          site = site.copy(robotsContent = Some(content + "\n"))
          val robotsTxt = new SiteRobotsTxt(site)
          if (robotsTxt.getSitemaps.size > 1) {
            logger.warn (s"$logInstanceIdentifier multiple sitemaps?? ${robotsTxt.getSitemaps}")
          }
          robotsTxt.getSitemaps.foreach (url => {
            site = site.copy(sitemapContent = browser.getUrlContent (url) )
            pageQueue.enqueueAll(SiteMaps.getSiteMapUrls(url, site).map(page => QueuePageEntry(page)))
          })
          content
        })
        dbService.insertOrUpdateSite(site)
    }
  }

  /*def processPage(inputPage: PageRow, site:SiteRow, downloadData: Boolean): Seq[PageRow] = {
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
  }*/
}
