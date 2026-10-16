package org.goldenport.cncf.unitofwork

import cats.~>
import cats.data.State
import cats.effect.Ref
import cats.free.Free
import org.goldenport.{Consequence, ConsequenceT, Conclusion}
import org.goldenport.cncf.action.AggregateObservedSnapshotFixture
import org.goldenport.cncf.action.AggregateObservedSnapshotFixture.Root
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.*
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.StoredTable
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.EntityVersionedMutationOutcomeFixture.{Variant, Mode, ProjectionMode, Rows, success, revision, conclusion, reasons, structuredFailure}
import org.goldenport.cncf.entity.EntityVersionedMutationPortFixture.{Patch, Sample, SelectedPlan, Captured}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection}
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.record.{Field, Record}
import org.scalacheck.{Gen, Shrink}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
private[cncf] object UnitOfWorkVersionedOutcomeFixture {
  enum Primitive { case Versioned, CompareAndSet, Direct }
  enum Fault { case None, Eviction }
  enum DecodeFault { case Good, Structured, Throw, NullConsequence, NullFailure, NullEntity }
  final case class LawSample(input: Sample, fault: Fault, decodefault: DecodeFault)
  final case class Expected(rows: Rows, physical: Map[(String, String), StoredTable], primitive: Primitive)
  final case class Resident(selected: Option[Root], unrelated: Option[Root], memoryselected: Option[Root], memoryunrelated: Option[Root])
  val samples: Gen[LawSample] = for {
    input <- EntityVersionedMutationPortFixture.samples
    fault <- Gen.oneOf(Fault.values.toVector)
    decodefault <- if (typed(input.variant)) Gen.const(DecodeFault.Good) else Gen.oneOf(DecodeFault.values.toVector)
  } yield LawSample(input, fault, decodefault)
  given Shrink[LawSample] = Shrink { sample =>
    val inputs = EntityVersionedMutationPortFixture.sampleShrink.shrink(sample.input).map(value => sample.copy(input = value))
    val faults = if (sample.fault == Fault.Eviction) Stream(sample.copy(fault = Fault.None)) else Stream.empty
    val decoders = if (sample.decodefault != DecodeFault.Good) Stream(sample.copy(decodefault = DecodeFault.Good)) else Stream.empty
    inputs #::: faults #::: decoders
  }

  final class RegisteredProbe(underlying: EntityVersionedMutationOutcomeFixture.Probe) extends EntityPersistent[Root] {
    private var _fault = DecodeFault.Good
    private var _residency_decodes = 0
    def admissions: Int = underlying.admissions
    def decodes: Int = underlying.decodes
    def residencyDecodes: Int = _residency_decodes
    def arm(projectionMode: ProjectionMode, decodeFault: DecodeFault): Unit = {
      underlying.arm(projectionMode); _fault = decodeFault; _residency_decodes = 0
    }
    def id(entity: Root): EntityId = underlying.id(entity)
    def toRecord(entity: Root): Record = underlying.toRecord(entity)
    override def toStoreRecord(entity: Root): Record = underlying.toStoreRecord(entity)
    def fromRecord(record: Record): Consequence[Root] = underlying.fromRecord(record)
    override private[cncf] def admitStoreRecord(r: Record): Consequence[Record] = underlying.admitStoreRecord(r)
    override private[cncf] def decodeAdmittedStoreRecord(r: Record): Consequence[Root] = underlying.decodeAdmittedStoreRecord(r)
    override def fromStoreRecord(r: Record): Consequence[Root] = {
      _residency_decodes += 1
      _fault match {
        case DecodeFault.Good => underlying.fromStoreRecord(r)
        case DecodeFault.Structured => structuredFailure
        case DecodeFault.Throw => throw new IllegalStateException("private residency credential")
        case DecodeFault.NullConsequence => null
        case DecodeFault.NullFailure => Consequence.Failure(null)
        case DecodeFault.NullEntity => Consequence.success(null)
      }
    }
  }

  def program[A](operation: UnitOfWorkOp[A]): ExecUowM[A] = ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, A](operation))
  def typed(variant: Variant): Boolean = EntityVersionedMutationPortFixture.isTyped(variant)
  def policy(observed: EntityRevision): EntityMutationExecutionPolicy = EntityVersionedMutationPortFixture.policy(observed)

  // This lawful DataStore deliberately exposes no versioned-provider capability.
  final class NonVersionedStore(underlying: DataStore) extends DataStore {
    def isAccept(cid: DataStore.CollectionId): Boolean = underlying.isAccept(cid)
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = underlying.create(collection, id, record)
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] = underlying.load(collection, id)
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = underlying.save(collection, id, record)
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] = underlying.update(collection, id, changes)
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] = underlying.delete(collection, id)
    def prepare(tx: org.goldenport.cncf.unitofwork.TransactionContext): PrepareResult = underlying.prepare(tx)
    def commit(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = underlying.commit(tx)
    def abort(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = underlying.abort(tx)
  }

  // Every result, capability and lifecycle call delegates to the actual selected provider.
  final class LifecycleStore(val underlying: EntityVersionedMutationPortFixture.RecordingStore)
      extends DataStore with EntityVersionedMutationDataStore {
    private var _calls = Vector.empty[String]
    def calls: Vector[String] = _calls
    def reset(): Unit = { _calls = Vector.empty }
    def isAccept(cid: DataStore.CollectionId): Boolean = underlying.isAccept(cid)
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities = underlying.entityMutationProviderCapabilities
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = underlying.create(collection, id, record)
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] = underlying.load(collection, id)
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = underlying.save(collection, id, record)
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] = underlying.update(collection, id, changes)
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] = underlying.delete(collection, id)
    def prepare(tx: org.goldenport.cncf.unitofwork.TransactionContext): PrepareResult = { _calls :+= "prepare"; underlying.prepare(tx) }
    def commit(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = { _calls :+= "commit"; underlying.commit(tx) }
    def abort(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = { _calls :+= "abort"; underlying.abort(tx) }
    def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] = underlying.mutateVersionedEntity(plan)
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = underlying.mutateEntityDirect(plan)
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = underlying.compareAndSetEntity(plan)
    override def compareAndDeleteEntity(plan: EntityCompareAndDeletePlan)(using ctx: ExecutionContext): Consequence[EntityCompareAndDeleteResult] = underlying.compareAndDeleteEntity(plan)
  }

  final class RecordingSpace extends EntityStoreSpace {
    private var _routes = Vector.empty[Variant]
    private var _outcome: Option[EntityVersionedMutationOutcome[?]] = None
    def routes: Vector[Variant] = _routes
    def outcome: Option[EntityVersionedMutationOutcome[?]] = _outcome
    def reset(): Unit = { _routes = Vector.empty; _outcome = None }
    override private[cncf] def _save_snapshot_outcome_c[T](entity: T, persistent: EntityPersistent[T], expectedrevision: Option[EntityRevision], executionpolicy: EntityMutationExecutionPolicy)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationOutcome[EntitySnapshot[T]]] = {
      _routes :+= Variant.TypedSnapshot
      super._save_snapshot_outcome_c(entity, persistent, expectedrevision, executionpolicy).map { value => _outcome = Some(value); value }
    }
    override private[cncf] def _save_detached_outcome_c[T](entity: T, persistent: EntityPersistent[T], expectedrevision: Option[EntityRevision], executionpolicy: EntityMutationExecutionPolicy)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationOutcome[EntityRevisionCarrier[T]]] = {
      _routes :+= Variant.TypedDetached
      super._save_detached_outcome_c(entity, persistent, expectedrevision, executionpolicy).map { value => _outcome = Some(value); value }
    }
    override private[cncf] def _update_record_snapshot_outcome_c[P](id: EntityId, patch: P, persistent: EntityPersistentUpdate[P], expectedrevision: Option[EntityRevision], executionpolicy: EntityMutationExecutionPolicy)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationOutcome[EntityRecordSnapshot]] = {
      _routes :+= Variant.RecordSnapshot
      super._update_record_snapshot_outcome_c(id, patch, persistent, expectedrevision, executionpolicy).map { value => _outcome = Some(value); value }
    }
    override private[cncf] def _update_record_detached_outcome_c[P](id: EntityId, patch: P, persistent: EntityPersistentUpdate[P], expectedrevision: Option[EntityRevision], executionpolicy: EntityMutationExecutionPolicy)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationOutcome[EntityRevisionCarrier[Record]]] = {
      _routes :+= Variant.RecordDetached
      super._update_record_detached_outcome_c(id, patch, persistent, expectedrevision, executionpolicy).map { value => _outcome = Some(value); value }
    }
  }

  final class Session(token: String, val variant: Variant, writer: DataStore,
    managed: Boolean = true, optimistic: Boolean = true, registered: Boolean = true,
    bindingRepresentation: Option[EntityRevisionRepresentation] = None,
    entityStore: Option[EntityStore] = Some(EntityStore.standard()),
    runtimeDataStore: Option[DataStore] = None, principal: Option[String] = None,
    denyFactory: Boolean = false, hook: TransitionValidationHook = TransitionValidationHook.noop) {
    val source = new EntityVersionedMutationPortFixture.Session(token, variant, writer)
    val setup = source.setup
    val probe = new RegisteredProbe(source.probe)
    val lifecycle = new LifecycleStore(source.recording)
    val space = new RecordingSpace()
    entityStore.foreach(space.addEntityStore)
    private val _datastores = new DataStoreSpace().useDataStore(runtimeDataStore.getOrElse(lifecycle))
    private val _declared_context = setup.context
    private var _factory_calls = Vector.empty[UnitOfWorkAuthorization]
    private var _throw_evict = false
    private var _epoch = 0
    private var _builds = 0
    private var _evictions = Vector.empty[(EntityId, Int)]
    val component: Component = new Component() {
      override def factoryOption: Option[Component.Factory] = Some(new Component.Factory {
        protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = comp.core
        protected def create_Component(params: ComponentCreate): Component = new Component() {}
        override def authorizeUnitOfWork(authorization: UnitOfWorkAuthorization, uow: UnitOfWork): Option[Consequence[Unit]] = {
          _factory_calls :+= authorization
          if (denyFactory) Some(Consequence.securityPermissionDenied("versioned-outcome-factory-denied")) else None
        }
      })
    }
    private val _root_scope = ScopeContext(ScopeKind.Runtime, "versioned-outcome-root", None, _declared_context.observability)
    private val _component_scope = Component.Context("versioned-outcome-component", _root_scope, component, ComponentOrigin.Embed)
    private lazy val _runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(kind = ScopeKind.Runtime, name = "versioned-outcome-runtime", parent = Some(_component_scope),
        observabilityContext = _declared_context.observability, httpDriverOption = None,
        datastore = Some(DataStoreContext(_datastores)), entitystore = Some(EntityStoreContext(space)),
        entityspace = Some(new EntitySpaceContext(component.entitySpace))),
      unitofworksupplier = () => uow,
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = interpreter.interpret(operation)
      }, commitaction = _ => (), abortaction = _ => (), disposeaction = _ => (),
      token = "versioned-outcome-runtime", transitionValidationHook = hook)
    lazy val context: ExecutionContext = {
      val base = ExecutionContext.create(_runtime).asInstanceOf[ExecutionContext.Instance]
      val security = principal.map { identity =>
        val subject = new Principal {
          def id: PrincipalId = PrincipalId(identity)
          def attributes: Map[String, String] = _declared_context.security.principal.attributes
        }
        _declared_context.security.copy(principal = subject)
      }.getOrElse(_declared_context.security)
      base.copy(core = base.core.copy(clock = _declared_context.clock), cncfCore = base.cncfCore.copy(
        observability = _declared_context.observability, security = security))
    }
    lazy val uow: UnitOfWork = new UnitOfWork(context)
    lazy val interpreter: UnitOfWorkInterpreter = new UnitOfWorkInterpreter(uow)
    val realm = new EntityRealm[Root](setup.rootName, EntityLoader[Root](_ => None),
      new IdRef(EntityRealmState[Root](Map.empty)))(using probe) {
      override def remove(id: EntityId): Boolean = {
        _evictions :+= id -> success(view.resolve(id))
        if (_throw_evict) throw new IllegalStateException("private eviction credential")
        super.remove(id)
      }
    }
    val memory = new PartitionedMemoryRealm[Root](strategy = PartitionStrategy.byOrganizationMonthUTC, idOf = _.id)
    val collection = new EntityCollection(EntityDescriptor(setup.collectionId,
      setup.collection.descriptor.plan.copy(concurrencyPolicy = if (optimistic) EntityConcurrencyPolicy.Optimistic else EntityConcurrencyPolicy.None),
      probe, if (managed) Some(EntityRevisionBinding(bindingRepresentation.getOrElse(setup.representation))) else None),
      EntityStorage(realm, Some(memory)))
    if (registered) component.entitySpace.registerEntity(setup.rootName, collection)
    val view = new ViewCollection(new ViewBuilder[Int] {
      def build(targetId: EntityId): Consequence[Int] = { _builds += 1; Consequence.success(_epoch) }
    })
    component.viewSpace.register(setup.rootName, view, Browser.from(view, _ => view.resolve(setup.id).map(Vector(_))))
    def builds: Int = _builds
    def evictions: Vector[(EntityId, Int)] = _evictions
    def factoryCalls: Vector[UnitOfWorkAuthorization] = _factory_calls
    def resident: Resident = Resident(realm.get(setup.id), realm.get(setup.unrelatedId), memory.get(setup.id), memory.get(setup.unrelatedId))
    def authorization: UnitOfWorkAuthorization = UnitOfWorkAuthorization("domain", Some("VersionedRoot"),
      Some(setup.rootName), Some(setup.id), "update", sourceComponentName = Some("versioned-outcome-component"),
      targetComponentName = Some("versioned-outcome-component"), entityNames = Vector("VersionedRoot"), accessMode = EntityAccessMode.UserPermission)
    def seed(title: String = "Before", retained: String = "retained 日本 Ω", initialRevision: Long = 1L): Unit = {
      _throw_evict = false
      source.seed(title, retained, initialRevision)
      Vector(setup.id, setup.unrelatedId).foreach { id => collection.putScoped(success(setup.loadC(id)))(using context) }
      _epoch = 0
      component.viewSpace.invalidateAll()
      success(view.resolve(setup.id))
      arm()
    }
    def arm(mode: ProjectionMode = ProjectionMode.Good, fault: Fault = Fault.None,
      decodeFault: DecodeFault = DecodeFault.Good, afterAck: () => Unit = () => ()): Unit = {
      _throw_evict = false; _evictions = Vector.empty; _factory_calls = Vector.empty
      lifecycle.reset(); space.reset()
      probe.arm(ProjectionMode.Good, DecodeFault.Good)
      source.arm(ProjectionMode.Good, () => { _epoch = 1; _throw_evict = fault == Fault.Eviction; probe.arm(mode, decodeFault); afterAck() })
    }
    def operation(entity: Root, patch: Patch, observed: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy,
      authorization: Option[UnitOfWorkAuthorization] = Some(this.authorization), targetId: EntityId = setup.id,
      persistent: EntityPersistent[Root] = probe, patchPersistent: EntityPersistentUpdate[Patch] = source.patchPersistent,
      selectedVariant: Variant = variant): UnitOfWorkOp[? <: EntityVersionedMutationCaptureOutcome[?]] = selectedVariant match {
      case Variant.TypedSnapshot => UnitOfWorkOp.EntityStoreSaveSnapshotOutcome(entity, observed, persistent, authorization, executionPolicy)
      case Variant.TypedDetached => UnitOfWorkOp.EntityStoreSaveDetachedOutcome(entity, observed, persistent, authorization, executionPolicy)
      case Variant.RecordSnapshot => UnitOfWorkOp.EntityStoreUpdateRecordSnapshotOutcome(targetId, patch, observed, patchPersistent, authorization, executionPolicy)
      case Variant.RecordDetached => UnitOfWorkOp.EntityStoreUpdateRecordDetachedOutcome(targetId, patch, observed, patchPersistent, authorization, executionPolicy)
    }
    def legacyOperation(entity: Root, patch: Patch, observed: Option[EntityRevision], executionPolicy: EntityMutationExecutionPolicy,
      authorization: Option[UnitOfWorkAuthorization] = Some(this.authorization)): UnitOfWorkOp[?] = variant match {
      case Variant.TypedSnapshot => UnitOfWorkOp.EntityStoreSave(entity, observed, probe, authorization, executionPolicy)
      case Variant.TypedDetached => UnitOfWorkOp.EntityStoreSaveDetached(entity, observed, probe, authorization, executionPolicy)
      case Variant.RecordSnapshot => UnitOfWorkOp.EntityStoreUpdateByIdObserved(setup.id, patch, observed.get, source.patchPersistent, authorization, executionPolicy)
      case Variant.RecordDetached => UnitOfWorkOp.EntityStoreUpdateByIdDetached(setup.id, patch, observed, source.patchPersistent, authorization, executionPolicy)
    }
    def evaluateC[A](operation: UnitOfWorkOp[A]): Consequence[A] = interpreter.evaluateInActiveUnitOfWorkC(program(operation))
  }

  def expected(current: Session, before: Rows, physical: Map[(String, String), StoredTable],
    title: String, applied: Boolean, version: Long, backend: String, primitive: Primitive = Primitive.Versioned): Expected = {
    val previous = before.selected.get
    val sql = backend != "memory"
    val timestamp: Any = if (sql) AggregateObservedSnapshotFixture.instant.toString else AggregateObservedSnapshotFixture.instant
    val fields = previous.fields.map { field =>
      val value = if (field.key == "title") title
        else if (field.key == current.setup.revisionField) version
        else if (Set("updated_at", "updatedAt").contains(field.key)) timestamp
        else if (Set("updated_by", "updatedBy").contains(field.key)) "owner"
        else if (!typed(current.variant) && Set("trace_id", "traceId").contains(field.key)) current.context.observability.traceId.value
        else if (!typed(current.variant) && Set("correlation_id", "correlationId").contains(field.key)) current.context.observability.correlationId.get.value
        else field.value.single
      field.copy(value = Field.Value.Single(value))
    }
    val order = if (sql) previous.fields.map(_.key) else if (typed(current.variant)) Vector(
      "id", "short_id", "name", "created_at", "created_by", "updated_at", "updated_by", "post_status", "aliveness",
      "owner_id", "group_id", "privilege_id", "permission", "trace_id", "correlation_id", "business", "audit", "title",
      "content_byte_size", "content_digest", "content_charset", "content_storage", "content_ref", current.setup.revisionField)
    else if (primitive == Primitive.Versioned) Vector("updated_at", "updated_by", "trace_id", "correlation_id", "title",
      "id", "short_id", "name", "created_at", "created_by", "post_status", "aliveness", "owner_id", "group_id", "privilege_id",
      "permission", "business", "audit", "content_byte_size", "content_digest", "content_charset", "content_storage", "content_ref", current.setup.revisionField)
    else Vector("id", "business", "short_id", "name", "created_at", "created_by", "post_status", "aliveness", "owner_id", "group_id",
      "privilege_id", "permission", "audit", "content_byte_size", "content_digest", "content_charset", "content_storage", "content_ref",
      "updated_at", "updated_by", "trace_id", "correlation_id", "title", current.setup.revisionField)
    val record = if (applied) Record(order.map(key => fields.find(_.key == key).get)) else previous
    val prepared = if (!sql || primitive == Primitive.Versioned) physical else physical.map { case (key, table) =>
      val guarded = if (key._2 == current.setup.dataCollection.collectionName && !table.columns.exists(_._1 == "deleted_at"))
        table.copy(columns = table.columns :+ ("deleted_at" -> (if (backend == "sqlite") "TEXT" else "text")),
          rows = table.rows.map(_ + ("deleted_at" -> null))) else table
      key -> guarded
    }
    Expected(before.copy(selected = Some(record)),
      if (applied) EntityVersionedMutationPortFixture.expectedChangedPhysical(prepared, current.source, title, version) else prepared,
      primitive)
  }

  trait Vocabulary extends EntityVersionedMutationOutcomeFixture.Vocabulary { self: org.scalatest.Informing =>
    def checkSourceFailure(actual: Conclusion, expected: Conclusion, sourceFile: String): Unit = {
      Given("separately constructed Conclusions with complete typed source facets and timestamps")
      val actualfacets = actual.observation.cause.descriptor.facets
      val expectedfacets = expected.observation.cause.descriptor.facets
      When("source positions are independently admitted before complete remaining Observation comparison")
      val actualsources = actualfacets.zipWithIndex.collect { case (source: Descriptor.Facet.SrcPos, index) => source -> index }
      val expectedsources = expectedfacets.zipWithIndex.collect { case (source: Descriptor.Facet.SrcPos, index) => source -> index }
      Then("both source owners, positions, all other facets and every Conclusion component correspond")
      actualsources should have size 1; expectedsources should have size 1
      val (actualsource, actualindex) = actualsources.head
      val (expectedsource, expectedindex) = expectedsources.head
      actualindex shouldBe expectedindex
      actualsource.pos.file.endsWith(sourceFile) shouldBe true
      Set("UnitOfWorkVersionedOutcomeSpec.scala", "PostgresqlUnitOfWorkVersionedOutcomeAcceptanceSpec.scala")
        .exists(expectedsource.pos.file.endsWith) shouldBe true
      Vector(actualsource, expectedsource).foreach { source => source.pos.line should be > 0; source.pos.column should be > 0 }
      actual.status shouldBe expected.status; actual.interpretation shouldBe expected.interpretation
      actual.disposition shouldBe expected.disposition; actual.previous shouldBe expected.previous
      expected.previous.foreach(previous => (actual.previous.get eq previous) shouldBe true)
      actual.observation.timestamp should not be null; expected.observation.timestamp should not be null
      actual.observation.copy(timestamp = expected.observation.timestamp,
        cause = actual.observation.cause.copy(descriptor = actual.observation.cause.descriptor.copy(
          facets = actualfacets.updated(actualindex, expectedsource)))) shouldBe expected.observation
    }
    def checkLifecycle(current: Session): Unit = {
      current.lifecycle.calls shouldBe Vector.empty
      current.uow.lastCommitResult shouldBe None; current.uow.lastCommitTermination shouldBe None
      current.uow.lastAbortResult shouldBe None
      (current.context.entitySpace eq current.component.entitySpace) shouldBe true
      (current.context.runtime.unitOfWork eq current.uow) shouldBe true
      (current.collection.descriptor.persistent eq current.probe) shouldBe true
      current.context.clock shouldBe current.source.setup.context.clock
      current.context.observability shouldBe current.source.setup.context.observability
    }
    def checkPrimitive(current: Session, primitive: Primitive, attempts: Int = 1): Unit = {
      current.source.recording.attempts shouldBe attempts; current.source.recording.genericWrites shouldBe 0
      current.source.recording.plans should have size attempts
      current.source.recording.plans.foreach {
        case SelectedPlan.Versioned(plan) => primitive shouldBe Primitive.Versioned
          plan.writePolicy shouldBe EntityWritePolicy.WriteIfChanged
        case SelectedPlan.CompareAndSet(_) => primitive shouldBe Primitive.CompareAndSet
        case SelectedPlan.Direct(_) => primitive shouldBe Primitive.Direct
      }
      checkLifecycle(current)
    }
    def checkCapture(capture: EntityVersionedMutationCaptureOutcome[?], current: Session, wanted: Expected,
      applied: Boolean, version: Long): Unit = {
      (capture.mutation eq current.space.outcome.get) shouldBe true
      val raw = current.source.recording.captured.get.record.get
      (capture.mutation.persisted.record eq raw) shouldBe true
      (capture.mutation.projection eq current.space.outcome.get.projection) shouldBe true
      capture.mutation.id shouldBe current.setup.id; capture.mutation.applied shouldBe applied
      capture.mutation.persisted.revision shouldBe revision(version)
      current.space.routes shouldBe Vector(current.variant)
      checkRecord(raw, wanted.rows.selected.get)
      capture.toString shouldBe "EntityVersionedMutationCaptureOutcome"
      capture.mutation.toString shouldBe "EntityVersionedMutationOutcome"
    }
    def checkCoherence(current: Session, capture: EntityVersionedMutationCaptureOutcome[?], before: Resident,
      fault: Fault, projected: Boolean, decodeFault: DecodeFault = DecodeFault.Good): Unit = {
      current.evictions shouldBe Vector(current.setup.id -> 1)
      current.resident.unrelated shouldBe before.unrelated
      current.resident.memoryunrelated shouldBe before.memoryunrelated
      if (fault == Fault.Eviction) {
        reasons(capture.coherence) shouldBe Vector("entity-mutation-coherence-invalid")
        conclusion(capture.coherence).show should not include "private eviction credential"
        current.resident shouldBe before
        current.probe.residencyDecodes shouldBe 0
      } else {
        val decoderfailed = !typed(current.variant) && projected && decodeFault != DecodeFault.Good
        if (decoderfailed) {
          if (decodeFault == DecodeFault.Structured)
            (conclusion(capture.coherence) eq structuredFailure.conclusion) shouldBe true
          else {
            reasons(capture.coherence) shouldBe Vector("entity-mutation-coherence-invalid")
            conclusion(capture.coherence).show should not include "private residency credential"
          }
        } else capture.coherence shouldBe Consequence.unit
        current.probe.residencyDecodes shouldBe (if (projected && !typed(current.variant)) 1 else 0)
        if (!projected || decoderfailed) {
          current.resident.selected shouldBe None; current.resident.memoryselected shouldBe None
        } else {
          val projectedentity = capture.mutation.projection.toOption.get match {
            case snapshot: EntitySnapshot[?] => snapshot.entity.asInstanceOf[Root]
            case carrier: EntityRevisionCarrier[?] if typed(current.variant) => carrier.entity.asInstanceOf[Root]
            case snapshot: EntityRecordSnapshot => success(current.setup.persistent.fromRecord(snapshot.record))
            case carrier: EntityRevisionCarrier[?] => success(current.setup.persistent.fromRecord(carrier.entity.asInstanceOf[Record]))
          }
          current.resident.selected.map(_.id) shouldBe Some(current.setup.id)
          checkRecord(current.resident.selected.get.record, projectedentity.record)
          current.resident.memoryselected shouldBe current.resident.selected
        }
      }
    }
    def checkTypedPair(current: Session, legacy: Session, mode: ProjectionMode, changed: Boolean,
      reader: DataStore, physical: () => Map[(String, String), StoredTable], backend: String): Unit = {
      Given("fresh separate new and legacy active UnitOfWork sessions with the same typed projection mode")
      current.seed(); legacy.seed()
      val title = if (changed) "After" else "Before"
      val version = if (changed) 2L else 1L
      val entity = current.source.input(title, revision(1L)); val oldentity = legacy.source.input(title, revision(1L))
      val before = current.source.rows(reader); val oldbefore = legacy.source.rows(reader); val beforephysical = physical()
      val resident = current.resident
      val wanted = expected(current, before, beforephysical, title, changed, version, backend)
      val oldwanted = expected(legacy, oldbefore, wanted.physical, title, changed, version, backend)
      val wrong = if (version == 1L) 2L else 1L
      val preparedrows = if (mode == ProjectionMode.WrongRevision) {
        val stored = oldwanted.rows.selected.get
        val hydrated = oldwanted.rows.body match {
          case Some(body) => Record(stored.fields.filterNot(_.key == "content") :+
            Field("content", Field.Value.Single(body.getString("content").get)))
          case None => stored
        }
        oldwanted.rows.copy(selected = Some(hydrated.upsertSingle(legacy.setup.revisionField, wrong)), body = None)
      } else oldwanted.rows
      var acknowledged = Option.empty[Rows]
      current.arm(mode, afterAck = () => { acknowledged = Some(current.source.rows(reader)) }); legacy.arm(mode)
      val pending = current.uow.pendingEvents; val oldpending = legacy.uow.pendingEvents
      When("the new node and its legacy Save owner separately evaluate after their own real provider acknowledgements")
      val capture = success(current.evaluateC(current.operation(entity, Patch(title), Some(revision(1L)), policy(revision(1L)))))
      val oldresult = legacy.evaluateC(legacy.legacyOperation(oldentity, Patch(title), Some(revision(1L)), policy(revision(1L))))
      Then("the exact new raw outcome survives projection and the legacy result retains its prior active-evaluation containment")
      checkPrimitive(current, Primitive.Versioned); checkPrimitive(legacy, Primitive.Versioned)
      checkCapture(capture, current, wanted, changed, version)
      checkRows(acknowledged.get, wanted.rows)
      checkRows(current.source.rows(reader), wanted.rows); checkRows(legacy.source.rows(reader), oldwanted.rows)
      checkPhysical(physical(), oldwanted.physical)
      current.uow.pendingEvents shouldBe pending; legacy.uow.pendingEvents shouldBe oldpending
      if (mode == ProjectionMode.Good) {
        checkProjection(capture.mutation.projection, current.variant, wanted.rows, current.setup, version)
        checkProjection(oldresult, legacy.variant, oldwanted.rows, legacy.setup, version)
      } else {
        checkProjectionFailure(capture.mutation.projection, changed,
          if (mode == ProjectionMode.StructuredFailure) Some(structuredFailure.conclusion) else None)
        conclusion(capture.mutation.projection).show should not include "private projection secret"
        mode match {
          case ProjectionMode.StructuredFailure =>
            checkConstructedFailure(conclusion(capture.mutation.projection), conclusion(oldresult))
            if (!changed) (conclusion(capture.mutation.projection) eq structuredFailure.conclusion) shouldBe true
          case ProjectionMode.Throw | ProjectionMode.NullConsequence | ProjectionMode.NullEntity =>
            val error = conclusion(oldresult).getException.get
            if (mode == ProjectionMode.Throw) {
              error.getClass shouldBe classOf[IllegalStateException]
              error.getMessage shouldBe "private projection secret"
            } else error shouldBe a[NullPointerException]
            checkConstructedFailure(conclusion(oldresult), Conclusion.from(error))
          case ProjectionMode.ForeignId =>
            val value = success(oldresult) match {
              case snapshot: EntitySnapshot[?] => snapshot.entity.asInstanceOf[Root]
              case carrier: EntityRevisionCarrier[?] => carrier.entity.asInstanceOf[Root]
            }
            value.id shouldBe legacy.setup.unrelatedId
          case ProjectionMode.WrongRevision =>
            checkProjection(oldresult, legacy.variant, preparedrows, legacy.setup, wrong)
          case _ => fail("the fixed typed projection matrix is exhaustive")
        }
      }
      checkCoherence(current, capture, resident, Fault.None, mode == ProjectionMode.Good)
    }

    def checkClosed(current: Session, reader: DataStore, physical: () => Map[(String, String), StoredTable],
      backend: String, changed: Boolean, close: () => Unit): Unit = {
      Given("the owned live SQL writer, independent reader, complete seed and predeclared post-ACK close")
      current.seed()
      val title = if (changed) "After" else "Before"; val version = if (changed) 2L else 1L
      val entity = current.source.input(title, revision(1L))
      val before = current.source.rows(reader); val beforephysical = physical(); val resident = current.resident
      val wanted = expected(current, before, beforephysical, title, changed, version, backend)
      var durable = Option.empty[Rows]
      current.arm(afterAck = () => { durable = Some(current.source.rows(reader)); close() })
      When("the real writer closes only after acknowledgement and before the canonical body projection")
      val capture = success(current.evaluateC(current.operation(entity, Patch(title), Some(revision(1L)), policy(revision(1L)))))
      Then("raw persistence and the closed-reader projection failure remain distinct from successful invalidation and eviction")
      checkPrimitive(current, Primitive.Versioned); checkCapture(capture, current, wanted, changed, version)
      checkClosedManagedReadFailure(capture.mutation.projection, changed)
      checkCoherence(current, capture, resident, Fault.None, false)
      current.probe.admissions shouldBe 0; current.probe.decodes shouldBe 0
      checkRows(durable.get, wanted.rows); checkRows(current.source.rows(reader), wanted.rows)
      checkPhysical(physical(), wanted.physical)
    }

    def checkNative(current: Session, reader: DataStore, physical: () => Map[(String, String), StoredTable],
      backend: String, optimistic: Boolean): Unit = {
      Given("a real patch provider and an explicitly declared AlwaysWrite policy at the selected registered concurrency")
      current.seed()
      val entity = current.source.input("Native after", revision(1L))
      val before = current.source.rows(reader); val beforephysical = physical(); val resident = current.resident
      val primitive = if (optimistic) Primitive.CompareAndSet else Primitive.Direct
      val attempt = EntityMutationExecutionPolicy(if (optimistic) EntityConcurrencyPolicy.Optimistic else EntityConcurrencyPolicy.None,
        EntityWritePolicy.AlwaysWrite, if (optimistic) RevisionPreconditionPolicy.ObservedRequired else RevisionPreconditionPolicy.Managed,
        if (optimistic) Some(revision(1L)) else None)
      val observed = if (optimistic) Some(revision(1L)) else None
      val wanted = expected(current, before, beforephysical, "Native after", true, 2L, backend, primitive)
      current.collection.descriptor.plan.concurrencyPolicy shouldBe (if (optimistic) EntityConcurrencyPolicy.Optimistic else EntityConcurrencyPolicy.None)
      attempt.preconditionPolicy shouldBe (if (optimistic) RevisionPreconditionPolicy.ObservedRequired else RevisionPreconditionPolicy.Managed)
      attempt.observedRevision shouldBe observed
      current.arm()
      When("one record node follows its fixed canonical producer and the existing direct or compare-and-set selection")
      val capture = success(current.evaluateC(current.operation(entity, Patch("Native after"), observed, attempt)))
      Then("the independently declared native primitive, guard schema, Applied revision and whole state correspond")
      checkPrimitive(current, primitive)
      current.source.recording.captured.get shouldBe a[Captured.Native]
      checkCapture(capture, current, wanted, true, 2L)
      checkProjection(capture.mutation.projection, current.variant, wanted.rows, current.setup, 2L)
      checkCoherence(current, capture, resident, Fault.None, true)
      checkRows(current.source.rows(reader), wanted.rows); checkPhysical(physical(), wanted.physical)
    }

    def checkAcknowledgement(current: Session, writer: PostgresqlPersistenceFixture.AcknowledgementStore,
      reader: DataStore, physical: () => Map[(String, String), StoredTable], backend: String,
      mode: PostgresqlPersistenceFixture.AcknowledgementMode): Unit = {
      import PostgresqlPersistenceFixture.AcknowledgementMode
      Given("an unchanged default-false ACK writer, independent default-false reader and full pre-action state")
      current.seed()
      val entity = current.source.input("After ACK", revision(1L))
      val before = current.source.rows(reader); val beforephysical = physical(); val resident = current.resident
      val applied = mode != AcknowledgementMode.Reject
      val wanted = expected(current, before, beforephysical, "After ACK", applied, if (applied) 2L else 1L, backend)
      current.arm(); writer.arm(mode)
      When("one real canonical writer makes its single actual commit attempt and reports the prescribed acknowledgement")
      val result = current.evaluateC(current.operation(entity, Patch("After ACK"), Some(revision(1L)), policy(revision(1L))))
      Then("known rollback or durable unknown acknowledgement cannot produce a captured success or trigger replay")
      checkPrimitive(current, Primitive.Versioned)
      writer.commitAttempts shouldBe 1
      if (mode == AcknowledgementMode.Normal) {
        val capture = success(result)
        checkCapture(capture, current, wanted, true, 2L)
        checkProjection(capture.mutation.projection, current.variant, wanted.rows, current.setup, 2L)
        checkCoherence(current, capture, resident, Fault.None, true)
      } else {
        result shouldBe a[Consequence.Failure[?]]
        reasons(result) should contain(if (mode == AcknowledgementMode.Reject) "transaction-failure" else "transaction-indeterminate")
        current.space.outcome shouldBe None; current.source.recording.captured shouldBe None
        current.probe.admissions shouldBe 0; current.probe.decodes shouldBe 0
        current.resident shouldBe resident; current.evictions shouldBe Vector.empty
      }
      checkRows(current.source.rows(reader), wanted.rows); checkPhysical(physical(), wanted.physical)
      checkRows(current.source.rows(reader), wanted.rows); checkPhysical(physical(), wanted.physical)
    }

    def checkLaw(sample: LawSample, current: Session, reader: DataStore,
      physical: () => Map[(String, String), StoredTable], backend: String): Boolean = {
      Given("a sequentially seeded lawful sample, independent complete rows and a declared coherence fault")
      val input = sample.input
      current.seed(input.title, input.retained, input.revision)
      val changed = Set(Mode.Changed, Mode.StaleChanged, Mode.FailureChanged).contains(input.mode)
      val stale = Set(Mode.StaleCurrent, Mode.StaleChanged).contains(input.mode)
      val failure = Set(Mode.FailureCurrent, Mode.FailureChanged).contains(input.mode)
      if (failure) typed(input.variant) shouldBe true
      if (typed(input.variant)) sample.decodefault shouldBe DecodeFault.Good
      val title = if (changed) "changed:" + input.title else input.title
      val observed = revision(if (stale) input.revision - 1 else input.revision)
      val version = if (changed && !stale) input.revision + 1 else input.revision
      val entity = current.source.input(title, observed)
      val before = current.source.rows(reader); val beforephysical = physical(); val resident = current.resident
      val wanted = expected(current, before, beforephysical, title, changed && !stale, version, backend)
      val pending = current.uow.pendingEvents
      val viewbuilds = current.builds
      current.context.security shouldBe current.source.setup.context.security
      current.arm(if (failure) ProjectionMode.StructuredFailure else ProjectionMode.Good, sample.fault, sample.decodefault)
      val operation = current.operation(entity, Patch(title), Some(observed), policy(observed))
      operation match {
        case node: UnitOfWorkOp.EntityStoreSaveSnapshotOutcome[?] => (node.tc.asInstanceOf[AnyRef] eq current.probe) shouldBe true
        case node: UnitOfWorkOp.EntityStoreSaveDetachedOutcome[?] => (node.tc.asInstanceOf[AnyRef] eq current.probe) shouldBe true
        case _ => ()
      }
      When("one new operation is evaluated in its active UnitOfWork without enclosing commit or automatic abort")
      val result = current.evaluateC(operation)
      val after = current.source.rows(reader); val afterphysical = physical()
      Then("the declared primitive, full ordered logical state, entire physical state and separate retained outcomes correspond")
      checkPrimitive(current, Primitive.Versioned)
      checkRows(after, wanted.rows); checkPhysical(afterphysical, wanted.physical)
      current.uow.pendingEvents shouldBe pending
      if (stale) {
        current.source.recording.captured.get.stale shouldBe Some(observed -> revision(input.revision))
        checkConstructedFailure(conclusion(result), conclusion(EntityConcurrencyMetadata.staleMutation(observed, revision(input.revision))))
        current.space.outcome shouldBe None
        current.probe.admissions shouldBe 0; current.probe.decodes shouldBe 0
        current.probe.residencyDecodes shouldBe 0
        current.resident.selected shouldBe None; current.resident.memoryselected shouldBe None
        current.evictions shouldBe Vector(current.setup.id -> 0)
        current.builds shouldBe viewbuilds
      } else {
        val capture = success(result)
        checkCapture(capture, current, wanted, changed, version)
        if (failure) {
          checkProjectionFailure(capture.mutation.projection, changed, Some(structuredFailure.conclusion))
          if (!changed) (conclusion(capture.mutation.projection) eq structuredFailure.conclusion) shouldBe true
        } else checkProjection(capture.mutation.projection, current.variant, wanted.rows, current.setup, version)
        checkCoherence(current, capture, resident, sample.fault, !failure, sample.decodefault)
        current.builds shouldBe viewbuilds + 1
      }
      checkRows(current.source.rows(reader), wanted.rows); checkPhysical(physical(), wanted.physical)
      true
    }
  }

  private final class IdRef[A](initial: A) extends Ref[cats.Id, A] {
    private var _value: A = initial
    def get: A = _value
    def set(value: A): Unit = { _value = value }
    override def getAndSet(value: A): A = { val previous = _value; _value = value; previous }
    def access: (A, A => Boolean) = { val previous = _value; (previous, next => if (_value == previous) { _value = next; true } else false) }
    override def tryUpdate(f: A => A): Boolean = { update(f); true }
    override def tryModify[B](f: A => (A, B)): Option[B] = Some(modify(f))
    def update(f: A => A): Unit = { _value = f(_value) }
    def modify[B](f: A => (A, B)): B = { val (next, result) = f(_value); _value = next; result }
    override def modifyState[B](state: State[A, B]): B = { val (next, result) = state.run(_value).value; _value = next; result }
    override def tryModifyState[B](state: State[A, B]): Option[B] = Some(modifyState(state))
  }
}
