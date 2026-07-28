package org.goldenport.cncf.entity

import cats.data.State
import cats.effect.Ref
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentOrigin}
import org.goldenport.cncf.context.{
  DataStoreContext,
  EntitySpaceContext,
  EntityStoreContext,
  ExecutionContext,
  ObservabilityContext,
  RuntimeContext,
  ScopeContext,
  ScopeKind,
  TraceId
}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.datastore.{
  DataStore,
  DataStoreSpace,
  EntityVersionedMutationCheckpoint
}
import org.goldenport.cncf.entity.runtime.{
  EntityCollection,
  EntityDescriptor,
  EntityLoader,
  EntityMemoryPolicy,
  EntityRealm,
  EntityRealmState,
  EntityRuntimePlan,
  EntitySpace,
  EntityStorage,
  PartitionStrategy,
  PartitionedMemoryRealm
}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.unitofwork.{
  UnitOfWork,
  UnitOfWorkInterpreter,
  UnitOfWorkOp
}
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}
import org.simplemodeling.model.directive.Update

/*
 * @since   Jul. 25, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityDetachedRevisionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _optimistic_policy =
    EntityMutationExecutionPolicy(
      concurrencyPolicy = EntityConcurrencyPolicy.Optimistic
    )

  "Explicit detached non-SimpleEntity revision" should {
    "revision lifecycle and conflict handling" which {
    "manage one external revision while preserving the domain revision field" in {
      Given(
        "Phase 50 SE-05; an explicitly Detached non-SimpleEntity collection with an application-owned revision attribute"
      )
      val fixture = _fixture(
        Some(EntityRevisionRepresentation.Detached),
        EntityConcurrencyPolicy.Optimistic
      )
      given ExecutionContext = fixture.context
      val id = _id("detached-lifecycle")
      val entity = DetachedEntity(id, "created", "domain-r1")
      val created = fixture.store.create(entity)

      When(
        "the detached load, typed update, patch update, delete, and restore routes execute"
      )
      val initial = created.flatMap(_ =>
        fixture.store
          .loadDetached[DetachedEntity](id)
          .flatMap(_required_carrier(id, _))
      )
      val interpreter =
        new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
      val updated = initial.flatMap { carrier =>
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreSaveDetached(
            carrier.entity.copy(
              name = "typed",
              revision = "domain-r2"
            ),
            Some(carrier.revision),
            _persistent
          )
        )
      }
      val patched = updated.flatMap { carrier =>
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreUpdateByIdDetached(
            id,
            DetachedPatch(
              Update.set("patched"),
              Update.set("domain-r3")
            ),
            Some(carrier.revision),
            _patch_persistent
          )
        )
      }
      val deleted = patched.flatMap(_ => fixture.store.delete(id))
      val deletedrevision = deleted.flatMap(_ =>
        _raw_record(fixture, id).flatMap(_required_managed_revision)
      )
      val restored = deleted.flatMap(_ => fixture.store.restore(id))
      val reloaded = restored.flatMap(_ =>
        interpreter
          .interpret(
            UnitOfWorkOp.EntityStoreLoadDetached(id, _persistent)
          )
          .flatMap(_required_carrier(id, _))
      )

      Then(
        "the carrier advances independently and neither ordinary decoding nor the application revision field sees cncf_revision"
      )
      initial.map(_.revision.value) shouldBe Consequence.success(1L)
      initial.map(_.entity.revision) shouldBe
        Consequence.success("domain-r1")
      updated.map(_.revision.value) shouldBe Consequence.success(2L)
      patched.map(_.revision.value) shouldBe Consequence.success(3L)
      patched.map(_.entity.getString("revision")) shouldBe
        Consequence.success(Some("domain-r3"))
      patched.map(_.entity.getAny("cncf_revision")) shouldBe
        Consequence.success(None)
      deletedrevision.map(_.value) shouldBe Consequence.success(4L)
      reloaded.map(_.revision.value) shouldBe Consequence.success(5L)
      reloaded.map(_.entity) shouldBe
        Consequence.success(
          DetachedEntity(id, "patched", "domain-r3")
        )
      _raw_record(fixture, id)
        .map(_.flatMap(_.getString("revision"))) shouldBe
        Consequence.success(Some("domain-r3"))
      _raw_record(fixture, id)
        .map(_.flatMap(_.getAny("cncf_revision"))) shouldBe
        Consequence.success(Some(5L))
    }

    "reload an authoritative detached patch result before installing it in the working set" in {
      Given(
        "a Detached Entity collection whose ordinary patch result omits cncf_revision"
      )
      val fixture = _fixture(
        Some(EntityRevisionRepresentation.Detached),
        EntityConcurrencyPolicy.Optimistic
      )
      given ExecutionContext = fixture.context
      val id = _id("detached-ordinary-patch")
      val interpreter =
        new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
      val created =
        fixture.store.create(
          DetachedEntity(id, "before", "domain-r1")
        )

      When("the ordinary patch route updates the revision-managed Entity")
      val updated = created.flatMap(_ =>
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreUpdateById(
            id,
            DetachedPatch(
              Update.set("after"),
              Update.set("domain-r2")
            ),
            _patch_persistent
          )
        )
      )

      Then(
        "the domain result stays managed-field-free and the resident Entity is refreshed from persisted storage"
      )
      updated.map(_.getAny("cncf_revision")) shouldBe
        Consequence.success(None)
      fixture.collection.resolve(id) shouldBe
        Consequence.success(
          DetachedEntity(id, "after", "domain-r2")
        )
      _raw_record(fixture, id)
        .flatMap(_required_managed_revision)
        .map(_.value) shouldBe Consequence.success(2L)
    }

    "enforce stale conflicts only through detached-aware APIs" in {
      Given(
        "Phase 50 SE-05; one optimistic Detached Entity and its initial carrier"
      )
      val fixture = _fixture(
        Some(EntityRevisionRepresentation.Detached),
        EntityConcurrencyPolicy.Optimistic
      )
      given ExecutionContext = fixture.context
      val id = _id("detached-stale")
      val initial =
        fixture.store
          .create(DetachedEntity(id, "created", "domain"))
          .flatMap(_ =>
            fixture.store
              .loadDetached[DetachedEntity](id)
              .flatMap(_required_carrier(id, _))
          )
      val first = initial.flatMap { carrier =>
        fixture.store.updateDetached(
          carrier.entity.copy(name = "first"),
          Some(carrier.revision),
          _optimistic_policy
        )
      }

      When(
        "a second update reuses the stale carrier and standard Embedded APIs target the same collection"
      )
      val stale = initial.flatMap { carrier =>
        fixture.store.updateDetached(
          carrier.entity.copy(name = "stale"),
          Some(carrier.revision),
          _optimistic_policy
        )
      }
      val standardsnapshot =
        fixture.store.loadSnapshot[DetachedEntity](id)
      val standardupdate = first.flatMap { carrier =>
        fixture.store.update(
          carrier.entity.copy(name = "wrong-api"),
          Some(carrier.revision),
          _optimistic_policy
        )
      }
      val managedpatch = first.flatMap { carrier =>
        fixture.store.updateByIdDetached(
          id,
          Record.dataAuto("cncf_revision" -> 99L),
          Some(carrier.revision),
          _optimistic_policy
        )(using _record_patch_persistent, fixture.context)
      }

      Then(
        "stale and representation violations fail without modifying the authoritative record"
      )
      stale shouldBe a[Consequence.Failure[?]]
      standardsnapshot shouldBe a[Consequence.Failure[?]]
      standardupdate shouldBe a[Consequence.Failure[?]]
      managedpatch shouldBe a[Consequence.Failure[?]]
      _raw_record(fixture, id)
        .map(_.flatMap(_.getString("name"))) shouldBe
        Consequence.success(Some("first"))
      _raw_record(fixture, id)
        .flatMap(_required_managed_revision)
        .map(_.value) shouldBe Consequence.success(2L)
    }

    "derive optimistic preconditions for detached full-Entity mutations" in {
      Given(
        "an optimistic Detached Entity whose application does not carry the managed revision"
      )
      val fixture = _fixture(
        Some(EntityRevisionRepresentation.Detached),
        EntityConcurrencyPolicy.Optimistic
      )
      given ExecutionContext = fixture.context
      val id = _id("detached-derived-precondition")
      val created =
        fixture.store.create(
          DetachedEntity(id, "created", "domain-r1")
        )

      When("full save and update omit an application-supplied expected revision")
      val saved = created.flatMap(_ =>
        fixture.store.saveDetached(
          DetachedEntity(id, "saved", "domain-r2"),
          None,
          _optimistic_policy
        )
      )
      val updated = saved.flatMap(_ =>
        fixture.store.updateDetached(
          DetachedEntity(id, "updated", "domain-r3"),
          None,
          _optimistic_policy
        )
      )

      Then("the store derives each precondition from authoritative persisted metadata")
      saved.map(_.revision.value) shouldBe Consequence.success(2L)
      updated.map(value => value.entity.name -> value.revision.value) shouldBe
        Consequence.success("updated" -> 3L)
    }

    "leave undeclared non-SimpleEntity collections unmanaged" in {
      Given(
        "Phase 50 SE-05; the same non-SimpleEntity model without an explicit revision binding"
      )
      val fixture = _fixture(None, EntityConcurrencyPolicy.None)
      given ExecutionContext = fixture.context
      val id = _id("unmanaged")
      val interpreter =
        new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
      val created =
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(
            DetachedEntity(id, "created", "application-revision"),
            summon[EntityPersistentCreate[DetachedEntity]]
          )
        )
      val residentaftercreate =
        created.flatMap(_ => fixture.collection.resolve(id))

      When(
        "ordinary internal mutation and detached-aware routes target the collection"
      )
      val saved = created.flatMap(_ =>
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreSaveManaged(
            DetachedEntity(id, "saved", "application-r2"),
            _persistent
          )
        )
      )
      val updated = saved.flatMap(_ =>
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreUpdateById(
            id,
            DetachedPatch(
              Update.set("updated"),
              Update.set("application-r3")
            ),
            _patch_persistent
          )
        )
      )
      val loaded = updated.flatMap(_ =>
        fixture.store.load[DetachedEntity](id)
      )
      val detachedload =
        fixture.store.loadDetached[DetachedEntity](id)
      val detachedupdate = fixture.store.updateDetached(
        DetachedEntity(id, "rejected", "application-r3"),
        None,
        EntityMutationExecutionPolicy.default
      )

      Then(
        "ordinary persistence preserves domain data without creating cncf_revision and explicit Detached APIs reject the unmanaged collection"
      )
      loaded shouldBe
        Consequence.success(
          Some(DetachedEntity(id, "updated", "application-r3"))
        )
      saved shouldBe
        Consequence.success(
          DetachedEntity(id, "saved", "application-r2")
        )
      residentaftercreate shouldBe
        Consequence.success(
          DetachedEntity(id, "created", "application-revision")
        )
      _raw_record(fixture, id)
        .map(_.flatMap(_.getAny("cncf_revision"))) shouldBe
        Consequence.success(None)
      detachedload shouldBe a[Consequence.Failure[?]]
      detachedupdate shouldBe a[Consequence.Failure[?]]
    }

    }

    "read and provider boundary behavior" which {
    "reject malformed detached revision metadata on every ordinary read route" in {
      Given(
        "Phase 50 SE-05; a persisted Detached record whose cncf_revision is fractional"
      )
      val fixture = _fixture(
        Some(EntityRevisionRepresentation.Detached),
        EntityConcurrencyPolicy.Optimistic
      )
      given ExecutionContext = fixture.context
      val id = _id("malformed-detached-revision")
      val seeded = fixture.context.dataStoreSpace.inject(
        DataStore.CollectionId.EntityStore(_collection_id),
        Record.dataAuto(
          "id" -> id,
          "name" -> "malformed",
          "revision" -> "domain",
          "cncf_revision" -> BigDecimal("1.5")
        )
      )

      When("ordinary load and search decode the persisted record")
      val loaded = seeded.flatMap(_ =>
        fixture.store.load[DetachedEntity](id)
      )
      val searched = seeded.flatMap(_ =>
        fixture.context.entityStoreSpace.search(
          UnitOfWorkOp.EntityStoreSearch(
            EntityQuery[DetachedEntity](
              _collection_id,
              Query(Record.empty)
            ),
            _persistent
          )
        )
      )

      Then("both routes reject the invalid managed revision before domain decoding")
      loaded shouldBe a[Consequence.Failure[?]]
      searched shouldBe a[Consequence.Failure[?]]
    }

    "preserve committed state across provider failure and fresh runtime loading" in {
      Given(
        "Phase 50 SE-05; a Detached Entity in a provider that fails before atomic publication"
      )
      val datastore = new FailingVersionedMutationDataStore
      val fixture = _fixture(
        Some(EntityRevisionRepresentation.Detached),
        EntityConcurrencyPolicy.Optimistic,
        datastore
      )
      given ExecutionContext = fixture.context
      val id = _id("detached-rollback")
      val created =
        fixture.store.create(
          DetachedEntity(id, "committed", "domain")
        )
      val carrier = created.flatMap(_ =>
        fixture.store
          .loadDetached[DetachedEntity](id)
          .flatMap(_required_carrier(id, _))
      )

      When(
        "the provider rejects a managed mutation and a fresh EntityStore runtime loads the same datastore"
      )
      val failed = carrier.flatMap { value =>
        fixture.store.updateDetached(
          value.entity.copy(name = "must-not-publish"),
          Some(value.revision),
          _optimistic_policy
        )
      }
      val restarted = _fixture(
        Some(EntityRevisionRepresentation.Detached),
        EntityConcurrencyPolicy.Optimistic,
        datastore
      )
      val loadedafterrestart =
        restarted.store.loadDetached[DetachedEntity](id)(using
          _persistent,
          restarted.context
        )

      Then(
        "the failed mutation is rolled back and the detached metadata remains authoritative after restart"
      )
      failed shouldBe a[Consequence.Failure[?]]
      loadedafterrestart
        .flatMap(_required_carrier(id, _))
        .map(value => value.entity.name -> value.revision.value) shouldBe
        Consequence.success("committed" -> 1L)
    }

    "allow None concurrency policy without weakening explicit representation selection" in {
      Given(
        "Phase 50 SE-05; a Detached collection whose mutation policy is None"
      )
      val fixture = _fixture(
        Some(EntityRevisionRepresentation.Detached),
        EntityConcurrencyPolicy.None
      )
      given ExecutionContext = fixture.context
      val id = _id("detached-none")
      val initial =
        fixture.store
          .create(DetachedEntity(id, "created", "domain"))
          .flatMap(_ =>
            fixture.store
              .loadDetached[DetachedEntity](id)
              .flatMap(_required_carrier(id, _))
          )
      val first = initial.flatMap { carrier =>
        fixture.store.updateDetached(
          carrier.entity.copy(name = "first"),
          Some(carrier.revision),
          EntityMutationExecutionPolicy.default
        )
      }

      When("a stale expected revision is submitted under policy None")
      val second = initial.flatMap { stale =>
        fixture.store.updateDetached(
          stale.entity.copy(name = "second"),
          Some(stale.revision),
          EntityMutationExecutionPolicy.default
        )
      }

      Then("the write is admitted and the managed revision still advances")
      first.map(_.revision.value) shouldBe Consequence.success(2L)
      second.map(value => value.entity.name -> value.revision.value) shouldBe
        Consequence.success("second" -> 3L)
    }

    "reject a typed codec that decodes an Entity into another collection" in {
      Given("one persisted Entity and a caller codec that returns a foreign Entity identity")
      val fixture = _fixture(
        representation = None,
        policy = EntityConcurrencyPolicy.None
      )
      given ExecutionContext = fixture.context
      val id = _id("typed-codec-boundary")
      fixture.store.create(DetachedEntity(id, "stored", "domain")).TAKE
      val foreigncollection =
        EntityCollectionId("test", "detached", "foreign")
      val foreignpersistent = new EntityPersistent[DetachedEntity] {
        def id(entity: DetachedEntity): EntityId =
          entity.id

        def toRecord(entity: DetachedEntity): Record =
          _persistent.toRecord(entity)

        def fromRecord(record: Record): Consequence[DetachedEntity] =
          _persistent.fromRecord(record).map(entity =>
            entity.copy(
              id = entity.id.copy(collection = foreigncollection)
            )
          )

        override def fromStoreRecord(
          context: EntityStoreDecodeContext,
          record: Record
        ): Consequence[DetachedEntity] =
          fromStoreRecord(record).flatMap { entity =>
            EntityPersistent.restoreCollectionIdentity(
              entity,
              entity.id,
              context.owningCollectionId
            )(id => entity.copy(id = id))
          }
      }

      When("the EntityStoreSpace decodes the requested record through that caller codec")
      val result = fixture.context.entityStoreSpace.load(
        UnitOfWorkOp.EntityStoreLoad(id, foreignpersistent)
      )

      Then("the typed load fails before a foreign Entity can cross the storage boundary")
      result match {
        case Consequence.Failure(conclusion) =>
          val diagnostic = ConclusionDiagnostics.classify(conclusion)
          val facets = conclusion.observation.cause.descriptor.facets
          diagnostic.causeKind shouldBe Some("inconsistency")
          diagnostic.policy shouldBe Some("entity.persistence.collection")
          diagnostic.reason shouldBe Some("entity-codec-collection-mismatch")
          facets should contain(Descriptor.Facet.Expected(id.collection.print))
          facets should contain(Descriptor.Facet.Actual(foreigncollection.print))
        case Consequence.Success(_) =>
          fail("A foreign Entity identity must not cross the storage boundary")
      }
    }

    "reject a custom store result owned by another same-name namespace" in {
      Given(
        "a custom EntityStore that returns the requested Entity type under a different exact owner"
      )
      val requestedid = _id("typed-store-owner-alias")
      val foreigncollection =
        EntityCollectionId("provider", "alias", _collection_id.name)
      val foreignentity =
        DetachedEntity(
          requestedid.copy(collection = foreigncollection),
          "foreign",
          "domain"
        )
      val store = new NoopEntityStore() {
        override def load[T](
          id: EntityId
        )(using
          tc: EntityPersistent[T],
          ctx: ExecutionContext
        ): Consequence[Option[T]] =
          Consequence.success(
            Some(foreignentity.asInstanceOf[T])
          )
      }
      val storespace =
        new EntityStoreSpace().addEntityStore(store)
      given ExecutionContext = ExecutionContext.create()

      When("the custom provider result crosses the EntityStoreSpace load boundary")
      val result = storespace.load(
        UnitOfWorkOp.EntityStoreLoad(requestedid, _persistent)
      )

      Then("the same logical name does not satisfy exact collection ownership")
      result match {
        case Consequence.Failure(conclusion) =>
          conclusion.display should include(requestedid.collection.print)
          conclusion.display should include(foreigncollection.print)
        case Consequence.Success(_) =>
          fail("A custom store result from another exact owner must be rejected")
      }
    }
    }
  }

  private val _collection_id =
    EntityCollectionId("test", "detached", "entity")

  private final case class DetachedEntity(
    id: EntityId,
    name: String,
    revision: String
  )

  private final case class DetachedPatch(
    name: Update[String],
    revision: Update[String]
  )

  private val _persistent: EntityPersistent[DetachedEntity] =
    new EntityPersistent[DetachedEntity] {
      def id(entity: DetachedEntity): EntityId =
        entity.id

      def toRecord(entity: DetachedEntity): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "name" -> entity.name,
          "revision" -> entity.revision
        )

      def fromRecord(record: Record): Consequence[DetachedEntity] =
        if (record.getAny("cncf_revision").isDefined)
          Consequence.argumentPolicyViolation(
            "record",
            "detached-revision-codec-isolation",
            "domain record without cncf_revision",
            "cncf_revision"
          )
        else
          for {
            id <- _entity_id(record)
            name <- _required_string(record, "name")
            revision <- _required_string(record, "revision")
          } yield DetachedEntity(id, name, revision)
    }

  private given EntityPersistent[DetachedEntity] =
    _persistent

  private given EntityPersistentCreate[DetachedEntity] =
    new EntityPersistentCreate[DetachedEntity] {
      def id(entity: DetachedEntity): Option[EntityId] =
        Some(entity.id)

      def collection(entity: DetachedEntity): EntityCollectionId =
        entity.id.collection

      def toRecord(entity: DetachedEntity): Record =
        _persistent.toRecord(entity)
    }

  private val _patch_persistent: EntityPersistentUpdate[DetachedPatch] =
    new EntityPersistentUpdate[DetachedPatch] {
      def collection(entity: DetachedPatch): EntityCollectionId =
        _collection_id

      def toRecord(entity: DetachedPatch): Record =
        Record.dataAuto(
          "name" -> entity.name,
          "revision" -> entity.revision
        )

      def fromRecord(record: Record): Consequence[DetachedPatch] =
        Consequence.argumentInvalid(
          "DetachedPatch decoding is not used"
        )
    }

  private val _record_patch_persistent: EntityPersistentUpdate[Record] =
    new EntityPersistentUpdate[Record] {
      def collection(entity: Record): EntityCollectionId =
        _collection_id

      def toRecord(entity: Record): Record =
        entity

      def fromRecord(record: Record): Consequence[Record] =
        Consequence.success(record)
    }

  private final case class Fixture(
    store: EntityStore,
    datastore: DataStore,
    collection: EntityCollection[DetachedEntity],
    context: ExecutionContext
  )

  private final class FailingVersionedMutationDataStore
      extends DataStore.InMemoryDataStore(
        org.goldenport.cncf.unitofwork.CommitRecorder.noop
      ) {
    override protected def versioned_mutation_checkpoint(
      checkpoint: EntityVersionedMutationCheckpoint
    ): Consequence[Unit] =
      checkpoint match {
        case EntityVersionedMutationCheckpoint.BeforePublish =>
          Consequence.dataStoreUnavailable(
            "injected detached mutation publication failure"
          )
        case _ =>
          Consequence.unit
      }
  }

  private def _fixture(
    representation: Option[EntityRevisionRepresentation],
    policy: EntityConcurrencyPolicy,
    datastore: DataStore = DataStore.inMemorySearchable()
  ): Fixture = {
    val datastorespace = new DataStoreSpace().useDataStore(datastore)
    val store = EntityStore.standard()
    val entitystorespace =
      new EntityStoreSpace().addEntityStore(store)
    val component = new Component() {}
    val entityspace = component.entitySpace
    val realm = new EntityRealm[DetachedEntity](
      entityName = _collection_id.name,
      loader = EntityLoader[DetachedEntity](_ => None),
      state = new IdRef(
        EntityRealmState(Map.empty)
      )
    )
    val memoryrealm = new PartitionedMemoryRealm[DetachedEntity](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val collection =
      new EntityCollection(
        EntityDescriptor(
          collectionId = _collection_id,
          plan = EntityRuntimePlan(
            entityName = _collection_id.name,
            memoryPolicy = EntityMemoryPolicy.LoadToMemory,
            workingSet = None,
            partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
            maxPartitions = 1,
            maxEntitiesPerPartition = 16,
            concurrencyPolicy = policy
          ),
          persistent = _persistent,
          revisionBinding = representation.map(EntityRevisionBinding.apply)
        ),
        EntityStorage(realm, Some(memoryrealm))
      )
    entityspace.registerEntity(
      _collection_id.name,
      collection
    )
    val observability = ObservabilityContext(
      traceId = TraceId("test", "entity_detached_revision"),
      spanId = None,
      correlationId = None
    )
    val rootscope = ScopeContext(
      ScopeKind.Runtime,
      "entity-detached-revision-root",
      None,
      observability
    )
    val componentscope = Component.Context(
      "entity-detached-revision-component",
      rootscope,
      component,
      ComponentOrigin.Embed
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "entity-detached-revision",
        parent = Some(componentscope),
        observabilityContext = observability,
        httpDriverOption = None,
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace)),
        entityspace = Some(EntitySpaceContext(entityspace))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] =
          new UnitOfWorkInterpreter(new UnitOfWork(context))
            .interpret(operation)
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "entity-detached-revision"
    )
    val _ = context
    Fixture(store, datastore, collection, context)
  }

  private def _id(
    entropy: String
  ): EntityId =
    EntityId(
      "test",
      "detached",
      _collection_id,
      entropy = Some(entropy)
    )

  private def _required_carrier[A](
    id: EntityId,
    carrier: Option[EntityRevisionCarrier[A]]
  ): Consequence[EntityRevisionCarrier[A]] =
    carrier
      .map(Consequence.success)
      .getOrElse(Consequence.entityNotFound(id.print))

  private def _raw_record(
    fixture: Fixture,
    id: EntityId
  )(using ExecutionContext): Consequence[Option[Record]] =
    fixture.context.entityStoreSpace
      .dataStoreCollection(id)
      .flatMap(collection =>
        fixture.context.entityStoreSpace
          .dataStoreEntryId(id)
          .flatMap(entry =>
            fixture.datastore.load(collection, entry)
          )
      )

  private def _required_managed_revision(
    record: Option[Record]
  ): Consequence[EntityRevision] =
    record
      .flatMap(_.getAny("cncf_revision"))
      .map(EntityRevision.createC)
      .getOrElse(Consequence.argumentMissing("cncf_revision"))

  private def _entity_id(
    record: Record
  ): Consequence[EntityId] =
    record.getAny("id") match {
      case Some(value: EntityId) =>
        Consequence.success(value)
      case Some(value: String) =>
        EntityId.parse(value)
      case other =>
        Consequence.argumentInvalid("id", "EntityId", other)
    }

  private def _required_string(
    record: Record,
    name: String
  ): Consequence[String] =
    record
      .getString(name)
      .map(Consequence.success)
      .getOrElse(Consequence.argumentMissing(name))

  private final class IdRef[A](
    initial: A
  ) extends Ref[cats.Id, A] {
    private var _value: A = initial

    def get: A = synchronized {
      _value
    }

    def set(value: A): Unit = synchronized {
      _value = value
    }

    override def getAndSet(value: A): A = synchronized {
      val previous = _value
      _value = value
      previous
    }

    def access: (A, A => Boolean) = synchronized {
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

    override def tryUpdate(f: A => A): Boolean = synchronized {
      _value = f(_value)
      true
    }

    override def tryModify[B](f: A => (A, B)): Option[B] = synchronized {
      val (next, result) = f(_value)
      _value = next
      Some(result)
    }

    def update(f: A => A): Unit = synchronized {
      _value = f(_value)
    }

    def modify[B](f: A => (A, B)): B = synchronized {
      val (next, result) = f(_value)
      _value = next
      result
    }

    override def modifyState[B](state: State[A, B]): B = synchronized {
      val (next, result) = state.run(_value).value
      _value = next
      result
    }

    override def tryModifyState[B](
      state: State[A, B]
    ): Option[B] = synchronized {
      val (next, result) = state.run(_value).value
      _value = next
      Some(result)
    }
  }
}
