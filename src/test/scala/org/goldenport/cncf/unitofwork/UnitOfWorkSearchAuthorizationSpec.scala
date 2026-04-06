package org.goldenport.cncf.unitofwork

import cats.~>
import org.goldenport.Consequence
import org.goldenport.cncf.context.{Capability, CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, ScopeContext, ScopeKind, SecurityContext, SecurityLevel, TraceId}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.directive.{Query, SearchResult}
import org.goldenport.cncf.entity.{EntityPersistent, EntityQuery, EntityStore, EntityStoreSpace}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.datatype.PathName
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.directive.Condition
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkSearchAuthorizationSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _cid = EntityCollectionId("test", "auth", "person")

  "UnitOfWork search authorization" should {
    "filter search results to owner-visible entities for general user" in {
      Given("a general user with two stored entities owned by different principals")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalId = "owner-1"
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "1", _cid), "taro", "owner-1")
      val p2 = PersonEntity(EntityId("test", "2", _cid), "hanako", "owner-2")
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p1.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p2.toRecord())
          )
        )
      )
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(summon[ExecutionContext]))

      When("searching through UnitOfWorkInterpreter with domain search authorization metadata")
      val result = interpreter.execute(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, Query(PersonQuery.any)),
          tc = summon[EntityPersistent[PersonEntity]],
          authorization = Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("Person"),
              accessKind = "search/list"
            )
          )
        )
      )

      Then("only owner-visible entity remains")
      result.data.map(_.id) shouldBe Vector(p1.id)
      result.totalCount shouldBe Some(1)
      result.fetchedCount shouldBe 1
    }

    "keep all search results for manager" in {
      Given("a manager principal")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalId = "manager-1",
        capabilities = Vector(Capability("content_manager"))
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "m1", _cid), "taro", "owner-1")
      val p2 = PersonEntity(EntityId("test", "m2", _cid), "hanako", "owner-2")
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p1.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p2.toRecord())
          )
        )
      )
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(summon[ExecutionContext]))

      When("searching through UnitOfWorkInterpreter")
      val result = interpreter.execute(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, Query(PersonQuery.any)),
          tc = summon[EntityPersistent[PersonEntity]],
          authorization = Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("Person"),
              accessKind = "search/list"
            )
          )
        )
      )

      Then("all entities remain visible")
      result.data.map(_.id).toSet shouldBe Set(p1.id, p2.id)
      result.totalCount shouldBe Some(2)
      result.fetchedCount shouldBe 2
    }

    "allow group-visible entities for matching group principal" in {
      Given("a user whose group matches entity security_attributes.group_id")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalId = "group-user",
        principalAttributes = Map("group_id" -> "team-a")
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "g1", _cid), "taro", "owner-x", groupId = Some("team-a"))
      val p2 = PersonEntity(EntityId("test", "g2", _cid), "hanako", "owner-y", groupId = Some("team-b"))
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p1.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p2.toRecord())
          )
        )
      )
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(summon[ExecutionContext]))

      When("searching through UnitOfWorkInterpreter")
      val result = interpreter.execute(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, Query(PersonQuery.any)),
          tc = summon[EntityPersistent[PersonEntity]],
          authorization = Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("Person"),
              accessKind = "search/list"
            )
          )
        )
      )

      Then("group-visible entity remains visible")
      result.data.map(_.id) shouldBe Vector(p1.id)
      result.totalCount shouldBe Some(1)
    }

    "allow privilege-visible entities for matching privilege principal" in {
      Given("a user whose privilege matches entity security_attributes.privilege_id")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalId = "priv-user",
        principalAttributes = Map("privilege" -> "vip-access")
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "p1", _cid), "taro", "owner-x", privilegeId = Some("vip-access"))
      val p2 = PersonEntity(EntityId("test", "p2", _cid), "hanako", "owner-y", privilegeId = Some("other-access"))
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p1.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p2.toRecord())
          )
        )
      )
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(summon[ExecutionContext]))

      When("searching through UnitOfWorkInterpreter")
      val result = interpreter.execute(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, Query(PersonQuery.any)),
          tc = summon[EntityPersistent[PersonEntity]],
          authorization = Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("Person"),
              accessKind = "search/list"
            )
          )
        )
      )

      Then("privilege-visible entity remains visible")
      result.data.map(_.id) shouldBe Vector(p1.id)
      result.totalCount shouldBe Some(1)
    }
  }

  private def _execution_context(
    datastorespace: DataStoreSpace,
    entitystorespace: EntityStoreSpace,
    principalId: String,
    capabilities: Vector[Capability] = Vector.empty,
    principalAttributes: Map[String, String] = Map.empty
  ): ExecutionContext = {
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
        name = "uow-search-authorization-spec-runtime",
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
      commitAction = uow => {
        val _ = uow.commit()
        ()
      },
      abortAction = uow => {
        val _ = uow.rollback()
        ()
      },
      disposeAction = _ => (),
      token = "uow-search-authorization-spec-runtime"
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
            "group" -> Record.dataAuto("read" -> true, "write" -> false, "execute" -> false),
            "other" -> Record.dataAuto("read" -> false, "write" -> false, "execute" -> false)
          )
        )
      )
  }

  private object PersonQuery {
    val any = PersonQuery(
      id = Condition.any[EntityId],
      name = Condition.any[String]
    )
  }

  private final case class PersonQuery(
    id: Condition[EntityId],
    name: Condition[String]
  ) {
    def toRecord(): Record =
      Record.dataAuto(
        "id" -> id,
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
        r.getString(PathName(Vector("security_attributes", "owner_id"))),
        r.getString(PathName(Vector("security_attributes", "group_id"))),
        r.getString(PathName(Vector("security_attributes", "privilege_id")))
      ) match
        case (Some(entityId), Some(entityName), Some(entityOwnerId), entityGroupId, entityPrivilegeId) =>
          Consequence.success(PersonEntity(entityId, entityName, entityOwnerId, entityGroupId, entityPrivilegeId))
        case _ =>
          Consequence.failure("invalid person record")
  }
}
