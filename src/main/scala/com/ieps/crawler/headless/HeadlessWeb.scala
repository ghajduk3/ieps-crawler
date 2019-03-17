package com.ieps.crawler.headless

import java.net.{HttpURLConnection, URL}
import java.util.logging.Level

import com.typesafe.scalalogging.StrictLogging
import io.github.bonigarcia.wdm.WebDriverManager
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.logging.LogEntries
import org.openqa.selenium.logging.LogEntry
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.logging.LoggingPreferences
import org.openqa.selenium.remote.CapabilityType
import org.openqa.selenium.remote.DesiredCapabilities


class HeadlessWeb(debug: Boolean = true) extends StrictLogging{

  WebDriverManager.chromedriver().setup()
  val driverOptions: ChromeOptions = new ChromeOptions
  driverOptions.addArguments("--headless")
  val capabilities: DesiredCapabilities = DesiredCapabilities.chrome()
  capabilities.setCapability(ChromeOptions.CAPABILITY, driverOptions)
  val loggingPreferences: LoggingPreferences = new LoggingPreferences()
  loggingPreferences.enable(LogType.PERFORMANCE, Level.ALL)
  capabilities.setCapability(CapabilityType.LOGGING_PREFS, loggingPreferences)
  val driver: WebDriver = new ChromeDriver(capabilities)

  def getPageSource(url: String): (Int, Option[String]) = {
    val timeStart = System.nanoTime()
    val statusCode: Int = getHTTPStatus(url)
    if(200 <= statusCode && statusCode < 400) {
      driver.get(url)
      val htmlContent = driver.getPageSource
      val timeNeeded = (System.nanoTime() - timeStart) / 1e9
      if (debug) logger.info(s"Time needed for $url = $timeNeeded")
      (statusCode, Some(htmlContent))
    } else {
      (statusCode, None)
    }
  }

  def getHTTPStatus(url: String): Int = {
    try {
      HttpURLConnection.setFollowRedirects(true)
      val connection: HttpURLConnection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("HEAD")
      connection.getResponseCode
    } catch {
      case e: Exception =>
        logger.error(s"An error occurred: ${e.toString}")
        500 // whenever there is an error -> not my fault
    }
  }
}
