package repository

import builders.PaymentBuilder
import model.Paid
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import utils.Eventually

import scala.language.postfixOps

class PaymentRepositorySpec extends WordSpec with Matchers with ScalaFutures with BeforeAndAfter with Eventually {

  val repo = new PaymentRepository(DbConnection.db)

  before {
    eventually {
      repo.remove(BSONDocument.empty)
    }
  }

  "save" should {
    "save the given payment" in {
      val internalReference = BSONObjectID.generate.stringify
      val payment = PaymentBuilder(internalReference = internalReference)

      eventually(repo.save(payment))

      repo.findOne(BSONDocument("internalReference" -> internalReference)).futureValue.get shouldBe payment
    }
  }

  "findInternalReference" should {
    "return internal reference for payment with given external reference" in {
      val internalReference = BSONObjectID.generate.stringify
      val payment = PaymentBuilder(internalReference = internalReference)
      eventually(repo.insert(payment))

      repo.findInternalReference(payment.externalReference).futureValue.get shouldBe (internalReference)
    }
  }

  "findByExternalReference" should {
    "return payment with given external reference" in {
      val payment = PaymentBuilder()
      eventually(repo.insert(payment))

      repo.findByExternalReference(payment.externalReference).futureValue.get shouldBe (payment)
    }
  }

  "find by internalReference and total" should {
    "return payment" in {
      val internalReference = BSONObjectID.generate.stringify

      val payment1 = PaymentBuilder(internalReference = internalReference).copy(total = BigDecimal(10))
      eventually(repo.insert(payment1))

      val payment2 = PaymentBuilder(internalReference = internalReference).copy(total = BigDecimal(20))
      eventually(repo.insert(payment2))

      repo.find(internalReference, BigDecimal(20)).futureValue.get shouldBe (payment2)
    }
  }


  "changedPaymentStatus" should {
    "change the status to the new" in {
      val internalReference = BSONObjectID.generate.stringify
      val payment = PaymentBuilder(internalReference = internalReference)
      eventually(repo.insert(payment))

      eventually(repo.changePaymentStatus(payment.externalReference, Paid)) shouldBe Changed

    }

    "return None if the current status is same as the new" in {
      val internalReference = BSONObjectID.generate.stringify
      val payment = PaymentBuilder(internalReference = internalReference).copy(status = Paid)
      eventually(repo.insert(payment))

      eventually(repo.changePaymentStatus(payment.externalReference, Paid)) shouldBe NotChanged

    }
  }

}

