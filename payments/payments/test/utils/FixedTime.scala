package utils

import org.joda.time.DateTime
import org.joda.time.DateTimeUtils._
import org.scalatest.BeforeAndAfter

trait FixedTime {

  self : BeforeAndAfter =>

  before {
    setCurrentMillisFixed(DateTime.now.getMillis)
  }

  after {
    setCurrentMillisSystem()
  }

}
