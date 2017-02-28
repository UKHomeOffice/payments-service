package report

import _root_.model.Payment
import logging.Logging
import org.joda.time.DateTime
import report.model._
import repository.{PaymentEventRepository, PaymentRepository, PaymentTransactionReportRepository}

import logging.MdcExecutionContext.Implicit.defaultContext
import scala.concurrent.{Future, future}

class WorldPayTransactionReportService(reportRepository: PaymentTransactionReportRepository,
                                       paymentRepository: PaymentRepository,
                                       paymentEventRepository: PaymentEventRepository,
                                       xmlExtractor: WorldPayXmlExtractor = new WorldPayXmlExtractor(),
                                       reportFormatter: ReportFormatter = new ReportFormatter())
  extends Logging {

  def saveRawReport(report: String): Future[PaymentTransactionReport] = {
    val batchId = xmlExtractor.extractBatchId(report)
    reportRepository.save(PaymentTransactionReport(batchId, report, DateTime.now))
  }

  def buildFinancialReport(transactionReport: PaymentTransactionReport): Future[FinancialReport] = {
    val worldPayEntries = future {
      xmlExtractor.extractReportEntries(transactionReport.report)
    }
    val paymentStateToCombinedItems: Future[Map[String, List[CombinedReportLineItem]]] = worldPayEntries.flatMap(buildCombinedReportLineItems(_))
    val reportLineItemsF = paymentStateToCombinedItems.map(reportFormatter.formatReport)
    val rollUpReportLineItemsF = paymentStateToCombinedItems.map(reportFormatter.buildRollUpReportLineItems)
    val merchantCodeF = worldPayEntries.map(_.headOption.map(r => r.merchantCode).getOrElse(""))

    for {
      reportLineItems <- reportLineItemsF
      rollUpReportLineItems <- rollUpReportLineItemsF
      merchantCode <- merchantCodeF
    } yield FinancialReport(merchantCode, transactionReport.batchId.getOrElse(""), reportLineItems, rollUpReportLineItems)
  }

  private def buildCombinedReportLineItems(wpEntries: Seq[WorldPayReportEntry]): Future[Map[String, List[CombinedReportLineItem]]] = {
    Future.sequence(
      wpEntries.map { wpEntry =>
        findPaymentByExternalRef(wpEntry).flatMap {
          case Some(p) => combinedLineItemsWithLatestEvent(p, wpEntry)
          case None => Future.successful(CombinedReportLineItem(wpEntry, None, None, None) :: Nil)
        }
      }
    ).map(_.flatten).map(reportFormatter.groupByPaymentState)
  }


  private def combinedLineItemsWithLatestEvent(p: Payment, wpEntry: WorldPayReportEntry): Future[List[CombinedReportLineItem]] =
    paymentEventRepository.findLatestStatus(p.internalReference, p.total).map(pe => p.paymentItems.map(pi => CombinedReportLineItem(wpEntry, Some(p), Some(pi), pe)))

  private def findPaymentByExternalRef(entry: WorldPayReportEntry): Future[Option[Payment]] = {
    paymentRepository.findByExternalReference(entry.externalReference).map {
      case None =>
        logger.warn(s"No payment with external reference = ${entry.externalReference}")
        None
      case p@Some(_) => p
    }
  }
}
