package org.goldenport.cncf.subsystem

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator

import scala.util.Using

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentDescriptor, ComponentId}
import org.goldenport.cncf.config.SubsystemInstanceId
import org.goldenport.cncf.component.testutil.CarArchiveFixture
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 30, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase53StableSubsystemIdentitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Phase 53 stable Subsystem identity" should {
    "prefer descriptor-owned identities in strict implicit Subsystem projection" in {
      Given("canonical component descriptors with each accepted descriptor-owned identity source")
      val path = Path.of("target", "test-tmp", "phase-53", "path-derived.car")
      val componentid = ComponentId("org.goldenport.fixture.ComponentName")
      val declared = ComponentDescriptor(
        name = Some(componentid.name),
        componentName = Some(componentid.name),
        subsystemName = Some("declared-subsystem"),
        version = Some("0.1.0"),
        schemaVersion = Some(3),
        componentId = Some(componentid)
      )
      val component = ComponentDescriptor(
        name = Some(componentid.name),
        componentName = Some(componentid.name),
        version = Some("0.1.0"),
        schemaVersion = Some(3),
        componentId = Some(componentid)
      )
      val name = ComponentDescriptor(
        name = Some(componentid.name),
        version = Some("0.1.0"),
        schemaVersion = Some(3),
        componentId = Some(componentid)
      )
      val conflicting = ComponentDescriptor(
        name = Some("display-like-name"),
        componentName = Some("component-name"),
        version = Some("0.1.0"),
        schemaVersion = Some(3),
        componentId = Some(componentid)
      )

      When("the Component descriptor projection creates implicit Subsystem descriptors")
      val declaredresult = GenericSubsystemDescriptor.fromComponentDescriptor(path, declared).toOption.get
      val componentresult = GenericSubsystemDescriptor.fromComponentDescriptor(path, component).toOption.get
      val nameresult = GenericSubsystemDescriptor.fromComponentDescriptor(path, name).toOption.get
      val conflictingresult = GenericSubsystemDescriptor.fromComponentDescriptor(path, conflicting)

      Then("the descriptor controls the stable identity")
      declaredresult.subsystemName shouldBe "declared-subsystem"
      componentresult.subsystemName shouldBe "ComponentName"
      nameresult.subsystemName shouldBe "ComponentName"
      conflictingresult shouldBe a[Consequence.Failure[_]]
    }

    "project a label-safe Subsystem identity from a canonical qualified Component ID" in {
      Given("a canonical Component descriptor for org.simplemodeling.textus.ArtScene")
      val componentid = ComponentId("org.simplemodeling.textus.ArtScene")
      val descriptor = ComponentDescriptor(
        name = Some(componentid.name),
        componentName = Some(componentid.name),
        version = Some("0.1.0"),
        schemaVersion = Some(3),
        componentId = Some(componentid)
      )
      val canonicalpath = Path.of("target", "test-tmp", "phase-53", "canonical-artscene.car")

      When("implicit Subsystem projection derives and validates the default identity")
      val result = GenericSubsystemDescriptor.fromComponentDescriptor(canonicalpath, descriptor).toOption.get
      val identity = SubsystemInstanceId.default(result.subsystemName)

      Then("the local Component ID is used as a valid Subsystem identity")
      result.subsystemName shouldBe "ArtScene"
      identity shouldBe a[Consequence.Success[_]]
    }

    "prefer an adjacent assembly Subsystem identity over every Component descriptor identity" in {
      Given("a component CAR whose adjacent assembly and Component descriptor identities differ")
      _with_work_directory { directory =>
        val componentdescriptor = directory.resolve("component-descriptor.json")
        val assemblydescriptor = directory.resolve("assembly-descriptor.yaml")
        Files.writeString(
          componentdescriptor,
          """{"schemaVersion":3,"component":{"namespace":"org.goldenport.fixture","id":"ComponentName","version":"0.1.0"},"subsystemName":"component-subsystem"}""",
          StandardCharsets.UTF_8
        )
        Files.writeString(
          assemblydescriptor,
          """subsystem: assembly-subsystem
            |version: 0.1.0
            |components:
            |  - namespace: org.goldenport.fixture
            |    id: ComponentName
            |    version: 0.1.0
            |""".stripMargin,
          StandardCharsets.UTF_8
        )
        val car = directory.resolve("unrelated-path-name.car")
        CarArchiveFixture.write(
          car,
          Seq(
            "component-descriptor.json" -> componentdescriptor,
            "assembly-descriptor.yaml" -> assemblydescriptor
          )
        )

        When("the direct packaged Component route projects its implicit Subsystem")
        val result = GenericSubsystemDescriptor.loadComponentArchive(car).toOption.get

        Then("the adjacent assembly declaration is the stable Subsystem identity")
        result.subsystemName shouldBe "assembly-subsystem"
      }
    }

    "reject path-derived identity for strict qualified bootstrap" in {
      Given("a Component descriptor with no descriptor-owned identity")
      val path = Path.of("target", "test-tmp", "phase-53", "path-derived.car")

      When("implicit Subsystem projection is requested")
      val result = GenericSubsystemDescriptor.fromComponentDescriptor(path, ComponentDescriptor())

      Then("the path is not promoted to a stable Subsystem identity")
      result shouldBe a[Consequence.Failure[_]]
    }

    "retain one stable identity across equivalent development and packaged descriptor paths" in {
      Given("one descriptor copied into development and packaged locations")
      val componentid = ComponentId("org.goldenport.fixture.ComponentName")
      val descriptor = ComponentDescriptor(
        name = Some(componentid.name),
        componentName = Some(componentid.name),
        subsystemName = Some("stable-subsystem"),
        version = Some("0.1.0"),
        schemaVersion = Some(3),
        componentId = Some(componentid)
      )
      val developmentpath = Path.of("target", "test-tmp", "development", "component-descriptor.json")
      val packagedpath = Path.of("target", "test-tmp", "packaged", "component.car")

      When("both runtime routes project their implicit Subsystem descriptors")
      val development = GenericSubsystemDescriptor.fromComponentDescriptor(developmentpath, descriptor).toOption.get
      val packaged = GenericSubsystemDescriptor.fromComponentDescriptor(packagedpath, descriptor).toOption.get

      Then("the descriptor-owned identity is independent of runtime path form")
      development.subsystemName shouldBe "stable-subsystem"
      packaged.subsystemName shouldBe "stable-subsystem"
    }
  }

  private def _with_work_directory[A](body: Path => A): A = {
    val base = Files.createDirectories(
      Path.of("target", "phase-53-stable-subsystem-identity-spec", "work").toAbsolutePath.normalize
    )
    val directory = Files.createTempDirectory(base, "case-")
    try body(directory)
    finally {
      Using.resource(Files.walk(directory)) { paths =>
        paths.sorted(Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
      }
    }
  }
}
