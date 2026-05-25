package org.goldenport.cncf.component.repository

import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters._
import scala.util.Using

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import org.goldenport.{Consequence, ConsequenceException}
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.component.{CarExtractor, ComponentCreate, ComponentDependencyManifest, ComponentDependencyPool, ComponentDescriptor, ComponentDescriptorLoader, ComponentLocalFirstClassLoader, ComponentOrigin, CoursierComponentDependencyResolver}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.configuration.{Configuration, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationTrace

/*
 * @since   Feb.  4, 2026
 *  version Apr. 25, 2026
 * @version May. 25, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentRepositoryCarSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  override def beforeAll(): Unit = {
    val workArea = WorkAreaSpace.create(RuntimeConfig.default)
    GlobalContext.set(GlobalContext(workArea))
  }

  "ComponentDirRepository" should {
    "expose SimpleModeling CAR and SAR standard repository URLs" in {
      ComponentRepository.standardComponentRepositoryUrl() shouldBe
        "https://www.simplemodeling.org/repository/car"
      ComponentRepository.standardSubsystemRepositoryUrl() shouldBe
        "https://www.simplemodeling.org/repository/sar"
    }

    "parse SimpleModeling CAR and SAR URLs as standard search repository specs" in {
      _with_temp_dir { root =>
        ComponentRepository.parseSpecs(
          ComponentRepository.standardComponentRepositoryUrl(),
          root
        ).toOption.get shouldBe Vector(ComponentRepository.standardComponentRepositorySpec())
        ComponentRepository.parseSpecs(
          ComponentRepository.standardSubsystemRepositoryUrl(),
          root
        ).toOption.get shouldBe Vector(ComponentRepository.standardSubsystemRepositorySpec())
      }
    }

    "append SimpleModeling standard URL repositories to the default search set" in {
      _with_temp_dir { cwd =>
        val resolved = ComponentRepositorySpace.appendDefaultSearchRepositories(
          Right(Vector.empty),
          active = Vector.empty,
          cwd,
          noDefault = false
        ).toOption.get

        resolved should contain (ComponentRepository.standardComponentRepositorySpec())
        resolved should contain (ComponentRepository.standardSubsystemRepositorySpec())
      }
    }

    "use cache as the default standard repository root" in {
      val root = ComponentRepository.defaultStandardRepositoryDir()

      root.toString should include (".cncf")
      root.toString should include ("cache")
    }

    "append existing local CNCF repository dirs before standard repositories" in {
      _with_temp_dir { home =>
        val oldhome = System.getProperty("user.home")
        try {
          System.setProperty("user.home", home.toString)
          val car = ComponentRepository.defaultLocalComponentRepositoryDir()
          Files.createDirectories(car)

          val resolved = ComponentRepositorySpace.appendDefaultSearchRepositories(
            Right(Vector.empty),
            active = Vector.empty,
            home,
            noDefault = false
          ).toOption.get

          resolved should contain (ComponentRepository.ComponentDirRepository.Specification(car))
          assert(
            resolved.indexOf(ComponentRepository.ComponentDirRepository.Specification(car)) <
              resolved.indexOf(ComponentRepository.standardComponentRepositorySpec())
          )
        } finally {
          if (oldhome == null) System.clearProperty("user.home")
          else System.setProperty("user.home", oldhome)
        }
      }
    }

    "not append cache as a plain component directory" in {
      _with_temp_dir { home =>
        val oldhome = System.getProperty("user.home")
        try {
          System.setProperty("user.home", home.toString)
          val cache = ComponentRepository.defaultStandardRepositoryDir()
          Files.createDirectories(cache)

          val resolved = ComponentRepositorySpace.appendDefaultSearchRepositories(
            Right(Vector.empty),
            active = Vector.empty,
            home,
            noDefault = false
          ).toOption.get

          resolved should not contain ComponentRepository.ComponentDirRepository.Specification(cache)
          resolved should contain (ComponentRepository.standardComponentRepositorySpec())
        } finally {
          if (oldhome == null) System.clearProperty("user.home")
          else System.setProperty("user.home", oldhome)
        }
      }
    }

    "does not resolve snapshots from the standard cache repository" in {
      _with_temp_dir { cache =>
        val snapshotdir = cache.resolve("car").resolve("sample-component").resolve("0.1.1-SNAPSHOT")
        Files.createDirectories(snapshotdir)
        Files.writeString(snapshotdir.resolve("sample-component-0.1.1-SNAPSHOT.car"), "")
        val spec = ComponentRepository.StandardRepository.Specification(
          ComponentRepository.StandardRepositoryKind.Car,
          "https://example.invalid/repository/car",
          cache
        )

        spec.resolveComponentArchivePath("sample-component", Some("0.1.1-SNAPSHOT")) shouldBe None
      }
    }

    "does not discover requested snapshots from the standard cache repository" in {
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

        repository.discover().flatMap(_.componentDescriptors.flatMap(_.componentName)) should not contain "sample-component"
      }
    }

    "does not let standard repository block a requested local snapshot component" in {
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

        val components = space.discover()

        components.flatMap(_.componentDescriptors.flatMap(_.componentName)) should contain ("sample-component")
      }
    }

    "resolve top-level component invocation by explicit component version" in {
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

        val resolved = org.goldenport.cncf.cli.CncfRuntime.resolveComponentInvocation(
          invocation,
          Vector(ComponentRepository.StandardRepository.Specification(
            ComponentRepository.StandardRepositoryKind.Car,
            ComponentRepository.standardComponentRepositoryUrl(),
            repositoryroot
          ))
        )

        resolved.actualArgs.toVector should contain (s"--${RuntimeConfig.ComponentFileKey}=${olddir.resolve("blog-component-0.0.1.car")}")
      }
    }

    "parse explicit development and expanded CAR directory routes" in {
      val extracted = ComponentRepositorySpace.extractRepositoryArgs(
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        Array("--component-dev-dir", "/tmp/dev-component", "--component-car-dir=/tmp/debug.car.d", "command")
      )

      extracted.active shouldBe Right(Vector("component-dev-dir:/tmp/dev-component", "component-dir:/tmp/debug.car.d"))
      extracted.residual.toVector shouldBe Vector("command")
    }

    "reject explicit component development directory without runtime classpath" in {
      _with_temp_dir { root =>
        val componentdir = root.resolve("component")
        Files.createDirectories(componentdir.resolve("target"))

        val parsed = ComponentRepository.parseSpecs(s"component-dev-dir:${componentdir}", root)

        parsed.left.toOption.get should include ("runtime classpath file is missing or empty")
        parsed.left.toOption.get should include (componentdir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt").toString)
        parsed.left.toOption.get should include ("will not fall back to a packaged CAR")
      }
    }

    "fail repository space creation when configured component development directory is invalid" in {
      _with_temp_dir { root =>
        val componentdir = root.resolve("component")
        Files.createDirectories(componentdir)
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.ComponentDevDirKey -> ConfigurationValue.StringValue(componentdir.toString)
          )),
          ConfigurationTrace.empty
        )

        val thrown = intercept[IllegalStateException] {
          ComponentRepositorySpace.create(TestComponentFactory.emptySubsystem("dev-dir-fail-fast"), root, configuration)
        }

        thrown.getMessage should include ("runtime classpath file is missing or empty")
        thrown.getMessage should include ("will not fall back to a packaged CAR")
      }
    }

    "reject CAR schemes in component development directory configuration" in {
      _with_temp_dir { root =>
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.RepositoryComponentDevDirKey -> ConfigurationValue.StringValue("component-dir:/tmp/packaged-cars")
          )),
          ConfigurationTrace.empty
        )

        val extracted = ComponentRepositorySpace.extractRepositoryArgs(configuration, Array("server"))
        val parsed = ComponentRepositorySpace.resolveSpecifications(extracted.search, root, noDefault = true)

        parsed.left.toOption.get should include ("component development directory configuration must be a plain path or component-dev-dir:path")
      }
    }

    "treat repository component development directories as direct dev-dir repositories" in {
      _with_temp_dir { root =>
        val componentdir = root.resolve("textus-user-account")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        Files.createDirectories(classdir)
        _write_runtime_classpath(componentdir, classdir)
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.RepositoryComponentDevDirKey -> ConfigurationValue.StringValue(componentdir.toString)
          )),
          ConfigurationTrace.empty
        )

        val extracted = ComponentRepositorySpace.extractRepositoryArgs(configuration, Array("server"))
        val specs = ComponentRepositorySpace.resolveSpecifications(extracted.search, root, noDefault = true).toOption.get

        specs shouldBe Vector(ComponentRepository.ComponentDevDirRepository.Specification(componentdir))
      }
    }

    "parse explicit expanded SAR directory routes" in {
      val extracted = ComponentRepositorySpace.extractRepositoryArgs(
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        Array("--subsystem-dev-dir", "/tmp/app", "--subsystem-sar-dir", "/tmp/debug.sar.d", "--textus.subsystem.sar.dir=/tmp/configured.sar.d", "command")
      )

      extracted.active shouldBe Right(Vector("subsystem-dev-dir:/tmp/app", "component-dir:/tmp/debug.sar.d", "component-dir:/tmp/configured.sar.d"))
      extracted.residual.toVector shouldBe Vector("command")
    }

    "activate subsystem development directory from runtime config alias" in {
      val configuration = ResolvedConfiguration(
        Configuration(Map(
          "textus.runtime.subsystem.dev.dir" -> ConfigurationValue.StringValue("/tmp/app")
        )),
        ConfigurationTrace.empty
      )

      val extracted = ComponentRepositorySpace.extractRepositoryArgs(configuration, Array("command"))

      extracted.active shouldBe Right(Vector("subsystem-dev-dir:/tmp/app"))
      extracted.residual.toVector shouldBe Vector("command")
    }

    "resolve subsystem descriptor from a subsystem development directory" in {
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

        spec.resolveSubsystemDescriptor("cwitter").map(_.subsystemName) shouldBe Some("cwitter")
      }
    }

    "infer subsystem name from explicit subsystem development directory" in {
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
        val descriptor = org.goldenport.cncf.subsystem.GenericSubsystemFactory.resolveDescriptor(bootstrap.configuration)

        descriptor.map(_.subsystemName) shouldBe Some("cwitter")
      }
    }

    "infer subsystem name from explicit component CAR directory" in {
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
        val descriptor = org.goldenport.cncf.subsystem.GenericSubsystemFactory.resolveDescriptor(bootstrap.configuration)

        descriptor.map(_.subsystemName) shouldBe Some("cwitter")
        descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("cwitter")
      }
    }

    "infer subsystem name from explicit component development directory" in {
      _with_temp_dir { root =>
        val componentdir = root.resolve("component")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        Files.createDirectories(classdir)
        _write_runtime_classpath(componentdir, classdir)
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
        val descriptor = org.goldenport.cncf.subsystem.GenericSubsystemFactory.resolveDescriptor(bootstrap.configuration)

        descriptor.map(_.subsystemName) shouldBe Some("cwitter")
        descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("cwitter")
      }
    }

    "do not auto-append a packaged CAR when component development directory is explicit" in {
      _with_temp_dir { root =>
        val componentdir = root.resolve("component")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        Files.createDirectories(classdir)
        _write_runtime_classpath(componentdir, classdir)
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
        val descriptor = org.goldenport.cncf.subsystem.GenericSubsystemFactory.resolveDescriptor(bootstrap.configuration)

        RuntimeConfig.getString(bootstrap.configuration, RuntimeConfig.ComponentFileKey) shouldBe empty
        descriptor.map(_.subsystemName) shouldBe Some("dev-cwitter")
        descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("dev-cwitter")
      }
    }

    "infer component development directory identity from discovered component metadata" in {
      _with_temp_dir { root =>
        val componentdir = root.resolve("01-minimal")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        _copy_devdir_sample_classes(classdir)
        _write_runtime_classpath(componentdir, classdir)
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.ComponentDevDirKey -> ConfigurationValue.StringValue(componentdir.toString)
          )),
          ConfigurationTrace.empty
        )

        val inferred = ComponentRepository.ComponentDevDirRepository.inferComponentNames(componentdir)
        val descriptor = org.goldenport.cncf.subsystem.GenericSubsystemFactory.resolveDescriptor(configuration)
        val initialized = new org.goldenport.cncf.cli.CncfRuntime().initializeForEmbedding(
          cwd = root,
          args = Array(
            "--component-dev-dir", componentdir.toString,
            "command", "devdirsample.main.hello"
          ),
          modeHint = Some(org.goldenport.cncf.cli.RunMode.Command)
        ).TAKE

        inferred shouldBe Vector("devdirsample")
        descriptor.map(_.subsystemName) shouldBe Some("devdirsample")
        descriptor.toVector.flatMap(_.componentBindings.map(_.componentName)) shouldBe Vector("devdirsample")
        initialized.components.map(_.name) should contain ("devdirsample")
      }
    }

    "treat runtime subsystem source aliases as explicit activation during name resolution" in {
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

        val resolved = org.goldenport.cncf.cli.CncfRuntime.resolveComponentInvocation(
          invocation,
          Vector(ComponentRepository.ComponentDirRepository.Specification(componentdir))
        )

        resolved.actualArgs.toVector shouldBe invocation.actualArgs.toVector
      }
    }

    "treat expanded SAR directory route as explicit activation during name resolution" in {
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

        val resolved = org.goldenport.cncf.cli.CncfRuntime.resolveComponentInvocation(
          invocation,
          Vector(ComponentRepository.ComponentDirRepository.Specification(componentdir))
        )

        resolved.actualArgs.toVector shouldBe invocation.actualArgs.toVector
      }
    }

    "not auto-activate cwd sar.d as a default active repository" in {
      _with_temp_dir { cwd =>
        Files.createDirectories(cwd.resolve("sar.d"))

        val resolved = ComponentRepositorySpace.appendDefaultActiveRepositories(
          Right(Vector.empty),
          cwd,
          noDefault = false
        ).toOption.get

        resolved should not contain ComponentRepository.ComponentDirRepository.Specification(cwd.resolve("sar.d").normalize)
      }
    }

    "not append default component target when an explicit component development directory is active" in {
      _with_temp_dir { cwd =>
        val devdir = cwd.resolve("component")
        Files.createDirectories(devdir)
        Files.createDirectories(cwd.resolve("component").resolve("target"))

        val resolved = ComponentRepositorySpace.appendDefaultActiveRepositories(
          Right(Vector(ComponentRepository.ComponentDevDirRepository.Specification(devdir))),
          cwd,
          noDefault = false
        ).toOption.get

        resolved shouldBe Vector(ComponentRepository.ComponentDevDirRepository.Specification(devdir))
      }
    }

    "treat equals-form expanded CAR route as an explicit activation during name resolution" in {
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

        val resolved = org.goldenport.cncf.cli.CncfRuntime.resolveComponentInvocation(
          invocation,
          Vector(ComponentRepository.ComponentDirRepository.Specification(componentdir))
        )

        resolved.actualArgs.toVector shouldBe invocation.actualArgs.toVector
      }
    }

    "resolve descriptor by componentlet name from a car directory" in {
      _with_temp_dir { componentdir =>
        _create_fake_component_jar(componentdir.resolve("component").resolve("main.jar"))
        val descriptorPath = componentdir.resolve("component-descriptor.json")
        Files.writeString(
          descriptorPath,
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

        val descriptor = ComponentRepository.resolveComponentDescriptorFromComponentDir(componentdir, "public-notice")

        descriptor.map(_.componentName) shouldBe Some(Some("sample-component"))
        descriptor.toVector.flatMap(_.componentlets.map(_.name)) shouldBe Vector("public-notice", "notice-admin")
      }
    }

    "discover the component wrapped in a car" in {
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
        val components = repository.discover()
        components should not be empty
        val head = components.head
        head.origin.label should include("component-dir:car:demo-component:0.1.0")
        head.artifactMetadata.map(_.name) shouldBe Some("demo-component")
        head.artifactMetadata.map(_.version) shouldBe Some("0.1.0")
        head.artifactMetadata.map(_.effectiveExtensions.get("driver").getOrElse("")) shouldBe Some("car")
      }
    }

    "treat component-file CAR as one component and ignore embedded component.d contents" in {
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
        val components = repository.discover()
        val componentNames = components.flatMap(_.artifactMetadata).flatMap(_.component).toSet

        componentNames should contain ("app")
        componentNames should not contain ("textus-user-account")
      }
    }

    "skip an invalid car containing zero component jars" in {
      val subsystem = new Subsystem(
        name = "test-skip",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val dummyjar = Files.createTempFile("dummy", ".jar")
        try {
          val carpath = componentdir.resolve("invalid.car")
          val descriptor = componentdir.resolve("component-descriptor-invalid-structure.json")
          Files.writeString(
            descriptor,
            """{"name":"invalid-structure","version":"0.1.0","component":"spec"}"""
          )
          _create_car(carpath, Seq("component/lib/dummy.jar" -> dummyjar, "component-descriptor.json" -> descriptor))
          val repository = new ComponentRepository.ComponentDirRepository(componentdir, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())
          val components = repository.discover()
          components shouldBe empty
        } finally {
          Files.deleteIfExists(dummyjar)
        }
      }
    }

    "skip an invalid car containing incomplete descriptor" in {
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
        repository.discover() shouldBe empty
      }
    }

    "apply SAR > CAR precedence for extension/config when loading sar containing car" in {
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
        val components = repository.discover()
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
        val components = repository.discover()

        components.map(_.name) should contain ("spec")
        components.flatMap(_.artifactMetadata).flatMap(_.component) should contain ("textus-user-account")
      }
    }

    "fetch requested component CARs from a standard repository before discovery" in {
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

        val components = repository.discover()

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

        val thrown = the [ConsequenceException] thrownBy repository.discover()
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
      val subsystem = new Subsystem(
        name = "test-standard-repo-dev-main-target",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      _with_temp_dir { root =>
        val componentdir = root.resolve("textus-knowledge-editor")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        Files.createDirectories(classdir)
        _write_runtime_classpath(componentdir, classdir)
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
            version = Some("0.1.0-SNAPSHOT"),
            componentName = Some("TextusKnowledgeEditor")
          ))
        )

        noException should be thrownBy space.discover()
      }
    }

    "not look up a requested main target CAR when component-dev-dir infers it" in {
      val subsystem = new Subsystem(
        name = "test-standard-repo-dev-main-target-inferred",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      _with_temp_dir { root =>
        val componentdir = root.resolve("devdirsample")
        val classdir = componentdir.resolve("target").resolve("scala-3.3.7").resolve("classes")
        _copy_devdir_sample_classes(classdir)
        _write_runtime_classpath(componentdir, classdir)
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
            version = Some("0.1.0-SNAPSHOT"),
            componentName = Some("devdirsample")
          ))
        )

        noException should be thrownBy space.discover()
      }
    }

    "resolve a subsystem descriptor from the standard SAR repository layout" in {
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

        val loaded = ComponentRepository.resolveSubsystemDescriptor(
          Vector(ComponentRepository.StandardRepository.Specification(
            ComponentRepository.StandardRepositoryKind.Sar,
            ComponentRepository.standardSubsystemRepositoryUrl(),
            repositoryroot
          )),
          "cwitter"
        )

        loaded.map(_.subsystemName) shouldBe Some("cwitter")
        loaded.flatMap(_.version) shouldBe Some("0.1.0")
      }
    }

    "keep compatibility with the legacy Maven-style standard repository layout" in {
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
        val components = repository.discover()

        components.map(_.name) should contain ("spec")
        components.flatMap(_.artifactMetadata).flatMap(_.component) should contain ("textus-user-account")
      }
    }

    "skip an invalid sar containing incomplete descriptor" in {
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
        repository.discover() shouldBe empty
      }
    }

    "reject a SAR file passed to the component archive descriptor loader" in {
      _with_temp_dir { componentdir =>
        val descriptorPath = componentdir.resolve("component-descriptor.json")
        Files.writeString(descriptorPath, """{"component":{"name":"wrong-kind"},"version":"0.1.0"}""")
        val sarpath = componentdir.resolve("wrong-kind.sar")
        _create_zip(
          sarpath,
          Seq(
            "component-descriptor.json" -> descriptorPath
          )
        )

        ComponentDescriptorLoader.loadArchive(sarpath).toOption shouldBe empty
      }
    }

    "parse scoped component dependency manifest" in {
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

      manifest.provided shouldBe Vector("org.goldenport:goldenport-cncf_3:0.4.8-SNAPSHOT")
      manifest.shared shouldBe Vector("org.postgresql:postgresql:42.7.3")
      manifest.local shouldBe Vector("com.example:legacy-driver:1.2.0")
      manifest.repositories shouldBe Vector("maven-central", "https://www.simplemodeling.org/repository/maven")
    }

    "extract CAR root lib jars as embedded component libraries" in {
      _with_temp_dir { root =>
        val mainjar = _create_fake_component_jar(root.resolve("component").resolve("main.jar"))
        val depjar = _create_fake_component_jar(root.resolve("lib").resolve("dep.jar"))
        Files.writeString(
          root.resolve("component-descriptor.json"),
          """{"name":"sample-component","version":"0.1.0","component":"sample-component"}"""
        )

        val extracted = CarExtractor.resolveDirectory(root).toOption.get

        extracted.componentMain shouldBe mainjar
        extracted.componentLibs.map(_.getFileName.toString) should contain ("dep.jar")
        extracted.componentLibs should contain (depjar)
      }
    }

    "keep generated component packages outside parent-first runtime ABI packages" in {
      ComponentLocalFirstClassLoader.isParentFirst("org.simplemodeling.model.Entity") shouldBe true
      ComponentLocalFirstClassLoader.isParentFirst("org.simplemodeling.textus.useraccount.ComponentFactory") shouldBe false
    }

    "detect resolved shared dependency module conflicts" in {
      val modules = Vector(
        CoursierComponentDependencyResolver.ResolvedModule("com.example", "driver", "1.0.0"),
        CoursierComponentDependencyResolver.ResolvedModule("com.example", "driver", "2.0.0"),
        CoursierComponentDependencyResolver.ResolvedModule("com.example", "other", "1.0.0")
      )

      ComponentDependencyPool.moduleConflicts(modules) shouldBe Vector("com.example:driver resolved=1.0.0,2.0.0")
    }

    "parse coursier resolved modules deterministically" in {
      val modules = CoursierComponentDependencyResolver.parseResolvedModules(
        """com.example:driver:1.0.0
          |com.example:other:2.0.0:default
          |
          |# ignored
          |""".stripMargin
      )

      modules shouldBe Vector(
        CoursierComponentDependencyResolver.ResolvedModule("com.example", "driver", "1.0.0"),
        CoursierComponentDependencyResolver.ResolvedModule("com.example", "other", "2.0.0")
      )
    }
  }

  private def _create_car(
    target: Path,
    entries: Seq[(String, Path)]
  ): Unit =
    _create_zip(target, entries)

  private def _write_runtime_classpath(
    componentdir: Path,
    classdir: Path
  ): Unit = {
    val file = componentdir.resolve("target").resolve("cncf.d").resolve("runtime-classpath.txt")
    Files.createDirectories(file.getParent)
    Files.writeString(file, classdir.toString)
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
    val factoryClassEntry =
      "org/goldenport/cncf/component/builtin/specification/SpecificationComponent$Factory.class"
    Option(target.getParent).foreach(Files.createDirectories(_))
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zos =>
      zos.putNextEntry(new ZipEntry(factoryClassEntry))
      zos.closeEntry()
    }
    target
  }

  private def _copy_devdir_sample_classes(target: Path): Unit = {
    val source = Path.of("target", "scala-3.3.7", "test-classes", "devdirsample")
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
    val base = Files.createTempDirectory("component-repo-spec")
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
