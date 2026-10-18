package org.goldenport.cncf.datastore

import com.mysql.cj.jdbc.MysqlDataSource
import javax.sql.DataSource
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.EntityCreateProviderFixture.{LiveMysqlContainer, MysqlDatabase, sqliteSource}
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.*
import org.goldenport.cncf.datastore.sql.{MySqlDialectDriver, PostgreSqlDialectDriver, SqlDataStore, SqlDialectDriver, SqliteDialectDriver}
import org.goldenport.cncf.directive.{Query as EntityQuery}
import org.goldenport.cncf.entity.EntityObservedDeleteCanonicalFixture.{reason, success}
import org.goldenport.cncf.entity.EntityObservedDeleteCanonicalProviderFixture.withSqlite
import org.goldenport.record.{Field, Record}
import org.scalacheck.{Gen, Prop, Shrink, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision
import org.simplemodeling.model.directive.Update
import io.circe.parser.parse
import scala.util.control.NonFatal

/*
 * @since   Oct. 14, 2026
 * @version Oct. 19, 2026
 */
@org.scalatest.tags.Slow
final class SqlMutationAcknowledgementSafetySpec extends AnyWordSpec with Matchers with GivenWhenThen with PostgresqlAcceptance {
  import SqlMutationAcknowledgementSafetySpec.*
  import SqlMutationAcknowledgementSafetySpec.given

  private var _mysql_container: Option[LiveMysqlContainer] = None
  private val _backends = Vector("postgresql", "sqlite", "mysql")
  private val _actions = Vector("create", "direct", "cas", "versioned", "physical-delete", "logical-delete", "conditional", "notmatched")
  private val _ack_modes = Vector(AcknowledgementMode.Reject, AcknowledgementMode.Indeterminate, AcknowledgementMode.Throw, AcknowledgementMode.Null)
  private val _comparison_samples = for {
    labelsize <- Gen.chooseNum(1, 12)
    label <- Gen.listOfN(labelsize, Gen.oneOf('a', 'Z', '日', '界', 'λ', 'β')).map(_.mkString)
    numberssize <- Gen.chooseNum(1, 4)
    numbers <- Gen.listOfN(numberssize, Gen.chooseNum(-10L, 10L)).map(_.toVector)
    ownerwrite <- Gen.oneOf(false, true)
    replace <- Gen.oneOf(false, true)
  } yield SqlComparisonSample(label, ownerwrite, numbers, replace)
  private val _raw_text_cases = Vector(
    ("significant whitespace", "  ordinary text  ", " ordinary text  "),
    ("malformed object", "{broken:before}", "{broken:after}"),
    ("malformed array", "[broken,before]", "[broken,after]"),
    ("scalar boolean", "true", "false"),
    ("scalar number", "1", "2"),
    ("JSON object whitespace", "{\"value\":1}", " { \"value\":1 } "),
    ("JSON array whitespace", "[1,true]", " [ 1, true ] "))
  private val _null_samples = for {
    labelsize <- Gen.chooseNum(1, 12)
    label <- Gen.listOfN(labelsize, Gen.oneOf('a', 'Z', '日', '界', 'λ', 'β')).map(_.mkString)
    replace <- Gen.oneOf(false, true)
    normalize <- Gen.oneOf(false, true)
  } yield SqlNullSample(label, replace, normalize)

  override protected def beforeAll(): Unit = {
    if (!sys.env.get("CNCF_LIVE_MYSQL_TEST").contains("true"))
      throw new IllegalStateException("Required SQL acknowledgement acceptance needs CNCF_LIVE_MYSQL_TEST=true")
    super.beforeAll()
    val container = new LiveMysqlContainer()
    try {
      container.start()
      _mysql_container = Some(container)
    } catch {
      case NonFatal(error) => container.stop(); super.afterAll(); throw error
    }
  }
  override protected def afterAll(): Unit =
    try {
      _mysql_container.foreach(_.stop())
      _mysql_container = None
    } finally super.afterAll()

  "Actual SQL mutation acknowledgement and cleanup" should {
    "preserve the three transaction-wrapper boundaries on every required live backend" which {
      for { backend <- _backends; action <- _actions; mode <- _ack_modes } {
        s"PG-E9/PG-E10 distinguish $backend $action $mode without replay or false success" in {
          Given("an actual selected SQL backend, independent durable reader and complete selected/unrelated rows")
          _with_backend(backend) { (dialect, datasource, reader) =>
            val faults = new ConnectionFaults()
            val writer = new AcknowledgementStore(dialect, new FaultDataSource(datasource, faults))
            val setup = new MutationSession(s"ack_${backend}_${action.replace('-', '_')}_$mode", writer)
            try {
              setup.seed(status = if (action == "notmatched") "closed" else "open")
              val before = _snapshot(setup, reader)
              faults.arm(failRollback = false)
              writer.arm(mode)
              When("one real wrapper reaches explicit commit rejection or actual commit with indeterminate, escaped or missing acknowledgement")
              val result = _execute(action, setup, writer)
              val after = _snapshot(setup, reader)
              Then("the actual boundary returns structured failure, makes one attempt and preserves the proper durable outcome")
              result shouldBe a[Consequence.Failure[?]]
              writer.commitAttempts shouldBe 1
              faults.rollbackAttempts shouldBe 1
              faults.unsafeRestores shouldBe 0
              if (mode == AcknowledgementMode.Reject) {
                reason(result) shouldBe Some("transaction-failure")
                after shouldBe before
              } else {
                reason(result) shouldBe Some("transaction-indeterminate")
                _require_committed(action, setup, before, after)
              }
            } finally success(writer.closeC())
          }
        }
      }
    }
    "discard only the unfinished borrowed connection without an implicit commit" which {
      _backends.foreach { backend =>
        s"PG-E9/PG-E10 retain $backend actual rollback-failure and committed-pair semantics" in {
          Given("actual native connection proxies observing rollback, abort and auto-commit on the selected real backend")
          _with_backend(backend) { (dialect, datasource, reader) =>
            for { mode <- Vector(AcknowledgementMode.RejectRoot, AcknowledgementMode.ThrowRoot, AcknowledgementMode.NullRoot); failrollback <- Vector(false, true) } {
              Given("a complete independent baseline and a real side write before the rejected root")
              val faults = new ConnectionFaults()
              val writer = new AcknowledgementStore(dialect, new FaultDataSource(datasource, faults))
              val setup = new MutationSession(s"rollback_${backend}_${mode}_$failrollback", writer)
              try {
                setup.seed()
                val before = _snapshot(setup, reader)
                faults.arm(failRollback = failrollback)
                writer.arm(mode)
                When("the body rejects, throws or returns null after side work and the actual connection performs the selected rollback outcome")
                val result = writer.createEntity(setup.createPlan())(using setup.executionContext)
                Then("verified rollback retains provider failure while unknown rollback discards only that connection without implicit publication")
                reason(result) shouldBe Some(if (failrollback) "transaction-indeterminate" else "provider-failure")
                writer.sideAttempts shouldBe 1
                writer.rootAttempts shouldBe 1
                writer.commitAttempts shouldBe 0
                faults.rollbackAttempts shouldBe 1
                faults.abortAttempts shouldBe (if (failrollback) 1 else 0)
                faults.unsafeRestores shouldBe 0
                _snapshot(setup, reader) shouldBe before
              } finally success(writer.closeC())
            }
            And("a separate actual commit followed by lost acknowledgement and refused rollback retains the correct complete pair")
            val faults = new ConnectionFaults()
            val writer = new AcknowledgementStore(dialect, new FaultDataSource(datasource, faults))
            val setup = new MutationSession(s"committed_rollback_${backend}", writer)
            try {
              setup.seed()
              val before = _snapshot(setup, reader)
              faults.arm(failRollback = true)
              writer.arm(AcknowledgementMode.Indeterminate)
              When("actual connection.commit publishes the pair before the acknowledgement and rollback both become uncertain")
              val result = writer.createEntity(setup.createPlan())(using setup.executionContext)
              val after = _snapshot(setup, reader)
              Then("exactly one committed pair survives with no implicit restore, no retry and an independently usable reader")
              reason(result) shouldBe Some("transaction-indeterminate")
              writer.commitAttempts shouldBe 1
              faults.rollbackAttempts shouldBe 1
              faults.abortAttempts shouldBe 1
              faults.unsafeRestores shouldBe 0
              _require_committed("create", setup, before, after)
            } finally success(writer.closeC())
          }
        }
      }
    }
  }

  "SQL storage-correspondent business comparison" should {
    _backends.foreach { backend =>
      s"retain authoritative revision and side state on actual $backend" which {
        "compare explicitly encoded object and array text in both Patch and Replace forms" in {
          Given("an actual SQL writer, independent reader and complete selected/unrelated storage targets")
          _with_backend(backend) { (dialect, datasource, reader) =>
            val writer = new SqlDataStore(dialect, datasource)
            val setup = new MutationSession(s"comparison_${backend}_fixed", writer)
            try {
              setup.seed()
              val originalroot = setup.load(reader)
              val originalbody = _load_comparison_body(setup, reader)
              val samples = Vector(SqlComparisonSample("日本語λ", false, Vector(-10L, 0L, 10L), false),
                SqlComparisonSample("aZβ", true, Vector(1L), false))
              for { sample <- samples; replace <- Vector(false, true) }
                _require_sql_comparison(setup, writer, reader, originalroot, originalbody, sample.copy(replace = replace))
            } finally success(writer.closeC())
          }
        }

        "preserve the total bounded carrier law for at least one hundred real SQL samples" in {
          Given("bounded nonempty labels and Long arrays, both mutation forms and a strictly reducing companion shrinker")
          _with_backend(backend) { (dialect, datasource, reader) =>
            val writer = new SqlDataStore(dialect, datasource)
            val setup = new MutationSession(s"comparison_${backend}_law", writer)
            try {
              setup.seed()
              val originalroot = setup.load(reader)
              val originalbody = _load_comparison_body(setup, reader)
              val property = Prop.forAll(_comparison_samples) { sample =>
                _require_sql_comparison(setup, writer, reader, originalroot, originalbody, sample)
                Prop.passed
              }
              When("ScalaCheck sequentially reseeds INITIAL1 and interprets each real SQL comparison on shared bounded rows/pools")
              val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
              Then("the backend correspondence law requires at least one hundred successes and zero discards")
              withClue(checked.toString) {
                checked.passed shouldBe true
                checked.succeeded should be >= 100
                checked.discarded shouldBe 0
              }
            } finally success(writer.closeC())
          }
        }

        _raw_text_cases.zipWithIndex.foreach { case ((label, beforetext, aftertext), index) =>
          s"retain exact $label and distinguish genuinely changed text" in {
            Given("an actual SQL writer and independent reader with complete selected/unrelated storage targets")
            _with_backend(backend) { (dialect, datasource, reader) =>
              val writer = new SqlDataStore(dialect, datasource)
              val setup = new MutationSession(s"comparison_${backend}_text_$index", writer)
              try {
                setup.seed()
                _require_raw_text_comparison(setup, writer, reader, setup.load(reader),
                  _load_comparison_body(setup, reader), beforetext, aftertext)
              } finally success(writer.closeC())
            }
          }
        }
      }
    }
  }

  "SQL NULL Record read/write boundary" should {
    _backends.foreach { backend =>
      s"retain logical and physical correspondence on actual $backend" which {
        Vector(false, true).foreach { normalize =>
          s"omit only SQL NULL across load/search/projection/JSON and generic update/save with normalization $normalize" in {
            Given("a plain real SQL writer and independent matching reader with complete selected/unrelated rows and non-NULL empty values")
            _with_backend(backend) { (dialect, datasource, _) =>
              _with_null_session(backend, dialect, datasource, normalize, "generic") { (setup, writer, reader) =>
                val key = _null_key(normalize)
                val original = _null_physical(setup, dialect, datasource)
                When("the independent reader loads the actual non-NULL seed")
                val loaded = setup.load(reader)
                Then("all ordered seed Fields retain exact decoded values including non-NULL empties")
                _check_null_logical(loaded, _null_expected(original(setup.rootCollection.collectionName), setup.rootEntry, normalize, backend))
                When("an omitted generic save changes only title while nullable_column is non-NULL")
                success(writer.save(setup.rootCollection, setup.rootEntry, Record.dataAuto("title" -> "Save omitted before clear"))(using setup.executionContext))
                val saveomittedphysical = _null_physical(setup, dialect, datasource)
                val saveomitted = setup.load(reader)
                Then("generic merge save retains the existing nullable label and complete ordered non-NULL Fields")
                saveomitted.getString(key) shouldBe Some("nullable 日λ")
                _check_null_physical(saveomittedphysical, _null_changed(original, setup.rootCollection, setup.rootEntry,
                  Map("title" -> "Save omitted before clear")))
                _check_null_logical(saveomitted, _null_expected(saveomittedphysical(setup.rootCollection.collectionName), setup.rootEntry, normalize, backend))
                When("the first generic save clear explicitly assigns SQL NULL to that non-NULL column")
                success(writer.save(setup.rootCollection, setup.rootEntry, _null_clear)(using setup.executionContext))
                val saveclearphysical = _null_physical(setup, dialect, datasource)
                val saveclear = setup.load(reader)
                Then("generic save clear returns absent logical state while retaining exact physical columns and unrelated/body rows")
                _check_null_physical(saveclearphysical, _null_changed(saveomittedphysical, setup.rootCollection, setup.rootEntry, Map("nullable_column" -> null)))
                _check_null_cell(saveclearphysical(setup.rootCollection.collectionName), setup.rootEntry)
                _check_null_logical(saveclear, _null_expected(saveclearphysical(setup.rootCollection.collectionName), setup.rootEntry, normalize, backend))
                Given("the same bounded generic selected row reseeded to its independently specified non-NULL baseline")
                success(writer.save(setup.rootCollection, setup.rootEntry,
                  _null_fields("nullable 日λ") ++ Record.dataAuto("title" -> "Before"))(using setup.executionContext))
                When("an omitted generic update changes only title while nullable_column is still non-NULL")
                success(writer.update(setup.rootCollection, setup.rootEntry, Record.dataAuto("title" -> "Omitted before clear"))(using setup.executionContext))
                val omitted = setup.load(reader)
                val omittedphysical = _null_physical(setup, dialect, datasource)
                Then("the existing nullable value and every other physical column/cell remain")
                omitted.getString(key) shouldBe Some("nullable 日λ")
                _check_null_physical(omittedphysical, _null_changed(original, setup.rootCollection, setup.rootEntry, Map("title" -> "Omitted before clear")))
                _check_null_logical(omitted, _null_expected(omittedphysical(setup.rootCollection.collectionName), setup.rootEntry, normalize, backend))

                When("an explicit generic update clear assigns SQL NULL and all independent read surfaces observe it")
                success(writer.update(setup.rootCollection, setup.rootEntry, _null_clear)(using setup.executionContext))
                val cleared = setup.load(reader)
                val physical = _null_physical(setup, dialect, datasource)
                val searched = success(reader.search(setup.rootCollection, QueryDirective(Query.Empty,
                  order = QueryOrder.By("id", OrderDirection.Asc)))).records
                val predicate = Query.Expr(EntityQuery.IsNull("nullable_column"))
                val nullrows = success(reader.search(setup.rootCollection, QueryDirective(predicate))).records
                val projected = success(reader.search(setup.rootCollection, QueryDirective(predicate,
                  projection = QueryProjection.Fields(Vector("nullable_column"))))).records
                val missing = success(reader.load(setup.rootCollection, DataStore.StringEntryId("missing"))(using setup.executionContext))
                val json = parse(cleared.toJsonString).toOption.get.asObject.get
                Then("only the NULL attribute and serialized key are absent while the existing row, ordered empties and physical NULL remain")
                _check_null_physical(physical, _null_changed(omittedphysical, setup.rootCollection, setup.rootEntry, Map("nullable_column" -> null)))
                _check_null_cell(physical(setup.rootCollection.collectionName), setup.rootEntry)
                _check_null_logical(cleared, _null_expected(physical(setup.rootCollection.collectionName), setup.rootEntry, normalize, backend))
                cleared.keySet.contains(key) shouldBe false
                json.contains(key) shouldBe false
                missing shouldBe None
                searched.map(_.getString("id")) shouldBe Vector(Some("selected"), Some("unrelated"))
                searched.foreach { record =>
                  _check_null_logical(record, _null_expected(physical(setup.rootCollection.collectionName),
                    DataStore.StringEntryId(record.getString("id").get), normalize, backend))
                }
                nullrows should have size 1
                _check_null_logical(nullrows.head, cleared)
                projected.map(_.fields) shouldBe Vector(Vector(Field("id", Field.Value.Single(setup.rootEntry.print))))
                _check_null_physical(_null_physical(setup, dialect, datasource), physical)

                When("an omitted generic update changes only title while the nullable column is already SQL NULL")
                success(writer.update(setup.rootCollection, setup.rootEntry, Record.dataAuto("title" -> "Update omitted after clear"))(using setup.executionContext))
                val nullomittedphysical = _null_physical(setup, dialect, datasource)
                val nullomitted = setup.load(reader)
                Then("generic update omission preserves complete physical NULL state and absent readback")
                _check_null_physical(nullomittedphysical, _null_changed(physical, setup.rootCollection, setup.rootEntry,
                  Map("title" -> "Update omitted after clear")))
                _check_null_cell(nullomittedphysical(setup.rootCollection.collectionName), setup.rootEntry)
                _check_null_logical(nullomitted, _null_expected(nullomittedphysical(setup.rootCollection.collectionName), setup.rootEntry, normalize, backend))
                When("an omitted generic save retains the cleared state and an explicit save clear is repeated")
                success(writer.save(setup.rootCollection, setup.rootEntry, Record.dataAuto("title" -> "Omitted after clear"))(using setup.executionContext))
                success(writer.save(setup.rootCollection, setup.rootEntry, _null_clear)(using setup.executionContext))
                val savedphysical = _null_physical(setup, dialect, datasource)
                val saved = setup.load(reader)
                Then("merge save makes no nullable assignment for omission and explicit clear preserves the full physical NULL state")
                _check_null_physical(savedphysical, _null_changed(physical, setup.rootCollection, setup.rootEntry, Map("title" -> "Omitted after clear")))
                _check_null_logical(saved, _null_expected(savedphysical(setup.rootCollection.collectionName), setup.rootEntry, normalize, backend))
                _check_null_cell(savedphysical(setup.rootCollection.collectionName), setup.rootEntry)

                Given("the same preprepared nullable column and a strict-create command with explicit SetNull")
                val freshentry = DataStore.StringEntryId("fresh")
                val freshfields = _null_fields("fresh 日λ").fields.map { field =>
                  if (field.key == "nullable_column") field.copy(value = Field.Value.Single(Update.SetNull)) else field
                }
                val createplan = EntityCreatePlan(setup.rootCollection, freshentry,
                  Record(freshfields :+ Field("cncf_revision", Field.Value.Single(1L))))
                When("the native strict-create provider acknowledges its actual nullable row")
                val created = success(writer.createEntity(createplan)(using setup.executionContext))
                val createdphysical = _null_physical(setup, dialect, datasource)
                val fresh = success(reader.load(setup.rootCollection, freshentry)(using setup.executionContext)).get
                Then("strict CreateResult uses authoritative absent-NULL Fields and all previous physical rows/body targets remain")
                _check_null_logical(created.record, fresh)
                _check_null_logical(fresh, _null_expected(createdphysical(setup.rootCollection.collectionName), freshentry, normalize, backend))
                fresh.keySet.contains(key) shouldBe false
                _check_null_cell(createdphysical(setup.rootCollection.collectionName), freshentry)
                val rootname = setup.rootCollection.collectionName
                createdphysical(rootname).columns shouldBe savedphysical(rootname).columns
                (createdphysical(rootname).rows.filterNot(_("id") == freshentry.print) == savedphysical(rootname).rows) shouldBe true
                _check_null_physical(createdphysical - rootname, savedphysical - rootname)
              }
            }
          }

          s"retain direct/CAS always-write clear and omission semantics with normalization $normalize" in {
            Given("real direct/CAS capabilities, matching independent reader and complete root/body/unrelated state")
            _with_backend(backend) { (dialect, datasource, _) =>
              _with_null_session(backend, dialect, datasource, normalize, "native") { (setup, writer, reader) =>
                val originalroot = setup.load(reader)
                val originalbody = _load_comparison_body(setup, reader)
                Vector(false, true).foreach { cas =>
                  _seed_null_sample(setup, writer, originalroot, originalbody, "native 日λ")
                  val nonnullbefore = _null_physical(setup, dialect, datasource)
                  When("the native primitive omits a still non-NULL attribute while changing only title")
                  val nonnullresult = _null_native(setup, writer, Record.dataAuto("title" -> "Native nonnull omission"), 1L, cas)
                  val nonnullrow = setup.load(reader)
                  val nonnullphysical = _null_physical(setup, dialect, datasource)
                  Then("native omission preserves the exact nullable value and full state with revision two")
                  nonnullresult shouldBe EntityMutationProviderResult.Applied(EntityMutationProviderReadback.Authoritative(nonnullrow))
                  nonnullrow.getString(_null_key(normalize)) shouldBe Some("native 日λ")
                  _check_null_physical(nonnullphysical, _null_changed(nonnullbefore, setup.rootCollection, setup.rootEntry,
                    Map("title" -> "Native nonnull omission", "cncf_revision" -> 2L)))
                  _check_null_logical(nonnullrow, _null_expected(nonnullphysical(setup.rootCollection.collectionName), setup.rootEntry, normalize, backend))
                  Given("the bounded native rows independently reseeded to INITIAL1 before explicit clear")
                  _seed_null_sample(setup, writer, originalroot, originalbody, "native 日λ")
                  val before = _null_physical(setup, dialect, datasource)
                  When("the selected native primitive clears a non-NULL attribute at revision one")
                  val clearresult = _null_native(setup, writer, _null_clear, 1L, cas)
                  val cleared = setup.load(reader)
                  val clearphysical = _null_physical(setup, dialect, datasource)
                  Then("actual readback omits the attribute and the always-write primitive advances to revision two")
                  clearresult shouldBe EntityMutationProviderResult.Applied(EntityMutationProviderReadback.Authoritative(cleared))
                  _check_null_physical(clearphysical, _null_changed(before, setup.rootCollection, setup.rootEntry,
                    Map("nullable_column" -> null, "cncf_revision" -> 2L)))
                  _check_null_cell(clearphysical(setup.rootCollection.collectionName), setup.rootEntry)
                  _check_null_logical(cleared, _null_expected(clearphysical(setup.rootCollection.collectionName), setup.rootEntry, normalize, backend))

                  When("the same native clear repeats at current revision two")
                  val repeated = _null_native(setup, writer, _null_clear, 2L, cas)
                  val repeatedrow = setup.load(reader)
                  val repeatedphysical = _null_physical(setup, dialect, datasource)
                  Then("native direct/CAS retains its existing always-write revision-three behavior")
                  repeated shouldBe EntityMutationProviderResult.Applied(EntityMutationProviderReadback.Authoritative(repeatedrow))
                  _check_null_physical(repeatedphysical, _null_changed(clearphysical, setup.rootCollection, setup.rootEntry, Map("cncf_revision" -> 3L)))
                  _check_null_logical(repeatedrow, _null_expected(repeatedphysical(setup.rootCollection.collectionName), setup.rootEntry, normalize, backend))

                  When("an omitted native attribute updates only title at current revision three")
                  val omitted = _null_native(setup, writer, Record.dataAuto("title" -> "Native omitted"), 3L, cas)
                  val omittedrow = setup.load(reader)
                  val omittedphysical = _null_physical(setup, dialect, datasource)
                  Then("revision four and the title change retain the complete physical NULL column and all other targets")
                  omitted shouldBe EntityMutationProviderResult.Applied(EntityMutationProviderReadback.Authoritative(omittedrow))
                  _check_null_physical(omittedphysical, _null_changed(repeatedphysical, setup.rootCollection, setup.rootEntry,
                    Map("title" -> "Native omitted", "cncf_revision" -> 4L)))
                  _check_null_cell(omittedphysical(setup.rootCollection.collectionName), setup.rootEntry)
                  _check_null_logical(omittedrow, _null_expected(omittedphysical(setup.rootCollection.collectionName), setup.rootEntry, normalize, backend))
                }
              }
            }
          }

          s"retain versioned Patch/Replace clear, repeat, omission and stale correspondence with normalization $normalize" in {
            Given("both frozen versioned mutation forms and nonempty multilingual state on real SQL with an independent reader")
            _with_backend(backend) { (dialect, datasource, _) =>
              _with_null_session(backend, dialect, datasource, normalize, "versioned") { (setup, writer, reader) =>
                val originalroot = setup.load(reader)
                val originalbody = _load_comparison_body(setup, reader)
                Vector(false, true).foreach { replace =>
                  _require_null_versioned(setup, writer, reader, dialect, datasource, originalroot, originalbody,
                    SqlNullSample("a日λ", replace, normalize), backend)
                }
              }
            }
          }
        }

        "require at least one hundred total bounded clear/repeat/omit samples across both forms and normalization conventions" in {
          Given("a reducing companion-owned multilingual sample and two bounded normalization-specific SQL sessions")
          _with_backend(backend) { (dialect, datasource, _) =>
            val sessions = Vector(false, true).map { normalize =>
              val writer = new SqlDataStore(dialect, datasource, config = SqlDataStore.Config(normalizeColumnNames = normalize))
              val reader = new SqlDataStore(dialect, datasource, config = SqlDataStore.Config(normalizeColumnNames = normalize))
              val setup = new MutationSession(s"null_${backend}_law_$normalize", writer)
              setup.seed()
              _seed_null_rows(setup, writer)
              (normalize, setup, writer, reader, setup.load(reader), _load_comparison_body(setup, reader))
            }
            try {
              val property = Prop.forAll(_null_samples) { sample =>
                val (_, setup, writer, reader, root, body) = sessions.find(_._1 == sample.normalize).get
                _require_null_versioned(setup, writer, reader, dialect, datasource, root, body, sample, backend)
                Prop.passed
              }
              When("ScalaCheck sequentially reseeds each real SQL sample using the existing bounded rows and pools")
              val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
              Then("the actual backend law requires at least one hundred successes and zero discards without filtering")
              withClue(checked.toString) {
                checked.passed shouldBe true
                checked.succeeded should be >= 100
                checked.discarded shouldBe 0
              }
            } finally sessions.foreach { case (_, _, writer, reader, _, _) =>
              success(reader.closeC())
              success(writer.closeC())
            }
          }
        }
      }
    }
  }

  private val _null_clear = Record(Vector(Field("nullable_column", Field.Value.Single(Update.SetNull))))

  private def _null_key(normalize: Boolean): String = if (normalize) "nullableColumn" else "nullable_column"

  private def _null_fields(label: String): Record = Record(Vector(
    Field("nullable_column", Field.Value.Single(label)),
    Field("retained_text", Field.Value.Single("retained 日λ")),
    Field("empty_text", Field.Value.Single("")),
    Field("numeric_zero", Field.Value.Single(0L)),
    Field("false_value", Field.Value.Single(false)),
    Field("empty_record", Field.Value.Single("{}")),
    Field("empty_vector", Field.Value.Single("[]"))))

  private def _seed_null_rows(setup: MutationSession, writer: SqlDataStore): Unit = {
    success(writer.save(setup.rootCollection, setup.rootEntry, _null_fields("nullable 日λ"))(using setup.executionContext))
    success(writer.save(setup.rootCollection, setup.oldEntry, _null_fields("unrelated β界"))(using setup.executionContext))
  }

  private def _seed_null_sample(setup: MutationSession, writer: SqlDataStore,
    originalroot: Record, originalbody: Record, label: String): Unit = {
    val keys = Set("nullable_column", "nullableColumn", "retained_text", "retainedText",
      "empty_text", "emptyText", "numeric_zero", "numericZero", "false_value", "falseValue",
      "empty_record", "emptyRecord", "empty_vector", "emptyVector")
    val baseline = Record(originalroot.fields.filterNot(field => keys.contains(field.key)))
    _seed_comparison(setup, writer, baseline, originalbody, _null_fields(label))
  }

  private def _with_null_session(backend: String, dialect: SqlDialectDriver, datasource: DataSource,
    normalize: Boolean, label: String)(body: (MutationSession, SqlDataStore, SqlDataStore) => Unit): Unit = {
    val writer = new SqlDataStore(dialect, datasource, config = SqlDataStore.Config(normalizeColumnNames = normalize))
    val reader = new SqlDataStore(dialect, datasource, config = SqlDataStore.Config(normalizeColumnNames = normalize))
    try {
      val setup = new MutationSession(s"null_${backend}_${label}_$normalize", writer)
      setup.seed()
      _seed_null_rows(setup, writer)
      body(setup, writer, reader)
    } finally {
      success(reader.closeC())
      success(writer.closeC())
    }
  }

  private def _null_native(setup: MutationSession, writer: SqlDataStore, changes: Record,
    expected: Long, cas: Boolean): EntityMutationProviderResult =
    if (cas) success(writer.compareAndSetEntity(EntityCompareAndSetMutationPlan(setup.rootCollection,
      setup.rootEntry, "cncf_revision", revision(expected), changes,
      EntityMutationReadbackRequirement.AuthoritativeRecord))(using setup.executionContext))
    else success(writer.mutateEntityDirect(EntityDirectMutationPlan(setup.rootCollection, setup.rootEntry,
      "cncf_revision", changes, EntityMutationReadbackRequirement.AuthoritativeRecord))(using setup.executionContext))

  private def _null_plan(setup: MutationSession, root: Record, changes: Record, replace: Boolean,
    expected: Long, sidecontent: String): EntityVersionedMutationPlan = {
    val plan = versionedPlan(setup.rootCollection, setup.rootEntry, changes, expected, Vector(setup.sideEffect(sidecontent)))
    if (replace) {
      val keys = changes.keySet + "cncf_revision"
      val candidate = Record(root.fields.filterNot(field => keys.contains(field.key)) ++ changes.fields)
      plan.copy(rootMutation = EntityVersionedRootMutation.Replace(candidate))
    } else plan
  }

  private def _require_null_versioned(setup: MutationSession, writer: SqlDataStore, reader: SqlDataStore,
    dialect: SqlDialectDriver, datasource: DataSource, originalroot: Record, originalbody: Record,
    sample: SqlNullSample, backend: String): Unit = {
    Given("a freshly reseeded INITIAL1 row with a nonempty generated nullable label and all ordered non-NULL empty carriers")
    _seed_null_sample(setup, writer, originalroot, originalbody, sample.label)
    val initial = setup.load(reader)
    val before = _null_physical(setup, dialect, datasource)
    val omittedplan = _null_plan(setup, initial, Record.dataAuto("title" -> ("omitted:" + sample.label)),
      sample.replace, 1L, "Published nonnull omission body")
    When("the generated Patch or Replace omits the non-NULL attribute and changes only title")
    val nonnullresult = success(writer.mutateVersionedEntity(omittedplan)(using setup.executionContext))
    val nonnullrow = setup.load(reader)
    val nonnullphysical = _null_physical(setup, dialect, datasource)
    Then("the omitted attribute retains its exact label while revision two and the admitted side state publish")
    nonnullresult shouldBe a[EntityVersionedMutationResult.Applied]
    _check_null_logical(nonnullresult.asInstanceOf[EntityVersionedMutationResult.Applied].record, nonnullrow)
    nonnullrow.getString(_null_key(sample.normalize)) shouldBe Some(sample.label)
    val nonnullexpected = _null_changed(_null_changed(before, setup.rootCollection, setup.rootEntry,
      Map("title" -> ("omitted:" + sample.label), "cncf_revision" -> 2L)), setup.sideCollection, setup.sideEntry,
      Map("content" -> "Published nonnull omission body"))
    _check_null_physical(nonnullphysical, nonnullexpected)
    _check_null_logical(nonnullrow, _null_expected(nonnullphysical(setup.rootCollection.collectionName), setup.rootEntry, sample.normalize, backend))

    Given("the same bounded rows reseeded to INITIAL1 and an exact SetNull command with a real side Save")
    _seed_null_sample(setup, writer, originalroot, originalbody, sample.label)
    val root = setup.load(reader)
    val clearbefore = _null_physical(setup, dialect, datasource)
    val clear = Record(Vector(Field(_null_key(sample.normalize), Field.Value.Single(Update.SetNull))))
    val clearplan = _null_plan(setup, root, clear, sample.replace, 1L, "Published clear body")
    When("the first explicit clear executes against non-NULL state")
    val applied = success(writer.mutateVersionedEntity(clearplan)(using setup.executionContext))
    val cleared = setup.load(reader)
    val committed = _null_physical(setup, dialect, datasource)
    Then("Applied returns actual absent-NULL readback at revision two and the complete prescribed root/body pair")
    applied shouldBe a[EntityVersionedMutationResult.Applied]
    _check_null_logical(applied.asInstanceOf[EntityVersionedMutationResult.Applied].record, cleared)
    _check_null_logical(cleared, _null_expected(committed(setup.rootCollection.collectionName), setup.rootEntry, sample.normalize, backend))
    cleared.keySet.contains(_null_key(sample.normalize)) shouldBe false
    cleared.getLong("cncf_revision") shouldBe Some(2L)
    val expected = _null_changed(_null_changed(clearbefore, setup.rootCollection, setup.rootEntry,
      Map("nullable_column" -> null, "cncf_revision" -> 2L)), setup.sideCollection, setup.sideEntry, Map("content" -> "Published clear body"))
    _check_null_physical(committed, expected)
    _check_null_cell(committed(setup.rootCollection.collectionName), setup.rootEntry)

    Given("the cleared authoritative Record and a deliberately different unpublishable side candidate")
    val repeatplan = _null_plan(setup, cleared, clear, sample.replace, 2L, "Must not publish repeated clear body")
    When("the exact clear repeats at current revision two")
    val repeated = success(writer.mutateVersionedEntity(repeatplan)(using setup.executionContext))
    Then("WriteIfChanged NoOp returns complete ordered raw Fields at revision two with zero side publication")
    _require_comparison_noop(repeated, cleared, revision(2))
    _check_null_physical(_null_physical(setup, dialect, datasource), committed)

    When("that equal clear executes with stale expected revision one")
    val stale = success(writer.mutateVersionedEntity(repeatplan.copy(expectedRevision = Some(revision(1))))(using setup.executionContext))
    Then("ObservedRequired stale admission precedes equality and preserves complete root/body/unrelated state")
    stale shouldBe EntityVersionedMutationResult.Stale(revision(1), revision(2))
    _check_null_physical(_null_physical(setup, dialect, datasource), committed)
    _check_null_logical(setup.load(reader), cleared)

    Given("a title change omitting the already-NULL attribute and its prescribed side body")
    val changes = Record.dataAuto("title" -> ("after-clear:" + sample.label))
    val afterplan = _null_plan(setup, cleared, changes, sample.replace, 2L, "Published null omission body")
    When("the omitted-NULL update executes at current revision two")
    val omitted = success(writer.mutateVersionedEntity(afterplan)(using setup.executionContext))
    val after = setup.load(reader)
    val afterphysical = _null_physical(setup, dialect, datasource)
    Then("revision three and exact root/body changes retain physical NULL and every ordered non-NULL empty Field")
    omitted shouldBe a[EntityVersionedMutationResult.Applied]
    _check_null_logical(omitted.asInstanceOf[EntityVersionedMutationResult.Applied].record, after)
    _check_null_logical(after, _null_expected(afterphysical(setup.rootCollection.collectionName), setup.rootEntry, sample.normalize, backend))
    _check_null_cell(afterphysical(setup.rootCollection.collectionName), setup.rootEntry)
    val afterexpected = _null_changed(_null_changed(committed, setup.rootCollection, setup.rootEntry,
      Map("title" -> ("after-clear:" + sample.label), "cncf_revision" -> 3L)), setup.sideCollection, setup.sideEntry,
      Map("content" -> "Published null omission body"))
    _check_null_physical(afterphysical, afterexpected)
    When("the same omitted candidate repeats at current revision three with another side candidate")
    val omittedrepeat = success(writer.mutateVersionedEntity(_null_plan(setup, after, changes, sample.replace,
      3L, "Must not publish omitted repeat body"))(using setup.executionContext))
    Then("NoOp retains the exact authoritative Record and all physical state at revision three")
    _require_comparison_noop(omittedrepeat, after, revision(3))
    _check_null_physical(_null_physical(setup, dialect, datasource), afterphysical)
  }

  private def _null_physical(setup: MutationSession, dialect: SqlDialectDriver,
    datasource: DataSource): Map[String, StoredTable] = {
    val connection = datasource.getConnection()
    try Vector(setup.rootCollection, setup.sideCollection, setup.successorCollection).map { collection =>
      val statement = connection.createStatement()
      try {
        val rows = statement.executeQuery(s"SELECT * FROM ${dialect.quoteIdentifier(collection.collectionName)} ORDER BY ${dialect.quoteIdentifier("id")}")
        try {
          val metadata = rows.getMetaData
          val columns = (1 to metadata.getColumnCount).toVector.map(index => metadata.getColumnLabel(index) -> metadata.getColumnTypeName(index))
          val records = Vector.newBuilder[Map[String, Any]]
          while (rows.next()) {
            records += columns.zipWithIndex.map { case ((name, _), index) => name -> rows.getObject(index + 1) }.toMap
          }
          collection.collectionName -> StoredTable(columns, records.result())
        } finally rows.close()
      } finally statement.close()
    }.toMap finally connection.close()
  }

  private def _null_changed(before: Map[String, StoredTable], collection: DataStore.CollectionId,
    entry: DataStore.EntryId, changes: Map[String, Any]): Map[String, StoredTable] = {
    val table = before(collection.collectionName)
    before.updated(collection.collectionName, table.copy(rows = table.rows.map { row =>
      if (row("id") == entry.print) row ++ changes else row
    }))
  }

  private def _check_null_physical(actual: Map[String, StoredTable], expected: Map[String, StoredTable]): Unit =
    withClue(s"complete physical table keys=${expected.keys.toVector.sorted}: ") {
      (actual == expected) shouldBe true
    }

  private def _check_null_cell(table: StoredTable, entry: DataStore.EntryId): Unit = {
    table.columns.count(_._1 == "nullable_column") shouldBe 1
    val row = table.rows.find(_("id") == entry.print).get
    row.contains("nullable_column") shouldBe true
    java.util.Objects.isNull(row("nullable_column")) shouldBe true
  }

  private def _null_expected(table: StoredTable, entry: DataStore.EntryId, normalize: Boolean, backend: String): Record = {
    val row = table.rows.find(_("id") == entry.print).get
    val columns = if (backend == "postgresql") table.columns.sortBy { case (name, _) =>
      if (name == "id") 0 else if (name == "cncf_revision") 1 else 2
    } else table.columns
    val aliases = Map("body_id" -> "bodyId", "successor_id" -> "successorId", "nullable_column" -> "nullableColumn",
      "retained_text" -> "retainedText", "empty_text" -> "emptyText", "numeric_zero" -> "numericZero",
      "false_value" -> "falseValue", "empty_record" -> "emptyRecord", "empty_vector" -> "emptyVector")
    Record(columns.flatMap { case (name, _) =>
      val raw = row(name)
      if (java.util.Objects.isNull(raw)) None else {
        val key = if (normalize) aliases.getOrElse(name, name) else name
        Some(Field(key, Field.Value.Single(raw)))
      }
    })
  }

  private def _check_null_logical(actual: Record, expected: Record): Unit = {
    withClue(s"complete ordered non-NULL keys=${expected.fields.map(_.key)}: ") {
      (actual.fields == expected.fields) shouldBe true
    }
    actual.fields.map(_.key).distinct.size shouldBe actual.fields.size
    Vector("empty_text", "emptyText").filter(actual.keySet.contains).foreach(key => actual.getString(key) shouldBe Some(""))
    Vector("empty_record", "emptyRecord").filter(actual.keySet.contains).foreach { key =>
      actual.getAny(key) shouldBe Some("{}")
    }
    Vector("empty_vector", "emptyVector").filter(actual.keySet.contains).foreach { key =>
      actual.getAny(key) shouldBe Some("[]")
    }
  }

  private def _require_sql_comparison(setup: MutationSession, writer: SqlDataStore, reader: SqlDataStore,
    originalroot: Record, originalbody: Record, sample: SqlComparisonSample): Unit = {
    Given("one freshly reseeded INITIAL1 root with explicitly encoded permission, attributes and Long-array text and exact ordinary/malformed text")
    val note = s" \t${sample.label}\r\n "
    val opaque = s" {broken:${sample.label}} "
    val seed = Record(Record.dataAuto("permission" -> _permission_record(sample.ownerwrite).toJsonString,
      "attributes" -> Record.dataAuto("label" -> sample.label, "count" -> sample.numbers.size.toLong).toJsonString,
      "note" -> note, "opaque" -> opaque).fields :+ Field("numbers", Field.Value.Single(sample.numbers.mkString("[", ",", "]"))))
    _seed_comparison(setup, writer, originalroot, originalbody, seed)
    val root = setup.load(reader)
    val body = _load_comparison_body(setup, reader)
    val before = _snapshot(setup, reader)
    success(EntityRevision.createC(root.asMap("cncf_revision"))) shouldBe EntityRevision.INITIAL
    root.getAny("permission") shouldBe Some(_permission_json(sample.ownerwrite))
    root.getAny("attributes") shouldBe Some(s"{\"label\":\"${sample.label}\",\"count\":${sample.numbers.size}}")
    root.getAny("numbers") shouldBe Some(sample.numbers.mkString("[", ",", "]"))
    root.getString("note") shouldBe Some(note)
    root.getString("opaque") shouldBe Some(opaque)
    val equivalent = Record.dataAuto("permission" -> _permission_json(sample.ownerwrite),
      "attributes" -> s"{\"label\":\"${sample.label}\",\"count\":${sample.numbers.size}}",
      "numbers" -> sample.numbers.mkString("[", ",", "]"))
    val plan = _comparison_plan(setup, root, equivalent, sample.replace, 1L, "Must not publish equivalent body")

    When("the equivalent independently written JSON-string candidate executes at expected revision one")
    val noop = success(writer.mutateVersionedEntity(plan)(using setup.executionContext))
    Then("NoOp returns the complete original authoritative Fields and publishes no root or side change")
    _require_comparison_noop(noop, root, EntityRevision.INITIAL)
    _snapshot(setup, reader) shouldBe before

    When("the same equivalent candidate executes with expected revision two while actual revision remains one")
    val stale = success(writer.mutateVersionedEntity(plan.copy(expectedRevision = Some(revision(2))))(using setup.executionContext))
    Then("ObservedRequired stale admission precedes equality and preserves every snapshot target")
    stale shouldBe a[EntityVersionedMutationResult.Stale]
    stale shouldBe EntityVersionedMutationResult.Stale(revision(2), EntityRevision.INITIAL)
    _snapshot(setup, reader) shouldBe before

    Given("a candidate changing only owner write permission and a real existing-side Save")
    val changed = Record(equivalent.fields.map { field =>
      if (field.key == "permission") field.copy(value = Field.Value.Single(_permission_json(!sample.ownerwrite))) else field
    })
    val changedplan = _comparison_plan(setup, root, changed, sample.replace, 1L, "Published permission-change body")
    When("the genuine permission change executes at the matched revision one")
    val applied = success(writer.mutateVersionedEntity(changedplan)(using setup.executionContext))
    val committedroot = setup.load(reader)
    val committedbody = _load_comparison_body(setup, reader)
    val committed = _snapshot(setup, reader)
    Then("Applied advances exactly to revision two, retains the full domain and publishes exactly the prescribed side body")
    applied shouldBe a[EntityVersionedMutationResult.Applied]
    success(EntityRevision.createC(committedroot.asMap("cncf_revision"))) shouldBe revision(2)
    (applied.asInstanceOf[EntityVersionedMutationResult.Applied].record.fields == committedroot.fields) shouldBe true
    _fixture_map(committedroot) shouldBe _fixture_map(root).updated("permission", _permission_json(!sample.ownerwrite))
      .updated("cncf_revision", committedroot.asMap("cncf_revision"))
    _fixture_map(committedbody) shouldBe _fixture_map(body).updated("content", "Published permission-change body")
    _require_other_targets_unchanged(before, committed)

    Given("the identical changed permission candidate and another deliberately different side candidate")
    val repeatedplan = _comparison_plan(setup, committedroot, changed, sample.replace, 2L, "Must not publish repeated body")
    When("that identical business state executes at expected revision two")
    val repeated = success(writer.mutateVersionedEntity(repeatedplan)(using setup.executionContext))
    Then("NoOp returns full authoritative Fields at revision two and retains the complete committed snapshot")
    _require_comparison_noop(repeated, committedroot, revision(2))
    _snapshot(setup, reader) shouldBe committed
  }

  private def _require_raw_text_comparison(setup: MutationSession, writer: SqlDataStore, reader: SqlDataStore,
    originalroot: Record, originalbody: Record, beforetext: String, aftertext: String): Unit = {
    Given("a selected INITIAL1 root carrying the exact original String and an independently loaded complete root/body baseline")
    _seed_comparison(setup, writer, originalroot, originalbody, Record.dataAuto("note" -> beforetext))
    val root = setup.load(reader)
    val body = _load_comparison_body(setup, reader)
    val before = _snapshot(setup, reader)
    success(EntityRevision.createC(root.asMap("cncf_revision"))) shouldBe EntityRevision.INITIAL
    root.getString("note") shouldBe Some(beforetext)
    val plan = _comparison_plan(setup, root, Record.dataAuto("note" -> beforetext), false, 1L, "Must not publish repeated text body")
    When("the exact same String is repeated with a different side candidate")
    val noop = success(writer.mutateVersionedEntity(plan)(using setup.executionContext))
    Then("NoOp retains exact text, authoritative Fields, revision one and all eight snapshot targets")
    _require_comparison_noop(noop, root, EntityRevision.INITIAL)
    _snapshot(setup, reader) shouldBe before

    Given("a genuinely different String, including significant whitespace, and a prescribed side body")
    val changedplan = _comparison_plan(setup, root, Record.dataAuto("note" -> aftertext), false, 1L, "Published changed-text body")
    When("the changed exact String executes at expected revision one")
    val applied = success(writer.mutateVersionedEntity(changedplan)(using setup.executionContext))
    val committedroot = setup.load(reader)
    val committedbody = _load_comparison_body(setup, reader)
    val committed = _snapshot(setup, reader)
    Then("Applied advances to revision two and stores exact changed text/body while preserving every other baseline key and target")
    applied shouldBe a[EntityVersionedMutationResult.Applied]
    success(EntityRevision.createC(committedroot.asMap("cncf_revision"))) shouldBe revision(2)
    committedroot.getString("note") shouldBe Some(aftertext)
    (applied.asInstanceOf[EntityVersionedMutationResult.Applied].record.fields == committedroot.fields) shouldBe true
    _fixture_map(committedroot) shouldBe _fixture_map(root).updated("note", aftertext)
      .updated("cncf_revision", committedroot.asMap("cncf_revision"))
    _fixture_map(committedbody) shouldBe _fixture_map(body).updated("content", "Published changed-text body")
    _require_other_targets_unchanged(before, committed)
  }

  private def _seed_comparison(setup: MutationSession, writer: SqlDataStore,
    originalroot: Record, originalbody: Record, fields: Record): Unit = {
    val keys = fields.keySet + "cncf_revision"
    val seed = Record(originalroot.fields.filterNot(field => keys.contains(field.key)) ++ fields.fields :+
      Field("cncf_revision", Field.Value.Single(1L)))
    success(writer.save(setup.rootCollection, setup.rootEntry, seed)(using setup.executionContext))
    success(writer.save(setup.sideCollection, setup.sideEntry, originalbody)(using setup.executionContext))
  }

  private def _comparison_plan(setup: MutationSession, root: Record, changes: Record,
    replace: Boolean, expected: Long, sidecontent: String): EntityVersionedMutationPlan = {
    val plan = versionedPlan(setup.rootCollection, setup.rootEntry, changes, expected, Vector(setup.sideEffect(sidecontent)))
    if (replace) {
      val values = changes.asMap
      val candidate = Record(root.fields.filterNot(_.key == "cncf_revision").map { field =>
        values.get(field.key).map(value => field.copy(value = Field.Value.Single(value))).getOrElse(field)
      })
      plan.copy(rootMutation = EntityVersionedRootMutation.Replace(candidate))
    } else plan
  }

  private def _require_comparison_noop(result: EntityVersionedMutationResult, root: Record, expected: EntityRevision): Unit = {
    result shouldBe a[EntityVersionedMutationResult.NoOp]
    val returned = result.asInstanceOf[EntityVersionedMutationResult.NoOp].record
    withClue(s"authoritative ordered keys=${root.fields.map(_.key)}: ") {
      (returned.fields == root.fields) shouldBe true
    }
    success(EntityRevision.createC(returned.asMap("cncf_revision"))) shouldBe expected
  }

  private def _load_comparison_body(setup: MutationSession, reader: DataStore): Record =
    success(reader.load(setup.sideCollection, setup.sideEntry)(using setup.executionContext)).get

  private def _require_other_targets_unchanged(before: Map[String, Option[Map[String, Any]]],
    after: Map[String, Option[Map[String, Any]]]): Unit =
    before.keys.filterNot(key => key == "root" || key == "side").foreach(key => after(key) shouldBe before(key))

  private def _fixture_map(record: Record): Map[String, Any] =
    record.asMap.view.mapValues(_fixture_value).toMap

  private def _fixture_value(value: Any): Any = value match {
    case record: Record => _fixture_map(record)
    case values: Seq[?] => values.map(_fixture_value).toVector
    case other => other
  }

  private def _permission_record(ownerwrite: Boolean): Record =
    Record.dataAuto("owner" -> Record.dataAuto("read" -> true, "write" -> ownerwrite, "execute" -> false),
      "group" -> Record.dataAuto("read" -> true, "write" -> false, "execute" -> false),
      "other" -> Record.dataAuto("read" -> false, "write" -> false, "execute" -> false))

  private def _permission_map(ownerwrite: Boolean): Map[String, Any] =
    Map("owner" -> Map("read" -> true, "write" -> ownerwrite, "execute" -> false),
      "group" -> Map("read" -> true, "write" -> false, "execute" -> false),
      "other" -> Map("read" -> false, "write" -> false, "execute" -> false))

  private def _permission_json(ownerwrite: Boolean): String =
    s"{\"owner\":{\"read\":true,\"write\":$ownerwrite,\"execute\":false},\"group\":{\"read\":true,\"write\":false,\"execute\":false},\"other\":{\"read\":false,\"write\":false,\"execute\":false}}"

  private def _execute(action: String, setup: MutationSession, writer: AcknowledgementStore): Consequence[?] = {
    given org.goldenport.cncf.context.ExecutionContext = setup.executionContext
    action match {
      case "create" => writer.createEntity(setup.createPlan())
      case "direct" => writer.mutateEntityDirect(EntityDirectMutationPlan(setup.rootCollection, setup.rootEntry, "cncf_revision", Record.dataAuto("title" -> "After")))
      case "cas" => writer.compareAndSetEntity(EntityCompareAndSetMutationPlan(setup.rootCollection, setup.rootEntry, "cncf_revision", EntityRevision.INITIAL, Record.dataAuto("title" -> "After")))
      case "versioned" => writer.mutateVersionedEntity(setup.updatePlan())
      case "physical-delete" => writer.compareAndDeleteEntity(EntityCompareAndDeletePlan(setup.rootCollection, setup.rootEntry, "cncf_revision",
        EntityRevision.INITIAL, EntityDeleteRootMutation.Physical, sideEffects = Vector(EntityVersionedSideEffect.Delete(setup.sideCollection, setup.sideEntry))))
      case "logical-delete" => writer.compareAndDeleteEntity(EntityCompareAndDeletePlan(setup.rootCollection, setup.rootEntry, "cncf_revision",
        EntityRevision.INITIAL, EntityDeleteRootMutation.Logical(Record.dataAuto("aliveness" -> "dead"))))
      case "conditional" | "notmatched" => writer.conditionalTransition(setup.conditionalPlan())
    }
  }

  private def _snapshot(setup: MutationSession, reader: DataStore): Map[String, Option[Map[String, Any]]] = {
    val targets = Vector("root" -> (setup.rootCollection -> setup.rootEntry), "side" -> (setup.sideCollection -> setup.sideEntry),
      "successor" -> (setup.successorCollection -> setup.successorEntry),
      "fresh-root" -> (setup.rootCollection -> DataStore.StringEntryId("fresh")),
      "fresh-side" -> (setup.sideCollection -> DataStore.StringEntryId("freshbody")),
      "unrelated-root" -> (setup.rootCollection -> setup.oldEntry), "unrelated-side" -> (setup.sideCollection -> setup.oldEntry),
      "unrelated-successor" -> (setup.successorCollection -> setup.oldEntry))
    targets.map { case (name, (collection, entry)) =>
      name -> success(reader.load(collection, entry)(using setup.executionContext)).map(_.asMap)
    }.toMap
  }

  private def _require_committed(action: String, setup: MutationSession,
    before: Map[String, Option[Map[String, Any]]], after: Map[String, Option[Map[String, Any]]]): Unit = {
    Vector("unrelated-root", "unrelated-side", "unrelated-successor").foreach(name => after(name) shouldBe before(name))
    action match {
      case "create" =>
        after("root") shouldBe before("root")
        after("side") shouldBe before("side")
        after("fresh-root").get("title") shouldBe "Created"
        success(EntityRevision.createC(after("fresh-root").get("cncf_revision"))) shouldBe EntityRevision.INITIAL
        after("fresh-side").get("content") shouldBe "Fresh complete body"
        after("fresh-root").get("body_id") shouldBe "freshbody"
      case "notmatched" => after shouldBe before
      case "physical-delete" => after("root") shouldBe None; after("side") shouldBe None
      case "logical-delete" =>
        after("root").get("aliveness") shouldBe "dead"
        success(EntityRevision.createC(after("root").get("cncf_revision"))) shouldBe revision(2)
        after("side") shouldBe before("side")
      case "conditional" =>
        after("root").get("status") shouldBe "closed"
        after("root").get("successor_id") shouldBe setup.successorEntry.print
        success(EntityRevision.createC(after("root").get("cncf_revision"))) shouldBe revision(2)
        success(EntityRevision.createC(after("successor").get("cncf_revision"))) shouldBe EntityRevision.INITIAL
        after("side").get("content") shouldBe "Changed complete body"
      case _ =>
        after("root").get("title") shouldBe "After"
        success(EntityRevision.createC(after("root").get("cncf_revision"))) shouldBe revision(2)
        if (action == "versioned") after("side").get("content") shouldBe "Changed complete body"
        else after("side") shouldBe before("side")
    }
  }

  private def _with_backend(backend: String)(body: (SqlDialectDriver, DataSource, SqlDataStore) => Unit): Unit = backend match {
    case "postgresql" => with_postgresql { database => body(PostgreSqlDialectDriver, database.source(), database.open()) }
    case "sqlite" => withSqlite { database => body(SqliteDialectDriver, sqliteSource(database), database.open()) }
    case "mysql" => _mysql_container match {
      case Some(container) =>
        val database = new MysqlDatabase(container.getJdbcUrl, container.getUsername, container.getPassword)
        val datasource = new MysqlDataSource()
        datasource.setURL(container.getJdbcUrl)
        datasource.setUser(container.getUsername)
        datasource.setPassword(container.getPassword)
        try body(MySqlDialectDriver, datasource, database.open()) finally database.close()
      case None => throw new IllegalStateException("Actual required MySQL acceptance is unavailable")
    }
  }
}

private object SqlMutationAcknowledgementSafetySpec {
  final case class SqlNullSample(label: String, replace: Boolean, normalize: Boolean)

  given Shrink[SqlNullSample] = Shrink { sample =>
    val labels = if (sample.label.length > 1) Stream(sample.copy(label = sample.label.take(sample.label.length / 2))) else Stream.empty
    val forms = if (sample.replace) Stream(sample.copy(replace = false)) else Stream.empty
    val conventions = if (sample.normalize) Stream(sample.copy(normalize = false)) else Stream.empty
    labels ++ forms ++ conventions
  }

  final case class SqlComparisonSample(label: String, ownerwrite: Boolean, numbers: Vector[Long], replace: Boolean)

  given Shrink[SqlComparisonSample] = Shrink { sample =>
    val labels = if (sample.label.length > 1) Stream(sample.copy(label = sample.label.take(sample.label.length / 2))) else Stream.empty
    val halves = if (sample.numbers.length > 1) Stream(sample.copy(numbers = sample.numbers.take(sample.numbers.length / 2))) else Stream.empty
    val zeros = if (sample.numbers.exists(_ != 0L)) Stream(sample.copy(numbers = sample.numbers.map(_ => 0L))) else Stream.empty
    val permissions = if (sample.ownerwrite) Stream(sample.copy(ownerwrite = false)) else Stream.empty
    val forms = if (sample.replace) Stream(sample.copy(replace = false)) else Stream.empty
    labels ++ halves ++ zeros ++ permissions ++ forms
  }
}
