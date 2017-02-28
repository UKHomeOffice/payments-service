package client

import logging.Logging
import play.api.Play.current
import play.api.libs.ws.{WS, WSAuthScheme, WSRequestHolder}

import logging.MdcExecutionContext.Implicit.defaultContext
import scala.concurrent.Future
import scala.util.Try

case class AuthDetails(username: String, password: String, authScheme: WSAuthScheme)

private case class Non200StatusException(url: String, status: Int) extends RuntimeException(s"Failed posting to : $url with http status : $status")

class HttpClient(clientFor: (String) => WSRequestHolder = HttpClient.clientFor) extends Logging {
  private val timeout = 20000

  def post(url: String, body: String, authDetails: Option[AuthDetails] = None, contentType: String = "application/json"): Future[String] = {
    val errMsg = s"Could not post to client with url : $url"
    Try {
      buildClient(url, body, authDetails, contentType).post(body)
    }.toOption match {
      case Some(futureResponse) => futureResponse.map { response =>
        response.status match {
          case s if s >= 200 && s < 300 => response.body
          case s => throw Non200StatusException(url, s)
        }
      } recover {
        case t: Non200StatusException =>
          logger.error(t.getMessage)
          throw t
        case t =>
          logger.error(errMsg, t)
          throw t
      }
      case None =>
        logger.error(errMsg)
        Future.failed(new RuntimeException(errMsg))
    }
  }

  private def buildClient(url: String, body: String, authDetails: Option[AuthDetails], contentType: String): WSRequestHolder = {
    val client: WSRequestHolder = clientFor(url)
      .withRequestTimeout(timeout)
      .withHeaders("Content-Type" -> contentType)

    authDetails.map { auth =>
      client.withAuth(auth.username, auth.password, auth.authScheme)
    }.getOrElse(client)
  }
}

object HttpClient {

  val clientFor: (String) => WSRequestHolder = (u: String) => WS.url(u)

}
