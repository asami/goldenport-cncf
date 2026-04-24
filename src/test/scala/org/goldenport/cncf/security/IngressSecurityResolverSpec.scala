package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, PrincipalId, ScopeContext, ScopeKind, SecurityLevel, TraceId}
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding, Subsystem}
import org.goldenport.cncf.event.EventReception
import org.goldenport.cncf.job.{ActionId, JobId, TaskId}
import org.goldenport.protocol.{Property, Protocol, Request}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Apr. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class IngressSecurityResolverSpec extends AnyWordSpec with Matchers {
  "IngressSecurityResolver" should {
    "resolve anonymous privilege when no protocol security attributes are present" in {
      val request = Request.of(component = "domain", service = "entity", operation = "loadPerson")

      val result = IngressSecurityResolver.resolve(request)

      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.principal.id.value shouldBe "anonymous"
      resolved.executionContext.security.level shouldBe SecurityLevel("anonymous")
      SecuritySubject.from(resolved.executionContext.security).isAnonymous shouldBe true
      resolved.executionContext.security.hasCapability("anonymous") shouldBe true
    }

    "resolve content-manager privilege from request properties" in {
      val request = Request
        .of(component = "domain", service = "entity", operation = "loadPerson")
        .copy(
          properties = List(
            Property("privilege", "application_content_manager", None)
          )
        )

      val result = IngressSecurityResolver.resolve(request)
      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.level shouldBe SecurityLevel("content_manager")
      resolved.executionContext.security.hasCapability("content-manager") shouldBe true
    }

    "deny when requested capability is not granted by resolved privilege" in {
      val request = Request
        .of(component = "domain", service = "entity", operation = "loadPerson")
        .copy(
          properties = List(
            Property("privilege", "user", None),
            Property("capability", "content_manager", None)
          )
        )

      val result = IngressSecurityResolver.resolve(request)
      result shouldBe a[Consequence.Failure[_]]
    }

    "restore observability and job context from standard event attributes" in {
      val traceid = TraceId("subsystem_a", "runtime")
      val correlationid = CorrelationId("subsystem_a", "runtime")
      val jobid = JobId.generate()
      val parentjobid = JobId.generate()
      val taskid = TaskId.generate()
      val actionid = ActionId.generate()
      val attrs = Map(
        EventReception.StandardAttribute.SecurityLevel -> "content_manager",
        EventReception.StandardAttribute.TraceId -> traceid.print,
        EventReception.StandardAttribute.CorrelationId -> correlationid.print,
        EventReception.StandardAttribute.JobId -> jobid.print,
        EventReception.StandardAttribute.ParentJobId -> parentjobid.print,
        EventReception.StandardAttribute.TaskId -> taskid.print,
        EventReception.StandardAttribute.ActionId -> actionid.print,
        EventReception.StandardAttribute.CausationId -> "event-12345"
      )

      val result = IngressSecurityResolver.resolve(attrs)
      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.level shouldBe SecurityLevel("content_manager")
      resolved.executionContext.observability.traceId.subsystem shouldBe traceid.subsystem
      resolved.executionContext.observability.traceId.entry shouldBe traceid.entry
      resolved.executionContext.observability.correlationId.map(_.subsystem) shouldBe Some(correlationid.subsystem)
      resolved.executionContext.observability.correlationId.map(_.boundary) shouldBe Some(correlationid.boundary)
      resolved.executionContext.jobContext.jobId.map(_.major) shouldBe Some(jobid.major)
      resolved.executionContext.jobContext.jobId.map(_.minor) shouldBe Some(jobid.minor)
      resolved.executionContext.jobContext.parentJobId.map(_.major) shouldBe Some(parentjobid.major)
      resolved.executionContext.jobContext.parentJobId.map(_.minor) shouldBe Some(parentjobid.minor)
      resolved.executionContext.jobContext.taskId.map(_.major) shouldBe Some(taskid.major)
      resolved.executionContext.jobContext.taskId.map(_.minor) shouldBe Some(taskid.minor)
      resolved.executionContext.jobContext.currentTask.map(_.major) shouldBe Some(taskid.major)
      resolved.executionContext.jobContext.currentTask.map(_.minor) shouldBe Some(taskid.minor)
      resolved.executionContext.jobContext.actionId.map(_.major) shouldBe Some(actionid.major)
      resolved.executionContext.jobContext.actionId.map(_.minor) shouldBe Some(actionid.minor)
      resolved.executionContext.jobContext.causationId shouldBe Some("event-12345")
      resolved.executionContext.jobContext.taskStack shouldBe Vector.empty
      resolved.executionContext.jobContext.traceMetadata.get("traceId").nonEmpty shouldBe true
      resolved.executionContext.jobContext.traceMetadata.get("correlationId").nonEmpty shouldBe true
    }

    "bind subject attributes and principal id into fallback security context" in {
      val result = IngressSecurityResolver.resolve(Map(
        "principal.id" -> "customer-a-user",
        "subject.customer_id" -> "customer-a"
      ))

      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.principal.id.value shouldBe "customer-a-user"
      SecuritySubject.from(resolved.executionContext.security).attributeValues("customerId") should contain("customer-a")
    }

    "deny when authentication providers do not resolve and privilege fallback is disabled by resolved wiring" in {
      val subsystem = _subsystem(fallbackEnabled = false)
      val base = subsystem.components.head.logic.executionContext()

      val result = IngressSecurityResolver.resolve(base, Map("access_token" -> "missing-token", "capability" -> "content_manager"))

      result shouldBe a[Consequence.Failure[_]]
    }

    "try the next provider when the first provider does not match" in {
      val subsystem = _subsystem(
        fallbackEnabled = true,
        providers = Vector(
          _provider("first-provider", _ => Consequence.success(None)),
          _provider(
            "second-provider",
            _ => Consequence.success(Some(AuthenticationResult(PrincipalId("user-2"), attributes = Map.empty)))
          )
        )
      )
      val base = subsystem.components.head.logic.executionContext()

      val result = IngressSecurityResolver.resolve(base, Map("access_token" -> "matched-token"))

      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.principal.id.value shouldBe "user-2"
      SecuritySubject.from(resolved.executionContext.security).isAuthenticated shouldBe true
      SecuritySubject.from(resolved.executionContext.security).isProviderAuthenticated shouldBe true
    }

    "propagate provider failure instead of falling back to privilege resolution" in {
      val subsystem = _subsystem(
        fallbackEnabled = true,
        providers = Vector(
          _provider("failing-provider", _ => Consequence.argumentInvalid("invalid credentials"))
        )
      )
      val base = subsystem.components.head.logic.executionContext()

      val result = IngressSecurityResolver.resolve(
        base,
        Map(
          "access_token" -> "bad-token",
          "privilege" -> "application_content_manager"
        )
      )

      result shouldBe a[Consequence.Failure[_]]
    }

    "allow anonymous resolution when providers are configured but no authentication material is present" in {
      val subsystem = _subsystem(fallbackEnabled = false)
      val base = subsystem.components.head.logic.executionContext()

      val result = IngressSecurityResolver.resolve(base, Map.empty[String, String])

      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.principal.id.value shouldBe "anonymous"
      resolved.executionContext.security.level shouldBe SecurityLevel("anonymous")
    }

    "allow anonymous resolution for public signup attributes when providers do not match and fallback privilege is disabled" in {
      val subsystem = _subsystem(fallbackEnabled = false)
      val base = subsystem.components.head.logic.executionContext()

      val result = IngressSecurityResolver.resolve(
        base,
        Map(
          "loginName" -> "new-user",
          "email" -> "new-user@example.com",
          "password" -> "secret123"
        )
      )

      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.principal.id.value shouldBe "anonymous"
      resolved.executionContext.security.level shouldBe SecurityLevel("anonymous")
    }

    "allow anonymous resolution when only a stale session id is present and fallback privilege is disabled" in {
      val subsystem = _subsystem(fallbackEnabled = false)
      val base = subsystem.components.head.logic.executionContext()

      val result = IngressSecurityResolver.resolve(
        base,
        Map(
          "x-textus-session" -> "missing-session",
          "loginName" -> "new-user",
          "email" -> "new-user@example.com",
          "password" -> "secret123"
        )
      )

      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.principal.id.value shouldBe "anonymous"
      resolved.executionContext.security.level shouldBe SecurityLevel("anonymous")
    }

    "allow anonymous resolution for login credentials when providers do not match and fallback privilege is disabled" in {
      val subsystem = _subsystem(fallbackEnabled = false)
      val base = subsystem.components.head.logic.executionContext()

      val result = IngressSecurityResolver.resolve(
        base,
        Map(
          "username" -> "existing-user",
          "password" -> "secret123"
        )
      )

      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.principal.id.value shouldBe "anonymous"
      resolved.executionContext.security.level shouldBe SecurityLevel("anonymous")
    }

    "preserve authenticated session metadata in execution context security" in {
      val session = org.goldenport.cncf.context.SessionContext(
        sessionId = Some("sess-1"),
        tokenId = Some("token-1")
      )
      val subsystem = _subsystem(
        fallbackEnabled = true,
        providers = Vector(
          _provider(
            "session-provider",
            _ => Consequence.success(Some(AuthenticationResult(PrincipalId("user-3"), attributes = Map.empty, session = Some(session))))
          )
        )
      )
      val base = subsystem.components.head.logic.executionContext()

      val result = IngressSecurityResolver.resolve(base, Map("access_token" -> "session-token"))

      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.session.map(_.sessionId) shouldBe Some(Some("sess-1"))
      resolved.executionContext.security.session.map(_.tokenId) shouldBe Some(Some("token-1"))
      SecuritySubject.from(resolved.executionContext.security).isAuthenticated shouldBe true
    }

    "restore authenticated security from x-textus-session header" in {
      val subsystem = _subsystem(
        fallbackEnabled = true,
        providers = Vector(
          _provider(
            "session-header-provider",
            req =>
              req.sessionId match {
                case Some("sess-header") =>
                  Consequence.success(
                    Some(
                      AuthenticationResult(
                        PrincipalId("user-header"),
                        session = Some(org.goldenport.cncf.context.SessionContext(sessionId = Some("sess-header")))
                      )
                    )
                  )
                case _ =>
                  Consequence.success(None)
              }
          )
        )
      )
      val base = subsystem.components.head.logic.executionContext()

      val result = IngressSecurityResolver.resolve(base, Map("x-textus-session" -> "sess-header"))

      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.principal.id.value shouldBe "user-header"
      resolved.executionContext.security.session.flatMap(_.sessionId) shouldBe Some("sess-header")
    }

    "prefer explicit x-textus-session over cookie session value" in {
      val subsystem = _subsystem(
        fallbackEnabled = true,
        providers = Vector(
          _provider(
            "session-precedence-provider",
            req =>
              req.sessionId match {
                case Some("sess-header") =>
                  Consequence.success(
                    Some(
                      AuthenticationResult(
                        PrincipalId("user-header"),
                        session = Some(org.goldenport.cncf.context.SessionContext(sessionId = Some("sess-header")))
                      )
                    )
                  )
                case Some("sess-cookie") =>
                  Consequence.success(
                    Some(
                      AuthenticationResult(
                        PrincipalId("user-cookie"),
                        session = Some(org.goldenport.cncf.context.SessionContext(sessionId = Some("sess-cookie")))
                      )
                    )
                  )
                case _ =>
                  Consequence.success(None)
              }
          )
        )
      )
      val base = subsystem.components.head.logic.executionContext()

      val result = IngressSecurityResolver.resolve(
        base,
        Map(
          "x-textus-session" -> "sess-header",
          "cookie" -> "textus-session-securitytest=sess-cookie"
        )
      )

      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.principal.id.value shouldBe "user-header"
      resolved.executionContext.security.session.flatMap(_.sessionId) shouldBe Some("sess-header")
    }

    "fallback to privilege resolution when providers do not resolve and fallback remains enabled" in {
      val subsystem = _subsystem(fallbackEnabled = true)
      val base = subsystem.components.head.logic.executionContext()

      val result = IngressSecurityResolver.resolve(
        base,
        Map(
          "access_token" -> "missing-token",
          "privilege" -> "application_content_manager"
        )
      )

      result shouldBe a[Consequence.Success[_]]
      val resolved = result.toOption.get
      resolved.executionContext.security.level shouldBe SecurityLevel("content_manager")
    }
  }

  private def _subsystem(
    fallbackEnabled: Boolean,
    providers: Vector[AuthenticationProvider] = Vector(_provider("dummy-provider", _ => Consequence.success(None)))
  ): Subsystem = {
    val subsystem = Subsystem(
      name = "security-test",
      scopeContext = Some(
        ScopeContext(
          kind = ScopeKind.Subsystem,
          name = "security-test",
          parent = None,
          observabilityContext = ExecutionContext.create().observability
        )
      ),
      configuration = org.goldenport.configuration.ResolvedConfiguration(
        org.goldenport.configuration.Configuration.empty,
        org.goldenport.configuration.ConfigurationTrace.empty
      )
    )
    val ownerSubsystem = subsystem
    val component = new Component() {
      override val core: Component.Core =
        Component.Core.create(
          "Dummy",
          ComponentId("dummy"),
          ComponentInstanceId.default(ComponentId("dummy")),
          Protocol.empty
        )
      override def subsystem: Option[Subsystem] = Some(ownerSubsystem)
      override def authenticationProviders: Vector[AuthenticationProvider] = providers
    }.withArtifactMetadata(
      Component.ArtifactMetadata(
        sourceType = "spec",
        name = "Dummy",
        version = "0.0.0",
        component = Some("dummy")
      )
    )
    val configured = subsystem.withDescriptor(
      GenericSubsystemDescriptor(
        path = java.nio.file.Path.of("<memory>"),
        subsystemName = "security-test",
        componentBindings = Vector(GenericSubsystemComponentBinding("dummy")),
        security = Some(
          GenericSubsystemSecurityBinding(
            authentication = Some(
              GenericSubsystemAuthenticationBinding(
                convention = Some("enabled"),
                fallbackPrivilege = Some(if (fallbackEnabled) "enabled" else "disabled"),
                providers = providers.map { provider =>
                  GenericSubsystemAuthenticationProviderBinding(
                    name = provider.name,
                    component = "dummy",
                    enabled = Some(true)
                  )
                }
              )
            )
          )
        )
      )
    )
    configured.add(Vector(component))
    require(
      configured.resolvedSecurityWiring.authentication.enabledProviders.flatMap(_.provider).map(_.name) == providers.map(_.name),
      s"resolved providers mismatch: ${configured.resolvedSecurityWiring.authentication.enabledProviders.flatMap(_.provider).map(_.name)} vs ${providers.map(_.name)}"
    )
    configured
  }

  private def _provider(
    providerName: String,
    f: AuthenticationRequest => Consequence[Option[AuthenticationResult]]
  ): AuthenticationProvider =
    new AuthenticationProvider {
      override val name: String = providerName
      def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
        f(request)
    }
}
