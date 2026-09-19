package org.goldenport.cncf.unitofwork

import java.time.{Clock, Instant, ZoneOffset}
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{
  CorrelationId,
  DataStoreContext,
  EntityStoreContext,
  ExecutionContext,
  ObservabilityContext,
  RuntimeContext,
  ScopeContext,
  ScopeKind,
  TraceId
}
import org.goldenport.cncf.context.IdGenerationContext
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace, EntityVersionedMutationCheckpoint}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.goldenport.cncf.entity.{
  EntityPersistent,
  EntityRevisionRepresentation,
  EntityRevisionSpecSupport,
  EntityStore,
  EntityStoreSpace
}
import org.goldenport.cncf.event.{CommittedTransition, EventEngine, EventLane, EventStore, ReceptionDomainEvent, TransitionLifecycleEvent}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.testutil.EntityRevisionFixture
import org.goldenport.cncf.statemachine.{ExecutionPlan, PlannedTransitionValidationHook, ResolvedAction, StateMachinePlannerProvider, TransitionEvent, TransitionValidationHook}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Mar. 24, 2026
 *  version Apr. 14, 2026
 *  version Jul. 25, 2026
 *  version Sep. 17, 2026
 * @version Sep. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkStateMachineHookSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _cid = EntityCollectionId("test", "sm", "person")

  "UnitOfWork transition validation hook" should {
    "invoke pre-check before update" in {
      Given("runtime context with a counting transition hook")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      val hook               = new _CountingHook
      val context            = _execution_context(datastorespace, entitystorespace, hook)
      given ExecutionContext = context
      given EntityPersistent[PersonEntity] = _person_persistent
      EntityRevisionSpecSupport.registerRevisionBinding(
        context,
        _cid,
        _person_persistent,
        EntityRevisionRepresentation.Detached
      )
      val uow = new UnitOfWork(context, EventEngine.noop(DataStore.noop()))
      val id  = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "sm_1", _cid, entropy = "sm_1")
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              PersonEntity(id, "taro", 20).toRecord()
            )
          )
        )
      )
      val entity = PersonEntity(id, "taro", 21)

      When("updating entity through UnitOfWork interpreter")
      val result = new UnitOfWorkInterpreter(uow).execute(
        UnitOfWorkOp.EntityStoreUpdateDetached(
          entity,
          Some(EntityRevision.INITIAL),
          summon[EntityPersistent[PersonEntity]]
        )
      )

      Then("pre-check is invoked once and update returns the authoritative snapshot")
      result.entity shouldBe entity
      hook.beforeUpdateCount shouldBe 1
    }

    "provide persisted and proposed records to an existing detached save" in {
      Given("a persisted entity and a record-aware transition hook")
      val datastorespace   = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      val hook             = new RecordAwareSaveHook
      val context          = _execution_context(datastorespace, entitystorespace, hook)
      given ExecutionContext = context
      given EntityPersistent[PersonEntity] = _person_persistent
      EntityRevisionSpecSupport.registerRevisionBinding(
        context,
        _cid,
        _person_persistent,
        EntityRevisionRepresentation.Detached
      )
      val id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
        "test",
        "sm_save_existing",
        _cid,
        entropy = "sm_save_existing"
      )
      val current = PersonEntity(id, "taro", 20).toRecord()
      val proposed = PersonEntity(id, "taro", 21)
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              current
            )
          )
        )
      )
      val uow = new UnitOfWork(context, EventEngine.noop(DataStore.noop()))

      When("saving the detached mutation through the UnitOfWork interpreter")
      val result = new UnitOfWorkInterpreter(uow).execute(
        UnitOfWorkOp.EntityStoreSaveDetached(
          proposed,
          Some(EntityRevision.INITIAL),
          summon[EntityPersistent[PersonEntity]]
        )
      )

      Then("the hook receives the persisted current record and complete proposed record")
      result.entity shouldBe proposed
      hook.recordAwareSaveCount shouldBe 1
      hook.legacySaveCount shouldBe 0
      hook.currentRecords shouldBe Vector(EntityRevisionFixture.persistedRecord(current))
      hook.proposedRecords shouldBe Vector(proposed.toRecord())
    }

    "retain the legacy save hook path when managed save creates an entity" in {
      Given("a record-aware transition hook and an entity without a persisted record")
      val datastorespace   = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      val hook             = new RecordAwareSaveHook
      val context          = _execution_context(datastorespace, entitystorespace, hook)
      given ExecutionContext = context
      given EntityPersistent[PersonEntity] = _person_persistent
      EntityRevisionSpecSupport.registerRevisionBinding(
        context,
        _cid,
        _person_persistent,
        EntityRevisionRepresentation.Detached
      )
      val id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
        "test",
        "sm_save_create",
        _cid,
        entropy = "sm_save_create"
      )
      val entity = PersonEntity(id, "hanako", 30)
      val uow = new UnitOfWork(context, EventEngine.noop(DataStore.noop()))

      When("saving the new managed entity through the UnitOfWork interpreter")
      val result = new UnitOfWorkInterpreter(uow).execute(
        UnitOfWorkOp.EntityStoreSaveManaged(
          entity,
          summon[EntityPersistent[PersonEntity]]
        )
      )

      Then("the legacy hook path is retained without record-aware transition selection")
      result shouldBe entity
      hook.recordAwareSaveCount shouldBe 0
      hook.legacySaveCount shouldBe 1
      hook.currentRecords shouldBe empty
      hook.proposedRecords shouldBe empty
    }

    "block update when pre-check fails" in {
      Given("runtime context with a rejecting transition hook")
      val datastorespace     = DataStoreSpace.default()
      val entitystorespace   = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      val hook               = new _RejectingUpdateHook
      val context            = _execution_context(datastorespace, entitystorespace, hook)
      given ExecutionContext = context
      given EntityPersistent[PersonEntity] = _person_persistent
      EntityRevisionSpecSupport.registerRevisionBinding(
        context,
        _cid,
        _person_persistent,
        EntityRevisionRepresentation.Detached
      )
      val id                               = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "sm_2", _cid, entropy = "sm_2")
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              PersonEntity(id, "hanako", 30).toRecord()
            )
          )
        )
      )
      val uow = new UnitOfWork(context, EventEngine.noop(DataStore.noop()))

      When("updating entity through UnitOfWork interpreter")
      val result = new UnitOfWorkInterpreter(uow).run(
        org.goldenport.ConsequenceT.liftF(
          cats.free.Free.liftF(
            UnitOfWorkOp.EntityStoreUpdateDetached(
              PersonEntity(id, "hanako", 31),
              Some(EntityRevision.INITIAL),
              summon[EntityPersistent[PersonEntity]]
            )
          )
        )
      )

      Then("update fails before mutation and existing record stays unchanged")
      result shouldBe a[Consequence.Failure[_]]

      val loadedage =
        for {
          cid  <- context.entityStoreSpace.dataStoreCollection(id)
          dsid <- context.entityStoreSpace.dataStoreEntryId(id)
          ds   <- context.dataStoreSpace.dataStore(cid)
          rec  <- ds.load(cid, dsid)
        } yield rec.flatMap(_.asMap.get("age").collect {
          case n: Int => n
        })

      loadedage shouldBe Consequence.success(Some(30))
    }

    "discard planned lifecycle success events when detached persistence fails before publication" in {
      Given("a successful planned transition and a datastore that fails before publishing its detached update")
      val datastorespace = new DataStoreSpace().useDataStore(
        new _FailingBeforePublishDataStore
      )
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      val provider = new _SuccessfulPlanProvider
      val hook = new PlannedTransitionValidationHook(provider)
      val context = _execution_context(datastorespace, entitystorespace, hook)
      given ExecutionContext = context
      given EntityPersistent[PersonEntity] = _person_persistent
      EntityRevisionSpecSupport.registerRevisionBinding(
        context,
        _cid,
        _person_persistent,
        EntityRevisionRepresentation.Detached
      )
      val id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
        "test",
        "sm_rollback",
        _cid,
        entropy = "sm_rollback"
      )
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            EntityRevisionFixture.entitySeed(
              DataStore.CollectionId.EntityStore(_cid),
              PersonEntity(id, "jiro", 40).toRecord()
            )
          )
        )
      )
      val uow = new UnitOfWork(context, EventEngine.noop(DataStore.noop()))
      val preexisting = ReceptionDomainEvent(
        "preexisting",
        "domain-event",
        Map.empty,
        Map.empty,
        Instant.EPOCH
      )
      uow.stageEvent(preexisting)

      When("the planned transition succeeds but versioned detached persistence fails before publication")
      val result = new UnitOfWorkInterpreter(uow).interpret(
        UnitOfWorkOp.EntityStoreUpdateDetached(
          PersonEntity(id, "jiro", 41),
          Some(EntityRevision.INITIAL),
          summon[EntityPersistent[PersonEntity]]
        )
      )

      Then("the failure preserves stored state and prior events but removes one-time lifecycle success events")
      result match {
        case Consequence.Failure(conclusion) =>
          conclusion.display should include ("injected before-publish failure")
        case other =>
          fail(s"expected injected before-publish failure, got $other")
      }
      provider.executionTrace shouldBe Vector("exit", "transition", "entry")
      uow.pendingEvents shouldBe Vector(preexisting)
      uow.pendingEvents.collect {
        case event: TransitionLifecycleEvent => event
      } shouldBe Vector.empty

      val loadedage =
        for {
          cid  <- context.entityStoreSpace.dataStoreCollection(id)
          dsid <- context.entityStoreSpace.dataStoreEntryId(id)
          ds   <- context.dataStoreSpace.dataStore(cid)
          rec  <- ds.load(cid, dsid)
        } yield rec.flatMap(_.asMap.get("age").collect {
          case n: Int => n
        })

      loadedage shouldBe Consequence.success(Some(40))
    }

    "discard a bound committed-transition emitter on rollback" in {
      Given("a UnitOfWork with deterministic identity issuance, an in-memory event store, and a bound committed-transition emitter")
      val instant = Instant.parse("2026-09-18T13:00:00Z")
      val clock = Clock.fixed(instant, ZoneOffset.UTC)
      val ids = IdGenerationContext.deterministic(
        IdGenerationContext.IdNamespace("test", "rollback"),
        clock,
        "rollback-committed-transition"
      )
      given ExecutionContext = ExecutionContext.withIdGenerationContext(ExecutionContext.create(clock), ids)
      val collection = EntityCollectionId("test", "sm", "person")
      val entityid = ids.entityId(collection, "rollback-entity")
      val store = EventStore.inMemory
      val unitofwork = new UnitOfWork(
        summon[ExecutionContext],
        EventEngine.noop(DataStore.noop(), eventstore = store)
      )
      var emittercalled = false
      unitofwork.stagePostCommitEventC { transactionid =>
        emittercalled = true
        CommittedTransition.create(entityid, _binding(collection), "update", transactionid)
      }

      When("the UnitOfWork rolls back before commit")
      val result = unitofwork.rollback()

      Then("the transaction aborts without invoking the emitter or persisting a committed transition")
      result.isSuccess shouldBe true
      emittercalled shouldBe false
      store.query(EventStore.Query()).toOption.getOrElse(Vector.empty) shouldBe empty
      unitofwork.lastCommitTermination shouldBe None
      unitofwork.lastAbortResult.exists(_.isSuccess) shouldBe true
    }

    "emit a safe transition failure after rollback" in {
      Given("a UnitOfWork with an in-memory event store and a failure containing private action text")
      val instant = Instant.parse("2026-09-18T14:00:00Z")
      val clock = Clock.fixed(instant, ZoneOffset.UTC)
      given ExecutionContext = ExecutionContext.create(clock)
      val collection = EntityCollectionId("test", "sm", "person")
      val entityid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
        "test",
        "sm_failure",
        collection,
        entropy = "sm_failure"
      )
      val store = EventStore.inMemory
      val unitofwork = new UnitOfWork(
        summon[ExecutionContext],
        EventEngine.noop(DataStore.noop(), eventstore = store)
      )
      val privateactiontext = "password=not-for-observability"
      val failure = Consequence.stateConflict(privateactiontext) match {
        case Consequence.Failure(conclusion) => conclusion
        case _ => fail("state conflict must produce a failure conclusion")
      }
      val lifecycle = TransitionLifecycleEvent.transitionFailed(
        TransitionEvent("update", Some(entityid)),
        Some(collection.name),
        failure
      )
      unitofwork.stagePostAbortEventC(_ => lifecycle)

      When("the transaction rolls back")
      val result = unitofwork.rollback()

      Then("one non-transactional record retains only the safe taxonomy")
      result.isSuccess shouldBe true
      lifecycle.failure.flatMap(_.message) shouldBe None
      lifecycle.failure.map(_.outcome) shouldBe Some(org.goldenport.cncf.event.TransitionLifecycleFailureOutcome.Action)
      val records = store.query(EventStore.Query(kind = Some("transition-failed"))).toOption.getOrElse(Vector.empty)
      records should have size 1
      records.head.lane shouldBe EventLane.NonTransactional
      records.head.payload.get("transition.failure.taxonomy").map(_.toString).getOrElse("") should not be empty
      records.head.payload.get("transition.failure.outcome") shouldBe Some("action")
      records.head.payload.values.mkString(" ") should not include privateactiontext
    }

    "clear post-abort lifecycle callbacks after successful commit" in {
      Given("a reusable UnitOfWork with an in-memory event store and a staged post-abort transition failure")
      val instant = Instant.parse("2026-09-19T09:00:00Z")
      val clock = Clock.fixed(instant, ZoneOffset.UTC)
      given ExecutionContext = ExecutionContext.create(clock)
      val collection = EntityCollectionId("test", "sm", "person")
      val entityid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
        "test",
        "sm_commit_then_rollback",
        collection,
        entropy = "sm_commit_then_rollback"
      )
      val store = EventStore.inMemory
      val unitofwork = new UnitOfWork(
        summon[ExecutionContext],
        EventEngine.noop(DataStore.noop(), eventstore = store)
      )
      val failure = Consequence.stateConflict("must not survive successful commit") match {
        case Consequence.Failure(conclusion) => conclusion
        case _ => fail("state conflict must produce a failure conclusion")
      }
      unitofwork.stagePostAbortEventC { _ =>
        TransitionLifecycleEvent.transitionFailed(
          TransitionEvent("update", Some(entityid)),
          Some(collection.name),
          failure
        )
      }

      When("the UnitOfWork commits successfully and is later rolled back")
      val commitresult = unitofwork.commit()
      val rollbackresult = unitofwork.rollback()

      Then("the later rollback does not persist the stale transition-failed lifecycle event")
      commitresult.isSuccess shouldBe true
      rollbackresult.isSuccess shouldBe true
      store.query(EventStore.Query(kind = Some("transition-failed"))).toOption.getOrElse(Vector.empty) shouldBe empty
    }
  }

  private def _binding(
      collection: EntityCollectionId
  ): org.goldenport.cncf.statemachine.CmlTransitionBinding = {
    val machine = org.goldenport.cncf.statemachine.CmlStateMachineIdentity("person-lifecycle")
    val source = org.goldenport.cncf.statemachine.CmlStateMachineStateIdentity(
      machine,
      org.goldenport.cncf.statemachine.CmlStateMachineStatePath(Vector("Draft"))
    )
    val target = org.goldenport.cncf.statemachine.CmlStateMachineStateIdentity(
      machine,
      org.goldenport.cncf.statemachine.CmlStateMachineStatePath(Vector("Approved"))
    )
    org.goldenport.cncf.statemachine.CmlTransitionBinding(
      componentId = ComponentId("org.example.Person"),
      entityType = collection,
      machine = machine,
      version = org.goldenport.cncf.statemachine.CmlStateMachineVersion(1),
      transition = org.goldenport.cncf.statemachine.CmlStateMachineTransitionIdentity(machine, 0),
      source = source,
      target = org.goldenport.cncf.statemachine.CmlStateMachineTransitionTarget.State(target),
      trigger = org.goldenport.cncf.statemachine.CmlStateMachineTriggerIdentity(machine, "approve")
    )
  }

  private def _execution_context(
      datastorespace: DataStoreSpace,
      entitystorespace: EntityStoreSpace,
      hook: TransitionValidationHook
  ): ExecutionContext = {
    val observability = ObservabilityContext(
      traceId = TraceId("test", "runtime"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "runtime"))
    )
    val driver                         = FakeHttpDriver.okText("nop")
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "uow-statemachine-hook-spec-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver),
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitofworksupplier = () => new UnitOfWork(context),
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException(
            "unitOfWorkInterpreter is not used in test context"
          )
      },
      commitaction = uow => {
        val _ = uow.commit()
        ()
      },
      abortaction = uow => {
        val _ = uow.rollback()
        ()
      },
      disposeaction = _ => (),
      token = "uow-statemachine-hook-spec-runtime",
      transitionValidationHook = hook
    )
    ExecutionContext.withRuntimeContext(ExecutionContext.create(), runtime)
  }

  private final case class PersonEntity(
      id: EntityId,
      name: String,
      age: Int
  ) {
    def toRecord(): Record =
      Record.dataAuto(
        "id"   -> id,
        "name" -> name,
        "age"  -> age
      )
  }

  private val _person_persistent: EntityPersistent[PersonEntity] =
    new EntityPersistent[PersonEntity] {
      def id(e: PersonEntity): EntityId     = e.id
      def toRecord(e: PersonEntity): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[PersonEntity] = {
        val m = r.asMap
        (m.get("id"), m.get("name"), m.get("age")) match {
          case (Some(id: EntityId), Some(name: String), Some(age: Int)) =>
            Consequence.success(PersonEntity(id, name, age))
          case _ =>
            Consequence.argumentInvalid("invalid person record")
        }
      }
    }

  private final class _CountingHook extends TransitionValidationHook {
    private var _before_update_count = 0
    def beforeUpdateCount: Int       = _before_update_count

    def beforeSave[T](
        entity: T,
        tc: org.goldenport.cncf.entity.EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      Consequence.unit
    }

    def beforeUpdate[T](
        entity: T,
        tc: org.goldenport.cncf.entity.EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      _before_update_count = _before_update_count + 1
      Consequence.unit
    }

    def beforeUpdateById[P](
        id: EntityId,
        patch: P,
        tc: org.goldenport.cncf.entity.EntityPersistentUpdate[P]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (id, patch, tc)
      Consequence.unit
    }
  }

  private final class _RejectingUpdateHook extends TransitionValidationHook {
    def beforeSave[T](
        entity: T,
        tc: org.goldenport.cncf.entity.EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      Consequence.unit
    }

    def beforeUpdate[T](
        entity: T,
        tc: org.goldenport.cncf.entity.EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      Consequence.stateConflict("transition pre-check failed")
    }

    def beforeUpdateById[P](
        id: EntityId,
        patch: P,
        tc: org.goldenport.cncf.entity.EntityPersistentUpdate[P]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (id, patch, tc)
      Consequence.unit
    }
  }

  private final class RecordAwareSaveHook extends TransitionValidationHook {
    private var _legacy_save_count = 0
    private var _record_aware_save_count = 0
    private var _current_records = Vector.empty[Record]
    private var _proposed_records = Vector.empty[Record]

    def legacySaveCount: Int = _legacy_save_count
    def recordAwareSaveCount: Int = _record_aware_save_count
    def currentRecords: Vector[Record] = _current_records
    def proposedRecords: Vector[Record] = _proposed_records

    def beforeSave[T](
        entity: T,
        tc: org.goldenport.cncf.entity.EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      _legacy_save_count = _legacy_save_count + 1
      Consequence.unit
    }

    override def beforeSave[T](
        entity: T,
        tc: org.goldenport.cncf.entity.EntityPersistent[T],
        current: Record,
        proposed: Record
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      _record_aware_save_count = _record_aware_save_count + 1
      _current_records = _current_records :+ current
      _proposed_records = _proposed_records :+ proposed
      Consequence.unit
    }

    def beforeUpdate[T](
        entity: T,
        tc: org.goldenport.cncf.entity.EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      Consequence.unit
    }

    def beforeUpdateById[P](
        id: EntityId,
        patch: P,
        tc: org.goldenport.cncf.entity.EntityPersistentUpdate[P]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (id, patch, tc)
      Consequence.unit
    }
  }

  private final class _SuccessfulPlanProvider extends StateMachinePlannerProvider {
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
            exitActions = Vector(_record[T]("exit")),
            transitionAction = Some(_record[T]("transition")),
            entryActions = Vector(_record[T]("entry"))
          )
        )
      )
    }

    def planForUpdateById[P](
        id: EntityId,
        patch: P,
        tc: org.goldenport.cncf.entity.EntityPersistentUpdate[P],
        event: TransitionEvent
    )(using ExecutionContext): Consequence[
      Option[ExecutionPlan[(EntityId, P), TransitionEvent]]
    ] = {
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

  private final class _FailingBeforePublishDataStore
      extends DataStore.InMemoryDataStore(CommitRecorder.noop) {
    override protected def versioned_mutation_checkpoint(
        checkpoint: EntityVersionedMutationCheckpoint
    ): Consequence[Unit] =
      checkpoint match {
        case EntityVersionedMutationCheckpoint.BeforePublish =>
          Consequence.dataStoreUnavailable("injected before-publish failure")
        case _ =>
          Consequence.unit
      }
  }
}
