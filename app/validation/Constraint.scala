package validation

case class Constraint[T](vf: T => Boolean, errorMessage: String) {

  def isValid(e: T): Boolean = vf(e)
}