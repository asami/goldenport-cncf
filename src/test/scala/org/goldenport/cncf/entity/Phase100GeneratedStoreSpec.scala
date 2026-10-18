package org.goldenport.cncf.entity

import java.nio.file.{Files, Path}
import io.circe.Json
import domain.entity.Proposal
import domain.entity.{create => createentity, update => updateentity}
import domain.value.ProposalSummary
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.record.Record
import org.goldenport.schema.{StructuredDataFormat, XJson, XSemiStructuredData}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.directive.Update

/*
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
final class Phase100GeneratedStoreSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  Vector("memory", "sqlite").foreach { backend =>
    s"Cozy-generated typed persistence over $backend" should {
      "retain declarations and all application values through ordinary create, save, update, patch and query" in {
        Given("actual generated Scala compiled from the Phase 78 CML fixture, with no hand-edited output")
        val provider = if (backend == "sqlite") _sql(_database())
          else new DataStore.InMemorySearchableDataStore(CommitRecorder.noop)
        given ExecutionContext = _context(provider)
        val persistent = summon[EntityPersistent[Proposal]]
        val id = _id("routes")
        val source = _logical(id, " \n{  \"original\": [1] } 雪\t ")
        val expected = _take(Proposal.createC(source))
        val create = _take(createentity.Proposal.createC(source))
        val store = EntityStore.standard()
        try {
          When("the standard store executes generated writes, logical structured queries and native Update markers")
          _take(store.create(create, EntityCreateOptions(Set("publication"))))
          val created = _take(store.load[Proposal](id)).get
          _take(store.save(created.copy(originalProposal = expected.originalProposal + "saved")))
          _take(store.update(created))
          val patch = _take(updateentity.Proposal.createC(Record.createFull(Vector(
            "evidence" -> Update.set(Record.empty),
            "optionalPayload" -> Update.setNull[Json],
            "evidences" -> Update.set(Vector.empty[Record])))))
          _take(store.updateByIdUnversioned(id, patch))
          val restored = _take(store.load[Proposal](id)).get
          val found = _take(store.search[Proposal](EntityQuery(Proposal.collectionId,
            Query.plan(Record.empty, where = Query.Eq("payload", expected.payload)))))
          val raw = _take(provider.load(DataStore.CollectionId.EntityStore(Proposal.collectionId), DataStore.EntryId(id))).get

          Then("physical text and generated JSON, Record, product, collection and native scalar meanings remain distinct")
          persistent.storeRowMapping.get.fields.find(_.logicalName == "payload").get.domain.datatype shouldBe XJson
          persistent.storeRowMapping.get.fields.find(_.logicalName == "evidence").get.domain.datatype shouldBe XSemiStructuredData
          Vector("originalProposal", "payload", "evidence", "payloads", "evidences", "summary", "summaries").foreach { field =>
            raw.getAny(field).get shouldBe a[String]
          }
          created shouldBe expected
          restored shouldBe expected.copy(evidence = Record.empty, optionalPayload = None, evidences = Vector.empty)
          found.data shouldBe Vector(restored)
          raw.getString("originalProposal") shouldBe Some(expected.originalProposal)
        } finally _close(provider)
      }
    }
  }

  "Cozy-generated durable persistence" should {
    "reconstruct the declared products and exact original text after provider shutdown and reopen" in {
      Given("a generated Proposal persisted to a test-owned SQLite database")
      val database = _database()
      val source = _logical(_id("restart"), "  [literal]\n雪 { intact }  ")
      val expected = _take(Proposal.createC(source))
      val writer = _sql(database)
      locally {
        given ExecutionContext = _context(writer)
        try _take(EntityStore.standard().save(expected))
        finally _close(writer)
      }

      When("a newly opened provider and store load the same generated collection identity")
      val reader = _sql(database)
      given ExecutionContext = _context(reader)
      try {
        val restored = _take(EntityStore.standard().load[Proposal](expected.id)).get

        Then("JSON stays JSON, Record stays Record and products, empty/nested values and exact numbers survive")
        restored shouldBe expected
        restored.payload shouldBe a[Json]
        restored.evidence shouldBe a[Record]
        restored.summary shouldBe a[ProposalSummary]
      } finally _close(reader)
    }
  }

  "Explicit external formats through generated durable persistence" should {
    "normalize each selected syntax before SQL storage and reconstruct its declared Record after reopen" in {
      Given("three explicitly selected external syntaxes with one independently expected structured value")
      val expectedjson = io.circe.parser.parse(
        """{"caption":"雪","empty":{},"items":[1,2],"nullable":null,"precise":9007199254740993.1234567890123456789}""")
        .fold(error => fail(error.message), identity)
      val representations = Vector(
        StructuredDataFormat.Json -> """  {"caption":"雪","empty":{},"items":[1,2],"nullable":null,"precise":9007199254740993.1234567890123456789}  """,
        StructuredDataFormat.Yaml -> """caption: 雪
          |empty: {}
          |items: [1, 2]
          |nullable: null
          |precise: 9007199254740993.1234567890123456789
          |""".stripMargin,
        StructuredDataFormat.Hocon -> """caption = "雪"
          |empty = {}
          |items = [1, 2]
          |nullable = null
          |precise = 9007199254740993.1234567890123456789
          |""".stripMargin)
      representations.foreach { case (format, text) =>
        Given(s"explicit $format input, independently expected JSON meaning and the actual generated Proposal declaration")
        val database = _database()
        val entityid = _id(s"format${format.toString.toLowerCase(java.util.Locale.ROOT)}")

        When("the selected external decoder supplies Record to generated creation, followed by EntityStore save and provider reopen")
        val evidence = _take(XSemiStructuredData.fromText(text, format))
        val entity = _take(Proposal.createC(_logical(entityid, text).upsertSingle("evidence", evidence)))
        val writer = _sql(database)
        locally {
          given ExecutionContext = _context(writer)
          try _take(EntityStore.standard().save(entity))
          finally _close(writer)
        }
        val reader = _sql(database)
        given ExecutionContext = _context(reader)
        try {
          val raw = _take(reader.load(DataStore.CollectionId.EntityStore(Proposal.collectionId), DataStore.EntryId(entity.id))).get
          val physical = raw.getAny("evidence") match {
            case Some(value: String) => value
            case other => fail(s"declared physical evidence should be String: $other")
          }
          val physicaljson = io.circe.parser.parse(physical).fold(error => fail(error.message), identity)
          val restored = _take(EntityStore.standard().load[Proposal](entity.id)).get
          val restoredjson = _take(XSemiStructuredData.toJson(restored.evidence))

          Then("SQL retains the exact original syntax as String and separate JSON text while declared readback restores Record and Json")
          raw.getAny("originalProposal") shouldBe Some(text)
          raw.getAny("evidence").get shouldBe a[String]
          physicaljson shouldBe expectedjson
          restored.originalProposal shouldBe text
          restored.evidence shouldBe a[Record]
          restored.payload shouldBe a[Json]
          restoredjson shouldBe expectedjson
          restored shouldBe entity
        } finally _close(reader)
      }
    }
  }

  "Native generated nested collection fixtures" should {
    "compile and preserve nested Json/Record reads, Updates and empty children through SQL" in {
      Given("unchanged producer-generated Scala fixture products from the documented semantic AST")
      val provider = _sql(_database())
      given ExecutionContext = _context(provider)
      val entityid = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", "nested",
        org.example.phase100.nested.entity.Proposal.collectionId, entropy = "nested")
      val payload = Vector(Vector.empty[Json], Vector(Json.Null, Json.obj("snow" -> Json.fromString("雪"))))
      val evidence = List(Vector.empty[Record], Vector(Record.empty, Record.data("value" -> "nested")))
      val nonempty = cats.data.NonEmptyVector.one(Vector.empty[Json])
      val input = Record.createFull(Vector("id" -> entityid.value, "payload" -> payload, "evidence" -> evidence, "nonempty" -> nonempty))
      try {
        When("generated builders and the standard EntityStore consume their declared nested container types")
        val entity = _take(org.example.phase100.nested.entity.Proposal.createC(input))
        val collectionbuilt = _take(org.example.phase100.nested.entity.Proposal.Builder()
          .withId(entityid).withPayload(payload).withEvidence(evidence).withNonempty(nonempty).buildC())
        val elementbuilt = _take(org.example.phase100.nested.entity.Proposal.Builder()
          .withId(entityid).withPayload(payload.head).withEvidence(evidence.head).withNonempty(nonempty.head).buildC())
        val store = EntityStore.standard()
        withClue("nested save: ") { _take(store.save(entity)) }
        val loaded = withClue("nested load: ") { _take(store.load[org.example.phase100.nested.entity.Proposal](entityid)).get }
        val patch = _take(org.example.phase100.nested.entity.update.Proposal.createC(Record.createFull(Vector(
          "payload" -> Update.set(Vector.empty[Vector[Json]]),
          "evidence" -> Update.set(List(Vector.empty[Record]))))))
        withClue("nested patch: ") { _take(store.updateByIdUnversioned(entityid, patch)) }
        val patched = withClue("nested load: ") { _take(store.load[org.example.phase100.nested.entity.Proposal](entityid)).get }
        val query = _take(org.example.phase100.nested.entity.query.Proposal.createC(input))

        Then("typed Json and Record leaves and empty inner/outer arrays retain their declared containers")
        collectionbuilt.payload shouldBe payload
        collectionbuilt.evidence shouldBe evidence
        collectionbuilt.nonempty shouldBe nonempty
        elementbuilt.payload shouldBe Vector(payload.head)
        elementbuilt.evidence shouldBe List(evidence.head)
        elementbuilt.nonempty shouldBe cats.data.NonEmptyVector.one(nonempty.head)
        loaded.payload shouldBe payload
        loaded.evidence shouldBe evidence
        loaded.nonempty shouldBe nonempty
        patched.nonempty shouldBe nonempty
        patched.payload shouldBe Vector.empty
        patched.evidence shouldBe List(Vector.empty)
        query.payload shouldBe org.simplemodeling.model.directive.Condition.is(payload)
        query.evidence shouldBe org.simplemodeling.model.directive.Condition.is(evidence)

        When("nested Update admission receives Noop/SetNull and a nested Json leaf has an incompatible value")
        val omitted = _take(org.example.phase100.nested.entity.update.Proposal.createC(Record.empty))
        val cleared = _take(org.example.phase100.nested.entity.update.Proposal.createC(
          Record.createFull(Vector("payload" -> Update.setNull[Vector[Vector[Json]]]))))
        val invalid = org.example.phase100.nested.entity.Proposal.createC(
          input.upsertSingle("payload", Vector(Vector("not a declared Json value"))))
        val emptyrequired = org.example.phase100.nested.entity.Proposal.createC(
          input.upsertSingle("nonempty", Vector.empty[Vector[Json]]))

        Then("marker meaning survives and the invalid leaf is rejected without erasure or stringification")
        omitted.payload shouldBe Update.noop[Vector[Vector[Json]]]
        cleared.payload shouldBe Update.setNull[Vector[Vector[Json]]]
        invalid shouldBe a[Consequence.Failure[?]]
        emptyrequired shouldBe a[Consequence.Failure[?]]
      } finally _close(provider)
    }
  }

  private def _logical(id: EntityId, original: String): Record = Record.createFull(Vector(
    "id" -> id.value, "originalProposal" -> original,
    "payload" -> Json.obj("exact" -> Json.fromBigDecimal(BigDecimal("12345678901234567890.123456789"))),
    "evidence" -> Record.createFull(Vector("empty" -> Record.empty, "null" -> null)),
    "optionalPayload" -> Json.Null, "optionalEvidence" -> Record.empty,
    "payloads" -> Vector(Json.Null, Json.arr()), "evidences" -> Vector(Record.empty),
    "name" -> "generated", "quantity" -> 7, "active" -> true,
    "amount" -> BigDecimal("12345678901234567890.123456789"),
    "summary" -> ProposalSummary("雪", 2), "optionalSummary" -> ProposalSummary("optional", 1),
    "summaries" -> Vector(ProposalSummary("repeated", 3))))

  private def _id(key: String): EntityId =
    org.goldenport.cncf.EntityIdFixtureBridge.fromParts("test", key, Proposal.collectionId, entropy = key)

  private def _database(): Path = {
    val root = Files.createDirectories(Path.of("target/phase100/generated-store"))
    Files.createTempDirectory(root, "sql-").toAbsolutePath.resolve("application.db")
  }

  private def _sql(database: Path): SqlDataStore = SqlDataStore.jdbc(s"jdbc:sqlite:$database")
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
    case Consequence.Failure(conclusion) =>
      fail((conclusion.show +: conclusion.causes.map(_.display)).mkString("\n"))
  }
}
