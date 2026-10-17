package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.entity.{ContentBodyStoragePolicy, EntityCreateFailureSafetyFixture, EntityRevisionRepresentation}
import org.goldenport.cncf.entity.EntityObservedDeleteCanonicalFixture.{reason, success}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
final class SqliteEntityCreateProviderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityCreateFailureSafetyFixture.*
  import EntityCreateProviderFixture.*

  "CA7/CA8/CA9/CA10 independent SQLite create observations" should {
    "distinguish known rollback from an actual committed pair without acknowledgement" which {
      for {
        representation <- Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
        mode <- Vector(FaultMode.BeforePublish, FaultMode.ThrowPublish, FaultMode.CommitReject, FaultMode.CommitAckLoss,
          FaultMode.CommitAckThrow, FaultMode.CommitAckNull)
      } {
        s"CA-PUBLISH/CA-COMMIT observe $mode for $representation through a new reader" in {
          Given("a real file-backed provider with complete preexisting rows and an exact create fault")
          withSqlite { (database, writer) =>
            val setup = fixture(s"cncf_sqlite_ack_${representation}_$mode", representation = representation, provider = writer)
            setup.seed()
            val before = setup.snapshot(setup.freshId, database.open())
            val candidate = setup.input(BodyMode.Overflow, Some(setup.freshId))
            writer.arm(setup.fault(mode))
            When("the transaction writes side then root and reaches the selected publication or commit condition")
            val result = setup.createC(candidate)
            val reader = database.open()
            val after = setup.snapshot(setup.freshId, reader)
            Then("one attempt returns its precise failure category and independent storage shows the complete actual outcome")
            result shouldBe a[Consequence.Failure[?]]
            writer.sideSaves shouldBe 1
            writer.rootCreates shouldBe 1
            writer.commitAttempts shouldBe (if (mode == FaultMode.BeforePublish || mode == FaultMode.ThrowPublish) 0 else 1)
            if (Vector(FaultMode.CommitAckLoss, FaultMode.CommitAckThrow, FaultMode.CommitAckNull).contains(mode)) {
              reason(result) shouldBe Some("transaction-indeterminate")
              val root = success(reader.load(setup.base.dataCollection, DataStore.EntryId(setup.freshId))(using setup.base.context)).get
              success(EntityRevision.createC(root.getAny(setup.base.revisionField).get)) shouldBe EntityRevision.INITIAL
              root.getString("title") shouldBe Some("Candidate")
              val prepared = success(ContentBodyStoragePolicy.planForVersionedSave(setup.freshId, candidate.record))
              storageScalar(root.getAny("id").get) shouldBe DataStore.EntryId(setup.freshId).print
              prepared.record.fields.filter(_.key.startsWith("content")).foreach(field =>
                storageScalar(root.getAny(field.key).get) shouldBe storageScalar(field.value.single))
              after.overflow shouldBe Some(prepared.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap)
              after.root shouldBe Some(root.asMap)
              after.preexisting shouldBe before.preexisting
            } else {
              reason(result) shouldBe Some(if (mode == FaultMode.CommitReject) "transaction-failure" else "provider-failure")
              after shouldBe before
            }
          }
        }
      }
    }

    "retain first-use data absence after schema preparation" which {
      "CA8/CFS-S3 roll back the newly written body when the fresh root is rejected" in {
        Given("a new task-owned SQLite file with no root or body table and an armed BeforeRootWrite fault")
        withSqlite { (database, writer) =>
          val setup = fixture("cncf_sqlite_first_use", provider = writer)
          val before = setup.snapshot(setup.freshId, database.open())
          writer.arm(setup.fault(FaultMode.RootCreate))
          When("schema preparation completes, the transaction writes the side and rejects the fresh root")
          val result = setup.createC(setup.input(BodyMode.Overflow, Some(setup.freshId)))
          Then("a separately opened provider finds no partial rows in any selected or unrelated position")
          reason(result) shouldBe Some("provider-failure")
          writer.sideSaves shouldBe 1
          writer.rootCreates shouldBe 1
          setup.snapshot(setup.freshId, database.open()) shouldBe before
        }
      }
    }

    "use native physical table and id equality" which {
      Vector("typed-table-alias", "ascii-table-case", "nocase-id", "repeated-nocase-side").foreach { mode =>
        s"CA-PORT/CA4 reject $mode before any row effect" in {
          Given("existing real SQLite tables whose text primary keys declare their native collation")
          withSqlite { (database, writer) =>
            val setup = new DirectSession(s"cncf_sqlite_alias_${mode.replace('-', '_')}", writer)
            if (mode == "nocase-id" || mode == "repeated-nocase-side") {
              val selected = if (mode == "nocase-id") setup.rootCollection else setup.sideCollection
              sqliteStatement(database, s"CREATE TABLE \"${selected.collectionName}\" (\"id\" TEXT COLLATE NOCASE PRIMARY KEY)")
            }
            setup.seed()
            val before = setup.snapshot(database.open())
            val original = setup.plan()
            val alias = DataStore.CollectionId.EntityStore(EntityCollectionId("another", "owner",
              if (mode == "ascii-table-case") setup.rootCollection.collectionName.toUpperCase(java.util.Locale.ROOT) else setup.rootCollection.collectionName))
            val plan = mode match {
              case "typed-table-alias" | "ascii-table-case" => original.copy(sideEffects = Vector(EntityVersionedSideEffect.Delete(alias, setup.rootEntry)))
              case "nocase-id" => original.copy(sideEffects = Vector(EntityVersionedSideEffect.Delete(alias, DataStore.StringEntryId("FRESH"))))
              case _ => original.copy(sideEffects = Vector(
                EntityVersionedSideEffect.Delete(setup.sideCollection, DataStore.StringEntryId("FreshBody")),
                EntityVersionedSideEffect.Delete(setup.sideCollection, DataStore.StringEntryId("FRESHBODY"))))
            }
            When("the actual provider resolves native table metadata and compares ids using their primary-key collation")
            val result = setup.createC(plan)
            Then("the aliased plan rejects and full independent root, side and older row maps remain")
            result shouldBe a[Consequence.Failure[?]]
            setup.snapshot(database.open()) shouldBe before
          }
        }
      }

      "CA4/CA9 admit distinct case-sensitive BINARY ids in the same physical table" in {
        Given("an actual SQLite BINARY primary key and different entry bytes behind differently typed table identifiers")
        withSqlite { (database, writer) =>
          val setup = new DirectSession("cncf_sqlite_binary", writer)
          setup.seed()
          val before = setup.snapshot(database.open())
          val sideentry = DataStore.StringEntryId("FRESH")
          val alias = DataStore.CollectionId.EntityStore(EntityCollectionId("other", "owner", setup.rootCollection.collectionName.toUpperCase(java.util.Locale.ROOT)))
          val side = Record.dataAuto("id" -> sideentry.print, "title" -> "Distinct case-sensitive side")
          val plan = setup.plan().copy(sideEffects = Vector(EntityVersionedSideEffect.Save(alias, sideentry, side)))
          When("the actual provider stages the side and uniquely inserts the root at different binary ids")
          val result = setup.createC(plan)
          val reader = database.open()
          val after = setup.snapshot(reader)
          val storedside = success(reader.load(setup.rootCollection, sideentry)(using setup.context)).get
          Then("both distinct ids persist, readback is authoritative and complete older rows stay unchanged")
          success(result).record.asMap shouldBe after.root.get
          storedside.getString("id") shouldBe Some("FRESH")
          storedside.getString("title") shouldBe side.getString("title")
          after.oldroot shouldBe before.oldroot
          after.oldside shouldBe before.oldside
        }
      }
    }

    "admit columns before schema or data effects" which {
      Vector("identity", "normalized-duplicate", "native-case-duplicate").foreach { mode =>
        s"CA-PORT reject the $mode collision before first-use writes" in {
          Given("a fresh SQLite provider configured for actual SQL column normalization")
          withSqlite { (database, _) =>
            val writer = sqliteFaultStore(database, normalizeColumns = true)
            try {
              val setup = new DirectSession(s"cncf_sqlite_columns_${mode.replace('-', '_')}", writer)
              val before = setup.snapshot(database.open())
              val additions = mode match {
                case "identity" => Record.dataAuto("Id" -> "other")
                case "normalized-duplicate" => Record.dataAuto("displayName" -> "a", "display_name" -> "b")
                case _ => Record.dataAuto("Title" -> "other")
              }
              val plan = setup.plan().copy(record = setup.plan().record ++ additions)
              When("strict create receives fields colliding with native normalized column identities")
              val result = setup.createC(plan)
              Then("the structured failure retains complete absence through a new reader")
              result shouldBe a[Consequence.Failure[?]]
              setup.snapshot(database.open()) shouldBe before
            } finally success(writer.closeC())
          }
        }
      }

      "CA9 return configured physical revision aliases as actual authoritative readback" in {
        Given("independently owned real SQLite writer and reader providers with the same canonical camel-to-snake column normalization")
        withSqlite { (database, _) =>
          val writer = sqliteFaultStore(database, normalizeColumns = true)
          try {
            val reader = sqliteFaultStore(database, normalizeColumns = true)
            try {
              val setup = new DirectSession("cncf_sqlite_normalized_success", writer)
              setup.seed()
              val before = setup.snapshot(reader)
              When("strict create commits the canonical record and reads back its configured column representation")
              val result = setup.createC(setup.plan())
              val after = setup.snapshot(reader)
              Then("the exact stored map is returned, its physical revision is one and unrelated records are retained")
              success(result).record.asMap shouldBe after.root.get
              success(EntityRevision.createC(success(result).record.getAny("cncf_revision").get)) shouldBe EntityRevision.INITIAL
              success(result).record.getString("bodyId") shouldBe Some(setup.sideEntry.print)
              success(result).record.getAny("body_id") shouldBe None
              success(result).record.getAny("cncfRevision") shouldBe None
              after.oldroot shouldBe before.oldroot
              after.oldside shouldBe before.oldside
            } finally success(reader.closeC())
          } finally success(writer.closeC())
        }
      }
    }

    "enforce absence across independent provider connections" which {
      "CA-RACE commit one consistent canonical winner without overwriting its body" in {
        Given("two separately owned SQL providers to one file and different candidates for one fresh canonical identity")
        withSqlite { (database, first) =>
          val second = database.open()
          val setup = fixture("cncf_sqlite_create_race", provider = first)
          val other = fixture("cncf_sqlite_create_race", provider = second)
          setup.seed()
          val before = setup.base.snapshot(database.open())
          val candidates = Vector(setup.input(BodyMode.Overflow, Some(setup.freshId), "A" * 6000),
            other.input(BodyMode.Overflow, Some(other.freshId), "B" * 7000))
          When("a start barrier releases independent native transactions without sharing a JDBC connection")
          val results = race(() => setup.createC(candidates(0)), () => other.createC(candidates(1)))
          val winners = results.zipWithIndex.collect { case (Consequence.Success(created), index) => created -> index }
          val after = setup.snapshot(setup.freshId, database.open())
          Then("one acknowledged pair matches its candidate, while any loser is duplicate or a real structured contention failure")
          winners.size shouldBe 1
          results.filter(_.isFaillure).foreach { result =>
            (taxonomy(result) == duplicateTaxonomy(setup.freshId) || reason(result).contains("provider-failure")) shouldBe true
          }
          val (created, index) = winners.head
          created.record.map(_.asMap) shouldBe after.root
          val prepared = success(ContentBodyStoragePolicy.planForVersionedSave(setup.freshId, candidates(index).record))
          prepared.record.fields.filter(_.key.startsWith("content")).foreach(field =>
            storageScalar(created.record.get.getAny(field.key).get) shouldBe storageScalar(field.value.single))
          after.overflow shouldBe Some(prepared.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap)
          after.preexisting shouldBe before
        }
      }
    }
  }
}
