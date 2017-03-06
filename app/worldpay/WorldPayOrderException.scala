package worldpay

case class WorldPayOrderException(applicationId: String, errors: Map[String,String] = Map.empty)
  extends RuntimeException(s"unable to create WorldPay Order with [$applicationId]. ${errors.toString}")
