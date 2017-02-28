package report

import _root_.model.Types.WorldPayStatus

import scala.language.postfixOps

class ReportFormatter {

  type StatusAggregate = Map[WorldPayStatus, String]
  
  def buildRollUpReportLineItems(reportLinesByStatus: Map[String, List[CombinedReportLineItem]]): List[Row] =
    List("PRODUCT DESCRIPTION", "SETTLED", "REFUNDED", "CHARGED_BACK") :: getRolledUpReportLineItems(reportLinesByStatus.values.flatten.toList)


  private[report] def getRolledUpReportLineItems(combinedReportLines: List[CombinedReportLineItem]): List[Row] = {
    val itemsGroupedByOrderDescription = combinedReportLines.groupBy(_.groupValue)
    val orderDescriptionToStatusAggregate: Map[String, StatusAggregate] = amountAggregateByStatus(itemsGroupedByOrderDescription)
    orderDescriptionToStatusAggregate.map{
      case (description, amounts) =>
        List(description, amounts.getOrElse("SETTLED", ""), amounts.getOrElse("REFUNDED", ""), amounts.getOrElse("CHARGED_BACK", ""))
    } toList
  }

  private def amountAggregateByStatus(groupedByOrderDescription: Map[String, List[CombinedReportLineItem]]) = groupedByOrderDescription.map {
    case (description, lineItems) => description -> {
      lineItems.groupBy(_.eventType).map {
        case (eventType, line) => (eventType, line.map(line => BigDecimal(line.grossAmount)).sum.toString())
      }
    }
  }

  def groupByPaymentState(combinedReportLineItems: Seq[CombinedReportLineItem]): Map[String, List[CombinedReportLineItem]] = {
    combinedReportLineItems groupBy {
      case x if x.payment.isEmpty => "allFailuresRecords"
      case x if x.paymentEvent.exists(_.isOfTypeInitialized) => "allIncompleteApplicationRecords"
      case _ => "allSuccessfulRecords"
    } mapValues (_.toList)
  }

  def formatReport(reportLines: Map[String, List[CombinedReportLineItem]]): List[Row] = {
    val additionalColumns = reportLines.flatMap{case (_,value)=> value.flatMap(li => li.additionalDataKeys) }.toSet

    val columnHeaders = List("Event Type", "Date", "PNN", "Payment Method", "Payment Amount",
      "Payment Currency", "Settlement Currency", "Commission Amount", "NetAmount", "Gross Amount", "Order Description") ::: additionalColumns.toList

    val allFailuresRecords = reportLines.get("allFailuresRecords").map(x => addHeadingRow(x, "No records of payment id's", columnHeaders) ::: convert(x, additionalColumns)).getOrElse(List.empty)

    val allIncompleteApplicationRecords = reportLines.get("allIncompleteApplicationRecords").map(x => addHeadingRow(x, "Applications not complete", columnHeaders) ::: convert(x, additionalColumns)).getOrElse(List.empty)

    val allSuccessfulRecords = allFailuresRecords ++ allIncompleteApplicationRecords match {
      case Nil => reportLines.get("allSuccessfulRecords").map(convert(_, additionalColumns)).getOrElse(Nil)
      case _ => reportLines.get("allSuccessfulRecords").map(x => addHeadingRow(x, "Completed Applications", columnHeaders) ::: convert(x, additionalColumns)).getOrElse(List.empty)
    }

    columnHeaders :: allFailuresRecords ++ allIncompleteApplicationRecords ++ allSuccessfulRecords
  }

  private def addHeadingRow(reportLines: Seq[CombinedReportLineItem], heading: String, columnHeaders: List[String]): List[Row] =
    reportLines match {
      case Nil => List.empty[Row]
      case _ => List(List(heading) ::: List.fill(columnHeaders.size - 1)(""))
    }

  private def convert(reportLines: Seq[CombinedReportLineItem], additionalColumns: Set[String]): List[Row] =
    reportLines.map(_.asList(additionalColumns)).toList
}
