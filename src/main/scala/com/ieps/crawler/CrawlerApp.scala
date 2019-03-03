package com.ieps.crawler

import com.ieps.crawler.db.{EntityOperations, Tables}
import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object CrawlerApp extends App with StrictLogging {
//  val db = Database.forConfig("slick.postgres.local")
  val db = Database.forConfig("local")
  try {
    // list entities
    val resultFuture = db.run(Tables.PageType.result)
    val result = Await.result(resultFuture, Duration.Inf).toList
    result.foreach(res => logger.info(s"$res"))

//    val ins1: DBIO[Int] = entityCollection.page += (1L, 1L, "HTML", "google.com/search", "<html>", 200, new DateTime())
//    val ins2: DBIO[Int] = entityCollection.page += (2L, 1L, "HTML", "google.com/search/yes", "<html>", 200, new DateTime())
//    val setup = DBIO.seq(ins1, ins2)

//    val insSite: DBIO[Int] = entityCollection.site += (3L, "google.com/yes")
//    val setup = DBIO.seq(insSite)
//    val setupFuture = db.run(setup)
//    val result = Await.result(setupFuture, Duration.Inf)
//    logger.info(s"$result")

  } catch {
    case e: Exception =>
      logger.error(e.getMessage)
      e.printStackTrace()
  } finally db.close()
}
