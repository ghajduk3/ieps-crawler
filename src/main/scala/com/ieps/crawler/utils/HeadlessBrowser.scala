package com.ieps.crawler.utils

import java.net.{HttpURLConnection, MalformedURLException, URL, UnknownHostException}
import java.util.logging.Level

import com.gargoylesoftware.htmlunit._
import com.typesafe.scalalogging.StrictLogging
/*

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
*/


class HeadlessBrowser(debug: Boolean = true) extends StrictLogging{

  /* // selenium stuff
  WebDriverManager.chromedriver().setup()
  val driverOptions: ChromeOptions = new ChromeOptions
  driverOptions.addArguments("--headless")
  val capabilities: DesiredCapabilities = DesiredCapabilities.chrome()
  capabilities.setCapability(ChromeOptions.CAPABILITY, driverOptions)
  val loggingPreferences: LoggingPreferences = new LoggingPreferences()
  loggingPreferences.enable(LogType.PERFORMANCE, Level.ALL)
  capabilities.setCapability(CapabilityType.LOGGING_PREFS, loggingPreferences)
  val driver: WebDriver = new ChromeDriver(capabilities)
  */

  import org.apache.commons.logging.LogFactory

  LogFactory.getFactory.setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog")
  java.util.logging.Logger.getLogger("net.sourceforge.htmlunit").setLevel(Level.INFO)
  java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.INFO)
  val webClient: WebClient = new WebClient()
  webClient.getOptions.setCssEnabled(false)
  webClient.getOptions.setThrowExceptionOnScriptError(false)
  webClient.getOptions.setTimeout(5000) // 5 s timeout

  /**
    * Gets a web page with a given `url`
    *
    * @param url: String
    * @return (statusCode: Option[Int], loadTime: Option[Long], contentType: Option[String], content: Option[String]
    */
  def getPageSource(url: String): (Int, Option[Long], Option[String], Option[String]) = {
    try {
      val response: Page = webClient.getPage(url)
      val webResponse: WebResponse = response.getWebResponse
      val statusCode = webResponse.getStatusCode
      val htmlContent = webResponse.getContentAsString
      val htmlContentType = webResponse.getContentType
      val loadTime = webResponse.getLoadTime

      (statusCode, Some(loadTime), Some(htmlContentType), Some(htmlContent))
    } catch {
      case e: FailingHttpStatusCodeException =>
        (e.getStatusCode, None, None, None)
      case _: UnknownHostException =>
        (404, None, None, None)
      case _: MalformedURLException=>
        (400, None, None, None)
    }
  }

  def getHTTPStatus(url: String): Int = {
    try {
      HttpURLConnection.setFollowRedirects(true)
      val connection: HttpURLConnection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      connection.getResponseCode
    } catch {
      case e: Exception =>
        logger.error(s"An error occurred: ${e.toString}")
        500 // whenever there is an error -> not my fault
    }
  }
}
