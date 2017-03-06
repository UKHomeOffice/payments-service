package report

import _root_.model.PaymentItem
import builders.{CombinedReportLineItemBuilder, PaymentBuilder, PaymentEventBuilder}
import org.joda.time.LocalDate
import org.scalatest.{Matchers, WordSpec}
import report.model.WorldPayReportEntry

class ReportFormatterSpec extends WordSpec with Matchers {

  val formatter = new ReportFormatter()

  val goodLineItem1 = CombinedReportLineItemBuilder(itemDescription = "itemDescription", itemPrice = 20)
  val goodLineItem2 = CombinedReportLineItemBuilder(itemDescription = "itemDescription", itemPrice = 20)
  val goodLineItem3 = CombinedReportLineItemBuilder(itemDescription = "itemDescription", itemPrice = 20)
  val allSuccessfulRecords = List(goodLineItem1, goodLineItem2, goodLineItem3)

  val incompletePayment = PaymentBuilder().copy(total = 100)
  val incompletePaymentEvent = PaymentEventBuilder(incompletePayment, "INIT")
  val incompletePaymentItem = PaymentItem("aProductTitle", 25, additionalInformation = Map("Visa Validity Period" -> "6 months", "Post ID" -> "post1", "GWF" -> "gwfNumber"))
  val incompleteWorldPayReportEntry = WorldPayReportEntry(merchantCode = "MERCHANT",
    externalReference = "pnn",
    status = "SETTLED",
    date = new LocalDate(2014, 8, 1).toDateTimeAtCurrentTime,
    paymentMethod = "MyCard",
    paymentAmount = 100.00,
    paymentCurrency = "paymentCurrency",
    settlementCurrency = "settlementCurrency",
    commissionAmount = Some(20.00),
    netAmount = Some(30.00)
  )
  val incompleteLineItem = CombinedReportLineItem(incompleteWorldPayReportEntry, Some(incompletePayment), Some(incompletePayment.paymentItems.head), Some(incompletePaymentEvent))

  val failureWorldPayReportEntry = WorldPayReportEntry(merchantCode = "MERCHANT",
    externalReference = "pnn",
    status = "SETTLED",
    date = new LocalDate(2014, 8, 1).toDateTimeAtCurrentTime,
    paymentMethod = "MyCard",
    paymentAmount = 100.00,
    paymentCurrency = "paymentCurrency",
    settlementCurrency = "settlementCurrency",
    commissionAmount = Some(20.00),
    netAmount = Some(30.00)
  )
  val failureLineItem = CombinedReportLineItem(failureWorldPayReportEntry, None, None, None)

  val mapFromLineStatusToCombinedLineItems = Map("allFailuresRecords" -> List(failureLineItem),
    "allIncompleteApplicationRecords" -> List(incompleteLineItem),
    "allSuccessfulRecords" -> allSuccessfulRecords)

  "buildRollUpReportLineItem" should {
    "returns successful rows with header" in {
      val rolledUpLineItem = List("6 months", "20.00", "", "")
      val reportLinesByStatus = Map("allSuccessfulRecords" -> List(goodLineItem1))

      formatter.buildRollUpReportLineItems(reportLinesByStatus) shouldBe List("PRODUCT DESCRIPTION", "SETTLED", "REFUNDED", "CHARGED_BACK") :: rolledUpLineItem :: Nil
    }

    "returns both found and not found values in the roll-up" in {
      val successfulRolledUpLineItem = List("6 months", "20.00", "", "")
      val unknownRolledUpLineItem = List("unknown", "50.00", "", "")
      val reportLinesByStatus = Map("allSuccessfulRecords" -> List(goodLineItem1), "allFailureRecords" -> List(failureLineItem))

      formatter.buildRollUpReportLineItems(reportLinesByStatus) shouldBe
        List(List("PRODUCT DESCRIPTION", "SETTLED", "REFUNDED", "CHARGED_BACK"), successfulRolledUpLineItem, unknownRolledUpLineItem)
    }
  }

  "groupReportByApplicationState" should {
    "group line items" in {
      val result = formatter.groupByPaymentState(Seq(goodLineItem1, goodLineItem2, incompleteLineItem, failureLineItem, goodLineItem3))
      result shouldBe mapFromLineStatusToCombinedLineItems
    }
  }

  "formatReport" should {
    val additionalColumns = Set("Visa Validity", "Post ID", "GWF")
    val rowTitles = List("Event Type", "Date", "PNN", "Payment Method", "Payment Amount", "Payment Currency", "Settlement Currency", "Commission Amount", "NetAmount", "Gross Amount", "Order Description") ::: additionalColumns.toList
    val noRecordsBlankLine = "No records of payment id's" :: List.fill(rowTitles.size - 1)("")
    val applicationNotCompletedBlankLine = "Applications not complete" :: List.fill(rowTitles.size - 1)("")
    val completedApplication = "Completed Applications" :: List.fill(rowTitles.size - 1)("")

    "group the records with a header as separator" in {
      val map = allSuccessfulRecords.map(_.asList(additionalColumns))
      val expectedReportLines = List(rowTitles, noRecordsBlankLine, failureLineItem.asList(additionalColumns), applicationNotCompletedBlankLine, incompleteLineItem.asList(additionalColumns), completedApplication) ::: map
      formatter.formatReport(mapFromLineStatusToCombinedLineItems) shouldBe expectedReportLines
    }
  }

  "getRolledUpReportLineItems" should {

    "sum of all the gross amounts per group and event type from combined report" in {
      val settled6Months1 = CombinedReportLineItemBuilder(itemPrice = 20, additionalInformation = Map(CombinedReportLineItem.GROUP_KEY -> "6 months"))
      val settledTwoYears1 = CombinedReportLineItemBuilder(itemPrice = 50, additionalInformation = Map(CombinedReportLineItem.GROUP_KEY -> "1 year/2 years"))
      val settledTwoYears2 = CombinedReportLineItemBuilder(itemPrice = 50, additionalInformation = Map(CombinedReportLineItem.GROUP_KEY -> "1 year/2 years"))
      val settledOneYears1 = CombinedReportLineItemBuilder(itemPrice = 50, additionalInformation = Map(CombinedReportLineItem.GROUP_KEY -> "1 year/2 years"))
      val settledOneYears2 = CombinedReportLineItemBuilder(itemPrice = 50, additionalInformation = Map(CombinedReportLineItem.GROUP_KEY -> "1 year/2 years"))
      val settledPriorityService1 = CombinedReportLineItemBuilder(itemPrice = 20, additionalInformation = Map(CombinedReportLineItem.GROUP_KEY -> "Priority Service"))
      val settledSuperPriorityService1 = CombinedReportLineItemBuilder(itemPrice = 20, additionalInformation = Map(CombinedReportLineItem.GROUP_KEY -> "Super Priority Service"))
      val refundedTwoYears1 = CombinedReportLineItemBuilder(itemPrice = 20, eventStatus = "REFUNDED", additionalInformation = Map(CombinedReportLineItem.GROUP_KEY -> "1 year/2 years"))
      val refundedSuperPriority1 = CombinedReportLineItemBuilder(itemPrice = 50, eventStatus = "REFUNDED",  additionalInformation = Map(CombinedReportLineItem.GROUP_KEY -> "Super Priority Service"))
      val refundedSuperPriority2 = CombinedReportLineItemBuilder(itemPrice = 20, eventStatus = "REFUNDED", additionalInformation = Map(CombinedReportLineItem.GROUP_KEY ->  "Super Priority Service"))
      val chargedBackTwoYears1 = CombinedReportLineItemBuilder(itemPrice = 20, eventStatus = "CHARGED_BACK", additionalInformation = Map(CombinedReportLineItem.GROUP_KEY ->  "1 year/2 years"))

      val rolledUpReportLineItems = formatter.getRolledUpReportLineItems(List(settled6Months1, refundedTwoYears1, settledTwoYears1, settledTwoYears2, settledPriorityService1,
        settledOneYears1, settledOneYears2, settledSuperPriorityService1, refundedSuperPriority1, refundedSuperPriority2, chargedBackTwoYears1))

      rolledUpReportLineItems.size shouldBe 4
      rolledUpReportLineItems(0) shouldBe List("6 months", "20.00", "", "")
      rolledUpReportLineItems(1) shouldBe List("1 year/2 years", "200.00", "20.00", "20.00")
      rolledUpReportLineItems(2) shouldBe List("Super Priority Service", "20.00", "70.00", "")
      rolledUpReportLineItems(3) shouldBe List("Priority Service", "20.00", "", "")
    }
  }

}
