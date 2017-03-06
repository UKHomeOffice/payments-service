package report

import logging.Logging
import org.joda.time.{DateTime, DateTimeZone}
import report.model.WorldPayReportEntry

import scala.collection.immutable.Seq
import scala.util.Try
import scala.xml.{NodeSeq, XML}

class WorldPayXmlExtractor extends Logging {

  case class PaymentAmount(amount: BigDecimal, currency: String)

  def extractBatchId(report: String): Option[String] =
    Try {
      val xml = XML.loadString(report)
      (xml \\ "paymentService" \ "notify" \ "report" \ "@batchId").toString()
    }.toOption

  def extractReportEntries(report: String): Seq[WorldPayReportEntry] =
    Try {
      val xml = XML.loadString(report)

      val merchantCode = xml \\ "paymentService" \ "@merchantCode"
      if (merchantCode.length == 0) {
        noMerchantCodeFoundError()
        Seq.empty
      } else {
        val merchantCodeString = merchantCode.toString()
        (xml \\ "paymentService" \ "notify" \ "report" \ "orderStatusEvent").map(orderStatusEvent => {
          val externalReference = (orderStatusEvent \ "@orderCode").toString()
          val status = (orderStatusEvent \ "journal" \ "@journalType").toString()
          val dateOfPayment = convertDate(orderStatusEvent)
          val paymentMethod = (orderStatusEvent \ "payment" \ "paymentMethod").text
          val paymentCurrency = (orderStatusEvent \ "payment" \ "amount" \ "@currencyCode").toString()
          val paymentAmount = obtainPaymentAmount(orderStatusEvent)
          val accountTrans = accountTransactions(orderStatusEvent)
          val commissionAmount = accountTrans.get("commission").map(_.amount)
          val netAmount = accountTrans.get("net").map(_.amount)
          val settlementCurrency = accountTrans.get("net").map(_.currency).getOrElse("")

          WorldPayReportEntry(
            merchantCode = merchantCodeString,
            externalReference = externalReference,
            status = status,
            date = dateOfPayment,
            paymentMethod = paymentMethod,
            paymentCurrency = paymentCurrency,
            paymentAmount = paymentAmount,
            settlementCurrency = settlementCurrency,
            commissionAmount = commissionAmount,
            netAmount = netAmount)
        })
      }
    }.getOrElse(Seq.empty)

  private def noMerchantCodeFoundError() =
    error("Failed to parse merchant code for financial report.")

  private def convertDate(xml: NodeSeq): DateTime = (xml \ "journal" \ "bookingDate" \ "date").map(bookingDate => {
    val day = (bookingDate \ "@dayOfMonth").toString().toInt
    val month = (bookingDate \ "@month").toString().toInt
    val year = (bookingDate \ "@year").toString().toInt
    val hour = (bookingDate \ "@hour").toString().toInt
    val minute = (bookingDate \ "@minute").toString().toInt
    val second = (bookingDate \ "@second").toString().toInt
    new DateTime(year, month, day, hour, minute, second, DateTimeZone.UTC)
  }).head

  private def accountTransactions(xml: NodeSeq): Map[String, PaymentAmount] = {
    val listOfTransactions = (xml \ "journal" \ "accountTx").map(trans => {
      val accountType = (trans \ "@accountType").toString()
      val value = (trans \ "amount" \ "@value").toString().toInt
      val currency = (trans \ "amount" \ "@currencyCode").toString()
      val exponent = (trans \ "amount" \ "@exponent").toString().toInt
      val creditDebit = (trans \ "amount" \ "@debitCreditIndicator").toString()
      val paymentAmount = buildPaymentAmount(value, exponent, creditDebit)
      (accountType, PaymentAmount(paymentAmount, currency))
    })
    listOfTransactions.foldLeft[Map[String, PaymentAmount]](Map.empty) {
      (result, tuple) =>
        tuple._1 match {
          case "SETTLED_BIBIT_NET" => Map("net" -> tuple._2) ++ result
          case "SETTLED_BIBIT_COMMISSION" => Map("commission" -> tuple._2) ++ result
          case _ => result
        }
    }
  }

  private def obtainPaymentAmount(xml: NodeSeq): BigDecimal = {
    (xml \ "payment" \ "amount").map(paymentDetails => {
      val value = (paymentDetails \ "@value").toString().toInt
      val exponent = (paymentDetails \ "@exponent").toString().toInt
      val creditDebit = (paymentDetails \ "@debitCreditIndicator").toString()
      buildPaymentAmount(value, exponent, creditDebit)
    }).head
  }

  private def buildPaymentAmount(value: Int, exponent: Int, creditDebit: String): BigDecimal =
    creditDebit.toLowerCase match {
      case "credit" => BigDecimal(value, exponent)
      case "debit" => -BigDecimal(value, exponent)
      case x => throw new IllegalArgumentException(s"Expected credit or debit but found: $x")
    }
}

