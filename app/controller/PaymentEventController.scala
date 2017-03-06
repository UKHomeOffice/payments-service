package controller

import java.net.URLDecoder

import conf.{ComponentRegistry, Config}
import json.JsonFormats
import logging.Logging
import model.Types._
import model.{Payment, PaymentStatus, PaymentEvent, PaymentEventNotification}
import org.joda.time.DateTime
import org.json4s.jackson.Serialization
import org.slf4j.MDC
import play.api.mvc._
import repository._
import worldpay.WorldPayClient

import logging.MdcExecutionContext.Implicit.defaultContext
import scala.concurrent.Future
import scala.util.Try

class PaymentEventControllerClass(paymentRepository: PaymentRepository = ComponentRegistry.paymentRepository,
                                  paymentEventRepository: PaymentEventRepository = ComponentRegistry.paymentEventRepository,
                                  messageRepository: MessageRepository = ComponentRegistry.messageRepository,
                                  worldPayClient: WorldPayClient = ComponentRegistry.worldPaySender)
  extends Controller with Logging with JsonFormats {
  val resultOk = Results.Ok("[OK]")
  val badRequest = Results.BadRequest

  def receivePaymentEvent: Action[AnyContent] = Action.async {
    request =>
      extractEvent(request) match {
        case Some(n) =>
          withExternalReferenceMdc(n.externalReference) { () =>
            info(s"Received worldPay notification for externalRef : ${n.externalReference}; status : ${n.paymentStatus.toString}")
            paymentRepository.findByExternalReference(n.externalReference).flatMap {
              case Some(payment) =>
                val newPaymentEvent = n.copy(internalReference = payment.internalReference)
                saveAndQueueNotification(newPaymentEvent, payment)
              case None =>
                warn(s"No payment record found for ExternalReference: ${n.externalReference}")
                Future.successful(resultOk)
            }
          }
        case _ =>
          info(s"Missing parameters in world pay event [Event uri] : ${request.uri}")
          Future.successful(resultOk)
      }
  }

  def performInquiry(internalReference: InternalReference): Action[AnyContent] = Action.async { _ =>
      withInternalReferenceMdc(internalReference) { () =>
        paymentEventRepository.findExternalReferencesForPendingPayments(internalReference)
          .map(
            events => events.map(processFoundStatus))
        Future.successful(Results.Ok)
      }
  }

  private def processFoundStatus(externalReference: ExternalReference) =
    paymentRepository.findByExternalReference(externalReference).map {
      paymentOption =>
        val payment = paymentOption.get
        worldPayClient.fetchPaymentEvent(payment).map {
          case None => logger.warn(s"No Status returned from WorldPay inquiry for internalReference: ${payment.internalReference}, externalReference: ${payment.externalReference} and ${payment.total}")
          case Some(ev) =>
            info(s"Inquiry for externalReference : ${ev.externalReference} and internalReference : ${ev.internalReference} returned status : ${ev.paymentStatus}")
            saveAndQueueNotificationIfChange(ev, payment)
        }
    }

  private def saveAndQueueNotificationIfChange(newPaymentEvent: PaymentEvent, payment: Payment) =
    paymentEventRepository.findLatestStatus(newPaymentEvent.internalReference, newPaymentEvent.amount).map {
      savedEvent =>
        if (savedEvent.exists(_.worldPayStatus != newPaymentEvent.worldPayStatus)) {
          paymentEventRepository.save(newPaymentEvent).map { _ =>
            queueMessageIfStatusChanged(payment.clientId, newPaymentEvent, payment.status)
          }
        }
    }

  /**
   * This is basically to handle APM's. For some reason if the payment has failed on APM then we get only the synchronous response back in the form of 'status' query parameter.
   * For all other payment types we rely on the notification from worldpay.
   */
  def receivePaymentSubmissionRedirect: Action[AnyContent] = Action.async {
    request =>
      debug("Received payment submission with query attributes: " + request.rawQueryString)
      val queryAttributes = request.queryString.mapValues {
        values => URLDecoder.decode(values.mkString, "UTF-8")
      }

      queryAttributes.get("externalReference") match {
        case None =>
          Future.successful(Results.BadRequest("No external reference given on payment submission redirect"))
        case Some(externalRef) =>
          withExternalReferenceMdc(externalRef){ () =>
          request.getQueryString("status") match {
            case None =>
              Future.successful(Results.Ok)
            case Some(status) =>
              paymentRepository.findByExternalReference(externalRef).flatMap {
                case Some(payment) =>
                  val paymentEvent = PaymentEvent(externalRef, payment.internalReference, None, status, payment.total, payment.currency, payment.profile.paymentType)
                  saveAndQueueNotification(paymentEvent, payment)
                case None =>
                  warn(s"No payment record found for ExternalReference: ${
                    externalRef
                  }")
                  Future.successful(Results.BadRequest)
              }
          }
          }
      }
  }

  private def withExternalReferenceMdc[T](externalReference: String)(r: () => T): T = {
    MDC.put("external_reference", externalReference)
    try {
      r()
    } finally {
      MDC.clear()
    }
  }

  private def withInternalReferenceMdc[T](internalReference: String)(r: () => T): T = {
    MDC.put("internal_reference", internalReference)
    try {
      r()
    } finally {
      MDC.clear()
    }
  }

  private def saveAndQueueNotification(newPaymentEvent: PaymentEvent, payment: Payment) =
    paymentEventRepository.save(newPaymentEvent).map { pe =>
      queueMessageIfStatusChanged(payment.clientId, pe, payment.status)
      resultOk
    } recover {
      case _ => badRequest
    }

  private def queueMessageIfStatusChanged(clientId: String, newPaymentEvent: PaymentEvent, currentPaymentStatus: PaymentStatus) {
    if (newPaymentEvent.isOfTypeUnknown) {
      logger.info(s"No message queued. Unrecognized world pay status :${newPaymentEvent.worldPayStatus} for PNN : ${newPaymentEvent.externalReference}")
    } else {
      paymentRepository.changePaymentStatus(newPaymentEvent.externalReference, newPaymentEvent.paymentStatus).map {
        case NotChanged =>
          logger.info(s"No change in current payment status [${newPaymentEvent.paymentStatus}] no messages queued for world pay event :${newPaymentEvent.worldPayStatus} PNN : ${newPaymentEvent.externalReference}")
        case Changed =>
          logger.info(s"Change in current payment status ['$currentPaymentStatus' to '${newPaymentEvent.paymentStatus}'] message queued for world pay event :${newPaymentEvent.worldPayStatus} PNN : ${newPaymentEvent.externalReference}")
          messageRepository.queueMessageWithPayload(Config.paymentNotificationUrl(clientId), PaymentEventNotification(Some(newPaymentEvent.externalReference), newPaymentEvent.internalReference, newPaymentEvent.amount, Some(newPaymentEvent.currency), newPaymentEvent.paymentStatus))
      }
    }
  }

  private def extractEvent(request: Request[_]): Option[PaymentEvent] =
    for {
      paymentId <- request.getQueryString("PaymentId")
      paymentCurrency <- request.getQueryString("PaymentCurrency")
      orderCode <- request.getQueryString("OrderCode")
      paymentMethod <- request.getQueryString("PaymentMethod")
      paymentStatus <- request.getQueryString("PaymentStatus")
      paymentAmountString <- request.getQueryString("PaymentAmount")
      total <- Try(BigDecimal(paymentAmountString) / BigDecimal(100.0)).toOption
    } yield {
      PaymentEvent(orderCode, "", Some(paymentId), paymentStatus, total, paymentCurrency, paymentMethod, DateTime.now())
    }
}

object PaymentEventController extends PaymentEventControllerClass