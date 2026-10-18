package org.goldenport.cncf.blob

import java.nio.file.{Files, Path}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{DataStore}
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Application-owned Blob and Media attributes over physical SQL text.
 *
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
final class BlobMetadataSqlPersistenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Blob and Media metadata" should {
    "retain exact attributes through repository create, provider shutdown and fresh SQLite reads" in {
      Given("a test-owned SQLite database and nonempty and empty String metadata for Blob and every Media kind")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase100/test-tmp")), "metadata-")
      val database = root.resolve("metadata.db")
      val writer = SqlDataStore.sqlite(database.toString)
      val expected = locally {
        given ExecutionContext = _context(writer)
        try {
          val blob = _take(BlobRepository.entityStore().create(_create(_attributes)))
          val media = MediaKind.values.toVector.flatMap { kind =>
            Vector(_attributes, Map.empty[String, String]).zipWithIndex.map { case (attributes, index) =>
              _take(MediaRepository.entityStore().create(_media_create(kind, index.toString, attributes)))
            }
          }
          val raw = _take(writer.load(DataStore.CollectionId.EntityStore(BlobRepository.CollectionId), DataStore.EntryId(blob.id))).get
          (blob, media, raw.getString("attributes"))
        } finally _take(writer.closeC())
      }

      When("new repository instances load metadata through a separately reopened physical provider")
      val reader = SqlDataStore.sqlite(database.toString)
      given ExecutionContext = _context(reader)
      try {
        val blob = _take(BlobRepository.entityStore().get(expected._1.id))
        val media = expected._2.map(value => _take(MediaRepository.entityStore().get(value.kind, value.id)))

        Then("the stored object wire and original Strings, identifiers, timestamps and other metadata survive")
        expected._3 shouldBe Some(Record.data(_attributes.toVector.sortBy(_._1)*).toJsonString)
        blob shouldBe expected._1
        blob.attributes shouldBe _attributes
        media shouldBe expected._2
        media.map(_.attributes) shouldBe MediaKind.values.toVector.flatMap(_ => Vector(_attributes, Map.empty[String, String]))
      } finally _take(reader.closeC())
    }

    "retain absent, empty and legacy application attributes without parsing their String members" in {
      Given("valid complete native Blob and Media records with missing, empty, Record and Map attributes")
      val blob = BlobRecordCodec.toRecord(_blob(_attributes))
      val media = MediaRecordCodec.toRecord(_media(_attributes))
      val cases = Vector(
        None -> Map.empty[String, String],
        Some("{}") -> Map.empty[String, String],
        Some(Record.empty) -> Map.empty[String, String],
        Some(Record.data(_attributes.toVector*)) -> _attributes,
        Some(_attributes) -> _attributes)

      When("both explicit application codecs read those existing representations")
      val loaded = cases.map { case (value, expected) =>
        val blobinput = value.fold(blob.removeKeys(Set("attributes")))(blob.upsertSingle("attributes", _))
        val mediainput = value.fold(media.removeKeys(Set("attributes")))(media.upsertSingle("attributes", _))
        (_take(BlobRecordCodec.fromStoreRecord(blobinput)).attributes,
          _take(MediaRecordCodec.fromStoreRecord(mediainput)).attributes, expected)
      }

      Then("missing and empty remain empty while native attribute values retain their exact original text")
      loaded.foreach { case (blobattributes, mediaattributes, expected) =>
        blobattributes shouldBe expected
        mediaattributes shouldBe expected
      }
    }

    "refuse malformed or noncanonical physical attributes instead of dropping metadata" in {
      Given("valid Blob and Media records whose known attributes column contains invalid canonical values")
      val invalid = Vector[Any]("{", "[]", "null", "42", "\"text\"", "{\"value\":null}", "{\"value\":3}", "{\"value\":{}}", 42)
      val blob = BlobRecordCodec.toRecord(_blob(_attributes))
      val media = MediaRecordCodec.toRecord(_media(_attributes))

      When("the selected owner codecs decode each corrupted attributes column")
      val failures = invalid.flatMap(value => Vector(
        BlobRecordCodec.fromStoreRecord(blob.upsertSingle("attributes", value)),
        MediaRecordCodec.fromStoreRecord(media.upsertSingle("attributes", value))))

      Then("every result is a structured failure and none is a successful entity with empty attributes")
      failures.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "roundtrip arbitrary String maps at the explicit attributes codec boundary" in {
      Given("generated bounded String maps containing empty, quoted, Unicode and JSON-looking text")
      val texts = Gen.oneOf(Gen.alphaNumStr, Gen.oneOf("", "  雪\n", "{\"x\":[]}", "null", "[ broken", "\\\"\t"))
      val maps = Gen.mapOf(Gen.zip(texts, texts))

      When("both physical writers and readers roundtrip each generated metadata map")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(maps) { attributes =>
        val blob = _blob(attributes)
        val media = _media(attributes)
        _take(BlobRecordCodec.fromStoreRecord(BlobRecordCodec.toStoreRecord(blob))) == blob &&
          _take(MediaRecordCodec.fromStoreRecord(MediaRecordCodec.toStoreRecord(media))) == media
      })

      Then("all generated values retain exact keys and String contents through JSON object text")
      result.passed shouldBe true
    }
  }

  private val _attributes = Map("空 白" -> "  雪\n\t", "json" -> " {\"x\":[1]} ", "null" -> "null", "empty" -> "", "quote" -> "\\\"")
  private val _instant = Instant.parse("2026-10-18T00:00:00Z")
  private def _id(collection: EntityCollectionId, entropy: String): EntityId =
    org.goldenport.cncf.EntityIdFixtureBridge.fromParts("cncf", "metadata", collection, entropy = entropy)

  private def _create(attributes: Map[String, String]): BlobCreate = BlobCreate(
    id = _id(BlobRepository.CollectionId, "blob"), kind = BlobKind.Binary, sourceMode = BlobSourceMode.Managed,
    filename = Some("metadata.txt"), contentType = None, byteSize = Some(7L), digest = Some("digest"),
    storageRef = Some(BlobStorageRef("blob", "metadata", "payload")), externalUrl = None,
    accessUrl = BlobAccessUrl("/display/metadata", "/download/metadata"), attributes = attributes)

  private def _blob(attributes: Map[String, String]): Blob = {
    val create = _create(attributes)
    Blob(create.id, create.kind, create.sourceMode, create.filename, create.contentType, create.byteSize,
      create.digest, create.storageRef, create.externalUrl, create.accessUrl, _instant, _instant, attributes)
  }

  private def _media_create(kind: MediaKind, suffix: String, attributes: Map[String, String]): MediaCreate = MediaCreate(
    id = _id(MediaEntityCollections.collection(kind), kind.print + suffix), kind = kind,
    blobId = _id(BlobRepository.CollectionId, "blob"), name = Some("media"), title = Some("title"),
    contentType = Some("application/octet-stream"), filename = Some("metadata.bin"), byteSize = Some(7L),
    digest = Some("digest"), accessUrl = Some("/display/metadata"), attributes = attributes)

  private def _media(attributes: Map[String, String]): MediaEntity = {
    val create = _media_create(MediaKind.Image, "codec", attributes)
    MediaEntity(create.id, create.kind, create.blobId, create.name, create.title, create.contentType,
      create.filename, create.byteSize, create.digest, create.accessUrl,
      createdAt = _instant, updatedAt = _instant, attributes = attributes)
  }

  private def _context(provider: DataStore): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(provider)
    context
  }
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }
}
