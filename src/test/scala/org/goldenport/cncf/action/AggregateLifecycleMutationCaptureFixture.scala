package org.goldenport.cncf.action

import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.*
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.{AcknowledgementMode, AcknowledgementStore}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.aggregate.{AggregateCollection, AggregateDefinition, ContextualAggregateBuilder}
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.Browser
import org.goldenport.cncf.observability.DslChokepointHook
import org.goldenport.cncf.operation.{CmlOperationAccess, CmlOperationDefinition}
import org.goldenport.cncf.security.*
import org.goldenport.cncf.unitofwork.*
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.{Record, RecordPresentable}
import org.scalacheck.{Gen, Shrink}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Oct. 16, 2026
 * @version Oct. 16, 2026
 */
private[cncf] object AggregateLifecycleMutationCaptureFixture {
  import UnitOfWorkMutationCaptureFixture.{Environment, Input, Entity, DecodeMode, Rows, Resident, Expected, success, revision}
  import UnitOfWorkMutationCaptureFixture.given
  import AggregateObservedMutationCaptureFixture.{CallbackMode, callback}

  val componentName: String = "org.goldenport.cncf.test.AggregateLifecycleCapture"
  val representations: Vector[EntityRevisionRepresentation] = Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
  val backends: Vector[String] = Vector("memory", "sqlite")
  enum DeleteMode { case Current, Stale, Veto }
  final case class Root(id: EntityId, record: Record, rendering: () => Record) extends RecordPresentable {
    def toRecord(): Record = rendering()
  }
  final case class Sample(value: UnitOfWorkMutationCaptureFixture.Sample, alias: Boolean, deletion: DeleteMode)
  final case class Attempt[A](result: Consequence[A], callbacks: Int)
  final case class Baseline(rows: Rows, exact: Rows,
    physical: Map[(String, String), PostgresqlPersistenceFixture.StoredTable], resident: Resident, builds: Int)

  val sample: Sample = Sample(UnitOfWorkMutationCaptureFixture.sample, false, DeleteMode.Current)
  val samples: Gen[Sample] = for {
    value <- UnitOfWorkMutationCaptureFixture.samples
    alias <- Gen.oneOf(false, true)
    deletion <- Gen.oneOf(DeleteMode.values.toVector)
  } yield Sample(value.copy(mode = DecodeMode.Good), alias, deletion)
  given Shrink[Sample] = Shrink { sample =>
    val values = summon[Shrink[UnitOfWorkMutationCaptureFixture.Sample]].shrink(sample.value).map(value => sample.copy(value = value))
    val aliases = if (sample.alias) Stream(sample.copy(alias = false)) else Stream.empty
    val modes = if (sample.deletion != DeleteMode.Current) Stream(sample.copy(deletion = DeleteMode.Current)) else Stream.empty
    values #::: aliases #::: modes
  }

  final class Session(token: String, val sample: Sample, val environment: Environment,
    managed: Boolean = true, optimistic: Boolean = true, registered: Boolean = true,
    rootRegistered: Boolean = true, ambiguous: Boolean = false,
    val access: CmlOperationAccess = CmlOperationAccess("authenticated_only"),
    principal: String = "owner", denyFactory: Boolean = false,
    hooks: Option[Vector[DslChokepointHook]] = None) {
    val data = new UnitOfWorkMutationCaptureFixture.Session(token, sample.value, environment)
    val rootName: String = data.collectionId.name
    val aliasName: String = rootName + "_aggregate"
    private var _factory_calls = Vector.empty[(UnitOfWorkAuthorization, UnitOfWork)]
    private var _builder_calls = Vector.empty[(EntityId, ExecutionContext)]
    private var _runtime_calls = Vector.empty[String]
    private var _order = Vector.empty[String]
    private var _builder: CallbackMode = CallbackMode.Good
    private var _rendering: () => Option[Record] = () => None
    private var _root_rendering: () => Option[Record] = () => None
    private var _root_identity: Option[EntityId] = None
    private var _domain_resolved = false
    val factoryFailure: Consequence.Failure[Nothing] = Consequence.securityPermissionDenied("aggregate-lifecycle-factory-denied")
    val veto: Consequence.Failure[Nothing] = Consequence.operationInvalid("aggregate-lifecycle-domain-veto")
    val component: Component = new Component() {
      private val _component_id = ComponentId(componentName)
      override val core: Component.Core = Component.Core.create(name = _component_id.name,
        componentId = _component_id, instanceId = ComponentInstanceId.default(_component_id), protocol = Protocol.empty)
      override def coreOption: Option[Component.Core] = Some(core)
      override def aggregateDefinitions: Vector[AggregateDefinition] = Vector(
        AggregateDefinition(rootName, rootName), AggregateDefinition(aliasName, rootName))
      override def operationDefinitions: Vector[CmlOperationDefinition] = Vector("createRoot", "deleteRoot").map { name =>
        CmlOperationDefinition(name = name, kind = "command", inputType = "Root", outputType = "Root",
          inputValueKind = "record", access = Some(Session.this.access))
      }
      override def factoryOption: Option[Component.Factory] = Some(new Component.Factory {
        protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = comp.core
        protected def create_Component(params: ComponentCreate): Component = new Component() {}
        override def authorizeUnitOfWork(authorization: UnitOfWorkAuthorization, uow: UnitOfWork): Option[Consequence[Unit]] = {
          _factory_calls :+= authorization -> uow; _order :+= "authorize"
          if (denyFactory) Some(factoryFailure) else None
        }
      })
    }
    val persistent: EntityPersistent[Entity] = new EntityPersistent[Entity] {
      def id(entity: Entity): EntityId =
        if (_domain_resolved) _root_identity.getOrElse(data.persistent.id(entity)) else data.persistent.id(entity)
      def toRecord(entity: Entity): Record =
        if (_domain_resolved) _root_rendering().getOrElse(data.persistent.toRecord(entity)) else data.persistent.toRecord(entity)
      def fromRecord(record: Record): Consequence[Entity] =
        if (data.recording.created.nonEmpty) data.persistent.fromRecord(record)
        else Vector(data.id, data.freshId, data.unrelatedId)
          .find(id => record.getAny("id").exists(value => value == id || value == id.print))
          .map(id => Consequence.success(Entity(id, record)))
          .getOrElse(Consequence.argumentInvalid("id", "registered identity", "foreign identity"))
    }
    val collection = new EntityCollection(EntityDescriptor(data.collectionId,
      data.collection.descriptor.plan.copy(concurrencyPolicy =
        if (optimistic) EntityConcurrencyPolicy.Optimistic else EntityConcurrencyPolicy.None),
      persistent, if (managed) data.collection.descriptor.revisionBinding else None),
      EntityStorage(data.realm, Some(data.memoryRealm)))
    if (rootRegistered) component.entitySpace.registerEntity(rootName, collection)
    if (ambiguous) {
      val foreign = EntityCollectionId("foreign", "capture", rootName)
      component.entitySpace.registerEntity(rootName, new EntityCollection(
        EntityDescriptor(foreign, collection.descriptor.plan, persistent, collection.descriptor.revisionBinding),
        EntityStorage(data.realm, Some(data.memoryRealm))))
    }
    if (registered) {
      val aggregates = new AggregateCollection(new ContextualAggregateBuilder[Root] {
        def build_with_context(id: EntityId)(using ctx: ExecutionContext): Consequence[Root] = {
          _builder_calls :+= id -> ctx; _order :+= "builder"
          callback(_builder) {
            ctx.entityStoreSpace.load(UnitOfWorkOp.EntityStoreLoad(id, persistent))
              .flatMap(entity => Consequence.successOrEntityNotFound(entity)(id))
              .map { entity =>
                _domain_resolved = true
                Root(entity.id, entity.record, () => _rendering().getOrElse(entity.record))
              }
          }
        }
      })
      component.aggregateSpace.register(rootName, aggregates)
      component.aggregateSpace.register(aliasName, aggregates)
    }
    component.viewSpace.register(rootName, data.view, Browser.from(data.view, _ => Consequence.success(Vector(0))))
    private val _datastores = new DataStoreSpace().useDataStore(data.recording)
    private val _root_scope = ScopeContext(ScopeKind.Runtime, "aggregate-lifecycle-root", None, data.context.observability)
    private val _component_scope = Component.Context("aggregate-lifecycle-component", _root_scope, component, ComponentOrigin.Embed)
    private lazy val _runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(kind = ScopeKind.Runtime, name = "aggregate-lifecycle-runtime", parent = Some(_component_scope),
        observabilityContext = data.context.observability, httpDriverOption = None,
        datastore = Some(DataStoreContext(_datastores)), entitystore = Some(EntityStoreContext(data.space)),
        entityspace = Some(new EntitySpaceContext(component.entitySpace))),
      unitofworksupplier = () => uow,
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = interpreter.interpret(operation)
      }, commitaction = _ => { _runtime_calls :+= "commit" }, abortaction = _ => { _runtime_calls :+= "abort" },
      disposeaction = _ => { _runtime_calls :+= "dispose" }, token = "aggregate-lifecycle-runtime")
    lazy val context: ExecutionContext = {
      val base = ExecutionContext.create(_runtime).asInstanceOf[ExecutionContext.Instance]
      val subject = new Principal {
        def id: PrincipalId = PrincipalId(principal)
        def attributes: Map[String, String] = data.context.security.principal.attributes
      }
      val completed = base.copy(core = base.core.copy(clock = data.context.clock), cncfCore = base.cncfCore.copy(
        observability = data.context.observability, security = data.context.security.copy(principal = subject),
        idGeneration = data.context.idGeneration))
      hooks.map(value => ExecutionContext.withFrameworkDslChokepointHooks(completed, value)).getOrElse(completed)
    }
    lazy val uow: UnitOfWork = new UnitOfWork(context)
    lazy val interpreter: UnitOfWorkInterpreter = new UnitOfWorkInterpreter(uow)
    def factoryCalls: Vector[(UnitOfWorkAuthorization, UnitOfWork)] = _factory_calls
    def builderCalls: Vector[(EntityId, ExecutionContext)] = _builder_calls
    def runtimeCalls: Vector[String] = _runtime_calls
    def order: Vector[String] = _order
    def selectedName: String = if (sample.alias) aliasName else rootName
    def baseline: Baseline = Baseline(data.rows(), data.rows(environment.exact), environment.physical(), data.resident, data.viewBuilds)
    def seed(): Unit = { data.seed(); reset() }
    def reset(mode: DecodeMode = sample.value.mode, throwEvict: Boolean = false): Unit = {
      _factory_calls = Vector.empty; _builder_calls = Vector.empty; _runtime_calls = Vector.empty; _order = Vector.empty
      _builder = CallbackMode.Good; _rendering = () => None; _root_rendering = () => None; _root_identity = None
      _domain_resolved = false
      data.arm(mode, throwEvict)
    }
    def armBuilder(mode: CallbackMode): Unit = { _builder = mode }
    def armRendering(rendering: () => Option[Record], root: Boolean = false): Unit = {
      if (root) _root_rendering = rendering else _rendering = rendering
    }
    def armIdentity(id: EntityId): Unit = { _root_identity = Some(id) }
    def create(command: () => Consequence[Input] = () => Consequence.success(data.input),
      selected: String = selectedName, createPersistent: EntityPersistentCreate[Input] = data.createPersistent,
      commandName: String = "createRoot"): Attempt[EntityCreateMutationOutcome[Input]] = {
      var count = 0
      val call = ActionCallSupport.actionCall("createRoot", ActionCallSupport.componentPair(component, context)) { core =>
        new CreateCall(core, selected, commandName, createPersistent,
          if (command == null) null else () => { count += 1; _order :+= "command"; command() })
      }.asInstanceOf[CreateCall]
      call.execute()
      Attempt(call.result.get, count)
    }
    def delete(validate: Root => Consequence[Unit] = _ => Consequence.unit,
      observed: EntityRevision = revision(sample.value.version), selected: String = selectedName,
      target: EntityId = data.id): Attempt[EntityDeleteMutationOutcome] = {
      var count = 0
      val call = ActionCallSupport.actionCall("deleteRoot", ActionCallSupport.componentPair(component, context)) { core =>
        new DeleteCall(core, selected, target, observed,
          if (validate == null) null else root => { count += 1; _order :+= "validate"; validate(root) })
      }.asInstanceOf[DeleteCall]
      call.execute()
      Attempt(call.result.get, count)
    }
    // Only fixture-owned exact identities are removed between bounded generated samples.
    def cleanup(): Unit = {
      given ExecutionContext = context
      Vector(data.id, data.freshId, data.unrelatedId).foreach { id =>
        success(environment.writer.delete(data.dataCollection, data.entry(id)))
        success(environment.writer.delete(UnitOfWorkMutationCaptureFixture.bodyCollection, data.bodyEntry(id)))
      }
    }
  }

  final class CreateCall(val core: ActionCall.Core, aggregatename: String, commandname: String,
    persistent: EntityPersistentCreate[Input], command: () => Consequence[Input]) extends ProcedureActionCall {
    private var _result: Option[Consequence[EntityCreateMutationOutcome[Input]]] = None
    def result: Option[Consequence[EntityCreateMutationOutcome[Input]]] = _result
    override def execute(): Consequence[OperationResponse] = {
      val result = _aggregate_create_capture_c(aggregatename, commandname, persistent)(command)
      _result = Some(result); result.map(_ => OperationResponse.Scalar("captured"))
    }
  }
  final class DeleteCall(val core: ActionCall.Core, aggregatename: String, targetid: EntityId,
    observed: EntityRevision, validate: Root => Consequence[Unit]) extends ProcedureActionCall {
    private var _result: Option[Consequence[EntityDeleteMutationOutcome]] = None
    def result: Option[Consequence[EntityDeleteMutationOutcome]] = _result
    override def execute(): Consequence[OperationResponse] = {
      val result = _aggregate_delete_observed_capture_c[Root](aggregatename, targetid, observed)(validate)
      _result = Some(result); result.map(_ => OperationResponse.Scalar("captured"))
    }
  }

  def withBackend[A](backend: String)(body: Environment => A): A = backend match {
    case "memory" => body(UnitOfWorkMutationCaptureFixture.memory())
    case "sqlite" => UnitOfWorkMutationCaptureFixture.withSqlite()(body)
  }

  trait Vocabulary extends UnitOfWorkMutationCaptureFixture.Vocabulary { self: org.scalatest.Informing =>
    def checkLifecycle(session: Session): Unit = {
      session.runtimeCalls shouldBe Vector.empty
      session.data.recording.lifecycle shouldBe Vector.empty
      session.uow.lastCommitResult shouldBe None; session.uow.lastCommitTermination shouldBe None
      session.uow.lastAbortResult shouldBe None
      (session.context.unitOfWork eq session.uow) shouldBe true
      (session.uow.executionContext eq session.context) shouldBe true
      (session.context.entityStoreSpace eq session.data.space) shouldBe true
      (session.context.entitySpace eq session.component.entitySpace) shouldBe true
      (session.collection.descriptor.persistent eq session.persistent) shouldBe true
      (session.context.idGeneration eq session.data.context.idGeneration) shouldBe true
      session.context.clock shouldBe session.data.context.clock
      session.context.observability shouldBe session.data.context.observability
      session.data.recording.writes shouldBe 0
    }
    def checkAuthorization(session: Session, create: Boolean, calls: Int = 2): Unit = {
      val wanted = UnitOfWorkAuthorization("aggregate", Some(session.selectedName),
        Some(if (create) session.selectedName else session.rootName), if (create) None else Some(session.data.id),
        if (create) "create:createRoot" else "delete", Some(session.access), Some(componentName), Some(componentName),
        Vector(session.selectedName), EntityAccessMode.UserPermission, Some(ServiceOperationModel.BusinessService),
        Some(EntityOperationKind.Task), Some(EntityApplicationDomain.Business),
        relationRules = session.access.relation.map(EntityAccessRelation.parseList).getOrElse(Vector.empty),
        naturalConditions = session.access.condition.map(EntityAbacCondition.parseList).getOrElse(Vector.empty))
      session.factoryCalls should have size calls
      session.factoryCalls.foreach { case (authorization, uow) =>
        authorization shouldBe wanted; (uow eq session.uow) shouldBe true
      }
      session.order.head shouldBe "authorize"
      if (calls == 2) session.order shouldBe
        (if (create) Vector("authorize", "command", "authorize") else Vector("authorize", "builder", "validate", "authorize"))
    }
    def checkPreserved(session: Session, before: Baseline): Unit = {
      checkRows(session.data.rows(), before.rows); checkRows(session.data.rows(session.environment.exact), before.exact)
      checkPhysical(session.environment.physical(), before.physical)
      session.data.resident shouldBe before.resident; session.data.viewBuilds shouldBe before.builds
      session.data.coherence shouldBe Vector.empty
      session.data.space.created shouldBe None; session.data.space.deleted shouldBe None
      checkLifecycle(session)
    }
    def checkCreate(session: Session, before: Baseline, wanted: Expected, exact: Expected,
      attempt: Attempt[EntityCreateMutationOutcome[Input]]): Unit = {
      val result = success(attempt.result)
      checkCreated(result, session.data, wanted.raw)
      checkRows(session.data.rows(), wanted.rows); checkRows(session.data.rows(session.environment.exact), exact.rows)
      checkPhysical(session.environment.physical(), wanted.physical)
      checkCreateResident(session.data.resident, before.resident, wanted.raw, session.data)
      attempt.callbacks shouldBe 1; session.builderCalls shouldBe Vector.empty
      checkAuthorization(session, true); checkLifecycle(session)
      if (session.sample.value.mode == DecodeMode.Good) result.coherence shouldBe Consequence.unit
      else if (session.sample.value.mode == DecodeMode.Structured)
        (UnitOfWorkMutationCaptureFixture.conclusion(result.coherence) eq UnitOfWorkMutationCaptureFixture.conclusion(session.data.decodeFailure)) shouldBe true
      else UnitOfWorkMutationCaptureFixture.reason(result.coherence) shouldBe Some("entity-mutation-coherence-invalid")
      Vector(success(session.data.view.resolve(session.data.id)), success(session.data.view.resolve(session.data.unrelatedId))) shouldBe Vector(1, 1)
      session.data.coherence shouldBe Vector("evict-view-1", "decode")
    }
    def checkDelete(session: Session, before: Baseline, wanted: Expected, exact: Expected,
      attempt: Attempt[EntityDeleteMutationOutcome], evictionFailure: Boolean = false): Unit = {
      val result = success(attempt.result)
      checkDeleted(result, session.data)
      checkRows(session.data.rows(), wanted.rows); checkRows(session.data.rows(session.environment.exact), exact.rows)
      checkPhysical(session.environment.physical(), wanted.physical)
      if (evictionFailure) {
        session.data.resident shouldBe before.resident
        UnitOfWorkMutationCaptureFixture.reason(result.coherence) shouldBe Some("entity-mutation-coherence-invalid")
      } else { checkDeleteResident(session.data.resident, before.resident); result.coherence shouldBe Consequence.unit }
      attempt.callbacks shouldBe 1; session.builderCalls should have size 1
      session.builderCalls.head._1 shouldBe session.data.id
      (session.builderCalls.head._2 eq session.context) shouldBe true
      checkAuthorization(session, false); checkLifecycle(session)
      Vector(success(session.data.view.resolve(session.data.id)), success(session.data.view.resolve(session.data.unrelatedId))) shouldBe Vector(1, 1)
      session.data.coherence shouldBe Vector("evict-view-1")
    }
    def checkCreateLaw(session: Session): Boolean = {
      Given("a real registered root and alias, fixed identity context and independent complete root/body/resident/View baseline")
      session.seed(); val before = session.baseline
      val wanted = UnitOfWorkMutationCaptureFixture.expectedCreate(session.data, before.rows, before.physical, session.environment.normalize)
      val exact = UnitOfWorkMutationCaptureFixture.expectedCreate(session.data, before.exact, before.physical, false)
      When("one typed application creation invokes the Aggregate helper on the actual active UnitOfWork")
      val attempt = session.create()
      Then("the INITIAL receipt has exact native and Space references and the complete predeclared state with no enclosing lifecycle")
      checkSchema(session.data, before.physical); checkCreate(session, before, wanted, exact, attempt)
      true
    }
    def checkDeleteLaw(session: Session): Boolean = {
      Given("a real registered current root, both stored bodies, unrelated state and the complete independent baseline")
      session.seed(); val before = session.baseline
      val wanted = UnitOfWorkMutationCaptureFixture.expectedDelete(session.data, before.rows, before.physical, session.environment.normalize)
      val exact = UnitOfWorkMutationCaptureFixture.expectedDelete(session.data, before.exact, before.physical, false)
      val observed = revision(session.sample.value.version - (if (session.sample.deletion == DeleteMode.Stale) 1L else 0L))
      When("one observed Aggregate lifecycle deletion uses the current, stale or vetoed domain observation")
      val attempt = session.delete(_ => if (session.sample.deletion == DeleteMode.Veto) session.veto else Consequence.unit, observed)
      Then("only admitted current deletion captures the exact prior revision, and rejected branches preserve every prior field and cell")
      if (session.sample.deletion == DeleteMode.Current) checkDelete(session, before, wanted, exact, attempt)
      else {
        attempt.result shouldBe a[Consequence.Failure[?]]
        attempt.callbacks shouldBe (if (session.sample.deletion == DeleteMode.Veto) 1 else 0)
        session.data.recording.deletes shouldBe 0
        checkAuthorization(session, false, 1)
        if (session.sample.deletion == DeleteMode.Veto)
          (UnitOfWorkMutationCaptureFixture.conclusion(attempt.result) eq session.veto.conclusion) shouldBe true
        else {
          UnitOfWorkMutationCaptureFixture.reason(attempt.result) shouldBe Some("stale-entity-revision")
          session.builderCalls shouldBe Vector.empty
        }
        checkPreserved(session, before)
      }
      true
    }
    def checkAcknowledgement(session: Session, writer: AcknowledgementStore, create: Boolean, mode: AcknowledgementMode): Unit = {
      Given("one real JDBC acknowledgement seam and complete independently prescribed durable root/body state")
      session.seed(); val before = session.baseline
      val wanted = if (create) UnitOfWorkMutationCaptureFixture.expectedCreate(session.data, before.rows, before.physical, false)
        else UnitOfWorkMutationCaptureFixture.expectedDelete(session.data, before.rows, before.physical, false)
      writer.arm(mode)
      val priorcommits = writer.commitAttempts
      When("one Aggregate helper reaches the actual native rejection or commit and the selected acknowledgement")
      val created = if (create) Some(session.create()) else None
      val deleted = if (create) None else Some(session.delete())
      val result: Consequence[?] = created.map(_.result).getOrElse(deleted.get.result)
      Then("only a positive ACK yields capture; rollback and durable unknown ACK preserve their actual classifications without replay")
      writer.commitAttempts shouldBe priorcommits + 1
      session.data.recording.creates shouldBe (if (create) 1 else 0)
      session.data.recording.deletes shouldBe (if (create) 0 else 1)
      checkAuthorization(session, create); checkLifecycle(session)
      if (mode == AcknowledgementMode.Normal) {
        if (create) checkCreated(success(created.get.result), session.data, wanted.raw)
        else checkDeleted(success(deleted.get.result), session.data)
      } else {
        UnitOfWorkMutationCaptureFixture.reason(result) shouldBe
          Some(if (mode == AcknowledgementMode.Reject) "transaction-failure" else "transaction-indeterminate")
        session.data.space.created shouldBe None; session.data.space.deleted shouldBe None
        session.data.recording.created shouldBe None; session.data.recording.deleted shouldBe None
        session.data.resident shouldBe before.resident; session.data.coherence shouldBe Vector.empty
      }
      checkRows(session.data.rows(), if (mode == AcknowledgementMode.Reject) before.rows else wanted.rows)
      checkPhysical(session.environment.physical(), if (mode == AcknowledgementMode.Reject) before.physical else wanted.physical)
    }
    def checkRace(session: Session): Unit = {
      Given("a current Aggregate observation and an independently prescribed complete winning root/body state")
      session.seed(); val before = session.baseline
      val version = session.sample.value.version + 1L
      val previous = before.rows.selected.get
      val root = if (session.environment.sql) Record(previous.fields.map { field =>
        if (field.key == "title") UnitOfWorkMutationCaptureFixture.field("title", "Independent winner")
        else if (field.key == session.data.revisionField) UnitOfWorkMutationCaptureFixture.field(field.key, version)
        else field
      }) else Record(previous.fields.filterNot(field => field.key == "title" || field.key == session.data.revisionField) ++
        Vector(UnitOfWorkMutationCaptureFixture.field("title", "Independent winner"),
          UnitOfWorkMutationCaptureFixture.field(session.data.revisionField, version)))
      val wantedrows = before.rows.copy(selected = Some(root))
      val wantedphysical = before.physical.map { case (key, table) =>
        key -> (if (key._2 == session.data.dataCollection.collectionName) table.copy(rows = table.rows.map { row =>
          if (row("id") == session.data.id.print) row ++ Map("title" -> "Independent winner", session.data.revisionField -> version) else row
        }) else table)
      }
      val peer = session.environment.reader.asInstanceOf[EntityVersionedMutationDataStore]
      var winner: Option[EntityVersionedMutationResult] = None
      When("an independent actual provider commits a versioned winner during the once-only validation before atomic delete recheck")
      val attempt = session.delete { _ =>
        winner = Some(success(peer.mutateVersionedEntity(EntityVersionedMutationPlan(
          session.data.dataCollection, session.data.entry(session.data.id), session.data.revisionField,
          EntityConcurrencyPolicy.Optimistic, EntityWritePolicy.AlwaysWrite, RevisionPreconditionPolicy.ObservedRequired,
          Some(revision(session.sample.value.version)), EntityVersionedRootMutation.Patch(Record.dataAuto("title" -> "Independent winner"))
        ))(using session.context)))
        Consequence.unit
      }
      Then("the real atomic delete rejects the old observation and retains every winning field/body/cell without receipt or replay")
      winner.get shouldBe a[EntityVersionedMutationResult.Applied]
      attempt.callbacks shouldBe 1
      UnitOfWorkMutationCaptureFixture.reason(attempt.result) shouldBe Some("stale-entity-revision")
      session.data.recording.creates shouldBe 0; session.data.recording.deletes shouldBe 1
      session.data.recording.deleted shouldBe Some(EntityCompareAndDeleteResult.Stale(
        revision(session.sample.value.version), revision(version)))
      session.data.space.deleted shouldBe None
      checkRows(session.data.rows(), wantedrows); checkPhysical(session.environment.physical(), wantedphysical)
      checkDeleteResident(session.data.resident, before.resident)
      checkAuthorization(session, false); checkLifecycle(session)
    }
  }
}
