package org.goldenport.cncf.component

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.{Executors, TimeUnit}
import java.util.Comparator

import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.goldenport.Consequence
import org.goldenport.cncf.assembly.AssemblyReport
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, GlobalRuntimeContext}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemFactory}
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.component.testutil.CarArchiveFixture
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  8, 2026
 * @version Aug. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56ComponentIdentityObservabilitySpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord("in spec:phase-56-component-identity-observability, example:E1, rules:CID06C-R5,R8, phase:56, slice:CID-06C")
  private val _e2 = afterWord("in spec:phase-56-component-identity-observability, example:E2, rules:CID06C-R5,R8, phase:56, slice:CID-06C")
  private val _e3 = afterWord("in spec:phase-56-component-identity-observability, example:E3, rules:CID06C-R2,R4,R6, phase:56, slice:CID-06C")
  private val _e4 = afterWord("in spec:phase-56-component-identity-observability, example:E4, rules:CID06C-R4,R9, phase:56, slice:CID-06C")
  private val _e5 = afterWord("in spec:phase-56-component-identity-observability, example:E5, rules:CID06C-R3,R4, phase:56, slice:CID-06C")
  private val _e6 = afterWord("in spec:phase-56-component-identity-observability, example:E6, rules:CID06C-R7, phase:56, slice:CID-06C")
  private val _e7 = afterWord("in spec:phase-56-component-identity-observability, example:E7, rules:CID06C-R1,R6, phase:56, slice:CID-06C")
  private val _e8 = afterWord("in spec:phase-56-component-identity-observability, example:E8, rules:CID06C-R3,R4,R6, phase:56, slice:CID-06C")
  private val _e9 = afterWord("in spec:phase-56-component-identity-observability, example:E9, rules:CID06C-R8,R9, phase:56, slice:CID-06C")
  private val _e10 = afterWord("in spec:phase-56-component-identity-observability, example:E10, rules:CID06C-R2,R3,R8,R9, phase:56, slice:CID-06C")

  "Phase 56 Component identity observability" should {
    "retain typed admission notices" which {
    "E1 adapt an assembly-binding alias and retain one report notice" must _e1 {
      "when a bare assembly alias is presented" in {
        Given("one admitted canonical ComponentId and an empty AssemblyReport")
        val componentid = ComponentId("org.example.Catalog")
        val report = new AssemblyReport()

        When("the assembly-binding surface adapts Catalog and its notice is observed")
        val result = ComponentIdentityCompatibilityAdapter.resolveAliases(
          "Catalog",
          Vector(_candidate(componentid)),
          ComponentIdentityCompatibilityAdapter.Surface.AssemblyBinding
        )
        val notice = result match {
          case ComponentIdentityCompatibilityAdapter.Adapted(_, value) => value
          case other => fail(s"expected an adapted assembly-binding alias: $other")
        }
        ComponentIdentityCompatibilityObserver.observe(report, notice)

        Then("the unique alias resolves canonically and one assembly warning is retained")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
        notice.componentid shouldBe componentid
        notice.aliaskind shouldBe ComponentIdentityCompatibilityAdapter.AliasKind.Bare
        report.warnings should have size 1
        report.warnings.head.reason.getOrElse("") should include ("surface=assembly-binding")
      }
    }

    "E2 adapt descriptor-field alias input and retain one report notice" must _e2 {
      "when a legacy descriptor-field selector is presented" in {
        Given("one canonical candidate and an empty report")
        val componentid = ComponentId("org.example.Catalog")
        val report = new AssemblyReport()

        When("the descriptor-field surface adapts Catalog and its notice is observed")
        val result = ComponentIdentityCompatibilityAdapter.resolveAliases(
          "Catalog",
          Vector(_candidate(componentid)),
          ComponentIdentityCompatibilityAdapter.Surface.DescriptorField
        )
        val notice = result match {
          case ComponentIdentityCompatibilityAdapter.Adapted(_, value) => value
          case other => fail(s"expected an adapted descriptor-field alias: $other")
        }
        ComponentIdentityCompatibilityObserver.observe(report, notice)

        Then("the unique alias resolves canonically and one descriptor warning is retained")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
        notice.componentid shouldBe componentid
        notice.aliaskind shouldBe ComponentIdentityCompatibilityAdapter.AliasKind.Bare
        report.warnings should have size 1
        report.warnings.head.reason.getOrElse("") should include ("surface=descriptor-field")
      }
    }
    }

    "resolve runtime selector compatibility" which {
    "E3 adapt one presentation alias at the runtime selector" must _e3 {
      "when a display or artifact spelling has one canonical candidate" in {
        Given("one admitted alias candidate")
        val componentid = ComponentId("org.example.Catalog")
        When("the runtime selector resolves the artifact spelling")
        val result = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", Vector(_candidate(componentid, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector)
        Then("the unique runtime selector resolves to its canonical identity with a typed notice")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
        result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].componentid shouldBe componentid
        result.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].notice.aliaskind shouldBe
          ComponentIdentityCompatibilityAdapter.AliasKind.Presentation
      }
    }

    "E4 keep accepted aliases idempotent and canonical selections silent" must _e4 {
      "when the same accepted alias is observed repeatedly" in {
        Given("one unique presentation alias and report")
        val componentid = ComponentId("org.example.Catalog")
        val report = new AssemblyReport()
        val candidates = Vector(_candidate(componentid, "catalog-ui"))
        When("the alias is resolved and observed twice, then canonical identity is resolved")
        val first = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", candidates, ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector)
        val second = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", candidates, ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector)
        val canonical = ComponentIdentityCompatibilityAdapter.resolveAliases(componentid.name, candidates, ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector)
        ComponentIdentityCompatibilityObserver.observe(
          report,
          first.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].notice
        )
        ComponentIdentityCompatibilityObserver.observe(
          report,
          second.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].notice
        )
        Then("repeated observation retains one warning and canonical resolution remains silent")
        first shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
        second shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
        report.warnings should have size 1
        canonical.toConsequence.map(_.notice) shouldBe Consequence.success(None)
      }
    }

    "E5 reject an alias shared by distinct canonical Components" must _e5 {
      "when admitted presentation aliases collide" in {
        Given("two canonical candidates with the same alias")
        val alpha = ComponentId("org.alpha.Catalog")
        val beta = ComponentId("org.beta.Catalog")
        When("the runtime selector resolves the shared alias")
        val result = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", Vector(_candidate(alpha, "catalog-ui"), _candidate(beta, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.RuntimeSelector)
        Then("the decision is rejected and cannot create an accepted-alias notice")
        result shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
      }
    }

    "E6 advertise only uniquely accepted Help presentation aliases" must _e6 {
      "when Help checks a unique and an ambiguous alias through the adapter" in {
        Given("one unique alias and one ambiguous alias")
        val alpha = ComponentId("org.alpha.Catalog")
        val beta = ComponentId("org.beta.Catalog")
        When("Help evaluates selector acceptance without observation")
        val unique = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", Vector(_candidate(alpha, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.HelpProjection)
        val ambiguous = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", Vector(_candidate(alpha, "catalog-ui"), _candidate(beta, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.HelpProjection)
        Then("Help adapts the unique alias and rejects the ambiguous alias")
        unique shouldBe a[ComponentIdentityCompatibilityAdapter.Adapted]
        unique.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].componentid shouldBe alpha
        unique.asInstanceOf[ComponentIdentityCompatibilityAdapter.Adapted].notice.surface shouldBe
          ComponentIdentityCompatibilityAdapter.Surface.HelpProjection
        ambiguous shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
        ambiguous.asInstanceOf[ComponentIdentityCompatibilityAdapter.Rejected].rejection shouldBe
          a[ComponentIdentityCompatibilityAdapter.Ambiguous]
      }
    }

    "E7 reject unknown qualified and malformed Meta selectors without fallback" must _e7 {
      "when dotted selectors are not admitted canonical identities" in {
        Given("one admitted component alias candidate")
        val componentid = ComponentId("org.example.Catalog")
        When("Meta resolves unknown qualified and malformed dotted selectors")
        val unknown = ComponentIdentityCompatibilityAdapter.resolveAliases("org.example.Unknown", Vector(_candidate(componentid, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.MetaProjection)
        val malformed = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui.bad", Vector(_candidate(componentid, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.MetaProjection)
        Then("both remain rejected rather than falling back to presentation aliases")
        unknown shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
        malformed shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
      }
    }

    "E8 select Web paths only for one admitted Component identity" must _e8 {
      "when a Web alias is unique or ambiguous" in {
        Given("one unique and one colliding admitted Web alias")
        val alpha = ComponentId("org.alpha.Catalog")
        val beta = ComponentId("org.beta.Catalog")
        When("the Web path decision resolves the aliases")
        val unique = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", Vector(_candidate(alpha, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.WebPath)
        val ambiguous = ComponentIdentityCompatibilityAdapter.resolveAliases("catalog-ui", Vector(_candidate(alpha, "catalog-ui"), _candidate(beta, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.WebPath)
        Then("only the unique decision is accepted")
        unique.toConsequence.map(_.componentid) shouldBe Consequence.success(alpha)
        ambiguous shouldBe a[ComponentIdentityCompatibilityAdapter.Rejected]
      }
    }
    }

    "project compatibility warnings through assembly reports" which {
    "E9 expose compatibility warning fields through the existing AssemblyReport" must _e9 {
      "when an accepted runtime alias is observed" in {
        Given("a retained Web-path alias notice and existing assembly report")
        val componentid = ComponentId("org.example.Catalog")
        val report = new AssemblyReport()
        When("bounded concurrent observers insert the same Web-path warning repeatedly")
        val notice = _adapted_notice("catalog-ui", Vector(_candidate(componentid, "catalog-ui")), ComponentIdentityCompatibilityAdapter.Surface.WebPath)
        val executor = Executors.newFixedThreadPool(8)
        (1 to 64).foreach { _ =>
          executor.submit(new Runnable {
            override def run(): Unit = ComponentIdentityCompatibilityObserver.observe(report, notice)
          })
        }
        executor.shutdown()
        val completed = executor.awaitTermination(5, TimeUnit.SECONDS)
        val record = report.toRecord
        Then("the canonical component, surface, alias kind, source alias, and message are exposed once")
        completed shouldBe true
        record.getString("status") shouldBe Some("warning")
        record.getInt("warningCount") shouldBe Some(1)
        report.warnings should have size 1
        report.warnings.head.kind shouldBe "component-identity-compatibility"
        report.warnings.head.componentName shouldBe componentid.name
        report.warnings.head.reason.getOrElse("") should include ("surface=web-path")
        report.warnings.head.reason.getOrElse("") should include ("alias-kind=presentation")
        report.warnings.head.reason.getOrElse("") should include ("alias=catalog-ui")
        report.warnings.head.message should include (s"canonical=${componentid.name}")
      }
    }

    "E10 observe one assembly-binding notice through the default GenericSubsystemFactory path" must _e10 {
      "when a factory admits one unique legacy assembly alias from a real CAR candidate" in {
        Given("a schema-3 CAR component, a bare assembly binding, and one owning GlobalRuntimeContext")
        _with_work_dir { root =>
          GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))
          val componentid = ComponentId("org.goldenport.fixture.PlainAiRunnerProvider")
          val componentjar = _create_fake_component_jar(root.resolve("assets").resolve("component-main.jar"))
          val componentdescriptor = root.resolve("component-descriptor.json")
          Files.writeString(
            componentdescriptor,
            _canonical_descriptor_json(componentid.name, "0.1.0"),
            StandardCharsets.UTF_8
          )
          CarArchiveFixture.write(
            root.resolve("plain-ai-runner-provider.car"),
            Seq("component/main.jar" -> componentjar, "component-descriptor.json" -> componentdescriptor)
          )
          val descriptor = GenericSubsystemDescriptor(
            path = root.resolve("subsystem.yaml"),
            subsystemName = "phase56-factory-observability",
            componentBindings = Vector(
              GenericSubsystemComponentBinding(
                "PlainAiRunnerProvider",
                version = Some("0.1.0")
              )
            ),
            componentDescriptorOverrides = Vector(_canonical_descriptor(componentid, "0.1.0"))
          )
          val configuration = ResolvedConfiguration(
            Configuration(Map(
              RuntimeConfig.repositoryDirKey -> ConfigurationValue.StringValue(s"component-dir:${root.toString}")
            )),
            ConfigurationTrace.empty
          )
          val runtime = GlobalRuntimeContext.create(
            "phase56-factory-observability",
            RuntimeConfig.default,
            configuration,
            ExecutionContext.create().observability,
            AliasResolver.empty
          )
          val previous = GlobalRuntimeContext.current
          try {
            GlobalRuntimeContext.current = Some(runtime)
            When("the public default GenericSubsystemFactory construction path admits the descriptor")
            val subsystem = GenericSubsystemFactory.defaultWithScope(
              descriptor = descriptor,
              context = runtime,
              mode = Some(RunMode.Command),
              configuration = configuration,
              aliasResolver = AliasResolver.empty
            )
            val binding = subsystem.descriptor.flatMap(_.componentBindings.headOption).getOrElse(
              fail("admitted descriptor binding is missing")
            )
            val component = subsystem.components.find(_.componentId == componentid).getOrElse(
              fail("canonical CAR component is missing")
            )
            val warnings = runtime.assemblyReport.warnings

            Then("the descriptor and real component are canonical and the owning report has one deduplicated warning")
            binding.componentName shouldBe componentid.name
            binding.componentId shouldBe Some(componentid)
            component.componentId shouldBe componentid
            warnings should have size 1
            warnings.head.kind shouldBe "component-identity-compatibility"
            warnings.head.componentName shouldBe componentid.name
            warnings.head.reason.getOrElse("") should include ("surface=assembly-binding")
            warnings.head.reason.getOrElse("") should include (s"canonical=${componentid.name}")
          } finally {
            GlobalRuntimeContext.current = previous
          }
        }
      }
    }
    }
  }

  private def _candidate(componentid: ComponentId, aliases: String*): ComponentIdentityCompatibilityAdapter.AliasCandidate =
    ComponentIdentityCompatibilityAdapter.AliasCandidate(componentid, aliases.toVector)

  private def _adapted_notice(
    alias: String,
    candidates: Vector[ComponentIdentityCompatibilityAdapter.AliasCandidate],
    surface: ComponentIdentityCompatibilityAdapter.Surface
  ): ComponentIdentityCompatibilityAdapter.Notice =
    ComponentIdentityCompatibilityAdapter.resolveAliases(alias, candidates, surface) match {
      case ComponentIdentityCompatibilityAdapter.Adapted(_, notice) => notice
      case result => throw new IllegalStateException(s"Expected accepted compatibility alias: $result")
    }

  private def _canonical_descriptor(componentid: ComponentId, release: String): ComponentDescriptor =
    ComponentDescriptor(
      name = Some(componentid.name),
      version = Some(release),
      componentName = Some(componentid.name),
      schemaVersion = Some(3),
      componentId = Some(componentid)
    )

  private def _canonical_descriptor_json(componentid: String, release: String): String = {
    val segments = componentid.split("\\.").toVector
    val namespace = segments.dropRight(1).mkString(".")
    val localid = segments.last
    s"""{"schemaVersion":3,"component":{"namespace":"$namespace","id":"$localid","version":"$release"}}"""
  }

  private def _create_fake_component_jar(target: Path): Path = {
    val factoryclassentry =
      "org/goldenport/cncf/component/repository/fixture/spi/ComponentFactory.class"
    Option(target.getParent).foreach(Files.createDirectories(_))
    Using.resource(new java.util.zip.ZipOutputStream(Files.newOutputStream(target))) { zip =>
      zip.putNextEntry(new java.util.zip.ZipEntry(factoryclassentry))
      zip.closeEntry()
    }
    target
  }

  private def _with_work_dir[A](body: Path => A): A = {
    val workdir = Files.createDirectories(
      Path.of("target", "cncf-test", "work", "phase56-component-identity-observability").toAbsolutePath.normalize
    )
    val root = Files.createTempDirectory(workdir, "factory-")
    try body(root)
    finally _delete_recursively(root)
  }

  private def _delete_recursively(path: Path): Unit =
    if (Files.exists(path))
      Using.resource(Files.walk(path)) { stream =>
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists(_))
      }
}
