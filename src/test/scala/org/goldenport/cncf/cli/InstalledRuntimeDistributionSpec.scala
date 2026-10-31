package org.goldenport.cncf.cli

import java.nio.file.{Files, Path, Paths}
import java.util.{Comparator, UUID}
import scala.jdk.CollectionConverters.*
import scala.util.Using
import io.circe.Json
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 21, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class InstalledRuntimeDistributionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Historical installed runtime metadata" should {
    "retain the original identifiers and paths without requiring any old binary or manifest file" in {
      Given("a historical manifest body and original locator whose installation was not materialized")
      val (manifest, json) = _fixture()
      Files.exists(manifest.getParent) shouldBe false
      When("the metadata-only decoder reads the retained body")
      val result = InstalledRuntimeDistribution.readHistoricalMetadataC(manifest, json.noSpaces).toOption.get
      Then("the original ordered provenance survives without operational admission or filesystem reconstruction")
      result.manifest shouldBe manifest
      result.installationId.toString shouldBe manifest.getParent.getFileName.toString
      result.modules.map(_.coordinate) shouldBe Vector(InstalledRuntimeDistribution.ROOT_COORDINATE,
        "org.example:resource:jar:Tests:1.0-SNAPSHOT")
      result.modules.map(_.path) shouldBe Vector(manifest.getParent.resolve("runtime/0000.jar"),
        manifest.getParent.resolve("runtime/0001.jar"))
      result.component.path shouldBe manifest.getParent.resolve("components/textus-sm-workflow.car")
      result.component.identity shouldBe InstalledRuntimeDistribution.COMPONENT_IDENTITY
      result.modules.forall(_.source == manifest.getParent.getParent.resolve("unavailable-source.jar")) shouldBe true
      Files.exists(manifest.getParent) shouldBe false
    }

    "reject malformed historical identity and path metadata rather than reinterpret it as an executable binding" in {
      Given("one exact old metadata shape")
      val (manifest, original) = _fixture()
      val module = original.hcursor.get[Vector[Json]]("modules").toOption.get.head
      val changes = Vector(
        original.deepMerge(Json.obj("kind" -> Json.fromString("cncf.deployed-runtime.v2"))),
        original.deepMerge(Json.obj("installation_id" -> Json.fromString(UUID.randomUUID().toString))),
        original.deepMerge(Json.obj("update_id" -> Json.fromString("bad"))),
        original.deepMerge(Json.obj("modules" -> Json.arr(module, module))),
        original.deepMerge(Json.obj("modules" -> Json.arr(module.deepMerge(
          Json.obj("path" -> Json.fromString("../outside.jar")))))),
        original.deepMerge(Json.obj("component" -> Json.obj("identity" -> Json.fromString("other"))))
      )
      When("malformed retained bodies and a relative original locator are decoded")
      val refused = changes.map(json => InstalledRuntimeDistribution.readHistoricalMetadataC(manifest, json.noSpaces)) ++
        Vector(InstalledRuntimeDistribution.readHistoricalMetadataC(Paths.get("relative.json"), original.noSpaces),
          InstalledRuntimeDistribution.readHistoricalMetadataC(manifest, "not-json"))
      Then("each fails metadata validation without inspecting binaries or constructing execution state")
      refused.foreach(_.isFaillure shouldBe true)
      Files.exists(manifest.getParent) shouldBe false
    }

    "preserve classifier spelling while refusing unsupported coordinate aliases" in {
      Given("retained modules with the old canonical coordinate contract")
      val (manifest, original) = _fixture()
      val modules = original.hcursor.get[Vector[Json]]("modules").toOption.get
      val coordinates = Gen.alphaNumStr.suchThat(_.nonEmpty).map(name => s"org.example:$name:jar:1.0")
      When("generated four-part aliases are decoded without an operational resolver")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), Prop.forAll(coordinates) { coordinate =>
        val changed = modules(1).deepMerge(Json.obj("coordinate" -> Json.fromString(coordinate)))
        InstalledRuntimeDistribution.readHistoricalMetadataC(manifest,
          original.deepMerge(Json.obj("modules" -> Json.arr(modules.head, changed))).noSpaces).isFaillure
      })
      Then("aliases are not interpreted as new deployment or resolution instructions")
      checked.passed shouldBe true
    }
  }

  "Normal CLI bootstrap" should {
    "admit ordinary activation settings without an installed manifest or UUID image" in {
      Given("an ordinary isolated host with no installed runtime files")
      val base = Files.createDirectories(Paths.get("target").toAbsolutePath)
      val root = Files.createTempDirectory(base, "ordinary-runtime-bootstrap-")
      try {
        val args = Array("--no-default-components", "--textus.test.home.path=" + root.resolve("home"),
          "--textus.test.home.inherit.runtime=false", "--textus.test.home.inherit.repositories=false",
          "--textus.test.home.inherit.local-data=false",
          "--component-factory-class=org.example.ExternalFactory", "--discover=classes", "--help")
        When("the normal bootstrap resolves its repository and activation policy")
        val result = CncfRuntime.bootstrapC(root, args, Map.empty)
        Then("it does not require a pinned copied CAR or a private installed profile")
        result.isSuccess shouldBe true
      } finally Using.resource(Files.walk(root)) { stream =>
        stream.sorted(Comparator.reverseOrder[Path]()).iterator().asScala.foreach(path => Files.deleteIfExists(path))
      }
    }
  }

  private def _fixture(): (Path, Json) = {
    val installation = UUID.randomUUID()
    val root = Paths.get("target", "historical-runtime-not-materialized", installation.toString).toAbsolutePath.normalize()
    val manifest = root.resolve("runtime-installation.json")
    def module(coordinate: String, relative: String): Json = Json.obj(
      "coordinate" -> Json.fromString(coordinate), "path" -> Json.fromString(relative),
      "source" -> Json.fromString(root.getParent.resolve("unavailable-source.jar").toString))
    val json = Json.obj(
      "kind" -> Json.fromString(InstalledRuntimeDistribution.KIND),
      "installation_id" -> Json.fromString(installation.toString),
      "update_id" -> Json.fromString(UUID.randomUUID().toString),
      "application" -> Json.fromString(InstalledRuntimeDistribution.APPLICATION),
      "root_coordinate" -> Json.fromString(InstalledRuntimeDistribution.ROOT_COORDINATE),
      "repositories" -> Json.arr(Json.fromString("ivy2Local"), Json.fromString("central")),
      "modules" -> Json.arr(module(InstalledRuntimeDistribution.ROOT_COORDINATE, "runtime/0000.jar"),
        module("org.example:resource:jar:Tests:1.0-SNAPSHOT", "runtime/0001.jar")),
      "component" -> module(InstalledRuntimeDistribution.COMPONENT_COORDINATE, "components/textus-sm-workflow.car")
        .deepMerge(Json.obj("identity" -> Json.fromString(InstalledRuntimeDistribution.COMPONENT_IDENTITY)))
    )
    manifest -> json
  }
}
