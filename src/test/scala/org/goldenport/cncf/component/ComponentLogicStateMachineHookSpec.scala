package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.EntityPersistent
import org.goldenport.cncf.entity.EntityPersistentUpdate
import org.goldenport.cncf.statemachine.{ExecutionPlan, PlannedTransitionValidationHook, ResolvedAction, StateMachinePlannerProvider, TransitionEvent}
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Mar. 24, 2026
 *  version Apr. 14, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentLogicStateMachineHookSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _cid = EntityCollectionId("test", "sm", "person")

  "ComponentLogic runtime" should {
    "inject planned transition hook from component planner provider" in {
      Given("a component with a planner provider and execution context")
      val component = new Component() {}
      val provider = new _CountingProvider
      val _ = component.withStateMachinePlannerProvider(provider)
      val logic = ComponentLogic(component)
      val ctx = logic.executionContext()

      given ExecutionContext = ctx
      given EntityPersistent[_Person] = _person_persistent
      val hook = ctx.runtime.transitionValidationHook
      When("the planned transition hook validates a person update")
      val result = hook.beforeUpdate(_Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "p1", _cid, entropy = "p1"), "taro"), _person_persistent)

      Then("the hook accepts the update and invokes the provider")
      ctx.runtime.transitionValidationHook shouldBe a[PlannedTransitionValidationHook]
      result shouldBe Consequence.unit
      provider.called shouldBe true
    }
  }

  private final case class _Person(id: EntityId, name: String) {
    def toRecord(): Record = Record.dataAuto("id" -> id, "name" -> name)
  }

  private val _person_persistent: EntityPersistent[_Person] = new EntityPersistent[_Person] {
    def id(e: _Person): EntityId = e.id
    def toRecord(e: _Person): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[_Person] = {
      val m = r.asMap
      (m.get("id"), m.get("name")) match {
        case (Some(id: EntityId), Some(name: String)) =>
          Consequence.success(_Person(id, name))
        case _ =>
          Consequence.argumentInvalid("invalid person record")
      }
    }
  }

  private final class _CountingProvider extends StateMachinePlannerProvider {
    private var _called = false

    def called: Boolean = _called

    def planForSave[T](
      entity: T,
      tc: EntityPersistent[T],
      event: TransitionEvent
    )(using ExecutionContext): Consequence[Option[ExecutionPlan[T, TransitionEvent]]] = {
      val _ = (entity, tc, event)
      Consequence.success(None)
    }

    def planForUpdate[T](
      entity: T,
      tc: EntityPersistent[T],
      event: TransitionEvent
    )(using ExecutionContext): Consequence[Option[ExecutionPlan[T, TransitionEvent]]] = {
      val _ = (entity, tc, event)
      _called = true
      val action = new ResolvedAction[T, TransitionEvent] {
        def run(state: T, ev: TransitionEvent): Consequence[Unit] = {
          val _ = (state, ev)
          Consequence.unit
        }
      }
      Consequence.success(
        Some(
          ExecutionPlan(
            exitActions = Vector(action),
            transitionAction = None,
            entryActions = Vector.empty
          )
        )
      )
    }

    def planForUpdateById[P](
      id: EntityId,
      patch: P,
      tc: EntityPersistentUpdate[P],
      event: TransitionEvent
    )(using ExecutionContext): Consequence[Option[ExecutionPlan[(EntityId, P), TransitionEvent]]] = {
      val _ = (id, patch, tc, event)
      Consequence.success(None)
    }
  }
}
