package org.goldenport.cncf.blob

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.bag.Bag
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
 * Executable specification for the reusable Blob payload helper.
 *
 * @since   Apr. 29, 2026
 * @version May.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class BlobPayloadSupportSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "BlobPayloadSupport" should {
    "register and read managed Blob payloads through the builtin Blob service port" in {
      Given("a component in a subsystem with an in-memory Blob service")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val id = _blob_id("helper_managed_1")
      val bytes = "blog archive".getBytes(StandardCharsets.UTF_8)

      When("registering a payload through the reusable helper")
      val created = _success(BlobPayloadSupport.putManagedPayload(
        component = component,
        id = id,
        kind = BlobKind.Attachment,
        filename = Some("blog.zip"),
        contentType = ContentType.APPLICATION_OCTET_STREAM,
        payload = Bag.binary(bytes)
      ))

      Then("Blob metadata points at a managed store ref and uses the CNCF content route")
      created.id shouldBe id
      created.sourceMode shouldBe BlobSourceMode.Managed
      created.storageRef should not be empty
      created.accessUrl.displayUrl shouldBe s"/web/blob/content/${id.parts.entropy}"

      When("reading the payload through the same helper")
      val read = _success(BlobPayloadSupport.readManagedPayload(component, id))

      Then("the stored bytes are returned from BlobStore")
      read.id shouldBe id
      read.payload.openInputStream().readAllBytes().toVector shouldBe bytes.toVector
    }

    "reject external URL Blob metadata for managed payload reads" in {
      Given("an external URL Blob metadata record")
      given ExecutionContext = ExecutionContext.create()
      val component = _blob_component(InMemoryBlobStore())
      val id = _blob_id("helper_external_1")
      _success(BlobRepository.entityStore().create(BlobCreate(
        id = id,
        kind = BlobKind.Image,
        sourceMode = BlobSourceMode.ExternalUrl,
        filename = Some("remote.png"),
        contentType = Some(ContentType.IMAGE_PNG),
        byteSize = None,
        digest = None,
        storageRef = None,
        externalUrl = Some("https://example.com/remote.png"),
        accessUrl = BlobAccessUrl(
          displayUrl = "https://example.com/remote.png",
          downloadUrl = "https://example.com/remote.png",
          urlSource = BlobAccessUrlSource.Backend
        )
      )))

      When("requesting a managed payload read")
      val result = BlobPayloadSupport.readManagedPayload(component, id)

      Then("the helper returns a deterministic failure")
      result shouldBe a[Consequence.Failure[_]]
    }

    "delete a stored payload when post-write validation rejects it" in {
      Given("a Blob service whose max byte size is smaller than the payload")
      given ExecutionContext = ExecutionContext.create()
      val store = InMemoryBlobStore()
      val component = _blob_component(store, maxsize = 4)
      val id = _blob_id("helper_oversize_1")

      When("registering an oversized managed payload")
      val result = BlobPayloadSupport.putManagedPayload(
        component = component,
        id = id,
        kind = BlobKind.Attachment,
        filename = Some("oversize.zip"),
        contentType = ContentType.APPLICATION_OCTET_STREAM,
        payload = Bag.binary("oversize".getBytes(StandardCharsets.UTF_8))
      )

      Then("the helper fails and removes the already-written BlobStore payload")
      result shouldBe a[Consequence.Failure[_]]
      val ref = BlobStorageRef(store.name, BlobStorageRef.DefaultContainer, BlobStoreSupport.keyFor(id, Some("oversize.zip")))
      store.get(ref) shouldBe a[Consequence.Failure[_]]
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
    EntityId("cncf", value, EntityCollectionId("cncf", "builtin", "blob"))

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case other => fail(s"expected success but got $other")
    }
}
