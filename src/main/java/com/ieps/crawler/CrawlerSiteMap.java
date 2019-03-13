package com.ieps.crawler;


import java.io.IOException;
import java.lang.String;
import java.net.MalformedURLException;
import java.net.URL;

import crawlercommons.sitemaps.*;

public class CrawlerSiteMap {
    private String url;

    public CrawlerSiteMap(String url) {
        this.url = url;
    }

    public void CrawlerSMUrls() throws IOException, UnknownFormatException {
        SiteMapURL siteMapURL = new SiteMapURL(url, true);
        siteMapURL.getLastModified();
        SiteMapParser siteMap = new SiteMapParser();
        System.out.println(siteMap.parseSiteMap(new URL(url + "/sitemaps.xml")));
        System.out.println("smurls");

    }


}
