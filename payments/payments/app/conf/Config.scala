package conf

import java.util

import com.typesafe.config.ConfigFactory
import org.joda.time.Period

import scala.collection.JavaConversions._


object Config {

  val config: com.typesafe.config.Config = ConfigFactory.load()

  val allClientSpecificConfigs: com.typesafe.config.Config = config.getObject("clients").toConfig

  val allClientIds: Set[String] = config.getObject("clients").keySet().toSet

  val mongoHost: util.List[String] = config.getStringList("mongodb.hosts")
  val mongoUsername: String = config.getString("mongodb.username")
  val mongoPassword: String = config.getString("mongodb.password")
  val mongoDb: String = config.getString("mongodb.db")

  def paymentNotificationUrl(clientId: String): String = allClientSpecificConfigs.getString(s"$clientId.payment.notification.url")

  def pendingUrl(clientId: String): String = allClientSpecificConfigs.getString(s"$clientId.pending.url")

  def cancelUrl(clientId: String): String = allClientSpecificConfigs.getString(s"$clientId.cancel.url")

  val paymentReportUrl: String = config.getString(s"payment.report.url")

  val messageExpiredPeriod: Period = Period.parse(config.getString("payment.message.expired.period"))
  val messageInProgressRetryPeriod: Period = Period.parse(config.getString("payment.message.inProgress.retry.period"))
  val initialSchedulingDelayInSeconds: Int = config.getInt("payment.message.initial.scheduling.delay.in.seconds")

  def paymentServiceUrl: String = config.getString("worldPay.paymentService.url")

  def paymentProfileNames(clientId: String): Set[String] =  allClientSpecificConfigs.getStringList(s"$clientId.worldPay.profiles").toSet

  def paymentSetProperty(profileName: String, qualifier: String): Set[String] =
    config.getStringList(s"worldPay.$profileName.$qualifier").toSet

  def paymentStringProperty(profileName: String, qualifier: String): String =
    config.getString(s"worldPay.$profileName.$qualifier")

  def countryCodeForRegion: Map[String, String] = {
    val regionConfig = config.getConfig("region.country.code")
    regionConfig.entrySet().foldLeft(Map[String, String]())((acc, configEntry) => acc + (configEntry.getKey -> regionConfig.getString(configEntry.getKey)))
  }
}
