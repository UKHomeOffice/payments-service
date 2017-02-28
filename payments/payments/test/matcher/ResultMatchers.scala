package matcher

import json.JsonFormats
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization
import org.scalatest.Matchers
import org.scalatest.matchers.{MatchResult, Matcher}
import play.api.mvc.Result
import play.api.test.Helpers._

import scala.concurrent.Future

trait ResultMatchers extends Matchers with JsonFormats {

  def haveStatus(expectedStatus: Int) = new Matcher[Future[Result]] {

    override def apply(left: Future[Result]) = {
      val actualCode = status(left)
      MatchResult(
        actualCode == expectedStatus,
        s"$actualCode code is not $expectedStatus",
        s"$actualCode code is $expectedStatus"
      )
    }
  }

  def haveStringBody(expectedBody: String) = new Matcher[Future[Result]] {

    override def apply(left: Future[Result]) = {
      val actualBody = contentAsString(left)
      MatchResult(
        actualBody == expectedBody,
        s"$actualBody is not $expectedBody",
        s"$actualBody code is $expectedBody"
      )
    }
  }

  def haveBody[T](body: T)(implicit manifest: Manifest[T]) = new  Matcher[Future[Result]] {

    override def apply(left: Future[Result]): MatchResult = {
      val actualBody = Serialization.read[T](contentAsString(left))
      MatchResult(
        actualBody == body,
        s"$actualBody is not $body",
        s"$actualBody code is $body"
      )
    }

  }
}
