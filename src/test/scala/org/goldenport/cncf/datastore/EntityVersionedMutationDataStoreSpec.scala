package org.goldenport.cncf.datastore

import java.util.concurrent.{CountDownLatch, TimeUnit}
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityVersionedMutationDataStoreSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with Eventually {

  private val _e2_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E2, rules:R3,R5,R11-R13,R21, phase:49"
    )
  private val _e3_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E3, rules:R1,R5,R11-R13,R21, phase:49"
    )
  private val _e4_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E4, rules:R5,R11-R13,R17,R21, phase:49"
    )
  private val _e15_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E15, rules:R11-R13, phase:49"
    )

  "Entity versioned mutation datastore capability" should {
    "E3 commit a guarded root and framework side record together" must _e3_metadata {
      "when the stored revision matches the provider plan" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1,R5,R11-R13,R21; Example: E3; one revisioned root and one side-record save"
        )
        val store = DataStore.inMemorySearchable()
        given ExecutionContext = _context(store)
        val seeded = store.create(
          _root_collection,
          _root_entry,
          _root_record("before", Some(1L))
        )
        val plan = _plan(
          EntityRevision.INITIAL,
          _revision(2L),
          "after",
          Vector(
            EntityVersionedSideEffect.Save(
              _side_collection,
              _side_entry,
              Record.dataAuto("id" -> _side_entry.print, "body" -> "payload")
            )
          )
        )

        When("the supplementary provider capability executes")
        val result = seeded.flatMap(_ =>
          _provider(store).mutateVersionedEntity(plan)
        )
        val root = store.load(_root_collection, _root_entry)
        val side = store.load(_side_collection, _side_entry)

        Then("the authoritative root and side record become visible as one applied result")
        result.map {
          case EntityVersionedMutationResult.Applied(record) =>
            record.getString("name") -> record.getAny(_revision_field)
          case other =>
            fail(s"expected applied result but got $other")
        } shouldBe Consequence.success(Some("after") -> Some(2L))
        root.map(_.flatMap(_.getString("name"))) shouldBe
          Consequence.success(Some("after"))
        root.map(_.flatMap(_.getAny(_revision_field))) shouldBe
          Consequence.success(Some(2L))
        side.map(_.flatMap(_.getString("body"))) shouldBe
          Consequence.success(Some("payload"))
      }
    }

    "E4 leave root and side records unchanged on a stale expectation" must _e4_metadata {
      "when a mutation expects an obsolete revision" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R5,R11-R13,R17,R21; Example: E4; one current root and one existing side record"
        )
        val store = DataStore.inMemorySearchable()
        given ExecutionContext = _context(store)
        val setup =
          store
            .create(
              _root_collection,
              _root_entry,
              _root_record("current", Some(2L))
            )
            .flatMap(_ =>
              store.create(
                _side_collection,
                _side_entry,
                Record.dataAuto(
                  "id" -> _side_entry.print,
                  "body" -> "authoritative"
                )
              )
            )
        val plan = _plan(
          EntityRevision.INITIAL,
          _revision(2L),
          "stale-candidate",
          Vector(
            EntityVersionedSideEffect.Save(
              _side_collection,
              _side_entry,
              Record.dataAuto(
                "id" -> _side_entry.print,
                "body" -> "stale-candidate"
              )
            )
          )
        )

        When("the provider compares the expectation inside its atomic boundary")
        val result = setup.flatMap(_ =>
          _provider(store).mutateVersionedEntity(plan)
        )
        val root = store.load(_root_collection, _root_entry)
        val side = store.load(_side_collection, _side_entry)

        Then("it reports stale and publishes neither candidate")
        result shouldBe Consequence.success(
          EntityVersionedMutationResult.Stale(
            _revision(2L)
          )
        )
        root.map(_.flatMap(_.getString("name"))) shouldBe
          Consequence.success(Some("current"))
        root.map(_.flatMap(_.getAny(_revision_field))) shouldBe
          Consequence.success(Some(2L))
        side.map(_.flatMap(_.getString("body"))) shouldBe
          Consequence.success(Some("authoritative"))
      }
    }

    "E2 reject a mutation against a record without revision" must _e2_metadata {
      "when the provider reads a physically missing revision" in {
        Given(
          "Phase 50 ER-08; one persisted root without the required managed revision"
        )
        val store = DataStore.inMemorySearchable()
        given ExecutionContext = _context(store)
        val seeded = store.create(
          _root_collection,
          _root_entry,
          _root_record("legacy", None)
        )
        val plan = _plan(
          EntityRevision.INITIAL,
          _revision(2L),
          "candidate",
          Vector.empty
        )

        When("the typed provider kernel attempts the mutation")
        val result = seeded.flatMap(_ =>
          _provider(store).mutateVersionedEntity(plan)
        )
        val stored = store.load(_root_collection, _root_entry)

        Then("missing revision fails instead of becoming virtual revision zero")
        result shouldBe a[Consequence.Failure[?]]
        stored.map(_.flatMap(_.getString("name"))) shouldBe
          Consequence.success(Some("legacy"))
        stored.map(_.flatMap(_.getAny(_revision_field))) shouldBe
          Consequence.success(None)
      }
    }

    "E3 serialize ordinary access with the provider atomic boundary" must _e3_metadata {
      "when every in-memory CRUD surface is invoked while that boundary is held" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R5,R11-R13,R21; Example: E3; one in-memory provider and ordinary CRUD/search calls"
        )
        val store = DataStore.inMemorySearchable()
        given ExecutionContext = _context(store)
        store.create(
          _root_collection,
          _root_entry,
          _root_record("before", Some(1L))
        ) shouldBe Consequence.unit

        def _verify_waits_for_atomic_boundary_(
          operation: String
        )(
          body: => Any
        ): Unit = {
          val started = new CountDownLatch(1)
          val finished = new CountDownLatch(1)
          val worker = new Thread(
            () => {
              started.countDown()
              try {
                val _ = body
              } finally {
                finished.countDown()
              }
            },
            s"entity-versioned-mutation-$operation"
          )
          store.synchronized {
            worker.start()
            started.await(5L, TimeUnit.SECONDS) shouldBe true
            eventually {
              worker.getState shouldBe Thread.State.BLOCKED
            }
            finished.getCount shouldBe 1L
          }
          worker.join(5000L)
          worker.isAlive shouldBe false
          finished.getCount shouldBe 0L
        }

        When("ordinary operations contend with the datastore-owned boundary")
        _verify_waits_for_atomic_boundary_("create") {
          store.create(
            _side_collection,
            DataStore.StringEntryId("ordinary-create"),
            Record.dataAuto("name" -> "created")
          )
        }
        _verify_waits_for_atomic_boundary_("load") {
          store.load(_root_collection, _root_entry)
        }
        _verify_waits_for_atomic_boundary_("save") {
          store.save(
            _root_collection,
            _root_entry,
            _root_record("saved", Some(1L))
          )
        }
        _verify_waits_for_atomic_boundary_("update") {
          store.update(
            _root_collection,
            _root_entry,
            Record.dataAuto("name" -> "updated")
          )
        }
        _verify_waits_for_atomic_boundary_("delete") {
          store.delete(
            _side_collection,
            DataStore.StringEntryId("ordinary-create")
          )
        }
        _verify_waits_for_atomic_boundary_("search") {
          store.search(
            _root_collection,
            QueryDirective(Query.Empty)
          )
        }

        Then("none can observe or modify a partially published root/side state")
        store
          .load(_root_collection, _root_entry)
          .map(_.flatMap(_.getString("name"))) shouldBe
          Consequence.success(Some("updated"))
      }
    }

    "E15 reject unsupported and cross-provider plans before mutation" must _e15_metadata {
      "when capability or transaction-domain admission fails" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R11-R13; Example: E15; one unsupported store and two collection-scoped stores"
        )
        val unsupportedspace =
          new DataStoreSpace().useDataStore(DataStore.noop())
        given ExecutionContext = _context(DataStore.noop())
        val unsupported = unsupportedspace.mutateVersionedEntity(
          _plan(
            EntityRevision.INITIAL,
            _revision(2L),
            "candidate",
            Vector.empty
          )
        )

        val rootstore =
          new ScopedInMemoryDataStore(_root_collection.collectionName)
        val sidestore =
          new ScopedInMemoryDataStore(_side_collection.collectionName)
        val splitcontext = ExecutionContext.create()
        val splitspace =
          splitcontext.dataStoreSpace
            .useDataStore(rootstore)
            .addDataStore(sidestore)
        val splitsetup = {
          given ExecutionContext = splitcontext
          rootstore.create(
            _root_collection,
            _root_entry,
            _root_record("before", Some(1L))
          )
        }

        When("DataStoreSpace resolves the supplementary capability")
        val split = {
          given ExecutionContext = splitcontext
          splitsetup.flatMap(_ =>
            splitspace.mutateVersionedEntity(
              _plan(
                EntityRevision.INITIAL,
                _revision(2L),
                "candidate",
                Vector(
                  EntityVersionedSideEffect.Save(
                    _side_collection,
                    _side_entry,
                    Record.dataAuto("body" -> "candidate")
                  )
                )
              )
            )
          )
        }
        val root = {
          given ExecutionContext = splitcontext
          rootstore.load(_root_collection, _root_entry)
        }

        Then("both requests fail deterministically and the admitted root remains unchanged")
        unsupported shouldBe a[Consequence.Failure[?]]
        split shouldBe a[Consequence.Failure[?]]
        root.map(_.flatMap(_.getString("name"))) shouldBe
          Consequence.success(Some("before"))
        root.map(_.flatMap(_.getAny(_revision_field))) shouldBe
          Consequence.success(Some(1L))
      }
    }
  }

  private val _root_collection =
    DataStore.CollectionId("entity_versioned_root")
  private val _side_collection =
    DataStore.CollectionId("entity_versioned_side")
  private val _root_entry =
    DataStore.StringEntryId("root-1")
  private val _side_entry =
    DataStore.StringEntryId("side-1")
  private val _revision_field = "cncf_revision"

  private def _plan(
    expectedrevision: EntityRevision,
    nextrevision: EntityRevision,
    name: String,
    effects: Vector[EntityVersionedSideEffect]
  ): EntityVersionedMutationPlan =
    EntityVersionedMutationPlan(
      collection = _root_collection,
      entryId = _root_entry,
      revisionField = _revision_field,
      expectedRevision = expectedrevision,
      nextRevision = nextrevision,
      rootMutation = EntityVersionedRootMutation.Replace(
        Record.dataAuto(
          "id" -> _root_entry.print,
          "name" -> name
        )
      ),
      sideEffects = effects
    )

  private def _revision(
    value: Long
  ): EntityRevision =
    EntityRevision
      .createC(value)
      .toOption
      .getOrElse(fail(s"valid EntityRevision expected: $value"))

  private def _root_record(
    name: String,
    revision: Option[Long]
  ): Record =
    revision
      .map(value =>
        Record.dataAuto(
          "id" -> _root_entry.print,
          "name" -> name,
          _revision_field -> value
        )
      )
      .getOrElse(
        Record.dataAuto(
          "id" -> _root_entry.print,
          "name" -> name
        )
      )

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

  private final class ScopedInMemoryDataStore(
    collectionname: String
  ) extends DataStore.InMemoryDataStore(
        org.goldenport.cncf.unitofwork.CommitRecorder.noop
      ) {
    override def isAccept(
      collection: DataStore.CollectionId
    ): Boolean =
      collection.collectionName == collectionname
  }
}
