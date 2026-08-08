package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters._

import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationResolutionContext, CncfConfigurationTarget, RuntimeConfig, SubsystemInstanceId}
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.Protocol
import org.goldenport.configuration.{Configuration, ConfigurationBindingCandidate, ConfigurationBindingCandidates, ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationOrigin, ConfigurationProvenance, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since Aug. 8, 2026
 * @version Aug. 8, 2026
 * @author ASAMI, Tomoharu
 */
final class RuntimeComponentDevelopmentWebProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen with BeforeAndAfterAll {
  private val _work_root: Path = Paths.get(
    "target",
    "cncf-test",
    "work",
    "runtime-component-development-web-projection-spec"
  ).toAbsolutePath.normalize

  override protected def beforeAll(): Unit = {
    _delete_tree(_work_root)
    super.beforeAll()
  }

  override protected def afterAll(): Unit =
    try {
      super.afterAll()
    } finally {
      _delete_tree(_work_root)
    }

  private val _e1 = afterWord("in spec:phase-55-runtime-component-development-web-projection, example:E1, rules:GCF09E-C1,C2,C3, phase:55, slice:GCF-09E")
  private val _e2 = afterWord("in spec:phase-56-runtime-identity-projection, example:E2, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e3 = afterWord("in spec:phase-56-runtime-identity-projection, example:E3, rules:CID05D-R3, phase:56, slice:CID-05D")

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
        subsystem.add(Vector(_qualified_display_component(subsystem, "org.alpha.DevComponent", "dev-component")))

        When("the runtime server resolves component Web and manual roots")
        val server = HttpRuntimeBindingAdmissionFixture.server(HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime engine is required")))
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
        subsystem.add(Vector(_qualified_display_component(subsystem, "org.alpha.DevComponent", "dev-component")))

        When("the runtime server resolves component roots")
        val server = HttpRuntimeBindingAdmissionFixture.server(HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime engine is required")))
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
        val rootresult = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))._web_descriptor_config_root()

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
        val rootresult = HttpRuntimeBindingAdmissionFixture.server(HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime engine is required")))._web_descriptor_config_root()

        Then("admitted descriptor absence blocks raw static-root fallback")
        rootresult shouldBe None
      }

      "when a legacy direct engine resolves raw component roots" in {
        Given("a raw component development root and no runtime admission")
        val raw = _component_directory("legacy")
        val subsystem = new Subsystem(name = "component-dev-legacy-components", configuration = _configuration(raw.toString))
        subsystem.add(Vector(_qualified_display_component(subsystem, "org.alpha.DevComponent", "dev-component")))

        When("the direct compatibility server resolves component roots")
        val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
        val webroots = server._component_web_roots("dev-component").map(_.name)
        val manualroots = server._component_manual_roots("dev-component").map(_.name)

        Then("raw component Web and manual roots remain available only on the legacy path")
        webroots should contain(raw.resolve("src").resolve("main").resolve("web").toString)
        manualroots should contain(raw.resolve("src").resolve("main").resolve("car").resolve("manual").toString)
      }
    }

    "E2 when a qualified runtime identity retains a legacy display and development-directory name" must _e2 {
      "when a qualified runtime identity retains a legacy display and development-directory name" in {
        Given("an admitted development directory named for the visible component display")
        val admitted = _component_directory("qualified-display")
        val subsystem = _subsystem("component-dev-qualified-display", admitted.toString, Some(admitted.toString))
        val component = _qualified_display_component(subsystem, "org.alpha.DevComponent", "dev-component")
        subsystem.add(Vector(component))

        When("the runtime server resolves Web roots by display and exact qualified identity")
        val server = HttpRuntimeBindingAdmissionFixture.server(HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime engine is required")))
        val displayroots = server._component_web_roots("dev-component").map(_.name)
        val canonicalroots = server._component_web_roots("org.alpha.DevComponent").map(_.name)
        val groupedroots = server._component_web_roots().map(_.name)

        Then("the display development directory remains visible while canonical identity remains an exact match")
        val expected = admitted.resolve("src").resolve("main").resolve("web").toString
        displayroots should contain(expected)
        canonicalroots should contain(expected)
        groupedroots should contain(expected)
      }
    }

    "E3 isolate Web and manual roots by exact ComponentId when visible displays collide" must _e3 {
      "when two components share a display but retain separate qualified identities and origins" in {
        Given("two artifact roots and one configured development directory all named for the ambiguous visible display")
        val alphaid = ComponentId("org.alpha.textus.Shared")
        val betaid = ComponentId("org.beta.textus.Shared")
        val alphaassets = _asset_directory("alpha")
        val betaassets = _asset_directory("beta")
        val ambiguousdev = _named_component_directory("Shared")
        val subsystem = _subsystem("component-dev-ambiguous-shared", ambiguousdev.toString, Some(ambiguousdev.toString))
        subsystem.add(Vector(
          _asset_component(subsystem, alphaid, "Shared", ComponentOrigin.Main, alphaassets),
          _asset_component(subsystem, betaid, "Shared", ComponentOrigin.Repository("repository"), betaassets)
        ))

        When("aggregate, qualified, and legacy-display root selectors are resolved")
        val server = HttpRuntimeBindingAdmissionFixture.server(HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime engine is required")))
        val aggregateweb = server._component_web_roots().map(_.name)
        val alphaweb = server._component_web_roots(alphaid.name).map(_.name)
        val betaweb = server._component_web_roots(betaid.name).map(_.name)
        val displayweb = server._component_web_roots("Shared").map(_.name)
        val alphamanual = server._component_manual_roots(alphaid.name).map(_.name)
        val betamanual = server._component_manual_roots(betaid.name).map(_.name)
        val displaymanual = server._component_manual_roots("Shared").map(_.name)

        Then("exact IDs retain only their own artifact roots while the ambiguous display and dev-dir basename select neither")
        val alphawebroot = alphaassets.resolve("src").resolve("main").resolve("web").toString
        val betawebroot = betaassets.resolve("src").resolve("main").resolve("web").toString
        val alphamanualroot = alphaassets.resolve("src").resolve("main").resolve("car").resolve("manual").toString
        val betamanualroot = betaassets.resolve("src").resolve("main").resolve("car").resolve("manual").toString
        aggregateweb should contain allOf (alphawebroot, betawebroot)
        alphaweb should contain(alphawebroot)
        alphaweb should not contain betawebroot
        betaweb should contain(betawebroot)
        betaweb should not contain alphawebroot
        displayweb shouldBe empty
        alphamanual should contain(alphamanualroot)
        alphamanual should not contain betamanualroot
        betamanual should contain(betamanualroot)
        betamanual should not contain alphamanualroot
        displaymanual shouldBe empty
        aggregateweb should not contain ambiguousdev.resolve("src").resolve("main").resolve("web").toString
      }
    }
  }

  private def _descriptor(exposure: String) = {
    val root = _temp_directory("cncf-runtime-component-dev-")
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
    val parent = _temp_directory(s"cncf-runtime-component-$label-")
    val directory = parent.resolve("dev-component")
    Files.createDirectories(directory.resolve("src").resolve("main").resolve("web"))
    Files.createDirectories(directory.resolve("src").resolve("main").resolve("car").resolve("manual"))
    directory
  }

  private def _named_component_directory(name: String) = {
    val parent = _temp_directory("cncf-runtime-component-ambiguous-")
    val directory = parent.resolve(name)
    Files.createDirectories(directory.resolve("src").resolve("main").resolve("web"))
    Files.createDirectories(directory.resolve("src").resolve("main").resolve("car").resolve("manual"))
    directory
  }

  private def _asset_directory(label: String) = {
    val directory = _temp_directory(s"cncf-runtime-component-asset-$label-")
    Files.createDirectories(directory.resolve("src").resolve("main").resolve("web"))
    Files.createDirectories(directory.resolve("src").resolve("main").resolve("car").resolve("manual"))
    directory
  }

  private def _temp_directory(prefix: String): Path = {
    Files.createDirectories(_work_root)
    Files.createTempDirectory(_work_root, prefix)
  }

  private def _delete_tree(path: Path): Unit = {
    if (Files.exists(path)) {
      val entries = Files.walk(path)
      try {
        entries.iterator().asScala.toVector.reverse.foreach(entry => Files.deleteIfExists(entry))
      } finally {
        entries.close()
      }
    }
  }

  private def _asset_component(
    subsystem: Subsystem,
    componentid: ComponentId,
    displayname: String,
    origin: ComponentOrigin,
    assets: java.nio.file.Path
  ): Component = {
    val component = new Component() {
      override def displayName: String = displayname
    }
    component.initialize(ComponentInit(
      subsystem = subsystem,
      core = Component.Core.create(componentid.name, componentid, ComponentInstanceId.default(componentid), Protocol.empty),
      origin = origin,
      componentDescriptors = Vector.empty
    ))
    component.withArtifactMetadata(Component.ArtifactMetadata(
      sourceType = "spec",
      name = componentid.name,
      version = "0.6.0",
      component = Some(displayname),
      archivePath = Some(assets.toString),
      componentId = Some(componentid)
    ))
  }

  private def _qualified_display_component(
    subsystem: Subsystem,
    componentid: String,
    displayname: String
  ): Component = {
    val id = ComponentId(componentid)
    val component = new Component() {
      override def displayName: String = displayname
    }
    component.initialize(ComponentInit(
      subsystem = subsystem,
      core = Component.Core.create(id.name, id, ComponentInstanceId.default(id), Protocol.empty),
      origin = ComponentOrigin.Main,
      componentDescriptors = Vector.empty
    ))
  }
}
