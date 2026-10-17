package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.EntityVersionedMutationCheckpoint
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision
import org.simplemodeling.model.statemachine.Aliveness
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName
import scala.util.control.NonFatal

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
final class MysqlCanonicalObservedDeleteAcceptanceSpec
    extends AnyWordSpec with Matchers with GivenWhenThen with BeforeAndAfterAll {
  import EntityObservedDeleteCanonicalFixture.*
  import EntityObservedDeleteCanonicalProviderFixture.*

  private var _container: Option[LiveMysqlContainer] = None

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (sys.env.get("CNCF_LIVE_MYSQL_TEST").contains("true")) {
      val container = new LiveMysqlContainer()
      try {
        container.start()
        _container = Some(container)
      } catch {
        case NonFatal(error) => container.stop(); throw error
      }
    }
  }

  override protected def afterAll(): Unit = {
    try {
      _container.foreach(_.stop())
      _container = None
    } finally super.afterAll()
  }

  "Live MySQL canonical observed Entity deletion" should {
    "COD-M1 commit physical deletion through actual provider-native JDBC" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-M1, rules:R3,R4, phase:96") {
      "expose root and canonical overflow absence with exact unrelated preservation to an independent pool" in {
        Given("a positively executing MySQL 8.4 Testcontainer and a canonical detached root at revision nine")
        _with_mysql { database =>
          val first = database.open()
          val setup = fixture(token = "cod_m1", provider = first, initialRevision = 9L)
          setup.seed()
          val before = setup.snapshot()

          When("canonical deletion commits before an independent owning pool reads the database")
          val result = setup.deleteC(revision(9L))
          val closed = first.closeC()
          val reader = database.open()
          val after = setup.snapshot(reader)

          Then("the actual matched prior revision is acknowledged and exactly the selected pair is absent")
          result shouldBe Consequence.success(EntityDeletionResult(setup.id, revision(9L)))
          closed shouldBe Consequence.unit
          after.root shouldBe None
          after.overflow shouldBe None
          after.unrelatedRoot shouldBe before.unrelatedRoot
          after.unrelatedOverflow shouldBe before.unrelatedOverflow
          reader.closeC() shouldBe Consequence.unit
        }
      }
    }

    "COD-M2 preserve canonical logical lifecycle, business and overflow" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-M2, rules:R2,R3,R4, phase:96") {
      "retain prior receipt and stored next while independently assembled normal reads hide the tombstone" in {
        Given("an actual embedded MySQL root at revision sixteen with fixed server audit identity and clock")
        _with_mysql { database =>
          val first = database.open()
          val setup = fixture(token = "cod_m2", logical = true, representation = EntityRevisionRepresentation.Embedded,
            provider = first, initialRevision = 16L)
          setup.seed()
          val before = setup.snapshot()

          When("the server-owned logical patch commits and an independent provider and canonical assembly read it")
          val result = setup.deleteC(revision(16L))
          val reader = database.open()
          val reopened = fixture(token = "cod_m2", logical = true, representation = EntityRevisionRepresentation.Embedded, provider = reader)
          val row = success(reopened.rawC()).get
          val after = reopened.snapshot()
          val loaded = reopened.loadC()
          val visible = reopened.visibleC

          Then("audit belongs to the server, storage is seventeen, the receipt is sixteen and normal reads exclude it")
          result shouldBe Consequence.success(EntityDeletionResult(setup.id, revision(16L)))
          success(EntityRevision.createC(row.getAny("revision").get)) shouldBe revision(17L)
          row.getAs[Aliveness]("aliveness") shouldBe Some(Aliveness.Dead)
          Vector("updated_at", "deleted_at").foreach(key => row.getString(key) shouldBe Some(instant.toString))
          Vector("updated_by", "deleted_by").foreach(key => row.getString(key) shouldBe Some("owner"))
          row.getString("trace_id") shouldBe Some(setup.context.observability.traceId.value)
          row.getString("correlation_id") shouldBe setup.context.observability.correlationId.map(_.value)
          Vector("title", "business", "content_ref", "owner_id", "permission").foreach(key => row.getAny(key) shouldBe before.root.get.get(key))
          after.overflow shouldBe before.overflow
          after.unrelatedRoot shouldBe before.unrelatedRoot
          after.unrelatedOverflow shouldBe before.unrelatedOverflow
          loaded shouldBe Consequence.success(None)
          success(visible).map(_.id) should not contain setup.id
          first.closeC() shouldBe Consequence.unit
          reader.closeC() shouldBe Consequence.unit
        }
      }
    }

    "COD-M3 preserve actual stale outcomes and rollback every injected native failure" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-M3, rules:R2,R3,R4,R5, phase:96") {
      "observe complete root and overflow preservation through independent MySQL providers" in {
        Given("actual canonical intervening writes and JDBC checkpoint/commit faults across assembled policies")
        _with_mysql { database =>
          val first = database.open()
          val reader = database.open()
          val stale = for {
            representation <- Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
            logical <- Vector(false, true)
          } yield {
            val setup = fixture(token = s"cod_m3_stale_${representation.toString}_$logical", logical = logical,
              representation = representation, provider = first)
            setup.seed()
            setup.updateC() shouldBe a[Consequence.Success[?]]
            setup
          }
          val stalebefore = stale.map(_.snapshot(reader))
          val failures = Vector(
            Some(EntityVersionedMutationCheckpoint.RootPrepared) -> false,
            Some(EntityVersionedMutationCheckpoint.SideEffectsPrepared) -> false,
            Some(EntityVersionedMutationCheckpoint.BeforePublish) -> false,
            None -> true)
          val rollback = for {
            ((checkpoint, failcommit), index) <- failures.zipWithIndex
            logical <- Vector(false, true)
          } yield {
            val store = database.open(checkpoint = checkpoint, failCommit = failcommit)
            val setup = fixture(token = s"cod_m3_rollback_${index}_$logical", logical = logical, provider = store)
            setup.seed(); setup
          }
          val rollbackbefore = rollback.map(_.snapshot(reader))

          When("canonical deletion makes native stale comparisons or fails after real staged JDBC writes")
          val staleresults = stale.map(_.deleteC())
          val rollbackresults = rollback.map(_.deleteC())
          val staleafter = stale.map(_.snapshot(reader))
          val rollbackafter = rollback.map(_.snapshot(reader))

          Then("strict stale and transaction failure outcomes publish no acknowledged deletion and preserve every record")
          staleresults.foreach(result => reason(result) shouldBe Some("stale-entity-revision"))
          rollbackresults.foreach(_ shouldBe a[Consequence.Failure[?]])
          staleafter shouldBe stalebefore
          rollbackafter shouldBe rollbackbefore
          first.closeC() shouldBe Consequence.unit
          reader.closeC() shouldBe Consequence.unit
          rollback.foreach(_.provider.asInstanceOf[org.goldenport.cncf.datastore.sql.SqlDataStore].closeC() shouldBe Consequence.unit)
        }
      }
    }
  }

  private def _with_mysql[A](body: MysqlDatabase => A): A = _container match {
    case Some(container) =>
      val database = new MysqlDatabase(container.getJdbcUrl, container.getUsername, container.getPassword)
      try body(database)
      finally database.close()
    case None =>
      fail("Required live MySQL canonical acceptance needs CNCF_LIVE_MYSQL_TEST=true; cancellation or substitution is not acceptance.")
  }

  private final class LiveMysqlContainer extends MySQLContainer(DockerImageName.parse("mysql:8.4")) {
    withDatabaseName("cncf_phase96_canonical_delete")
    withUsername("cncf")
    withPassword("cncf-canonical-delete-fixture")
  }
}
