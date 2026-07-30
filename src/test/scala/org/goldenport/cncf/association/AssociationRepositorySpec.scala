package org.goldenport.cncf.association

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the generic Association runtime foundation.
 *
 * @since   Apr. 27, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class AssociationRepositorySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "AssociationRepository" should {
    "persist and query associations through domain-specific storage" in {
      Given("an AssociationRepository with Blob attachment storage policy")
      given ExecutionContext = ExecutionContext.test()
      val repository = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
      val sourceid = _source_id("product-1")
      val targetid = _blob_id("blob-1")

      When("creating a Blob attachment association")
      val created = _success(repository.create(
        AssociationCreate(
          id = None,
          associationId = "assoc-1",
          sourceEntityId = sourceid,
          targetEntityId = targetid,
          targetKind = Some("blob"),
          role = "mainImage",
          associationDomain = AssociationDomain.BlobAttachment,
          sortOrder = Some(1),
          collectionId = AssociationStoragePolicy.BlobAttachmentCollection
        )
      ))

      Then("the association is persisted as a framework entity")
      created.associationId shouldBe "assoc-1"
      created.id.collection shouldBe AssociationStoragePolicy.BlobAttachmentCollection

      When("querying by source and role")
      val values = _success(repository.list(
        AssociationFilter(
          domain = AssociationDomain.BlobAttachment,
          sourceEntityId = Some(sourceid),
          targetKind = Some("blob"),
          role = Some("mainImage")
        )
      ))

      Then("the created association is returned")
      values.map(_.targetEntityId) shouldBe Vector(targetid)
    }

    "return all matching associations from the store" in {
      Given("an AssociationRepository with Blob attachment storage policy")
      given ExecutionContext = ExecutionContext.test()
      val repository = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
      val sourceid = _source_id("article-1")

      When("creating multiple associations for the same source")
      (1 to 3).foreach { i =>
        _success(repository.create(
          AssociationCreate(
            id = None,
            associationId = s"assoc-ws-$i",
            sourceEntityId = sourceid,
            targetEntityId = _blob_id(s"blob-ws-$i"),
            targetKind = Some("blob"),
            role = "galleryImage",
            associationDomain = AssociationDomain.BlobAttachment,
            sortOrder = Some(i),
            collectionId = AssociationStoragePolicy.BlobAttachmentCollection
          )
        ))
      }

      Then("store-backed lookup returns every matching association")
      val values = _success(repository.list(
        AssociationFilter(
          domain = AssociationDomain.BlobAttachment,
          sourceEntityId = Some(sourceid),
          targetKind = Some("blob"),
          role = Some("galleryImage")
        )
      ))
      values.map(_.targetEntityId) shouldBe Vector(_blob_id("blob-ws-1"), _blob_id("blob-ws-2"), _blob_id("blob-ws-3"))
    }

    "delete only the association row" in {
      Given("an existing association")
      given ExecutionContext = ExecutionContext.test()
      val repository = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
      val sourceid = _source_id("product-delete")
      val targetid = _blob_id("blob-delete")
      val created = _success(repository.create(
        AssociationCreate(
          id = None,
          associationId = "assoc-delete",
          sourceEntityId = sourceid,
          targetEntityId = targetid,
          targetKind = Some("blob"),
          role = "attachment",
          associationDomain = AssociationDomain.BlobAttachment,
          sortOrder = None,
          collectionId = AssociationStoragePolicy.BlobAttachmentCollection
        )
      ))

      When("deleting the association")
      _success(repository.delete(created))

      Then("the association is no longer visible in normal list queries")
      val values = _success(repository.list(
        AssociationFilter(
          domain = AssociationDomain.BlobAttachment,
          sourceEntityId = Some(sourceid),
          targetKind = Some("blob"),
          role = Some("attachment")
        )
      ))
      values shouldBe Vector.empty
    }

    "reject an explicit association id from a foreign collection rather than rebinding it" in {
      Given("a Blob attachment Association repository and a foreign canonical EntityId")
      given ExecutionContext = ExecutionContext.test()
      val repository = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
      val foreign = EntityId("cncf", "association_foreign", EntityCollectionId("cncf", "builtin", "tag"))
      val target = EntityId("cncf", "target_foreign", EntityCollectionId("cncf", "builtin", "blob"))

      When("the foreign id is selected for a Blob attachment association")
      val result = repository.create(
        AssociationCreate(
          id = Some(foreign),
          associationId = "assoc-foreign",
          sourceEntityId = _source_id("source-foreign"),
          targetEntityId = target.value,
          targetKind = Some("blob"),
          role = "attachment",
          associationDomain = AssociationDomain.BlobAttachment,
          sortOrder = None,
          collectionId = AssociationStoragePolicy.BlobAttachmentCollection
        )
      )

      Then("creation fails and the association codec retains the canonical target string exactly")
      result shouldBe a[Consequence.Failure[_]]
      val association = Association(
        id = EntityId("cncf", "association_canonical", AssociationStoragePolicy.BlobAttachmentCollection),
        associationId = "assoc-canonical",
        sourceEntityId = _source_id("source-canonical"),
        targetEntityId = target.value,
        targetKind = Some("blob"),
        role = "attachment",
        associationDomain = AssociationDomain.BlobAttachment,
        sortOrder = None,
        createdAt = Instant.parse("2026-07-30T00:00:00Z"),
        updatedAt = Instant.parse("2026-07-30T00:00:00Z")
      )
      _success(AssociationRecordCodec.fromStoreRecord(AssociationRecordCodec.toStoreRecord(association))).targetEntityId shouldBe target.value
    }

    "reject scalar association references before persistence and storage decode" in {
      Given("an Association repository and a scalar reference payload")
      given ExecutionContext = ExecutionContext.test()
      val repository = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
      val scalar = AssociationCreate(
        id = None,
        associationId = "assoc-scalar",
        sourceEntityId = "source-scalar",
        targetEntityId = "target-scalar",
        targetKind = Some("blob"),
        role = "attachment",
        associationDomain = AssociationDomain.BlobAttachment,
        sortOrder = None,
        collectionId = AssociationStoragePolicy.BlobAttachmentCollection
      )
      val canonical = Association(
        id = EntityId("cncf", "association_decode", AssociationStoragePolicy.BlobAttachmentCollection),
        associationId = "assoc-decode",
        sourceEntityId = _source_id("decode-source"),
        targetEntityId = _blob_id("decode-target"),
        targetKind = Some("blob"),
        role = "attachment",
        associationDomain = AssociationDomain.BlobAttachment,
        sortOrder = None,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
      )

      When("scalar references are submitted at the persistence and decode boundaries")
      val created = repository.create(scalar)
      val decoded = AssociationRecordCodec.fromStoreRecord(
        AssociationRecordCodec.toStoreRecord(canonical).upsertSingle("sourceEntityId", "source-scalar")
      )

      Then("both boundaries reject them without creating or returning an association")
      created shouldBe a[Consequence.Failure[_]]
      decoded shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private def _source_id(value: String): String =
    _entity_id(EntityCollectionId("test", "association", "source"), value)

  private def _blob_id(value: String): String =
    _entity_id(EntityCollectionId("cncf", "builtin", "blob"), value)

  private def _entity_id(
    collection: EntityCollectionId,
    entropy: String
  ): String =
    EntityId(
      collection.major,
      collection.minor,
      collection,
      timestamp = Some(Instant.EPOCH),
      entropy = Some(entropy.replace('-', '_'))
    ).value
}
