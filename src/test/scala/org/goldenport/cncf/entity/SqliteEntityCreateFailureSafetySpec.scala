package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
final class SqliteEntityCreateFailureSafetySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityCreateFailureSafetyFixture.*
  import EntityObservedDeleteCanonicalFixture.{reason, success}

  "File-backed SQLite canonical create failure safety" should {
    "CFS-S1 reject a duplicate overflow candidate" which {
      "retain all durable selected and unrelated rows through a newly opened reader" in {
        Given("a task-owned SQLite database with two persisted overflow entities and a different large duplicate candidate")
        withSqlite { (database, writer) =>
          val setup = fixture("cfs_s1", provider = writer)
          setup.seed()
          val before = setup.snapshot(setup.base.id, database.open())
          val candidate = setup.input(BodyMode.Overflow, Some(setup.base.id))

          When("canonical create rejects the duplicate and a fresh provider opens the same file")
          val result = setup.createC(candidate)
          val after = setup.snapshot(setup.base.id, database.open())

          Then("the native duplicate taxonomy and all complete persisted maps are retained")
          result shouldBe a[Consequence.Failure[?]]
          taxonomy(result) shouldBe duplicateTaxonomy(setup.base.id)
          after shouldBe before
        }
      }
    }

    "CFS-S2 reject duplicate inline and omitted body modes" which {
      Vector(BodyMode.Inline, BodyMode.Absent, BodyMode.SetNull).foreach { mode =>
        s"retain every durable root and body separately for $mode" in {
          Given(s"an independently seeded file-backed SQLite database and a $mode duplicate candidate")
          withSqlite { (database, writer) =>
            val setup = fixture(s"cfs_s2_$mode", provider = writer)
            setup.seed()
            val before = setup.snapshot(setup.base.id, database.open())
            val candidate = setup.input(mode, Some(setup.base.id))

            When(s"canonical create executes the $mode attempt and a new reader observes durable rows")
            val result = setup.createC(candidate)
            val after = setup.snapshot(setup.base.id, database.open())

            Then("that attempt returns native duplicate and retains its own entire before snapshot")
            result shouldBe a[Consequence.Failure[?]]
            taxonomy(result) shouldBe duplicateTaxonomy(setup.base.id)
            after shouldBe before
          }
        }
      }
    }

    "CFS-S3 reject the fresh root before write" which {
      "expose no orphan overflow after successful side save through an independent durable read" in {
        Given("a seeded SQLite writer armed only at the fresh typed BeforeRootWrite checkpoint")
        withSqlite { (database, writer) =>
          val setup = fixture("cfs_s3", provider = writer)
          setup.seed()
          val before = setup.snapshot(setup.freshId, database.open())
          val candidate = setup.input(BodyMode.Overflow, Some(setup.freshId))
          writer.arm(setup.fault(FaultMode.RootCreate))

          When("canonical create writes overflow inside its transaction, reaches BeforeRootWrite rejection and a new reader opens")
          val result = setup.createC(candidate)
          val after = setup.snapshot(setup.freshId, database.open())

          Then("the provider failure leaves both fresh rows absent and every old durable row unchanged")
          result shouldBe a[Consequence.Failure[?]]
          reason(result) shouldBe Some("provider-failure")
          writer.sideSaves shouldBe 1
          writer.rootCreates shouldBe 1
          before.root shouldBe None
          before.overflow shouldBe None
          after.root shouldBe None
          after.overflow shouldBe None
          after shouldBe before
        }
      }
    }

    "CFS-S4 reject the fresh side save before write" which {
      "never reach root create or alter any durable row" in {
        Given("a real seeded SQLite provider armed only for the fresh canonical overflow save")
        withSqlite { (database, writer) =>
          val setup = fixture("cfs_s4", provider = writer)
          setup.seed()
          val before = setup.snapshot(setup.freshId, database.open())
          val candidate = setup.input(BodyMode.Overflow, Some(setup.freshId))
          writer.arm(setup.fault(FaultMode.SideSave))

          When("canonical create reaches the keyed side rejection and a newly opened provider reads the file")
          val result = setup.createC(candidate)
          val after = setup.snapshot(setup.freshId, database.open())

          Then("the provider marker is retained, root create is unreached, and all durable records are unchanged")
          result shouldBe a[Consequence.Failure[?]]
          reason(result) shouldBe Some("provider-failure")
          writer.sideSaves shouldBe 1
          writer.rootCreates shouldBe 0
          after.root shouldBe None
          after.overflow shouldBe None
          after shouldBe before
        }
      }
    }

    "CFS-S5 persist generated-ID overflow creation" which {
      Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).foreach { representation =>
        s"expose INITIAL one and exact hydrated body in a fresh $representation canonical reader without changing old rows" in {
          Given(s"a seeded file-backed SQLite $representation collection with no application-supplied identity")
          withSqlite { (database, writer) =>
            val token = s"cfs_s5_$representation"
            val setup = fixture(token, representation = representation, provider = writer)
            setup.seed()
            val before = setup.base.snapshot(database.open())
            val candidate = setup.input(BodyMode.Overflow, None)

            When("canonical create succeeds and a separately opened provider hydrates the generated entity")
            val result = setup.createC(candidate)
            val created = success(result)
            val reader = database.open()
            val reopened = fixture(token, representation = representation, provider = reader)
            val after = reopened.snapshot(created.id)
            val row = success(reopened.base.rawC(created.id)).get
            val loaded = reopened.hydratedC(created.id)

            Then("the admitted collection and managed INITIAL persist with the exact candidate content and original rows")
            result shouldBe a[Consequence.Success[?]]
            created.id.collection shouldBe setup.base.collectionId
            created.id should not be setup.base.id
            created.id should not be setup.base.unrelatedId
            created.id.timestamp.nonEmpty shouldBe true
            created.id.entropy.nonEmpty shouldBe true
            created.record.nonEmpty shouldBe true
            success(EntityRevision.createC(created.record.get.getAny(setup.base.revisionField).get)) shouldBe EntityRevision.INITIAL
            success(EntityRevision.createC(row.getAny(setup.base.revisionField).get)) shouldBe EntityRevision.INITIAL
            Vector("title", "content_storage", "content_ref", "content_digest", "content_charset").foreach { key =>
              row.getString(key) shouldBe created.record.get.getString(key)
            }
            row.getString("content_storage") shouldBe Some("overflow")
            success(loaded).map(_.identity) shouldBe Some(created.id)
            success(loaded).flatMap(_.record.getString("content")) shouldBe candidate.record.getString("content")
            after.overflow.flatMap(_.get("content")) shouldBe candidate.record.getAny("content")
            after.preexisting shouldBe before
          }
        }
      }
    }
  }
}
