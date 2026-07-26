package org.goldenport.cncf.entity

import cats.~>
import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
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
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityManagedMutationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  "Embedded SimpleEntity managed revision" should {
    "initialize, return, advance, delete, and restore one authoritative value" in {
      Given(
        "Phase 50 ER-07; one Embedded Entity registered with an explicit Optimistic policy"
      )
      val fixture = _fixture(EntityConcurrencyPolicy.Optimistic)
      given ExecutionContext = fixture.context
      val id = _id("lifecycle")
      val created = fixture.store.create(CreateEntity(id, "created"))
      val loaded = created.flatMap(_ =>
        fixture.store.load[ManagedEntity](id)
      )

      When("managed update, soft delete, and restore execute")
      val updated = loaded.flatMap {
        case Some(entity) =>
          fixture.store.update(
            entity.copy(name = "updated"),
            None,
            EntityMutationExecutionPolicy.default
          )
        case None =>
          Consequence.entityNotFound(id.print)
      }
      val deleted = updated.flatMap(_ => fixture.store.delete(id))
      val deletedrevision = deleted.flatMap(_ =>
        _raw_record(fixture, id).flatMap(_required_revision)
      )
      val hidden = deleted.flatMap(_ =>
        fixture.store.load[ManagedEntity](id)
      )
      val restored = deleted.flatMap(_ => fixture.store.restore(id))
      val reloaded = restored.flatMap(_ =>
        fixture.store.load[ManagedEntity](id)
      )

      Then("the Entity codec sees revisions one through four and normal reads hide only the deleted interval")
      loaded.map(_.map(_.revision.value)) shouldBe
        Consequence.success(Some(1L))
      updated.map(_.entity.revision.value) shouldBe Consequence.success(2L)
      hidden shouldBe Consequence.success(None)
      deletedrevision.map(_.value) shouldBe Consequence.success(3L)
      reloaded.map(_.map(_.revision.value)) shouldBe
        Consequence.success(Some(4L))
      reloaded.map(_.map(_.name)) shouldBe
        Consequence.success(Some("updated"))
    }

    "reject application writes to the managed revision" in {
      Given(
        "Phase 50 ER-07; create and patch values that explicitly contain revision"
      )
      val fixture = _fixture(EntityConcurrencyPolicy.Optimistic)
      given ExecutionContext = fixture.context
      val createid = _id("managed-create")
      val detachedcreateid = _id("managed-create-detached-alias")
      val patchid = _id("managed-patch")
      val detachedpatchid = _id("managed-patch-detached-alias")
      val created = fixture.store.create(CreateEntity(patchid, "created"))
      val detachedcreated =
        fixture.store.create(
          CreateEntity(detachedpatchid, "created")
        )

      When("create and patch admission encounter either managed storage field")
      val rejectedcreate =
        fixture.store.create(
          CreateEntity(createid, "invalid", Some(EntityRevision.INITIAL))
        )
      val rejecteddetachedcreate =
        fixture.store.create(
          CreateEntity(
            detachedcreateid,
            "invalid",
            detachedRevision = Some(EntityRevision.INITIAL)
          )
        )
      val rejectedpatch = created.flatMap(_ =>
        fixture.store.updateById(
          patchid,
          ManagedPatch(
            Update.set("invalid"),
            Some(EntityRevision.INITIAL)
          ),
          None,
          EntityMutationExecutionPolicy.default
        )
      )
      val rejecteddetachedpatch = detachedcreated.flatMap(_ =>
        fixture.store.updateById(
          detachedpatchid,
          ManagedPatch(
            Update.set("invalid"),
            None,
            Some(EntityRevision.INITIAL)
          ),
          None,
          EntityMutationExecutionPolicy.default
        )
      )

      Then("all attempts fail before changing persistent state")
      rejectedcreate shouldBe a[Consequence.Failure[?]]
      rejecteddetachedcreate shouldBe a[Consequence.Failure[?]]
      rejectedpatch shouldBe a[Consequence.Failure[?]]
      rejecteddetachedpatch shouldBe a[Consequence.Failure[?]]
      _raw_record(fixture, createid) shouldBe Consequence.success(None)
      _raw_record(fixture, detachedcreateid) shouldBe
        Consequence.success(None)
      _raw_record(fixture, patchid)
        .map(_.flatMap(_.getString("name"))) shouldBe
        Consequence.success(Some("created"))
      _raw_record(fixture, patchid)
        .flatMap(_required_revision)
        .map(_.value) shouldBe Consequence.success(1L)
      _raw_record(fixture, detachedpatchid)
        .map(_.flatMap(_.getString("name"))) shouldBe
        Consequence.success(Some("created"))
      _raw_record(fixture, detachedpatchid)
        .flatMap(_required_revision)
        .map(_.value) shouldBe Consequence.success(1L)
    }

    "advance the managed revision for every System-admitted mutation route" in {
      Given(
        "Phase 50 ER-07; four embedded Entities admitted through internal System mutation routes"
      )
      val fixture = _fixture(EntityConcurrencyPolicy.Optimistic)
      given ExecutionContext = fixture.context
      val saveid = _id("system-save")
      val updateid = _id("system-update")
      val patchid = _id("system-update-by-id")
      val upsertid = _id("system-upsert")
      val admitted =
        for {
          _ <- fixture.store.create(CreateEntity(saveid, "created"))
          _ <- fixture.store.create(CreateEntity(updateid, "created"))
          _ <- fixture.store.create(CreateEntity(patchid, "created"))
          _ <- fixture.store.create(CreateEntity(upsertid, "created"))
          saved <- _required_entity(fixture, saveid)
          updated <- _required_entity(fixture, updateid)
        } yield saved -> updated

      When("save, update, update-by-id, and upsert execute without caller OCC input")
      val mutated = admitted.flatMap { case (saved, updated) =>
        for {
          _ <- fixture.store.save(saved.copy(name = "saved"))
          _ <- fixture.store.update(updated.copy(name = "updated"))
          _ <- fixture.store.updateByIdUnversioned(
            patchid,
            ManagedPatch(Update.set("patched"), None)
          )
          _ <- fixture.store.upsert(
            CreateEntity(upsertid, "upserted"),
            upsertid,
            EntityCreateOptions.default
          )(
            _ => Consequence.unit,
            _ => Consequence.unit
          )
        } yield ()
      }

      Then("all four routes keep one embedded revision and advance it once")
      mutated shouldBe Consequence.unit
      _stored_name_and_revision(fixture, saveid) shouldBe
        Consequence.success("saved" -> 2L)
      _stored_name_and_revision(fixture, updateid) shouldBe
        Consequence.success("updated" -> 2L)
      _stored_name_and_revision(fixture, patchid) shouldBe
        Consequence.success("patched" -> 2L)
      _stored_name_and_revision(fixture, upsertid) shouldBe
        Consequence.success("upserted" -> 2L)
    }

    "initialize the managed revision when System save admits a missing Entity" in {
      Given(
        "Phase 50 ER-07; one missing Embedded Entity submitted through the internal save route"
      )
      val fixture = _fixture(EntityConcurrencyPolicy.Optimistic)
      given ExecutionContext = fixture.context
      val id = _id("system-save-create")

      When("System save receives a value without caller-managed revision")
      val saved =
        fixture.store.save(SeedEntity(id, "seeded"))(
          using _seed_persistent,
          fixture.context
        )

      Then("the provider creates the Entity once and initializes revision one")
      saved shouldBe Consequence.unit
      _stored_name_and_revision(fixture, id) shouldBe
        Consequence.success("seeded" -> 1L)
    }

    "report the observed revision as the stale expected value" in {
      Given(
        "Phase 50 ER-04 and ER-07; persisted revision two and an ObservedRequired revision-one request"
      )
      val fixture = _fixture(EntityConcurrencyPolicy.Optimistic)
      given ExecutionContext = fixture.context
      val id = _id("observed-stale-diagnostic")
      val advanced =
        for {
          _ <- fixture.store.create(CreateEntity(id, "created"))
          entity <- _required_entity(fixture, id)
          _ <- fixture.store.update(
            entity.copy(name = "winner"),
            None,
            EntityMutationExecutionPolicy.default
          )
        } yield ()
      val observedpolicy = EntityMutationExecutionPolicy(
        concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
        preconditionPolicy = RevisionPreconditionPolicy.ObservedRequired,
        observedRevision = Some(EntityRevision.INITIAL)
      )

      When("the stale observed request reaches the provider comparison")
      val stale = advanced.flatMap(_ =>
        fixture.store.updateById(
          id,
          ManagedPatch(Update.set("stale"), None),
          None,
          observedpolicy
        )
      )

      Then("the structured failure reports expected one and actual two")
      val facets = stale match {
        case Consequence.Failure(conclusion) =>
          conclusion.observation.cause.descriptor.facets
        case other =>
          fail(s"stale failure expected: $other")
      }
      facets should contain(Descriptor.Facet.Expected(1L))
      facets should contain(Descriptor.Facet.Actual(2L))
      _stored_name_and_revision(fixture, id) shouldBe
        Consequence.success("winner" -> 2L)
    }

    "enforce Optimistic while allowing explicit None without disabling revision maintenance" in {
      Given(
        "Phase 50 ER-04 and ER-07; one Optimistic Entity and one None-policy Entity"
      )
      val optimistic = _fixture(EntityConcurrencyPolicy.Optimistic)
      val none = _fixture(EntityConcurrencyPolicy.None)
      val optimisticid = _id("optimistic")
      val noneid = _id("none")
      val optimisticresult = {
        given ExecutionContext = optimistic.context
        for {
          _ <- optimistic.store.create(CreateEntity(optimisticid, "created"))
          entity <- optimistic.store
            .load[ManagedEntity](optimisticid)
            .flatMap(value =>
              Consequence.successOrEntityNotFound(value)(optimisticid)
            )
          first <- optimistic.store.update(
            entity.copy(name = "winner"),
            None,
            EntityMutationExecutionPolicy.default
          )
          stale <- optimistic.store.update(
            entity.copy(name = "stale"),
            None,
            EntityMutationExecutionPolicy.default
          )
        } yield first -> stale
      }

      When("the None-policy mutation carries an obsolete embedded revision")
      val noneresult = {
        given ExecutionContext = none.context
        for {
          _ <- none.store.create(CreateEntity(noneid, "created"))
          entity <- none.store
            .load[ManagedEntity](noneid)
            .flatMap(value =>
              Consequence.successOrEntityNotFound(value)(noneid)
            )
          _ <- none.store.update(
            entity.copy(name = "first"),
            None,
            EntityMutationExecutionPolicy.default
          )
          second <- none.store.update(
            entity.copy(name = "last-write-wins"),
            None,
            EntityMutationExecutionPolicy.default
          )
        } yield second
      }
      val optimisticraw = {
        given ExecutionContext = optimistic.context
        _raw_record(optimistic, optimisticid)
      }

      Then("Optimistic rejects stale state while None applies and advances from the authoritative value")
      optimisticresult shouldBe a[Consequence.Failure[?]]
      optimisticraw
        .map(_.flatMap(_.getString("name"))) shouldBe
        Consequence.success(Some("winner"))
      noneresult.map(_.entity.name) shouldBe
        Consequence.success("last-write-wins")
      noneresult.map(_.revision.value) shouldBe Consequence.success(3L)
    }

    "never admit resident state in place of the datastore comparison" in {
      Given(
        "Phase 50 ER-07; one revision-one resident Entity whose datastore value has already advanced"
      )
      val fixture = _fixture(EntityConcurrencyPolicy.Optimistic)
      given ExecutionContext = fixture.context
      val id = _id("working-set")
      val admitted =
        fixture.store
          .create(CreateEntity(id, "created"))
          .flatMap(_ => fixture.store.load[ManagedEntity](id))
          .flatMap(value =>
            Consequence.successOrEntityNotFound(value)(id)
          )
      val winner = admitted.flatMap { entity =>
        fixture.collection.putScoped(entity)
        fixture.store.update(
          entity.copy(name = "winner"),
          None,
          EntityMutationExecutionPolicy.default
        )
      }
      val interpreter =
        new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))

      When("a revision-free UnitOfWork update carries the stale resident value")
      val stale = winner.flatMap(_ =>
        admitted.flatMap(entity =>
          interpreter.interpret(
            new UnitOfWorkOp.EntityStoreUpdate(
              entity.copy(name = "stale"),
              None,
              _persistent
            )
          )
        )
      )

      Then("the provider rejects it and UnitOfWork evicts the stale resident")
      stale shouldBe a[Consequence.Failure[?]]
      fixture.collection.residentCount shouldBe 0
      _raw_record(fixture, id)
        .map(_.flatMap(_.getString("name"))) shouldBe
        Consequence.success(Some("winner"))
      _raw_record(fixture, id)
        .flatMap(_required_revision)
        .map(_.value) shouldBe Consequence.success(2L)
    }

    "reject a request-level attempt to weaken the assembled concurrency policy" in {
      Given(
        "Phase 50 ER-04 and ER-07; one Optimistic runtime Entity and a caller-supplied None policy"
      )
      val fixture = _fixture(EntityConcurrencyPolicy.Optimistic)
      given ExecutionContext = fixture.context
      val id = _id("request-policy-bypass")
      val admitted =
        fixture.store
          .create(CreateEntity(id, "created"))
          .flatMap(_ => fixture.store.load[ManagedEntity](id))
          .flatMap(value =>
            Consequence.successOrEntityNotFound(value)(id)
          )
      val winner = admitted.flatMap(entity =>
        fixture.store.update(
          entity.copy(name = "winner"),
          None,
          EntityMutationExecutionPolicy.default
        )
      )
      val bypasspolicy = EntityMutationExecutionPolicy(
        concurrencyPolicy = EntityConcurrencyPolicy.None
      )

      When("the stale caller retries while requesting last-write-wins")
      val bypassed = winner.flatMap(_ =>
        admitted.flatMap(entity =>
          fixture.store.update(
            entity.copy(name = "bypass"),
            None,
            bypasspolicy
          )
        )
      )

      Then("EntityStore keeps the assembled Optimistic policy and rejects the stale mutation")
      bypassed shouldBe a[Consequence.Failure[?]]
      val diagnostic = bypassed match {
        case Consequence.Failure(conclusion) =>
          ConclusionDiagnostics.classify(conclusion)
        case other =>
          fail(s"stale failure expected: $other")
      }
      diagnostic.reason shouldBe Some("stale-entity-revision")
      diagnostic.policy shouldBe Some("entity.optimistic-concurrency")
      _raw_record(fixture, id)
        .map(_.flatMap(_.getString("name"))) shouldBe
        Consequence.success(Some("winner"))
      _raw_record(fixture, id)
        .flatMap(_required_revision)
        .map(_.value) shouldBe Consequence.success(2L)
    }

    "round-trip the authoritative revision through a fresh runtime instance" in {
      Given(
        "Phase 50 ER-07; one shared persistence provider and two independently constructed Entity runtimes"
      )
      val datastore = DataStore.inMemorySearchable()
      val writer = _fixture(
        EntityConcurrencyPolicy.Optimistic,
        datastore
      )
      val id = _id("runtime-restart")
      val created = {
        given ExecutionContext = writer.context
        writer.store.create(CreateEntity(id, "created"))
      }
      val reader = _fixture(
        EntityConcurrencyPolicy.Optimistic,
        datastore
      )

      When("a fresh runtime loads and updates the persisted Entity")
      val result = {
        given ExecutionContext = reader.context
        for {
          _ <- created
          loaded <- reader.store
            .load[ManagedEntity](id)
            .flatMap(value =>
              Consequence.successOrEntityNotFound(value)(id)
            )
          updated <- reader.store.update(
            loaded.copy(name = "after-restart"),
            None,
            EntityMutationExecutionPolicy.default
          )
        } yield loaded -> updated
      }

      Then("the fresh codec sees revision one and the provider advances it to two")
      result.map(_._1.revision.value) shouldBe Consequence.success(1L)
      result.map(_._2.revision.value) shouldBe Consequence.success(2L)
      result.map(_._2.entity.name) shouldBe
        Consequence.success("after-restart")
    }

    "preserve authoritative state across provider failure and UnitOfWork rollback" in {
      Given(
        "Phase 50 ER-07; one embedded Entity and a provider that fails before versioned publication"
      )
      val datastore = new FailingVersionedMutationDataStore()
      val fixture = _fixture(
        EntityConcurrencyPolicy.Optimistic,
        datastore
      )
      given ExecutionContext = fixture.context
      val id = _id("provider-rollback")
      val loaded =
        fixture.store
          .create(CreateEntity(id, "before"))
          .flatMap(_ => fixture.store.load[ManagedEntity](id))
          .flatMap(value =>
            Consequence.successOrEntityNotFound(value)(id)
          )
      val uow = new UnitOfWork(fixture.context)

      When("the mutation fails at publication and the surrounding UnitOfWork rolls back")
      val failed = loaded.flatMap(entity =>
        fixture.store.update(
          entity.copy(name = "candidate"),
          None,
          EntityMutationExecutionPolicy.default
        )
      )
      val rolledback = uow.rollback()

      Then("neither failure nor rollback advances revision or publishes the candidate")
      failed shouldBe a[Consequence.Failure[?]]
      rolledback shouldBe Consequence.unit
      _raw_record(fixture, id)
        .map(_.flatMap(_.getString("name"))) shouldBe
        Consequence.success(Some("before"))
      _raw_record(fixture, id)
        .flatMap(_required_revision)
        .map(_.value) shouldBe Consequence.success(1L)
    }
  }

  private val _collection_id =
    EntityCollectionId("test", "managed", "entity")

  private final case class ManagedEntity(
    id: EntityId,
    name: String,
    revision: EntityRevision
  )

  private final case class SeedEntity(
    id: EntityId,
    name: String
  )

  private final case class CreateEntity(
    id: EntityId,
    name: String,
    revision: Option[EntityRevision] = None,
    detachedRevision: Option[EntityRevision] = None
  )

  private final case class ManagedPatch(
    name: Update[String],
    revision: Option[EntityRevision],
    detachedRevision: Option[EntityRevision] = None
  )

  private val _persistent: EntityPersistent[ManagedEntity] =
    new EntityPersistent[ManagedEntity] {
      def id(entity: ManagedEntity): EntityId =
        entity.id
      def toRecord(entity: ManagedEntity): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "name" -> entity.name,
          "revision" -> entity.revision.value
        )
      def fromRecord(record: Record): Consequence[ManagedEntity] =
        for {
          id <- _entity_id(record)
          name <- _required_name(record)
          revision <- record
            .getAny("revision")
            .map(EntityRevision.createC)
            .getOrElse(Consequence.argumentMissing("revision"))
        } yield ManagedEntity(id, name, revision)
    }

  private val _seed_persistent: EntityPersistent[SeedEntity] =
    new EntityPersistent[SeedEntity] {
      def id(entity: SeedEntity): EntityId =
        entity.id
      def toRecord(entity: SeedEntity): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "name" -> entity.name
        )
      def fromRecord(record: Record): Consequence[SeedEntity] =
        for {
          id <- _entity_id(record)
          name <- _required_name(record)
        } yield SeedEntity(id, name)
    }

  private given EntityPersistentCreate[CreateEntity] =
    new EntityPersistentCreate[CreateEntity] {
      def id(entity: CreateEntity): Option[EntityId] =
        Some(entity.id)
      def collection(entity: CreateEntity): EntityCollectionId =
        entity.id.collection
      def toRecord(entity: CreateEntity): Record = {
        val base = Record.dataAuto(
          "id" -> entity.id,
          "name" -> entity.name
        )
        val embedded = entity.revision
          .map(value =>
            base ++ Record.dataAuto("revision" -> value.value)
          )
          .getOrElse(base)
        entity.detachedRevision
          .map(value =>
            embedded ++ Record.dataAuto("cncf_revision" -> value.value)
          )
          .getOrElse(embedded)
      }
    }

  private given EntityPersistent[ManagedEntity] =
    _persistent

  private given EntityPersistentUpdate[ManagedPatch] =
    new EntityPersistentUpdate[ManagedPatch] {
      def collection(entity: ManagedPatch): EntityCollectionId =
        _collection_id
      def toRecord(entity: ManagedPatch): Record = {
        val base = Record.dataAuto("name" -> entity.name)
        val embedded = entity.revision
          .map(value =>
            base ++ Record.dataAuto("revision" -> value.value)
          )
          .getOrElse(base)
        entity.detachedRevision
          .map(value =>
            embedded ++ Record.dataAuto("cncf_revision" -> value.value)
          )
          .getOrElse(embedded)
      }
      def fromRecord(record: Record): Consequence[ManagedPatch] =
        Consequence.argumentInvalid(
          "ManagedPatch decoding is not used"
        )
    }

  private final case class Fixture(
    store: EntityStore,
    datastore: DataStore,
    collection: EntityCollection[ManagedEntity],
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
            "injected managed mutation publication failure"
          )
        case _ =>
          Consequence.unit
      }
  }

  private def _fixture(
    policy: EntityConcurrencyPolicy,
    datastore: DataStore = DataStore.inMemorySearchable()
  ): Fixture = {
    val datastorespace = new DataStoreSpace().useDataStore(datastore)
    val store = EntityStore.standard()
    val entitystorespace =
      new EntityStoreSpace().addEntityStore(store)
    val entityspace = new EntitySpace()
    val realm = new EntityRealm[ManagedEntity](
      entityName = _collection_id.name,
      loader = EntityLoader[ManagedEntity](_ => None),
      state = new IdRef(
        EntityRealmState(Map.empty)
      )
    )
    val memoryrealm = new PartitionedMemoryRealm[ManagedEntity](
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
          revisionBinding = Some(
            EntityRevisionBinding(
              EntityRevisionRepresentation.Embedded
            )
          )
        ),
        EntityStorage(realm, Some(memoryrealm))
      )
    entityspace.registerEntity(
      _collection_id.name,
      collection
    )
    val observability = ObservabilityContext(
      traceId = TraceId("test", "entity_managed_mutation"),
      spanId = None,
      correlationId = None
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "entity-managed-mutation",
        parent = None,
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
      token = "entity-managed-mutation"
    )
    val _ = context
    Fixture(store, datastore, collection, context)
  }

  private def _id(
    entropy: String
  ): EntityId =
    EntityId(
      "test",
      "managed",
      _collection_id,
      entropy = Some(entropy)
    )

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

  private def _required_name(
    record: Record
  ): Consequence[String] =
    record
      .getString("name")
      .map(Consequence.success)
      .getOrElse(Consequence.argumentMissing("name"))

  private def _required_entity(
    fixture: Fixture,
    id: EntityId
  )(using ExecutionContext): Consequence[ManagedEntity] =
    fixture.store
      .load[ManagedEntity](id)
      .flatMap(value =>
        Consequence.successOrEntityNotFound(value)(id)
      )

  private def _stored_name_and_revision(
    fixture: Fixture,
    id: EntityId
  )(using ExecutionContext): Consequence[(String, Long)] =
    for {
      record <- _raw_record(fixture, id)
      name <- record
        .flatMap(_.getString("name"))
        .map(Consequence.success)
        .getOrElse(Consequence.argumentMissing("name"))
      revision <- _required_revision(record)
    } yield name -> revision.value

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

  private def _required_revision(
    record: Option[Record]
  ): Consequence[EntityRevision] =
    record
      .flatMap(_.getAny("revision"))
      .map(EntityRevision.createC)
      .getOrElse(Consequence.argumentMissing("revision"))

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
