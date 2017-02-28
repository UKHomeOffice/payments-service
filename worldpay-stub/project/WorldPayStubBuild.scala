import sbt.Keys._
import sbt._

object WorldPayStubBuild extends Build {

  val appName = "cjp-worldpay-stub"
  val appVersion = "1.0"

  incOptions := incOptions.value.withNameHashing(true)

  lazy val worldpayStub = play.Project(appName, appVersion, path = file("worldpay-stub")).settings(
    scalaVersion := "2.10.4"
  )

  lazy val root = project.in(file("."))
    .aggregate(worldpayStub)

  override def rootProject = Some(worldpayStub)

}
