package com.ieps.crawler

import com.typesafe.scalalogging.StrictLogging

object App extends StrictLogging {
  def main(args: Array[String]): Unit = {
    logger.info("Hello World!")
    val pojo: JavaExperiment = new JavaExperiment(1,2)
    logger.info(s"This is the pojo: $pojo")
    logger.info(s"This is a major change")

    // TODO: initialize the frontier
    
  }
}
