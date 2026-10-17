package org.goldenport.cncf.action

import cats.~>
import cats.data.State
import cats.effect.Ref
import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.*
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.StoredTable
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.aggregate.{AggregateCollection, AggregateDefinition, ContextualAggregateBuilder}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection}
import org.goldenport.cncf.operation.{CmlOperationAccess, CmlOperationDefinition}
import org.goldenport.cncf.observability.{DslChokepointContext, DslChokepointHook, DslChokepointOutcome, DslChokepointPhase}
import org.goldenport.cncf.security.*
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.cncf.unitofwork.*
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.{Field, Record}
import org.scalacheck.{Gen, Shrink}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 15, 2026
 */
private[cncf] object AggregateObservedMutationCaptureFixture {
  import AggregateObservedSnapshotFixture.{Root, Rendering}
  import EntityVersionedMutationOutcomeFixture.{Rows, ProjectionMode, Variant, success, revision, conclusion, reasons, structuredFailure}
  import EntityVersionedMutationPortFixture.{RecordingStore, SelectedPlan}

  val representations = AggregateObservedSnapshotFixture.representations
  val backends = Vector("memory", "sqlite")
  val componentName: String = "org.goldenport.cncf.test.AggregateCapture"
  enum Mode { case Current, Changed, StaleCurrent, StaleChanged }
  enum Fault { case None, Eviction, Put }
  enum CallbackMode { case Good, Structured, Throw, NullConsequence, NullSuccess, NullFailure, Fatal, Control, Interrupted, ForeignId }
  enum CallbackPhase { case Builder, CurrentRendering, RootRendering, Conversion, Identity }
  final class FatalSignal extends VirtualMachineError("private fatal credential")
  final class ControlSignal extends scala.util.control.ControlThrowable
  final case class Sample(title: String, retained: String, mode: Mode,
    representation: EntityRevisionRepresentation, alias: Boolean, projection: ProjectionMode, fault: Fault)
  final case class Expected(rows: Rows, physical: Map[(String, String), StoredTable])
  final case class Resident(selected: Option[Root], unrelated: Option[Root], memoryselected: Option[Root], memoryunrelated: Option[Root])
  final case class Attempt(result: Consequence[EntityVersionedMutationCaptureOutcome[?]], callbacks: Int)
  final case class HookEvent(name: String, context: DslChokepointContext,
    phase: Option[DslChokepointPhase], outcome: Option[DslChokepointOutcome], execution: ExecutionContext)
  final class HookLog {
    private var _events = Vector.empty[HookEvent]
    def events: Vector[HookEvent] = _events
    def clear(): Unit = { _events = Vector.empty }
    def append(event: HookEvent): Unit = { _events :+= event }
  }
  final class RecordingHook(name: String, log: HookLog) extends DslChokepointHook {
    private var _fault: HookEvent => Option[Throwable] = _ => None
    def arm(fault: HookEvent => Option[Throwable]): Unit = { _fault = fault }
    private def _record(context: DslChokepointContext, phase: Option[DslChokepointPhase],
      outcome: Option[DslChokepointOutcome])(using ec: ExecutionContext): Unit = {
      val event = HookEvent(name, context, phase, outcome, ec)
      log.append(event)
      _fault(event).foreach(signal => throw signal)
    }
    override def enter(ctx: DslChokepointContext)(using ExecutionContext): Unit = _record(ctx, None, None)
    override def leave(ctx: DslChokepointContext, outcome: DslChokepointOutcome)(using ExecutionContext): Unit =
      _record(ctx, None, Some(outcome))
    override def phaseEnter(ctx: DslChokepointContext, phase: DslChokepointPhase)(using ExecutionContext): Unit =
      _record(ctx, Some(phase), None)
    override def phaseLeave(ctx: DslChokepointContext, phase: DslChokepointPhase,
      outcome: DslChokepointOutcome)(using ExecutionContext): Unit = _record(ctx, Some(phase), Some(outcome))
  }

  private val _characters = Vector('a', 'Z', '0', '日', '本', '語', 'α', 'β', 'Ω')
  private val _text: Gen[String] = for {
    length <- Gen.chooseNum(1, 24)
    characters <- Gen.listOfN(length, Gen.oneOf(_characters))
  } yield characters.mkString
  val samples: Gen[Sample] = for {
    title <- _text
    retained <- _text
    mode <- Gen.oneOf(Mode.values.toVector)
    representation <- Gen.oneOf(representations)
    alias <- Gen.oneOf(false, true)
    projection <- Gen.oneOf(ProjectionMode.values.toVector)
    fault <- Gen.oneOf(Fault.values.toVector)
  } yield Sample(title, retained, mode, representation, alias, projection, fault)
  given Shrink[Sample] = Shrink { sample =>
    def _shorter_(value: String): Stream[String] =
      if (value.length <= 1) Stream.empty else Stream(value.take(math.max(1, value.length / 2)))
    val titles = _shorter_(sample.title).map(value => sample.copy(title = value))
    val retained = _shorter_(sample.retained).map(value => sample.copy(retained = value))
    val modes = sample.mode match {
      case Mode.StaleChanged => Stream(sample.copy(mode = Mode.StaleCurrent), sample.copy(mode = Mode.Changed))
      case Mode.StaleCurrent | Mode.Changed => Stream(sample.copy(mode = Mode.Current))
      case Mode.Current => Stream.empty
    }
    val representations = if (sample.representation == EntityRevisionRepresentation.Detached)
      Stream(sample.copy(representation = EntityRevisionRepresentation.Embedded)) else Stream.empty
    val aliases = if (sample.alias) Stream(sample.copy(alias = false)) else Stream.empty
    val projections = if (sample.projection != ProjectionMode.Good) Stream(sample.copy(projection = ProjectionMode.Good)) else Stream.empty
    val faults = if (sample.fault != Fault.None) Stream(sample.copy(fault = Fault.None)) else Stream.empty
    titles #::: retained #::: modes #::: representations #::: aliases #::: projections #::: faults
  }

  def callback[A](mode: CallbackMode)(value: => Consequence[A]): Consequence[A] = mode match {
    case CallbackMode.Good => value
    case CallbackMode.Structured => structuredFailure
    case CallbackMode.Throw => throw new IllegalStateException("private callback credential")
    case CallbackMode.NullConsequence => null
    case CallbackMode.NullSuccess => Consequence.success(null.asInstanceOf[A])
    case CallbackMode.NullFailure => Consequence.Failure(null)
    case CallbackMode.Fatal => throw new FatalSignal
    case CallbackMode.Control => throw new ControlSignal
    case CallbackMode.Interrupted => throw new InterruptedException("private interruption credential")
    case CallbackMode.ForeignId => value
  }

  final class RegisteredProbe(underlying: EntityVersionedMutationOutcomeFixture.Probe,
    data: AggregateObservedSnapshotFixture.Fixture) extends EntityPersistent[Root] {
    private var _conversion = CallbackMode.Good
    private var _identity = CallbackMode.Good
    private var _rendering = Rendering.Normal
    private var _pending_identity = CallbackMode.Good
    private var _pending_rendering = Rendering.Normal
    private var _conversion_signal = Option.empty[Throwable]
    private var _identity_signal = Option.empty[Throwable]
    private var _pending_identity_signal = Option.empty[Throwable]
    def admissions: Int = underlying.admissions
    def decodes: Int = underlying.decodes
    def reset(): Unit = {
      _conversion = CallbackMode.Good; _identity = CallbackMode.Good; _rendering = Rendering.Normal
      _pending_identity = CallbackMode.Good; _pending_rendering = Rendering.Normal
      _conversion_signal = None; _identity_signal = None; _pending_identity_signal = None; underlying.arm(ProjectionMode.Good)
    }
    def armProjection(mode: ProjectionMode): Unit = underlying.arm(mode)
    def armConversion(mode: CallbackMode): Unit = { _conversion = mode }
    def armIdentity(mode: CallbackMode): Unit = { _pending_identity = mode }
    def armRendering(mode: Rendering): Unit = { _pending_rendering = mode }
    def activateRendering(): Unit = { _rendering = _pending_rendering }
    def armConversionSignal(signal: Throwable): Unit = { _conversion_signal = Some(signal) }
    def armIdentitySignal(signal: Throwable): Unit = { _pending_identity_signal = Some(signal) }
    def id(entity: Root): EntityId = {
      _identity_signal.foreach(signal => throw signal)
      _identity match {
      case CallbackMode.Good => underlying.id(entity)
      case CallbackMode.ForeignId => data.unrelatedId
      case CallbackMode.NullSuccess => null
      case mode => success(callback(mode)(Consequence.success(underlying.id(entity))))
      }
    }
    def toRecord(entity: Root): Record = entity.copy(rendering = _rendering).toRecord()
    override def toStoreRecord(entity: Root): Record = underlying.toStoreRecord(entity)
    def fromRecord(record: Record): Consequence[Root] = {
      _conversion_signal.foreach(signal => throw signal)
      callback(_conversion)(underlying.fromRecord(record)).map { entity =>
        _identity = _pending_identity; _identity_signal = _pending_identity_signal; entity
      }
    }
    override private[cncf] def admitStoreRecord(r: Record): Consequence[Record] = underlying.admitStoreRecord(r)
    override private[cncf] def decodeAdmittedStoreRecord(r: Record): Consequence[Root] = underlying.decodeAdmittedStoreRecord(r)
  }

  final class RecordingSpace extends EntityStoreSpace {
    private var _routes = Vector.empty[EntityRevisionRepresentation]
    private var _outcome: Option[EntityVersionedMutationOutcome[?]] = None
    private var _persistents = Vector.empty[AnyRef]
    def routes: Vector[EntityRevisionRepresentation] = _routes
    def outcome: Option[EntityVersionedMutationOutcome[?]] = _outcome
    def persistents: Vector[AnyRef] = _persistents
    def reset(): Unit = { _routes = Vector.empty; _outcome = None; _persistents = Vector.empty }
    override private[cncf] def _save_snapshot_outcome_c[T](entity: T, persistent: EntityPersistent[T], expectedrevision: Option[EntityRevision], executionpolicy: EntityMutationExecutionPolicy)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationOutcome[EntitySnapshot[T]]] = {
      _routes :+= EntityRevisionRepresentation.Embedded; _persistents :+= persistent
      super._save_snapshot_outcome_c(entity, persistent, expectedrevision, executionpolicy).map { value => _outcome = Some(value); value }
    }
    override private[cncf] def _save_detached_outcome_c[T](entity: T, persistent: EntityPersistent[T], expectedrevision: Option[EntityRevision], executionpolicy: EntityMutationExecutionPolicy)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationOutcome[EntityRevisionCarrier[T]]] = {
      _routes :+= EntityRevisionRepresentation.Detached; _persistents :+= persistent
      super._save_detached_outcome_c(entity, persistent, expectedrevision, executionpolicy).map { value => _outcome = Some(value); value }
    }
  }

  final class Session(token: String, val representation: EntityRevisionRepresentation, writer: DataStore,
    managed: Boolean = true, optimistic: Boolean = true, registered: Boolean = true,
    rootRegistered: Boolean = true, invalidBinding: Boolean = false,
    val access: CmlOperationAccess = CmlOperationAccess("authenticated_only"),
    principal: String = "owner", denyFactory: Boolean = false,
    hook: TransitionValidationHook = TransitionValidationHook.noop,
    dslHooks: Option[Vector[DslChokepointHook]] = None) {
    val recording = new RecordingStore(writer)
    val data = AggregateObservedSnapshotFixture.fixture(token, representation, recording)
    val probe = new RegisteredProbe(new EntityVersionedMutationOutcomeFixture.Probe(data), data)
    val lifecycle = new UnitOfWorkVersionedOutcomeFixture.LifecycleStore(recording)
    val space = new RecordingSpace()
    space.addEntityStore(EntityStore.standard())
    val rootName: String = data.rootName
    val aliasName: String = s"${rootName}_aggregate"
    private val _datastores = new DataStoreSpace().useDataStore(lifecycle)
    private var _factory_calls = Vector.empty[(UnitOfWorkAuthorization, UnitOfWork)]
    private var _runtime_calls = Vector.empty[String]
    private var _builder_calls = Vector.empty[(EntityId, ExecutionContext)]
    private var _builder_fault = CallbackMode.Good
    private var _builder_transform: Root => Root = identity
    private var _active_fault = Fault.None
    private var _evictions = Vector.empty[(EntityId, Int)]
    private var _puts = 0
    private var _epoch = 0
    private var _builds = 0
    val factoryFailure: Consequence.Failure[Nothing] = Consequence.securityPermissionDenied("aggregate-capture-factory-denied")
    val component: Component = new Component() {
      private val _component_id = ComponentId(componentName)
      override val core: Component.Core = Component.Core.create(
        name = _component_id.name,
        componentId = _component_id,
        instanceId = ComponentInstanceId.default(_component_id),
        protocol = Protocol.empty
      )
      override def coreOption: Option[Component.Core] = Some(core)
      override def aggregateDefinitions: Vector[AggregateDefinition] = Vector(
        AggregateDefinition(rootName, rootName), AggregateDefinition(aliasName, rootName))
      override def operationDefinitions: Vector[CmlOperationDefinition] = Vector(
        CmlOperationDefinition(name = "changeTitle", kind = "command", inputType = "Root", outputType = "Root",
          inputValueKind = "record", access = Some(Session.this.access)))
      override def factoryOption: Option[Component.Factory] = Some(new Component.Factory {
        protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = comp.core
        protected def create_Component(params: ComponentCreate): Component = new Component() {}
        override def authorizeUnitOfWork(authorization: UnitOfWorkAuthorization, uow: UnitOfWork): Option[Consequence[Unit]] = {
          _factory_calls :+= authorization -> uow
          if (denyFactory) Some(factoryFailure) else None
        }
      })
    }
    private val _root_scope = ScopeContext(ScopeKind.Runtime, "aggregate-capture-root", None, data.context.observability)
    private val _component_scope = Component.Context("aggregate-capture-component", _root_scope, component, ComponentOrigin.Embed)
    private lazy val _runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(kind = ScopeKind.Runtime, name = "aggregate-capture-runtime", parent = Some(_component_scope),
        observabilityContext = data.context.observability, httpDriverOption = None,
        datastore = Some(DataStoreContext(_datastores)), entitystore = Some(EntityStoreContext(space)),
        entityspace = Some(new EntitySpaceContext(component.entitySpace))),
      unitofworksupplier = () => uow,
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = interpreter.interpret(operation)
      }, commitaction = value => { _runtime_calls :+= "commit"; success(value.commit()); () },
      abortaction = value => { _runtime_calls :+= "abort"; success(value.abort()); () },
      disposeaction = _ => { _runtime_calls :+= "dispose" }, token = "aggregate-capture-runtime", transitionValidationHook = hook)
    lazy val context: ExecutionContext = {
      val base = ExecutionContext.create(_runtime).asInstanceOf[ExecutionContext.Instance]
      val subject = new Principal {
        def id: PrincipalId = PrincipalId(principal)
        def attributes: Map[String, String] = data.context.security.principal.attributes
      }
      val completed = base.copy(core = base.core.copy(clock = data.context.clock), cncfCore = base.cncfCore.copy(
        observability = data.context.observability, security = data.context.security.copy(principal = subject)))
      dslHooks.map(hooks => ExecutionContext.withFrameworkDslChokepointHooks(completed, hooks)).getOrElse(completed)
    }
    lazy val uow: UnitOfWork = new UnitOfWork(context)
    lazy val interpreter: UnitOfWorkInterpreter = new UnitOfWorkInterpreter(uow)
    val realm = new EntityRealm[Root](rootName, EntityLoader[Root](_ => None),
      new IdRef(EntityRealmState[Root](Map.empty)))(using probe) {
      override def remove(id: EntityId): Boolean = {
        _evictions :+= id -> success(view.resolve(id))
        if (_active_fault == Fault.Eviction) throw new IllegalStateException("private eviction credential")
        super.remove(id)
      }
      override def put(entity: Root): Unit = {
        if (entity.id == data.id) _puts += 1
        if (_active_fault == Fault.Put) throw new IllegalStateException("private put credential")
        super.put(entity)
      }
    }
    val memory = new PartitionedMemoryRealm[Root](strategy = PartitionStrategy.byOrganizationMonthUTC, idOf = _.id)
    val collection = new EntityCollection(EntityDescriptor(data.collectionId,
      data.collection.descriptor.plan.copy(concurrencyPolicy = if (optimistic) EntityConcurrencyPolicy.Optimistic else EntityConcurrencyPolicy.None),
      probe, if (!managed) None else Some(EntityRevisionBinding(if (invalidBinding) null else representation))),
      EntityStorage(realm, Some(memory)))
    if (rootRegistered) component.entitySpace.registerEntity(rootName, collection)
    if (registered) {
      val aggregates = new AggregateCollection(new ContextualAggregateBuilder[Root] {
        def build_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[Root] = {
          _builder_calls :+= id -> ctx
          callback(_builder_fault) {
            ctx.entityStoreSpace.load(UnitOfWorkOp.EntityStoreLoad(id, probe))
              .flatMap(value => Consequence.successOrEntityNotFound(value)(id)).map(_builder_transform)
          }.map { entity => probe.activateRendering(); entity }
        }
      })
      component.aggregateSpace.register(rootName, aggregates)
      component.aggregateSpace.register(aliasName, aggregates)
    }
    val view = new ViewCollection(new ViewBuilder[Int] {
      def build(targetId: EntityId): Consequence[Int] = { _builds += 1; Consequence.success(_epoch) }
    })
    component.viewSpace.register(rootName, view, Browser.from(view, _ => view.resolve(data.id).map(Vector(_))))
    def factoryCalls: Vector[(UnitOfWorkAuthorization, UnitOfWork)] = _factory_calls
    def runtimeCalls: Vector[String] = _runtime_calls
    def builderCalls: Vector[(EntityId, ExecutionContext)] = _builder_calls
    def evictions: Vector[(EntityId, Int)] = _evictions
    def puts: Int = _puts
    def builds: Int = _builds
    def resident: Resident = Resident(realm.get(data.id), realm.get(data.unrelatedId), memory.get(data.id), memory.get(data.unrelatedId))
    def rows(reader: DataStore): Rows = new EntityVersionedMutationOutcomeFixture.Session(data).rows(reader)
    def seed(title: String = "Before", retained: String = "retained 日本 Ω", initialRevision: Long = 1L): Unit = {
      probe.reset(); _active_fault = Fault.None; _builder_fault = CallbackMode.Good; _builder_transform = identity
      data.seed(title, retained, initialRevision)
      Vector(data.id, data.unrelatedId).foreach { id => collection.putScoped(success(data.loadC(id)))(using context) }
      _epoch = 0; component.viewSpace.invalidateAll()
      success(view.resolve(data.id)); success(view.resolve(data.unrelatedId))
      // Prime the normal access schema before any independent physical baseline.
      if (!invalidBinding)
        success(space.load(UnitOfWorkOp.EntityStoreLoad(data.id, data.persistent))(using context))
      arm()
    }
    def arm(projection: ProjectionMode = ProjectionMode.Good, fault: Fault = Fault.None,
      afterAck: () => Unit = () => ()): Unit = {
      probe.reset(); _active_fault = Fault.None; _factory_calls = Vector.empty; _builder_calls = Vector.empty
      _runtime_calls = Vector.empty; _evictions = Vector.empty; _puts = 0
      lifecycle.reset(); space.reset()
      recording.reset(() => { _epoch = 1; _active_fault = fault; probe.armProjection(projection); afterAck() })
    }
    def armBuilder(mode: CallbackMode, transform: Root => Root = identity): Unit = {
      _builder_fault = mode; _builder_transform = transform
    }
    def attempt(command: Root => Consequence[Root], observed: EntityRevision = EntityRevision.INITIAL,
      aggregateName: String = rootName, targetId: EntityId = data.id, commandName: String = "changeTitle"): Attempt = {
      val call = ActionCallSupport.actionCall("changeTitle", ActionCallSupport.componentPair(component, context)) { core =>
        new CaptureCall(core, aggregateName, targetId, commandName, observed, command)
      }.asInstanceOf[CaptureCall]
      call.execute()
      Attempt(call.captureResult.get, call.callbacks)
    }
  }

  final class CaptureCall(val core: ActionCall.Core, aggregatename: String, targetid: EntityId,
    commandname: String, observed: EntityRevision, command: Root => Consequence[Root]) extends ProcedureActionCall {
    private var _capture_result: Option[Consequence[EntityVersionedMutationCaptureOutcome[?]]] = None
    private var _callbacks = 0
    def captureResult: Option[Consequence[EntityVersionedMutationCaptureOutcome[?]]] = _capture_result
    def callbacks: Int = _callbacks
    override def execute(): Consequence[OperationResponse] = {
      val callback: Root => Consequence[Root] = if (command == null) null else current => { _callbacks += 1; command(current) }
      val result = _aggregate_update_observed_capture_c[Root](aggregatename, targetid, commandname, observed)(callback)
      _capture_result = Some(result)
      result.map(_ => OperationResponse.Scalar("captured"))
    }
  }

  private def _metadata_field_name(record: Record, snake: String, camel: String): String =
    record.fields.map(_.key).filter(key => key == snake || key == camel) match {
      case Vector(key) => key
      case keys => throw new IllegalStateException(
        s"Aggregate capture fixture requires exactly one metadata field '$snake' or '$camel'; found: $keys")
    }

  def expected(current: Session, before: Rows, physical: Map[(String, String), StoredTable],
    title: String, applied: Boolean, version: Long, backend: String): Expected = {
    val previous = before.selected.get
    val atkey = _metadata_field_name(previous, "updated_at", "updatedAt")
    val bykey = _metadata_field_name(previous, "updated_by", "updatedBy")
    val timestamp: Any = if (backend == "memory") AggregateObservedSnapshotFixture.instant else AggregateObservedSnapshotFixture.instant.toString
    val fields = previous.fields.map { field =>
      if (field.key == "title") field.copy(value = Field.Value.Single(title))
      else if (field.key == current.data.revisionField) field.copy(value = Field.Value.Single(version))
      else if (field.key == atkey) field.copy(value = Field.Value.Single(timestamp))
      else if (field.key == bykey) field.copy(value = Field.Value.Single("owner"))
      else field
    }
    val order = if (backend != "memory") previous.fields.map(_.key) else Vector(
      "id", "short_id", "name", "created_at", "created_by", "updated_at", "updated_by", "post_status", "aliveness",
      "owner_id", "group_id", "privilege_id", "permission", "trace_id", "correlation_id", "business", "audit", "title",
      "content_byte_size", "content_digest", "content_charset", "content_storage", "content_ref", current.data.revisionField)
    val record = if (applied) Record(order.map(key => fields.find(_.key == key).get)) else previous
    val tables = if (!applied) physical else EntityVersionedMutationOutcomeFixture.changedPhysical(physical, current.data, title, version)
    Expected(before.copy(selected = Some(record)), tables)
  }

  trait Vocabulary extends EntityVersionedMutationOutcomeFixture.Vocabulary { self: org.scalatest.Informing =>
    def checkLifecycle(current: Session): Unit = {
      current.lifecycle.calls shouldBe Vector.empty
      current.runtimeCalls shouldBe Vector.empty
      current.uow.lastCommitResult shouldBe None
      current.uow.lastCommitTermination shouldBe None
      current.uow.lastAbortResult shouldBe None
      (current.context.unitOfWork eq current.uow) shouldBe true
      (current.context.entitySpace eq current.component.entitySpace) shouldBe true
      (current.collection.descriptor.persistent eq current.probe) shouldBe true
      current.context.clock shouldBe current.data.context.clock
      current.context.observability shouldBe current.data.context.observability
    }
    def checkPrimitive(current: Session, attempts: Int, observed: EntityRevision = EntityRevision.INITIAL): Unit = {
      current.recording.attempts shouldBe attempts
      current.recording.genericWrites shouldBe 0
      current.recording.plans should have size attempts
      current.recording.plans.foreach {
        case SelectedPlan.Versioned(plan) =>
          plan.concurrencyPolicy shouldBe EntityConcurrencyPolicy.Optimistic
          plan.writePolicy shouldBe EntityWritePolicy.WriteIfChanged
          plan.preconditionPolicy shouldBe RevisionPreconditionPolicy.ObservedRequired
          plan.expectedRevision shouldBe Some(observed)
        case _ => fail("Aggregate observed full-save requires the canonical Versioned primitive")
      }
      checkLifecycle(current)
    }
    def checkAuthorization(current: Session, aggregateName: String,
      relations: Vector[EntityAccessRelation] = Vector.empty,
      conditions: Vector[EntityAbacCondition] = Vector.empty): Unit = {
      val wanted = UnitOfWorkAuthorization("aggregate", Some(aggregateName), Some(current.rootName),
        Some(current.data.id), "command:changeTitle", Some(current.access),
        Some(componentName), Some(componentName), Vector(aggregateName),
        EntityAccessMode.UserPermission, Some(ServiceOperationModel.BusinessService), Some(EntityOperationKind.Task),
        Some(EntityApplicationDomain.Business), relationRules = relations, naturalConditions = conditions)
      current.factoryCalls should not be empty
      current.factoryCalls.foreach { case (authorization, uow) =>
        authorization shouldBe wanted
        (uow eq current.uow) shouldBe true
      }
    }
    def checkCapture(capture: EntityVersionedMutationCaptureOutcome[?], current: Session,
      wanted: Expected, applied: Boolean, version: Long): Unit = {
      (capture.mutation eq current.space.outcome.get) shouldBe true
      (capture.mutation.projection eq current.space.outcome.get.projection) shouldBe true
      val acknowledged = current.recording.captured.get
      acknowledged.applied shouldBe applied
      acknowledged.stale shouldBe None
      (capture.mutation.persisted.record eq acknowledged.record.get) shouldBe true
      capture.mutation.id shouldBe current.data.id
      capture.mutation.applied shouldBe applied
      capture.mutation.persisted.revision shouldBe revision(version)
      checkRecord(capture.mutation.persisted.record, wanted.rows.selected.get)
      current.space.routes shouldBe Vector(current.representation)
      current.space.persistents should have size 1
      (current.space.persistents.head eq current.probe) shouldBe true
      capture.toString shouldBe "EntityVersionedMutationCaptureOutcome"
    }
    def checkCoherence(current: Session, capture: EntityVersionedMutationCaptureOutcome[?],
      before: Resident, fault: Fault, projected: Boolean): Unit = {
      current.evictions shouldBe Vector(current.data.id -> 1)
      current.resident.unrelated shouldBe before.unrelated
      current.resident.memoryunrelated shouldBe before.memoryunrelated
      if (fault == Fault.Eviction) {
        reasons(capture.coherence) shouldBe Vector("entity-mutation-coherence-invalid")
        current.resident shouldBe before
        current.puts shouldBe 0
      } else if (projected && fault == Fault.Put) {
        reasons(capture.coherence) shouldBe Vector("entity-mutation-coherence-invalid")
        current.puts shouldBe 1
        current.resident.selected shouldBe None
        current.resident.memoryselected shouldBe None
      } else {
        capture.coherence shouldBe Consequence.unit
        current.puts shouldBe (if (projected) 1 else 0)
        if (projected) {
          val entity = success(capture.mutation.projection) match {
            case value: EntitySnapshot[?] => value.entity.asInstanceOf[Root]
            case value: EntityRevisionCarrier[?] => value.entity.asInstanceOf[Root]
          }
          checkRecord(current.resident.selected.get.record, entity.record)
          current.resident.memoryselected shouldBe current.resident.selected
        } else {
          current.resident.selected shouldBe None
          current.resident.memoryselected shouldBe None
        }
      }
      success(current.view.resolve(current.data.unrelatedId)) shouldBe 1
    }
    def checkSourceFailure(actual: Conclusion, expected: Conclusion): Unit = {
      val actualfacets = actual.observation.cause.descriptor.facets
      val expectedfacets = expected.observation.cause.descriptor.facets
      val actualsources = actualfacets.zipWithIndex.collect { case (source: Descriptor.Facet.SrcPos, index) => source -> index }
      val expectedsources = expectedfacets.zipWithIndex.collect { case (source: Descriptor.Facet.SrcPos, index) => source -> index }
      actualsources should have size 1
      expectedsources should have size 1
      val (actualsource, actualindex) = actualsources.head
      val (expectedsource, expectedindex) = expectedsources.head
      actualindex shouldBe expectedindex
      actualsource.pos.file.endsWith("ActionCallRepositoryPart.scala") shouldBe true
      Set("AggregateObservedMutationCaptureSpec.scala", "PostgresqlAggregateObservedMutationCaptureAcceptanceSpec.scala")
        .exists(expectedsource.pos.file.endsWith) shouldBe true
      Vector(actualsource, expectedsource).foreach { source =>
        source.pos.line should be > 0
        source.pos.column should be > 0
      }
      actual.status shouldBe expected.status
      actual.interpretation shouldBe expected.interpretation
      actual.disposition shouldBe expected.disposition
      actual.previous shouldBe expected.previous
      expected.previous.foreach(previous => (actual.previous.get eq previous) shouldBe true)
      actual.observation.timestamp should not be null
      expected.observation.timestamp should not be null
      actual.observation.copy(timestamp = expected.observation.timestamp,
        cause = actual.observation.cause.copy(descriptor = actual.observation.cause.descriptor.copy(
          facets = actualfacets.updated(actualindex, expectedsource)))) shouldBe expected.observation
    }
    def checkLaw(sample: Sample, current: Session, reader: DataStore,
      physical: () => Map[(String, String), StoredTable], backend: String): Boolean = {
      Given("selected and unrelated roots, both overflow bodies and actual Realm/View state seeded independently at revision one")
      current.seed(sample.title, sample.retained)
      val before = current.rows(reader)
      val beforephysical = physical()
      val resident = current.resident
      val pending = current.uow.pendingEvents
      val builds = current.builds
      val changed = sample.mode == Mode.Changed || sample.mode == Mode.StaleChanged
      val stale = sample.mode == Mode.StaleCurrent || sample.mode == Mode.StaleChanged
      val title = if (changed) "changed:" + sample.title else sample.title
      val observed = revision(if (stale) 2L else 1L)
      val version = if (changed && !stale) 2L else 1L
      val name = if (sample.alias) current.aliasName else current.rootName
      val wanted = expected(current, before, beforephysical, title, changed && !stale, version, backend)
      current.arm(sample.projection, sample.fault)
      When("one real registered Aggregate command uses the caller's active UnitOfWork and canonical typed producer")
      val attempt = current.attempt(root => Consequence.success(root.withTitle(title)), observed, name)
      val after = current.rows(reader)
      val afterphysical = physical()
      Then("the predeclared full ordered logical and physical state, raw identity, projection and coherence correspond without replay or enclosing lifecycle")
      checkPrimitive(current, if (stale) 0 else 1)
      checkAuthorization(current, name)
      checkRows(after, wanted.rows)
      checkPhysical(afterphysical, wanted.physical)
      current.uow.pendingEvents shouldBe pending
      attempt.callbacks shouldBe (if (stale) 0 else 1)
      if (stale) {
        checkConstructedFailure(conclusion(attempt.result), conclusion(EntityConcurrencyMetadata.staleMutation(observed, revision(1L))))
        current.builderCalls shouldBe Vector.empty
        current.space.outcome shouldBe None
        current.resident shouldBe resident
        current.evictions shouldBe Vector.empty
        current.puts shouldBe 0
        current.builds shouldBe builds
      } else {
        current.builderCalls should have size 1
        current.builderCalls.head._1 shouldBe current.data.id
        (current.builderCalls.head._2 eq current.context) shouldBe true
        current.factoryCalls should have size 2
        val capture = success(attempt.result)
        checkCapture(capture, current, wanted, changed, version)
        if (sample.projection == ProjectionMode.Good)
          checkProjection(capture.mutation.projection, EntityVersionedMutationOutcomeFixture.typedVariant(current.representation), wanted.rows, current.data, version)
        else {
          checkProjectionFailure(capture.mutation.projection, changed,
            if (sample.projection == ProjectionMode.StructuredFailure) Some(structuredFailure.conclusion) else None)
          val primary = if (changed) conclusion(capture.mutation.projection).previous.get else conclusion(capture.mutation.projection)
          if (sample.projection == ProjectionMode.StructuredFailure) (primary eq structuredFailure.conclusion) shouldBe true
          else reasons(Consequence.Failure(primary)) shouldBe Vector("entity-mutation-projection-invalid")
          conclusion(capture.mutation.projection).show should not include "private projection secret"
        }
        checkCoherence(current, capture, resident, sample.fault, sample.projection == ProjectionMode.Good)
        current.probe.admissions shouldBe 1
        current.probe.decodes shouldBe (if (Set(ProjectionMode.Good, ProjectionMode.ForeignId, ProjectionMode.NullEntity).contains(sample.projection)) 1 else if (sample.projection == ProjectionMode.WrongRevision) 1 else 0)
        current.builds shouldBe builds + 2
      }
      checkRows(current.rows(reader), wanted.rows)
      checkPhysical(physical(), wanted.physical)
      true
    }
    def checkClosed(current: Session, reader: DataStore, physical: () => Map[(String, String), StoredTable],
      backend: String, changed: Boolean, close: () => Unit): Unit = {
      Given("a fully primed actual SQL Aggregate, an independent durable reader and a post-ACK owned-writer close")
      current.seed()
      val title = if (changed) "Closed after" else "Before"
      val version = if (changed) 2L else 1L
      val before = current.rows(reader)
      val beforephysical = physical()
      val resident = current.resident
      val wanted = expected(current, before, beforephysical, title, changed, version, backend)
      var acknowledged = Option.empty[Rows]
      current.arm(afterAck = () => { acknowledged = Some(current.rows(reader)); close() })
      When("the actual Aggregate save receives a positive provider ACK before its writer closes and body projection fails")
      val capture = success(current.attempt(root => Consequence.success(root.withTitle(title)), aggregateName = current.aliasName).result)
      Then("the exact persisted capture survives genuine SQL read failure, while invalidation and eviction complete independently")
      checkPrimitive(current, 1)
      checkCapture(capture, current, wanted, changed, version)
      checkClosedManagedReadFailure(capture.mutation.projection, changed)
      checkCoherence(current, capture, resident, Fault.None, false)
      checkRows(acknowledged.get, wanted.rows)
      checkRows(current.rows(reader), wanted.rows)
      checkPhysical(physical(), wanted.physical)
    }
    def checkRace(current: Session, competitor: DataStore, reader: DataStore,
      physical: () => Map[(String, String), StoredTable], backend: String): Unit = {
      Given("a selected Aggregate observation and independently prescribed complete state for an actual intervening winner")
      current.seed()
      val before = current.rows(reader)
      val beforephysical = physical()
      val winnerstate = expected(current, before, beforephysical, "Independent winner", true, 2L, backend)
      val peer = current.data.peer(competitor)
      var winner = Option.empty[EntityRecordSnapshot]
      current.arm()
      When("the independent provider commits a canonical winner inside the domain callback before the outer save")
      val attempt = current.attempt { root =>
        winner = Some(success(peer.writeC("Independent winner")))
        Consequence.success(root.withTitle("Losing update"))
      }
      Then("the provider rejects the old observation atomically, preserves the entire winner state and never replays the domain command")
      winner.get.revision shouldBe revision(2L)
      winner.get.record.getString("title") shouldBe Some("Independent winner")
      checkPrimitive(current, 1)
      attempt.callbacks shouldBe 1
      current.recording.captured.get.stale shouldBe Some(revision(1L) -> revision(2L))
      checkConstructedFailure(conclusion(attempt.result), conclusion(EntityConcurrencyMetadata.staleMutation(revision(1L), revision(2L))))
      current.space.outcome shouldBe None
      checkRows(current.rows(reader), winnerstate.rows)
      checkPhysical(physical(), winnerstate.physical)
    }
    def checkAcknowledgement(current: Session, writer: PostgresqlPersistenceFixture.AcknowledgementStore,
      reader: DataStore, physical: () => Map[(String, String), StoredTable], mode: PostgresqlPersistenceFixture.AcknowledgementMode): Unit = {
      import PostgresqlPersistenceFixture.AcknowledgementMode
      Given("an actual Aggregate, native ACK fault writer and complete independently observed selected/body/unrelated state")
      current.seed()
      val before = current.rows(reader)
      val beforephysical = physical()
      val resident = current.resident
      val applied = mode != AcknowledgementMode.Reject
      val wanted = expected(current, before, beforephysical, "After ACK", applied, if (applied) 2L else 1L, "postgresql")
      current.arm(); writer.arm(mode)
      When("one canonical Aggregate update makes one real native commit attempt under the prescribed ACK mode")
      val attempt = current.attempt(root => Consequence.success(root.withTitle("After ACK")), aggregateName = current.aliasName)
      Then("known rollback and durable unknown ACK retain their real state without fabricated capture, local commit or replay")
      checkPrimitive(current, 1)
      writer.commitAttempts shouldBe 1
      attempt.callbacks shouldBe 1
      if (mode == AcknowledgementMode.Normal) {
        val capture = success(attempt.result)
        checkCapture(capture, current, wanted, true, 2L)
        checkProjection(capture.mutation.projection, EntityVersionedMutationOutcomeFixture.typedVariant(current.representation), wanted.rows, current.data, 2L)
        checkCoherence(current, capture, resident, Fault.None, true)
      } else {
        reasons(attempt.result) should contain(if (mode == AcknowledgementMode.Reject) "transaction-failure" else "transaction-indeterminate")
        current.space.outcome shouldBe None
        current.recording.captured shouldBe None
        current.evictions shouldBe Vector.empty
        current.resident shouldBe resident
      }
      checkRows(current.rows(reader), wanted.rows)
      checkPhysical(physical(), wanted.physical)
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
