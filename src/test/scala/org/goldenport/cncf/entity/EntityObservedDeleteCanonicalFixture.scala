package org.goldenport.cncf.entity

import cats.~>
import cats.data.State
import cats.effect.Ref
import cats.free.Free
import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.*
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection}
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.cncf.unitofwork.*
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.simplemodeling.model.directive.Update
import org.simplemodeling.model.value.SecurityAttributes

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
private[cncf] object EntityObservedDeleteCanonicalFixture {
  val instant: Instant = Instant.parse("2026-10-06T03:04:05Z")
  val clock: Clock = Clock.fixed(instant, ZoneOffset.UTC)
  val overflowCollection: DataStore.CollectionId = DataStore.CollectionId("cncf_content_body_overflow")
  val body: String = "canonical overflow body " * 300

  final class CanonicalId(collectionId: EntityCollectionId, token: String)
      extends EntityId("single", "global", collectionId, Some(instant), Some(token))
  final case class StoredEntity(id: EntityId, title: String, revision: EntityRevision) extends EntityPersistable {
    def toRecord(): Record = Record.dataAuto("id" -> id, "title" -> title, "revision" -> revision.value)
  }
  final case class Draft(title: String, content: String, id: Option[EntityId] = None)
  final case class Patch(title: Update[String])
  final case class Snapshot(root: Option[Map[String, Any]], overflow: Option[Map[String, Any]],
    unrelatedRoot: Option[Map[String, Any]], unrelatedOverflow: Option[Map[String, Any]])

  def revision(value: Long): EntityRevision = success(EntityRevision.createC(value))
  def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => throw new IllegalStateException(conclusion.display)
  }
  def reason(result: Consequence[?]): Option[String] = result match {
    case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collectFirst {
      case Descriptor.Facet.Reason(value) => value
    }
    case _ => None
  }
  def symptom(result: Consequence[?]): Option[Taxonomy.Symptom] = result match {
    case Consequence.Failure(conclusion) => Some(conclusion.observation.taxonomy.symptom)
    case _ => None
  }

  def fixture(
    token: String = "canonical",
    logical: Boolean = false,
    representation: EntityRevisionRepresentation = EntityRevisionRepresentation.Detached,
    provider: DataStore = new EntityObservedDeleteCanonicalProviderFixture.MemoryStore(),
    initialRevision: Long = 1L,
    principal: String = "owner",
    denyFactory: Boolean = false,
    managed: Boolean = true,
    sideProvider: Option[DataStore] = None
  ): Fixture = new Fixture(token, logical, representation, provider, initialRevision, principal, denyFactory, managed, sideProvider)

  final class Fixture(token: String, logical: Boolean, representation: EntityRevisionRepresentation,
    val provider: DataStore, initialRevision: Long, principal: String, denyFactory: Boolean, managed: Boolean,
    sideProvider: Option[DataStore]) {
    val collectionId: EntityCollectionId = EntityCollectionId("test", "canonical", token)
    val id: EntityId = new CanonicalId(collectionId, "selected")
    val unrelatedId: EntityId = new CanonicalId(collectionId, "unselected")
    val revisionField: String = representation.storageFieldName
    private val _datastores = new DataStoreSpace().useDataStore(provider)
    sideProvider.foreach(_datastores.addDataStore)
    private val _entitystores = new EntityStoreSpace().addEntityStore(EntityStore.standard())
    private var _factory_calls = Vector.empty[UnitOfWorkAuthorization]
    private val _factory = new Component.Factory {
      protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = comp.core
      protected def create_Component(params: ComponentCreate): Component = new Component() {}
      override def authorizeUnitOfWork(authorization: UnitOfWorkAuthorization, uow: UnitOfWork): Option[Consequence[Unit]] = {
        _factory_calls = _factory_calls :+ authorization
        if (denyFactory) Some(Consequence.securityPermissionDenied("canonical-delete-factory-denied")) else None
      }
    }
    val component: Component = new Component() {
      override def factoryOption: Option[Component.Factory] = Some(_factory)
    }
    private val _observability = ObservabilityContext(TraceId("test", "canonical_delete"), None,
      Some(CorrelationId("test", "canonical_delete")))
    private val _rootscope = ScopeContext(ScopeKind.Runtime, "canonical-root", None, _observability)
    private val _componentscope = Component.Context("canonical-component", _rootscope, component, ComponentOrigin.Embed)
    private lazy val _runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(kind = ScopeKind.Runtime, name = "canonical-runtime", parent = Some(_componentscope),
        observabilityContext = _observability, httpDriverOption = None,
        datastore = Some(DataStoreContext(_datastores)), entitystore = Some(EntityStoreContext(_entitystores))),
      unitofworksupplier = () => new UnitOfWork(context),
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = new UnitOfWorkInterpreter(new UnitOfWork(context)).interpret(operation)
      }, commitaction = _ => (), abortaction = _ => (), disposeaction = _ => (), token = "canonical-runtime")
    lazy val context: ExecutionContext = {
      val base = ExecutionContext.create(_runtime).asInstanceOf[ExecutionContext.Instance]
      val subject = new Principal {
        def id: PrincipalId = PrincipalId(principal)
        def attributes: Map[String, String] = Map.empty
      }
      base.copy(core = base.core.copy(clock = clock), cncfCore = base.cncfCore.copy(
        security = SecurityContext(subject, Set.empty, SecurityLevel("test"))))
    }
    val persistent: EntityPersistent[StoredEntity] = new EntityPersistent[StoredEntity] {
      def id(entity: StoredEntity): EntityId = entity.id
      def toRecord(entity: StoredEntity): Record = entity.toRecord()
      def fromRecord(record: Record): Consequence[StoredEntity] = for {
        identity <- record.getAs[EntityId]("id").map(Consequence.success).getOrElse(Consequence.argumentMissing("id"))
        title <- record.getString("title").map(Consequence.success).getOrElse(Consequence.argumentMissing("title"))
        version <- record.getAny("revision").map(EntityRevision.createC).getOrElse(Consequence.success(EntityRevision.INITIAL))
      } yield StoredEntity(identity, title, version)
    }
    val createPersistent: EntityPersistentCreate[Draft] = new EntityPersistentCreate[Draft] {
      def id(entity: Draft): Option[EntityId] = entity.id
      def collection(entity: Draft): EntityCollectionId = collectionId
      def toRecord(entity: Draft): Record = Record.dataAuto("title" -> entity.title, "content" -> entity.content)
    }
    val patchPersistent: EntityPersistentUpdate[Patch] = new EntityPersistentUpdate[Patch] {
      def collection(entity: Patch): EntityCollectionId = collectionId
      def toRecord(entity: Patch): Record = Record.dataAuto("title" -> entity.title)
      def fromRecord(record: Record): Consequence[Patch] = Consequence.operationInvalid("canonical-patch-is-write-only")
    }
    private val _realm = new EntityRealm[StoredEntity](collectionId.name, EntityLoader[StoredEntity](_ => None),
      new IdRef(EntityRealmState[StoredEntity](Map.empty)))(using persistent)
    val collection: EntityCollection[StoredEntity] = {
      given EntityPersistent[StoredEntity] = persistent
      val metadata = EntityRevisionModelMetadata(
        if (representation == EntityRevisionRepresentation.Embedded) EntityRevisionModelKind.SimpleEntity
        else EntityRevisionModelKind.NonSimpleEntity,
        if (managed) Some(representation) else None)
      val binding = if (managed) success(EntityRevisionBinding.resolve(metadata, Some(representation))) else None
      new EntityCollection(EntityDescriptor(collectionId,
        EntityRuntimePlan(entityName = collectionId.name, memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None, partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4, maxEntitiesPerPartition = 16, concurrencyPolicy = EntityConcurrencyPolicy.None),
        persistent, revisionBinding = binding), EntityStorage(_realm,
          Some(new PartitionedMemoryRealm[StoredEntity](strategy = PartitionStrategy.byOrganizationMonthUTC, idOf = _.id))))
    }
    component.entitySpace.registerEntity(collectionId.name, collection)
    if (managed) EntityRevisionSpecSupport.registerRevisionBinding(context, collectionId, persistent, representation)
    private var _view_builds = 0
    val view: ViewCollection[Vector[String]] = new ViewCollection(new ViewBuilder[Vector[String]] {
      def build(targetId: EntityId): Consequence[Vector[String]] = {
        _view_builds += 1
        visibleC.map(_.map(_.title))
      }
    })
    component.viewSpace.register(collectionId.name, view, Browser.from(view, _ => visibleC.map(xs => Vector(xs.map(_.title)))))

    def factoryCalls: Vector[UnitOfWorkAuthorization] = _factory_calls
    def viewBuilds: Int = _view_builds
    def dataCollection: DataStore.CollectionId = DataStore.CollectionId.EntityStore(collectionId)
    def overflowEntry(identity: EntityId): DataStore.EntryId = DataStore.StringEntryId(s"${identity.value}:content")
    def rawC(identity: EntityId = id, store: DataStore = provider): Consequence[Option[Record]] =
      store.load(dataCollection, DataStore.EntryId(identity))(using context)
    def snapshot(store: DataStore = provider): Snapshot = {
      def _row_(collection: DataStore.CollectionId, entry: DataStore.EntryId): Option[Map[String, Any]] =
        success((if (collection == overflowCollection) sideProvider.getOrElse(store) else store)
          .load(collection, entry)(using context)).map(_.asMap)
      Snapshot(_row_(dataCollection, DataStore.EntryId(id)), _row_(overflowCollection, overflowEntry(id)),
        _row_(dataCollection, DataStore.EntryId(unrelatedId)), _row_(overflowCollection, overflowEntry(unrelatedId)))
    }
    def seed(): Unit = {
      given ExecutionContext = context
      Vector(id -> initialRevision, unrelatedId -> 91L).foreach { case (identity, value) =>
        val source = Record.dataAuto("id" -> identity.value, "title" -> "Before", "business" -> "preserved",
          "content" -> body, revisionField -> value, "owner_id" -> "owner",
          "permission" -> SimpleEntityStorageShapePolicy.permissionJson(SecurityAttributes.ownedBy("owner").rights))
        val row = if (logical) source.upsertSingle("aliveness", "alive") else source
        val prepared = success(ContentBodyStoragePolicy.planForVersionedSave(identity, row))
        success(provider.create(dataCollection, DataStore.EntryId(identity), prepared.record))
        prepared.sideEffects.foreach {
          case EntityVersionedSideEffect.Save(cid, entry, record) => success(sideProvider.getOrElse(provider).create(cid, entry, record))
          case EntityVersionedSideEffect.Delete(cid, entry) => success(sideProvider.getOrElse(provider).delete(cid, entry))
        }
      }
      resetObservation()
    }
    def resetObservation(): Unit = provider match {
      case memory: EntityObservedDeleteCanonicalProviderFixture.MemoryStore => memory.resetObservation()
      case _ => ()
    }
    def primeResident(): Unit = collection.putScoped(StoredEntity(id, "Before", revision(initialRevision)))(using context)
    def authorization(identity: EntityId = id): UnitOfWorkAuthorization = UnitOfWorkAuthorization(
      resourceFamily = "domain", resourceType = Some("CanonicalEntity"), collectionName = Some(collectionId.name),
      targetId = Some(identity), accessKind = "delete", accessMode = EntityAccessMode.UserPermission,
      sourceComponentName = Some("canonical-component"), targetComponentName = Some("canonical-component"),
      entityNames = Vector("CanonicalEntity"))
    def deleteC(expected: EntityRevision = EntityRevision.INITIAL): Consequence[EntityDeletionResult] =
      context.entityStoreSpace.deleteObserved(UnitOfWorkOp.EntityStoreDeleteObserved(id, expected))(using context)
    def program(expected: EntityRevision = EntityRevision.INITIAL,
      grant: Option[UnitOfWorkAuthorization] = None): ExecUowM[EntityDeletionResult] =
      ConsequenceT.liftF(Free.liftF[UnitOfWorkOp, EntityDeletionResult](UnitOfWorkOp.EntityStoreDeleteObserved(id, expected, grant)))
    def evaluateC(program: ExecUowM[EntityDeletionResult]): Consequence[EntityDeletionResult] =
      new UnitOfWorkInterpreter(new UnitOfWork(context)).evaluateInActiveUnitOfWorkC(program)
    def updateC(title: String = "Concurrent", expected: EntityRevision = EntityRevision.INITIAL): Consequence[?] = {
      given ExecutionContext = context
      val patch = Patch(Update.set(title))
      if (representation == EntityRevisionRepresentation.Embedded)
        context.entityStoreSpace.updateByIdObserved(UnitOfWorkOp.EntityStoreUpdateByIdObserved(id, patch, expected, patchPersistent))
      else context.entityStoreSpace.updateByIdDetached(UnitOfWorkOp.EntityStoreUpdateByIdDetached(id, patch,
        Some(expected), patchPersistent, executionPolicy = EntityMutationExecutionPolicy(EntityConcurrencyPolicy.Optimistic)))
    }
    def createC(draft: Draft): Consequence[CreateResult[Draft]] =
      context.entityStoreSpace.create(UnitOfWorkOp.EntityStoreCreate(draft, createPersistent))(using context)
    def loadC(identity: EntityId = id): Consequence[Option[StoredEntity]] =
      context.entityStoreSpace.load(UnitOfWorkOp.EntityStoreLoad(identity, persistent))(using context)
    def visibleC: Consequence[Vector[StoredEntity]] =
      context.entityStoreSpace.search(UnitOfWorkOp.EntityStoreSearch(
        EntityQuery[StoredEntity](collectionId, Query(Record.empty), scope = EntitySearchScope.Store), persistent))(using context)
        .map(_.data.toVector)
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
