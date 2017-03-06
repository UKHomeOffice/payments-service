package worldpay

import play.api.mvc._
import scala.concurrent.Future

trait ValidClientId {

  self: Controller =>

  def withValidClientIdHeader[T](f: String => Action[T]): Action[T] = {

    Action.async(f("").parser) {
      request =>
        request.headers.get("X-CLIENT-ID") match {
          case None => Future.successful(Forbidden(s"Client id not provided"))
          case Some(cId) =>
            if (conf.Config.allClientIds.contains(cId)) {
              f(cId)(request)
            } else {
              Future.successful(Forbidden(s"Invalid client Id : $cId"))
            }
        }

    }

  }

}