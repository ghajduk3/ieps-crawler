package com.ieps.crawler.actors

import akka.actor.Actor
import akka.event.LoggingReceive
import com.ieps.crawler.actors.FrontierManagerActor.InitializeFrontier
import com.ieps.crawler.queue.{DataQueue, PageQueue}
import com.typesafe.scalalogging.StrictLogging

object FrontierManagerActor {
  case class InitializeFrontier(initialUrls: List[String])
}

class FrontierManagerActor(
   val pageQueue: PageQueue,
   val dataQueue: DataQueue
 ) extends Actor
   with StrictLogging {

  override def receive: Receive = LoggingReceive {
    case InitializeFrontier(initialUrls) =>
      initialUrls.foreach(url => logger.info(s"$url"))
  }
}
