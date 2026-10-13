package org.goldenport.cncf.datastore

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
final class EntityObservedDeleteProviderSpec extends AnyWordSpec with Matchers with GivenWhenThen with Eventually {
  import EntityObservedDeleteFixture.*

  "Observed Entity deletion" should {
    "publish the matched provider revision" which {
      "OD-E1 remove the physical root and selected overflow while preserving unrelated data" in {
        Given("a real memory provider with a revisioned root, its overflow and an unrelated root")
        val store = new MemoryStore()
        val ids = collections("e1")
        given ExecutionContext = context(store)
        seed(store, ids) shouldBe Consequence.unit
        val before = snapshot(store, ids)

        When("the selected provider atomically deletes the current observed root and overflow")
        val result = summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(plan(ids))
        val after = snapshot(store, ids)

        Then("the result retains revision one and only selected records disappear")
        result shouldBe Consequence.success(EntityCompareAndDeleteResult.Deleted(EntityRevision.INITIAL))
        after.root shouldBe Consequence.success(None)
        after.side shouldBe Consequence.success(None)
        after.unrelated shouldBe before.unrelated
        store.checkpointCount shouldBe 3
      }

      "OD-E2 patch logical lifecycle once and retain the matched prior revision and overflow" in {
        Given("a real root at revision seven with business fields and retained overflow")
        val store = new MemoryStore()
        val ids = collections("e2")
        given ExecutionContext = context(store)
        seed(store, ids, rootRecord(7L)) shouldBe Consequence.unit
        val before = snapshot(store, ids)
        val request = plan(ids, revision(7L), EntityDeleteRootMutation.Logical(logicalChanges), false)

        When("the provider applies the server-owned logical lifecycle patch")
        val result = summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(request)
        val after = snapshot(store, ids)

        Then("stored revision is eight, the receipt is seven and all business and overflow values survive")
        result shouldBe Consequence.success(EntityCompareAndDeleteResult.Deleted(revision(7L)))
        val row = success(after.root).get
        row.getAny(revisionField) shouldBe Some(8L)
        row.getString("aliveness") shouldBe Some("dead")
        row.getString("deleted_at") shouldBe logicalChanges.getString("deleted_at")
        row.getString("updated_at") shouldBe logicalChanges.getString("updated_at")
        Vector("title", "business", "overflow_ref").foreach(key =>
          row.getAny(key) shouldBe success(before.root).get.getAny(key)
        )
        after.side shouldBe before.side
        after.unrelated shouldBe before.unrelated
      }

      "OD-E3 honor an intervening real compare-and-set before stale observed deletion" in {
        Given("an observed revision one and an intervening canonical provider CAS")
        val store = new MemoryStore()
        val ids = collections("e3")
        given ExecutionContext = context(store)
        seed(store, ids) shouldBe Consequence.unit
        summon[ExecutionContext].dataStoreSpace.compareAndSetEntity(
          EntityCompareAndSetMutationPlan(ids.root, rootEntry, revisionField, EntityRevision.INITIAL, Record.dataAuto("title" -> "concurrent"))
        ) shouldBe a[Consequence.Success[?]]
        val before = snapshot(store, ids)

        When("physical deletion supplies the stale original observation")
        val result = summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(plan(ids))
        val after = snapshot(store, ids)

        Then("the actual provider revision is returned as stale with no root or overflow effect")
        result shouldBe Consequence.success(EntityCompareAndDeleteResult.Stale(EntityRevision.INITIAL, revision(2L)))
        after shouldBe before
        store.checkpointCount shouldBe 0
      }
    }

    "reject inadmissible deletion before publishing" which {
      "OD-E4 preserve missing invalid and exhausted managed revisions for both root policies" in {
        Given("independent real roots with absent, zero, negative, fractional, textual and exhausted revisions")
        val revisions = Vector[Any](0L, -1L, 1.5d, "invalid", Long.MaxValue)
        val missing = Record(rootRecord().fields.filterNot(_.key == revisionField))
        val roots = missing +: revisions.map(rootRecord(_))
        val policies = Vector[EntityDeleteRootMutation](EntityDeleteRootMutation.Physical, EntityDeleteRootMutation.Logical(logicalChanges))
        val scenarios = for {
          (row, index) <- roots.zipWithIndex
          (policy, policyindex) <- policies.zipWithIndex
        } yield {
          val store = new MemoryStore()
          val ids = collections(s"e4_revision_${index}_$policyindex")
          val ctx = context(store)
          seed(store, ids, row)(using ctx) shouldBe Consequence.unit
          val before = snapshot(store, ids)(using ctx)
          val expected = if (index == roots.size - 1) revision(Long.MaxValue) else EntityRevision.INITIAL
          (store, ids, ctx, before, plan(ids, expected, policy, policy == EntityDeleteRootMutation.Physical))
        }

        When("each current deletion attempts revision admission")
        val outcomes = scenarios.map { case (store, ids, ctx, before, request) =>
          (store.compareAndDeleteEntity(request)(using ctx), snapshot(store, ids)(using ctx), before, store.checkpointCount)
        }

        Then("every failed admission preserves all records and reaches no publication checkpoint")
        outcomes.foreach { case (result, after, before, checkpoints) =>
          result shouldBe a[Consequence.Failure[?]]
          after shouldBe before
          checkpoints shouldBe 0
        }
      }

      "OD-E4 distinguish absent and already hidden or logically deleted roots from stale" in {
        Given("real memory providers containing an absent target, dead lifecycle or a deletion timestamp")
        val rows = Vector(
          None,
          Some(rootRecord().upsertSingle("aliveness", "dead")),
          Some(rootRecord().upsertSingle("deleted_at", "2026-10-05T01:00:00Z"))
        )
        val scenarios = rows.zipWithIndex.map { case (row, index) =>
          val store = new MemoryStore()
          val ids = collections(s"e4_hidden_$index")
          val ctx = context(store)
          seed(store, ids)(using ctx) shouldBe Consequence.unit
          row match {
            case Some(value) => store.save(ids.root, rootEntry, value)(using ctx) shouldBe Consequence.unit
            case None => store.delete(ids.root, rootEntry)(using ctx) shouldBe Consequence.unit
          }
          val before = snapshot(store, ids)(using ctx)
          if (index == 1) {
            success(before.root).map(_.getString("aliveness")) shouldBe Some(Some("dead"))
            success(before.root).map(_.fields.count(_.key == "aliveness")) shouldBe Some(1)
          } else if (index == 2) {
            success(before.root).map(_.getString("deleted_at")) shouldBe Some(Some("2026-10-05T01:00:00Z"))
            success(before.root).map(_.fields.count(_.key == "deleted_at")) shouldBe Some(1)
          }
          (store, ids, ctx, before)
        }

        When("observed deletion requests those excluded targets")
        val outcomes = scenarios.map { case (store, ids, ctx, before) =>
          (store.compareAndDeleteEntity(plan(ids))(using ctx), snapshot(store, ids)(using ctx), before)
        }

        Then("the structural not-found classification leaves every remaining record intact")
        outcomes.foreach { case (result, after, before) =>
          _symptom(result) shouldBe Some(Taxonomy.Symptom.NotFound)
          after shouldBe before
        }
      }

      "OD-E4 reject the complete malformed nested plan and target matrix without writes" in {
        Given("one real provider and all frozen null, managed-field, duplicate and bounded-effect violations")
        val store = new MemoryStore()
        val ids = collections("e4_shape")
        given ExecutionContext = context(store)
        seed(store, ids) shouldBe Consequence.unit
        val before = snapshot(store, ids)
        val requests = invalidPlans(ids)

        When("each malformed plan reaches both DataStoreSpace and the direct provider boundary")
        val constructorresult = Consequence(DataStore.CollectionId.Instance(null))
        val results = requests.map { case (name, request) =>
          (name, summon[ExecutionContext].dataStoreSpace.compareAndDeleteEntity(request), store.compareAndDeleteEntity(request))
        }
        val after = snapshot(store, ids)

        Then("both entry points fail structurally and publish no root or effect changes")
        constructorresult shouldBe a[Consequence.Failure[?]]
        results.foreach { case (name, routed, direct) =>
          withClue(name) {
            routed shouldBe a[Consequence.Failure[?]]
            direct shouldBe a[Consequence.Failure[?]]
          }
        }
        after shouldBe before
        store.checkpointCount shouldBe 0
      }

      "OD-E5 admit exactly the new capabilities and preserve legacy defaults" in {
        Given("a legacy provider, a noncapable store and providers each missing one required deletion feature")
        val ids = collections("e5_capability")
        val legacy = new LegacyProvider
        val noop = DataStore.noop()
        val profiles = deleteFeatures.toVector.map(feature =>
          new RecordingProvider(EntityMutationProviderCapabilities(deleteFeatures - feature),
            Consequence.success(EntityCompareAndDeleteResult.Deleted(EntityRevision.INITIAL)))
        )
        val actual = new MemoryStore()

        When("each provider receives the bounded physical-plus-effect request")
        val legacyresult = context(legacy).dataStoreSpace.compareAndDeleteEntity(plan(ids))(using context(legacy))
        val defaultresult = legacy.compareAndDeleteEntity(plan(ids))(using context(legacy))
        val noopresult = context(noop).dataStoreSpace.compareAndDeleteEntity(plan(ids))(using context(noop))
        val results = profiles.map(store => context(store).dataStoreSpace.compareAndDeleteEntity(plan(ids))(using context(store)))

        Then("unsupported capability failures precede provider or generic writes and real providers advertise both features")
        Vector(legacyresult, defaultresult, noopresult).foreach(result => _reasons(result) should contain("unsupported-capability"))
        results.foreach(result => _reasons(result) should contain("unsupported-capability"))
        all(profiles.map(_.invocationCount)) shouldBe 0
        all(profiles.map(_.genericCount)) shouldBe 0
        legacy.genericCount shouldBe 0
        legacy.entityMutationProviderCapabilities shouldBe EntityMutationProviderCapabilities.guardedBaseline
        EntityMutationProviderCapabilities.guardedBaseline.features should not contain EntityMutationProviderFeature.OptimisticCompareAndDelete
        EntityMutationProviderCapabilities.guardedBaseline.features should not contain EntityMutationProviderFeature.AuthoritativeDeleteResult
        actual.entityMutationProviderCapabilities.supportsAll(deleteFeatures) shouldBe true
      }

      "OD-E5 reject invalid authoritative results without reloads or fabricated receipts" in {
        Given("recording providers used only to expose invalid result and result-container shapes")
        val ids = collections("e5_result")
        val values: Vector[EntityCompareAndDeleteResult] = Vector(
          null,
          EntityCompareAndDeleteResult.Deleted(null),
          EntityCompareAndDeleteResult.Deleted(revision(2L)),
          EntityCompareAndDeleteResult.Stale(null, revision(2L)),
          EntityCompareAndDeleteResult.Stale(EntityRevision.INITIAL, null),
          EntityCompareAndDeleteResult.Stale(EntityRevision.INITIAL, EntityRevision.INITIAL),
          EntityCompareAndDeleteResult.Stale(revision(2L), revision(3L))
        )
        val stores = values.map(value => new RecordingProvider(EntityMutationProviderCapabilities(deleteFeatures), Consequence.success(value))) :+
          new RecordingProvider(EntityMutationProviderCapabilities(deleteFeatures), null)

        When("DataStoreSpace admits each provider response once")
        val results = stores.map(store => context(store).dataStoreSpace.compareAndDeleteEntity(plan(ids))(using context(store)))

        Then("every invalid response fails without generic reads or writes")
        results.foreach(result => _reasons(result) should contain("invalid-authoritative-delete-result"))
        all(stores.map(_.invocationCount)) shouldBe 1
        all(stores.map(_.genericCount)) shouldBe 0
      }

      "OD-E5 preserve provider failures and reject shapes before routing to capable providers" in {
        Given("a capable recording provider retaining a structured failure and one malformed request")
        val ids = collections("e5_failure")
        val failure = Consequence.securityPermissionDenied("Observed deletion denied")
        val store = new RecordingProvider(EntityMutationProviderCapabilities(deleteFeatures), failure)
        val ctx = context(store)

        When("shape admission rejects one plan and one well-shaped plan returns the original provider failure")
        val malformed = ctx.dataStoreSpace.compareAndDeleteEntity(plan(ids).copy(expectedRevision = null))(using ctx)
        val result = ctx.dataStoreSpace.compareAndDeleteEntity(plan(ids))(using ctx)

        Then("only the valid plan invokes the provider and the failure taxonomy and cause survive")
        malformed shouldBe a[Consequence.Failure[?]]
        result shouldBe failure
        store.invocationCount shouldBe 1
        store.genericCount shouldBe 0
      }

      "OD-E5 resolve every deletion effect to the selected real provider domain before writing" in {
        Given("real collection-scoped providers with the root and overflow in different native domains")
        val ids = collections("e5_domain")
        val rootstore = new MemoryStore(acceptedCollection = Some(ids.root))
        val sidestore = new MemoryStore(acceptedCollection = Some(ids.side))
        val ctx = context(rootstore)
        ctx.dataStoreSpace.addDataStore(sidestore)
        rootstore.create(ids.root, rootEntry, rootRecord())(using ctx) shouldBe Consequence.unit
        sidestore.create(ids.side, sideEntry, Record.dataAuto("body" -> "retained"))(using ctx) shouldBe Consequence.unit
        val rootbefore = rootstore.load(ids.root, rootEntry)(using ctx)
        val sidebefore = sidestore.load(ids.side, sideEntry)(using ctx)

        When("a bounded physical deletion crosses those provider domains")
        val result = ctx.dataStoreSpace.compareAndDeleteEntity(plan(ids))(using ctx)
        val rootafter = rootstore.load(ids.root, rootEntry)(using ctx)
        val sideafter = sidestore.load(ids.side, sideEntry)(using ctx)

        Then("provider-domain admission rejects the plan before any deletion checkpoint")
        _reasons(result) should contain("provider-domain-mismatch")
        rootafter shouldBe rootbefore
        sideafter shouldBe sidebefore
        rootstore.checkpointCount shouldBe 0
        sidestore.checkpointCount shouldBe 0
      }
    }

    "retain provider-wide atomic publication" which {
      "OD-E6 publish neither physical nor logical state at every failing native checkpoint" in {
        Given("real memory provider failures at RootPrepared, SideEffectsPrepared and BeforePublish for both policies")
        val scenarios = for {
          checkpoint <- EntityVersionedMutationCheckpoint.values.toVector
          (policy, index) <- Vector[EntityDeleteRootMutation](EntityDeleteRootMutation.Physical, EntityDeleteRootMutation.Logical(logicalChanges)).zipWithIndex
        } yield {
          val store = new MemoryStore(Some(checkpoint))
          val ids = collections(s"e6_${checkpoint}_$index")
          val ctx = context(store)
          seed(store, ids)(using ctx) shouldBe Consequence.unit
          (store, ids, ctx, snapshot(store, ids)(using ctx), plan(ids, rootMutation = policy, includeSide = index == 0))
        }

        When("each provider stages deletion and terminates at its native checkpoint")
        val outcomes = scenarios.map { case (store, ids, ctx, before, request) =>
          (store.compareAndDeleteEntity(request)(using ctx), snapshot(store, ids)(using ctx), before)
        }

        Then("the exact complete root, overflow and unrelated records remain unchanged")
        outcomes.foreach { case (result, after, before) =>
          _reasons(result) should contain("provider-failure")
          after shouldBe before
        }
      }

      "OD-E7 accept one genuine concurrent observed caller for physical and logical deletion" in {
        Given("four independent callers of one real memory provider and one observed revision")
        val scenarios = Vector(false, true).map { logical =>
          val store = new MemoryStore()
          val ids = collections(s"e7_race_$logical")
          val ctx = context(store)
          seed(store, ids)(using ctx) shouldBe Consequence.unit
          val policy = if (logical) EntityDeleteRootMutation.Logical(logicalChanges) else EntityDeleteRootMutation.Physical
          val request = plan(ids, rootMutation = policy, includeSide = !logical).copy(exclusionGuards = Vector.empty)
          (store, ids, ctx, logical, request)
        }

        When("bounded simultaneous callers use the provider's own atomic boundary")
        val outcomes = scenarios.map { case (store, ids, ctx, logical, request) =>
          (race(Vector.fill(4)(store), request), snapshot(store, ids)(using ctx), logical)
        }

        Then("one caller receives the matched revision and each loser observes stale or physical absence")
        outcomes.foreach { case (evidence, after, logical) =>
          evidence.ready shouldBe true
          evidence.terminated shouldBe true
          evidence.results should have size 4
          evidence.results.count(_.toOption.contains(EntityCompareAndDeleteResult.Deleted(EntityRevision.INITIAL))) shouldBe 1
          if (logical) {
            evidence.results.count(_.toOption.contains(EntityCompareAndDeleteResult.Stale(EntityRevision.INITIAL, revision(2L)))) shouldBe 3
            success(after.root).get.getAny(revisionField) shouldBe Some(2L)
            success(after.side).isDefined shouldBe true
          } else {
            evidence.results.count(_symptom(_) == Some(Taxonomy.Symptom.NotFound)) shouldBe 3
            after.root shouldBe Consequence.success(None)
            after.side shouldBe Consequence.success(None)
          }
        }
      }

      "OD-E7 hold ordinary CRUD and search behind an actual deletion publication checkpoint" in {
        Given("an actual paused observed deletion and six ordinary operations on that provider")
        val entered = new CountDownLatch(1)
        val release = new CountDownLatch(1)
        val started = new CountDownLatch(6)
        val finished = new CountDownLatch(6)
        val store = new PausingMemoryStore(entered, release)
        val ids = collections("e7_monitor")
        given ExecutionContext = context(store)
        seed(store, ids) shouldBe Consequence.unit
        val outcomes = new ConcurrentLinkedQueue[Consequence[?]]()
        val deleted = new ConcurrentLinkedQueue[Consequence[EntityCompareAndDeleteResult]]()
        val actions: Vector[() => Consequence[?]] = Vector(
          () => store.create(ids.unrelated, DataStore.StringEntryId("created"), Record.dataAuto("title" -> "created")),
          () => store.load(ids.root, rootEntry),
          () => store.save(ids.unrelated, DataStore.StringEntryId("saved"), Record.dataAuto("title" -> "saved")),
          () => store.update(ids.unrelated, unrelatedEntry, Record.dataAuto("title" -> "updated")),
          () => store.delete(ids.unrelated, DataStore.StringEntryId("absent")),
          () => store.search(ids.root, QueryDirective(Query.Empty))
        )
        val workers = actions.zipWithIndex.map { case (action, index) => new Thread(() => {
          started.countDown()
          try { outcomes.add(action()); () } finally finished.countDown()
        }, s"observed-delete-crud-$index") }
        val deletion = new Thread(() => { deleted.add(store.compareAndDeleteEntity(plan(ids))); () }, "observed-delete-publisher")

        When("deletion reaches RootPrepared and ordinary calls contend before provider publication")
        var blocked = false
        try {
          deletion.start()
          entered.await(5L, TimeUnit.SECONDS) shouldBe true
          workers.foreach(_.start())
          started.await(5L, TimeUnit.SECONDS) shouldBe true
          eventually { all(workers.map(_.getState)) shouldBe Thread.State.BLOCKED }
          blocked = finished.getCount == 6L
        } finally {
          release.countDown()
          deletion.join(5000L)
          workers.foreach(_.join(5000L))
        }
        val after = snapshot(store, ids)

        Then("ordinary operations can only complete after the atomic publication releases the provider monitor")
        blocked shouldBe true
        deletion.isAlive shouldBe false
        all(workers.map(_.isAlive)) shouldBe false
        finished.getCount shouldBe 0L
        outcomes.size shouldBe 6
        outcomes.toArray.toVector.foreach(value => value.asInstanceOf[Consequence[?]].isSuccess shouldBe true)
        deleted.peek() shouldBe Consequence.success(EntityCompareAndDeleteResult.Deleted(EntityRevision.INITIAL))
        after.root shouldBe Consequence.success(None)
        after.side shouldBe Consequence.success(None)
      }
    }
  }

  private def _symptom(result: Consequence[?]): Option[Taxonomy.Symptom] =
    result match {
      case Consequence.Failure(conclusion) => Some(conclusion.observation.taxonomy.symptom)
      case _ => None
    }

  private def _reasons(result: Consequence[?]): Set[String] =
    result match {
      case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
        case Descriptor.Facet.Reason(name) => name
      }.toSet
      case _ => Set.empty
    }
}
