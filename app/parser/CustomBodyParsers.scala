package parser

import json.JsonFormats
import org.json4s.jackson.Serialization._
import play.api.libs.iteratee.{Iteratee, Traversable}
import play.api.mvc.{Results, BodyParsers, BodyParser}
import play.api.mvc.BodyParsers.parse.DEFAULT_MAX_TEXT_LENGTH
import logging.MdcExecutionContext.Implicit.defaultContext
import scala.concurrent.Future

trait CustomBodyParsers extends JsonFormats {

  def json[T](implicit manifest: Manifest[T]): BodyParser[T] = BodyParsers.parse.when(
    _.contentType.exists(m => m.equalsIgnoreCase("text/json") || m.equalsIgnoreCase("application/json")),
    json4sParser[T],
    _ => Future.successful(Results.BadRequest("Expecting text/json or application/json body"))
  )

  private def json4sParser[T](implicit manifest: Manifest[T]): BodyParser[T] = BodyParser("json4s") { request =>
    Traversable.takeUpTo[Array[Byte]](DEFAULT_MAX_TEXT_LENGTH)
      .transform(Iteratee.consume[Array[Byte]]().map(c =>
        read[T](new String(c, request.charset.getOrElse("UTF-8"))))
      )
      .flatMap(Iteratee.eofOrElse(Results.EntityTooLarge))
  }


}
