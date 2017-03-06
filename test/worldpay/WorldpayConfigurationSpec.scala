package worldpay

import conf.Config
import org.scalatest.{Matchers, WordSpec}

class WorldpayConfigurationSpec extends WordSpec with Matchers {

  "WorldpayConfiguration" should {
    "raise exception if region and paymentType combinations are ambiguous" in {
      a[IllegalArgumentException] shouldBe thrownBy(WorldPayConfiguration("", Set(
        WorldPayProfile("", "", "", "", "", "", Set("uk", "china"), Set("visa", "master")),
        WorldPayProfile("", "", "", "", "", "", Set("uk", "poland"), Set("visa", "ssl"))))
      )
    }
  }

  "Parsing the config file" should {
    "can find the payment service url" in {
      Config.paymentServiceUrl shouldBe "http://localhost:9020/start"
    }

    "can find all the client ids" in {

      Config.allClientIds shouldBe Set("DCJ" , "ADS")
    }

    "can find all the defined payment profiles for a client" in {
      Config.paymentProfileNames("DCJ") shouldBe Set("uk", "china", "china-apm", "western-europe")
      Config.paymentProfileNames("ADS") shouldBe Set("china", "china-apm", "america")
    }

    "can find the client specific payment notification url" in {
      Config.paymentNotificationUrl("DCJ") shouldBe "http://localhost:9010/worldPay/customer/notification"
      Config.paymentNotificationUrl("ADS") shouldBe "http://localhost:9010/worldPay/ads/notification"
    }


    "load configurations" in {
      val westernEuropeConfig: WorldPayProfile = WorldPayConfiguration("DCJ").profile("western-europe").get
      westernEuropeConfig.username shouldBe "TEST"
      westernEuropeConfig.password shouldBe "Pass"
      westernEuropeConfig.macSecret shouldBe "secret"
      westernEuropeConfig.regions shouldBe Set("western-europe", "poland")
      westernEuropeConfig.paymentTypes shouldBe Set("VISA-SSL", "ECMC-SSL", "AMEX-SSL", "MAESTRO-SSL")
      westernEuropeConfig.installationId shouldBe "123"
      val ukConfig: WorldPayProfile = WorldPayConfiguration("DCJ").profile("uk").get
      ukConfig.password shouldBe "password_removed"

    }

    "profileNames that are not found should return None" in {
      WorldPayConfiguration("DCJ").profile("anything") shouldBe None
    }

    "return profile for given label and paymentType" in {
      WorldPayConfiguration("DCJ").profile("ECMC-SSL", "poland") should be(Right(WorldPayConfiguration("DCJ").profile("western-europe").get))
    }

    "return InvalidPaymentProfileException for unknown label and paymentType" in {
      WorldPayConfiguration("DCJ").profile("paymentTypeX", "labelX") should be(Left(InvalidPaymentProfileException("paymentTypeX", "labelX")))
    }

    "return profile for given user name" in {
      WorldPayConfiguration("DCJ").username("TEST") should be(WorldPayConfiguration("DCJ").profile("western-europe"))
    }
  }

  "paymentTypes" should {
    "return paymentTypes from all profiles with given region" in {
      WorldPayConfiguration("DCJ").paymentTypes("china") should be(
        Seq("china", "china-apm").flatMap(region =>
          WorldPayConfiguration("DCJ").profile(region).map(_.paymentTypes)
        ).flatten.toSet
      )
    }
  }

  "allPaymentMethods" should {
    "return payment methods from all profiles" in {
      WorldPayConfiguration.allPaymentMethods should contain only("VISA-SSL", "ECMC-SSL", "AMEX-SSL", "MAESTRO-SSL", "VISA-SSL-america", "CHINAUNIONPAY-SSL", "ALIPAY-SSL")
    }
  }
}