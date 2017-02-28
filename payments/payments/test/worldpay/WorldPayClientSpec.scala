package worldpay

import java.io.StringWriter

import builders.{PaymentStartRequestBuilder, PaymentBuilder}
import client.{AuthDetails, HttpClient}
import model.{PaymentProfile, PaymentEvent}
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import play.api.libs.ws.WSAuthScheme
import utils.{FixedTime, Mocking}

import scala.concurrent.Future
import scala.xml.XML

class WorldPayClientSpec extends WordSpec with Mocking with ScalaFutures with Matchers with BeforeAndAfter with FixedTime {
  val clientId = "ADS"
  val httpClient = mock[HttpClient]
  val sender = new WorldPayClient(httpClient)
  val payment = PaymentBuilder().copy(clientId = "ADS" , profile = PaymentProfile("VISA-SSL-america", "south-america"))
  val paymentRequest = PaymentStartRequestBuilder().copy(profile = PaymentProfile("VISA-SSL-america", "south-america"))
  val worldPayProfile = WorldPayConfiguration("ADS").profile("america").get
  val authDetails =  Some(AuthDetails(worldPayProfile.username, worldPayProfile.password, WSAuthScheme.BASIC))

  "startPayment" should {
    val order = StartPaymentMessageBuilder.build(paymentRequest, worldPayProfile)
    val orderWriter = new StringWriter()
    XML.write(orderWriter, order, "UTF-8", true, sender.docType)

    "return WorldPay url" in {
      when(httpClient.post(worldPayProfile.paymentServiceUrl, orderWriter.toString, authDetails, WorldPayConfiguration.worldPayContentType)).thenReturn(Future.successful(responseXmlString))

      val url = sender.startPayment(clientId, paymentRequest)

      url.futureValue should equal(Right("https://secure.worldpay.com/jsp/shopper/SelectPaymentMethod.jsp?orderKey=MERCHANT_CODE^T0211010"))
    }

    "return a InvalidPaymentProfileException if WorldPay profile not found" in {
      val paymentWithoutMatchingProfile = paymentRequest.copy(profile = payment.profile.copy("invalidType", "invalidLabel"))

      val url = sender.startPayment(clientId, paymentWithoutMatchingProfile)

      url.futureValue should equal(Left(InvalidPaymentProfileException("invalidType", "invalidLabel")))
    }

    "return a WorldPayOrderException if WorldPay returns an invalid response" in {
      when(httpClient.post(worldPayProfile.paymentServiceUrl, orderWriter.toString, authDetails, WorldPayConfiguration.worldPayContentType)).thenReturn(Future.successful(invalidOrderResponseXml))

      val url = sender.startPayment(clientId, paymentRequest)

      url.futureValue match {
        case Left(WorldPayOrderException(ref, map)) => ref should be(payment.internalReference)
        case Left(l) => fail(s"$l not expected")
        case _ => fail("expected left")
      }
    }
  }

  val responseXmlString = """<?xml version="1.0"?>
      <!DOCTYPE paymentService PUBLIC "-//WorldPay/DTD WorldPay PaymentService v1//EN" "http://dtd.worldpay.com/paymentService_v1.dtd">
      <paymentService merchantCode="MERCHANT_CODE" version="1.4">
        <reply>
          <orderStatus orderCode="T0211010">
            <reference id="1234567">
              https://secure.worldpay.com/jsp/shopper/SelectPaymentMethod.jsp?orderKey=MERCHANT_CODE^T0211010
            </reference>
          </orderStatus>
        </reply>
      </paymentService>
                          """

  val invalidOrderResponseXml =
    """<paymentService merchantCode="MERCHANT_CODE" version="1.4">
          <reply>
           <error code="2">The content of element type &quot;order&quot; must match &quot;(description,amount,risk?,orderContent?,(paymentMethodMask|paymentDetails|payAsOrder),shopper?,shippingAddress?,billingAddress?,branchSpecificExtension?,redirectPageAttribute?,paymentMethodAttribute*,echoData?,statementNarrative?,hcgAdditionalData?,thirdPartyData?)&quot;.</error>
          </reply>
         </paymentService>"""


  "getPaymentEvent" should {
    val messageWriter = new StringWriter()
    val inquiryMessage = OrderInquiryMessageBuilder.build(payment, worldPayProfile)
    XML.write(messageWriter, inquiryMessage, "UTF-8", true, sender.docType)

    "return PaymentEvent from WorldPay if status is AUTHORISED" in {
      when(httpClient.post(worldPayProfile.paymentServiceUrl, messageWriter.toString, authDetails, WorldPayConfiguration.worldPayContentType)).thenReturn(Future.successful(paidStatusResponseXmlString))

      val futureEvent = sender.fetchPaymentEvent(payment)

      futureEvent.futureValue should equal(Some(PaymentEvent("529c7c5be4b0f668198009ac", payment.internalReference, None, "AUTHORISED", 578d, "GBP", "VISA-SSL", new DateTime(2013, 12, 2, 12, 38, 16))))
    }

    "return PaymentEvent from WorldPay if status is not AUTHORISED" in {
      when(httpClient.post(worldPayProfile.paymentServiceUrl, messageWriter.toString, authDetails, WorldPayConfiguration.worldPayContentType)).thenReturn(Future.successful(refusedStatusResponseXmlString))

      val futureEvent = sender.fetchPaymentEvent(payment)

      futureEvent.futureValue should equal(Some(PaymentEvent("529c7c5be4b0f668198009ac", payment.internalReference, None, "REFUSED", payment.total, payment.currency, payment.profile.paymentType, DateTime.now)))
    }

    "return None if WorldPay does not recognise externalReference" in {
      when(httpClient.post(worldPayProfile.paymentServiceUrl, messageWriter.toString, authDetails, WorldPayConfiguration.worldPayContentType)).thenReturn(Future.successful(notFoundStatusResponseXmlString))

      val futureEvent = sender.fetchPaymentEvent(payment)

      futureEvent.futureValue should equal(None)
    }
  }

  val paidStatusResponseXmlString = """<?xml version="1.0" encoding="UTF-8"?>
                                      |<!DOCTYPE paymentService PUBLIC "-//WorldPay//DTD WorldPay PaymentService v1//EN"
                                      |                                "http://dtd.worldpay.com/paymentService_v1.dtd">
                                      |<paymentService version="1.4" merchantCode="MERCHANT_CODE">
                                      |  <reply>
                                      |    <orderStatus orderCode="529c7c5be4b0f668198009ac">
                                      |      <payment>
                                      |        <paymentMethod>VISA-SSL</paymentMethod>
                                      |        <amount value="57800" currencyCode="GBP" exponent="2" debitCreditIndicator="credit"/>
                                      |        <lastEvent>AUTHORISED</lastEvent>
                                      |        <CVCResultCode description="APPROVED"/>
                                      |        <AVSResultCode description="APPROVED"/>
                                      |        <cardHolderName><![CDATA[asasa]]]]></cardHolderName>
                                      |        <issuerCountryCode>N/A</issuerCountryCode>
                                      |        <balance accountType="IN_PROCESS_AUTHORISED">
                                      |          <amount value="57800" currencyCode="GBP" exponent="2" debitCreditIndicator="credit"/>
                                      |        </balance>
                                      |        <cardNumber>4111********1111</cardNumber>
                                      |      </payment>
                                      |      <date dayOfMonth="02" month="12" year="2013" hour="12" minute="38" second="16"/>
                                      |    </orderStatus>
                                      |  </reply>
                                      |</paymentService>
                                      | """.stripMargin

  val refusedStatusResponseXmlString = """<?xml version="1.0" encoding="UTF-8"?>
                                         |<!DOCTYPE paymentService PUBLIC "-//WorldPay//DTD WorldPay PaymentService v1//EN"
                                         |                                "http://dtd.worldpay.com/paymentService_v1.dtd">
                                         |<paymentService version="1.4" merchantCode="MERCHANT_CODE">
                                         |  <reply>
                                         |    <orderStatus orderCode="529c7c5be4b0f668198009ac">
                                         |      <payment>
                                         |        <paymentMethod>VISA-SSL</paymentMethod>
                                         |        <amount value="57800" currencyCode="GBP" exponent="2" debitCreditIndicator="credit"/>
                                         |        <lastEvent>REFUSED</lastEvent>
                                         |        <CVCResultCode description="NOT SUPPLIED BY SHOPPER"/>
                                         |        <ISO8583ReturnCode code="33" description="CARD EXPIRED"/>
                                         |        <riskScore value="0"/>
                                         |      </payment>
                                         |    </orderStatus>
                                         |  </reply>
                                         |</paymentService>
                                         | """.stripMargin

  val notFoundStatusResponseXmlString = """<?xml version="1.0" encoding="UTF-8"?>
                                          |<!DOCTYPE paymentService PUBLIC "-//WorldPay//DTD WorldPay PaymentService v1//EN"
                                          |                                "http://dtd.worldpay.com/paymentService_v1.dtd">
                                          |<paymentService version="1.4" merchantCode="MERCHANT_CODE">
                                          | <reply>
                                          |   <orderStatus orderCode="529c7c5be4b0f668198009">
                                          |     <error code="5"><![CDATA[Could not find payment for order]]></error>
                                          |   </orderStatus>
                                          | </reply>
                                          |</paymentService>""".stripMargin

}
