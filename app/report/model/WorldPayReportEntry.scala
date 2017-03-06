package report.model

import model.Types.{Currency, PaymentType, WorldPayStatus, ExternalReference}
import org.joda.time.DateTime

case class WorldPayReportEntry(merchantCode: String,
                               externalReference: ExternalReference,
                               status: WorldPayStatus,
                               date: DateTime,
                               paymentMethod: PaymentType,
                               paymentCurrency: Currency,
                               paymentAmount: BigDecimal,
                               settlementCurrency: Currency,
                               commissionAmount: Option[BigDecimal],
                               netAmount: Option[BigDecimal],
                               grossAmount: BigDecimal)

object WorldPayReportEntry {
  def apply(merchantCode: String,
            externalReference: String,
            status: String,
            date: DateTime,
            paymentMethod: String,
            paymentCurrency: String,
            paymentAmount: BigDecimal,
            settlementCurrency: String,
            commissionAmount: Option[BigDecimal],
            netAmount: Option[BigDecimal]): WorldPayReportEntry = {
    val grossAmount = commissionAmount.getOrElse(BigDecimal(0)) + netAmount.getOrElse(BigDecimal(0))
    WorldPayReportEntry(merchantCode, externalReference, status, date, paymentMethod, paymentCurrency, paymentAmount, settlementCurrency, commissionAmount, netAmount, grossAmount)
  }
}
