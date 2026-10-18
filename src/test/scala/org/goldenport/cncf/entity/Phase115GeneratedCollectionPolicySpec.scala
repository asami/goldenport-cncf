package org.goldenport.cncf.entity

import java.nio.file.{Files, Path}
import cats.data.NonEmptyVector
import domain.datatype.ScalarCode115
import domain.entity.{ApplicationPolicy115, RecordPolicy115}
import domain.value.ChildPolicy115
import domain.entity.{create => createentity, query => queryentity, update => updateentity}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.record.Record
import org.goldenport.schema.XSemiStructuredData
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.directive.{Condition, Update}
import org.simplemodeling.model.value.{ModelValueEncoder, NominalScalar}

/*
 * Ordinary CML cardinality and Core Record policy through generated persistence.
 *
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase115GeneratedCollectionPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  Vector("memory", "sqlite").foreach { backend =>
    s"Ordinary generated RecordPolicy115 on $backend" should {
      "preserve declared collection kinds" which {
        "create, save and load normalized Records and raw JSON with ordered duplicates" in {
          Given("the actual generated entity/create family from ordinary CML * and + multiplicities and a real provider")
          _with_provider(backend) { provider =>
            given ExecutionContext = _context(provider)
            val input = _logical("roundtrip", " \n{literal title} 雪\t ")
            val expected = _take(RecordPolicy115.createC(input))
            val create = _take(createentity.RecordPolicy115.createC(input))
            val persistent = summon[EntityPersistent[RecordPolicy115]]
            val store = EntityStore.standard()

            When("published normal creation, save, untyped row admission and typed load cross the generated mapping")
            _take(store.create(create, EntityCreateOptions(Set("publication"))))
            val created = _take(store.load[RecordPolicy115](expected.id)).get
            _take(store.save(created))
            val restored = _take(store.load[RecordPolicy115](expected.id)).get
            val physical = _take(provider.load(_address, DataStore.EntryId(expected.id))).get
            val admitted = _take(persistent.admitStoreRecordC(RecordPolicy115.collectionId, physical))
            val evidencevector: Vector[Record] = restored.evidences
            val payloadvector: Vector[Json] = restored.payloads
            val requiredrecords: NonEmptyVector[Record] = restored.requiredEvidences
            val requiredjson: NonEmptyVector[Json] = restored.requiredPayloads

            Then("compiled Vector/NonEmptyVector fields retain order and duplicates while untyped arrays are Vector")
            created shouldBe expected
            restored shouldBe expected
            restored.title shouldBe " \n{literal title} 雪\t "
            _record_json(restored.evidence) shouldBe _normalized_json
            restored.evidence.getAny("gone") shouldBe None
            restored.evidence.getAny("empty") shouldBe Some(Record.empty)
            restored.evidence.getAny("array") shouldBe Some(Vector.empty)
            evidencevector.map(_record_json) shouldBe Vector(_normalized_json, Json.obj(), _normalized_json)
            requiredrecords.toVector.map(_record_json) shouldBe Vector(_normalized_json, Json.obj(), _normalized_json)
            payloadvector shouldBe Vector(_raw_json, Json.Null, _raw_json)
            requiredjson.toVector shouldBe Vector(_raw_json, Json.Null, _raw_json)
            restored.payload shouldBe _raw_json
            admitted.getAny("evidences") shouldBe Some(evidencevector)
            admitted.getAny("payloads") shouldBe Some(payloadvector)
            admitted.getAny("requiredEvidences") shouldBe Some(requiredrecords.toVector)
            admitted.getAny("requiredPayloads") shouldBe Some(requiredjson.toVector)
            physical.getString("evidence").map(_json) shouldBe Some(_normalized_json)
            physical.getString("payload").map(_json) shouldBe Some(_raw_json)
          }
        }
      }

      "apply generated update instructions" which {
        "retain omitted collections and clear only optional evidence explicitly" in {
          Given("a published generated entity with nonempty Record/Json collections and optional evidence")
          _with_provider(backend) { provider =>
            given ExecutionContext = _context(provider)
            val input = _logical("patch", "before")
            val source = _take(RecordPolicy115.createC(input))
            val store = EntityStore.standard()
            _take(store.create(_take(createentity.RecordPolicy115.createC(input)), EntityCreateOptions(Set("publication"))))
            val unrelated = _take(updateentity.RecordPolicy115.createC(Record.dataAuto("title" -> "after")))
            val clear = _take(updateentity.RecordPolicy115.createC(
              Record.dataAuto("optionalEvidence" -> Update.setNull[Record])))

            When("the ordinary update route receives an unrelated generated patch followed by typed optional clear")
            _take(store.updateByIdUnversioned(source.id, unrelated))
            val retained = _take(store.load[RecordPolicy115](source.id)).get
            _take(store.updateByIdUnversioned(source.id, clear))
            val cleared = _take(store.load[RecordPolicy115](source.id)).get
            val physical = _take(provider.load(_address, DataStore.EntryId(source.id))).get

            Then("omitted values survive both patches and explicit clear alone removes the optional physical and logical key")
            retained shouldBe source.copy(title = "after")
            cleared shouldBe retained.copy(optionalEvidence = None)
            physical.getAny("optionalEvidence") shouldBe None
            cleared.toRecord().getAny("optionalEvidence") shouldBe None
            cleared.requiredEvidences.toVector.map(_record_json) shouldBe
              Vector(_normalized_json, Json.obj(), _normalized_json)
            cleared.requiredPayloads.toVector shouldBe Vector(_raw_json, Json.Null, _raw_json)
          }
        }

        "reject empty required collections and required clear without changing actual storage" in {
          Given("a normally created generated entity and invalid empty/clear inputs for each required collection")
          _with_provider(backend) { provider =>
            given ExecutionContext = _context(provider)
            val input = _logical("invalid", "keep")
            val source = _take(RecordPolicy115.createC(input))
            val store = EntityStore.standard()
            _take(store.create(_take(createentity.RecordPolicy115.createC(input)), EntityCreateOptions(Set("publication"))))
            val before = _take(provider.load(_address, DataStore.EntryId(source.id))).get
            val patches = Vector(
              Record.dataAuto("requiredEvidences" -> Vector.empty[Record]),
              Record.dataAuto("requiredPayloads" -> Vector.empty[Json]),
              Record.dataAuto("requiredEvidences" -> Update.setNull[NonEmptyVector[Record]]),
              Record.dataAuto("requiredPayloads" -> Update.setNull[NonEmptyVector[Json]]),
              Record.dataAuto("evidence" -> Update.setNull[Record]))

            When("generated builders and the actual store admit each invalid required update and empty entity input")
            val rejected = patches.map(patch => updateentity.RecordPolicy115.createC(patch)
              .flatMap(changes => store.updateByIdUnversioned(source.id, changes)))
            val emptyrecords = RecordPolicy115.createC(input.upsertSingle("requiredEvidences", Vector.empty[Record]))
            val emptyjson = RecordPolicy115.createC(input.upsertSingle("requiredPayloads", Vector.empty[Json]))
            val after = _take(provider.load(_address, DataStore.EntryId(source.id))).get
            val restored = _take(store.load[RecordPolicy115](source.id)).get

            Then("required cardinality and clear failures remain typed and every independently read stored field survives")
            rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
            emptyrecords shouldBe a[Consequence.Failure[?]]
            emptyjson shouldBe a[Consequence.Failure[?]]
            after.fields shouldBe before.fields
            restored shouldBe source
          }
        }
      }

      "query actual generated entities" which {
        "retain empty and ordered paged Vector bodies with exact metadata" in {
          Given("three published generated entities with tied titles and duplicate collection elements")
          _with_provider(backend) { provider =>
            given ExecutionContext = _context(provider)
            val inputs = Vector("z", "a", "m").map(key => _logical(key, "same"))
            val sources = inputs.map(input => _take(RecordPolicy115.createC(input)))
            val expected = sources.sortBy(_.id.value)
            val store = EntityStore.standard()
            inputs.foreach(input => _take(store.create(_take(createentity.RecordPolicy115.createC(input)),
              EntityCreateOptions(Set("publication")))))
            val ordering = Vector(Query.SortKey("title"), Query.SortKey("id"))

            When("ordinary search selects no rows, explicitly ordered rows and a middle page")
            val empty = _take(store.search[RecordPolicy115](EntityQuery(RecordPolicy115.collectionId,
              Query.plan(Record.empty, where = Query.Eq("title", "missing"), sort = ordering,
                limit = Some(2), offset = Some(0), includeTotal = true))))
            val all = _take(store.search[RecordPolicy115](EntityQuery(RecordPolicy115.collectionId,
              Query.plan(Record.empty, sort = ordering, includeTotal = true))))
            val page = _take(store.search[RecordPolicy115](EntityQuery(RecordPolicy115.collectionId,
              Query.plan(Record.empty, sort = ordering, limit = Some(1), offset = Some(1), includeTotal = true))))

            Then("the existing Vector result body retains query order, declared nonempty values and paging counts")
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
            page.data.head.requiredEvidences.toVector.map(_record_json) shouldBe
              Vector(_normalized_json, Json.obj(), _normalized_json)
            page.data.head.requiredPayloads.toVector shouldBe Vector(_raw_json, Json.Null, _raw_json)
          }
        }
      }

      "preserve generated scalar and collection laws" which {
        "roundtrip at least fifty independent scalar values and ordered duplicate collections without discards" in {
          Given("an actual generated persistent, a real provider and independent text, exact-decimal and ordered-list generators")
          _with_provider(backend) { provider =>
            given ExecutionContext = _context(provider)
            val input = _logical("property", "seed")
            val source = _take(RecordPolicy115.createC(input))
            val store = EntityStore.standard()
            _take(store.create(_take(createentity.RecordPolicy115.createC(input)), EntityCreateOptions(Set("publication"))))
            val samples = for {
              text <- Gen.alphaNumStr
              number <- Gen.choose(-1000000, 1000000)
              values <- Gen.listOf(Gen.alphaNumStr)
            } yield (text, number, values.toVector)
            val property = Prop.forAll(samples) { case (text, number, values) =>
              Given("one independent scalar sample, exact decimal and a Vector duplicated in its original order")
              val ordered = values ++ values
              val decimal = BigDecimal(number) / BigDecimal(1000)
              val expectedjson = Json.obj("empty" -> Json.obj(), "array" -> Json.arr(),
                "text" -> Json.fromString(text), "decimal" -> Json.fromBigDecimal(decimal))
              val evidence = _take(XSemiStructuredData.fromJson(expectedjson))
              val records = ordered.map(value => Record.dataAuto("text" -> value))
              val jsonvalues = ordered.map(Json.fromString)
              val requiredrecords = NonEmptyVector.of(evidence, Record.empty, evidence)
              val requiredjson = NonEmptyVector.of(Json.Null, Json.fromBigDecimal(decimal), Json.Null)
              val entity = source.copy(title = text, evidence = evidence, evidences = records,
                requiredEvidences = requiredrecords, payload = Json.fromBigDecimal(decimal),
                payloads = jsonvalues, requiredPayloads = requiredjson)

              When("ordinary save and typed load traverse the generated mapping and selected real provider")
              _take(store.save(entity))
              val restored = _take(store.load[RecordPolicy115](source.id)).get
              val evidencevector: Vector[Record] = restored.evidences
              val payloadvector: Vector[Json] = restored.payloads
              val nonemptyrecords: NonEmptyVector[Record] = restored.requiredEvidences
              val nonemptyjson: NonEmptyVector[Json] = restored.requiredPayloads

              Then("exact independent scalars, empty children and every ordered duplicate survive declared reconstruction")
              restored.title shouldBe text
              _record_json(restored.evidence) shouldBe expectedjson
              evidencevector.map(_.getString("text")) shouldBe ordered.map(Some(_))
              payloadvector shouldBe jsonvalues
              nonemptyrecords.toVector.map(_record_json) shouldBe Vector(expectedjson, Json.obj(), expectedjson)
              nonemptyjson.toVector shouldBe Vector(Json.Null, Json.fromBigDecimal(decimal), Json.Null)
              restored.payload shouldBe Json.fromBigDecimal(decimal)
              true
            }

            When("ScalaCheck executes the connected scalar/order/duplicate law at least fifty times")
            val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
            Then("at least fifty successful generated persistence samples and zero discarded cases are required")
            withClue(checked.toString) { checked.passed shouldBe true }
            checked.succeeded should be >= 50
            checked.discarded shouldBe 0
          }
        }
      }
    }
  }

  "Generated SQLite collection reconstruction" should {
    "retain declared collection kinds across provider lifetime" which {
      "reconstruct Vector and NonEmptyVector with exact values after closing and reopening the provider" in {
        Given("a normally created generated entity in a test-owned SQLite database with an independently expected normalized value")
        val database = _database()
        val input = _logical("restart", " \nretained 雪\t ")
        val expected = _take(RecordPolicy115.createC(input))
        val writer = _sql(database)
        locally {
          given ExecutionContext = _context(writer)
          try {
            _take(EntityStore.standard().create(_take(createentity.RecordPolicy115.createC(input)),
              EntityCreateOptions(Set("publication"))))
          } finally _close(writer)
        }

        When("a newly opened SQLite provider reconstructs untyped rows and the generated declared entity")
        val reader = _sql(database)
        given ExecutionContext = _context(reader)
        try {
          val physical = _take(reader.load(_address, DataStore.EntryId(expected.id))).get
          val persistent = summon[EntityPersistent[RecordPolicy115]]
          val admitted = _take(persistent.admitStoreRecordC(RecordPolicy115.collectionId, physical))
          val restored = _take(EntityStore.standard().load[RecordPolicy115](expected.id)).get
          val records: Vector[Record] = restored.evidences
          val jsonvalues: Vector[Json] = restored.payloads
          val requiredrecords: NonEmptyVector[Record] = restored.requiredEvidences
          val requiredjson: NonEmptyVector[Json] = restored.requiredPayloads

          Then("untyped arrays remain Vector and generated declared nonempty fields retain order, duplicates, exact decimals and raw null")
          restored shouldBe expected
          records.map(_record_json) shouldBe Vector(_normalized_json, Json.obj(), _normalized_json)
          jsonvalues shouldBe Vector(_raw_json, Json.Null, _raw_json)
          requiredrecords.toVector.map(_record_json) shouldBe Vector(_normalized_json, Json.obj(), _normalized_json)
          requiredjson.toVector shouldBe Vector(_raw_json, Json.Null, _raw_json)
          admitted.getAny("requiredEvidences") shouldBe Some(requiredrecords.toVector)
          admitted.getAny("requiredPayloads") shouldBe Some(requiredjson.toVector)
          restored.evidence.getAny("gone") shouldBe None
          restored.evidence.getAny("empty") shouldBe Some(Record.empty)
          restored.evidence.getAny("array") shouldBe Some(Vector.empty)
          restored.payload shouldBe _raw_json
        } finally _close(reader)
      }
    }
  }

  "Ordinary generated application values" should {
    "bind generated query collections" which {
      "accept typed ChildPolicy115 values and Records with the same ordered duplicate Condition" in {
        Given("the ordinary generated structured VALUE and two independent child Records")
        val firstrecord = Record.dataAuto("label" -> "first", "count" -> 1)
        val secondrecord = Record.dataAuto("label" -> "second", "count" -> 2)
        val first = _take(ChildPolicy115.createC(firstrecord))
        val second = _take(ChildPolicy115.createC(secondrecord))
        val ordered: Vector[ChildPolicy115] = Vector(first, second, first)

        When("the actual ordinary query builder receives typed children and Record children")
        val typed = _take(queryentity.ApplicationPolicy115.createC(Record.dataAuto("items" -> ordered)))
        val records = _take(queryentity.ApplicationPolicy115.createC(
          Record.dataAuto("items" -> Vector(firstrecord, secondrecord, firstrecord))))
        val witness: Condition[Vector[ChildPolicy115]] = typed.items

        Then("both paths use the generated Condition.is contract with exact order and duplicates")
        witness shouldBe Condition.is(ordered)
        records.items shouldBe Condition.is(ordered)
        typed.toRecord().getAny("items") shouldBe Some(Vector(firstrecord, secondrecord, firstrecord))
      }

      "distinguish a present empty collection from an invalid application element" in {
        Given("the ordinary query builder and a malformed structured child count")
        val invalidchild = Record.dataAuto("label" -> "invalid", "count" -> "not-an-int")

        When("the actual query builder reads empty and invalid child vectors")
        val empty = _take(queryentity.ApplicationPolicy115.createC(Record.dataAuto("items" -> Vector.empty[ChildPolicy115])))
        val invalid = queryentity.ApplicationPolicy115.createC(Record.dataAuto("items" -> Vector(invalidchild)))
        val witness: Condition[Vector[ChildPolicy115]] = empty.items

        Then("empty remains a present Condition.is Vector and the invalid element returns a structured failure")
        witness shouldBe Condition.is(Vector.empty[ChildPolicy115])
        empty.toRecord().getAny("items") shouldBe Some(Vector.empty)
        invalid shouldBe a[Consequence.Failure[?]]
      }
    }

    "project nominal scalars separately from structured values" which {
      "roundtrip generated ScalarCode115 through the model encoder and thin entity Record projection" in {
        Given("an actual generated DATATYPE scalar, a structured VALUE and the ordinary entity reader")
        val scalar = _take(ScalarCode115.createC(" \nCODE-115 雪\t "))
        val marker: NominalScalar = scalar
        val childrecord = Record.dataAuto("label" -> "child", "count" -> 7)
        val child = _take(ChildPolicy115.createC(childrecord))
        val id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts(
          "test", "nominal", ApplicationPolicy115.collectionId, entropy = "nominal")
        val entity = _take(ApplicationPolicy115.createC(Record.dataAuto(
          "id" -> id.value, "items" -> Vector(child, child), "code" -> scalar)))

        When("the shared model encoder and generated toRecord project ordinary values and the reader reconstructs them")
        val scalarprojection = _take(ModelValueEncoder.encodeC(marker))
        val childprojection = _take(ModelValueEncoder.encodeC(child))
        val record = entity.toRecord()
        val restored = _take(ApplicationPolicy115.createC(record))
        val readscalar = _take(summon[ValueReader[ScalarCode115]].readC(record.getAny("code").get))

        Then("nominal identity has scalar external shape while the structured child retains its two-field Record")
        scalarprojection shouldBe " \nCODE-115 雪\t "
        childprojection shouldBe childrecord
        record.fields.map(_.key) shouldBe Vector("id", "items", "code")
        record.getAny("code") shouldBe Some(" \nCODE-115 雪\t ")
        record.getAny("items") shouldBe Some(Vector(childrecord, childrecord))
        restored shouldBe entity
        restored.code shouldBe Some(scalar)
        readscalar shouldBe scalar
      }

      "reject native null at the generated nominal reader's foreign Any ingress" in {
        Given("the actual generated nominal scalar ValueReader without invented scalar constraints")
        val reader = summon[ValueReader[ScalarCode115]]

        When("an external Any input contains native null")
        val invalid = reader.readC(null)

        Then("the generated reader preserves the Core String conversion failure")
        invalid shouldBe a[Consequence.Failure[?]]
      }
    }
  }

  private val _address = DataStore.CollectionId.EntityStore(RecordPolicy115.collectionId)
  private val _normalized_json = _json(
    """{"empty":{},"array":[],"child":{"literal":"null"},"decimal":9007199254740993.1234567890123456789}""")
  private val _raw_json = _json("""{"nullable":null,"values":[null],"decimal":9007199254740993.1234567890123456789}""")

  private def _logical(key: String, title: String): Record = {
    val id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", key, RecordPolicy115.collectionId, entropy = key)
    val external = _json(
      """{"gone":null,"empty":{},"array":[],"child":{"gone":null,"literal":"null"},"decimal":9007199254740993.1234567890123456789}""")
    val evidence = _take(XSemiStructuredData.fromJson(external))
    Record.dataAuto("id" -> id.value, "title" -> title, "evidence" -> evidence,
      "optionalEvidence" -> evidence, "evidences" -> Vector(evidence, Record.empty, evidence),
      "requiredEvidences" -> NonEmptyVector.of(evidence, Record.empty, evidence), "payload" -> _raw_json,
      "payloads" -> Vector(_raw_json, Json.Null, _raw_json),
      "requiredPayloads" -> NonEmptyVector.of(_raw_json, Json.Null, _raw_json))
  }
  private def _record_json(record: Record): Json = _take(XSemiStructuredData.toJson(record))
  private def _json(text: String): Json = io.circe.parser.parse(text).fold(error => fail(error.message), identity)
  private def _database(): Path = {
    val root = Files.createDirectories(Path.of("target/phase115/generated-collection-policy"))
    Files.createTempDirectory(root, "sql-").toAbsolutePath.resolve("application.db")
  }
  private def _sql(database: Path): SqlDataStore = SqlDataStore.jdbc(s"jdbc:sqlite:$database")
  private def _with_provider(backend: String)(run: DataStore => Unit): Unit = {
    val provider = if (backend == "sqlite") _sql(_database())
      else new DataStore.InMemorySearchableDataStore(CommitRecorder.noop)
    try run(provider)
    finally _close(provider)
  }
  private def _context(provider: DataStore): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(provider)
    context
  }
  private def _close(provider: DataStore): Unit = provider match {
    case sql: SqlDataStore => _take(sql.closeC())
    case _ => ()
  }
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail((conclusion.show +: conclusion.causes.map(_.display)).mkString("\n"))
  }
}
