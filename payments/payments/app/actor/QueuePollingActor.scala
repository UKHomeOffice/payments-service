package actor

import akka.actor.{Actor, ActorRef}
import model.Message
import repository.MessageRepository
import logging.MdcExecutionContext.Implicit.defaultContext

case object PollMessage

case class SendNotification(message: Message)

case class NotificationSuccess(message: Message)

case class NotificationFailure(message: Message)

class QueuePollingActor(messageRepository: MessageRepository, notificationActor: ActorRef) extends Actor {
  override def receive: Receive = {

    case PollMessage =>
      messageRepository.nextMessage.map {
        case Some(m) =>
          notificationActor ! SendNotification(m)
        case None =>
      }

    case NotificationSuccess(m) =>
      messageRepository.removeById(m._id)

    case NotificationFailure(m) =>
      messageRepository.updateMessageToBeRetried(m)

  }
}
