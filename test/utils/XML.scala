package utils

import scala.xml._
import javax.xml.parsers.SAXParserFactory

trait Xml {

  def offlineParser: SAXParser = {
    val f = SAXParserFactory.newInstance()
    f.setNamespaceAware(false)
    f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    f.newSAXParser()
  }

  def loadOfflineXML(source: String) = {
    Utility.trim(XML.loadXML(scala.xml.Source.fromString(source), offlineParser))
  }
}
