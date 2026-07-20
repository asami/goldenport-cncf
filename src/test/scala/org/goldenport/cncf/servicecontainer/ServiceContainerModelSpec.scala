package org.goldenport.cncf.servicecontainer

import org.goldenport.Consequence
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the provider-neutral managed service-container
 * model and its structured diagnostic boundary.
 *
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class ServiceContainerModelSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _names =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(24))

  "Managed service-container model" should {
    "admit canonical logical owner and service identities without host identifiers" in {
      Given("generated logical names and host-oriented invalid values")
      val property = Prop.forAll(_names) { value =>
        ServiceContainerOwnerId.parseC(value.toUpperCase(java.util.Locale.ROOT)).toOption.exists(_.print == value) &&
          ServiceContainerId.parseC(value.toUpperCase(java.util.Locale.ROOT)).toOption.exists(_.print == value)
      }
      val invalid = Vector("", "../service", "/tmp/service", "service_name", "service owner")

      When("the identities are admitted")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)
      val rejected = invalid.map(ServiceContainerId.parseC)

      Then("only safe logical identities survive")
      checked.passed shouldBe true
      rejected.forall(_.isFaillure) shouldBe true
    }

    "accept only credential-free HTTP endpoints" in {
      Given("one safe endpoint and unsafe endpoint variants")
      val safe = ServiceContainerEndpoint.parseC("http://127.0.0.1:11434/api")
      val unsafe = Vector(
        "http://user:secret@localhost:11434",
        "http://localhost:11434?token=secret",
        "http://localhost:11434/#fragment",
        "file:///tmp/service.sock",
        "localhost:11434"
      )

      When("deployment endpoints are parsed")
      val rejected = unsafe.map(ServiceContainerEndpoint.parseC)

      Then("the projected endpoint contains no credentials or transport extras")
      safe.toOption.map(_.safeAuthority) shouldBe Some("http://127.0.0.1:11434")
      rejected.forall(_.isFaillure) shouldBe true
    }

    "bind readiness only to a unique declared container port" in {
      Given("one managed service and readiness policies for declared and unknown ports")
      val service = _service_id("ollama")
      val owner = _owner("textus-ai")
      val image = _image("ollama/ollama:latest")
      val api = _port("api", 11434)
      val unknown = _port_name("admin")
      val readiness = _readiness(ServiceContainerReadinessProbe.tcp(api.name))
      val unknownreadiness = _readiness(ServiceContainerReadinessProbe.tcp(unknown))

      When("runtime-owned definitions are created")
      val admitted = ServiceContainerDefinition.runtimeOwnedC(service, owner, image, Vector(api), readiness)
      val missing = ServiceContainerDefinition.runtimeOwnedC(service, owner, image, Vector(api), unknownreadiness)
      val duplicate = ServiceContainerDefinition.runtimeOwnedC(service, owner, image, Vector(api, api), readiness)

      Then("only a unique declared endpoint contract is admitted")
      admitted.toOption.map(_.ownershipMode) shouldBe Some(ServiceContainerOwnershipMode.RuntimeOwned)
      admitted.toOption.map(_.registryKey.print) shouldBe Some("component-runtime:textus-ai/ollama")
      missing.isFaillure shouldBe true
      duplicate.isFaillure shouldBe true
    }

    "admit an OCI-like image identity while rejecting malformed repository structure" in {
      Given("tagged and digested images plus malformed image references")
      val digest = "a" * 64
      val admitted = Vector(
        "ollama/ollama:latest",
        "registry.example:5000/team/service:1.2.3",
        s"example/service@sha256:${digest}"
      )
      val invalid = Vector("repo//image", "repo/../image", "repo/image:", "registry.example:70000/image")

      When("the runtime parses the image identities")
      val accepted = admitted.map(ServiceContainerImage.parseC)
      val rejected = invalid.map(ServiceContainerImage.parseC)

      Then("only structurally safe image identities reach a gateway definition")
      accepted.forall(_.isSuccess) shouldBe true
      rejected.forall(_.isFaillure) shouldBe true
    }

    "permit only runtime-owned named volumes rather than host mount paths" in {
      Given("a named volume and host-oriented volume text")
      val volume = ServiceContainerVolumeName.parseC("ollama-models")
      val hostpath = ServiceContainerVolumeName.parseC("/var/lib/ollama")

      When("the persistence declaration is assembled")
      val persistence = volume.flatMap(x => ServiceContainerPersistence.namedVolumesC(Vector(x)))

      Then("the named volume is retained and the host path is rejected")
      persistence.toOption.collect {
        case ServiceContainerPersistence.NamedVolumes(volumes) => volumes.map(_.print)
      } shouldBe Some(Vector("ollama-models"))
      hostpath.isFaillure shouldBe true
    }

    "express lifecycle failures through structured Conclusion diagnostics" in {
      Given("normal CNCF failures for every initial managed-service failure category")
      val service = _service_id("ollama")
      val key = ServiceContainerRegistryKey(_owner("textus-ai"), service)
      val failures = Vector(
        "service-container.gateway-unavailable" -> ServiceContainerDiagnostics.gatewayUnavailableC[Unit](service),
        "service-container.image-unavailable" -> ServiceContainerDiagnostics.imageUnavailableC[Unit](service),
        "service-container.ownership-conflict" -> ServiceContainerDiagnostics.ownershipConflictC[Unit](key),
        "service-container.incompatible-existing-service" -> ServiceContainerDiagnostics.incompatibleExistingServiceC[Unit](key),
        "service-container.port-conflict" -> ServiceContainerDiagnostics.portConflictC[Unit](service),
        "service-container.startup-failure" -> ServiceContainerDiagnostics.startupFailureC[Unit](service),
        "service-container.readiness-timeout" -> ServiceContainerDiagnostics.readinessTimeoutC[Unit](service),
        "service-container.unhealthy" -> ServiceContainerDiagnostics.unhealthyC[Unit](service),
        "service-container.revision-conflict" -> ServiceContainerDiagnostics.revisionConflictC[Unit](key)
      )

      When("the common Conclusion projection classifies them")
      val diagnostics = failures.map { case (policy, failure) => policy -> _classification(failure) }

      Then("every category has a stable structured policy without a private error envelope")
      diagnostics.forall { case (policy, diagnostic) => diagnostic.policy.contains(policy) } shouldBe true
      diagnostics.find(_._1 == "service-container.readiness-timeout").map(_._2.diagnosticKey) shouldBe Some("timeout")
      diagnostics.find(_._1 == "service-container.ownership-conflict").map(_._2.diagnosticKey) shouldBe Some("conflict")
    }
  }

  private def _classification[A](consequence: Consequence[A]): ConclusionDiagnostics.Classification =
    consequence match {
      case Consequence.Failure(conclusion) => ConclusionDiagnostics.classify(conclusion)
      case _ => fail("expected structured failure")
    }

  private def _owner(value: String): ServiceContainerOwner =
    ServiceContainerOwner(
      ServiceContainerOwnerKind.ComponentRuntime,
      ServiceContainerOwnerId.parseC(value).toOption.get
    )

  private def _service_id(value: String): ServiceContainerId =
    ServiceContainerId.parseC(value).toOption.get

  private def _image(value: String): ServiceContainerImage =
    ServiceContainerImage.parseC(value).toOption.get

  private def _port_name(value: String): ServiceContainerPortName =
    ServiceContainerPortName.parseC(value).toOption.get

  private def _port(
    name: String,
    number: Int
  ): ServiceContainerPort =
    ServiceContainerPort.createC(_port_name(name), number).toOption.get

  private def _readiness(
    probe: ServiceContainerReadinessProbe
  ): ServiceContainerReadinessPolicy =
    ServiceContainerReadinessPolicy.createC(probe, 30000L, 250L).toOption.get
}
