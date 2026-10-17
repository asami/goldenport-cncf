package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.EntityVersionedMutationCheckpoint
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision
import org.simplemodeling.model.statemachine.Aliveness

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
final class SqliteCanonicalObservedDeleteSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityObservedDeleteCanonicalFixture.*
  import EntityObservedDeleteCanonicalProviderFixture.*

  "File-backed SQLite canonical observed deletion" should {
    "COD-S1 commit physical root and canonical overflow together" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-S1, rules:R3,R4, phase:96") {
      "expose selected absence and untouched unrelated state through an independently reopened owning pool" in {
        Given("a real file-backed SQLite store with a canonical detached root at revision nine")
        withSqlite { database =>
          val first = database.open()
          val setup = fixture(token = "cod_s1", provider = first, initialRevision = 9L)
          setup.seed()
          val before = setup.snapshot()

          When("the standard store acknowledges deletion and another provider opens the same file")
          val result = setup.deleteC(revision(9L))
          val closed = first.closeC()
          val reader = database.open()
          val after = setup.snapshot(reader)

          Then("the receipt is prior nine and both selected records are durably absent")
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

    "COD-S2 retain the server-owned logical lifecycle after independent reopen" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-S2, rules:R2,R3,R4, phase:96") {
      "return prior revision, retain business and overflow, and exclude the tombstone from normal reads" in {
        Given("a real embedded SQLite root at revision sixteen, server audit and canonical overflow")
        withSqlite { database =>
          val first = database.open()
          val setup = fixture(token = "cod_s2", logical = true, representation = EntityRevisionRepresentation.Embedded,
            provider = first, initialRevision = 16L)
          setup.seed()
          val before = setup.snapshot()

          When("logical deletion commits and a fresh canonical assembly reads the independently reopened provider")
          val result = setup.deleteC(revision(16L))
          val reader = database.open()
          val reopened = fixture(token = "cod_s2", logical = true, representation = EntityRevisionRepresentation.Embedded, provider = reader)
          val row = success(reopened.rawC()).get
          val after = reopened.snapshot()
          val loaded = reopened.loadC()
          val visible = reopened.visibleC

          Then("storage is seventeen, audit is authoritative, retained rows are exact and normal reads hide the target")
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

    "COD-S3 preserve independent persisted state after a canonical intervening update" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-S3, rules:R2,R4,R5, phase:96") {
      "reject stale deletion for both assembled representations without touching root or overflow" in {
        Given("real SQLite canonical updates advancing each assembled root to revision two")
        withSqlite { database =>
          val first = database.open()
          val reader = database.open()
          val setups = Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).map { representation =>
            val setup = fixture(token = s"cod_s3_${representation.toString}", representation = representation, provider = first)
            setup.seed()
            setup.updateC() shouldBe a[Consequence.Success[?]]
            setup
          }
          val before = setups.map(_.snapshot(reader))

          When("canonical deletion compares each original observation through a real JDBC transaction")
          val results = setups.map(_.deleteC())
          val after = setups.map(_.snapshot(reader))

          Then("every stale failure preserves the complete independently observed records")
          results.foreach(result => reason(result) shouldBe Some("stale-entity-revision"))
          after shouldBe before
          first.closeC() shouldBe Consequence.unit
          reader.closeC() shouldBe Consequence.unit
        }
      }
    }

    "COD-S4 roll back actual JDBC writes before acknowledgement" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-S4, rules:R3,R4,R5, phase:96") {
      "preserve complete independent root and overflow records at all checkpoints and rejected commit" in {
        Given("real SQLite fault providers at root, side-effect, prepublication and commit boundaries for both policies")
        withSqlite { database =>
          val reader = database.open()
          val failures = Vector(
            Some(EntityVersionedMutationCheckpoint.RootPrepared) -> false,
            Some(EntityVersionedMutationCheckpoint.SideEffectsPrepared) -> false,
            Some(EntityVersionedMutationCheckpoint.BeforePublish) -> false,
            None -> true)
          val setups = for {
            ((checkpoint, failcommit), index) <- failures.zipWithIndex
            logical <- Vector(false, true)
          } yield {
            val store = database.open(checkpoint = checkpoint, failCommit = failcommit)
            val setup = fixture(token = s"cod_s4_${index}_$logical", logical = logical, provider = store)
            setup.seed(); setup
          }
          val before = setups.map(_.snapshot(reader))

          When("canonical deletion reaches each real native transaction fault boundary")
          val results = setups.map(_.deleteC())
          val after = setups.map(_.snapshot(reader))

          Then("no accepted receipt exists and the independent reader sees the exact prior complete records")
          results.foreach(_ shouldBe a[Consequence.Failure[?]])
          after shouldBe before
          setups.foreach(_.provider.asInstanceOf[org.goldenport.cncf.datastore.sql.SqlDataStore].closeC() shouldBe Consequence.unit)
          reader.closeC() shouldBe Consequence.unit
        }
      }
    }
  }
}
