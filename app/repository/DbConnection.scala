package repository

import conf.Config
import reactivemongo.core.nodeset.Authenticate

import scala.collection.JavaConversions._

object DbConnection {

  import reactivemongo.api._

import logging.MdcExecutionContext.Implicit.defaultContext

  private val driver = new MongoDriver
  private val connection = driver.connection(
    nodes = asScalaBuffer(Config.mongoHost),
    authentications = Seq(Authenticate(Config.mongoDb, Config.mongoUsername, Config.mongoPassword)),
    nbChannelsPerNode = 1,
    name = None)

  def db(): DefaultDB = connection(Config.mongoDb)


}
