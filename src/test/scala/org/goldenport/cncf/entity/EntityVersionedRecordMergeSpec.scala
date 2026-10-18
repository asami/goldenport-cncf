package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.{Field, Record}
import org.simplemodeling.model.datatype.EntityId
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 14, 2026
 * @version Oct. 18, 2026
 */
final class EntityVersionedRecordMergeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityVersionedRecordMergeSpec.*
  import EntityVersionedRecordMergeSpec.given

  "Versioned Entity Record merge" should {
    Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).foreach { representation =>
      s"preserve disjoint changed, retained and domain ownership for $representation" which {
        Vector(false, true).foreach { rename =>
          s"retain exactly one name with explicit rename $rename and every untouched Field" in {
            Given("an existing SQL readback identity, name, managed values, absent NULL attributes and retained non-NULL empty Fields")
            val context = _merge_context
            val sample = Sample("Original name", "Original title", rename, 7L)
            val binding = EntityRevisionBinding(representation)
            val existing = _existing(sample, binding)
            val changes = _changes(sample, binding)
            val expected = _expected(existing, changes, binding)
            When("the existing StandardEntityStore versioned merge applies a changed title and optional name")
            val result = new StandardEntityStore()._merge_versioned_update_record(
              org.goldenport.cncf.datastore.DataStore.CollectionId.Instance("versioned_record_merge"),
              existing, changes, binding)(using context)
            Then("one exact name follows patch precedence and all other Fields retain their values and order")
            result shouldBe a[Consequence.Success[?]]
            val merged = result.toOption.get
            withClue(s"ordered keys: ${merged.fields.map(_.key)}; expected keys: ${expected.fields.map(_.key)}") {
              (merged.fields == expected.fields) shouldBe true
            }
            merged.fields.map(_.key).distinct.size shouldBe merged.fields.size
            merged.fields.count(_.key == "name") shouldBe 1
            merged.getString("name") shouldBe Some(if (rename) sample.oldname + " renamed" else sample.oldname)
            merged.getString("title") shouldBe Some(sample.oldtitle + " changed")
            merged.fields.exists(_.key == binding.storageFieldName) shouldBe false
            _check_sql_read_fields(existing)
            _check_sql_read_fields(merged)
            And("the four canonical security Fields retain their exact values and order")
            withClue(s"security keys: ${merged.fields.filter(field => _security_keys.contains(field.key)).map(_.key)}") {
              (merged.fields.filter(field => _security_keys.contains(field.key)) == _security_fields) shouldBe true
            }
          }
        }

        "obey complete Field correspondence for at least one hundred total bounded samples" in {
          Given("nonempty bounded names/titles, optional rename, positive revisions and meaningful reducing shrinking")
          val context = _merge_context
          val samples = for {
            namesize <- Gen.chooseNum(1, 40)
            titlesize <- Gen.chooseNum(1, 60)
            namecharacter <- Gen.oneOf("n", "日", "λ")
            titlecharacter <- Gen.oneOf("t", "雲", "β")
            rename <- Gen.oneOf(false, true)
            revision <- Gen.chooseNum(1L, 1000000L)
          } yield Sample(namecharacter * namesize, titlecharacter * titlesize, rename, revision)
          val property = Prop.forAll(samples) { sample =>
            Given(s"a fresh $representation SQL readback merge with complete managed/domain Fields and absent NULL attributes")
            val binding = EntityRevisionBinding(representation)
            val existing = _existing(sample, binding)
            val changes = _changes(sample, binding)
            val expected = _expected(existing, changes, binding)
            When("the real versioned merge applies the generated patch")
            val result = new StandardEntityStore()._merge_versioned_update_record(
              org.goldenport.cncf.datastore.DataStore.CollectionId.Instance("versioned_record_merge"),
              existing, changes, binding)(using context)
            Then("unique Fields correspond completely to changed and untouched owners without managed revision")
            Prop.all(
              "successful merge" |: Prop(result.toOption.isDefined),
              "complete ordered Field correspondence" |: Prop(result.toOption.exists(_.fields == expected.fields)),
              "unique keys without map collapse" |: Prop(result.toOption.exists(record =>
                record.fields.map(_.key).distinct.size == record.fields.size)),
              "exactly one name" |: Prop(result.toOption.exists(_.fields.count(_.key == "name") == 1)),
              "managed revision removed" |: Prop(result.toOption.exists(record =>
                !record.fields.exists(_.key == binding.storageFieldName))),
              "exact ordered canonical security Fields" |: Prop(result.toOption.exists(record =>
                record.fields.filter(field => _security_keys.contains(field.key)) == _security_fields)),
              "absent SQL NULL attributes and retained non-NULL empties" |: Prop {
                _check_sql_read_fields(existing)
                result.toOption.foreach(_check_sql_read_fields)
                result.toOption.isDefined
              })
          }
          When("ScalaCheck executes the deferred total merge law for this representation")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("the law requires at least one hundred successes and zero discards")
          withClue(checked.toString) {
            checked.passed shouldBe true
            checked.succeeded should be >= 100
            checked.discarded shouldBe 0
          }
        }
      }
    }
  }

  "Canonical Entity save Record complement" should {
    Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).foreach { representation =>
      s"preserve complete save Fields for $representation" which {
        Vector(NameSource.Provided, NameSource.Retained, NameSource.Default).foreach { namesource =>
          s"include exactly one name from $namesource with unchanged domain and security Fields" in {
            Given(s"a canonical $representation binding, fixed context and $namesource name source")
            val sample = Sample("Submitted 日λ name", "Complete 雲β title", false, 7L)
            val savecase = _save_case(sample, namesource, representation)
            When("the real StandardEntityStore complements the candidate save Record")
            val result = new StandardEntityStore()._complement_save_record(
              savecase.source, savecase.id, savecase.existing)(using savecase.context)
            Then("the complete ordered result retains one name, domain/security/empty Fields and absent SQL NULL attributes")
            _check_save_fields(result, savecase)
          }
        }

        "obey complete save correspondence for at least one hundred total bounded samples" in {
          Given("nonempty bounded ASCII, Japanese and Greek names/titles, all name sources and varying business values")
          val samples = for {
            namesize <- Gen.chooseNum(1, 40)
            titlesize <- Gen.chooseNum(1, 60)
            namecharacter <- Gen.oneOf("n", "日", "λ")
            titlecharacter <- Gen.oneOf("t", "雲", "β")
            rename <- Gen.oneOf(false, true)
            revision <- Gen.chooseNum(1L, 1000000L)
          } yield Sample(namecharacter * namesize, titlecharacter * titlesize, rename, revision)
          val namesources = Gen.oneOf(NameSource.Provided, NameSource.Retained, NameSource.Default)
          val property = Prop.forAll(samples, namesources) { (sample, namesource) =>
            Given(s"a fresh canonical $representation save with $namesource name and complete source Fields")
            val savecase = _save_case(sample, namesource, representation)
            When("the real save complement applies the generated candidate")
            val result = new StandardEntityStore()._complement_save_record(
              savecase.source, savecase.id, savecase.existing)(using savecase.context)
            Then("every ordered Field matches the independent save expectation without duplicate name or SQL NULL attributes")
            Prop {
              _check_save_fields(result, savecase)
              true
            }
          }
          When("ScalaCheck executes the deferred total save law for this representation")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
          Then("the save law requires at least one hundred successes and zero discards")
          withClue(checked.toString) {
            checked.passed shouldBe true
            checked.succeeded should be >= 100
            checked.discarded shouldBe 0
          }
        }
      }
    }
  }

  private def _merge_context: ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(new org.goldenport.cncf.datastore.DataStore.InMemoryDataStore(
      org.goldenport.cncf.unitofwork.CommitRecorder.noop))
    context
  }

  private def _save_case(
    sample: Sample,
    namesource: NameSource,
    representation: EntityRevisionRepresentation
  ): SaveCase = {
    val fixture = EntityObservedDeleteCanonicalFixture.fixture(
      token = "save_complement", representation = representation, principal = "save_principal")
    val context = fixture.context
    val id = fixture.id
    val submittedname = sample.oldname + (if (sample.rename) " renamed" else "")
    val retainedname = sample.oldname + " retained"
    val creation = java.time.Instant.parse("2026-10-01T01:02:03Z")
    val oldupdate = java.time.Instant.parse("2026-10-02T02:03:04Z")
    val content = "complete content 日λ " * 300 + sample.oldtitle
    val source = Record.dataAuto(
      "id" -> id.value, "short_id" -> id.parts.entropy,
      "created_at" -> creation, "created_by" -> "creation_author",
      "updated_at" -> oldupdate, "updated_by" -> "previous_author",
      "post_status" -> "draft", "aliveness" -> "alive") ++
      (if (namesource == NameSource.Provided) Record.dataAuto("name" -> submittedname) else Record.empty) ++
      Record(_security_fields) ++ Record.dataAuto(
        "trace_id" -> "retained_trace", "correlation_id" -> "retained_correlation",
        "title" -> sample.oldtitle, "business" -> sample.revision,
        "audit" -> ("complete audit " + sample.oldname), "content" -> content) ++
      Record(_empty_fields)
    val existing = Record.dataAuto(
      "id" -> id.value, "short_id" -> id.parts.entropy,
      "created_at" -> creation, "created_by" -> "creation_author",
      "updated_at" -> oldupdate, "updated_by" -> "previous_author",
      "post_status" -> "draft", "aliveness" -> "alive") ++
      (if (namesource == NameSource.Default) Record.empty else Record.dataAuto("name" -> retainedname)) ++
      Record(_security_fields) ++ Record.dataAuto(
        "trace_id" -> "retained_trace", "correlation_id" -> "retained_correlation",
        "title" -> sample.oldtitle, "business" -> sample.revision,
        "audit" -> ("complete audit " + sample.oldname), "content" -> content) ++
      Record(_empty_fields)
    val expectedname = namesource match {
      case NameSource.Provided => submittedname
      case NameSource.Retained => retainedname
      case NameSource.Default => context.security.principal.id.value
    }
    val expected = Record.dataAuto(
      "id" -> id.value, "short_id" -> id.parts.entropy, "name" -> expectedname,
      "created_at" -> creation, "created_by" -> "creation_author",
      "updated_at" -> EntityObservedDeleteCanonicalFixture.instant,
      "updated_by" -> context.security.principal.id.value,
      "post_status" -> "draft", "aliveness" -> "alive") ++
      Record(_security_fields) ++ Record.dataAuto(
        "trace_id" -> "retained_trace", "correlation_id" -> "retained_correlation",
        "title" -> sample.oldtitle, "business" -> sample.revision,
        "audit" -> ("complete audit " + sample.oldname), "content" -> content) ++
      Record(_empty_fields)
    SaveCase(context, id, source, Some(existing), expected)
  }

  private def _check_save_fields(result: Record, savecase: SaveCase): Unit = {
    withClue(s"ordered keys: ${result.fields.map(_.key)}; expected keys: ${savecase.expected.fields.map(_.key)}") {
      (result.fields == savecase.expected.fields) shouldBe true
    }
    result.fields.map(_.key).distinct.size shouldBe result.fields.size
    result.fields.count(_.key == "name") shouldBe 1
    result.getString("name") shouldBe savecase.expected.getString("name")
    _check_sql_read_fields(savecase.source)
    savecase.existing.foreach(_check_sql_read_fields)
    _check_sql_read_fields(savecase.expected)
    _check_sql_read_fields(result)
    And("the four canonical security Fields retain their exact values and order")
    withClue(s"security keys: ${result.fields.filter(field => _security_keys.contains(field.key)).map(_.key)}") {
      (result.fields.filter(field => _security_keys.contains(field.key)) == _security_fields) shouldBe true
    }
  }

  private val _permission_json =
    """{"owner":{"read":true,"write":true,"execute":false},"group":{"read":true,"write":false,"execute":false},"other":{"read":false,"write":false,"execute":false}}"""
  private val _security_keys = Set("owner_id", "group_id", "privilege_id", "permission")
  private val _security_fields = Vector(
    Field("owner_id", Field.Value.Single("existing_owner")),
    Field("group_id", Field.Value.Single("existing_group")),
    Field("privilege_id", Field.Value.Single("existing_privilege")),
    Field("permission", Field.Value.Single(_permission_json)))

  private val _empty_fields = Vector(
    Field("empty_text", Field.Value.Single("")),
    Field("empty_record", Field.Value.Single(Record.empty)),
    Field("empty_vector", Field.Value.Single(Vector.empty[Any])))

  private def _check_sql_read_fields(record: Record): Unit = {
    record.keySet.contains("business_null") shouldBe false
    record.keySet.contains("audit_null") shouldBe false
    (record.fields.filter(field => _empty_fields.exists(_.key == field.key)) == _empty_fields) shouldBe true
  }

  private def _existing(sample: Sample, binding: EntityRevisionBinding): Record =
    Record.dataAuto(
      "id" -> "existing-identity", "short_id" -> "existing-shortid", "name" -> sample.oldname,
      "created_at" -> "existing-creation", "created_by" -> "existing-author",
      "post_status" -> "draft", "aliveness" -> "alive", "tenant_id" -> "existing-tenant",
      "organization_id" -> "existing-organization", "publish_at" -> "existing-publication",
      "public_at" -> "existing-public", "published_by" -> "existing-publisher",
      "owner_id" -> "existing_owner", "group_id" -> "existing_group",
      "privilege_id" -> "existing_privilege", "permission" -> _permission_json,
      "title" -> sample.oldtitle, "business" -> "untouched business", "audit" -> "untouched audit",
      binding.storageFieldName -> sample.revision) ++
      Record(_empty_fields)

  private def _changes(sample: Sample, binding: EntityRevisionBinding): Record =
    Record.dataAuto("title" -> (sample.oldtitle + " changed"), binding.storageFieldName -> (sample.revision + 1L)) ++
      (if (sample.rename) Record.dataAuto("name" -> (sample.oldname + " renamed")) else Record.empty)

  private def _expected(existing: Record, changes: Record, binding: EntityRevisionBinding): Record = {
    val changed = changes.fields.filterNot(_.key == binding.storageFieldName)
    val changedkeys = changed.map(_.key).toSet
    Record(changed ++ existing.fields.filterNot(field =>
      field.key == binding.storageFieldName || changedkeys.contains(field.key)))
  }
}

private object EntityVersionedRecordMergeSpec {
  private enum NameSource {
    case Provided, Retained, Default
  }

  private final case class SaveCase(
    context: ExecutionContext,
    id: EntityId,
    source: Record,
    existing: Option[Record],
    expected: Record
  )

  final case class Sample(oldname: String, oldtitle: String, rename: Boolean, revision: Long)

  private given Shrink[NameSource] = Shrink {
    case NameSource.Provided => Stream(NameSource.Retained, NameSource.Default)
    case NameSource.Retained => Stream(NameSource.Default)
    case NameSource.Default => Stream.empty
  }

  given Shrink[Sample] = Shrink { sample =>
    val candidates = Vector(
      if (sample.oldname.length > 1) Some(sample.copy(oldname = sample.oldname.take(math.max(1, sample.oldname.length / 2)))) else None,
      if (sample.oldtitle.length > 1) Some(sample.copy(oldtitle = sample.oldtitle.take(math.max(1, sample.oldtitle.length / 2)))) else None,
      if (sample.rename) Some(sample.copy(rename = false)) else None,
      if (sample.revision > 1L) Some(sample.copy(revision = math.max(1L, sample.revision / 2L))) else None)
    candidates.flatten.toStream
  }
}
