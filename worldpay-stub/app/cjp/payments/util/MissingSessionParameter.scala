package cjp.payments.util

class MissingSessionParameter(val name: String) extends Exception(s"Missing session parameter [$name]")
