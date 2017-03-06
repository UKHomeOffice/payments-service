package utils

import java.util.concurrent.{TimeUnit, CountDownLatch}
import java.util.concurrent.TimeUnit.SECONDS

import org.scalatest.concurrent.ScalaFutures

import logging.MdcExecutionContext.Implicit.defaultContext
import scala.concurrent.Future
import scala.util.Try

trait Eventually {

  self: ScalaFutures =>

  def eventually[T](f: => Future[T]): T = {
    val latch = new CountDownLatch(1)
    val result: Future[T] = f.andThen {
      case le => latch.countDown()
    }
    latch.await(5, SECONDS)
    result.futureValue
  }

  def eventuallySucceed[T](f: => T): T = {
    val latch = new CountDownLatch(1)

    def eventuallyInternal: T = {
        (Try {
          val result = f
          latch.countDown
          result
        } recover {
          case _ =>
            Thread.sleep(100)
            eventuallyInternal
        }).get
      }

    val result = scala.concurrent.future {eventuallyInternal}
    latch.await(5, SECONDS)
    result.futureValue
  }
}
