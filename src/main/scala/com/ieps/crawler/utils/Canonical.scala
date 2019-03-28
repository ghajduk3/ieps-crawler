package com.ieps.crawler.utils

import java.net.{URI, URL}

import com.typesafe.scalalogging.StrictLogging
import crawlercommons.filters.basic.BasicURLNormalizer

import scala.util.matching.Regex

object Canonical extends StrictLogging {
  var pattern_percent: Regex = "(%[0-9a-f]{2})".r
  var index_pages = Array("index.html", "index.htm", "index.shtml", "index.php", "default.html", "default.htm", "home.html", "home.htm", "index.php5", "index.php4", "index.cgi", "index.php3", "placeholder.html", "default.asp")
  val extensions = Array(".html", ".htm", ".php", ".ppt", ".pdf", ".doc", ".docx", ".ppt", ".pptx", ".php5", ".php4", ".cgi", ".php3", ".asp", ".jpg", ".png", ".jpeg", ".svg", ".tiff", ".gif", ".jsp", ".jspx", ".asp", ".aspx", ".zip", ".gz", ".tar.gz", ".tar")

  def getCanonical(noviUrl: String): String = {
    var wildUrl = noviUrl

    val urlNormal: BasicURLNormalizer = new BasicURLNormalizer

    // before making a URL object protocol has to be checked
    if (!wildUrl.startsWith("http://") && !wildUrl.startsWith("https://")) {
      wildUrl = "http://" + wildUrl
    }

    val url: URL = new URL(wildUrl)
    var url1 = url.toString
    //Mixed host name Capital letters
    url1 = url1.replace(url.getHost, url.getHost.toLowerCase)

    //fragment remove
    if (url.getRef != null) {
      url1 = url1.replaceAll("#" + url.getRef, "")
    }

    //removing default index pages
    for (i <- index_pages) {
      if (url1.contains(i)) {
        url1 = url1.replace("/" + i, "")
      }
    }

    if (url.getPath.takeRight(1) != "/") {
      url1 = url1 + "/"
    }
    val c = urlNormal.filter(url1)
    val urli: URL = new URL(c)
    val uri: URI = new URI(urli.getProtocol, urli.getUserInfo, urli.getHost, urli.getPort, urli.getPath, urli.getQuery, urli.getRef)
    var urii = uri.toURL.toString
    for (i <- extensions) {
      if (urii.endsWith(i + "/")) {
        urii = urii.substring(0, urii.length - 1)
      }
    }
    urii
  }

  def extractDomain(urlStr: String): String = try {
    val domain = new URI(getCanonical(urlStr)).getHost
    if (domain.startsWith("www")) domain.substring(4)
    else domain
  } catch {
    case _: Exception => "Url is not correct"
  }
}
