package org.goldenport.cncf.entity

import java.nio.file.{Files, Path}
import io.circe.{Decoder, Encoder, Json}
import org.goldenport.Consequence
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.{SqlDataStore, SqliteDialectDriver}
import org.goldenport.cncf.directive.Query
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.goldenport.schema.{Multiplicity, ValueDomain, XJson, XSemiStructuredData, XString, XDecimal}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.directive.Update

/*
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
final class Phase100EntityStoreMappingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import Phase100EntityStoreMappingFixture.*

  Vector("memory", "sqlite").foreach { backend =>
    s"Declared EntityStore mapping over $backend" should {
      "separate physical text from all five application types through create, load and logical search" in {
        Given("a test-owned provider and a five-field persistent with explicit logical/storage names")
        _with_provider(backend) { provider =>
          given ExecutionContext = _context(provider)
          given EntityPersistent[Record] = persistent
          given EntityPersistentCreate[Record] = EntityPersistentCreate.fromPersistent(persistent)
          val store = EntityStore.standard()
          val source = row("create", "  {\n\"original\": [1]} 雪  ")

          When("the standard EntityStore creates, loads and searches the declared structured payload")
          _take(store.create(source, EntityCreateOptions(Set("publication"))))
          val physical = _take(provider.load(address, DataStore.EntryId(id(source)))).get
          val restored = _take(store.load[Record](id(source))).get
          val found = _take(store.search[Record](EntityQuery(collection,
            Query.plan(Record.empty, where = Query.Eq("payload", source.getAny("payload").get)))))

          Then("DataStore returns Strings and EntityStore reconstructs distinct String, Json, Record, array and product values")
          Vector("original_text", "payload_json", "evidence_json", "labels_json", "summary_json").foreach { field =>
            physical.getAny(field).get shouldBe a[String]
          }
          if (backend == "sqlite")
            physical.getString("number_exact").map(BigDecimal.apply) shouldBe source.getAny("number")
          else physical.getAny("number_exact") shouldBe source.getAny("number")
          physical.getString("original_text") shouldBe source.getString("original")
          _assert_values(restored, source)
          found.data.map(id) shouldBe Vector(id(source))
          found.data.head.getAny("payload").get shouldBe a[Json]
        }
      }

      "encode raw partial updates once while preserving omission, explicit null and empty structured values" in {
        Given("a persisted declared row and a native raw Update patch")
        _with_provider(backend) { provider =>
          given ExecutionContext = _context(provider)
          given EntityPersistent[Record] = persistent
          given EntityPersistentUpdate[Record] = patchPersistent
          val store = EntityStore.standard()
          val source = row("patch", "  [original]  ")
          _take(store.save(source))
          val patch = Record.createFull(Vector(
            "original" -> Update.noop[String], "memo" -> Update.setNull[String],
            "evidence" -> Update.set(Record.empty), "labels" -> Update.set(Vector.empty[String])))

          When("standard partial update merges the encoded patch and the row is loaded")
          _take(store.updateByIdUnversioned(id(source), patch))
          val physical = _take(provider.load(address, DataStore.EntryId(id(source)))).get
          val restored = _take(store.load[Record](id(source))).get

          Then("the physical row has unique keys, the original is exact, null clears only memo, and empty Record/array remain present")
          physical.fields.map(_.key).distinct.size shouldBe physical.fields.size
          restored.getString("original") shouldBe source.getString("original")
          restored.getAny("memo") shouldBe None
          restored.getAny("evidence") shouldBe Some(Record.empty)
          restored.getAny("labels") shouldBe Some(Vector.empty)
          restored.getAny("payload") shouldBe source.getAny("payload")
          restored.getAny("summary") shouldBe source.getAny("summary")
        }
      }
    }
  }

  "Declared SQL readback" should {
    "restore all logical values after closing and reopening the physical store" in {
      Given("a test-owned SQLite file and a declared row persisted through standard EntityStore")
      val database = _database()
      val writer = SqlDataStore.jdbc(s"jdbc:sqlite:$database")
      val source = row("restart", "  { original formatting }\n雪  ")
      locally {
        given ExecutionContext = _context(writer)
        given EntityPersistent[Record] = persistent
        try _take(EntityStore.standard().save(source))
        finally _take(writer.closeC())
      }

      When("a newly opened provider and standard EntityStore load the same canonical identity")
      val reader = SqlDataStore.jdbc(s"jdbc:sqlite:$database")
      given ExecutionContext = _context(reader)
      given EntityPersistent[Record] = persistent
      try {
        val restored = _take(EntityStore.standard().load[Record](id(source))).get

        Then("the exact String and declared application types survive durable readback")
        _assert_values(restored, source)
      } finally _take(reader.closeC())
    }

    "reject malformed mapped payloads without guessing a fallback application value" in {
      Given("a declared row whose physical JSON payload is then corrupted through the raw DataStore API")
      _with_provider("sqlite") { provider =>
        given ExecutionContext = _context(provider)
        given EntityPersistent[Record] = persistent
        val source = row("corrupt", "{still an exact String}")
        val store = EntityStore.standard()
        _take(store.save(source))
        val physical = _take(provider.load(address, DataStore.EntryId(id(source)))).get
        _take(provider.save(address, DataStore.EntryId(id(source)), physical.upsertSingle("payload_json", "{")))

        When("the ordinary EntityStore load reaches the selected JSON codec")
        val result = store.load[Record](id(source))

        Then("the typed operation fails and the diagnostic identifies the mapped parse boundary")
        result shouldBe a[Consequence.Failure[?]]
        val facets = result match {
          case Consequence.Failure(conclusion) =>
            conclusion.causes.toVector.flatMap(_.observation.cause.descriptor.facets)
          case _ => fail("Expected structured mapping failure")
        }
        facets should contain(Descriptor.Facet.Reason("malformed-stored-json"))
        facets should contain(Descriptor.Facet.FieldPath("payload"))
      }
    }
  }

  Vector(false, true).foreach { normalized =>
    s"Declared storage aliases with SQL normalization=$normalized" should {
      "retain exact mapping identity across save, patch, query and provider reopen" in {
        Given("explicit snake_case storage aliases and the selected SQL key policy")
        val database = _database()
        val writer = SqlDataStore.jdbc(s"jdbc:sqlite:$database", config = SqlDataStore.Config(normalized))
        val source = row("normalized", "  { original String } 雪\n ").upsertSingle("postStatus", "published")
        val replacement = io.circe.Json.arr(io.circe.Json.fromString("replacement"))
        locally {
          given ExecutionContext = _context(writer)
          given EntityPersistent[Record] = persistent
          given EntityPersistentUpdate[Record] = patchPersistent
          try {
            When("mapped save and patch pass through the configured provider")
            val store = EntityStore.standard()
            _take(store.save(source))
            _take(store.updateByIdUnversioned(id(source), Record.createFull(Vector("payload" -> Update.set(replacement)))))
            val physical = _take(writer.load(address, DataStore.EntryId(id(source)))).get

            Then("the physical row uses its provider key and preserves exact text")
            physical.getString(if (normalized) "originalText" else "original_text") shouldBe source.getString("original")
          } finally _take(writer.closeC())
        }

        When("a fresh provider loads and searches the same declared storage aliases")
        val reader = SqlDataStore.jdbc(s"jdbc:sqlite:$database", config = SqlDataStore.Config(normalized))
        given ExecutionContext = _context(reader)
        given EntityPersistent[Record] = persistent
        try {
          val store = EntityStore.standard()
          val restored = _take(store.load[Record](id(source))).get
          val found = _take(store.search[Record](EntityQuery(collection,
            Query.plan(Record.empty, where = Query.Eq("payload", replacement)))))

          Then("the logical keys and JSON type are restored independently of provider spelling")
          restored.getString("original") shouldBe source.getString("original")
          restored.getAny("payload") shouldBe Some(replacement)
          restored.getAny("evidence") shouldBe source.getAny("evidence")
          found.data.map(id) shouldBe Vector(id(source))
        } finally _take(reader.closeC())
      }
    }
  }

  "SQL mapping name collisions" should {
    "reject the complete declaration and direct duplicate physical columns before storing a row" in {
      Given("two distinct declared storage names which normalize to the same SQLite column")
      val provider = SqlDataStore.jdbc(s"jdbc:sqlite:${_database()}", config = SqlDataStore.Config(true))
      given ExecutionContext = _context(provider)
      val source = Record.dataAuto("id" -> row("collision", "").getString("id").get,
        "first" -> "one", "second" -> "two")
      given EntityPersistent[Record] = new EntityPersistent[Record] {
        def id(e: Record): EntityId = Phase100EntityStoreMappingFixture.id(e)
        def toRecord(e: Record): Record = e
        def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
        override def storeRowMapping: Option[EntityStoreRowMapping] = Some(EntityStoreRowMapping(Vector(
          EntityStoreField("first", "displayName", ValueDomain(XString, Multiplicity.One), EntityStoreFieldBinding.string),
          EntityStoreField("second", "display_name", ValueDomain(XString, Multiplicity.One), EntityStoreFieldBinding.string)), Set("id")))
      }
      try {
        When("the mapped operation evaluates the full projected declaration")
        val mapped = EntityStore.standard().save(source)
        val mappedrow = _take(provider.load(address, DataStore.EntryId(id(source))))

        Then("the mapping is rejected with no persisted value")
        mapped shouldBe a[Consequence.Failure[?]]
        mappedrow shouldBe None

        When("a direct physical caller supplies both normalized aliases")
        val direct = provider.save(address, DataStore.EntryId(id(source)),
          Record.dataAuto("displayName" -> "one", "display_name" -> "two"))
        val directrow = _take(provider.load(address, DataStore.EntryId(id(source))))

        Then("SQL refuses the duplicate columns instead of keeping the last value")
        direct shouldBe a[Consequence.Failure[?]]
        directrow shouldBe None
      } finally _take(provider.closeC())
    }
  }

  "Provider mapping projection failures" should {
    "return a structured save failure without publishing physical schema or rows" in {
      Given("a declared row and an injected SQLite provider whose key projection throws")
      val database = _database()
      val datasource = new org.sqlite.SQLiteDataSource()
      datasource.setUrl(s"jdbc:sqlite:$database")
      val provider = new SqlDataStore(SqliteDialectDriver, datasource) {
        override def recordKey(storageName: String): String =
          throw new IllegalStateException("fixture provider key projection failure")
      }
      given ExecutionContext = _context(provider)
      given EntityPersistent[Record] = persistent
      val source = row("projectionfailure", "exact original")
      def _schema_(): Vector[(String, String)] = {
        val connection = datasource.getConnection()
        try {
          val statement = connection.createStatement()
          try {
            val rows = statement.executeQuery("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name")
            try {
              val tables = Vector.newBuilder[(String, String)]
              while (rows.next()) tables += ((rows.getString(1), rows.getString(2)))
              tables.result()
            } finally rows.close()
          } finally statement.close()
        } finally connection.close()
      }
      try {
        val before = _schema_()
        before shouldBe Vector.empty

        When("ordinary EntityStore save evaluates the selected provider projection")
        val result = EntityStore.standard().save(source)

        Then("the exception becomes Consequence failure and no physical schema has been published")
        result shouldBe a[Consequence.Failure[?]]
        _schema_() shouldBe before

        When("the raw provider independently observes the selected identity afterward")
        val physical = _take(provider.load(address, DataStore.EntryId(id(source))))

        Then("there is no physical row from the rejected save and the schema remains unchanged")
        physical shouldBe None
        _schema_() shouldBe before
      } finally _take(provider.closeC())
    }
  }

  "Shared merge projection failures" should {
    Vector("update", "delete").foreach { operation =>
      s"return a structured $operation failure while retaining the complete physical row" in {
        Given("a raw SQLite row with live lifecycle state and a provider whose merge key projection throws")
        val database = _database()
        val datasource = new org.sqlite.SQLiteDataSource()
        datasource.setUrl(s"jdbc:sqlite:$database")
        val provider = new SqlDataStore(SqliteDialectDriver, datasource) {
          override def recordKey(storageName: String): String =
            throw new IllegalStateException("fixture provider merge projection failure")
        }
        given ExecutionContext = _context(provider)
        val sourceid = id(row("mergefailure" + operation, "exact original"))
        val entryid = DataStore.EntryId(sourceid)
        try {
          _take(provider.save(address, entryid, Record.dataAuto(
            "original_text" -> "exact original", "post_status" -> "draft", "aliveness" -> "alive")))
          val before = _take(provider.load(address, entryid)).get
          before.getString("original_text") shouldBe Some("exact original")
          before.getString("aliveness") shouldBe Some("alive")

          When(s"ordinary EntityStore $operation reaches the shared merge projection")
          val result = operation match {
            case "update" =>
              given EntityPersistentUpdate[Record] = new EntityPersistentUpdate[Record] {
                def collection(e: Record): EntityCollectionId = Phase100EntityStoreMappingFixture.collection
                def toRecord(e: Record): Record = e
                def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
              }
              EntityStore.standard().updateByIdUnversioned(sourceid, Record.dataAuto("original_text" -> "changed"))
            case "delete" => EntityStore.standard().delete(sourceid)
          }

          Then("the projection exception becomes a structured failure without any domain or lifecycle write")
          result shouldBe a[Consequence.Failure[?]]
          val physical = _take(provider.load(address, entryid)).get
          physical.asMap shouldBe before.asMap
          physical.getString("original_text") shouldBe Some("exact original")
          physical.getString("aliveness") shouldBe Some("alive")
        } finally _take(provider.closeC())
      }
    }
  }

  "Declared query admission" should {
    "reject incomplete metadata before field translation or physical query preparation" in {
      Given("a declared adapter whose mapping is missing and whose field translation must never run")
      var translations = 0
      val invalid = new EntityPersistent[Record] {
        def id(e: Record): EntityId = Phase100EntityStoreMappingFixture.id(e)
        def toRecord(e: Record): Record = e
        def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
        override def storeRowMapping: Option[EntityStoreRowMapping] = Some(null)
        override def storeFieldName(name: String): String = {
          translations += 1
          throw new IllegalStateException("query translation reached before declaration admission")
        }
      }
      given EntityPersistent[Record] = invalid
      given ExecutionContext = ExecutionContext.create()
      val store = EntityStore.standard()
      val query = EntityQuery[Record](collection, Query.plan(Record.empty))

      When("ordinary and internal queries are submitted")
      val results = Vector(store.search(query), store.searchInternal(query))

      Then("both fail through structured declaration validation before accessing a provider")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      translations shouldBe 0
    }
  }

  "The standard mapped save/load route" should {
    "preserve generated Unicode and JSON-looking String content over a physical provider" in {
      Given("a searchable physical memory provider and a public String generator")
      _with_provider("memory") { provider =>
        given ExecutionContext = _context(provider)
        given EntityPersistent[Record] = persistent
        val store = EntityStore.standard()
        val property = Prop.forAll(Gen.alphaNumStr) { value =>
          val source = row("property", s"  {\"value\":\"$value\"} 雪\n ")
          _take(store.save(source))
          _take(store.load[Record](id(source))).get.getString("original") == source.getString("original")
        }

        When("ScalaCheck performs at least fifty standard save/load roundtrips")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("the String law holds without discards")
        withClue(checked.toString) { checked.passed shouldBe true }
        checked.succeeded should be >= 50
        checked.discarded shouldBe 0
      }
    }
  }

  private def _assert_values(actual: Record, expected: Record): Unit =
    Vector("original", "payload", "evidence", "labels", "summary", "number").foreach { name =>
      withClue(name) { actual.getAny(name) shouldBe expected.getAny(name) }
    }

  private def _database(): Path = {
    val root = Files.createDirectories(Path.of("target/phase100/store-mapping"))
    Files.createTempDirectory(root, "sql-").toAbsolutePath.resolve("application.db")
  }

  private def _with_provider(backend: String)(run: DataStore => Unit): Unit = {
    val provider = if (backend == "sqlite") SqlDataStore.jdbc(s"jdbc:sqlite:${_database()}")
      else new DataStore.InMemorySearchableDataStore(CommitRecorder.noop)
    try run(provider)
    finally provider match {
      case sql: SqlDataStore => _take(sql.closeC())
      case _ => ()
    }
  }

  private def _context(provider: DataStore): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(provider)
    context
  }

  private def _take[A](result: Consequence[A]): A = result.getOrElse(fail(result.toString))
}

object Phase100EntityStoreMappingFixture {
  final case class Summary(label: String)
  private given _summary_encoder: Encoder[Summary] = Encoder.forProduct1("label")(_.label)
  private given _summary_decoder: Decoder[Summary] = Decoder.forProduct1("label")(Summary.apply)
  val collection: EntityCollectionId = EntityCollectionId("test", "phase100", "mapped")
  val address: DataStore.CollectionId = DataStore.CollectionId.EntityStore(collection)
  private val _summary_binding = EntityStoreFieldBinding.application[Summary]("phase100-summary")
  private val _fields = Vector(
    EntityStoreField("original", "original_text", ValueDomain(XString, Multiplicity.One), EntityStoreFieldBinding.string),
    EntityStoreField("payload", "payload_json", ValueDomain(XJson, Multiplicity.One), EntityStoreFieldBinding.json),
    EntityStoreField("evidence", "evidence_json", ValueDomain(XSemiStructuredData, Multiplicity.One), EntityStoreFieldBinding.semiStructuredData),
    EntityStoreField("labels", "labels_json", ValueDomain(XString, Multiplicity.ZeroMore), EntityStoreFieldBinding.array(EntityStoreFieldBinding.string)),
    EntityStoreField("summary", "summary_json", ValueDomain(_summary_binding.datatype, Multiplicity.One), _summary_binding),
    EntityStoreField("number", "number_exact", ValueDomain(XDecimal, Multiplicity.One),
      EntityStoreFieldBinding.nativeScalar[BigDecimal](XDecimal, EntityStoreScalarRepresentation.Number, "precise-decimal")),
    EntityStoreField("memo", "memo_text", ValueDomain(XString, Multiplicity.ZeroOne), EntityStoreFieldBinding.string))
  private val _framework = (SimpleEntityStorageShapePolicy.managementLogicalFields ++
    SimpleEntityStorageShapePolicy.securityIdentityLogicalFields ++ Vector("name", "permission"))
    .flatMap(name => Vector(name, SimpleEntityStorageShapePolicy.targetName(name))).toSet
  val mapping: EntityStoreRowMapping = EntityStoreRowMapping(_fields, _framework)
  val persistent: EntityPersistent[Record] = new EntityPersistent[Record] {
    def id(e: Record): EntityId = Phase100EntityStoreMappingFixture.id(e)
    def toRecord(e: Record): Record = e
    def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
    override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
  }
  val patchPersistent: EntityPersistentUpdate[Record] = new EntityPersistentUpdate[Record] {
    def collection(e: Record): EntityCollectionId = Phase100EntityStoreMappingFixture.collection
    def toRecord(e: Record): Record = e
    def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
    override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
  }
  def id(record: Record): EntityId = EntityId.parse(record.getString("id").get).TAKE
  def row(key: String, original: String): Record = {
    val entityid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", key, collection, entropy = key)
    Record.createFull(Vector("id" -> entityid.value, "original" -> original,
      "payload" -> Json.obj("number" -> Json.fromBigDecimal(BigDecimal("12345678901234567890.123456789"))),
      "evidence" -> Record.createFull(Vector("empty" -> Record.empty, "null" -> null)),
      "labels" -> Vector("雪", " [literal] "), "summary" -> Summary("declared"),
      "number" -> BigDecimal("12345678901234567890.123456789"), "memo" -> "keep"))
  }
}
