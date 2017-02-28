package controller

import conf.ComponentRegistry
import json.JsonFormats
import model.PaymentEventSummary
import org.json4s.jackson.Serialization
import play.api.mvc.{Action, AnyContent, Results}
import repository.PaymentEventRepository
import worldpay.WorldPayConfiguration
import logging.MdcExecutionContext.Implicit.defaultContext

object DailyReportController extends DailyReportControllerClass

class DailyReportControllerClass(paymentEventRepository: PaymentEventRepository = ComponentRegistry.paymentEventRepository,
                                 allPaymentMethods: Set[String] = WorldPayConfiguration.allPaymentMethods) extends JsonFormats {

  def report: Action[AnyContent] = Action.async {
    request =>
      paymentEventRepository.generateDailyReportBreakDownByPaymentMethod.map {
        case methodSummaries if methodSummaries.isEmpty => Results.Ok
        case methodSummaries =>
          val summary = PaymentEventSummary(methodSummaries, allPaymentMethods)
          val summaryToReturn = request.getQueryString("full") match {
            case Some("true") | Some("") => summary
            case _ =>
              summary.withAggregateForRejected
          }
          Results.Ok(Serialization.write(summaryToReturn))
      }
  }
}
