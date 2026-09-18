package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.datastore.DataStore
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}
import org.goldenport.cncf.event.{CommittedTransition, EventEngine, EventLane, EventStore, TransitionLifecycleEvent, TransitionLifecycleKind}
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Mar. 24, 2026
 *  version Apr. 14, 2026
 *  version Sep. 17, 2026
 * @version Sep. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class PlannedTransitionValidationHookSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _cid = EntityCollectionId("test", "sm", "person")

  "PlannedTransitionValidationHook" should {
    "call planner and execute plan before update" in {
      Given("an execution context, persistent person, and successful transition plan")
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[_Person] = _person_persistent

      val provider = new _ProviderWithPlan
      val hook = new PlannedTransitionValidationHook(provider)
      val entity = _Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "hook_1", _cid, entropy = "hook_1"), "taro")
      When("the planned validation hook processes the update")
      val result = hook.beforeUpdate(entity, summon[EntityPersistent[_Person]])

      Then("the update succeeds and lifecycle events are emitted")
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
      summon[ExecutionContext].runtime.unitOfWork.pendingEvents.collect {
        case e: CommittedTransition => e
      } shouldBe empty
    }

    "schedule an explicitly bound committed transition without staging it before commit" in {
      Given("an execution context, persistent person, and successful explicitly bound transition plan")
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[_Person] = _person_persistent
      val entity = _Person(
        org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "hook_bound", _cid, entropy = "hook_bound"),
        "sachiko"
      )
      val hook = new PlannedTransitionValidationHook(new ProviderWithBoundPlan(_binding))

      When("the planned validation hook processes the successful update before UnitOfWork commit")
      val result = hook.beforeUpdate(entity, summon[EntityPersistent[_Person]])

      Then("only the existing lifecycle events are staged and no committed-transition event is pre-commit")
      result shouldBe Consequence.unit
      val staged = summon[ExecutionContext].runtime.unitOfWork.pendingEvents
      staged.collect { case e: TransitionLifecycleEvent => e.kind } shouldBe Vector(
        TransitionLifecycleKind.BeforeTransition,
        TransitionLifecycleKind.AfterTransition
      )
      staged.collect { case e: CommittedTransition => e } shouldBe empty
    }

    "attach the selected rule binding to a structural execution plan without rewriting the hook operation" in {
      Given("a structural rule with an explicit CML binding and an update whose state changes from Draft to Approved")
      val entity = _Person(
        org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "planner_bound", _cid, entropy = "planner_bound"),
        "ichiro"
      )
      val rule = TransitionRule[_Person](
        eventName = "approve",
        priority = 0,
        declarationOrder = 0,
        guard = None,
        plan = ExecutionPlan(Vector.empty, None, Vector.empty),
        stateFieldName = Some("status"),
        fromState = Some("Draft"),
        toState = Some("Approved"),
        binding = Some(_binding)
      )
      val planner = new CollectionStateMachinePlanner(Vector(rule))
      val operation = TransitionEvent(
        "update",
        Some(entity.id),
        Some(Record.dataAuto("status" -> "Draft")),
        Some(Record.dataAuto("status" -> "Approved"))
      )

      When("the planner selects the rule while evaluating its semantic transition event")
      val selected = planner.plan(entity, operation).toOption.flatten

      Then("the selected plan carries the typed rule binding while the hook operation remains the original update")
      selected.flatMap(_.selectedTransitionBinding) shouldBe Some(_binding)
      operation.name shouldBe "update"
    }

    "emit transition-failed on action failure" in {
      Given("an execution context, persistent person, and failing transition plan")
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[_Person] = _person_persistent

      val provider = new _ProviderWithFailingPlan
      val hook = new PlannedTransitionValidationHook(provider)
      val entity = _Person(org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "hook_2", _cid, entropy = "hook_2"), "hanako")
      When("the planned validation hook processes the update")
      val result = hook.beforeUpdate(entity, summon[EntityPersistent[_Person]])

      Then("the update fails without staging a raw failure diagnostic for transactional publication")
      result shouldBe a[Consequence.Failure[_]]
      val lifecycle = summon[ExecutionContext].runtime.unitOfWork.pendingEvents.collect {
        case e: TransitionLifecycleEvent => e
      }
      lifecycle.map(_.kind) shouldBe Vector(
        TransitionLifecycleKind.BeforeTransition
      )
      summon[ExecutionContext].runtime.unitOfWork.pendingEvents.collect {
        case e: CommittedTransition => e
      } shouldBe empty
    }

    "persist one safe transition-failed record after hook failure rolls back" in {
      Given("a failing planned transition hook bound to a runtime UnitOfWork with an in-memory EventStore")
      val store = EventStore.inMemory
      val base = ExecutionContext.create()
      lazy val context: ExecutionContext = ExecutionContext.withRuntimeContext(base, runtime)
      lazy val runtime: RuntimeContext = new RuntimeContext(
        core = base.runtime.core,
        unitofworksupplier = () => new UnitOfWork(
          context,
          EventEngine.noop(DataStore.noop(), eventstore = store)
        ),
        unitofworkinterpreterfn = base.runtime.unitOfWorkInterpreter,
        commitaction = uow => {
          val _ = uow.commit()
          ()
        },
        abortaction = uow => {
          val _ = uow.rollback()
          ()
        },
        disposeaction = _ => (),
        token = "planned-transition-validation-hook-failure-spec"
      )
      given ExecutionContext = context
      given EntityPersistent[_Person] = _person_persistent
      val hook = new PlannedTransitionValidationHook(new _ProviderWithFailingPlan)
      val entity = _Person(
        org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
          "test",
          "hook_failure_rollback",
          _cid,
          entropy = "hook_failure_rollback"
        ),
        "hanako"
      )

      When("the hook fails and its runtime UnitOfWork rolls back")
      val result = hook.beforeUpdate(entity, summon[EntityPersistent[_Person]])
      val rollbackresult = summon[ExecutionContext].runtime.unitOfWork.rollback()

      Then("exactly one non-transactional taxonomy-only failure is stored without a committed-success transition")
      result shouldBe a[Consequence.Failure[_]]
      rollbackresult.isSuccess shouldBe true
      val failures = store.query(EventStore.Query(kind = Some("transition-failed"))).toOption.getOrElse(Vector.empty)
      failures should have size 1
      failures.head.lane shouldBe EventLane.NonTransactional
      failures.head.payload.get("transition.failure.taxonomy").map(_.toString).getOrElse("") should not be empty
      failures.head.payload.values.mkString(" ") should not include "transition failed in spec"
      store.query(EventStore.Query(kind = Some("committed-transition"))).toOption.getOrElse(Vector.empty) shouldBe empty
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

  private val _binding: CmlTransitionBinding = {
    val machine = CmlStateMachineIdentity("person-lifecycle")
    val source = CmlStateMachineStateIdentity(
      machine,
      CmlStateMachineStatePath(Vector("Draft"))
    )
    val target = CmlStateMachineStateIdentity(
      machine,
      CmlStateMachineStatePath(Vector("Approved"))
    )
    CmlTransitionBinding(
      componentId = ComponentId("org.example.Person"),
      entityType = _cid,
      machine = machine,
      version = CmlStateMachineVersion(1),
      transition = CmlStateMachineTransitionIdentity(machine, 0),
      source = source,
      target = CmlStateMachineTransitionTarget.State(target),
      trigger = CmlStateMachineTriggerIdentity(machine, "approve")
    )
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
          Consequence.stateConflict("transition failed in spec")
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

  private final class ProviderWithBoundPlan(
    binding: CmlTransitionBinding
  ) extends StateMachinePlannerProvider {
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
      Consequence.success(
        Some(
          ExecutionPlan(
            exitActions = Vector.empty,
            transitionAction = Some(new ResolvedAction[T, TransitionEvent] {
              def run(state: T, transitionevent: TransitionEvent): Consequence[Unit] = {
                val _ = (state, transitionevent)
                Consequence.unit
              }
            }),
            entryActions = Vector.empty,
            selectedTransitionBinding = Some(binding)
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
