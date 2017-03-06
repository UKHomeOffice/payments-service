package builders

import model.Types._
import model._
import org.joda.time.DateTime
import org.joda.time.DateTime.now

object PaymentEventBuilder {

  def apply(internalReference: InternalReference = "appId", worldPayStatus: WorldPayStatus = "AUTHORISED") =
    PaymentEvent("pnn", internalReference, Some("paymentId"), worldPayStatus, 100.00, "GBP", "VISA-SSL", now.withMillisOfSecond(0))

  def apply(payment: Payment, worldPayStatus: WorldPayStatus) = PaymentEvent(payment.externalReference, payment.internalReference, None, worldPayStatus, payment.total, payment.currency, payment.profile.paymentType, now.withMillisOfSecond(0))

  def apply(payment: Payment, worldPayStatus: WorldPayStatus, timestamp: DateTime) = PaymentEvent(payment.externalReference, payment.internalReference, None, worldPayStatus, payment.total, payment.currency, payment.profile.paymentType, timestamp)
}