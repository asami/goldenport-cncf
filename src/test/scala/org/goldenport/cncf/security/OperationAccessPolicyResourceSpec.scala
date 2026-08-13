package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.context.{Capability, ExecutionContext, Principal, PrincipalId, ScopeContext, ScopeKind, SecurityContext}
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthorizationBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding, Subsystem}
import org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.value.SecurityAttributes
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 28, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationAccessPolicyResourceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _blob_collection = EntityCollectionId("cncf", "builtin", "blob")
  private val _blob_id = EntityId("cncf", "blob_1", _blob_collection)

  "OperationAccessPolicy resource policies" should {
    "deny Blob collection create without the configured collection capability" in {
      Given("a Blob collection create request without the configured capability")
      given ExecutionContext = _context(capabilities = Set.empty)

      When("the unit of work is authorized")
      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("blob"),
          collectionName = Some("blob"),
          accessKind = "create"
        )
      )

      Then("authorization is denied")
      result shouldBe a[Consequence.Failure[_]]
    }

    "allow Blob collection create with the configured collection capability" in {
      Given("a Blob collection create request with the configured capability")
      given ExecutionContext = _context(capabilities = Set("collection:blob:create"))

      When("the unit of work is authorized")
      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("blob"),
          collectionName = Some("blob"),
          accessKind = "create"
        )
      )

      Then("authorization succeeds")
      result shouldBe Consequence.unit
    }

    "require execute permission for Blob delete when collection policy overrides delete" in {
      Given("an owned Blob delete request without execute permission")
      given ExecutionContext = _context(capabilities = Set.empty, principalid = "owner")
      val record = _record(SecurityAttributes.ownedBy("owner"))

      When("the overridden unit of work is authorized")
      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("blob"),
          collectionName = Some("blob"),
          targetId = Some(_blob_id),
          accessKind = "delete"
        ),
        _ => Consequence.success(Some(record))
      )

      Then("authorization is denied")
      result shouldBe a[Consequence.Failure[_]]
    }

    "allow Blob delete when owner has execute permission for override policy" in {
      Given("an owned Blob delete request with execute permission")
      given ExecutionContext = _context(capabilities = Set.empty, principalid = "owner")
      val security = SecurityAttributes.ownedBy(
        "owner",
        owner = SecurityAttributes.Rights.Permissions.full
      )
      val record = _record(security)

      When("the overridden unit of work is authorized")
      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("blob"),
          collectionName = Some("blob"),
          targetId = Some(_blob_id),
          accessKind = "delete"
        ),
        _ => Consequence.success(Some(record))
      )

      Then("authorization succeeds")
      result shouldBe Consequence.unit
    }

    "require association-domain capability for Blob attachment create and delete" in {
      Given("Blob attachment requests with and without the association capability")
      When("each attachment request is authorized")
      val denied = {
        given ExecutionContext = _context(capabilities = Set.empty)
        OperationAccessPolicy.authorizeUnitOfWorkDefault(
          UnitOfWorkAuthorization(
            resourceFamily = "association",
            resourceType = Some("blob_attachment"),
            collectionName = Some("cncf-blob-association"),
            accessKind = "create"
          )
        )
      }
      val allowed = {
        given ExecutionContext = _context(capabilities = Set("association:blob_attachment:create"))
        OperationAccessPolicy.authorizeUnitOfWorkDefault(
          UnitOfWorkAuthorization(
            resourceFamily = "association",
            resourceType = Some("blob_attachment"),
            collectionName = Some("cncf-blob-association"),
            accessKind = "create"
          )
        )
      }

      Then("only the capability-bearing request succeeds")
      denied shouldBe a[Consequence.Failure[_]]
      allowed shouldBe Consequence.unit
    }

    "require store capability for BlobStore status" in {
      Given("BlobStore status requests with and without the store capability")
      When("each status request is authorized")
      val denied = {
        given ExecutionContext = _context(capabilities = Set.empty)
        OperationAccessPolicy.authorizeUnitOfWorkDefault(
          UnitOfWorkAuthorization(
            resourceFamily = "store",
            resourceType = Some("blobstore"),
            collectionName = Some("blob-payload-store"),
            accessKind = "status"
          )
        )
      }
      val allowed = {
        given ExecutionContext = _context(capabilities = Set("store:blobstore:status"))
        OperationAccessPolicy.authorizeUnitOfWorkDefault(
          UnitOfWorkAuthorization(
            resourceFamily = "store",
            resourceType = Some("blobstore"),
            collectionName = Some("blob-payload-store"),
            accessKind = "status"
          )
        )
      }

      Then("only the capability-bearing request succeeds")
      denied shouldBe a[Consequence.Failure[_]]
      allowed shouldBe Consequence.unit
    }

    "record missing capability as an authorization diagnostic" in {
      Given("a BlobStore status request without its capability")
      val before = RuntimeDashboardMetrics.authorizationDiagnosticCounts.getOrElse("capability", 0L)
      given ExecutionContext = _context(capabilities = Set.empty)

      When("the request is authorized")
      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "store",
          resourceType = Some("blobstore"),
          collectionName = Some("blob-payload-store"),
          accessKind = "status"
        )
      )

      Then("a capability diagnostic is recorded")
      result shouldBe a[Consequence.Failure[_]]
      RuntimeDashboardMetrics.authorizationDiagnosticCounts.getOrElse("capability", 0L) should be > before
    }

    "record permission override denial as a permission diagnostic" in {
      Given("an owned Blob delete request without execute permission")
      val before = RuntimeDashboardMetrics.authorizationDiagnosticCounts.getOrElse("permission", 0L)
      given ExecutionContext = _context(capabilities = Set.empty, principalid = "owner")
      val record = _record(SecurityAttributes.ownedBy("owner"))

      When("the overridden request is authorized")
      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("blob"),
          collectionName = Some("blob"),
          targetId = Some(_blob_id),
          accessKind = "delete"
        ),
        _ => Consequence.success(Some(record))
      )

      Then("a permission diagnostic is recorded")
      result shouldBe a[Consequence.Failure[_]]
      RuntimeDashboardMetrics.authorizationDiagnosticCounts.getOrElse("permission", 0L) should be > before
    }

    "record natural-condition denial as an ABAC diagnostic" in {
      Given("a Blob read request with a mismatched natural condition")
      val before = RuntimeDashboardMetrics.authorizationDiagnosticCounts.getOrElse("abac", 0L)
      given ExecutionContext = _context(capabilities = Set.empty, principalid = "owner")
      val record = Record.create(_record(SecurityAttributes.ownedBy("owner")).asMap.toVector :+ ("tenantId" -> "tenant-b"))

      When("the natural-condition request is authorized")
      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("blob"),
          collectionName = Some("blob"),
          targetId = Some(_blob_id),
          accessKind = "read",
          naturalConditions = EntityAbacCondition.parse("tenantId=subject.tenantId:read").toVector
        ),
        _ => Consequence.success(Some(record))
      )

      Then("an ABAC diagnostic is recorded")
      result shouldBe a[Consequence.Failure[_]]
      RuntimeDashboardMetrics.authorizationDiagnosticCounts.getOrElse("abac", 0L) should be > before
    }

    "preserve existing behavior when no resource policy is configured" in {
      Given("an unconfigured resource request")
      given ExecutionContext = _context(capabilities = Set.empty, policies = AuthorizationResourcePolicies.empty)

      When("the request is authorized")
      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("unconfigured"),
          collectionName = Some("unconfigured"),
          accessKind = "create"
        )
      )

      Then("authorization remains successful")
      result shouldBe Consequence.unit
    }
  }

  private def _record(security: SecurityAttributes): Record =
    security.toRecord

  private def _context(
    capabilities: Set[String],
    principalid: String = "subject",
    policies: AuthorizationResourcePolicies = _policies
  ): ExecutionContext = {
    val subsystem = _subsystem(policies)
    val component = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.BLOB).get
    val base = ExecutionContext.create(SecurityContext.Privilege.User)
    val security = base.security.copy(
      principal = new Principal {
        val id: PrincipalId = PrincipalId(principalid)
        val attributes: Map[String, String] = Map.empty
      },
      capabilities = capabilities.map(Capability.apply)
    )
    ExecutionContext.withSecurityContext(base, security).withScope(component.scopeContext)
  }

  private def _subsystem(
    policies: AuthorizationResourcePolicies
  ): Subsystem = {
    val subsystem = Subsystem(
      name = "blob-authz-test",
      scopeContext = Some(
        ScopeContext(
          kind = ScopeKind.Subsystem,
          name = "blob-authz-test",
          parent = None,
          observabilityContext = ExecutionContext.create().observability
        )
      ),
      configuration = org.goldenport.configuration.ResolvedConfiguration(
        org.goldenport.configuration.Configuration.empty,
        org.goldenport.configuration.ConfigurationTrace.empty
      )
    ).withDescriptor(
      GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<memory>"),
        subsystemName = "blob-authz-test",
        componentBindings = Vector(GenericSubsystemComponentBinding(BuiltinComponentIdentity.BLOB.name)),
        security = Some(GenericSubsystemSecurityBinding(
          authorization = Some(GenericSubsystemAuthorizationBinding(resources = policies))
        ))
      )
    )
    val owner = subsystem
    val component = new Component() {
      override val core: Component.Core =
        Component.Core.create(
          BuiltinComponentIdentity.BLOB.name,
          BuiltinComponentIdentity.BLOB,
          ComponentInstanceId.default(BuiltinComponentIdentity.BLOB),
          Protocol.empty
        )
      override def subsystem: Option[Subsystem] = Some(owner)
    }
    subsystem.add(Vector(component))
    subsystem
  }

  private val _policies: AuthorizationResourcePolicies =
    AuthorizationResourcePolicies(
      collections = Map(
        "blob" -> Map(
          "create" -> AuthorizationResourcePolicy(capabilities = Vector("collection:blob:create")),
          "delete" -> AuthorizationResourcePolicy(permission = Some("execute"))
        )
      ),
      associations = Map(
        "blobattachment" -> Map(
          "create" -> AuthorizationResourcePolicy(capabilities = Vector("association:blob_attachment:create")),
          "delete" -> AuthorizationResourcePolicy(capabilities = Vector("association:blob_attachment:delete"))
        )
      ),
      stores = Map(
        "blobstore" -> Map(
          "status" -> AuthorizationResourcePolicy(capabilities = Vector("store:blobstore:status"))
        )
      )
    )
}
