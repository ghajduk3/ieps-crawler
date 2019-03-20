package com.ieps.crawler.utils

import java.io.InputStream
import java.net.{MalformedURLException, UnknownHostException}
import java.util.logging.Level

import com.gargoylesoftware.htmlunit._
import com.ieps.crawler.db.Tables.{ImageRow, PageDataRow, PageRow}
import com.typesafe.scalalogging.StrictLogging

class HeadlessBrowser(debug: Boolean = true) extends StrictLogging{

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
  def getPageSource(pageRow: PageRow): PageRow = {
    try {
      val response: Page = webClient.getPage(pageRow.url.get)
      val webResponse: WebResponse = response.getWebResponse
      val statusCode = webResponse.getStatusCode
      val htmlContent = webResponse.getContentAsString
      val htmlContentType = webResponse.getContentType
      val loadTime = webResponse.getLoadTime
      pageRow.copy(httpStatusCode = Some(statusCode), htmlContent = Some(htmlContent), pageTypeCode = Some(htmlContentType), loadTime = Some(loadTime))
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

  private def getData(url: String): (String, Array[Byte]) = {
    try {
      val response: Page = webClient.getPage(url)
      val webResponse: WebResponse = response.getWebResponse
      val contentType = webResponse.getContentType
      val contentData = toByteArray(webResponse.getContentAsStream)
      (contentType, contentData)
    } catch {
      case e: FailingHttpStatusCodeException =>
        (null, null)
      case _: UnknownHostException =>
        (null, null)
      case _ @(_: MalformedURLException| _: Exception) =>
        (null, null)
    }
  }

  private def getPageData(pageRow: PageRow): PageDataRow = {
    if(pageRow.url.isDefined) {
      val (contentType, contentData) = getData(pageRow.url.get)
      PageDataRow(-1, Some(pageRow.id), dataTypeCode = Some(contentType), data = Some(contentData))
    } else null
  }

  def getPageData(pageRows: List[PageRow]): List[PageDataRow] = {
    pageRows.map(getPageData).filter(_ != null)
  }

  private def getImageData(imageRow: ImageRow): ImageRow = {
    if(imageRow.filename.isDefined) {
      val (contentType, contentData) = getData(imageRow.filename.get)
      imageRow.copy(contentType = Some(contentType), data = Some(contentData))
    } else null
  }

  def getImageData(imageRows: List[ImageRow]): List[ImageRow] = {
    imageRows.map(getImageData).filter(_ != null)
  }
}
