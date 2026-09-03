package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.goldenport.cncf.component.testutil.CarArchiveFixture
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.GlobalContext
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class SubsystemDescriptorConfigurationPropagationSpec
  extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with GivenWhenThen {
  override def beforeAll(): Unit =
    GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))

  "GenericSubsystemDescriptor configuration propagation" should {
    "preserve structured descriptor values while external runtime configuration overrides matching keys" in {
      Given("a loaded descriptor with scalar, object, and list configuration values")
      val root = Files.createTempDirectory("subsystem-descriptor-configuration-propagation")
      try {
        val componentjar = _create_specification_component_jar(root.resolve("assets/component-main.jar"))
        val componentdescriptor = root.resolve("assets/component-descriptor.json")
        Files.writeString(
          componentdescriptor,
          _canonical_descriptor_json("org.goldenport.cncf.Specification", "0.1.0-SNAPSHOT"),
          StandardCharsets.UTF_8
        )
        CarArchiveFixture.write(
          root.resolve("specification.car"),
          Seq(
            "component/main.jar" -> componentjar,
            "component-descriptor.json" -> componentdescriptor
          )
        )
        val descriptorpath = root.resolve("subsystem-descriptor.yaml")
        Files.writeString(
          descriptorpath,
          """subsystem: descriptor-configuration-propagation
            |version: 0.1.0-SNAPSHOT
            |components:
            |  - namespace: org.goldenport.cncf
            |    id: Specification
            |    version: 0.1.0-SNAPSHOT
            |config:
            |  descriptor.scalar: retained
            |  descriptor.structured:
            |    nested:
            |      mode: descriptor
            |    options:
            |      - first
            |      - enabled: true
            |  descriptor.items:
            |    - alpha
            |    - beta
            |  descriptor.override: descriptor
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        val descriptor = GenericSubsystemDescriptor.load(descriptorpath).toOption
          .getOrElse(fail("descriptor did not load"))
        val supplied = ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.repositoryDirKey -> ConfigurationValue.StringValue(s"component-dir:$root"),
            "descriptor.override" -> ConfigurationValue.StringValue("runtime")
          )),
          ConfigurationTrace.empty
        )

        When("GenericSubsystemFactory constructs the subsystem through its descriptor path")
        val subsystem = GenericSubsystemFactory.default(descriptor, configuration = supplied)

        Then("the scalar view remains compatible and the production subsystem configuration retains typed values")
        descriptor.config should contain allOf (
          "descriptor.scalar" -> "retained",
          "descriptor.override" -> "descriptor"
        )
        descriptor.config should not contain "descriptor.structured"
        descriptor.configuration.values("descriptor.structured") shouldBe _structured_value
        descriptor.configuration.values("descriptor.items") shouldBe ConfigurationValue.ListValue(List(
          ConfigurationValue.StringValue("alpha"),
          ConfigurationValue.StringValue("beta")
        ))
        subsystem.configuration.configuration.values("descriptor.structured") shouldBe _structured_value
        subsystem.configuration.configuration.values("descriptor.items") shouldBe ConfigurationValue.ListValue(List(
          ConfigurationValue.StringValue("alpha"),
          ConfigurationValue.StringValue("beta")
        ))
        subsystem.configuration.configuration.values("descriptor.override") shouldBe
          ConfigurationValue.StringValue("runtime")
      } finally {
        _delete(root)
      }
    }
  }

  private val _structured_value = ConfigurationValue.ObjectValue(Map(
    "nested" -> ConfigurationValue.ObjectValue(Map(
      "mode" -> ConfigurationValue.StringValue("descriptor")
    )),
    "options" -> ConfigurationValue.ListValue(List(
      ConfigurationValue.StringValue("first"),
      ConfigurationValue.ObjectValue(Map(
        "enabled" -> ConfigurationValue.BooleanValue(true)
      ))
    ))
  ))

  private def _create_specification_component_jar(target: Path): Path = {
    Option(target.getParent).foreach(Files.createDirectories(_))
    Using.resource(new ZipOutputStream(Files.newOutputStream(target))) { zip =>
      zip.putNextEntry(new ZipEntry(
        "org/goldenport/cncf/component/builtin/specification/SpecificationComponent$Factory.class"
      ))
      zip.closeEntry()
    }
    target
  }

  private def _canonical_descriptor_json(componentid: String, release: String): String = {
    val segments = componentid.split("\\.").toVector
    val namespace = segments.dropRight(1).mkString(".")
    val localid = segments.last
    s"""{"schemaVersion":3,"component":{"namespace":"$namespace","id":"$localid","version":"$release"}}"""
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path))
      Using.resource(Files.walk(path)) { stream =>
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists)
      }
}
