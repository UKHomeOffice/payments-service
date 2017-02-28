package controller

import conf.Config.paymentReportUrl
import json.JsonFormats
import matcher.ResultMatchers
import org.joda.time.DateTime
import org.mockito.Mockito
import org.mockito.Mockito.{verifyZeroInteractions, never, verify, when}
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import play.api.test.FakeRequest
import report.model.{FinancialReport, PaymentTransactionReport}
import report.WorldPayTransactionReportService
import repository.MessageRepository
import utils.{Eventually, FakeApp, Mocking}

import scala.concurrent.Future

class WorldPayTransactionReportControllerSpec extends WordSpec with FakeApp with ResultMatchers with Mocking with JsonFormats with ScalaFutures with Eventually {

  trait Scope {
    val transactionService = mock[WorldPayTransactionReportService]
    val messageRepository = mock[MessageRepository]
    val controller = new WorldPayTransactionReportControllerClass(transactionService, messageRepository)

    val report = "report"
    val savedReport = PaymentTransactionReport(None, report, DateTime.now)
  }

  "receiveTransactionReport" should {

    "return BadRequest if save of raw report failed" in new Scope {
      when(transactionService.saveRawReport(report)).thenReturn(Future.failed(new RuntimeException))

      val result = controller.receiveTransactionReport(FakeRequest().withBody(report))
      result should haveStatus(400)

      eventuallySucceed(verify(transactionService, never).buildFinancialReport(savedReport))
    }

    "save raw report, generate financial report and queue message for notification" in new Scope {
      when(transactionService.saveRawReport(report)).thenReturn(Future.successful(savedReport))

      val financialReport = FinancialReport("1", "2", List.empty, List.empty)
      when(transactionService.buildFinancialReport(savedReport)).thenReturn(Future.successful(financialReport))

      val result = controller.receiveTransactionReport(FakeRequest().withBody(report))
      result should haveStatus(200)
      result should haveStringBody( """<html> <head>Report Response</head> <body> [OK] </body> </html>""")

      eventuallySucceed(verify(messageRepository).queueMessageWithPayload(paymentReportUrl, financialReport))
    }

    "return Ok status if report building fails" in new Scope {
      when(transactionService.saveRawReport(report)).thenReturn(Future.successful(savedReport))

      when(transactionService.buildFinancialReport(savedReport)).thenThrow(new RuntimeException())

      val result = controller.receiveTransactionReport(FakeRequest().withBody(report))
      result should haveStatus(200)
      result should haveStringBody( """<html> <head>Report Response</head> <body> [OK] </body> </html>""")

      verifyZeroInteractions(messageRepository)
    }
  }

}
