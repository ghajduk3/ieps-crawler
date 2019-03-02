name := "ieps-crawler"

version := "0.1"

scalaVersion := "2.12.8"

lazy val root = (project in file("."))
  .settings(
    name := "IEPS-crawler",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "com.typesafe.akka" %% "akka-actor" % "2.5.21",
      "com.typesafe.akka" %% "akka-testkit" % "2.5.21" % Test,
      "org.tpolecat" %% "doobie-core"      % "0.6.0",
      "org.tpolecat" %% "doobie-postgres"  % "0.6.0", // Postgres driver 42.2.5 + type mappings.
    )
  )