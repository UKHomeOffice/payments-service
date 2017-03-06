package report.model

import java.io.StringWriter

import reactivemongo.bson.BSONObjectID

import scala.xml.dtd.{DocType, PublicID}
import scala.xml.{Node, XML}

case class Email(_id: BSONObjectID,
                 applicationId: BSONObjectID,
                 toAddress: Set[String],
                 fromAddress: String,
                 subject: String,
                 body: String,
                 attachments: List[Attachment] = Nil,
                 contentType: ContentType = HTML,
                 route: EmailRoute = External)

object Email {
  def apply(applicationId: BSONObjectID,
            toAddress: Set[String],
            fromAddress: String,
            subject: String,
            body: String,
            attachment: List[Attachment],
            contentType: ContentType,
            route: EmailRoute): Email =
    Email(BSONObjectID.generate, applicationId, toAddress, fromAddress, subject, body, attachment, contentType, route)

  def apply(applicationId: BSONObjectID,
            toAddress: Set[String],
            fromAddress: String,
            subject: String,
            body: Node,
            attachment: List[Attachment],
            contentType: ContentType,
            route: EmailRoute): Email =
    Email(BSONObjectID.generate, applicationId, toAddress, fromAddress, subject, toHtml(body), attachment, contentType, route)

  private def toHtml(xml: Node) = {
    val docType = DocType("html", PublicID("-//W3C//DTD HTML 4.01 Transitional//EN", null), Nil)
    val stringWriter = new StringWriter()
    XML.write(stringWriter, xml, "UTF-8", xmlDecl = false, doctype = docType)
    stringWriter.toString
  }
}

case class Attachment(name: String, content: Array[Byte], contentType: String = "application/pdf")

sealed trait ContentType {
  val asString: String
}

case object PlainText extends ContentType {
  val asString = "PlainText"
}

case object HTML extends ContentType {
  val asString = "HTML"
}


sealed trait EmailRoute {
  val asString: String
}

case object External extends EmailRoute {
  val asString = "External"
}

case object Internal extends EmailRoute {
  val asString = "Internal"
}