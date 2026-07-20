package org.goldenport.cncf.servicecontainer

import org.goldenport.Consequence
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for external and runtime-owned managed service
 * lifecycle resolution and idempotent runtime cleanup.
 *
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class ServiceContainerRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Managed service-container runtime" should {
    "resolve an external endpoint without touching the registry or gateway" in {
      Given("an external service definition and an empty runtime")
      val serviceid = _service_id("ollama")
      val endpoint = _endpoint("http://ollama.example:11434")
      val definition = ServiceContainerDefinition.external(serviceid, endpoint)
      val registry = ServiceContainerRegistry.inMemory()
      val gateway = FakeServiceContainerGateway.create()
      val runtime = ServiceContainerRuntime.create(registry, gateway)

      When("the service is resolved")
      val result = runtime.resolveC(definition)

      Then("the explicit endpoint is returned with no owned lifecycle activity")
      result.toOption.map(_.endpoint) shouldBe Some(endpoint)
      registry.entries shouldBe empty
      gateway.transitions shouldBe empty
    }

    "converge repeated create-or-reuse on one compatible ready service" in {
      Given("one runtime-owned definition and a deterministic readiness endpoint")
      val definition = _definition("textus-ai", "ollama", ServiceContainerCleanupPolicy.Stop)
      val endpoint = _endpoint("http://127.0.0.1:11434")
      val registry = ServiceContainerRegistry.inMemory()
      val gateway = FakeServiceContainerGateway.create(Map(definition.registryKey -> endpoint))
      val runtime = ServiceContainerRuntime.create(registry, gateway)

      When("the same admitted definition is resolved twice")
      val first = runtime.resolveC(definition)
      val revisionafterfirst = registry.get(definition.registryKey).map(_.revision)
      val second = runtime.resolveC(definition)

      Then("one instance is created and the second resolution reuses it without registry churn")
      first.toOption.collect { case x: ServiceContainerResolution.RuntimeOwned => x.reused } shouldBe Some(false)
      second.toOption.collect { case x: ServiceContainerResolution.RuntimeOwned => x.reused } shouldBe Some(true)
      first.toOption.map(_.endpoint) shouldBe Some(endpoint)
      second.toOption.map(_.endpoint) shouldBe Some(endpoint)
      gateway.inspections.size shouldBe 1
      gateway.transitions.count(_._1 == ServiceContainerTransition.Create) shouldBe 1
      registry.get(definition.registryKey).map(_.status) shouldBe Some(ServiceContainerStatus.Ready)
      registry.get(definition.registryKey).map(_.revision) shouldBe revisionafterfirst
    }

    "require an existing owned service when creation is not admitted" in {
      Given("a RequireExisting definition and an empty gateway")
      val definition = _definition(
        "textus-sie",
        "fuseki",
        ServiceContainerCleanupPolicy.Keep,
        ServiceContainerReusePolicy.RequireExisting
      )
      val registry = ServiceContainerRegistry.inMemory()
      val gateway = FakeServiceContainerGateway.create()
      val runtime = ServiceContainerRuntime.create(registry, gateway)

      When("the absent service is resolved")
      val result = runtime.resolveC(definition)

      Then("resolution fails structurally without attempting creation")
      _policy(result) shouldBe Some("service-container.required-existing-service")
      gateway.transitions.map(_._1) shouldBe Vector(ServiceContainerTransition.Inspect)
      registry.get(definition.registryKey).map(_.status) shouldBe Some(ServiceContainerStatus.Declared)
    }

    "refuse a same-key provider resource whose ownership evidence does not match" in {
      Given("a runtime definition and a provider observation owned by another component")
      val definition = _definition("textus-ai", "ollama", ServiceContainerCleanupPolicy.Stop)
      val instanceid = ServiceContainerInstanceId.parseC("foreign-container").toOption.get
      val labels = ServiceContainerOwnershipLabels.from(definition)
      val foreignlabels = ServiceContainerOwnershipLabels.observed(
        labels.values.updated(ServiceContainerOwnershipLabels.OWNER_ID, "foreign-component")
      )
      val inspection = ServiceContainerInspection(
        instanceid,
        definition.registryKey,
        definition.image,
        definition.ports,
        foreignlabels,
        ServiceContainerStatus.Ready,
        None
      )
      val gateway = FakeServiceContainerGateway.create(initialinspections = Vector(inspection))
      val runtime = ServiceContainerRuntime.create(ServiceContainerRegistry.inMemory(), gateway)

      When("create-or-reuse inspects the same-key resource")
      val result = runtime.resolveC(definition)

      Then("the resource is not adopted or mutated")
      _policy(result) shouldBe Some("service-container.ownership-conflict")
      gateway.transitions.map(_._1) shouldBe Vector(ServiceContainerTransition.Inspect)
      gateway.inspections.map(_.status) shouldBe Vector(ServiceContainerStatus.Ready)
    }

    "retain a structured readiness failure and mark the owned registry state unhealthy" in {
      Given("a runtime-owned definition whose fake provider never becomes ready")
      val definition = _definition("textus-ai", "ollama", ServiceContainerCleanupPolicy.Stop)
      val registry = ServiceContainerRegistry.inMemory()
      val runtime = ServiceContainerRuntime.create(registry, FakeServiceContainerGateway.create())

      When("the runtime creates and checks the service")
      val result = runtime.resolveC(definition)

      Then("the original unhealthy diagnostic survives and the registry reflects the failure")
      _policy(result) shouldBe Some("service-container.unhealthy")
      registry.get(definition.registryKey).map(_.status) shouldBe Some(ServiceContainerStatus.Unhealthy)
    }

    "retain a structured startup failure and mark the owned registry state failed" in {
      Given("a runtime-owned definition whose fake provider rejects start")
      val definition = _definition("textus-ai", "ollama", ServiceContainerCleanupPolicy.Stop)
      val registry = ServiceContainerRegistry.inMemory()
      val gateway = FakeServiceContainerGateway.create(
        failuretransitions = Set(ServiceContainerTransition.Start -> definition.registryKey)
      )
      val runtime = ServiceContainerRuntime.create(registry, gateway)

      When("the runtime creates and starts the service")
      val result = runtime.resolveC(definition)

      Then("the original startup diagnostic survives and the registry reflects the failure")
      _policy(result) shouldBe Some("service-container.startup-failure")
      registry.get(definition.registryKey).map(_.status) shouldBe Some(ServiceContainerStatus.Failed)
    }

    "restart and stop an owned service idempotently through the same runtime registry" in {
      Given("one resolved runtime-owned service")
      val definition = _definition("textus-ai", "ollama", ServiceContainerCleanupPolicy.Stop)
      val endpoint = _endpoint("http://127.0.0.1:11434")
      val registry = ServiceContainerRegistry.inMemory()
      val gateway = FakeServiceContainerGateway.create(Map(definition.registryKey -> endpoint))
      val runtime = ServiceContainerRuntime.create(registry, gateway)
      runtime.resolveC(definition).isSuccess shouldBe true

      When("the service is restarted and stop is requested twice")
      val restarted = runtime.restartC(definition.registryKey)
      val firststop = runtime.stopC(definition.registryKey)
      val secondstop = runtime.stopC(definition.registryKey)

      Then("restart returns the ready endpoint and only the first stop mutates the provider")
      restarted.toOption.map(_.endpoint) shouldBe Some(endpoint)
      firststop.toOption.map(_.changed) shouldBe Some(true)
      secondstop.toOption.map(_.changed) shouldBe Some(false)
      gateway.transitions.count(_._1 == ServiceContainerTransition.Stop) shouldBe 1
      registry.get(definition.registryKey).map(_.status) shouldBe Some(ServiceContainerStatus.Stopped)
    }

    "apply keep stop and remove cleanup policies idempotently at runtime shutdown" in {
      Given("three owned services with distinct runtime cleanup policies")
      val keep = _definition("runtime", "keep-service", ServiceContainerCleanupPolicy.Keep)
      val stop = _definition("runtime", "stop-service", ServiceContainerCleanupPolicy.Stop)
      val remove = _definition("runtime", "remove-service", ServiceContainerCleanupPolicy.Remove)
      val endpoint = _endpoint("http://127.0.0.1:8080")
      val definitions = Vector(keep, stop, remove)
      val registry = ServiceContainerRegistry.inMemory()
      val gateway = FakeServiceContainerGateway.create(
        definitions.map(x => x.registryKey -> endpoint).toMap
      )
      val runtime = ServiceContainerRuntime.create(registry, gateway)
      definitions.foreach(x => runtime.resolveC(x).isSuccess shouldBe true)

      When("runtime shutdown cleanup is executed twice")
      val first = runtime.shutdownC()
      val second = runtime.shutdownC()

      Then("keep is retained, stop is performed once, and remove disappears once")
      first.toOption.map(_.map(x => x.registryKey -> x.changed).toMap) shouldBe Some(Map(
        keep.registryKey -> false,
        stop.registryKey -> true,
        remove.registryKey -> true
      ))
      second.toOption.map(_.forall(!_.changed)) shouldBe Some(true)
      registry.get(keep.registryKey).map(_.status) shouldBe Some(ServiceContainerStatus.Ready)
      registry.get(stop.registryKey).map(_.status) shouldBe Some(ServiceContainerStatus.Stopped)
      registry.get(remove.registryKey) shouldBe None
      gateway.transitions.count(_._1 == ServiceContainerTransition.Stop) shouldBe 1
      gateway.transitions.count(_._1 == ServiceContainerTransition.Remove) shouldBe 1
    }

    "make explicit removal idempotent after registry release" in {
      Given("one resolved remove-policy service")
      val definition = _definition("runtime", "database", ServiceContainerCleanupPolicy.Remove)
      val endpoint = _endpoint("http://127.0.0.1:8080")
      val registry = ServiceContainerRegistry.inMemory()
      val gateway = FakeServiceContainerGateway.create(Map(definition.registryKey -> endpoint))
      val runtime = ServiceContainerRuntime.create(registry, gateway)
      runtime.resolveC(definition).isSuccess shouldBe true

      When("removal is requested twice")
      val first = runtime.removeC(definition.registryKey)
      val second = runtime.removeC(definition.registryKey)

      Then("only the first request mutates the provider and both requests succeed")
      first.toOption.map(_.changed) shouldBe Some(true)
      second.toOption.map(_.changed) shouldBe Some(false)
      gateway.transitions.count(_._1 == ServiceContainerTransition.Remove) shouldBe 1
      registry.get(definition.registryKey) shouldBe None
    }

    "continue runtime shutdown cleanup after an earlier owned service fails" in {
      Given("one foreign conflicting service followed by one compatible stop-policy service")
      val conflicting = _definition("runtime", "a-conflicting", ServiceContainerCleanupPolicy.Stop)
      val compatible = _definition("runtime", "z-compatible", ServiceContainerCleanupPolicy.Stop)
      val endpoint = _endpoint("http://127.0.0.1:8080")
      val conflictinginstance = ServiceContainerInstanceId.parseC("foreign-container").toOption.get
      val conflictinglabels = ServiceContainerOwnershipLabels.observed(
        ServiceContainerOwnershipLabels.from(conflicting).values
          .updated(ServiceContainerOwnershipLabels.OWNER_ID, "foreign-runtime")
      )
      val observation = ServiceContainerInspection(
        conflictinginstance,
        conflicting.registryKey,
        conflicting.image,
        conflicting.ports,
        conflictinglabels,
        ServiceContainerStatus.Ready,
        Some(endpoint)
      )
      val registry = ServiceContainerRegistry.inMemory()
      val gateway = FakeServiceContainerGateway.create(
        Map(compatible.registryKey -> endpoint),
        Vector(observation)
      )
      val runtime = ServiceContainerRuntime.create(registry, gateway)
      runtime.resolveC(conflicting).isFaillure shouldBe true
      runtime.resolveC(compatible).isSuccess shouldBe true

      When("runtime shutdown encounters the ownership conflict first")
      val result = runtime.shutdownC()

      Then("shutdown reports the conflict after still stopping the later compatible service")
      _policy(result) shouldBe Some("service-container.ownership-conflict")
      registry.get(compatible.registryKey).map(_.status) shouldBe Some(ServiceContainerStatus.Stopped)
      gateway.transitions.count(_._1 == ServiceContainerTransition.Stop) shouldBe 1
    }
  }

  private def _policy[A](consequence: Consequence[A]): Option[String] =
    consequence match {
      case Consequence.Failure(conclusion) => ConclusionDiagnostics.classify(conclusion).policy
      case _ => None
    }

  private def _definition(
    owner: String,
    service: String,
    cleanup: ServiceContainerCleanupPolicy,
    reuse: ServiceContainerReusePolicy = ServiceContainerReusePolicy.CreateOrReuse
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
      reusepolicy = reuse,
      cleanuppolicy = cleanup
    ).toOption.get
  }

  private def _service_id(value: String): ServiceContainerId =
    ServiceContainerId.parseC(value).toOption.get

  private def _endpoint(value: String): ServiceContainerEndpoint =
    ServiceContainerEndpoint.parseC(value).toOption.get
}
