package com.ieps.crawler.actors

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import com.ieps.crawler.db.DBService
import com.ieps.crawler.db.Tables.{PageRow, SiteRow}
import com.ieps.crawler.queue.Queue.{QueueDataEntry, QueuePageEntry}
import com.ieps.crawler.queue.{DataQueue, PageQueue}
import com.ieps.crawler.utils._
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api.Database

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object PageWorkerActor {
  def props(workerId: String, db: Database, pageQueue: PageQueue, dataQueue: DataQueue) = Props(new PageWorkerActor(workerId, db, pageQueue, dataQueue))

  case object StartWorker
  case object ProcessNextPage
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
  private val browser = new HeadlessBrowser()
  private val dbService = new DBService(db)
  private val duplicate = new DuplicateLinks(db)

  override def receive: Receive = LoggingReceive {

    case StartWorker | ProcessNextPage =>
      !pageQueue.hasMorePages match {
        case true =>
          pageQueue.dequeue().foreach(queueEntry => {
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
                    logger.info(s"$logInstanceIdentifier Waiting for ${delay / 1000L}s")
                    context.system.scheduler.scheduleOnce(FiniteDuration(delay, MILLISECONDS), self, ProcessNextPage)
                  }
                  case false =>
                    logger.warn(s"$logInstanceIdentifier Site not allowed: ${queuedPage.url.get}")
                    self ! ProcessNextPage
                }
              })
            } catch {
              case e: Exception =>
                logger.error(s"$logInstanceIdentifier Failed processing ${queuedPage.url.get}: ${e.getMessage}")
                e.printStackTrace()
                self ! ProcessNextPage
            }
          })
        case false => logger.info(s"$logInstanceIdentifier Queue is empty.")
      }

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
}
