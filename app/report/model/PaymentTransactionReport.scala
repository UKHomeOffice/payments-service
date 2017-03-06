package report.model

import org.joda.time.DateTime
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONHandler, Macros}
import reactivemongo.extensions.dao.Handlers._
import repository.TypeHandlers._

case class PaymentTransactionReport(batchId: Option[String], report: String, dateCreated: DateTime)

object PaymentTransactionReport {

  implicit val lineItemHandler: BSONDocumentReader[PaymentTransactionReport] with BSONDocumentWriter[PaymentTransactionReport] with BSONHandler[BSONDocument, PaymentTransactionReport] = Macros.handler[PaymentTransactionReport]
}