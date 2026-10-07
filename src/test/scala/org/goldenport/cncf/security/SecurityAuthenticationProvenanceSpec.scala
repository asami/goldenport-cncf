package org.goldenport.cncf.security

import org.goldenport.cncf.context.{Principal, PrincipalId, SecurityContext, SecurityLevel}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.prop.TableDrivenPropertyChecks

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class SecurityAuthenticationProvenanceSpec
    extends AnyWordSpec with Matchers with GivenWhenThen with TableDrivenPropertyChecks {
  "Authentication provenance" should {
    "reject distinct equal copies of admitted contexts and subjects across identities" in {
      Given("provider-admitted contexts for independent principal identities")
      forAll(Table("identity", "participant-1", "participant-2", "participant-3", "participant-4")) { identity =>
        val admitted = SecurityAuthenticationProvenance.providerAuthenticated(_context(identity))
        val subject = SecuritySubject.from(admitted)
        When("public case-class copies reconstruct the same values without admission")
        val contextcopy = admitted.copy()
        val subjectcopy = subject.copy()
        Then("value equality does not transfer the internal origin")
        contextcopy shouldBe admitted
        (contextcopy eq admitted) shouldBe false
        subjectcopy shouldBe subject
        (subjectcopy eq subject) shouldBe false
        subject.isProviderAuthenticated shouldBe true
        SecuritySubject.from(contextcopy).isProviderAuthenticated shouldBe false
        subjectcopy.isProviderAuthenticated shouldBe false
      }
    }

    "preserve provider origin only through explicit framework role derivation" in {
      Given("a provider-admitted subject and its unmarked equal copy")
      val subject = SecuritySubject.from(SecurityAuthenticationProvenance.providerAuthenticated(_context("provider")))
      val unmarked = subject.copy()
      When("the framework expands roles on each source")
      val derived = subject.withRoleDefinitions(Vector.empty)
      val untrusted = unmarked.withRoleDefinitions(Vector.empty)
      Then("only the admitted source propagates provider origin")
      derived.isProviderAuthenticated shouldBe true
      derived.isTrustedLocalAuthenticated shouldBe false
      untrusted.isProviderAuthenticated shouldBe false
    }

    "keep trusted local origin separate from provider authentication" in {
      Given("a host-admitted local security context")
      val context = SecurityAuthenticationProvenance.trustedLocalAuthenticated(_context("local"))
      When("the framework derives a subject and expands its roles")
      val subject = SecuritySubject.from(context)
      val derived = subject.withRoleDefinitions(Vector.empty)
      Then("the local origin survives authorized derivation but not ordinary copying")
      subject.isAuthenticated shouldBe true
      subject.isTrustedLocalAuthenticated shouldBe true
      subject.isProviderAuthenticated shouldBe false
      derived.isTrustedLocalAuthenticated shouldBe true
      derived.isProviderAuthenticated shouldBe false
      subject.copy().isTrustedLocalAuthenticated shouldBe false
      SecuritySubject.from(context.copy()).isTrustedLocalAuthenticated shouldBe false
    }

    "refuse public attributes as evidence of an admitted origin" in {
      Given("a public context containing authenticated and claimed provider provenance attributes")
      val context = _context("claimed-provider")
      When("a subject is derived without internal ingress admission")
      val subject = SecuritySubject.from(context)
      Then("authentication-shaped values establish neither provider nor trusted local origin")
      subject.isAuthenticated shouldBe true
      subject.isProviderAuthenticated shouldBe false
      subject.isTrustedLocalAuthenticated shouldBe false
      SecurityAuthenticationProvenance.isProviderAuthenticated(null) shouldBe false
      SecurityAuthenticationProvenance.isTrustedLocalAuthenticated(null) shouldBe false
    }
  }

  private def _context(identity: String): SecurityContext = SecurityContext(
    principal = new Principal {
      val id: PrincipalId = PrincipalId(identity)
      val attributes: Map[String, String] = Map(
        "authenticated" -> "true",
        SecuritySubject.AuthenticationProvenanceAttribute -> SecuritySubject.ProviderAuthenticationProvenance)
    },
    capabilities = Set.empty,
    level = SecurityLevel("user"))
}
