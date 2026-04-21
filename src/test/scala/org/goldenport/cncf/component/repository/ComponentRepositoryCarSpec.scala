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
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.{Configuration, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationTrace

/*
 * @since   Feb.  4, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentRepositoryCarSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  override def beforeAll(): Unit = {
    val workArea = WorkAreaSpace.create(RuntimeConfig.default)
    GlobalContext.set(GlobalContext(workArea))
  }

  "ComponentDirRepository" should {
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
        val manifest = componentdir.resolve("manifest-car.json")
        Files.writeString(
          manifest,
          """{"name":"demo-component","version":"0.1.0","component":"spec","extension":{"driver":"car"},"config":{"log.level":"info"}}"""
        )
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "meta/manifest.json" -> manifest
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
          val manifest = componentdir.resolve("manifest-invalid-structure.json")
          Files.writeString(
            manifest,
            """{"name":"invalid-structure","version":"0.1.0","component":"spec"}"""
          )
          _create_car(carpath, Seq("component/lib/dummy.jar" -> dummyjar, "meta/manifest.json" -> manifest))
          val repository = new ComponentRepository.ComponentDirRepository(componentdir, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())
          val components = repository.discover()
          components shouldBe empty
        } finally {
          Files.deleteIfExists(dummyjar)
        }
      }
    }

    "skip an invalid car containing incomplete manifest" in {
      val subsystem = new Subsystem(
        name = "test-invalid-manifest",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main-invalid.jar"))
        val manifest = componentdir.resolve("manifest-invalid.json")
        Files.writeString(
          manifest,
          """{"version":"0.1.0","component":"spec"}"""
        )
        val carpath = componentdir.resolve("invalid-manifest.car")
        _create_car(
          carpath,
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "meta/manifest.json" -> manifest
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
        val carmanifest = componentdir.resolve("manifest-car-sar.json")
        Files.writeString(
          carmanifest,
          """{"name":"base-component","version":"1.0.0","component":"spec","extension":{"driver":"car-default","hash":"sha256"},"config":{"log.level":"info","feature":"base"}}"""
        )
        val embeddedcar = componentdir.resolve("embedded.car")
        _create_car(
          embeddedcar,
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "meta/manifest.json" -> carmanifest
          )
        )
        val sarmanifest = componentdir.resolve("manifest-sar.json")
        Files.writeString(
          sarmanifest,
          """{"name":"demo-subsystem","version":"2.0.0","subsystem":"demo","extension":{"driver":"sar-override"},"config":{"feature":"sar","endpoint":"https://example.invalid"}}"""
        )
        val sarpath = componentdir.resolve("demo.sar")
        _create_zip(
          sarpath,
          Seq(
            "meta/manifest.json" -> sarmanifest,
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

    "skip an invalid sar containing incomplete manifest" in {
      val subsystem = new Subsystem(
        name = "test-invalid-sar",
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
      )
      val origin = ComponentOrigin.Repository("component-dir")
      _with_temp_dir { componentdir =>
        val sarmanifest = componentdir.resolve("manifest-sar-invalid.json")
        Files.writeString(
          sarmanifest,
          """{"name":"invalid-sar","version":"1.0.0"}"""
        )
        val sarpath = componentdir.resolve("invalid.sar")
        _create_zip(
          sarpath,
          Seq(
            "meta/manifest.json" -> sarmanifest
          )
        )
        val repository = new ComponentRepository.ComponentDirRepository(componentdir, ComponentCreate(subsystem, origin), ComponentRepository.resolvePackagePrefixes())
        repository.discover() shouldBe empty
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
