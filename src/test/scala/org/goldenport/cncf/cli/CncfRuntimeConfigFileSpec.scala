package org.goldenport.cncf.cli

import java.time.Instant
import java.nio.file.{Files, Paths}
import java.util.zip.{ZipEntry, ZipOutputStream}
import org.goldenport.cncf.config.{RuntimeConfig, RuntimeTestDescriptor}
import org.goldenport.cncf.component.ComponentDescriptor
import org.goldenport.cncf.component.repository.ComponentRepository
import org.goldenport.cncf.subsystem.{GenericSubsystemDescriptor, GenericSubsystemFactory}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 15, 2026
 *  version Apr. 25, 2026
 * @version Jul. 31, 2026
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
      val args = Array(s"--textus.assembly.descriptor=${assemblydescriptor}", "server")

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
        val subsystem = runtime.initializeForEmbedding(
          cwd = cwd,
          args = invocation.actualArgs,
          modeHint = Some(RunMode.Server),
          extraComponents = extras
        ).toOption.get

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

    "collect development claims from active and assembly search repositories" in {
      Given("active and search development repositories with distinct component identities")
      val root = Files.createTempDirectory("cncf-development-claims")
      val activedir = root.resolve("active")
      val searchdir = root.resolve("search")
      Files.createDirectories(activedir.resolve("src").resolve("main").resolve("car"))
      Files.createDirectories(searchdir.resolve("src").resolve("main").resolve("car"))
      Files.writeString(
        activedir.resolve("src").resolve("main").resolve("car").resolve("component-descriptor.json"),
        """{"name":"active","version":"0.2.0-SNAPSHOT","component":"active"}"""
      )
      Files.writeString(
        searchdir.resolve("src").resolve("main").resolve("car").resolve("component-descriptor.json"),
        """{"name":"dependency","version":"0.2.0-SNAPSHOT","component":"dependency"}"""
      )
      _write_legacy_development_manifest(activedir)
      _write_legacy_development_manifest(searchdir)
      val active = ComponentRepository.ComponentDevDirRepository.Specification(activedir)
      val search = ComponentRepository.ComponentDevDirRepository.Specification(searchdir)

      When("the runtime prepares claims for its complete repository sequence")
      val claims = CncfRuntime.developmentComponentClaims(Vector(active), Vector(search))

      Then("both development repositories claim their components before descriptor routing")
      claims(active) shouldBe Set("active")
      claims(search) shouldBe Set("dependency")
    }

    "include development CAR assembly dependencies in API preflight" in {
      Given("an active development CAR whose assembly declares the Scraper provider")
      val root = Files.createTempDirectory("cncf-development-assembly-preflight")
      val cardir = root.resolve("src").resolve("main").resolve("car")
      Files.createDirectories(cardir)
      Files.writeString(
        cardir.resolve("component-descriptor.json"),
        """{"name":"ArtScene","version":"0.1.2-SNAPSHOT","component":"ArtScene"}"""
      )
      Files.writeString(
        cardir.resolve("assembly-descriptor.yaml"),
        """subsystem: textus-art-scene
          |version: 0.1.1
          |components:
          |  - name: textus-art-scene
          |    version: 0.1.1
          |  - name: textus-scraper
          |    version: 0.1.1
          |""".stripMargin
      )
      _write_legacy_development_manifest(root)
      val dev = ComponentRepository.ComponentDevDirRepository.Specification(root)

      When("the API preflight descriptors and development claims are assembled")
      val descriptors = ComponentRepository.assemblyPreflightDescriptors(Vector(dev), Vector.empty)
      val claims = ComponentRepository.developmentComponentClaims(Vector(dev))
      val devdescriptors =
        ComponentRepository.descriptorsForSpecification(dev, Vector.empty, descriptors, claims)

      Then("the stale assembly version and textus-prefixed alias are still claimed by the development component")
      descriptors.flatMap(_.componentName) shouldBe Vector("textus-art-scene", "textus-scraper")
      descriptors.flatMap(_.version) shouldBe Vector("0.1.1", "0.1.1")
      devdescriptors.flatMap(_.componentName) shouldBe Vector("textus-art-scene")

      And("only the dependency remains unresolved for the search repository")
      val searchdescriptors =
        ComponentRepository.unresolvedDescriptorsForSearch(Vector(dev), descriptors, claims)
      searchdescriptors.flatMap(_.componentName) shouldBe Vector("textus-scraper")
    }

    "claim textus-prefixed development identities symmetrically" in {
      Given("a textus-prefixed development descriptor and an unprefixed stale assembly identity")
      val reversedroot = Files.createTempDirectory("cncf-development-assembly-reversed-alias")
      val reversedcardir = reversedroot.resolve("src").resolve("main").resolve("car")
      Files.createDirectories(reversedcardir)
      Files.writeString(
        reversedcardir.resolve("component-descriptor.json"),
        """{"name":"textus-art-scene","version":"0.1.2-SNAPSHOT","component":"textus-art-scene"}"""
      )
      val reverseddev = ComponentRepository.ComponentDevDirRepository.Specification(reversedroot)
      val reversedclaims = ComponentRepository.developmentComponentClaims(Vector(reverseddev))
      val reverseddescriptors = Vector(
        ComponentDescriptor(
          name = Some("ArtScene"),
          version = Some("0.1.1"),
          componentName = Some("ArtScene")
        )
      )

      When("the assembly descriptor is routed to the development repository")
      val claimed = ComponentRepository
        .descriptorsForSpecification(reverseddev, Vector.empty, reverseddescriptors, reversedclaims)

      Then("the textus prefix direction does not change component identity")
      claimed.flatMap(_.componentName) shouldBe Vector("ArtScene")
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
      Given("a test descriptor with config and SPI overrides")
      val cwd = Files.createTempDirectory("cncf-test-descriptor-config")
      val testdescriptor = cwd.resolve("test.yaml")
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
          |          component: target-component
          |          contract: ai-runner
          |        provider:
          |          component: target-test-provider
          |""".stripMargin
      )

      When("the runtime is bootstrapped with an additional command-line override")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array(
          "--discover=classes",
          "--textus.test.descriptor=test.yaml",
          "--textus.component=target-component",
          "--textus.web.descriptor=config/from-cli.yaml",
          "server"
        )
      )
      val descriptorpath = RuntimeTestDescriptor.path(bootstrap.configuration).get
      val descriptor = GenericSubsystemFactory.resolveDescriptorC(bootstrap.configuration).toOption.get.get
      val bindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor).toOption.get

      Then("the descriptor contributes assembly data and the command line retains scalar precedence")
      descriptorpath shouldBe testdescriptor.normalize
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.WEB_DEMO_ASSIST_ENABLED_KEY) shouldBe Some("true")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some("config/from-cli.yaml")
      bindings.head.provider.component shouldBe Some("target-test-provider")
    }

    "expand test descriptor datastore and home shortcuts into canonical runtime configuration" in {
      Given("a test descriptor with home and local datastore shortcuts")
      val cwd = Files.createTempDirectory("cncf-test-descriptor-datastore")
      val testdescriptor = cwd.resolve("test.yaml")
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
           |          component: art-scene
           |          contract: ai-runner
           |        provider:
           |          component: art-scene
           |        selection:
           |          mode: test
           |""".stripMargin
      )

      When("the test descriptor is resolved")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array(
          "--discover=classes",
          "--textus.test.descriptor=test.yaml",
          "--textus.component=art-scene",
          "server"
        )
      )
      val descriptor = GenericSubsystemFactory.resolveDescriptorC(bootstrap.configuration).toOption.get.get
      val bindings = GenericSubsystemDescriptor.resolveAssemblySpiBindings(descriptor).toOption.get

      Then("the shortcuts expand into canonical runtime configuration and assembly bindings")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_HOME_PATH_KEY) shouldBe Some(testhome.toString)
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.TEST_HOME_INHERIT_LOCAL_DATA_KEY) shouldBe Some("false")
      RuntimeConfig.getString(bootstrap.configuration, "textus.local-data.root") shouldBe Some(testhome.resolve(".cncf").toString)
      RuntimeConfig.getString(bootstrap.configuration, "textus.datastore.kind") shouldBe Some("local")
      RuntimeConfig.getString(bootstrap.configuration, "textus.datastore.path") shouldBe Some(runtimedb.toString)
      RuntimeConfig.getString(bootstrap.configuration, "textus.component.art-scene.datastores.application.kind") shouldBe Some("local")
      RuntimeConfig.getString(bootstrap.configuration, "textus.component.art-scene.datastores.application.path") shouldBe Some(applicationdb.toString)
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

    "prefer standard .textus configuration over legacy .cncf configuration" in {
      Given("both standard .textus and legacy .cncf configuration files")
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

      Then("the standard .textus value takes precedence")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.webDescriptorKey) shouldBe Some("config/from-textus.yaml")
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

      Then("the project CAR becomes the component file")
      RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.componentFileKey) shouldBe Some(car.toString)
    }

    "resolve a named component from the standard repository for server startup" in {
      Given("a named component CAR in an explicit repository")
      val cwd = Files.createTempDirectory("textus-component-name-repo")
      val repository = cwd.resolve("repository")
      val artifactdir = repository.resolve("org").resolve("simplemodeling").resolve("car").resolve("cwitter").resolve("0.0.1-SNAPSHOT")
      Files.createDirectories(artifactdir)
      val car = artifactdir.resolve("cwitter-0.0.1-SNAPSHOT.car")
      _write_zip(
        car,
        Map(
          "component-descriptor.json" ->
            """{"component":{"name":"cwitter"},"version":"0.0.1-SNAPSHOT"}""",
          "assembly-descriptor.yaml" ->
            """subsystem: cwitter
              |version: 0.0.1-SNAPSHOT
              |components:
              |  - name: cwitter
              |    version: 0.0.1-SNAPSHOT
              |  - name: textus-user-account
              |    version: 0.1.0-SNAPSHOT
              |""".stripMargin
        )
      )
      When("the named component server invocation is resolved")
      val bootstrap = CncfRuntime.bootstrap(
        cwd,
        Array("--repository-dir", repository.toString, "--textus.component=cwitter", "server")
      )
      val specs = bootstrap.repositories.searchRepositories.toOption.get

      val resolved = CncfRuntime.resolveSubsystemInvocation(bootstrap.invocation, specs)

      Then("the repository CAR is appended as the component file")
      resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.componentFileKey}=${car}")
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
        Array("--repository-dir", repository.toString, s"--${RuntimeConfig.componentFileKey}=${explicitcar}", "--textus.component=cwitter", "server")
      )
      val specs = bootstrap.repositories.searchRepositories.toOption.get

      val resolved = CncfRuntime.resolveSubsystemInvocation(bootstrap.invocation, specs)

      Then("the explicit component file remains the only component-file argument")
      resolved.actualArgs.toVector.count(_.startsWith(s"--${RuntimeConfig.componentFileKey}=")) shouldBe 1
      resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.componentFileKey}=${explicitcar}")
    }

    "resolve a named subsystem SAR from the standard repository for server startup" in {
      Given("a named subsystem SAR in an explicit repository")
      val cwd = Files.createTempDirectory("textus-subsystem-name-repo")
      val repository = cwd.resolve("repository")
      val artifactdir = repository.resolve("org").resolve("simplemodeling").resolve("sar").resolve("cwitter").resolve("0.0.1-SNAPSHOT")
      Files.createDirectories(artifactdir)
      val sar = artifactdir.resolve("cwitter-0.0.1-SNAPSHOT.sar")
      _write_zip(
        sar,
        Map(
          "subsystem-descriptor.yaml" ->
            """subsystem: cwitter
              |version: 0.0.1-SNAPSHOT
              |components:
              |  - name: cwitter
              |    version: 0.0.1-SNAPSHOT
              |  - name: textus-user-account
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

      Then("the repository SAR is appended as the subsystem file")
      resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.subsystemFileKey}=${sar}")
    }
    }
  }

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

  private def _write_legacy_development_manifest(root: java.nio.file.Path): Unit = {
    val manifest = root.resolve("target/cncf.d/car-runtime-manifest.json")
    Files.createDirectories(manifest.getParent)
    Files.writeString(
      manifest,
      """{"schemaVersion":"cncf.car-development-runtime-manifest.v1"}"""
    )
  }
}
