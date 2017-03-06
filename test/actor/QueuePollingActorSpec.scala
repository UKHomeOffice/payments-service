package actor



import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import builders.MessageBuilder
import org.mockito.Mockito.{verify, when}
import org.scalatest.{Matchers, WordSpecLike}
import play.test.WithApplication
import repository.MessageRepository
import utils.Mocking

import scala.concurrent.Future

class QueuePollingActorSpec extends TestKit(ActorSystem("PollingActorSpec")) with WordSpecLike with Matchers with ImplicitSender with Mocking {
  val messageRepository = mock[MessageRepository]
  val pollingActorRef = system.actorOf(Props(new QueuePollingActor(messageRepository,self)))

  "Queue Polling actor" should {

    "get next message from the queue and send notification message" in new WithApplication {
      val nextMessage = MessageBuilder()
      when(messageRepository.nextMessage).thenReturn(Future.successful(Some(nextMessage)))

      pollingActorRef ! PollMessage

      expectMsg(SendNotification(nextMessage))
    }

    "not send notification if no next message" in new WithApplication {
      val nextMessage = MessageBuilder()
      when(messageRepository.nextMessage).thenReturn(Future.successful(None))

      pollingActorRef ! PollMessage

      expectNoMsg
    }

    "remove message on NotificationSuccess" in {
      val message = MessageBuilder()

      pollingActorRef ! NotificationSuccess(message)

      expectNoMsg
      verify(messageRepository).removeById(message._id)
    }

    "update message on NotificationFailure" in {
      val message = MessageBuilder()

      pollingActorRef ! NotificationFailure(message)

      expectNoMsg
      verify(messageRepository).updateMessageToBeRetried(message)
    }
  }

}
