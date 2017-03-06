package conf

import client.HttpClient
import management.MongoHealthCheck
import report.WorldPayTransactionReportService
import repository._
import worldpay.WorldPayClient

object ComponentRegistry {

  val paymentEventRepository = new PaymentEventRepository(DbConnection.db)
  val paymentRepository = new PaymentRepository(DbConnection.db)
  val reportRepository = new PaymentTransactionReportRepository(DbConnection.db)
  val messageRepository = new MessageRepository(DbConnection.db)

  val worldPaySender = new WorldPayClient(new HttpClient)

  val worldPayTransactionReportService = new WorldPayTransactionReportService(reportRepository, paymentRepository, paymentEventRepository)

  val mongoHealthCheck = new MongoHealthCheck(DbConnection.db)
}
