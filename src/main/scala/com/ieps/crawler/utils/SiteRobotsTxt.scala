package com.ieps.crawler.utils

import com.ieps.crawler.db.Tables.{PageRow, SiteRow}
import com.typesafe.scalalogging.StrictLogging
import crawlercommons.robots.{SimpleRobotRules, SimpleRobotRulesParser}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.postfixOps

class SiteRobotsTxt(
  val site: SiteRow,
  val contentType: String = "text/plain",
  val robotNames: String = "*"
) extends StrictLogging {
  private val robotsRulesParser: SimpleRobotRulesParser = new SimpleRobotRulesParser()
  private val robotRules: Option[SimpleRobotRules] = site.robotsContent.map(robotsContent => robotsRulesParser.parseContent(Canonical.getCanonical(site.domain.get).getOrElse(site.domain.get).concat("robots.txt"), robotsContent.getBytes(), contentType, robotNames))
  private val defaultCrawlDelay = (4 seconds).toMillis

  def getRobotRules: Option[List[SimpleRobotRules.RobotRule]] = robotRules.map(rules => rules.getRobotRules.asScala.toList)

  def getDelay: Long = robotRules match {
    case Some(robots) =>
      if (robots.getCrawlDelay == Long.MinValue) {
        defaultCrawlDelay
      } else {
        robots.getCrawlDelay
      }
    case None => defaultCrawlDelay // default delay is 4s
  }

  def getSitemaps: Option[List[String]] = robotRules.map(robotRules => robotRules.getSitemaps.asScala.toList)

  def isAllowed(pageRow: PageRow): Boolean = {
    if(pageRow.url.isDefined)
      robotRules match {
        case Some(robots) => robots.isAllowed(pageRow.url.get)
        case None => true // allow all by default
      }
    else false
  }

  def pageBelongs(pageRow: PageRow): Boolean = {
    if(pageRow.url.isDefined) {
      val url = pageRow.url.get
      site.domain match {
        case Some(domain) => Canonical.extractDomain(url) == domain
        case None => false
      }
    } else false
  }
}
