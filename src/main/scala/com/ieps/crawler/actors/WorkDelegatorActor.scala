package com.ieps.crawler.actors

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive
import com.ieps.crawler.db.Tables._
import com.typesafe.scalalogging.StrictLogging

object WorkDelegatorActor {
  def props(frontier: Object): Props = Props(new WorkDelegatorActor(frontier))

  case object GetNextURL
  case class ProcessNextPage(pageRow: PageRow, siteRow: SiteRow) // TODO: add wait duration
  case class InsertPage(url: String)
}

class WorkDelegatorActor(
    frontier: Object
  ) extends Actor
    with StrictLogging {
  import WorkDelegatorActor._
  val logInstanceIdentifier = s"WorkDelegator:"


  override def receive: Receive = LoggingReceive {

    case GetNextURL =>
      sender() ! getNextURL

    case InsertPage(url) =>
      if(isPageDuplicate(url)) {
        // do nothing
      } else {
        // insert it into the frontier
      }

    case any: Any => logger.error(s"$logInstanceIdentifier Got an unknown message: $any")
  }

  def isPageDuplicate(url: String): Boolean = {
    // TODO: implement
    false
  }

  val getNextURL: ProcessNextPage = {
    // TODO: implement a BFS strategy for obtaining the next URL
    null
  }
}
