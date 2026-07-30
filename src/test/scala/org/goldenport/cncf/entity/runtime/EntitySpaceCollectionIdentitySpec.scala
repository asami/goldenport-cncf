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
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 28, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntitySpaceCollectionIdentitySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _in_eid01_spec =
    afterWord("in spec:entity-collection-identity, example:E2, rules:R1,R4, phase:52")
  private val _in_phase52_spec =
    afterWord("in spec:entity-collection-identity, example:entity-space-routing, rules:R1,R4, phase:52")

  "EntitySpace collection identity" must _in_phase52_spec {
    "resolve same-name collections independently by exact identity" in {
      Given("two registered collections with one logical name and distinct namespaces")
      val firstid  = EntityCollectionId("first", "runtime", "facility")
      val secondid = EntityCollectionId("second", "runtime", "facility")
      val first    = _collection(firstid)
      val second   = _collection(secondid)
      val space    = new EntitySpace()
      space.registerEntity(firstid.name, first)
      space.registerEntity(secondid.name, second)

      When("each exact collection identity is resolved")
      val firstresult  = space.entityOption(firstid)
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

    "which records EID-01 EntitySpace routing" which {
      "E2 reject a parsed EntityId whose exact owner differs from this collection" must _in_eid01_spec {
        "preserve parsed ownership without selected-owner compatibility routing" in {
          Given(
            "Spec: docs/spec/entity-collection-identity.md; Rules: R1,R4; Example: E2; one exact collection and a canonical EntityId owned by a different collection"
          )
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

          And("the canonical parser exposes the stored exact owner")
          val parsed = EntityId.parse(scalarid.value).toOption.get
          parsed.collection shouldBe
            EntityCollectionId("runtime", "entry_scope", "facility")
          parsed.collection should not equal collectionid

          When("the selected collection resolves foreign references")
          val compatible   = collection.resolveEntityId(scalarid.value)
          val incompatible = collection.resolveEntityId(otherentityid.value)

          Then("neither foreign reference is rebound to the selected exact owner")
          compatible shouldBe None

          And("another logical collection remains rejected")
          incompatible shouldBe None

        }
      }
    }

    "reject an ambiguous logical-name lookup with candidate evidence" in {
      Given("two exact collections sharing one logical name")
      val firstid  = EntityCollectionId("first", "runtime", "facility")
      val secondid = EntityCollectionId("second", "runtime", "facility")
      val space    = new EntitySpace()
      space.registerEntity(firstid.name, _collection(firstid))
      space.registerEntity(secondid.name, _collection(secondid))

      When("a compatibility caller uses only the logical name")
      val result = space.entityByNameC[FixtureEntity]("facility")

      Then("CNCF rejects the ambiguous owner deterministically")
      result match {
        case Consequence.Failure(conclusion) =>
          val diagnostic = ConclusionDiagnostics.classify(conclusion)
          val facets     = conclusion.observation.cause.descriptor.facets
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

      And("canonical EntityId ingress does not use logical-name ambiguity resolution")
      val runtimeid =
        EntityId(
          "fixture",
          "ambiguous",
          EntityCollectionId("single", "global", "facility")
        )
      space.canonicalEntityIdC(runtimeid) match {
        case Consequence.Failure(conclusion) =>
          conclusion.displayMessage should include("EntityCollection not found")
        case Consequence.Success(_) =>
          fail("An unregistered exact EntityId must fail")
      }
    }

    "reject unique logical-name compatibility for exact EntityId ingress" in {
      Given("one exact collection registered for a logical name")
      val collectionid =
        EntityCollectionId("major", "minor", "facility")
      val collection = _collection(collectionid)
      val space      = new EntitySpace()
      space.registerEntity(collectionid.name, collection)

      When("a compatibility caller resolves the logical name")
      val result = space.entityByNameC[FixtureEntity]("facility")

      Then("the unique exact owner is returned")
      result shouldBe Consequence.success(collection)

      And("an unregistered exact EntityId is not rebound to that unique owner")
      val runtimeid =
        EntityId(
          "fixture",
          "unique",
          EntityCollectionId("single", "global", "facility")
        )
      val canonical = space.canonicalEntityIdC(runtimeid)
      canonical shouldBe a[Consequence.Failure[?]]
    }

    "preserve the registered plan name when the exact collection name differs" in {
      Given("one collection whose plan name and exact storage name differ")
      val collectionid =
        EntityCollectionId("major", "minor", "media_object")
      val collection = _collection(collectionid, "MediaEntity")
      val space      = new EntitySpace()
      space.registerEntity("MediaEntity", collection)

      When("a legacy plan-name caller and an unregistered exact collection are resolved")
      val byplanname =
        space.entityByNameC[FixtureEntity]("MediaEntity")
      val bycollectionname =
        space.canonicalCollectionIdC(
          EntityCollectionId("single", "global", "media_object")
        )

      Then("only the explicit name compatibility path selects the collection")
      byplanname shouldBe Consequence.success(collection)
      bycollectionname shouldBe a[Consequence.Failure[?]]
      space.entityNames shouldBe Vector("MediaEntity")
    }

    "pass an unregistered exact scalar identity unchanged at the UnitOfWork boundary" in {
      Given("a component with two exact collections sharing one logical name")
      val firstid  = EntityCollectionId("first", "runtime", "facility")
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

      When("a direct load reaches UnitOfWork with an unregistered exact collection identity")
      val result =
        interpreter.interpret(
          UnitOfWorkOp.EntityStoreLoadDirect(
            runtimeid,
            _persistent
          )
        )

      Then("UnitOfWork does not rewrite the exact identity by logical name")
      result shouldBe Consequence.success(Option.empty[FixtureEntity])
    }

    "preserve exact search ingress without a unique-name rewrite" in {
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

      When("a direct store search starts with an unregistered exact collection identity")
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

      Then("the datastore operation is observed under the supplied exact collection")
      rendered should include(scalarcollectionid.print)
      rendered should not include collectionid.print
    }

    "keep a normal resident search inside its exact collection namespace" in {
      Given("two resident collections with one logical name and data only in the second namespace")
      val firstid  = EntityCollectionId("first", "runtime", "facility")
      val secondid = EntityCollectionId("second", "runtime", "facility")
      val first    = _collection(firstid)
      val second   = _collection(secondid)
      val component =
        TestComponentFactory.create(
          "entity_space_exact_resident_search",
          Protocol.empty
        )
      component.entitySpace.registerEntity(firstid.name, first)
      component.entitySpace.registerEntity(secondid.name, second)
      given ExecutionContext = ExecutionContext.create().withScope(component.scopeContext)
      val secondentity = FixtureEntity(EntityId("fixture", "resident", secondid))
      second.putScoped(secondentity)
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(summon[ExecutionContext]))
      val query = EntityQuery[FixtureEntity](
        firstid,
        Query(Record.empty),
        EntitySearchScope.WorkingSet
      )

      When("a normal UnitOfWork search asks for the empty first namespace")
      val result = interpreter.interpret(
        UnitOfWorkOp.EntityStoreSearch(
          query,
          _persistent
        )
      )

      Then("the resident entity from the same-name second namespace is not returned")
      result.map(_.data) shouldBe Consequence.success(Vector.empty)
    }
  }

  "EntityCollection synchronized create" must _in_phase52_spec {
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

    "retain a newly created record when authoritative decoding preserves exact ownership" in {
      Given("a canonical store codec whose EntityId retains the selected exact owner")
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

      When("the persisted canonical Record is decoded under its exact owner")
      val result =
        collection.createRecordSynced(
          Record.dataAuto("id" -> id)
        )

      Then("the create succeeds without ownership repair")
      result shouldBe Consequence.unit

      And("the exact Entity enters resident collection state")
      collection.resolve(id).map(_.id) shouldBe Consequence.success(id)
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
            EntityPersistent._collection_mismatch(id.collection, collectionid)
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
