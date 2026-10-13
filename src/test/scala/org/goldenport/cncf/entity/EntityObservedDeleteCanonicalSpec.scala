package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.unitofwork.{UnitOfWorkOp, UnitOfWorkInterpreter, UnitOfWork}
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}
import org.simplemodeling.model.statemachine.{Aliveness, PostStatus}

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
final class EntityObservedDeleteCanonicalSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityObservedDeleteCanonicalFixture.*
  import EntityObservedDeleteCanonicalProviderFixture.*

  "Canonical observed Entity deletion" should {
    "COD-E1 delete the physical detached target" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-E1, rules:R1,R2,R3,R4, phase:96") {
      "return the actual prior revision and remove only its root and canonical overflow" in {
        Given("a real detached root at revision seven, canonical overflow and an unrelated entity")
        val store = new MemoryStore()
        val setup = fixture(token = "cod_e1", provider = store, initialRevision = 7L)
        setup.seed()
        val before = setup.snapshot()

        When("EntityStoreSpace requests observed deletion through the assembled standard store")
        val result = setup.deleteC(revision(7L))
        val after = setup.snapshot()

        Then("the provider receipt identifies revision seven and exactly the selected pair disappears")
        result shouldBe Consequence.success(EntityDeletionResult(setup.id, revision(7L)))
        after.root shouldBe None
        after.overflow shouldBe None
        after.unrelatedRoot shouldBe before.unrelatedRoot
        after.unrelatedOverflow shouldBe before.unrelatedOverflow
        store.deleteCalls shouldBe 1
        store.genericDeletes shouldBe 0
        store.lastPlan.map(_.revisionField) shouldBe Some(EntityRevisionRepresentation.Detached.storageFieldName)
        store.lastPlan.map(_.sideEffects) shouldBe Some(ContentBodyStoragePolicy.planForDelete(setup.id))
      }
    }

    "COD-E2 apply the existing embedded logical lifecycle" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-E2, rules:R1,R2,R3,R4, phase:96") {
      "preserve business and overflow while advancing storage once and returning the prior match" in {
        Given("a logically deletable embedded root at revision twelve and a fixed server identity and clock")
        val store = new MemoryStore()
        val setup = fixture(token = "cod_e2", logical = true, representation = EntityRevisionRepresentation.Embedded,
          provider = store, initialRevision = 12L)
        setup.seed()
        val before = setup.snapshot()

        When("the standard store selects its existing server-owned soft-delete policy")
        val result = setup.deleteC(revision(12L))
        val row = success(setup.rawC()).get
        val after = setup.snapshot()

        Then("audit is server-derived, storage is thirteen and the immutable receipt remains twelve")
        result shouldBe Consequence.success(EntityDeletionResult(setup.id, revision(12L)))
        success(EntityRevision.createC(row.getAny(setup.revisionField).get)) shouldBe revision(13L)
        row.getAs[Aliveness]("aliveness") shouldBe Some(Aliveness.Dead)
        row.getAs[PostStatus]("post_status") shouldBe Some(PostStatus.Archived)
        Vector("updated_at", "deleted_at").foreach(key => row.getString(key) shouldBe Some(instant.toString))
        Vector("updated_by", "deleted_by").foreach(key => row.getString(key) shouldBe Some("owner"))
        row.getString("trace_id") shouldBe Some(setup.context.observability.traceId.value)
        row.getString("correlation_id") shouldBe setup.context.observability.correlationId.map(_.value)
        Vector("title", "business", "content_ref", "owner_id", "permission").foreach(key =>
          row.getAny(key) shouldBe before.root.get.get(key))
        after.overflow shouldBe before.overflow
        after.unrelatedRoot shouldBe before.unrelatedRoot
        after.unrelatedOverflow shouldBe before.unrelatedOverflow
        store.lastPlan.map(_.sideEffects) shouldBe Some(Vector.empty)
        store.deleteCalls shouldBe 1
      }
    }

    "COD-E3 obey assembled representation and lifecycle policy" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-E3, rules:R2,R3,R4, phase:96") {
      "support detached logical and embedded physical policy without caller-selected storage decisions" in {
        Given("assembled detached logical and embedded physical collections with concurrency None")
        val setups = Vector(
          fixture(token = "cod_e3_logical", logical = true, initialRevision = 8L),
          fixture(token = "cod_e3_physical", representation = EntityRevisionRepresentation.Embedded, initialRevision = 8L))
        setups.foreach(_.seed())
        val before = setups.map(_.snapshot())

        When("the same typed observed-delete command supplies only identity and revision")
        val results = setups.map(_.deleteC(revision(8L)))
        val after = setups.map(_.snapshot())

        Then("both are strict while their plans retain the assembly binding and established root policy")
        results.zip(setups).foreach { case (result, setup) =>
          result shouldBe Consequence.success(EntityDeletionResult(setup.id, revision(8L)))
          setup.provider.asInstanceOf[MemoryStore].lastPlan.map(_.revisionField) shouldBe Some(setup.revisionField)
        }
        success(EntityRevision.createC(after.head.root.get(setups.head.revisionField))) shouldBe revision(9L)
        after.head.overflow shouldBe before.head.overflow
        after(1).root shouldBe None
        after(1).overflow shouldBe None
        after.zip(before).foreach { case (actual, previous) =>
          actual.unrelatedRoot shouldBe previous.unrelatedRoot
          actual.unrelatedOverflow shouldBe previous.unrelatedOverflow
        }
      }
    }

    "COD-E4 reject an observation superseded by a canonical update" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-E4, rules:R2,R4,R5, phase:96") {
      "preserve every complete persisted row and return the existing structured stale failure" in {
        Given("both revision representations with an actual intervening canonical observed update")
        val setups = Vector(EntityRevisionRepresentation.Embedded, EntityRevisionRepresentation.Detached).map { representation =>
          val setup = fixture(token = s"cod_e4_${representation.toString}", representation = representation)
          setup.seed()
          setup.updateC() shouldBe a[Consequence.Success[?]]
          setup
        }
        val before = setups.map(_.snapshot())

        When("canonical deletion still presents revision one")
        val results = setups.map(_.deleteC())
        val after = setups.map(_.snapshot())

        Then("stale expected one and actual two carry no deletion effects or fabricated success")
        results.foreach { result =>
          reason(result) shouldBe Some("stale-entity-revision")
          result shouldBe a[Consequence.Failure[?]]
          result match {
            case Consequence.Failure(conclusion) =>
              conclusion.observation.cause.descriptor.facets should contain(Descriptor.Facet.Expected(1L))
              conclusion.observation.cause.descriptor.facets should contain(Descriptor.Facet.Actual(2L))
            case _ => fail("Stale deletion must be a structured failure")
          }
        }
        after shouldBe before
      }
    }

    "COD-E5 admit mandatory request and persisted state before effects" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-E5, rules:R1,R2,R4,R5, phase:96") {
      "distinguish malformed constructors from representable request failures and preserve rejected targets" in {
        Given("real missing, deleted, malformed, exhausted and unmanaged targets plus null mandatory requests")
        val definitions = Vector("absent", "dead", "deleted_at", "deletedAt", "missing", "zero", "negative", "fractional", "text", "exhausted", "unmanaged")
        val setups = definitions.map { name =>
          val setup = fixture(token = s"cod_e5_$name", initialRevision = if (name == "exhausted") Long.MaxValue else 1L,
            managed = name != "unmanaged")
          setup.seed()
          val row = success(setup.rawC()).get
          val changed = name match {
            case "dead" => row.upsertSingle("aliveness", "dead")
            case "deleted_at" | "deletedAt" => row.upsertSingle(name, instant)
            case "missing" => Record(row.fields.filterNot(_.key == setup.revisionField))
            case "zero" => row.upsertSingle(setup.revisionField, 0L)
            case "negative" => row.upsertSingle(setup.revisionField, -1L)
            case "fractional" => row.upsertSingle(setup.revisionField, 1.5d)
            case "text" => row.upsertSingle(setup.revisionField, "invalid")
            case _ => row
          }
          if (name == "absent") success(setup.provider.delete(setup.dataCollection, DataStore.EntryId(setup.id))(using setup.context))
          else success(setup.provider.save(setup.dataCollection, DataStore.EntryId(setup.id), changed)(using setup.context))
          setup.resetObservation()
          (name, setup, setup.snapshot())
        }
        val requests = fixture(token = "cod_e5_request", provider = new MemoryStore())
        requests.seed()
        val requestbefore = requests.snapshot()
        requests.resetObservation()

        When("invalid stored states, representable null requests and malformed construction reach their own boundaries")
        val results = setups.map { case (name, setup, before) =>
          (name, setup.deleteC(if (name == "exhausted") revision(Long.MaxValue) else EntityRevision.INITIAL), setup.snapshot(), before,
            setup.provider.asInstanceOf[MemoryStore].deleteCalls)
        }
        val malformed = Vector(
          requests.context.entityStoreSpace.deleteObserved(null)(using requests.context),
          requests.context.entityStoreSpace.deleteObserved(UnitOfWorkOp.EntityStoreDeleteObserved(null, EntityRevision.INITIAL))(using requests.context),
          requests.context.entityStoreSpace.deleteObserved(UnitOfWorkOp.EntityStoreDeleteObserved(requests.id, null))(using requests.context),
          new UnitOfWorkInterpreter(new UnitOfWork(requests.context)).interpret(UnitOfWorkOp.EntityStoreDeleteObserved(null, null)))
        val requestloads = requests.provider.asInstanceOf[MemoryStore].loads
        val constructor = Consequence(new CanonicalId(null, "invalid"))
        val revisionconstructor = EntityRevision.createC(0L)
        val requestafter = requests.snapshot()

        Then("all failures are structured, no rejected target reaches compare-delete and malformed requests perform no load")
        results.foreach { case (name, result, after, before, calls) => withClue(name) {
          result shouldBe a[Consequence.Failure[?]]
          after shouldBe before
          calls shouldBe 0
          if (Set("absent", "dead", "deleted_at", "deletedAt").contains(name)) symptom(result) shouldBe Some(Taxonomy.Symptom.NotFound)
        } }
        malformed.foreach(_ shouldBe a[Consequence.Failure[?]])
        constructor shouldBe a[Consequence.Failure[?]]
        revisionconstructor shouldBe a[Consequence.Failure[?]]
        requestloads shouldBe 0
        requestafter shouldBe requestbefore
      }
    }

    "COD-E6 preserve unsupported and provider-result boundaries" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-E6, rules:R1,R4,R5, phase:96") {
      "fail without fallback or inventing a receipt from the requested revision" in {
        Given("real stores missing each capability, split root and overflow providers, and invalid provider responses")
        val features = Vector(EntityMutationProviderFeature.OptimisticCompareAndDelete,
          EntityMutationProviderFeature.AuthoritativeDeleteResult, EntityMutationProviderFeature.AtomicSideEffects)
        val incapable = features.zipWithIndex.map { case (feature, index) =>
          val setup = fixture(token = s"cod_e6_cap_$index", provider = new MemoryStore(missingFeature = Some(feature)))
          setup.seed(); setup
        }
        val splitcollection = EntityCollectionId("test", "canonical", "cod_e6_split")
        val root = new MemoryStore(acceptedCollection = Some(DataStore.CollectionId.EntityStore(splitcollection)))
        val side = new MemoryStore(acceptedCollection = Some(overflowCollection))
        val split = fixture(token = "cod_e6_split", provider = root, sideProvider = Some(side))
        split.seed()
        val invalid = Vector(
          Some(null: Consequence[EntityCompareAndDeleteResult]),
          Some(Consequence.success(null: EntityCompareAndDeleteResult)),
          Some(Consequence.success(EntityCompareAndDeleteResult.Deleted(revision(2L))))).zipWithIndex.map { case (returned, index) =>
          val setup = fixture(token = s"cod_e6_result_$index", provider = new MemoryStore(returned = returned))
          setup.seed(); setup
        }
        val setups = incapable ++ Vector(split) ++ invalid
        val before = setups.map(_.snapshot())

        When("canonical deletion exercises the unsupported default, routing admission and returned-result validation")
        val defaultresult = EntityStore.noop().deleteObserved(split.id, EntityRevision.INITIAL)(using split.context)
        val results = setups.map(_.deleteC())
        val after = setups.map(_.snapshot())

        Then("admission failures never dispatch and rejected mock outcomes are not evidence of provider rollback")
        reason(defaultresult) shouldBe Some("unsupported-capability")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        incapable.foreach(_.provider.asInstanceOf[MemoryStore].deleteCalls shouldBe 0)
        root.deleteCalls shouldBe 0
        side.deleteCalls shouldBe 0
        invalid.foreach(_.provider.asInstanceOf[MemoryStore].deleteCalls shouldBe 1)
        setups.foreach(_.provider.asInstanceOf[MemoryStore].genericDeletes shouldBe 0)
        after shouldBe before
      }
    }

    "COD-E7 publish neither root nor effects after memory checkpoint failure" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-E7, rules:R3,R4,R5, phase:96") {
      "retain complete state for every prepublication checkpoint and both lifecycle policies" in {
        Given("real memory checkpoint injection at root preparation, side preparation and before publication")
        val setups = for {
          checkpoint <- Vector(EntityVersionedMutationCheckpoint.RootPrepared, EntityVersionedMutationCheckpoint.SideEffectsPrepared,
            EntityVersionedMutationCheckpoint.BeforePublish)
          logical <- Vector(false, true)
        } yield {
          val setup = fixture(token = s"cod_e7_${checkpoint.toString}_$logical", logical = logical,
            provider = new MemoryStore(checkpoint = Some(checkpoint)))
          setup.seed(); setup
        }
        val before = setups.map(_.snapshot())

        When("the actual staged provider fails during canonical deletion")
        val results = setups.map(_.deleteC())
        val after = setups.map(_.snapshot())

        Then("each failure leaves both selected and unrelated complete records unpublished")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        after shouldBe before
        setups.foreach(_.provider.asInstanceOf[MemoryStore].deleteCalls shouldBe 1)
      }
    }

    "COD-E8 keep ordinary create and normal reads coherent with logical deletion" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-E8, rules:R1,R2,R3, phase:96") {
      "generate an opaque typed identity at INITIAL one and hide the logically deleted entity" in {
        Given("a canonical embedded collection and a create codec supplying no optional identity")
        val setup = fixture(token = "cod_e8", logical = true, representation = EntityRevisionRepresentation.Embedded)
        val created = success(setup.createC(Draft("Created", body)))
        val before = success(setup.rawC(created.id)).get
        val loaded = setup.loadC(created.id)
        val visible = setup.visibleC

        When("the actual generated identity is observed and logically deleted at INITIAL")
        val result = setup.context.entityStoreSpace.deleteObserved(
          UnitOfWorkOp.EntityStoreDeleteObserved(created.id, EntityRevision.INITIAL))(using setup.context)
        val after = success(setup.rawC(created.id)).get
        val hiddenload = setup.loadC(created.id)
        val hiddensearch = setup.visibleC

        Then("generated identity owns the collection, normal reads worked before and hide the tombstone afterward")
        created.id.collection shouldBe setup.collectionId
        created.id.timestamp.nonEmpty shouldBe true
        created.id.entropy.nonEmpty shouldBe true
        success(EntityRevision.createC(before.getAny("revision").get)) shouldBe EntityRevision.INITIAL
        success(loaded).map(_.id) shouldBe Some(created.id)
        success(visible).map(_.id) should contain(created.id)
        result shouldBe Consequence.success(EntityDeletionResult(created.id, EntityRevision.INITIAL))
        success(EntityRevision.createC(after.getAny("revision").get)) shouldBe revision(2L)
        hiddenload shouldBe Consequence.success(None)
        success(hiddensearch).map(_.id) should not contain created.id
      }
    }
  }
}
