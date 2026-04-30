package org.goldenport.cncf.component.repository

import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters._
import scala.util.Using

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentDescriptorLoader, ComponentOrigin}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.{Configuration, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationTrace

/*
 * @since   Feb.  4, 2026
 *  version Apr. 25, 2026
 * @version May.  1, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentRepositoryCarSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  override def beforeAll(): Unit = {
    val workArea = WorkAreaSpace.create(RuntimeConfig.default)
    GlobalContext.set(GlobalContext(workArea))
  }

  "ComponentDirRepository" should {
    "parse explicit development and expanded CAR directory routes" in {
      val extracted = ComponentRepositorySpace.extractRepositoryArgs(
        ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        Array("--component-dev-dir", "/tmp/dev-component", "--component-car-dir=/tmp/debug.car.d", "command")
      )

      extracted.active shouldBe Right(Vector("component-dev-dir:/tmp/dev-component", "component-dir:/tmp/debug.car.d"))
      extracted.residual.toVector shouldBe Vector("command")
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

    "discover a requested component from the default standard repository layout" in {
      val subsystem = new Subsystem(
        name = "test-standard-repo",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { repositoryroot =>
        val artifactdir = repositoryroot.resolve("org").resolve("simplemodeling").resolve("car").resolve("textus-user-account").resolve("0.1.0-SNAPSHOT")
        Files.createDirectories(artifactdir)
        val fakecomponentjar = _create_fake_component_jar(repositoryroot.resolve("assets").resolve("component-main-standard.jar"))
        val descriptor = repositoryroot.resolve("component-descriptor-standard.json")
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
  }

  private def _create_car(
    target: Path,
    entries: Seq[(String, Path)]
  ): Unit =
    _create_zip(target, entries)

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
