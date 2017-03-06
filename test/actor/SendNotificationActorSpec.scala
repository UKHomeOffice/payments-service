package actor

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import builders.MessageBuilder
import client.HttpClient
import org.mockito.Mockito.when
import org.scalatest.{Matchers, WordSpecLike}
import utils.Mocking

import scala.concurrent.Future

class SendNotificationActorSpec extends TestKit(ActorSystem("SendNotificationActorSpec")) with WordSpecLike with Matchers with ImplicitSender with Mocking {
  val httpClient = mock[HttpClient]
  val actorRef = system.actorOf(Props(new SendNotificationActor(httpClient)))

  "Send Notification Actor" should {
    "post a message and respond with NotificationSuccess" in {
      val message = MessageBuilder()

      when(httpClient.post(message.notificationUrl, message.jsonPayload)).thenReturn(Future.successful("some body string"))

      actorRef ! SendNotification(message)

      expectMsg(NotificationSuccess(message))
    }

    "respond with NotificationFailure if sending message fails" in {
      val message = MessageBuilder()

      val errorMessage = "failure"
      when(httpClient.post(message.notificationUrl, message.jsonPayload)).thenReturn(Future.failed(new RuntimeException(errorMessage)))

      actorRef ! SendNotification(message)

      expectMsg(NotificationFailure(message.copy(failureReason = Some(errorMessage))))
    }
  }

}
