package org.goldenport.cncf.security

import org.goldenport.cncf.context.{Principal, PrincipalId, SecurityContext, SecurityLevel}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.prop.TableDrivenPropertyChecks

/**
 * @since   Oct.  1, 2026
 *  version Oct.  8, 2026
 * @version Nov.  6, 2026
 */
final class SecurityAuthenticationProvenanceSpec
    extends AnyWordSpec with Matchers with GivenWhenThen with TableDrivenPropertyChecks {
  "Authentication provenance" should {
    "expose selected names only with admitted provider origin and preserve them through explicit framework inheritance" in {
      Given("a fixture-native selected binding, transport facts and a result without authentication admission")
      val identity = AuthenticationProviderIdentity("binding", "provider", "component")
      val result = AuthenticationResult(PrincipalId("selected"))
      SecurityAuthenticationProvenance.withProviderIdentity(result, identity)
      val context = result.toSecurityContext
      val facts = SecurityContext.CredentialTransports(bearerHeader = true, sessionCookie = true)
      SecurityAuthenticationProvenance.withCredentialTransports(context, facts)
      context.authenticatedProvider shouldBe None
      SecuritySubject.from(context).isProviderAuthenticated shouldBe false

      When("the framework admits the provider and derives a new context")
      SecurityAuthenticationProvenance.providerAuthenticated(context)
      val inherited = context.copy()
      SecurityAuthenticationProvenance.inherit(context, inherited)
      Then("selected names, origin and transports survive together without transferring through a public copy")
      inherited.authenticatedProvider shouldBe Some(identity)
      inherited.credentialTransports shouldBe Some(facts)
      SecuritySubject.from(inherited).isProviderAuthenticated shouldBe true
      context.copy().authenticatedProvider shouldBe None
      result.copy().toSecurityContext.authenticatedProvider shouldBe None
      SecurityAuthenticationProvenance.providerAuthenticated(context.copy()).authenticatedProvider shouldBe None

      When("only transport facts are copied to another provider-marked context, or origin is trusted-local")
      val transportonly = SecurityAuthenticationProvenance.providerAuthenticated(_context("other"))
      SecurityAuthenticationProvenance.inheritCredentialTransports(context, transportonly)
      Then("transport propagation and trusted-local admission cannot supply selected-provider proof")
      transportonly.credentialTransports shouldBe Some(facts)
      transportonly.authenticatedProvider shouldBe None
      SecurityAuthenticationProvenance.trustedLocalAuthenticated(inherited).authenticatedProvider shouldBe None
      val legacy = SecurityAuthenticationProvenance.providerAuthenticated(_context("legacy"))
      SecurityAuthenticationProvenance.inherit(legacy, context)
      context.authenticatedProvider shouldBe None
      SecuritySubject.from(context).isProviderAuthenticated shouldBe true
    }

    "refuse constructed or copied public identity claims for arbitrary shrinking names" in {
      Given("clients able to construct metadata and authenticated-looking attributes")
      val names = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.take(40).mkString)
      val property = Prop.forAll(names) { name =>
        val identity = AuthenticationProviderIdentity(name, name + "-provider", name + "-component")
        val raw = _context(name)
        val publiccopy = identity.copy()
        val selected = raw.copy()
        SecurityAuthenticationProvenance.withProviderIdentity(selected, identity)
        val copied = selected.copy()
        val local = SecurityAuthenticationProvenance.trustedLocalAuthenticated(selected)
        val legacy = SecurityAuthenticationProvenance.providerAuthenticated(raw)
        publiccopy == identity && raw.authenticatedProvider.isEmpty && copied.authenticatedProvider.isEmpty &&
          local.authenticatedProvider.isEmpty && legacy.authenticatedProvider.isEmpty &&
          !SecuritySubject.from(copied).isProviderAuthenticated
      }
      When("the public claims are inspected without actual provider selection and ingress admission")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("metadata construction and value equality grant no attribution")
      checked.passed shouldBe true
    }

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
