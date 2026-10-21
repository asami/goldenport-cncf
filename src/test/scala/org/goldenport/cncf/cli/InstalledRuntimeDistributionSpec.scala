package org.goldenport.cncf.cli

import java.nio.file.{Files, Path, Paths}
import java.util.{Comparator, UUID}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.config.{RepositoryBootstrapPolicy, ResolvedParameters, RuntimeConfig}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class InstalledRuntimeDistributionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E1 An installed distribution" should afterWord(
    "in spec:installed-runtime-distribution, example:E1, rules:R1,R2,R3, phase:64") {
    "preserve actual ordered coordinates, classifier spelling and unavailable source provenance" in {
      Given("docs/spec/installed-runtime-distribution.md R1-R3 E1 and a complete copied installation")
      _with_fixture { manifest =>
        When("the concrete distribution is admitted")
        val result = InstalledRuntimeDistribution.readC(manifest).toOption.get
        Then("ordered modules, component identity and concrete manifest remain intact")
        result.manifest shouldBe manifest
        result.modules.map(_.coordinate) shouldBe Vector(InstalledRuntimeDistribution.ROOT_COORDINATE,
          "org.example:resource:jar:Tests:1.0-SNAPSHOT")
        result.modules.map(_.path.getFileName.toString) shouldBe Vector("0000.jar", "0001.jar")
        result.component.identity shouldBe InstalledRuntimeDistribution.COMPONENT_IDENTITY
        Files.exists(result.modules.head.source) shouldBe false
      }
    }
  }

  "E2 An invalid manifest" should afterWord(
    "in spec:installed-runtime-distribution, example:E2, rules:R1,R2,R3, phase:64") {
    "reject unsupported identities, malformed coordinates, duplicates and escaping paths" in {
      Given("docs/spec/installed-runtime-distribution.md R1-R3 E2 and invalid manifest variations")
      _with_fixture { manifest =>
        val original = parse(Files.readString(manifest)).toOption.get
        val module = original.hcursor.get[Vector[Json]]("modules").toOption.get.head
        val changes = Vector(
          original.deepMerge(Json.obj("kind" -> Json.fromString("future"))),
          original.deepMerge(Json.obj("application" -> Json.fromString("other"))),
          original.deepMerge(Json.obj("installation_id" -> Json.fromString(UUID.randomUUID().toString))),
          original.deepMerge(Json.obj("update_id" -> Json.fromString("bad"))),
          original.deepMerge(Json.obj("modules" -> Json.arr(module, module))),
          original.deepMerge(Json.obj("modules" -> Json.arr(module.deepMerge(
            Json.obj("path" -> Json.fromString("../outside.jar")))))),
          original.deepMerge(Json.obj("component" -> Json.obj("identity" -> Json.fromString("other"))))
        )
        When("each unsupported distribution is admitted")
        val results = changes.map { json =>
          Files.writeString(manifest, json.noSpaces)
          InstalledRuntimeDistribution.readC(manifest)
        }
        Then("every variation fails before runtime execution")
        results.foreach(_.isFaillure shouldBe true)
      }
    }

    "reject every generated unsupported coordinate shape" in {
      Given("docs/spec/installed-runtime-distribution.md R2 E2 and invalid four-part module coordinates")
      _with_fixture { manifest =>
        val original = parse(Files.readString(manifest)).toOption.get
        val modules = original.hcursor.get[Vector[Json]]("modules").toOption.get
        val coordinates = Gen.alphaNumStr.suchThat(_.nonEmpty).map(name => s"org.example:$name:jar:1.0")
        When("generated four-part aliases are presented as a selected module")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), Prop.forAll(coordinates) { coordinate =>
          val changed = modules(1).deepMerge(Json.obj("coordinate" -> Json.fromString(coordinate)))
          Files.writeString(manifest, original.deepMerge(Json.obj("modules" -> Json.arr(modules.head, changed))).noSpaces)
          InstalledRuntimeDistribution.readC(manifest).isFaillure
        })
        Then("none is silently reinterpreted as a supported coordinate")
        checked.passed shouldBe true
      }
    }
  }

  "E3 Distribution file admission" should afterWord(
    "in spec:installed-runtime-distribution, example:E3, rules:R3, phase:64") {
    "reject missing files, symbolic links and hard links to mutable inputs" in {
      Given("docs/spec/installed-runtime-distribution.md R3 E3 and a copied runtime module")
      _with_fixture { manifest =>
        val module = manifest.getParent.resolve("runtime/0001.jar")
        val source = manifest.getParent.getParent.resolve("shared.jar")
        Files.move(module, source)
        When("the module is absent or replaced by a symbolic or hard link")
        val missing = InstalledRuntimeDistribution.readC(manifest)
        Files.createSymbolicLink(module, source)
        val symbolic = InstalledRuntimeDistribution.readC(manifest)
        Files.delete(module)
        Files.createLink(module, source)
        val hard = InstalledRuntimeDistribution.readC(manifest)
        Then("all three distributions fail admission")
        Vector(missing, symbolic, hard).foreach(_.isFaillure shouldBe true)
      }
    }

    "reject CAR dependency closure that could resolve external mutable artifacts" in {
      Given("docs/spec/installed-runtime-distribution.md R3 E3 and a CAR with external dependency metadata")
      _with_fixture { manifest =>
        InstalledRuntimeDistributionSpec.writeArchive(manifest.getParent.resolve("components/textus-sm-workflow.car"),
          Vector("component-dependencies.yaml" -> "dependencies:\n  shared:\n    - org.example:other:1.0-SNAPSHOT"))
        When("the distribution is admitted")
        val result = InstalledRuntimeDistribution.readC(manifest)
        Then("unsupported external component closure is rejected")
        result.isFaillure shouldBe true
      }
    }
  }

  "E4 Current runtime configuration" should afterWord(
    "in spec:installed-runtime-distribution, example:E4, rules:R4,R6, phase:64") {
    "leave a property-only filename outside current resolved configuration" in {
      Given("docs/spec/installed-runtime-distribution.md R4,R6 E4 and only a JVM property for the manifest")
      val previous = Option(System.getProperty(RuntimeConfig.installedRuntimeManifestKey))
      System.setProperty(RuntimeConfig.installedRuntimeManifestKey, "/unresolved/property-only-runtime.json")
      try {
        When("the runtime helper reads empty resolved parameters")
        val result = RuntimeConfig.installedRuntimeDistributionC(ResolvedParameters.empty())
        Then("the property does not create an operational binding")
        result.toOption shouldBe Some(None)
      } finally previous match {
        case Some(value) => System.setProperty(RuntimeConfig.installedRuntimeManifestKey, value)
        case None => System.clearProperty(RuntimeConfig.installedRuntimeManifestKey)
      }
    }

    "admit an explicit resolved binding and leave absent configuration unbound" in {
      Given("docs/spec/installed-runtime-distribution.md R4,R6 E4 and explicit resolved configuration values")
      _with_fixture { manifest =>
        val parameters = InstalledRuntimeDistributionSpec.parameters(manifest)
        When("the typed runtime helper reads the current resolved binding")
        val explicit = RuntimeConfig.installedRuntimeDistributionC(parameters)
        val absent = RuntimeConfig.installedRuntimeDistributionC(ResolvedParameters.empty())
        Then("only explicit configuration binds the concrete installation and records parameter use")
        explicit.toOption.flatten.map(_.manifest) shouldBe Some(manifest)
        absent.toOption shouldBe Some(None)
        parameters.usedEntries.map(_.key) shouldBe Vector(RuntimeConfig.installedRuntimeManifestKey)
      }
    }

    "reject an invalid explicit manifest during ordinary bootstrap including Help" in {
      Given("docs/spec/installed-runtime-distribution.md R4 E4 and an isolated Help bootstrap with an explicit missing manifest")
      _with_fixture { manifest =>
        val args = Array("--no-default-components", s"--textus.test.home.path=${manifest.getParent.resolve("home")}",
          "--textus.test.home.inherit.runtime=false", "--textus.test.home.inherit.repositories=false",
          "--textus.test.home.inherit.local-data=false",
          s"--component-file=${manifest.getParent.resolve("components/textus-sm-workflow.car")}")
        When("ordinary bootstrap resolves its normal configuration")
        val valid = CncfRuntime.bootstrapC(manifest.getParent, args ++ Array(
          s"--${RuntimeConfig.installedRuntimeManifestKey}=$manifest", "--help"), Map.empty)
        val result = CncfRuntime.bootstrapC(manifest.getParent, args ++ Array(
          s"--${RuntimeConfig.installedRuntimeManifestKey}=${manifest.resolveSibling("missing.json")}", "--help"), Map.empty)
        Then("valid binding bootstraps and the explicit invalid distribution fails before Help can launch a runtime")
        valid.isSuccess shouldBe true
        result.isFaillure shouldBe true
      }
    }
  }

  "E6 Installed bootstrap activation closure" should afterWord(
    "in spec:installed-runtime-distribution, example:E6, rules:R3,R4,R6, phase:64") {
    "reject an additional framework component file while accepting only the pinned installed CAR" in {
      Given("docs/spec/installed-runtime-distribution.md R3,R4,R6 E6 and an isolated pinned profile plus a foreign CAR")
      _with_fixture { manifest =>
        val args = _bootstrap_arguments(manifest)
        val foreign = manifest.getParent.resolve("foreign.car")
        InstalledRuntimeDistributionSpec.writeArchive(foreign, Vector("marker.txt" -> "external"))
        When("ordinary bootstrap admits the pinned profile and then the additional component-file argument")
        val admitted = CncfRuntime.bootstrapC(manifest.getParent, args, Map.empty)
        val rejected = CncfRuntime.bootstrapC(manifest.getParent, args ++ Array(s"--component-file=$foreign"), Map.empty)
        Then("only the profile whose effective activation is exactly its installed CAR succeeds")
        admitted.isSuccess shouldBe true
        rejected.isFaillure shouldBe true
      }
    }

    "reject an unrepresented effective search specification even when the raw policy names only the installed CAR" in {
      Given("docs/spec/installed-runtime-distribution.md R4 E6 and a pinned policy with final typed repository projections")
      _with_fixture { manifest =>
        val distribution = InstalledRuntimeDistribution.readC(manifest).toOption.get
        val policy = RepositoryBootstrapPolicy(componentFiles = Vector(distribution.component.path.toString),
          defaultRepositoriesEnabled = false)
        val pinned = ComponentRepository.ComponentFileRepository.Specification(distribution.component.path)
        val foreign = ComponentRepository.ComponentFileRepository.Specification(manifest.getParent.resolve("foreign.car"))
        When("final bootstrap admission receives the pinned active source and an extra effective search source")
        val admitted = InstalledRuntimeDistribution.admitBootstrapC(distribution, policy, Vector.empty, false,
          false, Right(Vector(pinned)), Right(Vector.empty), Vector.empty)
        val rejected = InstalledRuntimeDistribution.admitBootstrapC(distribution, policy, Vector.empty, false,
          false, Right(Vector(pinned)), Right(Vector(foreign)), Vector.empty)
        Then("the final actual projections must agree with the installed profile as well as its raw policy")
        admitted.isSuccess shouldBe true
        rejected.isFaillure shouldBe true
      }
    }

    "reject a foreign CAR discovered by the ordinary auto-archive continuation" in {
      Given("docs/spec/installed-runtime-distribution.md R4 E6 and an installed binding with a foreign local target archive")
      _with_fixture { manifest =>
        val target = Files.createDirectories(manifest.getParent.resolve("target"))
        InstalledRuntimeDistributionSpec.writeArchive(target.resolve("foreign.car"), Vector("marker.txt" -> "external"))
        val args = _bootstrap_arguments(manifest).filterNot(_.startsWith("--component-file="))
        When("ordinary bootstrap completes auto-archive discovery and re-admits its repository arguments")
        val result = CncfRuntime.bootstrapC(manifest.getParent, args, Map.empty)
        Then("the discovered foreign archive cannot replace the required pinned component")
        result.isFaillure shouldBe true
      }
    }

    "reject configured component, SAR, subsystem and collaborator sources after normal configuration admission" in {
      Given("docs/spec/installed-runtime-distribution.md R3,R4,R6 E6 and explicit configuration files adding external activation")
      _with_fixture { manifest =>
        val config = manifest.getParent.resolve("extra-activation.yaml")
        val extra = manifest.getParent.resolve("external")
        Files.createDirectories(extra)
        val keys = Vector(RuntimeConfig.componentDirKey, RuntimeConfig.subsystemSarDirKey,
          RuntimeConfig.subsystemFileKey, RuntimeConfig.subsystemDescriptorKey,
          "textus.collaborator.repositories")
        When("each configured source is combined with the ordinary pinned bootstrap arguments")
        val results = keys.map { key =>
          Files.writeString(config, s"$key: $extra\n")
          CncfRuntime.bootstrapC(manifest.getParent,
            _bootstrap_arguments(manifest) ++ Array(s"--textus.config.file=$config"), Map.empty)
        }
        Then("all unrepresented configured activation sources reject before component construction")
        results.foreach(_.isFaillure shouldBe true)
      }
    }

    "reject explicit and configured factories or class discovery while preserving unbound bootstrap" in {
      Given("docs/spec/installed-runtime-distribution.md R4,R6 E6 and unsupported factory and discovery activation")
      _with_fixture { manifest =>
        val args = _bootstrap_arguments(manifest)
        val extras = Vector("--component-factory-class=org.example.ExternalFactory", "--discover=classes",
          s"--${RuntimeConfig.componentFactoryClassKey}=org.example.ExternalFactory",
          s"--${RuntimeConfig.discoverClassesKey}=true")
        When("ordinary bootstrap admits each extra with and without the explicit installed binding")
        val bound = extras.map(extra => CncfRuntime.bootstrapC(manifest.getParent, args :+ extra, Map.empty))
        val unboundargs = args.filterNot(_.startsWith(s"--${RuntimeConfig.installedRuntimeManifestKey}="))
        val unbound = extras.map(extra => CncfRuntime.bootstrapC(manifest.getParent, unboundargs :+ extra, Map.empty))
        Then("the installed profile rejects unsupported activation and ordinary unbound bootstrap retains its behavior")
        bound.foreach(_.isFaillure shouldBe true)
        unbound.foreach(_.isSuccess shouldBe true)
      }
    }

    "require the installed CAR and suppress effective external defaults through the existing policy" in {
      Given("docs/spec/installed-runtime-distribution.md R4 E6 and isolated test-home repository inheritance controls")
      _with_fixture { manifest =>
        val args = _bootstrap_arguments(manifest)
        val missing = args.filterNot(_.startsWith("--component-file="))
        val isolated = args.filterNot(_ == "--no-default-components")
        val inherited = isolated.map {
          case "--textus.test.home.inherit.repositories=false" => "--textus.test.home.inherit.repositories=true"
          case value => value
        }
        When("ordinary bootstrap evaluates missing activation, suppressed inheritance and enabled external defaults")
        val absent = CncfRuntime.bootstrapC(manifest.getParent, missing, Map.empty)
        val suppressed = CncfRuntime.bootstrapC(manifest.getParent, isolated, Map.empty)
        val external = CncfRuntime.bootstrapC(manifest.getParent, inherited, Map.empty)
        Then("only the profile with its pinned CAR and suppressed effective external defaults succeeds")
        absent.isFaillure shouldBe true
        suppressed.isSuccess shouldBe true
        external.isFaillure shouldBe true
      }
    }
  }

  private def _bootstrap_arguments(manifest: Path): Array[String] = Array(
    "--no-default-components", s"--textus.test.home.path=${manifest.getParent.resolve("home")}",
    "--textus.test.home.inherit.runtime=false", "--textus.test.home.inherit.repositories=false",
    "--textus.test.home.inherit.local-data=false",
    s"--${RuntimeConfig.installedRuntimeManifestKey}=$manifest",
    s"--component-file=${manifest.getParent.resolve("components/textus-sm-workflow.car")}", "--help")

  private def _with_fixture(body: Path => Unit): Unit = {
    val base = Files.createDirectories(Paths.get("target").toAbsolutePath)
    val root = Files.createTempDirectory(base, "installed-runtime-spec-")
    try body(InstalledRuntimeDistributionSpec.writeFixture(root))
    finally Using.resource(Files.walk(root)) { stream =>
      stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
    }
  }
}

object InstalledRuntimeDistributionSpec {
  def writeFixture(root: Path): Path = {
    val installation = UUID.randomUUID()
    val directory = Files.createDirectories(root.resolve(installation.toString))
    Files.createDirectories(directory.resolve("runtime"))
    Files.createDirectories(directory.resolve("components"))
    Vector("runtime/0000.jar", "runtime/0001.jar", "components/textus-sm-workflow.car").foreach { relative =>
      writeArchive(directory.resolve(relative), Vector("marker.txt" -> relative))
    }
    def _module_(coordinate: String, relative: String): Json = Json.obj(
      "coordinate" -> Json.fromString(coordinate), "path" -> Json.fromString(relative),
      "source" -> Json.fromString(root.resolve("unavailable-source.jar").toString))
    val json = Json.obj(
      "kind" -> Json.fromString(InstalledRuntimeDistribution.KIND),
      "installation_id" -> Json.fromString(installation.toString),
      "update_id" -> Json.fromString(UUID.randomUUID().toString),
      "application" -> Json.fromString(InstalledRuntimeDistribution.APPLICATION),
      "root_coordinate" -> Json.fromString(InstalledRuntimeDistribution.ROOT_COORDINATE),
      "repositories" -> Json.arr(Json.fromString("ivy2Local"), Json.fromString("central")),
      "modules" -> Json.arr(_module_(InstalledRuntimeDistribution.ROOT_COORDINATE, "runtime/0000.jar"),
        _module_("org.example:resource:jar:Tests:1.0-SNAPSHOT", "runtime/0001.jar")),
      "component" -> _module_(InstalledRuntimeDistribution.COMPONENT_COORDINATE, "components/textus-sm-workflow.car")
        .deepMerge(Json.obj("identity" -> Json.fromString(InstalledRuntimeDistribution.COMPONENT_IDENTITY)))
    )
    Files.writeString(directory.resolve("runtime-installation.json"), json.noSpaces)
  }

  def parameters(manifest: Path): ResolvedParameters = ResolvedParameters.fromResolvedConfiguration(
    ResolvedConfiguration(Configuration(Map(RuntimeConfig.installedRuntimeManifestKey ->
      ConfigurationValue.StringValue(manifest.toString))), ConfigurationTrace.empty))

  def writeArchive(path: Path, entries: Vector[(String, String)]): Unit =
    Using.resource(new ZipOutputStream(Files.newOutputStream(path))) { output =>
      entries.foreach { case (name, content) =>
        output.putNextEntry(new ZipEntry(name))
        output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        output.closeEntry()
      }
    }
}
