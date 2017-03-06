import actor.{PollMessage, QueuePollingActor, SendNotificationActor}
import akka.actor.Props
import akka.routing.BalancingPool
import conf.{ComponentRegistry, Config}
import play.api.mvc._
import play.api.{Application, GlobalSettings}
import play.libs.Akka
import repository.DuplicateExternalReference
import worldpay.InvalidPaymentProfileException

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import logging.MdcExecutionContext.Implicit.defaultContext

object Global extends GlobalSettings {

  override def onError(request: RequestHeader, ex: Throwable): Future[Result] = ex.getCause match {
    case ex: InvalidPaymentProfileException => Future.successful(Results.BadRequest(ex.getMessage))
    case ex: DuplicateExternalReference => Future.successful(Results.BadRequest(ex.getMessage))
    case th: Throwable => Future.successful(Results.InternalServerError(th.getMessage))
  }

  override def onStart(app: Application): Unit = {
    val notificationRouter = Akka.system.actorOf(BalancingPool(5).props(Props(new SendNotificationActor())), "notification-router")
    val pollingActor = Akka.system.actorOf(Props(new QueuePollingActor(ComponentRegistry.messageRepository, notificationRouter)))

    Akka.system.scheduler.schedule(0 seconds, 100 millis)(pollingActor ! PollMessage)
  }
}
