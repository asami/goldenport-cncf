package org.goldenport.cncf.servicecontainer

import java.time.{Clock, Instant, ZoneOffset}

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.goldenport.cncf.observability.{CallTreeContext, ServiceContainerRuntimeObservation}
import org.goldenport.cncf.testutil.SubsystemTestFixture
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for payload-safe managed service lifecycle
 * CallTree/metrics and subsystem-owned shutdown integration.
 *
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class ServiceContainerObservabilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Managed service-container observability" should {
    "record only safe logical metadata and endpoint authority in CallTree" in {
      Given("an observed runtime, one managed definition, and a path-bearing readiness endpoint")
      given context: ExecutionContext = _execution_context()
      val definition = _definition("textus-ai", "ollama", ServiceContainerCleanupPolicy.Stop)
      val endpoint = _endpoint("http://127.0.0.1:11434/private/readiness")
      val runtime = ServiceContainerRuntime.create(
        ServiceContainerRegistry.inMemory(),
        FakeServiceContainerGateway.create(Map(definition.registryKey -> endpoint))
      )
      val observed = ServiceContainerRuntimeObservation.observed(runtime)

      When("the component-facing runtime resolves the managed service")
      val result = observed.resolveC(definition)
      val rendered = context.observability.callTreeContext.build().map(_.toRecord.toString).getOrElse("")

      Then("CallTree contains safe lifecycle identity but not the endpoint path or provider handles")
      result.isSuccess shouldBe true
      rendered should include("service-container:resolve")
      rendered should include("service-container")
      rendered should include("textus-ai")
      rendered should include("ollama")
      rendered should include("example/ollama:1")
      rendered should include("http://127.0.0.1:11434")
      rendered should not include "/private/readiness"
      rendered should not include "instanceId"
      rendered should not include "contract-digest"
    }

    "exclude provider failure text from CallTree while retaining structured metrics" in {
      Given("an observed runtime whose provider boundary returns sensitive failure text")
      given context: ExecutionContext = _execution_context()
      val before = RuntimeDashboardMetrics.serviceContainerLifecycleSnapshot.summary.cumulative
      val runtime = ServiceContainerRuntimeObservation.observed(
        new _FailingRuntime("authorization=Bearer provider-secret")
      )
      val definition = ServiceContainerDefinition.external(
        _service_id("external-ai"),
        _endpoint("https://service.example/api")
      )

      When("resolution fails")
      val result = runtime.resolveC(definition)
      val rendered = context.observability.callTreeContext.build().map(_.toRecord.toString).getOrElse("")
      val after = RuntimeDashboardMetrics.serviceContainerLifecycleSnapshot.summary.cumulative
      val diagnosticrecords = RuntimeDashboardMetrics.serviceContainerDiagnosticRecords.values.mkString(" ")

      Then("CallTree and metric labels omit provider text while the failure remains counted")
      result.isFaillure shouldBe true
      rendered should include("outcome")
      rendered should include("failure")
      rendered should not include "provider-secret"
      rendered should not include "Bearer"
      diagnosticrecords should not include "provider-secret"
      diagnosticrecords should not include "Bearer"
      after.total shouldBe before.total + 1L
      after.errors shouldBe before.errors + 1L
    }

    "project a bounded service-container runtime metric label set" in {
      Given("one observed external lifecycle resolution")
      given context: ExecutionContext = _execution_context()
      val runtime = ServiceContainerRuntimeObservation.observed(
        ServiceContainerRuntime.create(ServiceContainerRegistry.inMemory(), FakeServiceContainerGateway.create())
      )
      val definition = ServiceContainerDefinition.external(
        _service_id("catalog"),
        _endpoint("https://catalog.example/private/path")
      )

      When("the runtime metric snapshot is projected")
      runtime.resolveC(definition).isSuccess shouldBe true
      val points = RuntimeDashboardMetrics
        .runtimeMetricsSnapshot(EntityAccessMetricsRegistry.shared)
        .points
        .filter(_.scope == "service-container.lifecycle")

      Then("metrics use only the declared safe low-cardinality lifecycle labels")
      points should not be empty
      points.flatMap(_.labels.keySet).toSet.subsetOf(Set(
        "outcome",
        "operation",
        "ownership_mode",
        "owner_kind",
        "owner_id",
        "service_id",
        "cleanup_policy",
        "status",
        "diagnostic_key"
      )) shouldBe true
      points.flatMap(_.labels.values).mkString(" ") should not include "/private/path"
      points.flatMap(_.labels.keySet) should not contain "image"
      points.flatMap(_.labels.keySet) should not contain "endpoint"
    }
  }

  "Subsystem managed service lifecycle" should {
    "own idempotent service cleanup outside UnitOfWork termination" in {
      Given("a subsystem with one installed stop-policy managed service runtime")
      val subsystem = SubsystemTestFixture.Startup.Empty.create(SubsystemTestFixture.Params())
      val definition = _definition("runtime", "database", ServiceContainerCleanupPolicy.Stop)
      val endpoint = _endpoint("http://127.0.0.1:8080")
      val registry = ServiceContainerRegistry.inMemory()
      val gateway = FakeServiceContainerGateway.create(Map(definition.registryKey -> endpoint))
      val runtime = ServiceContainerRuntime.create(registry, gateway)
      runtime.resolveC(definition).isSuccess shouldBe true
      subsystem.installServiceContainerRuntimeC(runtime).isSuccess shouldBe true

      When("subsystem shutdown is requested twice")
      val first = subsystem.shutdownC()
      val second = subsystem.shutdownC()

      Then("the service is stopped only once and retained as stopped runtime state")
      first.toOption.flatMap(_.headOption).map(_.changed) shouldBe Some(true)
      second.toOption.flatMap(_.headOption).map(_.changed) shouldBe Some(false)
      gateway.transitions.count(_._1 == ServiceContainerTransition.Stop) shouldBe 1
      registry.get(definition.registryKey).map(_.status) shouldBe Some(ServiceContainerStatus.Stopped)
    }

    "reject replacement of an installed runtime that could orphan owned services" in {
      Given("a subsystem with one installed service-container runtime")
      val subsystem = SubsystemTestFixture.Startup.Empty.create(SubsystemTestFixture.Params())
      val first = ServiceContainerRuntime.create(ServiceContainerRegistry.inMemory(), FakeServiceContainerGateway.create())
      val second = ServiceContainerRuntime.create(ServiceContainerRegistry.inMemory(), FakeServiceContainerGateway.create())

      When("the same runtime and then a different runtime are installed")
      val original = subsystem.installServiceContainerRuntimeC(first)
      val repeated = subsystem.installServiceContainerRuntimeC(first)
      val replacement = subsystem.installServiceContainerRuntimeC(second)

      Then("installation is idempotent but replacement is a structured conflict")
      original.isSuccess shouldBe true
      repeated.isSuccess shouldBe true
      replacement.isFaillure shouldBe true
      subsystem.shutdown()
    }
  }

  private def _execution_context(): ExecutionContext = {
    val clock = Clock.fixed(Instant.parse("2026-07-20T12:00:00Z"), ZoneOffset.UTC)
    val base = ExecutionContext.create(clock).asInstanceOf[ExecutionContext.Instance]
    base.copy(
      cncfCore = base.cncfCore.copy(
        observability = base.observability.copy(callTreeContext = CallTreeContext.enabled)
      )
    )
  }

  private def _definition(
    owner: String,
    service: String,
    cleanup: ServiceContainerCleanupPolicy
  ): ServiceContainerDefinition.RuntimeOwned = {
    val serviceid = _service_id(service)
    val ownerid = ServiceContainerOwnerId.parseC(owner).toOption.get
    val image = ServiceContainerImage.parseC(s"example/${service}:1").toOption.get
    val portname = ServiceContainerPortName.parseC("api").toOption.get
    val port = ServiceContainerPort.createC(portname, 8080).toOption.get
    val readiness = ServiceContainerReadinessPolicy.createC(
      ServiceContainerReadinessProbe.tcp(portname),
      30000L,
      250L
    ).toOption.get
    ServiceContainerDefinition.runtimeOwnedC(
      serviceid,
      ServiceContainerOwner(ServiceContainerOwnerKind.ComponentRuntime, ownerid),
      image,
      Vector(port),
      readiness,
      cleanuppolicy = cleanup
    ).toOption.get
  }

  private def _service_id(value: String): ServiceContainerId =
    ServiceContainerId.parseC(value).toOption.get

  private def _endpoint(value: String): ServiceContainerEndpoint =
    ServiceContainerEndpoint.parseC(value).toOption.get

  private final class _FailingRuntime(message: String) extends ServiceContainerRuntime {
    private def _failure_c[A]: Consequence[A] =
      Consequence.Failure(Conclusion.from(new RuntimeException(message)))

    def resolveC(definition: ServiceContainerDefinition): Consequence[ServiceContainerResolution] = _failure_c
    def stopC(key: ServiceContainerRegistryKey): Consequence[ServiceContainerCleanupOutcome] = _failure_c
    def restartC(key: ServiceContainerRegistryKey): Consequence[ServiceContainerResolution.RuntimeOwned] = _failure_c
    def removeC(key: ServiceContainerRegistryKey): Consequence[ServiceContainerCleanupOutcome] = _failure_c
    def shutdownC(): Consequence[Vector[ServiceContainerCleanupOutcome]] = _failure_c
  }
}
