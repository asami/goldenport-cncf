package org.goldenport.cncf.entity.runtime

import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.context.{
  DataStoreContext,
  EntityStoreContext,
  ExecutionContext,
  ScopeContext
}
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.{
  CreateResult,
  EntityConcurrencyPolicy,
  EntityCreateOptions,
  EntityPersistent,
  EntityPersistentCreate,
  EntityQuery,
  EntitySearchScope,
  EntityStore,
  EntityStoreSpace,
  StandardEntityStore
}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.unitofwork.{
  UnitOfWork,
  UnitOfWorkInterpreter,
  UnitOfWorkOp
}
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId
}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 28, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntitySpaceCollectionIdentitySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "EntitySpace collection identity" should {
    "resolve same-name collections independently by exact identity" in {
      Given("two registered collections with one logical name and distinct namespaces")
      val firstid = EntityCollectionId("first", "runtime", "facility")
      val secondid = EntityCollectionId("second", "runtime", "facility")
      val first = _collection(firstid)
      val second = _collection(secondid)
      val space = new EntitySpace()
      space.registerEntity(firstid.name, first)
      space.registerEntity(secondid.name, second)

      When("each exact collection identity is resolved")
      val firstresult = space.entityOption(firstid)
      val secondresult = space.entityOption(secondid)

      Then("the exact registrations remain isolated")
      firstresult shouldBe Some(first)
      secondresult shouldBe Some(second)
      space.entityNames shouldBe Vector("facility")

      And("runtime iteration retains every exact collection")
      space.entityCollectionIds shouldBe Vector(firstid, secondid)
      space.entityCollections should contain theSameElementsInOrderAs Vector(
        first,
        second
      )
      space.entityCollectionsByName("facility") should contain theSameElementsAs
        Vector(first, second)
    }

    "restore the selected exact owner for a compatible scalar reference" in {
      Given("one exact collection and a scalar whose parser namespace differs")
      val collectionid =
        EntityCollectionId("runtime", "selected", "facility")
      val collection = _collection(collectionid)
      val scalarid =
        EntityId(
          "runtime",
          "entry_scope",
          EntityCollectionId("runtime", "entry_scope", "facility")
        )
      val otherentityid =
        EntityId(
          "runtime",
          "entry_scope",
          EntityCollectionId("runtime", "entry_scope", "exhibition")
        )

      When("the selected collection resolves compatible and incompatible scalar references")
      val compatible = collection.resolveEntityId(scalarid.value)
      val incompatible = collection.resolveEntityId(otherentityid.value)

      Then("the compatible scalar is rebound to the selected exact owner")
      compatible.map(_.collection) shouldBe Some(collectionid)

      And("another logical collection remains rejected")
      incompatible shouldBe None
    }

    "reject an ambiguous logical-name lookup with candidate evidence" in {
      Given("two exact collections sharing one logical name")
      val firstid = EntityCollectionId("first", "runtime", "facility")
      val secondid = EntityCollectionId("second", "runtime", "facility")
      val space = new EntitySpace()
      space.registerEntity(firstid.name, _collection(firstid))
      space.registerEntity(secondid.name, _collection(secondid))

      When("a compatibility caller uses only the logical name")
      val result = space.entityByNameC[FixtureEntity]("facility")

      Then("CNCF rejects the ambiguous owner deterministically")
      result match {
        case Consequence.Failure(conclusion) =>
          val diagnostic = ConclusionDiagnostics.classify(conclusion)
          val facets = conclusion.observation.cause.descriptor.facets
          diagnostic.policy shouldBe Some("entity.collection.identity")
          diagnostic.reason shouldBe Some("entity-collection-name-ambiguous")
          facets should contain(
            org.goldenport.observation.Descriptor.Facet.Actual(
              Vector(firstid.print, secondid.print).sorted.mkString(", ")
            )
          )
        case Consequence.Success(_) =>
          fail("A logical name must not select between exact collections")
      }

      And("the Option compatibility lookup does not pick an arbitrary owner")
      space.entityOption[FixtureEntity]("facility") shouldBe None

      And("canonical EntityId ingress returns the same structured ambiguity")
      val runtimeid =
        EntityId(
          "fixture",
          "ambiguous",
          EntityCollectionId("single", "global", "facility")
        )
      space.canonicalEntityIdC(runtimeid) match {
        case Consequence.Failure(conclusion) =>
          ConclusionDiagnostics
            .classify(conclusion)
            .reason shouldBe Some("entity-collection-name-ambiguous")
        case Consequence.Success(_) =>
          fail("Ambiguous name-only EntityId ingress must fail")
      }
    }

    "retain unique logical-name compatibility" in {
      Given("one exact collection registered for a logical name")
      val collectionid =
        EntityCollectionId("major", "minor", "facility")
      val collection = _collection(collectionid)
      val space = new EntitySpace()
      space.registerEntity(collectionid.name, collection)

      When("a compatibility caller resolves the logical name")
      val result = space.entityByNameC[FixtureEntity]("facility")

      Then("the unique exact owner is returned")
      result shouldBe Consequence.success(collection)

      And("a scalar EntityId is rebound to that unique exact owner")
      val runtimeid =
        EntityId(
          "fixture",
          "unique",
          EntityCollectionId("single", "global", "facility")
        )
      space
        .canonicalEntityIdC(runtimeid)
        .map(_.collection) shouldBe Consequence.success(collectionid)
    }

    "preserve the registered plan name when the exact collection name differs" in {
      Given("one collection whose plan name and exact storage name differ")
      val collectionid =
        EntityCollectionId("major", "minor", "media_object")
      val collection = _collection(collectionid, "MediaEntity")
      val space = new EntitySpace()
      space.registerEntity("MediaEntity", collection)

      When("legacy plan-name and scalar collection-name callers resolve it")
      val byplanname =
        space.entityByNameC[FixtureEntity]("MediaEntity")
      val bycollectionname =
        space.canonicalCollectionIdC(
          EntityCollectionId("single", "global", "media_object")
        )

      Then("both ingress forms select the same exact collection")
      byplanname shouldBe Consequence.success(collection)
      bycollectionname shouldBe Consequence.success(collectionid)
      space.entityNames shouldBe Vector("MediaEntity")
    }

    "reject ambiguous scalar identity at the UnitOfWork boundary" in {
      Given("a component with two exact collections sharing one logical name")
      val firstid = EntityCollectionId("first", "runtime", "facility")
      val secondid = EntityCollectionId("second", "runtime", "facility")
      val component =
        TestComponentFactory.create(
          "entity_space_collection_identity",
          Protocol.empty
        )
      component.entitySpace.registerEntity(
        firstid.name,
        _collection(firstid)
      )
      component.entitySpace.registerEntity(
        secondid.name,
        _collection(secondid)
      )
      given ExecutionContext =
        ExecutionContext.create().withScope(component.scopeContext)
      val interpreter =
        new UnitOfWorkInterpreter(
          new UnitOfWork(summon[ExecutionContext])
        )
      val runtimeid =
        EntityId(
          "fixture",
          "uow_ambiguous",
          EntityCollectionId("single", "global", "facility")
        )

      When("a direct load reaches UnitOfWork with only scalar collection identity")
      val result =
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreLoadDirect(
            runtimeid,
            _persistent
          )
        )

      Then("UnitOfWork returns the structured ambiguity before datastore access")
      result match {
        case Consequence.Failure(conclusion) =>
          ConclusionDiagnostics
            .classify(conclusion)
            .reason shouldBe Some("entity-collection-name-ambiguous")
        case Consequence.Success(_) =>
          fail("UnitOfWork must not continue after ambiguous name ingress")
      }
    }

    "route unique name-only search ingress through the exact owner" in {
      Given("one exact collection and an observed component execution context")
      val collectionid =
        EntityCollectionId("major", "minor", "facility")
      val scalarcollectionid =
        EntityCollectionId("single", "global", "facility")
      val component =
        TestComponentFactory.create(
          "entity_space_search_identity",
          Protocol.empty
        )
      component.entitySpace.registerEntity(
        collectionid.name,
        _collection(collectionid)
      )
      val context =
        ExecutionContext
          .withFrameworkCallTreeEnabled(
            ExecutionContext.create(),
            enabled = true
          )
          .withScope(component.scopeContext)
      given ExecutionContext = context
      val interpreter =
        new UnitOfWorkInterpreter(
          new UnitOfWork(context)
        )
      val query =
        EntityQuery[FixtureEntity](
          scalarcollectionid,
          Query(Record.empty),
          EntitySearchScope.Store
        )

      When("a direct store search starts with only scalar collection identity")
      val _ =
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreSearchDirect(
            query,
            _persistent
          )
        )
      val rendered =
        context.observability.callTreeContext
          .build()
          .map(_.toRecord.print)
          .getOrElse(fail("UnitOfWork CallTree is missing"))

      Then("the datastore operation is observed under the exact runtime owner")
      rendered should include(collectionid.print)
      rendered should not include scalarcollectionid.print
    }
  }

  "EntityCollection synchronized create" should {
    "retain compatibility when a successful provider omits its optional record" in {
      Given("an exact Entity and a provider returning CreateResult without a Record")
      val collectionid =
        EntityCollectionId("runtime", "create_none", "facility")
      val collection =
        _collection(
          collectionid,
          persistent = _restoring_persistent(collectionid)
        )
      val component =
        TestComponentFactory.create(
          "entity_collection_create_none",
          Protocol.empty
        )
      component.entitySpace.registerEntity(collectionid.name, collection)
      val entitystorespace =
        new EntityStoreSpace().addEntityStore(
          new MissingCreateRecordEntityStore()
        )
      val scope =
        ScopeContext.Instance(
          component.scopeContext.core.copy(
            datastore = Some(DataStoreContext(DataStoreSpace.default())),
            entitystore = Some(EntityStoreContext(entitystorespace))
          )
        )
      given ExecutionContext =
        ExecutionContext.create().withScope(scope)
      val id = EntityId("fixture", "create_none", collectionid)

      When("the collection synchronizes the create result")
      val result =
        collection.createRecordSynced(
          Record.dataAuto("id" -> id)
        )

      Then("the optional Record omission remains successful")
      result shouldBe Consequence.unit

      And("the exact input Entity enters resident collection state")
      collection.resolve(id).map(_.id) shouldBe Consequence.success(id)
    }

    "remove a newly created record when authoritative decoding fails" in {
      Given("a scalar store codec that cannot restore the selected exact owner")
      val collectionid =
        EntityCollectionId("runtime", "create_rollback", "facility")
      val collection = _collection(collectionid)
      val component =
        TestComponentFactory.create(
          "entity_collection_create_rollback",
          Protocol.empty
        )
      component.entitySpace.registerEntity(collectionid.name, collection)
      val datastorespace = DataStoreSpace.default()
      val entitystorespace =
        new EntityStoreSpace().addEntityStore(EntityStore.standard())
      val scope =
        ScopeContext.Instance(
          component.scopeContext.core.copy(
            datastore = Some(DataStoreContext(datastorespace)),
            entitystore = Some(EntityStoreContext(entitystorespace))
          )
        )
      given ExecutionContext =
        ExecutionContext.create().withScope(scope)
      val id = EntityId("fixture", "create_rollback", collectionid)

      When("the persisted scalar Record fails exact-owner decoding")
      val result =
        collection.createRecordSynced(
          Record.dataAuto("id" -> id)
        )
      val stored = for {
        cid <- entitystorespace.dataStoreCollection(id)
        dsid <- entitystorespace.dataStoreEntryId(id)
        datastore <- datastorespace.dataStore(cid)
        record <- datastore.load(cid, dsid)
      } yield record

      Then("the create reports the exact-owner failure")
      result shouldBe a[Consequence.Failure[?]]

      And("hard-delete compensation leaves no persisted orphan")
      stored shouldBe Consequence.success(None)

      And("the failed Entity does not enter resident collection state")
      collection.resolve(id) shouldBe a[Consequence.Failure[?]]
    }
  }

  private def _collection(
    collectionid: EntityCollectionId,
    logicalname: String = "",
    persistent: EntityPersistent[FixtureEntity] = _persistent
  ): EntityCollection[FixtureEntity] = {
    val entityname =
      if (logicalname.isEmpty) collectionid.name else logicalname
    given EntityPersistent[FixtureEntity] = persistent
    val realm = new EntityRealm[FixtureEntity](
      entityName = entityname,
      loader = EntityLoader[FixtureEntity](_ => None),
      state = new IdRef(
        EntityRealmState(Map.empty)
      )
    )
    new EntityCollection(
      EntityDescriptor(
        collectionId = collectionid,
        plan = EntityRuntimePlan(
          entityName = entityname,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 1,
          maxEntitiesPerPartition = 16,
          concurrencyPolicy = EntityConcurrencyPolicy.default
        ),
        persistent = persistent
      ),
      EntityStorage(realm)
    )
  }

  private val _persistent: EntityPersistent[FixtureEntity] =
    new EntityPersistent[FixtureEntity] {
      def id(entity: FixtureEntity): EntityId =
        entity.id

      def toRecord(entity: FixtureEntity): Record =
        Record.dataAuto("id" -> entity.id.value)

      def fromRecord(record: Record): Consequence[FixtureEntity] =
        EntityId.createC(record).map(FixtureEntity.apply)
    }

  private def _restoring_persistent(
    collectionid: EntityCollectionId
  ): EntityPersistent[FixtureEntity] =
    new EntityPersistent[FixtureEntity] {
      def id(entity: FixtureEntity): EntityId =
        entity.id

      def toRecord(entity: FixtureEntity): Record =
        Record.dataAuto("id" -> entity.id.value)

      def fromRecord(record: Record): Consequence[FixtureEntity] =
        EntityId.createC(record).flatMap { id =>
          if (id.collection == collectionid)
            Consequence.success(FixtureEntity(id))
          else
            EntityPersistent.restoreCollectionIdentity(
              FixtureEntity(id),
              id,
              collectionid
            )(canonicalid => FixtureEntity(canonicalid))
        }
    }

  private final case class FixtureEntity(
    id: EntityId
  )

  private final class MissingCreateRecordEntityStore
      extends StandardEntityStore() {
    override def create[T](
      entity: T,
      options: EntityCreateOptions
    )(using
      tc: EntityPersistentCreate[T],
      ctx: ExecutionContext
    ): Consequence[CreateResult[T]] =
      tc.id(entity)
        .map(id => Consequence.success(CreateResult[T](id)))
        .getOrElse(
          Consequence.argumentInvalid(
            "MissingCreateRecordEntityStore requires an explicit Entity id"
          )
        )
  }

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
