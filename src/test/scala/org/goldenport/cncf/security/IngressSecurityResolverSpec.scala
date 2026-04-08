package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ScopeContext, ScopeKind, SecurityLevel, TraceId}
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemSecurityBinding, Subsystem}
import org.goldenport.cncf.event.EventReception
import org.goldenport.cncf.job.{ActionId, JobId, TaskId}
import org.goldenport.protocol.{Property, Protocol, Request}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Apr.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class IngressSecurityResolverSpec extends AnyWordSpec with Matchers {
  "IngressSecurityResolver" should {
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

    "deny when authentication providers do not resolve and privilege fallback is disabled by resolved wiring" in {
      val subsystem = _subsystem(fallbackEnabled = false)
      val base = subsystem.components.head.logic.executionContext()

      val result = IngressSecurityResolver.resolve(base, Map("access_token" -> "missing-token", "capability" -> "content_manager"))

      result shouldBe a[Consequence.Failure[_]]
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

  private def _subsystem(fallbackEnabled: Boolean): Subsystem = {
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
    val component = Component.create(
      "Dummy",
      ComponentId("dummy"),
      ComponentInstanceId.default(ComponentId("dummy")),
      Protocol.empty
    )
    subsystem.add(Vector(component))
    subsystem.withDescriptor(
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
                providers = Vector(
                  GenericSubsystemAuthenticationProviderBinding(
                    name = "dummy-provider",
                    component = "dummy",
                    enabled = Some(true)
                  )
                )
              )
            )
          )
        )
      )
    )
  }
}
