package org.goldenport.cncf.unitofwork

import cats.~>
import cats.data.State
import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentOrigin}
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
  DataStoreSpace,
  EntityCompareAndSetMutationPlan,
  EntityDirectMutationPlan,
  EntityMutationProviderReadback,
  EntityMutationProviderResult,
  EntityMutationProviderCapabilities,
  EntityMutationReadbackRequirement,
  EntityVersionedMutationDataStore,
  EntityVersionedMutationPlan,
  EntityVersionedMutationResult
}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.runtime.*
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.cncf.security.EntityAccessMode
import org.goldenport.cncf.statemachine.TransitionValidationHook
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.simplemodeling.model.directive.Update

/*
 * @since   Jul. 24, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkVersionedMutationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _collectionid =
    EntityCollectionId("test", "phase49", "versioned_person")

  "UnitOfWork Entity versioned mutation" should {
    "route provider-native and guarded patches" which {
      "use acknowledgment-only native mutation for an unversioned internal patch" in {
        Given(
          "a revision-managed Entity and an internal patch route that returns no business record"
        )
        val datastore          = new RecordingNativeDataStore
        val fixture            = _fixture(EntityConcurrencyPolicy.None, datastore)
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "native_acknowledgment", _collectionid)
        val interpreter        = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val created = interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(
            VersionedPersonCreate(id, "before"),
            _create_persistent
          )
        )
        datastore.resetObservation()

        When("the unversioned internal patch executes through UnitOfWork")
        val updated = created.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdateByIdUnversioned(
              id,
              VersionedPersonPatch(Update.set("after")),
              EntityUnversionedMutationPurpose.FrameworkBootstrap,
              _patch_persistent,
              Some(
                UnitOfWorkAuthorization(
                  resourceFamily = "domain",
                  resourceType = Some("VersionedPerson"),
                  targetId = Some(id),
                  accessKind = "update",
                  accessMode = EntityAccessMode.System
                )
              )
            )
          )
        )

        Then(
          "the provider performs one direct mutation without target reads or authoritative readback"
        )
        updated shouldBe Consequence.unit
        datastore.directCount shouldBe 1
        datastore.guardedCount shouldBe 0
        datastore.loadCount shouldBe 0
        datastore.lastDirectReadbackRequirement shouldBe
          Some(EntityMutationReadbackRequirement.None)
        _raw_record(fixture.datastorespace, id)
          .map(_.flatMap(_.getString("name"))) shouldBe
          Consequence.success(Some("after"))
      }

      "route an ordinary managed patch directly and reconcile its authoritative result" in {
        Given(
          "a None-concurrency Entity and a recording provider with native direct mutation"
        )
        val datastore           = new RecordingNativeDataStore
        val fixture             = _fixture(EntityConcurrencyPolicy.None, datastore)
        given ExecutionContext  = fixture.context
        val id                  = EntityId("test", "native_direct", _collectionid)
        val interpreter         = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val authorization =
          Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("VersionedPerson"),
              targetId = Some(id),
              accessKind = "update",
              accessMode = EntityAccessMode.ServiceInternal
            )
          )
        val created = interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(
            VersionedPersonCreate(id, "before"),
            _create_persistent
          )
        )
        datastore.resetObservation()

        When("the ordinary patch executes through UnitOfWork")
        val updated = created.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdateById(
              id,
              VersionedPersonPatch(Update.set("after")),
              _patch_persistent,
              authorization
            )
          )
        )

        Then(
          "the provider performs one native mutation with no target load and the authoritative value becomes resident"
        )
        updated.map(_.getString("name")) shouldBe
          Consequence.success(Some("after"))
        datastore.directCount shouldBe 1
        datastore.compareAndSetCount shouldBe 0
        datastore.guardedCount shouldBe 0
        datastore.loadCount shouldBe 0
        fixture.collection.resolve(id).map(value =>
          value.name -> value.revision.value
        ) shouldBe Consequence.success("after" -> 2L)
      }

      "route a managed optimistic patch through native compare-and-set after one revision load" in {
        Given(
          "an Optimistic Entity whose application patch carries no observed revision"
        )
        val datastore          = new RecordingNativeDataStore
        val fixture            = _fixture(EntityConcurrencyPolicy.Optimistic, datastore)
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "native_managed_cas", _collectionid)
        val interpreter        = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val created = interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(
            VersionedPersonCreate(id, "before"),
            _create_persistent
          )
        )
        datastore.resetObservation()

        When("CNCF obtains the managed revision and applies the application patch")
        val updated = created.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdateById(
              id,
              VersionedPersonPatch(Update.set("after")),
              _patch_persistent
            )
          )
        )

        Then(
          "one authoritative revision load feeds one provider-native CAS without guarded fallback"
        )
        updated.map(_.getString("name")) shouldBe
          Consequence.success(Some("after"))
        datastore.directCount shouldBe 0
        datastore.compareAndSetCount shouldBe 1
        datastore.guardedCount shouldBe 0
        datastore.loadCount shouldBe 1
        fixture.collection.resolve(id).map(value =>
          value.name -> value.revision.value
        ) shouldBe Consequence.success("after" -> 2L)
      }

      "route an observed managed patch through compare-and-set and evict stale resident state" in {
        Given(
          "an Optimistic Entity and two patch attempts carrying the same observed revision"
        )
        val datastore          = new RecordingNativeDataStore
        val fixture            = _fixture(EntityConcurrencyPolicy.Optimistic, datastore)
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "native_cas", _collectionid)
        val interpreter        = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val authorization =
          Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("VersionedPerson"),
              targetId = Some(id),
              accessKind = "update",
              accessMode = EntityAccessMode.ServiceInternal
            )
          )
        val created = interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(
            VersionedPersonCreate(id, "before"),
            _create_persistent
          )
        )
        datastore.resetObservation()

        When("the first compare-and-set succeeds and the second is stale")
        val first = created.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdateByIdObserved(
              id,
              VersionedPersonPatch(Update.set("first")),
              EntityRevision.INITIAL,
              _patch_persistent,
              authorization
            )
          )
        )
        val stale = first.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdateByIdObserved(
              id,
              VersionedPersonPatch(Update.set("stale")),
              EntityRevision.INITIAL,
              _patch_persistent,
              authorization
            )
          )
        )

        Then(
          "both attempts use provider-native CAS without a target load and stale removes the resident projection"
        )
        first.map(_.record.getString("name")) shouldBe
          Consequence.success(Some("first"))
        stale shouldBe a[Consequence.Failure[?]]
        datastore.directCount shouldBe 0
        datastore.compareAndSetCount shouldBe 2
        datastore.guardedCount shouldBe 0
        datastore.loadCount shouldBe 0
        fixture.collection.resolve(id) shouldBe a[Consequence.Failure[?]]
      }

      "preserve logical deletion while using the provider-native patch route" in {
        Given(
          "a managed Entity whose deletedAt is present while aliveness remains alive"
        )
        val datastore          = new RecordingNativeDataStore
        val fixture            = _fixture(EntityConcurrencyPolicy.None, datastore)
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "native_deleted", _collectionid)
        val interpreter        = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val authorization =
          Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("VersionedPerson"),
              targetId = Some(id),
              accessKind = "update",
              accessMode = EntityAccessMode.ServiceInternal
            )
          )
        val deleted =
          interpreter
            .interpret(
              UnitOfWorkOp.EntityStoreCreate(
                VersionedPersonCreate(id, "before"),
                _create_persistent
              )
            )
            .flatMap(_ =>
              datastore.update(
                DataStore.CollectionId.EntityStore(_collectionid),
                DataStore.EntryId(id),
                Record.dataAuto(
                  "aliveness" -> "alive",
                  "deleted_at" -> "2026-07-26T00:00:00Z"
                )
              )
            )
        datastore.resetObservation()

        When("the patch reaches the native provider admission guard")
        val result = deleted.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdateById(
              id,
              VersionedPersonPatch(Update.set("forbidden")),
              _patch_persistent,
              authorization
            )
          )
        )

        Then("the update is rejected without reviving or changing the Entity")
        result shouldBe a[Consequence.Failure[?]]
        datastore.directCount shouldBe 1
        datastore.compareAndSetCount shouldBe 0
        datastore.guardedCount shouldBe 0
        datastore.loadCount shouldBe 0
        _raw_record(fixture.datastorespace, id)
          .map(_.flatMap(_.getString("name"))) shouldBe
          Consequence.success(Some("before"))
        fixture.collection.resolve(id) shouldBe a[Consequence.Failure[?]]
      }

      "retain one authorization read while avoiding an EntityStore duplicate read" in {
        Given(
          "a user-authorized ordinary patch whose object permission requires the current record"
        )
        val datastore          = new RecordingNativeDataStore
        val fixture            = _fixture(EntityConcurrencyPolicy.None, datastore)
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "native_authorized", _collectionid)
        val interpreter        = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val authorization =
          Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("VersionedPerson"),
              targetId = Some(id),
              accessKind = "update",
              accessMode = EntityAccessMode.UserPermission
            )
          )
        val created = interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(
            VersionedPersonCreate(id, "before"),
            _create_persistent
          )
        )
        datastore.resetObservation()

        When("UnitOfWork authorizes and applies the patch")
        val result = created.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdateById(
              id,
              VersionedPersonPatch(Update.set("after")),
              _patch_persistent,
              authorization
            )
          )
        )

        Then("authorization performs one load and native mutation performs no second pre-read")
        result shouldBe a[Consequence.Success[?]]
        datastore.loadCount shouldBe 1
        datastore.directCount shouldBe 1
        datastore.guardedCount shouldBe 0
      }

      "preserve the authorization-loaded managed base revision through native compare-and-set" in {
        Given(
          "an Optimistic user-authorized patch and a concurrent write after its authorization load"
        )
        val datastore          = new RecordingNativeDataStore
        val fixture            = _fixture(EntityConcurrencyPolicy.Optimistic, datastore)
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "authorized_managed_base", _collectionid)
        val interpreter        = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val authorization =
          Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("VersionedPerson"),
              targetId = Some(id),
              accessKind = "update",
              accessMode = EntityAccessMode.UserPermission
            )
          )
        val created = interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(
            VersionedPersonCreate(id, "before"),
            _create_persistent
          )
        )
        datastore.resetObservation()
        datastore.mutateAfterNextLoad(
          EntityDirectMutationPlan(
            DataStore.CollectionId.EntityStore(_collectionid),
            DataStore.EntryId(id),
            EntityRevisionRepresentation.Embedded.storageFieldName,
            Record.data("name" -> "concurrent")
          )
        )

        When("the authorized patch reaches provider-native compare-and-set")
        val result = created.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdateById(
              id,
              VersionedPersonPatch(Update.set("after")),
              _patch_persistent,
              authorization
            )
          )
        )

        Then(
          "the original authorized revision is preserved and the concurrent write makes the patch stale"
        )
        datastore.afterLoadMutationResult shouldBe
          Some(
            Consequence.success(
              EntityMutationProviderResult.Applied(
                EntityMutationProviderReadback.Omitted
              )
            )
          )
        result shouldBe a[Consequence.Failure[?]]
        datastore.loadCount shouldBe 1
        datastore.compareAndSetCount shouldBe 1
        datastore.guardedCount shouldBe 0
        _raw_record(fixture.datastorespace, id)
          .map(_.map(record =>
            record.getString("name") ->
              record.getAny(EntityRevisionRepresentation.Embedded.storageFieldName)
          )) shouldBe
          Consequence.success(Some(Some("concurrent") -> Some(2L)))
        fixture.collection.resolve(id) shouldBe a[Consequence.Failure[?]]
      }

      "preserve a validated missing managed base when the target is concurrently created" in {
        Given(
          "a transition-validated patch whose target is created after the validation load reports missing"
        )
        val datastore = new RecordingNativeDataStore
        val fixture = _fixture(
          EntityConcurrencyPolicy.Optimistic,
          datastore,
          _current_aware_transition_validation_hook
        )
        given ExecutionContext = fixture.context
        val id = EntityId("test", "validated_missing_base", _collectionid)
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        datastore.createAfterNextLoad(
          DataStore.CollectionId.EntityStore(_collectionid),
          DataStore.EntryId(id),
          _persistent.toStoreRecord(VersionedPerson(id, "concurrent"))
        )

        When("the managed patch continues after the concurrent create")
        val result = interpreter.interpret(
          UnitOfWorkOp.EntityStoreUpdateById(
            id,
            VersionedPersonPatch(Update.set("after")),
            _patch_persistent,
            None
          )
        )

        Then(
          "the validated missing base remains authoritative and the concurrent entity is not updated"
        )
        result shouldBe a[Consequence.Failure[?]]
        datastore.afterLoadCreateResult shouldBe Some(Consequence.unit)
        datastore.compareAndSetCount shouldBe 0
        datastore.guardedCount shouldBe 0
        _raw_record(fixture.datastorespace, id)
          .map(_.map(record =>
            record.getString("name") ->
              record.getAny(EntityRevisionRepresentation.Embedded.storageFieldName)
          )) shouldBe
          Consequence.success(Some(Some("concurrent") -> Some(1L)))
      }

      "keep WriteIfChanged on the guarded business-state path" in {
        Given(
          "a managed patch selecting WriteIfChanged on a native-capable provider"
        )
        val datastore          = new RecordingNativeDataStore
        val fixture            = _fixture(EntityConcurrencyPolicy.None, datastore)
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "guarded_write_if_changed", _collectionid)
        val interpreter        = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val created = interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(
            VersionedPersonCreate(id, "before"),
            _create_persistent
          )
        )
        datastore.resetObservation()

        When("the patch executes with business-state comparison")
        val result = created.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdateById(
              id,
              VersionedPersonPatch(Update.set("before")),
              _patch_persistent,
              authorization = Some(
                UnitOfWorkAuthorization(
                  resourceFamily = "domain",
                  resourceType = Some("VersionedPerson"),
                  targetId = Some(id),
                  accessKind = "update",
                  accessMode = EntityAccessMode.ServiceInternal
                )
              ),
              executionPolicy = EntityMutationExecutionPolicy(
                concurrencyPolicy = EntityConcurrencyPolicy.None,
                writePolicy = EntityWritePolicy.WriteIfChanged
              )
            )
          )
        )

        Then("the guarded provider compares authoritative state and reports the no-op")
        result shouldBe a[Consequence.Success[?]]
        datastore.directCount shouldBe 0
        datastore.compareAndSetCount shouldBe 0
        datastore.guardedCount shouldBe 1
        datastore.loadCount shouldBe 1
      }

      "fall back to guarded mutation when the provider lacks native capability" in {
        Given("a None-concurrency Entity on a guarded-only provider")
        val datastore          = new RecordingGuardedDataStore
        val fixture            = _fixture(EntityConcurrencyPolicy.None, datastore)
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "guarded_provider", _collectionid)
        val interpreter        = new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val created = interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(
            VersionedPersonCreate(id, "before"),
            _create_persistent
          )
        )
        datastore.resetObservation()

        When("the ordinary patch is planned")
        val result = created.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdateById(
              id,
              VersionedPersonPatch(Update.set("after")),
              _patch_persistent,
              Some(
                UnitOfWorkAuthorization(
                  resourceFamily = "domain",
                  resourceType = Some("VersionedPerson"),
                  targetId = Some(id),
                  accessKind = "update",
                  accessMode = EntityAccessMode.ServiceInternal
                )
              )
            )
          )
        )

        Then("the planner preserves semantics through the guarded provider contract")
        result shouldBe a[Consequence.Success[?]]
        datastore.directCount shouldBe 0
        datastore.compareAndSetCount shouldBe 0
        datastore.guardedCount shouldBe 1
        datastore.loadCount shouldBe 1
      }

    }

    "reconcile authoritative and resident Entity state" which {
      "decode an embedded create result before installing it in the working set" in {
        Given(
          "an Embedded Entity collection with a memory realm and a create payload without managed revision"
        )
        val fixture            = _fixture()
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "created", _collectionid)
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))

        When("the create result crosses the UnitOfWork working-set boundary")
        val created = interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(
            VersionedPersonCreate(id, "created"),
            _create_persistent
          )
        )

        Then(
          "the persisted Embedded revision is decoded and the domain Entity becomes resident"
        )
        created.map(_.record.flatMap(_.getAny("revision"))) shouldBe
          Consequence.success(Some(1L))
        fixture.collection.resolve(id) shouldBe
          Consequence.success(VersionedPerson(id, "created"))
      }

      "install an authoritative embedded patch result without an extra reload" in {
        Given(
          "an Embedded Entity collection whose ordinary patch result omits the managed revision"
        )
        val fixture            = _fixture()
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "embedded_patch", _collectionid)
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val created = interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(
            VersionedPersonCreate(id, "before"),
            _create_persistent
          )
        )

        When("the ordinary patch route updates the revision-managed Entity")
        val updated = created.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdateById(
              id,
              VersionedPersonPatch(Update.set("after")),
              _patch_persistent
            )
          )
        )

        Then(
          "the domain result stays revision-free while the authoritative resident Entity is decoded from persisted storage"
        )
        updated.map(_.getAny("revision")) shouldBe
          Consequence.success(None)
        fixture.collection.resolve(id).map(value =>
          value.name -> value.revision.value
        ) shouldBe Consequence.success("after" -> 2L)
      }

      "return and install the authoritative snapshot only after provider success" in {
        Given("one persisted Entity and its component-scoped working set")
        val fixture            = _fixture()
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "authoritative", _collectionid)
        val initial            = VersionedPerson(id, "before")
        val _ = fixture.datastorespace.inject(
          DataStore.CollectionId.EntityStore(_collectionid),
          _persistent.toStoreRecord(initial)
        )
        fixture.collection.put(initial)
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))

        When("the snapshot load and full save cross the UnitOfWork boundary")
        val admitted = interpreter.interpret(
          UnitOfWorkOp.EntityStoreLoadSnapshot(id, _persistent)
        ).flatMap(value =>
          Consequence.successOrEntityNotFound(value)(id)
        )
        val saved = admitted.flatMap(snapshot =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreSave(
              VersionedPerson(id, "after"),
              snapshot.revision,
              _persistent
            )
          )
        )

        Then("the provider result advances once and becomes the resident value")
        saved.map(_.entity.name) shouldBe Consequence.success("after")
        saved.map(_.revision) should not be admitted.map(_.revision)
        fixture.collection.resolve(id).map(_.name) shouldBe
          Consequence.success("after")
      }

      "evict a stale resident without replacing authoritative storage" in {
        Given("two candidates admitted from the same Entity snapshot")
        val fixture            = _fixture()
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "stale", _collectionid)
        val initial            = VersionedPerson(id, "before")
        val _ = fixture.datastorespace.inject(
          DataStore.CollectionId.EntityStore(_collectionid),
          _persistent.toStoreRecord(initial)
        )
        fixture.collection.put(initial)
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val admitted = interpreter.interpret(
          UnitOfWorkOp.EntityStoreLoadSnapshot(id, _persistent)
        ).flatMap(value =>
          Consequence.successOrEntityNotFound(value)(id)
        )
        val first = admitted.flatMap(snapshot =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreUpdate(
              VersionedPerson(id, "first"),
              snapshot.revision,
              _persistent
            )
          )
        )

        When("the second candidate reuses the stale expectation")
        val stale = first.flatMap(_ =>
          admitted.flatMap(snapshot =>
            interpreter.interpret(
              UnitOfWorkOp.EntityStoreUpdate(
                VersionedPerson(id, "stale"),
                snapshot.revision,
                _persistent
              )
            )
          )
        )

        Then("the conflict is preserved and the stale resident is removed")
        stale shouldBe a[Consequence.Failure[_]]
        fixture.collection.resolve(id) shouldBe a[Consequence.Failure[_]]
        fixture.entitystorespace
          .loadSnapshot(id, _persistent)
          .map(_.map(_.entity.name)) shouldBe
          Consequence.success(Some("first"))
      }

      "evict resident state when projection fails after the provider committed" in {
        Given("a versioned mutation whose result decoder fails after datastore success")
        val fixture            = _fixture()
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "projection_failure", _collectionid)
        val initial            = VersionedPerson(id, "before")
        val _ = fixture.datastorespace.inject(
          DataStore.CollectionId.EntityStore(_collectionid),
          _persistent.toStoreRecord(initial)
        )
        fixture.collection.put(initial)
        var projectedname = "before"
        var querycount    = 0
        val viewcollection =
          new ViewCollection[Record](new ViewBuilder[Record] {
            def build(targetId: EntityId): Consequence[Record] =
              Consequence.success(
                Record.dataAuto("id" -> targetId, "name" -> projectedname)
              )
          })
        fixture.component.viewSpace.register(
          _collectionid.name,
          viewcollection,
          Browser.from(
            viewcollection,
            _ => {
              querycount += 1
              Consequence.success(
                Vector(Record.dataAuto("id" -> id, "name" -> projectedname))
              )
            }
          )
        )
        val primed = fixture.component.viewSpace
          .browser[Record](_collectionid.name)
          .query(Query(Record.empty))
        primed shouldBe a[Consequence.Success[?]]
        querycount shouldBe 1
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val admitted = fixture.entitystorespace
          .loadSnapshot(id, _persistent)
          .flatMap(value => Consequence.successOrEntityNotFound(value)(id))
        val failingpersistent = new EntityPersistent[VersionedPerson] {
          def id(entity: VersionedPerson): EntityId = entity.id
          def toRecord(entity: VersionedPerson): Record =
            _persistent.toRecord(entity)
          override def toStoreRecord(entity: VersionedPerson): Record =
            _persistent.toStoreRecord(entity)
          def fromRecord(record: Record): Consequence[VersionedPerson] =
            Consequence.operationInvalid("projection decoder failed")
        }

        When("the provider applies the mutation but authoritative hydration fails")
        projectedname = "committed"
        val result = admitted.flatMap { snapshot =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreSave(
              VersionedPerson(id, "committed"),
              snapshot.revision,
              failingpersistent
            )
          )
        }

        Then("the structured failure reports committed projection and evicts resident state")
        result shouldBe a[Consequence.Failure[_]]
        result match {
          case Consequence.Failure(conclusion) =>
            ConclusionDiagnostics.classify(conclusion).reason shouldBe
              Some("committed-entity-projection-failure")
          case _ =>
            fail("expected committed projection failure")
        }
        fixture.collection.resolve(id) shouldBe a[Consequence.Failure[_]]
        val refreshed = fixture.component.viewSpace
          .browser[Record](_collectionid.name)
          .query(Query(Record.empty))
        refreshed.map(_.headOption.flatMap(_.getString("name"))) shouldBe
          Consequence.success(Some("committed"))
        querycount shouldBe 2

        And("the committed datastore value remains authoritative and is not retried")
        fixture.entitystorespace
          .loadSnapshot(id, _persistent)
          .map(_.map(_.entity.name)) shouldBe
          Consequence.success(Some("committed"))
      }

    }

    "preserve managed-save and admission policies" which {
      "preserve managed-save write and assembled concurrency policies" in {
        Given("one optimistic Entity and a stale copy admitted before any managed save")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val id = EntityId("test", "managed_policy", _collectionid)
        val initial = VersionedPerson(id, "before")
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val created = interpreter.interpret(
          UnitOfWorkOp.EntityStoreCreate(
            VersionedPersonCreate(id, "before"),
            _create_persistent
          )
        )
        val writeifchanged = EntityMutationExecutionPolicy(
          writePolicy = EntityWritePolicy.WriteIfChanged
        )

        When("an equal managed save is deduplicated and the stale copy is later reused")
        val unchanged = created.flatMap { _ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreSaveManaged(
              initial,
              _persistent,
              executionPolicy = writeifchanged
            )
          )
        }
        val first = unchanged.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreSaveManaged(
              initial.copy(name = "first"),
              _persistent
            )
          )
        )
        val stale = first.flatMap(_ =>
          interpreter.interpret(
            UnitOfWorkOp.EntityStoreSaveManaged(
              initial.copy(name = "stale"),
              _persistent
            )
          )
        )

        Then("the no-op keeps revision one and assembled Optimistic rejects the stale save")
        unchanged.map(_.revision.value) shouldBe Consequence.success(1L)
        first.map(_.revision.value) shouldBe Consequence.success(2L)
        stale shouldBe a[Consequence.Failure[?]]
        fixture.entitystorespace
          .loadSnapshot(id, _persistent)
          .map(_.map(value => value.entity.name -> value.revision.value)) shouldBe
          Consequence.success(Some("first" -> 2L))
      }

      "classify managed-save decoding failure after provider commit" in {
        Given("a managed save whose authoritative decoder fails after datastore success")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val id = EntityId("test", "managed_projection_failure", _collectionid)
        val initial = VersionedPerson(id, "before")
        val _ = fixture.datastorespace.inject(
          DataStore.CollectionId.EntityStore(_collectionid),
          _persistent.toStoreRecord(initial)
        )
        fixture.collection.put(initial)
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val failingpersistent = new EntityPersistent[VersionedPerson] {
          def id(entity: VersionedPerson): EntityId = entity.id
          def toRecord(entity: VersionedPerson): Record =
            _persistent.toRecord(entity)
          override def toStoreRecord(entity: VersionedPerson): Record =
            _persistent.toStoreRecord(entity)
          def fromRecord(record: Record): Consequence[VersionedPerson] =
            Consequence.operationInvalid("managed projection decoder failed")
        }

        When("the managed mutation commits before authoritative readback fails")
        val result = interpreter.interpret(
          UnitOfWorkOp.EntityStoreSaveManaged(
            initial.copy(name = "committed"),
            failingpersistent
          )
        )

        Then("the failure is marked as committed and resident state is evicted")
        result shouldBe a[Consequence.Failure[?]]
        result match {
          case Consequence.Failure(conclusion) =>
            ConclusionDiagnostics.classify(conclusion).reason shouldBe
              Some("committed-entity-projection-failure")
          case _ =>
            fail("expected committed managed projection failure")
        }
        fixture.collection.resolve(id) shouldBe a[Consequence.Failure[?]]

        And("the committed datastore value remains authoritative")
        fixture.entitystorespace
          .loadSnapshot(id, _persistent)
          .map(_.map(value => value.entity.name -> value.revision.value)) shouldBe
          Consequence.success(Some("committed" -> 2L))
      }

      "preserve explicit optimistic policy through a None-policy collection" in {
        Given(
          "a None-policy collection and two UnitOfWork saves carrying the same explicit revision"
        )
        val fixture = _fixture(EntityConcurrencyPolicy.None)
        given ExecutionContext = fixture.context
        val id = EntityId("test", "explicit_optimistic", _collectionid)
        val initial = VersionedPerson(id, "before")
        val _ = fixture.datastorespace.inject(
          DataStore.CollectionId.EntityStore(_collectionid),
          _persistent.toStoreRecord(initial)
        )
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))
        val revision = EntityRevision.INITIAL

        When("the first save commits and the second save reuses its stale explicit revision")
        val first = interpreter.interpret(
          UnitOfWorkOp.EntityStoreSave(
            VersionedPerson(id, "first"),
            revision,
            _persistent
          )
        )
        val stale = interpreter.interpret(
          UnitOfWorkOp.EntityStoreSave(
            VersionedPerson(id, "stale"),
            revision,
            _persistent
          )
        )

        Then("the explicit overload remains optimistic at the provider boundary")
        first shouldBe a[Consequence.Success[?]]
        stale shouldBe a[Consequence.Failure[?]]
        fixture.entitystorespace
          .loadSnapshot(id, _persistent)
          .map(_.map(_.entity.name)) shouldBe
          Consequence.success(Some("first"))
      }

      "reject an unversioned framework mutation without System admission" in {
        Given("an explicitly classified framework-bootstrap save")
        val fixture            = _fixture()
        given ExecutionContext = fixture.context
        val id                 = EntityId("test", "unversioned", _collectionid)
        val _ = fixture.datastorespace.inject(
          DataStore.CollectionId.EntityStore(_collectionid),
          _persistent.toStoreRecord(VersionedPerson(id, "before"))
        )
        val interpreter =
          new UnitOfWorkInterpreter(new UnitOfWork(fixture.context))

        When("ServiceInternal access attempts the unversioned route")
        val result = interpreter.interpret(
          UnitOfWorkOp.EntityStoreSaveUnversioned(
            VersionedPerson(id, "after"),
            EntityUnversionedMutationPurpose.FrameworkBootstrap,
            _persistent,
            Some(
              UnitOfWorkAuthorization(
                resourceFamily = "domain",
                resourceType = Some("VersionedPerson"),
                targetId = Some(id),
                accessKind = "update",
                accessMode = EntityAccessMode.ServiceInternal
              )
            )
          )
        )

        Then("the UnitOfWork boundary rejects the bypass before storage changes")
        result shouldBe a[Consequence.Failure[_]]
        _raw_record(fixture.datastorespace, id)
          .map(_.flatMap(_.getString("name"))) shouldBe
          Consequence.success(Some("before"))
        _raw_record(fixture.datastorespace, id)
          .map(
            _.flatMap(
              _.getAny(EntityConcurrencyMetadata.STORAGE_FIELD_NAME)
            )
          ) shouldBe Consequence.success(None)
      }
    }
  }

  private final case class Fixture(
      datastorespace: DataStoreSpace,
      entitystorespace: EntityStoreSpace,
      collection: EntityCollection[VersionedPerson],
      component: Component,
      context: ExecutionContext
  )

  private def _fixture(
    concurrencypolicy: EntityConcurrencyPolicy =
      EntityConcurrencyPolicy.Optimistic,
    datastore: DataStore = DataStore.inMemorySearchable(),
    transitionvalidationhook: TransitionValidationHook =
      TransitionValidationHook.noop
  ): Fixture = {
    val datastorespace = new DataStoreSpace().useDataStore(datastore)
    val entitystorespace =
      new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val component                           = new Component() {}
    given EntityPersistent[VersionedPerson] = _persistent
    val storerealm = new EntityRealm[VersionedPerson](
      entityName = _collectionid.name,
      loader = EntityLoader[VersionedPerson](_ => None),
      state = new IdRef(EntityRealmState(Map.empty))
    )
    val memoryrealm = new PartitionedMemoryRealm[VersionedPerson](
      strategy = PartitionStrategy.byOrganizationMonthUTC,
      idOf = _.id
    )
    val collection = new EntityCollection[VersionedPerson](
      EntityDescriptor(
        _collectionid,
        EntityRuntimePlan(
          entityName = _collectionid.name,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          workingSet = None,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 16,
          concurrencyPolicy = concurrencypolicy
        ),
        _persistent,
        revisionBinding = Some(
          EntityRevisionBinding(EntityRevisionRepresentation.Embedded)
        )
      ),
      EntityStorage(storerealm, Some(memoryrealm))
    )
    component.entitySpace.registerEntity(_collectionid.name, collection)
    val observability = ObservabilityContext(
      traceId = TraceId("test", "uow_versioned_mutation"),
      spanId = None,
      correlationId = None
    )
    val rootscope = ScopeContext(
      ScopeKind.Runtime,
      "uow-versioned-mutation-root",
      None,
      observability
    )
    val componentscope = Component.Context(
      "uow-versioned-mutation-component",
      rootscope,
      component,
      ComponentOrigin.Embed
    )
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "uow-versioned-mutation-runtime",
        parent = Some(componentscope),
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
      token = "uow-versioned-mutation-runtime-context",
      transitionValidationHook = transitionvalidationhook
    )
    val _ = context
    Fixture(datastorespace, entitystorespace, collection, component, context)
  }

  private class RecordingNativeDataStore
      extends DataStore.InMemoryDataStore(
        org.goldenport.cncf.unitofwork.CommitRecorder.noop
      ) {
    private var _direct_count = 0
    private var _compare_and_set_count = 0
    private var _guarded_count = 0
    private var _load_count = 0
    private var _last_direct_readback_requirement =
      Option.empty[EntityMutationReadbackRequirement]
    private var _after_load_plan =
      Option.empty[EntityDirectMutationPlan]
    private var _after_load_mutation_result =
      Option.empty[Consequence[EntityMutationProviderResult]]
    private var _after_load_create =
      Option.empty[(DataStore.CollectionId, DataStore.EntryId, Record)]
    private var _after_load_create_result =
      Option.empty[Consequence[Unit]]

    def directCount: Int = _direct_count
    def compareAndSetCount: Int = _compare_and_set_count
    def guardedCount: Int = _guarded_count
    def loadCount: Int = _load_count
    def lastDirectReadbackRequirement
        : Option[EntityMutationReadbackRequirement] =
      _last_direct_readback_requirement
    def afterLoadMutationResult
        : Option[Consequence[EntityMutationProviderResult]] =
      _after_load_mutation_result
    def afterLoadCreateResult: Option[Consequence[Unit]] =
      _after_load_create_result

    def mutateAfterNextLoad(plan: EntityDirectMutationPlan): Unit =
      _after_load_plan = Some(plan)

    def createAfterNextLoad(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId,
      record: Record
    ): Unit =
      _after_load_create = Some((collection, id, record))

    def resetObservation(): Unit = {
      _direct_count = 0
      _compare_and_set_count = 0
      _guarded_count = 0
      _load_count = 0
      _last_direct_readback_requirement = None
      _after_load_plan = None
      _after_load_mutation_result = None
      _after_load_create = None
      _after_load_create_result = None
    }

    override def load(
      collection: DataStore.CollectionId,
      id: DataStore.EntryId
    )(using ctx: ExecutionContext): Consequence[Option[Record]] = {
      _load_count += 1
      val result = super.load(collection, id)
      result match {
        case _: Consequence.Success[?] =>
          _after_load_plan.foreach { plan =>
            _after_load_plan = None
            _after_load_mutation_result = Some(super.mutateEntityDirect(plan))
          }
          _after_load_create.foreach { case (collection, id, record) =>
            _after_load_create = None
            _after_load_create_result = Some(super.create(collection, id, record))
          }
        case _ =>
          ()
      }
      result
    }

    override def mutateEntityDirect(
      plan: EntityDirectMutationPlan
    )(using
      ctx: ExecutionContext
    ): Consequence[EntityMutationProviderResult] = {
      _direct_count += 1
      _last_direct_readback_requirement = Some(plan.readbackRequirement)
      super.mutateEntityDirect(plan)
    }

    override def compareAndSetEntity(
      plan: EntityCompareAndSetMutationPlan
    )(using
      ctx: ExecutionContext
    ): Consequence[EntityMutationProviderResult] = {
      _compare_and_set_count += 1
      super.compareAndSetEntity(plan)
    }

    override def mutateVersionedEntity(
      plan: EntityVersionedMutationPlan
    )(using
      ctx: ExecutionContext
    ): Consequence[EntityVersionedMutationResult] = {
      _guarded_count += 1
      super.mutateVersionedEntity(plan)
    }
  }

  private final class RecordingGuardedDataStore
      extends RecordingNativeDataStore {
    override def entityMutationProviderCapabilities
        : EntityMutationProviderCapabilities =
      EntityMutationProviderCapabilities.guardedBaseline
  }

  private def _raw_record(
    datastorespace: DataStoreSpace,
    id: EntityId
  )(using ExecutionContext): Consequence[Option[Record]] =
    for {
      datastore <- datastorespace.dataStore(
        DataStore.CollectionId.EntityStore(id.collection)
      )
      record <- datastore.load(
        DataStore.CollectionId.EntityStore(id.collection),
        DataStore.EntryId(id)
      )
    } yield record

  private final case class VersionedPerson(
    id: EntityId,
    name: String,
    revision: EntityRevision = EntityRevision.INITIAL
  )

  private final case class VersionedPersonCreate(
    id: EntityId,
    name: String
  )

  private final case class VersionedPersonPatch(
    name: Update[String]
  )

  private val _current_aware_transition_validation_hook =
    new TransitionValidationHook {
      def beforeSave[T](
        entity: T,
        tc: EntityPersistent[T]
      )(using ExecutionContext): Consequence[Unit] = {
        val _ = (entity, tc)
        Consequence.unit
      }

      def beforeUpdate[T](
        entity: T,
        tc: EntityPersistent[T]
      )(using ExecutionContext): Consequence[Unit] = {
        val _ = (entity, tc)
        Consequence.unit
      }

      def beforeUpdateById[P](
        id: EntityId,
        patch: P,
        tc: EntityPersistentUpdate[P]
      )(using ExecutionContext): Consequence[Unit] = {
        val _ = (id, patch, tc)
        Consequence.unit
      }
    }

  private val _create_persistent: EntityPersistentCreate[VersionedPersonCreate] =
    new EntityPersistentCreate[VersionedPersonCreate] {
      def id(entity: VersionedPersonCreate): Option[EntityId] =
        Some(entity.id)
      def collection(entity: VersionedPersonCreate): EntityCollectionId =
        entity.id.collection
      def toRecord(entity: VersionedPersonCreate): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "name" -> entity.name
        )
    }

  private val _patch_persistent: EntityPersistentUpdate[VersionedPersonPatch] =
    new EntityPersistentUpdate[VersionedPersonPatch] {
      def collection(entity: VersionedPersonPatch): EntityCollectionId = {
        val _ = entity
        _collectionid
      }

      def toRecord(entity: VersionedPersonPatch): Record =
        Record.dataAuto("name" -> entity.name)

      def fromRecord(record: Record): Consequence[VersionedPersonPatch] =
        Consequence.argumentInvalid(
          "VersionedPersonPatch decoding is not used"
        )
    }

  private val _persistent: EntityPersistent[VersionedPerson] =
    new EntityPersistent[VersionedPerson] {
      def id(entity: VersionedPerson): EntityId = entity.id
      def toRecord(entity: VersionedPerson): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "name" -> entity.name,
          "revision" -> entity.revision.value
        )
      def fromRecord(record: Record): Consequence[VersionedPerson] =
        for {
          id <- record
            .getAs[EntityId]("id")
            .map(Consequence.success)
            .getOrElse(Consequence.argumentMissing("id"))
          name <- record
            .getString("name")
            .map(Consequence.success)
            .getOrElse(Consequence.argumentMissing("name"))
          revision <- record
            .getAny("revision")
            .map(EntityRevision.createC)
            .getOrElse(Consequence.argumentMissing("revision"))
        } yield VersionedPerson(id, name, revision)
    }

  private final class IdRef[A](
      initial: A
  ) extends Ref[cats.Id, A] {
    private var _value: A = initial

    def get: A = synchronized(_value)

    def set(value: A): Unit = synchronized {
      _value = value
    }

    override def getAndSet(value: A): A = synchronized {
      val previous = _value
      _value = value
      previous
    }

    def access: (A, A => Boolean) = synchronized {
      val snapshot = _value
      val setter: A => Boolean = (next: A) =>
        synchronized {
          if (_value == snapshot) {
            _value = next
            true
          } else {
            false
          }
        }
      (snapshot, setter)
    }

    override def tryUpdate(f: A => A): Boolean = synchronized {
      _value = f(_value)
      true
    }

    override def tryModify[B](f: A => (A, B)): Option[B] = synchronized {
      val (next, result) = f(_value)
      _value = next
      Some(result)
    }

    def update(f: A => A): Unit = synchronized {
      _value = f(_value)
    }

    def modify[B](f: A => (A, B)): B = synchronized {
      val (next, result) = f(_value)
      _value = next
      result
    }

    override def modifyState[B](state: State[A, B]): B = synchronized {
      val (next, result) = state.run(_value).value
      _value = next
      result
    }

    override def tryModifyState[B](
        state: State[A, B]
    ): Option[B] = synchronized {
      val (next, result) = state.run(_value).value
      _value = next
      Some(result)
    }
  }
}
