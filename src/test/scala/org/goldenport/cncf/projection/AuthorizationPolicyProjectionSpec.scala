package org.goldenport.cncf.projection

import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.security.{AuthorizationResourcePolicies, AuthorizationResourcePolicy, SecurityRoleDefinition}
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthorizationBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding, Subsystem}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 28, 2026
 * @version Apr. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class AuthorizationPolicyProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "AuthorizationPolicyProjection" should {
    "project descriptor roles, resource policies, and Blob operation requirements deterministically" in {
      Given("a subsystem with descriptor-backed Blob authorization policies")
      val (subsystem, component) = _subsystem_with_blob_authorization()

      When("projecting effective authorization policy metadata")
      val projection = AuthorizationPolicyProjection.project(subsystem.components, subsystem.name)
      val describe = DescribeProjection.project(component, Some(component.name)).asMap("authorizationPolicies").asInstanceOf[Record]
      val schema = SchemaProjection.project(component, Some(component.name)).asMap("authorizationPolicies").asInstanceOf[Record]

      Then("role definitions are visible with expanded descriptor metadata")
      val roles = _records(projection.asMap("roleDefinitions"))
      roles.map(_.getString("name").getOrElse("")) shouldBe Vector("bloboperator", "supportoperator")
      _strings(roles.head.asMap("includes")) shouldBe Vector("support_operator")
      _strings(roles.head.asMap("capabilities")) should contain ("association:blob_attachment:create")

      And("resource policies use normalized lookup keys matching enforcement")
      val resources = _records(projection.asMap("resourcePolicies"))
      resources.map(r => (r.getString("family").getOrElse(""), r.getString("resource").getOrElse(""), r.getString("action").getOrElse(""))) should contain allOf (
        ("collection", "blob", "create"),
        ("association", "blobattachment", "create"),
        ("association", "blobattachment", "search/list"),
        ("store", "blobstore", "status")
      )
      resources.find(_.getString("action").contains("delete")).flatMap(_.getString("permissionOverride")) shouldBe Some("execute")

      And("Blob operation requirement rows cover collection, association, store, and source entity checks")
      val requirements = _records(projection.asMap("blobOperationRequirements"))
      requirements.map(_.getString("operation").getOrElse("")) should contain allOf (
        "register_blob",
        "attach_blob_to_entity",
        "detach_blob_from_entity",
        "list_entity_blobs",
        "admin_blob_store_status"
      )
      requirements.map(r => (r.getString("family").getOrElse(""), r.getString("resource").getOrElse(""), r.getString("action").getOrElse(""))) should contain allOf (
        ("domain", "sourceentityid", "update"),
        ("association", "blobattachment", "create"),
        ("association", "blobattachment", "search/list"),
        ("store", "blobstore", "status")
      )

      And("DescribeProjection and SchemaProjection expose the same additive metadata surface")
      _records(describe.asMap("resourcePolicies")).map(_.getString("family").getOrElse("")) should contain ("association")
      _records(schema.asMap("blobOperationRequirements")).map(_.getString("operation").getOrElse("")) should contain ("admin_delete_blob")
    }
  }

  private def _subsystem_with_blob_authorization(): (Subsystem, Component) = {
    val policies = AuthorizationResourcePolicies(
      collections = Map(
        "blob" -> Map(
          "create" -> AuthorizationResourcePolicy(capabilities = Vector("collection:blob:create")),
          "read" -> AuthorizationResourcePolicy(capabilities = Vector("collection:blob:read")),
          "delete" -> AuthorizationResourcePolicy(permission = Some("execute"))
        )
      ),
      associations = Map(
        "blobattachment" -> Map(
          "create" -> AuthorizationResourcePolicy(capabilities = Vector("association:blob_attachment:create")),
          "delete" -> AuthorizationResourcePolicy(capabilities = Vector("association:blob_attachment:delete")),
          "search/list" -> AuthorizationResourcePolicy(capabilities = Vector("association:blob_attachment:search/list"))
        )
      ),
      stores = Map(
        "blobstore" -> Map(
          "status" -> AuthorizationResourcePolicy(capabilities = Vector("store:blobstore:status"))
        )
      )
    )
    val roles = Map(
      "blob_operator" -> SecurityRoleDefinition(
        "blob_operator",
        includes = Vector("support_operator"),
        capabilities = Vector("collection:blob:create", "association:blob_attachment:create")
      ),
      "support_operator" -> SecurityRoleDefinition(
        "support_operator",
        capabilities = Vector("collection:blob:read")
      )
    )
    val subsystem = Subsystem(
      name = "blob-authz-projection",
      scopeContext = Some(
        ScopeContext(
          kind = ScopeKind.Subsystem,
          name = "blob-authz-projection",
          parent = None,
          observabilityContext = ExecutionContext.create().observability
        )
      ),
      configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
    ).withDescriptor(
      GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<memory>"),
        subsystemName = "blob-authz-projection",
        componentBindings = Vector(GenericSubsystemComponentBinding("blob")),
        security = Some(GenericSubsystemSecurityBinding(
          authorization = Some(GenericSubsystemAuthorizationBinding(roles = roles, resources = policies))
        ))
      )
    )
    val owner = subsystem
    val component = new Component() {
      override val core: Component.Core =
        Component.Core.create(
          "blob",
          ComponentId("blob"),
          ComponentInstanceId.default(ComponentId("blob")),
          Protocol.empty
        )
      override def subsystem: Option[Subsystem] = Some(owner)
    }
    val assembled = subsystem.add(Vector(component))
    assembled -> assembled.findComponent("blob").get
  }

  private def _records(value: Any): Vector[Record] =
    value match {
      case xs: Seq[?] => xs.collect { case r: Record => r }.toVector
      case _ => Vector.empty
    }

  private def _strings(value: Any): Vector[String] =
    value match {
      case xs: Seq[?] => xs.map(_.toString).toVector
      case _ => Vector.empty
    }
}
