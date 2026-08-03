package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationResolutionContext, CncfConfigurationTarget, RuntimeConfig, SubsystemInstanceId}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.goldenport.configuration.{Configuration, ConfigurationBindingCandidate, ConfigurationBindingCandidates, ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationOrigin, ConfigurationProvenance, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class RuntimeComponentDevelopmentWebProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e1 = afterWord("in spec:phase-55-runtime-component-development-web-projection, example:E1, rules:GCF09E-C1,C2,C3, phase:55, slice:GCF-09E")

  "Runtime component-development Web projection" should {
    "E1 use only admitted component-development paths" must _e1 {
      "when raw configuration conflicts with an admitted path" in {
        Given("raw and admitted component-development descriptors with distinct exposures")
        val raw = _descriptor("internal")
        val admitted = _descriptor("public")
        val subsystem = _subsystem("component-dev-runtime", raw.toString, Some(admitted.toString))

        When("the runtime HTTP engine is created")
        val engine = HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime engine is required"))

        Then("the descriptor and engine path projection select the admitted directory only")
        engine.webDescriptor.expose("notice-board.notice.search-notices") shouldBe WebDescriptor.Exposure.Public
        engine.runtimeComponentDevDirs shouldBe Some(Vector(admitted.toAbsolutePath.normalize))
      }

      "when the admitted collection omits the optional component-development value" in {
        Given("a raw component-development descriptor and admitted absence")
        val raw = _descriptor("public")
        val subsystem = _subsystem("component-dev-absence", raw.toString, None)

        When("the runtime HTTP engine is created")
        val engine = HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime engine is required"))

        Then("raw component-development configuration cannot revive")
        engine.webDescriptor.expose.get("notice-board.notice.search-notices") shouldBe None
        engine.runtimeComponentDevDirs shouldBe Some(Vector.empty)
      }

      "when no bindings were admitted" in {
        Given("a raw component-development descriptor without runtime admission")
        val raw = _descriptor("public")
        val subsystem = new Subsystem(name = "component-dev-unadmitted", configuration = _configuration(raw.toString))

        When("runtime engine construction is requested")
        val result = HttpExecutionEngine.Factory.forRuntime(subsystem)

        Then("construction fails structurally rather than reviving raw configuration")
        result.isSuccess shouldBe false
      }

      "when static component roots are resolved" in {
        Given("conflicting raw and admitted component development roots containing Web and manual assets")
        val raw = _component_directory("raw")
        val admitted = _component_directory("typed")
        val subsystem = _subsystem("component-dev-static", raw.toString, Some(admitted.toString))
          .add(Vector(TestComponentFactory.create("dev_component", Protocol.empty)))

        When("the runtime server resolves component Web and manual roots")
        val server = new Http4sHttpServer(HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime engine is required")))
        val webroots = server._component_web_roots("dev-component").map(_.name)
        val manualroots = server._component_manual_roots("dev-component").map(_.name)

        Then("only the admitted root contributes static assets and manuals")
        webroots should contain(admitted.resolve("src").resolve("main").resolve("web").toString)
        webroots should not contain raw.resolve("src").resolve("main").resolve("web").toString
        manualroots should contain(admitted.resolve("src").resolve("main").resolve("car").resolve("manual").toString)
        manualroots should not contain raw.resolve("src").resolve("main").resolve("car").resolve("manual").toString
      }

      "when the admitted component-development value is absent" in {
        Given("a raw component development root and an admitted empty collection")
        val raw = _component_directory("absence")
        val subsystem = _subsystem("component-dev-static-absence", raw.toString, None)
          .add(Vector(TestComponentFactory.create("dev_component", Protocol.empty)))

        When("the runtime server resolves component roots")
        val server = new Http4sHttpServer(HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime engine is required")))
        val webroots = server._component_web_roots("dev-component").map(_.name)
        val manualroots = server._component_manual_roots("dev-component").map(_.name)

        Then("raw asset and manual roots do not revive")
        webroots should not contain raw.resolve("src").resolve("main").resolve("web").toString
        manualroots should not contain raw.resolve("src").resolve("main").resolve("car").resolve("manual").toString
      }

      "when a legacy direct engine resolves a configured descriptor root" in {
        Given("a raw descriptor path and no runtime engine admission")
        val root = _descriptor("public")
        val subsystem = new Subsystem(name = "component-dev-legacy-root", configuration = ResolvedConfiguration(
          Configuration(Map(RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString))),
          ConfigurationTrace.empty
        ))

        When("a direct compatibility engine constructs the HTTP server")
        val rootresult = new Http4sHttpServer(new HttpExecutionEngine(subsystem))._web_descriptor_config_root()

        Then("the legacy static root remains available from raw configuration")
        rootresult.map(_.name) shouldBe Some(root.toString)
      }

      "when runtime admission omits a conflicting raw descriptor" in {
        Given("a raw descriptor and an admitted empty runtime collection")
        val root = _descriptor("public")
        val subsystem = new Subsystem(name = "component-dev-runtime-descriptor-absence", configuration = ResolvedConfiguration(
          Configuration(Map(RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(root.resolve("web.yaml").toString))),
          ConfigurationTrace.empty
        ))
        subsystem.admitRuntimeConfigurationBindingsC(ConfigurationBindingCollection.empty[CncfConfigurationTarget]).isSuccess shouldBe true

        When("the runtime server resolves its descriptor static root")
        val rootresult = new Http4sHttpServer(HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime engine is required")))._web_descriptor_config_root()

        Then("admitted descriptor absence blocks raw static-root fallback")
        rootresult shouldBe None
      }

      "when a legacy direct engine resolves raw component roots" in {
        Given("a raw component development root and no runtime admission")
        val raw = _component_directory("legacy")
        val subsystem = new Subsystem(name = "component-dev-legacy-components", configuration = _configuration(raw.toString))
          .add(Vector(TestComponentFactory.create("dev_component", Protocol.empty)))

        When("the direct compatibility server resolves component roots")
        val server = new Http4sHttpServer(new HttpExecutionEngine(subsystem))
        val webroots = server._component_web_roots("dev-component").map(_.name)
        val manualroots = server._component_manual_roots("dev-component").map(_.name)

        Then("raw component Web and manual roots remain available only on the legacy path")
        webroots should contain(raw.resolve("src").resolve("main").resolve("web").toString)
        manualroots should contain(raw.resolve("src").resolve("main").resolve("car").resolve("manual").toString)
      }
    }
  }

  private def _descriptor(exposure: String) = {
    val root = Files.createTempDirectory("cncf-runtime-component-dev-")
    val descriptor = root.resolve("web.yaml")
    Files.writeString(descriptor, s"web:\n  expose:\n    notice-board.notice.search-notices: $exposure\n", StandardCharsets.UTF_8)
    root
  }

  private def _configuration(path: String): ResolvedConfiguration = ResolvedConfiguration(
    Configuration(Map(RuntimeConfig.componentDevDirKey -> ConfigurationValue.StringValue(path))), ConfigurationTrace.empty)

  private def _subsystem(name: String, raw: String, admitted: Option[String]): Subsystem = {
    val subsystem = new Subsystem(name = name, configuration = _configuration(raw))
    val identity = SubsystemInstanceId.default(name).getOrElse(fail("Subsystem identity is required"))
    val target: CncfConfigurationTarget = CncfConfigurationTarget.Global
    val candidates = admitted.toVector.map { value =>
      val parameter = CncfConfigurationParameterCatalog.componentDevDir
      val provenance = ConfigurationProvenance.create(ConfigurationOrigin.Cwd, "textus", "component-dev-spec", Some(parameter.id.value), Some(parameter.id.value), 30, 1, Vector("phase-55: gcf09e"), false, Some("spec")).getOrElse(fail("Provenance is required"))
      val decoded = parameter.codec.decode(ConfigurationValue.StringValue(value)).getOrElse(fail("Value is invalid"))
      ConfigurationBindingCandidate.create(parameter, target, decoded, provenance).getOrElse(fail("Candidate is required"))
    }
    val bindings = if (candidates.isEmpty) ConfigurationBindingCollection.empty[CncfConfigurationTarget] else ConfigurationBindingResolver.resolve(ConfigurationBindingCandidates.from(candidates).getOrElse(fail("Candidates are required")), CncfConfigurationResolutionContext.forSubsystem(identity).getOrElse(fail("Context is required")).generic).getOrElse(fail("Bindings are required"))
    subsystem.admitRuntimeConfigurationBindingsC(bindings).isSuccess shouldBe true
    subsystem
  }

  private def _component_directory(label: String) = {
    val parent = Files.createTempDirectory(s"cncf-runtime-component-$label-")
    val directory = parent.resolve("dev-component")
    Files.createDirectories(directory.resolve("src").resolve("main").resolve("web"))
    Files.createDirectories(directory.resolve("src").resolve("main").resolve("car").resolve("manual"))
    directory
  }
}
