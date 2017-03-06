package management

import java.io.{File, FileInputStream, FileNotFoundException, InputStream}
import java.util

import org.yaml.snakeyaml.Yaml
import play.api.Play
import play.api.Play.current

import scala.util.{Success, Try}

case class BuildInfo(buildNumber: Int,
                     lastCommit: String)

object BuildInfo {

  lazy val fromApplicationConfig: Option[BuildInfo] = {
    Play.configuration.getString("buildInfoFile").flatMap { buildInfoFile =>
      Try(BuildInfo(new File(buildInfoFile))) match {
        case Success(info) => Some(info)
        case _ => None
      }
    }
  }

  def apply(buildInfoFile: File): BuildInfo = {
    try {
      apply(new FileInputStream(buildInfoFile))
    }
    catch {
      case e: FileNotFoundException => sys.error(e.getMessage)
    }
  }

  def apply(buildInfo: InputStream): BuildInfo = {
    try {
      val BuildInfoMap = new Yaml().load(buildInfo).asInstanceOf[util.Map[String, Any]]
      BuildInfo(
        buildNumber = BuildInfoMap.get("buildNumber").asInstanceOf[Int],
        lastCommit = BuildInfoMap.get("lastCommit").asInstanceOf[String]
      )
    }
    catch {
      case _: Exception => sys.error("failed to parse build info file")
    }
  }

}
