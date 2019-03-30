package com.ieps.crawler.actors

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import com.ieps.crawler.db.DBService
import com.ieps.crawler.db.Tables.{PageRow, SiteRow}
import com.ieps.crawler.queue.Queue.{QueueDataEntry, QueuePageEntry}
import com.ieps.crawler.queue.{DataQueue, PageQueue}
import com.ieps.crawler.utils.HeadlessBrowser.FailedAttempt
import com.ieps.crawler.utils._
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api.Database

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.{Failure, Random, Success, Try}

object PageWorkerActor {
  def props(workerId: String, db: Database, pageQueue: PageQueue, dataQueue: DataQueue) = Props(new PageWorkerActor(workerId, db, pageQueue, dataQueue))

  case object StartWorker

  case object ProcessPage

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
  private val rand: Random.type = Random

  private val logInstanceIdentifier = s"CrawlerWorker_$workerId:"
  private val browser = new HeadlessBrowser()
  private val dbService = new DBService(db)
  private val duplicate = new DuplicateLinks(db)

  override def receive: Receive = LoggingReceive {

    case StartWorker | ProcessPage =>
      pageQueue.hasMorePages match {
        case true =>
          val processed = pageQueue.dequeue().map(queueEntry => {
            val queuedPage = queueEntry.pageInQueue
            val referencePage = queueEntry.referencePage
            try {
              inferSite(queuedPage).map(site => {
                val robots = new SiteRobotsTxt(site) // TODO: fix if not present with default values
                robots.isAllowed(queuedPage) match {
                  case true => {
                    if (!duplicate.isDuplicatePage(queuedPage)) {
                      Await.ready(browser.getPageSource(queuedPage), timeout).value.get match {
                        case Success(pageWithContent: PageRow) =>
                          logger.info(s"$logInstanceIdentifier Processing: ${queuedPage.url.get}")
                          Utils.retryWithBackoff(logRetry = true) {
                            val insertedPage: PageRow = dbService.insertIfNotExists(pageWithContent.copy(siteId = Some(site.id)))
                            referencePage.foreach(fromPage => dbService.linkPages(fromPage, insertedPage))
                            val httpStatusCode = insertedPage.httpStatusCode.get

                            if (200 <= httpStatusCode && httpStatusCode < 400) {
                              val extractor = new ExtractFromHTML(insertedPage, site)
                              // enqueue the extracted links // TODO: add time waited
                              extractor.getPageLinks.map(duplicate.deduplicatePages(_).map(link => QueuePageEntry(link, 0, Some(insertedPage)))).foreach(pageQueue.enqueueAll)
                              // enqueue the page data
                              extractor.getPageData.map(_.map(data => QueueDataEntry(isData = false, insertedPage.id, data.url.get))).foreach(dataQueue.enqueueAll)
                              // enqueue the images
                              extractor.getImages.map(_.map(image => QueueDataEntry(isData = true, insertedPage.id, image.url.get))).foreach(dataQueue.enqueueAll)
                            } //else logger.warn(s"Got status code $httpStatusCode")
                          }
                        case Failure(exception) => exception match {
                          case FailedAttempt(message, cause, failedPage: PageRow) =>
                            Utils.retryWithBackoff(logRetry = true) {
                              dbService.insertIfNotExists(failedPage.copy(siteId = Some(site.id)))
                            }
                        }
                          //logger.error(s"$logInstanceIdentifier Error processing ${queuedPage.url.get}: ${exception.getMessage}")
                      }
                      val delay = robots.getDelay + ((2 + rand.nextInt(20)) seconds).toMillis // adding jitter to be more crawl-friendly
                      logger.info(s"$logInstanceIdentifier Waiting for ${delay / 1000L}s")
                      context.system.scheduler.scheduleOnce(FiniteDuration(delay, MILLISECONDS), self, ProcessPage)
                    } else {
                      logger.warn(s"$logInstanceIdentifier Duplicate page: ${queuedPage.url.get}")
                      self ! ProcessPage
                    }
                  }
                  case false =>
                    dbService.insertIfNotExists(queuedPage.copy(siteId = Some(site.id), pageTypeCode = Some("DISALLOWED")))
                    logger.warn(s"$logInstanceIdentifier Site not allowed: ${queuedPage.url.get}")
                    self ! ProcessPage
                }
                true
              })
            } catch {
              case e: Exception =>
                logger.error(s"$logInstanceIdentifier Failed processing ${queuedPage.url.get}: ${e.getMessage}")
                e.printStackTrace()
                self ! ProcessPage
            }
            true
          })
          if (processed.isEmpty) self ! ProcessPage
        case false =>
          logger.info(s"$logInstanceIdentifier Queue is empty. Retrying in 10s")
          context.system.scheduler.scheduleOnce(FiniteDuration(10, SECONDS), self, ProcessPage)
      }

    case any: Any => logger.error(s"$logInstanceIdentifier Unknown message: $any")
  }

  def inferSite(page: PageRow): Option[SiteRow] = {
    dbService.getSiteByDomain(Canonical.extractDomain(page.url.get)) match {
      case Some(site) => Some(site)
      case None =>
        val domain = Some(Canonical.extractDomain(page.url.get))
        logger.info(s"$logInstanceIdentifier domain = $domain")
        var site = SiteRow(-1, domain)
        val canonicalDomain = Canonical.getCanonical(site.domain.get)
        if(canonicalDomain.isDefined) {
          browser.getRobotsTxt(canonicalDomain.get).foreach(content => {
            site = site.copy(robotsContent = Some(content))
            val robotsTxt = new SiteRobotsTxt(site)
            if (robotsTxt.getSitemaps.size > 1) {
              logger.warn(s"$logInstanceIdentifier multiple sitemaps?? ${robotsTxt.getSitemaps}")
            }
            robotsTxt.getSitemaps.foreach(sitemap => sitemap.foreach(url => {
              Try(browser.getUrlContent(url)) match {
                case Success(Some(content: String)) =>
                  site = site.copy(sitemapContent = Some(content))
                  val siteMapUrls = duplicate.deduplicatePages(SiteMaps.getSiteMapUrls(url, site)).map(page => QueuePageEntry(page))
                  pageQueue.enqueueAll(siteMapUrls)
                case _ =>
                  logger.info(s"$logInstanceIdentifier something is wrong?")
              }
            }))
          })
        }
        Utils.retryWithBackoff {
          Some(dbService.insertIfNotExistsByDomain(site))
        }

    }
  }
}
