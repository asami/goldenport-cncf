package org.goldenport.cncf.security

import org.goldenport.cncf.context.{Capability, Principal, PrincipalId, SecurityContext, SecurityLevel, SessionContext}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  7, 2026
 * @version Apr. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class SecuritySubjectSpec
  extends AnyWordSpec
  with Matchers {

  "SecuritySubject" should {
    "recognize create grants by resource type" in {
      val subject = SecuritySubject(
        subjectId = "u1",
        authenticationState = SecuritySubject.AuthenticationState.Unspecified,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set("createuseraccount"),
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty
      )

      subject.hasCreateGrant(
        resourceType = Some("UserAccount"),
        collectionName = None
      ) shouldBe true
    }

    "recognize create grants by collection name" in {
      val subject = SecuritySubject(
        subjectId = "u1",
        authenticationState = SecuritySubject.AuthenticationState.Unspecified,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set.empty,
        privileges = Set("textususeraccountcreate"),
        capabilities = Set.empty,
        securityLevel = Set.empty
      )

      subject.hasCreateGrant(
        resourceType = None,
        collectionName = Some("textus-user-account")
      ) shouldBe true
    }

    "return false when no create grant matches" in {
      val subject = SecuritySubject(
        subjectId = "u1",
        authenticationState = SecuritySubject.AuthenticationState.Unspecified,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set("reader"),
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty
      )

      subject.hasCreateGrant(
        resourceType = Some("UserAccount"),
        collectionName = Some("textus-user-account")
      ) shouldBe false
    }

    "recognize operation invoke capability" in {
      val subject = SecuritySubject(
        subjectId = "u1",
        authenticationState = SecuritySubject.AuthenticationState.Unspecified,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set.empty,
        privileges = Set.empty,
        capabilities = Set("operationchangepasswordinvoke"),
        securityLevel = Set.empty
      )

      subject.hasUsageCapability(
        targetKind = "operation",
        targetName = "changePassword",
        action = "invoke"
      ) shouldBe true
    }

    "recognize component use capability" in {
      val subject = SecuritySubject(
        subjectId = "u1",
        authenticationState = SecuritySubject.AuthenticationState.Unspecified,
        accessTokenPresent = false,
        primaryGroup = None,
        groups = Set.empty,
        roles = Set("componentuseraccountuse"),
        privileges = Set.empty,
        capabilities = Set.empty,
        securityLevel = Set.empty
      )

      subject.hasUsageCapability(
        targetKind = "component",
        targetName = "UserAccount",
        action = "use"
      ) shouldBe true
    }

    "derive authenticated subject from access token" in {
      val principal = new Principal {
        def id: PrincipalId = PrincipalId("u1")
        def attributes: Map[String, String] = Map("access_token" -> "abc.def.ghi")
      }
      val subject = SecuritySubject.from(
        SecurityContext(
          principal = principal,
          capabilities = Set.empty,
          level = SecurityLevel("user")
        )
      )

      subject.isAuthenticated shouldBe true
      subject.accessTokenPresent shouldBe true
    }

    "derive authenticated subject from session metadata even without access token" in {
      val principal = new Principal {
        def id: PrincipalId = PrincipalId("u2")
        def attributes: Map[String, String] = Map.empty
      }
      val subject = SecuritySubject.from(
        SecurityContext(
          principal = principal,
          capabilities = Set.empty,
          level = SecurityLevel("user"),
          session = Some(SessionContext(sessionId = Some("sess-1")))
        )
      )

      subject.isAuthenticated shouldBe true
      subject.accessTokenPresent shouldBe false
    }

    "derive anonymous subject from anonymous marker" in {
      val principal = new Principal {
        def id: PrincipalId = PrincipalId("anonymous")
        def attributes: Map[String, String] = Map("anonymous" -> "true")
      }
      val subject = SecuritySubject.from(
        SecurityContext(
          principal = principal,
          capabilities = Set.empty,
          level = SecurityLevel("guest")
        )
      )

      subject.isAnonymous shouldBe true
      subject.isAuthenticated shouldBe false
    }

    "normalize groups roles privileges capabilities and security level from SecurityContext" in {
      val principal = new Principal {
        def id: PrincipalId = PrincipalId("u1")
        def attributes: Map[String, String] = Map(
          "group" -> "Sales-Ops",
          "roles" -> "Content-Manager Inventory_Reader",
          "privileges" -> "Sales-Order-Write|Inventory_Read"
        )
      }
      val subject = SecuritySubject.from(
        SecurityContext(
          principal = principal,
          capabilities = Set(Capability("service-grant:sales:inventory")),
          level = SecurityLevel("Power-User")
        )
      )

      subject.primaryGroup shouldBe Some("salesops")
      subject.hasGroup("sales_ops") shouldBe true
      subject.hasRole("content_manager") shouldBe true
      subject.hasRole("inventory-reader") shouldBe true
      subject.hasPrivilege("sales_order_write") shouldBe true
      subject.hasPrivilege("inventory-read") shouldBe true
      subject.hasCapability("service-grant:sales:inventory") shouldBe true
      subject.securityLevel should contain("poweruser")
    }

    "support normalized and multi-valued business boundary attributes" in {
      val principal = new Principal {
        def id: PrincipalId = PrincipalId("u1")
        def attributes: Map[String, String] = Map(
          "tenant_id" -> "Tenant-A|Tenant_B",
          "account_id" -> "Account-1 Account_2",
          "customer_id" -> "Customer-123,Customer_456"
        )
      }
      val subject = SecuritySubject.from(
        SecurityContext(
          principal = principal,
          capabilities = Set.empty,
          level = SecurityLevel("user")
        )
      )

      subject.attributeValues("tenantId") should contain allOf ("Tenant-A", "Tenant_B")
      subject.normalizedAttributeValues("tenantId") should contain allOf ("tenanta", "tenantb")
      subject.normalizedAttributeValues("accountId") should contain allOf ("account1", "account2")
      subject.normalizedAttributeValues("customerId") should contain allOf ("customer123", "customer456")
    }

    "support subject-prefixed business boundary attributes" in {
      val principal = new Principal {
        def id: PrincipalId = PrincipalId("u1")
        def attributes: Map[String, String] = Map(
          "subject.customer_id" -> "Customer-123",
          "principal.tenant_id" -> "Tenant-A"
        )
      }
      val subject = SecuritySubject.from(
        SecurityContext(
          principal = principal,
          capabilities = Set.empty,
          level = SecurityLevel("user")
        )
      )

      subject.attributeValues("customerId") should contain ("Customer-123")
      subject.attributeValues("tenantId") should contain ("Tenant-A")
    }
  }
}
