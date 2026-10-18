package org.goldenport.cncf.job

import java.nio.file.{Files, Path}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
final class Phase100JobEntityStoreMappingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Built-in Job projection mappings" should {
    "separate native SQLite text from declared Job values across fresh owners" in {
      Vector(false, true).foreach { normalize =>
        Given("fixed builtin Job metadata with exact text, nested null/empties and ordered arrays")
        val entity = _job("  {literal} 雪  ")
        val persistent = JobEntity.entityPersistent
        val area = Files.createDirectories(Path.of("target", "phase100-job-mapping", "work"))
        val path = Files.createTempFile(area, "job-", ".db")
        val store = SqlDataStore.sqlite(path.toString, config = SqlDataStore.Config(normalizeColumnNames = normalize))
        given ExecutionContext = ExecutionContext.create()
        val collection = DataStore.CollectionId.EntityStore(JobEntityCollections.Job)
        try {
          When("the selected persistent encodes its logical entity and SQLite writes the physical row")
          val encoded = _take(persistent.toStoreRecordC(JobEntityCollections.Job, entity))
          _take(store.create(collection, DataStore.EntryId(entity.id), encoded))
          _take(store.closeC())
          val fresh = SqlDataStore.sqlite(path.toString, config = SqlDataStore.Config(normalizeColumnNames = normalize))
          try {
            val raw = _take(fresh.load(collection, DataStore.EntryId(entity.id))).get
            Then("fresh physical values are exact Strings")
            raw.getAny("debugParameters") shouldBe Some("""{"literal":"  {literal} 雪  ","empty":{}}""")
            entity.record.getRecord("debugParameters").get.fields.exists(_.key == "null") shouldBe false
            entity.record.getRecord("debugParameters").get.getRecord("empty") shouldBe Some(Record.empty)
            raw.getAny("continuationTaskIds") shouldBe Some("""["first","  {literal} 雪  ",""]""")
            raw.getAny("continuationTasks") shouldBe Some("""[{"taskId":"first","status":"ready"}]""")
            When("the selected persistent reconstructs the reopened physical row")
            val decoded = _take(persistent.fromStoreRecordC(JobEntityCollections.Job, raw))
            Then("declared decoding restores the original logical metadata and identity")
            decoded.id shouldBe entity.id
            decoded.record.getAny("requestSummary") shouldBe Some("  {literal} 雪  ")
            _json(decoded.record.getRecord("debugParameters").get) shouldBe _json(entity.record.getRecord("debugParameters").get)
            decoded.record.getRecord("debugParameters").get.fields.exists(_.key == "null") shouldBe false
            decoded.record.getRecord("debugParameters").get.getRecord("empty") shouldBe Some(Record.empty)
            decoded.record.getVector("continuationTaskIds") shouldBe Some(Vector("first", "  {literal} 雪  ", ""))
            decoded.record.getVector("continuationTasks").get.map {
              case record: Record => _json(record)
              case other => fail(s"declared continuation element should be Record: $other")
            } shouldBe entity.record.getVector("continuationTasks").get.map {
              case record: Record => _json(record)
              case other => fail(s"fixture continuation element should be Record: $other")
            }
          } finally _take(fresh.closeC())
        } finally {
          _take(store.closeC())
          Vector(path.resolveSibling(path.getFileName.toString + "-wal"),
            path.resolveSibling(path.getFileName.toString + "-shm"),
            path.resolveSibling(path.getFileName.toString + "-journal"), path).foreach(Files.deleteIfExists)
        }
      }
    }

    "retain explicit definition-profile storage while preserving the JCL parser authority" in {
      Given("a valid YAML definition with an independently selected existing profile")
      val body = "job:\n  name: profile-only\n  target:\n    action: org.goldenport.cncf.test.JclFixture.command.ok\n  profile:\n    expectedStatus: succeeded\n"
      val profile = _take(JobBatchDefinition.parseYaml(body)).jobs.head.profile
      val id = _take(JobDefinitionId.bridgeFromParts("cncf", "phase100", Instant.EPOCH, "definition"))
      val definition = JobDefinitionEntity.create(id, "profile-only", body, "yaml", profile,
        None, None, None, JobDefinitionStatus.Active, None, Instant.EPOCH)
      val persistent = JobDefinitionEntity.entityPersistent
      When("the declared strict stages encode, admit and decode the definition")
      val encoded = _take(persistent.toStoreRecordC(JobEntityCollections.JobDefinition, definition))
      val admitted = _take(persistent.admitStoreRecordC(JobEntityCollections.JobDefinition, encoded))
      val decoded = _take(persistent.fromStoreRecordC(JobEntityCollections.JobDefinition, encoded))
      Then("the physical profile is JSON text, the admitted profile is Record and source-based decoding retains identity and meaning")
      encoded.getAny("normalizedProfile").get shouldBe a[String]
      _json(admitted.getRecord("normalizedProfile").get) shouldBe _json(profile.get.toRecord)
      decoded.id shouldBe definition.id
      decoded.jclSource shouldBe body
      decoded.jclFormat shouldBe "yaml"
      decoded.normalizedProfile shouldBe profile
      Vector(false, true).foreach { normalize =>
        Given("the declared definition row and an independently owned SQLite file under the selected naming mode")
        val area = Files.createDirectories(Path.of("target", "phase100-job-mapping", "work"))
        val path = Files.createTempFile(area, "definition-", ".db")
        val store = SqlDataStore.sqlite(path.toString, config = SqlDataStore.Config(normalizeColumnNames = normalize))
        val collection = DataStore.CollectionId.EntityStore(JobEntityCollections.JobDefinition)
        given ExecutionContext = ExecutionContext.create()
        try {
          When("the physical row is written and independently reopened before declared reconstruction")
          _take(store.create(collection, DataStore.EntryId(id), encoded))
          _take(store.closeC())
          val fresh = SqlDataStore.sqlite(path.toString, config = SqlDataStore.Config(normalizeColumnNames = normalize))
          try {
            val raw = _take(fresh.load(collection, DataStore.EntryId(id))).get
            val restored = _take(persistent.fromStoreRecordC(JobEntityCollections.JobDefinition, raw))
            Then("the actual profile column is String and reopened source interpretation retains the original profile and identity")
            raw.getAny("normalizedProfile").get shouldBe a[String]
            restored.id shouldBe definition.id
            restored.jclSource shouldBe body
            restored.normalizedProfile shouldBe profile
          } finally _take(fresh.closeC())
        } finally {
          _take(store.closeC())
          Vector(path.resolveSibling(path.getFileName.toString + "-wal"),
            path.resolveSibling(path.getFileName.toString + "-shm"),
            path.resolveSibling(path.getFileName.toString + "-journal"), path).foreach(Files.deleteIfExists)
        }
      }
    }

    "retain empty declared arrays separately from absent optional metadata" in {
      Given("a valid Job identity with two present empty ordered arrays and absent debug metadata")
      val original = _job("empty")
      val entity = original.copy(record = Record.createFull(Vector("id" -> original.id.value,
        "name" -> "empty", "continuationTaskIds" -> Vector.empty[String], "continuationTasks" -> Vector.empty[Record])))
      val persistent = JobEntity.entityPersistent
      When("the selected mapping encodes and separately decodes the admitted empty structures")
      val encoded = _take(persistent.toStoreRecordC(JobEntityCollections.Job, entity))
      val decoded = _take(persistent.fromStoreRecordC(JobEntityCollections.Job, encoded))
      Then("both arrays remain present and empty while absent optional metadata remains absent")
      encoded.getAny("continuationTaskIds") shouldBe Some("[]")
      encoded.getAny("continuationTasks") shouldBe Some("[]")
      decoded.record.getVector("continuationTaskIds") shouldBe Some(Vector.empty)
      decoded.record.getVector("continuationTasks") shouldBe Some(Vector.empty)
      decoded.record.getAny("debugParameters") shouldBe None
    }

    "refuse unknown declarations and malformed known physical payloads without fallback" in {
      Given("the fixed builtin Job row declaration and one valid logical entity")
      val entity = _job("known")
      val persistent = JobEntity.entityPersistent
      val unknown = entity.copy(record = entity.record ++ Record.data("unregistered" -> Record.data("x" -> "y")))
      val encoded = _take(persistent.toStoreRecordC(JobEntityCollections.Job, entity))
      When("an unknown logical field or malformed known physical value crosses its selected boundary")
      val refused = persistent.toStoreRecordC(JobEntityCollections.Job, unknown)
      val malformed = persistent.fromStoreRecordC(JobEntityCollections.Job,
        Record(encoded.fields.map(field => if (field.key == "debugParameters")
          field.copy(value = org.goldenport.record.Field.Value.Single("{broken")) else field)))
      val wrongshape = persistent.fromStoreRecordC(JobEntityCollections.Job,
        Record(encoded.fields.map(field => if (field.key == "continuationTasks")
          field.copy(value = org.goldenport.record.Field.Value.Single("{}")) else field)))
      Then("both remain typed failures rather than inferred values or an accepted physical fallback")
      refused shouldBe a[Consequence.Failure[?]]
      malformed shouldBe a[Consequence.Failure[?]]
      wrongshape shouldBe a[Consequence.Failure[?]]
    }

    "preserve at least one hundred shrinking structured metadata and ordered-array values" in {
      Given("bounded Unicode and JSON-looking text with ordinary String shrinking and fixed nested null/empty members")
      val generator = Gen.chooseNum(0, 16).flatMap(size =>
        Gen.listOfN(size, Gen.oneOf('a', '雪', 'β', ' ', '{', '}', '"', '\\')).map(_.mkString))
      val property = Prop.forAll(generator) { text =>
        val entity = _job(text)
        val persistent = JobEntity.entityPersistent
        When("the fixed declaration encodes and separately decodes this generated logical entity")
        val encoded = _take(persistent.toStoreRecordC(JobEntityCollections.Job, entity))
        val decoded = _take(persistent.fromStoreRecordC(JobEntityCollections.Job, encoded))
        Then("exact String content, structured members and ordered arrays survive without inferred codec selection")
        decoded.record.getAny("requestSummary") shouldBe Some(text)
        _json(decoded.record.getRecord("debugParameters").get) shouldBe _json(entity.record.getRecord("debugParameters").get)
        decoded.record.getVector("continuationTaskIds") shouldBe Some(Vector("first", text, ""))
        Prop.passed
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
      checked.passed shouldBe true
      checked.succeeded should be >= 100
      checked.discarded shouldBe 0
    }
  }

  private def _job(text: String): JobEntity = {
    val id = org.goldenport.cncf.EntityIdFixtureBridge.fromParts("cncf", "phase100", JobEntityCollections.Job, entropy = "mapping")
    JobEntity(id, Record.createFull(Vector(
      "id" -> id.value, "name" -> "mapping", "jobId" -> id.value, "requestSummary" -> text,
      "debugParameters" -> Record.createFull(Vector("literal" -> text, "empty" -> Record.empty, "null" -> null)),
      "continuationTaskIds" -> Vector("first", text, ""),
      "continuationTasks" -> Vector(Record.data("taskId" -> "first", "status" -> "ready")))))
  }

  private def _json(record: Record): io.circe.Json =
    io.circe.parser.parse(record.toJsonString).toOption.getOrElse(fail("fixture JSON should be valid"))

  private def _take[A](result: Consequence[A]): A = result.toOption.getOrElse(fail(result.toString))
}
