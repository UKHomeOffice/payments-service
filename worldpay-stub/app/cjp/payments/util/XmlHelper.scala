package cjp.payments.util

import scala.xml._
import javax.xml.parsers.SAXParserFactory

trait XmlHelper {

  def offlineParser: SAXParser = {
    val f = SAXParserFactory.newInstance()
    f.setNamespaceAware(false)
    f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    f.newSAXParser()
  }

  def loadOfflineXML(source: String): Elem = {
    XML.loadXML(scala.xml.Source.fromString(source), offlineParser)
  }

}
