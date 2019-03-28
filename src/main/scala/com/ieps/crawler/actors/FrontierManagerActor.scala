package com.ieps.crawler.actors

import akka.actor.{Actor, ActorRef}
import akka.event.LoggingReceive
import com.ieps.crawler.db.DBService
import com.ieps.crawler.db.Tables.{PageRow, SiteRow}
import com.ieps.crawler.queue.Queue.QueuePageEntry
import com.ieps.crawler.queue.{DataQueue, PageQueue}
import com.ieps.crawler.utils._
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api.Database

import scala.collection.mutable.{Map => MutableMap}
import scala.util.{Success, Try}

object FrontierManagerActor {
  case class InitializeFrontier(initialUrls: List[String])
  case class AddLinksToFrontier(links: List[QueuePageEntry])
  case object NewDomainRequest
}

class FrontierManagerActor(
   val pageQueue: PageQueue,
   val dataQueue: DataQueue,
   val db: Database,
 ) extends Actor
   with StrictLogging {
  import FrontierManagerActor._
  import DomainWorkerActor._

  private val dbService = new DBService(db)
  private val browser = new HeadlessBrowser()

  private val sites: MutableMap[String, SiteRow] = MutableMap.empty
  private val sitesQueue: MutableMap[SiteRow, (ActorRef, List[PageRow])] = MutableMap.empty

  override def receive: Receive = LoggingReceive {
    case InitializeFrontier(initialUrls) =>
      initialUrls.foreach(url => logger.info(s"$url"))
      // TODO: spawn actors
    case AddLinksToFrontier(links) => // TODO
    case NewDomainRequest => // TODO
  }

  def inferSite(url: String): (SiteRow, List[PageRow]) = { // TODO: test
    sites.contains(url) match {
      case true =>
        (sites(url), sitesQueue.getOrElse(sites(url), (null, List.empty))._2)
      case false =>
        dbService.getSiteByDomain(Canonical.extractDomain(url)) match {
          case Some(site) => (site, List.empty)
          case None =>
            val domain = Canonical.extractDomain(url)
            var site = SiteRow(-1, Some(domain))

            val sitemapUrls: Option[List[PageRow]] = browser.getRobotsTxt(Canonical.getCanonical(domain)).map(content => {
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
                      SiteMaps.getSiteMapUrls(url, site)
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
}
