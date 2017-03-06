package logging

import play.api.{Logger, LoggerLike}

trait Logging extends LoggerLike {

  val logger: org.slf4j.Logger = Logger.logger

  def logDurationOf[T](methodName: String)(fn: => T): T = {
    logger.info(s"Executing $methodName")
    val start = System.currentTimeMillis()
    val result = fn
    val time = System.currentTimeMillis() - start
    logger.info(s"Executed $methodName in {}ms", time)
    result
  }
}
