package model

import org.joda.time.DateTime
import play.api.libs.json.JsValue
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONHandler, BSONObjectID, Macros}
import reactivemongo.extensions.BSONFormats
import reactivemongo.extensions.dao.Handlers._
import repository.TypeHandlers._

case class Message(notificationUrl: String, data: BSONDocument, attemptAt: DateTime, expiredAt: DateTime, attempts: Option[Int] = None, status: Option[String] = None, failureReason: Option[String] = None,_id: BSONObjectID = BSONObjectID.generate) {

  def jsonPayload: String = BSONFormats.BSONDocumentFormat.writes(data).as[JsValue].toString()
}

object Message {
  implicit val dbReadWriteHandler: BSONDocumentReader[Message] with BSONDocumentWriter[Message] with BSONHandler[BSONDocument, Message] = Macros.handler[Message]
}
