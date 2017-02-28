package worldpay

import java.io.StringWriter
import javax.xml.parsers.SAXParserFactory

import client.{AuthDetails, HttpClient}
import controller.PaymentStartRequest
import logging.Logging
import model.Types.Url
import model.{Payment, PaymentEvent}
import org.joda.time.DateTime
import play.api.libs.ws.WSAuthScheme
import worldpay.WorldPayConfiguration.worldPayContentType

import logging.MdcExecutionContext.Implicit.defaultContext
import scala.concurrent.Future
import scala.xml._
import scala.xml.dtd.{DocType, PublicID}

class WorldPayClient(client: HttpClient) extends Logging {
  val docType = DocType("paymentService", PublicID("-//WorldPay/DTD WorldPay PaymentService v1//EN", "http://dtd.worldpay.com/paymentService_v1.dtd"), Nil)

  def startPayment(clientId:String, payment: PaymentStartRequest): Future[Either[RuntimeException, Url]] =
    WorldPayConfiguration(clientId).profile(payment.profile.paymentType, payment.profile.region) match {
      case Left(ex) => Future.successful(Left(ex))
      case Right(profile) =>
        val order = StartPaymentMessageBuilder.build(payment, profile)
        send(order, profile).map {
          returnedXml =>
            extractStartPaymentUrl(payment, returnedXml) match {
              case Right(x) => Right(profile.countryCode.fold(x)(countryCode => x + s"&country=$countryCode"))
              case left@Left(_) => left
            }
        }
    }

  def fetchPaymentEvent(payment: Payment): Future[Option[PaymentEvent]] =
    WorldPayConfiguration(payment.clientId).profile(payment.profile.paymentType, payment.profile.region) match {
      case Left(_) => Future.successful(None)
      case Right(profile) =>
        val order = OrderInquiryMessageBuilder.build(payment, profile)
        send(order, profile).map {
          response =>
            extractPaymentEvent(payment, response) match {
              case Right(pe) => Some(pe)
              case Left(ex) =>
                logger.warn(ex.getMessage)
                None
            }
        }
    }

  private def extractStartPaymentUrl(payment: PaymentStartRequest, response: Node): Either[RuntimeException, Url] = {
    val error = response \ "reply" \ "error"
    if (error.nonEmpty) Left(WorldPayOrderException(payment.internalReference, Map("worldPayResponse" -> response.toString)))
    else Right((response \\ "reference").text.trim)
  }

  private def send(order: Node, profile: WorldPayProfile): Future[Node] = {
    val stringWriter = new StringWriter()
    XML.write(stringWriter, order, "UTF-8", xmlDecl = true, docType, MinimizeMode.Default)
    val auth = AuthDetails(profile.username, profile.password, WSAuthScheme.BASIC)
    client.post(profile.paymentServiceUrl, stringWriter.toString, Some(auth), worldPayContentType).map(response => loadOfflineXML(response))
  }

  private def loadOfflineXML(source: String) = {
    XML.loadXML(scala.xml.Source.fromString(source), offlineParser)
  }

  private def offlineParser: SAXParser = {
    val f = SAXParserFactory.newInstance()
    f.setNamespaceAware(false)
    f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    f.newSAXParser()
  }


  private def extractPaymentEvent(payment: Payment, response: Node): Either[Exception, PaymentEvent] = {
    val orderCode = (response \\ "reply" \ "orderStatus" \ "@orderCode").text
    val paymentElem = response \\ "payment"
    val lastEventElem = paymentElem \ "lastEvent"
    if (lastEventElem.nonEmpty) {
      lastEventElem.text match {
        case "AUTHORISED" =>
          val amountElem = paymentElem \ "amount"
          Right(PaymentEvent(
            orderCode,
            payment.internalReference,
            None,
            lastEventElem.text,
            readAmount(amountElem),
            (amountElem \ "@currencyCode").text,
            (paymentElem \ "paymentMethod").text,
            extractDate(response \\ "reply" \ "orderStatus" \ "date")))
        case status =>
          Right(PaymentEvent(
            orderCode,
            payment.internalReference,
            None,
            status,
            payment.total,
            payment.currency,
            payment.profile.paymentType,
            DateTime.now))
      }
    } else Left(new RuntimeException(s"No payment info from WorldPay for ${payment.externalReference}; WorldPay response: ${response.text}"))
  }

  private def extractDate(dateElem: NodeSeq): DateTime =
    new DateTime((dateElem \ "@year").text.toInt, (dateElem \ "@month").text.toInt, (dateElem \ "@dayOfMonth").text.toInt, (dateElem \ "@hour").text.toInt, (dateElem \ "@minute").text.toInt, (dateElem \ "@second").text.toInt)

  private def readAmount(amountElem: NodeSeq): BigDecimal = {
    val value = (amountElem \ "@value").text
    val exponent = (amountElem \ "@exponent").text
    BigDecimal(value) / BigDecimal(10).pow(Integer.valueOf(exponent))
  }

}
