package org.goldenport.cncf.admission

import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for runtime-owned scoped concurrency admission.
 *
 * @since   Jul. 18, 2026
 * @version Jul. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class ScopedConcurrencyAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Scoped concurrency admission" should {
    "reject a saturated scope and admit work again after its permit is released" in {
      Given("a runtime admission with one permit for a provider scope")
      val key = _key("textus-ai-web-research")
      val admission = _admission(key, 1)

      When("the first caller holds the only permit")
      val first = admission.acquireC(key).toOption.get
      val rejected = admission.acquireC(key)
      first.release()
      val second = admission.acquireC(key)

      Then("saturation is explicit and release restores the configured capacity")
      rejected.isFaillure shouldBe true
      second.isSuccess shouldBe true
      second.toOption.get.release()
    }

    "release a permit after a failed synchronous operation" in {
      Given("a single-permit runtime admission")
      val key = _key("textus-ai-source-extraction")
      val admission = _admission(key, 1)

      When("admitted work returns a structured failure")
      val failed = admission.withPermitC(key)(Consequence.serviceUnavailable("provider unavailable"))
      val next = admission.acquireC(key)

      Then("the failed operation does not leak the permit")
      failed.isFaillure shouldBe true
      next.isSuccess shouldBe true
      next.toOption.get.release()
    }

    "inherit runtime-installed admission through a component scope" in {
      Given("a runtime scope with one configured managed-research permit")
      val key = _key("textus-ai-managed-research")
      val admission = _admission(key, 1)
      val runtime = ScopeContext(
        kind = ScopeKind.Runtime,
        name = "runtime",
        parent = None,
        observabilitycontext = ExecutionContext.create().observability,
        scopedconcurrencyadmissionoption = Some(admission)
      )
      val component = runtime.createChildScope(ScopeKind.Component, "textus-ai")

      When("a component resolves and uses the inherited gate")
      val result = ScopedConcurrencyAdmission.withPermitC(component, key)(Consequence.success("admitted"))

      Then("the component receives the runtime-owned concurrency boundary")
      result.toOption shouldBe Some("admitted")
    }

    "reject invalid grant configuration before it is installed" in {
      Given("a valid concurrency scope")
      val key = _key("textus-ai-invalid")

      When("configuration repeats the scope or supplies a nonpositive limit")
      val duplicate = ScopedConcurrencyAdmission.createC(Vector(ConcurrencyGrant(key, 1), ConcurrencyGrant(key, 2)))
      val invalid = ScopedConcurrencyAdmission.createC(Vector(ConcurrencyGrant(key, 0)))

      Then("neither malformed configuration can create an admission service")
      duplicate.isFaillure shouldBe true
      invalid.isFaillure shouldBe true
    }

    "return a structured failure when a scope has no runtime admission" in {
      Given("a component scope without a configured concurrency service")
      val key = _key("textus-ai-unconfigured")
      val scope = ScopeContext(
        kind = ScopeKind.Component,
        name = "component",
        parent = None,
        observabilitycontext = ExecutionContext.create().observability
      )

      When("the component tries to use a scoped permit")
      val result = ScopedConcurrencyAdmission.withPermitC(scope, key)(Consequence.success("unreachable"))

      Then("the absence is explicit instead of falling back to an ambient limiter")
      result.isFaillure shouldBe true
    }
  }

  private def _key(value: String): ConcurrencyScopeId =
    ConcurrencyScopeId.parseC(value).toOption.get

  private def _admission(key: ConcurrencyScopeId, maxconcurrent: Int): ScopedConcurrencyAdmission =
    ScopedConcurrencyAdmission.createC(Vector(ConcurrencyGrant(key, maxconcurrent))).toOption.get
}
