package com.ieps.crawler.actors

import akka.actor.{ActorSystem, Terminated}
import akka.dispatch.PriorityGenerator
import com.ieps.crawler.actors.DomainWorkerActor._
import com.ieps.crawler.actors.FrontierManagerActor._

final class CrawlerBoundedPriorityMailbox(
  settings: ActorSystem.Settings, config: com.typesafe.config.Config
) extends BoundedPriorityMailbox(
  settings = settings,
  config = config,
  // Create a new PriorityGenerator, lower priority means more important
  cmp = PriorityGenerator {
    case Terminated => 0

    case StatusRequest | StatusResponse | ProcessDomain | NewDomainRequest| CheckIdleWorkers | StatusReport => 1

    case AddLinksToLocalQueue | AddLinksToFrontier=> 2

    case otherwise => 3
  })