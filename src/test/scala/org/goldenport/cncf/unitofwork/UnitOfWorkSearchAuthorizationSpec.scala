package org.goldenport.cncf.unitofwork

import cats.~>
import scala.collection.mutable.ListBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.{Capability, CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, ScopeContext, ScopeKind, SecurityContext, SecurityLevel, TraceId}
import org.goldenport.cncf.datastore.{DataStore, DataStoreSpace}
import org.goldenport.cncf.directive.{Query, SearchResult}
import org.goldenport.cncf.entity.{EntityPersistent, EntityQuery, EntityStore, EntityStoreSpace}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.cncf.security.{EntityAbacCondition, EntityAccessRelation}
import org.goldenport.datatype.PathName
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.directive.Condition
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  7, 2026
 * @version Apr. 13, 2026
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

      val p1 = PersonEntity(EntityId("test", "a", _cid), "taro", "owner-1")
      val p2 = PersonEntity(EntityId("test", "b", _cid), "hanako", "owner-2")
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

    "emit diagnostics when natural condition filters search results" in {
      Given("a search/list authorization with a publication window condition")
      val backend = new MemoryBackend
      LogBackendHolder.reset()
      LogBackendHolder.install(backend)
      try {
        val datastorespace = DataStoreSpace.default()
        val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
        given ExecutionContext = _execution_context(
          datastorespace,
          entitystorespace,
          principalId = "reader"
        )
        given EntityPersistent[PersonEntity] = _person_persistent

        val p1 = PersonEntity(EntityId("test", "n1", _cid), "published", "reader", publishAt = Some("2000-01-01T00:00:00Z"))
        val p2 = PersonEntity(EntityId("test", "n2", _cid), "future", "reader", publishAt = Some("2999-01-01T00:00:00Z"))
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
                accessKind = "search/list",
                naturalConditions = EntityAbacCondition.parseList("publishAt<=now:search/list")
              )
            )
          )
        )

        Then("the future entity is filtered and an ABAC filter event is emitted")
        result.data.map(_.id) shouldBe Vector(p1.id)
        result.totalCount shouldBe Some(1)
        backend.lines.exists(_.contains("authorization.abac.filter")) shouldBe true
      } finally {
        LogBackendHolder.reset()
      }
    }

    "filter search results by relation rule" in {
      Given("a principal whose customer and account relations each match one entity")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalId = "customer-user",
        principalAttributes = Map(
          "customer_id" -> "customer-a",
          "account_id" -> "account-a"
        )
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "r1", _cid), "customer-a-record", "owner-x", customerId = Some("customer-a"))
      val p2 = PersonEntity(EntityId("test", "r2", _cid), "account-a-record", "owner-y", customerId = Some("customer-b"), accountId = Some("account-a"))
      val p3 = PersonEntity(EntityId("test", "r3", _cid), "unrelated-record", "owner-z")
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p1.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p2.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p3.toRecord())
          )
        )
      )
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(summon[ExecutionContext]))

      When("searching with customer and account relation rules")
      val result = interpreter.execute(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, Query(PersonQuery.any)),
          tc = summon[EntityPersistent[PersonEntity]],
          authorization = Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("Person"),
              accessKind = "search/list",
              relationRules = Vector(
                EntityAccessRelation("customerId", "customerId", Set("search/list")),
                EntityAccessRelation("accountId", "accountId", Set("search/list"))
              )
            )
          )
        )
      )

      Then("customer or account related entities remain visible")
      result.data.map(_.id).toSet shouldBe Set(p1.id, p2.id)
      result.totalCount shouldBe Some(2)
      result.fetchedCount shouldBe 2
    }

    "ignore relation rule whose access kinds do not include search list" in {
      Given("a relation rule that only grants read")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalId = "customer-user",
        principalAttributes = Map("customer_id" -> "customer-a")
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "rk1", _cid), "customer-a-record", "owner-x", customerId = Some("customer-a"))
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p1.toRecord())
          )
        )
      )
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(summon[ExecutionContext]))

      When("searching with a read-only relation rule")
      val result = interpreter.execute(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, Query(PersonQuery.any)),
          tc = summon[EntityPersistent[PersonEntity]],
          authorization = Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("Person"),
              accessKind = "search/list",
              relationRules = Vector(EntityAccessRelation("customerId", "customerId", Set("read")))
            )
          )
        )
      )

      Then("the relation rule does not make the entity visible for search")
      result.data shouldBe Vector.empty
      result.totalCount shouldBe Some(0)
      result.fetchedCount shouldBe 0
    }

    "filter search results by principal id relation rule" in {
      Given("a principal whose id matches an entity owner id relation")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalId = "principal-owner"
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "rp1", _cid), "principal-record", "principal-owner")
      val p2 = PersonEntity(EntityId("test", "rp2", _cid), "other-record", "other-owner")
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p1.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p2.toRecord())
          )
        )
      )
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(summon[ExecutionContext]))

      When("searching with a principal id relation rule")
      val result = interpreter.execute(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, Query(PersonQuery.any)),
          tc = summon[EntityPersistent[PersonEntity]],
          authorization = Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("Person"),
              accessKind = "search/list",
              relationRules = Vector(EntityAccessRelation("ownerId", "subjectId", Set("search/list")))
            )
          )
        )
      )

      Then("only the principal-related entity remains")
      result.data.map(_.id) shouldBe Vector(p1.id)
      result.totalCount shouldBe Some(1)
      result.fetchedCount shouldBe 1
    }

    "filter search results by tenant and organization relation rules" in {
      Given("a principal whose tenant and organization match only one entity")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalId = "tenant-user",
        principalAttributes = Map(
          "tenant_id" -> "tenant-a",
          "organization_id" -> "org-a"
        )
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "rt1", _cid), "tenant-org-record", "owner-x", tenantId = Some("tenant-a"), organizationId = Some("org-a"))
      val p2 = PersonEntity(EntityId("test", "rt2", _cid), "tenant-only-record", "owner-y", tenantId = Some("tenant-a"), organizationId = Some("org-b"))
      val p3 = PersonEntity(EntityId("test", "rt3", _cid), "organization-only-record", "owner-z", tenantId = Some("tenant-b"), organizationId = Some("org-a"))
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p1.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p2.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p3.toRecord())
          )
        )
      )
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(summon[ExecutionContext]))

      When("searching with tenant and organization relation rules")
      val result = interpreter.execute(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, Query(PersonQuery.any)),
          tc = summon[EntityPersistent[PersonEntity]],
          authorization = Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("Person"),
              accessKind = "search/list",
              relationRules = Vector(
                EntityAccessRelation("tenantId", "tenantId", Set("search/list")),
                EntityAccessRelation("organizationId", "organizationId", Set("search/list"))
              )
            )
          )
        )
      )

      Then("entities matching either relation rule remain visible")
      result.data.map(_.id).toSet shouldBe Set(p1.id, p2.id, p3.id)
      result.totalCount shouldBe Some(3)
      result.fetchedCount shouldBe 3
    }

    "filter search results by assignee and participant relation rules" in {
      Given("a principal assigned to one entity and participating in another")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalId = "work-user"
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "ra1", _cid), "assigned-record", "owner-x", assigneeId = Some("work-user"))
      val p2 = PersonEntity(EntityId("test", "ra2", _cid), "participant-record", "owner-y", participantId = Some("work-user"))
      val p3 = PersonEntity(EntityId("test", "ra3", _cid), "unrelated-record", "owner-z", assigneeId = Some("other-user"), participantId = Some("another-user"))
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p1.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p2.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p3.toRecord())
          )
        )
      )
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(summon[ExecutionContext]))

      When("searching with assignee and participant relation rules")
      val result = interpreter.execute(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, Query(PersonQuery.any)),
          tc = summon[EntityPersistent[PersonEntity]],
          authorization = Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("Person"),
              accessKind = "search/list",
              relationRules = Vector(
                EntityAccessRelation("assigneeId", "subjectId", Set("search/list")),
                EntityAccessRelation("participantId", "subjectId", Set("search/list"))
              )
            )
          )
        )
      )

      Then("assigned or participating entities remain visible")
      result.data.map(_.id).toSet shouldBe Set(p1.id, p2.id)
      result.totalCount shouldBe Some(2)
      result.fetchedCount shouldBe 2
    }

    "filter search results by business boundary natural conditions" in {
      Given("a principal whose business boundary attributes match only one entity")
      val datastorespace = DataStoreSpace.default()
      val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
      given ExecutionContext = _execution_context(
        datastorespace,
        entitystorespace,
        principalId = "boundary-user",
        principalAttributes = Map(
          "tenant_id" -> "tenant-a",
          "organization_id" -> "org-a",
          "account_id" -> "account-a",
          "customer_id" -> "customer-a"
        )
      )
      given EntityPersistent[PersonEntity] = _person_persistent

      val p1 = PersonEntity(EntityId("test", "bn1", _cid), "boundary-record", "boundary-user", customerId = Some("customer-a"), accountId = Some("account-a"), tenantId = Some("tenant-a"), organizationId = Some("org-a"))
      val p2 = PersonEntity(EntityId("test", "bn2", _cid), "wrong-customer-record", "boundary-user", customerId = Some("customer-b"), accountId = Some("account-a"), tenantId = Some("tenant-a"), organizationId = Some("org-a"))
      val p3 = PersonEntity(EntityId("test", "bn3", _cid), "wrong-tenant-record", "boundary-user", customerId = Some("customer-a"), accountId = Some("account-a"), tenantId = Some("tenant-b"), organizationId = Some("org-a"))
      val _ = datastorespace.inject(
        DataStoreSpace.Seed(
          Vector(
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p1.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p2.toRecord()),
            DataStoreSpace.SeedEntry(DataStore.CollectionId.EntityStore(_cid), p3.toRecord())
          )
        )
      )
      val interpreter = new UnitOfWorkInterpreter(new UnitOfWork(summon[ExecutionContext]))

      When("searching with business boundary natural conditions")
      val result = interpreter.execute(
        UnitOfWorkOp.EntityStoreSearch(
          query = EntityQuery(_cid, Query(PersonQuery.any)),
          tc = summon[EntityPersistent[PersonEntity]],
          authorization = Some(
            UnitOfWorkAuthorization(
              resourceFamily = "domain",
              resourceType = Some("Person"),
              accessKind = "search/list",
              naturalConditions = EntityAbacCondition.parseList(
                "tenantId=subject.tenantId:search/list;organizationId=subject.organizationId:search/list;accountId=subject.accountId:search/list;customerId=subject.customerId:search/list"
              )
            )
          )
        )
      )

      Then("only the entity matching every boundary remains visible")
      result.data.map(_.id) shouldBe Vector(p1.id)
      result.totalCount shouldBe Some(1)
      result.fetchedCount shouldBe 1
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
    privilegeId: Option[String] = None,
    publishAt: Option[String] = None,
    customerId: Option[String] = None,
    accountId: Option[String] = None,
    tenantId: Option[String] = None,
    organizationId: Option[String] = None,
    assigneeId: Option[String] = None,
    participantId: Option[String] = None
  ) {
    def toRecord(): Record =
      Record.dataAuto(
        "id" -> id,
        "name" -> name,
        "publishAt" -> publishAt,
        "customerId" -> customerId,
        "accountId" -> accountId,
        "tenantId" -> tenantId,
        "organizationId" -> organizationId,
        "assigneeId" -> assigneeId,
        "participantId" -> participantId,
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
        r.getString(PathName(Vector("security_attributes", "privilege_id"))),
        r.getString("publishAt").orElse(r.getString("publish_at")),
        r.getString("customerId").orElse(r.getString("customer_id")),
        r.getString("accountId").orElse(r.getString("account_id")),
        r.getString("tenantId").orElse(r.getString("tenant_id")),
        r.getString("organizationId").orElse(r.getString("organization_id")),
        r.getString("assigneeId").orElse(r.getString("assignee_id")),
        r.getString("participantId").orElse(r.getString("participant_id"))
      ) match
        case (Some(entityId), Some(entityName), Some(entityOwnerId), entityGroupId, entityPrivilegeId, publishAt, customerId, accountId, tenantId, organizationId, assigneeId, participantId) =>
          Consequence.success(PersonEntity(entityId, entityName, entityOwnerId, entityGroupId, entityPrivilegeId, publishAt, customerId, accountId, tenantId, organizationId, assigneeId, participantId))
        case _ =>
          Consequence.failure("invalid person record")
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
