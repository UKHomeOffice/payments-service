package worldpay

import builders.{PaymentStartRequestBuilder, PaymentBuilder}
import org.scalatest.{Matchers, WordSpec}
import utils.{Mocking, Xml}
import worldpay.StartPaymentMessageBuilder.build

import scala.xml.Utility.trim
import scala.xml._

class PaymentBuilderSpec extends WordSpec with Matchers with Mocking with Xml {

  private val username = "MERCHANT_CODE"
  val payment =PaymentStartRequestBuilder(externalReference = "pnn")

  def profile(username: String = "myOwnCode", installationId: String= "311416") = WorldPayConfiguration("DCJ").profile("western-europe").get
    .copy(username = username, installationId = installationId)

  "construct complete order XML to send to WorldPay" in {
    val orderXml = trim(build(payment, profile(username)))
    orderXml.toString() should equal(trim(exhaustiveOrderXml).toString())
  }

  "worldPay 'username' is used for as the 'merchant code' attribute" in {
    val customUsername = "MY_MERCHANT_CODE"
    val orderXml = trim(build(payment, profile(customUsername)))
    (orderXml \\ "paymentService" \ "@merchantCode").text shouldBe customUsername
  }

  "worldPay 'payment.currencyCode' is used for as the 'currencyCode' attribute" in {
    val orderXml = trim(build(payment, profile(username)))
    (orderXml \\ "amount" \ "@currencyCode").text shouldBe "GBP"
  }

  "worldPay 'installationId' is used for as the 'installationId' attribute" in {
    val installationId = "myInstallationId"
    val orderXml = trim(build(payment, profile(username, installationId)))
    (orderXml \\ "order" \ "@installationId").text shouldBe installationId
  }

  "'username' is a required attribute and throws an IllegalArgumentException if not supplied" in {
    val customUsername = ""
    intercept[IllegalArgumentException]{
      build(payment, profile(customUsername))
    }
  }

  "billing address should have the country code specified in the PostalAddress" in {
    val billingAddress = StartPaymentMessageBuilder.buildBillingAddressElement(payment.payee)
    (billingAddress \\ "countryCode").text should be ("CN")
  }

  "when using an APM the billing address should not be present " in {
    val orderXml = trim(build(payment, profile().copy(regions = Set("APM"))))
    (orderXml \\ "billingAddress").isEmpty shouldBe true
  }

  "when using an APM the order should not have an installationId" in {
    val orderXml = trim(build(payment, profile().copy(regions = Set("APM"))))
    (orderXml \\ "order" \ "@orderCode").text shouldBe "pnn"
    (orderXml \\ "order" \ "@installationId").isEmpty shouldBe true
  }

  "when using an APM the shoppers email address should be added" in {
    val expectedEmail = payment.payee.email

    val orderXml = trim(build(payment, profile().copy(regions = Set("APM"))))
    (orderXml \\ "shopper" ).isEmpty shouldBe false
    (orderXml \\ "shopperEmailAddress" ).text shouldBe expectedEmail.get
  }

  "when using an APM and selecting china union, the FEE block must be set with the value 'China Union'" in {
    val expectedCardType = "myHighClassChinaUnionCard"
    val chinaPayment = payment.copy(profile = payment.profile.copy(paymentType = expectedCardType))

    val orderXml = trim(build(chinaPayment, profile().copy(regions = Set("APM"))))
    val includes = orderXml \\ "paymentMethodMask" \ "include"
    includes.size > 0 should be(true)
    (includes \\ "@code").text should be (expectedCardType)
  }

  private val exhaustiveOrderXml = <paymentService version="1.4" merchantCode="MERCHANT_CODE">
                                              <submit>
                                                   <order orderCode="pnn" installationId="311416">
                                                       <description>applicants details</description>
                                                       <amount value="23456" currencyCode="GBP" exponent="2"/>
                                                       <orderContent>
                                                         {PCData("<div><h2>Payment Title</h2></div>")}
                                                       </orderContent>
                                                        <paymentMethodMask>
                                                          <include code="VISA-SSL"/>
                                                        </paymentMethodMask>
                                                        <billingAddress>
                                                            <address>
                                                                <firstName>givenName</firstName>
                                                                <lastName>familyName</lastName>
                                                                <address1>line1</address1>
                                                                <address2>line2</address2>
                                                                <address3>line3</address3>
                                                                <postalCode>EC2 2CE</postalCode>
                                                                <city>London</city>
                                                                <countryCode>CN</countryCode>
                                                                <telephoneNumber>0123456789</telephoneNumber>
                                                           </address>
                                                        </billingAddress>
                                                       <statementNarrative>UKVI_pnn</statementNarrative>
                                                   </order>
                                               </submit>
                                           </paymentService>

}
