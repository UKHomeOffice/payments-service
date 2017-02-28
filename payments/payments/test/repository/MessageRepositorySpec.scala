package repository

import _root_.utils.Eventually
import json.JsonFormats
import model.{Message, Paid, PaymentEventNotification}
import org.joda.time.DateTimeUtils._
import org.joda.time.{Period, DateTime}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import reactivemongo.bson._
import reactivemongo.extensions.dao.Handlers._
import repository.TypeHandlers._

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class MessageRepositorySpec extends WordSpec with Matchers with ScalaFutures with BeforeAndAfter with Eventually with JsonFormats {
  override implicit def patienceConfig: PatienceConfig = PatienceConfig(20 seconds)
  val expiredPeriod = Period.days(2)
  val inProgressRetryPeriod = Period.minutes(2)
  val initialDelayInSeconds = 1
  val repo = new MessageRepository(DbConnection.db, expiredPeriod, inProgressRetryPeriod, initialDelayInSeconds)

  before {
    setCurrentMillisFixed(DateTime.now.getMillis)

    eventually {
      repo.remove(BSONDocument.empty)
    }
  }

  after {
    setCurrentMillisSystem()
  }

  "save" should {
    "save message irrespective of data and we are able to serialize it as a json" in {
      val paymentEvent = PaymentEventNotification(Some("pnn"), "appId", 100d, Some("GBP"), Paid)
      val url = "someUrl"

      val message = eventually(repo.queueMessageWithPayload(url, paymentEvent))

      repo.findOne(BSONDocument()).futureValue.get should be(Message(url, PaymentEventNotification.dbReadWriteHandler.write(paymentEvent), DateTime.now(), DateTime.now.plus(expiredPeriod), None, None, None, message._id))
    }
  }

  "nextMessage" should {

    "return next message with status IN_PROGRESS and attempt 1 for new messages" in {
      val message = Message("url", BSONDocument("key" -> "value"), DateTime.now, DateTime.now)
      eventually(repo.insert(message))

      repo.nextMessage.futureValue.get should be(message.copy(attempts = Some(1), status = Some("IN-PROGRESS")))
    }

    "return message in status FAILED when attemptAt is less than now" in {
      val message = Message("url", BSONDocument("key" -> "value"), DateTime.now.minusSeconds(1), DateTime.now.plusMinutes(20), Some(2), Some("FAILED"))
      eventually(repo.insert(message))

      repo.nextMessage.futureValue.get should be(message.copy(attempts = Some(3), status = Some("IN-PROGRESS"), attemptAt = DateTime.now , expiredAt = DateTime.now.plusMinutes(20)))
    }

    "return no message if there is message in status FAILED with attemptAt still in future" in {
      val message = Message("url", BSONDocument("key" -> "value"), DateTime.now.plusMinutes(1), DateTime.now.plusMinutes(20), Some(2), Some("FAILED"))
      eventually(repo.insert(message))

      repo.nextMessage.futureValue should be(None)
    }

    "return no message if there is expired message in status FAILED" in {
      val message = Message("url", BSONDocument("key" -> "value"), DateTime.now.minusSeconds(1), DateTime.now.minusMinutes(20), Some(2), Some("FAILED"))
      eventually(repo.insert(message))

      repo.nextMessage.futureValue should be(None)
    }

    "return message in status IN_PROGRESS which stayed in this status longer than configured value" in {
      val message = Message("url", BSONDocument("key" -> "value"), DateTime.now.minus(inProgressRetryPeriod).minusSeconds(1), DateTime.now, Some(2), Some("IN-PROGRESS"))
      eventually(repo.insert(message))

      repo.nextMessage.futureValue.get should be(message.copy(attempts = Some(3), status = Some("IN-PROGRESS"), attemptAt = DateTime.now))
    }

    "return no message if there is message in status IN_PROGRESS with attempted at less than configured value" in {
      val message = Message("url", BSONDocument("key" -> "value"), DateTime.now, DateTime.now, Some(2), Some("IN-PROGRESS"))
      eventually(repo.insert(message))

      repo.nextMessage.futureValue should be(None)
    }
  }

  "updateMessageToBeRetried" should {
    "update message to status FAILED and calculated attemptAt" in {
      val message = Message("url", BSONDocument("key" -> "value"), DateTime.now, DateTime.now, Some(2), Some("IN-PROGRESS"))

      eventually(repo.insert(message))

      eventually(repo.updateMessageToBeRetried(message))

      repo.findById(message._id).futureValue.get should be(message.copy(status = Some("FAILED"), attemptAt = DateTime.now().plusSeconds(initialDelayInSeconds * Math.pow(2,message.attempts.get).toInt)))
    }
  }
}

