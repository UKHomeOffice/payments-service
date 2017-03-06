package repository

import model.{PaymentStatus, Payment}
import model.Types.{ExternalReference, InternalReference}
import reactivemongo.api.DB
import reactivemongo.bson._
import reactivemongo.bson.{BSONDocument => BD}
import reactivemongo.core.commands.LastError
import reactivemongo.extensions.dao.BsonDao

import scala.concurrent.Future

sealed trait StatusChange
case object Changed extends StatusChange
case object NotChanged extends StatusChange

class PaymentRepository(db: () => DB) extends BsonDao[Payment, BSONObjectID](db, "payments") {

  def save(payment: Payment): Future[Payment] = {
    super.save(payment).map {
      case LastError(true, _, _, _, _, _, _) => payment
    } recover {
      case LastError(_, _, Some(11000), _, _, _, _) =>
        throw DuplicateExternalReference(payment.externalReference)
      case e: LastError =>
        throw new RuntimeException(e.errMsg.getOrElse("Could not save payment"))
    }
  }

  def changePaymentStatus(externalReference: ExternalReference, newStatus: PaymentStatus): Future[StatusChange] = {
    findAndUpdate(
      query = BD("externalReference" -> externalReference, "status" -> BD("$ne" -> newStatus.toString)),
      update = BD("$set" -> BD("status" -> newStatus.toString)),
      fetchNewObject = true
    ).map {
      case None => NotChanged
      case Some(_) => Changed
    }
  }

  def findInternalReference(externalReference: ExternalReference): Future[Option[InternalReference]] =
    findOne(BD("externalReference" -> externalReference)).map(_.map(_.internalReference))

  def findByExternalReference(externalReference: ExternalReference): Future[Option[Payment]] =
    findOne(BD("externalReference" -> externalReference))

  def find(internalReference: InternalReference, total: BigDecimal): Future[Option[Payment]] =
    findOne(BD("internalReference" -> internalReference, "total" -> total.toDouble))
}

case class DuplicateExternalReference(externalReference: ExternalReference) extends RuntimeException(s"Duplicate externalReference : $externalReference")
