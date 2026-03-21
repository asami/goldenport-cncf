package org.goldenport.cncf.entity.aggregate

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class AggregateViewSemanticBoundarySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "AV-01 semantic boundary" should {
    "keep aggregate deterministic for same command/state input" in {
      case class State(value: Int)
      case class Command(delta: Int)
      case class Event(kind: String, amount: Int)

      trait AggregateContract[S, C, E] {
        def handle(command: C, state: S): Consequence[(S, Vector[E])]
      }

      val aggregate = new AggregateContract[State, Command, Event] {
        def handle(command: Command, state: State): Consequence[(State, Vector[Event])] = {
          val next = State(state.value + command.delta)
          val events = Vector(Event("delta-applied", command.delta))
          Consequence.success((next, events))
        }
      }

      Given("the same command and current state")
      val command = Command(10)
      val state = State(20)

      When("aggregate handle is called twice")
      val first = aggregate.handle(command, state)
      val second = aggregate.handle(command, state)

      Then("state and event outputs are equal")
      first shouldBe second
    }

    "keep view rebuildable from event stream without command-side mutation" in {
      case class Event(kind: String, amount: Int)
      case class View(total: Int)

      trait ViewContract[V, E] {
        def project(current: V, event: E): Consequence[V]
      }

      val view = new ViewContract[View, Event] {
        def project(current: View, event: Event): Consequence[View] =
          event.kind match {
            case "delta-applied" => Consequence.success(View(current.total + event.amount))
            case _ => Consequence.success(current)
          }
      }

      def rebuild(events: Vector[Event]): Consequence[View] =
        events.foldLeft(Consequence.success(View(0))) { (acc, e) =>
          acc.flatMap(v => view.project(v, e))
        }

      Given("an event sequence")
      val events = Vector(Event("delta-applied", 10), Event("delta-applied", 3))

      When("view is rebuilt from the same sequence")
      val first = rebuild(events)
      val second = rebuild(events)

      Then("projection result is deterministic and reconstructable")
      first shouldBe second
      first shouldBe Consequence.success(View(13))
    }
  }
}

