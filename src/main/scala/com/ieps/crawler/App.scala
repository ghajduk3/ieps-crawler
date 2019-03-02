package com.ieps.crawler

import com.ieps.crawler.entities.Entities
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object App extends StrictLogging {
  val db = Database.forConfig("slick.postgres.local")
  val entities = new Entities("crawldb")

  def main(args: Array[String]): Unit = {
    try {
      logger.info("Getting the sites:")
      val resultFuture = db.run(entities.pageTypes.result)
      val result = Await.result(resultFuture, Duration.Inf).toList

      result.foreach(res => logger.info(s"$res"))
    } catch {
      case e: Exception =>
        logger.error(e.getMessage)
        e.printStackTrace()
    }
    db.close()
  }
}
