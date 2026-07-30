package org.goldenport.cncf.unitofwork

import cats.~>
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.{
  Capability,
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
  DataStoreConditionalTransitionPlan,
  DataStoreConditionalTransitionResult,
  DataStoreComponentOwner,
  DataStoreSpace
}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.observability.{
  ConclusionDiagnostics,
  EntityConditionalTransitionObservation
}
import org.goldenport.record.Record
import org.goldenport.cncf.security.EntityAccessRelation
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.cncf.unitofwork.CommitRecorder
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
final class UnitOfWorkConditionalTransitionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _rootcollection =
    EntityCollectionId("test", "phase49", "conditional_root")
  private val _successorcollection =
    EntityCollectionId("test", "phase49", "conditional_successor")
  private val _transition_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E5, rules:R14-R16, phase:49"
    )
  private val _mismatch_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E6, rules:R14-R16, phase:49"
    )
  private val _admission_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E5, rules:R12,R14,R16, phase:49"
    )
  private val _bound_race_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E13, rules:R14-R16, phase:49"
    )
  private val _reauthorization_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E14, rules:R14-R15, phase:49"
    )
  private val _successor_identity_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E19, rules:R8,R14, phase:49"
    )
  private val _relation_authorization_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, rules:R14-R15, phase:49"
    )

  "UnitOfWork Entity conditional transition" should {
    "return authoritative create snapshots" must _transition_metadata {
      "when the root guard and successor creation are admitted" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R14-R16; Example: E5; one admitted create input"
        )
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val rootid =
          EntityId("test", "conditional_create_root", _rootcollection)
        val successorid =
          EntityId("test", "conditional_create_successor", _successorcollection)
        _seed_root(fixture, Root(rootid, "open", None))
        val request =
          _request(
            rootid,
            "open",
            RootPatch("claimed", Some(successorid)),
            EntitySuccessorIntent
              .create[Successor, Successor](
                Successor(successorid, "created")
              )(using _successor_create, _successor_persistent)
              .TAKE
          )
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))

        When("the typed UnitOfWork create operation reaches the atomic provider")
        val result = interpreter.interpret(
          UnitOfWorkOp.EntityStoreConditionalTransition(
            request,
            DataStoreComponentOwner.create("phase49-test").TAKE,
            None,
            None,
            None
          )
        )

        Then("the result contains both authoritative snapshots")
        result.map {
          case EntityConditionalTransitionResult.Transitioned(root, successor) =>
            root.entity.status -> successor.entity.label
          case _ =>
            fail("expected transitioned result")
        } shouldBe Consequence.success("claimed" -> "created")

        And("both records are visible through the EntityStore")
        fixture.entitystorespace
          .loadDetached(rootid, _root_persistent)
          .map(_.map(_.entity.status)) shouldBe
          Consequence.success(Some("claimed"))
        fixture.entitystorespace
          .loadDetached(successorid, _successor_persistent)
          .map(_.map(_.entity.label)) shouldBe
          Consequence.success(Some("created"))
      }
    }

    "reuse the admitted create successor identity" must
      _successor_identity_metadata {
      "when provider preparation creates the successor" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R8,R14; Example: E19; one counting create codec admitted before UnitOfWork execution"
        )
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val rootid =
          EntityId("test", "conditional_identity_root", _rootcollection)
        val successorid =
          EntityId(
            "test",
            "conditional_identity_successor",
            _successorcollection
          )
        _seed_root(fixture, Root(rootid, "open", None))
        val create = new CountingSuccessorCreate
        val successor =
          EntitySuccessorIntent
            .create[Successor, Successor](
              Successor(successorid, "created")
            )(using create, _successor_persistent)
            .TAKE
        val request =
          _request(
            rootid,
            "open",
            RootPatch("claimed", Some(successorid)),
            successor
          )

        When("the UnitOfWork prepares and commits the atomic provider plan")
        val result = _interpret(fixture, request)

        Then("the admitted collection and candidate id are not evaluated again")
        result shouldBe a[Consequence.Success[?]]
        create.collectionCount shouldBe 1
        create.idCount shouldBe 1
        _raw_root(fixture, successorid).map(
          _.flatMap(_.getString("label"))
        ) shouldBe Consequence.success(Some("created"))
      }
    }

    "generate a missing successor id in the admitted collection" must
      _successor_identity_metadata {
      "when the create candidate has no Entity id" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R8,R14; Example: E19; one id-less successor draft and one admitted successor collection"
        )
        val datastore = new CapturingDataStore
        val fixture = _fixture(datastore = datastore)
        given ExecutionContext = fixture.context
        val rootid =
          EntityId("test", "conditional_generated_id_root", _rootcollection)
        _seed_root(fixture, Root(rootid, "open", None))
        val create = new CountingSuccessorDraftCreate
        val successor =
          EntitySuccessorIntent
            .create[SuccessorDraft, Successor](
              SuccessorDraft("generated")
            )(using create, _successor_persistent)
            .TAKE
        val request =
          _request(
            rootid,
            "open",
            RootPatch("claimed", None),
            successor
          )

        When("the UnitOfWork creates the successor inside the provider plan")
        val result = _interpret(fixture, request)

        Then("the generated id belongs to the retained collection without codec reevaluation")
        result shouldBe a[Consequence.Success[?]]
        datastore.lastPlan.map(_.successor.collection) shouldBe
          Some(DataStore.CollectionId.EntityStore(_successorcollection))
        create.collectionCount shouldBe 1
        create.idCount shouldBe 1
        val generatedid = result.map {
          case transitioned:
              EntityConditionalTransitionResult.Transitioned[?, ?] =>
            _successor_persistent.id(
              transitioned.successor.entity.asInstanceOf[Successor]
            )
          case _ =>
            fail("expected transitioned result")
        }
        val observationcontext =
          EntityConditionalTransitionObservation.context(
            Some("phase49-test"),
            rootid,
            successor,
            result
          )
        observationcontext.successorid shouldBe generatedid.toOption
      }
    }

    "return authoritative bind snapshots" must _transition_metadata {
      "when the root guard and bound successor revision are admitted" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R14-R16; Example: E5; one admitted bind input"
        )
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val rootid =
          EntityId("test", "conditional_bind_root", _rootcollection)
        val successorid =
          EntityId("test", "conditional_bind_successor", _successorcollection)
        _seed_root(fixture, Root(rootid, "open", None))
        _seed_successor(fixture, Successor(successorid, "existing"))
        val request =
          _request(
            rootid,
            "open",
            RootPatch("bound", Some(successorid)),
            EntitySuccessorIntent
              .bind(successorid)(using _successor_persistent)
              .TAKE
          )
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))

        When("the typed UnitOfWork bind operation reaches the atomic provider")
        val result =
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreConditionalTransition(
              request,
              DataStoreComponentOwner.create("phase49-test").TAKE,
              None,
              None,
              None
            )
          )

        Then("the result contains both authoritative snapshots")
        result.map {
          case EntityConditionalTransitionResult.Transitioned(root, successor) =>
            root.entity.status -> successor.entity.label
          case _ =>
            fail("expected transitioned result")
        } shouldBe Consequence.success("bound" -> "existing")

        And("the root mutation and existing successor are visible through the EntityStore")
        fixture.entitystorespace
          .loadDetached(rootid, _root_persistent)
          .map(_.map(_.entity.status)) shouldBe
          Consequence.success(Some("bound"))
        fixture.entitystorespace
          .loadDetached(successorid, _successor_persistent)
          .map(_.map(_.entity.label)) shouldBe
          Consequence.success(Some("existing"))
      }
    }

    "return the authorized authoritative mismatch" must _mismatch_metadata {
      "when one exact root guard differs" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R14-R16; Example: E6; one closed root and an open expectation"
        )
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val rootid =
          EntityId("test", "conditional_mismatch_root", _rootcollection)
        val successorid =
          EntityId(
            "test",
            "conditional_mismatch_successor",
            _successorcollection
          )
        _seed_root(fixture, Root(rootid, "closed", None))
        val request =
          _request(
            rootid,
            "open",
            RootPatch("claimed", Some(successorid)),
            EntitySuccessorIntent
              .create[Successor, Successor](
                Successor(successorid, "must-not-exist")
              )(using _successor_create, _successor_persistent)
              .TAKE
          )

        When("the provider compares the exact typed expectation")
        val result =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
            .interpret(
              UnitOfWorkOp.EntityStoreConditionalTransition(
                request,
                DataStoreComponentOwner.create("phase49-test").TAKE,
                None,
                None,
                None
              )
            )

        Then("NotMatched contains the current root and no successor is published")
        result.map {
          case EntityConditionalTransitionResult.NotMatched(existing) =>
            existing.entity.status
          case _ =>
            fail("expected not-matched result")
        } shouldBe Consequence.success("closed")
        fixture.entitystorespace
          .loadDetached(successorid, _successor_persistent) shouldBe
          Consequence.success(None)
      }
    }

    "reject inadmissible root changes before provider mutation" must
      _admission_metadata {
        "when the root patch is empty" in {
          Given(
            "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R12,R14,R16; Example: E5; one empty root patch"
          )
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val rootid =
            EntityId("test", "conditional_empty_root", _rootcollection)
          val successorid =
            EntityId("test", "conditional_empty_successor", _successorcollection)
          _seed_root(fixture, Root(rootid, "open", None))
          val request =
            _raw_request(
              rootid,
              RawPatch(Record.empty),
              successorid
            )

          When("the request reaches the EntityStore admission boundary")
          val result = _interpret(fixture, request)

          Then("the request fails without advancing the root or creating the successor")
          result shouldBe a[Consequence.Failure[?]]
          _raw_root(fixture, rootid).map(_.map { record =>
            record.getString("status") ->
              record.getLong(EntityConcurrencyMetadata.STORAGE_FIELD_NAME)
          }) shouldBe Consequence.success(Some(Some("open") -> Some(1L)))
          _raw_root(fixture, successorid) shouldBe Consequence.success(None)
        }

        "when the root patch has no effective change" in {
          Given(
            "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R12,R14,R16; Example: E5; one ineffective root patch"
          )
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val rootid =
            EntityId("test", "conditional_noop_root", _rootcollection)
          val successorid =
            EntityId("test", "conditional_noop_successor", _successorcollection)
          _seed_root(fixture, Root(rootid, "open", None))
          val request =
            _request(
              rootid,
              "open",
              RootPatch("open", None),
              _create_successor(successorid)
            )

          When("the request reaches the EntityStore admission boundary")
          val result = _interpret(fixture, request)

          Then("the request fails without advancing the root or creating the successor")
          result shouldBe a[Consequence.Failure[?]]
          _raw_root(fixture, rootid).map(_.map { record =>
            record.getString("status") ->
              record.getLong(EntityConcurrencyMetadata.STORAGE_FIELD_NAME)
          }) shouldBe Consequence.success(Some(Some("open") -> Some(1L)))
          _raw_root(fixture, successorid) shouldBe Consequence.success(None)
        }

        "when the root patch writes the managed revision field" in {
          Given(
            "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R12,R14,R16; Example: E5; one managed-field root patch"
          )
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val rootid =
            EntityId("test", "conditional_managed_root", _rootcollection)
          val successorid =
            EntityId("test", "conditional_managed_successor", _successorcollection)
          _seed_root(fixture, Root(rootid, "open", None))
          val request =
            _raw_request(
              rootid,
              RawPatch(
                Record.dataAuto(
                  EntityConcurrencyMetadata.STORAGE_FIELD_NAME -> 99L
                )
              ),
              successorid
            )

          When("the request reaches the EntityStore admission boundary")
          val result = _interpret(fixture, request)

          Then("the request fails without advancing the root or creating the successor")
          result shouldBe a[Consequence.Failure[?]]
          _raw_root(fixture, rootid).map(_.map { record =>
            record.getString("status") ->
              record.getLong(EntityConcurrencyMetadata.STORAGE_FIELD_NAME)
          }) shouldBe Consequence.success(Some(Some("open") -> Some(1L)))
          _raw_root(fixture, successorid) shouldBe Consequence.success(None)
        }

        "when the root is logically deleted" in {
          Given(
            "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R12,R14,R16; Example: E5; one logically deleted root"
          )
          val fixture = _fixture()
          given ExecutionContext = fixture.context
          val rootid =
            EntityId("test", "conditional_deleted_root", _rootcollection)
          val successorid =
            EntityId("test", "conditional_deleted_successor", _successorcollection)
          _seed_root(
            fixture,
            Root(rootid, "open", None),
            Record.dataAuto(
              "deletedAt" -> Instant.parse("2026-07-24T00:00:00Z")
            )
          )
          val request =
            _request(
              rootid,
              "open",
              RootPatch("claimed", Some(successorid)),
              _create_successor(successorid)
            )

          When("the request reaches the EntityStore admission boundary")
          val result = _interpret(fixture, request)

          Then("the request fails without advancing the root or creating the successor")
          result shouldBe a[Consequence.Failure[?]]
          _raw_root(fixture, rootid).map(_.map { record =>
            record.getString("status") ->
              record.getLong(EntityConcurrencyMetadata.STORAGE_FIELD_NAME)
          }) shouldBe Consequence.success(Some(Some("open") -> Some(1L)))
          _raw_root(fixture, successorid) shouldBe Consequence.success(None)
        }
      }

    "submit only the admitted root delta to the provider" must
      _admission_metadata {
        "when the current root contains unrelated fields" in {
          Given(
            "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R12,R14; Example: E5; a root with one unrelated persisted field"
          )
          val datastore = new CapturingDataStore
          val fixture = _fixture(datastore = datastore)
          given ExecutionContext = fixture.context
          val rootid =
            EntityId("test", "conditional_delta_root", _rootcollection)
          val successorid =
            EntityId("test", "conditional_delta_successor", _successorcollection)
          _seed_root(
            fixture,
            Root(rootid, "open", None),
            Record.dataAuto("unrelated" -> "preserve")
          )
          val request =
            _request(
              rootid,
              "open",
              RootPatch("claimed", Some(successorid)),
              _create_successor(successorid)
            )

          When("EntityStore normalizes the typed patch into the provider plan")
          val result = _interpret(fixture, request)

          Then("the provider receives only changed domain fields")
          result shouldBe a[Consequence.Success[_]]
          datastore.lastPlan.map(_.root.changes.keySet).exists { keys =>
            keys.contains("status") &&
              keys.contains("successor_id") &&
              !keys.contains("id") &&
              !keys.contains("unrelated")
          } shouldBe true
          datastore.lastPlan.flatMap(
            _.root.changes.getAny("unrelated")
          ) shouldBe None
          _raw_root(fixture, rootid).map(
            _.flatMap(_.getString("unrelated"))
          ) shouldBe Consequence.success(Some("preserve"))
        }
      }

    "run transition validation before provider mutation" must
      _admission_metadata {
        "when the lifecycle hook rejects the proposed root" in {
          Given(
            "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R14,R16; Example: E5; a rejecting transition-validation hook"
          )
          val hook = new RejectingConditionalHook
          val fixture = _fixture(hook)
          given ExecutionContext = fixture.context
          val rootid =
            EntityId("test", "conditional_hook_root", _rootcollection)
          val successorid =
            EntityId("test", "conditional_hook_successor", _successorcollection)
          _seed_root(fixture, Root(rootid, "open", None))
          val request =
            _request(
              rootid,
              "open",
              RootPatch("claimed", Some(successorid)),
              _create_successor(successorid)
            )

          When("the typed transition invokes the lifecycle boundary")
          val result = _interpret(fixture, request)

          Then("the hook is observed and no provider record changes")
          hook.beforeUpdateByIdCount shouldBe 1
          result shouldBe a[Consequence.Failure[_]]
          _raw_root(fixture, rootid).map(_.flatMap(_.getString("status"))) shouldBe
            Consequence.success(Some("open"))
          _raw_root(fixture, successorid) shouldBe Consequence.success(None)
        }
      }

    "authorize a Bind through root relationship rules and successor read" must
      _relation_authorization_metadata {
      "when the root relation and successor read are both admitted" in {
      Given(
        "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R14-R15; one relation-authorized root and owner-readable bound successor"
      )
      val rootid =
        EntityId("test", "conditional_relation_root", _rootcollection)
      val successorid =
        EntityId(
          "test",
          "conditional_relation_successor",
          _successorcollection
        )
      val fixture = _fixture(principalid = Some("transition-owner"))
      given ExecutionContext = fixture.context
      _seed_root(
        fixture,
        Root(rootid, "open", None),
        Record.dataAuto("assignee" -> "transition-owner")
      )
      _seed_successor(
        fixture,
        Successor(successorid, "existing"),
        Record.dataAuto(
          "security_attributes" ->
            SecurityAttributes.ownedBy("transition-owner").toRecord
        )
      )
      val request =
        _request(
          rootid,
          "open",
          RootPatch("bound", Some(successorid)),
          EntitySuccessorIntent
            .bind(successorid)(using _successor_persistent)
            .TAKE
        )
      val relationship =
        EntityAccessRelation(
          "assignee",
          "subjectId",
          Set("read", "update")
        )
      val rootauthorization =
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some(_rootcollection.name),
          collectionName = Some(_rootcollection.name),
          targetId = Some(rootid),
          accessKind = "read",
          relationRules = Vector(relationship)
        )
      val successorauthorization =
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some(_successorcollection.name),
          collectionName = Some(_successorcollection.name),
          targetId = Some(successorid),
          accessKind = "read"
        )

      When("the Bind crosses the UnitOfWork authorization chokepoint")
      val result =
        _interpret(
          fixture,
          request,
          Some(rootauthorization),
          Some(rootauthorization.copy(accessKind = "update")),
          Some(successorauthorization)
        )

      Then("both relationship and successor read admission precede mutation")
        result shouldBe a[Consequence.Success[_]]
        _raw_root(fixture, rootid)
          .map(_.flatMap(_.getString("status"))) shouldBe
          Consequence.success(Some("bound"))
      }
    }

    "guard an authorized bound successor at provider commit" must
      _bound_race_metadata {
        "when its revision changes after authorization" in {
          Given(
            "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R14-R16; Example: E13; a hook that advances the bound successor revision after authorization"
          )
          val successorid =
            EntityId("test", "conditional_racing_successor", _successorcollection)
          val hook = new AdvancingSuccessorHook(successorid)
          val fixture = _fixture(hook)
          given ExecutionContext = fixture.context
          val rootid =
            EntityId("test", "conditional_racing_root", _rootcollection)
          _seed_root(fixture, Root(rootid, "open", None))
          _seed_successor(fixture, Successor(successorid, "existing"))
          val request =
            _request(
              rootid,
              "open",
              RootPatch("bound", Some(successorid)),
              EntitySuccessorIntent
                .bind(successorid)(using _successor_persistent)
                .TAKE
            )

          When("the provider compares the authorized successor revision")
          val result = _interpret(fixture, request)

          Then("a structured conflict is returned without root mutation")
          result shouldBe a[Consequence.Failure[_]]
          result match {
            case Consequence.Failure(conclusion) =>
              ConclusionDiagnostics.classify(conclusion).reason shouldBe
                Some("bound-successor-revision-conflict")
            case _ =>
              fail("expected bound-successor conflict")
          }
          _raw_root(fixture, rootid).map(_.map { record =>
            record.getString("status") ->
              record.getLong(EntityConcurrencyMetadata.STORAGE_FIELD_NAME)
          }) shouldBe Consequence.success(Some(Some("open") -> Some(1L)))
        }
      }

    "reauthorize an authoritative mismatch root" must
      _reauthorization_metadata {
        "when a concurrent root change revokes read permission" in {
          Given(
            "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R14-R15; Example: E14; an owner-readable root and a hook that revokes ownership while advancing its revision"
          )
          val rootid =
            EntityId("test", "conditional_reauthorization_root", _rootcollection)
          val successorid =
            EntityId(
              "test",
              "conditional_reauthorization_successor",
              _successorcollection
            )
          val hook = new RevokingRootHook(rootid)
          val fixture =
            _fixture(hook = hook, principalid = Some("transition-owner"))
          given ExecutionContext = fixture.context
          _seed_root(
            fixture,
            Root(rootid, "open", None),
            Record.dataAuto(
              "security_attributes" ->
                SecurityAttributes.ownedBy("transition-owner").toRecord
            )
          )
          val request =
            _request(
              rootid,
              "open",
              RootPatch("claimed", Some(successorid)),
              _create_successor(successorid)
            )
          val readauthorization =
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some(_rootcollection.name),
              collectionName = Some(_rootcollection.name),
              targetId = Some(rootid),
              accessKind = "read"
            )
          val updateauthorization =
            readauthorization.copy(accessKind = "update")

          When("the provider returns the concurrently secured root as NotMatched")
          val result =
            _interpret(
              fixture,
              request,
              Some(readauthorization),
              Some(updateauthorization)
            )

          Then("post-result authorization denies the root payload")
          result shouldBe a[Consequence.Failure[_]]
          result match {
            case Consequence.Failure(conclusion) =>
              ConclusionDiagnostics
                .classify(conclusion)
                .diagnosticKey shouldBe "permission"
            case _ =>
              fail("expected post-result read authorization failure")
          }
          _raw_root(fixture, rootid).map(_.map { record =>
            record.getString("status") ->
              record.getLong(EntityConcurrencyMetadata.STORAGE_FIELD_NAME)
          }) shouldBe Consequence.success(Some(Some("closed") -> Some(2L)))
          _raw_root(fixture, successorid) shouldBe Consequence.success(None)
        }
      }
  }

  private final case class Fixture(
    datastorespace: DataStoreSpace,
    entitystorespace: EntityStoreSpace,
    context: ExecutionContext
  )

  private def _fixture(
    hook: TransitionValidationHook = TransitionValidationHook.noop,
    datastore: DataStore = DataStore.inMemorySearchable(),
    principalid: Option[String] = None
  ): Fixture = {
    val datastorespace = new DataStoreSpace().addDataStore(datastore)
    val entitystorespace =
      new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val observability = ObservabilityContext(
      traceId = TraceId("test", "uow_conditional_transition"),
      spanId = None,
      correlationId = None
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "uow-conditional-transition-runtime",
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
      token = "uow-conditional-transition-runtime-context",
      transitionValidationHook = hook
    )
    val effectivecontext =
      principalid.fold(context) { principalvalue =>
        context match {
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
                  Set(Capability("user")),
                  SecurityLevel("test")
                )
              )
            )
          case _ =>
            context
          }
      }
    EntityRevisionSpecSupport.registerRevisionBinding(
      effectivecontext,
      _rootcollection,
      _root_persistent,
      EntityRevisionRepresentation.Detached
    )
    EntityRevisionSpecSupport.registerRevisionBinding(
      effectivecontext,
      _successorcollection,
      _successor_persistent,
      EntityRevisionRepresentation.Detached
    )
    Fixture(datastorespace, entitystorespace, effectivecontext)
  }

  private def _seed_root(
    fixture: Fixture,
    root: Root,
    supplemental: Record = Record.empty
  ): Unit = {
    given ExecutionContext = fixture.context
    val _ = fixture.datastorespace.inject(
      DataStore.CollectionId.EntityStore(_rootcollection),
      EntityConcurrencyMetadata.initializeForCreate(
        _root_persistent.toStoreRecord(root) ++ supplemental
      )
    )
  }

  private def _seed_successor(
    fixture: Fixture,
    successor: Successor,
    supplemental: Record = Record.empty
  ): Unit = {
    given ExecutionContext = fixture.context
    val _ = fixture.datastorespace.inject(
      DataStore.CollectionId.EntityStore(_successorcollection),
      EntityConcurrencyMetadata.initializeForCreate(
        _successor_persistent.toStoreRecord(successor) ++ supplemental
      )
    )
  }

  private def _request(
    rootid: EntityId,
    status: String,
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
          field.expected(status).TAKE
        )
        .TAKE
    EntityConditionalTransition
      .create(rootid, expectation, patch, successor)(using _root_patch)
      .TAKE
  }

  private def _raw_request(
    rootid: EntityId,
    patch: RawPatch,
    successorid: EntityId
  ): EntityConditionalTransition[Root, RawPatch, Successor] = {
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
          field.expected("open").TAKE
        )
        .TAKE
    EntityConditionalTransition
      .create(
        rootid,
        expectation,
        patch,
        _create_successor(successorid)
      )(using _raw_patch)
      .TAKE
  }

  private def _create_successor(
    id: EntityId
  ): EntitySuccessorIntent[Successor] =
    EntitySuccessorIntent
      .create[Successor, Successor](
        Successor(id, "created")
      )(using _successor_create, _successor_persistent)
      .TAKE

  private def _interpret[P](
    fixture: Fixture,
    request: EntityConditionalTransition[Root, P, Successor],
    rootreadauthorization: Option[UnitOfWorkAuthorization] = None,
    rootupdateauthorization: Option[UnitOfWorkAuthorization] = None,
    successorauthorization: Option[UnitOfWorkAuthorization] = None
  ): Consequence[EntityConditionalTransitionResult[Root, Successor]] =
    new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
      .interpret(
        UnitOfWorkOp.EntityStoreConditionalTransition(
          request,
          DataStoreComponentOwner.create("phase49-test").TAKE,
          rootreadauthorization,
          rootupdateauthorization,
          successorauthorization
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

  private final case class RawPatch(
    record: Record
  )

  private final case class Successor(
    id: EntityId,
    label: String
  )

  private final case class SuccessorDraft(
    label: String
  )

  private val _root_persistent: EntityPersistent[Root] =
    new EntityPersistent[Root] {
      def id(entity: Root): EntityId = entity.id
      def toRecord(entity: Root): Record =
        Record.dataAuto(
          "id" -> entity.id,
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
        _rootcollection
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

  private val _raw_patch: EntityPersistentUpdate[RawPatch] =
    new EntityPersistentUpdate[RawPatch] {
      def collection(entity: RawPatch): EntityCollectionId = {
        val _ = entity
        _rootcollection
      }
      def toRecord(entity: RawPatch): Record =
        entity.record
      def fromRecord(record: Record): Consequence[RawPatch] =
        Consequence.success(RawPatch(record))
    }

  private val _successor_persistent: EntityPersistent[Successor] =
    new EntityPersistent[Successor] {
      def id(entity: Successor): EntityId = entity.id
      def toRecord(entity: Successor): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "label" -> entity.label
        )
      def fromRecord(record: Record): Consequence[Successor] =
        (record.getAs[EntityId]("id"), record.getString("label")) match {
          case (Some(id), Some(label)) =>
            Consequence.success(Successor(id, label))
          case _ =>
            Consequence.argumentInvalid(
              "successor",
              "id and label",
              record
            )
        }
    }

  private val _successor_create: EntityPersistentCreate[Successor] =
    EntityPersistentCreate.fromPersistent(_successor_persistent)

  private final class CountingSuccessorCreate
      extends EntityPersistentCreate[Successor] {
    private var _collection_count = 0
    private var _id_count = 0

    def collectionCount: Int =
      _collection_count

    def idCount: Int =
      _id_count

    def collection(entity: Successor): EntityCollectionId = {
      _collection_count = _collection_count + 1
      entity.id.collection
    }

    def id(entity: Successor): Option[EntityId] = {
      _id_count = _id_count + 1
      Some(entity.id)
    }

    def toRecord(entity: Successor): Record =
      _successor_persistent.toRecord(entity)
  }

  private final class CountingSuccessorDraftCreate
      extends EntityPersistentCreate[SuccessorDraft] {
    private var _collection_count = 0
    private var _id_count = 0

    def collectionCount: Int =
      _collection_count

    def idCount: Int =
      _id_count

    def collection(entity: SuccessorDraft): EntityCollectionId = {
      val _ = entity
      _collection_count = _collection_count + 1
      _successorcollection
    }

    def id(entity: SuccessorDraft): Option[EntityId] = {
      val _ = entity
      _id_count = _id_count + 1
      None
    }

    def toRecord(entity: SuccessorDraft): Record =
      Record.dataAuto("label" -> entity.label)
  }

  private final class RejectingConditionalHook
      extends TransitionValidationHook {
    private var _before_update_by_id_count = 0

    def beforeUpdateByIdCount: Int =
      _before_update_by_id_count

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
      _before_update_by_id_count = _before_update_by_id_count + 1
      Consequence.stateConflict("conditional transition rejected by hook")
    }
  }

  private final class AdvancingSuccessorHook(
    successorid: EntityId
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
        collection <- context.entityStoreSpace.dataStoreCollection(successorid)
        entry <- context.entityStoreSpace.dataStoreEntryId(successorid)
        datastore <- context.dataStoreSpace.dataStore(collection)
        _ <- datastore.update(
          collection,
          entry,
          Record.dataAuto(
            EntityConcurrencyMetadata.STORAGE_FIELD_NAME -> 2L
          )
        )
      } yield ()
    }
  }

  private final class CapturingDataStore
      extends DataStore.InMemoryDataStore(CommitRecorder.noop) {
    private var _last_plan: Option[DataStoreConditionalTransitionPlan] =
      None

    def lastPlan: Option[DataStoreConditionalTransitionPlan] =
      _last_plan

    override def conditionalTransition(
      plan: DataStoreConditionalTransitionPlan
    )(using
      context: ExecutionContext
    ): Consequence[DataStoreConditionalTransitionResult] = {
      _last_plan = Some(plan)
      super.conditionalTransition(plan)
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
}
