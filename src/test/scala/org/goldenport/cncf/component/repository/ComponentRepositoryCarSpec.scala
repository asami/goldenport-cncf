package org.goldenport.cncf.component.repository

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters._
import scala.util.Using

import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import org.goldenport.{Consequence, ConsequenceException}
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.config.{ComponentParameterProvenance, RuntimeConfig}
import org.goldenport.cncf.component.{AssemblyApiClassLoader, CarExtractor, Component, ComponentCreate, ComponentDependencyManifest, ComponentDependencyPool, ComponentDescriptor, ComponentDescriptorLoader, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentLocalFirstClassLoader, ComponentOrigin, CoursierComponentDependencyResolver, RepositoryParameterProbeComponent, RepositoryParameterProbeFactory}
import org.goldenport.cncf.component.identity.ComponentReleaseCoordinate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.SpiResolver
import org.goldenport.cncf.spi.ai.runner.{AiGenerateRequest, AiRunnerSocket}
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemFactory, Subsystem}
import org.goldenport.cncf.component.testutil.CarArchiveFixture
import org.goldenport.cncf.component.repository.fixture.spi.{ActiveAssemblyApiComponent, ActiveAssemblyApiComponentFactory, CanonicalRepositoryParameterProbeComponent, CanonicalRepositoryParameterProbeFactory, PlainFactoryPrimaryComponent, PlainFactoryPrimaryComponentFactory, SearchAssemblyApiComponent, SearchAssemblyApiComponentFactory}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.Protocol
import org.goldenport.configuration.{Configuration, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationTrace

/*
 * @since   Feb.  4, 2026
 *  version Apr. 25, 2026
 *  version May. 25, 2026
 *  version Jul. 31, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentRepositoryCarSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with GivenWhenThen {
  private val _e1 = afterWord("in spec:component-repository-car, example:E1, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e2 = afterWord("in spec:component-repository-car, example:E2, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e3 = afterWord("in spec:component-repository-car, example:E3, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e4 = afterWord("in spec:component-repository-car, example:E4, rules:CID05C-R9, phase:56, slice:CID-05C")
  private val _e5 = afterWord("in spec:component-repository-car, example:E5, rules:CID05C-R9, phase:56, slice:CID-05C")
  private def _metadata(exampleid: String) =
    afterWord(s"in spec:component-repository-car, example:$exampleid, rules:CID05C-R9, phase:56, slice:CID-05C")
  override def beforeAll(): Unit = {
    val workarea = WorkAreaSpace.create(RuntimeConfig.default)
    GlobalContext.set(GlobalContext(workarea))
  }

  "ComponentDirRepository" should {
    "standard and local repository selection" which {
    "E6 exclude Scala 3 package implementation classes from component discovery" must _metadata("E6") {
      "when exercising: exclude Scala 3 package implementation classes from component discovery" in {
      Given("Scala 3 package implementation and ordinary component class names")
      val classnames = Vector(
        "org.example.ArtSceneScalarDatatypes$package",
        "org.example.ArtSceneScalarDatatypes$package$",
        "org.example.ArtSceneScalarDatatypes$package$$anon$1",
        "org.example.ArtSceneComponent$BundleFactory",
        "org.example.PackageFactory"
      )

      When("component discovery classifies each name")
      val discovered =
        classnames.map(ComponentRepository._is_discoverable_component_class)

      Then("package implementation classes are excluded and component classes remain")
      discovered shouldBe Vector(false, false, false, true, true)
      }
    }

    "E7 expose SimpleModeling CAR and SAR standard repository URLs" must _metadata("E7") {
      "when exercising: expose SimpleModeling CAR and SAR standard repository URLs" in {
      Given("the framework-owned standard repository policy")

      When("the CAR and SAR repository URLs are requested")
      val carurl = ComponentRepository.standardComponentRepositoryUrl()
      val sarurl = ComponentRepository.standardSubsystemRepositoryUrl()

      Then("the SimpleModeling repository endpoints are returned")
      carurl shouldBe "https://www.simplemodeling.org/repository/car"
      sarurl shouldBe "https://www.simplemodeling.org/repository/sar"
      }
    }

    "E8 parse SimpleModeling CAR and SAR URLs as standard search repository specs" must _metadata("E8") {
      "when exercising: parse SimpleModeling CAR and SAR URLs as standard search repository specs" in {
      Given("the framework-owned CAR and SAR URLs and one cache root")
      _with_temp_dir { root =>
        When("the URLs are parsed as repository specifications")
        val carspecs =
          ComponentRepository.parseSpecs(
            ComponentRepository.standardComponentRepositoryUrl(),
            root
          ).toOption.get
        val sarspecs =
          ComponentRepository.parseSpecs(
            ComponentRepository.standardSubsystemRepositoryUrl(),
            root
          ).toOption.get

        Then("each URL resolves to its standard repository specification")
        carspecs shouldBe Vector(ComponentRepository.standardComponentRepositorySpec())
        sarspecs shouldBe Vector(ComponentRepository.standardSubsystemRepositorySpec())
      }
      }
    }

    "E9 append SimpleModeling standard URL repositories to the default search set" must _metadata("E9") {
      "when exercising: append SimpleModeling standard URL repositories to the default search set" in {
      Given("an empty explicit repository set with default search enabled")
      _with_temp_dir { cwd =>
        When("default search repositories are appended")
        val resolved = ComponentRepositorySpace.appendDefaultSearchRepositories(
          Right(Vector.empty),
          active = Vector.empty,
          cwd,
          noDefault = false
        ).toOption.get

        Then("the standard CAR and SAR repositories are present")
        resolved should contain (ComponentRepository.standardComponentRepositorySpec())
        resolved should contain (ComponentRepository.standardSubsystemRepositorySpec())
      }
      }
    }

    "E10 use cache as the default standard repository root" must _metadata("E10") {
      "when exercising: use cache as the default standard repository root" in {
      Given("the framework default repository policy")

      When("the standard repository root is resolved")
      val root = ComponentRepository.defaultStandardRepositoryDir()

      Then("the root is located in the CNCF cache")
      root.toString should include (".cncf")
      root.toString should include ("cache")
      }
    }

    "E11 append existing local CNCF repository dirs before standard repositories" must _metadata("E11") {
      "when exercising: append existing local CNCF repository dirs before standard repositories" in {
      Given("an existing local CAR repository under the CNCF user home")
      _with_temp_dir { home =>
        val oldhome = System.getProperty("user.home")
        try {
          System.setProperty("user.home", home.toString)
          val car = ComponentRepository.defaultLocalComponentRepositoryDir()
          Files.createDirectories(car)

          When("default search repositories are appended")
          val resolved = ComponentRepositorySpace.appendDefaultSearchRepositories(
            Right(Vector.empty),
            active = Vector.empty,
            home,
            noDefault = false
          ).toOption.get

          Then("the local repository precedes the standard repository")
          resolved should contain (ComponentRepository.ComponentDirRepository.Specification(car))
          resolved.indexOf(ComponentRepository.ComponentDirRepository.Specification(car)) should be <
            resolved.indexOf(ComponentRepository.standardComponentRepositorySpec())
        } finally {
          if (oldhome == null) System.clearProperty("user.home")
          else System.setProperty("user.home", oldhome)
        }
      }
      }
    }

    "E12 not append cache as a plain component directory" must _metadata("E12") {
      "when exercising: not append cache as a plain component directory" in {
      Given("the standard cache directory exists under the CNCF user home")
      _with_temp_dir { home =>
        val oldhome = System.getProperty("user.home")
        try {
          System.setProperty("user.home", home.toString)
          val cache = ComponentRepository.defaultStandardRepositoryDir()
          Files.createDirectories(cache)

          When("default search repositories are appended")
          val resolved = ComponentRepositorySpace.appendDefaultSearchRepositories(
            Right(Vector.empty),
            active = Vector.empty,
            home,
            noDefault = false
          ).toOption.get

          Then("the cache remains a standard repository rather than a component directory")
          resolved should not contain ComponentRepository.ComponentDirRepository.Specification(cache)
          resolved should contain (ComponentRepository.standardComponentRepositorySpec())
        } finally {
          if (oldhome == null) System.clearProperty("user.home")
          else System.setProperty("user.home", oldhome)
        }
      }
      }
    }

    "E13 not resolve snapshots from the standard cache repository" must _metadata("E13") {
      "when exercising: not resolve snapshots from the standard cache repository" in {
      Given("a SNAPSHOT CAR stored under the standard repository cache")
      _with_temp_dir { cache =>
        val snapshotdir = cache.resolve("car").resolve("sample-component").resolve("0.1.1-SNAPSHOT")
        Files.createDirectories(snapshotdir)
        Files.writeString(snapshotdir.resolve("sample-component-0.1.1-SNAPSHOT.car"), "")
        val spec = ComponentRepository.StandardRepository.Specification(
          ComponentRepository.StandardRepositoryKind.Car,
          "https://example.invalid/repository/car",
          cache
        )

        When("the standard repository resolves that SNAPSHOT coordinate")
        val resolved =
          spec.resolveComponentArchivePath(
            "sample-component",
            Some("0.1.1-SNAPSHOT")
          )

        Then("mutable snapshots are not admitted from the standard cache")
        resolved shouldBe None
      }
      }
    }

    "E14 not discover requested snapshots from the standard cache repository" must _metadata("E14") {
      "when exercising: not discover requested snapshots from the standard cache repository" in {
      Given("a requested SNAPSHOT CAR stored under the standard repository cache")
      val subsystem = new Subsystem(
        name = "test-standard-repo-snapshot",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { cache =>
        val componentid = ComponentId("org.goldenport.cncf.Specification")
        val release = "0.1.1-SNAPSHOT"
        val coordinate = ComponentReleaseCoordinate.require(componentid.sharedIdentity, release)
        val snapshotcar = cache.resolve("car").resolve(coordinate.carRepositoryRelativePath())
        Files.createDirectories(snapshotcar.getParent)
        val fakecomponentjar = _create_fake_component_jar(cache.resolve("assets").resolve("component-main-snapshot.jar"))
        val descriptor = cache.resolve("component-descriptor-snapshot.json")
        Files.writeString(
          descriptor,
          _canonical_descriptor_json(componentid.name, release)
        )
        _create_car(
          snapshotcar,
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> descriptor
          )
        )
        val repository = ComponentRepository.StandardRepository.Specification(
          ComponentRepository.StandardRepositoryKind.Car,
          "https://example.invalid/repository/car",
          cache
        ).build(
          ComponentCreate(
            subsystem,
            origin,
            Vector(_canonical_component_descriptor(componentid, release))
          )
        )

        When("component discovery scans the standard repository")
        val discovered =
          repository
            .discover()
            .flatMap(_.componentDescriptors.flatMap(_.componentName))

        Then("the requested mutable component is not discovered")
        discovered should not contain componentid.name
      }
      }
    }

    "E15 not let the standard repository block a requested local snapshot component" must _metadata("E15") {
      "when exercising: not let the standard repository block a requested local snapshot component" in {
      Given("a local SNAPSHOT CAR before an empty standard repository")
      val subsystem = new Subsystem(
        name = "test-local-snapshot-standard-fallback",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      _with_temp_dir { root =>
        val componentid = ComponentId("org.goldenport.cncf.Specification")
        val release = "0.1.1-SNAPSHOT"
        val localcar = root.resolve("local").resolve("repository").resolve("car")
        val coordinate = ComponentReleaseCoordinate.require(componentid.sharedIdentity, release)
        val artifact = localcar.resolve(coordinate.carRepositoryRelativePath())
        val artifactdir = artifact.getParent
        Files.createDirectories(artifactdir)
        val fakecomponentjar = _create_fake_component_jar(root.resolve("assets").resolve("component-main-local-snapshot.jar"))
        val descriptor = root.resolve("component-descriptor-local-snapshot.json")
        Files.writeString(
          descriptor,
          _canonical_descriptor_json(componentid.name, release)
        )
        _create_car(
          artifact,
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> descriptor
          )
        )
        val descriptors = Vector(
          ComponentDescriptor(
            version = Some(release),
            schemaVersion = Some(3),
            componentId = Some(componentid)
          )
        )
        val space = ComponentRepositorySpace.create(
          subsystem,
          ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
          Vector(
            ComponentRepository.ComponentDirRepository.Specification(localcar),
            ComponentRepository.StandardRepository.Specification(
              ComponentRepository.StandardRepositoryKind.Car,
              "https://example.invalid/repository/car",
              root.resolve("cache")
            )
          ),
          descriptors
        )

        When("component discovery traverses the ordered repositories")
        val components = space.discover()

        Then("the local mutable component is discovered")
        components.flatMap(_.componentDescriptors.flatMap(_.componentName)) should contain ("org.goldenport.cncf.Specification")
      }
      }
    }

    "E16 resolve top-level component invocation by explicit component version" must _metadata("E16") {
      "when exercising: resolve top-level component invocation by explicit component version" in {
      Given("two released CAR versions and an explicit older component version")
      _with_temp_dir { repositoryroot =>
        val componentid = ComponentId("org.goldenport.cncf.Specification")
        val current = ComponentReleaseCoordinate.require(componentid.sharedIdentity, "0.0.2")
        val old = ComponentReleaseCoordinate.require(componentid.sharedIdentity, "0.0.1")
        val currentcar = repositoryroot.resolve("car").resolve(current.carRepositoryRelativePath())
        val oldcar = repositoryroot.resolve("car").resolve(old.carRepositoryRelativePath())
        val currentdir = currentcar.getParent
        val olddir = oldcar.getParent
        Files.createDirectories(currentdir)
        Files.createDirectories(olddir)
        val fakecomponentjar = _create_fake_component_jar(repositoryroot.resolve("assets").resolve("blog-main.jar"))
        val currentdescriptor = repositoryroot.resolve("component-descriptor-blog-current.json")
        val olddescriptor = repositoryroot.resolve("component-descriptor-blog-old.json")
        Files.writeString(
          currentdescriptor,
          _canonical_descriptor_json(componentid.name, "0.0.2")
        )
        Files.writeString(
          olddescriptor,
          _canonical_descriptor_json(componentid.name, "0.0.1")
        )
        _create_car(
          currentcar,
          Seq("component/main.jar" -> fakecomponentjar, "component-descriptor.json" -> currentdescriptor)
        )
        _create_car(
          oldcar,
          Seq("component/main.jar" -> fakecomponentjar, "component-descriptor.json" -> olddescriptor)
        )
        val invocation = org.goldenport.cncf.cli.CncfRuntime.RuntimeInvocationParameters(
          actualArgs = Array(s"--textus.component=${componentid.name}", "--textus.component.version=0.0.1", "server"),
          subsystemName = None,
          componentName = Some(componentid.name),
          componentVersion = Some("0.0.1")
        )

        When("the top-level component invocation is resolved")
        val resolved = org.goldenport.cncf.cli.CncfRuntime.resolveComponentInvocation(
          invocation,
          Vector(ComponentRepository.StandardRepository.Specification(
            ComponentRepository.StandardRepositoryKind.Car,
            ComponentRepository.standardComponentRepositoryUrl(),
            repositoryroot
          ))
        )

        Then("the explicitly requested CAR becomes the component file")
        resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.componentFileKey}=${oldcar}")
      }
      }
    }

    "E17 skip a stale local CAR version when an explicit version is available from the next repository" must _metadata("E17") {
      "when exercising: skip a stale local CAR version when an explicit version is available from the next repository" in {
      Given("a stale local snapshot and the requested public CAR version")
      _with_temp_dir { repositoryroot =>
        val localroot = repositoryroot.resolve("local")
        val remoteroot = repositoryroot.resolve("remote")
        val componentid = ComponentId("org.goldenport.cncf.Specification")
        val localcoordinate = ComponentReleaseCoordinate.require(componentid.sharedIdentity, "0.1.1-SNAPSHOT")
        val remotecoordinate = ComponentReleaseCoordinate.require(componentid.sharedIdentity, "0.2.0")
        val localcar = localroot.resolve("car").resolve(localcoordinate.carRepositoryRelativePath())
        val remotecar = remoteroot.resolve("car").resolve(remotecoordinate.carRepositoryRelativePath())
        val localdir = localcar.getParent
        val remotedir = remotecar.getParent
        Files.createDirectories(localdir)
        Files.createDirectories(remotedir)
        val localdescriptor = repositoryroot.resolve("local-component-descriptor.json")
        val remotedescriptor = repositoryroot.resolve("remote-component-descriptor.json")
        Files.writeString(
          localdescriptor,
          _canonical_descriptor_json(componentid.name, "0.1.1-SNAPSHOT")
        )
        Files.writeString(
          remotedescriptor,
          _canonical_descriptor_json(componentid.name, "0.2.0")
        )
        _create_car(localcar, Seq("component-descriptor.json" -> localdescriptor))
        _create_car(remotecar, Seq("component-descriptor.json" -> remotedescriptor))
        val invocation = org.goldenport.cncf.cli.CncfRuntime.RuntimeInvocationParameters(
          actualArgs = Array(s"--textus.component=${componentid.name}", "--textus.component.version=0.2.0", "command"),
          subsystemName = None,
          componentName = Some(componentid.name),
          componentVersion = Some("0.2.0")
        )

        When("the explicit component version is resolved across local and remote repositories")
        val resolved = org.goldenport.cncf.cli.CncfRuntime.resolveComponentInvocation(
          invocation,
          Vector(
            ComponentRepository.ComponentDirRepository.Specification(localroot),
            ComponentRepository.ComponentDirRepository.Specification(remoteroot)
          )
        )

        Then("the public version is selected instead of the stale local snapshot")
        resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.componentFileKey}=${remotecar}")
        resolved.actualArgs.toVector should not contain s"--${RuntimeConfig.componentFileKey}=${localcar}"
      }
      }
    }

    "E18 reject a component file when its CAR descriptor does not match the requested version" must _metadata("E18") {
      "when exercising: reject a component file when its CAR descriptor does not match the requested version" in {
      Given("a component-file CAR with a fixed descriptor version")
      _with_temp_dir { root =>
        val descriptor = root.resolve("component-descriptor.json")
        val car = root.resolve("textus-sanpomap-0.1.1-SNAPSHOT.car")
        Files.writeString(
          descriptor,
          _canonical_descriptor_json("org.goldenport.cncf.Specification", "0.1.1-SNAPSHOT")
        )
        _create_car(car, Seq("component-descriptor.json" -> descriptor))
        val spec = ComponentRepository.ComponentFileRepository.Specification(car)

        When("component-file resolution receives a matching and a different explicit version")
        val matching = spec.resolveComponentArchivePath("org.goldenport.cncf.Specification", Some("0.1.1-SNAPSHOT"))
        val mismatched = spec.resolveComponentArchivePath("org.goldenport.cncf.Specification", Some("0.2.0"))

        Then("only the matching descriptor version can resolve the supplied CAR")
        matching shouldBe Some(car)
        mismatched shouldBe None
      }
      }
    }

    }

    "development and expanded repository routes" which {
    "E19 parse explicit development and expanded CAR directory routes" must _metadata("E19") {
      "when exercising: parse explicit development and expanded CAR directory routes" in {
      Given("explicit component development and expanded CAR directory arguments")

      When("repository arguments are extracted")
      val extracted = ComponentRepositorySpace.extractRepositoryArgs(
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        Array("--component-dev-dir", "/tmp/dev-component", "--component-car-dir=/tmp/debug.car.d", "command")
      )

      Then("both repository routes are active and the command remains residual")
      extracted.active shouldBe Right(Vector("component-dev-dir:/tmp/dev-component", "component-dir:/tmp/debug.car.d"))
      extracted.residual.toVector shouldBe Vector("command")
      }
    }

    "E20 preserve repository-looking tokens after the command sentinel" must _metadata("E20") {
      "when exercising: preserve repository-looking tokens after the command sentinel" in {
      Given("one command sentinel followed by component and repository option spellings")

      When("repository arguments are extracted")
      val extracted = ComponentRepositorySpace.extractRepositoryArgs(
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        Array("command", "--", "--component-file=/command-domain-only.car", "--repository-dir=/command-domain-only")
      )

      Then("no post-sentinel token becomes a repository and the full command tail remains residual")
      extracted.active shouldBe Right(Vector.empty)
      extracted.search shouldBe Right(Vector.empty)
      extracted.residual.toVector shouldBe Vector(
        "command",
        "--",
        "--component-file=/command-domain-only.car",
        "--repository-dir=/command-domain-only"
      )
      }
    }

    "E21 reject explicit component development directory without runtime classpath" must _metadata("E21") {
      "when exercising: reject explicit component development directory without runtime classpath" in {
      Given("a component development directory without its runtime classpath file")
      _with_temp_dir { root =>
        val componentdir = root.resolve("component")
        Files.createDirectories(componentdir.resolve("target"))

        When("the component development route is parsed")
        val parsed = ComponentRepository.parseSpecs(s"component-dev-dir:${componentdir}", root)

        Then("the missing runtime classpath fails without packaged-CAR fallback")
        parsed.left.toOption.get should include ("runtime classpath file is missing or empty")
        parsed.left.toOption.get should include (componentdir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt").toString)
        parsed.left.toOption.get should include ("will not fall back to a packaged CAR")
      }
      }
    }

    "E22 fail repository space creation when configured component development directory is invalid" must _metadata("E22") {
      "when exercising: fail repository space creation when configured component development directory is invalid" in {
      Given("runtime configuration naming an invalid component development directory")
      _with_temp_dir { root =>
        val componentdir = root.resolve("component")
        Files.createDirectories(componentdir)
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.componentDevDirKey -> ConfigurationValue.StringValue(componentdir.toString)
          )),
          ConfigurationTrace.empty
        )

        When("repository space creation consumes that configuration")
        val thrown = intercept[IllegalStateException] {
          ComponentRepositorySpace.create(TestComponentFactory.emptySubsystem("dev-dir-fail-fast"), root, configuration)
        }

        Then("creation fails with the runtime-classpath and no-fallback contract")
        thrown.getMessage should include ("runtime classpath file is missing or empty")
        thrown.getMessage should include ("will not fall back to a packaged CAR")
      }
      }
    }

    "E23 reject CAR schemes in component development directory configuration" must _metadata("E23") {
      "when exercising: reject CAR schemes in component development directory configuration" in {
      Given("a packaged-CAR scheme supplied as a component development directory")
      _with_temp_dir { root =>
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.repositoryComponentDevDirKey -> ConfigurationValue.StringValue("component-dir:/tmp/packaged-cars")
          )),
          ConfigurationTrace.empty
        )

        When("the configured development repository is resolved")
        val extracted = ComponentRepositorySpace.extractRepositoryArgs(configuration, Array("server"))
        val parsed = ComponentRepositorySpace.resolveSpecifications(extracted.search, root, noDefault = true)

        Then("the development-only configuration rejects the packaged route")
        parsed.left.toOption.get should include ("component development directory configuration must be a plain path or component-dev-dir:path")
      }
      }
    }

    "E24 treat repository component development directories as direct dev-dir repositories" must _metadata("E24") {
      "when exercising: treat repository component development directories as direct dev-dir repositories" in {
      Given("a configured component development directory with a runtime classpath")
      _with_temp_dir { root =>
        val componentdir = root.resolve("textus-user-account")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        Files.createDirectories(classdir)
        _write_runtime_classpath(componentdir, classdir, "textus-user-account", "0.1.0-SNAPSHOT", "textus-user-account")
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.repositoryComponentDevDirKey -> ConfigurationValue.StringValue(componentdir.toString)
          )),
          ConfigurationTrace.empty
        )

        When("the repository configuration is resolved")
        val extracted = ComponentRepositorySpace.extractRepositoryArgs(configuration, Array("server"))
        val specs = ComponentRepositorySpace.resolveSpecifications(extracted.search, root, noDefault = true).toOption.get

        Then("the directory becomes one direct development repository")
        specs shouldBe Vector(ComponentRepository.ComponentDevDirRepository.Specification(componentdir))
      }
      }
    }

    "E25 parse explicit expanded SAR directory routes" must _metadata("E25") {
      "when exercising: parse explicit expanded SAR directory routes" in {
      Given("explicit subsystem development and expanded SAR directory arguments")

      When("repository arguments are extracted")
      val extracted = ComponentRepositorySpace.extractRepositoryArgs(
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        Array("--subsystem-dev-dir", "/tmp/app", "--subsystem-sar-dir", "/tmp/debug.sar.d", "--textus.subsystem.sar.dir=/tmp/configured.sar.d", "command")
      )

      Then("every subsystem route is active and the command remains residual")
      extracted.active shouldBe Right(Vector("subsystem-dev-dir:/tmp/app", "component-dir:/tmp/debug.sar.d", "component-dir:/tmp/configured.sar.d"))
      extracted.residual.toVector shouldBe Vector("command")
      }
    }

    "E26 activate subsystem development directory from runtime config alias" must _metadata("E26") {
      "when exercising: activate subsystem development directory from runtime config alias" in {
      Given("the runtime subsystem development-directory alias")
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          "textus.runtime.subsystem.dev.dir" -> ConfigurationValue.StringValue("/tmp/app")
        )),
        ConfigurationTrace.empty
      )

      When("repository arguments are extracted")
      val extracted = ComponentRepositorySpace.extractRepositoryArgs(configuration, Array("command"))

      Then("the aliased directory becomes an active development route")
      extracted.active shouldBe Right(Vector("subsystem-dev-dir:/tmp/app"))
      extracted.residual.toVector shouldBe Vector("command")
      }
    }

    "E27 resolve subsystem descriptor from a subsystem development directory" must _metadata("E27") {
      "when exercising: resolve subsystem descriptor from a subsystem development directory" in {
      Given("a subsystem development directory containing one descriptor")
      _with_temp_dir { root =>
        Files.createDirectories(root.resolve("subsystem"))
        Files.writeString(
          root.resolve("subsystem").resolve("subsystem-descriptor.yaml"),
          """subsystem: cwitter
            |version: 0.1.0
            |components:
            |  - namespace: org.example
            |    id: Cwitter
            |    version: 0.1.0
            |""".stripMargin
        )

        val spec = ComponentRepository.SubsystemDevDirRepository.Specification(root)

        When("the named subsystem descriptor is resolved")
        val resolved = spec.resolveSubsystemDescriptor("cwitter")

        Then("the development descriptor is returned")
        resolved.map(_.subsystemName) shouldBe Some("cwitter")
      }
      }
    }

    "E28 infer subsystem name from explicit subsystem development directory" must _metadata("E28") {
      "when exercising: infer subsystem name from explicit subsystem development directory" in {
      Given("an explicit subsystem development directory with one descriptor")
      _with_temp_dir { root =>
        Files.createDirectories(root.resolve("subsystem"))
        Files.writeString(
          root.resolve("subsystem").resolve("subsystem-descriptor.yaml"),
          """subsystem: cwitter
            |version: 0.1.0
            |components:
            |  - namespace: org.example
            |    id: Cwitter
            |    version: 0.1.0
            |""".stripMargin
        )

        val bootstrap = org.goldenport.cncf.cli.CncfRuntime.bootstrap(
          root,
          Array("--subsystem-dev-dir", root.toString, "server")
        )

        When("the runtime resolves its subsystem descriptor")
        val descriptor = org.goldenport.cncf.subsystem.GenericSubsystemFactory.resolveDescriptor(bootstrap.configuration)

        Then("the descriptor supplies the subsystem name")
        descriptor.map(_.subsystemName) shouldBe Some("cwitter")
      }
      }
    }

    "E1 infer subsystem name from explicit component CAR directory" must _e1 {
      "when exercising: infer subsystem name from explicit component CAR directory" in {
        Given("an expanded component CAR directory with one component descriptor")
        _with_temp_dir { root =>
          val cardir = root.resolve("app.car.d")
          Files.createDirectories(cardir.resolve("component"))
          Files.writeString(
            cardir.resolve("component-descriptor.yaml"),
            """schemaVersion: 3
              |component:
              |  namespace: org.example
              |  id: Cwitter
              |  version: 0.1.0
              |""".stripMargin
          )

          val bootstrap = org.goldenport.cncf.cli.CncfRuntime.bootstrap(
            root,
            Array("--component-car-dir", cardir.toString, "server")
          )

          When("the runtime resolves its synthesized subsystem descriptor")
          val descriptor = org.goldenport.cncf.subsystem.GenericSubsystemFactory.resolveDescriptor(bootstrap.configuration)

          Then("the component identity supplies the subsystem and binding names")
          descriptor.map(_.subsystemName) shouldBe Some("Cwitter")
          descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("org.example.Cwitter")
        }
      }
    }

    "E29 infer subsystem name from explicit component development directory" must _metadata("E29") {
      "when exercising: infer subsystem name from explicit component development directory" in {
      Given("a component development directory with runtime classes and a descriptor")
      _with_temp_dir { root =>
        val componentdir = root.resolve("component")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        Files.createDirectories(classdir)
        _write_runtime_classpath(componentdir, classdir, "cwitter", "0.1.0", "cwitter")
        Files.createDirectories(componentdir.resolve("src").resolve("main").resolve("car"))
        Files.writeString(
          componentdir.resolve("src").resolve("main").resolve("car").resolve("component-descriptor.yaml"),
          """name: cwitter
            |version: 0.1.0
            |component: cwitter
            |""".stripMargin
        )

        val bootstrap = org.goldenport.cncf.cli.CncfRuntime.bootstrap(
          root,
          Array("--component-dev-dir", componentdir.toString, "server")
        )

        When("the runtime resolves its synthesized subsystem descriptor")
        val descriptor = org.goldenport.cncf.subsystem.GenericSubsystemFactory.resolveDescriptor(bootstrap.configuration)

        Then("the development component supplies the subsystem and binding names")
        descriptor.map(_.subsystemName) shouldBe Some("Cwitter")
        descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("org.goldenport.fixture.Cwitter")
      }
      }
    }

    "E30 not auto-append a packaged CAR when component development directory is explicit" must _metadata("E30") {
      "when exercising: not auto-append a packaged CAR when component development directory is explicit" in {
      Given("an explicit component development directory beside a packaged CAR")
      _with_temp_dir { root =>
        val componentdir = root.resolve("component")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        Files.createDirectories(classdir)
        _write_runtime_classpath(componentdir, classdir, "dev-cwitter", "0.1.0", "dev-cwitter")
        Files.createDirectories(componentdir.resolve("src").resolve("main").resolve("car"))
        Files.writeString(
          componentdir.resolve("src").resolve("main").resolve("car").resolve("component-descriptor.yaml"),
          """name: dev-cwitter
            |version: 0.1.0
            |component: dev-cwitter
            |""".stripMargin
        )
        val packageddescriptor = root.resolve("component-descriptor-packaged.json")
        Files.writeString(
          packageddescriptor,
          _canonical_descriptor_json("org.goldenport.cncf.Specification", "0.1.0")
        )
        val packagedjar = _create_fake_component_jar(root.resolve("assets").resolve("component-main.jar"))
        Files.createDirectories(componentdir.resolve("target"))
        _create_car(
          componentdir.resolve("target").resolve("packaged-cwitter.car"),
          Seq(
            "component/main.jar" -> packagedjar,
            "component-descriptor.json" -> packageddescriptor
          )
        )

        val bootstrap = org.goldenport.cncf.cli.CncfRuntime.bootstrap(
          root,
          Array("--component-dev-dir", componentdir.toString, "server")
        )

        When("the runtime resolves the explicit development source")
        val descriptor = org.goldenport.cncf.subsystem.GenericSubsystemFactory.resolveDescriptor(bootstrap.configuration)

        Then("the packaged CAR is not appended and development identity wins")
        RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.componentFileKey) shouldBe empty
        descriptor.map(_.subsystemName) shouldBe Some("DevCwitter")
        descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("org.goldenport.fixture.DevCwitter")
      }
      }
    }

    "E31 resolve only the prepared generated descriptor from a component development directory" must _metadata("E31") {
      "when exercising: resolve only the prepared generated descriptor from a component development directory" in {
      Given("a schema-3 generated descriptor and a later competing source descriptor")
      _with_temp_dir { componentdir =>
        val classdir = Files.createDirectories(componentdir.resolve("target/scala-3.3.8/classes"))
        _write_runtime_classpath(
          componentdir,
          classdir,
          "sample-car-artifact",
          "0.1.0-SNAPSHOT",
          "sample-component"
        )
        Files.writeString(
          componentdir.resolve("src/main/car/component-descriptor.json"),
          """{"name":"source-override","version":"9.9.9","component":"source-override"}""",
          StandardCharsets.UTF_8
        )
        Files.writeString(
          componentdir.resolve("assembly-descriptor.yaml"),
          """subsystem: root-assembly-override
            |version: 9.9.9
            |components:
            |  - component: root-assembly-override
            |""".stripMargin,
          StandardCharsets.UTF_8
        )

        When("development admission and descriptor resolution run after source mutation")
        val admitted = ComponentRepository.ComponentDevDirRepository.validate(componentdir)
        val descriptors = ComponentDescriptorLoader.load(
          componentdir.resolve("target/cncf.d/component-descriptor.json")
        ) match {
          case Consequence.Success(values) => values
          case Consequence.Failure(conclusion) => fail(conclusion.show)
        }
        val developmentdescriptors = ComponentRepository.ComponentDevDirRepository.devComponentDescriptors(componentdir)
        val resolved = ComponentRepository.ComponentDevDirRepository.Specification(componentdir).
          resolveComponentDescriptor("org.goldenport.fixture.SampleComponent")
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.componentDevDirKey -> ConfigurationValue.StringValue(componentdir.toString)
          )),
          ConfigurationTrace.empty
        )
        val factoryresolved = GenericSubsystemFactory.resolveDescriptorC(configuration)

        Files.writeString(
          componentdir.resolve("target/cncf.d/component-descriptor.json"),
          """{"name":"stale-target","version":"9.9.9","component":"stale-target"}""",
          StandardCharsets.UTF_8
        )
        val stale = GenericSubsystemFactory.resolveDescriptorC(configuration)

        Then("the prepared target descriptor remains the sole runtime authority")
        admitted.toOption shouldBe Some(())
        descriptors.map(_.componentName) shouldBe Vector(Some("org.goldenport.fixture.SampleComponent"))
        developmentdescriptors shouldBe descriptors
        resolved.flatMap(_.name) shouldBe Some("org.goldenport.fixture.SampleComponent")
        resolved.flatMap(_.componentName) shouldBe Some("org.goldenport.fixture.SampleComponent")
        factoryresolved.toOption.flatten.map(_.path) shouldBe Some(componentdir)
        factoryresolved.toOption.flatten.map(_.subsystemName) shouldBe Some("SampleComponent")
        factoryresolved.toOption.flatten.flatMap(_.version) shouldBe Some("0.1.0-SNAPSHOT")
        factoryresolved.toOption.flatten.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("org.goldenport.fixture.SampleComponent")
        stale match {
          case Consequence.Failure(conclusion) =>
            conclusion.observation.taxonomy.category.name shouldBe "resource"
            conclusion.observation.taxonomy.symptom.name shouldBe "invalid"
            conclusion.display should include("development-side runtime evidence error")
          case Consequence.Success(_) =>
            fail("stale prepared descriptor must not fall back to source metadata")
        }
      }
      }
    }

    "E32 infer component development directory identity from discovered component metadata" must _metadata("E32") {
      "when exercising: infer component development directory identity from discovered component metadata" in {
      Given("a component development directory whose compiled metadata names its component")
      _with_temp_dir { root =>
        val componentdir = root.resolve("01-minimal")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        _copy_devdir_sample_classes(classdir)
        _write_runtime_classpath(
          componentdir,
          classdir,
          "devdirsample",
          "0.1.0-SNAPSHOT",
          "devdirsample",
          componentid = Some("org.goldenport.fixture.DevDirSample"),
          subsystemname = Some("devdirsample")
        )
        Files.writeString(
          componentdir.resolve("src/main/car/assembly-descriptor.yaml"),
          """subsystem: devdirsample
            |components:
            |  - namespace: org.goldenport.fixture
            |    id: DevDirSample
            |    version: 0.1.0-SNAPSHOT
            |subsystemCapabilities:
            |  providers:
            |    - name: runtime-facilities
            |      component: org.goldenport.fixture.DevDirSample
            |      provides:
            |        - datastore.optimistic-concurrency@1
            |        - datastore.persistent@1
            |        - datastore.transactional@1
            |        - user-context.current@1
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.componentDevDirKey -> ConfigurationValue.StringValue(componentdir.toString)
          )),
          ConfigurationTrace.empty
        )
        val testdescriptor = _controlled_test_descriptor(root, "component-dev-dir-identity")

        When("identity inference, descriptor resolution, and runtime initialization complete")
        val inferred = ComponentRepository.ComponentDevDirRepository.inferComponentNames(componentdir)
        val descriptor = org.goldenport.cncf.subsystem.GenericSubsystemFactory.resolveDescriptor(configuration)
        val initialized = new org.goldenport.cncf.cli.CncfRuntime().initializeForEmbedding(
          cwd = root,
          args = Array(
            s"--textus.test.descriptor=$testdescriptor",
            "--component-dev-dir", componentdir.toString,
            "--textus.subsystem=devdirsample",
            "command", "devdirsample.main.hello"
          ),
          modeHint = Some(org.goldenport.cncf.cli.RunMode.Command)
        ).TAKE

        Then("every boundary retains one inferred development component identity")
        inferred shouldBe Vector("org.goldenport.fixture.DevDirSample")
        descriptor.map(_.subsystemName) shouldBe Some("devdirsample")
        descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("org.goldenport.fixture.DevDirSample")
        initialized.components.map(_.name) should contain ("org.goldenport.fixture.DevDirSample")
        initialized.components.count(_.name == "org.goldenport.fixture.DevDirSample") shouldBe 1
        initialized.components.find(_.name == "org.goldenport.fixture.DevDirSample").map(_.getClass.getName) shouldBe
          Some(classOf[devdirsample.DevDirSamplePrimaryComponent].getName)
      }
      }
    }

    "E33 treat runtime subsystem source aliases as explicit activation during name resolution" must _metadata("E33") {
      "when exercising: treat runtime subsystem source aliases as explicit activation during name resolution" in {
      Given("a component repository and an explicit runtime subsystem source alias")
      _with_temp_dir { componentdir =>
        val carpath = componentdir.resolve("testcomp.car")
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val descriptor = componentdir.resolve("component-descriptor-testcomp.json")
        Files.writeString(
          descriptor,
          """{"name":"testcomp","version":"0.1.0","component":"testcomp"}"""
        )
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> descriptor
          )
        )
        val invocation = org.goldenport.cncf.cli.CncfRuntime.RuntimeInvocationParameters(
          actualArgs = Array("--textus.runtime.subsystem.dev.dir=/tmp/app", "command"),
          subsystemName = None,
          componentName = Some("testcomp")
        )

        When("the component invocation is resolved")
        val resolved = org.goldenport.cncf.cli.CncfRuntime.resolveComponentInvocation(
          invocation,
          Vector(ComponentRepository.ComponentDirRepository.Specification(componentdir))
        )

        Then("the explicit activation prevents repository file injection")
        resolved.actualArgs.toVector shouldBe invocation.actualArgs.toVector
      }
      }
    }

    "E34 treat expanded SAR directory route as explicit activation during name resolution" must _metadata("E34") {
      "when exercising: treat expanded SAR directory route as explicit activation during name resolution" in {
      Given("a component repository and an explicit expanded SAR route")
      _with_temp_dir { componentdir =>
        val carpath = componentdir.resolve("testcomp.car")
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val descriptor = componentdir.resolve("component-descriptor-testcomp.json")
        Files.writeString(
          descriptor,
          """{"name":"testcomp","version":"0.1.0","component":"testcomp"}"""
        )
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> descriptor
          )
        )
        val invocation = org.goldenport.cncf.cli.CncfRuntime.RuntimeInvocationParameters(
          actualArgs = Array("--subsystem-sar-dir=/tmp/debug.sar.d", "command"),
          subsystemName = None,
          componentName = Some("testcomp")
        )

        When("the component invocation is resolved")
        val resolved = org.goldenport.cncf.cli.CncfRuntime.resolveComponentInvocation(
          invocation,
          Vector(ComponentRepository.ComponentDirRepository.Specification(componentdir))
        )

        Then("the expanded route prevents repository file injection")
        resolved.actualArgs.toVector shouldBe invocation.actualArgs.toVector
      }
      }
    }

    "E35 not auto-activate cwd sar.d as a default active repository" must _metadata("E35") {
      "when exercising: not auto-activate cwd sar.d as a default active repository" in {
      Given("a current working directory containing sar.d")
      _with_temp_dir { cwd =>
        Files.createDirectories(cwd.resolve("sar.d"))

        When("default active repositories are appended")
        val resolved = ComponentRepositorySpace.appendDefaultActiveRepositories(
          Right(Vector.empty),
          cwd,
          noDefault = false
        ).toOption.get

        Then("sar.d is not activated implicitly")
        resolved should not contain ComponentRepository.ComponentDirRepository.Specification(cwd.resolve("sar.d").normalize)
      }
      }
    }

    "E36 not auto-activate cwd component.d as a default active repository" must _metadata("E36") {
      "when exercising: not auto-activate cwd component.d as a default active repository" in {
      _with_temp_dir { cwd =>
        Given("a current working directory with component.d")
        Files.createDirectories(cwd.resolve("component.d"))

        When("default active repositories are appended")
        val resolved = ComponentRepositorySpace.appendDefaultActiveRepositories(
          Right(Vector.empty),
          cwd,
          noDefault = false
        ).toOption.get

        Then("component.d is not auto-activated as an active repository")
        resolved should not contain ComponentRepository.ComponentDirRepository.Specification(cwd.resolve("component.d").normalize)
      }
      }
    }

    "E37 not append default component target when an explicit component development directory is active" must _metadata("E37") {
      "when exercising: not append default component target when an explicit component development directory is active" in {
      Given("an explicit component development repository in the current project")
      _with_temp_dir { cwd =>
        val devdir = cwd.resolve("component")
        Files.createDirectories(devdir)
        Files.createDirectories(cwd.resolve("component").resolve("target"))

        When("default active repositories are appended")
        val resolved = ComponentRepositorySpace.appendDefaultActiveRepositories(
          Right(Vector(ComponentRepository.ComponentDevDirRepository.Specification(devdir))),
          cwd,
          noDefault = false
        ).toOption.get

        Then("the explicit development repository remains the only active target")
        resolved shouldBe Vector(ComponentRepository.ComponentDevDirRepository.Specification(devdir))
      }
      }
    }

    "E38 treat equals-form expanded CAR route as an explicit activation during name resolution" must _metadata("E38") {
      "when exercising: treat equals-form expanded CAR route as an explicit activation during name resolution" in {
      Given("a component repository and an equals-form expanded CAR route")
      _with_temp_dir { componentdir =>
        val carpath = componentdir.resolve("testcomp.car")
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val descriptor = componentdir.resolve("component-descriptor-testcomp.json")
        Files.writeString(
          descriptor,
          """{"name":"testcomp","version":"0.1.0","component":"testcomp"}"""
        )
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> descriptor
          )
        )
        val invocation = org.goldenport.cncf.cli.CncfRuntime.RuntimeInvocationParameters(
          actualArgs = Array("--component-car-dir=/tmp/debug.car.d", "command"),
          subsystemName = None,
          componentName = Some("testcomp")
        )

        When("the component invocation is resolved")
        val resolved = org.goldenport.cncf.cli.CncfRuntime.resolveComponentInvocation(
          invocation,
          Vector(ComponentRepository.ComponentDirRepository.Specification(componentdir))
        )

        Then("the expanded route prevents repository file injection")
        resolved.actualArgs.toVector shouldBe invocation.actualArgs.toVector
      }
      }
    }

    "E2 resolve a canonical descriptor from an expanded CAR directory" must _e2 {
      "when exercising: resolve a canonical descriptor from an expanded CAR directory" in {
        Given("an expanded CAR directory with one canonical primary component and two componentlets")
        _with_temp_dir { componentdir =>
          _create_fake_component_jar(componentdir.resolve("component").resolve("main.jar"))
          val descriptorpath = componentdir.resolve("component-descriptor.json")
          Files.writeString(
            descriptorpath,
            """{
              |  "schemaVersion": 3,
              |  "component": {
              |    "namespace": "org.goldenport.fixture",
              |    "id": "SampleComponent",
              |    "version": "0.1.0"
              |  },
              |  "componentlets": [
              |    {
              |      "name": "public-notice",
              |      "kind": "componentlet",
              |      "isPrimary": false
              |    },
              |    {
              |      "name": "notice-admin",
              |      "kind": "componentlet",
              |      "isPrimary": false
              |    }
              |  ]
              |}""".stripMargin
            )

          When("the descriptor is resolved through its qualified primary identity")
          val descriptor = ComponentRepository.resolveComponentDescriptorFromComponentDir(
            componentdir,
            "org.goldenport.fixture.SampleComponent"
          )

          Then("the canonical primary component and complete componentlet set are retained")
          descriptor.flatMap(_.componentId).map(_.name) shouldBe Some("org.goldenport.fixture.SampleComponent")
          descriptor.toVector.flatMap(_.componentlets.map(_.name)) shouldBe Vector("public-notice", "notice-admin")
        }
      }
    }

    "E3 reject a legacy CAR descriptor during repository discovery" must _e3 {
      "when exercising: reject a legacy CAR descriptor during repository discovery" in {
        Given("a CAR containing a legacy component descriptor")
        val subsystem = new Subsystem(
          name = "test",
          configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
        )
        val origin = ComponentOrigin.Repository("component-dir")
        _with_temp_dir { componentdir =>
          val carpath = componentdir.resolve("demo-component.car")
          val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
          val descriptor = componentdir.resolve("component-descriptor-car.json")
          Files.writeString(
            descriptor,
            """{"name":"demo-component","version":"0.1.0","component":"spec","extension":{"driver":"car"},"config":{"log.level":"info"}}"""
          )
          _create_car(
            carpath,
            Seq(
              "component/main.jar" -> fakecomponentjar,
              "component-descriptor.json" -> descriptor
            )
          )
          val repository = new ComponentRepository.ComponentDirRepository(componentdir, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())

          When("the component directory repository discovers the CAR")
          val components = repository.discover()

          Then("the legacy artifact is rejected rather than admitted through a compatibility path")
          components shouldBe empty
        }
      }
    }

    "E4 discover canonical packed and expanded CARs through the repository boundary" must _e4 {
      "when exercising: discover canonical packed and expanded CARs through the repository boundary" in {
        Given("packed and expanded CAR layouts with the same canonical descriptor and factory")
        val subsystem = TestComponentFactory.emptySubsystem("canonical-car-boundary")
        val origin = ComponentOrigin.Repository("component-dir")
        _with_temp_dir { root =>
          val componentid = "org.goldenport.fixture.CanonicalArchiveFixture"
          val release = "2026.08-rc_1+portable"
          val componentjar = _create_class_component_jar(
            root.resolve("assets").resolve("canonical-archive-fixture.jar"),
            Seq(classOf[CanonicalArchiveFixtureFactory], classOf[CanonicalArchiveFixtureComponent])
          )
          val content = Files.createDirectories(root.resolve("content"))
          Files.createDirectories(content.resolve("component"))
          val descriptor = content.resolve("component-descriptor.json")
          Files.writeString(descriptor, _canonical_descriptor_json(componentid, release), StandardCharsets.UTF_8)
          Files.copy(componentjar, content.resolve("component").resolve("main.jar"))
          _write_packaged_runtime_evidence(content, componentid, release)
          val packedroot = Files.createDirectories(root.resolve("packed"))
          val packedcar = packedroot.resolve("presentation-independent.car")
          _create_car(
            packedcar,
            Seq(
              "component/main.jar" -> content.resolve("component").resolve("main.jar"),
              "component-descriptor.json" -> descriptor,
              "abi-manifest.json" -> content.resolve("abi-manifest.json"),
              "car-runtime-manifest.json" -> content.resolve("car-runtime-manifest.json")
            )
          )
          val expanded = Files.createDirectories(root.resolve("expanded"))
          Files.createDirectories(expanded.resolve("component"))
          Files.copy(content.resolve("component").resolve("main.jar"), expanded.resolve("component").resolve("main.jar"))
          Files.copy(descriptor, expanded.resolve("component-descriptor.json"))
          Files.copy(content.resolve("abi-manifest.json"), expanded.resolve("abi-manifest.json"))
          Files.copy(content.resolve("car-runtime-manifest.json"), expanded.resolve("car-runtime-manifest.json"))

          When("ComponentDirRepository extracts, loads, discovers, and materializes each layout")
          val packed = new ComponentRepository.ComponentDirRepository(
            packedroot,
            ComponentCreate(subsystem, origin),
            ComponentRepository.resolvePackagePrefixes()
          ).discover()
          val cardir = new ComponentRepository.ComponentDirRepository(
            expanded,
            ComponentCreate(subsystem, origin),
            ComponentRepository.resolvePackagePrefixes()
          ).discover()

          Then("both paths retain the descriptor-owned coordinate as artifact metadata and expose their factory")
          Vector(packed, cardir).foreach { components =>
            components.map(_.core.componentId.name) shouldBe Vector(componentid)
            components.head.artifactMetadata.map(_.componentId.map(_.name)) shouldBe Some(Some(componentid))
            components.head.artifactMetadata.map(_.version) shouldBe Some(release)
            components.head.factoryOption should not be empty
          }
        }
      }
    }

    }

    "packaged CAR and SAR discovery" which {
    "E39 not discover unrelated CARs when an explicitly requested component is absent" must _metadata("E39") {
      "when exercising: not discover unrelated CARs when an explicitly requested component is absent" in {
      Given("a component repository containing an unrelated CAR and an explicit missing component request")
      val subsystem = TestComponentFactory.emptySubsystem("explicit-component-selection")
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val carpath = componentdir.resolve("unrelated-component.car")
        val componentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("unrelated-main.jar"))
        val descriptor = componentdir.resolve("component-descriptor-unrelated.json")
        Files.writeString(
          descriptor,
          _canonical_descriptor_json("org.goldenport.cncf.Specification", "0.1.0")
        )
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> componentjar,
            "component-descriptor.json" -> descriptor
          )
        )
        val requested = _canonical_component_descriptor(
          ComponentId("org.goldenport.fixture.MissingComponent"),
          "0.1.0"
        )
        val params = ComponentCreate(subsystem, origin, Vector(requested))
        val repository = new ComponentRepository.ComponentDirRepository(
          componentdir,
          params,
          ComponentRepository.resolvePackagePrefixes()
        )

        When("the repository resolves the explicit request")
        val components = repository.discover()

        Then("it does not fall back to scanning every CAR")
        components shouldBe empty
      }
      }
    }

    "E40 preserve repository priority when the same component coordinate exists later" must _metadata("E40") {
      "when exercising: preserve repository priority when the same component coordinate exists later" in {
      Given("an active component directory followed by a stale local component directory")
      _with_temp_dir { root =>
        val active = Files.createDirectories(root.resolve("active"))
        val stale = Files.createDirectories(root.resolve("stale"))
        Vector(active, stale).foreach { componentdir =>
          val componentjar = _create_fake_component_jar(
            componentdir.resolve("assets").resolve("component-main.jar")
          )
          val descriptorfile = componentdir.resolve("component-descriptor.json")
          Files.writeString(
            descriptorfile,
            _canonical_descriptor_json("org.goldenport.cncf.Specification", "0.1.0-SNAPSHOT")
          )
          _create_car(
            componentdir.resolve("priority-component-0.1.0-SNAPSHOT.car"),
            Seq(
              "component/main.jar" -> componentjar,
              "component-descriptor.json" -> descriptorfile
            )
          )
        }
        val requested = ComponentDescriptor(
          name = Some("org.goldenport.cncf.Specification"),
          version = Some("0.1.0-SNAPSHOT"),
          componentName = Some("org.goldenport.cncf.Specification"),
          schemaVersion = Some(3),
          componentId = Some(ComponentId("org.goldenport.cncf.Specification"))
        )
        val activespec = ComponentRepository.ComponentDirRepository.Specification(active)
        val stalespec = ComponentRepository.ComponentDirRepository.Specification(stale)

        When("the later repository receives assembly component descriptors")
        val remaining = ComponentRepository.descriptorsForSpecification(
          stalespec,
          Vector(activespec),
          Vector(requested)
        )

        Then("the coordinate already satisfied by the active repository is not resolved again")
        remaining shouldBe empty
      }
      }
    }

    "E41 use search repositories for assembly API preflight without discovering their components" must _metadata("E41") {
      "when exercising: use search repositories for assembly API preflight without discovering their components" in {
      Given("one active CAR repository and one API-only search CAR repository")
      val subsystem = TestComponentFactory.emptySubsystem("assembly-api-search")
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { root =>
        def _create_repository_(
          dirname: String,
          componentid: String,
          factoryclass: Class[?],
          componentclass: Class[?]
        ): ComponentRepository = {
          val componentdir = Files.createDirectories(root.resolve(dirname))
          val componentjar = _create_class_component_jar(
            componentdir.resolve("assets").resolve("component-main.jar"),
            Seq(factoryclass, componentclass)
          )
          val descriptor = componentdir.resolve("component-descriptor.json")
          Files.writeString(
            descriptor,
            _canonical_descriptor_json(componentid, "0.1.0")
          )
          _create_car(
            componentdir.resolve(s"${dirname}.car"),
            Seq(
              "component/main.jar" -> componentjar,
              "component-descriptor.json" -> descriptor
            )
          )
          new ComponentRepository.ComponentDirRepository(
            componentdir,
            ComponentCreate(subsystem, origin),
            ComponentRepository.resolvePackagePrefixes()
          )
        }
        val active = _create_repository_(
          "active",
          "org.goldenport.fixture.ActiveAssemblyApi",
          classOf[ActiveAssemblyApiComponentFactory],
          classOf[ActiveAssemblyApiComponent]
        )
        val search = _create_repository_(
          "search",
          "org.goldenport.fixture.SearchAssemblyApi",
          classOf[SearchAssemblyApiComponentFactory],
          classOf[SearchAssemblyApiComponent]
        )

        When("assembly discovery preflights both repositories but activates only the active repository")
        val components = ComponentRepository.discoverAssembly(Vector(active, search), Vector(active))

        Then("the active repository participates in component discovery")
        components.flatMap(_.artifactMetadata.flatMap(_.component)) should contain ("org.goldenport.fixture.ActiveAssemblyApi")

        And("the search repository remains an API source only")
        components.flatMap(_.artifactMetadata.flatMap(_.component)) should not contain "org.goldenport.fixture.SearchAssemblyApi"
      }
      }
    }

    "E42 discover a plain Component.Factory from a component CAR" must _metadata("E42") {
      "when exercising: discover a plain Component.Factory from a component CAR" in {
      Given("a component CAR containing a plain Component.Factory")
      val subsystem = new Subsystem(
        name = "test-plain-factory-car",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val carpath = componentdir.resolve("plain-factory-component.car")
        val componentjar = _create_class_component_jar(
          componentdir.resolve("assets").resolve("plain-factory-main.jar"),
          Seq(
            classOf[PlainFactoryPrimaryComponentFactory],
            classOf[PlainFactoryPrimaryComponent]
          )
        )
        val descriptor = componentdir.resolve("component-descriptor-plain-factory.json")
        Files.writeString(
          descriptor,
          _canonical_descriptor_json("org.goldenport.fixture.PlainFactoryPrimary", "0.1.0")
        )
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> componentjar,
            "component-descriptor.json" -> descriptor
          )
        )

        When("the component directory repository discovers the CAR")
        val repository = new ComponentRepository.ComponentDirRepository(componentdir, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())
        val components = repository.discover()

        Then("the plain factory creates the primary component")
        components.map(_.name) should contain ("org.goldenport.fixture.PlainFactoryPrimary")
        components.find(_.name == "org.goldenport.fixture.PlainFactoryPrimary").flatMap(_.factoryOption) should not be empty
      }
      }
    }

    "E43 retain packaged initialization defaults without repeated CAR materialization" must _metadata("E43") {
      "when exercising: retain packaged initialization defaults without repeated CAR materialization" in {
      Given("a component CAR whose descriptor supplies a required initialization default")
      _with_temp_dir { componentdir =>
        val componentid = "org.goldenport.fixture.RepositoryParameterProbe"
        val carpath = componentdir.resolve("repository-parameter-probe.car")
        val componentjar = _create_class_component_jar(
          componentdir.resolve("assets").resolve("repository-parameter-probe.jar"),
          Seq(
            classOf[CanonicalRepositoryParameterProbeFactory],
            classOf[CanonicalRepositoryParameterProbeComponent],
            CanonicalRepositoryParameterProbeFactory.getClass
          )
        )
        val cardescriptor = componentdir.resolve("component-descriptor-parameter.json")
        Files.writeString(
          cardescriptor,
          s"""{"schemaVersion":3,"component":{"namespace":"org.goldenport.fixture","id":"RepositoryParameterProbe","version":"0.1.0"},"config":{"provider.limit":"27"}}"""
        )
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> componentjar,
            "component-descriptor.json" -> cardescriptor
          )
        )
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.componentFileKey -> ConfigurationValue.StringValue(carpath.toString)
          )),
          ConfigurationTrace.empty
        )
        val descriptor = GenericSubsystemDescriptor(
          path = carpath,
          subsystemName = "packaged-parameter-default",
          componentBindings = Vector(
            GenericSubsystemComponentBinding(
              componentid,
              version = Some("0.1.0"),
              componentId = Some(ComponentId(componentid))
            )
          )
        )

        When("GenericSubsystemFactory discovers and materializes the packaged component")
        CanonicalRepositoryParameterProbeFactory.resetCreationCount()
        val component = GenericSubsystemFactory
          .default(descriptor, configuration = configuration)
          .components
          .find(_.name == componentid)
          .getOrElse(fail("repository parameter probe was not materialized"))

        Then("the packaged descriptor remains the fixed packaged-default source")
        val resolution = component.initializationParameters
          .resolve(CanonicalRepositoryParameterProbeFactory.limitKey)
          .toOption
          .getOrElse(fail("packaged initialization parameter was not resolved"))
        resolution.value shouldBe Some(27)
        resolution.provenance shouldBe ComponentParameterProvenance.PackagedDefault
        CanonicalRepositoryParameterProbeFactory.creationCount shouldBe 1
      }
      }
    }

    "E44 preserve packaged CAR and SAR initialization failures" must _metadata("E44") {
      "when exercising: preserve packaged CAR and SAR initialization failures" in {
      Given("CAR and SAR repositories whose selected component default is malformed")
      _with_temp_dir { root =>
        val componentid = "org.goldenport.fixture.RepositoryParameterProbe"
        val componentjar = _create_class_component_jar(
          root.resolve("assets").resolve("repository-parameter-probe.jar"),
          Seq(
            classOf[CanonicalRepositoryParameterProbeFactory],
            classOf[CanonicalRepositoryParameterProbeComponent],
            CanonicalRepositoryParameterProbeFactory.getClass
          )
        )
        val cardescriptor = root.resolve("component-descriptor-invalid-parameter.json")
        Files.writeString(
          cardescriptor,
          s"""{"schemaVersion":3,"component":{"namespace":"org.goldenport.fixture","id":"RepositoryParameterProbe","version":"0.1.0"},"config":{"provider.limit":"invalid"}}"""
        )
        val carpath = root.resolve("repository-parameter-probe.car")
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> componentjar,
            "component-descriptor.json" -> cardescriptor
          )
        )
        val sardescriptor = root.resolve("subsystem-descriptor-parameter.json")
        Files.writeString(
          sardescriptor,
          s"""{"name":"repository-parameter-subsystem","version":"0.1.0","subsystem":"repository-parameter-subsystem","components":[{"namespace":"org.goldenport.fixture","id":"RepositoryParameterProbe","version":"0.1.0"}]}"""
        )
        val sarpath = root.resolve("repository-parameter-probe.sar")
        _create_zip(
          sarpath,
          Seq(
            "subsystem-descriptor.json" -> sardescriptor,
            "component/repository-parameter-probe.car" -> carpath
          )
        )
        val subsystem = TestComponentFactory.emptySubsystem("initialization-packaged-failure")
        val requested = ComponentDescriptor(
          name = Some(componentid),
          version = Some("0.1.0"),
          componentName = Some(componentid),
          schemaVersion = Some(3),
          componentId = Some(org.goldenport.cncf.testutil.TestComponentFactory.componentId(componentid))
        )
        val params = ComponentCreate(
          subsystem,
          ComponentOrigin.Repository("phase-47"),
          Vector(requested)
        )
        val packageddescriptor = ComponentDescriptorLoader.loadArchive(carpath).toOption
          .getOrElse(fail("packaged parameter descriptor was not readable"))
        val expectedconclusion = new CanonicalRepositoryParameterProbeFactory()
          .createPrimaryC(params.withComponentDescriptors(Vector(packageddescriptor))) match {
            case Consequence.Failure(conclusion) => conclusion
            case Consequence.Success(component) =>
              fail(s"expected direct initialization failure but created: ${component.name}")
          }

        When("consequence-aware discovery invokes each packaged factory")
        val results = Vector(carpath, sarpath).map { artifactpath =>
          val repositorydir = Files.createDirectories(root.resolve(s"repository-${artifactpath.getFileName}"))
          Files.copy(artifactpath, repositorydir.resolve(artifactpath.getFileName))
          val repository = ComponentRepository.ComponentDirRepository
            .Specification(repositorydir)
            .build(params)
          val repositoryspace = ComponentRepositorySpace(Vector(
            ComponentRepositorySpace.Slot(repository, ComponentOrigin.Repository("phase-47"))
          ))
          new ComponentFactory(repositoryspace).discoverC()
        }
        val failures = results.map(_.isFaillure)

        Then("neither packaged failure is converted to component absence")
        failures shouldBe Vector(true, true)
        results.foreach {
          case Consequence.Failure(conclusion) =>
            withClue(s"actual=${conclusion.toJsonString} expected=${expectedconclusion.toJsonString}") {
              conclusion.isMatch(expectedconclusion) shouldBe true
            }
          case Consequence.Success(components) =>
            fail(s"expected packaged initialization failure but discovered: ${components.map(_.name).mkString(",")}")
        }
      }
      }
    }

    "E45 wire a socket to a plain-factory provider loaded from a component CAR" must _metadata("E45") {
      "when exercising: wire a socket to a plain-factory provider loaded from a component CAR" in {
      Given("a component CAR whose plain factory provides an AI runner socket provider")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = new Subsystem(
        name = "test-plain-factory-car-spi",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val carpath = componentdir.resolve("plain-ai-runner-provider.car")
        val componentjar = _create_class_component_jar(
          componentdir.resolve("assets").resolve("plain-ai-runner-main.jar"),
          Seq(
            classOf[org.goldenport.cncf.component.repository.fixture.spi.ComponentFactory],
            classOf[org.goldenport.cncf.component.repository.fixture.spi.PlainAiRunnerProviderComponent],
            classOf[org.goldenport.cncf.component.repository.fixture.spi.PlainAiRunner]
          )
        )
        val descriptor = componentdir.resolve("component-descriptor-plain-ai.json")
        Files.writeString(
          descriptor,
          _canonical_descriptor_json("org.goldenport.fixture.PlainAiRunnerProvider", "0.1.0")
        )
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> componentjar,
            "component-descriptor.json" -> descriptor
          )
        )

        val repository = new ComponentRepository.ComponentDirRepository(componentdir, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())
        val providercomponents = repository.discover().toVector
        val consumer = new Component() with AiRunnerSocket
        val consumerid = TestComponentFactory.componentId("component_repository_car_ai_runner_consumer")
        consumer.initialize(
          ComponentInit(
            subsystem,
            Component.Core.create(
              consumerid.name,
              consumerid,
              ComponentInstanceId.default(consumerid),
              Protocol.empty
            ),
            ComponentOrigin.Builtin
          )
        )

        When("SPI resolver wires the CAR provider into the consumer socket")
        val resolved = SpiResolver.resolve(providercomponents :+ consumer)

        Then("the consumer uses the provider loaded from the CAR")
        resolved shouldBe a[Consequence.Success[_]]
        consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "car:hello"
      }
      }
    }

    "E46 treat component-file CAR as one component and ignore embedded component.d contents" must _metadata("E46") {
      "when exercising: treat component-file CAR as one component and ignore embedded component.d contents" in {
      Given("an application CAR containing another CAR below component.d")
      val subsystem = new Subsystem(
        name = "test-component-file-embedded",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-file")
      _with_temp_dir { componentdir =>
        val appjar = _create_class_component_jar(
          componentdir.resolve("assets").resolve("app-main.jar"),
          Seq(
            classOf[org.goldenport.cncf.component.repository.fixture.spi.ArtSceneComponentFactory],
            classOf[org.goldenport.cncf.component.repository.fixture.spi.ArtSceneComponent]
          )
        )
        val providerjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("provider-main.jar"))
        val appdescriptor = componentdir.resolve("component-descriptor-app.json")
        val providerdescriptor = componentdir.resolve("component-descriptor-provider.json")
        Files.writeString(
          appdescriptor,
          _canonical_descriptor_json("org.goldenport.fixture.ComponentFileApp", "1.0.0")
        )
        Files.writeString(
          providerdescriptor,
          _canonical_descriptor_json("org.goldenport.cncf.Specification", "1.0.0")
        )
        val providercar = componentdir.resolve("textus-user-account.car")
        _create_car(
          providercar,
          Seq(
            "component/main.jar" -> providerjar,
            "component-descriptor.json" -> providerdescriptor
          )
        )
        val appcar = componentdir.resolve("app.car")
        _create_car(
          appcar,
          Seq(
            "component/main.jar" -> appjar,
            "component.d/textus-user-account.car" -> providercar,
            "component-descriptor.json" -> appdescriptor
          )
        )

        val repository = new ComponentRepository.ComponentFileRepository(appcar, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())
        When("the application CAR is discovered as a component file")
        val components = repository.discover()
        val componentnames = components.flatMap(_.artifactMetadata).flatMap(_.component).toSet

        Then("only the application component is activated")
        componentnames should contain ("org.goldenport.fixture.ComponentFileApp")
        componentnames should not contain ("org.goldenport.cncf.Specification")
      }
      }
    }

    "E47 activate component-file assembly dependencies from search repositories" must _metadata("E47") {
      "when exercising: activate component-file assembly dependencies from search repositories" in {
      Given("an application CAR whose assembly descriptor declares a provider component")
      _with_temp_dir { root =>
        val repositorydir = root.resolve("repository.d")
        val appcar = root.resolve("component-file-app.car")
        val appjar = _create_class_component_jar(
          root.resolve("assets").resolve("art-scene-main.jar"),
          Seq(
            classOf[org.goldenport.cncf.component.repository.fixture.spi.ArtSceneComponentFactory],
            classOf[org.goldenport.cncf.component.repository.fixture.spi.ArtSceneComponent]
          )
        )
        val appdescriptor = root.resolve("component-descriptor-art-scene.json")
        Files.writeString(
          appdescriptor,
          _canonical_descriptor_json("org.goldenport.fixture.ComponentFileApp", "0.1.0-SNAPSHOT")
        )
        val assemblydescriptor = root.resolve("assembly-descriptor-art-scene.yaml")
        Files.writeString(
          assemblydescriptor,
          """subsystem: component-file-app
            |version: 0.1.0
            |components:
            |  - namespace: org.goldenport.fixture
            |    id: ComponentFileApp
            |    version: 0.1.0-SNAPSHOT
            |  - namespace: org.goldenport.fixture
            |    id: PlainAiRunnerProvider
            |    version: 0.1.0
            |""".stripMargin
        )
        _create_car(
          appcar,
          Seq(
            "component/main.jar" -> appjar,
            "component-descriptor.json" -> appdescriptor,
            "assembly-descriptor.yaml" -> assemblydescriptor
          )
        )

        val providerjar = _create_class_component_jar(
          root.resolve("assets").resolve("plain-ai-runner-main.jar"),
          Seq(
            classOf[org.goldenport.cncf.component.repository.fixture.spi.ComponentFactory],
            classOf[org.goldenport.cncf.component.repository.fixture.spi.PlainAiRunnerProviderComponent],
            classOf[org.goldenport.cncf.component.repository.fixture.spi.PlainAiRunner]
          )
        )
        val providerdescriptor = root.resolve("component-descriptor-plain-ai.json")
        Files.writeString(
          providerdescriptor,
          _canonical_descriptor_json("org.goldenport.fixture.PlainAiRunnerProvider", "0.1.0")
        )
        Files.createDirectories(repositorydir)
        _create_car(
          repositorydir.resolve("plain-ai-runner-provider-0.1.0.car"),
          Seq(
            "component/main.jar" -> providerjar,
            "component-descriptor.json" -> providerdescriptor
          )
        )
        val testdescriptor = _controlled_test_descriptor(root, "component-file-dependency-resolution")

        When("CNCF initializes from the single application component file")
        val initialized = new org.goldenport.cncf.cli.CncfRuntime().initializeForEmbedding(
          cwd = root,
          args = Array(
            s"--textus.test.descriptor=$testdescriptor",
            "--no-default-components",
            "--component-file", appcar.toString,
            "--repository-dir", repositorydir.toString,
            "command", "component-file-app.main.noop"
          ),
          modeHint = Some(org.goldenport.cncf.cli.RunMode.Command)
        ).TAKE

        Then("the declared provider component is activated from the search repository")
        initialized.descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) should contain ("org.goldenport.fixture.PlainAiRunnerProvider")
        initialized.components.map { component =>
          component.displayName
        } should contain ("plain-ai-runner-provider")
      }
      }
    }

    "E48 fail startup when a component-file assembly dependency is unresolved" must _metadata("E48") {
      "when exercising: fail startup when a component-file assembly dependency is unresolved" in {
      Given("an application CAR whose assembly descriptor declares a missing provider")
      _with_temp_dir { root =>
        val appcar = root.resolve("component-file-app.car")
        val appjar = _create_class_component_jar(
          root.resolve("assets").resolve("art-scene-main.jar"),
          Seq(
            classOf[org.goldenport.cncf.component.repository.fixture.spi.ArtSceneComponentFactory],
            classOf[org.goldenport.cncf.component.repository.fixture.spi.ArtSceneComponent]
          )
        )
        val appdescriptor = root.resolve("component-descriptor-art-scene.json")
        Files.writeString(
          appdescriptor,
          _canonical_descriptor_json("org.goldenport.fixture.ComponentFileApp", "0.1.0-SNAPSHOT")
        )
        val assemblydescriptor = root.resolve("assembly-descriptor-art-scene.yaml")
        Files.writeString(
          assemblydescriptor,
          """subsystem: component-file-app
            |version: 0.1.0
            |components:
            |  - namespace: org.goldenport.fixture
            |    id: ComponentFileApp
            |    version: 0.1.0-SNAPSHOT
            |  - namespace: org.goldenport.fixture
            |    id: MissingAiRuntimeForComponentFileSpec
            |    version: 0.2.0-SNAPSHOT
            |""".stripMargin
        )
        _create_car(
          appcar,
          Seq(
            "component/main.jar" -> appjar,
            "component-descriptor.json" -> appdescriptor,
            "assembly-descriptor.yaml" -> assemblydescriptor
          )
        )
        val testdescriptor = _controlled_test_descriptor(root, "component-file-missing-dependency")

        When("CNCF initializes without a repository containing the dependency")
        val result = new org.goldenport.cncf.cli.CncfRuntime().initializeForEmbedding(
          cwd = root,
          args = Array(
            s"--textus.test.descriptor=$testdescriptor",
            "--no-default-components",
            "--component-file", appcar.toString,
            "command", "component-file-app.main.noop"
          ),
          modeHint = Some(org.goldenport.cncf.cli.RunMode.Command)
        )

        Then("canonical admission fails before later assembly dependency resolution")
        val message = result match {
          case Consequence.Failure(conclusion) => conclusion.display
          case Consequence.Success(value) =>
            val bindings = value.descriptor.toVector.flatMap(_.componentBindings.map(_.componentName))
            val components = value.components.map { component =>
              component.artifactMetadata.flatMap(_.component).getOrElse(component.name)
            }
            fail(s"expected component dependency failure but initialized bindings=${bindings.mkString(",")} components=${components.mkString(",")}")
        }
        message should include (
          "canonical component binding has no exact Core/artifact identity match: " +
            "org.goldenport.fixture.MissingAiRuntimeForComponentFileSpec"
        )
      }
      }
    }

    "E49 skip an invalid car containing zero component jars" must _metadata("E49") {
      "when exercising: skip an invalid car containing zero component jars" in {
      Given("a CAR with a descriptor and library but no component implementation JAR")
      val subsystem = new Subsystem(
        name = "test-skip",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val dummyjar = Files.createTempFile(componentdir, "dummy", ".jar")
        Using.resource(new ZipOutputStream(Files.newOutputStream(dummyjar))) { zip =>
          zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"))
          zip.write("Manifest-Version: 1.0\n\n".getBytes(StandardCharsets.UTF_8))
          zip.closeEntry()
        }
        try {
          val carpath = componentdir.resolve("invalid.car")
          val descriptor = componentdir.resolve("component-descriptor-invalid-structure.json")
          Files.writeString(
            descriptor,
            """{"name":"invalid-structure","version":"0.1.0","component":"spec"}"""
          )
          _create_car(carpath, Seq("component/lib/dummy.jar" -> dummyjar, "component-descriptor.json" -> descriptor))
          val repository = new ComponentRepository.ComponentDirRepository(componentdir, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())
          When("the repository discovers the invalid CAR")
          val components = repository.discover()

          Then("the invalid CAR is skipped")
          components shouldBe empty
        } finally {
          Files.deleteIfExists(dummyjar)
        }
      }
      }
    }

    "E50 skip an invalid car containing incomplete descriptor" must _metadata("E50") {
      "when exercising: skip an invalid car containing incomplete descriptor" in {
      Given("a CAR whose descriptor omits its component identity")
      val subsystem = new Subsystem(
        name = "test-invalid-manifest",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main-invalid.jar"))
        val descriptor = componentdir.resolve("component-descriptor-invalid.json")
        Files.writeString(
          descriptor,
          """{"version":"0.1.0"}"""
        )
        val carpath = componentdir.resolve("invalid-manifest.car")
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> descriptor
          )
        )
        val repository = new ComponentRepository.ComponentDirRepository(componentdir, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())
        When("the repository discovers the invalid CAR")
        val components = repository.discover()

        Then("the invalid CAR is skipped")
        components shouldBe empty
      }
      }
    }

    "E51 apply SAR > CAR precedence for extension/config when loading sar containing car" must _metadata("E51") {
      "when exercising: apply SAR > CAR precedence for extension/config when loading sar containing car" in {
      Given("a SAR and embedded CAR defining overlapping extensions and configuration")
      val subsystem = new Subsystem(
        name = "test-sar",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main-sar.jar"))
        val cardescriptor = componentdir.resolve("component-descriptor-car-sar.json")
        Files.writeString(
          cardescriptor,
          """{"schemaVersion":3,"component":{"namespace":"org.goldenport.cncf","id":"Specification","version":"1.0.0"},"extension":{"driver":"car-default","hash":"sha256"},"config":{"log.level":"info","feature":"base"}}"""
        )
        val embeddedcar = componentdir.resolve("embedded.car")
        _create_car(
          embeddedcar,
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> cardescriptor
          )
        )
        val sardescriptor = componentdir.resolve("subsystem-descriptor-sar.json")
        Files.writeString(
          sardescriptor,
          """{"name":"demo-subsystem","version":"2.0.0","subsystem":"demo","components":[{"namespace":"org.goldenport.cncf","id":"Specification","version":"1.0.0"}],"extension":{"driver":"sar-override"},"config":{"feature":"sar","endpoint":"https://example.invalid"}}"""
        )
        val sarpath = componentdir.resolve("demo.sar")
        _create_zip(
          sarpath,
          Seq(
            "subsystem-descriptor.json" -> sardescriptor,
            "component/base.car" -> embeddedcar
          )
        )
        val repository = new ComponentRepository.ComponentDirRepository(componentdir, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())
        When("the repository discovers the SAR")
        val components = repository.discover()

        Then("SAR values override CAR values while non-overlapping values are retained")
        components should not be empty
        val comp = components.head
        val metadata = comp.artifactMetadata.getOrElse(fail("missing artifact metadata"))
        comp.origin.label should include("component-dir:sar:demo:2.0.0:car:org.goldenport.cncf.Specification:1.0.0")
        metadata.subsystem shouldBe Some("demo")
        metadata.effectiveExtensions.get("driver") shouldBe Some("sar-override")
        metadata.effectiveExtensions.get("hash") shouldBe Some("sha256")
        metadata.effectiveConfig.get("feature") shouldBe Some("sar")
        metadata.effectiveConfig.get("log.level") shouldBe Some("info")
        metadata.effectiveConfig.get("endpoint") shouldBe Some("https://example.invalid")
      }
      }
    }

    "E52 discover a requested component from the standard CAR repository layout" must _metadata("E52") {
      "when exercising: discover a requested component from the standard CAR repository layout" in {
      Given("a requested schema-3 component stored at its canonical repository coordinate")
      val subsystem = new Subsystem(
        name = "test-standard-repo",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { repositoryroot =>
        val componentid = ComponentId("org.goldenport.fixture.CanonicalArchiveFixture")
        val release = "0.1.0"
        val coordinate = ComponentReleaseCoordinate.require(componentid.sharedIdentity, release)
        val artifact = repositoryroot.resolve("car").resolve(coordinate.carRepositoryRelativePath())
        val componentjar = _create_class_component_jar(
          repositoryroot.resolve("assets").resolve("component-main-standard.jar"),
          Seq(classOf[CanonicalArchiveFixtureFactory], classOf[CanonicalArchiveFixtureComponent])
        )
        _create_canonical_component_car(
          artifact,
          repositoryroot.resolve("content-standard"),
          componentid.name,
          release,
          componentjar
        )
        val repository = ComponentRepository.StandardRepository.Specification(
          ComponentRepository.StandardRepositoryKind.Car,
          ComponentRepository.standardComponentRepositoryUrl(),
          repositoryroot
        ).build(
          ComponentCreate(
            subsystem,
            origin,
            Vector(_canonical_component_descriptor(componentid, release))
          )
        )
        When("the standard repository is discovered")
        val components = repository.discover()

        Then("the requested component is loaded")
        components.map(_.componentId) should contain (componentid)
        components.flatMap(_.artifactMetadata).flatMap(_.component) should contain (componentid.name)
      }
      }
    }

    "E53 fetch requested component CARs from a standard repository before discovery" must _metadata("E53") {
      "when exercising: fetch requested component CARs from a standard repository before discovery" in {
      Given("two requested component CARs in a remote standard repository")
      val subsystem = new Subsystem(
        name = "test-standard-repo-fetch",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { root =>
        val remote = root.resolve("remote")
        val cache = root.resolve("cache")
        val firstid = ComponentId("org.goldenport.fixture.CanonicalArchiveFixture")
        val secondid = ComponentId("org.example.fixture.CanonicalArchiveFixture")
        val firstrelease = "0.1.1"
        val secondrelease = "0.0.2"
        val firstcoordinate = ComponentReleaseCoordinate.require(firstid.sharedIdentity, firstrelease)
        val secondcoordinate = ComponentReleaseCoordinate.require(secondid.sharedIdentity, secondrelease)
        val firstjar = _create_class_component_jar(
          root.resolve("assets").resolve("component-main-standard-fetch-first.jar"),
          Seq(classOf[CanonicalArchiveFixtureFactory], classOf[CanonicalArchiveFixtureComponent])
        )
        val secondjar = _create_class_component_jar(
          root.resolve("assets").resolve("component-main-standard-fetch-second.jar"),
          Seq(classOf[NamespaceIsolatedArchiveFixtureFactory], classOf[NamespaceIsolatedArchiveFixtureComponent])
        )
        _create_canonical_component_car(
          remote.resolve(firstcoordinate.carRepositoryRelativePath()),
          root.resolve("content-standard-fetch-first"),
          firstid.name,
          firstrelease,
          firstjar
        )
        _create_canonical_component_car(
          remote.resolve(secondcoordinate.carRepositoryRelativePath()),
          root.resolve("content-standard-fetch-second"),
          secondid.name,
          secondrelease,
          secondjar
        )
        val repository = ComponentRepository.StandardRepository.Specification(
          ComponentRepository.StandardRepositoryKind.Car,
          remote.toUri.toString.stripSuffix("/"),
          cache
        ).build(
          ComponentCreate(
            subsystem,
            origin,
            Vector(
              _canonical_component_descriptor(firstid, firstrelease),
              _canonical_component_descriptor(secondid, secondrelease)
            )
          )
        )

        When("the standard repository is discovered")
        val components = repository.discover()

        Then("both CARs are cached and each component keeps its own descriptors")
        cache.resolve("car").resolve(firstcoordinate.carRepositoryRelativePath()).toFile should exist
        cache.resolve("car").resolve(secondcoordinate.carRepositoryRelativePath()).toFile should exist
        components.map(_.componentId) should contain (firstid)
        components.map(_.componentId) should contain (secondid)
        components.flatMap(_.artifactMetadata).flatMap(_.component).distinct.sorted shouldBe
          Vector(firstid.name, secondid.name).sorted
      }
      }
    }

    "E54 fail fast when requested component CAR is not available in a standard repository" must _metadata("E54") {
      "when exercising: fail fast when requested component CAR is not available in a standard repository" in {
      Given("a requested component absent from the standard repository")
      val subsystem = new Subsystem(
        name = "test-standard-repo-fetch-missing",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { root =>
        val componentid = ComponentId("org.goldenport.cncf.Specification")
        val release = "0.1.1"
        val repository = ComponentRepository.StandardRepository.Specification(
          ComponentRepository.StandardRepositoryKind.Car,
          root.resolve("remote").toUri.toString.stripSuffix("/"),
          root.resolve("cache")
        ).build(
          ComponentCreate(
            subsystem,
            origin,
            Vector(_canonical_component_descriptor(componentid, release))
          )
        )

        When("the repository tries to discover the missing component")
        val thrown = the [ConsequenceException] thrownBy repository.discover()

        Then("discovery reports a structured resource-not-found failure")
        thrown.getMessage should include ("requested component CAR not found")
        val conclusion = thrown.consequence match {
          case Consequence.Failure(conclusion) => conclusion
          case _ => fail("expected structured failure")
        }
        conclusion.observation.taxonomy.category.name shouldBe "resource"
        conclusion.observation.taxonomy.symptom.name shouldBe "not-found"
        conclusion.toRecord.show should include (componentid.name)
        conclusion.toRecord.show should include (release)
      }
      }
    }

    "E55 not look up a requested main target CAR when component-dev-dir satisfies the exact canonical release" must _metadata("E55") {
      "when exercising: not look up a requested main target CAR when component-dev-dir satisfies the exact canonical release" in {
      Given("a prepared development directory satisfying the exact requested main target identity and release")
      val subsystem = new Subsystem(
        name = "test-standard-repo-dev-main-target",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      _with_temp_dir { root =>
        val componentdir = root.resolve("textus-knowledge-editor")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        Files.createDirectories(classdir)
        val componentid = "org.goldenport.fixture.TextusKnowledgeEditor"
        _write_runtime_classpath(componentdir, classdir, "TextusKnowledgeEditor", "0.1.0-SNAPSHOT", "TextusKnowledgeEditor", componentid = Some(componentid))
        Files.createDirectories(componentdir.resolve("src").resolve("main").resolve("car"))
        Files.writeString(
          componentdir.resolve("src").resolve("main").resolve("car").resolve("component-descriptor.yaml"),
          """name: TextusKnowledgeEditor
            |version: 0.1.0-SNAPSHOT
            |component: TextusKnowledgeEditor
            |""".stripMargin
        )
        val space = ComponentRepositorySpace.create(
          subsystem,
          ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
          Vector(
            ComponentRepository.ComponentDevDirRepository.Specification(componentdir),
            ComponentRepository.StandardRepository.Specification(
              ComponentRepository.StandardRepositoryKind.Car,
              root.resolve("remote").toUri.toString.stripSuffix("/"),
              root.resolve("cache")
            )
          ),
          Vector(_canonical_component_descriptor(ComponentId(componentid), "0.1.0-SNAPSHOT"))
        )

        When("the repository space resolves the exact prepared development claim")
        val result = space.discover()

        Then("the exact canonical development target prevents a standard CAR lookup")
        result should not be null
      }
      }
    }

    "E56 not look up a requested main target CAR when component-dev-dir provides the prepared canonical release" must _metadata("E56") {
      "when exercising: not look up a requested main target CAR when component-dev-dir provides the prepared canonical release" in {
      Given("a prepared development directory carrying its exact canonical component identity and release")
      val subsystem = new Subsystem(
        name = "test-standard-repo-dev-main-target-inferred",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      _with_temp_dir { root =>
        val componentid = "org.goldenport.fixture.DevDirSample"
        val componentdir = root.resolve("devdirsample")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        _copy_devdir_sample_classes(classdir)
        _write_runtime_classpath(componentdir, classdir, "devdirsample", "0.1.0-SNAPSHOT", "devdirsample", componentid = Some(componentid))
        val space = ComponentRepositorySpace.create(
          subsystem,
          ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
          Vector(
            ComponentRepository.ComponentDevDirRepository.Specification(componentdir),
            ComponentRepository.StandardRepository.Specification(
              ComponentRepository.StandardRepositoryKind.Car,
              root.resolve("remote").toUri.toString.stripSuffix("/"),
              root.resolve("cache")
            )
          ),
          Vector(ComponentDescriptor(
            name = Some(componentid),
            version = Some("0.1.0-SNAPSHOT"),
            componentName = Some(componentid),
            schemaVersion = Some(3),
            componentId = Some(org.goldenport.cncf.testutil.TestComponentFactory.componentId(componentid))
          ))
        )

        When("the repository space resolves the prepared exact canonical assembly coordinate")
        val components = space.discover()

        Then("the prepared exact canonical development implementation is activated exactly once without a CAR lookup")
        components.count(_.name == "org.goldenport.fixture.DevDirSample") shouldBe 1
        components.find(_.name == "org.goldenport.fixture.DevDirSample").map(_.getClass.getName) shouldBe
          Some(classOf[devdirsample.DevDirSamplePrimaryComponent].getName)
      }
      }
    }

    "E57 preserve an exact development claim while admitting unclaimed assembly dependencies as packaged CARs" must _metadata("E57") {
      "when exercising: preserve an exact development claim while admitting unclaimed assembly dependencies as packaged CARs" in {
      Given("a prepared exact development claim and an unclaimed dependency CAR without runtime evidence")
      val subsystem = TestComponentFactory.emptySubsystem("development-claim-dependency-admission")
      _with_temp_dir { root =>
        val appid = "org.goldenport.fixture.App"
        val dependencyid = "org.goldenport.cncf.Specification"
        val componentdir = root.resolve("app")
        val cardir = componentdir.resolve("src").resolve("main").resolve("car")
        Files.createDirectories(cardir)
        Files.writeString(
          cardir.resolve("component-descriptor.json"),
          _canonical_descriptor_json(appid, "0.2.0-SNAPSHOT")
        )
        val classdir = Files.createDirectories(componentdir.resolve("target").resolve("scala-3.3.8").resolve("classes"))
        _write_runtime_classpath(componentdir, classdir, "app", "0.2.0-SNAPSHOT", "app")
        val dependencydir = Files.createDirectories(root.resolve("dependencies"))
        val dependencyjar = _create_fake_component_jar(root.resolve("assets").resolve("dependency.jar"))
        val dependencydescriptor = root.resolve("dependency-descriptor.json")
        Files.writeString(
          dependencydescriptor,
          _canonical_descriptor_json(dependencyid, "0.1.0")
        )
        _create_zip(
          dependencydir.resolve("dependency-0.1.0.car"),
          Seq(
            "component/main.jar" -> dependencyjar,
            "component-descriptor.json" -> dependencydescriptor
          )
        )
        val dev = ComponentRepository.ComponentDevDirRepository.Specification(componentdir)
        val dependencies = ComponentRepository.ComponentDirRepository.Specification(dependencydir)
        val descriptors = Vector(
          ComponentDescriptor(
            name = Some(appid),
            version = Some("0.2.0-SNAPSHOT"),
            componentName = Some(appid),
            schemaVersion = Some(3),
            componentId = Some(org.goldenport.cncf.testutil.TestComponentFactory.componentId(appid))
          ),
          ComponentDescriptor(
            name = Some(dependencyid),
            version = Some("0.1.0"),
            componentName = Some(dependencyid),
            schemaVersion = Some(3),
            componentId = Some(org.goldenport.cncf.testutil.TestComponentFactory.componentId(dependencyid))
          )
        )
        val claims = ComponentRepository.developmentComponentClaims(Vector(dev, dependencies))
        val dependencydescriptors =
          ComponentRepository.descriptorsForSpecification(dependencies, Vector(dev), descriptors, claims)
        val repository = dependencies.build(
          ComponentCreate(
            subsystem,
            ComponentOrigin.Repository("component-dir"),
            dependencydescriptors
          )
        )

        When("the unclaimed dependency is loaded")
        val thrown = the [Exception] thrownBy repository.discover()

        Then("the exact development claim is removed and packaged CAR admission remains mandatory only for the dependency")
        dependencydescriptors.flatMap(_.componentName) shouldBe Vector(dependencyid)
        thrown.getMessage should include ("CAR runtime manifest is missing")
      }
      }
    }

    "E58 resolve a subsystem descriptor from the standard SAR repository layout" must _metadata("E58") {
      "when exercising: resolve a subsystem descriptor from the standard SAR repository layout" in {
      Given("a subsystem descriptor stored in the standard SAR repository layout")
      _with_temp_dir { repositoryroot =>
        val artifactdir = repositoryroot.resolve("sar").resolve("cwitter").resolve("0.1.0")
        Files.createDirectories(artifactdir)
        val descriptor = repositoryroot.resolve("subsystem-descriptor-standard-sar.yaml")
        Files.writeString(
          descriptor,
          """subsystem: cwitter
            |version: 0.1.0
            |components:
            |  - namespace: org.example
            |    id: Cwitter
            |    version: 0.1.0
            |""".stripMargin
        )
        _create_zip(
          artifactdir.resolve("cwitter-0.1.0.sar"),
          Seq(
            "subsystem-descriptor.yaml" -> descriptor
          )
        )

        When("the standard repository resolves the subsystem descriptor")
        val loaded = ComponentRepository.resolveSubsystemDescriptor(
          Vector(ComponentRepository.StandardRepository.Specification(
            ComponentRepository.StandardRepositoryKind.Sar,
            ComponentRepository.standardSubsystemRepositoryUrl(),
            repositoryroot
          )),
          "cwitter"
        )

        Then("the subsystem name and version are restored")
        loaded.map(_.subsystemName) shouldBe Some("cwitter")
        loaded.flatMap(_.version) shouldBe Some("0.1.0")
      }
      }
    }

    "E59A not search or activate the retired legacy component root for a canonical request" must _metadata("E59A") {
      "when exercising: resolve a canonical request against a repository containing only a retired-root CAR" in {
        Given("a canonical CAR stored only below the retired legacy component root")
        val subsystem = new Subsystem(
          name = "test-legacy-standard-repo",
          configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
        )
        val origin = ComponentOrigin.Repository("component-dir")
        _with_temp_dir { repositoryroot =>
          val componentid = ComponentId("org.goldenport.cncf.Specification")
          val release = "0.1.0-SNAPSHOT"
          val coordinate = ComponentReleaseCoordinate.require(componentid.sharedIdentity, release)
          val artifact = repositoryroot.resolve("org").resolve("simplemodeling").resolve("car").resolve(coordinate.carRepositoryRelativePath())
          Files.createDirectories(artifact.getParent)
          val descriptor = repositoryroot.resolve("component-descriptor-legacy-standard.json")
          Files.writeString(descriptor, _canonical_descriptor_json(componentid.name, release))
          _create_car(artifact, Seq("component-descriptor.json" -> descriptor))
          val repository = new ComponentRepository.ComponentDirRepository(
            repositoryroot,
            ComponentCreate(subsystem, origin, Vector(_canonical_component_descriptor(componentid, release))),
            ComponentRepository.resolvePackagePrefixes()
          )

          When("the canonical request searches the repository")
          val components = repository.discover()

          Then("the retired root is not searched or activated")
          components shouldBe empty
        }
      }
    }

    "E59B reject a bare request before repository component admission" must _metadata("E59B") {
      "when exercising: resolve an unqualified component request" in {
        Given("an empty repository and a request without canonical identity fields")
        val subsystem = new Subsystem(
          name = "test-unqualified-component-request",
          configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
        )
        val origin = ComponentOrigin.Repository("component-dir")
        _with_temp_dir { repositoryroot =>
          val release = "0.1.0-SNAPSHOT"
          val repository = new ComponentRepository.ComponentDirRepository(
            repositoryroot,
            ComponentCreate(
              subsystem,
              origin,
              Vector(ComponentDescriptor(
                name = Some("textus-user-account"),
                version = Some(release),
                componentName = Some("textus-user-account")
              ))
            ),
            ComponentRepository.resolvePackagePrefixes()
          )

          When("the bare request crosses canonical descriptor admission")
          val rejected = the[ConsequenceException] thrownBy repository.discover()

          Then("canonical descriptor admission rejects the bare request")
          rejected.getMessage should include ("canonical component descriptor requires schemaVersion 3")
        }
      }
    }

    "E61A reject a foreign descriptor at a canonical repository path" must _metadata("E61A") {
      "when exercising: bind a canonical repository path to its embedded descriptor identity" in {
      Given("a canonical coordinate path containing a foreign schema-3 descriptor")
      _with_temp_dir { root =>
        val requestedid = ComponentId("org.example.Requested")
        val foreignid = ComponentId("org.other.Requested")
        val release = "1.0.0"
        val coordinate = ComponentReleaseCoordinate.require(requestedid.sharedIdentity, release)
        val artifact = root.resolve("car").resolve(coordinate.carRepositoryRelativePath())
        Files.createDirectories(artifact.getParent)
        val descriptor = root.resolve("component-descriptor-canonical-path.json")
        val specification = ComponentRepository.ComponentDirRepository.Specification(root)

        Files.writeString(descriptor, _canonical_descriptor_json(foreignid.name, release), StandardCharsets.UTF_8)
        _create_car(artifact, Seq("component-descriptor.json" -> descriptor))
        When("the requested coordinate is resolved")
        val foreigndescriptor = specification.resolveComponentDescriptor(requestedid.name)
        val foreignarchive = specification.resolveComponentArchivePath(requestedid.name, Some(release))

        Then("neither descriptor nor archive resolution admits the foreign artifact")
        foreigndescriptor shouldBe None
        foreignarchive shouldBe None
      }
      }
    }

    "E61B reject a wrong-release descriptor at a canonical repository path" must _metadata("E61B") {
      "when exercising: bind a canonical repository path to its embedded descriptor release" in {
      Given("a canonical coordinate path containing the requested ID at a different release")
      _with_temp_dir { root =>
        val requestedid = ComponentId("org.example.Requested")
        val release = "1.0.0"
        val coordinate = ComponentReleaseCoordinate.require(requestedid.sharedIdentity, release)
        val artifact = root.resolve("car").resolve(coordinate.carRepositoryRelativePath())
        Files.createDirectories(artifact.getParent)
        val descriptor = root.resolve("component-descriptor-canonical-path.json")
        val specification = ComponentRepository.ComponentDirRepository.Specification(root)
        Files.writeString(descriptor, _canonical_descriptor_json(requestedid.name, "2.0.0"), StandardCharsets.UTF_8)
        _create_car(artifact, Seq("component-descriptor.json" -> descriptor))
        When("the requested coordinate is resolved")
        val wrongreleasedescriptor = specification.resolveComponentDescriptor(requestedid.name)
        val wrongreleasearchive = specification.resolveComponentArchivePath(requestedid.name, Some(release))

        Then("both resolution routes reject it before activation")
        wrongreleasedescriptor shouldBe None
        wrongreleasearchive shouldBe None
      }
      }
    }

    "E60 skip an invalid sar containing incomplete descriptor" must _metadata("E60") {
      "when exercising: skip an invalid sar containing incomplete descriptor" in {
      Given("a SAR whose subsystem descriptor omits its identity")
      val subsystem = new Subsystem(
        name = "test-invalid-sar",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val sardescriptor = componentdir.resolve("subsystem-descriptor-sar-invalid.json")
        Files.writeString(
          sardescriptor,
          """{"version":"1.0.0"}"""
        )
        val sarpath = componentdir.resolve("invalid.sar")
        _create_zip(
          sarpath,
          Seq(
            "subsystem-descriptor.json" -> sardescriptor
          )
        )
        val repository = new ComponentRepository.ComponentDirRepository(componentdir, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())
        When("the repository discovers the invalid SAR")
        val components = repository.discover()

        Then("the invalid SAR is skipped")
        components shouldBe empty
      }
      }
    }

    "E62A fail closed for a bound SAR containing a foreign embedded CAR" must _metadata("E62A") {
      "when exercising: require an embedded CAR to match one SAR binding" in {
      Given("a SAR bound to one canonical component and an embedded CAR from another namespace")
      _with_temp_dir { componentdir =>
        val boundid = ComponentId("org.example.Bound")
        val foreignid = ComponentId("org.other.Foreign")
        val release = "1.0.0"
        val cardescriptor = componentdir.resolve("foreign-descriptor.json")
        Files.writeString(cardescriptor, _canonical_descriptor_json(foreignid.name, release), StandardCharsets.UTF_8)
        val componentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val embeddedcar = componentdir.resolve("foreign.car")
        _create_car(embeddedcar, Seq(
          "component/main.jar" -> componentjar,
          "component-descriptor.json" -> cardescriptor
        ))
        val sardescriptor = componentdir.resolve("bound-sar.json")
        Files.writeString(sardescriptor, s"""{"name":"bound","subsystem":"bound","version":"$release","components":[{"namespace":"org.example","id":"Bound","version":"$release"}]}""", StandardCharsets.UTF_8)
        _create_zip(componentdir.resolve("bound-foreign.sar"), Seq(
          "subsystem-descriptor.json" -> sardescriptor,
          "component/foreign.car" -> embeddedcar
        ))
        val repository = new ComponentRepository.ComponentDirRepository(
          componentdir,
          ComponentCreate(TestComponentFactory.emptySubsystem("bound-foreign"), ComponentOrigin.Repository("component-dir"), Vector(_canonical_component_descriptor(boundid, release))),
          ComponentRepository.resolvePackagePrefixes()
        )

        When("repository discovery attempts to discover the bound SAR")
        val rejected = the[Exception] thrownBy repository.discover()

        Then("the foreign CAR fails closed before it can become a participant")
        rejected.getMessage should include ("SAR embedded CAR canonical binding mismatch")
        rejected.getMessage should include (foreignid.name)
      }
    }
    }

    "E62B fail closed for a bound SAR containing the right ID at the wrong release" must _metadata("E62B") {
      "when exercising: require an embedded CAR release to match its SAR binding" in {
      Given("a SAR bound at 1.0.0 and an embedded CAR with the same ID at 2.0.0")
      _with_temp_dir { componentdir =>
        val boundid = ComponentId("org.example.Bound")
        val release = "1.0.0"
        val cardescriptor = componentdir.resolve("wrong-release-descriptor.json")
        Files.writeString(cardescriptor, _canonical_descriptor_json(boundid.name, "2.0.0"), StandardCharsets.UTF_8)
        val componentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val embeddedcar = componentdir.resolve("wrong-release.car")
        _create_car(embeddedcar, Seq(
          "component/main.jar" -> componentjar,
          "component-descriptor.json" -> cardescriptor
        ))
        val sardescriptor = componentdir.resolve("bound-sar.json")
        Files.writeString(sardescriptor, s"""{"name":"bound","subsystem":"bound","version":"$release","components":[{"namespace":"org.example","id":"Bound","version":"$release"}]}""", StandardCharsets.UTF_8)
        _create_zip(componentdir.resolve("bound-wrong-release.sar"), Seq(
          "subsystem-descriptor.json" -> sardescriptor,
          "component/wrong-release.car" -> embeddedcar
        ))
        val repository = new ComponentRepository.ComponentDirRepository(
          componentdir,
          ComponentCreate(TestComponentFactory.emptySubsystem("bound-wrong-release"), ComponentOrigin.Repository("component-dir"), Vector(_canonical_component_descriptor(boundid, release))),
          ComponentRepository.resolvePackagePrefixes()
        )

        When("repository discovery attempts to discover the bound SAR")
        val rejected = the[Exception] thrownBy repository.discover()

        Then("the same ID at a different release fails closed before materialization")
        rejected.getMessage should include ("SAR embedded CAR canonical binding mismatch")
        rejected.getMessage should include ("version=2.0.0")
      }
    }
    }

    "E61 reject a SAR file passed to the component archive descriptor loader" must _metadata("E61") {
      "when exercising: reject a SAR file passed to the component archive descriptor loader" in {
      Given("a SAR passed to the CAR descriptor loader")
      _with_temp_dir { componentdir =>
        val descriptorpath = componentdir.resolve("component-descriptor.json")
        Files.writeString(descriptorpath, """{"component":{"name":"wrong-kind"},"version":"0.1.0"}""")
        val sarpath = componentdir.resolve("wrong-kind.sar")
        _create_zip(
          sarpath,
          Seq(
            "component-descriptor.json" -> descriptorpath
          )
        )

        When("the loader inspects the archive")
        val descriptor = ComponentDescriptorLoader.loadArchive(sarpath)

        Then("the wrong archive kind is rejected")
        descriptor.toOption shouldBe empty
      }
      }
    }

    "E62 reject a CAR file missing component descriptor version" must _metadata("E62") {
      "when exercising: reject a CAR file missing component descriptor version" in {
      _with_temp_dir { componentdir =>
        Given("a CAR whose component descriptor omits version")
        val descriptorpath = componentdir.resolve("component-descriptor.json")
        Files.writeString(descriptorpath, """{"name":"missing-version","component":"missing-version"}""")
        val carpath = componentdir.resolve("missing-version.car")
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar")),
            "component-descriptor.json" -> descriptorpath
          )
        )

        When("the archive descriptor loader reads the CAR")
        val result = ComponentDescriptorLoader.loadArchive(carpath)

        Then("the missing version is rejected as an invalid CAR descriptor")
        result.toOption shouldBe empty
        result match {
          case Consequence.Failure(conclusion) =>
            conclusion.display should include ("canonical component descriptor")
            conclusion.display should include ("schemaVersion 3 namespace, id, and version")
          case Consequence.Success(value) =>
            fail(s"expected missing version failure but got ${value}")
        }
      }
      }
    }

    "E5 wrap corrupt CAR archive I/O failures in a stable invalid-resource result" must _e5 {
      "when exercising: wrap corrupt CAR archive I/O failures in a stable invalid-resource result" in {
        Given("a .car path containing non-ZIP bytes")
        _with_temp_dir { componentdir =>
          val carpath = componentdir.resolve("corrupt.car")
          Files.writeString(carpath, "not-a-zip", StandardCharsets.UTF_8)

          When("the archive descriptor loader opens the corrupt CAR")
          val result = ComponentDescriptorLoader.loadArchive(carpath)

          Then("the loader retains the archive path and underlying I/O cause without throwing")
          result.toOption shouldBe empty
          result match {
            case Consequence.Failure(conclusion) =>
              conclusion.display should include ("CAR component archive I/O failed")
              conclusion.display should include (carpath.toString)
              conclusion.display should include ("cause=")
            case Consequence.Success(value) =>
              fail(s"expected corrupt CAR failure but got $value")
          }
        }
      }
    }

    }

    "component API and shared classloader boundaries" which {
    "E63 parse scoped component dependency manifest" must _metadata("E63") {
      "when exercising: parse scoped component dependency manifest" in {
      Given("a component dependency manifest with provided, shared, and local scopes")
      When("the manifest is parsed")
      val manifest = ComponentDependencyManifest.parse(
        Vector(
          "dependencies:",
          "  provided:",
          "    - org.goldenport:goldenport-cncf_3:0.4.8-SNAPSHOT",
          "  shared:",
          "    - org.postgresql:postgresql:42.7.3",
          "  local:",
          "    - com.example:legacy-driver:1.2.0",
          "  repositories:",
          "    - maven-central",
          "    - https://www.simplemodeling.org/repository/maven"
        )
      ).toOption.get

      Then("each dependency and repository stays in its declared scope")
      manifest.provided shouldBe Vector("org.goldenport:goldenport-cncf_3:0.4.8-SNAPSHOT")
      manifest.shared shouldBe Vector("org.postgresql:postgresql:42.7.3")
      manifest.local shouldBe Vector("com.example:legacy-driver:1.2.0")
      manifest.repositories shouldBe Vector("maven-central", "https://www.simplemodeling.org/repository/maven")
      }
    }

    "E64 extract CAR root lib jars as embedded component libraries" must _metadata("E64") {
      "when exercising: extract CAR root lib jars as embedded component libraries" in {
      Given("an extracted CAR directory with a main JAR and root library JAR")
      _with_temp_dir { root =>
        val mainjar = _create_fake_component_jar(root.resolve("component").resolve("main.jar"))
        val depjar = _create_fake_component_jar(root.resolve("lib").resolve("dep.jar"))
        val componentid = "org.goldenport.cncf.Specification"
        val release = "0.1.0"
        Files.writeString(
          root.resolve("component-descriptor.json"),
          _canonical_descriptor_json(componentid, release)
        )
        _write_packaged_runtime_evidence(root, componentid, release)

        When("the CAR directory is resolved")
        val extracted = CarExtractor.resolveDirectory(root).toOption.get

        Then("the root library is exposed as a component library")
        extracted.componentMain shouldBe mainjar
        extracted.componentLibs.map(_.getFileName.toString) should contain ("dep.jar")
        extracted.componentLibs should contain (depjar)
      }
      }
    }

    "E65 expose CAR component API jars separately from component implementation libraries" must _metadata("E65") {
      "when exercising: expose CAR component API jars separately from component implementation libraries" in {
      Given("an extracted CAR directory containing an API JAR")
      _with_temp_dir { root =>
        val mainjar = _create_fake_component_jar(root.resolve("component").resolve("main.jar"))
        val apijar = _create_fake_component_jar(root.resolve("spi").resolve("sample-api.jar"))
        val componentid = "org.goldenport.cncf.Specification"
        val release = "0.1.0"
        Files.writeString(
          root.resolve("component-descriptor.json"),
          _canonical_descriptor_json(componentid, release)
        )
        _write_packaged_runtime_evidence(root, componentid, release)

        When("the CAR directory is resolved")
        val extracted = CarExtractor.resolveDirectory(root).toOption.get

        Then("the API JAR is separated from implementation libraries")
        extracted.componentMain shouldBe mainjar
        extracted.componentApiJars shouldBe Vector(apijar)
        extracted.componentLibs should not contain apijar
      }
      }
    }

    "E66 preflight generated component API metadata from a development directory" must _metadata("E66") {
      "when exercising: preflight generated component API metadata from a development directory" in {
      _with_temp_dir { root =>
        Given("a component development directory with generated API metadata and its API JAR")
        val classdir = Files.createDirectories(root.resolve("target").resolve("scala-3.3.8").resolve("classes"))
        _write_runtime_classpath(root, classdir, "sample", "0.1.0-SNAPSHOT", "sample")
        val apidir = ComponentRepository.ComponentDevDirRepository.devComponentApiDirectory(root)
        val apijar = _create_fake_component_jar(apidir.resolve("spi").resolve("sample-api.jar"))
        Files.createDirectories(apidir)
        Files.writeString(
          apidir.resolve("component-api-descriptor.json"),
          """{"schemaVersion":"cncf.component-api.v1","component":{"name":"sample","version":"0.1.0-SNAPSHOT"},"provided":[{"apiClass":"example.api.SampleApi","packages":["example.api"],"abiHash":"sha256:sample","artifactPath":"spi/sample-api.jar"}],"required":[]}"""
        )
        val subsystem = TestComponentFactory.emptySubsystem("dev-api-spec")
        val params = ComponentCreate(subsystem, ComponentOrigin.Repository("component-dev-dir"))
        val repository = ComponentRepository.ComponentDevDirRepository.Specification(root).build(params)

        When("the development assembly API is prepared")
        val metadata = repository.prepareAssemblyApi().toOption.get

        Then("the generated API JAR participates in the same metadata model as a packaged CAR")
        metadata.artifacts.map(_.contract.apiClass) shouldBe Vector("example.api.SampleApi")
        metadata.artifacts.head.contract.artifactPath shouldBe "spi/sample-api.jar"
        metadata.artifacts.head.classes should not be empty
        Files.isRegularFile(apijar) shouldBe true
      }
      }
    }

    "E67 reject a development API descriptor whose declared JAR is missing" must _metadata("E67") {
      "when exercising: reject a development API descriptor whose declared JAR is missing" in {
      _with_temp_dir { root =>
        Given("a component development directory whose generated descriptor references an absent API JAR")
        val classdir = Files.createDirectories(root.resolve("target").resolve("scala-3.3.8").resolve("classes"))
        _write_runtime_classpath(root, classdir, "sample", "0.1.0-SNAPSHOT", "sample")
        val apidir = ComponentRepository.ComponentDevDirRepository.devComponentApiDirectory(root)
        Files.createDirectories(apidir)
        Files.writeString(
          apidir.resolve("component-api-descriptor.json"),
          """{"schemaVersion":"cncf.component-api.v1","component":{"name":"sample","version":"0.1.0-SNAPSHOT"},"provided":[{"apiClass":"example.api.SampleApi","packages":["example.api"],"abiHash":"sha256:sample","artifactPath":"spi/missing-api.jar"}],"required":[]}"""
        )
        val subsystem = TestComponentFactory.emptySubsystem("dev-api-spec")
        val params = ComponentCreate(subsystem, ComponentOrigin.Repository("component-dev-dir"))
        val repository = ComponentRepository.ComponentDevDirRepository.Specification(root).build(params)

        When("the development assembly API is prepared")
        val result = repository.prepareAssemblyApi()

        Then("startup reports the exact missing generated API artifact")
        result match {
          case Consequence.Failure(conclusion) =>
            conclusion.display should include ("spi/missing-api.jar")
          case Consequence.Success(value) =>
            fail(s"expected missing development API JAR failure but got $value")
        }
      }
      }
    }

    "E68 preserve assembly API validation failures through consequence-aware discovery" must _metadata("E68") {
      "when exercising: preserve assembly API validation failures through consequence-aware discovery" in {
      _with_temp_dir { root =>
        Given("a development component requiring an API absent from the assembly")
        val classdir = Files.createDirectories(root.resolve("target").resolve("scala-3.3.8").resolve("classes"))
        _write_runtime_classpath(root, classdir, "consumer", "1.0.0", "consumer")
        val apidir = ComponentRepository.ComponentDevDirRepository.devComponentApiDirectory(root)
        Files.createDirectories(apidir)
        Files.writeString(
          apidir.resolve("component-api-descriptor.json"),
          """{"schemaVersion":"cncf.component-api.v1","component":{"name":"consumer","version":"1.0.0"},"provided":[],"required":[{"apiClass":"example.api.MissingApi","required":true}]}"""
        )
        val subsystem = TestComponentFactory.emptySubsystem("dev-api-validation-spec")
        val params = ComponentCreate(subsystem, ComponentOrigin.Repository("component-dev-dir"))
        val repository = ComponentRepository.ComponentDevDirRepository.Specification(root).build(params)
        val metadata = repository.prepareAssemblyApi().toOption
          .getOrElse(fail("assembly API metadata was not prepared"))
        val expectedresult = AssemblyApiClassLoader.create(getClass.getClassLoader, metadata)

        When("the assembly is discovered through the consequence-aware entry point")
        val result = ComponentRepository.discoverAssemblyC(Vector(repository))

        Then("the classloader validation Conclusion is not replaced by a generic component failure")
        (expectedresult, result) match {
          case (Consequence.Failure(expected), Consequence.Failure(actual)) =>
            actual.status shouldBe expected.status
            actual.observation.taxonomy shouldBe expected.observation.taxonomy
            actual.interpretation shouldBe expected.interpretation
            actual.disposition shouldBe expected.disposition
            actual.display shouldBe expected.display
          case (_, Consequence.Success(components)) =>
            fail(s"expected missing assembly API failure but discovered: ${components.map(_.name).mkString(",")}")
          case _ =>
            fail("expected assembly API validation to produce a failure")
        }
      }
      }
    }

    "E69 preserve development admission failures through discovery-only assembly resolution" must _metadata("E69") {
      "when exercising: preserve development admission failures through discovery-only assembly resolution" in {
      _with_temp_dir { root =>
        Given("a development repository whose prepared manifest is removed after repository construction")
        val classdir = Files.createDirectories(root.resolve("target").resolve("scala-3.3.8").resolve("classes"))
        _write_runtime_classpath(root, classdir, "sample", "0.1.0-SNAPSHOT", "sample")
        val subsystem = TestComponentFactory.emptySubsystem("development-admission-consequence")
        val repository = ComponentRepository.ComponentDevDirRepository.Specification(root).build(
          ComponentCreate(subsystem, ComponentOrigin.Repository("component-dev-dir"))
        )
        Files.delete(ComponentRepository.ComponentDevDirRepository.runtimeManifestFile(root))
        val expected = ComponentRepository.ComponentDevDirRepository.validate(root)

        When("the repository is used only on the discovery side of assembly resolution")
        val result = ComponentRepository.discoverAssemblyC(Vector.empty, Vector(repository))

        Then("the original development-admission Conclusion is retained")
        (expected, result) match {
          case (Consequence.Failure(expectedconclusion), Consequence.Failure(actualconclusion)) =>
            actualconclusion.status shouldBe expectedconclusion.status
            actualconclusion.observation.taxonomy shouldBe expectedconclusion.observation.taxonomy
            actualconclusion.interpretation shouldBe expectedconclusion.interpretation
            actualconclusion.disposition shouldBe expectedconclusion.disposition
            actualconclusion.display shouldBe expectedconclusion.display
          case _ =>
            fail("expected discovery-only development admission to preserve a failure conclusion")
        }
      }
      }
    }

    "E70 normalize missing runtime evidence through component development repository validation" must _metadata("E70") {
      "when exercising: normalize missing runtime evidence through component development repository validation" in {
      _with_temp_dir { root =>
        Given("a prepared component development directory whose runtime classpath disappears")
        val classdir = Files.createDirectories(root.resolve("target").resolve("scala-3.3.8").resolve("classes"))
        _write_runtime_classpath(root, classdir, "sample", "0.1.0-SNAPSHOT", "sample")
        val classpath = ComponentRepository.ComponentDevDirRepository.runtimeClasspathFile(root)
        Files.delete(classpath)

        When("the component development repository validates its evidence")
        val result = ComponentRepository.ComponentDevDirRepository.validate(root)

        Then("the integration boundary preserves a resource-invalid recovery consequence")
        result match {
          case Consequence.Failure(conclusion) =>
            conclusion.observation.taxonomy.category.name shouldBe "resource"
            conclusion.observation.taxonomy.symptom.name shouldBe "invalid"
            conclusion.display should include(classpath.toString)
            conclusion.display should include("development-side runtime evidence error")
          case Consequence.Success(_) =>
            fail("missing runtime evidence must not pass repository validation")
        }
      }
      }
    }

    "E71 share runtime ABI packages without sharing generated component implementations" must _metadata("E71") {
      "when exercising: share runtime ABI packages without sharing generated component implementations" in {
      Given("runtime ABI types and generated component implementation types")

      When("classloader parent-first policy is evaluated")
      val runtimeabi = ComponentLocalFirstClassLoader.isParentFirst("org.simplemodeling.model.Entity")

      Then("runtime ABI types are parent-first while component implementations stay local-first")
      runtimeabi shouldBe true
      ComponentLocalFirstClassLoader.isParentFirst("cats.Monad") shouldBe true
      ComponentLocalFirstClassLoader.isParentFirst("io.circe.Json") shouldBe true
      ComponentLocalFirstClassLoader.isParentFirst("org.simplemodeling.textus.useraccount.ComponentFactory") shouldBe false
      }
    }

    "E72 share generated component API contracts across CAR classloaders" must _metadata("E72") {
      "when exercising: share generated component API contracts across CAR classloaders" in {
      Given("generated API and implementation class names")
      When("classloader parent-first policy is evaluated")
      val api = ComponentLocalFirstClassLoader.isParentFirst(
        "org.simplemodeling.textus.scraper.api.TextusScraperApi"
      )
      val implementation = ComponentLocalFirstClassLoader.isParentFirst(
        "org.simplemodeling.textus.scraper.impl.ComponentFactory"
      )

      Then("the API is shared while the implementation remains local")
      api shouldBe true
      implementation shouldBe false
      }
    }

    "E73 detect resolved shared dependency module conflicts" must _metadata("E73") {
      "when exercising: detect resolved shared dependency module conflicts" in {
      Given("resolved modules containing two versions of one coordinate")
      val modules = Vector(
        CoursierComponentDependencyResolver.ResolvedModule("com.example", "driver", "1.0.0"),
        CoursierComponentDependencyResolver.ResolvedModule("com.example", "driver", "2.0.0"),
        CoursierComponentDependencyResolver.ResolvedModule("com.example", "other", "1.0.0")
      )

      When("module conflicts are detected")
      val conflicts = ComponentDependencyPool.moduleConflicts(modules)

      Then("the conflicting coordinate and versions are reported deterministically")
      conflicts shouldBe Vector("com.example:driver resolved=1.0.0,2.0.0")
      }
    }

    "E74 parse coursier resolved modules deterministically" must _metadata("E74") {
      "when exercising: parse coursier resolved modules deterministically" in {
      Given("coursier output containing coordinates, configuration suffixes, and ignored lines")
      When("resolved modules are parsed")
      val modules = CoursierComponentDependencyResolver.parseResolvedModules(
        """com.example:driver:1.0.0
          |com.example:other:2.0.0:default
          |
          |# ignored
          |""".stripMargin
      )

      Then("the normalized modules preserve deterministic order")
      modules shouldBe Vector(
        CoursierComponentDependencyResolver.ResolvedModule("com.example", "driver", "1.0.0"),
        CoursierComponentDependencyResolver.ResolvedModule("com.example", "other", "2.0.0")
      )
      }
    }
    }
  }

  private def _create_car(
    target: Path,
    entries: Seq[(String, Path)]
  ): Unit =
    CarArchiveFixture.write(target, entries)

  private def _controlled_test_descriptor(
    root: Path,
    key: String
  ): Path = {
    val descriptor = root.resolve(s"test-$key.yaml")
    Files.writeString(
      descriptor,
      s"""kind: test-descriptor
         |execution:
         |  profile: controlled
         |  key: $key
         |  time:
         |    mode: manual
         |    start-at: 2026-08-06T00:00:00Z
         |  random:
         |    mode: seeded
         |    seed: $key
         |  ids:
         |    mode: deterministic
         |  scheduler:
         |    mode: manual
         |  ordering:
         |    mode: deterministic
         |""".stripMargin,
      StandardCharsets.UTF_8
    )
    descriptor
  }

  private def _write_runtime_classpath(
    componentdir: Path,
    classdir: Path,
    name: String,
    version: String,
    component: String,
    componentid: Option[String] = None,
    subsystemname: Option[String] = None
  ): Unit = {
    val file = componentdir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")
    val cardir = componentdir.resolve("src").resolve("main").resolve("car")
    val descriptor = file.getParent.resolve("component-descriptor.json")
    val effectivecomponent = componentid.getOrElse(_canonical_component_id(component))
    val effectiveid = ComponentId(effectivecomponent)
    val effectivecarname = ComponentReleaseCoordinate
      .require(effectiveid.sharedIdentity, version)
      .mavenArtifactId()
    Files.createDirectories(file.getParent)
    Files.createDirectories(cardir)
    Files.writeString(file, classdir.toString, StandardCharsets.UTF_8)
    Files.writeString(
      descriptor,
      _canonical_development_descriptor_json(effectivecomponent, version, subsystemname),
      StandardCharsets.UTF_8
    )
    val manifestschema = "cncf.car-development-runtime-manifest.v2"
    val descriptoridentity = "target/cncf.d/component-descriptor.json"
    Files.writeString(cardir.resolve("component-descriptor.json"), Files.readString(descriptor, StandardCharsets.UTF_8), StandardCharsets.UTF_8)
    Files.writeString(
      cardir.resolve("abi-manifest.json"),
      s"""{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"${effectiveid.namespace.value()}","id":"${effectiveid.localId.value()}","version":"$version"},"abi":{"version":1,"exports":{"components":[{"namespace":"${effectiveid.namespace.value()}","id":"${effectiveid.localId.value()}"}]},"dependencies":[]}}""",
      StandardCharsets.UTF_8
    )
    val classpathidentity = s"project:${componentdir.relativize(classdir).toString.replace('\\', '/')}"
    val evidence = Vector(
      ("target/cncf.d/runtime-classpath.txt", _sha256(file), Some(_sha256(classpathidentity.getBytes(StandardCharsets.UTF_8)))),
      (descriptoridentity, _sha256(componentdir.resolve(descriptoridentity)), None),
      ("src/main/car/abi-manifest.json", _sha256(cardir.resolve("abi-manifest.json")), None)
    )
    val entries = evidence.map { case (path, digest, logical) =>
      val logicalfield = logical.map(value => s""""logicalSha256":"$value",""").getOrElse("")
      s"""{$logicalfield"path":"$path","sha256":"$digest"}"""
    }.mkString("[", ",", "]")
    val evidencedigest = _sha256(evidence.map { case (path, digest, logical) =>
      s"$path\t$digest\t${logical.getOrElse("")}"
    }.mkString("\n").getBytes(StandardCharsets.UTF_8))
    Files.writeString(
      file.getParent.resolve("car-runtime-manifest.json"),
      s"""{"schemaVersion":"$manifestschema","sourceKind":"development-directory","car":{"name":"$effectivecarname","version":"$version","component":"${effectiveid.localId.value()}"},"runtime":{"cncf":{"minimum":"${CncfVersion.current}","excluded":[],"tested":["${CncfVersion.current}"]}},"evidence":$entries,"integrity":{"algorithm":"SHA-256","evidenceSha256":"$evidencedigest"}}""",
      StandardCharsets.UTF_8
    )
  }

  private def _sha256(path: Path): String = _sha256(Files.readAllBytes(path))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _canonical_component_id(component: String): String = {
    val local = component.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty).map(_.capitalize).mkString
    s"org.goldenport.fixture.$local"
  }

  private def _canonical_component_descriptor(
    componentid: ComponentId,
    release: String
  ): ComponentDescriptor =
    ComponentDescriptor(
      name = Some(componentid.name),
      version = Some(release),
      componentName = Some(componentid.name),
      schemaVersion = Some(3),
      componentId = Some(componentid)
    )

  private def _create_canonical_component_car(
    target: Path,
    content: Path,
    componentid: String,
    release: String,
    componentjar: Path
  ): Unit = {
    Files.createDirectories(target.getParent)
    Files.createDirectories(content.resolve("component"))
    Files.copy(componentjar, content.resolve("component").resolve("main.jar"))
    Files.writeString(
      content.resolve("component-descriptor.json"),
      _canonical_descriptor_json(componentid, release),
      StandardCharsets.UTF_8
    )
    _write_packaged_runtime_evidence(content, componentid, release)
    val entries = Using.resource(Files.walk(content)) { stream =>
      stream.iterator().asScala
        .filter(Files.isRegularFile(_))
        .toVector
        .sortBy(_.toString)
        .map(path => content.relativize(path).toString.replace('\\', '/') -> path)
    }
    _create_car(target, entries)
  }

  private def _canonical_descriptor_json(
    componentid: String,
    release: String
  ): String = {
    val segments = componentid.split("\\.").toVector
    val namespace = segments.dropRight(1).mkString(".")
    val localid = segments.last
    s"""{"schemaVersion":3,"component":{"namespace":"$namespace","id":"$localid","version":"$release"}}"""
  }

  private def _canonical_development_descriptor_json(
    componentid: String,
    release: String,
    subsystemname: Option[String] = None
  ): String = {
    val subsystem = subsystemname.map(name => s""","subsystem":"$name"""").getOrElse("")
    _canonical_descriptor_json(componentid, release).stripSuffix("}") + subsystem + "}"
  }

  private def _write_packaged_runtime_evidence(
    root: Path,
    componentid: String,
    release: String
  ): Unit = {
    val id = org.goldenport.cncf.testutil.TestComponentFactory.componentId(componentid)
    val coordinate = ComponentReleaseCoordinate.require(id.sharedIdentity, release)
    val artifactname = coordinate.mavenArtifactId()
    Files.writeString(
      root.resolve("abi-manifest.json"),
      s"""{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"${id.namespace.value()}","id":"${id.localId.value()}","version":"$release"},"abi":{"version":1,"exports":{"components":[{"namespace":"${id.namespace.value()}","id":"${id.localId.value()}"}]},"dependencies":[]}}""",
      StandardCharsets.UTF_8
    )
    val entries = Using.resource(Files.walk(root)) { stream =>
      stream.iterator().asScala
        .filter(path => Files.isRegularFile(path))
        .map(path => root.relativize(path).toString.replace('\\', '/'))
        .filter(_ != "car-runtime-manifest.json")
        .toVector
        .sorted
        .map { relative =>
          s"""{"path":"$relative","sha256":"${_sha256(root.resolve(relative))}"}"""
        }
        .mkString("[", ",", "]")
    }
    Files.writeString(
      root.resolve("car-runtime-manifest.json"),
      s"""{"schemaVersion":"cncf.car-runtime-manifest.v1","car":{"name":"$artifactname","version":"$release","component":"${id.name}"},"runtime":{"cncf":{"minimum":"${CncfVersion.current}","maximum":null,"excluded":[],"tested":["${CncfVersion.current}"]}},"integrity":{"algorithm":"SHA-256","entries":$entries}}""",
      StandardCharsets.UTF_8
    )
  }

  private def _create_zip(
    target: Path,
    entries: Seq[(String, Path)]
  ): Unit = {
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zos =>
      entries.foreach { case (name, file) =>
        zos.putNextEntry(new ZipEntry(name))
        Files.copy(file, zos)
        zos.closeEntry()
      }
    }
  }

  private def _create_fake_component_jar(target: Path): Path = {
    val factoryclassentry =
      "org/goldenport/cncf/component/builtin/specification/SpecificationComponent$Factory.class"
    Option(target.getParent).foreach(Files.createDirectories(_))
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zos =>
      zos.putNextEntry(new ZipEntry(factoryclassentry))
      zos.closeEntry()
    }
    target
  }

  private def _create_class_component_jar(
    target: Path,
    classes: Seq[Class[?]]
  ): Path = {
    Option(target.getParent).foreach(Files.createDirectories(_))
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zos =>
      classes.foreach { cls =>
        val entry = s"${cls.getName.replace('.', '/')}.class"
        val resource = Option(getClass.getClassLoader.getResource(entry))
          .getOrElse(fail(s"missing test class resource: ${entry}"))
        zos.putNextEntry(new ZipEntry(entry))
        Using.resource(resource.openStream()) { in =>
          in.transferTo(zos)
        }
        zos.closeEntry()
      }
    }
    target
  }

  private def _copy_devdir_sample_classes(target: Path): Unit = {
    val testclasses = Path.of(
      classOf[devdirsample.DevDirSampleComponent]
        .getProtectionDomain
        .getCodeSource
        .getLocation
        .toURI
    )
    val source = testclasses.resolve("devdirsample")
    Files.createDirectories(target.resolve("devdirsample"))
    Using.resource(Files.list(source)) { stream =>
      stream.iterator().asScala
        .filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".class"))
        .foreach { p =>
          Files.copy(p, target.resolve("devdirsample").resolve(p.getFileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }
  }

  private def _with_temp_dir[T](body: Path => T): T = {
    val workdir = Files.createDirectories(
      Path.of("target", "component-repository-car-spec", "work").toAbsolutePath.normalize
    )
    val base = Files.createTempDirectory(workdir, "component-repo-spec")
    try body(base)
    finally {
      _delete_recursively(base)
    }
  }

  private def _delete_recursively(base: Path): Unit = {
    if (Files.exists(base)) {
      Using.resource(Files.walk(base)) { stream =>
        stream
          .sorted(Comparator.reverseOrder())
          .iterator()
          .asScala
          .foreach(p => Files.deleteIfExists(p))
      }
    }
  }
}

final class CanonicalArchiveFixtureComponent extends Component

final class CanonicalArchiveFixtureFactory extends Component.PrimaryComponentFactory {
  protected def create_Component(params: ComponentCreate): Component =
    new CanonicalArchiveFixtureComponent

  protected def create_Core(
    params: ComponentCreate,
    component: Component
  ): Component.Core = {
    val componentid = ComponentId("org.goldenport.fixture.CanonicalArchiveFixture")
    Component.Core.create(componentid.name, componentid, ComponentInstanceId.default(componentid), Protocol.empty, this)
  }
}

final class NamespaceIsolatedArchiveFixtureComponent extends Component

final class NamespaceIsolatedArchiveFixtureFactory extends Component.PrimaryComponentFactory {
  protected def create_Component(params: ComponentCreate): Component =
    new NamespaceIsolatedArchiveFixtureComponent

  protected def create_Core(
    params: ComponentCreate,
    component: Component
  ): Component.Core = {
    val componentid = ComponentId("org.example.fixture.CanonicalArchiveFixture")
    Component.Core.create(componentid.name, componentid, ComponentInstanceId.default(componentid), Protocol.empty, this)
  }
}
