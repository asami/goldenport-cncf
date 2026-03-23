package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class CollectionStateMachinePlannerProviderSpec
  extends AnyWordSpec
  with Matchers {

  private val _cid = EntityCollectionId("test", "sm", "person")

  "CollectionStateMachinePlannerProvider" should {
    "select plan by event + guard + priority deterministically" in {
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[_Person] = _person_persistent

      val provider = new CollectionStateMachinePlannerProvider()
      val trace = scala.collection.mutable.ArrayBuffer.empty[String]
      val lowPriorityRule = TransitionRule[_Person](
        eventName = "update",
        priority = 2,
        declarationOrder = 0,
        guard = Some(_guard(_ => true)),
        plan = _recording_plan("low", trace)
      )
      val highPriorityRule = TransitionRule[_Person](
        eventName = "update",
        priority = 1,
        declarationOrder = 0,
        guard = Some(_guard(_ => true)),
        plan = _recording_plan("high", trace)
      )
      provider.registerUpdate(
        "person",
        new CollectionStateMachinePlanner(Vector(lowPriorityRule, highPriorityRule))
      )

      val person = _Person(EntityId("test", "p2", _cid), "taro", age = 20)
      val event = TransitionEvent("update", Some(person.id))
      val selected = provider.planForUpdate(person, _person_persistent, event)

      selected shouldBe a[Consequence.Success[_]]
      val plan = selected.TAKE.getOrElse(fail("plan should be selected"))
      ExecutionPlanExecutor.execute(plan, person, event) shouldBe Consequence.unit
      trace.toVector shouldBe Vector("high-exit", "high-transition", "high-entry")
    }

    "return None when no rule matches guard" in {
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[_Person] = _person_persistent

      val provider = new CollectionStateMachinePlannerProvider()
      val rule = TransitionRule[_Person](
        eventName = "update",
        priority = 1,
        declarationOrder = 0,
        guard = Some(_guard(_ => false)),
        plan = _recording_plan("blocked", scala.collection.mutable.ArrayBuffer.empty[String])
      )
      provider.registerUpdate(
        "person",
        new CollectionStateMachinePlanner(Vector(rule))
      )

      val person = _Person(EntityId("test", "p3", _cid), "hanako", age = 30)
      val event = TransitionEvent("update", Some(person.id))
      val selected = provider.planForUpdate(person, _person_persistent, event)

      selected shouldBe Consequence.success(None)
    }
  }

  private final case class _Person(
    id: EntityId,
    name: String,
    age: Int
  ) {
    def toRecord(): Record = Record.dataAuto("id" -> id, "name" -> name, "age" -> age)
  }

  private val _person_persistent: EntityPersistent[_Person] = new EntityPersistent[_Person] {
    def id(e: _Person): EntityId = e.id
    def toRecord(e: _Person): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[_Person] = {
      val m = r.asMap
      (m.get("id"), m.get("name"), m.get("age")) match {
        case (Some(id: EntityId), Some(name: String), Some(age: Int)) =>
          Consequence.success(_Person(id, name, age))
        case _ =>
          Consequence.failure("invalid person record")
      }
    }
  }

  private def _guard[S](
    f: S => Boolean
  ): Guard[S, TransitionEvent] = new Guard[S, TransitionEvent] {
    def eval(state: S, event: TransitionEvent): Consequence[Boolean] = {
      val _ = event
      Consequence.success(f(state))
    }
  }

  private def _recording_plan[S](
    label: String,
    trace: scala.collection.mutable.ArrayBuffer[String]
  ): ExecutionPlan[S, TransitionEvent] =
    ExecutionPlan(
      exitActions = Vector(_record[S](s"$label-exit", trace)),
      transitionAction = Some(_record[S](s"$label-transition", trace)),
      entryActions = Vector(_record[S](s"$label-entry", trace))
    )

  private def _record[S](
    label: String,
    trace: scala.collection.mutable.ArrayBuffer[String]
  ): ResolvedAction[S, TransitionEvent] =
    new ResolvedAction[S, TransitionEvent] {
      def run(state: S, event: TransitionEvent): Consequence[Unit] = {
        val _ = (state, event)
        trace += label
        Consequence.unit
      }
    }
}
