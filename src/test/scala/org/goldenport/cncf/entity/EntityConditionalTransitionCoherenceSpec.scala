package org.goldenport.cncf.entity

import cats.Id
import cats.data.State
import cats.effect.Ref
import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentOrigin}
import org.goldenport.cncf.context.{
  DataStoreContext,
  EntityStoreContext,
  ExecutionContext,
  ObservabilityContext,
  Principal,
  PrincipalId,
  RuntimeContext,
  SecurityContext,
  SecurityLevel,
  ScopeContext,
  ScopeKind,
  TraceId
}
import org.goldenport.cncf.datastore.{
  DataStore,
  DataStoreConditionalTransitionFailure,
  DataStoreConditionalTransitionPlan,
  DataStoreConditionalTransitionResult,
  DataStoreComponentOwner,
  DataStoreSpace,
  EntityConditionalTransitionDataStore
}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.runtime.{
  EntityCollection,
  EntityDescriptor,
  EntityLoader,
  EntityMemoryPolicy,
  EntityRealm,
  EntityRealmState,
  EntityRuntimePlan,
  EntityStorage,
  PartitionStrategy,
  PartitionedMemoryRealm
}
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection}
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.observability.{
  CallTreeContext,
  EntityConditionalTransitionObservation
}
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.cncf.unitofwork.{
  CommitRecorder,
  UnitOfWork,
  UnitOfWorkAuthorization,
  UnitOfWorkInterpreter,
  UnitOfWorkOp
}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.simplemodeling.model.value.SecurityAttributes

/*
 * @since   Jul. 24, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityConditionalTransitionCoherenceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _root_collection =
    EntityCollectionId("test", "phase49", "coherence_root")
  private val _successor_collection =
    EntityCollectionId("test", "phase49", "coherence_successor")

  "Entity conditional-transition coherence" should {
    "install authoritative transitioned state and invalidate cached Views" in {
      Given(
        "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R17-R18; Example: E5; one stale resident root and one cached View"
      )
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val rootid = EntityId("test", "transitioned_root", _root_collection)
      val successorid =
        EntityId("test", "transitioned_successor", _successor_collection)
      _seed_root(fixture, Root(rootid, "open", None))
      fixture.rootcollection.put(Root(rootid, "resident-stale", None))
      val viewcounter = _register_view(fixture)
      _query_view(fixture)
      viewcounter.count shouldBe 1

      When("the provider commits the root and successor atomically")
      val calltree = fixture.context.observability.callTreeContext
      val result =
        _interpret(
          fixture,
          _request(
            rootid,
            expectedstatus = "open",
            RootPatch("closed", Some(successorid)),
            _create_successor(successorid)
          )
        )

      Then("the authoritative snapshots replace resident state")
      result shouldBe a[Consequence.Success[_]]
      fixture.rootcollection.resolve(rootid).map(_.status) shouldBe
        Consequence.success("closed")
      fixture.successorcollection.resolve(successorid).map(_.label) shouldBe
        Consequence.success("created")

      And("the committed mutation invalidates the component-local View")
      _query_view(fixture)
      viewcounter.count shouldBe 2

      And("the UnitOfWork and provider CallTree contains no Entity payloads")
      val rendered =
        calltree.build().map(_.toRecord.print).getOrElse(
          fail("conditional-transition CallTree missing")
        )
      rendered should include("uow:entitystore:conditional-transition")
      rendered should include("space:entitystore:conditional-transition")
      rendered should include("space:datastore:entity-conditional-transition")
      rendered should not include "resident-stale"
      rendered should not include "created"
    }

    "refresh an authorized stale resident after NotMatched without invalidating Views" in {
      Given(
        "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R17-R18; Examples: E6,E16; an authoritative mismatch and stale resident root"
      )
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      val rootid = EntityId("test", "not_matched_root", _root_collection)
      val successorid =
        EntityId("test", "not_matched_successor", _successor_collection)
      _seed_root(fixture, Root(rootid, "already-closed", None))
      fixture.rootcollection.put(Root(rootid, "open", None))
      val viewcounter = _register_view(fixture)
      _query_view(fixture)

      When("the provider rejects the stale expected field")
      val result = _interpret(
        fixture,
        _request(
          rootid,
          expectedstatus = "open",
          RootPatch("closed", Some(successorid)),
          _create_successor(successorid)
        )
      )

      Then("NotMatched returns and installs the authorized authoritative root")
      result match {
        case Consequence.Success(
              value: EntityConditionalTransitionResult.NotMatched[?]
            ) =>
          value.existing.entity.asInstanceOf[Root].status shouldBe
            "already-closed"
        case other =>
          fail(s"expected NotMatched but got $other")
      }
      fixture.rootcollection.resolve(rootid).map(_.status) shouldBe
        Consequence.success("already-closed")
      fixture.successorcollection.resolve(successorid) shouldBe
        a[Consequence.Failure[_]]

      And("a non-mutating mismatch leaves the cached View intact")
      _query_view(fixture)
      viewcounter.count shouldBe 1
    }

    "leave resident and View state unchanged before provider success" in {
      Given(
        "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R16-R18; one validation hook that rejects before provider execution"
      )
      val fixture = _fixture(new RejectingHook)
      given ExecutionContext = fixture.context
      val rootid = EntityId("test", "rejected_root", _root_collection)
      val successorid =
        EntityId("test", "rejected_successor", _successor_collection)
      _seed_root(fixture, Root(rootid, "open", None))
      fixture.rootcollection.put(Root(rootid, "resident-before", None))
      val viewcounter = _register_view(fixture)
      _query_view(fixture)

      When("validation rejects the transition before the datastore capability")
      val result = _interpret(
        fixture,
        _request(
          rootid,
          expectedstatus = "open",
          RootPatch("closed", Some(successorid)),
          _create_successor(successorid)
        )
      )

      Then("no datastore, EntitySpace, Working Set, or View mutation occurs")
      result shouldBe a[Consequence.Failure[_]]
      fixture.rootcollection.resolve(rootid).map(_.status) shouldBe
        Consequence.success("resident-before")
      fixture.successorcollection.resolve(successorid) shouldBe
        a[Consequence.Failure[_]]
      _raw_root(fixture, rootid).map(_.flatMap(_.getString("status"))) shouldBe
        Consequence.success(Some("open"))
      _raw_root(fixture, successorid) shouldBe Consequence.success(None)
      _query_view(fixture)
      viewcounter.count shouldBe 1
    }

    "evict stale resident state when post-result read authorization is denied" in {
      Given(
        "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R15,R17-R18; Examples: E7,E14; a concurrent mismatch that revokes caller read access"
      )
      val rootid =
        EntityId("test", "authorization_revoked_root", _root_collection)
      val successorid =
        EntityId(
          "test",
          "authorization_revoked_successor",
          _successor_collection
        )
      val fixture =
        _fixture(
          new RevokingRootHook(rootid),
          Some("transition-owner")
        )
      given ExecutionContext = fixture.context
      _seed_root(
        fixture,
        Root(rootid, "open", None),
        Record.dataAuto(
          "security_attributes" ->
            SecurityAttributes.ownedBy("transition-owner").toRecord
        )
      )
      fixture.rootcollection.put(Root(rootid, "resident-stale", None))
      val viewcounter = _register_view(fixture)
      _query_view(fixture)
      val readauthorization =
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some(_root_collection.name),
          collectionName = Some(_root_collection.name),
          targetId = Some(rootid),
          accessKind = "read"
        )

      When("the provider returns NotMatched with newly unauthorized data")
      val result = _interpret(
        fixture,
        _request(
          rootid,
          expectedstatus = "open",
          RootPatch("closed", Some(successorid)),
          _create_successor(successorid)
        ),
        Some(readauthorization),
        Some(readauthorization.copy(accessKind = "update"))
      )

      Then("the failure exposes no root and removes the stale resident")
      result shouldBe a[Consequence.Failure[_]]
      fixture.rootcollection.resolve(rootid) shouldBe
        a[Consequence.Failure[_]]
      _raw_root(fixture, rootid)
        .map(_.flatMap(_.getString("status"))) shouldBe
        Consequence.success(Some("closed"))
      _raw_root(fixture, successorid) shouldBe Consequence.success(None)

      And("the non-committing caller does not invalidate the View")
      _query_view(fixture)
      viewcounter.count shouldBe 1
    }

    "normalize actual provider and transaction boundary failures" in {
      Given(
        "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rule: R20; provider implementations that return raw and transaction failures"
      )
      val rootid =
        EntityId("test", "provider_failure_root", _root_collection)
      val successorid =
        EntityId("test", "provider_failure_successor", _successor_collection)
      val request =
        _request(
          rootid,
          expectedstatus = "open",
          RootPatch("closed", Some(successorid)),
          _create_successor(successorid)
        )
      val beforecounts =
        RuntimeDashboardMetrics.entityConditionalTransitionDiagnosticCounts

      When("the failures cross the real DataStoreSpace and UnitOfWork boundary")
      val providerfixture = _fixture(
        datastore = new RawProviderFailureDataStore
      )
      _seed_root(providerfixture, Root(rootid, "open", None))
      val providerresult = _interpret(providerfixture, request)
      val transactionfixture = _fixture(
        datastore = new TransactionFailureDataStore
      )
      _seed_root(transactionfixture, Root(rootid, "open", None))
      val transactionresult = _interpret(transactionfixture, request)

      Then("provider and transaction failures have stable structured outcomes")
      val providerclassification =
        EntityConditionalTransitionObservation.classify(providerresult)
      val transactionclassification =
        EntityConditionalTransitionObservation.classify(transactionresult)
      providerclassification.outcome shouldBe
        EntityConditionalTransitionObservation.Outcome.ProviderFailure
      transactionclassification.outcome shouldBe
        EntityConditionalTransitionObservation.Outcome.TransactionFailure

      And("the real UnitOfWork observations increment structured diagnostics")
      val aftercounts =
        RuntimeDashboardMetrics.entityConditionalTransitionDiagnosticCounts
      Vector(providerclassification, transactionclassification).foreach {
        classification =>
          val key = classification.diagnostic
            .map(_.diagnosticKey)
            .getOrElse(fail("structured diagnostic missing"))
          aftercounts.getOrElse(key, 0L) shouldBe
            beforecounts.getOrElse(key, 0L) + 1L
      }
    }
  }

  private final case class Fixture(
    datastorespace: DataStoreSpace,
    entitystorespace: EntityStoreSpace,
    rootcollection: EntityCollection[Root],
    successorcollection: EntityCollection[Successor],
    component: Component,
    context: ExecutionContext
  )

  private final class Counter {
    private var _count = 0

    def count: Int =
      _count

    def increment(): Unit =
      _count = _count + 1
  }

  private def _fixture(
    hook: TransitionValidationHook = TransitionValidationHook.noop,
    principalid: Option[String] = None,
    datastore: DataStore = DataStore.inMemorySearchable()
  ): Fixture = {
    val datastorespace = new DataStoreSpace().addDataStore(datastore)
    val entitystorespace =
      new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val component = new Component() {}
    val rootcollection =
      _entity_collection(_root_collection, _root_persistent)
    val successorcollection =
      _entity_collection(_successor_collection, _successor_persistent)
    component.entitySpace.registerEntity(
      _root_collection.name,
      rootcollection
    )
    component.entitySpace.registerEntity(
      _successor_collection.name,
      successorcollection
    )
    val observability = ObservabilityContext(
      traceId = TraceId("test", "conditional_transition_coherence"),
      spanId = None,
      correlationId = None,
      callTreeContext = CallTreeContext.enabled
    )
    val rootscope = ScopeContext(
      ScopeKind.Runtime,
      "conditional-transition-coherence-root",
      None,
      observability
    )
    val componentscope = Component.Context(
      "conditional-transition-coherence-component",
      rootscope,
      component,
      ComponentOrigin.Embed
    )
    lazy val context: ExecutionContext = {
      val observed =
        ExecutionContext.create(runtime) match {
        case instance: ExecutionContext.Instance =>
          instance.copy(
            cncfCore = instance.cncfCore.copy(
              observability = instance.observability.copy(
                callTreeContext = CallTreeContext.enabled
              )
            )
          )
        case value =>
          value
      }
      principalid.fold(observed) { principalvalue =>
        observed match {
          case instance: ExecutionContext.Instance =>
            val principal = new Principal {
              def id: PrincipalId =
                PrincipalId(principalvalue)

              def attributes: Map[String, String] =
                Map.empty
            }
            instance.copy(
              cncfCore = instance.cncfCore.copy(
                security = SecurityContext(
                  principal,
                  Set.empty,
                  SecurityLevel("test")
                )
              )
            )
          case value =>
            value
        }
      }
    }
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "conditional-transition-coherence-runtime",
        parent = Some(componentscope),
        observabilityContext = observability,
        httpDriverOption = None,
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitofworksupplier = () => new UnitOfWork(context),
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] =
          new UnitOfWorkInterpreter(new UnitOfWork(context))
            .interpret(operation)
      },
      commitaction = _ => (),
      abortaction = _ => (),
      disposeaction = _ => (),
      token = "conditional-transition-coherence-runtime-context",
      transitionValidationHook = hook
    )
    val _ = context
    Fixture(
      datastorespace,
      entitystorespace,
      rootcollection,
      successorcollection,
      component,
      context
    )
  }

  private final class RawProviderFailureDataStore
      extends DataStore.InMemoryDataStore(CommitRecorder.noop) {
    override def conditionalTransition(
      plan: DataStoreConditionalTransitionPlan
    )(using
      context: ExecutionContext
    ): Consequence[DataStoreConditionalTransitionResult] = {
      val _ = (plan, context)
      Consequence.operationInvalid("provider boundary rejected transition")
    }
  }

  private final class TransactionFailureDataStore
      extends DataStore.InMemoryDataStore(CommitRecorder.noop) {
    override def conditionalTransition(
      plan: DataStoreConditionalTransitionPlan
    )(using
      context: ExecutionContext
    ): Consequence[DataStoreConditionalTransitionResult] = {
      val _ = (plan, context)
      DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "provider transaction outcome is indeterminate"
      )
    }
  }

  private def _entity_collection[A](
    id: EntityCollectionId,
    persistent: EntityPersistent[A]
  ): EntityCollection[A] = {
    given EntityPersistent[A] = persistent
    val storerealm = new EntityRealm[A](
      entityName = id.name,
      loader = EntityLoader[A](_ => None),
      state = new IdRef(EntityRealmState(Map.empty))
    )
    val memoryrealm = new PartitionedMemoryRealm[A](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = persistent.id
    )
    new EntityCollection[A](
      EntityDescriptor(
        id,
        EntityRuntimePlan(
          entityName = id.name,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16
        ),
        persistent,
        revisionBinding = Some(
          EntityRevisionBinding(EntityRevisionRepresentation.Detached)
        )
      ),
      EntityStorage(storerealm, Some(memoryrealm))
    )
  }

  private def _register_view(
    fixture: Fixture
  ): Counter = {
    val counter = new Counter
    val collection =
      new ViewCollection[Record](new ViewBuilder[Record] {
        def build(targetId: EntityId): Consequence[Record] =
          Consequence.success(Record.dataAuto("id" -> targetId))
      })
    fixture.component.viewSpace.register(
      _root_collection.name,
      collection,
      Browser.from(
        collection,
        _ => {
          counter.increment()
          Consequence.success(Vector(Record.dataAuto("state" -> "cached")))
        }
      )
    )
    counter
  }

  private def _query_view(
    fixture: Fixture
  ): Unit = {
    val _ = fixture.component.viewSpace
      .browser[Record](_root_collection.name)
      .query(Query(Record.empty))
      .TAKE
  }

  private def _seed_root(
    fixture: Fixture,
    root: Root,
    supplemental: Record = Record.empty
  ): Unit = {
    given ExecutionContext = fixture.context
    val _ = fixture.datastorespace.inject(
      DataStore.CollectionId.EntityStore(_root_collection),
      EntityConcurrencyMetadata.initializeForCreate(
        _root_persistent.toStoreRecord(root) ++ supplemental
      )
    )
  }

  private def _request(
    rootid: EntityId,
    expectedstatus: String,
    patch: RootPatch,
    successor: EntitySuccessorIntent[Successor]
  ): EntityConditionalTransition[Root, RootPatch, Successor] = {
    val field =
      EntityTransitionField
        .exact[Root, String]("status", _root_persistent)
        .TAKE
    val definition =
      EntityTransitionDefinition
        .create(_root_persistent, Vector(field))
        .TAKE
    val expectation =
      definition
        .expectation(
          EntityRevision.INITIAL,
          field.expected(expectedstatus).TAKE
        )
        .TAKE
    EntityConditionalTransition
      .create(rootid, expectation, patch, successor)(using _root_patch)
      .TAKE
  }

  private def _create_successor(
    id: EntityId
  ): EntitySuccessorIntent[Successor] =
    EntitySuccessorIntent
      .create[Successor, Successor](Successor(id, "created"))(using
        _successor_create,
        _successor_persistent
      )
      .TAKE

  private def _interpret(
    fixture: Fixture,
    request: EntityConditionalTransition[Root, RootPatch, Successor],
    rootreadauthorization: Option[UnitOfWorkAuthorization] = None,
    rootupdateauthorization: Option[UnitOfWorkAuthorization] = None
  ): Consequence[EntityConditionalTransitionResult[Root, Successor]] =
    new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
      .interpret(
        UnitOfWorkOp.EntityStoreConditionalTransition(
          request,
          DataStoreComponentOwner.create("phase49-test").TAKE,
          rootreadauthorization,
          rootupdateauthorization,
          None
        )
      )

  private def _raw_root(
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

  private final case class Root(
    id: EntityId,
    status: String,
    successorid: Option[EntityId]
  )

  private final case class RootPatch(
    status: String,
    successorid: Option[EntityId]
  )

  private final case class Successor(
    id: EntityId,
    label: String
  )

  private val _root_persistent: EntityPersistent[Root] =
    new EntityPersistent[Root] {
      def id(entity: Root): EntityId =
        entity.id

      def toRecord(entity: Root): Record =
        Record.dataAuto(
          "id" -> entity.id.value,
          "status" -> entity.status,
          "successor_id" -> entity.successorid
        )

      def fromRecord(record: Record): Consequence[Root] =
        (record.getAs[EntityId]("id"), record.getString("status")) match {
          case (Some(id), Some(status)) =>
            Consequence.success(
              Root(
                id,
                status,
                record.getAs[EntityId]("successor_id")
              )
            )
          case _ =>
            Consequence.argumentInvalid("root", "id and status", record)
        }
    }

  private val _root_patch: EntityPersistentUpdate[RootPatch] =
    new EntityPersistentUpdate[RootPatch] {
      def collection(entity: RootPatch): EntityCollectionId = {
        val _ = entity
        _root_collection
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

  private val _successor_persistent: EntityPersistent[Successor] =
    new EntityPersistent[Successor] {
      def id(entity: Successor): EntityId =
        entity.id

      def toRecord(entity: Successor): Record =
        Record.dataAuto("id" -> entity.id.value, "label" -> entity.label)

      def fromRecord(record: Record): Consequence[Successor] =
        (record.getAs[EntityId]("id"), record.getString("label")) match {
          case (Some(id), Some(label)) =>
            Consequence.success(Successor(id, label))
          case _ =>
            Consequence.argumentInvalid("successor", "id and label", record)
        }
    }

  private val _successor_create: EntityPersistentCreate[Successor] =
    EntityPersistentCreate.fromPersistent(_successor_persistent)

  private final class RejectingHook extends TransitionValidationHook {
    def beforeSave[T](
      entity: T,
      persistent: EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, persistent)
      Consequence.unit
    }

    def beforeUpdate[T](
      entity: T,
      persistent: EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, persistent)
      Consequence.unit
    }

    def beforeUpdateById[P](
      id: EntityId,
      patch: P,
      persistent: EntityPersistentUpdate[P]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (id, patch, persistent)
      Consequence.stateConflict("rejected before provider execution")
    }
  }

  private final class RevokingRootHook(
    rootid: EntityId
  ) extends TransitionValidationHook {
    def beforeSave[T](
      entity: T,
      persistent: EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, persistent)
      Consequence.unit
    }

    def beforeUpdate[T](
      entity: T,
      persistent: EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, persistent)
      Consequence.unit
    }

    def beforeUpdateById[P](
      id: EntityId,
      patch: P,
      persistent: EntityPersistentUpdate[P]
    )(using context: ExecutionContext): Consequence[Unit] = {
      val _ = (id, patch, persistent)
      for {
        collection <- context.entityStoreSpace.dataStoreCollection(rootid)
        entry <- context.entityStoreSpace.dataStoreEntryId(rootid)
        datastore <- context.dataStoreSpace.dataStore(collection)
        _ <- datastore.update(
          collection,
          entry,
          Record.dataAuto(
            "status" -> "closed",
            "security_attributes" ->
              SecurityAttributes.ownedBy("other-owner").toRecord,
            EntityConcurrencyMetadata.STORAGE_FIELD_NAME -> 2L
          )
        )
      } yield ()
    }
  }

  private final class IdRef[A](
    initial: A
  ) extends Ref[Id, A] {
    private var _value = initial

    def get: A =
      _value

    def set(value: A): Unit =
      _value = value

    def update(f: A => A): Unit =
      _value = f(_value)

    def modify[B](f: A => (A, B)): B = {
      val (next, result) = f(_value)
      _value = next
      result
    }

    def tryUpdate(f: A => A): Boolean = {
      update(f)
      true
    }

    def tryModify[B](f: A => (A, B)): Option[B] =
      Some(modify(f))

    def access: (A, A => Boolean) = {
      val snapshot = _value
      snapshot -> { next =>
        if (_value == snapshot) {
          _value = next
          true
        } else {
          false
        }
      }
    }

    override def modifyState[B](state: State[A, B]): B = {
      val (next, result) = state.run(_value).value
      _value = next
      result
    }

    override def tryModifyState[B](
      state: State[A, B]
    ): Option[B] =
      Some(modifyState(state))
  }
}
