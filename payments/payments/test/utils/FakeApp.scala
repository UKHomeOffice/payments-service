package utils

import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.Play
import play.api.test.FakeApplication

trait FakeApp extends BeforeAndAfterAll {
  this: Suite =>
  val fakeApp: FakeApplication = FakeApplication()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    Play.start(fakeApp)
  }

  override protected def afterAll(): Unit = {
    Play.stop()
    super.afterAll()
  }
}
