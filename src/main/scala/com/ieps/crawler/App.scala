package com.ieps.crawler

import com.typesafe.scalalogging.StrictLogging


object App extends StrictLogging {
  def main(args: Array[String]): Unit = {
    logger.info("Hello World!")
    val pojo: JavaExperiment = new JavaExperiment(1,2)
    logger.info(s"This is the pojo: $pojo")
    logger.info(s"This is a major change")


    //    Reading /robots.txt
    val visitingPageURL = "https://www.pornhub.com"
    val robotsTxtParser: RobotsParser = new RobotsParser(visitingPageURL)
    val siteMapParser: CrawlerSiteMap = new CrawlerSiteMap(visitingPageURL)


    //    val content = robotsTxtParser.robotsTxtContent()
    //    logger.info(s"This is the content: \n$content")

    val siteMapUrls = robotsTxtParser.getSiteMapUrls()
    logger.info(s"This is the siteMap link: \n$siteMapUrls")

    val crawlerSM = siteMapParser.CrawlerSMUrls()
    logger.info(s"This is the siteMapURLs: \n$crawlerSM")

    //    val agents = robotsTxtParser.allUserAgents()
    //    logger.info(s"This is the list with all User-agents: \n$agents")

    //    val listDisallowed = robotsTxtParser.getDisallowList("*");
    //    logger.info(s"This is the list with all disallowed resources for crawler *: \n$listDisallowed")

  }
}
