package repository

import logging.Logging
import model.Types.{ExternalReference, InternalReference}
import model._
import org.joda.time.DateTime
import reactivemongo.api.DB
import reactivemongo.bson._
import reactivemongo.core.commands._
import reactivemongo.extensions.dao.BsonDao
import reactivemongo.extensions.dao.Handlers._

import scala.concurrent.Future

class PaymentEventRepository(db: () => DB) extends BsonDao[PaymentEvent, BSONObjectID](db, "paymentEvents") with Logging {

  def save(paymentEvent: PaymentEvent): Future[PaymentEvent] = {
    super.save(paymentEvent).map {
      case LastError(true, _, _, _, _, _, _) => paymentEvent
    } recover {
      case e: LastError =>
        throw new RuntimeException(e.errMsg.getOrElse(s"Could not record paymentEvent for externalReference: ${paymentEvent.externalReference}"))
    }
  }

  def findLatestStatus(internalReference: InternalReference, amount: BigDecimal): Future[Option[PaymentEvent]] =
    findAll(BSONDocument("internalReference" -> internalReference, "amount" -> amount.toDouble, "paymentStatus" -> BSONDocument("$ne" -> Unknown.toString)), BSONDocument("timestamp" -> -1)).map(_.headOption)

  def findExternalReferencesForPendingPayments(internalReference: InternalReference): Future[List[ExternalReference]] =
    findAll(BSONDocument("internalReference" -> internalReference)).map {
      events =>
        val eventsGroupedByExternalRef = events.groupBy(_.externalReference)
        val groupsWithNonPendingOrUnknownEventsOnly = eventsGroupedByExternalRef.mapValues(_.filter { event =>  !Seq(Pending, Unknown).contains(event.paymentStatus)})
        val externalRefsOfPendingOnly = groupsWithNonPendingOrUnknownEventsOnly.filter { case (_, es) => es.isEmpty}.keySet
        val eventsWithPendingStatus = externalRefsOfPendingOnly.map(exRef => events.find(pe => pe.externalReference == exRef && pe.paymentStatus == Pending).get).toList
        eventsWithPendingStatus.sortBy(_.timestamp.getMillis).map(_.externalReference)
    }

  def updateTimestamp(event: PaymentEvent): Future[Option[PaymentEvent]] =
    findAndUpdate(
      query = BSONDocument("internalReference" -> event.internalReference, "externalReference" -> event.externalReference, "worldPayStatus" -> event.worldPayStatus),
      update = BSONDocument("$set" -> BSONDocument("timestamp" -> event.timestamp)),
      sort = BSONDocument("timestamp" -> -1)
    )

  private case class EventsSummaryAggregate(_id: String, count: Int, events: Seq[String])

  private object EventsSummaryAggregate {
    implicit val dbReadWriteHandler: BSONDocumentReader[EventsSummaryAggregate] with BSONDocumentWriter[EventsSummaryAggregate] with BSONHandler[BSONDocument, EventsSummaryAggregate] = Macros.handler[EventsSummaryAggregate]
  }

  def generateDailyReportBreakDownByPaymentMethod: Future[Set[PaymentMethodSummary]] = {

    def checkStatus(condition: BSONValue, `then`: String, `else`: String): BSONDocument =
      BSONDocument("$cond" -> BSONArray(condition, `then`, `else`))

    def ifStatusOneOf(status1: String, status2: String, status3: String) =
      BSONDocument("$or" -> BSONArray(worldPayStatusEqual(status1), worldPayStatusEqual(status2), worldPayStatusEqual(status3)))

    def worldPayStatusEqual(status: String) =
      BSONDocument("$eq" -> BSONArray("$worldPayStatus", status))

    val aggregate = Aggregate(
      collectionName = collection.name,
      pipeline  = Seq(
        Project(
          "timestamp" -> BSONString("$timestamp"),
          "method" -> BSONString("$method"),
          "externalReference" -> BSONString("$externalReference"),
          "worldPayStatus" -> checkStatus(
            ifStatusOneOf("OPEN", "SENT_FOR_AUTHORISATION", "SHOPPER_REDIRECTED"),
            `then` = "PENDING",
            `else` = "$worldPayStatus"
          )
        ),
        Match(BSONDocument("worldPayStatus" -> BSONDocument("$nin" -> BSONArray("INIT", "CAPTURED", "SETTLED")), "timestamp" -> BSONDocument("$gte" -> DateTime.now.withTimeAtStartOfDay()))),
        GroupMulti("method" -> "method", "externalReference" -> "externalReference", "status" -> "worldPayStatus")(),
        GroupField("_id.method")("count" -> SumValue(1), "events" -> Push("_id.status"))
      )
    )

    collection.db.command(aggregate).map {
      case summariesStream if summariesStream.isEmpty => Set.empty
      case summariesStream =>
        def statusMapper(key: String) =
          key match {
            case "PENDING" | "CANCELLED" | "REFUNDED" | "REFUSED" => key
            case "AUTHORISED" => "PAID"
            case "ERROR" | "EXPIRED" | "FAILURE" => "FAILURE"
            case _ => "OTHER"
          }

        def countIndividualStatuses(aggregate: EventsSummaryAggregate) =
          aggregate.events.groupBy(statusMapper).map { case (status, eventStatuses) => EventStatusSummary(status, eventStatuses.size) }.toSet

        summariesStream.foldLeft(Set.empty[PaymentMethodSummary]) { (paymentMethodSummaries, aggregateAsJson) =>
          val aggregate = BSON.readDocument[EventsSummaryAggregate](aggregateAsJson)
          paymentMethodSummaries + PaymentMethodSummary(aggregate._id, aggregate.count, countIndividualStatuses(aggregate))
        }
    }
  }
}
