package worldpay

import java.util.Locale

import controller.PaymentStartRequest
import model.Payee

import scala.xml.dtd.{DocType, PublicID}
import scala.xml.{Elem, Node, NodeSeq, PCData}


object StartPaymentMessageBuilder {

  val docType = DocType("paymentService", PublicID("-//WorldPay/DTD WorldPay PaymentService v1//EN", "http://dtd.worldpay.com/paymentService_v1.dtd"), Nil)

  private lazy val localeMap: Map[String, String] = loadLocales.toMap

  private def loadLocales: List[(String, String)] = {
    for {
      country <- Locale.getISOCountries.toList
      locale = new Locale("", country)
    } yield locale.getISO3Country.toUpperCase -> country
  }

  def build(payment: PaymentStartRequest, profile: WorldPayProfile): Node = {
    require(!profile.username.isEmpty, "the username (merchant code) has not been defined for this payment")

    val installationId = if (profile.isAPM) None else Some(profile.installationId)

    <paymentService version="1.4" merchantCode={profile.username}>
      <submit>
        {makeOrder(payment.externalReference, installationId) {
        if (profile.isAPM) buildAPM(payment)
        else buildRealTimeOrderDetails(payment)
      }}
      </submit>
    </paymentService>
  }

  private def buildAPM(payment: PaymentStartRequest): NodeSeq =
    includeCommonElements(payment, shopperAddress(payment.payee))

  private def buildRealTimeOrderDetails(payment: PaymentStartRequest): NodeSeq =
    includeCommonElements(payment, buildBillingAddressElement(payment.payee))

  private def makeOrder(paymentReference: String, installationId: Option[String])(children: => NodeSeq): Elem = {
    installationId.fold({
      <order orderCode={paymentReference}>
        {children}
      </order>
    })({ id =>
      <order orderCode={paymentReference} installationId={id}>
        {children}
      </order>
    })
  }

  private def shopperAddress(payee: Payee): NodeSeq = payee.email.fold(NodeSeq.Empty) { emailAddress =>
    <shopper>
      <shopperEmailAddress> {emailAddress} </shopperEmailAddress>
    </shopper>
  }

  private def includeCommonElements(payment: PaymentStartRequest, siblings: => NodeSeq) = {
    <description> {s"%.50s" format payment.description} </description>
    <amount value={"%.0f".format(payment.total * 100)} currencyCode={payment.currency} exponent="2"/>
    <orderContent> {PCData(payment.title)} </orderContent>
    <paymentMethodMask> <include code={payment.profile.paymentType}/></paymentMethodMask> ++
    siblings ++
    <statementNarrative>UKVI_{payment.externalReference}</statementNarrative>
  }

  private [worldpay] def buildBillingAddressElement(payee: Payee): NodeSeq =
    payee.billingAddress.map{
      billingAddress =>
        <billingAddress>
          <address>
            {buildNames(payee)}
            <address1> {billingAddress.line1} </address1>
            {billingAddress.line2.map(l2 => <address2> {l2} </address2>).getOrElse("")}
            {billingAddress.line3.map(l3 => <address3> {l3} </address3>).getOrElse("")}
            <postalCode> {billingAddress.postCode.getOrElse("")} </postalCode>
            <city>{billingAddress.townCity}</city>
            <countryCode>{iso3CountryCodeToIso2CountryCode(billingAddress.countryCode)}</countryCode>
            {buildPhone(payee)}
          </address>
        </billingAddress>
    }.getOrElse(NodeSeq.Empty)


  private def iso3CountryCodeToIso2CountryCode(iso3CountryCode: String) =
    localeMap.getOrElse(iso3CountryCode, "GB")

  private def buildPhone(payee: Payee) = payee.phoneNumber.map { pn =>
    <telephoneNumber> {pn} </telephoneNumber>
  }.getOrElse("")

  private def buildNames(payee: Payee) =
    payee.givenName.map{givenName => <firstName> {givenName} </firstName> }.getOrElse(NodeSeq.Empty) ++
    payee.familyName.map{lastName => <lastName> {lastName} </lastName> }

}
