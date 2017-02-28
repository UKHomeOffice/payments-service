package repository

import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import reactivemongo.bson.BSONDocument
import report.model.PaymentTransactionReport
import utils.Eventually

import scala.concurrent.duration.DurationLong
import scala.language.postfixOps

class PaymentTransactionReportRepositorySpec extends WordSpec with Matchers with ScalaFutures with BeforeAndAfter with Eventually {
  override implicit def patienceConfig: PatienceConfig = PatienceConfig(10 seconds)
  val repo = new PaymentTransactionReportRepository(DbConnection.db)

  before {
    eventually {
      repo.remove(BSONDocument.empty)
    }
  }

  "saveReport" should {
    "store the report in mongo" in {
      val worldPayReport = PaymentTransactionReport(Some("5"), "report", DateTime.now)

      eventually(repo.save(worldPayReport))

      val worldPayReports = repo.findAll().futureValue

      worldPayReports.size shouldBe 1
      worldPayReports.head shouldBe worldPayReport
    }
  }
}
