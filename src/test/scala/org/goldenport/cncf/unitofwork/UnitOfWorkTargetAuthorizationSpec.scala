package org.goldenport.cncf.unitofwork

import cats.~>
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.{global => execution_context}
import scala.concurrent.duration.*
import org.goldenport.Consequence
import org.goldenport.cncf.context.{
  Capability,
  CorrelationId,
  DataStoreContext,
  EntityStoreContext,
  ExecutionContext,
  ObservabilityContext,
  Principal,
  PrincipalId,
  RuntimeContext,
  ScopeContext,
  ScopeKind,
  SecurityContext,
  SecurityLevel,
  TraceId
}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.entity.{
  EntityPersistent,
  EntityPersistentUpdate,
  EntityRevisionRepresentation,
  EntityRevisionSpecSupport,
  EntityStore,
  EntityStoreSpace,
  SimpleEntityStorageShapePolicy
}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.testutil.EntityRevisionFixture
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.cncf.operation.CmlOperationAccess
import org.goldenport.cncf.security.{
  AggregateAuthorization,
  EntityAbacCondition,
  EntityAccessMode,
  EntityAccessRelation,
  EntityApplicationDomain,
  EntityOperationKind,
  ServiceOperationModel
}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.simplemodeling.model.value.SecurityAttributes
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  7, 2026
 *  version Apr. 26, 2026
 * @version Jul. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkTargetAuthorizationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _in_eid01_spec =
    afterWord("in spec:entity-collection-identity, example:E7, rules:R1,R5, phase:52")
  private val _in_phase52_spec =
    afterWord("in spec:entity-collection-identity, example:unit-of-work-target-authorization, rules:R1,R5, phase:52")

  private val _cid = EntityCollectionId("test", "authz", "person")
  private val _initialrevision =
    EntityRevision.INITIAL

  "UnitOfWork target authorization" must _in_phase52_spec {
    "authorize operation preflight and aggregate command admission" which {
      "allow create by default for domain resources in phase 1" in {
        Given("an authenticated creator and a domain Entity create operation")
        given ExecutionContext = _execution_context(
          principalid = "creator-user"
        )
        given org.goldenport.cncf.entity.EntityPersistentCreate[PersonCreate] =
          _person_create_persistent

        val entity = PersonCreate("saburo", "owner-z", groupid = Some("team-z"))
        val uow    = new UnitOfWork(summon[ExecutionContext])

        When("the UnitOfWork interpreter authorizes and executes the create")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF[
              UnitOfWorkOp,
              org.goldenport.cncf.entity.CreateResult[PersonCreate]
            ](
              UnitOfWorkOp.EntityStoreCreate(
                entity = entity,
                tc = summon[org.goldenport.cncf.entity.EntityPersistentCreate[PersonCreate]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    accessKind = "create"
                  )
                )
              )
            )
          )
        )

        Then("the default domain create policy allows the operation")
        result shouldBe a[Consequence.Success[_]]
      }

      "run explicit authorization preflight through the UnitOfWork interpreter" in {
        Given("an owner and an existing Entity targeted for update")
        given ExecutionContext = _execution_context(
          principalid = "preflight-owner"
        )

        val id = EntityId("test", "preflight_authorize", _cid)
        _seed(PersonEntity(id, "preflight", "preflight-owner"))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("an explicit update authorization preflight is interpreted")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.Authorize(
                UnitOfWorkAuthorization(
                  resourceFamily = "domain",
                  resourceType = Some("Person"),
                  collectionName = Some(_cid.name),
                  targetId = Some(id),
                  accessKind = "update"
                )
              )
            )
          )
        )

        Then("the owner passes the preflight")
        result shouldBe Consequence.unit
      }

      "reject explicit authorization preflight before a side effect can run" in {
        Given("a non-owner and an existing Entity owned by another principal")
        given ExecutionContext = _execution_context(
          principalid = "preflight-non-owner"
        )

        val id = EntityId("test", "preflight_authorize_denied", _cid)
        _seed(PersonEntity(id, "preflight-denied", "preflight-owner"))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("an explicit update authorization preflight is interpreted")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.Authorize(
                UnitOfWorkAuthorization(
                  resourceFamily = "domain",
                  resourceType = Some("Person"),
                  collectionName = Some(_cid.name),
                  targetId = Some(id),
                  accessKind = "update"
                )
              )
            )
          )
        )

        Then("the preflight rejects the operation before its side effect")
        result shouldBe a[Consequence.Failure[_]]
      }

      "allow aggregate type create command authorization before an instance exists" in {
        Given("an authenticated principal and an Aggregate type-level create command")
        given ExecutionContext = _execution_context(
          principalid = "aggregate-creator"
        )

        When("the command is authorized without a target instance")
        val result = AggregateAuthorization.authorizeCommand(
          aggregateName = "notice",
          targetId = None,
          commandName = "createNotice",
          loadRecord = _ => Consequence.success(None)
        )

        Then("type-level create authorization succeeds")
        result shouldBe Consequence.unit
      }

      "reject aggregate instance command authorization when target permission denies update" in {
        Given("a non-owner and an Aggregate instance owned by another principal")
        given ExecutionContext = _execution_context(
          principalid = "aggregate-non-owner"
        )

        val id = EntityId("test", "aggregate_update_denied", _cid)
        val record =
          PersonEntity(id, "notice", "notice-owner", groupid = Some("notice-team")).toRecord()

        When("the principal requests an instance update command")
        val result = AggregateAuthorization.authorizeCommand(
          aggregateName = "notice",
          targetId = Some(id),
          commandName = "updateNotice",
          loadRecord = _ => Consequence.success(Some(record))
        )

        Then("Entity permission rejects the Aggregate command")
        result shouldBe a[Consequence.Failure[_]]
        result match
          case Consequence.Failure(conclusion) =>
            conclusion.show should include("Permission is insufficient for update")
          case _ =>
            fail("expected aggregate command authorization failure")
      }

      "allow an authenticated aggregate command declared for authenticated users" in {
        Given("an authenticated principal and an authenticated-only Aggregate command")
        given ExecutionContext = _execution_context(
          principalid = "aggregate-reviewer",
          principalattributes = Map("access_token" -> "reviewer-token")
        )

        val id     = EntityId("test", "aggregate_review", _cid)
        val record = PersonEntity(id, "shared-exhibition", "source-manager").toRecord()

        When("the principal requests the command")
        val result = AggregateAuthorization.authorizeCommand(
          aggregateName = "exhibition",
          targetId = Some(id),
          commandName = "reviewExhibition",
          loadRecord = _ => Consequence.success(Some(record)),
          access = Some(CmlOperationAccess("authenticated_only"))
        )

        Then("the authentication access rule allows the command")
        result shouldBe Consequence.unit
      }

      "reject an anonymous aggregate command declared for authenticated users" in {
        Given("an anonymous principal and an authenticated-only Aggregate command")
        given ExecutionContext = _execution_context(
          principalid = "anonymous",
          principalattributes = Map("anonymous" -> "true")
        )

        val id     = EntityId("test", "aggregate_anonymous_review", _cid)
        val record = PersonEntity(id, "shared-exhibition", "source-manager").toRecord()

        When("the anonymous principal requests the command")
        val result = AggregateAuthorization.authorizeCommand(
          aggregateName = "exhibition",
          targetId = Some(id),
          commandName = "reviewExhibition",
          loadRecord = _ => Consequence.success(Some(record)),
          access = Some(CmlOperationAccess("authenticated_only"))
        )

        Then("the authentication access rule rejects the command")
        result shouldBe a[Consequence.Failure[_]]
        result match
          case Consequence.Failure(conclusion) =>
            conclusion.show should include("Authenticated user is required")
          case _ =>
            fail("expected aggregate command authentication failure")
      }
    }

    "enforce Entity CRUD permissions and identity locking" which {
      "E7 register exact target identity before EID-05 authorization adoption" must _in_eid01_spec {
        "reject an old scalar before the authorization datastore branch" in {
          Given(
            "Spec: docs/spec/entity-collection-identity.md; Rules: R1,R5; Example: E7; a frozen old scalar whose exact authorization owner is absent from the String"
          )
          val oldscalar         = "test-authorization_target-entity-person-0-stable"
          val countingdatastore = new CountingDataStore(DataStore.inMemory())
          val datastorespace    = new DataStoreSpace().addDataStore(countingdatastore)
          given ExecutionContext = _execution_context(
            principalid = "authorization-owner",
            datastorespace = datastorespace
          )
          given EntityPersistent[PersonEntity] = _person_persistent

          When("the old target is parsed and submitted to an authorized UnitOfWork load")
          val result = EntityId.parse(oldscalar).flatMap { parsedid =>
            val record = PersonEntity(parsedid, "legacy-target", "authorization-owner").toRecord()
            val _ = summon[ExecutionContext].dataStoreSpace.inject(
              DataStoreSpace.Seed(Vector(EntityRevisionFixture.entitySeed(
                DataStore.CollectionId.EntityStore(parsedid.collection),
                record
              )))
            )
            val uow = new UnitOfWork(summon[ExecutionContext])
            new UnitOfWorkInterpreter(uow).run(
              org.goldenport.ConsequenceT.liftF(
                cats.free.Free.liftF[UnitOfWorkOp, Option[PersonEntity]](
                  UnitOfWorkOp.EntityStoreLoad(
                    parsedid,
                    summon[EntityPersistent[PersonEntity]],
                    authorization = Some(UnitOfWorkAuthorization(
                      resourceFamily = "domain",
                      resourceType = Some("Person"),
                      targetId = Some(parsedid),
                      accessKind = "read"
                    ))
                  )
                )
              )
            )
          }

          Then("canonical parsing rejects the old scalar before authorization or datastore side effects")
          result shouldBe a[Consequence.Failure[_]]
          countingdatastore.loadcalls shouldBe 0
        }
      }

      "allow load for a group-visible entity" in {
        Given("a principal sharing the target Entity group")
        given ExecutionContext = _execution_context(
          principalid = "group-user",
          principalattributes = Map("group_id" -> "team-a")
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "load_group", _cid)
        _seed(PersonEntity(id, "taro", "owner-x", groupid = Some("team-a")))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the Entity is loaded through an authorized UnitOfWork operation")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF[UnitOfWorkOp, Option[PersonEntity]](
              UnitOfWorkOp.EntityStoreLoad(
                id,
                summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    targetId = Some(id),
                    accessKind = "read"
                  )
                )
              )
            )
          )
        )

        Then("group read permission exposes the Entity")
        result.map(_.map(_.id)) shouldBe Consequence.success(Some(id))
      }

      "allow save for a group-visible entity" in {
        Given("a principal sharing a writable target Entity group")
        given ExecutionContext = _execution_context(
          principalid = "group-user",
          principalattributes = Map("group_id" -> "team-a")
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "save_group", _cid)
        _seed(PersonEntity(id, "taro", "owner-x", groupid = Some("team-a")))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the Entity is saved through an authorized UnitOfWork operation")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreSaveDetached(
                entity = PersonEntity(id, "taro-2", "owner-x", groupid = Some("team-a")),
                expectedRevision = Some(_initialrevision),
                tc = summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    targetId = Some(id),
                    accessKind = "update"
                  )
                )
              )
            )
          )
        )

        Then("group update permission persists the change")
        result shouldBe a[Consequence.Success[_]]
        _load_name(id) shouldBe Consequence.success(Some("taro-2"))
      }

      "allow save from typed security access when entity record omits security attributes" in {
        Given(
          "an owner represented by typed security access and no security fields in the Entity record"
        )
        given ExecutionContext = _execution_context(
          principalid = "typed-owner"
        )
        given EntityPersistent[TypedSecurityTargetEntity] = _typed_security_target_persistent

        val id = EntityId("test", "save_typed_security", _cid)
        val _ = summon[ExecutionContext].dataStoreSpace.inject(
          DataStore.CollectionId.EntityStore(_cid),
          EntityRevisionFixture.persistedRecord(
            TypedSecurityTargetEntity(
              id,
              "typed-before",
              "typed-owner"
            ).toRecord()
          )
        )
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the typed Entity is saved through an authorized UnitOfWork operation")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreSaveDetached(
                entity = TypedSecurityTargetEntity(id, "typed-after", "typed-owner"),
                expectedRevision = Some(_initialrevision),
                tc = summon[EntityPersistent[TypedSecurityTargetEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("TypedSecurityTarget"),
                    targetId = Some(id),
                    accessKind = "update"
                  )
                )
              )
            )
          )
        )

        Then("typed security grants the save and the Entity is persisted")
        result shouldBe a[Consequence.Success[_]]
        _load_name(id) shouldBe Consequence.success(Some("typed-after"))
      }

      "build authorization record with typed security overriding stale target and legacy security" in {
        Given("typed security metadata together with stale target and legacy security fields")
        given EntityPersistent[TypedSecurityTargetEntity] = _typed_security_target_persistent
        val id = EntityId("test", "save_typed_security_overlay", _cid)
        val entity =
          TypedSecurityTargetEntity(id, "typed-overlay", "typed-owner", stalesecurity = true)

        When("the persistence codec builds its authorization record")
        val record = summon[EntityPersistent[TypedSecurityTargetEntity]].authorizationRecord(entity)

        Then("typed security is canonical and stale representations are removed")
        record.getString("owner_id") shouldBe Some("typed_owner")
        record.getString("ownerId") shouldBe None
        record.getRecord("securityAttributes") shouldBe None
        record.getRecord("security_attributes") shouldBe None
        val rights = record.getString("permission")
          .flatMap(SimpleEntityStorageShapePolicy.permissionRightsFromJson)
          .getOrElse(fail("permission should be compact JSON"))
        rights.other.read shouldBe false
      }

      "reject stale target owner when typed security access grants a different owner" in {
        Given("a stale target owner that differs from authoritative typed security")
        given ExecutionContext = _execution_context(
          principalid = "stale-owner"
        )
        given EntityPersistent[TypedSecurityTargetEntity] = _typed_security_target_persistent

        val id  = EntityId("test", "save_typed_security_stale_owner_denied", _cid)
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the stale owner attempts to save the typed Entity")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreSaveDetached(
                entity = TypedSecurityTargetEntity(
                  id,
                  "typed-stale-denied",
                  "typed-owner",
                  stalesecurity = true
                ),
                expectedRevision = Some(_initialrevision),
                tc = summon[EntityPersistent[TypedSecurityTargetEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("TypedSecurityTarget"),
                    targetId = Some(id),
                    accessKind = "update"
                  )
                )
              )
            )
          )
        )

        Then("authorization follows typed security and rejects the stale owner")
        result shouldBe a[Consequence.Failure[_]]
        _load_name(id) shouldBe Consequence.success(None)
      }

      "reject save from typed security access for a non-owner entity record without security attributes" in {
        Given("a non-owner and an Entity whose authority exists only in typed security")
        given ExecutionContext = _execution_context(
          principalid = "typed-other"
        )
        given EntityPersistent[TypedSecurityTargetEntity] = _typed_security_target_persistent

        val id  = EntityId("test", "save_typed_security_denied", _cid)
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the non-owner attempts to save the Entity")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreSaveDetached(
                entity = TypedSecurityTargetEntity(id, "typed-denied", "typed-owner"),
                expectedRevision = Some(_initialrevision),
                tc = summon[EntityPersistent[TypedSecurityTargetEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("TypedSecurityTarget"),
                    targetId = Some(id),
                    accessKind = "update"
                  )
                )
              )
            )
          )
        )

        Then("typed security rejects the save")
        result shouldBe a[Consequence.Failure[_]]
        _load_name(id) shouldBe Consequence.success(None)
      }

      "reject update for a non-owner non-group non-privileged user" in {
        Given("an Entity and a principal with no owner group or privilege relation")
        given ExecutionContext = _execution_context(
          principalid = "other-user"
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "update_denied", _cid)
        _seed(PersonEntity(id, "shiro", "owner-x"))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the principal attempts an authorized update")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreUpdateDetached(
                entity = PersonEntity(id, "shiro-2", "owner-x"),
                expectedRevision = Some(_initialrevision),
                tc = summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    targetId = Some(id),
                    accessKind = "update"
                  )
                )
              )
            )
          )
        )

        Then("authorization rejects the update and preserves storage")
        result shouldBe a[Consequence.Failure[_]]
        _load_name(id) shouldBe Consequence.success(Some("shiro"))
      }

      "allow update-by-id for a privilege-visible entity" in {
        Given("a principal carrying the privilege required by the target Entity")
        given ExecutionContext = _execution_context(
          principalid = "priv-user",
          principalattributes = Map("privilege" -> "vip-access")
        )
        given EntityPersistent[PersonEntity]      = _person_persistent
        given EntityPersistentUpdate[PersonPatch] = _person_patch_persistent

        val id = EntityId("test", "update_priv", _cid)
        _seed(PersonEntity(id, "hanako", "owner-x", privilegeid = Some("vip-access")))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the principal applies a patch update by Entity id")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreUpdateByIdDetached(
                id = id,
                patch = PersonPatch(name = Some("hanako-2")),
                expectedRevision = Some(_initialrevision),
                tc = summon[EntityPersistentUpdate[PersonPatch]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    targetId = Some(id),
                    accessKind = "update"
                  )
                )
              )
            )
          )
        )

        Then("privilege visibility allows the patch")
        result shouldBe a[Consequence.Success[_]]
        _load_name(id) shouldBe Consequence.success(Some("hanako-2"))
      }

      "reject delete when rights deny access for non-owner non-group non-privileged user" in {
        Given("an Entity and a principal without delete permission")
        given ExecutionContext = _execution_context(
          principalid = "other-user"
        )
        val id = EntityId("test", "delete_denied", _cid)
        _seed(PersonEntity(id, "jiro", "owner-x"))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the principal attempts an authorized delete")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreDelete(
                id = id,
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    targetId = Some(id),
                    accessKind = "delete"
                  )
                )
              )
            )
          )
        )

        Then("authorization rejects deletion and preserves the Entity")
        result shouldBe a[Consequence.Failure[_]]
        _load_name(id) shouldBe Consequence.success(Some("jiro"))
      }
    }

    "control internal and system permission bypasses" which {
      "allow service-internal update without entity permission" in {
        Given("a service principal and a service-internal Entity update")
        given ExecutionContext = _execution_context(
          principalid = "service-principal"
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "update_service_internal", _cid)
        _seed(PersonEntity(id, "order-1", "sales-org"))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the UnitOfWork executes the service-internal update")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreUpdateDetached(
                entity = PersonEntity(id, "order-2", "sales-org"),
                expectedRevision = Some(_initialrevision),
                tc = summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("SalesOrder"),
                    targetId = Some(id),
                    accessKind = "update",
                    accessMode = EntityAccessMode.ServiceInternal
                  )
                )
              )
            )
          )
        )

        Then("service-internal access bypasses Entity permission")
        result shouldBe a[Consequence.Success[_]]
        _load_name(id) shouldBe Consequence.success(Some("order-2"))
      }

      "allow system update without entity permission" in {
        Given("a system principal and a system-mode Entity update")
        given ExecutionContext = _execution_context(
          principalid = "system-principal"
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "update_system", _cid)
        _seed(PersonEntity(id, "projection-1", "business-owner"))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the UnitOfWork executes the system update")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreUpdateDetached(
                entity = PersonEntity(id, "projection-2", "business-owner"),
                expectedRevision = Some(_initialrevision),
                tc = summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Projection"),
                    targetId = Some(id),
                    accessKind = "update",
                    accessMode = EntityAccessMode.System
                  )
                )
              )
            )
          )
        )

        Then("system access bypasses Entity permission")
        result shouldBe a[Consequence.Success[_]]
        _load_name(id) shouldBe Consequence.success(Some("projection-2"))
      }

      "emit audit event when service-internal permission bypass is used" in {
        Given("an installed audit backend and a service-internal update")
        val backend = new MemoryBackend
        LogBackendHolder.reset()
        LogBackendHolder.install(backend)
        try {
          given ExecutionContext = _execution_context(
            principalid = "service-principal"
          )
          given EntityPersistent[PersonEntity] = _person_persistent

          val id = EntityId("test", "update_service_internal_audit", _cid)
          _seed(PersonEntity(id, "order-1", "sales-org"))
          val uow = new UnitOfWork(summon[ExecutionContext])

          When("service-internal access bypasses Entity permission")
          val result = new UnitOfWorkInterpreter(uow).run(
            org.goldenport.ConsequenceT.liftF(
              cats.free.Free.liftF(
                UnitOfWorkOp.EntityStoreUpdateDetached(
                  entity = PersonEntity(id, "order-2", "sales-org"),
                  expectedRevision = Some(_initialrevision),
                  tc = summon[EntityPersistent[PersonEntity]],
                  authorization = Some(
                    UnitOfWorkAuthorization(
                      resourceFamily = "domain",
                      resourceType = Some("SalesOrder"),
                      targetId = Some(id),
                      accessKind = "update",
                      accessMode = EntityAccessMode.ServiceInternal
                    )
                  )
                )
              )
            )
          )

          Then("the operation succeeds and emits bypass and decision audit events")
          result shouldBe a[Consequence.Success[_]]
          backend.lines.exists(_.contains("authorization.permission.bypass")) shouldBe true
          backend.lines.exists(_.contains("authorization.decision")) shouldBe true
        } finally
          LogBackendHolder.reset()
      }

      "emit audit event when system permission bypass is used" in {
        Given("an installed audit backend and a system-mode update")
        val backend = new MemoryBackend
        LogBackendHolder.reset()
        LogBackendHolder.install(backend)
        try {
          given ExecutionContext = _execution_context(
            principalid = "system-principal"
          )
          given EntityPersistent[PersonEntity] = _person_persistent

          val id = EntityId("test", "update_system_audit", _cid)
          _seed(PersonEntity(id, "projection-1", "business-owner"))
          val uow = new UnitOfWork(summon[ExecutionContext])

          When("system access bypasses Entity permission")
          val result = new UnitOfWorkInterpreter(uow).run(
            org.goldenport.ConsequenceT.liftF(
              cats.free.Free.liftF(
                UnitOfWorkOp.EntityStoreUpdateDetached(
                  entity = PersonEntity(id, "projection-2", "business-owner"),
                  expectedRevision = Some(_initialrevision),
                  tc = summon[EntityPersistent[PersonEntity]],
                  authorization = Some(
                    UnitOfWorkAuthorization(
                      resourceFamily = "domain",
                      resourceType = Some("Projection"),
                      targetId = Some(id),
                      accessKind = "update",
                      accessMode = EntityAccessMode.System
                    )
                  )
                )
              )
            )
          )

          Then("the operation succeeds and emits a bypass audit event")
          result shouldBe a[Consequence.Success[_]]
          backend.lines.exists(_.contains("authorization.permission.bypass")) shouldBe true
        } finally
          LogBackendHolder.reset()
      }

      "allow same-component service-internal update without service grant" in {
        Given("a service-internal update whose source and target are the same component")
        given ExecutionContext = _execution_context(
          principalid = "service-principal"
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "update_same_component_internal", _cid)
        _seed(PersonEntity(id, "order-1", "sales-org"))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the UnitOfWork executes the same-component update")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreUpdateDetached(
                entity = PersonEntity(id, "order-2", "sales-org"),
                expectedRevision = Some(_initialrevision),
                tc = summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("SalesOrder"),
                    targetId = Some(id),
                    accessKind = "update",
                    accessMode = EntityAccessMode.ServiceInternal,
                    sourceComponentName = Some("sales"),
                    targetComponentName = Some("sales")
                  )
                )
              )
            )
          )
        )

        Then("same-component access does not require a cross-component service grant")
        result shouldBe a[Consequence.Success[_]]
        _load_name(id) shouldBe Consequence.success(Some("order-2"))
      }

      "reject cross-component service-internal update without service grant" in {
        Given("an audited cross-component update without a service grant")
        val backend = new MemoryBackend
        LogBackendHolder.reset()
        LogBackendHolder.install(backend)
        try {
          given ExecutionContext = _execution_context(
            principalid = "service-principal"
          )
          given EntityPersistent[PersonEntity] = _person_persistent

          val id = EntityId("test", "update_cross_component_internal_denied", _cid)
          _seed(PersonEntity(id, "stock-1", "inventory-org"))
          val uow = new UnitOfWork(summon[ExecutionContext])

          When("the service principal attempts the cross-component update")
          val result = new UnitOfWorkInterpreter(uow).run(
            org.goldenport.ConsequenceT.liftF(
              cats.free.Free.liftF(
                UnitOfWorkOp.EntityStoreUpdateDetached(
                  entity = PersonEntity(id, "stock-2", "inventory-org"),
                  expectedRevision = Some(_initialrevision),
                  tc = summon[EntityPersistent[PersonEntity]],
                  authorization = Some(
                    UnitOfWorkAuthorization(
                      resourceFamily = "domain",
                      resourceType = Some("Inventory"),
                      targetId = Some(id),
                      accessKind = "update",
                      accessMode = EntityAccessMode.ServiceInternal,
                      sourceComponentName = Some("sales"),
                      targetComponentName = Some("inventory")
                    )
                  )
                )
              )
            )
          )

          Then("authorization rejects the update and emits a decision event")
          result shouldBe a[Consequence.Failure[_]]
          _load_name(id) shouldBe Consequence.success(Some("stock-1"))
          backend.lines.exists(_.contains("authorization.decision")) shouldBe true
        } finally
          LogBackendHolder.reset()
      }

      "allow cross-component service-internal update with service grant" in {
        Given("a service principal carrying the source-to-target service grant")
        given ExecutionContext = _execution_context(
          principalid = "service-principal",
          capabilities = Vector(Capability("service-grant:sales:inventory"))
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "update_cross_component_internal_allowed", _cid)
        _seed(PersonEntity(id, "stock-1", "inventory-org"))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the principal attempts the cross-component update")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreUpdateDetached(
                entity = PersonEntity(id, "stock-2", "inventory-org"),
                expectedRevision = Some(_initialrevision),
                tc = summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Inventory"),
                    targetId = Some(id),
                    accessKind = "update",
                    accessMode = EntityAccessMode.ServiceInternal,
                    sourceComponentName = Some("sales"),
                    targetComponentName = Some("inventory")
                  )
                )
              )
            )
          )
        )

        Then("the service grant allows the update")
        result shouldBe a[Consequence.Success[_]]
        _load_name(id) shouldBe Consequence.success(Some("stock-2"))
      }
    }

    "evaluate relation-based authorization" which {
      "allow relation-based read without granting other read permission" in {
        Given("an audited principal-to-Entity customer relation")
        val backend = new MemoryBackend
        LogBackendHolder.reset()
        LogBackendHolder.install(backend)
        try {
          given ExecutionContext = _execution_context(
            principalid = "customer-user",
            principalattributes = Map("customer_id" -> "customer-123")
          )
          given EntityPersistent[PersonEntity] = _person_persistent

          val id = EntityId("test", "read_relation", _cid)
          _seed(PersonEntity(id, "order-3", "sales-org", customerid = Some("customer-123")))
          val uow = new UnitOfWork(summon[ExecutionContext])

          When("the related principal loads the Entity")
          val result = new UnitOfWorkInterpreter(uow).run(
            org.goldenport.ConsequenceT.liftF(
              cats.free.Free.liftF[UnitOfWorkOp, Option[PersonEntity]](
                UnitOfWorkOp.EntityStoreLoad(
                  id,
                  summon[EntityPersistent[PersonEntity]],
                  authorization = Some(
                    UnitOfWorkAuthorization(
                      resourceFamily = "domain",
                      resourceType = Some("SalesOrder"),
                      targetId = Some(id),
                      accessKind = "read",
                      relationRules = Vector(EntityAccessRelation("customerId", "customerId"))
                    )
                  )
                )
              )
            )
          )

          Then("the relation grants read and emits relation diagnostics")
          result.map(_.map(_.id)) shouldBe Consequence.success(Some(id))
          backend.lines.exists(_.contains("authorization.relation.diagnostics")) shouldBe true
        } finally
          LogBackendHolder.reset()
      }

      "reject relation-based update unless update access is explicitly allowed" in {
        Given("a matching relation whose allowed accesses exclude update")
        given ExecutionContext = _execution_context(
          principalid = "customer-user",
          principalattributes = Map("customer_id" -> "customer-123")
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "update_relation_denied", _cid)
        _seed(PersonEntity(id, "order-4", "sales-org", customerid = Some("customer-123")))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the related principal attempts an update")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreUpdateDetached(
                entity = PersonEntity(
                  id,
                  "order-4-updated",
                  "sales-org",
                  customerid = Some("customer-123")
                ),
                expectedRevision = Some(_initialrevision),
                tc = summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("SalesOrder"),
                    targetId = Some(id),
                    accessKind = "update",
                    relationRules = Vector(EntityAccessRelation(
                      "customerId",
                      "customerId",
                      Set("read", "search/list")
                    ))
                  )
                )
              )
            )
          )
        )

        Then("relation authorization rejects the update and preserves storage")
        result shouldBe a[Consequence.Failure[_]]
        _load_name(id) shouldBe Consequence.success(Some("order-4"))
      }

      "allow relation-based update when update access is explicitly allowed" in {
        Given("a matching relation whose allowed accesses include update")
        given ExecutionContext = _execution_context(
          principalid = "customer-user",
          principalattributes = Map("customer_id" -> "customer-123")
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "update_relation_allowed", _cid)
        _seed(PersonEntity(id, "order-5", "sales-org", customerid = Some("customer-123")))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the related principal updates the Entity")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF(
              UnitOfWorkOp.EntityStoreUpdateDetached(
                entity = PersonEntity(
                  id,
                  "order-5-updated",
                  "sales-org",
                  customerid = Some("customer-123")
                ),
                expectedRevision = Some(_initialrevision),
                tc = summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("SalesOrder"),
                    targetId = Some(id),
                    accessKind = "update",
                    relationRules =
                      Vector(EntityAccessRelation("customerId", "customerId", Set("update")))
                  )
                )
              )
            )
          )
        )

        Then("relation authorization allows and persists the update")
        result shouldBe a[Consequence.Success[_]]
        _load_name(id) shouldBe Consequence.success(Some("order-5-updated"))
      }
    }

    "evaluate ABAC natural conditions" which {
      "reject read when explicit ABAC tenant condition does not match" in {
        Given("a principal and Entity with different tenant attributes")
        given ExecutionContext = _execution_context(
          principalid = "tenant-user",
          principalattributes = Map("tenant_id" -> "tenant-a")
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "read_abac_tenant_denied", _cid)
        _seed(PersonEntity(id, "tenant-record", "tenant-owner", tenantid = Some("tenant-b")))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the principal loads the Entity under a tenant equality condition")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF[UnitOfWorkOp, Option[PersonEntity]](
              UnitOfWorkOp.EntityStoreLoad(
                id,
                summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    targetId = Some(id),
                    accessKind = "read",
                    naturalConditions = Vector(EntityAbacCondition(
                      "tenantId",
                      EntityAbacCondition.Value.SubjectAttribute("tenantId")
                    ))
                  )
                )
              )
            )
          )
        )

        Then("ABAC rejects the tenant mismatch")
        result shouldBe a[Consequence.Failure[_]]
      }

      "allow read when explicit ABAC tenant condition matches and permission allows" in {
        Given("a principal whose tenant and group attributes match the Entity")
        given ExecutionContext = _execution_context(
          principalid = "tenant-owner",
          principalattributes = Map("tenant_id" -> "tenant-a", "group_id" -> "team-a")
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "read_abac_tenant_allowed", _cid)
        _seed(PersonEntity(
          id,
          "tenant-record",
          "tenant-owner",
          groupid = Some("team-a"),
          tenantid = Some("tenant-a")
        ))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the principal loads the Entity under a tenant equality condition")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF[UnitOfWorkOp, Option[PersonEntity]](
              UnitOfWorkOp.EntityStoreLoad(
                id,
                summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    targetId = Some(id),
                    accessKind = "read",
                    naturalConditions = Vector(EntityAbacCondition(
                      "tenantId",
                      EntityAbacCondition.Value.SubjectAttribute("tenantId")
                    ))
                  )
                )
              )
            )
          )
        )

        Then("ABAC and Entity permission allow the read")
        result.map(_.map(_.id)) shouldBe Consequence.success(Some(id))
      }

      "emit diagnostics for matched ABAC natural conditions" in {
        Given("an audit backend and a matching tenant ABAC condition")
        val backend = new MemoryBackend
        LogBackendHolder.reset()
        LogBackendHolder.install(backend)
        try {
          given ExecutionContext = _execution_context(
            principalid = "tenant-owner",
            principalattributes = Map("tenant_id" -> "tenant-a")
          )
          given EntityPersistent[PersonEntity] = _person_persistent

          val id = EntityId("test", "read_abac_diagnostics", _cid)
          _seed(PersonEntity(id, "tenant-record", "tenant-owner", tenantid = Some("tenant-a")))
          val uow = new UnitOfWork(summon[ExecutionContext])

          When("the matching ABAC read is authorized")
          val result = new UnitOfWorkInterpreter(uow).run(
            org.goldenport.ConsequenceT.liftF(
              cats.free.Free.liftF[UnitOfWorkOp, Option[PersonEntity]](
                UnitOfWorkOp.EntityStoreLoad(
                  id,
                  summon[EntityPersistent[PersonEntity]],
                  authorization = Some(
                    UnitOfWorkAuthorization(
                      resourceFamily = "domain",
                      resourceType = Some("Person"),
                      targetId = Some(id),
                      accessKind = "read",
                      naturalConditions = Vector(EntityAbacCondition(
                        "tenantId",
                        EntityAbacCondition.Value.SubjectAttribute("tenantId")
                      ))
                    )
                  )
                )
              )
            )
          )

          Then("the read succeeds and emits ABAC diagnostics")
          result.map(_.map(_.id)) shouldBe Consequence.success(Some(id))
          backend.lines.exists(_.contains("authorization.abac.diagnostics")) shouldBe true
        } finally
          LogBackendHolder.reset()
      }

      "allow read when explicit ABAC publication window matches" in {
        Given("an Entity whose publication interval contains the current time")
        given ExecutionContext = _execution_context(
          principalid = "reader"
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "read_abac_publication_allowed", _cid)
        _seed(PersonEntity(
          id,
          "published-record",
          "reader",
          publishat = Some("2000-01-01T00:00:00Z"),
          closeat = Some("2999-01-01T00:00:00Z")
        ))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the Entity is loaded under publication-window conditions")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF[UnitOfWorkOp, Option[PersonEntity]](
              UnitOfWorkOp.EntityStoreLoad(
                id,
                summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    targetId = Some(id),
                    accessKind = "read",
                    naturalConditions =
                      EntityAbacCondition.parseList("publishAt<=now:read;closeAt>now:read")
                  )
                )
              )
            )
          )
        )

        Then("ABAC allows the read")
        result.map(_.map(_.id)) shouldBe Consequence.success(Some(id))
      }

      "reject read when explicit ABAC publication window does not match" in {
        Given("an Entity whose publication time is in the future")
        given ExecutionContext = _execution_context(
          principalid = "reader"
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "read_abac_publication_denied", _cid)
        _seed(PersonEntity(id, "future-record", "reader", publishat = Some("2999-01-01T00:00:00Z")))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the Entity is loaded under a current-publication condition")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF[UnitOfWorkOp, Option[PersonEntity]](
              UnitOfWorkOp.EntityStoreLoad(
                id,
                summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    targetId = Some(id),
                    accessKind = "read",
                    naturalConditions = EntityAbacCondition.parseList("publishAt<=now:read")
                  )
                )
              )
            )
          )
        )

        Then("ABAC rejects the read with condition evidence")
        result shouldBe a[Consequence.Failure[_]]
        result match
          case Consequence.Failure(conclusion) =>
            conclusion.show should include("publishAt<=now")
            conclusion.show should include("2999-01-01T00:00:00Z")
          case _ =>
            fail("expected authorization failure")
      }

      "allow read when operation and application natural ABAC conditions match" in {
        Given("operation and application metadata matching all natural conditions")
        given ExecutionContext = _execution_context(
          principalid = "reader"
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "read_abac_operation_application_allowed", _cid)
        _seed(PersonEntity(id, "operation-application-record", "reader"))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the Entity is loaded under operation and application ABAC conditions")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF[UnitOfWorkOp, Option[PersonEntity]](
              UnitOfWorkOp.EntityStoreLoad(
                id,
                summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    targetId = Some(id),
                    accessKind = "read",
                    operationModel = Some(ServiceOperationModel.BusinessService),
                    entityOperationKind = Some(EntityOperationKind.Resource),
                    entityApplicationDomain = Some(EntityApplicationDomain.Business),
                    naturalConditions = EntityAbacCondition.parseList(
                      "operation.operationModel=business-service:read;application.entityOperationKind=resource:read;application.entityApplicationDomain=business:read"
                    )
                  )
                )
              )
            )
          )
        )

        Then("the matching operational context allows the read")
        result.map(_.map(_.id)) shouldBe Consequence.success(Some(id))
      }

      "allow read when explicit CMS publication visibility conditions match" in {
        Given("a public CMS Entity inside all publication visibility windows")
        given ExecutionContext = _execution_context(
          principalid = "reader"
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "read_abac_visibility_allowed", _cid)
        _seed(PersonEntity(
          id,
          "public-record",
          "reader",
          visibility = Some("Public"),
          publicat = Some("2000-01-01T00:00:00Z"),
          startat = Some("2000-01-01T00:00:00Z"),
          endat = Some("2999-01-01T00:00:00Z"),
          unpublishat = Some("2999-01-01T00:00:00Z")
        ))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the Entity is loaded under CMS visibility conditions")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF[UnitOfWorkOp, Option[PersonEntity]](
              UnitOfWorkOp.EntityStoreLoad(
                id,
                summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    targetId = Some(id),
                    accessKind = "read",
                    naturalConditions = EntityAbacCondition.parseList(
                      "visibility=Public:read;publicAt<=now:read;startAt<=now:read;endAt>now:read;unpublishAt>now:read"
                    )
                  )
                )
              )
            )
          )
        )

        Then("all matching conditions allow the read")
        result.map(_.map(_.id)) shouldBe Consequence.success(Some(id))
      }

      "reject read when explicit CMS publication visibility condition misses" in {
        Given("a private CMS Entity that does not satisfy public visibility")
        given ExecutionContext = _execution_context(
          principalid = "reader"
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val id = EntityId("test", "read_abac_visibility_denied", _cid)
        _seed(PersonEntity(
          id,
          "private-record",
          "reader",
          visibility = Some("Private"),
          publicat = Some("2000-01-01T00:00:00Z")
        ))
        val uow = new UnitOfWork(summon[ExecutionContext])

        When("the Entity is loaded under public CMS visibility conditions")
        val result = new UnitOfWorkInterpreter(uow).run(
          org.goldenport.ConsequenceT.liftF(
            cats.free.Free.liftF[UnitOfWorkOp, Option[PersonEntity]](
              UnitOfWorkOp.EntityStoreLoad(
                id,
                summon[EntityPersistent[PersonEntity]],
                authorization = Some(
                  UnitOfWorkAuthorization(
                    resourceFamily = "domain",
                    resourceType = Some("Person"),
                    targetId = Some(id),
                    accessKind = "read",
                    naturalConditions =
                      EntityAbacCondition.parseList("visibility=Public:read;publicAt<=now:read")
                  )
                )
              )
            )
          )
        )

        Then("ABAC rejects the read with the failed visibility evidence")
        result shouldBe a[Consequence.Failure[_]]
        result match
          case Consequence.Failure(conclusion) =>
            conclusion.show should include("visibility=Public")
            conclusion.show should include("Private")
          case _ =>
            fail("expected authorization failure")
      }
    }
  }

  private def _execution_context(
      principalid: String,
      capabilities: Vector[Capability] = Vector.empty,
      principalattributes: Map[String, String] = Map.empty,
      datastorespace: DataStoreSpace = DataStoreSpace.default()
  ): ExecutionContext = {
    val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val observability = ObservabilityContext(
      traceId = TraceId("test", "runtime"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "runtime"))
    )
    val driver                         = FakeHttpDriver.okText("nop")
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "uow-target-authorization-spec-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver),
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitofworksupplier = () => new UnitOfWork(context),
      unitofworkinterpreterfn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException(
            "unitOfWorkInterpreter is not used in test context"
          )
      },
      commitaction = uow => { val _ = uow.commit(); () },
      abortaction = uow => { val _ = uow.rollback(); () },
      disposeaction = _ => (),
      token = "uow-target-authorization-spec-runtime"
    )
    val secured = context match {
      case i: ExecutionContext.Instance =>
        val principal = new Principal {
          def id: PrincipalId                 = PrincipalId(principalid)
          def attributes: Map[String, String] = principalattributes
        }
        i.copy(
          cncfCore = i.cncfCore.copy(
            security = SecurityContext(
              principal = principal,
              capabilities = capabilities.toSet,
              level = SecurityLevel("test")
            )
          )
        )
      case _ =>
        context
    }
    EntityRevisionSpecSupport.registerRevisionBinding(
      secured,
      _cid,
      _person_persistent,
      EntityRevisionRepresentation.Detached
    )
    secured
  }

  private def _seed(entity: PersonEntity)(using ctx: ExecutionContext): Unit = {
    val _ = ctx.dataStoreSpace.inject(
      DataStoreSpace.Seed(
        Vector(
          EntityRevisionFixture.entitySeed(
            DataStore.CollectionId.EntityStore(_cid),
            entity.toRecord()
          )
        )
      )
    )
  }

  private def _load_name(id: EntityId)(using ctx: ExecutionContext): Consequence[Option[String]] =
    for {
      cid  <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds   <- ctx.dataStoreSpace.dataStore(cid)
      rec  <- ds.load(cid, dsid)
    } yield rec.flatMap(_.getString("name"))

  private final case class PersonEntity(
      id: EntityId,
      name: String,
      ownerid: String,
      groupid: Option[String] = None,
      privilegeid: Option[String] = None,
      customerid: Option[String] = None,
      tenantid: Option[String] = None,
      publishat: Option[String] = None,
      closeat: Option[String] = None,
      visibility: Option[String] = None,
      publicat: Option[String] = None,
      startat: Option[String] = None,
      endat: Option[String] = None,
      unpublishat: Option[String] = None
  ) {
    def toRecord(): Record =
      Record.dataAuto(
        "id"          -> id,
        "name"        -> name,
        "customerId"  -> customerid,
        "tenantId"    -> tenantid,
        "publishAt"   -> publishat,
        "closeAt"     -> closeat,
        "visibility"  -> visibility,
        "publicAt"    -> publicat,
        "startAt"     -> startat,
        "endAt"       -> endat,
        "unpublishAt" -> unpublishat,
        "security_attributes" -> Record.dataAuto(
          "owner_id"     -> ownerid,
          "group_id"     -> groupid,
          "privilege_id" -> privilegeid,
          "rights" -> Record.dataAuto(
            "owner" -> Record.dataAuto("read" -> true, "write" -> true, "execute" -> true),
            "group" -> Record.dataAuto("read" -> true, "write" -> true, "execute" -> false),
            "other" -> Record.dataAuto("read" -> false, "write" -> false, "execute" -> false)
          )
        )
      )
  }

  private final case class PersonCreate(
      name: String,
      ownerid: String,
      groupid: Option[String] = None,
      privilegeid: Option[String] = None,
      id: Option[EntityId] = None
  ) {
    def toRecord(): Record =
      Record.dataAuto(
        "name" -> name,
        "security_attributes" -> Record.dataAuto(
          "owner_id"     -> ownerid,
          "group_id"     -> groupid,
          "privilege_id" -> privilegeid,
          "rights" -> Record.dataAuto(
            "owner" -> Record.dataAuto("read" -> true, "write" -> true, "execute" -> true),
            "group" -> Record.dataAuto("read" -> true, "write" -> true, "execute" -> false),
            "other" -> Record.dataAuto("read" -> false, "write" -> false, "execute" -> false)
          )
        )
      )
  }

  private final case class PersonPatch(
      name: Option[String] = None
  ) {
    def toRecord(): Record =
      Record.dataAuto(
        "name" -> name
      )
  }

  private final class CountingDataStore(delegate: DataStore) extends DataStore {
    private var _loadcalls: Int = 0

    def loadcalls: Int = _loadcalls

    def isAccept(cid: DataStore.CollectionId): Boolean = delegate.isAccept(cid)

    def create(
        collection: DataStore.CollectionId,
        id: DataStore.EntryId,
        record: Record
    )(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.create(collection, id, record)

    def load(
        collection: DataStore.CollectionId,
        id: DataStore.EntryId
    )(using ctx: ExecutionContext): Consequence[Option[Record]] = {
      _loadcalls += 1
      delegate.load(collection, id)
    }

    def save(
        collection: DataStore.CollectionId,
        id: DataStore.EntryId,
        record: Record
    )(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.save(collection, id, record)

    def update(
        collection: DataStore.CollectionId,
        id: DataStore.EntryId,
        changes: Record
    )(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.update(collection, id, changes)

    def delete(
        collection: DataStore.CollectionId,
        id: DataStore.EntryId
    )(using ctx: ExecutionContext): Consequence[Unit] =
      delegate.delete(collection, id)

    def prepare(tx: TransactionContext): PrepareResult = delegate.prepare(tx)
    def commit(tx: TransactionContext): Unit           = delegate.commit(tx)
    def abort(tx: TransactionContext): Unit            = delegate.abort(tx)
  }

  private lazy val _person_persistent: EntityPersistent[PersonEntity] =
    new EntityPersistent[PersonEntity] {
      def id(e: PersonEntity): EntityId     = e.id
      def toRecord(e: PersonEntity): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[PersonEntity] = {
        val security =
          SimpleEntityStorageShapePolicy.securityAttributesFromRecord(r)
        (
          r.getAs[EntityId]("id"),
          r.getString("name"),
          r.getString(org.goldenport.datatype.PathName(Vector("security_attributes", "owner_id")))
            .orElse(r.getString("owner_id"))
            .orElse(security.map(_.ownerId.id.value)),
          r.getString(org.goldenport.datatype.PathName(Vector("security_attributes", "group_id")))
            .orElse(r.getString("group_id"))
            .orElse(security.map(_.groupId.id.value).filter(_.nonEmpty)),
          r.getString(org.goldenport.datatype.PathName(Vector(
            "security_attributes",
            "privilege_id"
          )))
            .orElse(r.getString("privilege_id"))
            .orElse(security.map(_.privilegeId.id.value).filter(_.nonEmpty)),
          r.getString("customerId").orElse(r.getString("customer_id")),
          r.getString("tenantId").orElse(r.getString("tenant_id")),
          r.getString("publishAt").orElse(r.getString("publish_at")),
          r.getString("closeAt").orElse(r.getString("close_at")),
          r.getString("visibility"),
          r.getString("publicAt").orElse(r.getString("public_at")),
          r.getString("startAt").orElse(r.getString("start_at")),
          r.getString("endAt").orElse(r.getString("end_at")),
          r.getString("unpublishAt").orElse(r.getString("unpublish_at"))
        ) match
          case (
                Some(entityid),
                Some(entityname),
                Some(entityownerid),
                entitygroupid,
                entityprivilegeid,
                customerid,
                tenantid,
                publishat,
                closeat,
                visibility,
                publicat,
                startat,
                endat,
                unpublishat
              ) =>
            Consequence.success(PersonEntity(
              entityid,
              entityname,
              entityownerid,
              entitygroupid,
              entityprivilegeid,
              customerid,
              tenantid,
              publishat,
              closeat,
              visibility,
              publicat,
              startat,
              endat,
              unpublishat
            ))
          case _ =>
            Consequence.argumentInvalid(
              "person",
              "id, name and owner security",
              r
            )
      }
    }

  private final case class TypedSecurityTargetEntity(
      id: EntityId,
      name: String,
      ownerid: String,
      stalesecurity: Boolean = false
  ) {
    def toRecord(): Record = {
      val base = Record.dataAuto(
        "id"   -> id,
        "name" -> name
      )
      if (stalesecurity)
        base ++ Record.dataAuto(
          "owner_id"     -> "stale-owner",
          "group_id"     -> "stale-owner",
          "privilege_id" -> "stale-owner",
          "permission" -> SimpleEntityStorageShapePolicy.permissionJson(
            SecurityAttributes.publicOwnedBy("stale-owner").rights
          ),
          "securityAttributes" -> _security_record("stale-owner")
        )
      else
        base
    }
  }

  private val _typed_security_target_persistent: EntityPersistent[TypedSecurityTargetEntity] =
    new EntityPersistent[TypedSecurityTargetEntity] {
      def id(e: TypedSecurityTargetEntity): EntityId     = e.id
      def toRecord(e: TypedSecurityTargetEntity): Record = e.toRecord()
      def fromRecord(r: Record): Consequence[TypedSecurityTargetEntity] =
        (
          r.getAs[EntityId]("id"),
          r.getString("name")
        ) match
          case (Some(entityid), Some(entityname)) =>
            Consequence.success(TypedSecurityTargetEntity(entityid, entityname, "typed-owner"))
          case _ =>
            Consequence.argumentInvalid("invalid typed security target record")
      override def securityAttributes(e: TypedSecurityTargetEntity): Option[SecurityAttributes] =
        Some(SecurityAttributes.ownedBy(e.ownerid))
    }

  private def _security_record(ownerid: String): Record =
    SecurityAttributes.ownedBy(ownerid).toRecord

  private val _person_patch_persistent: EntityPersistentUpdate[PersonPatch] =
    new EntityPersistentUpdate[PersonPatch] {
      def collection(e: PersonPatch): EntityCollectionId = _cid
      def toRecord(e: PersonPatch): Record               = e.toRecord()
      def fromRecord(r: Record): Consequence[PersonPatch] =
        Consequence.success(PersonPatch(name = r.getString("name")))
    }

  private val _person_create_persistent
      : org.goldenport.cncf.entity.EntityPersistentCreate[PersonCreate] =
    new org.goldenport.cncf.entity.EntityPersistentCreate[PersonCreate] {
      def id(e: PersonCreate): Option[EntityId]           = e.id
      def toRecord(e: PersonCreate): Record               = e.toRecord()
      def collection(e: PersonCreate): EntityCollectionId = _cid
    }

  private final class MemoryBackend extends LogBackend {
    private val _lines = ListBuffer.empty[String]

    def lines: Vector[String] = _lines.synchronized {
      _lines.toVector
    }

    override def writeLine(line: String): Unit = _lines.synchronized {
      _lines += line
    }
  }
}
