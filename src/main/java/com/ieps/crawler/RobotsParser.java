package com.ieps.crawler;


import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.lang.String;
import java.util.ArrayList;
import java.util.List;

import com.panforge.robotstxt.RobotsTxt;


public class RobotsParser {
    private String url;

    public RobotsParser(String url) {
        this.url = url;
    }


    // Return /robots.txt content
    public String robotsTxtContent() {
        String content = null;
        try (InputStream robotsTxtStream = new URL(url + "/robots.txt").openStream()) {
            RobotsTxt robotsTxt = RobotsTxt.read(robotsTxtStream);
            content = robotsTxt.toString();
        } catch (IOException e) {
//            e.printStackTrace();
        }
        return content;
    }


    // Return all user agents
    public List<String> allUserAgents() {
        List<String> userAgents = new ArrayList<>();
        try (InputStream robotsTxtStream = new URL(url + "/robots.txt").openStream()) {
            RobotsTxt robotsTxt = RobotsTxt.read(robotsTxtStream);
            String[] lines = robotsTxt.toString().split("\\r?\\n");
            // Read whole robots.txt and find all user-agents
            for (String line : lines) {
                if (line.regionMatches(0, "User-agent: ", 0, 11)) {
                    String[] userAgent = line.split("agent: ");
                    userAgents.add(userAgent[1]);
                }
            }
            return userAgents;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
//
//    // Get list of disallowed resources
//    public List<String> getDisallowList(String userAgentName) {
//        List<String> permission = new ArrayList<>();
//        try (InputStream robotsTxtStream = new URL(url + "/robots.txt").openStream()) {
//            RobotsTxt robotsTxt = RobotsTxt.read(robotsTxtStream);
//            permission = robotsTxt.getDisallowList(userAgentName);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return permission;
//    }


    // Get list of disallowed resources
    public int getDelay() {
        int delay = 5;
        try (InputStream robotsTxtStream = new URL(url + "/robots.txt").openStream()) {
            RobotsTxt robotsTxt = RobotsTxt.read(robotsTxtStream);
            delay = robotsTxt.ask("*", url + "/robots.txt").getCrawlDelay();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return delay;
    }

    // Returns a list od site map urls
    public List<String> getSiteMapUrls() {
        List<String> siteMapUrls = new ArrayList<>();
        try (InputStream robotsTxtStream = new URL(url + "/robots.txt").openStream()) {
            RobotsTxt robotsTxt = RobotsTxt.read(robotsTxtStream);
            siteMapUrls = robotsTxt.getSitemaps();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return siteMapUrls;
    }

    /*
    * Is visiting url allowed
    * This method should have for
    * it's argument baseless url
    *
    * e.g. if checking for
    * "www.facebook.com/groups/names/"
    * input should only be "/groups/names/"
    * *
    * */
    public boolean isAllowed(String urlRobots) {
        List<String> listUrls;
        boolean allowed = false;
        try (InputStream robotsTxtStream = new URL(url + "/robots.txt").openStream()) {
            RobotsTxt robotsTxt = RobotsTxt.read(robotsTxtStream);
            listUrls = robotsTxt.getDisallowList("*");
            for (String urls : listUrls) {
                allowed = urlRobots.equals(urls);
                if (allowed) break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return allowed;
    }
}
