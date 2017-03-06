package worldpay

import builders.PaymentBuilder
import org.scalatest.{Matchers, WordSpec}
import utils.{Mocking, Xml}

import scala.xml.Utility.trim

class OrderInquiryMessageBuilderSpec extends WordSpec with Matchers with Mocking with Xml {

  "build" should {
    "construct orderInquiry to be sent to WorldPay " in {
      val orderInquiryXml = trim(OrderInquiryMessageBuilder.build(PaymentBuilder(externalReference = "myOrderCode"), WorldPayConfiguration("DCJ").profile("western-europe").get.copy(username = "MERCHANT_CODE")))
      orderInquiryXml should equal (loadOfflineXML(orderStatusRequestXmlString))
    }
  }

  private val orderStatusRequestXmlString = """<?xml version="1.0"?>
                                              |<!DOCTYPE paymentService PUBLIC "-//WorldPay/DTD WorldPay PaymentService v1//EN" "http://dtd.worldpay.com/paymentService_v1.dtd">
                                              |<paymentService version="1.4" merchantCode="MERCHANT_CODE">
                                              | <inquiry>
                                              |   <orderInquiry orderCode="myOrderCode"/>
                                              | </inquiry>
                                              |</paymentService>""".stripMargin
}
