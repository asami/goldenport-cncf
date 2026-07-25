package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
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
final class ContentBodyVersionedMutationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _e4_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E4, rules:R5,R11-R14,R17,R21, phase:49"
    )
  private val _e3_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E3, rules:R5,R11-R14,R21, phase:49"
    )

  "ContentBody versioned mutation" should {
    "E4 keep the authoritative overflow body when a stale save loses" must _e4_metadata {
      "when two large-content saves reuse one Entity snapshot revision" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R5,R11-R14,R17,R21; Example: E4; one overflow-backed Entity and two candidates"
        )
        val datastore = DataStore.inMemorySearchable()
        val context = ExecutionContext.create()
        context.dataStoreSpace.useDataStore(datastore)
        given ExecutionContext = context
        val entitystore = EntityStore.standard()
        val id = _id("stale-overflow")
        val initialcontent = _large_content("initial")
        val winnercontent = _large_content("winner")
        val stalecontent = _large_content("stale")
        val initial =
          entitystore
            .create(TestEntity(id, initialcontent))
            .flatMap(_ => entitystore.loadSnapshot[TestEntity](id))
        val expectation = initial.toOption.flatten
          .map(snapshot => snapshot.revision)
          .getOrElse(fail("initial snapshot is required"))

        When("the first save commits and the second reaches the same provider plan stale")
        val winner = entitystore.save(
          TestEntity(id, winnercontent),
          expectation
        )
        val stale = winner.flatMap(_ =>
          entitystore.save(
            TestEntity(id, stalecontent),
            expectation
          )
        )
        val loaded = entitystore.loadSnapshot[TestEntity](id)
        val overflow = datastore.load(
          _overflow_collection,
          DataStore.StringEntryId(s"${id.value}:content")
        )

        Then("the stale root and overflow side-record candidates are both discarded")
        winner.map(_.revision.value) shouldBe Consequence.success(2L)
        stale shouldBe a[Consequence.Failure[?]]
        loaded.map(_.map(_.revision.value)) shouldBe
          Consequence.success(Some(2L))
        loaded.map(_.map(_.entity.content)) shouldBe
          Consequence.success(Some(winnercontent))
        overflow.map(_.flatMap(_.getString("content"))) shouldBe
          Consequence.success(Some(winnercontent))
      }
    }

    "E3 plan overflow storage without performing an early datastore effect" must _e3_metadata {
      "when versioned persistence prepares a large content body" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R5,R11-R14,R21; Example: E3; one large candidate and an empty provider"
        )
        val datastore = DataStore.inMemorySearchable()
        val context = ExecutionContext.create()
        context.dataStoreSpace.useDataStore(datastore)
        given ExecutionContext = context
        val id = _id("pure-plan")
        val content = _large_content("planned")

        When("ContentBodyStoragePolicy creates the provider-neutral plan")
        val preparation = ContentBodyStoragePolicy.planForVersionedSave(
          id,
          Record.dataAuto("id" -> id, "content" -> content)
        )
        val overflow = datastore.load(
          _overflow_collection,
          DataStore.StringEntryId(s"${id.value}:content")
        )

        Then("the plan contains a side-record save but storage remains untouched")
        preparation.map(_.sideEffects.size) shouldBe Consequence.success(1)
        overflow shouldBe Consequence.success(None)
      }
    }
  }

  private val _collection_id =
    EntityCollectionId("test", "versioned", "content")
  private val _overflow_collection =
    DataStore.CollectionId("cncf_content_body_overflow")

  private final case class TestEntity(
    id: EntityId,
    content: String
  )

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
      "content" -> entity.content
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
      content <- record
        .getString("content")
        .map(Consequence.success)
        .getOrElse(Consequence.argumentMissing("content"))
    } yield TestEntity(id, content)

  private def _large_content(
    prefix: String
  ): String =
    s"$prefix:" + ("x" * 5000)
}
