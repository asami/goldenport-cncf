package org.goldenport.cncf.repository

import java.nio.charset.StandardCharsets
import org.goldenport.cncf.component.identity.{ComponentId, ComponentReleaseCoordinate}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for explicit namespace-qualified CAR/SAR repository discovery.
 *
 * @since   Jul. 21, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentRepositoryIndexSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The CNCF Component Repository Index" should {
    "codec and admission" which {
      "normalize and render v2 CAR and SAR entries deterministically without filename identity collapse" in {
      Given("a v2 index with two namespace-distinct CARs sharing the same derived filename and one SAR")
      val source = _resource("valid.json")

      When("the CNCF codec parses and renders the index")
      val index = _success(ComponentRepositoryIndex.parse(source))
      val rendered = index.render
      val reparsed = _success(ComponentRepositoryIndex.parse(rendered))

      Then("canonical CAR identity includes kind, namespace, and local id while SAR remains artifact-keyed")
      index.artifacts.map(_.identity) shouldBe Vector(
        ("car", "org.alpha.textus", "Shared"),
        ("car", "org.beta.textus", "Shared"),
        ("sar", "", "textus-platform")
      )
      index.artifacts.take(2).map(_.artifactId) shouldBe Vector("textus-shared", "textus-shared")
      reparsed shouldBe index
      rendered shouldBe reparsed.render
      ComponentRepositoryIndex.SCHEMA_VERSION shouldBe "cncf.component-repository-index.v2"
      ComponentRepositoryIndex.PUBLIC_PATH shouldBe "repository/catalog/index.json"
      ComponentRepositoryIndex.SCHEMA_RESOURCE_PATH shouldBe "META-INF/cncf/component-repository-index.schema.json"
      }

      "reject duplicate canonical identities while admitting same derived CAR artifacts in distinct namespaces" in {
      Given("a duplicate canonical CAR identity and the independent valid same-filename entries")
      val duplicate = _resource("invalid-duplicate.json")
      val distinct = _resource("valid.json")

      When("the v2 identity contract is validated")
      val rejected = ComponentRepositoryIndex.parse(duplicate)
      val admitted = ComponentRepositoryIndex.parse(distinct)

      Then("only exact kind namespace and id duplication is rejected")
      _failure(rejected) should include("Duplicate component repository artifacts: car:org.alpha.textus:Shared")
      _success(admitted).artifacts.count(_.artifactId == "textus-shared") shouldBe 2
      }

      "reject malformed generatedAt values with the stable v2 index diagnostic" in {
      Given("a v2 index whose generatedAt value is not an ISO instant")
      val source = _resource("valid.json").replaceFirst("2026-08-07T00:00:00Z", "not-an-instant")

      When("the index reader admits the generated timestamp")
      val result = ComponentRepositoryIndex.parse(source)

      Then("the malformed generatedAt value is rejected before an index is accepted")
      _failure(result) should include("Invalid component repository index generatedAt: not-an-instant")
      }

      "reject unsupported v2 artifact kinds with their stable diagnostic" in {
      Given("a v2 index whose CAR entry has an unsupported kind")
      val source = _resource("valid.json").replaceFirst("\\\"kind\\\": \\\"car\\\"", "\\\"kind\\\": \\\"jar\\\"")

      When("the index reader parses the artifact kind")
      val result = ComponentRepositoryIndex.parse(source)

      Then("the unsupported kind is rejected before identity or catalog projection")
      _failure(result) should include("Unsupported component artifact kind: jar")
      }

      "reject invalid v2 artifact statuses with their stable diagnostic" in {
      Given("a v2 index whose first active artifact has an unsupported status")
      val source = _resource("valid.json").replaceFirst("\\\"status\\\": \\\"active\\\"", "\\\"status\\\": \\\"unknown\\\"")

      When("the index reader validates the artifact status")
      val result = ComponentRepositoryIndex.parse(source)

      Then("the invalid status is rejected before an index is accepted")
      _failure(result) should include("Invalid component repository artifact status: unknown")
      }

      "reject empty v2 recommended selectors with their stable diagnostic" in {
      Given("a v2 index whose recommended selector is explicitly empty")
      val source = _resource("valid.json").replaceFirst("\\\"recommended\\\": \\\"1.2.0\\\"", "\\\"recommended\\\": \\\"\\\"")

      When("the index reader validates the optional recommended selector")
      val result = ComponentRepositoryIndex.parse(source)

      Then("the empty recommended selector is rejected before an index is accepted")
      _failure(result) should include("Component repository index recommended must not be empty")
      }

      "admit repeated ordinary string values while rejecting duplicate JSON object fields with their actual names" in {
      Given("a valid repeated selector value plus duplicate root and entry field variants")
      val source = _resource("valid.json")
      val duplicateroot = source.replaceFirst(
        "\\\"generatedAt\\\": \\\"2026-08-07T00:00:00Z\\\",",
        "\\\"generatedAt\\\": \\\"2026-08-07T00:00:00Z\\\",\n  \\\"generatedAt\\\": \\\"2026-08-07T00:00:00Z\\\","
      )
      val duplicateentry = source.replaceFirst(
        "\\\"artifactId\\\": \\\"textus-platform\\\",",
        "\\\"artifactId\\\": \\\"textus-platform\\\",\n      \\\"artifactId\\\": \\\"textus-platform\\\","
      )

      When("the strict JSON reader parses repeated values and duplicate object keys")
      val admitted = ComponentRepositoryIndex.parse(source)
      val rootrejected = ComponentRepositoryIndex.parse(duplicateroot)
      val entryrejected = ComponentRepositoryIndex.parse(duplicateentry)

      Then("repeated selectors remain valid while actual duplicate keys report stable diagnostics")
      val index = _success(admitted)
      index.artifacts.find(_.id.contains("Shared")).flatMap(_.recommended) shouldBe Some("1.2.0")
      index.artifacts.find(_.id.contains("Shared")).flatMap(_.latestStable) shouldBe Some("1.2.0")
      _failure(rootrejected) should include("component.repository-index.duplicate-field source=index path=json field=generatedAt")
      _failure(entryrejected) should include("component.repository-index.duplicate-field source=index path=json field=artifactId")
      }

      "reject missing null wrong-type unknown malformed path and derived-projection ambiguity" in {
      Given("otherwise valid v2 indexes that violate one closed serialized contract rule")
      val source = _resource("valid.json")
      val traversal = _resource("invalid-path.json")
      val variants = Vector(
        source.replaceFirst("\\\"namespace\\\": \\\"org.alpha.textus\\\",\\n      ", ""),
        source.replaceFirst("\\\"id\\\": \\\"Shared\\\"", "\\\"id\\\": null"),
        source.replaceFirst("\\\"namespace\\\": \\\"org.alpha.textus\\\"", "\\\"namespace\\\": 7"),
        source.replaceFirst("\\\"catalog\\\"", "\\\"unexpected\\\": true,\n      \\\"catalog\\\""),
        source.replaceFirst("\\\"textus-shared\\\"", "\\\"other-artifact\\\""),
        source.replaceFirst("car/org/alpha/textus/textus-shared.yaml", "car/textus-shared.yaml"),
        source.replaceFirst("\\\"schemaVersion\\\": \\\"cncf.component-repository-index.v2\\\"", "\\\"schemaVersion\\\": \\\"cncf.component-repository-index.v1\\\""),
      ) :+ traversal :+ "{"

      When("the reader admits only unambiguous canonical JSON")
      val results = variants.map(ComponentRepositoryIndex.parse)

      Then("every missing, invalid, legacy, malformed, or derived-ambiguity form fails")
      results.forall(_.isLeft) shouldBe true
      _failure(results(0)) should include("expected=namespace actual=missing")
      _failure(results(1)) should include("id must not be null")
      _failure(results(3)) should include("Unknown component repository index artifact[0] fields: unexpected")
      _failure(results(4)) should include("field=artifactId")
      _failure(results(8)) should include("Invalid component repository index JSON")
      }
    }

    "SAR compatibility" which {
      "retain SAR catalog compatibility and reject namespace fields on SAR entries" in {
      Given("a v2 SAR catalog entry and its prohibited CAR-only fields")
      val source = _resource("valid.json")
      val withnamespace = source.replaceFirst("\\\"kind\\\": \\\"sar\\\"", "\\\"kind\\\": \\\"sar\\\",\n      \\\"namespace\\\": \\\"org.alpha.textus\\\"")

      When("the reader validates SAR identity and catalog shape")
      val index = _success(ComponentRepositoryIndex.parse(source))
      val rejected = ComponentRepositoryIndex.parse(withnamespace)

      Then("SAR stays artifact-keyed at its direct catalog location")
      index.artifacts.last.catalog shouldBe "sar/textus-platform.yaml"
      index.artifacts.last.namespace shouldBe None
      _failure(rejected) should include("SAR index entry must not carry component namespace or id")
      }
    }

    "schema and availability boundaries" which {
      "keep availability knowledge separate from runtime health and publish the v2 schema resource" in {
      Given("a deprecated CAR, disabled SAR, and the packaged JSON schema")
      val source = _resource("valid.json")
        .replaceFirst("\\\"status\\\": \\\"active\\\"", "\\\"status\\\": \\\"deprecated\\\"")
        .replaceFirst("\\\"status\\\": \\\"active\\\"", "\\\"status\\\": \\\"disabled\\\"")
      val schema = _schema_resource()

      When("the index and schema are inspected without runtime registration")
      val index = _success(ComponentRepositoryIndex.parse(source))

      Then("the resource declares v2 and availability does not fabricate runtime facts")
      index.artifacts.map(_.status).sorted should contain allOf ("deprecated", "disabled")
      index.render should not include "health"
      index.render should not include "process"
      schema should include("cncf.component-repository-index.v2")
      schema should include("\"namespace\"")
      schema should include("^[a-z][a-z0-9]*(\\\\.[a-z][a-z0-9]*)+$")
      schema should include("\"id\"")
      }
    }

    "canonical namespace projection properties" which {
      "round-trip namespace-qualified CAR projections without same-filename identity collapse" in {
        Given("two distinct canonical namespaces with one shared local id and release selector")
        val segments = Gen.pick(2, Vector("alpha", "beta", "gamma", "delta")).map(_.toVector)
        val property = Prop.forAll(segments) { values =>
          val firstnamespace = s"org.${values.head}.textus"
          val secondnamespace = s"org.${values(1)}.textus"
          val first = _car_entry(firstnamespace, "Shared", "1.2.3")
          val second = _car_entry(secondnamespace, "Shared", "1.2.3")
          val index = ComponentRepositoryIndex(
            ComponentRepositoryIndex.SCHEMA_VERSION,
            java.time.Instant.parse("2026-08-07T00:00:00Z"),
            Vector(first, second)
          )
          val rendered = index.render

          ComponentRepositoryIndex.parse(rendered).toOption.exists { reparsed =>
            val entries = reparsed.artifacts
            val identities = entries.map(_.identity).toSet
            val catalogs = entries.map(_.catalog)
            val coordinates = Vector(
              _coordinate(firstnamespace, "Shared", "1.2.3"),
              _coordinate(secondnamespace, "Shared", "1.2.3")
            )

            reparsed.render == rendered &&
              identities == Set(
                ("car", firstnamespace, "Shared"),
                ("car", secondnamespace, "Shared")
              ) &&
              entries.map(_.artifactId).distinct == Vector("textus-shared") &&
              catalogs.distinct.size == 2 &&
              catalogs.toSet == coordinates.map(_.carCatalogRelativePath()).toSet
          }
        }

        When("the canonical index codec renders reparses and samples the generated entries")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

        Then("each namespace/local-id projection round-trips while identical filenames remain catalog-isolated")
        checked.passed shouldBe true
      }
    }
  }

  private def _resource(name: String): String = {
    val path = s"contracts/component-repository-index/$name"
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse(fail(s"missing fixture: $path"))
    try new String(stream.readAllBytes(), StandardCharsets.UTF_8)
    finally stream.close()
  }

  private def _schema_resource(): String = {
    val path = ComponentRepositoryIndex.SCHEMA_RESOURCE_PATH
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse(fail(s"missing schema resource: $path"))
    try new String(stream.readAllBytes(), StandardCharsets.UTF_8)
    finally stream.close()
  }

  private def _car_entry(namespace: String, id: String, release: String): ComponentRepositoryIndexEntry = {
    val coordinate = _coordinate(namespace, id, release)
    ComponentRepositoryIndexEntry(
      ComponentArtifactKind.Car,
      coordinate.mavenArtifactId(),
      coordinate.carCatalogRelativePath(),
      "active",
      Some(release),
      Some(release),
      None,
      Some(namespace),
      Some(id)
    )
  }

  private def _coordinate(namespace: String, id: String, release: String): ComponentReleaseCoordinate =
    ComponentReleaseCoordinate.require(ComponentId.require(s"$namespace.$id"), release)

  private def _success[A](result: Either[String, A]): A =
    result.fold(message => fail(message), identity)

  private def _failure[A](result: Either[String, A]): String =
    result.fold(identity, _ => fail("expected component repository index failure"))
}
