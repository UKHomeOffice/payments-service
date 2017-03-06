package builders

import model.Message
import org.joda.time.DateTime
import reactivemongo.bson.BSONDocument

object MessageBuilder {

  def apply(): Message = Message("url", BSONDocument("data" -> "Data"), DateTime.now, DateTime.now)

}
