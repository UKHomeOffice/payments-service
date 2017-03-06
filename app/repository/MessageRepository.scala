package repository

import java.lang.Math.pow

import conf.Config
import logging.Logging
import model._
import org.joda.time.{DateTime, Period}
import reactivemongo.api.DB
import reactivemongo.bson._
import reactivemongo.core.commands.LastError
import reactivemongo.extensions.dao.BsonDao
import reactivemongo.extensions.dao.Handlers._

import scala.concurrent.Future

class MessageRepository(db: () => DB,
                        expiredPeriod: Period = Config.messageExpiredPeriod,
                        inProgressRetryPeriod: Period = Config.messageInProgressRetryPeriod,
                        initialDelayInSeconds: Int = Config.initialSchedulingDelayInSeconds)
  extends BsonDao[Message, BSONObjectID](db, "messages") with Logging {

  def queueMessageWithPayload[T <: AnyRef](notificationUrl: String, payload: T)(implicit writer: BSONDocumentWriter[T], manifest: Manifest[T]): Future[Message] = {
    val message = Message(notificationUrl, writer.write(payload), DateTime.now, DateTime.now.plus(expiredPeriod))
    save(message).map {
      case LastError(true, _, _, _, _, _, _) =>
        logger.info(s"Message to: '${message.notificationUrl}' successfully queued")
        message
    } recover {
      case e: LastError =>
        throw new RuntimeException(e.errMsg.getOrElse(s"Queueing of message to: '${message.notificationUrl}' failed"))
    }
  }

  def updateMessageToBeRetried(message: Message): Future[LastError] = {
    val appliedDelay = delay(1, message.attempts.getOrElse(0))
    update(BSONDocument("_id" -> message._id), message.copy(status = Some("FAILED"), attemptAt = DateTime.now.plusSeconds(appliedDelay)))
  }

  def nextMessage: Future[Option[Message]] =
    findAndUpdate(findQueuedMessagesQuery, toInProgress, sort = BSONDocument("attemptAt" -> 1), fetchNewObject = true)

  private def findQueuedMessagesQuery =
    BSONDocument("$or" -> Seq(newMessage, failedAndReadyToBeRetriedAndNotExpiredMessage, inProgressForTooLongMessage))

  private def inProgressForTooLongMessage =
    BSONDocument("$and" -> Seq(
      BSONDocument("status" -> "IN-PROGRESS"),
      BSONDocument("attemptAt" -> BSONDocument("$lt" -> DateTime.now.minus(inProgressRetryPeriod))))
    )

  private def failedAndReadyToBeRetriedAndNotExpiredMessage =
    BSONDocument("$and" -> Seq(
      BSONDocument("status" -> "FAILED"),
      BSONDocument("expiredAt" -> BSONDocument("$gt" -> DateTime.now)),
      BSONDocument("attemptAt" -> BSONDocument("$lt" -> DateTime.now))))

  private def toInProgress = BSONDocument(
    "$set" -> BSONDocument("status" -> "IN-PROGRESS", "attemptAt" -> DateTime.now),
    "$inc" -> BSONDocument("attempts" -> 1))

  private def newMessage = BSONDocument("status" -> BSONDocument("$exists" -> false))

  private def delay(initial: Int, retries: Int): Int = initial * pow(2, retries).toInt

}
