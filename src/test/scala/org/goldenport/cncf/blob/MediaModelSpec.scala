package org.goldenport.cncf.blob

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.builtin.blob.BlobComponent
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.id.{TextusUrn, UrnRepository}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.datatype.ContentType
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for built-in media entities over Blob metadata.
 *
 * @since   May.  3, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class MediaModelSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "MediaRepository" should {
    "create Image entity over Blob metadata and resolve image URN" in {
      Given("a managed image Blob")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val blobId = _blob_id("media_model_image_blob")
      val blob = _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = blobId,
        kind = BlobKind.Image,
        filename = Some("image.png"),
        contentType = ContentType.IMAGE_PNG,
        payload = Bag.binary("image".getBytes(StandardCharsets.UTF_8))
      ))
      val mediaRepository = MediaRepository.entityStore()

      When("ensuring an Image media entity")
      val media = _success(mediaRepository.ensureImageForBlob(blob, alt = Some("Alt"), title = Some("Title")))
      val urn = TextusUrn.image(media.id.parts.entropy)
      val resolution = _success(UrnRepository.from(MediaUrnResolver.all :+ new BlobUrnResolver).resolve(urn))

      Then("the Image points at the Blob and the URN resolves to the media Entity")
      media.kind shouldBe MediaKind.Image
      media.blobId shouldBe blobId
      media.alt shouldBe Some("Alt")
      media.title shouldBe Some("Title")
      resolution.map(_.entityId) shouldBe Some(media.id)
    }

    "keep generic Blob URN fallback for non-media Blob metadata" in {
      Given("a generic binary Blob")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val blobId = _blob_id("media_model_generic_blob")
      _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = blobId,
        kind = BlobKind.Binary,
        filename = Some("payload.bin"),
        contentType = ContentType.APPLICATION_OCTET_STREAM,
        payload = Bag.binary("payload".getBytes(StandardCharsets.UTF_8))
      ))

      When("resolving a generic Blob URN")
      val resolution = _success(UrnRepository.from(MediaUrnResolver.all :+ new BlobUrnResolver).resolve(TextusUrn.blob(blobId.parts.entropy)))

      Then("the URN resolves to the Blob Entity, not a media Entity")
      resolution.map(_.collection) shouldBe Some(BlobRepository.CollectionId)
      resolution.map(_.entityId) shouldBe Some(blobId)
    }
  }

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
