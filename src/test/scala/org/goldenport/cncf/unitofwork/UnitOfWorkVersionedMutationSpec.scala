package org.goldenport.cncf.unitofwork

import cats.~>
import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentOrigin}
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
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkVersionedMutationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _collectionid =
    EntityCollectionId("test", "phase49", "versioned_person")

  "UnitOfWork Entity versioned mutation" should {
    "decode an embedded create result before installing it in the working set" in {
      Given(
        "an Embedded Entity collection with a memory realm and a create payload without managed revision"
      )
      val fixture            = _fixture()
      given ExecutionContext = fixture.context
      val id                 = EntityId("test", "created", _collectionid)
      val interpreter =
        new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))

      When("the create result crosses the UnitOfWork working-set boundary")
      val created = interpreter.interpret(
        UnitOfWorkOp.EntityStoreCreate(
          VersionedPersonCreate(id, "created"),
          _create_persistent
        )
      )

      Then(
        "the persisted Embedded revision is decoded and the domain Entity becomes resident"
      )
      created.map(_.record.flatMap(_.getAny("revision"))) shouldBe
        Consequence.success(Some(1L))
      fixture.collection.resolve(id) shouldBe
        Consequence.success(VersionedPerson(id, "created"))
    }

    "return and install the authoritative snapshot only after provider success" in {
      Given("one persisted Entity and its component-scoped working set")
      val fixture            = _fixture()
      given ExecutionContext = fixture.context
      val id                 = EntityId("test", "authoritative", _collectionid)
      val initial            = VersionedPerson(id, "before")
      val _ = fixture.datastorespace.inject(
        DataStore.CollectionId.EntityStore(_collectionid),
        _persistent.toStoreRecord(initial)
      )
      fixture.collection.put(initial)
      val interpreter =
        new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))

      When("the snapshot load and full save cross the UnitOfWork boundary")
      val admitted = interpreter.interpret(
        UnitOfWorkOp.EntityStoreLoadSnapshot(id, _persistent)
      ).flatMap(value =>
        Consequence.successOrEntityNotFound(value)(id)
      )
      val saved = admitted.flatMap(snapshot =>
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreSave(
            VersionedPerson(id, "after"),
            snapshot.revision,
            _persistent
          )
        )
      )

      Then("the provider result advances once and becomes the resident value")
      saved.map(_.entity.name) shouldBe Consequence.success("after")
      saved.map(_.revision) should not be admitted.map(_.revision)
      fixture.collection.resolve(id).map(_.name) shouldBe
        Consequence.success("after")
    }

    "evict a stale resident without replacing authoritative storage" in {
      Given("two candidates admitted from the same Entity snapshot")
      val fixture            = _fixture()
      given ExecutionContext = fixture.context
      val id                 = EntityId("test", "stale", _collectionid)
      val initial            = VersionedPerson(id, "before")
      val _ = fixture.datastorespace.inject(
        DataStore.CollectionId.EntityStore(_collectionid),
        _persistent.toStoreRecord(initial)
      )
      fixture.collection.put(initial)
      val interpreter =
        new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
      val admitted = interpreter.interpret(
        UnitOfWorkOp.EntityStoreLoadSnapshot(id, _persistent)
      ).flatMap(value =>
        Consequence.successOrEntityNotFound(value)(id)
      )
      val first = admitted.flatMap(snapshot =>
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreUpdate(
            VersionedPerson(id, "first"),
            snapshot.revision,
            _persistent
          )
        )
      )

      When("the second candidate reuses the stale expectation")
      val stale = first.flatMap(_ =>
        admitted.flatMap(snapshot =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdate(
              VersionedPerson(id, "stale"),
              snapshot.revision,
              _persistent
            )
          )
        )
      )

      Then("the conflict is preserved and the stale resident is removed")
      stale shouldBe a[Consequence.Failure[_]]
      fixture.collection.resolve(id) shouldBe a[Consequence.Failure[_]]
      fixture.entitystorespace
        .loadSnapshot(id, _persistent)
        .map(_.map(_.entity.name)) shouldBe
        Consequence.success(Some("first"))
    }

    "evict resident state when projection fails after the provider committed" in {
      Given("a versioned mutation whose result decoder fails after datastore success")
      val fixture            = _fixture()
      given ExecutionContext = fixture.context
      val id                 = EntityId("test", "projection_failure", _collectionid)
      val initial            = VersionedPerson(id, "before")
      val _ = fixture.datastorespace.inject(
        DataStore.CollectionId.EntityStore(_collectionid),
        _persistent.toStoreRecord(initial)
      )
      fixture.collection.put(initial)
      var projectedname = "before"
      var querycount    = 0
      val viewcollection =
        new ViewCollection[Record](new ViewBuilder[Record] {
          def build(targetId: EntityId): Consequence[Record] =
            Consequence.success(
              Record.dataAuto("id" -> targetId, "name" -> projectedname)
            )
        })
      fixture.component.viewSpace.register(
        _collectionid.name,
        viewcollection,
        Browser.from(
          viewcollection,
          _ => {
            querycount += 1
            Consequence.success(
              Vector(Record.dataAuto("id" -> id, "name" -> projectedname))
            )
          }
        )
      )
      val primed = fixture.component.viewSpace
        .browser[Record](_collectionid.name)
        .query(Query(Record.empty))
      primed shouldBe a[Consequence.Success[?]]
      querycount shouldBe 1
      val interpreter =
        new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
      val admitted = fixture.entitystorespace
        .loadSnapshot(id, _persistent)
        .flatMap(value => Consequence.successOrEntityNotFound(value)(id))
      val failingpersistent = new EntityPersistent[VersionedPerson] {
        def id(entity: VersionedPerson): EntityId = entity.id
        def toRecord(entity: VersionedPerson): Record =
          _persistent.toRecord(entity)
        override def toStoreRecord(entity: VersionedPerson): Record =
          _persistent.toStoreRecord(entity)
        def fromRecord(record: Record): Consequence[VersionedPerson] =
          Consequence.operationInvalid("projection decoder failed")
      }

      When("the provider applies the mutation but authoritative hydration fails")
      projectedname = "committed"
      val result = admitted.flatMap { snapshot =>
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreSave(
            VersionedPerson(id, "committed"),
            snapshot.revision,
            failingpersistent
          )
        )
      }

      Then("the structured failure reports committed projection and evicts resident state")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(conclusion) =>
          ConclusionDiagnostics.classify(conclusion).reason shouldBe
            Some("committed-entity-projection-failure")
        case _ =>
          fail("expected committed projection failure")
      }
      fixture.collection.resolve(id) shouldBe a[Consequence.Failure[_]]
      val refreshed = fixture.component.viewSpace
        .browser[Record](_collectionid.name)
        .query(Query(Record.empty))
      refreshed.map(_.headOption.flatMap(_.getString("name"))) shouldBe
        Consequence.success(Some("committed"))
      querycount shouldBe 2

      And("the committed datastore value remains authoritative and is not retried")
      fixture.entitystorespace
        .loadSnapshot(id, _persistent)
        .map(_.map(_.entity.name)) shouldBe
        Consequence.success(Some("committed"))
    }

    "reject an unversioned framework mutation without System admission" in {
      Given("an explicitly classified framework-bootstrap save")
      val fixture            = _fixture()
      given ExecutionContext = fixture.context
      val id                 = EntityId("test", "unversioned", _collectionid)
      val _ = fixture.datastorespace.inject(
        DataStore.CollectionId.EntityStore(_collectionid),
        _persistent.toStoreRecord(VersionedPerson(id, "before"))
      )
      val interpreter =
        new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))

      When("ServiceInternal access attempts the unversioned route")
      val result = interpreter.interpret(
        UnitOfWorkOp.EntityStoreSaveUnversioned(
          VersionedPerson(id, "after"),
          EntityUnversionedMutationPurpose.FrameworkBootstrap,
          _persistent,
          Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("VersionedPerson"),
              targetId = Some(id),
              accessKind = "update",
              accessMode = EntityAccessMode.ServiceInternal
            )
          )
        )
      )

      Then("the UnitOfWork boundary rejects the bypass before storage changes")
      result shouldBe a[Consequence.Failure[_]]
      _raw_record(fixture.datastorespace, id)
        .map(_.flatMap(_.getString("name"))) shouldBe
        Consequence.success(Some("before"))
      _raw_record(fixture.datastorespace, id)
        .map(
          _.flatMap(
            _.getAny(EntityConcurrencyMetadata.STORAGE_FIELD_NAME)
          )
        ) shouldBe Consequence.success(None)
    }
  }

  private final case class Fixture(
      datastorespace: DataStoreSpace,
      entitystorespace: EntityStoreSpace,
      collection: EntityCollection[VersionedPerson],
      component: Component,
      context: ExecutionContext
  )

  private def _fixture(): Fixture = {
    val datastorespace = DataStoreSpace.default()
    val entitystorespace =
      new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val component                           = new Component() {}
    given EntityPersistent[VersionedPerson] = _persistent
    val storerealm = new EntityRealm[VersionedPerson](
      entityName = _collectionid.name,
      loader = EntityLoader[VersionedPerson](_ => None),
      state = new IdRef(EntityRealmState(Map.empty))
    )
    val memoryrealm = new PartitionedMemoryRealm[VersionedPerson](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val collection = new EntityCollection[VersionedPerson](
      EntityDescriptor(
        _collectionid,
        EntityRuntimePlan(
          entityName = _collectionid.name,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        ),
        _persistent,
        revisionBinding = Some(
          EntityRevisionBinding(EntityRevisionRepresentation.Embedded)
        )
      ),
      EntityStorage(storerealm, Some(memoryrealm))
    )
    component.entitySpace.registerEntity(_collectionid.name, collection)
    val observability = ObservabilityContext(
      traceId = TraceId("test", "uow_versioned_mutation"),
      spanId = None,
      correlationId = None
    )
    val rootscope = ScopeContext(
      ScopeKind.Runtime,
      "uow-versioned-mutation-root",
      None,
      observability
    )
    val componentscope = Component.Context(
      "uow-versioned-mutation-component",
      rootscope,
      component,
      ComponentOrigin.Embed
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "uow-versioned-mutation-runtime",
        parent = Some(componentscope),
        observabilityContext = observability,
        httpDriverOption = None,
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
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
      token = "uow-versioned-mutation-runtime-context"
    )
    val _ = context
    Fixture(datastorespace, entitystorespace, collection, component, context)
  }

  private def _raw_record(
    datastorespace: DataStoreSpace,
    id: EntityId
  )(using ExecutionContext): Consequence[Option[Record]] =
    for {
      datastore <- datastorespace.dataStore(
        DataStore.CollectionId.EntityStore(id.collection)
      )
      record <- datastore.load(
        DataStore.CollectionId.EntityStore(id.collection),
        DataStore.EntryId(id)
      )
    } yield record

  private final case class VersionedPerson(
    id: EntityId,
    name: String,
    revision: EntityRevision = EntityRevision.INITIAL
  )

  private final case class VersionedPersonCreate(
    id: EntityId,
    name: String
  )

  private val _create_persistent: EntityPersistentCreate[VersionedPersonCreate] =
    new EntityPersistentCreate[VersionedPersonCreate] {
      def id(entity: VersionedPersonCreate): Option[EntityId] =
        Some(entity.id)
      def collection(entity: VersionedPersonCreate): EntityCollectionId =
        entity.id.collection
      def toRecord(entity: VersionedPersonCreate): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "name" -> entity.name
        )
    }

  private val _persistent: EntityPersistent[VersionedPerson] =
    new EntityPersistent[VersionedPerson] {
      def id(entity: VersionedPerson): EntityId = entity.id
      def toRecord(entity: VersionedPerson): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "name" -> entity.name,
          "revision" -> entity.revision.value
        )
      def fromRecord(record: Record): Consequence[VersionedPerson] =
        for {
          id <- record
            .getAs[EntityId]("id")
            .map(Consequence.success)
            .getOrElse(Consequence.argumentMissing("id"))
          name <- record
            .getString("name")
            .map(Consequence.success)
            .getOrElse(Consequence.argumentMissing("name"))
          revision <- record
            .getAny("revision")
            .map(EntityRevision.createC)
            .getOrElse(Consequence.argumentMissing("revision"))
        } yield VersionedPerson(id, name, revision)
    }

  private final class IdRef[A](
      initial: A
  ) extends Ref[cats.Id, A] {
    private var _value: A = initial

    def get: A = synchronized(_value)

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
      val setter: A => Boolean = (next: A) =>
        synchronized {
          if (_value == snapshot) {
            _value = next
            true
          } else {
            false
          }
        }
      (snapshot, setter)
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
