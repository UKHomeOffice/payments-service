package report

import _root_.model.PaymentItem
import builders.PaymentBuilder
import org.joda.time.LocalDate
import org.scalatest.{Matchers, WordSpec}
import report.model.WorldPayReportEntry

class CombinedReportLineItemSpec extends WordSpec with Matchers {

  "CombinedReportLineItem" should {
    "calculate amounts using payment data" in {
      val paymentAmountFromWpEntry = 1000
      val netAmountFromWpEntry = 75
      val commissionAmountFromWpEntry = 25

      val paymentItem = PaymentItem("itemDescription", 50, additionalInformation = Map.empty)
      val payment = PaymentBuilder(paymentItems = paymentItem :: Nil).copy(total = 100)

      val worldPayReportEntry = WorldPayReportEntry(merchantCode = "MERCHANT",
        externalReference = "pnn",
        status = "status",
        date = new LocalDate(2014, 8, 1).toDateTimeAtCurrentTime,
        paymentMethod = "MyCard",
        paymentAmount = paymentAmountFromWpEntry,
        paymentCurrency = "paymentCurrency",
        settlementCurrency = "settlementCurrency",
        commissionAmount = Some(commissionAmountFromWpEntry),
        netAmount = Some(netAmountFromWpEntry))

      val result = CombinedReportLineItem(worldPayReportEntry, Some(payment), Some(payment.paymentItems.head), None)

      result.paymentAmount shouldBe "500.00"
      result.commissionAmount shouldBe "12.50"
      result.netAmount shouldBe "37.50"
      result.grossAmount shouldBe "50.00"
    }

    "calculate amounts when payment not available" in {
      val paymentAmountFromWpEntry = 1000
      val netAmountFromWpEntry = 75
      val commissionAmountFromWpEntry = 25

      val worldPayReportEntry = WorldPayReportEntry(merchantCode = "MERCHANT",
        externalReference = "pnn",
        status = "status",
        date = new LocalDate(2014, 8, 1).toDateTimeAtCurrentTime,
        paymentMethod = "MyCard",
        paymentAmount = paymentAmountFromWpEntry,
        paymentCurrency = "paymentCurrency",
        settlementCurrency = "settlementCurrency",
        commissionAmount = Some(commissionAmountFromWpEntry),
        netAmount = Some(netAmountFromWpEntry))

      val result = CombinedReportLineItem(worldPayReportEntry, None, None, None)

      result.paymentAmount shouldBe "1000.00"
      result.commissionAmount shouldBe "25.00"
      result.netAmount shouldBe "75.00"
      result.grossAmount shouldBe "100.00"
    }

    "calculate correct gross amount when net amount is negative" in {
      val paymentAmountFromWpEntry = 1000
      val commissionAmountFromWpEntry = -25
      val netAmountFromWpEntry = -75


      val worldPayReportEntry = WorldPayReportEntry(merchantCode = "MERCHANT",
        externalReference = "pnn",
        status = "status",
        date = new LocalDate(2014, 8, 1).toDateTimeAtCurrentTime,
        paymentMethod = "MyCard",
        paymentAmount = paymentAmountFromWpEntry,
        paymentCurrency = "paymentCurrency",
        settlementCurrency = "settlementCurrency",
        commissionAmount = Some(commissionAmountFromWpEntry),
        netAmount = Some(netAmountFromWpEntry))

      val result = CombinedReportLineItem(worldPayReportEntry, None, None, None)

      result.paymentAmount shouldBe "1000.00"
      result.commissionAmount shouldBe "-25.00"
      result.netAmount shouldBe "-75.00"
      result.grossAmount shouldBe "-100.00"
    }

    "have additionalDataKeys without value for GROUP_KEY" in {
      val paymentItem = PaymentItem("itemDescription", 50, Map("Visa Validity" -> "6m", CombinedReportLineItem.GROUP_KEY -> "abc"))
      val payment = PaymentBuilder(paymentItems = paymentItem :: Nil).copy(total = 100)

      val worldPayReportEntry = WorldPayReportEntry(merchantCode = "MERCHANT",
        externalReference = "pnn",
        status = "status",
        date = new LocalDate(2014, 8, 1).toDateTimeAtCurrentTime,
        paymentMethod = "MyCard",
        paymentAmount = 1000,
        paymentCurrency = "paymentCurrency",
        settlementCurrency = "settlementCurrency",
        commissionAmount = Some(25),
        netAmount = Some(75))

      val result = CombinedReportLineItem(worldPayReportEntry, Some(payment), Some(payment.paymentItems.head), None)

      result.additionalDataKeys shouldBe Set("Visa Validity")
    }

    "return value associated with GROUP_KEY for the groupValue" in {
      val paymentItem = PaymentItem("itemDescription", 50, Map(CombinedReportLineItem.GROUP_KEY -> "abc"))
      val payment = PaymentBuilder(paymentItems = paymentItem :: Nil).copy(total = 100)

      val worldPayReportEntry = WorldPayReportEntry(merchantCode = "MERCHANT",
        externalReference = "pnn",
        status = "status",
        date = new LocalDate(2014, 8, 1).toDateTimeAtCurrentTime,
        paymentMethod = "MyCard",
        paymentAmount = 1000,
        paymentCurrency = "paymentCurrency",
        settlementCurrency = "settlementCurrency",
        commissionAmount = Some(25),
        netAmount = Some(75))

      val result = CombinedReportLineItem(worldPayReportEntry, Some(payment), Some(payment.paymentItems.head), None)

      result.groupValue shouldBe "abc"
    }

    "return 'unknown' if no value associated with GROUP_KEY" in {
      val paymentItem = PaymentItem("itemDescription", 50)
      val payment = PaymentBuilder(paymentItems = paymentItem :: Nil).copy(total = 100)

      val worldPayReportEntry = WorldPayReportEntry(merchantCode = "MERCHANT",
        externalReference = "pnn",
        status = "status",
        date = new LocalDate(2014, 8, 1).toDateTimeAtCurrentTime,
        paymentMethod = "MyCard",
        paymentAmount = 1000,
        paymentCurrency = "paymentCurrency",
        settlementCurrency = "settlementCurrency",
        commissionAmount = Some(25),
        netAmount = Some(75))

      val result = CombinedReportLineItem(worldPayReportEntry, Some(payment), Some(payment.paymentItems.head), None)

      result.groupValue shouldBe "unknown"
    }

  }
}
