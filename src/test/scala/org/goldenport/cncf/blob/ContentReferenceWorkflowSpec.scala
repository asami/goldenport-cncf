package org.goldenport.cncf.blob

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.association.{AssociationDomain, AssociationFilter, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.builtin.blob.BlobComponent
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.datatype.ContentType
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for SimpleEntity content reference handling.
 *
 * @since   May.  3, 2026
 * @version May.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class ContentReferenceWorkflowSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "ContentReferenceWorkflow" should {
    "normalize HTML img and link references into ContentReferenceOccurrence" in {
      Given("a managed Blob referenced from img and link tags")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val blobId = _blob_id("content_ref_normalize_blob")
      _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = blobId,
        kind = BlobKind.Image,
        filename = Some("inline.png"),
        contentType = ContentType.IMAGE_PNG,
        payload = Bag.binary("inline".getBytes(StandardCharsets.UTF_8))
      ))
      val workflow = ContentReferenceWorkflow(component)

      When("normalizing an HTML fragment")
      val result = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.HtmlFragment,
        s"""<article><img src="/web/blob/content/${blobId.value}" alt="Inline"><a href="https://example.com/doc">Doc</a></article>"""
      )))

      Then("the image is rewritten to Textus URN and both references are indexed")
      result.normalizedText should include (s"""src="urn:textus:blob:${blobId.parts.entropy}"""")
      result.references.map(x => (x.elementKind, x.attributeName, x.referenceKind)) shouldBe Vector(
        (Some("img"), Some("src"), Some("blob")),
        (Some("a"), Some("href"), Some("external-url"))
      )
      result.references.head.targetEntityId shouldBe Some(blobId.value)
      result.references(1).label shouldBe Some("Doc")
    }

    "attach duplicate Blob occurrences as one inline Association" in {
      Given("two content occurrences pointing at the same Blob")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val blobId = _blob_id("content_ref_attach_blob")
      _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = blobId,
        kind = BlobKind.Image,
        filename = Some("inline.png"),
        contentType = ContentType.IMAGE_PNG,
        payload = Bag.binary("inline".getBytes(StandardCharsets.UTF_8))
      ))
      val workflow = ContentReferenceWorkflow(component)
      val normalized = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.HtmlFragment,
        s"""<article><img src="/web/blob/content/${blobId.value}"><img src="/web/blob/content/${blobId.value}"></article>"""
      )))

      When("attaching references")
      val result = _success(workflow.attachReferences("article-content-ref-attach", normalized.references))

      Then("occurrences remain duplicated but Association is Blob-distinct")
      normalized.references.map(_.targetEntityId).flatten shouldBe Vector(blobId.value, blobId.value)
      result.associations.size shouldBe 1
      _associations("article-content-ref-attach").map(_.targetEntityId) shouldBe Vector(blobId.value)
    }

    "not attach Blob links as inline image Associations" in {
      Given("an image Blob in img/src and an attachment Blob in a/href")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val imageId = _blob_id("content_ref_link_image")
      val attachmentId = _blob_id("content_ref_link_attachment")
      _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = imageId,
        kind = BlobKind.Image,
        filename = Some("inline.png"),
        contentType = ContentType.IMAGE_PNG,
        payload = Bag.binary("inline".getBytes(StandardCharsets.UTF_8))
      ))
      _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = attachmentId,
        kind = BlobKind.Attachment,
        filename = Some("document.pdf"),
        contentType = ContentType.APPLICATION_PDF,
        payload = Bag.binary("pdf".getBytes(StandardCharsets.UTF_8))
      ))
      val workflow = ContentReferenceWorkflow(component)
      val normalized = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.HtmlFragment,
        s"""<article><img src="/web/blob/content/${imageId.value}"><a href="/web/blob/content/${attachmentId.value}">PDF</a></article>"""
      )))

      When("attaching content references")
      val result = _success(workflow.attachReferences("article-content-ref-link", normalized.references))

      Then("only the img/src Blob becomes an inline image association")
      normalized.references.map(x => (x.elementKind, x.attributeName, x.targetEntityId)) shouldBe Vector(
        (Some("img"), Some("src"), Some(imageId.value)),
        (Some("a"), Some("href"), Some(attachmentId.value))
      )
      result.associations.size shouldBe 1
      _associations("article-content-ref-link").map(_.targetEntityId) shouldBe Vector(imageId.value)
    }

    "validate inline image references before source mutation" in {
      given ExecutionContext = ExecutionContext.create()
      val workflow = ContentReferenceWorkflow(_blob_component(InMemoryBlobStore()))
      val missing = _blob_id("content_ref_missing_blob")
      val reference = org.goldenport.value.ContentReferenceOccurrence(
        contentField = Some("content"),
        markup = Some("html-fragment"),
        elementKind = Some("img"),
        attributeName = Some("src"),
        occurrenceIndex = 0,
        referenceKind = Some("blob"),
        targetEntityId = Some(missing.value)
      )

      workflow.validateInlineReferences(Vector(reference)) shouldBe a[Consequence.Failure[_]]
    }

    "reject Markdown and SmartDox normalization in v1" in {
      given ExecutionContext = ExecutionContext.create()
      val workflow = ContentReferenceWorkflow(_blob_component(InMemoryBlobStore()))

      workflow.normalize(ContentReferenceContent(InlineImageMarkup.Markdown, "![x](a.png)")) shouldBe a[Consequence.Failure[_]]
      workflow.normalize(ContentReferenceContent(InlineImageMarkup.SmartDox, "image::a.png")) shouldBe a[Consequence.Failure[_]]
    }
  }

  private def _associations(source: String)(using ExecutionContext) =
    _success(
      AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault).list(
        AssociationFilter(
          domain = AssociationDomain.BlobAttachment,
          sourceEntityId = Some(source),
          targetKind = Some("blob"),
          role = Some("inline")
        )
      )
    )

  private def _blob_component(
    store: BlobStore,
    maxsize: Long = 1024 * 1024
  ): Component = {
    val subsystem = DefaultSubsystemFactory.default(Some("command"))
    val component = subsystem.findComponent("blob").getOrElse(fail("missing Blob component"))
    component.withPort(Component.Port.of(new _BlobService(store, maxsize)))
    component
  }

  private final class _BlobService(
    store: BlobStore,
    maxsize: Long
  ) extends BlobComponent.BlobService {
    def blobStore: BlobStore = store
    def maxByteSize: Long = maxsize
  }

  private def _blob_id(value: String): EntityId =
    EntityId("cncf", "builtin", EntityCollectionId("cncf", "builtin", "blob"), entropy = Some(value))

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }
}
