# WIER: Crawler implementation

## Brief description
This is an implementation of a [web-crawler](https://en.wikipedia.org/wiki/Web_crawler) written in [Scala](https://www.scala-lang.org/).

It is using [Akka Actors](https://doc.akka.io/docs/akka/current/actors.html) to spawn multiple workers in order to parallelize the execution of the code. To follow the crawling rules provided in [robots.txt](https://en.wikipedia.org/wiki/Robots_exclusion_standard) files we are using [crawler-commons](https://github.com/crawler-commons/crawler-commons), which also provides us support of [SiteMaps](https://en.wikipedia.org/wiki/Site_map).

Using the crawler, we gather pages from as much as possible `.gov.si` websites. To render the websites and get the status code, we are using [HtmlUnit](https://github.com/HtmlUnit/htmlunit). We start off with a limited static seed list provided in [CrawlerApp.scala](./src/main/scala/com/ieps/crawler/CrawlerApp.scala). We gather the HTML content of all encountered pages, including all the images and binary files of type `pdf`, `doc`, `docx`, `ppt`, `pptx` within the seed list and its subdomains.

All the data gathered by the crawler is written to a local instance of a [PostgreSQL](https://www.postgresql.org/) database using [Slick](http://slick.lightbend.com/) as a [relational mapper](https://en.wikipedia.org/wiki/Object-relational_mapping).

## Requirements
In order to run the code you need to:

1. A local instance of `PostgreSQL` database needs to be set up:
    1. Have the _modified_ `crawldb.sql` imported into a database called _crawldb_
    2. Have a user `postgres` with no password (default user)
2. Make sure to have `sbt` [installed](https://www.scala-sbt.org/0.13/docs/Setup.html) on the machine where the code will be running
3. Make sure that a `queue` directory exists within the working directory of the executable. (If you are running everything from the root directory you should be fine.)

NOTE: The script provided in the repository is required to run the code because the database models which the code relies upon are generated on compile time.

NOTE: To change the specifics about the database name, username and password, please modify the `local` configuration in [application.conf](./src/main/resources/application.conf).

## Running
To run the code please make sure you have the database set up (previous section) and then run the following command in the root directory: 
```bash
sbt run
```
To stop the execution just send a `SIGTERM` signal in the console (<kbd>CTRL</kbd>/<kbd>CMD</kbd> + <kbd>C</kbd>)

## Packaging
To generate a `.jar` executable, run the following command in the root directory:
```bash
# generate a fat jar:
sbt assembly
# upon completion run the jar:
java -jar ./target/scala-2.12/ieps-crawler-assembly-0.1.jar
```
