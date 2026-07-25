package org.goldenport.cncf.entity

import java.util.concurrent.{
  ConcurrentLinkedQueue,
  CountDownLatch,
  TimeUnit
}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{
  DataStore,
  EntityVersionedMutationDataStore,
  EntityVersionedMutationPlan,
  EntityVersionedMutationResult,
  EntityVersionedRootMutation
}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityWritePolicySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Entity write policy" should {
    "advance AlwaysWrite while preserving authoritative metadata on a WriteIfChanged no-op" in {
      Given(
        "Phase 50 ER-05; equal normalized business state with different candidate managed metadata"
      )
      val store = DataStore.inMemorySearchable()
      given ExecutionContext = _context(store)
      val alwaysentry = DataStore.StringEntryId("always")
      val noopentry = DataStore.StringEntryId("noop")
      val setup =
        store
          .create(
            _collection,
            alwaysentry,
            _record(alwaysentry, "same", 1L, "authoritative")
          )
          .flatMap(_ =>
            store.create(
              _collection,
              noopentry,
              _record(noopentry, "same", 7L, "authoritative")
            )
          )

      When("AlwaysWrite receives equal business state and WriteIfChanged receives only metadata differences")
      val always = setup.flatMap(_ =>
        _provider(store).mutateVersionedEntity(
          _plan(
            alwaysentry,
            "same",
            "candidate",
            EntityWritePolicy.AlwaysWrite,
            _revision(1L)
          )
        )
      )
      val noop = setup.flatMap(_ =>
        _provider(store).mutateVersionedEntity(
          _plan(
            noopentry,
            "same",
            "candidate",
            EntityWritePolicy.WriteIfChanged,
            _revision(7L)
          )
        )
      )

      Then("AlwaysWrite advances and WriteIfChanged returns the unchanged authoritative record")
      always.map(_revision_of) shouldBe Consequence.success(2L)
      noop shouldBe Consequence.success(
        EntityVersionedMutationResult.NoOp(
          _record(noopentry, "same", 7L, "authoritative")
        )
      )
      store
        .load(_collection, noopentry)
        .map(_.flatMap(_.getString(_updated_field))) shouldBe
        Consequence.success(Some("authoritative"))
      store
        .load(_collection, noopentry)
        .map(_.flatMap(_.getAny(_revision_field))) shouldBe
        Consequence.success(Some(7L))
    }

    "converge simultaneous identical writes to one advancement and authoritative no-op successes" in {
      Given(
        "Phase 50 ER-05; generated contender counts requesting one identical normalized state"
      )
      val property = Prop.forAll(Gen.chooseNum(2, 8)) { count =>
        val store = DataStore.inMemorySearchable()
        given ExecutionContext = _context(store)
        val entry = DataStore.StringEntryId(s"identical-$count")
        val setup = store.create(
          _collection,
          entry,
          _record(entry, "before", 1L, "before")
        )
        val ready = new CountDownLatch(count)
        val start = new CountDownLatch(1)
        val done = new CountDownLatch(count)
        val results =
          new ConcurrentLinkedQueue[
            Consequence[EntityVersionedMutationResult]
          ]()
        val workers = (1 to count).map { number =>
          new Thread(
            () => {
              ready.countDown()
              try {
                if (start.await(5L, TimeUnit.SECONDS))
                  results.add(
                    setup.flatMap(_ =>
                      _provider(store).mutateVersionedEntity(
                        _plan(
                          entry,
                          "desired",
                          s"candidate-$number",
                          EntityWritePolicy.WriteIfChanged,
                          EntityRevision.INITIAL
                        )
                      )
                    )
                  )
              } finally {
                done.countDown()
              }
            },
            s"entity-write-policy-$count-$number"
          )
        }
        workers.foreach(_.start())
        val prepared = ready.await(5L, TimeUnit.SECONDS)
        start.countDown()
        val completed = done.await(10L, TimeUnit.SECONDS)
        workers.foreach(_.join(5000L))
        val outcomes =
          results.toArray.toVector.map(
            _.asInstanceOf[Consequence[EntityVersionedMutationResult]]
          )
        val applied = outcomes.count(
          _.toOption.exists(
            _.isInstanceOf[EntityVersionedMutationResult.Applied]
          )
        )
        val noops = outcomes.count(
          _.toOption.exists(
            _.isInstanceOf[EntityVersionedMutationResult.NoOp]
          )
        )
        val stored = store.load(_collection, entry).toOption.flatten
        prepared &&
        completed &&
        outcomes.size == count &&
        applied == 1 &&
        noops == count - 1 &&
        stored.flatMap(_.getString("name")).contains("desired") &&
        stored.flatMap(_.getAny(_revision_field)).contains(2L)
      }

      When("the property runner schedules every contender set")
      val checked = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(20),
        property
      )

      Then("the provider performs one write and resolves later equal attempts as no-ops")
      checked.passed shouldBe true
    }

    "parse the canonical write-policy vocabulary" in {
      Given("Phase 50 ER-05; write-policy text at a metadata boundary")

      When("known and unknown values are parsed")
      val always = EntityWritePolicy.parseC("always-write")
      val changed = EntityWritePolicy.parseC("write_if_changed")
      val invalid = EntityWritePolicy.parseC("deduplicate")

      Then("AlwaysWrite remains default and unknown policy fails structurally")
      EntityWritePolicy.default shouldBe EntityWritePolicy.AlwaysWrite
      always.toOption shouldBe Some(EntityWritePolicy.AlwaysWrite)
      changed.toOption shouldBe Some(EntityWritePolicy.WriteIfChanged)
      invalid.toOption shouldBe None
    }
  }

  private val _collection =
    DataStore.CollectionId("entity_write_policy")
  private val _revision_field = "revision"
  private val _updated_field = "updated_at"

  private def _plan(
    entry: DataStore.EntryId,
    name: String,
    updatedat: String,
    policy: EntityWritePolicy,
    expectedrevision: EntityRevision
  ): EntityVersionedMutationPlan =
    EntityVersionedMutationPlan(
      collection = _collection,
      entryId = entry,
      revisionField = _revision_field,
      concurrencyPolicy = EntityConcurrencyPolicy.Optimistic,
      writePolicy = policy,
      preconditionPolicy = RevisionPreconditionPolicy.Managed,
      expectedRevision = Some(expectedrevision),
      rootMutation = EntityVersionedRootMutation.Replace(
        Record.dataAuto(
          "id" -> entry.print,
          "name" -> name,
          _updated_field -> updatedat
        )
      ),
      comparisonExcludedFields = Set(_updated_field)
    )

  private def _record(
    entry: DataStore.EntryId,
    name: String,
    revision: Long,
    updatedat: String
  ): Record =
    Record.dataAuto(
      "id" -> entry.print,
      "name" -> name,
      _revision_field -> revision,
      _updated_field -> updatedat
    )

  private def _revision(
    value: Long
  ): EntityRevision =
    EntityRevision
      .createC(value)
      .toOption
      .getOrElse(fail(s"valid EntityRevision expected: $value"))

  private def _revision_of(
    result: EntityVersionedMutationResult
  ): Long =
    result match {
      case EntityVersionedMutationResult.Applied(record) =>
        record
          .getAny(_revision_field)
          .collect { case value: Long => value }
          .getOrElse(fail("applied revision expected"))
      case other =>
        fail(s"applied result expected: $other")
    }

  private def _provider(
    store: DataStore
  ): EntityVersionedMutationDataStore =
    store.asInstanceOf[EntityVersionedMutationDataStore]

  private def _context(
    store: DataStore
  ): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(store)
    context
  }
}
