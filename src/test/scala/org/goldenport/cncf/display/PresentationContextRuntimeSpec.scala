package org.goldenport.cncf.display

import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.context.{ExecutionContext, PrincipalId, ScopeContext, SubjectKind}
import org.goldenport.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class PresentationContextRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import PresentationContextRuntimeFixture.*
  import PresentationContextRuntime.*
  import PresentationContextResolver.Result

  "Presentation restoration" should afterWord("in spec:display-presentation-context, example:B1..B9, rules:R5,R6, phase:96") {
    "explicit optional support" which {
      "B1 clear inherited state on absence without a provider lookup" in {
        Given("R5: an accepted server flow inherited by a new invocation")
        val provider = new Provider
        val active = success(restoreC(instanceId, Some(emptyWire), context(provider)))
        provider.requests.clear()
        When("the new invocation has no explicit candidate")
        val cleared = success(restoreC(instanceId, None, active))
        Then("presentation authority is absent and the provider was not called")
        cleared.presentationContext shouldBe None
        project(cleared) shouldBe None
        provider.requests shouldBe empty
        active.presentationContext should not be empty
      }
      "B2 distinguish unavailable support from supported locale-only projection" in {
        Given("R5,R6: explicit empty context with missing disabled and support-only capabilities")
        val provider = new Provider
        provider.answer = (request, _) => Consequence.success(Result.Supported(success(Resolution.createC(request.binding))))
        When("each invocation asks for support without allocating any kernel flow")
        val missing = success(restoreC(instanceId, Some(emptyWire), context()))
        val supported = success(restoreC(instanceId, Some(emptyWire), context(provider)))
        Then("unavailable clears while support-only emits the trusted core locale and no variant or flow")
        missing.presentationContext shouldBe None
        project(supported).get.locale shouldBe Some("en-US")
        project(supported).get.variant shouldBe None
        project(supported).get.flow shouldBe None
        provider.requests.head.continuation shouldBe None
        val und = ExecutionContext.withLocale(supported, Locale.ROOT)
        project(und).get.locale shouldBe Some("und")
      }
      "B3 replace every client claim with server resolution and expiry" in {
        Given("R5: a past incoming expiry with valid opaque references and conflicting client locale and variant")
        val provider = new Provider
        val candidate = wire(flow(expiresAt = instant.minusSeconds(1)))
        When("the server restores the exact requested references")
        val active = success(restoreC(instanceId, Some(candidate), context(provider)))
        val projected = project(active).get
        Then("only core locale and server variant expiry and exact opaque references are projected")
        projected.locale shouldBe Some("en-US")
        projected.variant shouldBe Some("server-variant")
        projected.flow.get.expiresAt shouldBe instant.plusSeconds(600)
        projected.flow.get.displayInstance shouldBe "server-display"
        projected.flow.get.correlation shouldBe "server-correlation"
        provider.requests.head.continuation.get.displayInstance shouldBe "server-display"
        provider.contexts.head.presentationContext shouldBe None
      }
      "B4 clear unknown expired unsupported and restart state without changing ordinary optional semantics" in {
        Given("R5: unknown server state server expiry and unsupported flow envelopes")
        val unsupported = "{\"version\":1,\"families\":[{\"name\":\"cncf.display-flow\",\"version\":2,\"data\":{}}]}"
        val provider = new Provider
        provider.answer = (_, _) => Consequence.success(Result.Unavailable)
        When("unknown and restart references meet unavailable server state and unsupported flow meets an installed provider")
        val unknown = success(restoreC(instanceId, Some(wire()), context(provider)))
        val restarted = success(restoreC(instanceId, Some(wire()), context()))
        val calls = provider.requests.size
        val ignored = success(restoreC(instanceId, Some(unsupported), context(provider)))
        provider.answer = (request, _) => Consequence.success(Result.Supported(success(Resolution.createC(request.binding,
          Some("server"), Some(flow(expiresAt = request.now))))))
        val expired = success(restoreC(instanceId, Some(wire(flow(expiresAt = instant.plusSeconds(9999)))), context(provider)))
        Then("all four results succeed with absent presentation and unsupported flow never negotiates support")
        Vector(unknown, restarted, ignored, expired).foreach(_.presentationContext shouldBe None)
        provider.requests.size shouldBe calls + 1
      }
    }
    "trusted identity and provider failure boundaries" which {
      "B5 isolate component principal session and current session expiry before lookup or projection" in {
        Given("R5,R6: one accepted flow and independently rebound identities")
        val provider = new Provider
        val original = context(provider)
        val active = success(restoreC(instanceId, Some(emptyWire), original))
        val other = ComponentInstanceId(componentId, "other")
        val rebinding = Vector(authenticated(active, principalId = "other"), authenticated(active, sessionId = "other"),
          authenticated(active, expiresAt = Some(instant)), active.withScope(context(identity = other).scope))
        val ineligible = Vector(authenticated(original, subjectKind = SubjectKind.Anonymous),
          authenticated(original, subjectKind = SubjectKind.Unspecified), authenticated(original, sessionId = ""),
          authenticated(original, expiresAt = Some(instant.minusSeconds(1))))
        provider.requests.clear()
        When("projection observes rebinding and new explicit calls observe ineligible trusted sessions")
        val projections = rebinding.map(project)
        val cleared = ineligible.map(value => success(restoreC(instanceId, Some(wire()), value)))
        Then("no identity reuses the old authority and ineligible calls never invoke the provider")
        projections.foreach(_ shouldBe None)
        cleared.foreach(_.presentationContext shouldBe None)
        provider.requests shouldBe empty
      }
      "B6 reject provider failures exceptions nulls and malformed values with fixed redacted structure" in {
        Given("R6: provider outcomes containing private exception or failure text and null protocol containers")
        val provider = new Provider
        val failures: Vector[(PresentationContextResolver.Request, ExecutionContext) => Consequence[Result]] = Vector(
          (_, _) => Consequence.argumentInvalid("SECRET provider"),
          (_, _) => throw new IllegalStateException("SECRET provider"),
          (_, _) => null,
          (_, _) => Consequence.success(null),
          (_, _) => Consequence.success(Result.Supported(null)),
          (request, _) => Consequence.success(Result.Supported(malformedResolution(request.binding, variant = null))),
          (request, _) => Consequence.success(Result.Supported(malformedResolution(request.binding, variant = Some(null)))),
          (request, _) => Consequence.success(Result.Supported(malformedResolution(request.binding, flow = null))),
          (request, _) => Consequence.success(Result.Supported(malformedResolution(request.binding, flow = Some(null)))),
          (_, _) => Consequence.success(Result.Supported(malformedResolution(null))))
        When("each provider outcome crosses the restoration boundary")
        val results = failures.map { answer =>
          provider.answer = answer
          restoreC(instanceId, Some(emptyWire), context(provider))
        }
        Then("failures are typed unavailable or invalid state and retain no private payload or Throwable")
        results.zipWithIndex.foreach { case (result, index) =>
          val failure = result.asInstanceOf[Consequence.Failure[?]]
          failure.conclusion.observation.taxonomy shouldBe (if (index < 2) Taxonomy.serviceUnavailable else Taxonomy.stateInvalid)
          failure.conclusion.display should not include "SECRET"
          failure.conclusion.observation.cause.getException shouldBe None
        }
      }
      "B7 reject mismatched trusted bindings references and missing continuation flow" in {
        Given("R6: a valid continuation and one-dimensional incompatible provider replies")
        val provider = new Provider
        val bindings = Vector(success(Binding.createC(ComponentInstanceId(componentId, "other"), PrincipalId("owner"), "session-one")),
          success(Binding.createC(instanceId, PrincipalId("other"), "session-one")),
          success(Binding.createC(instanceId, PrincipalId("owner"), "other")))
        val answers: Vector[PresentationContextResolver.Request => Resolution] = bindings.map(binding =>
          (_: PresentationContextResolver.Request) => success(Resolution.createC(binding, flow = Some(flow())))) ++ Vector(
          request => success(Resolution.createC(request.binding, flow = Some(flow(displayInstance = "other")))),
          request => success(Resolution.createC(request.binding, flow = Some(flow(correlation = "other")))),
          request => success(Resolution.createC(request.binding)))
        When("each reply attempts to restore the continuation")
        val results = answers.map { answer =>
          provider.answer = (request, _) => Consequence.success(Result.Supported(answer(request)))
          restoreC(instanceId, Some(wire()), context(provider))
        }
        Then("every mismatch is an invalid-state failure and never optional downgrade")
        results.foreach(_.asInstanceOf[Consequence.Failure[?]].conclusion.observation.taxonomy shouldBe Taxonomy.stateInvalid)
      }
      "B8 retain inherited provider across runtime rebinding while new calls require explicit continuation" in {
        Given("R5,R6: a provider inherited through typed component scope and an accepted flow")
        val provider = new Provider
        val active = success(restoreC(instanceId, Some(emptyWire), context(provider)))
        val rebound = ExecutionContext.withRuntimeContext(active, active.runtime.withContext(active.runtime.context))
        When("ordinary runtime and security rebinding are followed by a new absent Operation")
        val securityrebound = ExecutionContext.withSecurityContext(rebound, rebound.security)
        val cleared = success(restoreC(instanceId, None, securityrebound))
        Then("both rebound variants retain the provider capability and immutable data but absence clears only presentation")
        (rebound.scope.presentationContextResolver eq provider) shouldBe true
        (securityrebound.scope.presentationContextResolver eq provider) shouldBe true
        (securityrebound.presentationContext.get eq active.presentationContext.get) shouldBe true
        cleared.presentationContext shouldBe None
        provider.requests should have size 1
      }
      "B9 enforce immutable runtime factory invariants at nullable interop boundaries" in {
        Given("R6: null typed identities strings Option containers and flowless variants")
        val binding = success(Binding.createC(instanceId, PrincipalId("owner"), "session-one"))
        When("the public runtime factories interpret invalid data")
        val results = Vector(Binding.createC(null, PrincipalId("owner"), "session-one"),
          Binding.createC(instanceId, null, "session-one"), Binding.createC(instanceId, PrincipalId(" owner"), "session-one"),
          Binding.createC(instanceId, PrincipalId("owner"), "session\n"), References.createC(null, "c"),
          Resolution.createC(null), Resolution.createC(binding, variant = null),
          Resolution.createC(binding, flow = null), Resolution.createC(binding, flow = Some(null)),
          Resolution.createC(binding, Some("server")))
        Then("all invalid values remain structured argument failures with no constructor or copy bypass")
        results.foreach(_.isSuccess shouldBe false)
      }
    }
  }
}
