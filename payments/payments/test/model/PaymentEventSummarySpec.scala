package model

import org.scalatest.{Matchers, WordSpec}

class PaymentEventSummarySpec extends WordSpec with Matchers {

  "PaymentEventSummary" should {
    "add missing payment methods and calculate totals with aggregate for REJECTED" in {
      val allPaymentMethods = Set("visa", "china", "maestro")
      val methodSummaries = Set(
        PaymentMethodSummary(
          method = "visa",
          count = 7,
          events = Set(
            EventStatusSummary("PENDING", 2),
            EventStatusSummary("PAID", 2),
            EventStatusSummary("CANCELLED", 1),
            EventStatusSummary("FAILURE", 1),
            EventStatusSummary("REFUNDED", 1)
          )
        ),
        PaymentMethodSummary(
          method = "china",
          count = 4,
          events = Set(
            EventStatusSummary("PENDING", 1),
            EventStatusSummary("CANCELLED", 1),
            EventStatusSummary("REFUNDED", 2)
          )
        )
      )

      PaymentEventSummary(methodSummaries, allPaymentMethods).withAggregateForRejected shouldBe PaymentEventSummary(
        total = 11,
        totalsPerStatus = Set(
          EventStatusSummary("PENDING", 3),
          EventStatusSummary("PAID", 2),
          EventStatusSummary("REJECTED", 3),
          EventStatusSummary("REFUNDED", 3)
        ),
        methodSummaries = Set(
          PaymentMethodSummary(
            method = "visa",
            count = 7,
            events = Set(
              EventStatusSummary("PENDING", 2),
              EventStatusSummary("PAID", 2),
              EventStatusSummary("REJECTED", 2),
              EventStatusSummary("REFUNDED", 1)
            )
          ),
          PaymentMethodSummary(
            method = "china",
            count = 4,
            events = Set(
              EventStatusSummary("PENDING", 1),
              EventStatusSummary("REJECTED", 1),
              EventStatusSummary("REFUNDED", 2),
              EventStatusSummary("PAID", 0)
            )
          ),
          PaymentMethodSummary(
            method = "maestro",
            count = 0,
            events = Set(
              EventStatusSummary("PENDING", 0),
              EventStatusSummary("REJECTED", 0),
              EventStatusSummary("REFUNDED", 0),
              EventStatusSummary("PAID", 0)
            )
          )
        )
      )
    }

    "add missing payment methods and calculate totals" in {
      val allPaymentMethods = Set("visa", "china", "maestro")
      val methodSummaries = Set(
        PaymentMethodSummary(
          method = "visa",
          count = 6,
          events = Set(
            EventStatusSummary("PENDING", 2),
            EventStatusSummary("PAID", 2),
            EventStatusSummary("CANCELLED", 1),
            EventStatusSummary("REFUNDED", 1)
          )
        ),
        PaymentMethodSummary(
          method = "china",
          count = 4,
          events = Set(
            EventStatusSummary("PENDING", 1),
            EventStatusSummary("CANCELLED", 1),
            EventStatusSummary("REFUNDED", 2)
          )
        )
      )

      PaymentEventSummary(methodSummaries, allPaymentMethods) shouldBe PaymentEventSummary(
        total = 10,
        totalsPerStatus = Set(
          EventStatusSummary("PENDING", 3),
          EventStatusSummary("PAID", 2),
          EventStatusSummary("CANCELLED", 2),
          EventStatusSummary("REFUNDED", 3),
          EventStatusSummary("REFUSED", 0),
          EventStatusSummary("FAILURE", 0),
          EventStatusSummary("OTHER", 0)
        ),
        methodSummaries = Set(
          PaymentMethodSummary(
            method = "visa",
            count = 6,
            events = Set(
              EventStatusSummary("PENDING", 2),
              EventStatusSummary("PAID", 2),
              EventStatusSummary("CANCELLED", 1),
              EventStatusSummary("REFUNDED", 1),
              EventStatusSummary("REFUSED", 0),
              EventStatusSummary("FAILURE", 0),
              EventStatusSummary("OTHER", 0)
            )
          ),
          PaymentMethodSummary(
            method = "china",
            count = 4,
            events = Set(
              EventStatusSummary("PENDING", 1),
              EventStatusSummary("CANCELLED", 1),
              EventStatusSummary("REFUNDED", 2),
              EventStatusSummary("PAID", 0),
              EventStatusSummary("REFUSED", 0),
              EventStatusSummary("FAILURE", 0),
              EventStatusSummary("OTHER", 0)
            )
          ),
          PaymentMethodSummary(
            method = "maestro",
            count = 0,
            events = Set(
              EventStatusSummary("PENDING", 0),
              EventStatusSummary("CANCELLED", 0),
              EventStatusSummary("REFUNDED", 0),
              EventStatusSummary("PAID", 0),
              EventStatusSummary("REFUSED", 0),
              EventStatusSummary("FAILURE", 0),
              EventStatusSummary("OTHER", 0)
            )
          )
        )
      )
    }
  }
}
