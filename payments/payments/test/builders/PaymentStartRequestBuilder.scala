package builders

import model.Types.{ExternalReference, InternalReference}
import model._

import scala.util.Random
import controller.PaymentStartRequest

object PaymentStartRequestBuilder {

  val billingAddress = BillingAddress("line1", Some("line2"), Some("line3"), "London", None, Some("EC2 2CE"), "CHN")
  val payee = Payee(Some("Mr"), Some("givenName"), Some("familyName"), Some("lincolnshire.poacher@example.com"), Some("0123456789"), Some(billingAddress))
  val paymentProfile = PaymentProfile("VISA-SSL", "poland")
  val paymentItems = List(PaymentItem("code", 234.56))
  val payment = PaymentStartRequest("pnn", "anAppId", payee, "<div><h2>Payment Title</h2></div>", "applicants details", paymentProfile, paymentItems, 234.56, "GBP")

  def apply(internalReference: InternalReference = "appId", externalReference: ExternalReference = s"pnn${Random.nextInt(1000)}", paymentItems: List[PaymentItem] = paymentItems): PaymentStartRequest =
    payment.copy(externalReference = externalReference, internalReference = internalReference, paymentItems = paymentItems)

}