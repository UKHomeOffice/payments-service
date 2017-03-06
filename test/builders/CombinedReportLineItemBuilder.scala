package builders

import model.PaymentItem
import org.joda.time.LocalDate
import report.CombinedReportLineItem
import report.model.WorldPayReportEntry

object CombinedReportLineItemBuilder {

  def apply(itemDescription: String = "itemDescription", itemPrice: BigDecimal, grossAmount: BigDecimal = 50, eventStatus: String = "SETTLED", additionalInformation: Map[String, String] = Map("Visa Validity" -> "6 months", "Post ID" -> "post1", "GWF" -> "gwfNumber", CombinedReportLineItem.GROUP_KEY -> "6 months" )): CombinedReportLineItem = {
    val paymentItem = PaymentItem(itemDescription, itemPrice, additionalInformation = additionalInformation)
    val payment = PaymentBuilder(paymentItems = paymentItem :: Nil).copy(total = grossAmount)
    val paymentEvent = PaymentEventBuilder(payment, "AUTHORISED")

    val commissionAmount = grossAmount - 10
    val worldPayReportEntry = WorldPayReportEntry(merchantCode = "MERCHANT",
      externalReference = "pnn",
      status = eventStatus,
      date = new LocalDate(2014, 8, 1).toDateTimeAtCurrentTime,
      paymentMethod = "MyCard",
      paymentAmount = payment.total,
      paymentCurrency = "paymentCurrency",
      settlementCurrency = "settlementCurrency",
      commissionAmount = Some(commissionAmount),
      netAmount = Some(grossAmount - commissionAmount))
    CombinedReportLineItem(worldPayReportEntry, Some(payment), Some(payment.paymentItems.head), Some(paymentEvent))
  }
}
