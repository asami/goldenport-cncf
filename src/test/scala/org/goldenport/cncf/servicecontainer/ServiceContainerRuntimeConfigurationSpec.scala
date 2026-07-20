package org.goldenport.cncf.servicecontainer

import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.Subsystem
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for opt-in managed service-container driver
 * configuration.
 *
 * @since   Jul. 20, 2026
 * @version Jul. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class ServiceContainerRuntimeConfigurationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Managed service-container runtime configuration" should {
    "remain disabled unless a deployment explicitly selects a driver" in {
      Given("an empty runtime configuration")
      val configuration = _configuration(Map.empty)

      When("the managed service runtime is resolved")
      val result = ServiceContainerRuntimeConfiguration.createC(configuration)

      Then("normal CNCF startup does not acquire a Docker dependency")
      result.toOption shouldBe Some(None)
    }

    "install the constrained Docker runtime through canonical and compatibility keys" in {
      Given("canonical and CNCF compatibility driver selections")
      val canonical = _configuration(Map(
        ServiceContainerRuntimeConfiguration.DRIVER_KEY -> "docker"
      ))
      val compatibility = _configuration(Map(
        "cncf.runtime.service-container.driver" -> "docker",
        "cncf.runtime.service-container.docker.executable" -> "/opt/cncf/bin/docker"
      ))

      When("both deployment configurations are resolved")
      val canonicalresult = ServiceContainerRuntimeConfiguration.createC(canonical)
      val compatibilityresult = ServiceContainerRuntimeConfiguration.createC(compatibility)

      Then("both produce an owned runtime without invoking Docker during configuration")
      canonicalresult.toOption.flatten.isDefined shouldBe true
      compatibilityresult.toOption.flatten.isDefined shouldBe true
    }

    "reject unknown drivers and malformed executable configuration deterministically" in {
      Given("an unknown driver and an executable value that would require shell parsing")
      val unknown = _configuration(Map(
        ServiceContainerRuntimeConfiguration.DRIVER_KEY -> "podman"
      ))
      val malformed = _configuration(Map(
        ServiceContainerRuntimeConfiguration.DRIVER_KEY -> "docker",
        ServiceContainerRuntimeConfiguration.DOCKER_EXECUTABLE_KEY -> "docker --host remote"
      ))

      When("the configurations cross the runtime boundary")
      val unknownresult = ServiceContainerRuntimeConfiguration.createC(unknown)
      val malformedresult = ServiceContainerRuntimeConfiguration.createC(malformed)

      Then("configuration errors remain normal structured failures")
      unknownresult.isFaillure shouldBe true
      malformedresult.isFaillure shouldBe true
    }

    "install the selected driver lazily through the component-facing Subsystem boundary" in {
      Given("a Subsystem whose deployment selected Docker but has requested no service yet")
      given ExecutionContext = ExecutionContext.create()
      val configuration = _configuration(Map(
        ServiceContainerRuntimeConfiguration.DRIVER_KEY -> "docker"
      ))
      val subsystem = new Subsystem("service-container-config-spec", configuration = configuration)
      val serviceid = ServiceContainerId.parseC("ollama").toOption

      When("a component requests the managed lifecycle runtime twice")
      val first = serviceid.map(x => subsystem.serviceContainerRuntimeC(x))
      val second = serviceid.map(x => subsystem.serviceContainerRuntimeC(x))

      Then("the Subsystem supplies an observed runtime without contacting Docker during installation")
      first.exists(_.isSuccess) shouldBe true
      second.exists(_.isSuccess) shouldBe true
      subsystem.serviceContainerRuntime.isDefined shouldBe true
    }
  }

  private def _configuration(values: Map[String, String]): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.view.mapValues(ConfigurationValue.StringValue(_)).toMap),
      ConfigurationTrace.empty
    )
}
