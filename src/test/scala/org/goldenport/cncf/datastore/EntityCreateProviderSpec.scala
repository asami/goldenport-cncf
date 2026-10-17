package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.entity.{ContentBodyStoragePolicy, EntityCreateFailureSafetyFixture, EntityRevisionRepresentation}
import org.goldenport.cncf.entity.EntityObservedDeleteCanonicalFixture.{reason, success}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 */
final class EntityCreateProviderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityCreateFailureSafetyFixture.*
  import EntityCreateProviderFixture.race

  "CA1/CA2/CA5/CA6/CA9/CA11 real memory create" should {
    "publish authoritative canonical roots and typed content effects" which {
      for {
        searchable <- Vector(false, true)
        representation <- Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached)
        mode <- BodyMode.values.toVector
      } {
        s"CFS-E1 acknowledge $representation $mode creation on searchable=$searchable with every old row retained" in {
          Given("a fresh actual memory provider, canonical binding and complete seeded root and overflow rows")
          val provider = if (searchable) DataStore.inMemorySearchable() else DataStore.inMemory()
          val setup = fixture(s"memory_${searchable}_${representation}_$mode", representation = representation, provider = provider)
          setup.seed()
          val before = setup.base.snapshot()
          val candidate = setup.input(mode, None)
          When("canonical create generates an identity and the actual provider publishes its complete next map")
          val result = setup.createC(candidate)
          val created = success(result)
          val after = setup.snapshot(created.id)
          val hydrated = success(setup.hydratedC(created.id))
          Then("the returned map is the stored root with exact INITIAL, body shape and unrelated state")
          created.id.collection shouldBe setup.base.collectionId
          created.id.timestamp.nonEmpty shouldBe true
          created.id.entropy.nonEmpty shouldBe true
          created.record.map(_.asMap) shouldBe after.root
          success(EntityRevision.createC(created.record.get.getAny(setup.base.revisionField).get)) shouldBe EntityRevision.INITIAL
          val preparation = success(ContentBodyStoragePolicy.planForVersionedSave(created.id, candidate.record))
          preparation.record.fields.filter(_.key.startsWith("content")).foreach { field =>
            created.record.get.getAny(field.key) shouldBe Some(field.value.single)
          }
          if (mode == BodyMode.Overflow) {
            after.overflow shouldBe Some(preparation.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap)
            hydrated.flatMap(_.record.getString("content")) shouldBe candidate.record.getString("content")
          } else {
            after.overflow shouldBe None
            hydrated.flatMap(_.record.getString("content")) shouldBe (if (mode == BodyMode.Inline) Some("small candidate") else None)
          }
          after.preexisting shouldBe before
        }
      }
    }

    "retain complete maps on native duplicate" which {
      for { searchable <- Vector(false, true); mode <- BodyMode.values.toVector } {
        s"CFS-E2/E3/E4 reject $mode on searchable=$searchable without replacing the winning body" in {
          Given("a seeded actual memory store with an existing root and overflow at the selected identity")
          val provider = if (searchable) DataStore.inMemorySearchable() else DataStore.inMemory()
          val setup = fixture(s"memory_duplicate_${searchable}_$mode", provider = provider)
          setup.seed()
          val before = setup.snapshot(setup.base.id)
          When("canonical strict create attempts the same identity with the selected new body mode")
          val result = setup.createC(setup.input(mode, Some(setup.base.id)))
          Then("native duplicate and complete selected/unrelated preservation hold together")
          taxonomy(result) shouldBe duplicateTaxonomy(setup.base.id)
          setup.snapshot(setup.base.id) shouldBe before
        }
      }
    }

    "abort unpublished candidates at every actual checkpoint" which {
      Vector(FaultMode.SideSave, FaultMode.RootCreate, FaultMode.BeforePublish,
        FaultMode.ThrowSide, FaultMode.ThrowRoot, FaultMode.ThrowPublish).foreach { mode =>
        s"CA-PUBLISH/CFS-E5/E6 retain all actual rows for $mode" in {
          Given("a real searchable memory store armed at one exact canonical create checkpoint")
          val provider = new MemoryStore()
          val setup = fixture(s"memory_fault_$mode", provider = provider)
          setup.seed()
          val before = setup.snapshot(setup.freshId)
          val candidate = setup.input(BodyMode.Overflow, Some(setup.freshId))
          provider.arm(setup.fault(mode))
          When("strict create stages side/root candidates and encounters the selected rejection or throw")
          val result = setup.createC(candidate)
          Then("the structured provider failure retains fresh absence and every full preexisting map")
          result shouldBe a[Consequence.Failure[?]]
          reason(result) shouldBe Some("provider-failure")
          provider.sideSaves shouldBe 1
          provider.rootCreates shouldBe (if (mode == FaultMode.SideSave || mode == FaultMode.ThrowSide) 0 else 1)
          setup.snapshot(setup.freshId) shouldBe before
        }
      }
    }

    "enforce absence under real concurrent publication" which {
      for { searchable <- Vector(false, true); representation <- Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached) } {
        s"CA-RACE acknowledge one consistent $representation root/body winner on searchable=$searchable" in {
          Given("one actual provider and two different large candidates for the same fresh canonical identity")
          val provider = if (searchable) DataStore.inMemorySearchable() else DataStore.inMemory()
          val setup = fixture(s"memory_race_${searchable}_$representation", representation = representation, provider = provider)
          setup.seed()
          val before = setup.base.snapshot()
          val first = setup.input(BodyMode.Overflow, Some(setup.freshId), "A" * 6000)
          val second = setup.input(BodyMode.Overflow, Some(setup.freshId), "B" * 7000)
          val candidates = Vector(first, second)
          When("a start barrier releases both actual creates without a caller mutex")
          val results = race(() => setup.createC(first), () => setup.createC(second))
          val winners = results.zipWithIndex.collect { case (Consequence.Success(created), index) => created -> index }
          val after = setup.snapshot(setup.freshId)
          Then("one acknowledged root matches its own full body and the loser cannot overwrite any winning or unrelated row")
          winners.size shouldBe 1
          results.count(_.isFaillure) shouldBe 1
          results.filter(_.isFaillure).foreach(result => taxonomy(result) shouldBe duplicateTaxonomy(setup.freshId))
          val (created, index) = winners.head
          created.record.map(_.asMap) shouldBe after.root
          after.overflow.flatMap(_.get("content")) shouldBe candidates(index).record.getAny("content")
          success(EntityRevision.createC(created.record.get.getAny(setup.base.revisionField).get)) shouldBe EntityRevision.INITIAL
          val prepared = success(ContentBodyStoragePolicy.planForVersionedSave(setup.freshId, candidates(index).record))
          prepared.record.fields.filter(_.key.startsWith("content")).foreach(field =>
            created.record.get.getAny(field.key) shouldBe Some(field.value.single))
          after.overflow shouldBe Some(prepared.sideEffects.head.asInstanceOf[EntityVersionedSideEffect.Save].record.asMap)
          after.preexisting shouldBe before
        }
      }
    }
  }
}
