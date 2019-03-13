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
        try (InputStream robotsTxtStream = new URL(url + "/robots.txt").openStream()) {
            RobotsTxt robotsTxt = RobotsTxt.read(robotsTxtStream);
            return robotsTxt.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
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

    // Get list of disallowed resources
    public List<String> getDisallowList(String userAgentName) {
        List<String> permission = new ArrayList<>();
        try (InputStream robotsTxtStream = new URL(url + "/robots.txt").openStream()) {
            RobotsTxt robotsTxt = RobotsTxt.read(robotsTxtStream);
            permission = robotsTxt.getDisallowList(userAgentName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return permission;
    }

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
}
