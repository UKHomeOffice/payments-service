package report.model

import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONHandler, Macros}
import report._
import reactivemongo.extensions.dao.Handlers._
import repository.TypeHandlers._


case class FinancialReport(merchantCode: String, batchId: String, combinedReportEntries: List[Row], rolledUpReportEntries: List[Row])
object FinancialReport {
  implicit val dbReadWriteHandler: BSONDocumentReader[FinancialReport] with BSONDocumentWriter[FinancialReport] with BSONHandler[BSONDocument, FinancialReport] = Macros.handler[FinancialReport]

}
