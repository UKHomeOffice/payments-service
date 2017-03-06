package controllers

import builders.{PaymentEventBuilder, PaymentStartRequestBuilder}
import controller.{PaymentControllerClass, PaymentStartResponse}
import matcher.ResultMatchers
import model.Types.Url
import model.{Payment, PaymentProfile}
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, WordSpec}
import play.api.test.FakeRequest
import play.mvc.Http.Status
import repository.{PaymentEventRepository, PaymentRepository}
import utils.{FakeApp, FixedTime, Mocking}
import worldpay.{WorldPayClient, WorldPayConfiguration}

import scala.concurrent.Future

class PaymentControllerSpec extends WordSpec with ResultMatchers with FakeApp with Mocking with ScalaFutures with BeforeAndAfter with FixedTime {

  trait Scope {
    val paymentRepository = mock[PaymentRepository]
    val paymentEventRepository = mock[PaymentEventRepository]
    val worldPayClient = mock[WorldPayClient]
    val paymentController = new PaymentControllerClass(worldPayClient, paymentRepository, paymentEventRepository)
  }

  def jsonRequest = FakeRequest().withHeaders("content-type" -> "application/json", "X-CLIENT-ID" -> "DCJ")

  "startPayment" should {

    "return 403 if no client id " in new Scope {

      val payment = PaymentStartRequestBuilder()
      val request = FakeRequest().withHeaders("content-type" -> "application/json")
      val result = paymentController.startPayment(request.withBody(payment))
      result should haveStatus(403)
      result should haveStringBody("Client id not provided")

    }

    "return 403 if invalid client id " in new Scope {

      val payment = PaymentStartRequestBuilder()
      val request = FakeRequest().withHeaders("content-type" -> "application/json", "X-CLIENT-ID" -> "UNKNOWN")
      val result = paymentController.startPayment(request.withBody(payment))
      result should haveStatus(403)
      result should haveStringBody("Invalid client Id : UNKNOWN")

    }

    "return BadRequest with validation errors if given invalid payment" in new Scope {
      val payment = PaymentStartRequestBuilder("", "").copy(total = 0d, profile = PaymentProfile("", ""), paymentItems = List.empty)

      val paymentResult = paymentController.startPayment(jsonRequest.withBody(payment))

      paymentResult should haveStatus(Status.BAD_REQUEST)
      paymentResult should haveBody(Seq(
        "externalReference.empty",
        "internalReference.empty",
        "total.zeroOrLess",
        "profile.paymentType.empty",
        "profile.region.empty",
        "paymentItems.empty")
      )
    }

    "return BadRequest with validation error if paymentItems do not sum up to payment total" in new Scope {
      val payment = PaymentStartRequestBuilder().copy(total = 100d)

      val paymentResult = paymentController.startPayment(jsonRequest.withBody(payment))

      paymentResult should haveStatus(Status.BAD_REQUEST)
      paymentResult should haveBody(Seq(
        "paymentItems.total.invalid")
      )
    }

    "save paymentRequest and initial paymentEvent and return success with url to WorldPay" in new Scope {
      val paymentStartRequest = PaymentStartRequestBuilder()
      val payment = Payment(paymentStartRequest, "DCJ")

      when(paymentRepository.save(payment)).thenReturn(Future.successful(payment))

      val paymentUrl: Url = "http://successful-url-to-worlpay"
      val returnUrls = s"&successURL=http%3A%2F%2Flocalhost%3A9010%2FworldPayPayment%2Fpending%3FexternalReference%3D${payment.externalReference}" +
        "&cancelURL=http%3A%2F%2Flocalhost%3A9010%2FworldPayPayment%2Fcancel" +
        s"&pendingURL=http%3A%2F%2Flocalhost%3A9010%2FworldPayPayment%2Fpending%3FexternalReference%3D${payment.externalReference}" +
        s"&failureURL=http%3A%2F%2Flocalhost%3A9010%2FworldPayPayment%2Fpending%3FexternalReference%3D${payment.externalReference}" +
        s"&preferredPaymentMethod=${payment.profile.paymentType}"
      when(worldPayClient.startPayment("DCJ", paymentStartRequest)).thenReturn(Future.successful(Right(paymentUrl)))

      val paymentResult = paymentController.startPayment(jsonRequest.withBody(paymentStartRequest))
      paymentResult should haveStatus(Status.OK)
      paymentResult should haveBody(PaymentStartResponse(paymentUrl + returnUrls, paymentStartRequest.externalReference))

      verify(paymentEventRepository).save(PaymentEventBuilder(payment, "INIT", DateTime.now))
      verify(paymentRepository).save(payment)
    }

    "return error if can't save the paymentRequest" in new Scope {
      val paymentStartRequest = PaymentStartRequestBuilder()
      val payment = Payment(paymentStartRequest, "DCJ")

      when(paymentRepository.save(payment)).thenReturn(Future.failed(new RuntimeException))

      a[RuntimeException] shouldBe thrownBy(paymentController.startPayment(jsonRequest.withBody(paymentStartRequest)).futureValue)
    }

    "throws Exception returned from worldPaySender" in new Scope {
      val paymentStartRequest = PaymentStartRequestBuilder()
      val payment = Payment(paymentStartRequest, "DCJ")


      when(worldPayClient.startPayment("DCJ",paymentStartRequest)).thenReturn(Future.successful(Left(new RuntimeException)))

      a[RuntimeException] shouldBe thrownBy(paymentController.startPayment(jsonRequest.withBody(paymentStartRequest)).futureValue)
      verify(paymentRepository).save(payment)
    }

    "return 400 error code if paymentRequest request not valid" in new Scope {
      paymentController.startPayment(FakeRequest()).run should haveStatus(Status.BAD_REQUEST)
    }
  }

  "getPaymentTypes" should {

    "return 403 if no client id " in new Scope {

      val result = paymentController.getPaymentTypes("poland")(FakeRequest())

      result should haveStatus(403)
      result should haveStringBody("Client id not provided")

    }

    "return 403 if invalid client id " in new Scope {
      val result = paymentController.getPaymentTypes("poland")(FakeRequest().withHeaders("X-CLIENT-ID" -> "UNKNOWN"))

      result should haveStatus(403)
      result should haveStringBody("Invalid client Id : UNKNOWN")
    }


    "return list of payment methods available for a given region" in new Scope {
      val result = paymentController.getPaymentTypes("south-america")(FakeRequest().withHeaders("X-CLIENT-ID" -> "ADS"))

      result should haveStatus(200)
      result should haveBody(WorldPayConfiguration("ADS").profile("america").get.paymentTypes)
    }

    "return empty list for not recognized region" in new Scope {
      val result = paymentController.getPaymentTypes("ukraine")(FakeRequest().withHeaders("X-CLIENT-ID" -> "DCJ"))

      result should haveStatus(200)
      result should haveBody(Set.empty)
    }
  }
}
