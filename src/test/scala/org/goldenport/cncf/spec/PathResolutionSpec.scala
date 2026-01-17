package org.goldenport.cncf.spec

import org.goldenport.cncf.resolver.{CanonicalPath, PathResolution, PathResolutionResult}
import org.scalatest.Tag
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen

/*
 * @since   Jan. 17, 2026
 * @version Jan. 17, 2026
 * @author  ASAMI, Tomoharu
 */
class PathResolutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  val inPathResolutionSpec =
    afterWord("in spec:path-resolution, example:E1, rules:R5,R2, phase:2.8")

  "Path Resolution" when {
    "which is defined in docs/spec/path-resolution.md" that {
      "E1 Single-operation Component Omission" must inPathResolutionSpec {
        val operations = Seq(CanonicalPath("script", "default", "run"))
        Given("a single available operation: CanonicalPath(script, default, run)")
        val result = PathResolution.resolve("script", operations)
        When("resolving a component-only selector for a single-operation component")
        Then("CanonicalPath(script, default, run) is produced")
        result match {
          case PathResolutionResult.Success(path) =>
            path shouldBe CanonicalPath("script", "default", "run")
          case other =>
            fail(s"Expected canonical path, got ${other}")
        }
      }

      "E2_ServiceSpecifiedOmission" must inPathResolutionSpec {
        val operations = Seq(
          CanonicalPath("script", "default", "run")
        )
        Given("a single available operation under service 'default'")
        val result = PathResolution.resolve("script/default", operations)
        When("resolving a service-level selector that omits the operation")
        Then("CanonicalPath(script, default, run) is produced")
        result match {
          case PathResolutionResult.Success(path) =>
            path shouldBe CanonicalPath("script", "default", "run")
          case other =>
            fail(s"Expected canonical path, got ${other}")
        }
      }

      "E3_OpenAPIPathWithOmittedOperation" must inPathResolutionSpec {
        val operations = Seq(
          CanonicalPath("openapi", "api", "get")
        )
        Given("a single available OpenAPI operation: openapi/api/get")
        val result = PathResolution.resolve("openapi/api", operations)
        When("resolving an OpenAPI path that omits the operation")
        Then("CanonicalPath(openapi, api, get) is produced")
        result match {
          case PathResolutionResult.Success(path) =>
            path shouldBe CanonicalPath("openapi", "api", "get")
          case other =>
            fail(s"Expected canonical path, got ${other}")
        }
      }

      "E4_OpenAPIJsonRepresentation" must inPathResolutionSpec {
        val operations = Seq(
          CanonicalPath("openapi", "api", "get")
        )
        Given("a single available OpenAPI operation with JSON representation")
        val result = PathResolution.resolve("openapi/api/openapi.json", operations)
        When("resolving an openapi.json representation")
        Then("CanonicalPath(openapi, api, get) is produced")
        result match {
          case PathResolutionResult.Success(path) =>
            path shouldBe CanonicalPath("openapi", "api", "get")
          case other =>
            fail(s"Expected canonical path, got ${other}")
        }
      }

      "E5_BuiltinComponentWithoutOmission" must inPathResolutionSpec {
        val operations = Seq(
          CanonicalPath("builtin", "system", "status"),
          CanonicalPath("builtin", "system", "health")
        )
        Given("multiple available operations under a builtin component")
        val result = PathResolution.resolve("builtin/system", operations)
        When("resolving a builtin component selector without operation")
        Then("resolution fails due to ambiguous omission")
        result match {
          case PathResolutionResult.Failure(_) =>
            succeed
          case other =>
            fail(s"Expected failure, got ${other}")
        }
      }

      "E6_InvalidOmission" must inPathResolutionSpec {
        val operations = Seq(
          CanonicalPath("script", "default", "run")
        )
        Given("a selector that does not match component or service structure")
        val result = PathResolution.resolve("script//run", operations)
        When("resolving an invalid or malformed selector")
        Then("resolution fails")
        result match {
          case PathResolutionResult.Failure(_) =>
            succeed
          case other =>
            fail(s"Expected failure, got ${other}")
        }
      }
    }
  }
}
