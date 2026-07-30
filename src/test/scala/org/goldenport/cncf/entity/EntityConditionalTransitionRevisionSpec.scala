package org.goldenport.cncf.entity

import cats.~>
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import org.goldenport.Consequence
import org.goldenport.cncf.context.{
  DataStoreContext,
  EntityStoreContext,
  ExecutionContext,
  ObservabilityContext,
  RuntimeContext,
  ScopeContext,
  ScopeKind,
  TraceId
}
import org.goldenport.cncf.datastore.{
  DataStore,
  DataStoreComponentOwner,
  DataStoreSpace
}
import org.goldenport.cncf.unitofwork.{
  UnitOfWork,
  UnitOfWorkInterpreter,
  UnitOfWorkOp
}
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}

/*
 * @since   Jul. 25, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityConditionalTransitionRevisionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with TableDrivenPropertyChecks {
  private val _policies = Table(
    "policy",
    EntityConcurrencyPolicy.Optimistic,
    EntityConcurrencyPolicy.None
  )

  "Entity conditional transition revision integration" should {
    "return embedded SimpleEntity-shaped values under every concurrency policy" in {
      forAll(_policies) { policy =>
        Given(
          s"an Embedded root and successor governed by the ${policy.label} policy"
        )
        val fixture = _embedded_fixture(policy)
        given ExecutionContext = fixture.context
        val rootid = _root_id(fixture, s"embedded-${policy.label}")
        val successorid =
          _successor_id(fixture, s"embedded-${policy.label}")
        _seed(
          fixture,
          rootid,
          Record.dataAuto(
            "id" -> rootid,
            "status" -> "open",
            "successor_id" -> None,
            "revision" -> EntityRevision.INITIAL.value
          )
        )

        When("the expected revision and domain guard both match")
        val result = _interpret(
          fixture,
          _embedded_request(rootid, successorid, EntityRevision.INITIAL)
        )

        Then("authoritative revisions remain embedded in both domain values")
        result.map {
          case EntityConditionalTransitionResult.Transitioned(
                root: EntityConditionalTransitionValue.Embedded[?],
                successor: EntityConditionalTransitionValue.Embedded[?]
              ) =>
            val rootentity =
              root.entity.asInstanceOf[EmbeddedRoot]
            val successorentity =
              successor.entity.asInstanceOf[EmbeddedSuccessor]
            (
              rootentity.revision.value,
              root.revision.value,
              successorentity.revision.value,
              successor.revision.value
            )
          case _ =>
            fail("expected embedded transitioned result")
        } shouldBe Consequence.success((2L, 2L, 1L, 1L))
      }
    }

    "return detached carriers only for explicitly detached values" in {
      forAll(_policies) { policy =>
        Given(
          s"a non-SimpleEntity root and successor with explicit Detached binding under ${policy.label}"
        )
        val fixture = _detached_fixture(policy)
        given ExecutionContext = fixture.context
        val rootid = _root_id(fixture, s"detached-${policy.label}")
        val successorid =
          _successor_id(fixture, s"detached-${policy.label}")
        _seed(
          fixture,
          rootid,
          Record.dataAuto(
            "id" -> rootid,
            "status" -> "open",
            "successor_id" -> None,
            "cncf_revision" -> EntityRevision.INITIAL.value
          )
        )

        When("the expected revision and domain guard both match")
        val result = _interpret(
          fixture,
          _detached_request(rootid, successorid, EntityRevision.INITIAL)
        )

        Then("framework revisions are returned only through detached carriers")
        result.map {
          case EntityConditionalTransitionResult.Transitioned(
                root: EntityConditionalTransitionValue.Detached[?],
                successor: EntityConditionalTransitionValue.Detached[?]
              ) =>
            (
              root.entity.asInstanceOf[DetachedRoot].status,
              root.revision.value,
              successor.entity.asInstanceOf[DetachedSuccessor].label,
              successor.revision.value
            )
          case _ =>
            fail("expected detached transitioned result")
        } shouldBe Consequence.success(("claimed", 2L, "created", 1L))
      }
    }

    "require the expected revision under every concurrency policy" in {
      forAll(_policies) { policy =>
        Given(
          s"an Embedded root already advanced beyond the submitted revision under ${policy.label}"
        )
        val fixture = _embedded_fixture(policy)
        given ExecutionContext = fixture.context
        val rootid = _root_id(fixture, s"stale-${policy.label}")
        val successorid =
          _successor_id(fixture, s"stale-${policy.label}")
        _seed(
          fixture,
          rootid,
          Record.dataAuto(
            "id" -> rootid,
            "status" -> "open",
            "successor_id" -> None,
            "revision" -> 2L
          )
        )

        When("a transition submits the stale initial revision")
        val result = _interpret(
          fixture,
          _embedded_request(rootid, successorid, EntityRevision.INITIAL)
        )

        Then("the transition does not match and no successor is created")
        result.map {
          case EntityConditionalTransitionResult.NotMatched(
                existing: EntityConditionalTransitionValue.Embedded[?]
              ) =>
            existing.revision.value
          case _ =>
            fail("expected embedded not-matched result")
        } shouldBe Consequence.success(2L)
        _raw_record(fixture, successorid) shouldBe Consequence.success(None)
      }
    }

    "admit exactly one winner without orphan successors for both representations and policies" in {
      val property = Prop.forAll(Gen.chooseNum(2, 8)) { callercount =>
        Vector(
          _run_embedded_race(
            EntityConcurrencyPolicy.Optimistic,
            callercount
          ),
          _run_embedded_race(
            EntityConcurrencyPolicy.None,
            callercount
          ),
          _run_detached_race(
            EntityConcurrencyPolicy.Optimistic,
            callercount
          ),
          _run_detached_race(
            EntityConcurrencyPolicy.None,
            callercount
          )
        ).forall(identity)
      }

      When("bounded simultaneous callers use one expected revision")
      val checked = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(12),
        property
      )

      Then(
        "each representation and policy admits one winner, advances once, and publishes no loser successor"
      )
      checked.passed shouldBe true
    }
  }

  private final case class Fixture(
    datastorespace: DataStoreSpace,
    entitystorespace: EntityStoreSpace,
    rootcollection: EntityCollectionId,
    successorcollection: EntityCollectionId,
    context: ExecutionContext
  )

  private final case class EmbeddedRoot(
    id: EntityId,
    status: String,
    successorid: Option[EntityId],
    revision: EntityRevision
  )

  private final case class EmbeddedSuccessor(
    id: EntityId,
    label: String,
    revision: EntityRevision
  )

  private final case class DetachedRoot(
    id: EntityId,
    status: String,
    successorid: Option[EntityId]
  )

  private final case class DetachedSuccessor(
    id: EntityId,
    label: String
  )

  private final case class RootPatch(
    status: String,
    successorid: Option[EntityId]
  )

  private final case class SuccessorDraft(
    id: EntityId,
    label: String
  )

  private def _fixture[R, S](
    policy: EntityConcurrencyPolicy,
    rootpersistent: EntityPersistent[R],
    successorpersistent: EntityPersistent[S],
    representation: EntityRevisionRepresentation
  ): Fixture = {
    val rootcollection =
      _collection("revision_transition_root", representation, policy)
    val successorcollection =
      _collection("revision_transition_successor", representation, policy)
    val datastorespace =
      new DataStoreSpace().addDataStore(DataStore.inMemorySearchable())
    val entitystorespace =
      new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val observability = ObservabilityContext(
      traceId = TraceId("test", "entity_conditional_transition_revision"),
      spanId = None,
      correlationId = None
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "entity-conditional-transition-revision-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = None,
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] =
          new UnitOfWorkInterpreter(new UnitOfWork(context))
            .interpret(operation)
      },
      commitAction = _ => (),
      abortAction = _ => (),
      disposeAction = _ => (),
      token = "entity-conditional-transition-revision-runtime-context"
    )
    EntityRevisionSpecSupport.registerRevisionBinding(
      context,
      rootcollection,
      rootpersistent,
      representation,
      policy
    )
    EntityRevisionSpecSupport.registerRevisionBinding(
      context,
      successorcollection,
      successorpersistent,
      representation,
      policy
    )
    Fixture(
      datastorespace,
      entitystorespace,
      rootcollection,
      successorcollection,
      context
    )
  }

  private def _embedded_fixture(
    policy: EntityConcurrencyPolicy
  ): Fixture =
    _fixture(
      policy,
      _embedded_root_persistent,
      _embedded_successor_persistent,
      EntityRevisionRepresentation.Embedded
    )

  private def _detached_fixture(
    policy: EntityConcurrencyPolicy
  ): Fixture =
    _fixture(
      policy,
      _detached_root_persistent,
      _detached_successor_persistent,
      EntityRevisionRepresentation.Detached
    )

  private def _embedded_request(
    rootid: EntityId,
    successorid: EntityId,
    expectedrevision: EntityRevision
  ): EntityConditionalTransition[EmbeddedRoot, RootPatch, EmbeddedSuccessor] =
    _request(
      rootid,
      successorid,
      expectedrevision,
      _embedded_root_persistent,
      _embedded_successor_persistent,
      _successor_create
    )

  private def _detached_request(
    rootid: EntityId,
    successorid: EntityId,
    expectedrevision: EntityRevision
  ): EntityConditionalTransition[DetachedRoot, RootPatch, DetachedSuccessor] =
    _request(
      rootid,
      successorid,
      expectedrevision,
      _detached_root_persistent,
      _detached_successor_persistent,
      _successor_create
    )

  private def _request[R, S](
    rootid: EntityId,
    successorid: EntityId,
    expectedrevision: EntityRevision,
    rootpersistent: EntityPersistent[R],
    successorpersistent: EntityPersistent[S],
    successorcreate: EntityPersistentCreate[SuccessorDraft]
  ): EntityConditionalTransition[R, RootPatch, S] = {
    val field =
      _success(
        EntityTransitionField
          .exact[R, String]("status", rootpersistent),
        "transition field"
      )
    val definition =
      _success(
        EntityTransitionDefinition
          .create(rootpersistent, Vector(field)),
        "transition definition"
      )
    val expectation =
      _success(
        definition.expectation(
          expectedrevision,
          _success(field.expected("open"), "transition expectation field")
        ),
        "transition expectation"
      )
    val successor =
      _success(
        EntitySuccessorIntent.create[SuccessorDraft, S](
          SuccessorDraft(successorid, "created")
        )(using successorcreate, successorpersistent),
        "successor intent"
      )
    val patchpersistent = _root_patch(rootid.collection)
    _success(
      EntityConditionalTransition.create(
        rootid,
        expectation,
        RootPatch("claimed", Some(successorid)),
        successor
      )(using patchpersistent),
      "conditional transition"
    )
  }

  private def _interpret[R, S](
    fixture: Fixture,
    request: EntityConditionalTransition[R, RootPatch, S]
  ): Consequence[EntityConditionalTransitionResult[R, S]] =
    new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
      .interpret(
        UnitOfWorkOp.EntityStoreConditionalTransition(
          request,
          _success(
            DataStoreComponentOwner.create("phase50-test"),
            "component owner"
          ),
          None,
          None,
          None
        )
      )

  private def _seed(
    fixture: Fixture,
    id: EntityId,
    record: Record
  ): Unit = {
    given ExecutionContext = fixture.context
    val _ = fixture.datastorespace.inject(
      DataStore.CollectionId.EntityStore(id.collection),
      record
    )
  }

  private def _raw_record(
    fixture: Fixture,
    id: EntityId
  ): Consequence[Option[Record]] = {
    given ExecutionContext = fixture.context
    for {
      collection <- fixture.entitystorespace.dataStoreCollection(id)
      entry <- fixture.entitystorespace.dataStoreEntryId(id)
      datastore <- fixture.datastorespace.dataStore(collection)
      record <- datastore.load(collection, entry)
    } yield record
  }

  private def _run_embedded_race(
    policy: EntityConcurrencyPolicy,
    callercount: Int
  ): Boolean = {
    val fixture = _embedded_fixture(policy)
    given ExecutionContext = fixture.context
    val rootid =
      _root_id(fixture, s"embedded-race-${policy.label}-$callercount")
    _seed(
      fixture,
      rootid,
      Record.dataAuto(
        "id" -> rootid,
        "status" -> "open",
        "successor_id" -> None,
        "revision" -> 1L
      )
    )
    val results = _race(callercount) { index =>
      val successorid =
        _successor_id(
          fixture,
          s"embedded-race-${policy.label}-$callercount-$index"
        )
      successorid ->
        _interpret(
          fixture,
          _embedded_request(rootid, successorid, EntityRevision.INITIAL)
        )
    }
    _verify_race(fixture, rootid, results, "revision")
  }

  private def _run_detached_race(
    policy: EntityConcurrencyPolicy,
    callercount: Int
  ): Boolean = {
    val fixture = _detached_fixture(policy)
    given ExecutionContext = fixture.context
    val rootid =
      _root_id(fixture, s"detached-race-${policy.label}-$callercount")
    _seed(
      fixture,
      rootid,
      Record.dataAuto(
        "id" -> rootid,
        "status" -> "open",
        "successor_id" -> None,
        "cncf_revision" -> 1L
      )
    )
    val results = _race(callercount) { index =>
      val successorid =
        _successor_id(
          fixture,
          s"detached-race-${policy.label}-$callercount-$index"
        )
      successorid ->
        _interpret(
          fixture,
          _detached_request(rootid, successorid, EntityRevision.INITIAL)
        )
    }
    _verify_race(fixture, rootid, results, "cncf_revision")
  }

  private def _race[A](
    callercount: Int
  )(
    invoke: Int => A
  ): Vector[A] = {
    val executor = Executors.newFixedThreadPool(callercount)
    val futurecontext =
      scala.concurrent.ExecutionContext.fromExecutorService(executor)
    val ready = new CountDownLatch(callercount)
    val start = new CountDownLatch(1)
    try {
      val calls = Vector.tabulate(callercount) { index =>
        Future {
          ready.countDown()
          start.await(5L, TimeUnit.SECONDS)
          invoke(index)
        }(futurecontext)
      }
      ready.await(5L, TimeUnit.SECONDS) shouldBe true
      start.countDown()
      calls.map(Await.result(_, 10.seconds))
    } finally {
      futurecontext.shutdown()
    }
  }

  private def _verify_race[R, S](
    fixture: Fixture,
    rootid: EntityId,
    results: Vector[
      (
        EntityId,
        Consequence[EntityConditionalTransitionResult[R, S]]
      )
    ],
    revisionfield: String
  ): Boolean = {
    val winners = results.collect {
      case (successorid, result)
          if result.toOption.exists(
            _.isInstanceOf[
              EntityConditionalTransitionResult.Transitioned[?, ?]
            ]
          ) =>
        successorid
    }
    val losers = results.count { case (_, result) =>
      result.toOption.exists(
        _.isInstanceOf[EntityConditionalTransitionResult.NotMatched[?]]
      )
    }
    val successorcount = results.count { case (successorid, _) =>
      _raw_record(fixture, successorid).toOption.flatten.isDefined
    }
    val root = _raw_record(fixture, rootid).toOption.flatten
    winners.size == 1 &&
    losers == results.size - 1 &&
    successorcount == 1 &&
    root.flatMap(_.getLong(revisionfield)).contains(2L) &&
    root
      .flatMap(_.getAs[EntityId]("successor_id"))
      .exists(_.value == winners.head.value)
  }

  private def _root_id(
    fixture: Fixture,
    suffix: String
  ): EntityId =
    EntityId(
      "test",
      _minor(s"revision_transition_root_$suffix"),
      fixture.rootcollection
    )

  private def _successor_id(
    fixture: Fixture,
    suffix: String
  ): EntityId =
    EntityId(
      "test",
      _minor(s"revision_transition_successor_$suffix"),
      fixture.successorcollection
    )

  private def _collection(
    basename: String,
    representation: EntityRevisionRepresentation,
    policy: EntityConcurrencyPolicy
  ): EntityCollectionId =
    EntityCollectionId(
      "test",
      "phase50",
      s"${basename}_${representation.label}_${policy.label}"
        .replace('-', '_')
    )

  private def _minor(
    value: String
  ): String =
    value.replace('-', '_')

  private def _success[A](
    result: Consequence[A],
    label: String
  ): A =
    result match {
      case Consequence.Success(value) =>
        value
      case Consequence.Failure(conclusion) =>
        fail(s"$label fixture is invalid: ${conclusion.display}")
    }

  private def _root_patch(
    collectionid: EntityCollectionId
  ): EntityPersistentUpdate[RootPatch] =
    new EntityPersistentUpdate[RootPatch] {
      def collection(entity: RootPatch): EntityCollectionId = {
        val _ = entity
        collectionid
      }

      def toRecord(entity: RootPatch): Record =
        Record.dataAuto(
          "status" -> entity.status,
          "successor_id" -> entity.successorid
        )

      def fromRecord(record: Record): Consequence[RootPatch] =
        record.getString("status") match {
          case Some(status) =>
            Consequence.success(
              RootPatch(
                status,
                record.getAs[EntityId]("successor_id")
              )
            )
          case None =>
            Consequence.argumentInvalid("rootPatch", "status", record)
        }
    }

  private val _embedded_root_persistent: EntityPersistent[EmbeddedRoot] =
    new EntityPersistent[EmbeddedRoot] {
      def id(entity: EmbeddedRoot): EntityId =
        entity.id

      def toRecord(entity: EmbeddedRoot): Record =
        Record.dataAuto(
          "id" -> entity.id.value,
          "status" -> entity.status,
          "successor_id" -> entity.successorid,
          "revision" -> entity.revision.value
        )

      def fromRecord(record: Record): Consequence[EmbeddedRoot] =
        (
          record.getAs[EntityId]("id"),
          record.getString("status"),
          record.getLong("revision")
        ) match {
          case (Some(id), Some(status), Some(revision)) =>
            EntityRevision.createC(revision).map(
              EmbeddedRoot(
                id,
                status,
                record.getAs[EntityId]("successor_id"),
                _
              )
            )
          case _ =>
            Consequence.argumentInvalid(
              "embeddedRoot",
              "id, status, and revision",
              record
            )
        }
    }

  private val _embedded_successor_persistent
      : EntityPersistent[EmbeddedSuccessor] =
    new EntityPersistent[EmbeddedSuccessor] {
      def id(entity: EmbeddedSuccessor): EntityId =
        entity.id

      def toRecord(entity: EmbeddedSuccessor): Record =
        Record.dataAuto(
          "id" -> entity.id.value,
          "label" -> entity.label,
          "revision" -> entity.revision.value
        )

      def fromRecord(record: Record): Consequence[EmbeddedSuccessor] =
        (
          record.getAs[EntityId]("id"),
          record.getString("label"),
          record.getLong("revision")
        ) match {
          case (Some(id), Some(label), Some(revision)) =>
            EntityRevision
              .createC(revision)
              .map(EmbeddedSuccessor(id, label, _))
          case _ =>
            Consequence.argumentInvalid(
              "embeddedSuccessor",
              "id, label, and revision",
              record
            )
        }
    }

  private val _detached_root_persistent: EntityPersistent[DetachedRoot] =
    new EntityPersistent[DetachedRoot] {
      def id(entity: DetachedRoot): EntityId =
        entity.id

      def toRecord(entity: DetachedRoot): Record =
        Record.dataAuto(
          "id" -> entity.id.value,
          "status" -> entity.status,
          "successor_id" -> entity.successorid
        )

      def fromRecord(record: Record): Consequence[DetachedRoot] =
        (record.getAs[EntityId]("id"), record.getString("status")) match {
          case (Some(id), Some(status)) =>
            Consequence.success(
              DetachedRoot(
                id,
                status,
                record.getAs[EntityId]("successor_id")
              )
            )
          case _ =>
            Consequence.argumentInvalid(
              "detachedRoot",
              "id and status",
              record
            )
        }
    }

  private val _detached_successor_persistent
      : EntityPersistent[DetachedSuccessor] =
    new EntityPersistent[DetachedSuccessor] {
      def id(entity: DetachedSuccessor): EntityId =
        entity.id

      def toRecord(entity: DetachedSuccessor): Record =
        Record.dataAuto(
          "id" -> entity.id.value,
          "label" -> entity.label
        )

      def fromRecord(record: Record): Consequence[DetachedSuccessor] =
        (record.getAs[EntityId]("id"), record.getString("label")) match {
          case (Some(id), Some(label)) =>
            Consequence.success(DetachedSuccessor(id, label))
          case _ =>
            Consequence.argumentInvalid(
              "detachedSuccessor",
              "id and label",
              record
            )
        }
    }

  private val _successor_create: EntityPersistentCreate[SuccessorDraft] =
    new EntityPersistentCreate[SuccessorDraft] {
      def collection(entity: SuccessorDraft): EntityCollectionId = {
        entity.id.collection
      }

      def id(entity: SuccessorDraft): Option[EntityId] =
        Some(entity.id)

      def toRecord(entity: SuccessorDraft): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "label" -> entity.label
        )
    }
}
