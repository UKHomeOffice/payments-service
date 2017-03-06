package actor

import akka.actor.Actor
import client.HttpClient
import logging.Logging
import logging.MdcExecutionContext.Implicit.defaultContext

class SendNotificationActor(httpClient: HttpClient = new HttpClient) extends Actor with Logging {

  override def receive: Receive = {
    case SendNotification(m) =>
      val s = sender()
      httpClient.post(m.notificationUrl, m.jsonPayload).map {
        _ => s ! NotificationSuccess(m)
      } recover {
        case t =>
          s ! NotificationFailure(m.copy(failureReason = Some(t.getMessage)))
      }
  }
}
