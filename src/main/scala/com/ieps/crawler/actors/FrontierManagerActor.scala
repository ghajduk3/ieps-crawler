package com.ieps.crawler.actors

import akka.actor.{Actor, ActorRef, Kill, Props}
import akka.event.LoggingReceive
import com.ieps.crawler.CrawlerApp.{actorSystem, dbConnection, pageQueue}
import com.ieps.crawler.db.DBService
import com.ieps.crawler.db.Tables.{PageRow, SiteRow}
import com.ieps.crawler.queue.Queue.QueuePageEntry
import com.ieps.crawler.queue.{DataQueue, PageQueue}
import com.ieps.crawler.utils._
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api.Database

import scala.collection.mutable
import scala.collection.mutable.{Map => MutableMap}
import scala.util.{Success, Try}

object FrontierManagerActor {

  def props(db: Database, pageQueue: PageQueue): Props = Props(new FrontierManagerActor(db, pageQueue))


  case class InitializeFrontier(initialUrls: List[String])
  case class AddLinksToFrontier(links: List[QueuePageEntry])
  case class NewDomainRequest(currentSite: SiteRow)
}

class FrontierManagerActor(
   val db: Database,
   val pageQueue: PageQueue,
   val maxWorkers: Int = 4
//   val dataQueue: DataQueue,
 ) extends Actor
   with StrictLogging {
  import FrontierManagerActor._
  import DomainWorkerActor._

  private val dbService = new DBService(db)
  private val browser = new HeadlessBrowser()

  private val sitesQueue: MutableMap[String, (SiteRow, List[QueuePageEntry])] = MutableMap.empty
  private val domainWorkers: MutableMap[String, Option[ActorRef]] = MutableMap.empty
  private val workers: mutable.MutableList[ActorRef] = mutable.MutableList.empty

  override def receive: Receive = LoggingReceive {
    case Kill =>
      context.stop(self)

    case InitializeFrontier(initialUrls) =>
      initialUrls.foreach(url => {
        logger.info(s"$url")
        val domain = Canonical.extractDomain(url)
        val (site, urls) = sitesQueue.get(domain) match {
          case Some((siteEntry, urlEntries)) =>
            (siteEntry, urlEntries)
          case None =>
            val (site, urls) = inferSite(url)
            sitesQueue(domain) = (site, urls)
            sitesQueue(domain)
        }

        logger.info(s"site = ${site.domain}")
        logger.info(s"${urls.size}")

        val currentWorker: Option[ActorRef] = domainWorkers.get(domain) match {
          case Some(worker) =>
            worker
          case None =>
            if (domainWorkers.size < maxWorkers) {
              logger.info(s"Creating new Worker")
              val worker: ActorRef = context.actorOf(
                DomainWorkerActor.props(s"${sitesQueue.size + 1}", db, pageQueue).withDispatcher("thread-pool-dispatcher"),
                s"Worker_${sitesQueue.size + 1}"
              )
              domainWorkers(domain) = Some(worker)
              workers += worker
              Some(worker)
            } else {
              logger.info(s"Maximum number of workers achieved, waiting the first to free")
              domainWorkers(domain) = None
              None
            }
        }

        currentWorker.foreach(worker => {
          worker ! ProcessDomain(site, urls)
        })
      })

    case AddLinksToFrontier(links) => // TODO

    case NewDomainRequest(currentSite) => // TODO
      currentSite.domain.foreach(currentDomain => {
        domainWorkers.get(currentDomain) match {
          case Some(Some(worker)) =>
            domainWorkers.remove(currentDomain)
            getWorkerlessDomain.foreach( newDomain => {
              sitesQueue.get(newDomain) match {
                case Some((site, urls)) =>
                  worker ! ProcessDomain(site, urls)
                case _ =>
                  logger.warn("[new domain] No free domains currently.")
              }
            })
          case Some(None) =>
            logger.error(s"[new domain] WTF? no worker??")
          case None =>
            logger.error(s"[new domain] WTF? no entry for $currentDomain")
        }
      })

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
              if (robotsTxt.getSitemaps.size > 1) {
                logger.warn(s"multiple sitemaps?? ${robotsTxt.getSitemaps}")
              }
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

  def getWorkerlessDomain: Option[String] = {
    domainWorkers.toList.find(_._2.isEmpty) match {
      case Some((domain, None)) =>
        Some(domain)
      case _ => None
    }
  }
}
