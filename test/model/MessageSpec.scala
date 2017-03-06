package model

import json.JsonFormats
import org.joda.time.DateTime
import org.json4s.jackson.Serialization
import org.scalatest.{Matchers, WordSpec}
import report.model.FinancialReport
import reactivemongo.extensions.dao.Handlers._
import repository.TypeHandlers._

class MessageSpec extends WordSpec with Matchers with JsonFormats {

  "Message" should {
    "return payload as json" in {
      val report = FinancialReport("code", "batch", List(List("a", "b", "c")), List(List("d", "e", "f")))
      val payload = FinancialReport.dbReadWriteHandler.write(report)
      val message = Message("url", payload, DateTime.now, DateTime.now)

      message.jsonPayload should be(Serialization.write(report))
    }
  }
}
