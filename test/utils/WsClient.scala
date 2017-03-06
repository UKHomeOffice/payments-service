package utils

import java.io.ByteArrayInputStream

import com.ning.http.client.{FluentCaseInsensitiveStringsMap, Response => AHCResponse}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => mockitoEq, _}
import org.mockito.Mockito._
import play.api.http.{ContentTypeOf, Writeable}
import play.api.libs.ws.ning.NingWSResponse
import play.api.libs.ws.{WSClient, WSRequestHolder}

import logging.MdcExecutionContext.Implicit.defaultContext
import scala.concurrent.Future

trait WsClient extends Mocking {

  val wsClient = mock[WSClient]
  val mockRequestHolder = mock[WSRequestHolder]
  val mockResponse = mock[AHCResponse]
  private val bodyCaptor = ArgumentCaptor.forClass(classOf[String])

  def setUpResponse(url: String, statusCode: Int, responseBody: String = "") = {
    setUpRequestHolder(url)
    setUpResponseHolder(statusCode, responseBody)
  }
  
  def setUpException(url: String) = {
    setUpRequestHolder(url)
    when(mockRequestHolder.post(any[String])(any[Writeable[String]], any[ContentTypeOf[String]])).thenReturn(Future(NingWSResponse(mockResponse)))
    when(mockResponse.getStatusCode).thenThrow(new RuntimeException("Boom"))
  }

  def requestBody() = {
    verify(mockRequestHolder, timeout(500)).post(bodyCaptor.capture())(any[Writeable[String]], any[ContentTypeOf[String]])
    bodyCaptor.getValue
  }

  private def setUpRequestHolder(url: String) = {
    when(mockRequestHolder.withHeaders(any[(String, String)])).thenReturn(mockRequestHolder)
    when(mockRequestHolder.withQueryString(any[(String, String)])).thenReturn(mockRequestHolder)
    when(mockRequestHolder.headers).thenReturn(Map.empty[String, Seq[String]])
    when(wsClient.url(mockitoEq(url))).thenReturn(mockRequestHolder)
  }

  private def setUpResponseHolder(statusCode: Int, responseBody: String) = {
    when(mockResponse.getStatusCode).thenReturn(statusCode)
    when(mockResponse.getResponseBodyAsBytes).thenReturn(responseBody.getBytes)
    when(mockResponse.getResponseBodyAsStream).thenReturn(new ByteArrayInputStream(responseBody.getBytes))
    when(mockResponse.getResponseBody(any[String])).thenReturn(responseBody)
    when(mockResponse.getHeaders).thenReturn(new FluentCaseInsensitiveStringsMap())
    when(mockRequestHolder.post(any[String])(any[Writeable[String]], any[ContentTypeOf[String]])).thenReturn(Future(NingWSResponse(mockResponse)))
  }
}
