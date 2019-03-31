package com.ieps.crawler.utils

import java.io.InputStream
import java.net.{MalformedURLException, UnknownHostException}
import java.nio.charset.StandardCharsets
import java.util.logging.Level

import com.gargoylesoftware.htmlunit._
import com.ieps.crawler.db.Tables.{ImageRow, PageDataRow, PageRow}
import com.ieps.crawler.utils.HeadlessBrowser.FailedAttempt
import com.typesafe.scalalogging.StrictLogging
import org.joda.time.{DateTime, DateTimeZone}

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
  webClient.getOptions.setUseInsecureSSL(true)

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
      val statusCode: Int = webResponse.getStatusCode
      val htmlContent: String = webResponse.getContentAsString
      val hashCode: Option[String] = HashGenerator.generateSHA256(htmlContent)
      val htmlContentType: String = webResponse.getContentType
      val loadTime: Long = webResponse.getLoadTime
      if (!htmlContentType.equals("text/html")) {
          throw new Exception("Invalid content type")
      }
      Future.successful(pageRow.copy(
        httpStatusCode = Some(statusCode),
        htmlContent = Some(htmlContent),
        hash = hashCode,
        pageTypeCode = Some("HTML"),
        loadTime = Some(loadTime),
        accessedTime = Some(DateTime.now(DateTimeZone.UTC))
      ))
    } catch {
      case e: FailingHttpStatusCodeException =>
        Future.failed(
          FailedAttempt(
            e.getMessage,
            e.getCause,
            pageRow.copy(
              pageTypeCode = Some("INVALID"),
              httpStatusCode = Some(e.getStatusCode),
              accessedTime = Some(DateTime.now(DateTimeZone.UTC))
            )
          )
        )
      case e: UnknownHostException =>
        Future.failed(
          FailedAttempt(
            e.getMessage,
            e.getCause,
            pageRow.copy(
              pageTypeCode = Some("INVALID"),
              httpStatusCode = Some(404),
              accessedTime = Some(DateTime.now(DateTimeZone.UTC))
            )
          )
        )
      case e @(_: MalformedURLException| _: Exception) =>
        Future.failed(
          FailedAttempt(
            e.getMessage,
            e.getCause,
            pageRow.copy(
              pageTypeCode = Some("INVALID"),
              httpStatusCode = Some(400),
              accessedTime = Some(DateTime.now(DateTimeZone.UTC))
            )
          )
        )
    }
  }

  def getUrlContent(url: String): Option[String] = {
    try {
      //logger.info(s"getting $url")
      val response: Page = webClient.getPage(url)
      val webResponse: WebResponse = response.getWebResponse
      Some(webResponse.getContentAsString())
    } catch {
      case e: Exception =>
//        e.printStackTrace()
        None
    }
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
      val httpCode = webResponse.getStatusCode
      //logger.info(s"httpcode for $url: $httpCode")
      if (200 <= httpCode  && httpCode < 400) {
        Some((contentType, contentData))
      } else None
    } catch {
      case _: Exception =>
        //logger.info(s"Failed obtaining data for $url")
        None
    }
  }

  def getRobotsTxt(domain: String): Option[String] = {
    getData(domain.concat("robots.txt")) match {
      case Some((_, data)) => data.map(new String(_, StandardCharsets.UTF_8))
      case None => None
    }
  }

  def getPageData(pageDataRow: PageDataRow): Option[PageDataRow] = {
    if(pageDataRow.filename.isDefined) {
      getData(pageDataRow.filename.get) match {
        case Some((contentType, contentData)) =>
          Some(pageDataRow.copy(
            dataTypeCode = contentType,
            data = contentData
          ))
        case _ => None
      }
    } else None
  }

  def getImageData(imageRow: ImageRow): Option[ImageRow] = {
    if(imageRow.filename.isDefined) {
      getData(imageRow.filename.get) match {
        case Some((contentType, contentData)) =>
          Some(imageRow.copy(
            contentType = contentType,
            data = contentData
          ))
        case _ => None
      }
    } else None
  }

  def getImageData(imageRows: List[ImageRow]): List[ImageRow] = {
    imageRows.map(getImageData).filter(_.isDefined).map(_.get)
  }
}
