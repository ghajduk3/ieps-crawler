package com.ieps.crawler

import com.typesafe.scalalogging.StrictLogging

object App extends StrictLogging {
  def main(args: Array[String]): Unit = {
    logger.info("Hello World!")
    val pojo: JavaExperiment = new JavaExperiment(1,2)


    //    Reading /robots.txt
    val visitingPageURL = "https://github.com"
    val robotsTxtParser: RobotsParser = new RobotsParser(visitingPageURL)

    val content = robotsTxtParser.robotsTxtContent()
    logger.info(s"This is the content: \n$content")

    val agents = robotsTxtParser.allUserAgents()
    logger.info(s"This is the list with all User-agents: \n$agents")

    robotsTxtParser.isAllowed("*", "")


    logger.info(s"This is the pojo: $pojo")
    logger.info(s"This is a major change")
  }
}
