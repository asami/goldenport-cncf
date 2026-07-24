package org.goldenport.cncf.entity

import cats.data.State
import cats.effect.Ref
import cats.~>
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
import org.goldenport.cncf.entity.runtime.{
  EntityCollection,
  EntityDescriptor,
  EntityLoader,
  EntityMemoryPolicy,
  EntityRealm,
  EntityRealmState,
  EntityRuntimePlan,
  EntityStorage,
  PartitionStrategy,
  PartitionedMemoryRealm
}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.directive.Update

/*
 * @since   Jul. 24, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityConcurrencyTokenSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {

  private val _e1_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E1, rules:R1,R2,R4, phase:49"
    )
  private val _e2_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E2, rules:R3, phase:49"
    )

  "Entity concurrency token" should {
    "E1 define a non-negative token with one overflow-safe advancement" must _e1_metadata {
      "when generated admitted values advance" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1,R2,R4; Example: E1; generated non-negative token values"
        )
        val property = Prop.forAll(
          Gen.chooseNum(0L, Long.MaxValue - 1L)
        ) { number =>
          val token = EntityConcurrencyTokenSupport._create(number)
          val next  = token.flatMap(EntityConcurrencyTokenSupport._advance)
          token.toOption.exists(_.print == number.toString) &&
          next.toOption.exists(_.print == (number + 1L).toString)
        }

        When("the token constructor and advancement are interpreted")
        val checked =
          Test.check(
            Test.Parameters.default.withMinSuccessfulTests(100),
            property
          )

        Then("all admitted values remain exact and advance once")
        checked.passed shouldBe true
        EntityConcurrencyToken.INITIAL.print shouldBe "1"
        EntityMutationExpectation.parse("1").map(_.token) shouldBe
          Consequence.success(EntityConcurrencyToken.INITIAL)
        EntityMutationExpectation.parse("1.0") shouldBe
          a[Consequence.Failure[?]]
        EntityMutationExpectation.parse("-1") shouldBe
          a[Consequence.Failure[?]]
        EntityConcurrencyTokenSupport._create(-1L) shouldBe
          a[Consequence.Failure[?]]
        classOf[EntityConcurrencyToken].getConstructors shouldBe empty
        classOf[EntityConcurrencyToken].getDeclaredConstructors
          .forall(constructor =>
            java.lang.reflect.Modifier.isPrivate(constructor.getModifiers)
          ) shouldBe true
        classOf[EntityConcurrencyToken].getMethods
          .map(_.getName) should not contain "value"
        classOf[EntityConcurrencyToken].getMethods
          .map(_.getName) should not contain "next"

        val overflow =
          EntityConcurrencyTokenSupport
            ._create(Long.MaxValue)
            .flatMap(EntityConcurrencyTokenSupport._advance)
        val conclusion = overflow match {
          case Consequence.Failure(conclusion) =>
            conclusion
          case _ =>
            fail("maximum token advancement must fail")
        }
        val cause = conclusion.observation.cause

        cause.kind shouldBe Some(Cause.Kind.Limit)
        cause.descriptor.facets should contain(
          Descriptor.Facet.Limit(Long.MaxValue - 1L)
        )
        cause.descriptor.facets should contain(
          Descriptor.Facet.Actual(Long.MaxValue)
        )
      }
    }

    "E1 carry a typed Entity beside its admitted token" must _e1_metadata {
      "when a framework snapshot is constructed" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R4; Example: E1; one typed Entity and its token"
        )
        val snapshot =
          EntitySnapshot("entity-value", EntityConcurrencyToken.INITIAL)

        When("the snapshot is inspected")
        val entity = snapshot.entity
        val token  = snapshot.token

        Then("the domain value and framework token remain separate")
        entity shouldBe "entity-value"
        token shouldBe EntityConcurrencyToken.INITIAL
      }
    }

    "E1 reserve the canonical storage field during initialization" must _e1_metadata {
      "when caller-provided revision aliases are normalized for create" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1,R2; Example: E1; a create record containing caller revision aliases"
        )
        val source = Record.dataAuto(
          "name"          -> "entity",
          "cncfRevision"  -> 91L,
          "cncf_revision" -> 92L
        )

        When("framework concurrency metadata initializes the record")
        val initialized =
          EntityConcurrencyMetadata.initializeForCreate(source)

        Then("caller values are removed and canonical initial token one is stored")
        SimpleEntityStorageShapePolicy
          .targetName(
            SimpleEntityStorageShapePolicy.CONCURRENCY_REVISION_LOGICAL_FIELD
          ) shouldBe
          SimpleEntityStorageShapePolicy.CONCURRENCY_REVISION_STORAGE_FIELD
        SimpleEntityStorageShapePolicy.managementLogicalFields should contain(
          SimpleEntityStorageShapePolicy.CONCURRENCY_REVISION_LOGICAL_FIELD
        )
        initialized.getString("name") shouldBe Some("entity")
        initialized.getAny("cncfRevision") shouldBe None
        initialized.getAny("cncf_revision") shouldBe Some(1L)
        EntityConcurrencyMetadata.token(initialized) shouldBe
          Consequence.success(EntityConcurrencyToken.INITIAL)
      }
    }

    "E1 isolate canonical metadata from the UnitOfWork EntitySpace update" must _e1_metadata {
      "when a strict domain codec receives a newly persisted Entity" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R2,R14,R17; Example: E1; a component-scoped EntitySpace whose domain codec rejects framework metadata"
        )
        val fixture            = _component_fixture()
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "uow_create", _collection_id)
        val entity             = TestEntity(id, "unit-of-work", Some(71L))
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))

        When("the canonical UnitOfWork create route persists and admits the Entity")
        val created = interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(entity, _entity_create)
        )
        val admitted =
          fixture.component.entitySpace
            .entity[TestEntity](_collection_id.name)
            .resolve(id)
        val stored = _raw_record(fixture.datastorespace, id)

        Then(
          "the store keeps revision metadata while EntitySpace receives only the decoded domain Entity"
        )
        created.map(_.id) shouldBe Consequence.success(id)
        admitted shouldBe
          Consequence.success(entity.copy(attempted = None))
        stored
          .map(_.flatMap(_.getAny(EntityConcurrencyMetadata.STORAGE_FIELD_NAME))) shouldBe
          Consequence.success(Some(1L))
      }
    }

    "E1 integrate the initial token with canonical Entity persistence" must _e1_metadata {
      "when generated caller revision values pass through create and load" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1-R4; Example: E1; generated create records and isolated EntityStore runtimes"
        )
        val property = Prop.forAll(
          Gen.chooseNum(2L, Long.MaxValue)
        ) { attempted =>
          val fixture            = _fixture()
          given ExecutionContext = fixture.context
          val id = EntityId(
            "test",
            s"revision_$attempted",
            _collection_id
          )
          val entity = TestEntity(id, s"entity-$attempted", Some(attempted))
          val created = fixture.entitystorespace.create(
            UnitOfWorkOp.EntityStoreCreate(entity, _entity_create)
          )
          val stored = created.flatMap(result =>
            _raw_record(fixture.datastorespace, result.id)
          )
          val loaded = fixture.entitystorespace.load(
            UnitOfWorkOp.EntityStoreLoad(id, _entity_persistent)
          )
          val snapshot = fixture.entitystorespace.loadSnapshot(
            id,
            _entity_persistent
          )

          stored.toOption.flatten
            .flatMap(_.getAny(EntityConcurrencyMetadata.STORAGE_FIELD_NAME))
            .contains(1L) &&
          stored.toOption.flatten
            .flatMap(_.getAny(EntityConcurrencyMetadata.LOGICAL_FIELD_NAME))
            .isEmpty &&
          loaded.toOption.flatten.contains(entity.copy(attempted = None)) &&
          snapshot.toOption.flatten.exists(value =>
            value.entity == entity.copy(attempted = None) &&
              value.token == EntityConcurrencyToken.INITIAL
          )
        }

        When("the canonical Entity create and read boundaries are exercised")
        val checked = Test.check(
          Test.Parameters.default.withMinSuccessfulTests(25),
          property
        )

        Then(
          "storage owns token one while business decoding and snapshot projection remain separate"
        )
        checked.passed shouldBe true
      }
    }

    "E1 protect the stored token from save and import input" must _e1_metadata {
      "when callers submit managed aliases through mutation and import records" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1-R4; Example: E1; one created Entity and one imported Entity carrying caller revisions"
        )
        val fixture            = _fixture()
        given ExecutionContext = fixture.context
        val createdid          = EntityId("test", "created", _collection_id)
        val importedid         = EntityId("test", "imported", _collection_id)
        val reimportedid       = EntityId("test", "reimported", _collection_id)
        val created = fixture.entitystorespace.create(
          UnitOfWorkOp.EntityStoreCreate(
            TestEntity(createdid, "before", Some(41L)),
            _entity_create
          )
        )
        val seeded = created.flatMap(_ =>
          fixture.datastorespace.inject(
            DataStoreSpace.Seed(
              Vector(
                DataStoreSpace.SeedEntry(
                  DataStore.CollectionId.EntityStore(_collection_id),
                  Record.dataAuto(
                    "id"                                         -> reimportedid,
                    "name"                                       -> "before-import",
                    EntityConcurrencyMetadata.STORAGE_FIELD_NAME -> 7L
                  )
                )
              )
            )
          )
        )

        When("full save patch update and seed import attempt to replace framework metadata")
        val saved = seeded.flatMap { _ =>
          fixture.entitystorespace
            .loadSnapshot(createdid, _entity_persistent)
            .flatMap(snapshot =>
              Consequence.successOrEntityNotFound(snapshot)(createdid)
            )
            .flatMap(snapshot =>
              fixture.entitystorespace.save(
                UnitOfWorkOp.EntityStoreSave(
                  TestEntity(createdid, "after", Some(42L)),
                  EntityMutationExpectation(snapshot.token),
                  _entity_persistent
                )
              )
            )
        }
        val patched = saved.flatMap(snapshot =>
          fixture.entitystorespace.updateById(
            UnitOfWorkOp.EntityStoreUpdateById(
              createdid,
              TestPatch(Update.set("patched"), Update.set(44L)),
              EntityMutationExpectation(snapshot.token),
              _entity_update
            )
          )
        )
        val imported = patched.flatMap(_ =>
          fixture.entitystorespace.importSeed(
            EntityStoreSeed(
              Vector(
                EntityStoreSeedEntry(
                  TestEntity(importedid, "imported", Some(45L))
                ),
                EntityStoreSeedEntry(
                  TestEntity(reimportedid, "reimported", Some(46L))
                )
              )
            )
          )(using fixture.context, _entity_persistent)
        )
        val createdrecord = imported.flatMap(_ =>
          _raw_record(fixture.datastorespace, createdid)
        )
        val importedrecord = imported.flatMap(_ =>
          _raw_record(fixture.datastorespace, importedid)
        )
        val reimportedrecord = imported.flatMap(_ =>
          _raw_record(fixture.datastorespace, reimportedid)
        )

        Then(
          "versioned mutations advance the existing token while newly imported storage receives canonical token one"
        )
        createdrecord
          .map(_.flatMap(_.getAny(EntityConcurrencyMetadata.STORAGE_FIELD_NAME))) shouldBe
          Consequence.success(Some(3L))
        createdrecord
          .map(_.flatMap(_.getString("name"))) shouldBe
          Consequence.success(Some("patched"))
        importedrecord
          .map(_.flatMap(_.getAny(EntityConcurrencyMetadata.STORAGE_FIELD_NAME))) shouldBe
          Consequence.success(Some(1L))
        reimportedrecord
          .map(_.flatMap(_.getAny(EntityConcurrencyMetadata.STORAGE_FIELD_NAME))) shouldBe
          Consequence.success(Some(7L))
      }
    }

    "E2 interpret a physically absent revision as virtual token zero" must _e2_metadata {
      "when legacy and exact integral storage records are decoded" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R3; Example: E2; a legacy record and generated exact integral values"
        )
        val property = Prop.forAll(
          Gen.chooseNum(0L, Long.MaxValue)
        ) { number =>
          EntityConcurrencyMetadata
            .token(Record.dataAuto("cncf_revision" -> BigInt(number)))
            .toOption
            .exists(_.print == number.toString)
        }

        When("the metadata codec reads the records")
        val legacy =
          EntityConcurrencyMetadata.token(Record.dataAuto("name" -> "legacy"))
        val checked =
          Test.check(
            Test.Parameters.default.withMinSuccessfulTests(100),
            property
          )

        Then(
          "absence maps to zero without persisting a replacement and integral values remain exact"
        )
        legacy shouldBe Consequence.success(EntityConcurrencyToken.LEGACY)
        checked.passed shouldBe true
      }
    }

    "E2 reject malformed physical revision values" must _e2_metadata {
      "when negative fractional overflowing or duplicate values are decoded" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R3; Example: E2; inadmissible physical revision values"
        )
        val records = Vector(
          Record.dataAuto("cncf_revision" -> -1L),
          Record.dataAuto("cncf_revision" -> BigDecimal("1.5")),
          Record.dataAuto(
            "cncf_revision" -> (BigInt(Long.MaxValue) + 1)
          ),
          Record.dataAuto(
            "cncf_revision" -> 1L,
            "cncf_revision" -> 2L
          )
        )

        When("the metadata codec reads each record")
        val results =
          records.map(EntityConcurrencyMetadata.token)

        Then("every malformed value returns a structured failure")
        all(results) shouldBe a[Consequence.Failure[?]]
      }
    }

    "E2 load a legacy Entity without physically backfilling its token" must _e2_metadata {
      "when a concurrency-aware read observes a record without revision metadata" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R2-R4; Example: E2; one authoritative legacy storage record"
        )
        val fixture            = _fixture()
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "legacy", _collection_id)
        val seeded = fixture.datastorespace.inject(
          DataStoreSpace.Seed(
            Vector(
              DataStoreSpace.SeedEntry(
                DataStore.CollectionId.EntityStore(_collection_id),
                Record.dataAuto(
                  "id"                                         -> id,
                  "name"                                       -> "legacy",
                  EntityConcurrencyMetadata.LOGICAL_FIELD_NAME -> 99L
                )
              )
            )
          )
        )

        When("plain and concurrency-aware reads use the canonical EntityStore route")
        val loaded = seeded.flatMap(_ =>
          fixture.entitystorespace.load(
            UnitOfWorkOp.EntityStoreLoad(id, _entity_persistent)
          )
        )
        val snapshot = seeded.flatMap(_ =>
          fixture.entitystorespace.loadSnapshot(id, _entity_persistent)
        )
        val stored = seeded.flatMap(_ =>
          _raw_record(fixture.datastorespace, id)
        )

        Then(
          "the business Entity loads, snapshot reports virtual zero, and physical storage remains absent"
        )
        loaded shouldBe
          Consequence.success(Some(TestEntity(id, "legacy", None)))
        snapshot.map(_.map(_.token)) shouldBe
          Consequence.success(Some(EntityConcurrencyToken.LEGACY))
        stored
          .map(_.flatMap(_.getAny(EntityConcurrencyMetadata.STORAGE_FIELD_NAME))) shouldBe
          Consequence.success(None)
        stored
          .map(_.flatMap(_.getAny(EntityConcurrencyMetadata.LOGICAL_FIELD_NAME))) shouldBe
          Consequence.success(Some(99L))
      }
    }
  }

  private val _collection_id =
    EntityCollectionId("test", "a", "entity_concurrency")

  private final case class TestEntity(
      id: EntityId,
      name: String,
      attempted: Option[Long]
  )

  private final case class TestPatch(
      name: Update[String],
      attempted: Update[Long]
  ) extends EntityPersistableUpdate {
    def toRecord(): Record =
      Record.dataAuto(
        "name"                                       -> name,
        EntityConcurrencyMetadata.LOGICAL_FIELD_NAME -> attempted
      )
  }

  private val _entity_create: EntityPersistentCreate[TestEntity] =
    new EntityPersistentCreate[TestEntity] {
      def id(entity: TestEntity): Option[EntityId] = Some(entity.id)
      def collection(entity: TestEntity): EntityCollectionId =
        entity.id.collection
      def toRecord(entity: TestEntity): Record =
        _entity_record(entity)
      override def toStoreRecord(entity: TestEntity): Record =
        _entity_record(entity)
    }

  private val _entity_persistent: EntityPersistent[TestEntity] =
    new EntityPersistent[TestEntity] {
      def id(entity: TestEntity): EntityId = entity.id
      def toRecord(entity: TestEntity): Record =
        _entity_record(entity)
      override def toStoreRecord(entity: TestEntity): Record =
        _entity_record(entity)
      def fromRecord(record: Record): Consequence[TestEntity] =
        _decode_entity(record)
      override def fromStoreRecord(
          record: Record
      ): Consequence[TestEntity] =
        _decode_entity(record)
    }

  private val _entity_update: EntityPersistentUpdate[TestPatch] =
    EntityPersistentUpdate.derived(
      _ => Consequence.argumentInvalid("test patch decoding is not supported"),
      _collection_id
    )

  private final case class Fixture(
      datastorespace: DataStoreSpace,
      entitystorespace: EntityStoreSpace,
      context: ExecutionContext
  )

  private final case class ComponentFixture(
      datastorespace: DataStoreSpace,
      entitystorespace: EntityStoreSpace,
      component: Component,
      context: ExecutionContext
  )

  private def _fixture(): Fixture = {
    val datastorespace = DataStoreSpace.default()
    val entitystorespace =
      new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val observability = ObservabilityContext(
      traceId = TraceId("test", "entity_concurrency"),
      spanId = None,
      correlationId = None
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "entity-concurrency-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = None,
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = {
          val _ = operation
          throw new UnsupportedOperationException(
            "unitOfWorkInterpreter is not used in this spec"
          )
        }
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "entity-concurrency-runtime-context"
    )
    val _ = context
    Fixture(datastorespace, entitystorespace, context)
  }

  private def _component_fixture(): ComponentFixture = {
    val datastorespace = DataStoreSpace.default()
    val entitystorespace =
      new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val observability = ObservabilityContext(
      traceId = TraceId("test", "entity_concurrency_component"),
      spanId = None,
      correlationId = None
    )
    val component                      = new Component() {}
    given EntityPersistent[TestEntity] = _entity_persistent
    val storerealm = new EntityRealm[TestEntity](
      entityName = _collection_id.name,
      loader = EntityLoader[TestEntity](_ => None),
      state = new IdRef[EntityRealmState[TestEntity]](
        EntityRealmState(Map.empty)
      )
    )
    val memoryrealm = new PartitionedMemoryRealm[TestEntity](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val descriptor = EntityDescriptor(
      collectionId = _collection_id,
      plan = EntityRuntimePlan(
        entityName = _collection_id.name,
        memoryPolicy = EntityMemoryPolicy.LoadToMemory,
        workingSet = None,
        partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
        maxPartitions = 4,
        maxEntitiesPerPartition = 16
      ),
      persistent = _entity_persistent
    )
    component.entitySpace.registerEntity(
      _collection_id.name,
      new EntityCollection[TestEntity](
        descriptor = descriptor,
        storage = EntityStorage(storerealm, Some(memoryrealm))
      )
    )
    val rootscope = ScopeContext(
      kind = ScopeKind.Runtime,
      name = "entity-concurrency-root",
      parent = None,
      observabilityContext = observability
    )
    val componentscope = Component.Context(
      name = "entity-concurrency-component",
      parent = rootscope,
      component = component,
      componentOrigin = ComponentOrigin.Embed
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "entity-concurrency-runtime",
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
      token = "entity-concurrency-component-runtime-context"
    )
    val _ = context
    ComponentFixture(
      datastorespace,
      entitystorespace,
      component,
      context
    )
  }

  private def _entity_record(entity: TestEntity): Record = {
    val base = Record.dataAuto(
      "id"   -> entity.id,
      "name" -> entity.name
    )
    entity.attempted match {
      case Some(value) =>
        base ++ Record.dataAuto(
          EntityConcurrencyMetadata.LOGICAL_FIELD_NAME -> value,
          EntityConcurrencyMetadata.STORAGE_FIELD_NAME -> (value + 1L)
        )
      case None =>
        base
    }
  }

  private def _decode_entity(
      record: Record
  ): Consequence[TestEntity] =
    if (
      record.fields.exists(field =>
        Set(
          EntityConcurrencyMetadata.LOGICAL_FIELD_NAME,
          EntityConcurrencyMetadata.STORAGE_FIELD_NAME
        ).contains(field.key)
      )
    )
      Consequence.argumentInvalid(
        "entityRecord",
        "business record without concurrency metadata",
        record
      )
    else
      (record.getAny("id"), record.getString("name")) match {
        case (Some(id: EntityId), Some(name)) =>
          Consequence.success(TestEntity(id, name, None))
        case _ =>
          Consequence.argumentInvalid(
            "entityRecord",
            "EntityId and name",
            record
          )
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
