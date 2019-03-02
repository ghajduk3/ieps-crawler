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
      "com.typesafe.slick" %% "slick" % "3.3.0",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.3.0",
      "org.postgresql" % "postgresql" % "9.4-1206-jdbc42",
      "com.typesafe.slick" %% "slick-codegen" % "3.3.0",
      "com.github.tototoshi" %% "slick-joda-mapper" % "2.4.0",
      "joda-time" % "joda-time" % "2.7",
      "org.joda" % "joda-convert" % "1.7"
    )
  )

//lazy val slick = TaskKey[Seq[File]]("gen-tables")
//lazy val slickCodeGenTask = (sourceManaged, dependencyClasspath in Compile, runner in Compile, streams) map { (dir, cp, r, s) =>
//  val outputDir = (dir / "slick").getPath // place generated files in sbt's managed sources folder
//  val url = "jdbc:h2:mem:test;INIT=runscript from 'src/main/sql/create.sql'" // connection info for a pre-populated throw-away, in-memory db for this demo, which is freshly initialized on every run
//  val jdbcDriver = "org.h2.Driver"
//  val slickDriver = "slick.driver.H2Driver"
//  val pkg = "demo"
//  toError(r.run("slick.codegen.SourceCodeGenerator", cp.files, Array(slickDriver, jdbcDriver, url, outputDir, pkg), s.log))
//  val fname = outputDir + "/demo/Tables.scala"
//  Seq(file(fname))
//}