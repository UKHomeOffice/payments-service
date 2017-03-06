package model

import builders.PaymentEventBuilder
import org.scalatest.{Matchers, WordSpec}

class PaymentEventSpec extends WordSpec with Matchers {

  "PaymentStatus instantiation" should {
    "return PaymentEvent with correct status" in {
      PaymentEventBuilder("intRef", "INIT").paymentStatus.toString should be("PENDING")
      PaymentEventBuilder("intRef", "OPEN").paymentStatus.toString should be("PENDING")
      PaymentEventBuilder("intRef", "SENT_FOR_AUTHORISATION").paymentStatus.toString should be("PENDING")
      PaymentEventBuilder("intRef", "SHOPPER_REDIRECTED").paymentStatus.toString should be("PENDING")

      PaymentEventBuilder("intRef", "AUTHORISED").paymentStatus.toString should be("PAID")
      PaymentEventBuilder("intRef", "CAPTURED").paymentStatus.toString should be("PAID")
      PaymentEventBuilder("intRef", "SETTLED").paymentStatus.toString should be("PAID")

      PaymentEventBuilder("intRef", "REFUSED").paymentStatus.toString should be("REFUSED")
      PaymentEventBuilder("intRef", "ERROR").paymentStatus.toString should be("REFUSED")
      PaymentEventBuilder("intRef", "EXPIRED").paymentStatus.toString should be("REFUSED")
      PaymentEventBuilder("intRef", "FAILURE").paymentStatus.toString should be("REFUSED")

      PaymentEventBuilder("intRef", "CANCELLED").paymentStatus.toString should be("CANCELLED")

      PaymentEventBuilder("intRef", "unknown").paymentStatus.toString should be("UNKNOWN")
    }
  }
}
