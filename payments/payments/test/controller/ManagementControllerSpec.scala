package controller

import management.{BuildInfo, MongoHealthCheck}
import matcher.ResultMatchers
import org.mockito.Mockito.when
import org.scalatest.{Matchers, WordSpec}
import play.api.test.FakeRequest
import utils.Mocking

import scala.concurrent.Future

class ManagementControllerSpec extends WordSpec with Matchers with Mocking with ResultMatchers {

  trait Scope {
    val mongoHealthCheck = mock[MongoHealthCheck]

    val buildInfo = mock[BuildInfo]
    val buildNumber = 2
    when(buildInfo.buildNumber).thenReturn(buildNumber)

    val controller = new ManagementControllerClass(mongoHealthCheck, Some(buildInfo))
  }

  "healthcheck" should {
    "check connectivity to mongo and respond as healthy with build number if it's up" in  new Scope {
      when(mongoHealthCheck.ping()).thenReturn(Future.successful(true))

      val result = controller.healthcheck(FakeRequest())
      result should haveStatus(200)
      result should haveStringBody(s"""|healthy!
        |build-number: ${buildNumber}""".stripMargin
      )
    }

    "check connectivity to mongo and respond with 500 if mongo not up" in  new Scope {
      when(mongoHealthCheck.ping()).thenReturn(Future.successful(false))

      val result = controller.healthcheck(FakeRequest())
      result should haveStatus(500)
      result should haveStringBody("Mongo db did not respond respond correctly")
    }

    "check connectivity to mongo and respond with as healthy without build number if not available" in new Scope {
      when(mongoHealthCheck.ping()).thenReturn(Future.successful(true))

      val result = new ManagementControllerClass(mongoHealthCheck, None).healthcheck(FakeRequest())
      result should haveStatus(200)
      result should haveStringBody(s"""|healthy!
        |build-number: [not available]""".stripMargin
      )
    }
  }
}
