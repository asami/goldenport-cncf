package org.goldenport.cncf.datastore

import com.mysql.cj.jdbc.MysqlDataSource
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.{MySqlDialectDriver, SqlDataStore}
import org.goldenport.observation.Taxonomy
import org.goldenport.record.Record
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName
import scala.util.control.NonFatal

/*
 * @since   Oct.  6, 2026
 * @version Oct. 31, 2026
 */
@org.scalatest.DoNotDiscover
final class MysqlEntityObservedDeleteAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen with BeforeAndAfterAll {
  import EntityObservedDeleteFixture.*

  private val _live_enabled = sys.env.get("CNCF_LIVE_MYSQL_TEST").contains("true")
  private var _container: Option[LiveMysqlContainer] = None

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (_live_enabled) {
      val container = new LiveMysqlContainer()
      try {
        container.start()
        _container = Some(container)
      } catch {
        case NonFatal(error) =>
          container.stop()
          throw error
      }
    }
  }

  override protected def afterAll(): Unit =
    try {
      _container.foreach(_.stop())
      _container = None
    } finally super.afterAll()

  "Live MySQL observed Entity deletion" should {
    "prove committed provider-native outcomes" which {
      "OD-M1 expose physical root and overflow removal through independently reopened JDBC stores" in {
        Given("one live MySQL 8.4 database with independent provider instances and a root at revision nine")
        _with_mysql { database =>
          val first = database.open()
          val ids = collections("mysql_m1")
          given ExecutionContext = context(first)
          seed(first, ids, rootRecord(9L)) shouldBe Consequence.unit
          val before = snapshot(first, ids)

          When("native observed deletion commits and another JDBC store reads the same database")
          val result = summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(plan(ids, revision(9L)))
          first.closeC() shouldBe Consequence.unit
          val reopened = database.open()
          val after = snapshot(reopened, ids)(using context(reopened))

          Then("the matched prior revision is authoritative and root and selected overflow disappear together")
          result shouldBe Consequence.success(EntityCompareAndDeleteResult.Deleted(revision(9L)))
          after.root shouldBe Consequence.success(None)
          after.side shouldBe Consequence.success(None)
          after.unrelated shouldBe before.unrelated
          reopened.entityMutationProviderCapabilities.supportsAll(deleteFeatures) shouldBe true
        }
      }

      "OD-M2 retain logical lifecycle and overflow with one increment and a prior matched result" in {
        Given("one real MySQL root at revision sixteen with server lifecycle columns and untouched business data")
        _with_mysql { database =>
          val first = database.open()
          val ids = collections("mysql_m2")
          given ExecutionContext = context(first)
          seed(first, ids, rootRecord(16L, "retained-business")) shouldBe Consequence.unit
          val before = snapshot(first, ids)
          val request = plan(ids, revision(16L), EntityDeleteRootMutation.Logical(logicalChanges), false)

          When("logical deletion commits and an independently constructed provider reads the stored rows")
          val result = summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(request)
          val reopened = database.open()
          val after = snapshot(reopened, ids)(using context(reopened))

          Then("the result is sixteen, storage is seventeen and business and overflow values remain exact")
          result shouldBe Consequence.success(EntityCompareAndDeleteResult.Deleted(revision(16L)))
          val row = success(after.root).get
          success(EntityVersionedMutationSupport.revision(row, revisionField)) shouldBe revision(17L)
          logicalChanges.fields.foreach(field => row.getAny(field.key) shouldBe logicalChanges.getAny(field.key))
          Vector("title", "business", "overflow_ref").foreach(key =>
            row.getAny(key) shouldBe success(before.root).get.getAny(key)
          )
          after.side shouldBe before.side
          after.unrelated shouldBe before.unrelated
        }
      }
    }

    "prove real failure preservation and native rollback" which {
      "OD-M3 preserve stale missing invalid exhausted excluded and malformed deletion attempts" in {
        Given("live MySQL roots for both deletion policies and the complete persisted-state and admission matrix")
        _with_mysql { database =>
          val store = database.open()
          val reader = database.open()
          val ctx = context(store)
          val readercontext = context(reader)
          val definitions = Vector(
            ("stale", Some(rootRecord(4L)), EntityRevision.INITIAL, None),
            ("absent", None, EntityRevision.INITIAL, Some(Taxonomy.Symptom.NotFound)),
            ("missing", Some(Record(rootRecord().fields.filterNot(_.key == revisionField))), EntityRevision.INITIAL, None),
            ("zero", Some(rootRecord(0L)), EntityRevision.INITIAL, None),
            ("fractional", Some(rootRecord(1.5d)), EntityRevision.INITIAL, None),
            ("invalid", Some(rootRecord("invalid")), EntityRevision.INITIAL, None),
            ("exhausted", Some(rootRecord(Long.MaxValue)), revision(Long.MaxValue), None),
            ("dead", Some(rootRecord().upsertSingle("aliveness", "dead")), EntityRevision.INITIAL, Some(Taxonomy.Symptom.NotFound)),
            ("timestamp", Some(rootRecord().upsertSingle("deleted_at", "2026-10-05T01:00:00Z")), EntityRevision.INITIAL, Some(Taxonomy.Symptom.NotFound))
          )
          val scenarios = for {
            (name, row, expected, symptom) <- definitions
            logical <- Vector(false, true)
          } yield {
            val ids = collections(s"mysql_m3_${name}_$logical")
            seed(store, ids, row.getOrElse(rootRecord()))(using ctx) shouldBe Consequence.unit
            if (row.isEmpty) store.delete(ids.root, rootEntry)(using ctx) shouldBe Consequence.unit
            val before = snapshot(reader, ids)(using readercontext)
            if (name == "dead") {
              success(before.root).map(_.getString("aliveness")) shouldBe Some(Some("dead"))
              success(before.root).map(_.fields.count(_.key == "aliveness")) shouldBe Some(1)
            } else if (name == "timestamp") {
              success(before.root).map(_.getString("deleted_at")) shouldBe Some(Some("2026-10-05T01:00:00Z"))
              success(before.root).map(_.fields.count(_.key == "deleted_at")) shouldBe Some(1)
            }
            val policy = if (logical) EntityDeleteRootMutation.Logical(logicalChanges) else EntityDeleteRootMutation.Physical
            (name, symptom, ids, before, plan(ids, expected, policy, !logical))
          }
          val shapeids = collections("mysql_m3_shapes")
          seed(store, shapeids)(using ctx) shouldBe Consequence.unit
          val shapebefore = snapshot(reader, shapeids)(using readercontext)
          val malformed = invalidPlans(shapeids)

          When("real provider transactions compare persisted state and reject malformed plans before mutation")
          val outcomes = scenarios.map { case (name, symptom, ids, before, request) =>
            (name, symptom, store.compareAndDeleteEntity(request)(using ctx), snapshot(reader, ids)(using readercontext), before)
          }
          val shaperesults = malformed.map { case (name, request) =>
            name -> ctx.dataStoreSpace.compareAndDeleteEntity(request)(using ctx)
          }
          val shapeafter = snapshot(reader, shapeids)(using readercontext)

          Then("stale carries actual revision and every unsuccessful attempt preserves all independent records")
          outcomes.foreach { case (name, symptom, result, after, before) => withClue(name) {
            if (name == "stale")
              result shouldBe Consequence.success(EntityCompareAndDeleteResult.Stale(EntityRevision.INITIAL, revision(4L)))
            else {
              result shouldBe a[Consequence.Failure[?]]
              symptom.foreach(expected => _symptom(result) shouldBe Some(expected))
            }
            after shouldBe before
          } }
          shaperesults.foreach { case (name, result) => withClue(name) { result shouldBe a[Consequence.Failure[?]] } }
          shapeafter shouldBe shapebefore
        }
      }

      "OD-M4 roll back actual root deletion and logical lifecycle when RootPrepared fails" in {
        Given("live provider injection immediately after each qualified physical or logical root mutation")
        _with_mysql { database =>
          val reader = database.open()
          val scenarios = Vector(false, true).map { logical =>
            val store = database.open(checkpoint = Some(EntityVersionedMutationCheckpoint.RootPrepared))
            val ids = collections(s"mysql_m4_$logical")
            val ctx = context(store)
            seed(store, ids)(using ctx) shouldBe Consequence.unit
            val before = snapshot(reader, ids)(using context(reader))
            val policy = if (logical) EntityDeleteRootMutation.Logical(logicalChanges) else EntityDeleteRootMutation.Physical
            (store, ids, ctx, before, plan(ids, rootMutation = policy, includeSide = !logical))
          }

          When("each native MySQL transaction fails after its actual root write")
          val outcomes = scenarios.map { case (store, ids, ctx, before, request) =>
            (store.compareAndDeleteEntity(request)(using ctx), snapshot(reader, ids)(using context(reader)), before)
          }

          Then("independent connections find the prior complete root and overflow and no accepted deletion result")
          outcomes.foreach { case (result, after, before) =>
            result shouldBe a[Consequence.Failure[?]]
            after shouldBe before
          }
        }
      }

      "OD-M5 roll back root and overflow after side mutation before publication and rejected commit" in {
        Given("live failures after side effects, before publication and at the inherited native commit admission")
        _with_mysql { database =>
          val reader = database.open()
          val failures = Vector(
            (Some(EntityVersionedMutationCheckpoint.SideEffectsPrepared), false),
            (Some(EntityVersionedMutationCheckpoint.BeforePublish), false),
            (None, true)
          )
          val scenarios = for {
            ((checkpoint, failcommit), index) <- failures.zipWithIndex
            logical <- Vector(false, true)
          } yield {
            val store = database.open(checkpoint = checkpoint, failCommit = failcommit)
            val ids = collections(s"mysql_m5_${index}_$logical")
            val ctx = context(store)
            seed(store, ids)(using ctx) shouldBe Consequence.unit
            val before = snapshot(reader, ids)(using context(reader))
            val policy = if (logical) EntityDeleteRootMutation.Logical(logicalChanges) else EntityDeleteRootMutation.Physical
            (store, ids, ctx, before, plan(ids, rootMutation = policy, includeSide = !logical))
          }

          When("real transaction failures follow staged root and all selected deletion effects")
          val outcomes = scenarios.map { case (store, ids, ctx, before, request) =>
            (store.compareAndDeleteEntity(request)(using ctx), snapshot(reader, ids)(using context(reader)), before)
          }

          Then("every rollback preserves the complete prior rows through independent JDBC reads")
          outcomes.foreach { case (result, after, before) =>
            result shouldBe a[Consequence.Failure[?]]
            after shouldBe before
          }
        }
      }
    }

    "prove independent shared-database concurrency" which {
      "OD-M6 accept one observed caller across independent MySQL pools and connections" in {
        Given("four independent callers of each physical or logical root at the same observed revision")
        _with_mysql { database =>
          Vector(false, true).foreach { logical =>
            val first = database.open()
            val ids = collections(s"mysql_m6_$logical")
            seed(first, ids)(using context(first)) shouldBe Consequence.unit
            val stores = Vector.fill(4)(database.open())
            val policy = if (logical) EntityDeleteRootMutation.Logical(logicalChanges) else EntityDeleteRootMutation.Physical
            val request = plan(ids, rootMutation = policy, includeSide = !logical).copy(exclusionGuards = Vector.empty)

            When("bounded callers race inside original provider-native transactions without application serialization")
            val evidence = race(stores, request)
            val reopened = database.open()
            val after = snapshot(reopened, ids)(using context(reopened))

            Then("one authoritative matched result commits and every loser sees stale revision or physical absence")
            evidence.ready shouldBe true
            evidence.terminated shouldBe true
            evidence.results should have size 4
            evidence.results.count(_.toOption.contains(EntityCompareAndDeleteResult.Deleted(EntityRevision.INITIAL))) shouldBe 1
            if (logical) {
              evidence.results.count(_.toOption.contains(EntityCompareAndDeleteResult.Stale(EntityRevision.INITIAL, revision(2L)))) shouldBe 3
              success(EntityVersionedMutationSupport.revision(success(after.root).get, revisionField)) shouldBe revision(2L)
              success(after.side).isDefined shouldBe true
            } else {
              evidence.results.count(_symptom(_) == Some(Taxonomy.Symptom.NotFound)) shouldBe 3
              after.root shouldBe Consequence.success(None)
              after.side shouldBe Consequence.success(None)
            }
          }
        }
      }
    }
  }

  private def _with_mysql[A](body: LiveDatabase => A): A = _container match {
    case Some(container) =>
      val database = new LiveDatabase(container.getJdbcUrl, container.getUsername, container.getPassword)
      try body(database)
      finally database.close()
    case None =>
      cancel("Set CNCF_LIVE_MYSQL_TEST=true to execute required live MySQL observed-deletion acceptance; cancellation is not acceptance.")
  }

  private def _symptom(result: Consequence[?]): Option[Taxonomy.Symptom] = result match {
    case Consequence.Failure(conclusion) => Some(conclusion.observation.taxonomy.symptom)
    case _ => None
  }

  private final class LiveMysqlContainer extends MySQLContainer(DockerImageName.parse("mysql:8.4")) {
    withDatabaseName("cncf_phase96_observed_delete")
    withUsername("cncf")
    withPassword("cncf-observed-delete-fixture")
  }

  private final class LiveDatabase(jdbcurl: String, username: String, password: String) {
    private var _stores = Vector.empty[SqlDataStore]

    def open(
      checkpoint: Option[EntityVersionedMutationCheckpoint] = None,
      failCommit: Boolean = false
    ): SqlDataStore = {
      val store = if (checkpoint.isDefined || failCommit) {
        val source = new MysqlDataSource()
        source.setURL(jdbcurl)
        source.setUser(username)
        source.setPassword(password)
        new SqlStore(MySqlDialectDriver, source, checkpoint = checkpoint, failCommit = failCommit)
      } else SqlDataStore.jdbc(
        jdbcUrl = jdbcurl, dialect = SqlDataStore.Mysql,
        username = Some(username), password = Some(password), driverClassName = Some("com.mysql.cj.jdbc.Driver")
      )
      _stores = _stores :+ store
      store
    }

    def close(): Unit = { _stores.foreach(store => success(store.closeC())) }
  }
}
