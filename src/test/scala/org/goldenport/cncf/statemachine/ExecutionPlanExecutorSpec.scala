package org.goldenport.cncf.statemachine

import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Mar. 19, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionPlanExecutorSpec extends AnyWordSpec with Matchers {
  "ExecutionPlanExecutor" should {
    "run actions in exit -> transition -> entry order" in {
      val trace = ArrayBuffer.empty[String]
      val exit1 = _action[String, String]("exit-1", trace)
      val exit2 = _action[String, String]("exit-2", trace)
      val transition = _action[String, String]("transition", trace)
      val entry1 = _action[String, String]("entry-1", trace)

      val plan = ExecutionPlan[String, String](
        exitActions = Vector(exit1, exit2),
        transitionAction = Some(transition),
        entryActions = Vector(entry1)
      )

      val result = ExecutionPlanExecutor.execute(plan, "state", "event")

      result shouldBe Consequence.unit
      trace.toVector shouldBe Vector("exit-1", "exit-2", "transition", "entry-1")
    }

    "stop on action failure and propagate failure" in {
      val trace = ArrayBuffer.empty[String]
      val exit1 = _action[String, String]("exit-1", trace)
      val transition = new ResolvedAction[String, String] {
        def run(state: String, event: String): Consequence[Unit] = {
          val _ = (state, event)
          trace += "transition"
          Consequence.stateConflict("transition failed")
        }
      }
      val entry1 = _action[String, String]("entry-1", trace)
      val plan = ExecutionPlan[String, String](
        exitActions = Vector(exit1),
        transitionAction = Some(transition),
        entryActions = Vector(entry1)
      )

      val result = ExecutionPlanExecutor.execute(plan, "state", "event")

      result shouldBe a[Consequence.Failure[_]]
      trace.toVector shouldBe Vector("exit-1", "transition")
    }

    "stop immediately when exit action fails" in {
      val trace = ArrayBuffer.empty[String]
      val exit1 = new ResolvedAction[String, String] {
        def run(state: String, event: String): Consequence[Unit] = {
          val _ = (state, event)
          trace += "exit-1"
          Consequence.stateConflict("exit failed")
        }
      }
      val transition = _action[String, String]("transition", trace)
      val entry1 = _action[String, String]("entry-1", trace)
      val plan = ExecutionPlan[String, String](
        exitActions = Vector(exit1),
        transitionAction = Some(transition),
        entryActions = Vector(entry1)
      )

      val result = ExecutionPlanExecutor.execute(plan, "state", "event")

      result shouldBe a[Consequence.Failure[_]]
      trace.toVector shouldBe Vector("exit-1")
    }

    "propagate entry failure after exit and transition" in {
      val trace = ArrayBuffer.empty[String]
      val exit1 = _action[String, String]("exit-1", trace)
      val transition = _action[String, String]("transition", trace)
      val entry1 = new ResolvedAction[String, String] {
        def run(state: String, event: String): Consequence[Unit] = {
          val _ = (state, event)
          trace += "entry-1"
          Consequence.stateConflict("entry failed")
        }
      }
      val plan = ExecutionPlan[String, String](
        exitActions = Vector(exit1),
        transitionAction = Some(transition),
        entryActions = Vector(entry1)
      )

      val result = ExecutionPlanExecutor.execute(plan, "state", "event")

      result shouldBe a[Consequence.Failure[_]]
      trace.toVector shouldBe Vector("exit-1", "transition", "entry-1")
    }
  }

  private def _action[S, E](
    label: String,
    trace: ArrayBuffer[String]
  ): ResolvedAction[S, E] =
    new ResolvedAction[S, E] {
      def run(state: S, event: E): Consequence[Unit] = {
        val _ = (state, event)
        trace += label
        Consequence.unit
      }
    }
}
