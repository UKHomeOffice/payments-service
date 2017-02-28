package controller

import conf.{ComponentRegistry, Config}
import json.JsonFormats
import logging.Logging
import play.api.mvc.{Action, Controller, Results}
import report.WorldPayTransactionReportService
import repository.MessageRepository

import logging.MdcExecutionContext.Implicit.defaultContext

class WorldPayTransactionReportControllerClass(worldPayTransactionReportService: WorldPayTransactionReportService = ComponentRegistry.worldPayTransactionReportService,
                                               messageRepository: MessageRepository = ComponentRegistry.messageRepository)
  extends Controller with Logging with JsonFormats {

  //Max BSON object size
  val MAX_FILE_SIZE = 16777216

  def receiveTransactionReport: Action[String] = Action.async(parse.tolerantText(MAX_FILE_SIZE)) {
    request =>
      logger.info("Received financial report")
      val rawReport = request.body
      val reportSaveF = worldPayTransactionReportService.saveRawReport(rawReport)

      reportSaveF map {
        report =>
          worldPayTransactionReportService.buildFinancialReport(report).map {
            fr =>
              logger.info(s"Financial report for merchant code: '${fr.merchantCode}' and batch id: ${fr.batchId} built")
              messageRepository.queueMessageWithPayload(Config.paymentReportUrl, fr)
          } recover {
            case ex =>
              logger.error("Financial report processing failure:", ex)
          }
      }

      reportSaveF.map {
        _ =>
          Results.Ok( """<html> <head>Report Response</head> <body> [OK] </body> </html>""")
      } recover {
        case _ => Results.BadRequest
      }
  }
}

object WorldPayTransactionReportController extends WorldPayTransactionReportControllerClass