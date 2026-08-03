package org.goldenport.cncf.http

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import org.goldenport.Consequence
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, CncfConfigurationResolutionContext, CncfConfigurationTarget, RuntimeConfig, SubsystemInstanceId}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.{Configuration, ConfigurationBindingCandidate, ConfigurationBindingCandidates, ConfigurationBindingResolver, ConfigurationOrigin, ConfigurationProvenance, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeWebDescriptorProjectionSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-runtime-web-descriptor-projection, example:E1, rules:GCF09-C, phase:55, slice:GCF-09C"
  )

  "Runtime Web descriptor projection" should {
    "E1 select only the admitted descriptor for descriptor and static-root resolution" must _e1 {
      "when raw configuration conflicts with the admitted binding" in {
        Given("separate raw and admitted descriptor roots with different descriptor and asset contents")
        val rawroot = Files.createTempDirectory("cncf-runtime-web-descriptor-raw-")
        val typedroot = Files.createTempDirectory("cncf-runtime-web-descriptor-typed-")
        val rawdescriptor = _descriptor(rawroot, "internal")
        val typedescriptor = _descriptor(typedroot, "public")
        Files.writeString(rawroot.resolve("source.txt"), "raw", StandardCharsets.UTF_8)
        Files.writeString(typedroot.resolve("source.txt"), "typed", StandardCharsets.UTF_8)
        val configuration = ResolvedConfiguration(
          Configuration(Map(RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue(rawdescriptor.toString))),
          ConfigurationTrace.empty
        )
        val subsystem = _admitted_subsystem("runtime-web-descriptor", configuration, typedescriptor.toString)

        When("the runtime-only HTTP engine is created")
        val engine = HttpExecutionEngine.Factory.forRuntime(subsystem).getOrElse(fail("Runtime HTTP engine is required"))
        val root = new Http4sHttpServer(engine)._web_descriptor_config_root().getOrElse(fail("Typed static root is required"))

        Then("both descriptor semantics and static resources come only from the admitted path")
        engine.webDescriptor.expose("notice-board.notice.search-notices") shouldBe WebDescriptor.Exposure.Public
        root.readText(Paths.get("source.txt")) shouldBe Some("typed")
      }

      "when no runtime configuration bindings were admitted" in {
        Given("a Subsystem whose raw configuration names a descriptor")
        val subsystem = new Subsystem(
          name = "unadmitted-runtime-web-descriptor",
          configuration = ResolvedConfiguration(
            Configuration(Map(RuntimeConfig.webDescriptorKey -> ConfigurationValue.StringValue("raw-only.yaml"))),
            ConfigurationTrace.empty
          )
        )

        When("runtime HTTP engine creation is requested")
        val result = HttpExecutionEngine.Factory.forRuntime(subsystem)

        Then("construction fails structurally instead of reviving the raw descriptor")
        result.isSuccess shouldBe false
      }
    }
  }

  private def _descriptor(root: java.nio.file.Path, exposure: String): java.nio.file.Path = {
    val path = root.resolve("web.yaml")
    Files.writeString(
      path,
      s"""web:
         |  expose:
         |    notice-board.notice.search-notices: $exposure
         |""".stripMargin,
      StandardCharsets.UTF_8
    )
    path
  }

  private def _admitted_subsystem(
    name: String,
    configuration: ResolvedConfiguration,
    descriptorpath: String
  ): Subsystem = {
    val subsystem = new Subsystem(name = name, configuration = configuration)
    val identity = SubsystemInstanceId.default(subsystem.name).getOrElse(fail("Subsystem identity is required"))
    val target: CncfConfigurationTarget = CncfConfigurationTarget.SubsystemInstance.create(identity).getOrElse(fail("Subsystem target is required"))
    val provenance = ConfigurationProvenance.create(
      ConfigurationOrigin.Cwd,
      "textus",
      "runtime-web-descriptor",
      Some(CncfConfigurationParameterCatalog.webDescriptor.id.value),
      Some(CncfConfigurationParameterCatalog.webDescriptor.id.value),
      30,
      1,
      Vector("phase-55: gcf09c"),
      false,
      Some("spec")
    ).getOrElse(fail("Web descriptor provenance is required"))
    val candidate = ConfigurationBindingCandidate.create(
      CncfConfigurationParameterCatalog.webDescriptor,
      target,
      descriptorpath,
      provenance
    ).getOrElse(fail("Web descriptor candidate is required"))
    val bindings = ConfigurationBindingResolver.resolve(
      ConfigurationBindingCandidates.from(Vector(candidate)).getOrElse(fail("Web descriptor candidates are required")),
      CncfConfigurationResolutionContext.forSubsystem(identity).getOrElse(fail("Web descriptor context is required")).generic
    ).getOrElse(fail("Web descriptor bindings are required"))
    subsystem.admitRuntimeConfigurationBindingsC(bindings).isSuccess shouldBe true
    subsystem
  }
}
