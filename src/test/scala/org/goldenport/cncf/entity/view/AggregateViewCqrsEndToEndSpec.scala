package org.goldenport.cncf.entity.view

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.event.{EventId, EventLane, EventRecord}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class AggregateViewCqrsEndToEndSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  final case class _State(total: Int)
  final case class _Command(amount: Int)
  final case class _Event(amount: Int)
  final case class _View(id: EntityId, total: Int)

  "CQRS end-to-end" should {
    "flow command -> event -> projection -> query without command/read boundary violation" in {
      Given("an aggregate command handler and view projector")
      val collectionid = EntityCollectionId("tokyo", "sales", "person_view")
      val viewid = EntityId("tokyo", "sales", collectionid)
      val aggregate = new AggregateCommandHandler[_Command, _State, _Event] {
        def handle(command: _Command, state: _State): Consequence[AggregateCommandResult[_State, _Event]] =
          if (command.amount <= 0)
            Consequence.failure("amount must be positive")
          else
            Consequence.success(
              AggregateCommandResult(
                newState = _State(state.total + command.amount),
                events = Vector(_Event(command.amount))
              )
            )
      }
      val projector = new EventProjector[_View] {
        def project(current: _View, event: EventRecord): Consequence[_View] =
          Consequence.success(current.copy(total = current.total + _amount(event)))
      }
      val synchronizer = new AggregateViewSynchronizer[_View](
        initialView = _View(viewid, 0),
        projector = projector
      )

      val browser = Browser.from[
        _View
      ](
        collection = new org.goldenport.cncf.entity.runtime.Collection[_View] {
          def resolve(id: EntityId): Consequence[_View] =
            if (id == viewid) Consequence.success(synchronizer.currentView)
            else Consequence.failure(s"view not found: $id")
        },
        queryfn = _ => Consequence.success(Vector(synchronizer.currentView))
      )

      When("a command is handled and synchronization emits projected view")
      val updated = synchronizer.synchronizeCommand(
        command = _Command(7),
        state = _State(3),
        aggregate = aggregate,
        toEventRecord = e => _event_record("sales.updated", 1L, e.amount)
      )

      Then("aggregate update and view query are both available on their own boundaries")
      updated.map(_._1.total) shouldBe Consequence.success(10)
      updated.map(_._2.view.total) shouldBe Consequence.success(7)

      val queried = browser.query(Query("total > 0"))
      queried.map(_.map(_.total)) shouldBe Consequence.success(Vector(7))

      And("read side query does not call aggregate handler directly")
      val secondQuery = browser.query(Query("total > 0"))
      secondQuery.map(_.map(_.total)) shouldBe Consequence.success(Vector(7))
    }
  }

  private def _event_record(name: String, sequence: Long, amount: Int): EventRecord =
    EventRecord(
      id = EventId.generate(),
      name = name,
      kind = "domain-event",
      payload = Map("amount" -> amount),
      attributes = Map.empty,
      createdAt = Instant.ofEpochMilli(sequence),
      persistent = true,
      status = EventRecord.Status.Stored,
      lane = EventLane.Transactional,
      sequence = sequence
    )

  private def _amount(record: EventRecord): Int =
    record.payload.get("amount") match {
      case Some(i: Int) => i
      case Some(l: Long) => l.toInt
      case Some(s: String) => s.toInt
      case _ => 0
    }
}
