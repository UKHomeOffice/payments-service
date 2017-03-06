package worldpay

import model.Payment

import scala.xml.Node

object OrderInquiryMessageBuilder {

  def build(payment: Payment, profile: WorldPayProfile): Node = {
    <paymentService version="1.4" merchantCode={profile.username}>
      <inquiry>
        <orderInquiry orderCode={payment.externalReference}/>
      </inquiry>
    </paymentService>
  }

}
