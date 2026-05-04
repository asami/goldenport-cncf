package org.goldenport.cncf.blob

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.association.{AssociationDomain, AssociationFilter, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.builtin.blob.BlobComponent
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.datatype.{ContentType, FileBundle}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for SimpleEntity content reference handling.
 *
 * @since   May.  3, 2026
 * @version May.  5, 2026
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
      result.normalizedText should include ("""src="urn:textus:image:""")
      result.references.map(x => (x.elementKind, x.attributeName, x.referenceKind)) shouldBe Vector(
        (Some("img"), Some("src"), Some("image")),
        (Some("a"), Some("href"), Some("external-url"))
      )
      result.references.head.targetEntityId.flatMap(EntityId.parse(_).toOption).map(_.collection.name) shouldBe Some("image")
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

      Then("occurrences remain duplicated but Association is Image-distinct")
      normalized.references.map(_.targetEntityId).flatten.distinct.size shouldBe 1
      result.associations.size shouldBe 1
      _associations("article-content-ref-attach").map(_.targetEntityId) shouldBe normalized.references.map(_.targetEntityId).flatten.distinct
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
      normalized.references.map(x => (x.elementKind, x.attributeName, x.referenceKind)) shouldBe Vector(
        (Some("img"), Some("src"), Some("image")),
        (Some("a"), Some("href"), Some("blob"))
      )
      result.associations.size shouldBe 1
      _associations("article-content-ref-link").map(_.targetEntityId) shouldBe normalized.references.head.targetEntityId.toVector
    }

    "normalize HTML video and source references to media URNs" in {
      Given("a filebundle with video, audio, and picture source files")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val root = Files.createTempDirectory("cncf-html-media-ref")
      val videos = Files.createDirectories(root.resolve("videos"))
      val audio = Files.createDirectories(root.resolve("audio"))
      val images = Files.createDirectories(root.resolve("images"))
      Files.write(videos.resolve("a.mp4"), "mp4".getBytes(StandardCharsets.UTF_8))
      Files.write(videos.resolve("b.webm"), "webm".getBytes(StandardCharsets.UTF_8))
      Files.write(audio.resolve("a.mp3"), "mp3".getBytes(StandardCharsets.UTF_8))
      Files.write(images.resolve("a.webp"), "webp".getBytes(StandardCharsets.UTF_8))
      val workflow = ContentReferenceWorkflow(component)

      When("normalizing HTML media references")
      val normalized = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.HtmlFragment,
        """<article>
          |  <video src="videos/a.mp4"></video>
          |  <video><source src="videos/b.webm" type="video/webm"></video>
          |  <audio><source src="audio/a.mp3"></audio>
          |  <picture><source src="images/a.webp" type="image/webp"></picture>
          |</article>""".stripMargin,
        fileBundle = Some(FileBundle.Directory(root))
      )))

      Then("each reference is rewritten to the inferred Textus media URN")
      normalized.normalizedText should include ("""<video src="urn:textus:video:""")
      normalized.normalizedText should include ("""src="urn:textus:video:""")
      normalized.normalizedText should include ("""type="video/webm"""")
      normalized.normalizedText should include ("""<source src="urn:textus:audio:""")
      normalized.normalizedText should include ("""src="urn:textus:image:""")
      normalized.normalizedText should include ("""type="image/webp"""")
      normalized.references.map(x => (x.elementKind, x.attributeName, x.referenceKind)) shouldBe Vector(
        (Some("video"), Some("src"), Some("video")),
        (Some("source"), Some("src"), Some("video")),
        (Some("source"), Some("src"), Some("audio")),
        (Some("source"), Some("src"), Some("image"))
      )
      normalized.references.map(_.targetEntityId.flatMap(EntityId.parse(_).toOption).map(_.collection.name)) shouldBe Vector(
        Some("video"),
        Some("video"),
        Some("audio"),
        Some("image")
      )
    }

    "normalize HTML download links to Attachment URNs and associations" in {
      Given("a downloadable local file in a filebundle")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val root = Files.createTempDirectory("cncf-html-download-ref")
      val docs = Files.createDirectories(root.resolve("docs"))
      Files.write(docs.resolve("a.pdf"), "pdf".getBytes(StandardCharsets.UTF_8))
      val workflow = ContentReferenceWorkflow(component)

      When("normalizing and attaching a download link")
      val normalized = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.HtmlFragment,
        """<article><a href="docs/a.pdf" download title="PDF">Download PDF</a></article>""",
        fileBundle = Some(FileBundle.Directory(root))
      )))
      val result = _success(workflow.attachReferences("article-content-ref-download", normalized.references))

      Then("the href is rewritten to an Attachment URN and attached with attachment role")
      normalized.normalizedText should include ("""href="urn:textus:attachment:""")
      normalized.references.map(x => (x.elementKind, x.attributeName, x.referenceKind, x.label, x.title)) shouldBe Vector(
        (Some("a"), Some("href"), Some("attachment"), Some("Download PDF"), Some("PDF"))
      )
      normalized.references.head.targetEntityId.flatMap(EntityId.parse(_).toOption).map(_.collection.name) shouldBe Some("attachment")
      result.associations.size shouldBe 1
      _associations("article-content-ref-download", "attachment", "attachment").map(_.targetEntityId) shouldBe normalized.references.head.targetEntityId.toVector
    }

    "leave HTML source failure comments for ambiguous or conflicting source refs" in {
      Given("source elements whose media kind cannot be safely inferred")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val root = Files.createTempDirectory("cncf-html-source-failure")
      val videos = Files.createDirectories(root.resolve("videos"))
      Files.write(videos.resolve("a.mp4"), "mp4".getBytes(StandardCharsets.UTF_8))
      val workflow = ContentReferenceWorkflow(component)

      When("normalizing ambiguous and conflicting source references")
      val normalized = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.HtmlFragment,
        """<article>
          |  <source src="videos/a.mp4">
          |  <video><source src="videos/a.mp4" type="audio/mpeg"></video>
          |</article>""".stripMargin,
        fileBundle = Some(FileBundle.Directory(root))
      )))

      Then("original refs are preserved with deterministic failure comments")
      normalized.normalizedText should include ("""src="videos/a.mp4"""")
      normalized.normalizedText should include ("textus:media-normalization-failed")
      normalized.references shouldBe Vector.empty
    }

    "leave HTML failure comments for external media without validating MIME" in {
      Given("an external image URL whose suffix is not an image MIME hint")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val repository = BlobRepository.entityStore()
      val workflow = ContentReferenceWorkflow(component, repository = repository)

      When("normalizing the external image")
      val normalized = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.HtmlFragment,
        """<article><img src="https://example.com/file.txt"></article>"""
      )))

      Then("the original reference is preserved and no Blob/Image metadata is created")
      normalized.normalizedText should include ("""src="https://example.com/file.txt"""")
      normalized.normalizedText should include ("textus:image-normalization-failed")
      normalized.references shouldBe Vector.empty
      _success(repository.list()) shouldBe Vector.empty
      _success(MediaRepository.entityStore().list(MediaKind.Image)) shouldBe Vector.empty
    }

    "leave HTML failure comment for non-image Blob refs in img/src without promoting them to Image media" in {
      Given("an attachment Blob referenced from img/src")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val attachmentId = _blob_id("content_ref_img_attachment")
      _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = attachmentId,
        kind = BlobKind.Attachment,
        filename = Some("document.pdf"),
        contentType = ContentType.APPLICATION_PDF,
        payload = Bag.binary("pdf".getBytes(StandardCharsets.UTF_8))
      ))
      val workflow = ContentReferenceWorkflow(component)

      When("normalizing an HTML image reference")
      val result = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.HtmlFragment,
        s"""<article><img src="/web/blob/content/${attachmentId.value}"></article>"""
      )))

      Then("normalization keeps the img source with a failure comment and no Image media entity is created")
      result.normalizedText should include (s"""src="/web/blob/content/${attachmentId.value}"""")
      result.normalizedText should include ("<!-- textus:image-normalization-failed:")
      result.references shouldBe Vector.empty
      _success(MediaRepository.entityStore().list(MediaKind.Image)) shouldBe Vector.empty
    }

    "compensate external Blob metadata when Image media creation fails" in {
      Given("a workflow whose media repository rejects Image creation")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val repository = BlobRepository.entityStore()
      val workflow = BlobInlineImageWorkflow(
        component,
        repository = repository,
        mediaRepository = new _FailingMediaRepository
      )

      When("normalizing an external image")
      val result = workflow.normalize(InlineImageContent(
        InlineImageMarkup.HtmlFragment,
        """<article><img src="https://example.com/inline.png"></article>"""
      ))

      Then("the operation fails and the just-created Blob metadata is removed")
      result shouldBe a[Consequence.Failure[_]]
      _success(repository.list()) shouldBe Vector.empty
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

    "normalize Markdown inline images and index inline links" in {
      Given("a managed Blob referenced from Markdown image and link syntax")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val blobId = _blob_id("content_ref_markdown_blob")
      _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = blobId,
        kind = BlobKind.Image,
        filename = Some("inline.png"),
        contentType = ContentType.IMAGE_PNG,
        payload = Bag.binary("inline".getBytes(StandardCharsets.UTF_8))
      ))
      val workflow = ContentReferenceWorkflow(component)

      When("normalizing Markdown content")
      val result = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.Markdown,
        s"""Intro ![Inline](/web/blob/content/${blobId.value} "Title") and [Doc](https://example.com/doc)."""
      )))

      Then("only the image destination is rewritten and both references are indexed in document order")
      result.normalizedText should include ("![Inline](urn:textus:image:")
      result.normalizedText should include ("[Doc](https://example.com/doc)")
      result.references.map(x => (x.markup, x.elementKind, x.attributeName, x.referenceKind)) shouldBe Vector(
        (Some("markdown-gfm"), Some("img"), Some("src"), Some("image")),
        (Some("markdown-gfm"), Some("a"), Some("href"), Some("external-url"))
      )
      result.references.head.alt shouldBe Some("Inline")
      result.references.head.title shouldBe Some("Title")
      result.references(1).label shouldBe Some("Doc")
    }

    "attach duplicate Markdown image references as one inline MediaAttachment" in {
      Given("two Markdown image occurrences pointing at the same Blob")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val blobId = _blob_id("content_ref_markdown_duplicate_blob")
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
        InlineImageMarkup.Markdown,
        s"""![A](/web/blob/content/${blobId.value})
           |
           |![B](/web/blob/content/${blobId.value})""".stripMargin
      )))

      When("attaching normalized references")
      val result = _success(workflow.attachReferences("article-content-ref-markdown-duplicate", normalized.references))

      Then("occurrences remain duplicated but the association is Image-distinct")
      normalized.references.size shouldBe 2
      normalized.references.map(_.targetEntityId).flatten.distinct.size shouldBe 1
      result.associations.size shouldBe 1
      _associations("article-content-ref-markdown-duplicate").map(_.targetEntityId) shouldBe normalized.references.map(_.targetEntityId).flatten.distinct
    }

    "index Markdown Blob links without creating inline image attachments" in {
      Given("an attachment Blob referenced from Markdown link syntax")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val attachmentId = _blob_id("content_ref_markdown_attachment")
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
        InlineImageMarkup.Markdown,
        s"""[PDF](/web/blob/content/${attachmentId.value})"""
      )))

      When("attaching normalized references")
      val result = _success(workflow.attachReferences("article-content-ref-markdown-link", normalized.references))

      Then("the link occurrence is indexed but no inline image association is created")
      normalized.normalizedText shouldBe s"""[PDF](/web/blob/content/${attachmentId.value})"""
      normalized.references.map(x => (x.elementKind, x.attributeName, x.referenceKind)) shouldBe Vector(
        (Some("a"), Some("href"), Some("blob"))
      )
      result.associations shouldBe Vector.empty
      _associations("article-content-ref-markdown-link") shouldBe Vector.empty
    }

    "normalize Markdown reference-style images and index reference-style links" in {
      Given("image and attachment Blobs referenced from Markdown reference definitions")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val imageId = _blob_id("content_ref_markdown_ref_image")
      val attachmentId = _blob_id("content_ref_markdown_ref_attachment")
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

      When("normalizing reference-style Markdown")
      val normalized = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.Markdown,
        s"""![Inline][img] and [PDF][doc]
           |
           |[img]: /web/blob/content/${imageId.value} "Title"
           |[doc]: /web/blob/content/${attachmentId.value}""".stripMargin
      )))

      Then("image definitions are rewritten and link definitions are only indexed")
      normalized.normalizedText should include ("[img]: urn:textus:image:")
      normalized.normalizedText should include (s"[doc]: /web/blob/content/${attachmentId.value}")
      normalized.references.map(x => (x.elementKind, x.attributeName, x.referenceKind)) shouldBe Vector(
        (Some("img"), Some("src"), Some("image")),
        (Some("a"), Some("href"), Some("blob"))
      )
      normalized.references.head.alt shouldBe Some("Inline")
      normalized.references.head.title shouldBe Some("Title")
      normalized.references(1).label shouldBe Some("PDF")
    }

    "normalize collapsed and shortcut Markdown image references as one image association" in {
      Given("collapsed, shortcut, and inline images pointing at the same Blob")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val blobId = _blob_id("content_ref_markdown_shortcut_image")
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
        InlineImageMarkup.Markdown,
        s"""![A][]
           |
           |![A]
           |
           |![B](/web/blob/content/${blobId.value})
           |
           |[A]: /web/blob/content/${blobId.value}""".stripMargin
      )))

      When("attaching normalized references")
      val result = _success(workflow.attachReferences("article-content-ref-markdown-shortcut", normalized.references))

      Then("occurrences are preserved while MediaAttachment is Image-distinct")
      normalized.normalizedText should include ("[A]: urn:textus:image:")
      normalized.normalizedText should include ("![B](urn:textus:image:")
      normalized.references.size shouldBe 3
      normalized.references.map(_.targetEntityId).flatten.distinct.size shouldBe 1
      result.associations.size shouldBe 1
      _associations("article-content-ref-markdown-shortcut").map(_.targetEntityId) shouldBe normalized.references.map(_.targetEntityId).flatten.distinct
    }

    "not rewrite a Markdown reference definition shared by an image and a link" in {
      Given("one reference definition used by both image and link syntax")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val blobId = _blob_id("content_ref_markdown_mixed_definition_image")
      _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = blobId,
        kind = BlobKind.Image,
        filename = Some("inline.png"),
        contentType = ContentType.IMAGE_PNG,
        payload = Bag.binary("inline".getBytes(StandardCharsets.UTF_8))
      ))
      val workflow = ContentReferenceWorkflow(component)

      When("normalizing Markdown with mixed image/link reference use")
      val normalized = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.Markdown,
        s"""![Preview][asset] and [Download][asset]
           |
           |[asset]: /web/blob/content/${blobId.value}""".stripMargin
      )))

      Then("the shared definition is left unchanged and only the link occurrence is indexed")
      normalized.normalizedText should include (s"[asset]: /web/blob/content/${blobId.value}")
      normalized.normalizedText should include ("textus:image-normalization-failed")
      normalized.normalizedText should not include "urn:textus:image:"
      normalized.references.map(x => (x.elementKind, x.attributeName, x.referenceKind, x.label)) shouldBe Vector(
        (Some("a"), Some("href"), Some("blob"), Some("Download"))
      )
    }

    "index Markdown autolinks without rewriting them" in {
      Given("a Markdown autolink")
      given ExecutionContext = ExecutionContext.create()
      val workflow = ContentReferenceWorkflow(_blob_component(InMemoryBlobStore()))

      When("normalizing Markdown")
      val normalized = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.Markdown,
        """See <https://example.com/autolink>."""
      )))

      Then("the autolink is indexed and the Markdown source is preserved")
      normalized.normalizedText shouldBe """See <https://example.com/autolink>."""
      normalized.references.map(x => (x.elementKind, x.attributeName, x.referenceKind, x.originalRef)) shouldBe Vector(
        (Some("a"), Some("href"), Some("external-url"), Some("https://example.com/autolink"))
      )
    }

    "register relative Markdown images from filebundle" in {
      Given("a filebundle with a relative image")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val root = Files.createTempDirectory("cncf-markdown-ref")
      val images = Files.createDirectories(root.resolve("images"))
      Files.write(images.resolve("a.png"), "png".getBytes(StandardCharsets.UTF_8))
      val workflow = ContentReferenceWorkflow(component)

      When("normalizing Markdown with a relative image")
      val result = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.Markdown,
        """![A](images/a.png)""",
        fileBundle = Some(FileBundle.Directory(root))
      )))

      Then("the image is stored as Blob/Image and Markdown points at the canonical Image URN")
      result.normalizedText should startWith ("![A](urn:textus:image:")
      result.references.map(x => (x.markup, x.elementKind, x.referenceKind)) shouldBe Vector(
        (Some("markdown-gfm"), Some("img"), Some("image"))
      )
      result.references.head.targetEntityId.flatMap(EntityId.parse(_).toOption).map(_.collection.name) shouldBe Some("image")
    }

    "leave Markdown failure comments for missing and non-image image sources" in {
      Given("a missing relative file and a non-image Blob")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val root = Files.createTempDirectory("cncf-markdown-ref-missing")
      val attachmentId = _blob_id("content_ref_markdown_img_attachment")
      _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = attachmentId,
        kind = BlobKind.Attachment,
        filename = Some("document.pdf"),
        contentType = ContentType.APPLICATION_PDF,
        payload = Bag.binary("pdf".getBytes(StandardCharsets.UTF_8))
      ))
      val workflow = ContentReferenceWorkflow(component)

      val missing = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.Markdown,
        """![Missing](images/missing.png)""",
        fileBundle = Some(FileBundle.Directory(root))
      )))
      missing.normalizedText should include ("![Missing](images/missing.png) <!-- textus:image-normalization-failed:")
      missing.references shouldBe Vector.empty

      val nonImage = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.Markdown,
        s"""![PDF](/web/blob/content/${attachmentId.value})"""
      )))
      nonImage.normalizedText should include ("<!-- textus:image-normalization-failed:")
      nonImage.references shouldBe Vector.empty
    }

    "keep Markdown-created images when a later image fails" in {
      Given("Markdown with one valid relative image before one missing image")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val root = Files.createTempDirectory("cncf-markdown-ref-compensate")
      val images = Files.createDirectories(root.resolve("images"))
      Files.write(images.resolve("a.png"), "png".getBytes(StandardCharsets.UTF_8))
      val repository = BlobRepository.entityStore()
      val mediaRepository = MediaRepository.entityStore()
      val workflow = ContentReferenceWorkflow(component, repository = repository, mediaRepository = mediaRepository)

      When("normalization succeeds with a failure note on the second image")
      val result = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.Markdown,
        """![A](images/a.png)
          |
          |![Missing](images/missing.png)""".stripMargin,
        fileBundle = Some(FileBundle.Directory(root))
      )))

      Then("the successful image remains normalized and the failed image is marked in Markdown")
      result.normalizedText should include ("![A](urn:textus:image:")
      result.normalizedText should include ("![Missing](images/missing.png) <!-- textus:image-normalization-failed:")
      result.references.size shouldBe 1
      _success(repository.list()).size shouldBe 1
      _success(mediaRepository.list(MediaKind.Image)).size shouldBe 1
    }

    "leave HTML failure comments while keeping successful images" in {
      Given("HTML with one valid relative image before one missing image")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val root = Files.createTempDirectory("cncf-html-ref-partial")
      val images = Files.createDirectories(root.resolve("images"))
      Files.write(images.resolve("a.png"), "png".getBytes(StandardCharsets.UTF_8))
      val workflow = ContentReferenceWorkflow(component)

      val result = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.HtmlFragment,
        """<article><img src="images/a.png"><img src="images/missing.png"></article>""",
        fileBundle = Some(FileBundle.Directory(root))
      )))

      result.normalizedText should include ("""src="urn:textus:image:""")
      result.normalizedText should include ("""src="images/missing.png"""")
      result.normalizedText should include ("<!-- textus:image-normalization-failed:")
      result.references.size shouldBe 1
    }

    "ignore Markdown image syntax inside fenced code" in {
      Given("Markdown with a fenced code sample and one real image")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val blobId = _blob_id("content_ref_markdown_code_blob")
      _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = blobId,
        kind = BlobKind.Image,
        filename = Some("inline.png"),
        contentType = ContentType.IMAGE_PNG,
        payload = Bag.binary("inline".getBytes(StandardCharsets.UTF_8))
      ))
      val workflow = ContentReferenceWorkflow(component)

      val result = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.Markdown,
        s"""```markdown
           |![code](/web/blob/content/${blobId.value})
           |```
           |
           |![real](/web/blob/content/${blobId.value})""".stripMargin
      )))

      result.normalizedText should include (s"![code](/web/blob/content/${blobId.value})")
      result.normalizedText should include ("![real](urn:textus:image:")
      result.references.size shouldBe 1
      result.references.head.alt shouldBe Some("real")
    }

    "extract SmartDox image and link references" in {
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val blobId = _blob_id("content_ref_smartdox_image")
      _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = blobId,
        kind = BlobKind.Image,
        filename = Some("inline.png"),
        contentType = ContentType.IMAGE_PNG,
        payload = Bag.binary("inline".getBytes(StandardCharsets.UTF_8))
      ))
      val workflow = ContentReferenceWorkflow(component)

      val result = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.SmartDox,
        s"""Intro [[/web/blob/content/${blobId.value}]] and [[https://example.com][external]]."""
      )))

      result.normalizedText should include ("[[urn:textus:image:")
      result.normalizedText should include ("[[https://example.com][external]]")
      result.references.map(x => (x.markup, x.elementKind, x.attributeName, x.referenceKind)) shouldBe Vector(
        (Some("smartdox"), Some("img"), Some("src"), Some("image")),
        (Some("smartdox"), Some("a"), Some("href"), Some("external-url"))
      )
      result.references.head.normalizedRef.getOrElse("") should startWith ("urn:textus:image:")
      result.references.head.targetEntityId.flatMap(EntityId.parse(_).toOption).map(_.collection.name) shouldBe Some("image")
    }

    "leave SmartDox failure comments while keeping successful image metadata" in {
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val root = Files.createTempDirectory("cncf-smartdox-ref-partial")
      val images = Files.createDirectories(root.resolve("images"))
      Files.write(images.resolve("a.png"), "png".getBytes(StandardCharsets.UTF_8))
      val workflow = ContentReferenceWorkflow(component)

      val result = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.SmartDox,
        """Intro [[images/a.png]] and [[images/missing.png]].""",
        fileBundle = Some(FileBundle.Directory(root))
      )))

      result.normalizedText should include ("[[urn:textus:image:")
      result.normalizedText should include ("[[images/missing.png]]\n# textus:image-normalization-failed:")
      result.normalizedText should include ("# textus:image-normalization-failed:")
      result.references.map(x => (x.markup, x.elementKind, x.attributeName, x.referenceKind)) shouldBe Vector(
        (Some("smartdox"), Some("img"), Some("src"), Some("image"))
      )
    }

    "not rewrite SmartDox image syntax inside source blocks or structured tokens" in {
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val workflow = ContentReferenceWorkflow(component)

      val result = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.SmartDox,
        """#+begin_src text
          |[[images/source.png]]
          |#+end_src
          |
          |<a>
          |  [[images/xml.png]]
          |</a>
          |
          |{
          |  "image": "[[images/json.png]]"
          |}
          |
          |> #+begin_src text
          |> [[images/quoted-source.png]]
          |> #+end_src
          |
          |> <a>
          |>   [[images/quoted-xml.png]]
          |> </a>
          |
          |> {
          |>   "image": "[[images/quoted-json.png]]"
          |> }
          |""".stripMargin
      )))

      result.normalizedText should include ("[[images/source.png]]")
      result.normalizedText should include ("[[images/xml.png]]")
      result.normalizedText should include ("[[images/json.png]]")
      result.normalizedText should include ("[[images/quoted-source.png]]")
      result.normalizedText should include ("[[images/quoted-xml.png]]")
      result.normalizedText should include ("[[images/quoted-json.png]]")
      result.normalizedText should not include ("urn:textus:image:")
      result.references shouldBe Vector.empty
    }

    "rewrite SmartDox structured prose image references with source spans" in {
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val blobId = _blob_id("content_ref_smartdox_structured_image")
      _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = blobId,
        kind = BlobKind.Image,
        filename = Some("inline.png"),
        contentType = ContentType.IMAGE_PNG,
        payload = Bag.binary("inline".getBytes(StandardCharsets.UTF_8))
      ))
      val workflow = ContentReferenceWorkflow(component)
      val source =
        s"""# [[/web/blob/content/${blobId.value}]]
           |
           |- [[/web/blob/content/${blobId.value}]]
           |
           || [[/web/blob/content/${blobId.value}]] |
           |
           |#+CAPTION: [[/web/blob/content/${blobId.value}]]
           |[[/web/blob/content/${blobId.value}]]
           |
           |> [[/web/blob/content/${blobId.value}]]
           |
           |Marked *[[/web/blob/content/${blobId.value}]]* but =[[/web/blob/content/${blobId.value}]]= stays inert.
           |""".stripMargin

      val result = _success(workflow.normalize(ContentReferenceContent(
        InlineImageMarkup.SmartDox,
        source
      )))

      result.normalizedText should not be source
      result.normalizedText should include ("# [[urn:textus:image:")
      result.normalizedText should include ("- [[urn:textus:image:")
      result.normalizedText should include ("| [[urn:textus:image:")
      result.normalizedText should include ("#+CAPTION: [[urn:textus:image:")
      result.normalizedText should include ("> [[urn:textus:image:")
      result.normalizedText should include ("Marked *[[urn:textus:image:")
      result.normalizedText should include (s"=[[/web/blob/content/${blobId.value}]]=")
      result.references.size shouldBe 7
      result.references.foreach(_.normalizedRef.getOrElse("") should startWith ("urn:textus:image:"))
    }
  }

  private def _associations(
    source: String
  )(using ExecutionContext): Vector[org.goldenport.cncf.association.Association] =
    _associations(source, "image", "inline")

  private def _associations(
    source: String,
    targetKind: String,
    role: String
  )(using ExecutionContext): Vector[org.goldenport.cncf.association.Association] =
    _success(
      AssociationRepository.entityStore(AssociationStoragePolicy.mediaAttachmentDefault).list(
        AssociationFilter(
          domain = AssociationDomain.MediaAttachment,
          sourceEntityId = Some(source),
          targetKind = Some(targetKind),
          role = Some(role)
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

  private final class _FailingMediaRepository extends MediaRepository {
    def create(media: MediaCreate)(using ExecutionContext): Consequence[MediaEntity] =
      Consequence.operationInvalid("media creation disabled")

    def get(kind: MediaKind, id: EntityId)(using ExecutionContext): Consequence[MediaEntity] =
      Consequence.operationNotFound(s"${kind.print} entity:${id.value}")

    def list(kind: MediaKind)(using ExecutionContext): Consequence[Vector[MediaEntity]] =
      Consequence.success(Vector.empty)

    def findByBlob(kind: MediaKind, blobId: EntityId)(using ExecutionContext): Consequence[Option[MediaEntity]] =
      Consequence.success(None)

    def ensureForBlobResult(kind: MediaKind, blob: Blob, alt: Option[String] = None, title: Option[String] = None)(using ExecutionContext): Consequence[MediaEnsureResult] =
      Consequence.operationInvalid("media creation disabled")

    override def ensureImageForBlobResult(blob: Blob, alt: Option[String] = None, title: Option[String] = None)(using ExecutionContext): Consequence[MediaEnsureResult] =
      ensureForBlobResult(MediaKind.Image, blob, alt, title)

    override def ensureImageForBlob(blob: Blob, alt: Option[String] = None, title: Option[String] = None)(using ExecutionContext): Consequence[MediaEntity] =
      ensureImageForBlobResult(blob, alt, title).map(_.media)

    def delete(media: MediaEntity)(using ExecutionContext): Consequence[Unit] =
      Consequence.unit
  }

  private def _blob_id(value: String): EntityId =
    EntityId("cncf", "builtin", EntityCollectionId("cncf", "builtin", "blob"), entropy = Some(value))

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }
}
