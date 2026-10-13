package org.goldenport.cncf.unitofwork

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{EntityMutationProviderFeature, EntityVersionedMutationCheckpoint}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.operation.CmlOperationAccess
import org.goldenport.cncf.security.*
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
final class UnitOfWorkObservedDeleteSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EntityObservedDeleteCanonicalFixture.*
  import EntityObservedDeleteCanonicalProviderFixture.*

  "Active UnitOfWork observed Entity deletion" should {
    "COD-U1 acknowledge persistence before evicting Entity and invalidating View" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-U1, rules:R4,R6, phase:96") {
      "remove actual resident state and recompute the cached View only after successful deletion" in {
        Given("a real Component resident entity and a computed View over two actual stored entities")
        val store = new MemoryStore()
        val setup = fixture(token = "cod_u1", provider = store)
        setup.seed()
        setup.primeResident()
        val resident = setup.collection.resolveScoped(setup.id)(using setup.context)
        val cached = setup.view.resolve(setup.id)
        val before = setup.snapshot()
        setup.resetObservation()

        When("a genuine ExecUowM observed-delete program is evaluated in the active UnitOfWork")
        val result = setup.evaluateC(setup.program())
        val after = setup.snapshot()
        val evicted = setup.collection.resolveScoped(setup.id)(using setup.context)
        val recomputed = setup.view.resolve(setup.id)

        Then("the actual root disappears, resident resolution fails and the View reflects only the surviving entity")
        success(resident).id shouldBe setup.id
        success(cached) should have size 2
        result shouldBe Consequence.success(EntityDeletionResult(setup.id, EntityRevision.INITIAL))
        after.root shouldBe None
        after.overflow shouldBe None
        after.unrelatedRoot shouldBe before.unrelatedRoot
        after.unrelatedOverflow shouldBe before.unrelatedOverflow
        symptom(evicted) shouldBe Some(Taxonomy.Symptom.NotFound)
        success(recomputed) should have size 1
        setup.viewBuilds shouldBe 2
        store.deleteCalls shouldBe 1
      }
    }

    "COD-U2 authorize the actual delete target and preserve supplied evidence" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-U2, rules:R1,R4,R6, phase:96") {
      "allow the real owner, deny nonowner or factory before dispatch, and rebind only target collection and access kind" in {
        Given("actual UserPermission owner/nonowner contexts and an owner denied by its Component factory")
        val owner = fixture(token = "cod_u2_owner")
        val nonowner = fixture(token = "cod_u2_nonowner", principal = "stranger")
        val factorydenied = fixture(token = "cod_u2_factory", denyFactory = true)
        val setups = Vector(owner, nonowner, factorydenied)
        setups.foreach(_.seed())
        val before = setups.map(_.snapshot())
        val supplied = owner.authorization(owner.unrelatedId).copy(collectionName = Some("incorrect-collection"), accessKind = "read",
          resourceType = Some("RetainedResource"), entityNames = Vector("CanonicalEntity", "RetainedEntity"),
          access = Some(CmlOperationAccess(policy = "canonical-evidence", resource = Some("RetainedResource"),
            target = Some("retained-target"), mode = Some("user-permission"), relation = Some("retained-relation"),
            operationModel = Some("business-service"), entityUsage = Some("resource"), entityOperationKind = Some("resource"),
            entityApplicationDomain = Some("business"), condition = Some("retained-condition"))),
          operationModel = Some(ServiceOperationModel.BusinessService), entityOperationKind = Some(EntityOperationKind.Resource),
          entityApplicationDomain = Some(EntityApplicationDomain.Business), visibilityScope = Some(EntityVisibilityScope.Owner),
          relationRules = Vector(EntityAccessRelation("owner_id", "subjectId", Set("read"))),
          naturalConditions = Vector(EntityAbacCondition("title", EntityAbacCondition.Operator.Eq,
            EntityAbacCondition.Value.Literal("Before"), Set("delete"))))

        When("each active UnitOfWork evaluates the actual observed delete with UserPermission authorization")
        val ownerresult = owner.evaluateC(owner.program(grant = Some(supplied)))
        val deniedresult = nonowner.evaluateC(nonowner.program(grant = Some(nonowner.authorization())))
        val factoryresult = factorydenied.evaluateC(factorydenied.program(grant = Some(factorydenied.authorization())))
        val after = setups.map(_.snapshot())

        Then("only the owner mutates and the factory sees all original evidence except the three canonical bindings")
        ownerresult shouldBe Consequence.success(EntityDeletionResult(owner.id, EntityRevision.INITIAL))
        deniedresult shouldBe a[Consequence.Failure[?]]
        factoryresult shouldBe a[Consequence.Failure[?]]
        owner.factoryCalls shouldBe Vector(supplied.copy(targetId = Some(owner.id), collectionName = Some(owner.collectionId.name), accessKind = "delete"))
        nonowner.factoryCalls shouldBe empty
        factorydenied.factoryCalls shouldBe Vector(factorydenied.authorization())
        owner.provider.asInstanceOf[MemoryStore].deleteCalls shouldBe 1
        nonowner.provider.asInstanceOf[MemoryStore].deleteCalls shouldBe 0
        factorydenied.provider.asInstanceOf[MemoryStore].deleteCalls shouldBe 0
        after.head.root shouldBe None
        after.head.unrelatedRoot shouldBe before.head.unrelatedRoot
        after.head.unrelatedOverflow shouldBe before.head.unrelatedOverflow
        after.tail shouldBe before.tail
      }
    }

    "COD-U3 reconcile stale resident state without successful-delete View invalidation" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-U3, rules:R2,R5,R6, phase:96") {
      "return actual expected/actual facets, evict the stale resident and retain the computed View" in {
        Given("a real resident and View computed before an actual canonical intervening update")
        val setup = fixture(token = "cod_u3", representation = EntityRevisionRepresentation.Embedded)
        setup.seed()
        setup.primeResident()
        val cached = setup.view.resolve(setup.id)
        setup.updateC() shouldBe a[Consequence.Success[?]]
        val before = setup.snapshot()

        When("the active UnitOfWork attempts deletion with the superseded revision one")
        val result = setup.evaluateC(setup.program())
        val after = setup.snapshot()
        val evicted = setup.collection.resolveScoped(setup.id)(using setup.context)
        val retainedview = setup.view.resolve(setup.id)

        Then("structured stale metadata is retained, complete rows survive and View is not recomputed as a success")
        reason(result) shouldBe Some("stale-entity-revision")
        result match {
          case Consequence.Failure(conclusion) =>
            conclusion.observation.cause.descriptor.facets should contain(Descriptor.Facet.Expected(1L))
            conclusion.observation.cause.descriptor.facets should contain(Descriptor.Facet.Actual(2L))
          case _ => fail("Stale observed deletion must fail")
        }
        after shouldBe before
        symptom(evicted) shouldBe Some(Taxonomy.Symptom.NotFound)
        retainedview shouldBe cached
        setup.viewBuilds shouldBe 1
      }
    }

    "COD-U4 retain typed program and metadata-independent Local planning" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-U4, rules:R1,R4,R6, phase:96") {
      "mutate the real provider once and separately record the typed acknowledged result without persistence substitution" in {
        Given("a genuine typed ExecUowM delete program over a real canonical provider")
        val store = new MemoryStore()
        val setup = fixture(token = "cod_u4", provider = store)
        setup.seed()
        val program: ExecUowM[EntityDeletionResult] = setup.program()
        val operation = UnitOfWorkOp.EntityStoreDeleteObserved(setup.id, EntityRevision.INITIAL)

        When("the active interpreter executes persistence and the independent recorder is supplied that typed outcome")
        val result: Consequence[EntityDeletionResult] = setup.evaluateC(program)
        val persisted = setup.snapshot()
        val recorded = UnitOfWorkProgramRecorder.record(program, new UnitOfWorkProgramStubProvider {
          def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
            case _: UnitOfWorkOp.EntityStoreDeleteObserved => result.asInstanceOf[Consequence[A]]
            case _ => Consequence.operationInvalid("Unexpected operation in observed-delete recording fixture")
          }
        })
        val planned = UnitOfWorkProgramPlanner.plan(Vector(operation))
        val afterrecording = setup.snapshot()

        Then("real absence and one provider dispatch prove persistence while recording proves only the typed Local contract")
        result shouldBe Consequence.success(EntityDeletionResult(setup.id, EntityRevision.INITIAL))
        persisted.root shouldBe None
        persisted.overflow shouldBe None
        store.deleteCalls shouldBe 1
        UnitOfWorkOperationClassifier.classify(operation) shouldBe UnitOfWorkEffectClass.Local
        recorded.result shouldBe result
        recorded.occurrences.map(_.operation) shouldBe Vector(operation)
        recorded.occurrences.map(_.ordinal) shouldBe Vector(0)
        recorded.occurrences.map(_.effectClass) shouldBe Vector(UnitOfWorkEffectClass.Local)
        recorded.plan shouldBe planned
        planned.segments shouldBe Vector(UnitOfWorkPlanSegment.LocalAtomic(planned.occurrences))
        afterrecording shouldBe persisted
      }
    }

    "COD-U5 do not replay deletion of an absent or logically deleted target" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-U5, rules:R4,R5,R6, phase:96") {
      "return structured notfound while retaining tombstone or absence and the already computed View" in {
        Given("both root policies already deleted through actual active UnitOfWork programs")
        val setups = Vector(false, true).map { logical =>
          val setup = fixture(token = s"cod_u5_$logical", logical = logical)
          setup.seed()
          setup.evaluateC(setup.program()) shouldBe Consequence.success(EntityDeletionResult(setup.id, EntityRevision.INITIAL))
          val cached = setup.view.resolve(setup.id)
          (setup, logical, setup.snapshot(), cached, setup.viewBuilds)
        }

        When("a second active program requests the absent root or the current tombstone revision")
        val results = setups.map { case (setup, logical, before, cached, builds) =>
          val result = setup.evaluateC(setup.program(if (logical) revision(2L) else EntityRevision.INITIAL))
          (setup, result, setup.snapshot(), before, setup.view.resolve(setup.id), cached, builds)
        }

        Then("no second compare-delete or success invalidation occurs and durable state remains exact")
        results.foreach { case (setup, result, after, before, view, cached, builds) =>
          symptom(result) shouldBe Some(Taxonomy.Symptom.NotFound)
          reason(result) shouldBe Some("entity-mutation-target-not-found")
          after shouldBe before
          view shouldBe cached
          setup.viewBuilds shouldBe builds
          setup.provider.asInstanceOf[MemoryStore].deleteCalls shouldBe 1
        }
      }
    }

    "COD-U6 preserve appropriate resident and View state for prepublication failures" which afterWord(
      "in spec:entity-observed-delete-canonical, example:COD-U6, rules:R1,R4,R5,R6, phase:96") {
      "retain complete storage and caches after malformed request, missing capability or actual checkpoint failure" in {
        Given("real resident entities and computed Views for malformed, incapable and checkpoint-failing requests")
        val definitions = Vector(
          "malformed" -> new MemoryStore(),
          "capability" -> new MemoryStore(missingFeature = Some(EntityMutationProviderFeature.AuthoritativeDeleteResult)),
          "checkpoint" -> new MemoryStore(checkpoint = Some(EntityVersionedMutationCheckpoint.SideEffectsPrepared)))
        val setups = definitions.map { case (name, store) =>
          val setup = fixture(token = s"cod_u6_$name", provider = store)
          setup.seed()
          setup.primeResident()
          val cached = setup.view.resolve(setup.id)
          val before = setup.snapshot()
          setup.resetObservation()
          (name, setup, cached, before)
        }

        When("each actual active program fails before provider publication without invoking outer abort compensation")
        val results = setups.map { case (name, setup, cached, before) =>
          val result = setup.evaluateC(setup.program(if (name == "malformed") null else EntityRevision.INITIAL))
          val loads = setup.provider.asInstanceOf[MemoryStore].loads
          (name, setup, result, loads, setup.snapshot(), before,
            setup.collection.resolveScoped(setup.id)(using setup.context), setup.view.resolve(setup.id), cached)
        }

        Then("no failure is treated as success, appropriate actual caches survive and no global rollback claim is needed")
        results.foreach { case (name, setup, result, loads, after, before, resident, view, cached) =>
          result shouldBe a[Consequence.Failure[?]]
          after shouldBe before
          success(resident).id shouldBe setup.id
          view shouldBe cached
          setup.viewBuilds shouldBe 1
          setup.provider.asInstanceOf[MemoryStore].deleteCalls shouldBe (if (name == "checkpoint") 1 else 0)
          setup.provider.asInstanceOf[MemoryStore].genericDeletes shouldBe 0
          if (name == "malformed") loads shouldBe 0
        }
      }
    }
  }
}
