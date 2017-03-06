package worldpay

import conf.Config
import model.Types.{PaymentType, Region}

case class WorldPayProfile(profileName: String,
                           username: String,
                           password: String,
                           macSecret: String,
                           paymentServiceUrl: String,
                           installationId: String,
                           regions: Set[String],
                           paymentTypes: Set[String]) {

  def countryCode: Option[String] = if (isAPM) Some("CN") else None

  def isAPM: Boolean = regions.contains("APM")
}

case class WorldPayConfiguration(paymentServiceUrl: String, profiles: Set[WorldPayProfile]) {
  require(allRegionAndPaymentTypeCombinationsToBeUnique, "Non unique region and payment combinations")

  def paymentTypes(region: Region): Set[String] =
    profiles.filter(_.regions.contains(region)).flatMap(_.paymentTypes)

  def username(username: String): Option[WorldPayProfile] = profiles.find(_.username == username)

  def profileNames: Set[String] = profiles.map(_.profileName)

  def profile(profileName: String): Option[WorldPayProfile] = profiles.find(_.profileName == profileName)

  def profile(paymentType: PaymentType, label: Region): Either[InvalidPaymentProfileException, WorldPayProfile] =
    profiles.find(p => p.regions.contains(label) && p.paymentTypes.contains(paymentType))
    .map(Right(_))
    .getOrElse(Left(InvalidPaymentProfileException(paymentType, label)))

  private def allRegionAndPaymentTypeCombinationsToBeUnique = {
    val all = profiles.toList.flatMap { p =>
      for {
        i1 <- p.regions
        i2 <- p.paymentTypes
      } yield (i1, i2)
    }
    all.size == all.toSet.size
  }

}

object WorldPayConfiguration {

  val worldPayContentType = "text/plain"

  def apply(clientId : String): WorldPayConfiguration = {

    val instances = Config.paymentProfileNames(clientId) map {
      profileName =>
        WorldPayProfile(profileName,
          Config.paymentStringProperty(profileName, "username"),
          Config.paymentStringProperty(profileName, "password"),
          Config.paymentStringProperty(profileName, "macSecret"),
          Config.paymentServiceUrl,
          Config.paymentStringProperty(profileName, "installationId"),
          Config.paymentSetProperty(profileName, "regions"),
          Config.paymentSetProperty(profileName, "paymentTypes")
        )
    }

    new WorldPayConfiguration(Config.paymentServiceUrl, instances)
  }

  def allPaymentMethods: Set[String] =
    Config.allClientIds.foldLeft(Set.empty[String]) { (allMethods, clientId) =>
      val profileNames = Config.paymentProfileNames(clientId)
      profileNames.foldLeft(allMethods){(all, profileName) =>
        all ++ Config.paymentSetProperty(profileName, "paymentTypes")
      }
    }
}

case class InvalidPaymentProfileException(paymentType: PaymentType, label: Region)
  extends RuntimeException(s"No WorldPay configuration for '$paymentType' payment type and '$label' label") {

}
