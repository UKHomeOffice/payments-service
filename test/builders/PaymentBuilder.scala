package builders

import model.Types.{ExternalReference, InternalReference}
import model._

import scala.util.Random

object PaymentBuilder {

  val billingAddress = BillingAddress("line1", Some("line2"), Some("line3"), "London", None, Some("EC2 2CE"), "CHN")
  val payee = Payee(Some("Mr"), Some("givenName"), Some("familyName"), Some("lincolnshire.poacher@example.com"), Some("0123456789"), Some(billingAddress))
  val defaultProfile = PaymentProfile("VISA-SSL", "poland")
  val paymentItems = List(PaymentItem("code", 234.56))
  val payment = Payment("pnn", "anAppId", payee, "applicants details", defaultProfile, paymentItems, 234.56, "GBP","DCJ")

  def apply(internalReference: InternalReference = "appId",
            externalReference: ExternalReference = s"pnn${Random.nextInt(1000)}",
            paymentItems: List[PaymentItem] = paymentItems,
            profile: PaymentProfile = defaultProfile): Payment =
    payment.copy(externalReference = externalReference, internalReference = internalReference, profile = profile, paymentItems = paymentItems)

}