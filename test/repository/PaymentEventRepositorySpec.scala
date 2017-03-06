package repository

import builders.{PaymentBuilder, PaymentEventBuilder}
import model._
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.joda.time.DateTimeUtils._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import reactivemongo.bson.BSONDocument
import utils.Eventually


class PaymentEventRepositorySpec extends WordSpec with Matchers with ScalaFutures with BeforeAndAfter with Eventually {

  val repo = new PaymentEventRepository(DbConnection.db)

  before {
    eventually {
      repo.remove(BSONDocument.empty)
    }
  }

  "save" should {
    "save the given payment paymentEvent" in {
      val paymentEvent = PaymentEventBuilder()

      eventually(repo.insert(paymentEvent))

      repo.findOne(BSONDocument("internalReference" -> paymentEvent.internalReference)).futureValue.get shouldBe paymentEvent
    }
  }

  "findOne" should {
    "be able to read events with refunded and cancelled status" in {

      val paymentEvent1 = PaymentEventBuilder().copy(paymentStatus = Refunded, internalReference = "ref1")
      val paymentEvent2 = PaymentEventBuilder().copy(paymentStatus = Cancelled, internalReference = "ref2")

      eventually(repo.insert(paymentEvent1))
      eventually(repo.insert(paymentEvent2))

      repo.findOne(BSONDocument("internalReference" -> paymentEvent1.internalReference)).futureValue.get shouldBe paymentEvent1
      repo.findOne(BSONDocument("internalReference" -> paymentEvent2.internalReference)).futureValue.get shouldBe paymentEvent2

    }
  }

  "updateTimestamp" should {
    "update timestamp of the latest payment event with value from the given event" in {
      val payment = PaymentBuilder()
      val timestamp = now
      val paymentEvent1 = PaymentEventBuilder(payment, "AUTHORISED", timestamp)
      val paymentEvent2 = PaymentEventBuilder(payment, "AUTHORISED", timestamp.minusHours(5))

      eventually(repo.insert(paymentEvent1))
      eventually(repo.insert(paymentEvent2))

      val updateEvent = PaymentEventBuilder(payment, "AUTHORISED", timestamp.plusHours(5))
      eventually(repo.updateTimestamp(updateEvent))

      repo.findAll(sort = BSONDocument("timestamp" -> -1)).futureValue shouldBe List(updateEvent, paymentEvent2)
    }
  }

  "findLatestStatus for internal reference and amount" should {
    "return latest dated not UNKNOWN PaymentEvent" in {
      val payment = PaymentBuilder()

      val matchingEvent1 = PaymentEventBuilder(payment, "INIT", now.minusDays(10))
      eventually(repo.save(matchingEvent1))

      val matchingEvent2 = PaymentEventBuilder(payment, "AUTHORISED", now.minusDays(1))
      eventually(repo.save(matchingEvent2))

      val matchingEvent3 = PaymentEventBuilder(payment, "CHARGED_BACK", now.minusHours(1))
      eventually(repo.save(matchingEvent3))

      val nonMatchingEvent = PaymentEventBuilder("anotherInternalRef", "AUTHORISED")
      eventually(repo.save(nonMatchingEvent))

      val actualEvent = repo.findLatestStatus(payment.internalReference, payment.total)
      actualEvent.futureValue should be(Some(matchingEvent2))
    }
  }

  "findEventsWithOnlyInitStatus for internal reference" should {
    "return list of PaymentEvents for payments having just PENDING events" in {
      val internalRef = "internalRef"
      val payment1 = PaymentBuilder(internalReference = internalRef)

      val payment1Event1 = PaymentEventBuilder(payment1, "INIT", now.minusDays(10))
      eventually(repo.save(payment1Event1))

      val payment1Event2 = PaymentEventBuilder(payment1, "AUTHORISED", now.minusDays(1))
      eventually(repo.save(payment1Event2))

      val payment2 = PaymentBuilder(internalReference = internalRef)
      val payment2Event1 = PaymentEventBuilder(payment2, "INIT", now.minusHours(3))
      eventually(repo.save(payment2Event1))
      val payment2Event2 = PaymentEventBuilder(payment2, "SHOPPER_REDIRECTED", now.minusHours(1))
      eventually(repo.save(payment2Event2))

      val payment3 = PaymentBuilder(internalReference = internalRef)
      val payment3Event1 = PaymentEventBuilder(payment3, "INIT", now.minusHours(1))
      eventually(repo.save(payment3Event1))

      val foundExternalRefs = repo.findExternalReferencesForPendingPayments(internalRef)
      foundExternalRefs.futureValue should be(List(payment2.externalReference, payment3.externalReference))
    }

    "return list of PaymentEvents for payments having just PENDING or UNKNOWN events" in {
      val internalRef = "internalRef"
      val payment1 = PaymentBuilder(internalReference = internalRef)

      val payment1Event1 = PaymentEventBuilder(payment1, "INIT", now.minusDays(10))
      eventually(repo.save(payment1Event1))

      val payment1Event2 = PaymentEventBuilder(payment1, "AUTHORISED", now.minusDays(1))
      eventually(repo.save(payment1Event2))

      val payment2 = PaymentBuilder(internalReference = internalRef)
      val payment2Event1 = PaymentEventBuilder(payment2, "INIT", now.minusHours(3))
      eventually(repo.save(payment2Event1))
      val payment2Event2 = PaymentEventBuilder(payment2, "null", now.minusHours(1))
      eventually(repo.save(payment2Event2))

      val payment3 = PaymentBuilder(internalReference = internalRef)
      val payment3Event1 = PaymentEventBuilder(payment3, "INIT", now.minusHours(1))
      eventually(repo.save(payment3Event1))

      val foundExternalRefs = repo.findExternalReferencesForPendingPayments(internalRef)
      foundExternalRefs.futureValue should be(List(payment2.externalReference, payment3.externalReference))
    }
  }

  "generateDailyReportBreakDownByPaymentMethod" should {

    val currentTime = new DateTime(2015, 6, 5, 15, 0)

    def inFixedTime(test: => Unit) = {
      setCurrentMillisFixed(currentTime.getMillis)
      test
      setCurrentMillisSystem()
    }

    "prepare report split by payment method" in {
      inFixedTime {
        val payment1 = PaymentBuilder(internalReference = "internalRef1", profile = PaymentProfile("VISA", ""))
        val payment1Event1 = PaymentEventBuilder(payment1, "AUTHORISED", currentTime.minusHours(2))
        eventually(repo.save(payment1Event1))

        val payment2 = PaymentBuilder(internalReference = "internalRef2", profile = PaymentProfile("MAESTRO", ""))
        val payment2Event1 = PaymentEventBuilder(payment2, "AUTHORISED", currentTime.minusHours(2))
        eventually(repo.save(payment2Event1))

        val payment3 = PaymentBuilder(internalReference = "internalRef3", profile = PaymentProfile("VISA", ""))
        val payment3Event1 = PaymentEventBuilder(payment3, "AUTHORISED", currentTime.minusHours(2))
        eventually(repo.save(payment3Event1))

        val payment4 = PaymentBuilder(internalReference = "internalRef4", profile = PaymentProfile("VISA", ""))
        val payment4Event1 = PaymentEventBuilder(payment4, "OPEN", currentTime.minusHours(1))
        eventually(repo.save(payment4Event1))

        eventually(repo.generateDailyReportBreakDownByPaymentMethod) shouldBe Set(
          PaymentMethodSummary("VISA", count = 3, events = Set(EventStatusSummary("PAID", 2), EventStatusSummary("PENDING", 1))),
          PaymentMethodSummary("MAESTRO", count = 1, events = Set(EventStatusSummary("PAID", 1)))
        )
      }
    }

    "prepare report for all events from last day" in {
      inFixedTime {
        val payment1 = PaymentBuilder(internalReference = "internalRef1", profile = PaymentProfile("VISA", ""))
        val payment1Event1 = PaymentEventBuilder(payment1, "AUTHORISED", currentTime.minusHours(2))
        eventually(repo.save(payment1Event1))

        val payment2 = PaymentBuilder(internalReference = "internalRef2", profile = PaymentProfile("MAESTRO", ""))
        val payment2Event1 = PaymentEventBuilder(payment2, "AUTHORISED", currentTime.minusDays(2))
        eventually(repo.save(payment2Event1))

        eventually(repo.generateDailyReportBreakDownByPaymentMethod) shouldBe Set(
          PaymentMethodSummary("VISA", count = 1, events = Set(EventStatusSummary("PAID", 1)))
        )
      }
    }

    "prepare report for all events except 'INIT', 'CAPTURED' and 'SETTLED' " in {
      inFixedTime {
        val payment1 = PaymentBuilder(internalReference = "internalRef1", profile = PaymentProfile("VISA", ""))
        val payment1Event1 = PaymentEventBuilder(payment1, "AUTHORISED", currentTime.minusHours(2))
        eventually(repo.save(payment1Event1))

        val payment2 = PaymentBuilder(internalReference = "internalRef2", profile = PaymentProfile("MAESTRO", ""))
        val payment2Event1 = PaymentEventBuilder(payment2, "INIT", currentTime.minusHours(2))
        eventually(repo.save(payment2Event1))

        val payment3 = PaymentBuilder(internalReference = "internalRef3", profile = PaymentProfile("VISA", ""))
        val payment3Event1 = PaymentEventBuilder(payment3, "CAPTURED", currentTime.minusHours(2))
        eventually(repo.save(payment3Event1))

        val payment4 = PaymentBuilder(internalReference = "internalRef4", profile = PaymentProfile("VISA", ""))
        val payment4Event1 = PaymentEventBuilder(payment4, "SETTLED", currentTime.minusHours(1))
        eventually(repo.save(payment4Event1))

        eventually(repo.generateDailyReportBreakDownByPaymentMethod) shouldBe Set(
          PaymentMethodSummary("VISA", count = 1, events = Set(EventStatusSummary("PAID", 1)))
        )
      }
    }

    "prepare report with deduplicated entries by externalReference" in {
      inFixedTime {
        val payment1 = PaymentBuilder(externalReference = "externalRef1", profile = PaymentProfile("VISA", ""))
        val payment1Event1 = PaymentEventBuilder(payment1, "AUTHORISED", currentTime.minusHours(2))
        eventually(repo.save(payment1Event1))

        val payment2 = PaymentBuilder(externalReference = "externalRef1", profile = PaymentProfile("VISA", ""))
        val payment2Event1 = PaymentEventBuilder(payment2, "AUTHORISED", currentTime.minusHours(2))
        eventually(repo.save(payment2Event1))

        eventually(repo.generateDailyReportBreakDownByPaymentMethod) shouldBe Set(
          PaymentMethodSummary("VISA", count = 1, events = Set(EventStatusSummary("PAID", 1)))
        )
      }
    }

    "deduplicate OPEN, SENT_FOR_AUTHORISATION, SHOPPER_REDIRECTED and map to 'PENDING'" in {
      inFixedTime {
        val payment1 = PaymentBuilder(externalReference = "externalRef1", profile = PaymentProfile("VISA", ""))
        val payment1Event1 = PaymentEventBuilder(payment1, "OPEN", currentTime.minusHours(2))
        eventually(repo.save(payment1Event1))

        val payment2 = PaymentBuilder(externalReference = "externalRef2", profile = PaymentProfile("MAESTRO", ""))
        val payment2Event1 = PaymentEventBuilder(payment2, "SENT_FOR_AUTHORISATION", currentTime.minusHours(2))
        eventually(repo.save(payment2Event1))

        val payment3 = PaymentBuilder(externalReference = "externalRef1", profile = PaymentProfile("VISA", ""))
        val payment3Event1 = PaymentEventBuilder(payment3, "SHOPPER_REDIRECTED", currentTime.minusHours(2))
        eventually(repo.save(payment3Event1))

        val payment4 = PaymentBuilder(externalReference = "externalRef4", profile = PaymentProfile("VISA", ""))
        val payment4Event1 = PaymentEventBuilder(payment4, "OPEN", currentTime.minusHours(1))
        eventually(repo.save(payment4Event1))

        eventually(repo.generateDailyReportBreakDownByPaymentMethod) shouldBe Set(
          PaymentMethodSummary("VISA", count = 2, events = Set(EventStatusSummary("PENDING", 2))),
          PaymentMethodSummary("MAESTRO", count = 1, events = Set(EventStatusSummary("PENDING", 1)))
        )
      }
    }

    "map AUTHORISED as 'PAID'" in {
      inFixedTime {
        val payment1 = PaymentBuilder(internalReference = "internalRef1", profile = PaymentProfile("VISA", ""))
        val payment1Event1 = PaymentEventBuilder(payment1, "AUTHORISED", currentTime.minusHours(2))
        eventually(repo.save(payment1Event1))

        eventually(repo.generateDailyReportBreakDownByPaymentMethod) shouldBe Set(
          PaymentMethodSummary("VISA", count = 1, events = Set(EventStatusSummary("PAID", 1)))
        )
      }
    }

    "map ERROR, EXPIRED and FAILURE as 'FAILURE'" in {
      inFixedTime {
        val payment1 = PaymentBuilder(internalReference = "internalRef1", profile = PaymentProfile("VISA", ""))
        val payment1Event1 = PaymentEventBuilder(payment1, "ERROR", currentTime.minusHours(2))
        eventually(repo.save(payment1Event1))

        val payment2 = PaymentBuilder(internalReference = "internalRef2", profile = PaymentProfile("MAESTRO", ""))
        val payment2Event1 = PaymentEventBuilder(payment2, "EXPIRED", currentTime.minusHours(2))
        eventually(repo.save(payment2Event1))

        val payment3 = PaymentBuilder(internalReference = "internalRef3", profile = PaymentProfile("VISA", ""))
        val payment3Event1 = PaymentEventBuilder(payment3, "FAILURE", currentTime.minusHours(2))
        eventually(repo.save(payment3Event1))

        eventually(repo.generateDailyReportBreakDownByPaymentMethod) shouldBe Set(
          PaymentMethodSummary("VISA", count = 2, events = Set(EventStatusSummary("FAILURE", 2))),
          PaymentMethodSummary("MAESTRO", count = 1, events = Set(EventStatusSummary("FAILURE", 1)))
        )
      }
    }

    "leave a worldpay status as is if it's one of CANCELLED, REFUNDED or REFUSED" in {
      inFixedTime {
        val payment1 = PaymentBuilder(internalReference = "internalRef1", profile = PaymentProfile("VISA", ""))
        val payment1Event1 = PaymentEventBuilder(payment1, "CANCELLED", currentTime.minusHours(2))
        eventually(repo.save(payment1Event1))

        val payment2 = PaymentBuilder(internalReference = "internalRef2", profile = PaymentProfile("MAESTRO", ""))
        val payment2Event1 = PaymentEventBuilder(payment2, "REFUNDED", currentTime.minusHours(2))
        eventually(repo.save(payment2Event1))

        val payment3 = PaymentBuilder(internalReference = "internalRef3", profile = PaymentProfile("VISA", ""))
        val payment3Event1 = PaymentEventBuilder(payment3, "REFUSED", currentTime.minusHours(2))
        eventually(repo.save(payment3Event1))

        eventually(repo.generateDailyReportBreakDownByPaymentMethod) shouldBe Set(
          PaymentMethodSummary("VISA", count = 2, events = Set(EventStatusSummary("CANCELLED", 1), EventStatusSummary("REFUSED", 1))),
          PaymentMethodSummary("MAESTRO", count = 1, events = Set(EventStatusSummary("REFUNDED", 1)))
        )
      }
    }

    "map a worldPay status to OTHER if not one of INIT, OPEN, SHOPPER_REDIRECTED, SENT_FOR_AUTHORISATION, AUTHORISED, ERROR, EXPIRED, FAILURE, CANCELLED, REFUNDED, REFUSED" in {
      inFixedTime {
        val payment1 = PaymentBuilder(internalReference = "internalRef1", profile = PaymentProfile("VISA", ""))
        val payment1Event1 = PaymentEventBuilder(payment1, "CHARGED_BACK", currentTime.minusHours(2))
        eventually(repo.save(payment1Event1))

        eventually(repo.generateDailyReportBreakDownByPaymentMethod) shouldBe Set(
          PaymentMethodSummary("VISA", count = 1, events = Set(EventStatusSummary("OTHER", 1)))
        )
      }
    }
  }
}

