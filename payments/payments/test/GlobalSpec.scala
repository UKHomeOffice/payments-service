import matcher.ResultMatchers
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import play.api.mvc.RequestHeader
import play.mvc.Http.Status
import utils.Mocking
import worldpay.InvalidPaymentProfileException

class GlobalSpec extends WordSpec with ResultMatchers with Mocking with ScalaFutures {

  val requestHeader = mock[RequestHeader]

  "onError" should {

    "return BadRequest with exception message if InvalidPaymentProfileException is exception cause" in {
      val exception = new InvalidPaymentProfileException("", "")
      val result = Global.onError(requestHeader, new Exception(exception))
      result should haveStatus(Status.BAD_REQUEST)
      result should haveStringBody(exception.getMessage)
    }

    "return InternalServerError with exception message if a Throwable is exception cause" in {
      val exception = new Exception("")
      val result = Global.onError(requestHeader, new Exception(exception))
      result should haveStatus(Status.INTERNAL_SERVER_ERROR)
      result should haveStringBody(exception.getMessage)
    }
  }
}
