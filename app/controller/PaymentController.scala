package controller

import java.net.URLEncoder

import conf.{ComponentRegistry, Config}
import logging.Logging
import model.Types._
import model._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._
import org.slf4j.MDC
import parser.CustomBodyParsers
import play.api.mvc.{Result, _}
import repository.{PaymentEventRepository, PaymentRepository}
import validation.Constraint
import worldpay.{ValidClientId, WorldPayClient, WorldPayConfiguration}

import logging.MdcExecutionContext.Implicit.defaultContext
import scala.concurrent.Future

case class PaymentStartResponse(url: Url, externalReference: ExternalReference)

case class PaymentStartRequest(externalReference: ExternalReference,
                               internalReference: InternalReference,
                               payee: Payee,
                               title: String,
                               description: String,
                               profile: PaymentProfile,
                               paymentItems: List[PaymentItem],
                               total: BigDecimal,
                               currency: Currency
                                )


class PaymentControllerClass(worldPayClient: WorldPayClient = ComponentRegistry.worldPaySender,
                             paymentRepository: PaymentRepository = ComponentRegistry.paymentRepository,
                             paymentEventRepository: PaymentEventRepository = ComponentRegistry.paymentEventRepository)
  extends Controller with CustomBodyParsers with Logging with ValidClientId {

  def startPayment: Action[PaymentStartRequest] = withValidClientIdHeader {
    clientId =>
      withValidatedEntity(PaymentController.paymentConstraints) {
        paymentStartRequest => {
          withMdc(paymentStartRequest.internalReference, paymentStartRequest.externalReference){ ()=>
            logger.info(s"Start payment request for: ${paymentStartRequest.currency} ${paymentStartRequest.total} ${paymentStartRequest.profile} "  )
            val payment = Payment(paymentStartRequest, clientId)
            logDurationOf("savePayment") {
              paymentRepository.save(payment).flatMap { _: Payment =>
                  paymentEventRepository.save(PaymentEvent.withInitStatus(payment))
                  logDurationOf("savePayment.paymentStartUrl") {
                    paymentStartUrl(clientId, paymentStartRequest).map {
                      url =>
                        val startPaymentResponse = write(PaymentStartResponse(url, payment.externalReference))
                        logger.info(s"Responding to start payment request with: $startPaymentResponse")
                        Results.Ok(startPaymentResponse)
                    }
                  }
              }
            }
          }
        }
      }
  }

  def getPaymentTypes(region: Region): Action[AnyContent] = withValidClientIdHeader { clientId =>
      Action.async { _ =>
          Future.successful(Results.Ok(Serialization.write(WorldPayConfiguration(clientId).paymentTypes(region))))
      }
  }

  private def withValidatedEntity[T](constraints: Seq[Constraint[T]])(block: T => Future[Result])(implicit manifest: Manifest[T]): Action[T] = Action.async(json[T]) {
    request =>
      val entity = request.body
      val validationResults = constraints.collect {
        case c if !c.isValid(entity) => c.errorMessage
      }

      if (validationResults.isEmpty) {
        block(entity)
      } else {
        Future.successful(Results.BadRequest(write(validationResults)))
      }
  }

  private def paymentStartUrl(clientId:String , payment: PaymentStartRequest) =
    worldPayClient.startPayment(clientId, payment).map {
      case Right(url) => prepareUrl(url, clientId, payment)
      case Left(x) => throw x
    }

  private def prepareUrl(worldpayUrl: String, clientId:String, payment: PaymentStartRequest): String = {
    val pnnAttribute = "externalReference=" + payment.externalReference
    val pendingUrl = URLEncoder.encode(Config.pendingUrl(clientId) + "?" + pnnAttribute, "UTF-8")
    val cancelUrl = URLEncoder.encode(Config.cancelUrl(clientId), "UTF-8")
    val responseUrls =
      s"successURL=$pendingUrl&" +
        s"cancelURL=$cancelUrl&" +
        s"pendingURL=$pendingUrl&" +
        s"failureURL=$pendingUrl&" +
        s"preferredPaymentMethod=${payment.profile.paymentType}"
    val countryCode = Config.countryCodeForRegion.get(payment.profile.region).map(code => s"country=$code&").getOrElse("")
    s"$worldpayUrl&$countryCode$responseUrls"
  }


  private def withMdc[T](internalReference: String, externalReference: String)(r: () => T): T = {
    MDC.put("external_reference", externalReference)
    MDC.put("internal_reference", internalReference)
    try {
      r()
    } finally {
      MDC.clear()
    }
  }
}

object PaymentController extends PaymentControllerClass {

  val paymentConstraints = Seq(
    Constraint[PaymentStartRequest](!_.externalReference.isEmpty, "externalReference.empty"),
    Constraint[PaymentStartRequest](!_.internalReference.isEmpty, "internalReference.empty"),
    Constraint[PaymentStartRequest](_.total > 0, "total.zeroOrLess"),
    Constraint[PaymentStartRequest](!_.profile.paymentType.isEmpty, "profile.paymentType.empty"),
    Constraint[PaymentStartRequest](!_.profile.region.isEmpty, "profile.region.empty"),
    Constraint[PaymentStartRequest](_.paymentItems.nonEmpty, "paymentItems.empty"),
    Constraint[PaymentStartRequest](pm => pm.paymentItems.foldRight[BigDecimal](0)((li, t) => t + li.price) == pm.total, "paymentItems.total.invalid")
  )

}