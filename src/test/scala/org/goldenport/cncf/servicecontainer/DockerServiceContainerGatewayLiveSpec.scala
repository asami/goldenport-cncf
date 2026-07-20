package org.goldenport.cncf.servicecontainer

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Opt-in executable specification for the real Docker service-container
 * lifecycle. Normal test runs cancel this suite before contacting Docker.
 *
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class DockerServiceContainerGatewayLiveSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Docker managed service-container live integration" should {
    "create reuse probe and remove one framework-owned HTTP service" in {
      if (!sys.env.get("CNCF_LIVE_DOCKER_TEST").contains("true"))
        cancel("Set CNCF_LIVE_DOCKER_TEST=true to run the Docker integration specification.")

      Given("an explicitly selected local Docker daemon and a pre-pulled HTTP image")
      val definition = _definition(sys.env.getOrElse("CNCF_LIVE_DOCKER_IMAGE", "nginx:alpine"))
      val registry = ServiceContainerRegistry.inMemory()
      val runtime = ServiceContainerRuntime.create(registry, DockerServiceContainerGateway.create())

      try {
        When("the same runtime-owned service is resolved twice")
        val first = runtime.resolveC(definition)
        val second = runtime.resolveC(definition)

        Then("one ready provider instance is reused through the stable registry key")
        first.toOption.collect { case x: ServiceContainerResolution.RuntimeOwned => x.reused } shouldBe Some(false)
        second.toOption.collect { case x: ServiceContainerResolution.RuntimeOwned => x.reused } shouldBe Some(true)
        first.toOption.map(_.endpoint.safeAuthority) shouldBe second.toOption.map(_.endpoint.safeAuthority)

        When("runtime cleanup is requested")
        val cleanup = runtime.shutdownC()

        Then("the Remove policy releases the Docker resource and registry entry")
        cleanup.toOption.map(_.map(_.changed)) shouldBe Some(Vector(true))
        registry.get(definition.registryKey) shouldBe None
      } finally {
        runtime.removeC(definition.registryKey)
      }
    }
  }

  private def _definition(imagetext: String): ServiceContainerDefinition.RuntimeOwned = {
    val result = for {
      ownerid <- ServiceContainerOwnerId.parseC("cncf-live-spec")
      serviceid <- ServiceContainerId.parseC("http")
      image <- ServiceContainerImage.parseC(imagetext)
      portname <- ServiceContainerPortName.parseC("http")
      port <- ServiceContainerPort.createC(portname, 80)
      probe <- ServiceContainerReadinessProbe.httpC(portname, "/")
      readiness <- ServiceContainerReadinessPolicy.createC(probe, 30000L, 250L)
      definition <- ServiceContainerDefinition.runtimeOwnedC(
        serviceid,
        ServiceContainerOwner(ServiceContainerOwnerKind.SubsystemRuntime, ownerid),
        image,
        Vector(port),
        readiness,
        cleanuppolicy = ServiceContainerCleanupPolicy.Remove
      )
    } yield definition
    result.toOption.getOrElse(fail("The live executable specification fixture must be valid."))
  }
}
