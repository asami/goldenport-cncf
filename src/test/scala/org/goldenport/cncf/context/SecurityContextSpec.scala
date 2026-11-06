package org.goldenport.cncf.context

import org.goldenport.cncf.security.{SecurityAuthenticationProvenance, SecuritySubject}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version Nov.  6, 2026
 * @author  ASAMI, Tomoharu
 */
class SecurityContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {

  "SecurityContext" should {

    "keep constructor and product contracts while adding an optional provenance reader" in {
      Given("the existing five-argument context and nine-argument session products")
      val principal = new Principal {
        val id: PrincipalId = PrincipalId("account-owner")
        val attributes: Map[String, String] = Map("role" -> "user")
      }
      val session = SessionContext(Some("session"), None, Some("access"), None, None, None, None, None, Map.empty)
      val context = SecurityContext(principal, Set(Capability("read")), SecurityLevel("user"), SubjectKind.User, Some(session))

      When("existing construction, copying and extraction use their original shapes")
      val SecurityContext(actor, capabilities, level, kind, selectedsession) = context.copy()

      Then("the old product contract remains and public attributes do not create physical observations")
      context.productArity shouldBe 5
      session.productArity shouldBe 9
      actor shouldBe principal
      capabilities shouldBe Set(Capability("read"))
      level shouldBe SecurityLevel("user")
      kind shouldBe SubjectKind.User
      selectedsession shouldBe Some(session)
      context.credentialTransports shouldBe None
      context.authenticatedProvider shouldBe None
      context.productElementNames.toVector shouldBe Vector("principal", "capabilities", "level", "subjectKind", "session")
      org.goldenport.cncf.security.AuthenticationProviderIdentity("binding", "provider", "component").copy() match {
        case org.goldenport.cncf.security.AuthenticationProviderIdentity(binding, provider, component) =>
          Vector(binding, provider, component) shouldBe Vector("binding", "provider", "component")
      }
      context.copy().authenticatedProvider shouldBe None
    }

    "keep transport inheritance separate from authentication authority" in {
      Given("an observed physical request with secret-free transport facts and an anonymous security context")
      val physical = new Object
      val facts = SecurityContext.CredentialTransports(authorizationHeader = true, bearerHeader = true, sessionCookie = true)
      SecurityAuthenticationProvenance.withCredentialTransports(physical, facts)
      val context = ExecutionContext.create().security

      When("the native boundary copies only physical transport facts")
      SecurityAuthenticationProvenance.inheritCredentialTransports(physical, context)

      Then("typed facts are available without authenticating the subject or adding public attributes")
      context.credentialTransports shouldBe Some(facts)
      SecuritySubject.from(context).isProviderAuthenticated shouldBe false
      SecuritySubject.from(context).isTrustedLocalAuthenticated shouldBe false
      context.principal.attributes.keySet.exists(_.toLowerCase.contains("transport")) shouldBe false

      When("the native framework independently admits provider provenance and another context inherits it")
      SecurityAuthenticationProvenance.providerAuthenticated(context)
      val copied = context.copy()
      SecurityAuthenticationProvenance.inherit(context, copied)

      Then("ordinary provenance inheritance retains both facts and the independently established origin")
      copied.credentialTransports shouldBe Some(facts)
      SecuritySubject.from(copied).isProviderAuthenticated shouldBe true
      SecurityAuthenticationProvenance.inheritCredentialTransports(new Object, copied)
      copied.credentialTransports shouldBe None
      SecuritySubject.from(copied).isProviderAuthenticated shouldBe true
    }

    "preserve every boolean transport combination without trusting a public product or attribute claim" in {
      Given("all transport combinations and clients that can construct data and public attributes")
      val combinations = Gen.choose(0, 15)
      val law = Prop.forAll(combinations) { bits =>
        val facts = SecurityContext.CredentialTransports(
          authorizationHeader = (bits & 1) != 0, bearerHeader = (bits & 2) != 0,
          sessionHeader = (bits & 4) != 0, sessionCookie = (bits & 8) != 0)
        val principal = new Principal {
          val id: PrincipalId = PrincipalId("untrusted")
          val attributes: Map[String, String] = Map("credentialTransports" -> facts.toString,
            "credentialTransports.sessionCookie" -> "true", "authenticated" -> "true")
        }
        val source = SecurityContext(principal, Set.empty, SecurityLevel("anonymous"))
        val unmarked = source.copy()
        SecurityAuthenticationProvenance.withCredentialTransports(source, facts)
        val inherited = source.copy()
        SecurityAuthenticationProvenance.inherit(source, inherited)
        inherited.credentialTransports.contains(facts) && unmarked.credentialTransports.isEmpty &&
          !SecuritySubject.from(inherited).isProviderAuthenticated &&
          inherited == source && inherited.productIterator.toVector == source.productIterator.toVector
      }

      When("the inheritance and non-forgery law is checked with at least one hundred successful examples")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), law)

      Then("facts retain their exact typed value independently from public products and authentication")
      checked.passed shouldBe true
    }

  }
}
