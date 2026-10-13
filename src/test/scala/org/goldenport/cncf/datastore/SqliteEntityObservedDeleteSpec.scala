package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
final class SqliteEntityObservedDeleteSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityObservedDeleteFixture.*

  "SQLite observed Entity deletion" should {
    "commit native deletion with authoritative matched revisions" which {
      "OD-S1 expose physical root and effect absence through an independently reopened datasource" in {
        Given("a temp-file SQLite database with real root, overflow and unrelated rows")
        withSqlite { database =>
          val first = database.open()
          val ids = collections("s1")
          given ExecutionContext = context(first)
          seed(first, ids, rootRecord(5L)) shouldBe Consequence.unit
          val before = snapshot(first, ids)

          When("native observed deletion commits and a new datasource opens the same file")
          val result = summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(plan(ids, revision(5L)))
          first.closeC() shouldBe Consequence.unit
          val reopened = database.open()
          val after = snapshot(reopened, ids)(using context(reopened))

          Then("the matched revision is five and the new connection sees root and overflow removed together")
          result shouldBe Consequence.success(EntityCompareAndDeleteResult.Deleted(revision(5L)))
          after.root shouldBe Consequence.success(None)
          after.side shouldBe Consequence.success(None)
          after.unrelated shouldBe before.unrelated
          first.entityMutationProviderCapabilities.supportsAll(deleteFeatures) shouldBe true
        }
      }

      "OD-S2 preserve logical business and overflow state after independent reconstruction" in {
        Given("a real SQLite root at revision seven with an overflow reference and lifecycle columns")
        withSqlite { database =>
          val first = database.open()
          val ids = collections("s2")
          given ExecutionContext = context(first)
          seed(first, ids, rootRecord(7L, "unchanged-business")) shouldBe Consequence.unit
          val before = snapshot(first, ids)
          val request = plan(ids, revision(7L), EntityDeleteRootMutation.Logical(logicalChanges), false)

          When("the logical lifecycle patch commits and an independent store reads it")
          val result = summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(request)
          val reopened = database.open()
          val after = snapshot(reopened, ids)(using context(reopened))

          Then("storage advances once while the result retains the matched prior revision")
          result shouldBe Consequence.success(EntityCompareAndDeleteResult.Deleted(revision(7L)))
          val row = success(after.root).get
          success(EntityVersionedMutationSupport.revision(row, revisionField)) shouldBe revision(8L)
          row.getString("aliveness") shouldBe Some("dead")
          row.getString("deleted_at") shouldBe logicalChanges.getString("deleted_at")
          row.getString("updated_at") shouldBe logicalChanges.getString("updated_at")
          Vector("title", "business", "overflow_ref").foreach(key =>
            row.getAny(key) shouldBe success(before.root).get.getAny(key)
          )
          after.side shouldBe before.side
          after.unrelated shouldBe before.unrelated
        }
      }
    }

    "preserve native state after rejection and rollback" which {
      "OD-S3 preserve stale missing invalid exhausted and excluded roots with all effects" in {
        Given("independent physical and logical SQL roots for the complete persisted-revision and exclusion matrix")
        withSqlite { database =>
          val definitions = Vector(
            ("stale", Some(rootRecord(3L)), EntityRevision.INITIAL, Some(Taxonomy.Symptom.Conflict)),
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
            val store = database.open()
            val ids = collections(s"s3_${name}_$logical")
            val ctx = context(store)
            seed(store, ids, row.getOrElse(rootRecord()))(using ctx) shouldBe Consequence.unit
            if (row.isEmpty) store.delete(ids.root, rootEntry)(using ctx) shouldBe Consequence.unit
            val reader = database.open()
            val before = snapshot(reader, ids)(using context(reader))
            if (name == "dead") {
              success(before.root).map(_.getString("aliveness")) shouldBe Some(Some("dead"))
              success(before.root).map(_.fields.count(_.key == "aliveness")) shouldBe Some(1)
            } else if (name == "timestamp") {
              success(before.root).map(_.getString("deleted_at")) shouldBe Some(Some("2026-10-05T01:00:00Z"))
              success(before.root).map(_.fields.count(_.key == "deleted_at")) shouldBe Some(1)
            }
            val policy = if (logical) EntityDeleteRootMutation.Logical(logicalChanges) else EntityDeleteRootMutation.Physical
            (store, ids, ctx, reader, before, name, symptom, plan(ids, expected, policy, !logical))
          }

          When("each real SQL transaction attempts its observed deletion")
          val outcomes = scenarios.map { case (store, ids, ctx, reader, before, name, symptom, request) =>
            val result = store.compareAndDeleteEntity(request)(using ctx)
            (name, symptom, result, snapshot(reader, ids)(using context(reader)), before)
          }

          Then("stale is typed, other rejections remain structural and independent reads find no mutation")
          outcomes.foreach { case (name, symptom, result, after, before) =>
            withClue(name) {
              if (name == "stale")
                result shouldBe Consequence.success(EntityCompareAndDeleteResult.Stale(EntityRevision.INITIAL, revision(3L)))
              else {
                result shouldBe a[Consequence.Failure[?]]
                symptom.foreach(expected => _symptom(result) shouldBe Some(expected))
              }
              after shouldBe before
            }
          }
        }
      }

      "OD-S3 reject the entire shape matrix before real SQL mutation" in {
        Given("one real SQLite provider and the bounded nested plan admission violations")
        withSqlite { database =>
          val store = database.open()
          val ids = collections("s3_shape")
          given ExecutionContext = context(store)
          seed(store, ids) shouldBe Consequence.unit
          val before = snapshot(store, ids)
          store.clearStatements()
          val requests = invalidPlans(ids)

          When("the direct and routed SQL provider boundaries receive each invalid plan")
          val results = requests.map { case (name, request) =>
            (name, store.compareAndDeleteEntity(request), summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(request))
          }
          val statements = store.statements
          val reopened = database.open()
          val after = snapshot(reopened, ids)(using context(reopened))

          Then("admission performs no target UPDATE or DELETE and all independent records remain exact")
          results.foreach { case (name, direct, routed) => withClue(name) {
            direct shouldBe a[Consequence.Failure[?]]
            routed shouldBe a[Consequence.Failure[?]]
          } }
          statements shouldBe empty
          after shouldBe before
        }
      }

      "OD-S4 roll back physical root and overflow and logical lifecycle after every checkpoint or rejected commit" in {
        Given("actual JDBC faults after root mutation, side mutation, before publish and at commit admission")
        withSqlite { database =>
          val failures = EntityVersionedMutationCheckpoint.values.toVector.map(value => Some(value) -> false) :+ (None -> true)
          val scenarios = for {
            ((checkpoint, failcommit), index) <- failures.zipWithIndex
            logical <- Vector(false, true)
          } yield {
            val store = database.open(checkpoint = checkpoint, failCommit = failcommit)
            val ids = collections(s"s4_${index}_$logical")
            val ctx = context(store)
            seed(store, ids)(using ctx) shouldBe Consequence.unit
            val reader = database.open()
            val before = snapshot(reader, ids)(using context(reader))
            val policy = if (logical) EntityDeleteRootMutation.Logical(logicalChanges) else EntityDeleteRootMutation.Physical
            (store, ids, ctx, reader, before, plan(ids, rootMutation = policy, includeSide = !logical))
          }

          When("each real native transaction stages its root and selected effects and then fails")
          val outcomes = scenarios.map { case (store, ids, ctx, reader, before, request) =>
            (store.compareAndDeleteEntity(request)(using ctx), snapshot(reader, ids)(using context(reader)), before)
          }

          Then("independent JDBC reads observe complete rollback and no deleted result escapes")
          outcomes.foreach { case (result, after, before) =>
            result shouldBe a[Consequence.Failure[?]]
            after shouldBe before
          }
        }
      }
    }

    "enforce native concurrent and statement guards" which {
      "OD-S5 accept one winner across independent SQLite datasources and callers" in {
        Given("four independent datasources on one file per physical or logical observed deletion")
        Vector(false, true).foreach { logical => withSqlite { database =>
          val first = database.open()
          val ids = collections(s"s5_$logical")
          val ctx = context(first)
          seed(first, ids)(using ctx) shouldBe Consequence.unit
          val stores = Vector.fill(4)(database.open())
          val policy = if (logical) EntityDeleteRootMutation.Logical(logicalChanges) else EntityDeleteRootMutation.Physical
          val request = plan(ids, rootMutation = policy, includeSide = !logical).copy(exclusionGuards = Vector.empty)

          When("independent callers race under the original SQLite IMMEDIATE transactions")
          val evidence = race(stores, request)
          val reopened = database.open()
          val after = snapshot(reopened, ids)(using context(reopened))

          Then("exactly one matched result commits with complete root and overflow visibility")
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
        } }
      }

      "OD-S6 reject normalized managed aliases and execute an actually guarded DELETE" in {
        Given("a traced real normalized SQLite provider with admitted managed revision and lifecycle columns")
        withSqlite { database =>
          val store = database.open(config = SqlDataStore.Config(normalizeColumnNames = true))
          val ids = collections("s6_normalized")
          given ExecutionContext = context(store)
          seed(store, ids) shouldBe Consequence.unit
          val before = snapshot(store, ids)
          val aliases = Vector("cncfRevision", "Id").map(key =>
            plan(ids, rootMutation = EntityDeleteRootMutation.Logical(Record.dataAuto(key -> 40L)), includeSide = false)
          )
          store.clearStatements()

          When("logical alias patches are rejected, a stale physical request fails, then the current request executes")
          val rejected = aliases.map(store.compareAndDeleteEntity(_))
          val rejectedstatements = store.statements
          val stale = store.compareAndDeleteEntity(plan(ids, revision(2L)))
          val preserved = snapshot(store, ids)
          store.clearStatements()
          val result = store.compareAndDeleteEntity(plan(ids))
          val deletes = store.statements.filter(sql => sql.startsWith("DELETE") && sql.contains(s"\"${ids.root.collectionName}\""))
          val reopened = database.open(config = SqlDataStore.Config(normalizeColumnNames = true))
          val after = snapshot(reopened, ids)(using context(reopened))

          Then("aliases publish no writes and the executed root DELETE binds identity revision exclusion and validity predicates")
          all(rejected) shouldBe a[Consequence.Failure[?]]
          rejectedstatements shouldBe empty
          stale shouldBe Consequence.success(EntityCompareAndDeleteResult.Stale(revision(2L), EntityRevision.INITIAL))
          preserved shouldBe before
          result shouldBe Consequence.success(EntityCompareAndDeleteResult.Deleted(EntityRevision.INITIAL))
          deletes should have size 1
          deletes.head should include("\"id\" = ?")
          deletes.head should include("\"cncf_revision\" = ?")
          deletes.head should include("\"aliveness\" <> ?")
          deletes.head should include("\"deleted_at\" IS NULL")
          deletes.head should include("typeof(\"cncf_revision\") = 'integer'")
          deletes.head should include("\"cncf_revision\" >= ?")
          deletes.head should include("\"cncf_revision\" < ?")
          after.root shouldBe Consequence.success(None)
          after.side shouldBe Consequence.success(None)
          after.unrelated shouldBe before.unrelated
        }
      }

      "OD-S6 diagnose zero affected rows with an unchanged revision as provider failure" in {
        Given("a real SQLite BEFORE DELETE trigger that rejects the qualified mutation without changing revision")
        withSqlite { database =>
          val store = database.open()
          val ids = collections("s6_zero")
          given ExecutionContext = context(store)
          seed(store, ids) shouldBe Consequence.unit
          database.execute(s"CREATE TRIGGER observed_delete_ignore BEFORE DELETE ON \"${ids.root.collectionName}\" BEGIN SELECT RAISE(IGNORE); END")
          val before = snapshot(store, ids)
          store.clearStatements()

          When("the real JDBC guarded DELETE reports zero affected rows")
          val result = store.compareAndDeleteEntity(plan(ids))
          val statements = store.statements
          val reopened = database.open()
          val after = snapshot(reopened, ids)(using context(reopened))

          Then("same-transaction diagnosis returns provider failure without a fabricated stale or deleted outcome")
          _reasons(result) should contain("provider-failure")
          statements.count(sql => sql.startsWith("DELETE") && sql.contains(s"\"${ids.root.collectionName}\"")) shouldBe 1
          statements.count(sql => sql.startsWith("SELECT *") && sql.contains(s"\"${ids.root.collectionName}\"")) shouldBe 2
          statements.exists(sql => sql.startsWith("DELETE") && sql.contains(s"\"${ids.side.collectionName}\"")) shouldBe false
          after shouldBe before
        }
      }
    }
  }

  private def _symptom(result: Consequence[?]): Option[Taxonomy.Symptom] = result match {
    case Consequence.Failure(conclusion) => Some(conclusion.observation.taxonomy.symptom)
    case _ => None
  }

  private def _reasons(result: Consequence[?]): Set[String] = result match {
    case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
      case Descriptor.Facet.Reason(name) => name
    }.toSet
    case _ => Set.empty
  }
}
