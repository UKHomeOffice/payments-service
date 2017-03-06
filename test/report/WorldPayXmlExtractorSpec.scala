package report

import org.joda.time.{DateTime, DateTimeZone}
import org.scalatest.{Matchers, WordSpec}
import utils.Mocking

import scala.io.Source

class WorldPayXmlExtractorSpec extends WordSpec with Matchers with Mocking {

  val service = new WorldPayXmlExtractor()
  val report = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("exampleWorldpayReport.xml")).mkString

  "extractBatchId" should {

    "return batchId if found" in {
      service.extractBatchId(report) should be(Some("5"))
    }

    "return None" in {
      service.extractBatchId("report") should be(None)
    }
  }

  "extractReportEntries" should {

    "convert xml to list of WorldPayReportEntries" in {
      val entries = service.extractReportEntries(report)

      entries(2).merchantCode shouldBe "MERCHANTCODE"
      entries(2).externalReference shouldBe "3030000707821"
      entries(2).status shouldBe "REFUNDED"
      entries(2).date shouldBe new DateTime(2014, 7, 27, 7, 56, 3, DateTimeZone.UTC)
      entries(2).paymentMethod shouldBe "MASTER_CARD"
      entries(2).paymentCurrency shouldBe "INR"
      entries(2).paymentAmount shouldBe 913.00
      entries(2).settlementCurrency shouldBe "USD"
      entries(2).commissionAmount shouldBe None
      entries(2).netAmount shouldBe Some(BigDecimal("-143.36"))
      entries(2).grossAmount shouldBe BigDecimal("-143.36")

      entries(1).merchantCode shouldBe "MERCHANTCODE"
      entries(1).externalReference shouldBe "3030000707820"
      entries(1).status shouldBe "REFUNDED"
      entries(1).date shouldBe new DateTime(2014, 7, 27, 7, 56, 3, DateTimeZone.UTC)
      entries(1).paymentMethod shouldBe "MASTER_CARD"
      entries(1).paymentCurrency shouldBe "DOLLAR"
      entries(1).paymentAmount shouldBe 913.00
      entries(1).settlementCurrency shouldBe "USD"
      entries(1).commissionAmount shouldBe None
      entries(1).netAmount shouldBe Some(BigDecimal("-143.36"))
      entries(1).grossAmount shouldBe BigDecimal("-143.36")

      entries(0).merchantCode shouldBe "MERCHANTCODE"
      entries(0).externalReference shouldBe "0000000000001"
      entries(0).status shouldBe "SETTLED"
      entries(0).date shouldBe new DateTime(2014, 7, 21, 7, 56, 3, DateTimeZone.UTC)
      entries(0).paymentMethod shouldBe "VISA_CREDIT-SSL"
      entries(0).paymentCurrency shouldBe "CNY"
      entries(0).paymentAmount shouldBe 2013.00
      entries(0).settlementCurrency shouldBe "USD"
      entries(0).commissionAmount shouldBe Some(BigDecimal("7.11"))
      entries(0).netAmount shouldBe Some(BigDecimal("308.97"))
      entries(0).grossAmount shouldBe BigDecimal("316.08")
    }

    "fail to convert XML into entries" in {
      service.extractReportEntries("This is not valid XML.") shouldBe Seq.empty
    }

    "failing to find merchant code in XML" in {
      service.extractReportEntries("<p>No merch code here.</p>") shouldBe Seq.empty
    }
  }
}
