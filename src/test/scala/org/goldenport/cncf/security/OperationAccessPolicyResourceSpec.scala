package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{Capability, ExecutionContext, Principal, PrincipalId, ScopeContext, ScopeKind, SecurityContext}
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthorizationBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding, Subsystem}
import org.goldenport.cncf.unitofwork.UnitOfWorkAuthorization
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.value.SecurityAttributes
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 28, 2026
 * @version Apr. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationAccessPolicyResourceSpec extends AnyWordSpec with Matchers {
  private val _blobCollection = EntityCollectionId("cncf", "builtin", "blob")
  private val _blobId = EntityId("cncf", "blob_1", _blobCollection)

  "OperationAccessPolicy resource policies" should {
    "deny Blob collection create without the configured collection capability" in {
      given ExecutionContext = _context(capabilities = Set.empty)

      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("blob"),
          collectionName = Some("blob"),
          accessKind = "create"
        )
      )

      result shouldBe a[Consequence.Failure[_]]
    }

    "allow Blob collection create with the configured collection capability" in {
      given ExecutionContext = _context(capabilities = Set("collection:blob:create"))

      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("blob"),
          collectionName = Some("blob"),
          accessKind = "create"
        )
      )

      result shouldBe Consequence.unit
    }

    "require execute permission for Blob delete when collection policy overrides delete" in {
      given ExecutionContext = _context(capabilities = Set.empty, principalId = "owner")
      val record = _record(SecurityAttributes.ownedBy("owner"))

      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("blob"),
          collectionName = Some("blob"),
          targetId = Some(_blobId),
          accessKind = "delete"
        ),
        _ => Consequence.success(Some(record))
      )

      result shouldBe a[Consequence.Failure[_]]
    }

    "allow Blob delete when owner has execute permission for override policy" in {
      given ExecutionContext = _context(capabilities = Set.empty, principalId = "owner")
      val security = SecurityAttributes.ownedBy(
        "owner",
        owner = SecurityAttributes.Rights.Permissions.full
      )
      val record = _record(security)

      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("blob"),
          collectionName = Some("blob"),
          targetId = Some(_blobId),
          accessKind = "delete"
        ),
        _ => Consequence.success(Some(record))
      )

      result shouldBe Consequence.unit
    }

    "require association-domain capability for Blob attachment create and delete" in {
      {
        given ExecutionContext = _context(capabilities = Set.empty)
        OperationAccessPolicy.authorizeUnitOfWorkDefault(
          UnitOfWorkAuthorization(
            resourceFamily = "association",
            resourceType = Some("blob_attachment"),
            collectionName = Some("cncf-blob-association"),
            accessKind = "create"
          )
        ) shouldBe a[Consequence.Failure[_]]
      }
      {
        given ExecutionContext = _context(capabilities = Set("association:blob_attachment:create"))
        OperationAccessPolicy.authorizeUnitOfWorkDefault(
          UnitOfWorkAuthorization(
            resourceFamily = "association",
            resourceType = Some("blob_attachment"),
            collectionName = Some("cncf-blob-association"),
            accessKind = "create"
          )
        ) shouldBe Consequence.unit
      }
    }

    "require store capability for BlobStore status" in {
      {
        given ExecutionContext = _context(capabilities = Set.empty)
        OperationAccessPolicy.authorizeUnitOfWorkDefault(
          UnitOfWorkAuthorization(
            resourceFamily = "store",
            resourceType = Some("blobstore"),
            collectionName = Some("blob-payload-store"),
            accessKind = "status"
          )
        ) shouldBe a[Consequence.Failure[_]]
      }
      {
        given ExecutionContext = _context(capabilities = Set("store:blobstore:status"))
        OperationAccessPolicy.authorizeUnitOfWorkDefault(
          UnitOfWorkAuthorization(
            resourceFamily = "store",
            resourceType = Some("blobstore"),
            collectionName = Some("blob-payload-store"),
            accessKind = "status"
          )
        ) shouldBe Consequence.unit
      }
    }

    "preserve existing behavior when no resource policy is configured" in {
      given ExecutionContext = _context(capabilities = Set.empty, policies = AuthorizationResourcePolicies.empty)

      val result = OperationAccessPolicy.authorizeUnitOfWorkDefault(
        UnitOfWorkAuthorization(
          resourceFamily = "domain",
          resourceType = Some("unconfigured"),
          collectionName = Some("unconfigured"),
          accessKind = "create"
        )
      )

      result shouldBe Consequence.unit
    }
  }

  private def _record(security: SecurityAttributes): Record =
    security.toRecord

  private def _context(
    capabilities: Set[String],
    principalId: String = "subject",
    policies: AuthorizationResourcePolicies = _policies
  ): ExecutionContext = {
    val subsystem = _subsystem(policies)
    val component = subsystem.findComponent("blob").get
    val base = ExecutionContext.create(SecurityContext.Privilege.User)
    val security = base.security.copy(
      principal = new Principal {
        val id: PrincipalId = PrincipalId(principalId)
        val attributes: Map[String, String] = Map.empty
      },
      capabilities = capabilities.map(Capability.apply)
    )
    ExecutionContext.withSecurityContext(base.withScope(component.scopeContext), security)
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
        componentBindings = Vector(GenericSubsystemComponentBinding("blob")),
        security = Some(GenericSubsystemSecurityBinding(
          authorization = Some(GenericSubsystemAuthorizationBinding(resources = policies))
        ))
      )
    )
    val owner = subsystem
    val component = new Component() {
      override val core: Component.Core =
        Component.Core.create(
          "Blob",
          ComponentId("blob"),
          ComponentInstanceId.default(ComponentId("blob")),
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
