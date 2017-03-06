package model

import model.Types.{Currency, ExternalReference, InternalReference, WorldPayStatus}
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONHandler, Macros}
import reactivemongo.extensions.dao.Handlers._
import repository.TypeHandlers._

case class PaymentEvent(externalReference: ExternalReference, internalReference: InternalReference, paymentId: Option[String], worldPayStatus: WorldPayStatus, amount: BigDecimal, currency: Currency, method: String, paymentStatus: PaymentStatus, timestamp: DateTime) {
  def isOfTypeInitialized: Boolean = worldPayStatus == "INIT"

  def isOfTypeUnknown: Boolean = paymentStatus == Unknown
}

object PaymentEvent {
  implicit val dbReadWriteHandler: BSONDocumentReader[PaymentEvent] with BSONDocumentWriter[PaymentEvent] with BSONHandler[BSONDocument, PaymentEvent] = Macros.handler[PaymentEvent]

  def apply(externalReference: ExternalReference, internalReference: InternalReference, paymentId: Option[String], worldPayStatus: WorldPayStatus, amount: BigDecimal, currency: Currency, method: String, timestamp: DateTime = now): PaymentEvent =
    PaymentEvent(externalReference, internalReference, paymentId, worldPayStatus, amount, currency, method, paymentStatus(worldPayStatus), timestamp)

  def withInitStatus(payment: Payment) = PaymentEvent(payment.externalReference, payment.internalReference, None, "INIT", payment.total, payment.currency, payment.profile.paymentType)

  private def paymentStatus(worldPayStatus: WorldPayStatus) = worldPayStatus match {
    case "INIT" | "OPEN" | "SENT_FOR_AUTHORISATION" | "SHOPPER_REDIRECTED" => Pending
    case "AUTHORISED" | "CAPTURED" | "SETTLED" | "SETTLED_BY_MERCHANT" => Paid
    case "REFUSED" | "ERROR" | "EXPIRED" | "FAILURE" => Refused
    case "CANCELLED" => Cancelled
    case "REFUNDED" => Refunded
    case _ => Unknown
  }
}