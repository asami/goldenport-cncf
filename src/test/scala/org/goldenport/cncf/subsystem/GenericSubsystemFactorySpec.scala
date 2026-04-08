package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters._
import scala.util.Using

import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  8, 2026
 * @version Apr.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class GenericSubsystemFactorySpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  override def beforeAll(): Unit = {
    val workArea = WorkAreaSpace.create(RuntimeConfig.default)
    GlobalContext.set(GlobalContext(workArea))
  }

  "GenericSubsystemFactory" should {
    "load the descriptor-bound component through the repository runtime path" in {
      _with_temp_dir { componentdir =>
        val fakecomponentjar = _create_fake_component_jar(componentdir.resolve("assets").resolve("component-main.jar"))
        val manifest = componentdir.resolve("manifest-car.json")
        Files.writeString(
          manifest,
          """{"name":"structured-knowledge","version":"0.1.0","component":"textus-mcp-rag"}"""
        )
        _create_car(
          componentdir.resolve("structured-knowledge.car"),
          Seq(
            "component/main.jar" -> fakecomponentjar,
            "meta/manifest.json" -> manifest
          )
        )

        val descriptorPath = Files.createTempFile("generic-subsystem-factory", ".yaml")
        Files.writeString(
          descriptorPath,
          """subsystem: mcprag
            |version: 0.1.0-SNAPSHOT
            |components:
            |  - component: textus-mcp-rag
            |    coordinate: org.textus:textus-mcp-rag:0.1.0-SNAPSHOT
            |    extension_bindings:
            |      knowledge_source_adapters:
            |        - key: view
            |""".stripMargin,
          StandardCharsets.UTF_8
        )

        val descriptor = GenericSubsystemDescriptor.load(descriptorPath).toOption.get
        val configuration = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.ComponentRepositoryKey ->
              ConfigurationValue.StringValue(s"component-dir:${componentdir.toString}")
          )),
          ConfigurationTrace.empty
        )

        val subsystem = GenericSubsystemFactory.default(descriptor, configuration = configuration)
        val names = subsystem.components.map(_.name).sorted
        val metadata = subsystem.components.flatMap(_.artifactMetadata)

        subsystem.name shouldBe "mcprag"
        subsystem.version shouldBe Some("0.1.0-SNAPSHOT")
        names shouldBe Vector("spec")
        metadata.map(_.component) should contain (Some("textus-mcp-rag"))
      }
    }

    "carry descriptor-defined security wiring from the textus-identity journal sample descriptor" in {
      val descriptorPath = java.nio.file.Path.of("/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/2026-04-09-subsystem-descriptor-textus-identity.yaml")
      val descriptor = GenericSubsystemDescriptor.load(descriptorPath).toOption.get
      val subsystem = GenericSubsystemFactory.default(descriptor)

      val wiring = subsystem.resolvedSecurityWiring.authentication

      subsystem.name shouldBe "textus-identity"
      wiring.conventionEnabled shouldBe true
      wiring.fallbackPrivilegeEnabled shouldBe false
      wiring.providers.map(x => (x.componentName, x.name, x.source.toString)) shouldBe Vector(
        ("UserAccount", "user-account", "Descriptor")
      )
      wiring.providers.head.kind shouldBe Some("human")
      wiring.providers.head.priority shouldBe 100
      wiring.providers.head.schemes shouldBe Vector("bearer", "refresh-token")
      wiring.providers.head.provider shouldBe empty
    }

  }

  private def _create_car(
    target: Path,
    entries: Seq[(String, Path)]
  ): Unit =
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zos =>
      entries.foreach { case (name, file) =>
        zos.putNextEntry(new ZipEntry(name))
        Files.copy(file, zos)
        zos.closeEntry()
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
    val base = Files.createTempDirectory("generic-subsystem-factory-spec")
    try body(base)
    finally {
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
}
