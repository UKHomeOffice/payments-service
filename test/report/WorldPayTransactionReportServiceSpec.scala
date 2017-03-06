package report

import _root_.model.PaymentItem
import builders.{PaymentBuilder, PaymentEventBuilder}
import org.joda.time.DateTime.now
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import report.model.{FinancialReport, PaymentTransactionReport, WorldPayReportEntry}
import repository.{PaymentEventRepository, PaymentRepository, PaymentTransactionReportRepository}
import utils.{FixedTime, Mocking}

import scala.collection.immutable.Seq
import scala.concurrent.Future

class WorldPayTransactionReportServiceSpec extends WordSpec with Matchers with Mocking with BeforeAndAfter with FixedTime with ScalaFutures {

  val reportRepository = mock[PaymentTransactionReportRepository]
  val paymentRepository = mock[PaymentRepository]
  val paymentEventRepository = mock[PaymentEventRepository]
  val xmlExtractor = mock[WorldPayXmlExtractor]
  val reportFormatter = mock[ReportFormatter]
  val service = new WorldPayTransactionReportService(reportRepository, paymentRepository, paymentEventRepository, xmlExtractor, reportFormatter)

  val merchantCode = "MERCHANT_CODE"
  val batchId = "5"

  def worldPayReportEntry = WorldPayReportEntry(merchantCode = merchantCode,
    externalReference = "pnn",
    status = "REFUNDED",
    date = now,
    paymentMethod = "MyCard",
    paymentAmount = 90.00,
    paymentCurrency = "paymentCurrency",
    settlementCurrency = "settlementCurrency",
    commissionAmount = Some(30.00),
    netAmount = Some(45.00))

  "saveRawReport" should {
    "persist report" in {
      val report = "report"
      val batchId = Some("batchId")
      when(xmlExtractor.extractBatchId(report)).thenReturn(batchId)

      service.saveRawReport(report)

      verify(reportRepository).save(PaymentTransactionReport(batchId, report, now))
    }
  }

  "buildFinancialReport" should {
    "return report" in {
      val report = "report"
      val paymentItems = List(PaymentItem("code", 234.56, Map("duration" -> "1 year")))
      val payment = PaymentBuilder().copy(paymentItems = paymentItems)
      val paymentEvent = PaymentEventBuilder(payment, "AUTHORISED")
      when(xmlExtractor.extractReportEntries(report)).thenReturn(Seq(worldPayReportEntry))

      when(paymentRepository.findByExternalReference(worldPayReportEntry.externalReference)).thenReturn(Future.successful(Some(payment)))
      when(paymentEventRepository.findLatestStatus(payment.internalReference, payment.total)).thenReturn(Future.successful(Some(paymentEvent)))

      val combinedItem = CombinedReportLineItem(worldPayReportEntry, Some(payment), Some(payment.paymentItems.head), Some(paymentEvent))
      val itemsGroupedByPayment = Map("successfulRecords" -> List(combinedItem))
      when(reportFormatter.groupByPaymentState(Seq(combinedItem))).thenReturn(itemsGroupedByPayment)

      val reportLines = List(List("columnA"), List("valueA"))
      when(reportFormatter.formatReport(itemsGroupedByPayment)).thenReturn(reportLines)

      val rollUpReportLines = List(List("SETTLED", "10.0", "0.0", "1.0"))
      when(reportFormatter.buildRollUpReportLineItems(itemsGroupedByPayment)).thenReturn(rollUpReportLines)

      val financialReport = service.buildFinancialReport(PaymentTransactionReport(Some(batchId), report, now))

      financialReport.futureValue should be(FinancialReport(merchantCode, batchId, reportLines, rollUpReportLines))
    }

    "return report if no payment record found" in {
      val report = "report"
      when(xmlExtractor.extractReportEntries(report)).thenReturn(Seq(worldPayReportEntry))

      when(paymentRepository.findByExternalReference(worldPayReportEntry.externalReference)).thenReturn(Future.successful(None))

      val combinedItem = CombinedReportLineItem(worldPayReportEntry, None, None, None)
      val itemsGroupedByPayment = Map("successfulRecords" -> List(combinedItem))
      when(reportFormatter.groupByPaymentState(Seq(combinedItem))).thenReturn(itemsGroupedByPayment)

      val reportLines = List(List("columnA"), List("valueA"))
      when(reportFormatter.formatReport(itemsGroupedByPayment)).thenReturn(reportLines)

      val rollUpReportLines = List(List("SETTLED", "10.0", "0.0", "1.0"))
      when(reportFormatter.buildRollUpReportLineItems(itemsGroupedByPayment)).thenReturn(rollUpReportLines)

      val financialReport = service.buildFinancialReport(PaymentTransactionReport(Some(batchId), report, now))

      financialReport.futureValue should be(FinancialReport(merchantCode, batchId, reportLines, rollUpReportLines))
    }
  }
}
