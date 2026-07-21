package org.goldenport.cncf.repository

import java.nio.charset.StandardCharsets
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for explicit CAR/SAR repository discovery.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentRepositoryIndexSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The CNCF Component Repository Index" should {
    "normalize and render explicit CAR and SAR discovery entries deterministically" in {
      Given("a versioned repository index whose source order differs from canonical artifact order")
      val source = _resource("valid.json")

      When("the CNCF codec parses and renders the index")
      val index = _success(ComponentRepositoryIndex.parse(source))
      val rendered = index.render
      val reparsed = _success(ComponentRepositoryIndex.parse(rendered))

      Then("CAR and SAR identities are retained in deterministic order with stable public contract paths")
      index.artifacts.map(_.identity) shouldBe Vector("car" -> "textus-blog", "sar" -> "textus-platform")
      reparsed shouldBe index
      rendered shouldBe reparsed.render
      ComponentRepositoryIndex.PUBLIC_PATH shouldBe "repository/catalog/index.json"
      ComponentRepositoryIndex.SCHEMA_RESOURCE_PATH shouldBe "META-INF/cncf/component-repository-index.schema.json"
    }

    "reject duplicate artifact identities before a repository snapshot is accepted" in {
      Given("two index entries with the same CAR artifact identity")
      val source = _resource("invalid-duplicate.json")

      When("the CNCF codec validates the discovery snapshot")
      val result = ComponentRepositoryIndex.parse(source)

      Then("the duplicate remains an explicit contract failure")
      _failure(result) should include("Duplicate component repository artifacts: car:textus-blog")
    }

    "reject traversal and identity-mismatched catalog paths" in {
      Given("an index entry that escapes the repository catalog root")
      val traversal = _resource("invalid-path.json")
      val mismatch = _resource("valid.json").replace("car/textus-blog.yaml", "sar/other.yaml")

      When("the CNCF codec validates both catalog references")
      val first = ComponentRepositoryIndex.parse(traversal)
      val second = ComponentRepositoryIndex.parse(mismatch)

      Then("neither unsafe nor identity-mismatched detail links are admitted")
      _failure(first) should include("Invalid component repository catalog path")
      _failure(second) should include("Invalid component repository catalog path")
    }

    "reject unsupported schemas, malformed timestamps, kinds, statuses, and empty selectors" in {
      Given("repository indexes that violate independent discovery contract fields")
      val source = _resource("valid.json")
      val variants = Vector(
        source.replace(ComponentRepositoryIndex.SCHEMA_VERSION, "cncf.component-repository-index.v2"),
        source.replace("2026-07-21T00:00:00Z", "not-an-instant"),
        source.replace("\"kind\": \"car\"", "\"kind\": \"jar\""),
        source.replaceFirst("\"status\": \"active\"", "\"status\": \"unknown\""),
        source.replace("\"recommended\": \"1.2.0\"", "\"recommended\": \"\"")
      )

      When("the CNCF codec validates each malformed index")
      val results = variants.map(ComponentRepositoryIndex.parse)

      Then("every invalid field is rejected instead of being normalized silently")
      results.forall(_.isLeft) shouldBe true
    }

    "reject unknown root and artifact fields instead of drifting from the published JSON Schema" in {
      Given("otherwise valid indexes containing fields outside the v1 schema")
      val source = _resource("valid.json")
      val rootfield = source.replaceFirst("\"generatedAt\"", "\"repository\": \"unexpected\",\n  \"generatedAt\"")
      val entryfield = source.replaceFirst("\"catalog\"", "\"health\": \"up\",\n      \"catalog\"")

      When("the CNCF codec applies the same closed-field contract as the JSON Schema")
      val first = ComponentRepositoryIndex.parse(rootfield)
      val second = ComponentRepositoryIndex.parse(entryfield)

      Then("both unknown fields fail explicitly")
      _failure(first) should include("Unknown component repository index index fields: repository")
      _failure(second) should include("Unknown component repository index artifact[0] fields: health")
    }

    "keep repository availability separate from runtime health" in {
      Given("a deprecated CAR and a disabled SAR that remain known repository entries")
      val source = _resource("valid.json")
        .replaceFirst("\"status\": \"active\"", "\"status\": \"deprecated\"")
        .replaceFirst("\"status\": \"active\"", "\"status\": \"disabled\"")

      When("the repository index is parsed without a runtime registration")
      val index = _success(ComponentRepositoryIndex.parse(source))

      Then("availability status is preserved without inventing process or health facts")
      index.artifacts.map(_.status).sorted shouldBe Vector("deprecated", "disabled")
      index.render should not include "health"
      index.render should not include "process"
    }
  }

  private def _resource(name: String): String = {
    val path = s"contracts/component-repository-index/$name"
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse(fail(s"missing fixture: $path"))
    try new String(stream.readAllBytes(), StandardCharsets.UTF_8)
    finally stream.close()
  }

  private def _success[A](result: Either[String, A]): A =
    result.fold(message => fail(message), identity)

  private def _failure[A](result: Either[String, A]): String =
    result.fold(identity, _ => fail("expected component repository index failure"))
}
