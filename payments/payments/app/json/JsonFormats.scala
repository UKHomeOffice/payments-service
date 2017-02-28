package json

import model.{Unknown, Paid, PaymentStatus, Pending}
import org.json4s._
import org.json4s.ext.JodaTimeSerializers

trait JsonFormats {

  implicit val formats: Formats =  DefaultFormats.withBigDecimal + PaymentStatusSerializer ++ JodaTimeSerializers.all

}

object PaymentStatusSerializer extends CustomSerializer[PaymentStatus](_ => ({
    case JString(ps) => ps match {
      case "PAID" => Paid
      case "NOT_PAID" => Unknown
      case "PENDING" => Pending
    }
    case JNull => null
  },
    {
    case ps: PaymentStatus => JString(ps.toString)
  })
)
