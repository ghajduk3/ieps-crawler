package com.ieps.crawler.utils

import com.ieps.crawler.db.Tables.{PageRow, SiteRow}
import com.typesafe.scalalogging.StrictLogging
import crawlercommons.robots.{SimpleRobotRules, SimpleRobotRulesParser}

import scala.collection.JavaConverters._

class SiteRobotsTxt(
  val site: SiteRow,
  val contentType: String = "text/plain",
  val robotNames: String = "*"
) extends StrictLogging {
  private val robotsRulesParser: SimpleRobotRulesParser = new SimpleRobotRulesParser()
  private val robotRules: SimpleRobotRules = robotsRulesParser.parseContent(site.domain.get, site.robotsContent.get.getBytes, contentType, robotNames)

  def getRobotRules: List[SimpleRobotRules.RobotRule] = robotRules.getRobotRules.asScala.toList

  def isAllowed(pageRow: PageRow): Boolean = {
    if (pageRow.url.isDefined) robotRules.isAllowed(pageRow.url.get)
    else false
  }
}
