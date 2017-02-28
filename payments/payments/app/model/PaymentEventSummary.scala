package model

import model.EventStatusSummary.rejectedStatuses
import model.PaymentMethodSummary.allEventStatuses

case class PaymentEventSummary(total: Int, totalsPerStatus: Set[EventStatusSummary], methodSummaries: Set[PaymentMethodSummary]) {

  def withAggregateForRejected: PaymentEventSummary = {
    val shortMethodSummaries = methodSummaries.map {
      methodSummary =>
        methodSummary.withAggregateForRejected
    }
    PaymentEventSummary(shortMethodSummaries)
  }

}

object PaymentEventSummary {

  def apply(methodSummaries: Set[PaymentMethodSummary], allPaymentMethods: Set[String]): PaymentEventSummary = {
    val allMethodSummaries = addSummariesForMissingPaymentMethods(methodSummaries, allPaymentMethods)
    val totals = findTotals(allMethodSummaries)
    PaymentEventSummary(totals.totalCount, totals.totals, allMethodSummaries)
  }

  private def addSummariesForMissingPaymentMethods(methodSummaries: Set[PaymentMethodSummary], allPaymentMethods: Set[String]) =
    allPaymentMethods.map { method =>
      methodSummaries.find(_.method == method) match {
        case Some(methodSummary) => methodSummary.addMissingStatuses()
        case None => PaymentMethodSummary(method).addMissingStatuses()
      }
    }

  def apply(methodSummaries: Set[PaymentMethodSummary]): PaymentEventSummary = {
    val totals = findTotals(methodSummaries)
    PaymentEventSummary(totals.totalCount, totals.totals, methodSummaries)
  }

  private case class Totals(totalCount: Int, totals: Set[EventStatusSummary]) {

    def addToEventTotals(event: EventStatusSummary): Totals = {
      val newTotals = totals.find(_.status == event.status) match {
        case Some(statusTotal) =>
          totals - statusTotal + statusTotal.addCount(event.count)
        case _ => totals + event
      }
      Totals(totalCount, newTotals)
    }

    def addToTotalCount(count: Int) =
      Totals(totalCount + count, totals)
  }

  private def findTotals(methodSummaries: Set[PaymentMethodSummary]): Totals = {
    methodSummaries.foldLeft(Totals(0, Set.empty)) {
      (totals, methodSummary) =>
        val updatedTotals = totals.addToTotalCount(methodSummary.count)
        methodSummary.events.foldLeft(updatedTotals) {
          (tots, event) =>
            tots.addToEventTotals(event)
        }
    }
  }
}

case class PaymentMethodSummary(method: String, count: Int, events: Set[EventStatusSummary]) {

  def addMissingStatuses(): PaymentMethodSummary = {
    val allStatuses = allEventStatuses.map {
      status => events.find(_.status == status) match {
        case Some(statusSummary) => statusSummary
        case None => EventStatusSummary(status, 0)
      }
    }
    PaymentMethodSummary(method, count, allStatuses)
  }

  def withAggregateForRejected: PaymentMethodSummary = {
    val nonRejectedPlusRejectedEvent = events.foldLeft((Set.empty[EventStatusSummary], EventStatusSummary("REJECTED"))) {
      case ((newEvents, rejectedEvent), event) =>
        if (event.hasRejectedStatus)
          (newEvents, rejectedEvent.addCount(event.count))
        else
          (newEvents + event, rejectedEvent)
    }
    PaymentMethodSummary(method, count, nonRejectedPlusRejectedEvent._1 + nonRejectedPlusRejectedEvent._2)
  }
}

object PaymentMethodSummary {

  val allEventStatuses = Set(
    "PENDING",
    "PAID",
    "REFUSED",
    "FAILURE",
    "CANCELLED",
    "REFUNDED",
    "OTHER"
  )

  def apply(method: String): PaymentMethodSummary =
    PaymentMethodSummary(method, 0, Set.empty)
}

case class EventStatusSummary(status: String, count: Int) {

  def addCount(countToAdd: Int) =
    EventStatusSummary(status, count + countToAdd)

  def hasRejectedStatus: Boolean =
    rejectedStatuses.contains(status)
}

object EventStatusSummary {
  val rejectedStatuses = Set(
    "REFUSED",
    "FAILURE",
    "CANCELLED",
    "OTHER"
  )
  
  def apply(status: String): EventStatusSummary = 
    EventStatusSummary(status, 0)
}