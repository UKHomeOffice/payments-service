package report

import _root_.model.Types._
import _root_.model.{Payment, PaymentEvent, PaymentItem}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import report.model.WorldPayReportEntry

case class CombinedReportLineItem(worldPayEntry: WorldPayReportEntry,
                                  payment: Option[Payment],
                                  paymentItem: Option[PaymentItem],
                                  paymentEvent: Option[PaymentEvent]) {

  private val paymentItemPrice = paymentItem.map(_.price).getOrElse(BigDecimal(0))
  private val proRata = payment.map(paymentItemPrice / _.total).getOrElse(BigDecimal(1))

  val eventType: WorldPayStatus = worldPayEntry.status

  val date: String = new DateTime(worldPayEntry.date).toString(CombinedReportLineItem.worldPayFormat)

  val externalReference: ExternalReference = worldPayEntry.externalReference

  val paymentMethod: PaymentType = worldPayEntry.paymentMethod

  val paymentAmount: String = proRataPrice(worldPayEntry.paymentAmount)

  val paymentCurrency: Currency = worldPayEntry.paymentCurrency

  val settlementCurrency: Currency = worldPayEntry.settlementCurrency

  val commissionAmount: String = proRataPrice(worldPayEntry.commissionAmount.getOrElse(BigDecimal(0)))

  val netAmount: String = proRataPrice(worldPayEntry.netAmount.getOrElse(BigDecimal(0)))

  val grossAmount: String = proRataPrice(worldPayEntry.grossAmount)

  val orderDescription: String = paymentItem.map(_.description).getOrElse("unknown")

  val additionalDataKeys: Set[String] = paymentItem.map(_.additionalInformation.keySet.filter(_ != CombinedReportLineItem.GROUP_KEY)).getOrElse(Set.empty)

  val groupValue: String = paymentItem.flatMap(_.additionalInformation.get(CombinedReportLineItem.GROUP_KEY)).getOrElse("unknown")

  private def proRataPrice(amount: BigDecimal) = formatAmount(amount.toDouble * proRata)

  private def formatAmount(amount: BigDecimal) = amount.formatted(CombinedReportLineItem.amountFormat)

  def asList(additionalColumns: Set[String]): List[String] = List(
    eventType,
    date,
    externalReference,
    paymentMethod,
    paymentAmount,
    paymentCurrency,
    settlementCurrency,
    commissionAmount,
    netAmount,
    grossAmount,
    orderDescription
  ) ::: additionalColumns.map(paymentAdditionalInformation.getOrElse(_, "")).toList

  private def paymentAdditionalInformation = paymentItem.map(_.additionalInformation).getOrElse(Map.empty)
}

object CombinedReportLineItem {
  val GROUP_KEY = "Group Value"
  private val amountFormat = "%.2f"
  private val worldPayFormat = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss")
}
