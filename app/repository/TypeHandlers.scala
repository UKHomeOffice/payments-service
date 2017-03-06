package repository

import model._
import reactivemongo.bson.{BSONString, BSONDouble, BSONHandler}
import reactivemongo.bson.BSONDouble
import reactivemongo.bson.BSONString

object TypeHandlers {

  implicit object BigDecimalHandler extends BSONHandler[BSONDouble, BigDecimal] {
    def read(double: BSONDouble) = BigDecimal(double.value)

    def write(bd: BigDecimal) = BSONDouble(bd.toDouble)
  }

  implicit object PaymentStatusHandler extends BSONHandler[BSONString, PaymentStatus] {
    def read(status: BSONString): PaymentStatus = status match {
      case BSONString("PAID") => Paid
      case BSONString("REFUSED") => Refused
      case BSONString("UNKNOWN") => Unknown
      case BSONString("PENDING") => Pending
      case BSONString("CANCELLED") => Cancelled
      case BSONString("REFUNDED") => Refunded
    }

    def write(status: PaymentStatus) = BSONString(status.toString)
  }

}
