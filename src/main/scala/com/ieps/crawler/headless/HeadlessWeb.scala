package com.ieps.crawler.headless

import com.typesafe.scalalogging.StrictLogging
import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}

class HeadlessWeb(debug: Boolean = true) extends StrictLogging{

  WebDriverManager.chromedriver().setup()
  val driverOptions: ChromeOptions = new ChromeOptions
  driverOptions.addArguments("--headless")
  val driver: WebDriver = new ChromeDriver(driverOptions)

  def getPageSource(url: String): String = {
    val timeStart = System.nanoTime()
    driver.get(url)
    val result = driver.getPageSource
    val timeNeeded = (System.nanoTime() - timeStart)/1e9
    if (debug) logger.info(s"Time needed for $url = $timeNeeded" )
    result
  }
}
