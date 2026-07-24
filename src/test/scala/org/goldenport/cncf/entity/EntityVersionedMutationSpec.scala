package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
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
final class EntityVersionedMutationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _e3_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E3, rules:R1-R5,R11-R14,R17, phase:49"
    )
  private val _e4_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E4, rules:R5,R11-R14,R17,R19, phase:49"
    )

  "EntityStore versioned mutation" should {
    "E3 advance full-save and typed-update revisions exactly once" must _e3_metadata {
      "when each mutation carries the current snapshot token" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1-R5,R11-R14,R17; Example: E3; one newly created Entity"
        )
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val id = _id("full-and-typed")
        val created = fixture.entitystore.create(
          TestEntity(id, "created")
        )
        val initial = created.flatMap(_ =>
          fixture.entitystore.loadSnapshot[TestEntity](id)
        )

        When("full save and typed update use successive admitted expectations")
        val saved = initial.flatMap {
          case Some(snapshot) =>
            fixture.entitystore.save(
              snapshot.entity.copy(name = "saved"),
              EntityMutationExpectation(snapshot.token)
            )
          case None =>
            Consequence.entityNotFound(id.print)
        }
        val updated = saved.flatMap { snapshot =>
          fixture.entitystore.update(
            snapshot.entity.copy(name = "updated"),
            EntityMutationExpectation(snapshot.token)
          )
        }

        Then("each authoritative result advances one revision and storage contains the final value")
        initial.map(_.map(_.token)) shouldBe
          Consequence.success(Some(EntityConcurrencyToken.INITIAL))
        saved.map(_.token.print) shouldBe Consequence.success("2")
        updated.map(_.token.print) shouldBe Consequence.success("3")
        updated.map(_.entity.name) shouldBe Consequence.success("updated")
        _raw_record(fixture, id)
          .map(_.flatMap(_.getAny(EntityConcurrencyMetadata.STORAGE_FIELD_NAME))) shouldBe
          Consequence.success(Some(3L))
      }
    }

    "E3 apply patch-by-id with the same authoritative revision contract" must _e3_metadata {
      "when a generated patch carries the current token" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1-R5,R11-R14,R17; Example: E3; one Entity snapshot and a typed patch"
        )
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val id = _id("patch")
        val initial =
          fixture.entitystore
            .create(TestEntity(id, "created"))
            .flatMap(_ => fixture.entitystore.loadSnapshot[TestEntity](id))

        When("the patch-by-id entry point executes")
        val patched = initial.flatMap {
          case Some(snapshot) =>
            fixture.entitystore.updateById(
              id,
              TestPatch(Update.set("patched")),
              EntityMutationExpectation(snapshot.token)
            )
          case None =>
            Consequence.entityNotFound(id.print)
        }
        val loaded = patched.flatMap(_ =>
          fixture.entitystore.loadSnapshot[TestEntity](id)
        )

        Then("the patch result and subsequent typed load expose revision two")
        patched.map(_.record.getString("name")) shouldBe
          Consequence.success(Some("patched"))
        patched.map(_.token.print) shouldBe Consequence.success("2")
        loaded.map(_.map(_.entity.name)) shouldBe
          Consequence.success(Some("patched"))
        loaded.map(_.map(_.token.print)) shouldBe
          Consequence.success(Some("2"))
      }
    }

    "E4 reject stale mutations with structured revision diagnostics" must _e4_metadata {
      "when two mutations reuse one admitted token" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R5,R11-R14,R17,R19; Example: E4; two full saves derived from one snapshot"
        )
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val id = _id("stale")
        val initial =
          fixture.entitystore
            .create(TestEntity(id, "created"))
            .flatMap(_ => fixture.entitystore.loadSnapshot[TestEntity](id))
        val expectation = initial.toOption.flatten
          .map(snapshot => EntityMutationExpectation(snapshot.token))
          .getOrElse(fail("initial snapshot is required"))
        val first = fixture.entitystore.save(
          TestEntity(id, "winner"),
          expectation
        )

        When("the stale candidate reaches the provider boundary")
        val stale = first.flatMap(_ =>
          fixture.entitystore.save(
            TestEntity(id, "stale-candidate"),
            expectation
          )
        )
        val authoritative = fixture.entitystore.loadSnapshot[TestEntity](id)
        val conclusion = stale match {
          case Consequence.Failure(value) => value
          case other => fail(s"expected stale conflict but got $other")
        }

        Then("the candidate changes no state and the conflict carries safe expected and actual facets")
        authoritative.map(_.map(_.entity.name)) shouldBe
          Consequence.success(Some("winner"))
        authoritative.map(_.map(_.token.print)) shouldBe
          Consequence.success(Some("2"))
        conclusion.observation.cause.descriptor.facets should contain (
          Descriptor.Facet.Reason("stale-entity-revision")
        )
        conclusion.observation.cause.descriptor.facets should contain (
          Descriptor.Facet.Expected(1L)
        )
        conclusion.observation.cause.descriptor.facets should contain (
          Descriptor.Facet.Actual(2L)
        )
      }
    }
  }

  private val _collection_id =
    EntityCollectionId("test", "versioned", "entity")

  private final case class TestEntity(
    id: EntityId,
    name: String
  )

  private final case class TestPatch(
    name: Update[String]
  ) extends EntityPersistableUpdate {
    def toRecord(): Record =
      Record.dataAuto("name" -> name)
  }

  private given EntityPersistentCreate[TestEntity] =
    new EntityPersistentCreate[TestEntity] {
      def id(entity: TestEntity): Option[EntityId] = Some(entity.id)
      def collection(entity: TestEntity): EntityCollectionId =
        entity.id.collection
      def toRecord(entity: TestEntity): Record =
        _entity_record(entity)
    }

  private given EntityPersistent[TestEntity] =
    new EntityPersistent[TestEntity] {
      def id(entity: TestEntity): EntityId = entity.id
      def toRecord(entity: TestEntity): Record =
        _entity_record(entity)
      def fromRecord(record: Record): Consequence[TestEntity] =
        _entity(record)
    }

  private given EntityPersistentUpdate[TestPatch] =
    EntityPersistentUpdate.derived(
      _ => Consequence.argumentInvalid("patch decoding is not used"),
      _collection_id
    )

  private final case class Fixture(
    datastore: DataStore,
    entitystore: EntityStore,
    context: ExecutionContext
  )

  private def _fixture(): Fixture = {
    val datastore = DataStore.inMemorySearchable()
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(datastore)
    Fixture(datastore, EntityStore.standard(), context)
  }

  private def _id(
    entropy: String
  ): EntityId =
    EntityId(
      "test",
      "versioned",
      _collection_id,
      entropy = Some(entropy)
    )

  private def _entity_record(
    entity: TestEntity
  ): Record =
    Record.dataAuto(
      "id" -> entity.id,
      "name" -> entity.name
    )

  private def _entity(
    record: Record
  ): Consequence[TestEntity] =
    for {
      id <- record.getAny("id") match {
        case Some(value: EntityId) => Consequence.success(value)
        case Some(value: String) => EntityId.parse(value)
        case other =>
          Consequence.argumentInvalid("id", "EntityId", other)
      }
      name <- record
        .getString("name")
        .map(Consequence.success)
        .getOrElse(Consequence.argumentMissing("name"))
    } yield TestEntity(id, name)

  private def _raw_record(
    fixture: Fixture,
    id: EntityId
  )(using
    ExecutionContext
  ): Consequence[Option[Record]] =
    fixture.context.entityStoreSpace
      .dataStoreCollection(id)
      .flatMap { collection =>
        fixture.context.entityStoreSpace
          .dataStoreEntryId(id)
          .flatMap(entry =>
            fixture.datastore.load(collection, entry)
          )
      }
}
