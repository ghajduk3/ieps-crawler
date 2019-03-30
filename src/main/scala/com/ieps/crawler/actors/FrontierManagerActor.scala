package com.ieps.crawler.actors

import akka.actor.{Actor, ActorRef, Kill, Props}
import akka.event.LoggingReceive
import com.ieps.crawler.db.DBService
import com.ieps.crawler.db.Tables.SiteRow
import com.ieps.crawler.queue.PageQueue
import com.ieps.crawler.queue.Queue.QueuePageEntry
import com.ieps.crawler.utils._
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api.Database

import scala.collection.{immutable, mutable}
import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.{Success, Try}
import scala.util.Random

object FrontierManagerActor {

  def props(db: Database, pageQueue: PageQueue): Props = Props(new FrontierManagerActor(db, pageQueue))

  case class InitializeFrontier(initialUrls: List[String])
  case class AddLinksToFrontier(links: List[QueuePageEntry])
  case class NewDomainRequest(currentSite: SiteRow)
  case object NewDomainRequest
  case object CheckIdleWorkers
}

class FrontierManagerActor(
   val db: Database,
   val pageQueue: PageQueue,
   val maxWorkers: Int = 8
//   val dataQueue: DataQueue,
 ) extends Actor
   with StrictLogging {
  import DomainWorkerActor._
  import FrontierManagerActor._

  private val dbService = new DBService(db)
  private val browser = new HeadlessBrowser()
  private implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup("thread-pool-dispatcher")

  private val sitesQueue: MutableMap[String, (SiteRow, List[QueuePageEntry])] = MutableMap.empty
  private val domainWorkers: MutableMap[String, Option[ActorRef]] = MutableMap.empty
  private val workers: mutable.MutableList[ActorRef] = mutable.MutableList.empty

  override def receive: Receive = LoggingReceive {
    case Kill =>
      context.stop(self)

    case InitializeFrontier(initialUrls) =>
      initialUrls.foreach(url => {
        logger.info(s"Initializing $url")
        val domain = Canonical.extractDomain(url)
        val (site, urls) = sitesQueue.get(domain) match {
          case Some((siteEntry, urlEntries)) =>
            (siteEntry, urlEntries)
          case None =>
            val (site, urls) = inferSite(url)
            sitesQueue(domain) = (site, urls)
            sitesQueue(domain)
        }

        val currentWorker: Option[ActorRef] = domainWorkers.get(domain) match {
          case Some(worker) =>
            worker
          case None =>
            spawnNewWorker(domain)
        }

        currentWorker.foreach(worker => {
          worker ! ProcessDomain(site, urls)
        })
      })

    case AddLinksToFrontier(links) => // TODO
//      logger.info(s"Adding new links to frontier: ${links.size}")
      links.groupBy(link => inferSite(link.pageInQueue.url.get)._1).foreach {
        case (site, list) =>
          domainWorkers.get(site.domain.get) match {
            case Some(Some(worker)) =>
              worker ! AddLinksToLocalQueue(list)
            case Some(None) =>
//              logger.info(s"Adding to workerless domain ${list.size}")
              val (_, queue) = sitesQueue(site.domain.get)
              sitesQueue(site.domain.get) = (site, queue ++ list)
            case None =>
              sitesQueue(site.domain.get) = (site, list)
              spawnNewWorker(site.domain.get) match {
                case Some(worker) =>
                  worker ! ProcessDomain(site, list)
                case None =>
//                  logger.info("No available workers.")
              }
          }
      }

    case NewDomainRequest(currentSite) =>
      logger.info(s"New domain request, done ${currentSite.domain.get}")
      currentSite.domain.foreach(currentDomain => {
        domainWorkers.get(currentDomain) match {
          case Some(Some(worker)) =>
            getWorkerlessDomain(currentDomain).foreach {
              case (newDomain, None) =>
                sitesQueue.get(newDomain) match {
                  case Some((site, urls)) =>
                    domainWorkers(newDomain) = Some(worker)
                    domainWorkers(currentDomain) = None
                    worker ! ProcessDomain(site, urls)
                  case _ =>
                    logger.warn("[new domain] No free domains currently.")
                }
              case any: Any =>
                logger.error(s"[new domain] WTF? worker is defined??? $any")
            }
          case Some(None) =>
            logger.error(s"[new domain] WTF? no worker??")
          case None =>
            logger.error(s"[new domain] WTF? no entry for $currentDomain")
        }
      })
    case any: Any => logger.warn(s"Got unknown message: $any")
  }

  def inferSite(url: String): (SiteRow, List[QueuePageEntry]) = { // TODO: test
    val domain = Canonical.extractDomain(url)
    sitesQueue.contains(domain) match {
      case true =>
        sitesQueue(domain)
      case false =>
        dbService.getSiteByDomain(domain) match {
          case Some(site) =>
            (site, List.empty)
          case None =>
            var site = SiteRow(-1, Some(domain))
            val sitemapUrls: Option[List[QueuePageEntry]] = browser.getRobotsTxt(Canonical.getCanonical(domain)).map(content => {
              site = site.copy(robotsContent = Some(content))
              val robotsTxt = new SiteRobotsTxt(site)
              robotsTxt.getSitemaps match {
                case Some(siteMaps) => siteMaps.flatMap(url => {
                  Try(browser.getUrlContent(url)) match {
                    case Success(Some(content: String)) =>
                      site = site.copy(sitemapContent = Some(content))
                      SiteMaps.getSiteMapUrls(url, site).map(QueuePageEntry(_))
                    case _ => List.empty
                  }
                })
                case None => List.empty
              }
            })

            (dbService.insertIfNotExistsByDomain(site), sitemapUrls match {
                case Some(urls) => urls
                case None => List.empty
            })
        }
    }
  }

  def getWorkerlessDomain(filterDomain: String): Option[(String, Option[ActorRef])] = {
    val filtered: immutable.Seq[(String, Option[ActorRef])] = domainWorkers.toList.filter(entry => entry._1 != filterDomain && entry._2.isEmpty)
    Random.shuffle(filtered).headOption
  }

  def spawnNewWorker(domain: String): Option[ActorRef] = {
    if(!domainWorkers.contains(domain)) {
      if (domainWorkers.size < maxWorkers) {
        logger.info(s"Creating new Worker")
        val worker: ActorRef = context.actorOf(
          DomainWorkerActor.props(s"${sitesQueue.size}", db).withDispatcher("thread-pool-dispatcher"),
          s"Worker_${sitesQueue.size}"
        )
        domainWorkers(domain) = Some(worker)
        workers += worker
        Some(worker)
      } else {
//        logger.info(s"Maximum number of workers achieved, waiting the first to free")
        domainWorkers(domain) = None
        None
      }
    } else {
      domainWorkers(domain)
    }
  }
}
