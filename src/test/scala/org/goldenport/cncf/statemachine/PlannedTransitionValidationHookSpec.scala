package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}
import org.goldenport.cncf.event.{TransitionLifecycleEvent, TransitionLifecycleKind}
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class PlannedTransitionValidationHookSpec extends AnyWordSpec with Matchers {
  private val _cid = EntityCollectionId("test", "sm", "person")

  "PlannedTransitionValidationHook" should {
    "call planner and execute plan before update" in {
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[_Person] = _person_persistent

      val provider = new _ProviderWithPlan
      val hook = new PlannedTransitionValidationHook(provider)
      val entity = _Person(EntityId("test", "hook_1", _cid), "taro")
      val result = hook.beforeUpdate(entity, summon[EntityPersistent[_Person]])

      result shouldBe Consequence.unit
      provider.called shouldBe true
      provider.executionTrace shouldBe Vector("exit", "transition", "entry")
      val lifecycle = summon[ExecutionContext].runtime.unitOfWork.pendingEvents.collect {
        case e: TransitionLifecycleEvent => e
      }
      lifecycle.map(_.kind) shouldBe Vector(
        TransitionLifecycleKind.BeforeTransition,
        TransitionLifecycleKind.AfterTransition
      )
      lifecycle.foreach { e =>
        e.name shouldBe "transition.lifecycle"
        e.id.major should not be empty
        e.id.minor should not be empty
        e.occurredAt should not be null
        e.correlation.traceId should not be empty
        e.correlation.executionContextId.major should not be empty
        e.correlation.executionContextId.minor should not be empty
        e.transition.collection shouldBe Some("person")
        e.transition.event shouldBe "update"
        e.transition.targetId shouldBe Some(entity.id)
        e.failure shouldBe None
      }
    }

    "emit transition-failed on action failure" in {
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[_Person] = _person_persistent

      val provider = new _ProviderWithFailingPlan
      val hook = new PlannedTransitionValidationHook(provider)
      val entity = _Person(EntityId("test", "hook_2", _cid), "hanako")
      val result = hook.beforeUpdate(entity, summon[EntityPersistent[_Person]])

      result shouldBe a[Consequence.Failure[_]]
      val lifecycle = summon[ExecutionContext].runtime.unitOfWork.pendingEvents.collect {
        case e: TransitionLifecycleEvent => e
      }
      lifecycle.map(_.kind) shouldBe Vector(
        TransitionLifecycleKind.BeforeTransition,
        TransitionLifecycleKind.TransitionFailed
      )
      val failed = lifecycle.last
      failed.failure.isDefined shouldBe true
      failed.failure.map(_.taxonomy).getOrElse("") should not be empty
      failed.failure.flatMap(_.message).getOrElse("") should include("transition")
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
          Consequence.failure("invalid person record")
      }
    }
  }

  private final class _ProviderWithPlan extends StateMachinePlannerProvider {
    private var _called = false
    private var _execution_trace = Vector.empty[String]

    def called: Boolean = _called
    def executionTrace: Vector[String] = _execution_trace

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
      val exit = _record[T]("exit")
      val transition = _record[T]("transition")
      val entry = _record[T]("entry")
      Consequence.success(
        Some(
          ExecutionPlan(
            exitActions = Vector(exit),
            transitionAction = Some(transition),
            entryActions = Vector(entry)
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

    private def _record[S](label: String): ResolvedAction[S, TransitionEvent] =
      new ResolvedAction[S, TransitionEvent] {
        def run(state: S, event: TransitionEvent): Consequence[Unit] = {
          val _ = (state, event)
          _execution_trace = _execution_trace :+ label
          Consequence.unit
        }
      }
  }

  private final class _ProviderWithFailingPlan extends StateMachinePlannerProvider {
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
      val failaction = new ResolvedAction[T, TransitionEvent] {
        def run(state: T, event: TransitionEvent): Consequence[Unit] = {
          val _ = (state, event)
          Consequence.failure("transition failed in spec")
        }
      }
      Consequence.success(
        Some(
          ExecutionPlan(
            exitActions = Vector.empty,
            transitionAction = Some(failaction),
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
