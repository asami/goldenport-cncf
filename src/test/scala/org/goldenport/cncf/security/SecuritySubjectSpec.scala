package org.goldenport.cncf.security

import org.goldenport.cncf.context.{Capability, Principal, PrincipalId, SecurityContext, SecurityLevel}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
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
  }
}
