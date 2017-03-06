package utils

import org.mockito.Mockito.{mock => mockitoMock}
import org.mockito.internal.util.MockUtil
import org.scalatest.{BeforeAndAfterEach, WordSpecLike}

import scala.reflect.Manifest

trait Mocking extends WordSpecLike with BeforeAndAfterEach {

  var mockList: List[Any] = List()
  val mockUtil = new MockUtil

  def reset(): Unit = {
    mockList foreach mockUtil.resetMock
  }

  def mock[T <: AnyRef](implicit manifest: Manifest[T]): T  = {
    val m: T = mockitoMock(manifest.runtimeClass.asInstanceOf[Class[T]])
    mockList ::= m
    m
  }

  def argEq[T](t: T): T = org.mockito.Matchers.eq(t)

  override def beforeEach() = {
    reset()
  }

}
