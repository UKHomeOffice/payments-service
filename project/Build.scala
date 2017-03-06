import com.typesafe.sbt.SbtStartScript
import play.Play.autoImport._
import sbt._
import Keys._
import PlayKeys._

object ApplicationBuild extends Build {

  val appName    = "payments"
  val appVersion = "1.0"

  val appDependencies = Seq(
    "org.scala-lang.modules" %% "scala-async" % "0.9.0-M4",
    "org.mockito" % "mockito-all" % "1.9.0" % "test",
    "org.scalatest" %% "scalatest" % "2.2.0" % "test",
    "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23",
    "net.fehmicansaglam" %% "reactivemongo-extensions-bson" % "0.10.0.4",
    "net.logstash.logback" % "logstash-logback-encoder" % "2.8",
    "org.yaml" % "snakeyaml" % "1.13",
    "com.typesafe.akka" %% "akka-testkit" % "2.3.3" % "test",
    "me.lessis" %% "courier" % "0.1.3",
    "dumbster" % "dumbster" % "1.6" exclude("javax.mail", "mail"),
    "org.json4s" %% "json4s-ext" % "3.2.4",
    "org.json4s" %% "json4s-jackson" % "3.2.4"
  )

  lazy val main = Project(appName, file(".")).enablePlugins(play.PlayScala).settings(
      version := appVersion,
      libraryDependencies ++= appDependencies,
      libraryDependencies += ws,
      scalaVersion := "2.10.4",
      scalacOptions ++= Seq("-feature"),
      resolvers += "SnakeYAML repository" at "http://oss.sonatype.org/content/groups/public/",
      resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
      parallelExecution in ScctPlugin.ScctTest := false,
      libraryDependencies += filters,
      parallelExecution in Test := false,
      routesImport += "binder.PathBinders._"
      //scalacOptions ++= Seq("-unchecked", "-deprecation","-feature")
    )
    .settings(SbtStartScript.startScriptForClassesSettings: _*)
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)

  override def rootProject = Some(main)

}
