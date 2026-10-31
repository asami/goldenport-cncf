package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.datastore.EntityCreateProviderFixture.{race, storageScalar}
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.seedCanonical
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct. 14, 2026
 * @version Oct. 31, 2026
 */
@org.scalatest.DoNotDiscover
final class PostgresqlEntityCreateAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen with PostgresqlAcceptance {
  import EntityCreateFailureSafetyFixture.*
  import EntityObservedDeleteCanonicalFixture.{reason, success}

  "Native PostgreSQL canonical strict create" should {
    "retain canonical initialization and complete body correspondence" which {
      for {
        representation <- Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
        mode <- BodyMode.values.toVector
        supplied <- Vector(false, true)
      } {
        s"CFS-E1/S5 CA1/CA2/CA9/CA11 create $representation $mode with supplied identity $supplied" in {
          Given("the actual canonical binding, a real PostgreSQL writer and independent complete old rows")
          with_postgresql { database =>
            val writer = database.open()
            val reader = database.open()
            val token = database.token(s"pg_positive_${representation}_${mode}_$supplied")
            val setup = fixture(token, representation = representation, provider = writer)
            seedCanonical(setup)
            val before = setup.base.snapshot(reader)
            val candidate = setup.input(mode, if (supplied) Some(setup.freshId) else None)
            When("canonical EntityStoreSpace generates or retains the opaque identity and commits one native root/body unit")
            val created = success(setup.createC(candidate))
            val after = setup.snapshot(created.id, reader)
            val reopened = fixture(token, representation = representation, provider = reader)
            val hydrated = success(reopened.hydratedC(created.id))
            Then("the authoritative INITIAL record, native durable pair, complements and canonical hydration correspond")
            created.id.collection shouldBe setup.base.collectionId
            if (supplied) created.id shouldBe setup.freshId
            else {
              created.id.timestamp.nonEmpty shouldBe true
              created.id.entropy.nonEmpty shouldBe true
            }
            created.record.map(_.asMap) shouldBe after.root
            success(EntityRevision.createC(created.record.get.getAny(setup.base.revisionField).get)) shouldBe EntityRevision.INITIAL
            val preparation = success(ContentBodyStoragePolicy.planForVersionedSave(created.id, candidate.record))
            preparation.record.fields.filter(_.key.startsWith("content")).foreach { field =>
              storageScalar(created.record.get.getAny(field.key).get) shouldBe storageScalar(field.value.single)
            }
            if (mode == BodyMode.Overflow) {
              after.overflow shouldBe Some(preparation.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap)
              hydrated.flatMap(_.record.getString("content")) shouldBe candidate.record.getString("content")
            } else {
              after.overflow shouldBe None
              hydrated.flatMap(_.record.getString("content")) shouldBe (if (mode == BodyMode.Inline) Some("small candidate") else None)
            }
            created.record.get.getString("title") shouldBe Some("Candidate")
            created.record.get.fields.exists(_.key == "created_at") shouldBe true
            after.preexisting shouldBe before
          }
        }
      }
      "CA1 create an admitted unmanaged collection without inventing a managed revision" in {
        Given("an actual unmanaged canonical collection backed by native PostgreSQL")
        with_postgresql { database =>
          val writer = database.open()
          val reader = database.open()
          val token = database.token("pg_unmanaged")
          val setup = EntityObservedDeleteCanonicalFixture.fixture(token, provider = writer, managed = false)
          When("canonical creation admits its ordinary persistent codec")
          val created = success(setup.createC(EntityObservedDeleteCanonicalFixture.Draft("Unmanaged", "plain body")))
          val durable = success(reader.load(setup.dataCollection, DataStore.EntryId(created.id))(using setup.context)).get
          Then("the actual stored record retains identity and body without synthetic revision management")
          created.record.map(_.asMap) shouldBe Some(durable.asMap)
          durable.getAny("cncf_revision") shouldBe None
          durable.getAny("revision") shouldBe None
          success(setup.loadC(created.id)).map(_.title) shouldBe Some("Unmanaged")
        }
      }
    }

    "preserve selected and unrelated persistence under native duplicate admission" which {
      for { representation <- Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached); mode <- BodyMode.values.toVector } {
        s"CFS-E2/E3/E4/S1/S2 reject $representation $mode without corrupting the winning body" in {
          Given("a real canonical root/body pair and complete independent selected and unrelated snapshots")
          with_postgresql { database =>
            val setup = fixture(database.token(s"pg_duplicate_${representation}_$mode"), representation = representation, provider = database.open())
            seedCanonical(setup)
            val reader = database.open()
            val before = setup.snapshot(setup.base.id, reader)
            When("the actual atomic provider rejects the duplicate canonical create")
            val result = setup.createC(setup.input(mode, Some(setup.base.id)))
            Then("DataStoreDuplicate and every field of the durable pair are retained")
            taxonomy(result) shouldBe duplicateTaxonomy(setup.base.id)
            setup.snapshot(setup.base.id, reader) shouldBe before
          }
        }
      }
    }

    "distinguish real prepublication rejection from loss after real commit" which {
      for {
        representation <- Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
        mode <- Vector(FaultMode.SideSave, FaultMode.RootCreate, FaultMode.BeforePublish, FaultMode.ThrowSide,
          FaultMode.ThrowRoot, FaultMode.ThrowPublish, FaultMode.CommitReject, FaultMode.CommitAckLoss,
          FaultMode.CommitAckThrow, FaultMode.CommitAckNull)
      } {
        s"CFS-E5/E6/S3/S4 CA-PUBLISH/CA-COMMIT expose the actual $representation $mode boundary" in {
          Given("an actual PostgreSQL checkpoint writer, exact fresh target and independently retained older pairs")
          with_postgresql { database =>
            val writer = database.openFault()
            val reader = database.open()
            val token = database.token(s"pg_fault_${representation}_$mode")
            val setup = fixture(token, representation = representation, provider = writer)
            seedCanonical(setup)
            val before = setup.snapshot(setup.freshId, reader)
            val candidate = setup.input(BodyMode.Overflow, Some(setup.freshId))
            writer.arm(setup.fault(mode))
            When("one native attempt reaches side, root after side, unpublished rows or the actual connection commit")
            val result = setup.createC(candidate)
            val after = setup.snapshot(setup.freshId, reader)
            Then("the exact checkpoint counts distinguish rollback from the correct durable pair with unknown acknowledgement")
            result shouldBe a[Consequence.Failure[?]]
            writer.sideSaves shouldBe 1
            writer.rootCreates shouldBe (if (mode == FaultMode.SideSave || mode == FaultMode.ThrowSide) 0 else 1)
            val commitmode = Vector(FaultMode.CommitReject, FaultMode.CommitAckLoss, FaultMode.CommitAckThrow, FaultMode.CommitAckNull).contains(mode)
            writer.commitAttempts shouldBe (if (commitmode) 1 else 0)
            if (Vector(FaultMode.CommitAckLoss, FaultMode.CommitAckThrow, FaultMode.CommitAckNull).contains(mode)) {
              reason(result) shouldBe Some("transaction-indeterminate")
              val preparation = success(ContentBodyStoragePolicy.planForVersionedSave(setup.freshId, candidate.record))
              val root = success(reader.load(setup.base.dataCollection, DataStore.EntryId(setup.freshId))(using setup.base.context)).get
              success(EntityRevision.createC(root.getAny(setup.base.revisionField).get)) shouldBe EntityRevision.INITIAL
              root.getString("title") shouldBe Some("Candidate")
              after.root shouldBe Some(root.asMap)
              after.overflow shouldBe Some(preparation.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap)
              after.preexisting shouldBe before.preexisting
              success(fixture(token, representation = representation, provider = reader).hydratedC(setup.freshId)).flatMap(_.record.getString("content")) shouldBe candidate.record.getString("content")
            } else {
              reason(result) shouldBe Some(if (mode == FaultMode.CommitReject) "transaction-failure" else "provider-failure")
              after shouldBe before
            }
          }
        }
      }
      Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).foreach { representation =>
        s"CA8 roll back first-use $representation DDL with the rejected root after a real side write" in {
          Given("a real PostgreSQL transaction without a selected root table and a complete native catalog/row snapshot")
          with_postgresql { database =>
            val writer = database.openFault()
            val setup = fixture(database.token(s"pg_first_use_$representation"), representation = representation, provider = writer)
            val before = database.snapshotAll()
            writer.arm(setup.fault(FaultMode.RootCreate))
            When("same-connection preparation creates schema, writes the overflow and rejects BeforeRootWrite")
            val result = setup.createC(setup.input(BodyMode.Overflow, Some(setup.freshId)))
            Then("the actual transactional schema and every selected/unrelated row roll back together")
            reason(result) shouldBe Some("provider-failure")
            writer.sideSaves shouldBe 1
            writer.rootCreates shouldBe 1
            database.snapshotAll() shouldBe before
          }
        }
      }
    }

    "retain managed-field and actual UnitOfWork authorization admission" which {
      Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).foreach { representation =>
        s"CFS-E7 reject the client $representation revision before all effects" in {
          Given("an actual bound canonical collection and a client revision on an overflow candidate")
          with_postgresql { database =>
            val writer = database.openFault()
            val setup = fixture(database.token(s"pg_managed_$representation"), representation = representation, provider = writer)
            seedCanonical(setup)
            val reader = database.open()
            val before = setup.snapshot(setup.freshId, reader)
            writer.arm(setup.fault(FaultMode.RootCreate))
            val input = setup.input(BodyMode.Overflow, Some(setup.freshId))
            When("canonical initialization rejects the managed field")
            val result = setup.createC(input.copy(record = input.record.upsertSingle(setup.base.revisionField, 13L)))
            Then("the original structured admission and independent complete rows remain unchanged")
            taxonomy(result) shouldBe taxonomy(Consequence.argumentPolicyViolation("entity", "framework-managed-revision",
              s"record without ${setup.base.revisionField}", setup.base.revisionField))
            writer.sideSaves shouldBe 0
            writer.rootCreates shouldBe 0
            setup.snapshot(setup.freshId, reader) shouldBe before
          }
        }
      }
      "CFS-E8 preserve native persistence, resident state and View after actual create permission denial" in {
        Given("a canonical factory denying a UserPermission create with actual primed resident and View state")
        with_postgresql { database =>
          val setup = fixture(database.token("pg_create_denied"), provider = database.open(), denyFactory = true)
          seedCanonical(setup)
          setup.base.primeResident()
          val resident = setup.base.collection.resolveScoped(setup.base.id)(using setup.base.context)
          val view = setup.base.view.resolve(setup.base.id)
          val builds = setup.base.viewBuilds
          val reader = database.open()
          val before = setup.snapshot(setup.freshId, reader)
          val grant = setup.base.authorization(setup.freshId).copy(accessKind = "create")
          When("the active UnitOfWork interpreter evaluates the actual canonical create program")
          val result = setup.evaluateC(setup.program(setup.input(BodyMode.Overflow, Some(setup.freshId)), grant))
          Then("the exact grant is denied without storage, resident or View changes")
          taxonomy(result) shouldBe taxonomy(Consequence.securityPermissionDenied("canonical-delete-factory-denied"))
          setup.base.factoryCalls shouldBe Vector(grant)
          setup.snapshot(setup.freshId, reader) shouldBe before
          setup.base.collection.resolveScoped(setup.base.id)(using setup.base.context) shouldBe resident
          setup.base.view.resolve(setup.base.id) shouldBe view
          setup.base.viewBuilds shouldBe builds
        }
      }
    }

    "arbitrate independent same-id creates without replay or caller exclusion" which {
      Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).foreach { representation =>
        s"CA-RACE retain one exact acknowledged $representation native root/body winner" in {
          Given("two real independently owned PostgreSQL pools and different large canonical candidates")
          with_postgresql { database =>
            val token = database.token(s"pg_race_$representation")
            val first = fixture(token, representation = representation, provider = database.open())
            val second = fixture(token, representation = representation, provider = database.open())
            seedCanonical(first)
            val reader = database.open()
            val before = first.base.snapshot(reader)
            val candidates = Vector(first.input(BodyMode.Overflow, Some(first.freshId), "A" * 6000),
              second.input(BodyMode.Overflow, Some(second.freshId), "B" * 7000))
            When("the start barrier releases both actual native transactions for one fresh identity")
            val results = race(() => first.createC(candidates(0)), () => second.createC(candidates(1)))
            val winners = results.zipWithIndex.collect { case (Consequence.Success(created), index) => created -> index }
            Then("one id-primary arbiter winner owns the exact durable pair and the loser cannot replace its body")
            winners.size shouldBe 1
            val (created, index) = winners.head
            results.filter(_.isFaillure).foreach(result => taxonomy(result) shouldBe duplicateTaxonomy(first.freshId))
            val after = first.snapshot(first.freshId, reader)
            created.record.map(_.asMap) shouldBe after.root
            val preparation = success(ContentBodyStoragePolicy.planForVersionedSave(first.freshId, candidates(index).record))
            after.overflow shouldBe Some(preparation.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap)
            after.preexisting shouldBe before
          }
        }
      }
    }
  }
}
