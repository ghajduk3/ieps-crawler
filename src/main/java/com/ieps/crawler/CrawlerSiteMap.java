package com.ieps.crawler;


import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

import crawlercommons.sitemaps.*;

public class CrawlerSiteMap {
    private String url;

    public CrawlerSiteMap(String url) {
        this.url = url;
    }


    public void CrawlerSMUrls() throws IOException, UnknownFormatException {
//        SiteMapURL siteMapURL = new SiteMapURL(url, true);
//        SiteMapParser siteMap = new SiteMapParser();
//        AbstractSiteMap abSM = siteMap.parseSiteMap(new URL(url + "/sitemaps.xml"));
//        System.out.println("Radimo li" + siteMap.parseSiteMap(new URL(url + "/sitemaps.xml")));
//        System.out.println("absm" + abSM);
//        System.out.println("smurls");
//        URL urlU = new URL(url);
//        SiteMapParser siteMap = new SiteMapParser();
//        SiteMapIndex indexSiteMap = new SiteMapIndex();
//        System.out.println("Iz crawlera " + siteMap.parseSiteMap(setNewURL(url)));
//        System.out.println("getSitemaps " + indexSiteMap.getSitemaps());

    }


    public void URLSList(List<String> smUrls) throws IOException, UnknownFormatException {

/*
* OVaj dio ovdje jos treba da se sredi
* Ovo mi cita output i prebaca ga u file
* i onda cu iz njega da izbacim sve sitemap url-ove
* */

//        File inputFile = new File("./siteMapUrlss.txt");
//        File tempFile = new File("myTempFile.txt");
//
//        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
//        BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
//
//        String lineToRemove = "03:03:41.323 [run-main-3] INFO  c.sitemaps.SiteMapTester - Parsing http://www.e-prostor.gov.si/?eID=dd_googlesitemap";
//        String currentLine;
//
//        while((currentLine = reader.readLine()) != null) {
//            // trim newline when comparing with lineToRemove
//            String trimmedLine = currentLine.trim();
//            if(trimmedLine.equals(lineToRemove)) continue;
//            writer.write(currentLine + System.getProperty("line.separator"));
//        }
//        writer.close();
//        reader.close();


//        PrintStream stdout = System.out;
//        String[] siteMapUrl = new String[1];
//        siteMapUrl[0] = smUrls.size() <= 1 ? smUrls.get(0) : null;
//        PrintStream fileOut = new PrintStream("./siteMapUrlss.txt");
//        System.setOut(fileOut);
//        SiteMapTester.main(siteMapUrl);
//        System.setOut(stdout);




    }


}
