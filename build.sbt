name := "ieps-crawler"

version := "0.1"

scalaVersion := "2.12.8"
scalacOptions += "-Ypartial-unification" // 2.11.9+

lazy val root = (project in file("."))
  .settings(
    name := "IEPS-crawler",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "com.typesafe.akka" %% "akka-actor" % "2.5.21",
      "com.typesafe.akka" %% "akka-testkit" % "2.5.21" % Test,
      "com.panforge" % "robots" % "1.4.0",
      "com.github.crawler-commons" % "crawler-commons" % "0.10",
      "com.typesafe.slick" %% "slick" % "3.3.0",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.3.0",
      "org.postgresql" % "postgresql" % "9.4-1206-jdbc42",
      "com.typesafe.slick" %% "slick-codegen" % "3.3.0",
      "com.github.tototoshi" %% "slick-joda-mapper" % "2.4.0",
      "joda-time" % "joda-time" % "2.7",
      "org.joda" % "joda-convert" % "1.7",
      "com.github.crawler-commons" % "crawler-commons" % "0.10",
      "org.jsoup" % "jsoup" % "1.11.3",
      "info.debatty" % "java-lsh" % "0.11",
      "org.joda" % "joda-convert" % "1.7",
//      "org.seleniumhq.selenium" % "selenium-server" % "3.141.59",
//      "org.seleniumhq.selenium" % "selenium-java" % "3.141.59",
//      "org.seleniumhq.selenium" % "selenium-remote-driver" % "3.141.59",
//      "org.seleniumhq.selenium" % "selenium-api" % "3.141.59",
//      "io.github.bonigarcia" % "webdrivermanager" % "3.3.0"
      "com.github.nscuro" % "webdriver-manager" % "0.2.3",
      "net.sourceforge.htmlunit" % "htmlunit" % "2.32"

    )
  )

import slick.codegen.SourceCodeGenerator
import slick.{ model => m }
// required
enablePlugins(CodegenPlugin)

// required
// Register codegen hook
sourceGenerators in Compile += slickCodegen

// required
slickCodegenDatabaseUrl := "jdbc:postgresql://localhost/crawldb"

// required
slickCodegenDatabaseUser := "postgres"

// required
slickCodegenDatabasePassword := ""

// required (If not set, postgresql driver is choosen)
slickCodegenDriver := slick.jdbc.PostgresProfile

// required (If not set, postgresql driver is choosen)
slickCodegenJdbcDriver := "org.postgresql.Driver"

// optional but maybe you want
slickCodegenOutputPackage := "com.ieps.crawler.db"

// optional, pass your own custom source code generator
slickCodegenCodeGenerator := { model: m.Model =>
  new SourceCodeGenerator(model) {
    override def code =
      "import org.joda.time.DateTime\n" +
      "import com.github.tototoshi.slick.PostgresJodaSupport._\n" + super.code

    override def Table = new Table(_) {
      override def Column = new Column(_) {
        override def rawType = model.tpe match {
          case "java.sql.Timestamp" => "DateTime"
          case _ => super.rawType
        }
      }
    }
  }

}

// optional
// For example of all the tables in a database we only would like to take table named "users"
//slickCodegenIncludedTables in Compile := Seq("users")

// optional
// For example, to exclude flyway's schema_version table from the target of codegen. This still applies after slickCodegenIncludedTables.
//slickCodegenExcludedTables in Compile := Seq("schema_version")

//optional
//slickCodegenOutputDir := (sourceManaged in Compile).value
