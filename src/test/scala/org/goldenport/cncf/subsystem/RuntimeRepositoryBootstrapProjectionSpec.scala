package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.zip.ZipOutputStream

import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*

import org.goldenport.cncf.config.{RepositoryBootstrapPolicy, RuntimeConfig}
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class RuntimeRepositoryBootstrapProjectionSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with GivenWhenThen {
  private val _e1 = afterWord("in spec:phase-55-runtime-repository-bootstrap-projection, example:E1, rules:GCF09F-C1,C2,C3, phase:55, slice:GCF-09F")
  private val _fixture_roots = ArrayBuffer.empty[Path]

  "Runtime repository bootstrap projection" should {
    "E1 require an admitted policy and project its repository sources without reparsing raw configuration" must _e1 {
      "when descriptor-producing field families are admitted" in {
        Given("minimal repository, CAR, subsystem, and component-development fixtures")
        val root = _fixture_root("gcf09f-runtime-bootstrap-")
        val repository = _repository_fixture(root, "policy-repository")
        val development = _development_fixture(root.resolve("policy-development"), "policy-development")
        val cardirectory = _component_car_directory(root.resolve("policy-car-directory"), "policy-car-directory")
        val carfile = _component_car_file(root.resolve("policy-car-file.car"), "policy-car-file")
        val subsystem = _subsystem_fixture(root.resolve("policy-subsystem"), "policy-subsystem")
        val cases = Vector(
          ("repositoryDirs", RuntimeConfig.repositoryDirKey, repository, RepositoryBootstrapPolicy(repositoryDirs = Vector(repository.toString)), "policy-repository"),
          ("componentDevDirs", RuntimeConfig.componentDevDirKey, development, RepositoryBootstrapPolicy(componentDevDirs = Vector(development.toString)), "policy-development"),
          ("componentCarDirs", RuntimeConfig.componentCarDirKey, cardirectory, RepositoryBootstrapPolicy(componentCarDirs = Vector(cardirectory.toString)), "policy-car-directory"),
          ("componentFiles", RuntimeConfig.componentFileKey, carfile, RepositoryBootstrapPolicy(componentFiles = Vector(carfile.toString)), "policy-car-file"),
          ("subsystemDevDirs", RuntimeConfig.subsystemDevDirKey, subsystem, RepositoryBootstrapPolicy(subsystemDevDirs = Vector(subsystem.toString)), "policy-subsystem"),
          ("subsystemSarDirs", RuntimeConfig.subsystemSarDirKey, subsystem, RepositoryBootstrapPolicy(subsystemSarDirs = Vector(subsystem.toString)), "policy-subsystem")
        )

        When("runtime descriptor resolution receives raw values and an independently admitted policy")
        val results = cases.map { case (family, key, path, policy, expected) =>
          val raw = root.resolve(s"raw-$family").toString
          val configuration = _configuration(
            Map(key -> raw) ++
              (if (family == "repositoryDirs") Map(RuntimeConfig.subsystemNameKey -> expected) else Map.empty)
          )
          val admitted = GenericSubsystemFactory.runtimeResolveDescriptorC(configuration, Some(policy))
          val admittedempty = GenericSubsystemFactory.runtimeResolveDescriptorC(configuration, Some(RepositoryBootstrapPolicy()))
          (family, admitted, admittedempty, expected)
        }

        Then("every non-empty policy source is consumed while admitted emptiness blocks the corresponding raw source")
        results.foreach { case (family, admitted, admittedempty, expected) =>
          withClue(s"$family: ") {
            admitted.toOption.flatten.map(_.subsystemName) shouldBe Some(expected)
            admittedempty.toOption.flatten shouldBe None
          }
        }
      }

      "when repository search and activation fields are admitted" in {
        Given("a repository-backed subsystem and conflicting raw/admitted component-development paths")
        val root = _fixture_root("gcf09f-runtime-repository-")
        val repository = _repository_fixture(root, "policy-component-dir")
        val rawcomponentdirectory = root.resolve("raw-component-directory")
        val rawdevelopment = _development_fixture(root.resolve("raw-development"), "rawdev")
        val rejecteddevelopment = root.resolve("rejected-development")
        val context = _runtime_context
        val componentdirconfiguration = _configuration(Map(
          RuntimeConfig.subsystemNameKey -> "policy-component-dir",
          RuntimeConfig.componentDirKey -> rawcomponentdirectory.toString
        ))
        val developmentconfiguration = _configuration(Map(
          RuntimeConfig.subsystemNameKey -> "devdirsample",
          RuntimeConfig.repositoryComponentDevDirKey -> rawdevelopment.toString
        ))

        When("the generic runtime factory is called with admitted and empty policies")
        val componentdiradmitted = GenericSubsystemFactory.runtimeDefaultWithScopeC(
          "policy-component-dir", context, Some(RunMode.Server), componentdirconfiguration, AliasResolver.empty,
          Some(RepositoryBootstrapPolicy(componentDirs = Vector(repository.toString)))
        )
        val componentdirempty = GenericSubsystemFactory.runtimeDefaultWithScopeC(
          "policy-component-dir", context, Some(RunMode.Server), componentdirconfiguration, AliasResolver.empty,
          Some(RepositoryBootstrapPolicy())
        )
        val developmentadmitted = GenericSubsystemFactory.runtimeDefaultWithScopeC(
          "devdirsample", context, Some(RunMode.Server), developmentconfiguration, AliasResolver.empty,
          Some(RepositoryBootstrapPolicy(repositoryComponentDevDirs = Vector(rejecteddevelopment.toString)))
        )
        val developmentempty = GenericSubsystemFactory.runtimeDefaultWithScopeC(
          "devdirsample", context, Some(RunMode.Server), developmentconfiguration, AliasResolver.empty,
          Some(RepositoryBootstrapPolicy())
        )

        Then("componentDirs selects its admitted repository and repositoryComponentDevDirs rejects its admitted invalid path")
        componentdiradmitted.toOption.flatMap(_.descriptor).map(_.subsystemName) shouldBe Some("policy-component-dir")
        componentdirempty.toOption.flatMap(_.descriptor) shouldBe None
        developmentadmitted.isSuccess shouldBe false
        developmentempty.isSuccess shouldBe true
      }

      "when component development and assembly descriptor sources are both admitted" in {
        Given("a prepared development Component and an assembly-owned standalone security binding")
        val root = _fixture_root("gcf09f-runtime-development-assembly-")
        val development = _development_fixture(root.resolve("development"), "development-assembly")
        Files.writeString(
          development.resolve("src/main/car/assembly-descriptor.yaml"),
          """subsystem: development-assembly
            |subsystemCapabilities:
            |  providers:
            |    - name: runtime-facilities
            |      component: development-assembly
            |      provides:
            |        - datastore.persistent@1
            |components:
            |  - name: development-assembly
            |    version: 1.0.0
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        val assembly = root.resolve("assembly.yaml")
        Files.writeString(
          assembly,
          """subsystem: development-assembly
            |components:
            |  - name: development-assembly
            |    version: 1.0.0
            |config:
            |  textus.subsystem.user-mode: standalone
            |security:
            |  authentication:
            |    local_subject:
            |      id: standalone-local
            |      capabilities:
            |        - user
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        val configuration = _configuration(Map(
          RuntimeConfig.assemblyDescriptorKey -> assembly.toString
        ))
        val policy = RepositoryBootstrapPolicy(componentDevDirs = Vector(development.toString))
        val assemblydrivenpolicy = RepositoryBootstrapPolicy(
          repositoryComponentDevDirs = Vector(development.toString)
        )

        When("runtime descriptor resolution starts from the admitted development directory")
        val resolved = GenericSubsystemFactory.runtimeResolveDescriptorC(configuration, Some(policy))
        val assemblydriven = GenericSubsystemFactory.runtimeResolveDescriptorC(
          configuration,
          Some(assemblydrivenpolicy)
        )
        val constructed = DefaultSubsystemFactory.runtimeDefaultWithScopeC(
          _runtime_context,
          Some(RunMode.Server),
          configuration,
          AliasResolver.empty,
          Some(policy)
        )
        val assemblydrivenconstructed = DefaultSubsystemFactory.runtimeDefaultWithScopeC(
          _runtime_context,
          Some(RunMode.Server),
          configuration,
          AliasResolver.empty,
          Some(assemblydrivenpolicy)
        )

        Then("the resolved descriptor and constructed Subsystem retain the assembly-owned local subject")
        resolved shouldBe a[org.goldenport.Consequence.Success[_]]
        val descriptor = resolved.toOption.flatten.getOrElse(fail("runtime descriptor"))
        descriptor.assemblyDescriptor.flatMap(_.path).map(_.toAbsolutePath.normalize) shouldBe
          Some(assembly.toAbsolutePath.normalize)
        descriptor.security.flatMap(_.authentication).flatMap(_.localSubject).map(_.id) shouldBe Some("standalone-local")
        assemblydriven.toOption.flatten
          .flatMap(_.security)
          .flatMap(_.authentication)
          .flatMap(_.localSubject)
          .map(_.id) shouldBe Some("standalone-local")
        constructed shouldBe a[org.goldenport.Consequence.Success[_]]
        constructed.toOption.flatMap(_.resolvedSecurityWiring.authentication.localSubject).map(_.id) shouldBe
          Some("standalone-local")
        assemblydrivenconstructed shouldBe a[org.goldenport.Consequence.Success[_]]
        val assemblydrivendescriptor = assemblydrivenconstructed.toOption.flatMap(_.descriptor).getOrElse(fail("assembly-driven runtime descriptor"))
        assemblydrivendescriptor.security.flatMap(_.authentication).flatMap(_.localSubject).map(_.id) shouldBe
          Some("standalone-local")
        assemblydrivendescriptor.subsystemCapabilityProviders.map(_.name) should contain ("runtime-facilities")
      }

      "when a policy repository path is relative to the bootstrap directory" in {
        Given("a repository under an explicit bootstrap directory and a conflicting raw path")
        val root = _fixture_root("gcf09f-runtime-relative-")
        val repository = _repository_fixture(root, "policy-relative")
        val policy = RepositoryBootstrapPolicy(
          repositoryDirs = Vector(root.relativize(repository).toString),
          baseDirectory = root
        )
        val configuration = _configuration(Map(
          RuntimeConfig.subsystemNameKey -> "policy-relative",
          RuntimeConfig.repositoryDirKey -> root.resolve("raw-repository").toString
        ))

        When("runtime descriptor resolution uses the admitted policy")
        val resolved = GenericSubsystemFactory.runtimeResolveDescriptorC(configuration, Some(policy))

        Then("the relative policy path is resolved from the bootstrap directory")
        resolved.toOption.flatten.map(_.subsystemName) shouldBe Some("policy-relative")
      }

      "when a policy component archive directory is relative to the bootstrap directory" in {
        Given("a component archive directory under the explicit bootstrap directory")
        val root = _fixture_root("gcf09f-runtime-relative-component-")
        val componentdirectory = _component_car_directory(root.resolve("component-car"), "policy-relative-component")
        val policy = RepositoryBootstrapPolicy(
          componentCarDirs = Vector(root.relativize(componentdirectory).toString),
          baseDirectory = root
        )

        When("runtime descriptor resolution uses the relative admitted component path")
        val resolved = GenericSubsystemFactory.runtimeResolveDescriptorC(_configuration(Map.empty), Some(policy))

        Then("the component archive directory is resolved from the bootstrap directory")
        resolved.toOption.flatten.map(_.subsystemName) shouldBe Some("policy-relative-component")
      }

      "when a packaged CAR supplies defaults to a SAR descriptor through repository admission" in {
        Given("a packaged CAR default assembly and a SAR assembly that overrides one default field")
        val root = _fixture_root("gcf09f-packaged-defaults-")
        val componentname = "packaged-defaults"
        val repository = Files.createDirectories(root.resolve("repository")).toAbsolutePath.normalize
        val unrequestedcar = repository.resolve(s"$componentname-1.0.0.car")
        _write_archive(
          unrequestedcar,
          Map(
            "component-descriptor.json" -> s"""{"name":"$componentname","version":"1.0.0","component":"$componentname"}""",
            "assembly-descriptor.yaml" ->
              s"""subsystem: $componentname
                 |components:
                 |  - name: $componentname
                 |    version: 1.0.0
                 |config:
                 |  component.default: wrong-version
                 |  wrong.version: selected
                 |""".stripMargin
          )
        )
        val car = repository.resolve(s"$componentname-2.0.0.car")
        _write_archive(
          car,
          Map(
            "component-descriptor.json" -> s"""{"name":"$componentname","version":"2.0.0","component":"$componentname"}""",
            "assembly-descriptor.yaml" ->
              s"""subsystem: $componentname
                 |components:
                 |  - name: $componentname
                 |    version: 2.0.0
                 |config:
                 |  component.default: retained
                 |  shared.value: component
                 |""".stripMargin
          )
        )
        val sar = root.resolve(s"$componentname.sar")
        _write_archive(
          sar,
          Map(
            "subsystem-descriptor.yaml" ->
              s"""subsystem: $componentname
                 |components:
                 |  - name: $componentname
                 |    version: 2.0.0
                 |""".stripMargin,
            "assembly-descriptor.yaml" ->
              s"""subsystem: $componentname
                 |config:
                 |  shared.value: sar
                 |  sar.override: retained
                 |""".stripMargin
          )
        )
        val descriptor = GenericSubsystemDescriptor.load(sar).getOrElse(fail("SAR descriptor"))
        val policy = RepositoryBootstrapPolicy(repositoryDirs = Vector(repository.toString), baseDirectory = root)
        val specification = ComponentRepository.ComponentDirRepository.Specification(repository)

        When("the packaged repository resolves component-owned CAR defaults before factory admission")
        val defaultsresult = specification.resolveComponentSubsystemDefaultsC(componentname, Some("2.0.0"))

        Then("the repository boundary returns the retained component default")
        defaultsresult shouldBe a[org.goldenport.Consequence.Success[_]]
        val defaults = defaultsresult.toOption.flatten.getOrElse(fail("packaged CAR defaults are required"))
        defaults.config should contain ("component.default" -> "retained")

        When("the runtime factory admits the SAR descriptor against the packaged repository")
        val result = GenericSubsystemFactory.runtimeAdmitDescriptorC(
          descriptor,
          _configuration(Map.empty),
          Some(policy)
        )

        Then("CAR defaults are retained while SAR assembly values override the same fields")
        result shouldBe a[org.goldenport.Consequence.Success[_]]
        val effective = result.getOrElse(fail("effective descriptor"))
        effective.config should contain ("component.default" -> "retained")
        effective.config should contain ("sar.override" -> "retained")
        effective.config should contain ("shared.value" -> "sar")
        effective.config should not contain key("wrong.version")
      }

      "when a matching packaged CAR contains a malformed assembly default" in {
        Given("a repository CAR with a valid component descriptor and malformed assembly descriptor")
        val root = _fixture_root("gcf09f-malformed-packaged-default-")
        val repository = Files.createDirectories(root.resolve("repository"))
        val componentname = "malformed-packaged-default"
        _write_archive(
          repository.resolve(s"$componentname.car"),
          Map(
            "component-descriptor.json" -> _component_descriptor(componentname),
            "assembly-descriptor.yaml" -> "subsystem: [unterminated"
          )
        )
        val specification = ComponentRepository.ComponentDirRepository.Specification(repository)

        When("pre-activation default resolution selects the matching CAR")
        val result = specification.resolveComponentSubsystemDefaultsC(componentname)

        Then("the malformed selected default remains a structured failure")
        result shouldBe a[org.goldenport.Consequence.Failure[_]]
      }

      "when an expanded CAR directory supplies defaults to a versioned SAR descriptor" in {
        Given("a canonical expanded CAR directory and a SAR assembly override")
        val root = _fixture_root("gcf09f-expanded-defaults-")
        val componentname = "expanded-defaults"
        val repository = Files.createDirectories(root.resolve("repository")).toAbsolutePath.normalize
        _write_archive(repository.resolve("unrelated-malformed.car"), "component-descriptor.json", "{ invalid")
        val cardir = Files.createDirectories(repository.resolve(s"$componentname-3.0.0"))
        Files.writeString(
          cardir.resolve("component-descriptor.json"),
          s"""{"name":"$componentname","version":"3.0.0","component":"$componentname"}""",
          StandardCharsets.UTF_8
        )
        Files.writeString(
          cardir.resolve("assembly-descriptor.yaml"),
          s"""subsystem: $componentname
             |components:
             |  - name: $componentname
             |    version: 3.0.0
             |config:
             |  directory.default: retained
             |  shared.value: directory
             |""".stripMargin,
          StandardCharsets.UTF_8
        )
        _write_empty_jar(cardir.resolve("component").resolve("main.jar"))
        val sar = root.resolve(s"$componentname.sar")
        _write_archive(
          sar,
          Map(
            "subsystem-descriptor.yaml" ->
              s"""subsystem: $componentname
                 |components:
                 |  - name: $componentname
                 |    version: 3.0.0
                 |""".stripMargin,
            "assembly-descriptor.yaml" ->
              s"""subsystem: $componentname
                 |config:
                 |  shared.value: sar
                 |  sar.override: retained
                 |""".stripMargin
          )
        )
        val descriptor = GenericSubsystemDescriptor.load(sar).getOrElse(fail("SAR descriptor"))
        val policy = RepositoryBootstrapPolicy(repositoryDirs = Vector(repository.toString), baseDirectory = root)

        When("runtime descriptor admission resolves the expanded CAR defaults")
        val result = GenericSubsystemFactory.runtimeAdmitDescriptorC(
          descriptor,
          _configuration(Map.empty),
          Some(policy)
        )

        Then("directory-owned defaults are retained while SAR values retain precedence")
        result shouldBe a[org.goldenport.Consequence.Success[_]]
        val effective = result.getOrElse(fail("effective descriptor"))
        effective.config should contain ("directory.default" -> "retained")
        effective.config should contain ("sar.override" -> "retained")
        effective.config should contain ("shared.value" -> "sar")
      }

      "when a versioned expanded CAR default request finds an unversioned descriptor" in {
        Given("a classified expanded CAR directory whose name matches but descriptor omits the requested version")
        val root = _fixture_root("gcf09f-unversioned-expanded-default-")
        val componentname = "unversioned-expanded-default"
        val requestedversion = "4.0.0"
        val repository = Files.createDirectories(root.resolve("repository")).toAbsolutePath.normalize
        val cardir = Files.createDirectories(repository.resolve(s"$componentname-$requestedversion"))
        Files.writeString(
          cardir.resolve("component-descriptor.json"),
          s"""{"name":"$componentname","component":"$componentname"}""",
          StandardCharsets.UTF_8
        )
        Files.writeString(
          cardir.resolve("assembly-descriptor.yaml"),
          s"""subsystem: $componentname
             |components:
             |  - name: $componentname
             |config:
             |  wrong.default: unversioned
             |""".stripMargin,
          StandardCharsets.UTF_8
        )
        _write_empty_jar(cardir.resolve("component").resolve("main.jar"))
        val specification = ComponentRepository.ComponentDirRepository.Specification(repository)

        When("version-aware default resolution evaluates the named expanded candidate")
        val result = specification.resolveComponentSubsystemDefaultsC(componentname, Some(requestedversion))

        Then("the unversioned descriptor remains a successful nonmatch")
        result shouldBe a[org.goldenport.Consequence.Success[_]]
        result.toOption.flatten shouldBe None
      }

      "when an explicitly selected component-file CAR has an invalid component descriptor" in {
        Given("a configured CAR file whose component descriptor cannot be decoded")
        val root = _fixture_root("gcf09f-invalid-component-file-default-")
        val car = root.resolve("invalid-component-file.car")
        _write_archive(car, "component-descriptor.json", "{ invalid")
        val specification = ComponentRepository.ComponentFileRepository.Specification(car)

        When("version-aware pre-activation default resolution selects the configured file")
        val result = specification.resolveComponentSubsystemDefaultsC("invalid-component-file", Some("1.0.0"))

        Then("the invalid selected component descriptor remains a structured failure")
        result shouldBe a[org.goldenport.Consequence.Failure[_]]
      }

      "when an explicitly configured assembly descriptor is missing or malformed" in {
        Given("one missing configured path and one malformed configured descriptor")
        val root = _fixture_root("gcf09f-explicit-assembly-")
        val missing = root.resolve("missing-assembly.yaml")
        val malformed = root.resolve("malformed-assembly.yaml")
        Files.writeString(malformed, "subsystem: [unterminated", StandardCharsets.UTF_8)
        val policy = RepositoryBootstrapPolicy(baseDirectory = root)
        val configurations = Vector(missing, malformed).map { path =>
          _configuration(Map(RuntimeConfig.assemblyDescriptorKey -> path.toString))
        }

        When("runtime descriptor resolution reaches the configured assembly boundary")
        val results = configurations.map(GenericSubsystemFactory.runtimeResolveDescriptorC(_, Some(policy)))

        Then("each explicit source failure remains structural rather than becoming descriptor absence")
        results.foreach(_ shouldBe a[org.goldenport.Consequence.Failure[_]])
      }

      "when repository parameters use a distinct caller working directory" in {
        Given("a relative admitted component directory and different bootstrap/default directories")
        val root = _fixture_root("gcf09f-runtime-parameters-")
        val componentdirectory = _component_car_directory(root.resolve("component-car"), "policy-parameters")
        val policy = RepositoryBootstrapPolicy(
          componentDirs = Vector(root.relativize(componentdirectory).toString),
          baseDirectory = root
        )

        When("runtime repository parameters are constructed")
        val parameters = CncfRuntime.repositoryParameters(
          policy,
          _configuration(Map.empty),
          Array.empty,
          _fixture_root("gcf09f-runtime-default-")
        )

        Then("admitted relative paths use the policy base while defaults retain the caller directory")
        parameters.activeRepositories.toOption.get should contain (
          ComponentRepository.ComponentDirRepository.Specification(componentdirectory)
        )
      }

      "when the textus-identity runtime factory receives an admitted invalid policy" in {
        Given("a raw repository value and a conflicting invalid admitted development repository")
        val root = _fixture_root("gcf09f-runtime-identity-")
        val configuration = _configuration(Map(
          RuntimeConfig.repositoryDirKey -> root.resolve("raw-repository").toString
        ))
        val policy = RepositoryBootstrapPolicy(
          repositoryComponentDevDirs = Vector(root.resolve("rejected-development").toString),
          baseDirectory = root
        )

        When("the policy-aware identity runtime factory is invoked")
        val result = TextusIdentitySubsystemFactory.runtimeDefaultWithScopeC(
          _runtime_context,
          Some(RunMode.Server),
          configuration,
          AliasResolver.empty,
          Some(policy)
        )

        Then("the raw repository is not revived and the admitted policy failure is structured")
        result.isSuccess shouldBe false
      }

      "when any runtime factory boundary lacks the policy" in {
        Given("a runtime scope, a descriptor, and an otherwise empty configuration")
        val context = _runtime_context
        val configuration = _configuration(Map.empty)
        val descriptor = GenericSubsystemDescriptor(Path.of("runtime-descriptor"), "runtime-descriptor")

        When("each runtime-only resolution or construction overload is called without admission")
        val results = Vector(
          "descriptor resolution" -> GenericSubsystemFactory.runtimeResolveDescriptorC(configuration, None).isSuccess,
          "generic named factory" -> GenericSubsystemFactory.runtimeDefaultWithScopeC("runtime-descriptor", context, Some(RunMode.Server), configuration, AliasResolver.empty, None).isSuccess,
          "generic descriptor factory" -> GenericSubsystemFactory.runtimeDefaultWithScopeC(descriptor, context, Some(RunMode.Server), configuration, AliasResolver.empty, None).isSuccess,
          "default factory" -> DefaultSubsystemFactory.runtimeDefaultWithScopeC(context, Some(RunMode.Server), configuration, AliasResolver.empty, None).isSuccess
        )

        Then("all entry points return structured configuration failures before construction")
        results.foreach { case (boundary, result) =>
          withClue(s"$boundary: ") {
            result shouldBe false
          }
        }
      }
    }
  }

  override def beforeAll(): Unit = {
    GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))
  }

  override def afterAll(): Unit =
    try {
      _fixture_roots.foreach(_delete_tree)
    } finally {
      super.afterAll()
    }

  private def _runtime_context: ScopeContext =
    ScopeContext(ScopeKind.Runtime, "runtime", None, ExecutionContext.create().observability)

  private def _configuration(values: Map[String, String]): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.view.mapValues(ConfigurationValue.StringValue.apply).toMap),
      ConfigurationTrace.empty
    )

  private def _fixture_root(prefix: String): Path = {
    val root = Files.createTempDirectory(Path.of("target"), prefix)
    _fixture_roots += root
    root
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists(_))
      } finally {
        stream.close()
      }
    }

  private def _repository_fixture(root: Path, subsystemname: String): Path = {
    val repository = Files.createDirectories(root.resolve(s"$subsystemname-repository"))
    _write_archive(repository.resolve(s"$subsystemname.sar"), "subsystem-descriptor.yaml", _subsystem_descriptor(subsystemname))
    repository
  }

  private def _subsystem_fixture(path: Path, subsystemname: String): Path = {
    Files.createDirectories(path)
    Files.writeString(path.resolve("descriptor.yaml"), _subsystem_descriptor(subsystemname), StandardCharsets.UTF_8)
    path
  }

  private def _component_car_directory(path: Path, componentname: String): Path = {
    Files.createDirectories(path)
    Files.writeString(path.resolve("component-descriptor.json"), _component_descriptor(componentname), StandardCharsets.UTF_8)
    path
  }

  private def _component_car_file(path: Path, componentname: String): Path = {
    _write_archive(path, "component-descriptor.json", _component_descriptor(componentname))
    path
  }

  private def _development_fixture(path: Path, componentname: String): Path = {
    val classdir = Files.createDirectories(path.resolve("target").resolve("classes"))
    val cncfdir = Files.createDirectories(path.resolve("target").resolve("cncf.d"))
    val cardir = Files.createDirectories(path.resolve("src").resolve("main").resolve("car"))
    val classpath = cncfdir.resolve("runtime-classpath.txt")
    val descriptor = cardir.resolve("component-descriptor.json")
    val abi = cardir.resolve("abi-manifest.json")
    Files.writeString(classpath, classdir.toString, StandardCharsets.UTF_8)
    Files.writeString(descriptor, _component_descriptor(componentname), StandardCharsets.UTF_8)
    Files.writeString(abi, s"""{"format":"cozy.car.abi-manifest.v1","car":{"name":"$componentname","version":"1.0.0"},"abi":{"exports":{"components":[{"name":"$componentname"}]}}}""", StandardCharsets.UTF_8)
    val evidence = Vector(
      ("target/cncf.d/runtime-classpath.txt", _sha256(classpath), Some(_sha256(s"project:target/classes".getBytes(StandardCharsets.UTF_8)))),
      ("src/main/car/component-descriptor.json", _sha256(descriptor), None),
      ("src/main/car/abi-manifest.json", _sha256(abi), None)
    )
    val entries = evidence.map { case (identity, digest, logical) =>
      val logicalfield = logical.map(value => s"\"logicalSha256\":\"$value\",").getOrElse("")
      s"{$logicalfield\"path\":\"$identity\",\"sha256\":\"$digest\"}"
    }.mkString("[", ",", "]")
    val integrity = _sha256(evidence.map { case (identity, digest, logical) =>
      s"$identity\t$digest\t${logical.getOrElse("")}"
    }.mkString("\n").getBytes(StandardCharsets.UTF_8))
    Files.writeString(
      cncfdir.resolve("car-runtime-manifest.json"),
      s"""{"schemaVersion":"cncf.car-development-runtime-manifest.v1","sourceKind":"development-directory","car":{"name":"$componentname","version":"1.0.0","component":"$componentname"},"runtime":{"cncf":{"minimum":"0.0.0","excluded":[],"tested":["0.0.0"]}},"evidence":$entries,"integrity":{"algorithm":"SHA-256","evidenceSha256":"$integrity"}}""",
      StandardCharsets.UTF_8
    )
    path
  }

  private def _subsystem_descriptor(subsystemname: String): String =
    s"subsystem: $subsystemname\ncomponents:\n  - name: $subsystemname\n"

  private def _component_descriptor(componentname: String): String =
    s"""{"name":"$componentname","version":"1.0.0","component":"$componentname"}"""

  private def _write_archive(path: Path, entry: String, contents: String): Unit = {
    _write_archive(path, Map(entry -> contents))
  }

  private def _write_archive(path: Path, entries: Map[String, String]): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val output = new ZipOutputStream(Files.newOutputStream(path))
    try {
      entries.foreach { case (entry, contents) =>
        output.putNextEntry(new java.util.zip.ZipEntry(entry))
        output.write(contents.getBytes(StandardCharsets.UTF_8))
        output.closeEntry()
      }
    } finally output.close()
  }

  private def _write_empty_jar(path: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val output = new ZipOutputStream(Files.newOutputStream(path))
    try {
      output.putNextEntry(new java.util.zip.ZipEntry(
        "org/goldenport/cncf/component/builtin/specification/SpecificationComponent$Factory.class"
      ))
      output.closeEntry()
    } finally output.close()
  }

  private def _sha256(path: Path): String = _sha256(Files.readAllBytes(path))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString
}
