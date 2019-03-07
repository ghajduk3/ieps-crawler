package com.ieps.crawler.actors

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import com.typesafe.scalalogging.StrictLogging

object CrawlerWorkerActor {
  def props(workerId: String) = Props(new CrawlerWorkerActor(workerId))
}

class CrawlerWorkerActor(
    workerId: String
  ) extends Actor
    with StrictLogging {
  import CrawlerWorkerActor._
  import WorkDelegatorActor._
  val logInstanceIdentifier = s"CrawlerWorker_$workerId:"


  override def receive: Receive = LoggingReceive {
    case ProcessNextPage(url) =>
      logger.info(s"$logInstanceIdentifier Got a new url to process: $url")

    case any: Any => logger.error(s"$logInstanceIdentifier Unknown message: $any")
  }

  def processUrl(url: String): Unit = {
    // TODO: Process the url:
    //  1. extract the HTML code via Selenium
    //  2. extract:
    //      - urls (<a>, location) - add them into the frontier
    //      - images (<img>)
    //      - other binary content
    //  3. store content into the DB
    //  4. request next url
  }
}
