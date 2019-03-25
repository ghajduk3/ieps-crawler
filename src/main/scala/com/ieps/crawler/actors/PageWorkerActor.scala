package com.ieps.crawler.actors

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import com.ieps.crawler.db.{DBService, Tables}
import com.ieps.crawler.db.Tables.{ImageRow, PageDataRow, PageRow, SiteRow}
import com.ieps.crawler.queue.Queue.{QueueDataEntry, QueuePageEntry}
import com.ieps.crawler.queue.{DataQueue, PageQueue}
import com.ieps.crawler.utils._
import com.panforge.robotstxt.RobotsTxt
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.DateTime
import slick.jdbc.PostgresProfile.api.Database

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object PageWorkerActor {
  def props(workerId: String, db: Database, pageQueue: PageQueue, dataQueue: DataQueue) = Props(new PageWorkerActor(workerId, db, pageQueue, dataQueue))

  case object StartWorker
  case object WorkerStatusRequest
  case class WorkerStatusResponse(isIdle: Boolean, idleTime: FiniteDuration)

  val defaultDelayTime: Long = 4 * 1000L // 4s
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
  private implicit val timeout: FiniteDuration = 10 seconds

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

          logger.info(s"$logInstanceIdentifier Processing: ${queuedPage.url.get}")
          try {
            inferSite(queuedPage).foreach(site => {
              val robots = new SiteRobotsTxt(site)
              robots.isAllowed(queuedPage) match {
                case true => {
                  Await.ready(browser.getPageSource(queuedPage), timeout).value.get match {
                    case Success(pageWithContent: PageRow) =>
                      Utils.retryWithBackoff(logRetry = true) {
                        val insertedPage: PageRow = dbService.insertIfNotExistsByUrl(pageWithContent.copy(siteId = Some(site.id))) // TODO: add SHA hash as a field in `page` table
                        referencePage.foreach(fromPage => dbService.linkPages(fromPage, insertedPage))
                        val httpStatusCode = insertedPage.httpStatusCode.get

                        if (200 <= httpStatusCode && httpStatusCode < 400) {
                          val extractor = new ExtractFromHTML(insertedPage, site)

                          // enqueue the extracted links
                          extractor.getPageLinks.map(duplicate.deduplicatePages(_).map(link => QueuePageEntry(link, Some(insertedPage)))).foreach(pageQueue.enqueueAll)
                          // enqueue the page data
                          extractor.getPageData.map(_.map(data => QueueDataEntry(isData = false, insertedPage.id, data.url.get))).foreach(dataQueue.enqueueAll)
                          // enqueue the images
                          extractor.getImages.map(_.map(image => QueueDataEntry(isData = true, insertedPage.id, image.filename.get))).foreach(dataQueue.enqueueAll)
                        } else logger.warn(s"Got status code $httpStatusCode")
                      }
                    case Failure(exception) =>
                      logger.error(s"$logInstanceIdentifier Error processing ${queuedPage.url.get}: ${exception.getMessage}")
                  }
                  var delay = robots.getDelay
                  if (delay == Long.MinValue) {
                    delay = defaultDelayTime
                  }
                  logger.info(s"$logInstanceIdentifier Waiting for ${delay / 1000L}")
                  Thread.sleep(delay)
                }
                case false => logger.warn(s"$logInstanceIdentifier Site not allowed: ${queuedPage.url.get}")
              }
            })
          } catch {
            case e: Exception => logger.error(s"$logInstanceIdentifier Failed processing ${queuedPage.url.get}: ${e.getMessage}")
          }
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
        robotsContent.foreach(content => {
          site = site.copy(robotsContent = Some(content + "\n"))
          val robotsTxt = new SiteRobotsTxt(site)
          if (robotsTxt.getSitemaps.size > 1) {
            logger.warn (s"$logInstanceIdentifier multiple sitemaps?? ${robotsTxt.getSitemaps}")
          }
          robotsTxt.getSitemaps.foreach (url => {
            Try(browser.getUrlContent(url)) match {
              case Success(Some(content: String)) =>
                site = site.copy(sitemapContent = Some(content))
                pageQueue.enqueueAll(SiteMaps.getSiteMapUrls(url, site).map(page => QueuePageEntry(page)))
            }
          })
        })
        Utils.retryWithBackoff {
          Some(dbService.insertIfNotExistsByDomain(site))
        }
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
