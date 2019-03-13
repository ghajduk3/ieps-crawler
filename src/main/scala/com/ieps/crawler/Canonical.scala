package com.ieps.crawler

import scala.util.matching.Regex
import com.typesafe.scalalogging.StrictLogging
import java.net.{URI, URL, URLDecoder, URLEncoder}

object Canonical extends StrictLogging {
  var pattern_percent: Regex = "(%[0-9a-f]{2})".r
  var index_pages = Array("index.html", "index.htm", "index.shtml", "index.php", "default.html", "default.htm", "home.html", "home.htm", "index.php5", "index.php4", "index.cgi", "index.php3", "placeholder.html", "default.asp")

  def getCanonical(wildUrl: String): String = {

    // var decodedURl = URLEncoder.encode(wildUrl.toLowerCase,"UTF-8")
    val url: URL = new URL(wildUrl)

    // var uri:URI = new URI(url.getProtocol , url.getUserInfo , url.getHost , url.getPort , url.getPath , url.getQuery , url.getRef )
    //var urli = uri.toURL
    var url1 = url.toString
    //Mixed host name Capital letters
    url1 = url1.replace(url.getHost, url.getHost.toLowerCase)

    //Default port remove
    if (url.getDefaultPort != 0) {
      val port = url.getDefaultPort.toString
      url1 = url1.replaceAll(":" + port, "")
    }

    //fragment remove
    if (url.getRef != null) {
      url1 = url1.replaceAll("#" + url.getRef, "")
    }

    //OVO TREBA DA SE POPRAVI MOZDA ---PITATI MARKA
    if (url.getHost.takeRight(1) != "/") {
      url1 = url1 + "/"
    }

    //decoding  needlessly encoded characters
    if (url1.contains("%")) {
      val reg = pattern_percent.findAllMatchIn(url1).toList
      for (i <- reg) {
        url1 = url1.replace(i.toString, URLDecoder.decode(i.toString, "UTF-8"))

      }
    }

    /*
     var newUrl :URL = new URL(url1)
      var path = newUrl.getPath

      if(path.startsWith("../") || path.startsWith("./") )
      {
        path=path.replaceAll("./","")
        path=path.replaceAll("../","")
      }
      else if (path.startsWith("/./") || path.startsWith("./" ))
        {}

    */ for (i <- index_pages) {
      if (url1.contains(i)) {
        url1 = url1.replace("/" + i, "")
      }
    }
    val urli: URL = new URL(url1)
    val uri: URI = new URI(urli.getProtocol, urli.getUserInfo, urli.getHost, urli.getPort, urli.getPath, urli.getQuery, urli.getRef)


    return uri.toURL.toString


  }
}
