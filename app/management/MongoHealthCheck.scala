package management

import reactivemongo.api.DB
import reactivemongo.bson.BSONDocument
import reactivemongo.core.commands.RawCommand
import repository.DbConnection

import logging.MdcExecutionContext.Implicit.defaultContext
import scala.concurrent.Future

class MongoHealthCheck(db: () => DB) {

  def ping(): Future[Boolean] =
    DbConnection.db().command(RawCommand(BSONDocument("ping" -> 1))) map {
      res =>
        res.getAs[Double]("ok").getOrElse(0) == 1.0
    }
}
