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
import org.goldenport.cncf.component.{AssemblyApiClassLoader, CarExtractor, Component, ComponentCreate, ComponentDependencyManifest, ComponentDependencyPool, ComponentDescriptor, ComponentDescriptorLoader, ComponentFactory, ComponentLocalFirstClassLoader, ComponentOrigin, CoursierComponentDependencyResolver, RepositoryParameterProbeComponent, RepositoryParameterProbeFactory}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.SpiResolver
import org.goldenport.cncf.spi.ai.runner.{AiGenerateRequest, AiRunnerSocket}
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemFactory, Subsystem}
import org.goldenport.cncf.component.testutil.CarArchiveFixture
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.{Configuration, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationTrace

/*
 * @since   Feb.  4, 2026
 *  version Apr. 25, 2026
 *  version May. 25, 2026
 *  version Jul. 31, 2026
 * @version Aug.  6, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentRepositoryCarSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with GivenWhenThen {
  override def beforeAll(): Unit = {
    val workarea = WorkAreaSpace.create(RuntimeConfig.default)
    GlobalContext.set(GlobalContext(workarea))
  }

  "ComponentDirRepository" should {
    "standard and local repository selection" which {
    "exclude Scala 3 package implementation classes from component discovery" in {
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

    "expose SimpleModeling CAR and SAR standard repository URLs" in {
      Given("the framework-owned standard repository policy")

      When("the CAR and SAR repository URLs are requested")
      val carurl = ComponentRepository.standardComponentRepositoryUrl()
      val sarurl = ComponentRepository.standardSubsystemRepositoryUrl()

      Then("the SimpleModeling repository endpoints are returned")
      carurl shouldBe "https://www.simplemodeling.org/repository/car"
      sarurl shouldBe "https://www.simplemodeling.org/repository/sar"
    }

    "parse SimpleModeling CAR and SAR URLs as standard search repository specs" in {
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

    "append SimpleModeling standard URL repositories to the default search set" in {
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

    "use cache as the default standard repository root" in {
      Given("the framework default repository policy")

      When("the standard repository root is resolved")
      val root = ComponentRepository.defaultStandardRepositoryDir()

      Then("the root is located in the CNCF cache")
      root.toString should include (".cncf")
      root.toString should include ("cache")
    }

    "append existing local CNCF repository dirs before standard repositories" in {
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

    "not append cache as a plain component directory" in {
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

    "not resolve snapshots from the standard cache repository" in {
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

    "not discover requested snapshots from the standard cache repository" in {
      Given("a requested SNAPSHOT CAR stored under the standard repository cache")
      val subsystem = new Subsystem(
        name = "test-standard-repo-snapshot",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { cache =>
        val snapshotdir = cache.resolve("car").resolve("sample-component").resolve("0.1.1-SNAPSHOT")
        Files.createDirectories(snapshotdir)
        val fakecomponentjar = _create_fake_component_jar(cache.resolve("assets").resolve("component-main-snapshot.jar"))
        val descriptor = cache.resolve("component-descriptor-snapshot.json")
        Files.writeString(
          descriptor,
          """{"name":"sample-component","version":"0.1.1-SNAPSHOT","component":"sample-component"}"""
        )
        _create_car(
          snapshotdir.resolve("sample-component-0.1.1-SNAPSHOT.car"),
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
            Vector(ComponentDescriptor(name = Some("sample-component"), version = Some("0.1.1-SNAPSHOT"), componentName = Some("sample-component")))
          )
        )

        When("component discovery scans the standard repository")
        val discovered =
          repository
            .discover()
            .flatMap(_.componentDescriptors.flatMap(_.componentName))

        Then("the requested mutable component is not discovered")
        discovered should not contain "sample-component"
      }
    }

    "not let the standard repository block a requested local snapshot component" in {
      Given("a local SNAPSHOT CAR before an empty standard repository")
      val subsystem = new Subsystem(
        name = "test-local-snapshot-standard-fallback",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      _with_temp_dir { root =>
        val localcar = root.resolve("local").resolve("repository").resolve("car")
        val artifactdir = localcar.resolve("sample-component").resolve("0.1.1-SNAPSHOT")
        Files.createDirectories(artifactdir)
        val fakecomponentjar = _create_fake_component_jar(root.resolve("assets").resolve("component-main-local-snapshot.jar"))
        val descriptor = root.resolve("component-descriptor-local-snapshot.json")
        Files.writeString(
          descriptor,
          """{"name":"sample-component","version":"0.1.1-SNAPSHOT","component":"sample-component"}"""
        )
        _create_car(
          artifactdir.resolve("sample-component-0.1.1-SNAPSHOT.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> descriptor
          )
        )
        val descriptors = Vector(
          ComponentDescriptor(name = Some("sample-component"), version = Some("0.1.1-SNAPSHOT"), componentName = Some("sample-component"))
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
        components.flatMap(_.componentDescriptors.flatMap(_.componentName)) should contain ("sample-component")
      }
    }

    "resolve top-level component invocation by explicit component version" in {
      Given("two released CAR versions and an explicit older component version")
      _with_temp_dir { repositoryroot =>
        val currentdir = repositoryroot.resolve("car").resolve("blog-component").resolve("0.0.2")
        val olddir = repositoryroot.resolve("car").resolve("blog-component").resolve("0.0.1")
        Files.createDirectories(currentdir)
        Files.createDirectories(olddir)
        val fakecomponentjar = _create_fake_component_jar(repositoryroot.resolve("assets").resolve("blog-main.jar"))
        val currentdescriptor = repositoryroot.resolve("component-descriptor-blog-current.json")
        val olddescriptor = repositoryroot.resolve("component-descriptor-blog-old.json")
        Files.writeString(
          currentdescriptor,
          """{"name":"blog-component","version":"0.0.2","component":"blog-component"}"""
        )
        Files.writeString(
          olddescriptor,
          """{"name":"blog-component","version":"0.0.1","component":"blog-component"}"""
        )
        _create_car(
          currentdir.resolve("blog-component-0.0.2.car"),
          Seq("component/main.jar" -> fakecomponentjar, "component-descriptor.json" -> currentdescriptor)
        )
        _create_car(
          olddir.resolve("blog-component-0.0.1.car"),
          Seq("component/main.jar" -> fakecomponentjar, "component-descriptor.json" -> olddescriptor)
        )
        val invocation = org.goldenport.cncf.cli.CncfRuntime.RuntimeInvocationParameters(
          actualArgs = Array("--textus.component=blog-component", "--textus.component.version=0.0.1", "server"),
          subsystemName = None,
          componentName = Some("blog-component"),
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
        resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.componentFileKey}=${olddir.resolve("blog-component-0.0.1.car")}")
      }
    }

    "skip a stale local CAR version when an explicit version is available from the next repository" in {
      Given("a stale local snapshot and the requested public CAR version")
      _with_temp_dir { repositoryroot =>
        val localroot = repositoryroot.resolve("local")
        val remoteroot = repositoryroot.resolve("remote")
        val localdir = localroot.resolve("textus-sanpomap").resolve("0.1.1-SNAPSHOT")
        val remotedir = remoteroot.resolve("textus-sanpomap").resolve("0.2.0")
        Files.createDirectories(localdir)
        Files.createDirectories(remotedir)
        val localdescriptor = repositoryroot.resolve("local-component-descriptor.json")
        val remotedescriptor = repositoryroot.resolve("remote-component-descriptor.json")
        Files.writeString(
          localdescriptor,
          """{"name":"textus-sanpomap","version":"0.1.1-SNAPSHOT","component":"textus-sanpomap"}"""
        )
        Files.writeString(
          remotedescriptor,
          """{"name":"textus-sanpomap","version":"0.2.0","component":"textus-sanpomap"}"""
        )
        val localcar = localdir.resolve("textus-sanpomap-0.1.1-SNAPSHOT.car")
        val remotecar = remotedir.resolve("textus-sanpomap-0.2.0.car")
        _create_car(localcar, Seq("component-descriptor.json" -> localdescriptor))
        _create_car(remotecar, Seq("component-descriptor.json" -> remotedescriptor))
        val invocation = org.goldenport.cncf.cli.CncfRuntime.RuntimeInvocationParameters(
          actualArgs = Array("--textus.component=textus-sanpomap", "--textus.component.version=0.2.0", "command"),
          subsystemName = None,
          componentName = Some("textus-sanpomap"),
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

    "reject a component file when its CAR descriptor does not match the requested version" in {
      Given("a component-file CAR with a fixed descriptor version")
      _with_temp_dir { root =>
        val descriptor = root.resolve("component-descriptor.json")
        val car = root.resolve("textus-sanpomap-0.1.1-SNAPSHOT.car")
        Files.writeString(
          descriptor,
          """{"name":"textus-sanpomap","version":"0.1.1-SNAPSHOT","component":"textus-sanpomap"}"""
        )
        _create_car(car, Seq("component-descriptor.json" -> descriptor))
        val spec = ComponentRepository.ComponentFileRepository.Specification(car)

        When("component-file resolution receives a matching and a different explicit version")
        val matching = spec.resolveComponentArchivePath("textus-sanpomap", Some("0.1.1-SNAPSHOT"))
        val mismatched = spec.resolveComponentArchivePath("textus-sanpomap", Some("0.2.0"))

        Then("only the matching descriptor version can resolve the supplied CAR")
        matching shouldBe Some(car)
        mismatched shouldBe None
      }
    }

    }

    "development and expanded repository routes" which {
    "parse explicit development and expanded CAR directory routes" in {
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

    "preserve repository-looking tokens after the command sentinel" in {
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

    "reject explicit component development directory without runtime classpath" in {
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

    "fail repository space creation when configured component development directory is invalid" in {
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

    "reject CAR schemes in component development directory configuration" in {
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

    "treat repository component development directories as direct dev-dir repositories" in {
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

    "parse explicit expanded SAR directory routes" in {
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

    "activate subsystem development directory from runtime config alias" in {
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

    "resolve subsystem descriptor from a subsystem development directory" in {
      Given("a subsystem development directory containing one descriptor")
      _with_temp_dir { root =>
        Files.createDirectories(root.resolve("subsystem"))
        Files.writeString(
          root.resolve("subsystem").resolve("subsystem-descriptor.yaml"),
          """subsystem: cwitter
            |version: 0.1.0
            |components:
            |  - component: cwitter
            |""".stripMargin
        )

        val spec = ComponentRepository.SubsystemDevDirRepository.Specification(root)

        When("the named subsystem descriptor is resolved")
        val resolved = spec.resolveSubsystemDescriptor("cwitter")

        Then("the development descriptor is returned")
        resolved.map(_.subsystemName) shouldBe Some("cwitter")
      }
    }

    "infer subsystem name from explicit subsystem development directory" in {
      Given("an explicit subsystem development directory with one descriptor")
      _with_temp_dir { root =>
        Files.createDirectories(root.resolve("subsystem"))
        Files.writeString(
          root.resolve("subsystem").resolve("subsystem-descriptor.yaml"),
          """subsystem: cwitter
            |version: 0.1.0
            |components:
            |  - component: cwitter
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

    "infer subsystem name from explicit component CAR directory" in {
      Given("an expanded component CAR directory with one component descriptor")
      _with_temp_dir { root =>
        val cardir = root.resolve("app.car.d")
        Files.createDirectories(cardir.resolve("component"))
        Files.writeString(
          cardir.resolve("component-descriptor.yaml"),
          """name: cwitter
            |version: 0.1.0
            |component: cwitter
            |""".stripMargin
        )

        val bootstrap = org.goldenport.cncf.cli.CncfRuntime.bootstrap(
          root,
          Array("--component-car-dir", cardir.toString, "server")
        )

        When("the runtime resolves its synthesized subsystem descriptor")
        val descriptor = org.goldenport.cncf.subsystem.GenericSubsystemFactory.resolveDescriptor(bootstrap.configuration)

        Then("the component identity supplies the subsystem and binding names")
        descriptor.map(_.subsystemName) shouldBe Some("cwitter")
        descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("cwitter")
      }
    }

    "infer subsystem name from explicit component development directory" in {
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
        descriptor.map(_.subsystemName) shouldBe Some("cwitter")
        descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("cwitter")
      }
    }

    "not auto-append a packaged CAR when component development directory is explicit" in {
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
          """{"name":"packaged-cwitter","version":"0.1.0","component":"packaged-cwitter"}"""
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
        descriptor.map(_.subsystemName) shouldBe Some("dev-cwitter")
        descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("dev-cwitter")
      }
    }

    "resolve only the prepared generated descriptor from a component development directory" in {
      Given("a schema-v2 generated descriptor and a later competing source descriptor")
      _with_temp_dir { componentdir =>
        val classdir = Files.createDirectories(componentdir.resolve("target/scala-3.3.8/classes"))
        _write_runtime_classpath(
          componentdir,
          classdir,
          "sample-car-artifact",
          "0.1.0-SNAPSHOT",
          "sample-component",
          descriptorjson = Some(
            """{
              |  "schemaVersion": 2,
              |  "name": "sample-car-artifact",
              |  "version": "0.1.0-SNAPSHOT",
              |  "component": { "name": "sample-component" },
              |  "componentStyle": {
              |    "apiVersion": "cncf.textus/v1",
              |    "provider": "cncf",
              |    "id": "full-fledged-with-standalone@1",
              |    "version": 1,
              |    "parameterSchema": { "type": "object", "properties": {}, "required": [], "additionalProperties": false },
              |    "parameters": {},
              |    "provides": {
              |      "bundles": ["domain.full@1"],
              |      "capabilities": ["user.fixed-context-compatible@1", "user.multi-user@1"],
              |      "effective": ["domain.aggregate@1", "domain.command@1", "domain.domain-event@1", "domain.entity@1", "domain.optimistic-concurrency@1", "domain.persistence@1", "domain.projection@1", "domain.query@1", "domain.transaction@1", "user.fixed-context-compatible@1", "user.multi-user@1"]
              |    },
              |    "requires": { "subsystemCapabilities": ["datastore.optimistic-concurrency@1", "datastore.persistent@1", "datastore.transactional@1", "user-context.current@1"] }
              |  }
              |}
              |""".stripMargin
          )
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
          resolveComponentDescriptor("sample-component")
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
        descriptors.map(_.componentName) shouldBe Vector(Some("sample-component"))
        descriptors.flatMap(_.componentStyleSnapshot.map(_.id.canonical)) shouldBe Vector("full-fledged-with-standalone@1")
        developmentdescriptors shouldBe descriptors
        resolved.flatMap(_.name) shouldBe Some("sample-car-artifact")
        resolved.flatMap(_.componentName) shouldBe Some("sample-component")
        factoryresolved.toOption.flatten.map(_.path) shouldBe Some(componentdir)
        factoryresolved.toOption.flatten.map(_.subsystemName) shouldBe Some("sample-component")
        factoryresolved.toOption.flatten.flatMap(_.version) shouldBe Some("0.1.0-SNAPSHOT")
        factoryresolved.toOption.flatten.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("sample-component")
        stale match {
          case Consequence.Failure(conclusion) =>
            conclusion.observation.taxonomy.category.name shouldBe "resource"
            conclusion.observation.taxonomy.symptom.name shouldBe "invalid"
            conclusion.display should include("sbt cozyPrepareRuntime")
          case Consequence.Success(_) =>
            fail("stale prepared descriptor must not fall back to source metadata")
        }
      }
    }

    "infer component development directory identity from discovered component metadata" in {
      Given("a component development directory whose compiled metadata names its component")
      _with_temp_dir { root =>
        val componentdir = root.resolve("01-minimal")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        _copy_devdir_sample_classes(classdir)
        _write_runtime_classpath(componentdir, classdir, "devdirsample", "0.1.0-SNAPSHOT", "devdirsample")
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
            "command", "devdirsample.main.hello"
          ),
          modeHint = Some(org.goldenport.cncf.cli.RunMode.Command)
        ).TAKE

        Then("every boundary retains one inferred development component identity")
        inferred shouldBe Vector("devdirsample")
        descriptor.map(_.subsystemName) shouldBe Some("devdirsample")
        descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("devdirsample")
        initialized.components.map(_.name) should contain ("devdirsample")
        initialized.components.count(_.name == "devdirsample") shouldBe 1
        initialized.components.find(_.name == "devdirsample").map(_.getClass.getName) shouldBe
          Some(classOf[devdirsample.DevDirSamplePrimaryComponent].getName)
      }
    }

    "treat runtime subsystem source aliases as explicit activation during name resolution" in {
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

    "treat expanded SAR directory route as explicit activation during name resolution" in {
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

    "not auto-activate cwd sar.d as a default active repository" in {
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

    "not auto-activate cwd component.d as a default active repository" in {
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

    "not append default component target when an explicit component development directory is active" in {
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

    "treat equals-form expanded CAR route as an explicit activation during name resolution" in {
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

    "resolve descriptor by componentlet name from a car directory" in {
      Given("an expanded CAR directory with one primary component and two componentlets")
      _with_temp_dir { componentdir =>
        _create_fake_component_jar(componentdir.resolve("component").resolve("main.jar"))
        val descriptorpath = componentdir.resolve("component-descriptor.json")
        Files.writeString(
          descriptorpath,
          """{
            |  "component": {
            |    "name": "sample-component",
            |    "kind": "component",
            |    "isPrimary": true
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

        When("the descriptor is resolved through one componentlet name")
        val descriptor = ComponentRepository.resolveComponentDescriptorFromComponentDir(componentdir, "public-notice")

        Then("the primary component and complete componentlet set are retained")
        descriptor.map(_.componentName) shouldBe Some(Some("sample-component"))
        descriptor.toVector.flatMap(_.componentlets.map(_.name)) shouldBe Vector("public-notice", "notice-admin")
      }
    }

    "discover the component wrapped in a car" in {
      Given("a CAR containing one component implementation and descriptor")
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

        Then("the packaged component and descriptor metadata are exposed")
        components should not be empty
        val head = components.head
        head.origin.label should include("component-dir:car:demo-component:0.1.0")
        head.artifactMetadata.map(_.name) shouldBe Some("demo-component")
        head.artifactMetadata.map(_.version) shouldBe Some("0.1.0")
        head.artifactMetadata.map(_.effectiveExtensions.get("driver").getOrElse("")) shouldBe Some("car")
      }
    }

    }

    "packaged CAR and SAR discovery" which {
    "not discover unrelated CARs when an explicitly requested component is absent" in {
      Given("a component repository containing an unrelated CAR and an explicit missing component request")
      val subsystem = TestComponentFactory.emptySubsystem("explicit-component-selection")
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val carpath = componentdir.resolve("unrelated-component.car")
        val componentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("unrelated-main.jar"))
        val descriptor = componentdir.resolve("component-descriptor-unrelated.json")
        Files.writeString(
          descriptor,
          """{"name":"unrelated-component","version":"0.1.0","component":"unrelated-component"}"""
        )
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> componentjar,
            "component-descriptor.json" -> descriptor
          )
        )
        val requested = ComponentDescriptor(
          name = Some("missing-component"),
          version = Some("0.1.0"),
          componentName = Some("missing-component")
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

    "preserve repository priority when the same component coordinate exists later" in {
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
            """{"name":"priority-component","version":"0.1.0-SNAPSHOT","component":"priority-component"}"""
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
          name = Some("priority-component"),
          version = Some("0.1.0-SNAPSHOT"),
          componentName = Some("priority-component")
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

    "use search repositories for assembly API preflight without discovering their components" in {
      Given("one active CAR repository and one API-only search CAR repository")
      val subsystem = TestComponentFactory.emptySubsystem("assembly-api-search")
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { root =>
        def _create_repository_(dirname: String, componentname: String): ComponentRepository = {
          val componentdir = Files.createDirectories(root.resolve(dirname))
          val componentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
          val descriptor = componentdir.resolve("component-descriptor.json")
          Files.writeString(
            descriptor,
            s"""{"name":"${componentname}","version":"0.1.0","component":"${componentname}"}"""
          )
          _create_car(
            componentdir.resolve(s"${componentname}.car"),
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
        val active = _create_repository_("active", "active-component")
        val search = _create_repository_("search", "search-component")

        When("assembly discovery preflights both repositories but activates only the active repository")
        val components = ComponentRepository.discoverAssembly(Vector(active, search), Vector(active))

        Then("the active repository participates in component discovery")
        components.flatMap(_.artifactMetadata.map(_.name)) should contain ("active-component")

        And("the search repository remains an API source only")
        components.flatMap(_.artifactMetadata.map(_.name)) should not contain "search-component"
      }
    }

    "discover a plain Component.Factory from a component CAR" in {
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
            classOf[org.goldenport.cncf.component.repository.fixture.plain.ComponentFactory],
            classOf[org.goldenport.cncf.component.repository.fixture.plain._PlainFactoryBackedComponent]
          )
        )
        val descriptor = componentdir.resolve("component-descriptor-plain-factory.json")
        Files.writeString(
          descriptor,
          """{"name":"plain-factory-component","version":"0.1.0","component":"plain-factory-component"}"""
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
        components.map(_.name) should contain ("plain-factory-primary")
        components.find(_.name == "plain-factory-primary").flatMap(_.factoryOption) should not be empty
      }
    }

    "retain packaged initialization defaults without repeated CAR materialization" in {
      Given("a component CAR whose descriptor supplies a required initialization default")
      _with_temp_dir { componentdir =>
        val carpath = componentdir.resolve("repository-parameter-probe.car")
        val componentjar = _create_class_component_jar(
          componentdir.resolve("assets").resolve("repository-parameter-probe.jar"),
          Seq(
            classOf[RepositoryParameterProbeFactory],
            classOf[RepositoryParameterProbeComponent],
            RepositoryParameterProbeFactory.getClass
          )
        )
        val cardescriptor = componentdir.resolve("component-descriptor-parameter.json")
        Files.writeString(
          cardescriptor,
          """{"name":"repository_parameter_probe","version":"0.1.0","component":"repository_parameter_probe","config":{"provider.limit":"27"}}"""
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
            GenericSubsystemComponentBinding("repository_parameter_probe")
          )
        )

        When("GenericSubsystemFactory discovers and materializes the packaged component")
        RepositoryParameterProbeFactory.resetCreationCount()
        val component = GenericSubsystemFactory
          .default(descriptor, configuration = configuration)
          .components
          .find(_.name == "repository_parameter_probe")
          .getOrElse(fail("repository parameter probe was not materialized"))

        Then("the packaged descriptor remains the fixed packaged-default source")
        val resolution = component.initializationParameters
          .resolve(RepositoryParameterProbeFactory.limitKey)
          .toOption
          .getOrElse(fail("packaged initialization parameter was not resolved"))
        resolution.value shouldBe Some(27)
        resolution.provenance shouldBe ComponentParameterProvenance.PackagedDefault
        RepositoryParameterProbeFactory.creationCount shouldBe 1
      }
    }

    "preserve packaged CAR and SAR initialization failures" in {
      Given("CAR and SAR repositories whose selected component default is malformed")
      _with_temp_dir { root =>
        val componentjar = _create_class_component_jar(
          root.resolve("assets").resolve("repository-parameter-probe.jar"),
          Seq(
            classOf[RepositoryParameterProbeFactory],
            classOf[RepositoryParameterProbeComponent],
            RepositoryParameterProbeFactory.getClass
          )
        )
        val cardescriptor = root.resolve("component-descriptor-invalid-parameter.json")
        Files.writeString(
          cardescriptor,
          """{"name":"repository_parameter_probe","version":"0.1.0","component":"repository_parameter_probe","config":{"provider.limit":"invalid"}}"""
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
          """{"name":"repository-parameter-subsystem","version":"0.1.0","subsystem":"repository-parameter-subsystem","components":[{"name":"repository_parameter_probe"}]}"""
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
        val requested = ComponentDescriptor(componentName = Some("repository_parameter_probe"))
        val params = ComponentCreate(
          subsystem,
          ComponentOrigin.Repository("phase-47"),
          Vector(requested)
        )
        val packageddescriptor = ComponentDescriptorLoader.loadArchive(carpath).toOption
          .getOrElse(fail("packaged parameter descriptor was not readable"))
        val expectedconclusion = new RepositoryParameterProbeFactory()
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

    "wire a socket to a plain-factory provider loaded from a component CAR" in {
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
          """{"name":"plain-ai-runner-provider","version":"0.1.0","component":"plain-ai-runner-provider"}"""
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

        When("SPI resolver wires the CAR provider into the consumer socket")
        val resolved = SpiResolver.resolve(providercomponents :+ consumer)

        Then("the consumer uses the provider loaded from the CAR")
        resolved shouldBe a[Consequence.Success[_]]
        consumer.aiRunner.generate(AiGenerateRequest("hello")).toOption.get.text shouldBe "car:hello"
      }
    }

    "treat component-file CAR as one component and ignore embedded component.d contents" in {
      Given("an application CAR containing another CAR below component.d")
      val subsystem = new Subsystem(
        name = "test-component-file-embedded",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-file")
      _with_temp_dir { componentdir =>
        val appjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("app-main.jar"))
        val providerjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("provider-main.jar"))
        val appdescriptor = componentdir.resolve("component-descriptor-app.json")
        val providerdescriptor = componentdir.resolve("component-descriptor-provider.json")
        Files.writeString(
          appdescriptor,
          """{"name":"app-component","version":"1.0.0","component":"app"}"""
        )
        Files.writeString(
          providerdescriptor,
          """{"name":"textus-user-account","version":"1.0.0","component":"textus-user-account"}"""
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
        componentnames should contain ("app")
        componentnames should not contain ("textus-user-account")
      }
    }

    "activate component-file assembly dependencies from search repositories" in {
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
          """{"name":"component-file-app","version":"0.1.0-SNAPSHOT","component":"component-file-app"}"""
        )
        val assemblydescriptor = root.resolve("assembly-descriptor-art-scene.yaml")
        Files.writeString(
          assemblydescriptor,
          """subsystem: component-file-app
            |version: 0.1.0
            |components:
            |  - name: component-file-app
            |    version: 0.1.0-SNAPSHOT
            |  - name: plain-ai-runner-provider
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
          """{"name":"plain-ai-runner-provider","version":"0.1.0","component":"plain-ai-runner-provider"}"""
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
        initialized.descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) should contain ("plain-ai-runner-provider")
        initialized.components.map { component =>
          component.artifactMetadata.flatMap(_.component).getOrElse(component.name)
        } should contain ("plain-ai-runner-provider")
      }
    }

    "fail startup when a component-file assembly dependency is unresolved" in {
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
          """{"name":"component-file-app","version":"0.1.0-SNAPSHOT","component":"component-file-app"}"""
        )
        val assemblydescriptor = root.resolve("assembly-descriptor-art-scene.yaml")
        Files.writeString(
          assemblydescriptor,
          """subsystem: component-file-app
            |version: 0.1.0
            |components:
            |  - name: component-file-app
            |    version: 0.1.0-SNAPSHOT
            |  - name: missing-ai-runtime-for-component-file-spec
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

        Then("startup fails with the missing assembly component name and source")
        val message = result match {
          case Consequence.Failure(conclusion) => conclusion.display
          case Consequence.Success(value) =>
            val bindings = value.descriptor.toVector.flatMap(_.componentBindings.map(_.componentName))
            val components = value.components.map { component =>
              component.artifactMetadata.flatMap(_.component).getOrElse(component.name)
            }
            fail(s"expected component dependency failure but initialized bindings=${bindings.mkString(",")} components=${components.mkString(",")}")
        }
        message should include ("assembly component dependency not resolved")
        message should include ("missing-ai-runtime-for-component-file-spec:0.2.0-SNAPSHOT")
        message should include (appcar.toString)
        message should include ("assembly-descriptor")
      }
    }

    "skip an invalid car containing zero component jars" in {
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

    "skip an invalid car containing incomplete descriptor" in {
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

    "apply SAR > CAR precedence for extension/config when loading sar containing car" in {
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
          """{"name":"base-component","version":"1.0.0","component":"spec","extension":{"driver":"car-default","hash":"sha256"},"config":{"log.level":"info","feature":"base"}}"""
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
          """{"name":"demo-subsystem","version":"2.0.0","subsystem":"demo","extension":{"driver":"sar-override"},"config":{"feature":"sar","endpoint":"https://example.invalid"}}"""
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
        comp.origin.label should include("component-dir:sar:demo:2.0.0:car:base-component:1.0.0")
        metadata.subsystem shouldBe Some("demo")
        metadata.effectiveExtensions.get("driver") shouldBe Some("sar-override")
        metadata.effectiveExtensions.get("hash") shouldBe Some("sha256")
        metadata.effectiveConfig.get("feature") shouldBe Some("sar")
        metadata.effectiveConfig.get("log.level") shouldBe Some("info")
        metadata.effectiveConfig.get("endpoint") shouldBe Some("https://example.invalid")
      }
    }

    "discover a requested component from the standard CAR repository layout" in {
      Given("a requested component stored in the standard CAR repository layout")
      val subsystem = new Subsystem(
        name = "test-standard-repo",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { repositoryroot =>
        val artifactdir = repositoryroot.resolve("car").resolve("textus-user-account").resolve("0.1.0")
        Files.createDirectories(artifactdir)
        val fakecomponentjar = _create_fake_component_jar(repositoryroot.resolve("assets").resolve("component-main-standard.jar"))
        val descriptor = repositoryroot.resolve("component-descriptor-standard.json")
        Files.writeString(
          descriptor,
          """{"name":"textus-user-account","version":"0.1.0","component":"textus-user-account"}"""
        )
        _create_car(
          artifactdir.resolve("textus-user-account-0.1.0.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> descriptor
          )
        )
        val repository = ComponentRepository.StandardRepository.Specification(
          ComponentRepository.StandardRepositoryKind.Car,
          ComponentRepository.standardComponentRepositoryUrl(),
          repositoryroot
        ).build(
          ComponentCreate(
            subsystem,
            origin,
            Vector(ComponentDescriptor(name = Some("textus-user-account"), version = Some("0.1.0"), componentName = Some("textus-user-account")))
          )
        )
        When("the standard repository is discovered")
        val components = repository.discover()

        Then("the requested component is loaded")
        components.map(_.name) should contain ("spec")
        components.flatMap(_.artifactMetadata).flatMap(_.component) should contain ("textus-user-account")
      }
    }

    "fetch requested component CARs from a standard repository before discovery" in {
      Given("two requested component CARs in a remote standard repository")
      val subsystem = new Subsystem(
        name = "test-standard-repo-fetch",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { root =>
        val remote = root.resolve("remote")
        val cache = root.resolve("cache")
        val artifactdir = remote.resolve("textus-user-account").resolve("0.1.1")
        Files.createDirectories(artifactdir)
        val fakecomponentjar = _create_fake_component_jar(root.resolve("assets").resolve("component-main-standard-fetch.jar"))
        val descriptor = root.resolve("component-descriptor-standard-fetch.json")
        Files.writeString(
          descriptor,
          """{"name":"textus-user-account","version":"0.1.1","component":"textus-user-account"}"""
        )
        _create_car(
          artifactdir.resolve("textus-user-account-0.1.1.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> descriptor
          )
        )
        val blogartifactdir = remote.resolve("textus-blog").resolve("0.0.2")
        Files.createDirectories(blogartifactdir)
        val blogdescriptor = root.resolve("component-descriptor-blog-standard-fetch.json")
        Files.writeString(
          blogdescriptor,
          """{"name":"textus-blog","version":"0.0.2","component":"textus-blog"}"""
        )
        _create_car(
          blogartifactdir.resolve("textus-blog-0.0.2.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> blogdescriptor
          )
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
              ComponentDescriptor(name = Some("textus-blog"), version = Some("0.0.2"), componentName = Some("textus-blog")),
              ComponentDescriptor(name = Some("textus-user-account"), version = Some("0.1.1"), componentName = Some("textus-user-account"))
            )
          )
        )

        When("the standard repository is discovered")
        val components = repository.discover()

        Then("both CARs are cached and each component keeps its own descriptors")
        cache.resolve("car").resolve("textus-user-account").resolve("0.1.1").resolve("textus-user-account-0.1.1.car").toFile should exist
        cache.resolve("car").resolve("textus-blog").resolve("0.0.2").resolve("textus-blog-0.0.2.car").toFile should exist
        components.map(_.name) should contain ("spec")
        components.flatMap(_.artifactMetadata).flatMap(_.component) should contain ("textus-user-account")
        val accountcomponents = components.filter(_.artifactMetadata.flatMap(_.component).contains("textus-user-account"))
        accountcomponents.flatMap(_.componentDescriptors.flatMap(_.componentName)) should contain ("textus-user-account")
        accountcomponents.flatMap(_.componentDescriptors.flatMap(_.componentName)) should not contain "textus-blog"
      }
    }

    "fail fast when requested component CAR is not available in a standard repository" in {
      Given("a requested component absent from the standard repository")
      val subsystem = new Subsystem(
        name = "test-standard-repo-fetch-missing",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { root =>
        val repository = ComponentRepository.StandardRepository.Specification(
          ComponentRepository.StandardRepositoryKind.Car,
          root.resolve("remote").toUri.toString.stripSuffix("/"),
          root.resolve("cache")
        ).build(
          ComponentCreate(
            subsystem,
            origin,
            Vector(ComponentDescriptor(name = Some("textus-user-account"), version = Some("0.1.1"), componentName = Some("textus-user-account")))
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
        conclusion.toRecord.show should include ("textus-user-account")
        conclusion.toRecord.show should include ("0.1.1")
      }
    }

    "not look up a requested main target CAR when component-dev-dir satisfies it" in {
      Given("a development directory satisfying a stale requested main target coordinate")
      val subsystem = new Subsystem(
        name = "test-standard-repo-dev-main-target",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      _with_temp_dir { root =>
        val componentdir = root.resolve("textus-knowledge-editor")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        Files.createDirectories(classdir)
        _write_runtime_classpath(componentdir, classdir, "TextusKnowledgeEditor", "0.1.0-SNAPSHOT", "TextusKnowledgeEditor")
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
          Vector(ComponentDescriptor(
            name = Some("TextusKnowledgeEditor"),
            version = Some("0.0.1"),
            componentName = Some("TextusKnowledgeEditor")
          ))
        )

        When("the repository space is discovered")
        val result = space.discover()

        Then("the development target prevents a standard CAR lookup")
        result should not be null
      }
    }

    "not look up a requested main target CAR when component-dev-dir infers it" in {
      Given("a development directory whose component identity can be inferred")
      val subsystem = new Subsystem(
        name = "test-standard-repo-dev-main-target-inferred",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      _with_temp_dir { root =>
        val componentdir = root.resolve("devdirsample")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        _copy_devdir_sample_classes(classdir)
        _write_runtime_classpath(componentdir, classdir, "devdirsample", "0.1.0-SNAPSHOT", "devdirsample")
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
            name = Some("devdirsample"),
            version = Some("0.0.1"),
            componentName = Some("devdirsample")
          ))
        )

        When("the repository space resolves a stale assembly coordinate")
        val components = space.discover()

        Then("the development implementation is activated exactly once without a CAR lookup")
        components.count(_.name == "devdirsample") shouldBe 1
        components.find(_.name == "devdirsample").map(_.getClass.getName) shouldBe
          Some(classOf[devdirsample.DevDirSamplePrimaryComponent].getName)
      }
    }

    "continue admitting unclaimed assembly dependencies as packaged CARs" in {
      Given("a claimed development target and an unclaimed dependency CAR without runtime evidence")
      val subsystem = TestComponentFactory.emptySubsystem("development-claim-dependency-admission")
      _with_temp_dir { root =>
        val componentdir = root.resolve("app")
        val cardir = componentdir.resolve("src").resolve("main").resolve("car")
        Files.createDirectories(cardir)
        Files.writeString(
          cardir.resolve("component-descriptor.json"),
          """{"name":"app","version":"0.2.0-SNAPSHOT","component":"app"}"""
        )
        val classdir = Files.createDirectories(componentdir.resolve("target").resolve("scala-3.3.8").resolve("classes"))
        _write_runtime_classpath(componentdir, classdir, "app", "0.2.0-SNAPSHOT", "app")
        val dependencydir = Files.createDirectories(root.resolve("dependencies"))
        val dependencyjar = _create_fake_component_jar(root.resolve("assets").resolve("dependency.jar"))
        val dependencydescriptor = root.resolve("dependency-descriptor.json")
        Files.writeString(
          dependencydescriptor,
          """{"name":"dependency","version":"0.1.0","component":"dependency"}"""
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
          ComponentDescriptor(name = Some("app"), version = Some("0.1.0"), componentName = Some("app")),
          ComponentDescriptor(name = Some("dependency"), version = Some("0.1.0"), componentName = Some("dependency"))
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

        Then("packaged CAR admission remains mandatory for the dependency")
        dependencydescriptors.flatMap(_.componentName) shouldBe Vector("dependency")
        thrown.getMessage should include ("CAR runtime manifest is missing")
      }
    }

    "resolve a subsystem descriptor from the standard SAR repository layout" in {
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
            |  - component: cwitter
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

    "keep compatibility with the legacy Maven-style standard repository layout" in {
      Given("a requested CAR stored in the legacy Maven-style layout")
      val subsystem = new Subsystem(
        name = "test-legacy-standard-repo",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { repositoryroot =>
        val artifactdir = repositoryroot.resolve("org").resolve("simplemodeling").resolve("car").resolve("textus-user-account").resolve("0.1.0-SNAPSHOT")
        Files.createDirectories(artifactdir)
        val fakecomponentjar = _create_fake_component_jar(repositoryroot.resolve("assets").resolve("component-main-legacy-standard.jar"))
        val descriptor = repositoryroot.resolve("component-descriptor-legacy-standard.json")
        Files.writeString(
          descriptor,
          """{"name":"textus-user-account","version":"0.1.0-SNAPSHOT","component":"textus-user-account"}"""
        )
        _create_car(
          artifactdir.resolve("textus-user-account-0.1.0-SNAPSHOT.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "component-descriptor.json" -> descriptor
          )
        )
        val repository = new ComponentRepository.ComponentDirRepository(
          repositoryroot,
          ComponentCreate(
            subsystem,
            origin,
            Vector(ComponentDescriptor(name = Some("textus-user-account"), version = Some("0.1.0-SNAPSHOT"), componentName = Some("textus-user-account")))
          ),
          ComponentRepository.resolvePackagePrefixes()
        )
        When("the component repository is discovered")
        val components = repository.discover()

        Then("the legacy component remains available")
        components.map(_.name) should contain ("spec")
        components.flatMap(_.artifactMetadata).flatMap(_.component) should contain ("textus-user-account")
      }
    }

    "skip an invalid sar containing incomplete descriptor" in {
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

    "reject a SAR file passed to the component archive descriptor loader" in {
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

    "reject a CAR file missing component descriptor version" in {
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
            conclusion.display should include ("component-descriptor")
            conclusion.display should include ("version")
          case Consequence.Success(value) =>
            fail(s"expected missing version failure but got ${value}")
        }
      }
    }

    }

    "component API and shared classloader boundaries" which {
    "parse scoped component dependency manifest" in {
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

    "extract CAR root lib jars as embedded component libraries" in {
      Given("an extracted CAR directory with a main JAR and root library JAR")
      _with_temp_dir { root =>
        val mainjar = _create_fake_component_jar(root.resolve("component").resolve("main.jar"))
        val depjar = _create_fake_component_jar(root.resolve("lib").resolve("dep.jar"))
        Files.writeString(
          root.resolve("component-descriptor.json"),
          """{"name":"sample-component","version":"0.1.0","component":"sample-component"}"""
        )

        When("the CAR directory is resolved")
        val extracted = CarExtractor.resolveDirectory(root).toOption.get

        Then("the root library is exposed as a component library")
        extracted.componentMain shouldBe mainjar
        extracted.componentLibs.map(_.getFileName.toString) should contain ("dep.jar")
        extracted.componentLibs should contain (depjar)
      }
    }

    "expose CAR component API jars separately from component implementation libraries" in {
      Given("an extracted CAR directory containing an API JAR")
      _with_temp_dir { root =>
        val mainjar = _create_fake_component_jar(root.resolve("component").resolve("main.jar"))
        val apijar = _create_fake_component_jar(root.resolve("spi").resolve("sample-api.jar"))
        Files.writeString(
          root.resolve("component-descriptor.json"),
          """{"name":"sample-component","version":"0.1.0","component":"sample-component"}"""
        )

        When("the CAR directory is resolved")
        val extracted = CarExtractor.resolveDirectory(root).toOption.get

        Then("the API JAR is separated from implementation libraries")
        extracted.componentMain shouldBe mainjar
        extracted.componentApiJars shouldBe Vector(apijar)
        extracted.componentLibs should not contain apijar
      }
    }

    "preflight generated component API metadata from a development directory" in {
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

    "reject a development API descriptor whose declared JAR is missing" in {
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

    "preserve assembly API validation failures through consequence-aware discovery" in {
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

    "preserve development admission failures through discovery-only assembly resolution" in {
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

    "normalize missing runtime evidence through component development repository validation" in {
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
            conclusion.display should include("sbt cozyPrepareRuntime")
          case Consequence.Success(_) =>
            fail("missing runtime evidence must not pass repository validation")
        }
      }
    }

    "share runtime ABI packages without sharing generated component implementations" in {
      Given("runtime ABI types and generated component implementation types")

      When("classloader parent-first policy is evaluated")
      val runtimeabi = ComponentLocalFirstClassLoader.isParentFirst("org.simplemodeling.model.Entity")

      Then("runtime ABI types are parent-first while component implementations stay local-first")
      runtimeabi shouldBe true
      ComponentLocalFirstClassLoader.isParentFirst("cats.Monad") shouldBe true
      ComponentLocalFirstClassLoader.isParentFirst("io.circe.Json") shouldBe true
      ComponentLocalFirstClassLoader.isParentFirst("org.simplemodeling.textus.useraccount.ComponentFactory") shouldBe false
    }

    "share generated component API contracts across CAR classloaders" in {
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

    "detect resolved shared dependency module conflicts" in {
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

    "parse coursier resolved modules deterministically" in {
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
    descriptorjson: Option[String] = None
  ): Unit = {
    val file = componentdir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")
    val cardir = componentdir.resolve("src").resolve("main").resolve("car")
    val descriptor = file.getParent.resolve("component-descriptor.json")
    Files.createDirectories(file.getParent)
    Files.createDirectories(cardir)
    Files.writeString(file, classdir.toString, StandardCharsets.UTF_8)
    Files.writeString(
      descriptor,
      descriptorjson.getOrElse(s"""{"name":"$name","version":"$version","component":"$component"}"""),
      StandardCharsets.UTF_8
    )
    val manifestschema = if (descriptorjson.isDefined) "cncf.car-development-runtime-manifest.v2" else "cncf.car-development-runtime-manifest.v1"
    val descriptoridentity = if (descriptorjson.isDefined) "target/cncf.d/component-descriptor.json" else "src/main/car/component-descriptor.json"
    if (descriptoridentity.startsWith("src/"))
      Files.writeString(cardir.resolve("component-descriptor.json"), Files.readString(descriptor, StandardCharsets.UTF_8), StandardCharsets.UTF_8)
    Files.writeString(
      cardir.resolve("abi-manifest.json"),
      s"""{"format":"cozy.car.abi-manifest.v1","car":{"name":"$name","version":"$version"},"abi":{"exports":{"components":[{"name":"$component"}]}}}""",
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
      s"""{"schemaVersion":"$manifestschema","sourceKind":"development-directory","car":{"name":"$name","version":"$version","component":"$component"},"runtime":{"cncf":{"minimum":"${CncfVersion.current}","excluded":[],"tested":["${CncfVersion.current}"]}},"evidence":$entries,"integrity":{"algorithm":"SHA-256","evidenceSha256":"$evidencedigest"}}""",
      StandardCharsets.UTF_8
    )
  }

  private def _sha256(path: Path): String = _sha256(Files.readAllBytes(path))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

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
