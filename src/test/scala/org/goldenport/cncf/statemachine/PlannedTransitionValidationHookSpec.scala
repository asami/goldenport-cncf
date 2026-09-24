package org.goldenport.cncf.statemachine

import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.context.{ExecutionContext, ExecutionInvocationIdentity, RuntimeContext}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.datastore.DataStore
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}
import org.goldenport.cncf.event.{CommittedTransition, EventEngine, EventLane, EventStore, TransitionLifecycleEvent, TransitionLifecycleFailureOutcome, TransitionLifecycleFailureStage, TransitionLifecycleKind}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkOp}
import org.goldenport.cncf.workflow.{ActionExecution, CompletionContract, ContextBundle, ContextContract, ContextReference, ContextSnapshot, Continuation, ContinuationIdentity, EvidenceContract, StateMachineOperationFailure, StateMachineOperationIdentity, StateMachineOperationResult, StateMachineRequiredOperation, StateMachineRequiredOperationIdentity, StateMachineRequiredOperationMetadata, StateMachineResultTypeReference, StateMachineRevision, StateMachineRunIdentity}
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
        plan = ExecutionPlan(Vector.empty, Vector.empty, Vector.empty),
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

    "commit an explicit binding with the actual selector and fail closed before executing a mismatched direct plan" in {
      Given("selected explicit binding plans and independently controlled matching and mismatched invocations")
      given EntityPersistent[_Person] = _person_persistent
      val entity = _Person(
        org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "hook_operation", _cid, entropy = "hook_operation"),
        "ichiro"
      )
      val selector = "org.example.Person.entity.updateSalesOrder"
      val matchingstore = EventStore.inMemory
      val mismatchedstore = EventStore.inMemory
      val matchingcontext = _operation_context(matchingstore, selector)
      val mismatchedcontext = _operation_context(mismatchedstore, "org.example.Person.entity.updateSalesOrderRecord")
      val provider = new ProviderWithBoundPlan(_explicit_operation_binding)
      val hook = new PlannedTransitionValidationHook(provider)

      When("the hook completes a matching plan and rejects a mismatched direct provider plan")
      val matchingresult = hook.beforeUpdate(entity, _person_persistent)(using matchingcontext)
      val matchingcommit = matchingcontext.runtime.unitOfWork.commit()
      val mismatchedresult = hook.beforeUpdate(entity, _person_persistent)(using mismatchedcontext)
      val mismatchedrollback = mismatchedcontext.runtime.unitOfWork.rollback()

      Then("the committed envelope uses the actual selector while the mismatch fails before action execution")
      matchingresult shouldBe Consequence.unit
      matchingcommit.isSuccess shouldBe true
      val matchingrecords = matchingstore.query(EventStore.Query(kind = Some("committed-transition"))).toOption.getOrElse(Vector.empty)
      matchingrecords should have size 1
      matchingrecords.head.payload.get("operation.id") shouldBe Some(selector)
      mismatchedresult shouldBe a[Consequence.Failure[_]]
      mismatchedrollback.isSuccess shouldBe true
      provider.executionTrace shouldBe Vector("transition")
      mismatchedcontext.runtime.unitOfWork.pendingEvents.collect {
        case e: TransitionLifecycleEvent => e
      } shouldBe empty
      val mismatchfailures = mismatchedstore.query(EventStore.Query(kind = Some("transition-failed"))).toOption.getOrElse(Vector.empty)
      mismatchfailures should have size 1
      mismatchfailures.head.payload.get("transition.failure.stage") shouldBe Some(TransitionLifecycleFailureStage.Planning.value)
      mismatchedstore.query(EventStore.Query(kind = Some("committed-transition"))).toOption.getOrElse(Vector.empty) shouldBe empty
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

    "reject suspension at the update hook until durable continuation persistence is available" in {
      Given("a planned update whose Required SPI action returns a typed suspension")
      given ExecutionContext = ExecutionContext.create()
      given EntityPersistent[_Person] = _person_persistent
      val hook = new PlannedTransitionValidationHook(new ProviderWithOutcomePlan(_suspended_program))
      val entity = _Person(
        org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "hook_suspended", _cid, entropy = "hook_suspended"),
        "hanako"
      )

      When("the update hook interprets the plan without an atomic persistence boundary")
      val result = hook.beforeUpdate(entity, summon[EntityPersistent[_Person]])

      Then("the update fails before a successful transition can be staged")
      result shouldBe a[Consequence.Failure[_]]
      result.display should include ("durable continuation persistence")
      summon[ExecutionContext].runtime.unitOfWork.pendingEvents.collect {
        case e: TransitionLifecycleEvent => e.kind
      } shouldBe Vector(TransitionLifecycleKind.BeforeTransition)
      summon[ExecutionContext].runtime.unitOfWork.pendingEvents.collect {
        case e: CommittedTransition => e
      } shouldBe empty
    }

    "persist one safe Rollback failure for a selected transition without a committed success" in {
      Given("a selected transition bound to a runtime UnitOfWork with an in-memory EventStore")
      val store = EventStore.inMemory
      val base = ExecutionContext.create()
      lazy val context: ExecutionContext = ExecutionContext.withRuntimeContext(base, runtime)
      lazy val unitofwork = new UnitOfWork(
        context,
        EventEngine.noop(DataStore.noop(), eventstore = store)
      )
      lazy val runtime: RuntimeContext = new RuntimeContext(
        core = base.runtime.core,
        unitofworksupplier = () => unitofwork,
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
        token = "planned-transition-selected-rollback-spec"
      )
      given ExecutionContext = context
      given EntityPersistent[_Person] = _person_persistent
      val hook = new PlannedTransitionValidationHook(new ProviderWithBoundPlan(_binding))
      val entity = _Person(
        org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
          "test",
          "hook_selected_rollback",
          _cid,
          entropy = "hook_selected_rollback"
        ),
        "hanako"
      )

      When("the selected transition completes its actions and the UnitOfWork explicitly rolls back")
      val result = hook.beforeUpdate(entity, summon[EntityPersistent[_Person]])
      val rollbackresult = unitofwork.rollback()

      Then("one taxonomy-only Rollback record preserves the selected binding while no committed envelope is emitted")
      result shouldBe Consequence.unit
      rollbackresult.isSuccess shouldBe true
      val failures = store.query(EventStore.Query(kind = Some("transition-failed"))).toOption.getOrElse(Vector.empty)
      failures should have size 1
      failures.head.lane shouldBe EventLane.NonTransactional
      failures.head.payload.get("transition.failure.outcome") shouldBe Some(TransitionLifecycleFailureOutcome.Rollback.value)
      failures.head.payload.get("transition.source") shouldBe Some("Draft")
      failures.head.payload.values.mkString(" ") should not include "selected transition was not committed"
      store.query(EventStore.Query(kind = Some("committed-transition"))).toOption.getOrElse(Vector.empty) shouldBe empty
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
      failures.head.payload.get("transition.failure.stage") shouldBe Some(TransitionLifecycleFailureStage.Action.value)
      failures.head.payload.values.mkString(" ") should not include "transition failed in spec"
      store.query(EventStore.Query(kind = Some("committed-transition"))).toOption.getOrElse(Vector.empty) shouldBe empty
    }

    "persist one safe planning failure after hook failure rolls back" in {
      Given("a failing planner bound to a runtime UnitOfWork with an in-memory EventStore")
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
        token = "planned-transition-planning-failure-spec"
      )
      given ExecutionContext = context
      given EntityPersistent[_Person] = _person_persistent
      val hook = new PlannedTransitionValidationHook(new _ProviderWithPlanningFailure)
      val entity = _Person(
        org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
          "test",
          "hook_planning_failure_rollback",
          _cid,
          entropy = "hook_planning_failure_rollback"
        ),
        "hanako"
      )

      When("the planner fails and its runtime UnitOfWork rolls back")
      val result = hook.beforeUpdate(entity, summon[EntityPersistent[_Person]])
      val rollbackresult = summon[ExecutionContext].runtime.unitOfWork.rollback()

      Then("exactly one non-transactional planning failure is stored without private planner text")
      result shouldBe a[Consequence.Failure[_]]
      rollbackresult.isSuccess shouldBe true
      val failures = store.query(EventStore.Query(kind = Some("transition-failed"))).toOption.getOrElse(Vector.empty)
      failures should have size 1
      failures.head.lane shouldBe EventLane.NonTransactional
      failures.head.payload.get("transition.failure.stage") shouldBe Some(TransitionLifecycleFailureStage.Planning.value)
      failures.head.payload.get("transition.failure.taxonomy").map(_.toString).getOrElse("") should not be empty
      failures.head.payload.values.mkString(" ") should not include "planner secret in spec"
      store.query(EventStore.Query(kind = Some("committed-transition"))).toOption.getOrElse(Vector.empty) shouldBe empty
    }

    "persist a typed planning rejection outcome after rollback" in {
      Given("a typed planner rejection bound to a runtime UnitOfWork with an in-memory EventStore")
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
        token = "planned-transition-typed-rejection-spec"
      )
      given ExecutionContext = context
      given EntityPersistent[_Person] = _person_persistent
      val hook = new PlannedTransitionValidationHook(new TypedPlanningRejectionProvider)
      val entity = _Person(
        org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
          "test",
          "hook_typed_rejection",
          _cid,
          entropy = "hook_typed_rejection"
        ),
        "hanako"
      )

      When("the typed planner rejection is returned and the runtime UnitOfWork rolls back")
      val result = hook.beforeUpdate(entity, summon[EntityPersistent[_Person]])
      val rollbackresult = summon[ExecutionContext].runtime.unitOfWork.rollback()

      Then("the safe typed outcome is projected without private text or a committed success")
      result shouldBe a[Consequence.Failure[_]]
      rollbackresult.isSuccess shouldBe true
      val failures = store.query(EventStore.Query(kind = Some("transition-failed"))).toOption.getOrElse(Vector.empty)
      failures should have size 1
      failures.head.payload.get("transition.failure.stage") shouldBe Some(TransitionLifecycleFailureStage.Planning.value)
      failures.head.payload.get("transition.failure.outcome") shouldBe Some(TransitionLifecycleFailureOutcome.Target.value)
      failures.head.payload.values.mkString(" ") should not include "typed planner private failure"
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

  private def _completed_program: ExecUowM[ActionExecution] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], ActionExecution](
      ActionExecution.Completed(
        StateMachineOperationResult(
          StateMachineResultTypeReference("test.result"),
          ContextReference("result", "1")
        )
      )
    )

  private def _failed_program: ExecUowM[ActionExecution] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], ActionExecution](
      ActionExecution.Failed(
        StateMachineOperationFailure("transition_failed", "transition failed in spec", Vector.empty)
      )
    )

  private def _suspended_program: ExecUowM[ActionExecution] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], ActionExecution](
      ActionExecution.Suspended(
        Continuation(
          StateMachineRunIdentity("run-hook"),
          ContinuationIdentity("continuation-hook"),
          StateMachineRevision("1"),
          StateMachineRequiredOperation(
            StateMachineRequiredOperationIdentity("hook.required"),
            "hook.action",
            StateMachineOperationIdentity("hook", "required"),
            None,
            None,
            StateMachineRequiredOperationMetadata(
              ContextContract("hook.context", Vector.empty, Vector.empty),
              CompletionContract("hook.completion", Vector.empty),
              EvidenceContract("hook.evidence", Vector.empty),
              Vector.empty
            )
          ),
          ContextBundle("hook context", Vector.empty, Vector.empty, ContextSnapshot("1"))
        )
      )
    )

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

  private val _explicit_operation_binding: CmlTransitionBinding = {
    val machine = CmlStateMachineIdentity("person-lifecycle")
    val trigger = CmlStateMachineTriggerIdentity(machine, "operation:entity.updateSalesOrder")
    val source = CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("Draft")))
    val target = CmlStateMachineStateIdentity(machine, CmlStateMachineStatePath(Vector("Approved")))
    val contextidentity = CmlStateMachineTriggerContextIdentity(trigger)
    val context = CmlStateMachineTriggerContext(
      identity = contextidentity,
      version = CmlStateMachineVersion(1),
      fields = Vector("eventName", "targetIdentifier", "currentState", "candidateState").map { name =>
        CmlStateMachineTriggerContextField(
          CmlStateMachineTriggerContextFieldIdentity(contextidentity, name),
          CmlStateMachineScalarType.StringValue
        )
      }
    )
    CmlTransitionBinding(
      componentId = ComponentId("org.example.Person"),
      entityType = _cid,
      machine = machine,
      version = CmlStateMachineVersion(1),
      transition = CmlStateMachineTransitionIdentity(machine, 0),
      source = source,
      target = CmlStateMachineTransitionTarget.State(target),
      trigger = trigger,
      operation = Some(CmlStateMachineOperationIdentity("entity", "updateSalesOrder")),
      triggerContext = Some(context)
    )
  }

  private def _operation_context(
    store: EventStore,
    selector: String
  ): ExecutionContext = {
    val base = ExecutionContext.create()
    lazy val context: ExecutionContext = _with_invocation(
      ExecutionContext.withRuntimeContext(base, runtime),
      selector
    )
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
      token = s"planned-transition-operation-$selector"
    )
    context
  }

  private def _with_invocation(
    context: ExecutionContext,
    selector: String
  ): ExecutionContext =
    context match {
      case instance: ExecutionContext.Instance =>
        new ExecutionContext.Instance(
          instance.core,
          instance.cncfCore.copy(
            executionControl = instance.cncfCore.executionControl.copy(
              invocation = Some(ExecutionInvocationIdentity("hook-operation", 1L, selector, explicit = true))
            )
          )
        )
      case _ =>
        fail("The planned-transition hook specification requires a CNCF execution context instance.")
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
            transitionActions = Vector(transition),
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
        def program(state: S, event: TransitionEvent): ExecUowM[ActionExecution] = {
          val _ = (state, event)
          _execution_trace = _execution_trace :+ label
          _completed_program
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
        def program(state: T, event: TransitionEvent): ExecUowM[ActionExecution] = {
          val _ = (state, event)
          _failed_program
        }
      }
      Consequence.success(
        Some(
          ExecutionPlan(
            exitActions = Vector.empty,
            transitionActions = Vector(failaction),
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

  private final class ProviderWithOutcomePlan(
    outcome: ExecUowM[ActionExecution]
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
      val action = new ResolvedAction[T, TransitionEvent] {
        def program(state: T, event: TransitionEvent): ExecUowM[ActionExecution] = {
          val _ = (state, event)
          outcome
        }
      }
      Consequence.success(Some(ExecutionPlan(Vector.empty, Vector(action), Vector.empty)))
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

  private final class _ProviderWithPlanningFailure extends StateMachinePlannerProvider {
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
      Consequence.stateConflict("planner secret in spec")
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
    private var _execution_trace = Vector.empty[String]

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
      Consequence.success(
        Some(
          ExecutionPlan(
            exitActions = Vector.empty,
            transitionActions = Vector(new ResolvedAction[T, TransitionEvent] {
              def program(state: T, transitionevent: TransitionEvent): ExecUowM[ActionExecution] = {
                val _ = (state, transitionevent)
                _execution_trace = _execution_trace :+ "transition"
                _completed_program
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

  private final class TypedPlanningRejectionProvider extends StateMachinePlannerProvider {
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
      planForUpdateOutcome(entity, tc, event).toConsequence
    }

    override def planForUpdateOutcome[T](
      entity: T,
      tc: EntityPersistent[T],
      event: TransitionEvent
    )(using ExecutionContext): TransitionPlanningResult[T] = {
      val _ = (entity, tc, event)
      _typed_planning_rejection[T]
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

    private def _typed_planning_rejection[T]: TransitionPlanningResult[T] =
      Consequence.stateConflict("typed planner private failure") match {
        case Consequence.Failure(conclusion) =>
          TransitionPlanningResult.Rejected(
            TransitionLifecycleFailureOutcome.Target,
            conclusion,
            Some(_binding)
          )
      }
  }
}
