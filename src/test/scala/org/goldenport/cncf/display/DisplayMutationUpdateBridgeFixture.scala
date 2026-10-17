package org.goldenport.cncf.display

import cats.~>
import cats.data.State
import cats.effect.Ref
import logicalui.TypedDisplayProjectionFixture
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.*
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection}
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.cncf.unitofwork.*
import org.goldenport.record.Record
import org.goldenport.schema.WebColumn
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.simplemodeling.model.directive.Update

/*
 * @since   Oct.  6, 2026
 * @version Oct. 14, 2026
 */
/** Development-only canonical Entity fixture; capture-detail supplies preparation metadata only. */
private[cncf] object DisplayMutationUpdateBridgeFixture {
  import DisplayMutation.*
  import DisplayMutationBinding.*
  final case class BridgeEntity(id: EntityId, title: String, description: String, revision: EntityRevision)
  final case class BridgePatch(title: Update[String], description: Update[String])
  val collectionId: EntityCollectionId = EntityCollectionId("test", "phase96", "bridge_item")
  val title: FieldLocator = FieldLocator("main", "title")
  val description: FieldLocator = FieldLocator("main", "description")
  val seed: BridgePatch = BridgePatch(Update.noop[String], Update.noop[String])
  val domainFailure: Consequence[Unit] = Consequence.argumentInvalid("title", "nonblank canonical title", "redacted")
  val transitionFailure: Consequence[Unit] = Consequence.operationInvalid("bridge-transition-rejected")
  val authorizationFailure: Consequence[Unit] = Consequence.securityPermissionDenied("bridge-update-denied")
  val definitions: GeneratedLogicalUiAbi.AdmittedDefinitions =
    success(GeneratedLogicalUiAbi.admitC(TypedDisplayProjectionFixture.definitions))
  val source: DisplayProtocol.Source = success(
    org.goldenport.cncf.component.ComponentId.parseC("org.goldenport.fixture.Bridge").flatMap(
      componentid => DisplayProtocol.Source.createC(componentid, "BridgeItem")))
  val persistent: EntityPersistent[BridgeEntity] = new EntityPersistent[BridgeEntity] {
    def id(entity: BridgeEntity): EntityId = entity.id
    def toRecord(entity: BridgeEntity): Record = Record.dataAuto("id" -> entity.id,
      "item_title" -> entity.title, "item_description" -> entity.description, "revision" -> entity.revision.value)
    def fromRecord(record: Record): Consequence[BridgeEntity] = for {
      id <- record.getAs[EntityId]("id").map(Consequence.success).getOrElse(Consequence.argumentMissing("id"))
      title <- record.getString("item_title").map(Consequence.success).getOrElse(Consequence.argumentMissing("item_title"))
      description <- record.getString("item_description").map(Consequence.success).getOrElse(Consequence.argumentMissing("item_description"))
      revision <- record.getAny("revision").map(EntityRevision.createC).getOrElse(Consequence.argumentMissing("revision"))
    } yield BridgeEntity(id, title, description, revision)
  }
  val createPersistent: EntityPersistentCreate[BridgeEntity] = new EntityPersistentCreate[BridgeEntity] {
    def id(entity: BridgeEntity): Option[EntityId] = Some(entity.id)
    def collection(entity: BridgeEntity): EntityCollectionId = entity.id.collection
    def toRecord(entity: BridgeEntity): Record = Record.dataAuto("id" -> entity.id,
      "item_title" -> entity.title, "item_description" -> entity.description,
      "created_at" -> "2026-10-06T00:00:00Z", "updated_at" -> "2026-10-06T00:00:00Z",
      "created_by" -> "bridge-owner", "updated_by" -> "bridge-owner")
  }
  def patchPersistent(collection: EntityCollectionId = collectionId,
    fieldName: String => String = name => name match {
      case "title" => "item_title"
      case "description" => "item_description"
      case other => other
    },
    storeRecord: BridgePatch => Record = patch => Record.dataAuto(
      "item_title" -> patch.title, "item_description" -> patch.description)): EntityPersistentUpdate[BridgePatch] = {
    val retainedcollection = collection
    new EntityPersistentUpdate[BridgePatch] {
      def collection(entity: BridgePatch): EntityCollectionId = retainedcollection
      override def storeFieldName(logicalName: String): String = fieldName(logicalName)
      def toRecord(entity: BridgePatch): Record = storeRecord(entity)
      def fromRecord(record: Record): Consequence[BridgePatch] = Consequence.operationInvalid("patch-decoding-not-supported")
    }
  }
  val patchCodec: EntityPersistentUpdate[BridgePatch] = patchPersistent()

  def binding(ownerId: String = "BridgeItem", aggregate: Boolean = false,
    sourceValue: DisplayProtocol.Source = source, titleField: String = "title",
    validatePreparation: Boolean = true): Binding[BridgePatch] = {
    val fields = Vector(
      FieldBinding[BridgePatch](title, "bridge.title",
        if (aggregate) UpdateTarget.Aggregate(ownerId, titleField) else UpdateTarget.Entity(ownerId, titleField),
        Vector(GeneratedLogicalUiAbi.ValueKind.Text), WebColumn.empty, (patch, value) => value match {
          case DisplayValue.Text(text) => Consequence.success(patch.copy(title = Update.set(text)))
          case _ => inputInvalidC("VALUE_KIND_INVALID", "title")
        }),
      FieldBinding[BridgePatch](description, "bridge.description", UpdateTarget.Entity(ownerId, "description"),
        Vector(GeneratedLogicalUiAbi.ValueKind.Text), WebColumn.empty, (patch, value) => value match {
          case DisplayValue.Text(text) => Consequence.success(patch.copy(description = Update.set(text)))
          case _ => inputInvalidC("VALUE_KIND_INVALID", "description")
        }))
    success(admitC(sourceValue, definitions, "capture-detail", "bridge-update-1",
      Capabilities(Support.Supported, Support.Supported, Support.Unsupported, Support.Supported, Support.Unsupported),
      fields, Set.empty, patch => {
        val invalid = Update.toChangesRecord(patchCodec.toStoreRecord(patch)).getString("item_title").exists(_.trim.isEmpty)
        if (validatePreparation && invalid) domainFailure.map(_ => patch) else Consequence.success(patch)
      }))
  }
  def registration(bindingValue: Binding[BridgePatch], codec: EntityPersistentUpdate[BridgePatch] = patchCodec)
      : DisplayMutationUpdateBridge.EntityUpdate[BridgePatch] = success(
    DisplayMutationUpdateBridge.entityC(bindingValue.selection, "BridgeItem", collectionId, Vector("title", "description"), codec))
  def prepareC(fixture: Fixture, bindingValue: Binding[BridgePatch], values: Vector[(FieldLocator, String)],
    observed: Long = 1, resourceId: Option[EntityId] = None, seedValue: BridgePatch = seed): Consequence[Prepared[BridgePatch]] = {
    val id = resourceId.getOrElse(fixture.id)
    val revision = success(EntityRevision.createC(observed))
    val transport = success(DisplayMutationTransport.bindC(Operation.Update,
      Vector("If-Match" -> EntityRevisionTransport.entityTag(revision))))
    bindingValue.prepareUpdateC(Request.Update(bindingValue.selection, id,
      values.map { case (location, text) => FieldInput(location, DisplayValue.Text(text)) }), seedValue,
      DisplayModel.Resource(id, revision), transport, Availability.enabled, () => Consequence.unit)
  }
  def authorization(id: EntityId = null): UnitOfWorkAuthorization = UnitOfWorkAuthorization(
    resourceFamily = "domain", resourceType = Some("BridgeItem"), collectionName = Some(collectionId.name),
    targetId = Option(id), accessKind = "update", sourceComponentName = Some(source.componentId.name),
    targetComponentName = Some(source.componentId.name), accessMode = EntityAccessMode.ServiceInternal)
  def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case _ => throw new IllegalStateException("total bridge fixture must succeed")
  }

  final class Fixture(initialTitle: String, initialDescription: String, denyAuthorization: Boolean, rejectTransition: Boolean) {
    val id: EntityId = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "bridge_item", collectionId, entropy = "bridge_item")
    val provider: RecordingProvider = new RecordingProvider
    private val _datastores = new DataStoreSpace().useDataStore(provider)
    private val _entitystores = new EntityStoreSpace().addEntityStore(EntityStore.standard())
    private val _authorizer = new Component.Factory {
      protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = comp.core
      protected def create_Component(params: ComponentCreate): Component = new Component() {}
      override def authorizeUnitOfWork(authorization: UnitOfWorkAuthorization, uow: UnitOfWork): Option[Consequence[Unit]] = {
        _authorization_calls += 1
        _last_authorization = Some(authorization)
        if (denyAuthorization) Some(authorizationFailure) else None
      }
    }
    val component: Component = new Component() {
      override def factoryOption: Option[Component.Factory] = Some(_authorizer)
    }
    private var _authorization_calls = 0
    private var _last_authorization: Option[UnitOfWorkAuthorization] = None
    private var _transition_calls = 0
    private val _hook = new TransitionValidationHook {
      def beforeSave[T](entity: T, tc: EntityPersistent[T])(using ExecutionContext): Consequence[Unit] = Consequence.unit
      def beforeUpdate[T](entity: T, tc: EntityPersistent[T])(using ExecutionContext): Consequence[Unit] = Consequence.unit
      def beforeUpdateById[P](id: EntityId, patch: P, tc: EntityPersistentUpdate[P])(using ExecutionContext): Consequence[Unit] = {
        _transition_calls += 1
        if (rejectTransition) transitionFailure
        else if (Update.toChangesRecord(tc.toStoreRecord(patch)).getString("item_title").exists(_.trim.isEmpty)) domainFailure
        else Consequence.unit
      }
    }
    private val _observability = ObservabilityContext(TraceId("test", "display_update_bridge"), None, None)
    private val _rootscope = ScopeContext(ScopeKind.Runtime, "bridge-root", None, _observability)
    private val _componentscope = Component.Context("bridge-component", _rootscope, component, ComponentOrigin.Embed)
    lazy val context: ExecutionContext = ExecutionContext.create(_runtime)
    private lazy val _runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(kind = ScopeKind.Runtime, name = "bridge-runtime", parent = Some(_componentscope),
        observabilityContext = _observability, httpDriverOption = None,
        datastore = Some(DataStoreContext(_datastores)), entitystore = Some(EntityStoreContext(_entitystores))),
      unitofworksupplier = () => new UnitOfWork(context),
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = new UnitOfWorkInterpreter(new UnitOfWork(context)).interpret(operation)
      }, commitaction = _ => (), abortaction = _ => (), disposeaction = _ => (), token = "bridge-runtime",
      transitionValidationHook = _hook)
    private val _realm = new EntityRealm[BridgeEntity](collectionId.name, EntityLoader[BridgeEntity](_ => None),
      new IdRef(EntityRealmState[BridgeEntity](Map.empty)))(using persistent)
    val collection: EntityCollection[BridgeEntity] = {
      given EntityPersistent[BridgeEntity] = persistent
      new EntityCollection(EntityDescriptor(collectionId,
        EntityRuntimePlan(entityName = collectionId.name, memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None, partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4, maxEntitiesPerPartition = 16, concurrencyPolicy = EntityConcurrencyPolicy.Optimistic),
        persistent, revisionBinding = Some(EntityRevisionBinding(EntityRevisionRepresentation.Embedded))),
        EntityStorage(_realm, Some(new PartitionedMemoryRealm[BridgeEntity](
          strategy = PartitionStrategy.byOrganizationMonthUTC, idOf = _.id))))
    }
    component.entitySpace.registerEntity(collectionId.name, collection)
    EntityRevisionSpecSupport.registerRevisionBinding(context, collectionId, persistent,
      EntityRevisionRepresentation.Embedded, EntityConcurrencyPolicy.Optimistic)
    private var _view_builds = 0
    val view: ViewCollection[Record] = new ViewCollection(new ViewBuilder[Record] {
      def build(targetId: EntityId): Consequence[Record] = {
        _view_builds += 1
        rawC.map(identity)
      }
    })
    component.viewSpace.register(collectionId.name, view, Browser.from(view, _ => rawC.map(Vector(_))))
    private val _initial = BridgeEntity(id, initialTitle, initialDescription, EntityRevision.INITIAL)
    locally {
      given ExecutionContext = context
      success(new UnitOfWorkInterpreter(new UnitOfWork(context)).interpret(UnitOfWorkOp.EntityStoreCreate(_initial, createPersistent)))
      provider.resetObservation()
      _authorization_calls = 0
    }
    def authorizationCalls: Int = _authorization_calls
    def lastAuthorization: Option[UnitOfWorkAuthorization] = _last_authorization
    def transitionCalls: Int = _transition_calls
    def viewBuilds: Int = _view_builds
    def rawC: Consequence[Record] = {
      given ExecutionContext = context
      provider.load(DataStore.CollectionId.EntityStore(collectionId), DataStore.EntryId(id))
        .flatMap(_.map(Consequence.success).getOrElse(Consequence.argumentMissing("bridge-item")))
    }
    def executeC(program: ExecUowM[EntityRecordSnapshot]): Consequence[EntityRecordSnapshot] = {
      val uow = new UnitOfWork(context)
      new UnitOfWorkInterpreter(uow).evaluateInActiveUnitOfWorkC(program).flatMap(snapshot => uow.commit().map(_ => snapshot))
    }
    def canonicalUpdateC(patch: BridgePatch, observed: Long = 1): Consequence[EntityRecordSnapshot] = {
      val revision = success(EntityRevision.createC(observed))
      val policy = EntityMutationExecutionPolicy(EntityConcurrencyPolicy.Optimistic, EntityWritePolicy.WriteIfChanged,
        RevisionPreconditionPolicy.ObservedRequired, Some(revision))
      val op: UnitOfWorkOp[EntityRecordSnapshot] = UnitOfWorkOp.EntityStoreUpdateByIdObserved(
        id, patch, revision, patchCodec, Some(authorization(id)), policy)
      executeC(org.goldenport.ConsequenceT.liftF(cats.free.Free.liftF(op)))
    }
  }
  def fixture(titleValue: String = "Before", descriptionValue: String = "Description",
    denyAuthorization: Boolean = false, rejectTransition: Boolean = false): Fixture =
    new Fixture(titleValue, descriptionValue, denyAuthorization, rejectTransition)

  /** Counts actual provider entry points and delegates all state to the real searchable provider. */
  final class RecordingProvider extends SearchableDataStore with EntityVersionedMutationDataStore with EntityCreateDataStore {
    private val _delegate = DataStore.inMemorySearchable()
    private val _versioned = _delegate.asInstanceOf[EntityVersionedMutationDataStore]
    private val _create = _delegate.asInstanceOf[EntityCreateDataStore]
    private var _attempts = 0
    private var _mutations = 0
    private var _loads = 0
    def attempts: Int = _attempts
    def mutations: Int = _mutations
    def loads: Int = _loads
    def resetObservation(): Unit = { _attempts = 0; _mutations = 0; _loads = 0 }
    def isAccept(cid: DataStore.CollectionId): Boolean = _delegate.isAccept(cid)
    def create(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] =
      _delegate.create(collection, id, record)
    def load(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Option[Record]] = {
      _loads += 1
      _delegate.load(collection, id)
    }
    def save(collection: DataStore.CollectionId, id: DataStore.EntryId, record: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _attempts += 1
      _delegate.save(collection, id, record).map { _ => _mutations += 1 }
    }
    def update(collection: DataStore.CollectionId, id: DataStore.EntryId, changes: Record)(using ctx: ExecutionContext): Consequence[Unit] = {
      _attempts += 1
      _delegate.update(collection, id, changes).map { _ => _mutations += 1 }
    }
    def delete(collection: DataStore.CollectionId, id: DataStore.EntryId)(using ctx: ExecutionContext): Consequence[Unit] = {
      _attempts += 1
      _delegate.delete(collection, id).map { _ => _mutations += 1 }
    }
    def search(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[SearchResult] = _delegate.search(collection, directive)
    def prepare(tx: org.goldenport.cncf.unitofwork.TransactionContext): PrepareResult = _delegate.prepare(tx)
    def commit(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = _delegate.commit(tx)
    def abort(tx: org.goldenport.cncf.unitofwork.TransactionContext): Unit = _delegate.abort(tx)
    override def entityMutationProviderCapabilities: EntityMutationProviderCapabilities = _versioned.entityMutationProviderCapabilities
    override def createEntity(plan: EntityCreatePlan)(using ctx: ExecutionContext): Consequence[EntityCreateResult] = {
      _attempts += 1
      _create.createEntity(plan).map { result =>
        _mutations += 1
        result
      }
    }
    override def mutateEntityDirect(plan: EntityDirectMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = {
      _attempts += 1
      _versioned.mutateEntityDirect(plan).map(_observe)
    }
    override def compareAndSetEntity(plan: EntityCompareAndSetMutationPlan)(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] = {
      _attempts += 1
      _versioned.compareAndSetEntity(plan).map(_observe)
    }
    def mutateVersionedEntity(plan: EntityVersionedMutationPlan)(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult] = {
      _attempts += 1
      _versioned.mutateVersionedEntity(plan).map { result =>
        result match { case _: EntityVersionedMutationResult.Applied => _mutations += 1; case _ => () }
        result
      }
    }
    private def _observe(result: EntityMutationProviderResult): EntityMutationProviderResult = {
      result match { case _: EntityMutationProviderResult.Applied => _mutations += 1; case _ => () }
      result
    }
  }
  private final class IdRef[A](initial: A) extends Ref[cats.Id, A] {
    private var _value: A = initial
    def get: A = synchronized(_value)
    def set(value: A): Unit = synchronized { _value = value }
    override def getAndSet(value: A): A = synchronized { val previous = _value; _value = value; previous }
    def access: (A, A => Boolean) = synchronized {
      val snapshot = _value
      (snapshot, next => synchronized { if (_value == snapshot) { _value = next; true } else false })
    }
    override def tryUpdate(f: A => A): Boolean = synchronized { _value = f(_value); true }
    override def tryModify[B](f: A => (A, B)): Option[B] = synchronized { Some(modify(f)) }
    def update(f: A => A): Unit = synchronized { _value = f(_value) }
    def modify[B](f: A => (A, B)): B = synchronized { val (next, result) = f(_value); _value = next; result }
    override def modifyState[B](state: State[A, B]): B = synchronized { val (next, result) = state.run(_value).value; _value = next; result }
    override def tryModifyState[B](state: State[A, B]): Option[B] = synchronized { Some(modifyState(state)) }
  }
}
