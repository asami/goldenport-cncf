package org.goldenport.cncf.servicecontainer

import org.goldenport.Consequence
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationResolutionContext, CncfConfigurationTarget, SubsystemInstanceId}
import org.goldenport.configuration.{Configuration, ConfigurationBindingCandidate, ConfigurationBindingCandidates, ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationOrigin, ConfigurationProvenance, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
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
      val subsystem = _runtime_subsystem("service-container-config-spec", configuration)
      val serviceid = ServiceContainerId.parseC("ollama").toOption

      When("a component requests the managed lifecycle runtime twice")
      val first = serviceid.map(x => subsystem.serviceContainerRuntimeC(x))
      val second = serviceid.map(x => subsystem.serviceContainerRuntimeC(x))

      Then("the Subsystem supplies an observed runtime without contacting Docker during installation")
      first.exists(_.isSuccess) shouldBe true
      second.exists(_.isSuccess) shouldBe true
      subsystem.serviceContainerRuntime.isDefined shouldBe true
    }

    "fail closed after admitted absence and before admission" in {
      Given("a raw Docker deployment, an admitted empty collection, and an unadmitted Subsystem")
      given ExecutionContext = ExecutionContext.create()
      val raw = _configuration(Map(ServiceContainerRuntimeConfiguration.DRIVER_KEY -> "docker"))
      val absent = new Subsystem("service-container-absent", configuration = raw)
      val unadmitted = new Subsystem("service-container-unadmitted", configuration = raw)
      val serviceid = ServiceContainerId.parseC("ollama").getOrElse(fail("Service id is required"))

      When("the admitted and unadmitted runtime boundaries resolve the service container")
      absent.admitRuntimeConfigurationBindingsC(ConfigurationBindingCollection.empty[CncfConfigurationTarget]).isSuccess shouldBe true
      val absentresult = absent.serviceContainerRuntimeC(serviceid)
      val unadmittedresult = unadmitted.serviceContainerRuntimeC(serviceid)

      Then("raw Docker does not revive after admitted absence and missing admission fails structurally")
      absentresult.isSuccess shouldBe false
      absent.serviceContainerRuntime shouldBe None
      unadmittedresult.isSuccess shouldBe false
    }

    "select the admitted Docker executable over conflicting raw configuration" in {
      Given("a malformed raw executable and a valid admitted Docker executable")
      val raw = _configuration(Map(
        ServiceContainerRuntimeConfiguration.DRIVER_KEY -> "docker",
        ServiceContainerRuntimeConfiguration.DOCKER_EXECUTABLE_KEY -> "docker --host raw"
      ))
      val subsystem = _runtime_subsystem("service-container-executable", raw, Map(
        ServiceContainerRuntimeConfiguration.DRIVER_KEY -> "docker",
        ServiceContainerRuntimeConfiguration.DOCKER_EXECUTABLE_KEY -> "/opt/textus/docker"
      ))

      When("the Subsystem resolves and installs its runtime from the admitted collection")
      given ExecutionContext = ExecutionContext.create()
      val serviceid = ServiceContainerId.parseC("ollama").getOrElse(fail("Service id is required"))
      val result = subsystem.serviceContainerRuntimeC(serviceid)

      Then("the malformed raw executable cannot replace the admitted value at the Subsystem boundary")
      result.isSuccess shouldBe true
      result.toOption.flatMap(_.configuredDockerExecutable) shouldBe Some("/opt/textus/docker")
    }
  }

  private def _configuration(values: Map[String, String]): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.view.mapValues(ConfigurationValue.StringValue(_)).toMap),
      ConfigurationTrace.empty
    )

  private def _runtime_subsystem(name: String, configuration: ResolvedConfiguration, typedvalues: Map[String, String] = Map.empty): Subsystem = {
    val subsystem = new Subsystem(name, configuration = configuration)
    subsystem.admitRuntimeConfigurationBindingsC(_runtime_bindings(subsystem, if (typedvalues.nonEmpty) typedvalues else configuration.configuration.values.collect { case (key, ConfigurationValue.StringValue(value)) => key -> value })).isSuccess shouldBe true
    subsystem
  }

  private def _runtime_bindings(subsystem: Subsystem, values: Map[String, String]) = {
    val identity = SubsystemInstanceId.default(subsystem.name).getOrElse(fail("Subsystem identity is required"))
    val target: CncfConfigurationTarget = CncfConfigurationTarget.SubsystemInstance.create(identity).getOrElse(fail("Subsystem target is required"))
    val parameters = Vector(
      CncfConfigurationParameterCatalog.serviceContainerDriver,
      CncfConfigurationParameterCatalog.serviceContainerDockerExecutable
    )
    val candidates = parameters.flatMap { parameter =>
      values.get(parameter.id.value).map(value => parameter.codec.decode(ConfigurationValue.StringValue(value)).getOrElse(fail("Typed value is invalid"))).map { value =>
        val provenance = ConfigurationProvenance.create(ConfigurationOrigin.Cwd, "textus", "service-container-spec", Some(parameter.id.value), Some(parameter.id.value), 30, 1, Vector("phase-55: gcf09d"), false, Some("spec")).getOrElse(fail("Provenance is required"))
        ConfigurationBindingCandidate.create(parameter, target, value, provenance).getOrElse(fail("Candidate is required"))
      }
    }
    val bindings = ConfigurationBindingResolver.resolve(
      ConfigurationBindingCandidates.from(candidates).getOrElse(fail("Candidates are required")),
      CncfConfigurationResolutionContext.forSubsystem(identity).getOrElse(fail("Context is required")).generic
    ).getOrElse(fail("Bindings are required"))
    bindings
  }
}
