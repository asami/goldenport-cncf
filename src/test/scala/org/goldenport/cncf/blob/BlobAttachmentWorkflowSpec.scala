package org.goldenport.cncf.blob

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.association.{AssociationDomain, AssociationFilter, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.datatype.{ContentType, MimeBody}
import org.goldenport.protocol.{Argument, Request}
import org.simplemodeling.model.datatype.EntityId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for BL-05B entity create/update Blob attachment workflow.
 *
 * @since   Apr. 27, 2026
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class BlobAttachmentWorkflowSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "BlobAttachmentWorkflow" should {
    "extract uploaded files and existing Blob id references from one request" in {
      Given("a multipart-style operation request with one upload and one existing Blob id")
      val existingId = _blob_entity_id("existing")
      val request = Request.of(
        component = "sample",
        service = "article",
        operation = "create",
        arguments = List(
          Argument("title", "Hello", None),
          Argument("blob.mainImage", MimeBody(ContentType.IMAGE_PNG, Bag.binary("image".getBytes(StandardCharsets.UTF_8))), None),
          Argument("blob.mainImage.filename", "cover.png", None),
          Argument("blobId.attachment", existingId.value, None)
        )
      )

      When("extracting Blob attachment parts")
      val extracted = _success(BlobAttachmentWorkflow.extract(request))

      Then("ordinary fields are ignored and Blob upload/reference parts are preserved")
      extracted.uploads.map(_.role) shouldBe Vector("mainImage")
      extracted.uploads.map(_.kind) shouldBe Vector(BlobKind.Image)
      extracted.uploads.flatMap(_.filename) shouldBe Vector("cover.png")
      extracted.references.map(x => x.role -> x.id.value) shouldBe Vector("attachment" -> existingId.value)
    }

    "extract hybrid imageAttachments rows with existing blob syntax" in {
      Given("a request using the structured imageAttachments rows and legacy blobId fields")
      val existingId = _blob_entity_id("existing_hybrid")
      val request = Request.of(
        component = "sample",
        service = "article",
        operation = "create",
        arguments = List(
          Argument("imageAttachments.0.role", "primary", None),
          Argument("imageAttachments.0.file", MimeBody(ContentType.IMAGE_PNG, Bag.binary("image".getBytes(StandardCharsets.UTF_8))), None),
          Argument("imageAttachments.0.file.filename", "primary.png", None),
          Argument("imageAttachments.0.sortOrder", "10", None),
          Argument("blobId.thumbnail", existingId.value, None)
        )
      )

      When("extracting Blob attachment parts")
      val extracted = _success(BlobAttachmentWorkflow.extract(request))

      Then("structured upload rows and legacy references are normalized together")
      extracted.uploads.map(x => (x.role, x.filename, x.sortOrder)) shouldBe Vector(("primary", Some("primary.png"), Some(10)))
      extracted.references.map(x => x.role -> x.id.value) shouldBe Vector("thumbnail" -> existingId.value)
    }

    "reject malformed imageAttachments rows deterministically" in {
      Given("a structured row that specifies both upload and existing Blob id")
      val existingId = _blob_entity_id("existing_invalid")
      val request = Request.of(
        component = "sample",
        service = "article",
        operation = "create",
        arguments = List(
          Argument("imageAttachments.0.role", "primary", None),
          Argument("imageAttachments.0.file", MimeBody(ContentType.IMAGE_PNG, Bag.binary("image".getBytes(StandardCharsets.UTF_8))), None),
          Argument("imageAttachments.0.blobId", existingId.value, None)
        )
      )

      When("extracting Blob attachment parts")
      val result = BlobAttachmentWorkflow.extract(request)

      Then("the row fails instead of selecting an arbitrary source")
      result shouldBe a[Consequence.Failure[_]]
    }

    "register uploaded files and attach uploaded plus existing Blob ids to an entity" in {
      Given("a workflow with an existing Blob and a request containing another upload")
      given ExecutionContext = ExecutionContext.test()
      val store = InMemoryBlobStore()
      val repository = BlobRepository.entityStore()
      val associations = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
      val workflow = BlobAttachmentWorkflow(store, repository, associations)
      val existing = _success(_create_managed_blob(repository, store, _new_blob_entity_id(), "existing.png"))
      val request = Request.of(
        component = "sample",
        service = "article",
        operation = "update",
        arguments = List(
          Argument("blob.galleryImage.0", MimeBody(ContentType.IMAGE_PNG, Bag.binary("new".getBytes(StandardCharsets.UTF_8))), None),
          Argument("blob.galleryImage.0.filename", "new.png", None),
          Argument("blob.galleryImage.0.sortOrder", "1", None),
          Argument("blobId.galleryImage.1", existing.id.value, None),
          Argument("blobId.galleryImage.1.sortOrder", "2", None)
        )
      )

      When("attaching Blob inputs to the entity")
      val summary = _success(workflow.attachToEntity("article-1", request))

      Then("both the uploaded Blob and existing Blob reference are attached")
      summary.uploaded.map(_.filename) shouldBe Vector(Some("new.png"))
      summary.referenced.map(_.id) shouldBe Vector(existing.id)
      summary.uploaded.map(_.id).intersect(summary.referenced.map(_.id)) shouldBe Vector.empty
      summary.associations should have size 2
      val listed = _success(associations.list(AssociationFilter(
        domain = AssociationDomain.BlobAttachment,
        sourceEntityId = Some("article-1"),
        targetKind = Some("blob"),
        role = Some("galleryImage")
      )))
      listed.map(_.targetEntityId) shouldBe Vector(summary.uploaded.head.id.value, existing.id.value)
    }

    "compensate newly uploaded Blobs when an existing Blob reference is invalid" in {
      Given("a workflow request with one upload followed by an invalid Blob id reference")
      given ExecutionContext = ExecutionContext.test()
      val store = InMemoryBlobStore()
      val repository = BlobRepository.entityStore()
      val associations = AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
      val workflow = BlobAttachmentWorkflow(store, repository, associations)
      val missing = _blob_entity_id("missing_reference")
      val request = Request.of(
        component = "sample",
        service = "article",
        operation = "create",
        arguments = List(
          Argument("blob.mainImage", MimeBody(ContentType.IMAGE_PNG, Bag.binary("new".getBytes(StandardCharsets.UTF_8))), None),
          Argument("blobId.attachment", missing.value, None)
        )
      )

      When("attaching Blob inputs to the entity")
      val result = workflow.attachToEntity("article-failure", request)

      Then("the operation fails and newly uploaded Blob metadata is removed")
      result shouldBe a[Consequence.Failure[_]]
      _success(repository.list()).map(_.filename) shouldBe Vector.empty
      _success(associations.list(AssociationFilter(
        domain = AssociationDomain.BlobAttachment,
        sourceEntityId = Some("article-failure"),
        targetKind = Some("blob")
      ))) shouldBe Vector.empty
    }

    "propagate create Entity compensation failures" in {
      Given("an Entity create succeeds but attachment and compensation both fail")
      given ExecutionContext = ExecutionContext.test()
      val workflow = BlobAttachmentWorkflow(
        InMemoryBlobStore(),
        BlobRepository.entityStore(),
        AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
      )
      val missing = _blob_entity_id("missing_create_reference")
      val request = Request.of(
        component = "sample",
        service = "article",
        operation = "create",
        arguments = List(Argument("blobId.primary", missing.value, None))
      )

      When("the create helper tries to compensate the created Entity")
      val result = workflow.createEntityWithBlobAttachments(request)(
        create = Consequence.success("article-created"),
        entityId = identity,
        compensateEntity = _ => Consequence.stateConflict("entity compensation failed")
      )

      Then("the compensation failure is not hidden by the attachment failure")
      result shouldBe a[Consequence.Failure[_]]
      _failure_message(result) should include ("entity compensation failed")
    }
  }

  private def _create_managed_blob(
    repository: BlobRepository,
    store: BlobStore,
    id: EntityId,
    filename: String
  )(using ExecutionContext): Consequence[Blob] =
    store.put(
      BlobPutRequest(id, BlobKind.Image, Some(filename), ContentType.IMAGE_PNG),
      Bag.binary(filename.getBytes(StandardCharsets.UTF_8))
    ).flatMap { put =>
      repository.create(
        BlobCreate(
          id = put.id,
          kind = BlobKind.Image,
          sourceMode = BlobSourceMode.Managed,
          filename = Some(filename),
          contentType = Some(put.contentType),
          byteSize = Some(put.byteSize),
          digest = Some(put.digest),
          storageRef = Some(put.storageRef),
          externalUrl = None,
          accessUrl = BlobUrl.cncfRoute(put.id)
        )
      )
    }

  private def _blob_entity_id(value: String): EntityId =
    EntityId(BlobRepository.CollectionId.major, value, BlobRepository.CollectionId)

  private def _new_blob_entity_id(): EntityId =
    EntityId(BlobRepository.CollectionId.major, BlobRepository.CollectionId.minor, BlobRepository.CollectionId)

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private def _failure_message[A](result: Consequence[A]): String =
    result match {
      case Consequence.Failure(conclusion) => conclusion.show
      case Consequence.Success(value) => fail(s"unexpected success: $value")
    }
}
