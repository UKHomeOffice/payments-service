package binder

import play.api.mvc.PathBindable.Parsing

object PathBinders {

  implicit object bindableBigDecimal extends Parsing[BigDecimal](
    BigDecimal(_), _.toString, (key: String, e: Exception) => "Cannot parse parameter %s as BigDecimal: %s".format(key, e.getMessage)
  )

}
