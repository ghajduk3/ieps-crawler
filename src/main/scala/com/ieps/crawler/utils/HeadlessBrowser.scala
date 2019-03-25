package com.ieps.crawler.utils

import java.io.InputStream
import java.net.{MalformedURLException, UnknownHostException}
import java.util.logging.Level

import com.gargoylesoftware.htmlunit._
import com.ieps.crawler.db.Tables.{ImageRow, PageDataRow, PageRow}
import com.ieps.crawler.utils.HeadlessBrowser.FailedAttempt
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future

object HeadlessBrowser {
  final case class FailedAttempt(private val message: String = "", private val cause: Throwable = None.orNull, pageRow: PageRow) extends Throwable(message, cause)
}

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
  def getPageSource(pageRow: PageRow): Future[PageRow] = {
    try {
      val response: Page = webClient.getPage(pageRow.url.get)
      val webResponse: WebResponse = response.getWebResponse
      val statusCode = webResponse.getStatusCode
      val htmlContent = webResponse.getContentAsString
      val htmlContentType = webResponse.getContentType
      val loadTime = webResponse.getLoadTime
      if (!htmlContentType.equals("text/html")) {
          throw new Exception("Invalid content type")
      }
      Future.successful(pageRow.copy(httpStatusCode = Some(statusCode), htmlContent = Some(htmlContent), pageTypeCode = Some("HTML"), loadTime = Some(loadTime)))
    } catch {
      case e: FailingHttpStatusCodeException =>
        Future.failed(FailedAttempt(e.getMessage, e.getCause, pageRow.copy(httpStatusCode = Some(e.getStatusCode))))
      case e: UnknownHostException =>
        Future.failed(FailedAttempt(e.getMessage, e.getCause, pageRow.copy(httpStatusCode = Some(404))))
      case e @(_: MalformedURLException| _: Exception) =>
        Future.failed(FailedAttempt(e.getMessage, e.getCause, pageRow.copy(httpStatusCode = Some(400))))
    }
  }

  def getUrlContent(url: String): Option[String] = {
    try {
      logger.info(s"getting $url")
      val response: Page = webClient.getPage(url)
      val webResponse: WebResponse = response.getWebResponse
      Some(webResponse.getContentAsString())
    } catch {
      case e: Exception =>
//        e.printStackTrace()
        None
    }
  }

  def getRobotsTxt(domain: String): Option[String] = {
    getUrlContent(domain.concat("robots.txt"))
  }

  private def toByteArray(inputStream: InputStream): Array[Byte] = {
    Stream.continually(inputStream.read).takeWhile(_ != -1).map(_.toByte).toArray
  }

  private def getData(url: String): Option[(Option[String], Option[Array[Byte]])] = {
    try {
      val response: Page = webClient.getPage(url)
      val webResponse: WebResponse = response.getWebResponse
      val contentType = Some(webResponse.getContentType)
      val contentData = Some(toByteArray(webResponse.getContentAsStream))
      Some((contentType, contentData))
    } catch {
      case _: Exception =>
        None
    }
  }

  private def getPageData(pageRow: PageRow): Option[PageDataRow] = {
    if(pageRow.url.isDefined) {
      getData(pageRow.url.get) match {
        case Some((contentType, contentData)) =>
          Some(PageDataRow(-1, Some(pageRow.id), dataTypeCode = contentType, data = contentData))
        case _ => None
      }
    } else None
  }

  def getPageData(pageRows: List[PageRow]): List[PageDataRow] = {
    pageRows.map(getPageData).filter(_.isDefined).map(_.get)
  }

  private def getImageData(imageRow: ImageRow): Option[ImageRow] = {
    if(imageRow.filename.isDefined) {
      getData(imageRow.filename.get) match {
        case Some((contentType, contentData)) =>
          Some(imageRow.copy(contentType = contentType, data = contentData))
        case _ => None
      }
    } else None
  }

  def getImageData(imageRows: List[ImageRow]): List[ImageRow] = {
    imageRows.map(getImageData).filter(_.isDefined).map(_.get)
  }
}
