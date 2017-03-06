package repository

import reactivemongo.api.DB
import reactivemongo.bson.BSONObjectID
import reactivemongo.core.commands.LastError
import reactivemongo.extensions.dao.BsonDao
import report.model.PaymentTransactionReport

import scala.concurrent.Future

class PaymentTransactionReportRepository(db: () => DB) extends BsonDao[PaymentTransactionReport, BSONObjectID](db, "transactionReports") {

  def save(report: PaymentTransactionReport): Future[PaymentTransactionReport] = {
    super.save(report).map {
      case LastError(true, _, _, _, _, _, _) => report
    } recover {
      case e: LastError =>
        throw new RuntimeException(e.errMsg.getOrElse(s"Could not record report for batchId: ${report.batchId}"))
    }
  }
}
