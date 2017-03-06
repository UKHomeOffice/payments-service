package controller

import matcher.ResultMatchers
import model.{EventStatusSummary, PaymentMethodSummary, PaymentEventSummary}
import org.json4s.jackson.Serialization
import org.mockito.Mockito.when
import org.scalatest.WordSpec
import play.api.test.FakeRequest
import repository.PaymentEventRepository
import utils.Mocking

import scala.concurrent.Future

class MetricsControllerSpec extends WordSpec with ResultMatchers with Mocking {

  private val paymentEventRepository = mock[PaymentEventRepository]
  private val allPaymentMethods = Set("visa", "maestro")

  private val controller = new DailyReportControllerClass(paymentEventRepository, allPaymentMethods)

  "report" should {
    "return statistics fetched from the repository in short version if the 'full' query parameter not given" in {
      val methodSummaries = Set(PaymentMethodSummary(
        method = "visa",
        count = 5,
        events = Set(
          EventStatusSummary("PENDING", 1),
          EventStatusSummary("PAID", 1),
          EventStatusSummary("CANCELLED", 1),
          EventStatusSummary("REFUNDED", 1),
          EventStatusSummary("OTHER", 1)
        )
      ))
      when(paymentEventRepository.generateDailyReportBreakDownByPaymentMethod).thenReturn(Future.successful(methodSummaries))

      val result = controller.report(FakeRequest("GET", "/report"))

      result should haveStatus(200)
      result should haveBody(PaymentEventSummary(
        total = 5,
        totalsPerStatus = Set(
          EventStatusSummary("PENDING", 1),
          EventStatusSummary("PAID", 1),
          EventStatusSummary("REJECTED", 2),
          EventStatusSummary("REFUNDED", 1)
        ),
        methodSummaries = Set(
          PaymentMethodSummary(
            method = "visa",
            count = 5,
            events = Set(
              EventStatusSummary("PENDING", 1),
              EventStatusSummary("PAID", 1),
              EventStatusSummary("REJECTED", 2),
              EventStatusSummary("REFUNDED", 1)
            )
          ),
          PaymentMethodSummary(
            method = "maestro",
            count = 0,
            events = Set(
              EventStatusSummary("PENDING", 0),
              EventStatusSummary("PAID", 0),
              EventStatusSummary("REJECTED", 0),
              EventStatusSummary("REFUNDED", 0)
            )
          )
      )))
    }

    "return statistics fetched from the repository in full version if the 'full' query parameter given" in {
      val methodSummaries = Set(PaymentMethodSummary(
        method = "visa",
        count = 5,
        events = Set(
          EventStatusSummary("PENDING", 1),
          EventStatusSummary("PAID", 1),
          EventStatusSummary("CANCELLED", 1),
          EventStatusSummary("REFUNDED", 1),
          EventStatusSummary("OTHER", 1)
        )
      ))
      when(paymentEventRepository.generateDailyReportBreakDownByPaymentMethod).thenReturn(Future.successful(methodSummaries))

      val result = controller.report(FakeRequest("GET", "/report?full=true"))

      result should haveStatus(200)
      result should haveBody(PaymentEventSummary(
        total = 5,
        totalsPerStatus = Set(
          EventStatusSummary("PENDING", 1),
          EventStatusSummary("PAID", 1),
          EventStatusSummary("REFUSED", 0),
          EventStatusSummary("FAILURE", 0),
          EventStatusSummary("CANCELLED", 1),
          EventStatusSummary("REFUNDED", 1),
          EventStatusSummary("OTHER", 1)
        ),
        methodSummaries = Set(
          PaymentMethodSummary(
            method = "visa",
            count = 5,
            events = Set(
              EventStatusSummary("PENDING", 1),
              EventStatusSummary("PAID", 1),
              EventStatusSummary("REFUSED", 0),
              EventStatusSummary("FAILURE", 0),
              EventStatusSummary("CANCELLED", 1),
              EventStatusSummary("REFUNDED", 1),
              EventStatusSummary("OTHER", 1)
            )
          ),
          PaymentMethodSummary(
            method = "maestro",
            count = 0,
            events = Set(
              EventStatusSummary("PENDING", 0),
              EventStatusSummary("PAID", 0),
              EventStatusSummary("REFUSED", 0),
              EventStatusSummary("FAILURE", 0),
              EventStatusSummary("CANCELLED", 0),
              EventStatusSummary("REFUNDED", 0),
              EventStatusSummary("OTHER", 0)
            )
          )
      )))
    }
  }
}
