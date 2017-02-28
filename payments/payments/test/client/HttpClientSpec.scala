package client

import org.mockito.Matchers.{any, anyInt, eq => mockitoEq}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import play.api.http.{ContentTypeOf, Writeable}
import play.api.libs.ws.{WSAuthScheme, WSRequestHolder, WSResponse}
import utils.Mocking

import scala.concurrent.Future

class HttpClientSpec extends WordSpec with Matchers with Mocking with ScalaFutures {

  "post" should {
    "build a client, do a post and return received body as string if status 2xx" in {
      val requestHolder = mock[WSRequestHolder]
      val clientFor = (url: String) => requestHolder
      val client = new HttpClient(clientFor)

      val authDetails = AuthDetails("user", "pass", WSAuthScheme.BASIC)
      val body = "body"

      when(requestHolder.withRequestTimeout(anyInt)).thenReturn(requestHolder)
      when(requestHolder.withHeaders("Content-Type" -> "application/json")).thenReturn(requestHolder)
      when(requestHolder.withAuth(authDetails.username, authDetails.password, authDetails.authScheme)).thenReturn(requestHolder)

      val response = mock[WSResponse]
      val responseBody = "json"
      when(response.body).thenReturn(responseBody)
      when(response.status).thenReturn(200)
      when(requestHolder.post(mockitoEq(body))(any[Writeable[Any]], any[ContentTypeOf[Any]])).thenReturn(Future.successful(response))

      val result = client.post("url", body, Some(authDetails))

      result.futureValue should be(responseBody)
    }

    "build a client, do a post and throw exception if status not 2xx" in {
      val requestHolder = mock[WSRequestHolder]
      val clientFor = (url: String) => requestHolder
      val client = new HttpClient(clientFor)

      val authDetails = AuthDetails("user", "pass", WSAuthScheme.BASIC)
      val body = "body"

      when(requestHolder.withRequestTimeout(anyInt)).thenReturn(requestHolder)
      when(requestHolder.withHeaders("Content-Type" -> "application/json")).thenReturn(requestHolder)
      when(requestHolder.withAuth(authDetails.username, authDetails.password, authDetails.authScheme)).thenReturn(requestHolder)

      val response = mock[WSResponse]
      val responseBody = "json"
      when(response.body).thenReturn(responseBody)
      when(response.status).thenReturn(400)
      when(requestHolder.post(mockitoEq(body))(any[Writeable[Any]], any[ContentTypeOf[Any]])).thenReturn(Future.successful(response))

      val result = client.post("url", body, Some(authDetails))

      a[RuntimeException] shouldBe thrownBy {
        result.futureValue
      }
    }

    "build a client, do a post and throw exception if client cannot be reached" in {
      val requestHolder = mock[WSRequestHolder]
      val clientFor = (url: String) => requestHolder
      val client = new HttpClient(clientFor)

      val authDetails = AuthDetails("user", "pass", WSAuthScheme.BASIC)
      val body = "body"

      when(requestHolder.withRequestTimeout(anyInt)).thenReturn(requestHolder)
      when(requestHolder.withHeaders("Content-Type" -> "application/json")).thenReturn(requestHolder)
      when(requestHolder.withAuth(authDetails.username, authDetails.password, authDetails.authScheme)).thenReturn(requestHolder)

      val response = mock[WSResponse]
      val responseBody = "json"
      when(response.body).thenReturn(responseBody)
      when(response.status).thenReturn(400)
      when(requestHolder.post(mockitoEq(body))(any[Writeable[Any]], any[ContentTypeOf[Any]])).thenReturn(Future.failed(new RuntimeException()))

      val result = client.post("url", body, Some(authDetails))

      a[RuntimeException] shouldBe thrownBy {
        result.futureValue
      }
    }

    "throw exception if client cannot be built" in {
      val clientFor = (url: String) => throw new RuntimeException()
      val client = new HttpClient(clientFor)

      val body = "body"

      val result = client.post("url", body, None)

      a[RuntimeException] shouldBe thrownBy {
        result.futureValue
      }
    }
  }
}
