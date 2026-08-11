package org.goldenport.cncf.action

import cats.~>
import cats.Id
import cats.data.State
import cats.effect.Ref
import cats.syntax.all.*
import org.goldenport.Consequence
import org.goldenport.observation.Cause
import org.goldenport.cncf.component.{
  Component,
  ComponentId,
  ComponentInstanceId
}
import org.goldenport.cncf.context.{
  DataStoreContext,
  EntityStoreContext,
  ExecutionContext,
  ObservabilityContext,
  RuntimeContext,
  ScopeContext,
  ScopeKind,
  TraceId
}
import org.goldenport.cncf.datastore.{
  DataStore,
  DataStoreComponentOwner,
  DataStoreSpace
}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.aggregate.{AggregateBuilder, AggregateCollection}
import org.goldenport.cncf.entity.runtime.{
  EntityCollection,
  EntityDescriptor,
  EntityLoader,
  EntityMemoryPolicy,
  EntityRealm,
  EntityRealmState,
  EntityRuntimePlan,
  EntityStorage,
  PartitionStrategy
}
import org.goldenport.cncf.observability.{
  CallTreeContext,
  ConclusionDiagnostics,
  ObservabilityEngine
}
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.cncf.unitofwork.{
  ExecUowM,
  UnitOfWork,
  UnitOfWorkOp
}
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Jul. 24, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class ActionCallConditionalTransitionDslSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _rootcollection =
    EntityCollectionId("test", "phase49", "dsl_root")
  private val _successorcollection =
    EntityCollectionId("test", "phase49", "dsl_successor")
  private val _metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E5, rules:R14-R15, phase:49"
    )
  private val _component_scope_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E19, rules:R8,R14-R15, phase:49"
    )
  private val _internal_snapshot_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, rules:R14-R15, phase:49"
    )
  private val _phase52_exact_identity_metadata =
    afterWord(
      "in spec:entity-collection-identity, example:aggregate-exact-owner, rules:R1,R5, phase:52"
    )

  "ActionCall conditional-transition DSL" should {
    "construct one protected UnitOfWork operation" must _metadata {
      "when user and ServiceInternal helpers are executed" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R14-R15; Example: E5; one component-owned typed request"
        )
        val standardcapture = new OperationCapture
        val internalcapture = new OperationCapture
        val component = new TestComponent
        val request = _request()

        When("the two protected helpers build and interpret their programs")
        val _ =
          new ConditionalTransitionCall(
            _core(component, standardcapture),
            request,
            serviceinternal = false
          ).execute()
        val _ =
          new ConditionalTransitionCall(
            _core(component, internalcapture),
            request,
            serviceinternal = true
          ).execute()

        Then("both helpers use the executing component as provider owner")
        standardcapture.transition.map(_.componentOwner) shouldBe
          Some(DataStoreComponentOwner.create("org.goldenport.cncf.test.Phase49Dsl").TAKE)
        internalcapture.transition.map(_.componentOwner) shouldBe
          Some(DataStoreComponentOwner.create("org.goldenport.cncf.test.Phase49Dsl").TAKE)

        And("ServiceInternal changes only the admitted authorization mode")
        _authorization_shape(standardcapture.transition) shouldBe
          Vector.fill(3)(EntityAccessMode.UserPermission)
        _authorization_shape(internalcapture.transition) shouldBe
          Vector.fill(3)(EntityAccessMode.ServiceInternal)
        standardcapture.transition.map(_.request.rootId) shouldBe
          internalcapture.transition.map(_.request.rootId)
        _authorization_without_mode(standardcapture.transition) shouldBe
          _authorization_without_mode(internalcapture.transition)
      }
    }

    "load an authoritative ServiceInternal transition snapshot" must
      _internal_snapshot_metadata {
      "when a server-owned workflow needs the persisted Entity revision" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R14-R15; one persisted component-owned root"
        )
        val capture = new OperationCapture
        val component = new TestComponent
        val core = _core(component, capture)
        given ExecutionContext = core.executionContext
        val rootid = EntityId("test", "internal_snapshot_root", _rootcollection)
        val record =
          EntityConcurrencyMetadata.initializeForCreate(
            _root_persistent.toStoreRecord(Root(rootid, "terminal"))
          )
        core.executionContext.dataStoreSpace
          .inject(DataStore.CollectionId.EntityStore(_rootcollection), record)
          .TAKE
        val call = new InternalSnapshotLoadCall(core, rootid)

        When("the protected internal snapshot helper executes")
        val result = call.execute()

        Then("the helper returns the datastore revision through an authorized UnitOfWork read")
        result shouldBe a[Consequence.Success[?]]
        call.loadedRevision shouldBe Some(EntityRevision.INITIAL)
        capture.snapshotLoad
          .flatMap(_.authorization)
          .map(_.accessMode) shouldBe
          Some(EntityAccessMode.ServiceInternal)
        capture.snapshotLoad.map(_.id) shouldBe Some(rootid)
      }

      "when a server-owned workflow requests a foreign collection" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R14-R15; one root outside the executing component"
        )
        val capture = new OperationCapture
        val component = new TestComponent
        val foreigncollection =
          EntityCollectionId("test", "phase49", "foreign_snapshot_root")
        val foreignid =
          EntityId(
            foreigncollection.major,
            foreigncollection.minor,
            foreigncollection
          )
        val call =
          new InternalSnapshotLoadCall(
            _core(component, capture),
            foreignid
          )

        When("the protected internal snapshot helper admits the request")
        val result = call.execute()

        Then("component scope is denied before a UnitOfWork load is built")
        _assert_component_scope_denial(result)
        capture.snapshotLoad shouldBe None
      }
    }

    "enforce executing-component collection ownership" must
      _component_scope_metadata {
      "when either helper receives a root from another component" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R8,R14-R15; Example: E19; one unregistered root and one local successor collection"
        )
        val standardcapture = new OperationCapture
        val internalcapture = new OperationCapture
        val component = new TestComponent
        val foreigncollection =
          EntityCollectionId("test", "foreign", "foreign_root")
        val request =
          _request(rootcollection = foreigncollection)

        When("user and ServiceInternal helpers admit the typed request")
        val standardresult =
          new ConditionalTransitionCall(
            _core(component, standardcapture),
            request,
            serviceinternal = false
          ).execute()
        val internalresult =
          new ConditionalTransitionCall(
            _core(component, internalcapture),
            request,
            serviceinternal = true
          ).execute()

        Then("both helpers reject the request before constructing a UnitOfWork operation")
        _assert_component_scope_denial(standardresult)
        _assert_component_scope_denial(internalresult)
        standardcapture.transition shouldBe None
        internalcapture.transition shouldBe None
      }

      "when the foreign root has the local collection's logical name" in {
        Given(
          "Spec: docs/spec/entity-collection-identity.md; Rules: R1,R5; a foreign exact collection named dsl_root"
        )
        val standardcapture = new OperationCapture
        val internalcapture = new OperationCapture
        val component = new TestComponent
        val foreigncollection =
          EntityCollectionId("foreign", "phase52", _rootcollection.name)
        val request = _request(rootcollection = foreigncollection)

        When("the ActionCall helpers admit the same-name foreign root")
        val standardresult =
          new ConditionalTransitionCall(
            _core(component, standardcapture),
            request,
            serviceinternal = false
          ).execute()
        val internalresult =
          new ConditionalTransitionCall(
            _core(component, internalcapture),
            request,
            serviceinternal = true
          ).execute()

        Then("they retain the exact foreign collection and reject it before UnitOfWork routing")
        _assert_component_scope_denial(standardresult)
        _assert_component_scope_denial(internalresult)
        standardcapture.transition shouldBe None
        internalcapture.transition shouldBe None
      }

      "when either helper receives a successor from another component" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R8,R14-R15; Example: E19; one local root and one unregistered successor collection"
        )
        val standardcapture = new OperationCapture
        val internalcapture = new OperationCapture
        val component = new TestComponent
        val foreigncollection =
          EntityCollectionId("test", "foreign", "foreign_successor")
        val request =
          _request(successorcollection = foreigncollection)

        When("user and ServiceInternal helpers admit the typed request")
        val standardresult =
          new ConditionalTransitionCall(
            _core(component, standardcapture),
            request,
            serviceinternal = false
          ).execute()
        val internalresult =
          new ConditionalTransitionCall(
            _core(component, internalcapture),
            request,
            serviceinternal = true
          ).execute()

        Then("both helpers reject the request before constructing a UnitOfWork operation")
        _assert_component_scope_denial(standardresult)
        _assert_component_scope_denial(internalresult)
        standardcapture.transition shouldBe None
        internalcapture.transition shouldBe None
      }
    }

    "preserve exact aggregate root ownership" must
      _phase52_exact_identity_metadata {
      "when a foreign aggregate ID shares the local root name" in {
        Given("local and foreign exact root collections registered with the same logical name")
        val capture = new OperationCapture
        val component = new TestComponent
        var resolvercalls = 0
        component.aggregateSpace.register(
          _rootcollection.name,
          new AggregateCollection(new AggregateBuilder[Root] {
            def build(id: EntityId): Consequence[Root] = {
              resolvercalls += 1
              Consequence.success(Root(id, "resolved"))
            }
          })
        )
        val foreigncollection =
          EntityCollectionId("foreign", "phase52", _rootcollection.name)
        component.entitySpace.registerEntity(
          foreigncollection.name,
          _empty_collection(foreigncollection, _root_persistent)
        )
        val foreignid = EntityId("foreign", "same_local_root", foreigncollection)
        val core = _core(component, capture)
        val loadcall = new AggregateExactIdLoadCall(core, foreignid)
        val updatecall = new AggregateExactIdUpdateCall(core, foreignid)

        When("ActionCall aggregate load and update receive the foreign ID")
        val loaded = loadcall.execute()
        val updated = updatecall.execute()

        Then("both reject the ambiguous owner before resolver, action, or persistence")
        loaded shouldBe a[Consequence.Failure[_]]
        updated shouldBe a[Consequence.Failure[_]]
        resolvercalls shouldBe 0
        updatecall.actionRan shouldBe false
      }
    }

    "reject noncanonical aggregate record IDs" must
      _phase52_exact_identity_metadata {
      "when persistence receives malformed or legacy scalar IDs" in {
        Given("a component whose local aggregate root storage is empty")
        val capture = new OperationCapture
        val component = new TestComponent
        val core = _core(component, capture)
        val malformed =
          new AggregateRecordCreateCall(
            core,
            Record.dataAuto(
              "id" -> "single-global-entity-facility-0-stable",
              "status" -> "invalid"
            )
          )
        val legacy =
          new AggregateRecordCreateCall(
            core,
            Record.dataAuto("id" -> "legacy_scalar", "status" -> "invalid")
          )

        When("two aggregate create actions return scalar ID records")
        val malformedresult = malformed.execute()
        val legacyresult = legacy.execute()

        Then("both fail at canonical ID admission before writing the root storage")
        malformedresult shouldBe a[Consequence.Failure[_]]
        legacyresult shouldBe a[Consequence.Failure[_]]
        malformed.actionRan shouldBe true
        legacy.actionRan shouldBe true
        component.rootStoreCount shouldBe 0
      }

      "when a returned record has an unsupported or foreign exact ID" in {
        Given("a component with an empty local aggregate root storage")
        val capture = new OperationCapture
        val component = new TestComponent
        val core = _core(component, capture)
        val foreigncollection =
          EntityCollectionId("foreign", "phase52", _rootcollection.name)
        val unsupported =
          new AggregateRecordCreateCall(
            core,
            Record.dataAuto("id" -> 42, "status" -> "invalid")
          )
        val foreign =
          new AggregateRecordCreateCall(
            core,
            Record.dataAuto(
              "id" -> EntityId("foreign", "wrong_owner", foreigncollection).value,
              "status" -> "invalid"
            )
          )

        When("aggregate create actions return unsupported and foreign exact IDs")
        val unsupportedresult = unsupported.execute()
        val foreignresult = foreign.execute()

        Then("both reject the present IDs before root storage is written")
        unsupportedresult shouldBe a[Consequence.Failure[_]]
        foreignresult shouldBe a[Consequence.Failure[_]]
        unsupported.actionRan shouldBe true
        foreign.actionRan shouldBe true
        component.rootStoreCount shouldBe 0
      }

      "when a create record omits its ID and a later update does the same" in {
        Given("an aggregate root codec that assigns an exact ID during creation")
        val capture = new OperationCapture
        val component = new TestComponent(_generated_root_persistent)
        val core = _core(component, capture, _generated_root_persistent)
        val create =
          new AggregateRecordCreateCall(
            core,
            Record.dataAuto("status" -> "created")
          )
        val update =
          new AggregateRecordUpdateCall(
            core,
            _generated_root_id,
            Record.dataAuto("status" -> "updated")
          )

        When("the create and update actions return records without ID fields")
        val createresult = create.execute()
        val updateresult = update.execute()

        Then("creation accepts the missing ID but update rejects it before a second write")
        createresult shouldBe a[Consequence.Success[_]]
        updateresult shouldBe a[Consequence.Failure[_]]
        create.actionRan shouldBe true
        update.actionRan shouldBe true
        component.rootStoreCount shouldBe 1
      }
    }

    "preserve the actual ActionCall to provider CallTree" in {
      Given(
        "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rule: R20; one seeded root and an enabled ActionCall CallTree"
      )
      val capture = new OperationCapture
      val component = new TestComponent
      val core = _core(component, capture)
      given ExecutionContext = core.executionContext
      core.executionContext.observability.callTreeContext.isEnabled shouldBe true
      val request = _request()
      val root = Root(request.rootId, "open")
      val record =
        EntityConcurrencyMetadata.initializeForCreate(
          _root_persistent.toStoreRecord(root)
        )
      core.executionContext.dataStoreSpace
        .inject(DataStore.CollectionId.EntityStore(_rootcollection), record)
        .TAKE
      ObservabilityEngine.clearExecutionHistory()

      try {
        When("ActionEngine executes the FunctionalActionCall")
        val result =
          component.core.actionEngine.execute(
            new ConditionalTransitionCall(
              core,
              request,
              serviceinternal = true
            )
          )

        Then("the real CallTree contains every execution boundary")
        result shouldBe a[Consequence.Success[?]]
        val rendered =
          ObservabilityEngine
            .executionHistory
            .lastOption
            .flatMap(_.calltree)
            .map(_.toRecord.print)
            .getOrElse(fail("conditional-transition CallTree missing"))
        rendered should include("action:org.goldenport.cncf.test.Phase49Dsl.conditional_transition")
        rendered should include("uow:entitystore:conditional-transition")
        rendered should include("space:entitystore:conditional-transition")
        rendered should include(
          "space:datastore:entity-conditional-transition"
        )
        rendered should not include "created"
      } finally
        ObservabilityEngine.clearExecutionHistory()
    }
  }

  private final class OperationCapture {
    private var _transition:
        Option[UnitOfWorkOp.EntityStoreConditionalTransition[?, ?, ?]] =
      None
    private var _snapshotload:
        Option[UnitOfWorkOp.EntityStoreLoadDetached[?]] =
      None

    def transition:
        Option[UnitOfWorkOp.EntityStoreConditionalTransition[?, ?, ?]] =
      _transition

    def snapshotLoad:
        Option[UnitOfWorkOp.EntityStoreLoadDetached[?]] =
      _snapshotload

    def interpreter(
      context: => ExecutionContext
    ): UnitOfWorkOp ~> Consequence =
      new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = {
          operation match {
            case transition:
                UnitOfWorkOp.EntityStoreConditionalTransition[?, ?, ?] =>
              _transition = Some(transition)
            case snapshotload: UnitOfWorkOp.EntityStoreLoadDetached[?] =>
              _snapshotload = Some(snapshotload)
            case _ =>
              ()
          }
          new org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter(
            new UnitOfWork(context)
          ).interpret(operation)
        }
      }
  }

  private final class ConditionalTransitionCall(
    val core: ActionCall.Core,
    request: EntityConditionalTransition[Root, RootPatch, Successor],
    serviceinternal: Boolean
  ) extends FunctionalActionCall
      with ActionCall.Core.Holder {
    protected def build_Program: ExecUowM[OperationResponse] = {
      val transition =
        if (serviceinternal)
          entity_conditional_transition_internal(request)
        else
          entity_conditional_transition(request)
      transition.map(_ => OperationResponse.Void())
    }
  }

  private final class InternalSnapshotLoadCall(
    val core: ActionCall.Core,
    id: EntityId
  ) extends FunctionalActionCall
      with ActionCall.Core.Holder {
    private var _loadedrevision: Option[EntityRevision] =
      None

    def loadedRevision: Option[EntityRevision] =
      _loadedrevision

    protected def build_Program: ExecUowM[OperationResponse] =
      entity_load_detached_internal[Root](id)(using _root_persistent).map { carrier =>
        _loadedrevision = Some(carrier.revision)
        OperationResponse.Void()
      }
  }

  private final class AggregateExactIdLoadCall(
    val core: ActionCall.Core,
    id: EntityId
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] =
      aggregate_load_c[Root](id).map(root => OperationResponse.RecordResponse(root.toRecord()))
  }

  private final class AggregateExactIdUpdateCall(
    val core: ActionCall.Core,
    id: EntityId
  ) extends ProcedureActionCall {
    private var _actionran = false

    def actionRan: Boolean = _actionran

    override def execute(): Consequence[OperationResponse] =
      aggregate_update_c("dsl_root", id, "updateRoot", {
        _actionran = true
        Consequence.success(Root(id, "updated"))
      }).map(root => OperationResponse.RecordResponse(root.toRecord()))
  }

  private final class AggregateRecordCreateCall(
    val core: ActionCall.Core,
    record: Record
  ) extends ProcedureActionCall {
    private var _actionran = false

    def actionRan: Boolean = _actionran

    override def execute(): Consequence[OperationResponse] =
      aggregate_create_c("dsl_root", "createRoot", {
        _actionran = true
        Consequence.success(AggregateRecord(record))
      }).map(_ => OperationResponse.Void())
  }

  private final class AggregateRecordUpdateCall(
    val core: ActionCall.Core,
    id: EntityId,
    record: Record
  ) extends ProcedureActionCall {
    private var _actionran = false

    def actionRan: Boolean = _actionran

    override def execute(): Consequence[OperationResponse] =
      aggregate_update_c("dsl_root", id, "updateRoot", {
        _actionran = true
        Consequence.success(AggregateRecord(record))
      }).map(_ => OperationResponse.Void())
  }

  private final class TestComponent(
    rootpersistent: EntityPersistent[Root] = _root_persistent
  ) extends Component {
    override val core: Component.Core = Component.Core.create(
      "org.goldenport.cncf.test.Phase49Dsl",
      ComponentId("org.goldenport.cncf.test.Phase49Dsl"),
      ComponentInstanceId.default(ComponentId("org.goldenport.cncf.test.Phase49Dsl")),
      Protocol.empty
    )

    override def coreOption: Option[Component.Core] =
      Some(core)

    entitySpace.registerEntity(
      _rootcollection.name,
      _empty_collection(_rootcollection, rootpersistent)
    )
    entitySpace.registerEntity(
      _successorcollection.name,
      _empty_collection(_successorcollection, _successor_persistent)
    )

    def rootStoreCount: Int =
      entitySpace
        .entityOption(_rootcollection)
        .map(
          _.asInstanceOf[EntityCollection[Root]].storage.storeRealm.values.size
        )
        .getOrElse(0)
  }

  private def _core(
    component: Component,
    capture: OperationCapture,
    rootpersistent: EntityPersistent[Root] = _root_persistent
  ): ActionCall.Core = {
    val datastorespace = DataStoreSpace.default()
    val entitystorespace =
      new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val observability = ObservabilityContext(
      traceId = TraceId("test", "action_call_conditional_transition"),
      spanId = None,
      correlationId = None,
      callTreeContext = CallTreeContext.enabled
    )
    lazy val context: ExecutionContext =
      ExecutionContext.create(runtime) match {
        case instance: ExecutionContext.Instance =>
          instance.copy(
            cncfCore = instance.cncfCore.copy(
              observability = instance.observability.copy(
                callTreeContext = CallTreeContext.enabled
              )
            )
          )
        case value =>
          value
      }
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "action-call-conditional-transition-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = None,
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = capture.interpreter(context),
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "action-call-conditional-transition-runtime"
    )
    val action = new CommandAction {
      override def createCall(core: ActionCall.Core): ActionCall =
        throw new UnsupportedOperationException("not used")

      override def request: Request =
        Request(
          component = Some("org.goldenport.cncf.test.Phase49Dsl"),
          service = None,
          operation = "conditional_transition",
          arguments = Nil,
          switches = Nil,
          properties = Nil
        )
    }
    EntityRevisionSpecSupport.registerRevisionBinding(
      context,
      _rootcollection,
      rootpersistent,
      EntityRevisionRepresentation.Detached
    )
    EntityRevisionSpecSupport.registerRevisionBinding(
      context,
      _successorcollection,
      _successor_persistent,
      EntityRevisionRepresentation.Detached
    )
    ActionCall.Core(action, context, Some(component), None)
  }

  private def _authorization_shape(
    operation:
      Option[UnitOfWorkOp.EntityStoreConditionalTransition[?, ?, ?]]
  ): Vector[EntityAccessMode] =
    operation.toVector.flatMap { transition =>
      Vector(
        transition.rootReadAuthorization,
        transition.rootUpdateAuthorization,
        transition.successorAuthorization
      ).flatten.map(_.accessMode)
    }

  private def _authorization_without_mode(
    operation:
      Option[UnitOfWorkOp.EntityStoreConditionalTransition[?, ?, ?]]
  ) =
    operation.toVector.flatMap { transition =>
      Vector(
        transition.rootReadAuthorization,
        transition.rootUpdateAuthorization,
        transition.successorAuthorization
      ).flatten.map(_.copy(accessMode = EntityAccessMode.UserPermission))
    }

  private def _assert_component_scope_denial(
    result: Consequence[OperationResponse]
  ): Unit =
    result match {
      case Consequence.Failure(conclusion) =>
        val diagnostic = ConclusionDiagnostics.classify(conclusion)
        diagnostic.webStatus shouldBe 403
        diagnostic.causeKind shouldBe Some(Cause.Kind.Guard.name)
        diagnostic.diagnosticKey shouldBe "cross_component"
        diagnostic.reason shouldBe
          Some("conditional-transition-component-scope")
        diagnostic.guard shouldBe Some("cross-component")
      case _ =>
        fail("expected structured component-scope denial")
    }

  private def _request(
    rootcollection: EntityCollectionId = _rootcollection,
    successorcollection: EntityCollectionId = _successorcollection
  ):
      EntityConditionalTransition[Root, RootPatch, Successor] = {
    val rootid = EntityId("test", "dsl_root", rootcollection)
    val successorid =
      EntityId("test", "dsl_successor", successorcollection)
    val field =
      EntityTransitionField
        .exact[Root, String]("status", _root_persistent)
        .TAKE
    val expectation =
      EntityTransitionDefinition
        .create(_root_persistent, Vector(field))
        .flatMap(_.expectation(
          EntityRevision.INITIAL,
          field.expected("open").TAKE
        ))
        .TAKE
    val successor =
      EntitySuccessorIntent
        .create[Successor, Successor](
          Successor(successorid, "created")
        )(using _successor_create, _successor_persistent)
        .TAKE
    EntityConditionalTransition
      .create(
        rootid,
        expectation,
        RootPatch("claimed"),
        successor
      )(using _root_patch_for(rootcollection))
      .TAKE
  }

  private def _empty_collection[E](
    collectionid: EntityCollectionId,
    persistent: EntityPersistent[E]
  ): EntityCollection[E] = {
    given EntityPersistent[E] = persistent
    val storerealm = new EntityRealm[E](
      entityName = collectionid.name,
      loader = EntityLoader[E](_ => None),
      state = new IdRef(EntityRealmState(Map.empty))
    )
    val descriptor = EntityDescriptor(
      collectionId = collectionid,
      plan = EntityRuntimePlan(
        entityName = collectionid.name,
        memoryPolicy = EntityMemoryPolicy.StoreOnly,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byEntityId,
        maxPartitions = 1,
        maxEntitiesPerPartition = 1
      ),
      persistent = persistent,
      revisionBinding = Some(
        EntityRevisionBinding(EntityRevisionRepresentation.Detached)
      )
    )
    new EntityCollection(
      descriptor,
      EntityStorage(storerealm)
    )
  }

  private final case class Root(
    id: EntityId,
    status: String
  ) extends EntityPersistable {
    def toRecord(): Record =
      Record.dataAuto("id" -> id, "status" -> status)
  }

  private final case class AggregateRecord(
    record: Record
  ) extends org.goldenport.record.RecordPresentable {
    def toRecord(): Record = record
  }

  private final case class RootPatch(
    status: String
  )

  private final case class Successor(
    id: EntityId,
    label: String
  )

  private val _root_persistent: EntityPersistent[Root] =
    new EntityPersistent[Root] {
      def id(entity: Root): EntityId = entity.id
      def toRecord(entity: Root): Record =
        entity.toRecord()
      def fromRecord(record: Record): Consequence[Root] =
        (record.getAs[EntityId]("id"), record.getString("status")) match {
          case (Some(id), Some(status)) =>
            Consequence.success(Root(id, status))
          case _ =>
            Consequence.argumentInvalid("root", "id and status", record)
        }
    }

  private val _generated_root_id =
    EntityId("test", "generated_root", _rootcollection)

  private val _generated_root_persistent: EntityPersistent[Root] =
    new EntityPersistent[Root] {
      def id(entity: Root): EntityId = entity.id
      def toRecord(entity: Root): Record = entity.toRecord()
      def fromRecord(record: Record): Consequence[Root] =
        record.getString("status")
          .map(status => Consequence.success(Root(_generated_root_id, status)))
          .getOrElse(Consequence.argumentInvalid("generatedRoot", "status", record))
    }

  private def _root_patch_for(
    collectionid: EntityCollectionId
  ): EntityPersistentUpdate[RootPatch] =
    new EntityPersistentUpdate[RootPatch] {
      def collection(entity: RootPatch): EntityCollectionId = {
        val _ = entity
        collectionid
      }
      def toRecord(entity: RootPatch): Record =
        Record.dataAuto("status" -> entity.status)
      def fromRecord(record: Record): Consequence[RootPatch] =
        record.getString("status")
          .map(status => Consequence.success(RootPatch(status)))
          .getOrElse(
            Consequence.argumentInvalid("rootPatch", "status", record)
          )
    }

  private val _successor_persistent: EntityPersistent[Successor] =
    new EntityPersistent[Successor] {
      def id(entity: Successor): EntityId = entity.id
      def toRecord(entity: Successor): Record =
        Record.dataAuto("id" -> entity.id, "label" -> entity.label)
      def fromRecord(record: Record): Consequence[Successor] =
        (record.getAs[EntityId]("id"), record.getString("label")) match {
          case (Some(id), Some(label)) =>
            Consequence.success(Successor(id, label))
          case _ =>
            Consequence.argumentInvalid("successor", "id and label", record)
        }
    }

  private val _successor_create: EntityPersistentCreate[Successor] =
    EntityPersistentCreate.fromPersistent(_successor_persistent)

  private final class IdRef[A](initial: A) extends Ref[Id, A] {
    private var _value: A = initial

    def get: A =
      synchronized(_value)

    def set(value: A): Unit =
      synchronized {
        _value = value
      }

    override def getAndSet(value: A): A =
      synchronized {
        val previous = _value
        _value = value
        previous
      }

    def access: (A, A => Boolean) =
      synchronized {
        val snapshot = _value
        val setter: A => Boolean = next =>
          synchronized {
            if (_value == snapshot) {
              _value = next
              true
            } else {
              false
            }
          }
        snapshot -> setter
      }

    override def tryUpdate(f: A => A): Boolean =
      synchronized {
        _value = f(_value)
        true
      }

    override def tryModify[B](f: A => (A, B)): Option[B] =
      synchronized {
        val (next, result) = f(_value)
        _value = next
        Some(result)
      }

    def update(f: A => A): Unit =
      synchronized {
        _value = f(_value)
      }

    def modify[B](f: A => (A, B)): B =
      synchronized {
        val (next, result) = f(_value)
        _value = next
        result
      }

    override def modifyState[B](state: State[A, B]): B =
      synchronized {
        val (next, result) = state.run(_value).value
        _value = next
        result
      }

    override def tryModifyState[B](state: State[A, B]): Option[B] =
      synchronized {
        val (next, result) = state.run(_value).value
        _value = next
        Some(result)
      }
  }
}
