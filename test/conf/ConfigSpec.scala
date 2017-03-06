package conf

import org.scalatest.{FunSuite, Matchers, WordSpec}

class ConfigSpec extends WordSpec with Matchers {

  "country code to region" should {
    "be populated" in {
      Config.countryCodeForRegion.size should be(2)
      Config.countryCodeForRegion should contain("NGA" -> "NG")
      Config.countryCodeForRegion should contain("BEN" -> "NG")
    }
  }
}
