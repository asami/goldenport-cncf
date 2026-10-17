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
final class EntityCreateFailureSafetySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityCreateFailureSafetyFixture.*
  import EntityObservedDeleteCanonicalFixture.{reason, success}

  "Canonical Entity create failure safety" should {
    "CFS-E1 initialize generated identities" which {
      Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).foreach { representation =>
        s"return the admitted INITIAL record and hydrate the overflow without changing older rows for $representation" in {
          Given(s"a real memory collection with $representation and two preexisting overflow entities")
          val setup = fixture(s"cfs_e1_$representation", representation = representation)
          setup.seed()
          val before = setup.base.snapshot()
          val candidate = setup.input(BodyMode.Overflow, None)

          When("canonical EntityStoreSpace create generates its identity and publishes the candidate")
          val result = setup.createC(candidate)
          val created = success(result)
          val after = setup.snapshot(created.id)
          val loaded = setup.hydratedC(created.id)

          Then("the exact admitted record belongs to this collection at INITIAL one with the full body")
          result shouldBe a[Consequence.Success[?]]
          created.id.collection shouldBe setup.base.collectionId
          created.id should not be setup.base.id
          created.id should not be setup.base.unrelatedId
          created.id.timestamp.nonEmpty shouldBe true
          created.id.entropy.nonEmpty shouldBe true
          created.record.map(_.asMap) shouldBe after.root
          created.record.flatMap(_.getString("title")) shouldBe Some("Candidate")
          success(EntityRevision.createC(after.root.get(setup.base.revisionField))) shouldBe EntityRevision.INITIAL
          after.root.get.get("content_storage") shouldBe Some("overflow")
          after.overflow.flatMap(_.get("content")) shouldBe candidate.record.getAny("content")
          success(loaded).map(_.identity) shouldBe Some(created.id)
          success(loaded).flatMap(_.record.getString("content")) shouldBe candidate.record.getString("content")
          after.preexisting shouldBe before
        }
      }
    }

    "CFS-E2 reject duplicate overflow candidates" which {
      "retain the complete selected body and every unrelated row with native duplicate taxonomy" in {
        Given("an existing revision-seven root and body plus an unrelated root and body in real memory")
        val setup = fixture("cfs_e2")
        setup.seed()
        val before = setup.snapshot(setup.base.id)
        val candidate = setup.input(BodyMode.Overflow, Some(setup.base.id))

        When("canonical create attempts the same identity with different large content")
        val result = setup.createC(candidate)
        val after = setup.snapshot(setup.base.id)

        Then("the native duplicate failure retains every complete record including the old overflow")
        result shouldBe a[Consequence.Failure[?]]
        taxonomy(result) shouldBe duplicateTaxonomy(setup.base.id)
        after shouldBe before
      }
    }

    "CFS-E3 reject duplicate inline candidates" which {
      "retain the original overflow instead of deleting it before duplicate admission" in {
        Given("a selected overflow entity and a different inline candidate for its existing identity")
        val setup = fixture("cfs_e3")
        setup.seed()
        val before = setup.snapshot(setup.base.id)
        val candidate = setup.input(BodyMode.Inline, Some(setup.base.id))

        When("canonical create attempts the duplicate inline candidate")
        val result = setup.createC(candidate)
        val after = setup.snapshot(setup.base.id)

        Then("native duplicate leaves the selected and unrelated root and overflow maps unchanged")
        result shouldBe a[Consequence.Failure[?]]
        taxonomy(result) shouldBe duplicateTaxonomy(setup.base.id)
        after shouldBe before
      }
    }

    "CFS-E4 reject duplicate missing and cleared content" which {
      Vector(BodyMode.Absent, BodyMode.SetNull).foreach { mode =>
        s"preserve every row independently for $mode content" in {
          Given(s"a fresh real memory fixture with existing overflow and a $mode duplicate candidate")
          val setup = fixture(s"cfs_e4_$mode")
          setup.seed()
          val before = setup.snapshot(setup.base.id)
          val candidate = setup.input(mode, Some(setup.base.id))

          When(s"canonical create attempts the $mode candidate")
          val result = setup.createC(candidate)
          val after = setup.snapshot(setup.base.id)

          Then("native duplicate retains the full selected and preexisting snapshot")
          result shouldBe a[Consequence.Failure[?]]
          taxonomy(result) shouldBe duplicateTaxonomy(setup.base.id)
          after shouldBe before
        }
      }
    }

    "CFS-E5 preserve fresh absence on root rejection" which {
      "leave no orphan body after side staging and rejected root create" in {
        Given("a seeded real memory provider armed only at the fresh typed BeforeRootWrite checkpoint")
        val store = new MemoryStore()
        val setup = fixture("cfs_e5", provider = store)
        setup.seed()
        val before = setup.snapshot(setup.freshId)
        val candidate = setup.input(BodyMode.Overflow, Some(setup.freshId))
        store.arm(setup.fault(FaultMode.RootCreate))

        When("canonical create stages the exact side record and reaches the keyed root rejection before publication")
        val result = setup.createC(candidate)
        val after = setup.snapshot(setup.freshId)

        Then("the provider marker is preserved and all fresh and preexisting records retain their prior state")
        result shouldBe a[Consequence.Failure[?]]
        reason(result) shouldBe Some("provider-failure")
        store.sideSaves shouldBe 1
        store.rootCreates shouldBe 1
        before.root shouldBe None
        before.overflow shouldBe None
        after.root shouldBe None
        after.overflow shouldBe None
        after shouldBe before
      }
    }

    "CFS-E6 stop at rejected side save" which {
      "never reach fresh root create and retain the entire real provider state" in {
        Given("a seeded memory provider armed only for the fresh canonical overflow save before write")
        val store = new MemoryStore()
        val setup = fixture("cfs_e6", provider = store)
        setup.seed()
        val before = setup.snapshot(setup.freshId)
        val candidate = setup.input(BodyMode.Overflow, Some(setup.freshId))
        store.arm(setup.fault(FaultMode.SideSave))

        When("canonical create reaches that real BeforeSideWrite checkpoint before side staging")
        val result = setup.createC(candidate)
        val after = setup.snapshot(setup.freshId)

        Then("the structured provider failure publishes neither root nor body and changes no old row")
        result shouldBe a[Consequence.Failure[?]]
        reason(result) shouldBe Some("provider-failure")
        store.sideSaves shouldBe 1
        store.rootCreates shouldBe 0
        after.root shouldBe None
        after.overflow shouldBe None
        after shouldBe before
      }
    }

    "CFS-E7 reject application managed revisions" which {
      Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).foreach { representation =>
        s"reject the $representation managed revision before side or root effects" in {
          Given(s"a $representation binding and an application-supplied managed field on a large fresh candidate")
          val store = new MemoryStore()
          val setup = fixture(s"cfs_e7_$representation", representation = representation, provider = store)
          setup.seed()
          val before = setup.snapshot(setup.freshId)
          val source = setup.input(BodyMode.Overflow, Some(setup.freshId))
          val candidate = source.copy(record = source.record.upsertSingle(setup.base.revisionField, 13L))
          store.arm(setup.fault(FaultMode.RootCreate))

          When("canonical create validates the admitted managed revision field")
          val result = setup.createC(candidate)
          val after = setup.snapshot(setup.freshId)

          Then("the actual managed-field policy failure occurs before any provider mutation")
          result shouldBe a[Consequence.Failure[?]]
          taxonomy(result) shouldBe taxonomy(Consequence.argumentPolicyViolation(
            "entity", "framework-managed-revision", s"record without ${setup.base.revisionField}", setup.base.revisionField))
          store.rootCreates shouldBe 0
          store.sideSaves shouldBe 0
          after shouldBe before
        }
      }
    }

    "CFS-E8 enforce real create authorization" which {
      "preserve persisted rows and primed resident and View after Component.Factory denial" in {
        Given("the real canonical factory denying an explicit UserPermission create grant and primed caches")
        val setup = fixture("cfs_e8", denyFactory = true)
        setup.seed()
        setup.base.primeResident()
        val residentbefore = setup.base.collection.resolveScoped(setup.base.id)(using setup.base.context)
        val viewbefore = setup.base.view.resolve(setup.base.id)
        val builds = setup.base.viewBuilds
        val before = setup.snapshot(setup.freshId)
        val grant = setup.base.authorization(setup.freshId).copy(accessKind = "create")
        val program = setup.program(setup.input(BodyMode.Overflow, Some(setup.freshId)), grant)

        When("UnitOfWorkInterpreter evaluates the actual Free EntityStoreCreate program")
        val result = setup.evaluateC(program)
        val after = setup.snapshot(setup.freshId)
        val residentafter = setup.base.collection.resolveScoped(setup.base.id)(using setup.base.context)
        val viewafter = setup.base.view.resolve(setup.base.id)

        Then("the actual create grant is denied and neither storage nor resident or View is invalidated")
        result shouldBe a[Consequence.Failure[?]]
        taxonomy(result) shouldBe taxonomy(Consequence.securityPermissionDenied("canonical-delete-factory-denied"))
        setup.base.factoryCalls shouldBe Vector(grant)
        grant.accessKind shouldBe "create"
        residentbefore shouldBe a[Consequence.Success[?]]
        viewbefore shouldBe a[Consequence.Success[?]]
        residentafter shouldBe residentbefore
        viewafter shouldBe viewbefore
        setup.base.viewBuilds shouldBe builds
        after shouldBe before
      }
    }
  }
}
