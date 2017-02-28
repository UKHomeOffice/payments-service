package cjp.payments.controllers

import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.{Timer, TimerTask}

import cjp.payments.util.{SessionParameter, WorldPayMacHelper, XmlHelper}
import com.typesafe.config.{Config, ConfigFactory}
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.ws.WS
import play.api.mvc.{Action, AnyContent, Controller, Results}

import scala.collection.convert.decorateAsScala._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Random, Try}
import scala.xml.dtd.{DocType, PublicID}
import scala.xml.{Elem, MinimizeMode, Node, XML => sXML}

object PaymentsStub extends Controller with XmlHelper {

  case class StubbedPayment(amount: String,
                            currencyCode: String,
                            merchantCode: String,
                            paymentMethodMask: String,
                            successUrl: Option[String] = None,
                            failureUrl: Option[String] = None,
                            pendingUrl: Option[String] = None,
                            cancelUrl: Option[String] = None) {

    def isAlternativePaymentMethod: Boolean = Set("CHINAUNIONPAY-SSL", "ALIPAY-SSL").contains(paymentMethodMask)
  }

  val logger: org.slf4j.Logger = Logger.logger

  object WorldPayStubId extends SessionParameter("worldPayStubId")

  val cache = new ConcurrentHashMap[String, StubbedPayment]()

  val worldPayMacHelper = new WorldPayMacHelper()

  val config: Config = ConfigFactory.load()

  val selfBaseUrl: String = config.getString("self.base.url")

  val paymentNotificationEndpoint: String = config.getString("payment.notification.endpoint")
  val customerNotificationEndpoint: String = config.getString("customer.notification.endpoint")

  val macSecrets = Map(
    "MERCHANT_CODE_1" -> "macSecret1",
    "MERCHANT_CODE_2" -> "macSecret2",
    "MERCHANT_CODE_3" -> "macSecret3"
  )


  val docType = DocType(
    name = "paymentService",
    extID = PublicID("-//WorldPay/DTD WorldPay PaymentService v1//EN", "http://dtd.worldpay.com/paymentService_v1.dtd"),
    intSubset = Nil
  )

  def cardTypeHtml(stubbedData: StubbedPayment): Elem = <html>
    <body>
      <h1>Pay
        &pound;{(BigDecimal(stubbedData.amount) / BigDecimal(100)).formatted("%.2f")}
      </h1>
      <a href={routes.PaymentsStub.cardDetails().url} id="op-DPChoose-VISA^SSL" name="op-DPChoose-VISA^SSL">VISA</a>

      <div>
        <a href={stubbedData.cancelUrl.get} name="op-DPCancel" id="op-DPCancel" alt="Cancel">Cancel</a>
      </div>
    </body>
  </html>

  def cardDetailsHtml(stubbedData: StubbedPayment): Elem = <html>
    <body>
      <form accept-charset="UTF-8" method="GET" action={routes.PaymentsStub.cardErrors().url}>
        <label for="cardNoInput">Card number</label>
        <input type="text" name="cardNoInput" id="cardNoInput" size="19" maxlength="19" value=" "/>
        <label for="Card expiry date (MM/YYYY)"/>
        <select name="cardExp.month" size="1">
          <option value="*"></option>
          <option value="1">01</option>
          <option value="2">02</option>
          <option value="3">03</option>
          <option value="4">04</option>
          <option value="5">05</option>
          <option value="6">06</option>
          <option value="7">07</option>
          <option value="8">08</option>
          <option value="9">09</option>
          <option value="10">10</option>
          <option value="11">11</option>
          <option value="12">12</option>
        </select>
        <strong>/</strong>
        <select name="cardExp.year" size="1">
          <option value="*"></option>
          <option value="2013">2013</option>
          <option value="2014">2014</option>
          <option value="2015">2015</option>
          <option value="2016">2016</option>
          <option value="2017">2017</option>
          <option value="2018">2018</option>
          <option value="2019">2019</option>
          <option value="2020">2020</option>
          <option value="2021">2021</option>
          <option value="2022">2022</option>
          <option value="2023">2023</option>
          <option value="2024">2024</option>
          <option value="2025">2025</option>
          <option value="2026">2026</option>
          <option value="2027">2027</option>
          <option value="2028">2028</option>
          <option value="2029">2029</option>
          <option value="2030">2030</option>
          <option value="2031">2031</option>
          <option value="2032">2032</option>
          <option value="2033">2033</option>
        </select>
        <label for="name">Name card holder</label>
        <input type="text" name="name" id="name" size="30" maxlength="30" value=" "/>
        <label for="cardCVV">Card Verification Code</label>
        <input type="text" name="cardCVV" id="cardCVV" size="3" maxlength="3" value=" "/>
        <input id="op-PMMakePayment" class="button" type="SUBMIT" name="Send" value="Submit"/>

      </form>
      <a href={stubbedData.cancelUrl.get} name="op-DPCancel" id="op-DPCancel" alt="Cancel">Cancel</a>
    </body>
  </html>

  def startTransaction = Action { implicit request =>
    val rawBody = request.body.asText.get
    logger.info("WorldPay stub received payment request " + rawBody)

    val orderXml = loadOfflineXML(rawBody)
    val orderCode = (orderXml \\ "order" \ "@orderCode").text
    val paymentAmount = (orderXml \\ "amount" \ "@value").text
    val currencyCode = (orderXml \\ "amount" \ "@currencyCode").text
    val merchantCode = (orderXml \\ "paymentService" \ "@merchantCode").text
    val paymentMethodMask = (orderXml \\ "paymentMethodMask" \ "include" \ "@code").text
    val address1 = (orderXml \\ "address1").text.toLowerCase

    val xml = address1 match {
      case "error" =>
        val code = Try((orderXml \\ "address2").text.trim.toInt).getOrElse(0)
        val message = (orderXml \\ "address3").text
        errorResponse(code, message)
      case _ => successResponse(orderCode, merchantCode)
    }

    if (!orderCode.isEmpty)
      cache.put(orderCode, StubbedPayment(paymentAmount, currencyCode, merchantCode, paymentMethodMask))

    val stringWriter = new StringWriter()

    scala.xml.XML.write(stringWriter, xml, "UTF-8", xmlDecl = true, docType, MinimizeMode.Default)

    val response = stringWriter.toString
    logger.info(s"Responding to start payment with: $response")
    Ok(response)
  }

  def successResponse(orderCode: String, merchantCode: String): Elem = {
    val xml = <paymentService merchantCode={merchantCode} version="1.4">
      <reply>
        <orderStatus orderCode={orderCode}>
          <reference id="1234567">
            {selfBaseUrl + routes.PaymentsStub.cardType().url + "?orderCode=" + orderCode}
          </reference>
        </orderStatus>
      </reply>
    </paymentService>
    xml
  }

  def cardType = Action { implicit request =>
    val successUrl = request.getQueryString("successURL")
    val cancelUrl = request.getQueryString("cancelURL")
    val pendingUrl = request.getQueryString("pendingURL")
    val failureUrl = request.getQueryString("failureURL")
    val orderCode = request.getQueryString("orderCode").get

    val stubbedData = cache.get(orderCode).copy(
      successUrl = successUrl,
      pendingUrl = pendingUrl,
      failureUrl = failureUrl,
      cancelUrl = cancelUrl
    )

    cache.put(orderCode, stubbedData)

    val html = request.getQueryString("preferredPaymentMethod") match {
      case None => cardTypeHtml(stubbedData)
      case Some(_) => cardDetailsHtml(stubbedData)
    }

    Ok(toHtml(html)).withSession(WorldPayStubId.add(orderCode)).as("text/html")
  }

  def cardDetails = Action { implicit request =>
    val orderCode = WorldPayStubId.get
    val stubbedData = cache.get(orderCode)

    Ok(toHtml(cardDetailsHtml(stubbedData))).as("text/html")
  }

  def cardErrors = Action { implicit request =>
    val html = <html>
      <body>
        <form accept-charset="UTF-8" method="POST" action={routes.PaymentsStub.completePayment().url}>

          <select name="PaRes">
            <option value="AUTHORISED" selected="">Authorised</option>
            <option value="REFUSED">Refused</option>
            <option value="PENDING">Pending</option>
            <option value="PENDING_FOR_CUP">Pending for China Union Pay</option>
            <option value="PENDING_WITH_ERROR">Pending with error</option>
          </select>
          <label for="OrderCode">Order code</label>
          <input type="text" name="OrderCode" readOnly="true" value={WorldPayStubId.get}/>
          <input name="continue" id="continue" value="Submit" type="submit"/>
        </form>
      </body>
    </html>

    Ok(toHtml(html)).as("text/html")
  }

  def completePayment = Action { implicit request =>

    def sendAsyncNotification(notificationParameters: String, delayInSec: Int): Unit = {
      new Timer().schedule(new TimerTask {
        override def run(): Unit = {
          sendNotificationTo(paymentNotificationEndpoint, notificationParameters)
        }
      }, delayInSec * 1000)

      logger.info(s"Scheduling notification. Parameters: $notificationParameters")
    }

    val orderCode = WorldPayStubId.get

    val form = Form(
      single(
        "PaRes" -> text
      )
    )

    val paymentStatus = form.bindFromRequest().get

    logger.info(s"Payment submitted with status : $paymentStatus")

    val stubbedData: StubbedPayment = cache.get(orderCode)
    val paymentCurrency = stubbedData.currencyCode
    val orderKey = s"ACCOUNT_CODE^MERCHANT_CODE^$orderCode"
    val paymentMethod = stubbedData.paymentMethodMask
    val mac = worldPayMacHelper.createMac(orderKey, stubbedData.amount, paymentCurrency, paymentStatus, macSecrets(stubbedData.merchantCode))

    val parameters = s"orderKey=$orderKey&paymentAmount=${stubbedData.amount}&paymentStatus=$paymentStatus&paymentCurrency=$paymentCurrency&mac=$mac"

    // added for new payment service
    val worldPayStatus = paymentStatus match {
      case "PENDING" | "PENDING_FOR_CUP" | "PENDING_WITH_ERROR" => "SENT_FOR_AUTHORISATION"
      case x => x
    }
    val notificationParameters = s"OrderCode=$orderCode&PaymentAmount=${stubbedData.amount}&PaymentStatus=$worldPayStatus&PaymentCurrency=$paymentCurrency&PaymentId=${Random.alphanumeric.take(5).mkString}&PaymentMethod=$paymentMethod"
    sendAsyncNotification(notificationParameters, 2)

    val redirect = paymentStatus match {
      case "AUTHORISED" => Redirect(s"${stubbedData.successUrl.get}&$parameters")
      case "REFUSED" => Redirect(s"${stubbedData.failureUrl.get}&$parameters")
      case "PENDING" => Redirect(stubbedData.pendingUrl.get + s"&orderKey=$orderKey&status=OPEN")
      case "PENDING_FOR_CUP" => Redirect(stubbedData.successUrl.get)
      case "PENDING_WITH_ERROR" => Redirect(stubbedData.pendingUrl.get + s"?orderKey=$orderKey&status=ERROR")
      case _ => Redirect(stubbedData.cancelUrl.get)
    }

    logger.info(s"After payment redirecting to : ${redirect.header.headers("Location")}")

    redirect
  }


  def toHtml(xml: Node): String = {
    val docType = DocType("html", PublicID("-//W3C//DTD HTML 4.01 Transitional//EN", null), Nil)

    val stringWriter = new StringWriter()

    sXML.write(stringWriter, xml, "UTF-8", xmlDecl = false, docType, MinimizeMode.Default)

    stringWriter.toString
  }

  def sendNotification = Action { implicit request =>
    val html =
      <html>
        <body>
          Pending payments:
          <ul>
            {for ((orderCode, payment) <- cache.asScala) yield
            <li>
              <form accept-charset="UTF-8" method="GET" action="sendNotificationAction">
                <label for="OrderCode">OrderCode</label>
                <input type="text" name="OrderCode" id="orderCode" readOnly="true" value={orderCode}/>
                <label for="PaymentCurrency">Currency</label>
                <input type="text" name="PaymentCurrency" id="paymentCurrency" readOnly="true" value={payment.currencyCode}/>
                <label for="PaymentAmount">Amount</label>
                <input type="text" name="PaymentAmount" id="paymentAmount" readOnly="true" value={payment.amount}/>
                <label for="PaymentMethod">Method</label>
                <input type="text" name="PaymentMethod" readOnly="true" value={payment.paymentMethodMask}/>
                <label for="PaymentStatus">Status</label>
                <select name="PaymentStatus" id={"PaymentStatus" + orderCode}>
                  <option value="AUTHORISED" selected=" ">Authorised</option>
                  <option value="REFUSED">Refused</option>
                  <option value="EXPIRED">Expired</option>
                </select>
                <input type="hidden" name="PaymentId" value="22188746"/>
                <input name="sendNotification" id={"sendNotification" + orderCode} value="Send notification" type="submit"/>
              </form>
            </li>}
          </ul>
        </body>
      </html>

    val htmlToReturn = toHtml(html)
    logger.info(s"Responding with notification selection page: $htmlToReturn")
    Ok(htmlToReturn).as("text/html")
  }

  def sendNotificationAction: Action[AnyContent] = Action.async { implicit request =>
    sendNotificationTo(customerNotificationEndpoint, request.rawQueryString).map {
      case 200 => Ok("Payment notification sent")
      case status => throw new RuntimeException(s"Unexpected response $status from GET to $customerNotificationEndpoint")
    }
    sendNotificationTo(paymentNotificationEndpoint, request.rawQueryString).map {
      case 200 => Ok("Payment notification sent")
      case status => throw new RuntimeException(s"Unexpected response $status from GET to $paymentNotificationEndpoint")
    }
    Future.successful(Results.Ok)
  }

  def sendNotificationTo(baseUrl: String, parameters: String): Future[Int] = {
    val endpoint = baseUrl + "?" + parameters
    logger.info(s"Sending notification to : $endpoint")
    for {
      result <- WS.url(endpoint).get()
    } yield {
      result.status
    }
  }

  private def errorResponse(code: Int, message: String) =
    <paymentService merchantCode="ATOSM12" version="1.4">
      <reply>
        <error code={code.toString}>
          {message}
        </error>
      </reply>
    </paymentService>
}