package org.goldenport.cncf.entity

import java.nio.file.{Files, Path}
import cats.data.NonEmptyVector
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.goldenport.schema.{Multiplicity, ValueDomain, XJson, XSemiStructuredData, XString}
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
final class Phase115EntityRecordPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import Phase115EntityRecordPolicySpec.*

  Vector("memory", "sqlite").foreach { backend =>
    s"Canonical Record entity persistence over $backend" should {
      "create and save normalized payloads with declared nonempty reconstruction and separate raw JSON" in {
        Given("a test-owned provider, explicit mapping and typed entity admitted from external null-bearing JSON")
        _with_provider(backend) { provider =>
          given ExecutionContext = _context(provider)
          given EntityPersistent[PolicyEntity] = persistent
          given EntityPersistentCreate[PolicyEntity] = EntityPersistentCreate.fromPersistent(persistent)
          val source = _take(sourceEntity("roundtrip", "null", Vector("雪", "same", "same")))
          val expected = _json("""{"empty":{},"array":[],"child":{"literal":"null"},"number":9007199254740993.1234567890123456789}""")
          val store = EntityStore.standard()

          When("ordinary create, load and save traverse the declared mapping and provider")
          _take(store.create(source, EntityCreateOptions(Set("publication"))))
          val created = _take(store.load[PolicyEntity](source.id)).get
          _take(store.save(created))
          val restored = _take(store.load[PolicyEntity](source.id)).get
          val physical = _take(provider.load(address, DataStore.EntryId(source.id))).get
          val payloadjson = _take(XSemiStructuredData.toJson(restored.payload))
          val itemjson = restored.items.toVector.map(value => _take(XSemiStructuredData.toJson(value)))

          Then("null attributes disappear recursively while empty values, exact numbers, order, duplicates and raw JSON null remain")
          created shouldBe source
          restored shouldBe source
          restored.title shouldBe "null"
          payloadjson shouldBe expected
          restored.payload.fields.map(_.key) shouldBe Vector("empty", "array", "child", "number")
          restored.payload.getAny("empty") shouldBe Some(Record.empty)
          restored.payload.getAny("array") shouldBe Some(Vector.empty)
          itemjson shouldBe Vector(expected, Json.obj(), expected)
          restored.tags shouldBe Vector("雪", "same", "same")
          restored.raw shouldBe _json("""{"nullable":null,"values":[null]}""")
          physical.getString("payload_json").map(_json) shouldBe Some(expected)
          physical.getString("items_json").map(_json) shouldBe Some(Json.arr(expected, Json.obj(), expected))
          physical.getString("raw_json").map(_json) shouldBe Some(restored.raw)
        }
      }

      "retain omitted fields and use only typed SetNull to clear optional storage" in {
        Given("a stored typed entity and raw Update adapter with Noop and optional None")
        _with_provider(backend) { provider =>
          given ExecutionContext = _context(provider)
          given EntityPersistent[PolicyEntity] = persistent
          given EntityPersistentUpdate[Record] = patchPersistent
          val source = _take(sourceEntity("patch", "before", Vector("x", "x")))
          val store = EntityStore.standard()
          _take(store.save(source))
          val unrelated = _take(Record.createC(Vector("title" -> Update.set("after"),
            "payload" -> Update.noop[Record], "memo" -> None)))

          When("an unrelated patch is followed by an explicit optional clear")
          _take(store.updateByIdUnversioned(source.id, unrelated))
          val retained = _take(store.load[PolicyEntity](source.id)).get
          _take(store.updateByIdUnversioned(source.id,
            _take(Record.createC(Vector("memo" -> Update.setNull[String])))))
          val cleared = _take(store.load[PolicyEntity](source.id)).get
          val physical = _take(provider.load(address, DataStore.EntryId(source.id))).get

          Then("omission and Noop preserve values and explicit clear alone makes memo semantically absent")
          retained shouldBe source.copy(title = "after")
          cleared shouldBe retained.copy(memo = None)
          physical.getAny("memo_text") shouldBe None
          persistent.toRecord(cleared).getAny("memo") shouldBe None
        }
      }

      "reject required clear, null SetValue and invalid sequences without mutating the entity" in {
        Given("a stored entity and native Update wrappers whose invalid values have not yet been normalized")
        _with_provider(backend) { provider =>
          given ExecutionContext = _context(provider)
          given EntityPersistent[PolicyEntity] = persistent
          given EntityPersistentUpdate[Record] = patchPersistent
          val source = _take(sourceEntity("invalid", "keep", Vector("ordered", "ordered")))
          val store = EntityStore.standard()
          _take(store.save(source))
          val patches = Vector(
            _take(Record.createC(Vector("title" -> Update.setNull[String]))),
            _take(Record.createC(Vector("memo" -> Update.set[String](null)))),
            _take(Record.createC(Vector("items" -> Update.set(Vector.empty[Record])))),
            _take(Record.createC(Vector("items" -> Update.set(Vector(source.payload, null))))))

          When("the ordinary update route interprets each invalid instruction and Core admits invalid foreign sequences")
          val results = patches.map(patch => store.updateByIdUnversioned(source.id, patch))
          val emptytyped = persistent.fromRecord(persistent.toRecord(source).upsertSingle("items", Vector.empty[Record]))
          val nullsequence = Record.createC(Vector("items" -> Vector(source.payload, null)))
          val nulljsonsequence = XSemiStructuredData.fromJson(_json("""{"items":[{},null,{}]}"""))
          val restored = _take(store.load[PolicyEntity](source.id)).get

          Then("required cardinality and explicit-clear admission fail structurally and preserve every stored value")
          results.foreach(_ shouldBe a[Consequence.Failure[?]])
          _reasons(results(0)) should contain("null-not-allowed")
          _reasons(results(1)) should contain("explicit-clear-required")
          _reasons(results(2)) should contain("cardinality-mismatch")
          emptytyped shouldBe a[Consequence.Failure[?]]
          nullsequence shouldBe a[Consequence.Failure[?]]
          nulljsonsequence shouldBe a[Consequence.Failure[?]]
          restored shouldBe source
        }
      }

      "retain empty and multiple search bodies with explicit ordering and paging metadata" in {
        Given("three published/publicly readable mapped entities with tied titles and ordered duplicate attributes")
        _with_provider(backend) { provider =>
          given ExecutionContext = _context(provider)
          given EntityPersistent[PolicyEntity] = persistent
          given EntityPersistentCreate[PolicyEntity] = EntityPersistentCreate.fromPersistent(persistent)
          val sources = Vector("z", "a", "m").map(key => _take(sourceEntity(key, "same", Vector(key, key, "last"))))
          val store = EntityStore.standard()
          sources.foreach(source => _take(store.create(source, EntityCreateOptions(Set("publication")))))
          val ordering = Vector(Query.SortKey("title"), Query.SortKey("id"))
          val expected = sources.sortBy(source => (source.title, source.id.value))

          When("ordinary search requests no matches, all explicitly ordered matches and a middle page")
          val empty = _take(store.search[PolicyEntity](EntityQuery(collection,
            Query.plan(Record.empty, where = Query.Eq("title", "missing"), sort = ordering,
              limit = Some(2), offset = Some(0), includeTotal = true))))
          val all = _take(store.search[PolicyEntity](EntityQuery(collection,
            Query.plan(Record.empty, sort = ordering, includeTotal = true))))
          val page = _take(store.search[PolicyEntity](EntityQuery(collection,
            Query.plan(Record.empty, sort = ordering, limit = Some(1), offset = Some(1), includeTotal = true))))

          Then("Vector bodies retain query order, duplicates and exact total, offset, limit and fetched counts")
          empty.data shouldBe Vector.empty
          empty.totalCount shouldBe Some(0)
          empty.offset shouldBe Some(0)
          empty.limit shouldBe Some(2)
          empty.fetchedCount shouldBe 0
          all.data shouldBe expected
          all.totalCount shouldBe Some(3)
          all.fetchedCount shouldBe 3
          page.data shouldBe expected.slice(1, 2)
          page.totalCount shouldBe Some(3)
          page.offset shouldBe Some(1)
          page.limit shouldBe Some(1)
          page.fetchedCount shouldBe 1
          page.data.head.tags shouldBe expected(1).tags
          page.data.head.items shouldBe expected(1).items
        }
      }

      "roundtrip at least fifty generated scalar and ordered collection values" in {
        Given("a mapped provider and independent text, exact decimal and ordered duplicate-list generators")
        _with_provider(backend) { provider =>
          given ExecutionContext = _context(provider)
          given EntityPersistent[PolicyEntity] = persistent
          val store = EntityStore.standard()
          val generator = for {
            text <- Gen.alphaNumStr
            number <- Gen.choose(-1000000, 1000000)
            tags <- Gen.listOf(Gen.alphaNumStr)
          } yield (text, number, tags.toVector)
          val property = Prop.forAll(generator) { case (text, number, tags) =>
            Given("a generated String, independently computed exact decimal and an ordered Vector with explicit duplicates")
            val ordered = tags ++ tags
            val decimal = BigDecimal(number) / BigDecimal(1000)
            val json = Json.obj("empty" -> Json.obj(), "array" -> Json.arr(),
              "text" -> Json.fromString(text), "number" -> Json.fromBigDecimal(decimal))
            val base = _take(sourceEntity("property", text, ordered))
            val payload = _take(XSemiStructuredData.fromJson(json))
            val source = base.copy(payload = payload, items = NonEmptyVector.of(payload, Record.empty, payload))
            When("standard save and typed read traverse the selected provider")
            _take(store.save(source))
            val restored = _take(store.load[PolicyEntity](source.id)).get
            val actualjson = _take(XSemiStructuredData.toJson(restored.payload))
            Then("the independently built JSON meaning and exact input order survive")
            restored.title shouldBe text
            restored.tags shouldBe ordered
            actualjson shouldBe json
            restored.items.toVector.map(value => _take(XSemiStructuredData.toJson(value))) shouldBe Vector(json, Json.obj(), json)
            true
          }

          When("ScalaCheck runs at least fifty connected roundtrips")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
          Then("every generated case satisfies the independent value and order laws without discards")
          withClue(checked.toString) { checked.passed shouldBe true }
          checked.succeeded should be >= 50
          checked.discarded shouldBe 0
        }
      }
    }
  }

  "SQL provider ingress and retained values" should {
    "translate actual optional SQL NULL to absence and fail required SQL NULL during entity admission" in {
      Given("a test-owned SQLite provider with a normally saved typed entity")
      _with_provider("sqlite") { provider =>
        given ExecutionContext = _context(provider)
        given EntityPersistent[PolicyEntity] = persistent
        val source = _take(sourceEntity("sqlnull", "required", Vector.empty))
        val store = EntityStore.standard()
        _take(store.save(source))
        val entryid = DataStore.EntryId(source.id)
        val physical = _take(provider.load(address, entryid)).get

        When("the physical output adapter writes SetNull and JDBC reads the optional column")
        _take(provider.save(address, entryid, physical.upsertSingle("memo_text", Update.SetNull)))
        val optionalrow = _take(provider.load(address, entryid)).get
        val optional = _take(store.load[PolicyEntity](source.id)).get
        Then("provider and typed results omit memo and retain every required value")
        optionalrow.getAny("memo_text") shouldBe None
        optional shouldBe source.copy(memo = None)

        When("the same SQL output adapter clears the required title and the ordinary entity route reads it")
        _take(provider.save(address, entryid, optionalrow.upsertSingle("title_text", Update.SetNull)))
        val requiredrow = _take(provider.load(address, entryid)).get
        val required = store.load[PolicyEntity](source.id)
        Then("the provider exposes absence and the mapped entity reports its established missing-required-field failure")
        requiredrow.getAny("title_text") shouldBe None
        required shouldBe a[Consequence.Failure[?]]
        _reasons(required) should contain("missing-required-field")
      }
    }

    "normalize retained JSON text on read without writing and normalize physical text on the next save" in {
      Given("a normally stored entity with retained old JSON text installed in its test-owned SQLite row")
      _with_provider("sqlite") { provider =>
        given ExecutionContext = _context(provider)
        given EntityPersistent[PolicyEntity] = persistent
        val source = _take(sourceEntity("retained", "null", Vector("keep", "keep")))
        val store = EntityStore.standard()
        _take(store.save(source))
        val entryid = DataStore.EntryId(source.id)
        val physical = _take(provider.load(address, entryid)).get
        val retained = """ { "old":null, "empty":{}, "array":[], "child":{"gone":null,"literal":"null"}, "number":9007199254740993.1234567890123456789 } """
        val expected = _json("""{"empty":{},"array":[],"child":{"literal":"null"},"number":9007199254740993.1234567890123456789}""")
        _take(provider.save(address, entryid, physical.upsertSingle("payload_json", retained)))

        When("the ordinary typed entity read decodes retained text")
        val restored = _take(store.load[PolicyEntity](source.id)).get
        val afterread = _take(provider.load(address, entryid)).get
        Then("the logical Record is normalized but the stored text and raw JSON remain unchanged")
        _take(XSemiStructuredData.toJson(restored.payload)) shouldBe expected
        afterread.getString("payload_json") shouldBe Some(retained)
        afterread.getString("raw_json") shouldBe physical.getString("raw_json")
        restored.raw shouldBe source.raw

        When("that reconstructed entity is saved through its declaration")
        _take(store.save(restored))
        val aftersave = _take(provider.load(address, entryid)).get
        Then("the next save writes normalized Record JSON and preserves raw JSON null")
        aftersave.getString("payload_json").map(_json) shouldBe Some(expected)
        aftersave.getString("payload_json").get should not be retained
        aftersave.getString("raw_json") shouldBe physical.getString("raw_json")
      }
    }
  }

  private def _json(text: String): Json = io.circe.parser.parse(text).fold(error => fail(error.message), identity)
  private def _reasons(result: Consequence[?]): Vector[String] = result match {
    case Consequence.Failure(conclusion) => conclusion.causes.toVector.flatMap(_.observation.cause.descriptor.facets)
      .collect { case Descriptor.Facet.Reason(value) => value }
    case _ => Vector.empty
  }
  private def _database(): Path = {
    val root = Files.createDirectories(Path.of("target/phase115/entity-record-policy"))
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
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail((conclusion.show +: conclusion.causes.map(_.display)).mkString("\n"))
  }
}

object Phase115EntityRecordPolicySpec {
  final case class PolicyEntity(id: EntityId, title: String, memo: Option[String],
    payload: Record, items: NonEmptyVector[Record], tags: Vector[String], raw: Json)

  val collection: EntityCollectionId = EntityCollectionId("test", "phase115", "policy")
  val address: DataStore.CollectionId = DataStore.CollectionId.EntityStore(collection)
  private val _fields = Vector(
    EntityStoreField("title", "title_text", ValueDomain(XString, Multiplicity.One), EntityStoreFieldBinding.string),
    EntityStoreField("memo", "memo_text", ValueDomain(XString, Multiplicity.ZeroOne), EntityStoreFieldBinding.string),
    EntityStoreField("payload", "payload_json", ValueDomain(XSemiStructuredData, Multiplicity.One), EntityStoreFieldBinding.semiStructuredData),
    EntityStoreField("items", "items_json", ValueDomain(XSemiStructuredData, Multiplicity.OneMore),
      EntityStoreFieldBinding.array(EntityStoreFieldBinding.semiStructuredData)),
    EntityStoreField("tags", "tags_json", ValueDomain(XString, Multiplicity.ZeroMore),
      EntityStoreFieldBinding.array(EntityStoreFieldBinding.string)),
    EntityStoreField("raw", "raw_json", ValueDomain(XJson, Multiplicity.One), EntityStoreFieldBinding.json))
  private val _framework = (SimpleEntityStorageShapePolicy.managementLogicalFields ++
    SimpleEntityStorageShapePolicy.securityIdentityLogicalFields ++ Vector("name", "permission"))
    .flatMap(name => Vector(name, SimpleEntityStorageShapePolicy.targetName(name))).toSet
  val mapping: EntityStoreRowMapping = EntityStoreRowMapping(_fields, _framework)
  val persistent: EntityPersistent[PolicyEntity] = new EntityPersistent[PolicyEntity] {
    def id(e: PolicyEntity): EntityId = e.id
    def toRecord(e: PolicyEntity): Record = Record.dataAuto("id" -> e.id.value, "title" -> e.title,
      "memo" -> e.memo, "payload" -> e.payload, "items" -> e.items, "tags" -> e.tags, "raw" -> e.raw)
    def fromRecord(r: Record): Consequence[PolicyEntity] = for {
      entityid <- EntityId.createC(r)
      title <- r.asC[String]("title")
      memo <- r.getAsC[String]("memo")
      payload <- r.asC[Record]("payload")
      items <- r.asC[NonEmptyVector[Record]]("items")
      tags <- r.getStringVector("tags") match {
        case Some(values) => Consequence.success(values)
        case None => Consequence.valueInvalid[Vector[String]]("Expected declared tags Vector")
      }
      raw <- r.asC[Json]("raw")
    } yield PolicyEntity(entityid, title, memo, payload, items, tags, raw)
    override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
  }
  val patchPersistent: EntityPersistentUpdate[Record] = new EntityPersistentUpdate[Record] {
    def collection(e: Record): EntityCollectionId = Phase115EntityRecordPolicySpec.collection
    def toRecord(e: Record): Record = e
    def fromRecord(r: Record): Consequence[Record] = Consequence.success(r)
    override def storeRowMapping: Option[EntityStoreRowMapping] = Some(mapping)
  }
  def sourceEntity(key: String, title: String, tags: Vector[String]): Consequence[PolicyEntity] = {
    val entityid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", key, collection, entropy = key)
    val external = io.circe.parser.parse(
      """{"gone":null,"empty":{},"array":[],"child":{"omit":null,"literal":"null"},"number":9007199254740993.1234567890123456789}""")
      .fold(throw _, identity)
    for {
      payload <- XSemiStructuredData.fromJson(external)
      input <- Record.createC(Vector("id" -> entityid.value, "title" -> title, "memo" -> "keep",
        "payload" -> payload, "items" -> NonEmptyVector.of(payload, Record.empty, payload), "tags" -> tags,
        "raw" -> Json.obj("nullable" -> Json.Null, "values" -> Json.arr(Json.Null))))
      entity <- persistent.fromRecord(input)
    } yield entity
  }
}
