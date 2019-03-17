package com.ieps.crawler

import scala.util.matching.Regex
import com.typesafe.scalalogging.StrictLogging
import java.net.{URI, URL, URLDecoder, URLEncoder}
import crawlercommons.filters.basic.BasicURLNormalizer

object Canonical extends StrictLogging {
  var pattern_percent: Regex = "(%[0-9a-f]{2})".r
  var index_pages = Array("index.html", "index.htm", "index.shtml", "index.php", "default.html", "default.htm", "home.html", "home.htm", "index.php5", "index.php4", "index.cgi", "index.php3", "placeholder.html", "default.asp")
  val extensions = Array(".html", ".htm" , ".htm",".php" , ".ppt" , ".pdf",".doc",".docx",".ppt",".pptx", ".php5" , ".php4" , ".cgi" ,".php3", ".asp" )
  def getCanonical(wildUrl: String): String = {

    val urlNormal: BasicURLNormalizer = new BasicURLNormalizer
    // var decodedURl = URLEncoder.encode(wildUrl.toLowerCase,"UTF-8")
    val url: URL = new URL(wildUrl)
    var url1 = url.toString
    //Mixed host name Capital letters
    url1 = url1.replace(url.getHost, url.getHost.toLowerCase)

    //fragment remove
    if (url.getRef != null) {
      url1 = url1.replaceAll("#" + url.getRef, "")
    }
    if (url.getHost.takeRight(1) != "/") {
      url1 = url1 + "/"
    }

    //removing default index pages
    for (i <- index_pages) {
      if (url1.contains(i)) {
        url1 = url1.replace("/" + i, "")
      }
    }

    val c = urlNormal.filter(url1)
    val urli: URL = new URL(c)
    val uri: URI = new URI(urli.getProtocol, urli.getUserInfo, urli.getHost, urli.getPort, urli.getPath, urli.getQuery, urli.getRef)
    var urii = uri.toURL.toString
    for (i <- extensions) {
      if(urii.endsWith(i)){
        urii = urii.replace(urii.takeRight(1),"")
      }
    }
    urii

  }
}
