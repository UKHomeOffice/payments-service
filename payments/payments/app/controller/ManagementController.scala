package controller

import conf.ComponentRegistry
import management.{BuildInfo, MongoHealthCheck}
import play.api.mvc.{Action, AnyContent, Results}
import logging.MdcExecutionContext.Implicit.defaultContext

object ManagementController extends ManagementControllerClass

class ManagementControllerClass(mongoHealthCheck: MongoHealthCheck = ComponentRegistry.mongoHealthCheck,
                                buildInfo: Option[BuildInfo] = BuildInfo.fromApplicationConfig) {

  def healthcheck: Action[AnyContent] = Action.async { _ =>
      mongoHealthCheck.ping().map {
        case true =>
          Results.Ok(healthyMessage)
        case _ =>
          Results.InternalServerError("Mongo db did not respond respond correctly")
      }
  }

  private val healthyMessage = {
    val buildNumber = buildInfo.map(_.buildNumber).getOrElse("[not available]")
    s"""|healthy!
        |build-number: $buildNumber""".stripMargin
  }


}
