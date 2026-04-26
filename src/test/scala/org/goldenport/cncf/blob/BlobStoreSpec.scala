package org.goldenport.cncf.blob

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.datatype.ContentType
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the Phase 18 BL-02 BlobStore SPI baseline.
 *
 * @since   Apr. 26, 2026
 * @version Apr. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class BlobStoreSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "BlobKind" should {
    "parse the builtin Blob kinds" in {
      Given("the builtin kind names")
      val examples = Vector(
        "image" -> BlobKind.Image,
        "video" -> BlobKind.Video,
        "attachment" -> BlobKind.Attachment,
        "binary" -> BlobKind.Binary
      )

      When("parsing each name")
      val parsed = examples.map { case (name, _) => name -> BlobKind.parse(name) }

      Then("each name resolves to the corresponding BlobKind")
      parsed.zip(examples).foreach {
        case ((_, Consequence.Success(actual)), (_, expected)) =>
          actual shouldBe expected
        case ((name, other), _) =>
          fail(s"expected $name to parse but got $other")
      }
    }

    "reject unknown kind names deterministically" in {
      Given("an unknown kind name")
      val name = "spreadsheet"

      When("parsing the name")
      val result = BlobKind.parse(name)

      Then("the result is a deterministic validation failure")
      result shouldBe a[Consequence.Failure[_]]
    }
  }

  "BlobSourceMode" should {
    "parse the builtin source modes" in {
      Given("the builtin source-mode names")
      val examples = Vector(
        "managed" -> BlobSourceMode.Managed,
        "external_url" -> BlobSourceMode.ExternalUrl
      )

      When("parsing each name")
      val parsed = examples.map { case (name, _) => name -> BlobSourceMode.parse(name) }

      Then("each name resolves to the corresponding BlobSourceMode")
      parsed.zip(examples).foreach {
        case ((_, Consequence.Success(actual)), (_, expected)) =>
          actual shouldBe expected
        case ((name, other), _) =>
          fail(s"expected $name to parse but got $other")
      }
    }

    "accept hyphenated external-url spelling as input compatibility" in {
      Given("a hyphenated external-url source-mode name")
      val name = "external-url"

      When("parsing the name")
      val result = BlobSourceMode.parse(name)

      Then("the result is the canonical external_url mode")
      result shouldBe Consequence.success(BlobSourceMode.ExternalUrl)
    }

    "reject unknown source modes deterministically" in {
      Given("an unknown source mode")
      val name = "inline"

      When("parsing the name")
      val result = BlobSourceMode.parse(name)

      Then("the result is a deterministic validation failure")
      result shouldBe a[Consequence.Failure[_]]
    }
  }

  "InMemoryBlobStore" should {
    "store, read, and delete managed payloads without entity storage" in {
      Given("an in-memory BlobStore and a binary payload")
      val store = InMemoryBlobStore()
      val request = _request("blob-memory-1", Some("hello.txt"), ContentType.TEXT_PLAIN)
      val payload = Bag.binary("hello blob".getBytes(StandardCharsets.UTF_8))

      When("putting the payload")
      val put = store.put(request, payload)

      Then("the store returns metadata and CNCF route access URLs")
      val putResult = _success(put)
      putResult.id shouldBe request.id
      putResult.contentType shouldBe ContentType.TEXT_PLAIN
      putResult.byteSize shouldBe 10L
      putResult.digest shouldBe BlobStoreSupport.sha256("hello blob".getBytes(StandardCharsets.UTF_8))
      putResult.accessUrl.displayUrl should include ("/web/blob/content/")
      putResult.accessUrl.downloadUrl should include ("download=true")

      When("reading the payload")
      val read = _success(store.get(putResult.storageRef))

      Then("the same bytes and metadata are returned")
      _bytes(read.payload) shouldBe "hello blob".getBytes(StandardCharsets.UTF_8).toVector
      read.digest shouldBe putResult.digest
      read.byteSize shouldBe putResult.byteSize

      When("deleting the payload")
      val deleted = store.delete(putResult.storageRef)

      Then("the delete succeeds and later reads fail because the storage ref is no longer valid")
      deleted shouldBe Consequence.unit
      store.get(putResult.storageRef) shouldBe a[Consequence.Failure[_]]
    }

    "return backend URLs when configured" in {
      Given("an in-memory BlobStore with a backend URL")
      val store = InMemoryBlobStore(backendBaseUrl = Some("https://blob.example.test/assets"))
      val request = _request("blob-url-1", Some("image.png"), ContentType.IMAGE_PNG)

      When("putting a payload")
      val result = _success(store.put(request, Bag.binary(Array[Byte](1, 2, 3))))

      Then("the access URL is marked as backend-provided")
      result.accessUrl.urlSource shouldBe BlobAccessUrlSource.Backend
      result.accessUrl.displayUrl should startWith ("https://blob.example.test/assets/")
    }

    "reject storage refs from another store" in {
      Given("an in-memory BlobStore and a reference issued by another store")
      val store = InMemoryBlobStore(name = "memory-a")
      val request = _request("blob-memory-mismatch", Some("payload.bin"), ContentType.APPLICATION_OCTET_STREAM)
      val stored = _success(store.put(request, Bag.binary(Array[Byte](1, 2, 3))))
      val foreignref = stored.storageRef.copy(store = "memory-b")

      When("reading, deleting, or resolving URLs for the foreign reference")
      val read = store.get(foreignref)
      val delete = store.delete(foreignref)
      val url = store.accessUrl(foreignref)

      Then("each operation fails before touching local entries")
      read shouldBe a[Consequence.Failure[_]]
      delete shouldBe a[Consequence.Failure[_]]
      url shouldBe a[Consequence.Failure[_]]
      _success(store.get(stored.storageRef)).byteSize shouldBe 3L
    }
  }

  "LocalBlobStore" should {
    "persist payloads outside entity records and read them back" in {
      Given("a local BlobStore rooted at a temporary directory")
      val root = Files.createTempDirectory("cncf-blob-store-spec")
      val store = LocalBlobStore(root)
      val request = _request("blob-local-1", Some("note.txt"), ContentType.TEXT_PLAIN)
      val payload = Bag.binary("local blob".getBytes(StandardCharsets.UTF_8))

      When("putting the payload")
      val putResult = _success(store.put(request, payload))
      val storedPath = root.resolve(putResult.storageRef.container).resolve(putResult.storageRef.key)

      Then("the bytes are written under the BlobStore root, outside entity records")
      Files.exists(storedPath) shouldBe true
      Files.readAllBytes(storedPath).toVector shouldBe "local blob".getBytes(StandardCharsets.UTF_8).toVector

      When("reading the payload through the store")
      val read = _success(store.get(putResult.storageRef))

      Then("the same bytes and result metadata are returned")
      _bytes(read.payload) shouldBe "local blob".getBytes(StandardCharsets.UTF_8).toVector
      read.contentType shouldBe ContentType.TEXT_PLAIN
      read.digest shouldBe putResult.digest
    }

    "return Failure for missing storage references and I/O failures" in {
      Given("a local BlobStore and a missing storage reference")
      val root = Files.createTempDirectory("cncf-blob-store-missing-spec")
      val store = LocalBlobStore(root)
      val missingRef = BlobStorageRef("local", "default", "missing/payload.bin")

      When("reading the missing path")
      val missing = store.get(missingRef)

      Then("not found is represented as a Failure because BlobStorageRef is a committed reference")
      missing shouldBe a[Consequence.Failure[_]]

      Given("a storage reference whose path is a directory, not a readable payload file")
      val brokenRef = BlobStorageRef("local", "default", "broken/payload.bin")
      Files.createDirectories(root.resolve("default").resolve("broken").resolve("payload.bin"))

      When("reading the broken path")
      val broken = store.get(brokenRef)

      Then("the I/O error is preserved as a Failure")
      broken shouldBe a[Consequence.Failure[_]]
    }

    "reject storage refs from another store" in {
      Given("a local BlobStore and a reference issued by another store")
      val root = Files.createTempDirectory("cncf-blob-store-mismatch-spec")
      val store = LocalBlobStore(root, name = "local-a")
      val request = _request("blob-store-mismatch", Some("payload.bin"), ContentType.APPLICATION_OCTET_STREAM)
      val stored = _success(store.put(request, Bag.binary(Array[Byte](1, 2, 3))))
      val foreignRef = stored.storageRef.copy(store = "local-b")

      When("reading, deleting, or resolving URLs for the foreign reference")
      val read = store.get(foreignRef)
      val delete = store.delete(foreignRef)
      val url = store.accessUrl(foreignRef)

      Then("each operation fails before touching local payload paths")
      read shouldBe a[Consequence.Failure[_]]
      delete shouldBe a[Consequence.Failure[_]]
      url shouldBe a[Consequence.Failure[_]]
      _success(store.get(stored.storageRef)).byteSize shouldBe 3L
    }

    "reject empty and directory storage keys before delete" in {
      Given("a local BlobStore with an existing container directory")
      val root = Files.createTempDirectory("cncf-blob-store-key-spec")
      val store = LocalBlobStore(root)
      val request = _request("blob-key-safe", Some("payload.bin"), ContentType.APPLICATION_OCTET_STREAM)
      _success(store.put(request, Bag.binary(Array[Byte](1))))
      val containerRoot = root.resolve("default")
      Files.exists(containerRoot) shouldBe true

      When("deleting invalid refs that point at directories rather than object keys")
      val empty = store.delete(BlobStorageRef("local", "default", ""))
      val dot = store.delete(BlobStorageRef("local", "default", "."))
      val directory = store.delete(BlobStorageRef("local", "default", "nested/"))

      Then("the operations fail and the container directory remains")
      empty shouldBe a[Consequence.Failure[_]]
      dot shouldBe a[Consequence.Failure[_]]
      directory shouldBe a[Consequence.Failure[_]]
      Files.exists(containerRoot) shouldBe true
    }

    "fail instead of fabricating metadata when payload file exists without metadata" in {
      Given("a local BlobStore and a payload file without store metadata")
      val root = Files.createTempDirectory("cncf-blob-store-metadata-loss-spec")
      val store = LocalBlobStore(root)
      val ref = BlobStorageRef("local", "default", "orphan/payload.bin")
      val path = root.resolve("default").resolve("orphan").resolve("payload.bin")
      Files.createDirectories(path.getParent)
      Files.write(path, Array[Byte](1, 2, 3, 4))

      When("reading the payload through a formal storage ref")
      val result = store.get(ref)

      Then("the store fails because authoritative metadata is missing")
      result shouldBe a[Consequence.Failure[_]]
    }

    "report backend status" in {
      Given("a local BlobStore")
      val root = Files.createTempDirectory("cncf-blob-store-status-spec")
      val store = LocalBlobStore(root)

      When("checking status")
      val status = _success(store.status())

      Then("the status identifies the local backend and location")
      status.backend shouldBe "local"
      status.available shouldBe true
      status.location.getOrElse(fail("status location should be present")) should include (root.toAbsolutePath.normalize.toString)
    }
  }

  "BlobStoreSupport" should {
    "generate safe deterministic keys for a range of file names" in {
      Given("representative problematic filenames")
      val filenames = Vector(
        "../secret.txt",
        "/absolute/path/image.png",
        """..\windows\secret.txt""",
        "日本語 file.png",
        "spaces and symbols !@#.pdf"
      )
      val id = _id("Blob Key Spec")

      When("building storage keys")
      val keys = filenames.map(filename => BlobStoreSupport.keyFor(id, Some(filename)))

      Then("each key is relative and traversal-safe")
      keys.foreach { key =>
        key should not include ("..")
        key should not startWith ("/")
        key should not include ("\\")
      }
    }
  }

  private def _request(
    id: String,
    filename: Option[String],
    contentType: ContentType
  ): BlobPutRequest =
    BlobPutRequest(
      id = _id(id),
      kind = BlobKind.Binary,
      filename = filename,
      contentType = contentType
    )

  private val _collection_id: EntityCollectionId =
    EntityCollectionId("cncf", "builtin", "blob")

  private def _id(value: String): EntityId =
    EntityId("cncf", _label(value), _collection_id)

  private def _label(value: String): String =
    value.trim.toLowerCase(java.util.Locale.ROOT).map {
      case c if c.isLetterOrDigit => c
      case _ => '_'
    }.mkString.replaceAll("_+", "_").stripPrefix("_").stripSuffix("_") match {
      case "" => "blob"
      case s if s.head.isLetter => s
      case s => s"b_$s"
    }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private def _bytes(payload: org.goldenport.bag.BinaryBag): Vector[Byte] =
    payload.openInputStream().readAllBytes().toVector
}
