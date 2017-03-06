package model

import model.Types.{Currency, ExternalReference, InternalReference}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONHandler, Macros}
import reactivemongo.extensions.dao.Handlers._
import repository.TypeHandlers._

case class PaymentEventNotification(externalReference: Option[ExternalReference], internalReference: InternalReference, amount: BigDecimal, currency: Option[Currency], paymentStatus: PaymentStatus)

object PaymentEventNotification {

  implicit val dbReadWriteHandler: BSONDocumentReader[PaymentEventNotification] with BSONDocumentWriter[PaymentEventNotification] with BSONHandler[BSONDocument, PaymentEventNotification] = Macros.handler[PaymentEventNotification]

  def notPaid(externalReference: Option[ExternalReference], internalReference: InternalReference, amount: BigDecimal): PaymentEventNotification = {
    apply(externalReference, internalReference, amount, None, Unknown)
  }
}

sealed trait PaymentStatus

case object Paid extends PaymentStatus {
  override def toString = "PAID"
}

case object Pending extends PaymentStatus {
  override def toString = "PENDING"
}

case object Unknown extends PaymentStatus {
  override def toString = "UNKNOWN"
}

case object Refused extends PaymentStatus {
  override def toString = "REFUSED"
}

case object Cancelled extends PaymentStatus {
  override def toString = "CANCELLED"
}

case object Refunded extends PaymentStatus {
  override def toString = "REFUNDED"
}