package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.simplemodeling.model.directive.Update

/*
 * @since   Jul. 24, 2026
 *  version Jul. 30, 2026
 * @version Aug.  5, 2026
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
  private val _optimistic_policy =
    EntityMutationExecutionPolicy(
      concurrencyPolicy = EntityConcurrencyPolicy.Optimistic
    )

  "EntityStore versioned mutation" should {
    "E3 advance full-save and typed-update revisions exactly once" must _e3_metadata {
      "when each mutation carries the current snapshot revision" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1-R5,R11-R14,R17; Example: E3; one newly created Entity"
        )
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val id = _id("full_and_typed")
        val created = fixture.entitystore.create(
          TestEntity(id, "created")
        )
        val initial = created.flatMap(_ =>
          fixture.entitystore.loadDetached[TestEntity](id)
        )

        When("full save and typed update use successive admitted expectations")
        val saved = initial.flatMap {
          case Some(snapshot) =>
            fixture.entitystore.saveDetached(
              snapshot.entity.copy(name = "saved"),
              Some(snapshot.revision),
              _optimistic_policy
            )
          case None =>
            Consequence.entityNotFound(id.print)
        }
        val updated = saved.flatMap { snapshot =>
          fixture.entitystore.updateDetached(
            snapshot.entity.copy(name = "updated"),
            Some(snapshot.revision),
            _optimistic_policy
          )
        }

        Then("each authoritative result advances one revision and storage contains the final value")
        initial.map(_.map(_.revision)) shouldBe
          Consequence.success(Some(EntityRevision.INITIAL))
        saved.map(_.revision.value) shouldBe Consequence.success(2L)
        updated.map(_.revision.value) shouldBe Consequence.success(3L)
        updated.map(_.entity.name) shouldBe Consequence.success("updated")
        _raw_record(fixture, id)
          .map(_.flatMap(_.getAny(EntityConcurrencyMetadata.STORAGE_FIELD_NAME))) shouldBe
          Consequence.success(Some(3L))
      }
    }

    "E3 apply patch-by-id with the same authoritative revision contract" must _e3_metadata {
      "when a generated patch carries the current revision" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1-R5,R11-R14,R17; Example: E3; one Entity snapshot and a typed patch"
        )
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val id = _id("patch")
        val initial =
          fixture.entitystore
            .create(TestEntity(id, "created"))
            .flatMap(_ => fixture.entitystore.loadDetached[TestEntity](id))

        When("the patch-by-id entry point executes")
        val patched = initial.flatMap {
          case Some(snapshot) =>
            fixture.entitystore.updateByIdDetached(
              id,
              TestPatch(Update.set("patched")),
              Some(snapshot.revision),
              _optimistic_policy
            )
          case None =>
            Consequence.entityNotFound(id.print)
        }
        val loaded = patched.flatMap(_ =>
          fixture.entitystore.loadDetached[TestEntity](id)
        )

        Then("the patch result and subsequent typed load expose revision two")
        patched.map(_.entity.getString("name")) shouldBe
          Consequence.success(Some("patched"))
        patched.map(_.revision.value) shouldBe Consequence.success(2L)
        loaded.map(_.map(_.entity.name)) shouldBe
          Consequence.success(Some("patched"))
        loaded.map(_.map(_.revision.value)) shouldBe
          Consequence.success(Some(2L))
      }
    }

    "E3 clear explicit patch fields through guarded versioned fallback" must _e3_metadata {
      "when a content update clears another existing domain field" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1-R5,R11-R14,R17; Example: E3; one Entity with content and a source URL"
        )
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val id = _id("guarded_set_null")
        val initial =
          fixture.entitystore
            .create(
              TestEntity(
                id,
                "created",
                content = Some("initial content"),
                sourceurl = Some("https://example.com/initial")
              )
            )
            .flatMap(_ => fixture.entitystore.loadDetached[TestEntity](id))

        When("a content patch uses the guarded versioned fallback and clears the source URL")
        val patched = initial.flatMap {
          case Some(snapshot) =>
            fixture.entitystore.updateByIdDetached(
              id,
              ContentPatch(
                content = Update.set("replacement content"),
                sourceurl = Update.setNull[String]
              ),
              Some(snapshot.revision),
              _optimistic_policy
            )
          case None =>
            Consequence.entityNotFound(id.print)
        }
        val stored = _raw_record(fixture, id)

        Then("the source URL is absent, replacement content is retained, and revision advances")
        patched.map(_.revision.value) shouldBe Consequence.success(2L)
        patched.map(_.entity.getString("content")) shouldBe
          Consequence.success(Some("replacement content"))
        stored.map(_.flatMap(_.getString("source_url"))) shouldBe
          Consequence.success(None)
        stored.map(_.flatMap(_.getString("content"))) shouldBe
          Consequence.success(Some("replacement content"))
      }
    }

    "E4 reject stale mutations with structured revision diagnostics" must _e4_metadata {
      "when two mutations reuse one admitted revision" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R5,R11-R14,R17,R19; Example: E4; two full saves derived from one snapshot"
        )
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val id = _id("stale")
        val initial =
          fixture.entitystore
            .create(TestEntity(id, "created"))
            .flatMap(_ => fixture.entitystore.loadDetached[TestEntity](id))
        val expectation = initial.toOption.flatten
          .map(snapshot => snapshot.revision)
          .getOrElse(fail("initial snapshot is required"))
        val first = fixture.entitystore.saveDetached(
          TestEntity(id, "winner"),
          Some(expectation),
          _optimistic_policy
        )

        When("the stale candidate reaches the provider boundary")
        val stale = first.flatMap(_ =>
          fixture.entitystore.saveDetached(
            TestEntity(id, "stale-candidate"),
            Some(expectation),
            _optimistic_policy
          )
        )
        val authoritative = fixture.entitystore.loadDetached[TestEntity](id)
        val conclusion = stale match {
          case Consequence.Failure(value) => value
          case other => fail(s"expected stale conflict but got $other")
        }

        Then("the candidate changes no state and the conflict carries safe expected and actual facets")
        authoritative.map(_.map(_.entity.name)) shouldBe
          Consequence.success(Some("winner"))
        authoritative.map(_.map(_.revision.value)) shouldBe
          Consequence.success(Some(2L))
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
    name: String,
    content: Option[String] = None,
    sourceurl: Option[String] = None
  )

  private final case class TestPatch(
    name: Update[String]
  ) extends EntityPersistableUpdate {
    def toRecord(): Record =
      Record.dataAuto("name" -> name)
  }

  private final case class ContentPatch(
    content: Update[String],
    sourceurl: Update[String]
  ) extends EntityPersistableUpdate {
    def toRecord(): Record =
      Record.dataAuto(
        "content" -> content,
        "source_url" -> sourceurl
      )
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

  private given EntityPersistentUpdate[ContentPatch] =
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
    EntityRevisionSpecSupport.registerRevisionBinding(
      context,
      _collection_id,
      summon[EntityPersistent[TestEntity]],
      EntityRevisionRepresentation.Detached,
      EntityConcurrencyPolicy.Optimistic
    )
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
    ) ++ Record.dataOption(
      "content" -> entity.content,
      "source_url" -> entity.sourceurl
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
