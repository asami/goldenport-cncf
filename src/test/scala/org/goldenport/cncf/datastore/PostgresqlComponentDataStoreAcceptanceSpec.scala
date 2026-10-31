package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.PostgresqlPersistenceFixture.*
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.EntityObservedDeleteCanonicalFixture.success
import org.goldenport.cncf.subsystem.SystemNode
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct. 14, 2026
 * @version Oct. 31, 2026
 */
@org.scalatest.DoNotDiscover
final class PostgresqlComponentDataStoreAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen with PostgresqlAcceptance {
  private val _routes = Vector(
    "kind" -> Vector("kind" -> "postgresql"),
    "type" -> Vector("type" -> "postgresql"),
    "explicit-dialect" -> Vector("kind" -> "jdbc", "sql.dialect" -> "postgresql"),
    "jdbc-auto" -> Vector("kind" -> "jdbc"),
    "url-auto" -> Vector.empty[(String, String)])
  private val _prefixes = Vector("textus.component.native.datastores.application",
    "cncf.component.native.datastore", "textus.datastore", "cncf.datastore")

  "Normal PostgreSQL ComponentDataStore resolution" should {
    "perform real native persistence through every direct and managed route" which {
      for { (route, values) <- _routes; prefix <- _prefixes; managed <- Vector(false, true) } {
        s"PG-E1/PG-E11 execute $route under $prefix with SystemNode ownership $managed" in {
          Given("an actual task-owned PostgreSQL database and ordinary component configuration")
          with_postgresql { database =>
            val environment = ComponentDataStore.Environment(ResolvedParameters.empty(),
              Some(database.configuration(prefix, values.map { case (key, value) => s"$prefix.$key" -> value }*)))
            val request = ComponentDataStore.Request("native")
            if (managed) {
              val node = SystemNode.create()
              val binding = node.bind()
              val lease = success(binding.acquireLeaseC())
              try {
                When("the ordinary managed resolver selects the native PostgreSQL view")
                val store = success(ComponentDataStore.resolveManagedForDataStoreSpaceC(environment, request, binding, lease, node.hmacKey)).get.asInstanceOf[SqlDataStore]
                val resource = store.managedResourceOption.get
                _exercise_store(store, database, database.token(s"pg_managed_${route}_${prefix.length}"))
                success(store.closeC())
                Then("the non-owning view leaves its actual SystemNode resource open until normal shutdown")
                resource.isOpen shouldBe true
                lease.release()
                binding.release()
                success(node.shutdownC())
                resource.isOpen shouldBe false
                resource.borrowC(_ => Consequence.unit) shouldBe a[Consequence.Failure[?]]
              } finally {
                lease.release()
                binding.release()
                success(node.shutdownC())
              }
            } else {
              When("ordinary direct component resolution constructs its caller-owned native store")
              val store = ComponentDataStore.resolve(environment, request).asInstanceOf[SqlDataStore]
              try {
                _exercise_store(store, database, database.token(s"pg_direct_${route}_${prefix.length}"))
                Then("the actual native store remains explicitly caller-owned")
                store.managedResourceOption.get.isOpen shouldBe true
                success(store.closeC())
                store.managedResourceOption.get.isOpen shouldBe false
              } finally success(store.closeC())
            }
          }
        }
      }
    }
    "preserve existing configuration precedence and physical resource identity" which {
      "PG-E1 apply parameters before configuration on real direct and managed storage paths" in {
        Given("configured MySQL selection and parameter-selected actual PostgreSQL under the same ordinary prefix")
        with_postgresql { database =>
          val prefix = "textus.component.native.datastores.application"
          val environment = ComponentDataStore.Environment(database.parameters(prefix, s"$prefix.kind" -> "postgresql"),
            Some(configuration(Vector(s"$prefix.kind" -> "mysql", s"$prefix.jdbc.url" -> "jdbc:mysql:incompatible-configured-target"))))
          val request = ComponentDataStore.Request("native")
          val node = SystemNode.create()
          val binding = node.bind()
          val lease = success(binding.acquireLeaseC())
          try {
            val direct = ComponentDataStore.resolve(environment, request).asInstanceOf[SqlDataStore]
            try {
              When("ordinary direct and managed selection apply the existing parameter precedence")
              val managed = success(ComponentDataStore.resolveManagedForDataStoreSpaceC(environment, request, binding, lease, node.hmacKey)).get.asInstanceOf[SqlDataStore]
              _exercise_store(direct, database, database.token("pg_parameter_direct"))
              _exercise_store(managed, database, database.token("pg_parameter_managed"))
              Then("both native routes positively persist under the selected PostgreSQL definition")
              managed.managedResourceOption.get.isOpen shouldBe true
            } finally success(direct.closeC())
          } finally {
            lease.release()
            binding.release()
            success(node.shutdownC())
          }
        }
      }
      "PG-E11 share only matching explicit-driver aliases inside one node and reject released borrows" in {
        Given("ordinary named and legacy application aliases carrying equal explicit PostgreSQL driver identities")
        with_postgresql { database =>
          val node = SystemNode.create()
          val firstbinding = node.bind()
          val secondbinding = node.bind()
          val firstlease = success(firstbinding.acquireLeaseC())
          val secondlease = success(secondbinding.acquireLeaseC())
          val firstprefix = "textus.component.native.datastores.application"
          val secondprefix = "textus.component.native.datastore"
          val firstenvironment = ComponentDataStore.Environment(ResolvedParameters.empty(), Some(database.configuration(firstprefix, s"$firstprefix.kind" -> "postgresql")))
          val secondenvironment = ComponentDataStore.Environment(ResolvedParameters.empty(), Some(database.configuration(secondprefix, s"$secondprefix.kind" -> "jdbc", s"$secondprefix.sql.dialect" -> "postgresql")))
          val request = ComponentDataStore.Request("native")
          try {
            When("both normal bindings acquire their actual registry-managed PostgreSQL views")
            val first = success(ComponentDataStore.resolveManagedForDataStoreSpaceC(firstenvironment, request, firstbinding, firstlease, node.hmacKey)).get.asInstanceOf[SqlDataStore]
            val second = success(ComponentDataStore.resolveManagedForDataStoreSpaceC(secondenvironment, request, secondbinding, secondlease, node.hmacKey)).get.asInstanceOf[SqlDataStore]
            val resource = first.managedResourceOption.get
            _exercise_store(first, database, database.token("pg_alias_native"))
            success(first.closeC())
            Then("matching identities share one physical resource and view close does not close the managed pool")
            (resource eq second.managedResourceOption.get) shouldBe true
            node.managedDataStoreCount shouldBe 1
            resource.isOpen shouldBe true
            firstlease.release()
            first.load(DataStore.CollectionId("released"), DataStore.StringEntryId("selected"))(using context(first)) shouldBe a[Consequence.Failure[?]]
            secondlease.release()
            firstbinding.release()
            secondbinding.release()
            success(node.shutdownC())
            resource.isOpen shouldBe false
            resource.borrowC(_ => Consequence.unit) shouldBe a[Consequence.Failure[?]]
            success(node.shutdownC())
            node.activeLeaseCount shouldBe 0
            node.bindingCount shouldBe 0
          } finally {
            firstlease.release()
            secondlease.release()
            firstbinding.release()
            secondbinding.release()
            success(node.shutdownC())
          }
        }
      }
      "PG-E11 retain separate actual managed pools across different SystemNodes" in {
        Given("two running nodes resolving equal ordinary definitions against the actual native database")
        with_postgresql { database =>
          val firstnode = SystemNode.create()
          val secondnode = SystemNode.create()
          val firstbinding = firstnode.bind()
          val secondbinding = secondnode.bind()
          val firstlease = success(firstbinding.acquireLeaseC())
          val secondlease = success(secondbinding.acquireLeaseC())
          val prefix = "textus.datastore"
          val environment = ComponentDataStore.Environment(ResolvedParameters.empty(), Some(database.configuration(prefix, s"$prefix.kind" -> "postgresql")))
          try {
            When("both normal nodes construct their own native managed resources")
            val first = success(ComponentDataStore.resolveManagedForDataStoreSpaceC(environment, ComponentDataStore.Request("native"), firstbinding, firstlease, firstnode.hmacKey)).get.asInstanceOf[SqlDataStore]
            val second = success(ComponentDataStore.resolveManagedForDataStoreSpaceC(environment, ComponentDataStore.Request("native"), secondbinding, secondlease, secondnode.hmacKey)).get.asInstanceOf[SqlDataStore]
            _exercise_store(first, database, database.token("pg_first_node"))
            _exercise_store(second, database, database.token("pg_second_node"))
            Then("resource ownership remains local to each node")
            (first.managedResourceOption.get eq second.managedResourceOption.get) shouldBe false
            firstlease.release()
            firstbinding.release()
            success(firstnode.shutdownC())
            first.managedResourceOption.get.isOpen shouldBe false
            second.managedResourceOption.get.isOpen shouldBe true
          } finally {
            firstlease.release()
            secondlease.release()
            firstbinding.release()
            secondbinding.release()
            try success(firstnode.shutdownC()) finally success(secondnode.shutdownC())
          }
        }
      }
      "PG-E1 reject an explicitly incompatible PostgreSQL URL with fixed nonsensitive diagnostics" in {
        Given("an explicit native PostgreSQL selection and an incompatible JDBC scheme")
        When("the structured factory admits the selected dialect before opening any pool")
        val result = SqlDataStore.jdbcC("jdbc:sqlite:incompatible-private-target", SqlDataStore.Postgresql)
        Then("the existing structured factory boundary rejects it without exposing the target")
        result shouldBe a[Consequence.Failure[?]]
        result.asInstanceOf[Consequence.Failure[?]].conclusion.getException.map(_.getMessage) shouldBe Some("PostgreSQL dialect requires a PostgreSQL JDBC URL")
      }
    }
  }

  private def _exercise_store(store: SqlDataStore, database: Database, token: String): Unit = {
    given ExecutionContext = context(store)
    val collection = DataStore.CollectionId(token)
    val entry = DataStore.StringEntryId("selected")
    val reader = database.open()
    When("the normally selected provider performs actual strict create")
    val created = success(new DataStoreSpace().useDataStore(store).createEntity(EntityCreatePlan(collection, entry,
      Record.dataAuto("title" -> "Created", "cncf_revision" -> 1L))))
    Then("the authoritative created record equals an independent native read")
    created.record.asMap shouldBe success(reader.load(collection, entry)).get.asMap
    When("that same normally selected provider performs native update")
    success(store.mutateEntityDirect(EntityDirectMutationPlan(collection, entry, "cncf_revision", Record.dataAuto("title" -> "Updated"))))
    Then("the independent reader observes the actual updated value")
    success(reader.load(collection, entry)).get.getString("title") shouldBe Some("Updated")
    When("strict observed deletion executes on the same normally selected provider")
    success(store.compareAndDeleteEntity(EntityCompareAndDeletePlan(collection, entry, "cncf_revision", revision(2), EntityDeleteRootMutation.Physical))) shouldBe EntityCompareAndDeleteResult.Deleted(revision(2))
    Then("the independent reader sees the native target absent")
    success(reader.load(collection, entry)) shouldBe None
  }
}
