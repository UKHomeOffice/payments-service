package model

import controller.PaymentStartRequest
import model.Types._
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONHandler, Macros}
import reactivemongo.extensions.dao.Handlers._
import repository.TypeHandlers._
import controller.PaymentStartRequest

case class PaymentItem(description: String, price: BigDecimal, additionalInformation: Map[String, String] = Map.empty[String, String])

object PaymentItem {

  implicit val dbReadWriteHandler: BSONDocumentReader[PaymentItem] with BSONDocumentWriter[PaymentItem] with BSONHandler[BSONDocument, PaymentItem] = Macros.handler[PaymentItem]
}


case class BillingAddress(line1: String,
                          line2: Option[String],
                          line3: Option[String],
                          townCity: String,
                          province: Option[String],
                          postCode: Option[String],
                          countryCode: String = "GBR")

object BillingAddress {
  implicit val dbReadWriteHandler: BSONDocumentReader[BillingAddress] with BSONDocumentWriter[BillingAddress] with BSONHandler[BSONDocument, BillingAddress] = Macros.handler[BillingAddress]
}


case class Payee(title: Option[String], givenName: Option[String], familyName: Option[String], email: Option[String], phoneNumber: Option[String], billingAddress: Option[BillingAddress]) {
  def fullName: PaymentType = s"${title.getOrElse("")} ${givenName.getOrElse("")} ${familyName.getOrElse("")}".trim
}

object Payee {
  implicit val dbReadWriteHandler: BSONDocumentReader[Payee] with BSONDocumentWriter[Payee] with BSONHandler[BSONDocument, Payee] = Macros.handler[Payee]
}


case class PaymentProfile(paymentType: PaymentType, region: Region)

object PaymentProfile {
  implicit val dbReadWriteHandler: BSONDocumentReader[PaymentProfile] with BSONDocumentWriter[PaymentProfile] with BSONHandler[BSONDocument, PaymentProfile] = Macros.handler[PaymentProfile]
}

case class Payment(externalReference: ExternalReference,
                   internalReference: InternalReference,
                   payee: Payee,
                   description: String,
                   profile: PaymentProfile,
                   paymentItems: List[PaymentItem],
                   total: BigDecimal,
                   currency: Currency,
                   clientId: String,
                   status: PaymentStatus = Unknown
                    )


object Payment {
  implicit val dbReadWriteHandler: BSONDocumentReader[Payment] with BSONDocumentWriter[Payment] with BSONHandler[BSONDocument, Payment] = Macros.handler[Payment]

  def apply(paymentStartRequest: PaymentStartRequest, clientId: String): Payment = {
    Payment(
      paymentStartRequest.externalReference,
      paymentStartRequest.internalReference,
      paymentStartRequest.payee,
      paymentStartRequest.description,
      paymentStartRequest.profile,
      paymentStartRequest.paymentItems,
      paymentStartRequest.total,
      paymentStartRequest.currency,
      clientId
    )
  }
}
