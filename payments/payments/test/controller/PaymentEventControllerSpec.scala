package controller

import builders.PaymentBuilder
import conf.Config
import matcher.ResultMatchers
import model.Types.{ExternalReference, InternalReference}
import model._
import org.joda.time.DateTime
import org.mockito.Matchers.{any, eq => mockitoEq}
import org.mockito.Mockito.{never, verify, verifyZeroInteractions, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, WordSpec}
import play.api.test.FakeRequest
import reactivemongo.bson.BSONDocumentWriter
import repository._
import utils.{Eventually, FixedTime, Mocking}
import worldpay.WorldPayClient

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Random

class PaymentEventControllerSpec extends WordSpec with ResultMatchers with Mocking with BeforeAndAfter with ScalaFutures with FixedTime with Eventually {

  trait Scope {
    val paymentEventRepository = mock[PaymentEventRepository]
    val paymentRepository = mock[PaymentRepository]
    val messageRepository = mock[MessageRepository]
    val worldPayClient = mock[WorldPayClient]
    val controller = new PaymentEventControllerClass(paymentRepository, paymentEventRepository, messageRepository, worldPayClient)

    val externalReference: ExternalReference = Random.alphanumeric.take(5).mkString("ex", "", "")
    val internalReference: InternalReference = Random.alphanumeric.take(5).mkString("in", "", "")

    val allValidParameters = List(s"OrderCode=$externalReference", "PaymentId=15390", "PaymentStatus=SETTLED", "PaymentAmount=10010", "PaymentCurrency=CNY", "PaymentMethod=VISA-SSL")
  }

  "receivePaymentEvent" should {

    def withRequestParams(p: String*) = p toList match {
      case Nil => ""
      case list => list mkString("?", "&", "")
    }

    "respond with 200 and queue event for notification if new payment status is different than current" in new Scope {
      private val payment = PaymentBuilder()
      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(Some(payment)))

      val paymentEvent = PaymentEvent(externalReference, payment.internalReference, Some("15390"), "SENT_FOR_AUTHORISATION", 100.1, "CNY", "VISA-SSL", Pending, DateTime.now)


      val receivedEvent = paymentEvent.copy(worldPayStatus = "SETTLED", paymentStatus = Paid)
      when(paymentEventRepository.save(receivedEvent)).thenReturn(Future.successful(receivedEvent))
      when(paymentRepository.changePaymentStatus(receivedEvent.externalReference,receivedEvent.paymentStatus)).thenReturn(Future.successful(Changed))

      val result = controller.receivePaymentEvent(FakeRequest("GET", withRequestParams(allValidParameters: _*)))
      result should haveStatus(200)
      result should haveStringBody("[OK]")

      eventuallySucceed(verify(messageRepository).queueMessageWithPayload(Config.paymentNotificationUrl(payment.clientId), PaymentEventNotification(Some(externalReference), payment.internalReference, receivedEvent.amount, Some(receivedEvent.currency), receivedEvent.paymentStatus)))
    }

    "respond with 200 and queue event for notification if no current status exists" in new Scope {
      private val payment = PaymentBuilder()
      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(Some(payment)))

      val paymentEvent = PaymentEvent(externalReference, payment.internalReference, Some("15390"), "SETTLED", 100.1, "CNY", "VISA-SSL", DateTime.now)

      when(paymentEventRepository.save(paymentEvent)).thenReturn(Future.successful(paymentEvent))

      when(paymentRepository.changePaymentStatus(paymentEvent.externalReference,paymentEvent.paymentStatus)).thenReturn(Future.successful(NotChanged))

      val result = controller.receivePaymentEvent(FakeRequest("GET", withRequestParams(allValidParameters: _*)))
      result should haveStatus(200)
      result should haveStringBody("[OK]")

      eventuallySucceed(verify(messageRepository, never).queueMessageWithPayload(mockitoEq(Config.paymentNotificationUrl(payment.clientId)), any[PaymentEventNotification])(any[BSONDocumentWriter[PaymentEventNotification]], any[Manifest[PaymentEventNotification]]))
    }

    "respond with 200 without queueing an event if new payment status is one the resolving to UNKNOWN" in new Scope {
      val params = List(s"OrderCode=$externalReference", "PaymentId=15390", "PaymentStatus=CHARGED_BACK", "PaymentAmount=10010", "PaymentCurrency=CNY", "PaymentMethod=VISA-SSL")
      private val payment = PaymentBuilder()
      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(Some(payment)))

      val paymentEvent = PaymentEvent(externalReference, payment.internalReference, Some("15390"), "CHARGED_BACK", 100.1, "CNY", "VISA-SSL", DateTime.now)
      when(paymentEventRepository.findLatestStatus(paymentEvent.internalReference, paymentEvent.amount)).thenReturn(Future.successful(Some(paymentEvent.copy(worldPayStatus = "AUTHORISED"))))
      when(paymentEventRepository.save(paymentEvent)).thenReturn(Future.successful(paymentEvent))

      val result = controller.receivePaymentEvent(FakeRequest("GET", withRequestParams(params: _*)))
      result should haveStatus(200)
      result should haveStringBody("[OK]")

      eventuallySucceed(
        verify(messageRepository, never).queueMessageWithPayload(mockitoEq(Config.paymentNotificationUrl(payment.clientId)), any[PaymentEventNotification])(any[BSONDocumentWriter[PaymentEventNotification]], any[Manifest[PaymentEventNotification]])
      )
    }

    "respond with 200 '[OK]' without saving when there is no matching internal reference" in new Scope {
      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(None))

      val result = controller.receivePaymentEvent(FakeRequest("GET", withRequestParams(allValidParameters: _*)))
      result should haveStatus(200)
      result should haveStringBody("[OK]")

      verify(paymentEventRepository, never).save(any[PaymentEvent])
    }

    "respond with 400 when paymentEvent cannot be saved" in new Scope {
      private val payment = PaymentBuilder()
      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(Some(payment)))

      val paymentEvent = PaymentEvent(externalReference, payment.internalReference, Some("15390"), "SETTLED", 100.1, "CNY", "VISA-SSL", DateTime.now)
      when(paymentEventRepository.findLatestStatus(paymentEvent.internalReference, paymentEvent.amount)).thenReturn(Future.successful(None))
      when(paymentEventRepository.save(paymentEvent)).thenReturn(Future.failed(new RuntimeException))

      controller.receivePaymentEvent(FakeRequest("GET", withRequestParams(allValidParameters: _*))) should haveStatus(400)
    }

    "respond with 200 when any of the parameters is missing" in new Scope {
      for {
        paramIdx <- 0 to allValidParameters.size - 1
      } yield {
        val parametersSubset = allValidParameters.filter(_ != allValidParameters(paramIdx))
        controller.receivePaymentEvent(FakeRequest("GET", withRequestParams(parametersSubset: _*))) should haveStatus(200)
      }
      verify(paymentEventRepository, never).save(any[PaymentEvent])
    }
  }

  "receivePaymentSubmissionRedirect" should {
    "respond with 200 and queue event for payment submission if new payment status is different than current" in new Scope {
      private val payment = PaymentBuilder(externalReference = externalReference)
      val paymentEvent = PaymentEvent(externalReference, payment.internalReference, None, "SENT_FOR_AUTHORISATION", payment.total, payment.currency, payment.profile.paymentType, Pending, DateTime.now)
      val receivedEvent = paymentEvent.copy(worldPayStatus = "ERROR", paymentStatus = Refused)
      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(Some(payment)))

      when(paymentRepository.changePaymentStatus(receivedEvent.externalReference,receivedEvent.paymentStatus)).thenReturn(Future.successful(Changed))


      when(paymentEventRepository.save(receivedEvent)).thenReturn(Future.successful(receivedEvent))

      val result = controller.receivePaymentSubmissionRedirect(FakeRequest("GET", s"?externalReference=$externalReference&status=ERROR"))
      result should haveStatus(200)

      eventuallySucceed(verify(messageRepository).queueMessageWithPayload(Config.paymentNotificationUrl(payment.clientId), PaymentEventNotification(Some(externalReference), payment.internalReference, receivedEvent.amount, Some(receivedEvent.currency), receivedEvent.paymentStatus)))
    }

    "respond with 200 and not queue event for payment submission if new payment status is unknown" in new Scope {
      private val payment = PaymentBuilder(externalReference = externalReference)
      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(Some(payment)))

      val paymentEvent = PaymentEvent(externalReference, payment.internalReference, None, "SENT_FOR_AUTHORISATION", payment.total, payment.currency, payment.profile.paymentType, Pending, DateTime.now)
      when(paymentEventRepository.findLatestStatus(paymentEvent.internalReference, paymentEvent.amount)).thenReturn(Future.successful(Some(paymentEvent)))

      val newStatus = "CHARGED_BACK"
      val receivedEvent = paymentEvent.copy(worldPayStatus = newStatus, paymentStatus = Unknown)
      when(paymentEventRepository.save(receivedEvent)).thenReturn(Future.successful(receivedEvent))

      val result = controller.receivePaymentSubmissionRedirect(FakeRequest("GET", s"?externalReference=$externalReference&status=$newStatus"))
      result should haveStatus(200)

      eventuallySucceed(verifyZeroInteractions(messageRepository))
    }

    "respond with 200 and not queue event for payment submission if new payment status is the same as previous" in new Scope {
      private val payment = PaymentBuilder(externalReference = externalReference)
      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(Some(payment)))

      val paymentEvent = PaymentEvent(externalReference, payment.internalReference, None, "AUTHORISED", payment.total, payment.currency, payment.profile.paymentType, Paid, DateTime.now)
      val newStatus = "CAPTURED"
      val receivedEvent = paymentEvent.copy(worldPayStatus = newStatus, paymentStatus = Paid)
      when(paymentEventRepository.save(receivedEvent)).thenReturn(Future.successful(receivedEvent))


      when(paymentRepository.changePaymentStatus(receivedEvent.externalReference,receivedEvent.paymentStatus)).thenReturn(Future.successful(NotChanged))


      val result = controller.receivePaymentSubmissionRedirect(FakeRequest("GET", s"?externalReference=$externalReference&status=$newStatus"))
      result should haveStatus(200)

      eventuallySucceed(verifyZeroInteractions(messageRepository))
    }

    "respond with 400 if new paymentEvent cannot be saved" in new Scope {
      private val payment = PaymentBuilder(externalReference = externalReference)
      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(Some(payment)))

      val paymentEvent = PaymentEvent(externalReference, payment.internalReference, None, "SENT_FOR_AUTHORISATION", payment.total, payment.currency, payment.profile.paymentType, Pending, DateTime.now)
      when(paymentEventRepository.findLatestStatus(paymentEvent.internalReference, paymentEvent.amount)).thenReturn(Future.successful(Some(paymentEvent)))

      val receivedEvent = paymentEvent.copy(worldPayStatus = "ERROR", paymentStatus = Refused)
      when(paymentEventRepository.save(receivedEvent)).thenReturn(Future.failed(new RuntimeException))

      val result = controller.receivePaymentSubmissionRedirect(FakeRequest("GET", s"?externalReference=$externalReference&status=ERROR"))
      result should haveStatus(400)
    }

    "respond with 400 if there is no payment with given externalReference" in new Scope {
      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(None))

      val result = controller.receivePaymentSubmissionRedirect(FakeRequest("GET", s"?externalReference=$externalReference&status=ERROR"))
      result should haveStatus(400)

      eventuallySucceed(verifyZeroInteractions(messageRepository))
    }

    "respond with 200 if there is no 'status' query parameter" in new Scope {
      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(None))

      val result = controller.receivePaymentSubmissionRedirect(FakeRequest("GET", s"?externalReference=$externalReference"))
      result should haveStatus(200)

      eventuallySucceed(verifyZeroInteractions(messageRepository))
    }

    "respond with 400 if there is no 'externalReference' query parameter" in new Scope {
      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(None))

      val result = controller.receivePaymentSubmissionRedirect(FakeRequest("GET", s"?status=someStatus"))
      result should haveStatus(400)

      eventuallySucceed(verifyZeroInteractions(messageRepository))
    }
  }

  "performInquiry" should {

    "do nothing if no events with init status" in new Scope {
      when(paymentEventRepository.findExternalReferencesForPendingPayments(internalReference)).thenReturn(Future.successful(Nil))

      val result = controller.performInquiry(internalReference)(FakeRequest())
      result should haveStatus(200)

      eventuallySucceed(verifyZeroInteractions(messageRepository))
    }

    "save and queue event if fetched event's status different than exiting" in new Scope {
      val payment = PaymentBuilder(externalReference = externalReference, internalReference = internalReference)

      val oldEvent = PaymentEvent(externalReference, internalReference, None, "SENT_FOR_AUTHORISATION", payment.total, payment.currency, payment.profile.paymentType, Pending, DateTime.now)
      when(paymentEventRepository.findExternalReferencesForPendingPayments(internalReference)).thenReturn(Future.successful(List(oldEvent.externalReference)))

      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(Some(payment)))

      val newEvent = PaymentEvent(externalReference, internalReference, None, "AUTHORISED", payment.total, payment.currency, payment.profile.paymentType, Paid, DateTime.now)
      when(worldPayClient.fetchPaymentEvent(payment)).thenReturn(Future.successful(Some(newEvent)))

      when(paymentEventRepository.findLatestStatus(internalReference, payment.total)).thenReturn(Future.successful(Some(oldEvent)))
      when(paymentRepository.changePaymentStatus(newEvent.externalReference,newEvent.paymentStatus)).thenReturn(Future.successful(Changed))
      when(paymentEventRepository.save(newEvent)).thenReturn(Future.successful(newEvent))

      val result = controller.performInquiry(internalReference)(FakeRequest())
      result should haveStatus(200)

      eventuallySucceed(verify(messageRepository).queueMessageWithPayload(Config.paymentNotificationUrl(payment.clientId), PaymentEventNotification(Some(externalReference), internalReference, newEvent.amount, Some(newEvent.currency), newEvent.paymentStatus)))
    }

    "not save and queue event if fetched event's worldpay status is the same as saved one" in new Scope {
      val payment = PaymentBuilder(externalReference = externalReference, internalReference = internalReference)

      val oldEvent = PaymentEvent(externalReference, internalReference, None, "SENT_FOR_AUTHORISATION", payment.total, payment.currency, payment.profile.paymentType, Pending, DateTime.now)
      when(paymentEventRepository.findExternalReferencesForPendingPayments(internalReference)).thenReturn(Future.successful(List(oldEvent.externalReference)))

      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(Some(payment)))

      val newEvent = PaymentEvent(externalReference, internalReference, None, "SENT_FOR_AUTHORISATION", payment.total, payment.currency, payment.profile.paymentType, Pending, DateTime.now)
      when(worldPayClient.fetchPaymentEvent(payment)).thenReturn(Future.successful(Some(newEvent)))

      when(paymentEventRepository.findLatestStatus(internalReference, payment.total)).thenReturn(Future.successful(Some(oldEvent)))

      val result = controller.performInquiry(internalReference)(FakeRequest())
      result should haveStatus(200)

      eventuallySucceed(verify(paymentEventRepository, never).save(any[PaymentEvent]))
      eventuallySucceed(verifyZeroInteractions(messageRepository))
    }

    "save but not queue event if fetched event's worldpay status is is different but the effective paymentStatus is unchanged" in new Scope {
      val payment = PaymentBuilder(externalReference = externalReference, internalReference = internalReference)

      val oldEvent = PaymentEvent(externalReference, internalReference, None, "AUTHORISED", payment.total, payment.currency, payment.profile.paymentType, Pending, DateTime.now)
      when(paymentEventRepository.findExternalReferencesForPendingPayments(internalReference)).thenReturn(Future.successful(List(oldEvent.externalReference)))

      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(Some(payment)))

      val newEvent = PaymentEvent(externalReference, internalReference, None, "CAPTURED", payment.total, payment.currency, payment.profile.paymentType, Pending, DateTime.now)
      when(worldPayClient.fetchPaymentEvent(payment)).thenReturn(Future.successful(Some(newEvent)))

      when(paymentEventRepository.findLatestStatus(internalReference, payment.total)).thenReturn(Future.successful(Some(oldEvent)))
      when(paymentEventRepository.save(newEvent)).thenReturn(Future.successful(newEvent))

      val result = controller.performInquiry(internalReference)(FakeRequest())
      result should haveStatus(200)

      eventuallySucceed(verifyZeroInteractions(messageRepository))
    }

    "do nothing if new event not fetched from worldpay" in new Scope {
      val payment = PaymentBuilder(externalReference = externalReference, internalReference = internalReference)

      val oldEvent = PaymentEvent(externalReference, internalReference, None, "AUTHORISED", payment.total, payment.currency, payment.profile.paymentType, Pending, DateTime.now)
      when(paymentEventRepository.findExternalReferencesForPendingPayments(internalReference)).thenReturn(Future.successful(List(oldEvent.externalReference)))

      when(paymentRepository.findByExternalReference(externalReference)).thenReturn(Future.successful(Some(payment)))

      when(worldPayClient.fetchPaymentEvent(payment)).thenReturn(Future.successful(None))

      val result = controller.performInquiry(internalReference)(FakeRequest())
      result should haveStatus(200)

      eventuallySucceed(verify(paymentEventRepository, never).save(any[PaymentEvent]))
      eventuallySucceed(verifyZeroInteractions(messageRepository))
    }
  }
}
