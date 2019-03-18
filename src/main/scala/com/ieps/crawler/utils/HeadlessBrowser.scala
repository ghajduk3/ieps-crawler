package com.ieps.crawler.utils

import java.io.InputStream
import java.net.{HttpURLConnection, MalformedURLException, URL, UnknownHostException}
import java.util.logging.Level

import com.gargoylesoftware.htmlunit._
import com.ieps.crawler.db.Tables.{PageDataRow, PageRow}
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
//  def getPageSource(pageRow: PageRow): (Int, Option[Long], Option[String], Option[String]) = {
  def getPageSource(pageRow: PageRow): PageRow = {
    try {
      val response: Page = webClient.getPage(pageRow.url.get)
      val webResponse: WebResponse = response.getWebResponse
      val statusCode = webResponse.getStatusCode
      val htmlContent = webResponse.getContentAsString
      val htmlContentType = webResponse.getContentType
      val loadTime = webResponse.getLoadTime // TODO

//      (statusCode, Some(loadTime), Some(htmlContentType), Some(htmlContent))
      pageRow.copy(httpStatusCode = Some(statusCode), htmlContent = Some(htmlContent), pageTypeCode = Some(htmlContentType))
    } catch {
      case e: FailingHttpStatusCodeException =>
        pageRow.copy(httpStatusCode = Some(e.getStatusCode))
      case _: UnknownHostException =>
        pageRow.copy(httpStatusCode = Some(404))
      case _ @(_: MalformedURLException| _: Exception) =>
        pageRow.copy(httpStatusCode= Some(400))
    }
  }

  private def toByteArray(inputStream: InputStream): Array[Byte] = {
    Stream.continually(inputStream.read).takeWhile(_ != -1).map(_.toByte).toArray
  }

  def getPageData(pageRow: PageRow): PageDataRow = {
    val pageDataRow = PageDataRow(-1, Some(pageRow.id))
    try {
      val response: Page = webClient.getPage(pageRow.url.get)
      val webResponse: WebResponse = response.getWebResponse
      val contentType = webResponse.getContentType
      val contentData = toByteArray(webResponse.getContentAsStream)
      pageDataRow.copy(dataTypeCode = Some(contentType), data = Some(contentData))
    } catch {
      case e: FailingHttpStatusCodeException =>
        null
      case _: UnknownHostException =>
        null
      case _ @(_: MalformedURLException| _: Exception) =>
        null
    }
  }

  def getPageData(pageRows: List[PageRow]): List[PageDataRow] = {
    pageRows.map(getPageData).filter(_ != null)
  }
}
