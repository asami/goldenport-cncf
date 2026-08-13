package org.goldenport.cncf.cli

import java.time.Instant
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import java.util.zip.{ZipEntry, ZipOutputStream}
import org.goldenport.{Consequence, ConsequenceException}
import org.goldenport.cncf.config.{RuntimeConfig, RuntimeTestDescriptor, StandaloneUserProfile, StandaloneUserProfileResolver}
import org.goldenport.cncf.component.{ComponentDescriptor, ComponentId}
import org.goldenport.cncf.component.identity.ComponentReleaseCoordinate
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemDescriptor, GenericSubsystemFactory, GenericSubsystemLocalSubjectBinding, GenericSubsystemSecurityBinding, Subsystem, SubsystemUserMode}
import org.goldenport.configuration.{Configuration, ConfigurationOrigin, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 15, 2026
 *  version Apr. 25, 2026
 *  version Jul. 31, 2026
 *  version Aug.  6, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfRuntimeConfigFileSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CncfRuntime" should {
    "assembly and development repository resolution" which {
    "apply assembly descriptor config while preserving test and command-line precedence" in {
      Given("an assembly descriptor with runtime config and a test descriptor override")
      val cwd = Files.createTempDirectory("cncf-assembly-runtime-config")
      val assemblydescriptor = cwd.resolve("assembly.yaml")
      val testdescriptor = cwd.resolve("test.yaml")
      Files.writeString(
        assemblydescriptor,
        """subsystem: config-target
          |components: []
          |config:
          |  textus.artscene.application.mode: multi_user
          |  textus.component.art-scene.datastores.application.policy: external-required
          |""".stripMargin
      )
      Files.writeString(
        testdescriptor,
        """kind: test-descriptor
          |config:
          |  textus.artscene.application.mode: test_multi_user
          |""".stripMargin
      )

      When("the assembly is bootstrapped without and with higher-precedence overrides")
      val assemblyonly = CncfRuntime.bootstrap(
        cwd,
        Array(s"--textus.assembly.descriptor=${assemblydescriptor}", "command")
      )
      val testoverride = CncfRuntime.bootstrap(
        cwd,
        Array(
          s"--textus.assembly.descriptor=${assemblydescriptor}",
          s"--textus.test.descriptor=${testdescriptor}",
          "command"
        )
      )
      val clioverride = CncfRuntime.bootstrap(
        cwd,
        Array(
          s"--textus.assembly.descriptor=${assemblydescriptor}",
          s"--textus.test.descriptor=${testdescriptor}",
          "--textus.artscene.application.mode=standalone",
          "command"
        )
      )

      Then("assembly config supplies defaults and explicit test or CLI config wins")
      RuntimeConfig.getString(assemblyonly.configuration, "textus.artscene.application.mode") shouldBe Some("multi_user")
      RuntimeConfig.getString(
        assemblyonly.configuration,
        "textus.component.art-scene.datastores.application.policy"
      ) shouldBe Some("external-required")
      RuntimeConfig.getString(testoverride.configuration, "textus.artscene.application.mode") shouldBe Some("test_multi_user")
      RuntimeConfig.getString(clioverride.configuration, "textus.artscene.application.mode") shouldBe Some("standalone")
      assemblyonly.configuration.trace
        .get("textus.artscene.application.mode")
        .flatMap(_.sourceType) shouldBe Some("assembly-descriptor")
    }

    "retain assembly Web execution config across the launcher subsystem handoff" in {
      Given("an assembly default consumed after launcher repository resolution")
      val cwd = Files.createTempDirectory("cncf-assembly-web-execution-config")
      val assemblydescriptor = cwd.resolve("assembly.yaml")
      val testdescriptor = cwd.resolve("test.yaml")
      Files.writeString(
        assemblydescriptor,
        """subsystem: config-target
          |components: []
          |config:
          |  textus.web.execution.display-override.enabled: true
          |  textus.web.execution.sample-count: 8
          |  textus.web.execution.sample-ratio: 1.5
          |""".stripMargin
      )
      Files.writeString(testdescriptor, "kind: test-descriptor\n")
      val args = Array(
        s"--textus.assembly.descriptor=${assemblydescriptor}",
        s"--textus.test.descriptor=${testdescriptor}",
        "server"
      )

      When("the launcher resolves the assembly descriptor and repository invocation")
      val bootstrap = CncfRuntime.bootstrap(cwd, args)
      val active = bootstrap.repositories.activeRepositories.toOption.get
      val search = bootstrap.repositories.searchRepositories.toOption.get
      val invocation = CncfRuntime.resolveSubsystemInvocation(bootstrap.invocation, search, active)
      val extras = CncfRuntime.componentExtraFunction(active, bootstrap.front)

      Then("boolean and numeric YAML scalars remain available as runtime configuration strings")
      RuntimeConfig.getString(
        bootstrap.configuration,
        "textus.web.execution.display-override.enabled"
      ) shouldBe Some("true")
      RuntimeConfig.getString(bootstrap.configuration, "textus.web.execution.sample-count") shouldBe Some("8")
      RuntimeConfig.getString(bootstrap.configuration, "textus.web.execution.sample-ratio") shouldBe Some("1.5")

      And("the resolved invocation retains the assembly descriptor source")
      invocation.actualArgs.exists(_.startsWith(s"--${RuntimeConfig.assemblyDescriptorKey}=")) shouldBe true

      When("the resolved launcher invocation initializes the runtime subsystem")
      val runtime = new CncfRuntime()
      try {
        val initialized = runtime.initializeForEmbedding(
          cwd = cwd,
          args = invocation.actualArgs,
          modeHint = Some(RunMode.Server),
          extraComponents = extras
        )
        val subsystem = initialized match {
          case Consequence.Success(value) => value
          case Consequence.Failure(conclusion) =>
            fail(s"assembly runtime initialization failed: ${conclusion.show}")
        }

        Then("the subsystem configuration still owns the assembly Web policy")
        RuntimeConfig.getString(
          subsystem.configuration,
          "textus.web.execution.display-override.enabled"
        ) shouldBe Some("true")
      } finally {
        runtime.closeEmbedding()
      }
    }

    "select search repositories only as development assembly API sources" in {
      Given("one active component development target and one dependency search repository")
      val dev = ComponentRepository.ComponentDevDirRepository.Specification(Paths.get("/tmp/app"))
      val dependency = ComponentRepository.ComponentDirRepository.Specification(Paths.get("/tmp/repository"))

      When("development assembly specifications are selected")
      val development = CncfRuntime.developmentAssemblySearchSpecifications(Vector(dev), Vector(dependency))
      val packaged = CncfRuntime.developmentAssemblySearchSpecifications(Vector(dependency), Vector(dev))

      Then("the development target selects only the dependency repository for API preflight")
      development shouldBe Vector(dependency)

      And("a packaged target keeps search repositories non-active")
      packaged shouldBe empty
    }

    "collect exact canonical development claims from active and assembly search repositories" in {
      Given("active and search prepared development targets with distinct canonical identities")
      val root = Files.createTempDirectory("cncf-development-claims")
      val activedir = root.resolve("active")
      val searchdir = root.resolve("search")
      _write_prepared_development_evidence(activedir, "org.example.Active", "0.2.0-SNAPSHOT")
      _write_prepared_development_evidence(searchdir, "org.example.Dependency", "0.2.0-SNAPSHOT")
      val active = ComponentRepository.ComponentDevDirRepository.Specification(activedir)
      val search = ComponentRepository.ComponentDevDirRepository.Specification(searchdir)

      When("the runtime prepares claims for its complete repository sequence")
      val claims = CncfRuntime.developmentComponentClaims(Vector(active), Vector(search))

      Then("both development repositories claim only their prepared canonical identities")
      claims(active) shouldBe Set(ComponentId("org.example.Active") -> "0.2.0-SNAPSHOT")
      claims(search) shouldBe Set(ComponentId("org.example.Dependency") -> "0.2.0-SNAPSHOT")
    }

    "preflight development assembly from prepared canonical target evidence" in {
      Given("a prepared target and a conflicting source-tree assembly identity")
      val root = Files.createTempDirectory("cncf-development-assembly-preflight")
      val cardir = root.resolve("src").resolve("main").resolve("car")
      Files.createDirectories(cardir)
      _write_prepared_development_evidence(root, "org.example.Prepared", "0.1.2-SNAPSHOT")
      Files.writeString(
        cardir.resolve("assembly-descriptor.yaml"),
        """subsystem: textus-art-scene
          |version: 0.1.1
          |components:
          |  - namespace: org.other
          |    id: Foreign
          |    version: 0.1.1
          |""".stripMargin
      )
      val dev = ComponentRepository.ComponentDevDirRepository.Specification(root)

      When("the API preflight descriptors and development claims are assembled")
      val descriptors = ComponentRepository.assemblyPreflightDescriptors(Vector(dev), Vector.empty)
      val claims = ComponentRepository.developmentComponentClaims(Vector(dev))
      val devdescriptors =
        ComponentRepository.descriptorsForSpecification(dev, Vector.empty, descriptors, claims)

      Then("only the prepared target identity is preflighted and claimed")
      descriptors.flatMap(_.requireCanonicalIdentityC.toOption) shouldBe
        Vector(ComponentId("org.example.Prepared") -> "0.1.2-SNAPSHOT")
      devdescriptors.flatMap(_.requireCanonicalIdentityC.toOption) shouldBe
        Vector(ComponentId("org.example.Prepared") -> "0.1.2-SNAPSHOT")

      And("the conflicting source-tree assembly identity is not routed")
      val searchdescriptors =
        ComponentRepository.unresolvedDescriptorsForSearch(Vector(dev), descriptors, claims)
      searchdescriptors shouldBe empty
    }

    "not claim a same-ID descriptor at a different release" in {
      Given("a prepared development target and same-ID descriptors at matching and stale releases")
      val reversedroot = Files.createTempDirectory("cncf-development-assembly-reversed-alias")
      _write_prepared_development_evidence(reversedroot, "org.example.Prepared", "0.1.2-SNAPSHOT")
      val reverseddev = ComponentRepository.ComponentDevDirRepository.Specification(reversedroot)
      val reversedclaims = ComponentRepository.developmentComponentClaims(Vector(reverseddev))
      val reverseddescriptors = Vector(
        ComponentDescriptor(
          name = Some("org.example.Prepared"), version = Some("0.1.2-SNAPSHOT"),
          componentName = Some("org.example.Prepared"), schemaVersion = Some(3), componentId = Some(ComponentId("org.example.Prepared"))
        ),
        ComponentDescriptor(
          name = Some("org.example.Prepared"), version = Some("0.1.1"),
          componentName = Some("org.example.Prepared"), schemaVersion = Some(3), componentId = Some(ComponentId("org.example.Prepared"))
        )
      )

      When("the assembly descriptor is routed to the development repository")
      val claimed = ComponentRepository
        .descriptorsForSpecification(reverseddev, Vector.empty, reverseddescriptors, reversedclaims)

      Then("only the matching canonical release is claimed")
      claimed.flatMap(_.requireCanonicalIdentityC.toOption) shouldBe
        Vector(ComponentId("org.example.Prepared") -> "0.1.2-SNAPSHOT")
    }

    }

    "configuration source precedence" which {
    "resolve an explicit YAML config file passed as a Textus CLI framework option" in {
      Given("an explicit YAML configuration file")
      val cwd = Files.createTempDirectory("textus-runtime-config")
      val config = cwd.resolve("runtime.yaml")
      Files.writeString(
        config,
        """textus:
          |  web:
          |    descriptor: config/web-descriptor.yaml
          |""".stripMargin
      )

      When("the file is passed through the Textus framework option")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", s"--textus.config.file=${config}", "server")
      )

      Then("the runtime resolves the file and preserves the framework argument")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some("config/web-descriptor.yaml")
      bootstrap.invocation.actualArgs.toVector should contain (s"--textus.config.file=${config}")
    }

    "resolve a virtual clock start passed as a Textus CLI framework option" in {
      Given("a virtual start date-time with an explicit offset")
      val cwd = Files.createTempDirectory("textus-runtime-virtual-clock")

      When("the runtime is bootstrapped with the clock setting")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--textus.clock.virtual-start-at=2026-07-28T18:00:00+09:00", "command")
      )
      val runtimeconfig = RuntimeConfig.from(bootstrap.configuration)

      Then("the CLI value selects an advancing offset clock")
      RuntimeConfig.getString(
        bootstrap.configuration,
        RuntimeConfig.CLOCK_VIRTUAL_START_AT_KEY
      ) shouldBe Some("2026-07-28T18:00:00+09:00")
      runtimeconfig.executionClock.virtualStartAt shouldBe
        Some(Instant.parse("2026-07-28T09:00:00Z"))
    }

    "resolve a legacy explicit YAML config file passed as a CNCF CLI framework option" in {
      Given("an explicit YAML configuration file")
      val cwd = Files.createTempDirectory("cncf-runtime-config")
      val config = cwd.resolve("runtime.yaml")
      Files.writeString(
        config,
        """textus:
          |  web:
          |    descriptor: config/web-descriptor.yaml
          |""".stripMargin
      )

      When("the file is passed through the legacy CNCF framework option")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", s"--cncf.config.file=${config}", "server")
      )

      Then("the runtime resolves the compatibility input")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some("config/web-descriptor.yaml")
      bootstrap.invocation.actualArgs.toVector should contain (s"--cncf.config.file=${config}")
    }

    "resolve a standard .textus config.yaml file before command-line domain execution" in {
      Given("a standard .textus config.yaml file")
      val cwd = Files.createTempDirectory("textus-standard-yaml")
      val configdir = cwd.resolve(".textus")
      Files.createDirectories(configdir)
      Files.writeString(
        configdir.resolve("config.yaml"),
        """textus:
          |  web:
          |    descriptor: config/web-descriptor.yaml
          |""".stripMargin
      )

      When("the runtime is bootstrapped from the project directory")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", "server")
      )

      Then("the standard configuration is resolved")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some("config/web-descriptor.yaml")
    }

    "resolve a legacy standard .cncf config.yaml file as compatibility input" in {
      Given("a legacy .cncf config.yaml file")
      val cwd = Files.createTempDirectory("cncf-standard-yaml")
      val configdir = cwd.resolve(".cncf")
      Files.createDirectories(configdir)
      Files.writeString(
        configdir.resolve("config.yaml"),
        """textus:
          |  web:
          |    descriptor: config/web-descriptor.yaml
          |""".stripMargin
      )

      When("the runtime is bootstrapped from the project directory")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", "server")
      )

      Then("the compatibility configuration is resolved")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some("config/web-descriptor.yaml")
    }

    "resolve explicit test descriptor config before command-line scalar overrides" in {
      Given("a canonical base subsystem descriptor plus a test override with config and canonical SPI bindings")
      val cwd = Files.createTempDirectory("cncf-test-descriptor-config")
      val testdescriptor = cwd.resolve("test.yaml")
      val subsystemdescriptor = cwd.resolve("subsystem.yaml")
      Files.writeString(
        testdescriptor,
        """kind: test-descriptor
          |config:
          |  textus.web.demo-assist.enabled: true
          |  textus.web.descriptor: config/from-test.yaml
          |assembly:
          |  spi:
          |    bindings:
          |      - socket:
          |          component: org.example.TargetComponent
          |          contract: ai-runner
          |        provider:
          |          component: org.example.TargetTestProvider
          |""".stripMargin
      )
      Files.writeString(
        subsystemdescriptor,
        """subsystem: config-target
          |version: 0.1.0
          |components:
          |  - namespace: org.example
          |    id: TargetComponent
          |    version: 0.1.0
          |  - namespace: org.example
          |    id: TargetTestProvider
          |    version: 0.1.0
          |""".stripMargin
      )

      When("the runtime is bootstrapped with an additional command-line override")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array(
          "--discover=classes",
          "--textus.test.descriptor=test.yaml",
          s"--textus.subsystem.file=${subsystemdescriptor.toAbsolutePath.normalize}",
          "--textus.web.descriptor=config/from-cli.yaml",
          "server"
        )
      )
      val descriptorpath = RuntimeTestDescriptor.path(bootstrap.configuration).get
      val descriptor = GenericSubsystemFactory.resolveDescriptorC(bootstrap.configuration).toOption.get.get
      val bindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor).toOption.get

      Then("the descriptor contributes canonical assembly bindings and the command line retains scalar precedence")
      descriptorpath shouldBe testdescriptor.normalize
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.WEB_DEMO_ASSIST_ENABLED_KEY) shouldBe Some("true")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some("config/from-cli.yaml")
      bindings.head.socket.component shouldBe Some("org.example.TargetComponent")
      bindings.head.provider.component shouldBe Some("org.example.TargetTestProvider")
    }

    "expand test descriptor datastore and home shortcuts into canonical runtime configuration" in {
      Given("a canonical base subsystem descriptor plus a test override with home and local datastore shortcuts")
      val cwd = Files.createTempDirectory("cncf-test-descriptor-datastore")
      val testdescriptor = cwd.resolve("test.yaml")
      val subsystemdescriptor = cwd.resolve("subsystem.yaml")
      val applicationdb = cwd.resolve("target").resolve("cncf.d").resolve("stage3d").resolve("application.db")
      val runtimedb = cwd.resolve("target").resolve("cncf.d").resolve("stage3d").resolve("runtime.db")
      val testhome = cwd.resolve("target").resolve("cncf.d").resolve("stage3d-home")
      Files.writeString(
        testdescriptor,
        s"""kind: test-descriptor
           |home:
           |  mode: isolated
           |  path: ${testhome}
           |  inherit:
           |    runtime: true
           |    repositories: true
           |    credentials: false
           |    local-data: false
           |runtime:
           |  datastore:
           |    type: local
           |    path: ${runtimedb}
           |components:
           |  art-scene:
           |    datastore:
           |      application:
           |        type: local
           |        path: ${applicationdb}
           |assembly:
           |  spi:
           |    bindings:
           |      - socket:
           |          component: org.simplemodeling.textus.ArtScene
           |          contract: ai-runner
           |        provider:
           |          component: org.simplemodeling.textus.ArtScene
           |        selection:
           |          mode: test
           |""".stripMargin
      )
      Files.writeString(
        subsystemdescriptor,
        """subsystem: textus-art-scene
          |version: 0.1.0
          |components:
          |  - namespace: org.simplemodeling.textus
          |    id: ArtScene
          |    version: 0.1.0
          |""".stripMargin
      )

      When("the test descriptor is resolved")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array(
          "--discover=classes",
          "--textus.test.descriptor=test.yaml",
          s"--textus.subsystem.file=${subsystemdescriptor.toAbsolutePath.normalize}",
          "server"
        )
      )
      val descriptor = GenericSubsystemFactory.resolveDescriptorC(bootstrap.configuration).toOption.get.get
      val bindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor).toOption.get

      Then("the shortcuts expand into canonical runtime configuration and canonical assembly bindings")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_HOME_PATH_KEY) shouldBe Some(testhome.toString)
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_HOME_INHERIT_LOCAL_DATA_KEY) shouldBe Some("false")
      RuntimeConfig.getString(bootstrap.configuration, "textus.local-data.root") shouldBe Some(testhome.resolve(".cncf").toString)
      RuntimeConfig.getString(bootstrap.configuration, "textus.datastore.kind") shouldBe Some("local")
      RuntimeConfig.getString(bootstrap.configuration, "textus.datastore.path") shouldBe Some(runtimedb.toString)
      RuntimeConfig.getString(bootstrap.configuration, "textus.component.art-scene.datastores.application.kind") shouldBe Some("local")
      RuntimeConfig.getString(bootstrap.configuration, "textus.component.art-scene.datastores.application.path") shouldBe Some(applicationdb.toString)
      bindings.head.socket.component shouldBe Some("org.simplemodeling.textus.ArtScene")
      bindings.head.provider.component shouldBe Some("org.simplemodeling.textus.ArtScene")
      bindings.head.selection.mode shouldBe Some("test")
    }

    "normalize cncf test command options without changing JVM user.home" in {
      Given("a test descriptor and an explicit isolated test home")
      val cwd = Files.createTempDirectory("cncf-test-home-command")
      val testdescriptor = cwd.resolve("test.yaml")
      val testhome = cwd.resolve("target").resolve("cncf.d").resolve("stage3d-home")
      val originalhome = System.getProperty("user.home")
      Files.writeString(
        testdescriptor,
        """kind: test-descriptor
          |config:
          |  textus.web.demo-assist.enabled: true
          |""".stripMargin
      )

      When("the test command is normalized")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array(
          "test",
          "--test-config",
          "test.yaml",
          "--home",
          testhome.toString,
          "server"
        )
      )

      Then("test configuration is applied without changing JVM user.home")
      bootstrap.invocation.actualArgs.toVector should contain ("server")
      bootstrap.invocation.actualArgs.toVector should not contain ("test")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.operationModeKey) shouldBe Some("test")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_DESCRIPTOR_KEY) shouldBe Some(testdescriptor.normalize.toString)
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_HOME_PATH_KEY) shouldBe Some(testhome.toString)
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_HOME_INHERIT_REPOSITORIES_KEY) shouldBe Some("true")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_HOME_INHERIT_CREDENTIALS_KEY) shouldBe Some("false")
      RuntimeConfig.getString(bootstrap.configuration, "textus.local-data.root") shouldBe Some(testhome.resolve(".cncf").toString)
      System.getProperty("user.home") shouldBe originalhome
    }

    "reject a test-config option without a value" in {
      Given("a test command whose test-config option has no value")
      val cwd = Files.createTempDirectory("cncf-test-config-missing-value")

      When("the command is bootstrapped")
      val thrown = intercept[IllegalArgumentException] {
        CncfRuntime.bootstrap(cwd, Array("test", "--test-config"))
      }

      Then("the missing option value is reported deterministically")
      thrown.getMessage shouldBe "--test-config requires a value"
    }

    "reject a test home option without a value" in {
      Given("a test command whose home option has no value")
      val cwd = Files.createTempDirectory("cncf-test-home-missing-value")

      When("the command is bootstrapped")
      val thrown = intercept[IllegalArgumentException] {
        CncfRuntime.bootstrap(cwd, Array("test", "--home"))
      }

      Then("the missing option value is reported deterministically")
      thrown.getMessage shouldBe "--home requires a value"
    }

    "create temporary cncf test homes under target without changing JVM user.home" in {
      Given("a test command requesting a temporary home")
      val cwd = Files.createTempDirectory("cncf-temporary-test-home-command")
      val originalhome = System.getProperty("user.home")

      When("the runtime bootstraps the test command")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array(
          "test",
          "--temporary-home",
          "server"
        )
      )
      val home = RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_HOME_PATH_KEY).get

      Then("an isolated home is created under target without changing JVM user.home")
      bootstrap.invocation.actualArgs.toVector should contain ("server")
      home should startWith (cwd.resolve("target").resolve("cncf.d").toString)
      Files.isDirectory(java.nio.file.Paths.get(home)) shouldBe true
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_HOME_TEMPORARY_KEY) shouldBe Some("true")
      RuntimeConfig.getString(bootstrap.configuration, "textus.local-data.root") shouldBe Some(java.nio.file.Paths.get(home).resolve(".cncf").toString)
      System.getProperty("user.home") shouldBe originalhome
    }

    "apply the legacy .cncf compatibility override after baseline .textus configuration" in {
      Given("both baseline .textus and compatibility-override .cncf configuration files")
      val cwd = Files.createTempDirectory("textus-over-cncf")
      val legacydir = cwd.resolve(".cncf")
      val configdir = cwd.resolve(".textus")
      Files.createDirectories(legacydir)
      Files.createDirectories(configdir)
      Files.writeString(
        legacydir.resolve("config.yaml"),
        """textus:
          |  web:
          |    descriptor: config/from-cncf.yaml
          |""".stripMargin
      )
      Files.writeString(
        configdir.resolve("config.yaml"),
        """textus:
          |  web:
          |    descriptor: config/from-textus.yaml
          |""".stripMargin
      )

      When("the runtime resolves project configuration")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", "server")
      )

      Then("the compatibility override value takes precedence")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some("config/from-cncf.yaml")
    }

    "prefer standard config.yaml over config.conf in the same .textus scope" in {
      Given("config.conf and config.yaml in the same .textus scope")
      val cwd = Files.createTempDirectory("textus-standard-yaml-precedence")
      val configdir = cwd.resolve(".textus")
      Files.createDirectories(configdir)
      Files.writeString(configdir.resolve("config.conf"), "textus.web.descriptor = config/from-conf.yaml\n")
      Files.writeString(
        configdir.resolve("config.yaml"),
        """textus:
          |  web:
          |    descriptor: config/from-yaml.yaml
          |""".stripMargin
      )

      When("the runtime resolves standard project configuration")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", "server")
      )

      Then("the later YAML source takes precedence")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some("config/from-yaml.yaml")
    }

    "resolve standard configuration files in conf props properties json yaml xml order" in {
      Given("all supported standard configuration files in one .textus scope")
      val cwd = Files.createTempDirectory("textus-standard-order")
      val configdir = cwd.resolve(".textus")
      Files.createDirectories(configdir)
      Files.writeString(configdir.resolve("config.conf"), "textus.web.descriptor = config/from-conf.yaml\n")
      Files.writeString(configdir.resolve("config.props"), "textus.web.descriptor=config/from-props.yaml\n")
      Files.writeString(configdir.resolve("config.properties"), "textus.web.descriptor=config/from-properties.yaml\n")
      Files.writeString(configdir.resolve("config.json"), """{"textus":{"web":{"descriptor":"config/from-json.yaml"}}}""")
      Files.writeString(
        configdir.resolve("config.yaml"),
        """textus:
          |  web:
          |    descriptor: config/from-yaml.yaml
          |""".stripMargin
      )
      Files.writeString(
        configdir.resolve("config.xml"),
        """<config>
          |  <textus>
          |    <web>
          |      <descriptor>config/from-xml.yaml</descriptor>
          |    </web>
          |  </textus>
          |</config>
          |""".stripMargin
      )

      When("the runtime resolves the standard source sequence")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", "server")
      )

      Then("the documented final XML source has precedence")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some("config/from-xml.yaml")
    }

    }

    "component and subsystem startup resolution" which {
    "detect a component CAR from a component project directory for plain server startup" in {
      Given("a component project with one generated CAR")
      val cwd = Files.createTempDirectory("textus-component-only-server")
      val target = cwd.resolve("component").resolve("target")
      Files.createDirectories(target)
      val car = target.resolve("notice-board-0.1.0.car")
      Files.writeString(car, "placeholder")

      When("plain server startup is bootstrapped")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--discover=classes", "server")
      )

      Then("the project CAR is retained by the typed repository policy and selected for startup")
      bootstrap.repositoryBootstrapPolicy.componentFiles shouldBe Vector(car.toString)
      bootstrap.repositories.activeRepositories.toOption.get should contain (
        ComponentRepository.ComponentFileRepository.Specification(car)
      )
    }

    "resolve a qualified named component from the canonical repository coordinate for server startup" in {
      Given("a schema-3 named component CAR at its canonical repository coordinate")
      val cwd = Files.createTempDirectory("textus-component-name-repo")
      val repository = cwd.resolve("repository")
      val componentid = ComponentId("org.example.Cwitter")
      val release = "0.0.1"
      val coordinate = ComponentReleaseCoordinate.require(componentid.sharedIdentity, release)
      val car = repository.resolve("car").resolve(coordinate.carRepositoryRelativePath())
      val artifactdir = car.getParent
      Files.createDirectories(artifactdir)
      _write_zip(
        car,
        Map(
          "component-descriptor.json" ->
            _canonical_descriptor_json(componentid.name, release),
          "assembly-descriptor.yaml" ->
            """subsystem: cwitter
              |version: 0.0.1
              |components:
              |  - namespace: org.example
              |    id: Cwitter
              |    version: 0.0.1
              |""".stripMargin
        )
      )
      When("the named component server invocation is resolved")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--repository-dir", repository.toString, s"--textus.component=${componentid.name}", s"--textus.component.version=$release", "server")
      )
      val specs = bootstrap.repositories.searchRepositories.toOption.get

      val resolved = CncfRuntime.resolveSubsystemInvocation(bootstrap.invocation, specs)

      Then("the repository CAR is appended as the component file")
      resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.componentFileKey}=${car}")
    }

    "reject a bare named selector and avoid injecting a foreign same-looking local CAR" in {
      Given("a local CAR whose filename resembles the requested qualified selector but whose descriptor is foreign")
      val root = Files.createTempDirectory("textus-component-name-identity")
      val requestedid = ComponentId("org.example.Cwitter")
      val foreignid = ComponentId("org.other.Cwitter")
      val release = "0.0.1"
      val foreigncar = root.resolve(s"${requestedid.name}-$release.car")
      _write_zip(foreigncar, Map(
        "component-descriptor.json" -> _canonical_descriptor_json(foreignid.name, release)
      ))
      val specs = Vector(ComponentRepository.ComponentDirRepository.Specification(root))

      When("a bare component selector is resolved")
      val bare = the[ConsequenceException] thrownBy CncfRuntime.resolveComponentInvocation(
        CncfRuntime.RuntimeInvocationParameters(
          actualArgs = Array("--textus.component=cwitter", "server"),
          subsystemName = None,
          componentName = Some("cwitter")
        ),
        specs
      )

      And("the qualified selector is resolved against the foreign local CAR")
      val resolved = CncfRuntime.resolveComponentInvocation(
        CncfRuntime.RuntimeInvocationParameters(
          actualArgs = Array(s"--textus.component=${requestedid.name}", s"--textus.component.version=$release", "server"),
          subsystemName = None,
          componentName = Some(requestedid.name),
          componentVersion = Some(release)
        ),
        specs
      )

      Then("the shared qualified identity diagnostic rejects the bare form and no foreign CAR is selected")
      bare.getMessage should include ("component")
      resolved.actualArgs.toVector should not contain s"--${RuntimeConfig.componentFileKey}=${foreigncar}"
    }

    "not append a repository component file when component file is already explicit" in {
      Given("a repository component and an explicit component file")
      val cwd = Files.createTempDirectory("textus-component-file-explicit")
      val repository = cwd.resolve("repository")
      val artifactdir = repository.resolve("org").resolve("simplemodeling").resolve("car").resolve("cwitter").resolve("0.0.1-SNAPSHOT")
      Files.createDirectories(artifactdir)
      val repocar = artifactdir.resolve("cwitter-0.0.1-SNAPSHOT.car")
      _write_zip(
        repocar,
        Map("component-descriptor.json" -> """{"component":{"name":"cwitter"},"version":"0.0.1-SNAPSHOT"}""")
      )
      val explicitcar = cwd.resolve("explicit-cwitter.car")
      _write_zip(
        explicitcar,
        Map("component-descriptor.json" -> """{"component":{"name":"cwitter"},"version":"local"}""")
      )
      When("the component invocation is resolved")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--repository-dir", repository.toString, s"--${RuntimeConfig.componentFileKey}=${explicitcar}", "--textus.component=org.example.Cwitter", "server")
      )
      val specs = bootstrap.repositories.searchRepositories.toOption.get

      val resolved = CncfRuntime.resolveSubsystemInvocation(bootstrap.invocation, specs)

      Then("the explicit component file remains the only component-file argument")
      resolved.actualArgs.toVector.count(_.startsWith(s"--${RuntimeConfig.componentFileKey}=")) shouldBe 1
      resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.componentFileKey}=${explicitcar}")
    }

    "retain admitted repository selections across resolved invocation re-bootstrap" in {
      Given("one admitted path for every repository selection kind")
      val cwd = Files.createTempDirectory("cncf-repository-invocation-rebootstrap")
      val values = Vector(
        RuntimeConfig.repositoryDirKey -> cwd.resolve("repository").toString,
        RuntimeConfig.repositoryComponentDevDirKey -> cwd.resolve("repository-component-dev").toString,
        RuntimeConfig.componentDirKey -> cwd.resolve("component").toString,
        RuntimeConfig.componentDevDirKey -> cwd.resolve("component-dev").toString,
        RuntimeConfig.componentCarDirKey -> cwd.resolve("component-car").toString,
        RuntimeConfig.componentFileKey -> cwd.resolve("component.car").toString,
        RuntimeConfig.subsystemDevDirKey -> cwd.resolve("subsystem-dev").toString,
        RuntimeConfig.subsystemSarDirKey -> cwd.resolve("subsystem-sar").toString
      )
      val args = values.map { case (key, value) => s"--${key}=${value}" }.toArray ++ Array("command")

      When("the canonical invocation is bootstrapped again after launcher resolution")
      val first = CncfRuntime.bootstrap(cwd, args)
      val second = CncfRuntime.bootstrap(cwd, first.invocation.actualArgs)

      Then("every repository selection retains its original semantic type and value")
      second.repositoryBootstrapPolicy.repositoryDirs shouldBe Vector(values(0)._2)
      second.repositoryBootstrapPolicy.repositoryComponentDevDirs shouldBe Vector(values(1)._2)
      second.repositoryBootstrapPolicy.componentDirs shouldBe Vector(values(2)._2)
      second.repositoryBootstrapPolicy.componentDevDirs shouldBe Vector(values(3)._2)
      second.repositoryBootstrapPolicy.componentCarDirs shouldBe Vector(values(4)._2)
      second.repositoryBootstrapPolicy.componentFiles shouldBe Vector(values(5)._2)
      second.repositoryBootstrapPolicy.subsystemDevDirs shouldBe Vector(values(6)._2)
      second.repositoryBootstrapPolicy.subsystemSarDirs shouldBe Vector(values(7)._2)
    }

    "resolve a named subsystem SAR from the standard repository for server startup" in {
      Given("a named subsystem SAR with canonical component bindings in the standard repository layout")
      val cwd = Files.createTempDirectory("textus-subsystem-name-repo")
      val repository = cwd.resolve("repository")
      val artifactdir = repository.resolve("sar").resolve("cwitter").resolve("0.0.1-SNAPSHOT")
      Files.createDirectories(artifactdir)
      val sar = artifactdir.resolve("cwitter-0.0.1-SNAPSHOT.sar")
      _write_zip(
        sar,
        Map(
          "subsystem-descriptor.yaml" ->
            """subsystem: cwitter
              |version: 0.0.1-SNAPSHOT
              |components:
              |  - namespace: org.example
              |    id: Cwitter
              |    version: 0.0.1-SNAPSHOT
              |  - namespace: org.simplemodeling.textus
              |    id: UserAccount
              |    version: 0.1.0-SNAPSHOT
              |""".stripMargin
        )
      )
      When("the named subsystem server invocation is resolved")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--repository-dir", repository.toString, "--textus.subsystem=cwitter", "server")
      )
      val specs = bootstrap.repositories.searchRepositories.toOption.get

      val resolved = CncfRuntime.resolveSubsystemInvocation(bootstrap.invocation, specs)

      Then("the canonical standard-layout SAR is appended as the subsystem file")
      resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.subsystemFileKey}=${sar}")
    }

    "retain snapshot user-mode and HOME fixed-user candidates in the final runtime collection" in {
      Given("a retained standalone runtime source, a conflicting legacy view, and a HOME fixed-user profile")
      val cwd = Files.createTempDirectory("gcf07g-runtime-collection")
      Files.createDirectories(cwd.resolve(".textus"))
      Files.writeString(cwd.resolve(".textus/config.conf"), "textus.subsystem.user-mode = standalone\n")
      val subsystem = _fixed_runtime_collection_subsystem()
      val admittedprofile = Vector(StandaloneUserProfileResolver.Admitted(
        StandaloneUserProfileResolver.Layer.TextusHome,
        cwd.resolve("home/.textus/user-profile.yaml"),
        ConfigurationOrigin.Home,
        StandaloneUserProfile.Document(Some(StandaloneUserProfile.User(
          id = Some("runtime-fixed-user"),
          displayName = Some("Runtime Fixed User"),
          locale = Some("ja-JP"),
          timezone = Some("Asia/Tokyo")
        )))
      ))

      When("the runtime's snapshot-admission sequence resolves and admits its final collection")
      val snapshot = CncfRuntime.bootstrap(cwd, Array("command")).configurationSnapshot.getOrElse(
        fail("runtime configuration snapshot is required")
      )
      val admitted = new CncfRuntime()._admit_runtime_configuration_snapshot(
        snapshot,
        subsystem,
        _ => Consequence.success(admittedprofile)
      )

      Then("the final collection retains both the snapshot user-mode and the typed fixed-user profile")
      admitted shouldBe a[Consequence.Success[_]]
      subsystem.subsystemUserModeC.toOption.map(_.mode) shouldBe Some(SubsystemUserMode.Standalone)
      subsystem.resolvedStandaloneUserProfile.map(_.id) shouldBe Some("runtime-fixed-user")
      subsystem.resolvedStandaloneUserProfile.flatMap(_.displayName) shouldBe Some("Runtime Fixed User")
    }

    "project already-loaded assembly Web defaults into the final runtime collection" in {
      Given("a standalone snapshot, a fixed-user profile, and assembly-only Web defaults")
      val cwd = Files.createTempDirectory("gcf07h-assembly-web-defaults")
      val assembly = cwd.resolve("assembly.yaml")
      Files.createDirectories(cwd.resolve(".textus"))
      Files.writeString(cwd.resolve(".textus/config.conf"), "textus.subsystem.user-mode = standalone\n")
      Files.writeString(
        assembly,
        """subsystem: gcf07h-runtime
          |components: []
          |config:
          |  textus.web.execution.locale: ja-JP
          |  textus.web.execution.timezone: Asia/Tokyo
          |  textus.web.execution.display-override.enabled: true
          |  textus.web.execution.public-capabilities: browse, report
          |""".stripMargin
      )
      val bootstrap = CncfRuntime.bootstrap(cwd, Array(s"--textus.assembly.descriptor=$assembly", "command"))
      val snapshot = bootstrap.configurationSnapshot.getOrElse(fail("runtime configuration snapshot is required"))
      val subsystem = Subsystem("gcf07h-runtime", configuration = bootstrap.configuration).withDescriptor(
        GenericSubsystemDescriptor(
          path = assembly,
          subsystemName = "gcf07h-runtime",
          security = Some(GenericSubsystemSecurityBinding(authentication = Some(
            GenericSubsystemAuthenticationBinding(localSubject = Some(GenericSubsystemLocalSubjectBinding("runtime-local-subject")))
          )))
        )
      )
      val admittedprofile = Vector(StandaloneUserProfileResolver.Admitted(
        StandaloneUserProfileResolver.Layer.TextusHome,
        cwd.resolve("home/.textus/user-profile.yaml"),
        ConfigurationOrigin.Home,
        StandaloneUserProfile.Document(Some(StandaloneUserProfile.User(id = Some("gcf07h-user"))))
      ))

      When("bootstrap merges the descriptor contribution before final Subsystem admission")
      val admitted = new CncfRuntime()._admit_runtime_configuration_snapshot(
        snapshot,
        bootstrap.assemblyConfiguration,
        subsystem,
        _ => Consequence.success(admittedprofile)
      )

      Then("the final typed policy retains assembly defaults without a source reload")
      admitted shouldBe a[Consequence.Success[_]]
      val policy = subsystem.webExecutionResolutionPolicyC.getOrElse(fail("typed Web policy is required")).getOrElse(fail("typed Web policy must resolve"))
      policy.displayOverrideEnabled shouldBe true
      policy.publicCapabilities shouldBe Vector("browse", "report")
    }

    "select the execution profile from an assembly-only user-mode binding" in {
      Given("an assembly descriptor that supplies the only standalone user-mode value")
      val cwd = Files.createTempDirectory("gcf09b-assembly-user-mode")
      val assembly = cwd.resolve("assembly.yaml")
      Files.writeString(
        assembly,
        """subsystem: gcf09b-runtime
          |components: []
          |config:
          |  textus.subsystem.user-mode: standalone
          |""".stripMargin
      )
      val bootstrap = CncfRuntime.bootstrap(cwd, Array(s"--textus.assembly.descriptor=$assembly", "command"))
      val snapshot = bootstrap.configurationSnapshot.getOrElse(fail("runtime configuration snapshot is required"))
      val subsystem = Subsystem("gcf09b-runtime", configuration = bootstrap.configuration).withDescriptor(
        GenericSubsystemDescriptor(
          path = assembly,
          subsystemName = "gcf09b-runtime",
          security = Some(GenericSubsystemSecurityBinding(authentication = Some(
            GenericSubsystemAuthenticationBinding(localSubject = Some(GenericSubsystemLocalSubjectBinding("gcf09b-subject")))
          )))
        )
      )
      val admittedprofile = Vector(StandaloneUserProfileResolver.Admitted(
        StandaloneUserProfileResolver.Layer.TextusHome,
        cwd.resolve("home/.textus/user-profile.yaml"),
        ConfigurationOrigin.Home,
        StandaloneUserProfile.Document(Some(StandaloneUserProfile.User(id = Some("gcf09b-user"))))
      ))

      When("runtime profile admission resolves the assembled pre-profile collection")
      val admitted = new CncfRuntime()._admit_runtime_configuration_snapshot(
        snapshot,
        bootstrap.assemblyConfiguration,
        subsystem,
        _ => Consequence.success(admittedprofile)
      )

      Then("the assembly-only mode selects standalone before profile admission and remains final")
      admitted shouldBe a[Consequence.Success[_]]
      subsystem.subsystemUserModeC.toOption.map(_.mode) shouldBe Some(SubsystemUserMode.Standalone)
      subsystem.resolvedStandaloneUserProfile.map(_.id) shouldBe Some("gcf09b-user")
    }

    "default a missing standalone HOME profile from the assembly local subject" in {
      Given("an assembly-only standalone mode, a local subject, and no admitted HOME profile")
      val cwd = Files.createTempDirectory("standalone-default-user-profile")
      val assembly = cwd.resolve("assembly.yaml")
      Files.writeString(
        assembly,
        """subsystem: standalone-default-runtime
          |components: []
          |config:
          |  textus.subsystem.user-mode: standalone
          |""".stripMargin
      )
      val bootstrap = CncfRuntime.bootstrap(cwd, Array(s"--textus.assembly.descriptor=$assembly", "command"))
      val snapshot = bootstrap.configurationSnapshot.getOrElse(fail("runtime configuration snapshot is required"))
      val subsystem = Subsystem("standalone-default-runtime", configuration = bootstrap.configuration).withDescriptor(
        GenericSubsystemDescriptor(
          path = assembly,
          subsystemName = "standalone-default-runtime",
          security = Some(GenericSubsystemSecurityBinding(authentication = Some(
            GenericSubsystemAuthenticationBinding(
              localSubject = Some(GenericSubsystemLocalSubjectBinding("standalone-local"))
            )
          )))
        )
      )

      When("runtime admission resolves an empty HOME profile set")
      val admitted = new CncfRuntime()._admit_runtime_configuration_snapshot(
        snapshot,
        bootstrap.assemblyConfiguration,
        subsystem,
        _ => Consequence.success(Vector.empty)
      )

      Then("the typed fixed-user profile uses the assembly local-subject id")
      admitted shouldBe a[Consequence.Success[_]]
      subsystem.subsystemUserModeC.toOption.map(_.mode) shouldBe Some(SubsystemUserMode.Standalone)
      subsystem.resolvedStandaloneUserProfile.map(_.id) shouldBe Some("standalone-local")
    }
    }
  }

  private def _fixed_runtime_collection_subsystem(): Subsystem =
    Subsystem(
      "gcf07g-runtime",
      configuration = ResolvedConfiguration(
        Configuration(Map(SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("multi-user"))),
        ConfigurationTrace.empty
      )
    ).withDescriptor(
      GenericSubsystemDescriptor(
        path = Paths.get("gcf07g-runtime.car"),
        subsystemName = "gcf07g-runtime",
        security = Some(GenericSubsystemSecurityBinding(authentication = Some(
          GenericSubsystemAuthenticationBinding(
            localSubject = Some(GenericSubsystemLocalSubjectBinding("runtime-local-subject"))
          )
        )))
      )
    )

  private def _write_zip(path: java.nio.file.Path, entries: Map[String, String]): Unit = {
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      entries.foreach { case (name, content) =>
        out.putNextEntry(new ZipEntry(name))
        out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        out.closeEntry()
      }
    } finally {
      out.close()
    }
  }

  private def _write_prepared_development_evidence(root: java.nio.file.Path, componentid: String, release: String): Unit = {
    val target = root.resolve("target/cncf.d")
    val classes = root.resolve("target/scala-3.3.8/classes")
    val coordinate = ComponentReleaseCoordinate.require(ComponentId(componentid).sharedIdentity, release)
    Files.createDirectories(target)
    Files.createDirectories(classes)
    Files.writeString(classes.resolve("prepared.class"), "prepared", StandardCharsets.UTF_8)
    val descriptor = target.resolve("component-descriptor.json")
    val abi = root.resolve("src/main/car/abi-manifest.json")
    val classpath = target.resolve("runtime-classpath.txt")
    Files.writeString(descriptor, _canonical_descriptor_json(componentid, release), StandardCharsets.UTF_8)
    Files.createDirectories(abi.getParent)
    Files.writeString(abi, s"""{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"${componentid.split("\\.").dropRight(1).mkString(".")}","id":"${componentid.split("\\.").last}","version":"$release"},"abi":{"version":1,"exports":{"components":[{"namespace":"${componentid.split("\\.").dropRight(1).mkString(".")}","id":"${componentid.split("\\.").last}"}],"operations":[],"entities":[]},"dependencies":[]}}""", StandardCharsets.UTF_8)
    Files.writeString(classpath, classes.toString, StandardCharsets.UTF_8)
    val evidence = Vector(
      "target/cncf.d/runtime-classpath.txt" -> classpath,
      "target/cncf.d/component-descriptor.json" -> descriptor,
      "src/main/car/abi-manifest.json" -> abi
    ).map { case (identity, file) =>
      val logical = if (identity.endsWith("runtime-classpath.txt")) s"project:${root.relativize(classes).toString}" else ""
      (identity, _sha256(Files.readAllBytes(file)), logical)
    }
    val entries = evidence.map { case (path, digest, logical) =>
      val prefix = if (logical.nonEmpty) s"\"logicalSha256\":\"${_sha256(logical.getBytes(StandardCharsets.UTF_8))}\", " else ""
      s"{$prefix\"path\":\"$path\",\"sha256\":\"$digest\"}"
    }.mkString("[", ",", "]")
    val digest = _sha256(evidence.map { case (path, value, logical) => s"$path\t$value\t${if (logical.nonEmpty) _sha256(logical.getBytes(StandardCharsets.UTF_8)) else ""}" }.mkString("\n").getBytes(StandardCharsets.UTF_8))
    Files.writeString(target.resolve("car-runtime-manifest.json"), s"""{"schemaVersion":"cncf.car-development-runtime-manifest.v2","sourceKind":"development-directory","car":{"name":"${coordinate.mavenArtifactId()}","version":"$release","component":"${componentid.split("\\.").last}"},"runtime":{"cncf":{"minimum":"${org.goldenport.cncf.CncfVersion.current}","excluded":[],"tested":["${org.goldenport.cncf.CncfVersion.current}"]}},"evidence":$entries,"integrity":{"algorithm":"SHA-256","evidenceSha256":"$digest"}}""", StandardCharsets.UTF_8)
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _canonical_descriptor_json(componentid: String, release: String): String = {
    val segments = componentid.split("\\.").toVector
    val namespace = segments.dropRight(1).mkString(".")
    val localid = segments.last
    s"""{"schemaVersion":3,"component":{"namespace":"$namespace","id":"$localid","version":"$release"}}"""
  }
}
