package org.goldenport.cncf.unitofwork

import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.context.{Capability, CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, ScopeContext, ScopeKind, SecurityContext, SecurityLevel, TraceId}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate, EntityStore, EntityStoreSpace}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkTargetAuthorizationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _cid = EntityCollectionId("test", "authz", "person")

  "UnitOfWork target authorization" should {
    "allow create by default for domain resources in phase 1" in {
      given ExecutionContext = _execution_context(
        principalId = "creator-user"
      )
      given org.goldenport.cncf.entity.EntityPersistentCreate[PersonCreate] = _person_create_persistent

      val entity = PersonCreate("saburo", "owner-z", groupId = Some("team-z"))
      val uow = new UnitOfWork(summon[ExecutionContext])

      val result = new UnitOfWorkInterpreter(uow).run(
        org.goldenport.ConsequenceT.liftF(
          cats.free.Free.liftF[UnitOfWorkOp, org.goldenport.cncf.entity.CreateResult[PersonCreate]](
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

      result shouldBe a[Consequence.Success[_]]
    }

    "allow load for a group-visible entity" in {
      given ExecutionContext = _execution_context(
        principalId = "group-user",
        principalAttributes = Map("group_id" -> "team-a")
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val id = EntityId("test", "load_group", _cid)
      _seed(PersonEntity(id, "taro", "owner-x", groupId = Some("team-a")))
      val uow = new UnitOfWork(summon[ExecutionContext])

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

      result.map(_.map(_.id)) shouldBe Consequence.success(Some(id))
    }

    "allow save for a group-visible entity" in {
      given ExecutionContext = _execution_context(
        principalId = "group-user",
        principalAttributes = Map("group_id" -> "team-a")
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val id = EntityId("test", "save_group", _cid)
      _seed(PersonEntity(id, "taro", "owner-x", groupId = Some("team-a")))
      val uow = new UnitOfWork(summon[ExecutionContext])

      val result = new UnitOfWorkInterpreter(uow).run(
        org.goldenport.ConsequenceT.liftF(
          cats.free.Free.liftF[UnitOfWorkOp, Unit](
            UnitOfWorkOp.EntityStoreSave(
              entity = PersonEntity(id, "taro-2", "owner-x", groupId = Some("team-a")),
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

      result shouldBe Consequence.unit
      _load_name(id) shouldBe Consequence.success(Some("taro-2"))
    }

    "reject update for a non-owner non-group non-privileged user" in {
      given ExecutionContext = _execution_context(
        principalId = "other-user"
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val id = EntityId("test", "update_denied", _cid)
      _seed(PersonEntity(id, "shiro", "owner-x"))
      val uow = new UnitOfWork(summon[ExecutionContext])

      val result = new UnitOfWorkInterpreter(uow).run(
        org.goldenport.ConsequenceT.liftF(
          cats.free.Free.liftF[UnitOfWorkOp, Unit](
            UnitOfWorkOp.EntityStoreUpdate(
              entity = PersonEntity(id, "shiro-2", "owner-x"),
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

      result shouldBe a[Consequence.Failure[_]]
      _load_name(id) shouldBe Consequence.success(Some("shiro"))
    }

    "allow update-by-id for a privilege-visible entity" in {
      given ExecutionContext = _execution_context(
        principalId = "priv-user",
        principalAttributes = Map("privilege" -> "vip-access")
      )
      given EntityPersistent[PersonEntity] = _person_persistent
      given EntityPersistentUpdate[PersonPatch] = _person_patch_persistent

      val id = EntityId("test", "update_priv", _cid)
      _seed(PersonEntity(id, "hanako", "owner-x", privilegeId = Some("vip-access")))
      val uow = new UnitOfWork(summon[ExecutionContext])

      val result = new UnitOfWorkInterpreter(uow).run(
        org.goldenport.ConsequenceT.liftF(
          cats.free.Free.liftF[UnitOfWorkOp, Unit](
            UnitOfWorkOp.EntityStoreUpdateById(
              id = id,
              patch = PersonPatch(name = Some("hanako-2")),
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

      result shouldBe Consequence.unit
      _load_name(id) shouldBe Consequence.success(Some("hanako-2"))
    }

    "reject delete when rights deny access for non-owner non-group non-privileged user" in {
      given ExecutionContext = _execution_context(
        principalId = "other-user"
      )
      val id = EntityId("test", "delete_denied", _cid)
      _seed(PersonEntity(id, "jiro", "owner-x"))
      val uow = new UnitOfWork(summon[ExecutionContext])

      val result = new UnitOfWorkInterpreter(uow).run(
        org.goldenport.ConsequenceT.liftF(
          cats.free.Free.liftF[UnitOfWorkOp, Unit](
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

      result shouldBe a[Consequence.Failure[_]]
      _load_name(id) shouldBe Consequence.success(Some("jiro"))
    }
  }

  private def _execution_context(
    principalId: String,
    capabilities: Vector[Capability] = Vector.empty,
    principalAttributes: Map[String, String] = Map.empty
  ): ExecutionContext = {
    val datastorespace = DataStoreSpace.default()
    val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
    val observability = ObservabilityContext(
      traceId = TraceId("test", "runtime"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "runtime"))
    )
    val driver = FakeHttpDriver.okText("nop")
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
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in test context")
      },
      commitAction = uow => { val _ = uow.commit(); () },
      abortAction = uow => { val _ = uow.rollback(); () },
      disposeAction = _ => (),
      token = "uow-target-authorization-spec-runtime"
    )
    context match {
      case i: ExecutionContext.Instance =>
        val principal = new Principal {
          def id: PrincipalId = PrincipalId(principalId)
          def attributes: Map[String, String] = principalAttributes
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
  }

  private def _seed(entity: PersonEntity)(using ctx: ExecutionContext): Unit = {
    val _ = ctx.dataStoreSpace.inject(
      DataStoreSpace.Seed(
        Vector(
          DataStoreSpace.SeedEntry(
            DataStore.CollectionId.EntityStore(_cid),
            entity.toRecord()
          )
        )
      )
    )
  }

  private def _load_name(id: EntityId)(using ctx: ExecutionContext): Consequence[Option[String]] =
    for {
      cid <- ctx.entityStoreSpace.dataStoreCollection(id)
      dsid <- ctx.entityStoreSpace.dataStoreEntryId(id)
      ds <- ctx.dataStoreSpace.dataStore(cid)
      rec <- ds.load(cid, dsid)
    } yield rec.flatMap(_.getString("name"))

  private final case class PersonEntity(
    id: EntityId,
    name: String,
    ownerId: String,
    groupId: Option[String] = None,
    privilegeId: Option[String] = None
  ) {
    def toRecord(): Record =
      Record.dataAuto(
        "id" -> id,
        "name" -> name,
        "security_attributes" -> Record.dataAuto(
          "owner_id" -> ownerId,
          "group_id" -> groupId,
          "privilege_id" -> privilegeId,
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
    ownerId: String,
    groupId: Option[String] = None,
    privilegeId: Option[String] = None
  ) {
    def toRecord(): Record =
      Record.dataAuto(
        "name" -> name,
        "security_attributes" -> Record.dataAuto(
          "owner_id" -> ownerId,
          "group_id" -> groupId,
          "privilege_id" -> privilegeId,
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

  private val _person_persistent: EntityPersistent[PersonEntity] = new EntityPersistent[PersonEntity] {
    def id(e: PersonEntity): EntityId = e.id
    def toRecord(e: PersonEntity): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[PersonEntity] =
      (
        r.getAs[EntityId]("id"),
        r.getString("name"),
        r.getString(org.goldenport.datatype.PathName(Vector("security_attributes", "owner_id"))),
        r.getString(org.goldenport.datatype.PathName(Vector("security_attributes", "group_id"))),
        r.getString(org.goldenport.datatype.PathName(Vector("security_attributes", "privilege_id")))
      ) match
        case (Some(entityId), Some(entityName), Some(entityOwnerId), entityGroupId, entityPrivilegeId) =>
          Consequence.success(PersonEntity(entityId, entityName, entityOwnerId, entityGroupId, entityPrivilegeId))
        case _ =>
          Consequence.failure("invalid person record")
  }

  private val _person_patch_persistent: EntityPersistentUpdate[PersonPatch] = new EntityPersistentUpdate[PersonPatch] {
    def collection(e: PersonPatch): EntityCollectionId = _cid
    def toRecord(e: PersonPatch): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[PersonPatch] =
      Consequence.success(PersonPatch(name = r.getString("name")))
  }

  private val _person_create_persistent: org.goldenport.cncf.entity.EntityPersistentCreate[PersonCreate] =
    new org.goldenport.cncf.entity.EntityPersistentCreate[PersonCreate] {
      def id(e: PersonCreate): Option[EntityId] = None
      def toRecord(e: PersonCreate): Record = e.toRecord()
      def collection(e: PersonCreate): EntityCollectionId = _cid
    }
}
