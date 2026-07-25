package org.goldenport.cncf.datastore

import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class InMemoryConditionalTransitionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with Eventually {

  private val _transition_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, examples:E5,E6,E8-E11, rules:R8-R13,R21, phase:49"
    )
  private val _concurrency_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E6, rules:R1,R8-R13,R21, phase:49"
    )

  "In-memory conditional transition" should {
    "commit create and bind successors as authoritative compound results" must _transition_metadata {
      "when root guards and successor revisions match" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R8-R13,R21; create and bind plans"
        )
        val createstore = _store()
        val createcontext = _context(createstore)
        val createplan = _create_plan(
          _successor_entry,
          _side_entry
        )
        val createresult = {
          given ExecutionContext = createcontext
          createstore
            .create(
              _root_collection,
              _root_entry,
              _root_record("open", 1L, None)
            )
            .flatMap(_ => createstore.conditionalTransition(createplan))
        }

        val bindstore = _store()
        val bindcontext = _context(bindstore)
        val bindresult = {
          given ExecutionContext = bindcontext
          val setup =
            bindstore
              .create(
                _root_collection,
                _root_entry,
                _root_record("open", 1L, None)
              )
              .flatMap(_ =>
                bindstore.create(
                  _successor_collection,
                  _successor_entry,
                  _successor_record(_successor_entry, 3L)
                )
              )
          setup.flatMap(_ =>
            bindstore.conditionalTransition(
              _bind_plan(_successor_entry, 3L)
            )
          )
        }

        When("the provider publishes each prepared immutable state")
        val createdroot = {
          given ExecutionContext = createcontext
          createstore.load(_root_collection, _root_entry)
        }
        val createdsuccessor = {
          given ExecutionContext = createcontext
          createstore.load(_successor_collection, _successor_entry)
        }
        val createdside = {
          given ExecutionContext = createcontext
          createstore.load(_side_collection, _side_entry)
        }
        val boundroot = {
          given ExecutionContext = bindcontext
          bindstore.load(_root_collection, _root_entry)
        }
        val boundsuccessor = {
          given ExecutionContext = bindcontext
          bindstore.load(_successor_collection, _successor_entry)
        }
        val createtransition =
          createresult.toOption.collect {
            case value: DataStoreConditionalTransitionResult.Transitioned =>
              value
          }
        val bindtransition =
          bindresult.toOption.collect {
            case value: DataStoreConditionalTransitionResult.Transitioned =>
              value
          }

        Then("both intents return authoritative records and create publishes all compound records")
        createtransition.map(_.rootRecord) shouldBe
          createdroot.toOption.flatten
        createtransition.map(_.successorRecord) shouldBe
          createdsuccessor.toOption.flatten
        bindtransition.map(_.rootRecord) shouldBe
          boundroot.toOption.flatten
        bindtransition.map(_.successorRecord) shouldBe
          boundsuccessor.toOption.flatten
        createdroot.toOption.flatten
          .flatMap(_.getAny(_revision_field)) shouldBe Some(2L)
        createdroot.toOption.flatten
          .flatMap(_.getString("successor_id")) shouldBe
          Some(_successor_entry.print)
        createdsuccessor.toOption.flatten
          .flatMap(_.getAny(_revision_field)) shouldBe Some(1L)
        createdside.toOption.flatten
          .flatMap(_.getString("event")) shouldBe Some("transitioned")
      }
    }

    "return NotMatched without publishing any candidate record" must _transition_metadata {
      "when one exact root expectation differs" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R7-R10,R13,R21; one authoritative open root"
        )
        val store = _store()
        given ExecutionContext = _context(store)
        store.create(
          _root_collection,
          _root_entry,
          _root_record("closed", 1L, None)
        ) shouldBe Consequence.unit

        When("a plan expects a different exact status")
        val result =
          store.conditionalTransition(
            _create_plan(_successor_entry, _side_entry)
          )

        Then("the authoritative root is returned and successor/side records remain absent")
        result.toOption shouldBe Some(
          DataStoreConditionalTransitionResult.NotMatched(
            _root_record("closed", 1L, None)
          )
        )
        store.load(_successor_collection, _successor_entry) shouldBe
          Consequence.success(None)
        store.load(_side_collection, _side_entry) shouldBe
          Consequence.success(None)
      }
    }

    "preserve incompatible authoritative storage as a conversion failure" must _transition_metadata {
      "when the stored field kind cannot be compared with its admitted exact value" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R7,R10,R12,R19,R21; one root with an incompatible stored field kind"
        )
        val store = _store()
        given ExecutionContext = _context(store)
        val root =
          Record.dataAuto(
            "id" -> _root_entry.print,
            "status" -> 1,
            _revision_field -> 1L
          )
        store.create(_root_collection, _root_entry, root) shouldBe
          Consequence.unit

        When("the provider compares an admitted text expectation")
        val result =
          store.conditionalTransition(
            _create_plan(_successor_entry, _side_entry)
          )

        Then("it returns a structured failure rather than NotMatched and publishes nothing")
        _is_failure(result) shouldBe true
        store.load(_root_collection, _root_entry) shouldBe
          Consequence.success(Some(root))
        store.load(_successor_collection, _successor_entry) shouldBe
          Consequence.success(None)
      }
    }

    "preserve structured successor failures without changing the root" must _transition_metadata {
      "when create collides or bind target identity is missing or stale" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R8,R10,R13,R19,R21; independent successor failure scenarios"
        )
        def _scenario_(
          setup: (
            DataStore.InMemoryDataStore,
            ExecutionContext
          ) => Consequence[Unit],
          plan: DataStoreConditionalTransitionPlan
        ): (Consequence[DataStoreConditionalTransitionResult], Option[Record]) = {
          val store = _store()
          given ExecutionContext = _context(store)
          val prepared =
            store
              .create(
                _root_collection,
                _root_entry,
                _root_record("open", 1L, None)
              )
              .flatMap(_ => setup(store, summon[ExecutionContext]))
          val result =
            prepared.flatMap(_ => store.conditionalTransition(plan))
          result -> store.load(_root_collection, _root_entry).toOption.flatten
        }
        val collision =
          _scenario_(
            (store, context) => {
              given ExecutionContext = context
              store.create(
                _successor_collection,
                _successor_entry,
                _successor_record(_successor_entry, 1L)
              )
            },
            _create_plan(_successor_entry, _side_entry)
          )
        val missing =
          _scenario_(
            (_, _) => Consequence.unit,
            _bind_plan(_successor_entry, 1L)
          )
        val stale =
          _scenario_(
            (store, context) => {
              given ExecutionContext = context
              store.create(
                _successor_collection,
                _successor_entry,
                _successor_record(_successor_entry, 2L)
              )
            },
            _bind_plan(_successor_entry, 1L)
          )

        When("the provider verifies successor intent inside its atomic boundary")
        val outcomes = Vector(collision, missing, stale)

        Then("each remains a failure rather than NotMatched and every root stays unchanged")
        outcomes.forall { case (result, root) =>
          _is_failure(result) &&
          root.contains(_root_record("open", 1L, None))
        } shouldBe true
      }
    }

    "roll back every staged record at each deterministic checkpoint" must _transition_metadata {
      "when provider execution fails after guard, successor, root, or side-effect preparation" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R13,R21; deterministic failures at every pre-publish checkpoint"
        )
        val checkpoints = Vector(
          DataStoreConditionalTransitionCheckpoint.GuardAdmitted,
          DataStoreConditionalTransitionCheckpoint.SuccessorPrepared,
          DataStoreConditionalTransitionCheckpoint.RootPrepared,
          DataStoreConditionalTransitionCheckpoint.BeforePublish
        )

        When("each checkpoint rejects the same valid plan")
        val outcomes = checkpoints.map { checkpoint =>
          val store = new FailingCheckpointDataStore(checkpoint)
          given ExecutionContext = _context(store)
          val rootbefore = _root_record("open", 1L, None)
          val sidebefore =
            Record.dataAuto("id" -> _side_entry.print, "event" -> "before")
          store.create(_root_collection, _root_entry, rootbefore) shouldBe
            Consequence.unit
          store.create(_side_collection, _side_entry, sidebefore) shouldBe
            Consequence.unit
          val result =
            store.conditionalTransition(
              _create_plan(_successor_entry, _side_entry)
            )
          val rootafter =
            store.load(_root_collection, _root_entry).toOption.flatten
          val successorafter =
            store.load(_successor_collection, _successor_entry).toOption.flatten
          val sideafter =
            store.load(_side_collection, _side_entry).toOption.flatten
          (
            _is_failure(result),
            rootafter.contains(rootbefore),
            successorafter.isEmpty,
            sideafter.contains(sidebefore)
          )
        }

        Then("every failure leaves root, successor, side record, and token byte-for-byte unchanged")
        outcomes.forall(_ == (true, true, true, true)) shouldBe true
      }
    }

    "admit exactly one winner for every bounded concurrent caller count" must _concurrency_metadata {
      "when independent callers race with one root token and unique candidates" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R1,R8-R13,R21; ScalaCheck caller counts from two through twelve"
        )
        val property = Prop.forAll(Gen.chooseNum(2, 12)) { callercount =>
          val store = _store()
          given ExecutionContext = _context(store)
          val seeded =
            store.create(
              _root_collection,
              _root_entry,
              _root_record("open", 1L, None)
            )
          val executor = Executors.newFixedThreadPool(callercount)
          val futurecontext =
            scala.concurrent.ExecutionContext.fromExecutorService(executor)
          val ready = new CountDownLatch(callercount)
          val start = new CountDownLatch(1)
          try {
            val calls = Vector.tabulate(callercount) { index =>
              val successor =
                DataStore.StringEntryId(s"successor-$index")
              val side = DataStore.StringEntryId(s"side-$index")
              Future {
                ready.countDown()
                start.await(5L, TimeUnit.SECONDS)
                successor ->
                  store.conditionalTransition(
                    _create_plan(successor, side)
                  )
              }(futurecontext)
            }
            ready.await(5L, TimeUnit.SECONDS) shouldBe true
            start.countDown()
            val results =
              seeded.toOption.toVector.flatMap(_ =>
                calls.map(Await.result(_, 10.seconds))
              )
            val winners = results.collect {
              case (successor, result)
                  if result.toOption.exists(
                    _.isInstanceOf[
                      DataStoreConditionalTransitionResult.Transitioned
                    ]
                  ) =>
                successor
            }
            val losers = results.count {
              case (_, result) =>
                result.toOption.exists(
                  _.isInstanceOf[
                    DataStoreConditionalTransitionResult.NotMatched
                  ]
                )
            }
            val root =
              store.load(_root_collection, _root_entry).toOption.flatten
            val successorexistence =
              results.map { case (successor, _) =>
                store
                  .load(_successor_collection, successor)
                  .toOption
                  .flatten
                  .isDefined
              }
            val sideexistence =
              Vector.tabulate(callercount) { index =>
                store
                  .load(
                    _side_collection,
                    DataStore.StringEntryId(s"side-$index")
                  )
                  .toOption
                  .flatten
                  .isDefined
              }
            winners.size == 1 &&
              losers == callercount - 1 &&
              successorexistence.count(identity) == 1 &&
              sideexistence.count(identity) == 1 &&
              root.flatMap(_.getAny(_revision_field)).contains(2L) &&
              root
                .flatMap(_.getString("successor_id"))
                .contains(winners.head.print)
          } finally {
            futurecontext.shutdown()
          }
        }

        When("the in-memory atomic boundary executes every generated race")
        val checked = Test.check(
          Test.Parameters.default.withMinSuccessfulTests(20),
          property
        )

        Then("one transition wins, all losers mismatch, and no orphan record exists")
        checked.passed shouldBe true
      }
    }

    "hide staged records from ordinary CRUD until one-state publication" must _transition_metadata {
      "when a read races with a transition paused immediately before publish" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R13,R21; one provider paused before immutable state replacement"
        )
        val store = new BlockingBeforePublishDataStore
        given ExecutionContext = _context(store)
        store.create(
          _root_collection,
          _root_entry,
          _root_record("open", 1L, None)
        ) shouldBe Consequence.unit
        val executor = Executors.newFixedThreadPool(2)
        val futurecontext =
          scala.concurrent.ExecutionContext.fromExecutorService(executor)
        try {
          val transition =
            Future(
              store.conditionalTransition(
                _create_plan(_successor_entry, _side_entry)
              )
            )(futurecontext)
          store.paused.await(5L, TimeUnit.SECONDS) shouldBe true

          When("ordinary load attempts to enter while staged state is private")
          val loaded =
            Future(store.load(_root_collection, _root_entry))(futurecontext)
          eventually {
            loaded.isCompleted shouldBe false
          }
          store.release.countDown()
          val transitionresult = Await.result(transition, 10.seconds)
          val loadresult = Await.result(loaded, 10.seconds)

          Then("the read observes only the fully published successor reference and next token")
          transitionresult.toOption.exists(
            _.isInstanceOf[
              DataStoreConditionalTransitionResult.Transitioned
            ]
          ) shouldBe true
          loadresult.toOption.flatten
            .flatMap(_.getString("successor_id")) shouldBe
            Some(_successor_entry.print)
          loadresult.toOption.flatten
            .flatMap(_.getAny(_revision_field)) shouldBe Some(2L)
        } finally {
          futurecontext.shutdown()
        }
      }
    }
  }

  private val _root_collection =
    DataStore.CollectionId.EntityStore(
      EntityCollectionId("test", "conditional", "root")
    )
  private val _successor_collection =
    DataStore.CollectionId.EntityStore(
      EntityCollectionId("test", "conditional", "successor")
    )
  private val _side_collection =
    DataStore.CollectionId.EntityStore(
      EntityCollectionId("test", "conditional", "side")
    )
  private val _root_entry = DataStore.StringEntryId("root-1")
  private val _successor_entry = DataStore.StringEntryId("successor-1")
  private val _side_entry = DataStore.StringEntryId("side-1")
  private val _revision_field = "cncf_revision"
  private val _component_owner =
    _success(DataStoreComponentOwner.create("test-component"))

  private def _store(): DataStore.InMemoryDataStore =
    new DataStore.InMemoryDataStore(
      org.goldenport.cncf.unitofwork.CommitRecorder.noop
    )

  private def _create_plan(
    successorid: DataStore.EntryId,
    sideid: DataStore.EntryId
  ): DataStoreConditionalTransitionPlan =
    _success(
      DataStoreConditionalTransitionPlan.create(
        _root(successorid),
        DataStoreConditionalSuccessor.Create(
          _component_owner,
          _successor_collection,
          successorid,
          _revision_field,
          _successor_record(successorid, 1L)
        ),
        Vector(
          EntityVersionedSideEffect.Save(
            _side_collection,
            sideid,
            Record.dataAuto(
              "id" -> sideid.print,
              "event" -> "transitioned"
            )
          )
        )
      )
    )

  private def _bind_plan(
    successorid: DataStore.EntryId,
    revision: Long
  ): DataStoreConditionalTransitionPlan =
    _success(
      DataStoreConditionalTransitionPlan.create(
        _root(successorid),
        DataStoreConditionalSuccessor.Bind(
          _component_owner,
          _successor_collection,
          successorid,
          _revision_field,
          _revision(revision)
        )
      )
    )

  private def _root(
    successorid: DataStore.EntryId
  ): DataStoreConditionalRoot =
    _success(
      DataStoreConditionalRoot.create(
        _component_owner,
        _root_collection,
        _root_entry,
        _revision_field,
        Some(EntityRevision.INITIAL),
        Vector(
          DataStoreConditionalExpectedField(
            "status",
            _success(DataStoreConditionalValue.from("open"))
          )
        ),
        Record.dataAuto(
          "status" -> "closed",
          "successor_id" -> successorid.print
        ),
        _revision(2L)
      )
    )

  private def _root_record(
    status: String,
    revision: Long,
    successorid: Option[String]
  ): Record =
    successorid
      .map(id =>
        Record.dataAuto(
          "id" -> _root_entry.print,
          "status" -> status,
          "successor_id" -> id,
          _revision_field -> revision
        )
      )
      .getOrElse(
        Record.dataAuto(
          "id" -> _root_entry.print,
          "status" -> status,
          _revision_field -> revision
        )
      )

  private def _successor_record(
    entryid: DataStore.EntryId,
    revision: Long
  ): Record =
    Record.dataAuto(
      "id" -> entryid.print,
      "name" -> "successor",
      _revision_field -> revision
    )

  private def _context(
    store: DataStore
  ): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(store)
    context
  }

  private def _success[A](
    consequence: Consequence[A]
  ): A =
    consequence match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        fail(conclusion.display)
    }

  private def _is_failure[A](
    consequence: Consequence[A]
  ): Boolean =
    consequence match {
      case _: Consequence.Failure[?] => true
      case _ => false
    }

  private def _revision(
    value: Long
  ): EntityRevision =
    EntityRevision.createC(value).toOption.getOrElse(
      fail(s"valid EntityRevision expected: $value")
    )

  private final class FailingCheckpointDataStore(
    checkpoint: DataStoreConditionalTransitionCheckpoint
  ) extends DataStore.InMemoryDataStore(
        org.goldenport.cncf.unitofwork.CommitRecorder.noop
      ) {
    override protected def conditional_transition_checkpoint(
      current: DataStoreConditionalTransitionCheckpoint
    ): Consequence[Unit] =
      if (current == checkpoint)
        Consequence.operationInvalid("injected-conditional-checkpoint")
      else
        Consequence.unit
  }

  private final class BlockingBeforePublishDataStore
      extends DataStore.InMemoryDataStore(
        org.goldenport.cncf.unitofwork.CommitRecorder.noop
      ) {
    val paused = new CountDownLatch(1)
    val release = new CountDownLatch(1)

    override protected def conditional_transition_checkpoint(
      current: DataStoreConditionalTransitionCheckpoint
    ): Consequence[Unit] =
      if (
        current ==
          DataStoreConditionalTransitionCheckpoint.BeforePublish
      ) {
        paused.countDown()
        if (release.await(5L, TimeUnit.SECONDS))
          Consequence.unit
        else
          Consequence.operationInvalid(
            "conditional-transition-checkpoint-timeout"
          )
      } else
        Consequence.unit
  }
}
