package org.goldenport.cncf.path

import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
final class PathPreNormalizerHttpSuffixSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _identifier = for {
    first <- Gen.choose('a', 'z')
    size <- Gen.choose(0, 12)
    rest <- Gen.listOfN(size, Gen.oneOf(('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')))
  } yield first.toString + rest.mkString

  private val _qualified_component = for {
    depth <- Gen.choose(1, 4)
    namespace <- Gen.listOfN(depth, _identifier)
    component <- _identifier
  } yield (namespace :+ component).mkString(".")

  "E1 Canonical HTTP suffix equivalence" should afterWord("in spec:path-resolution, example:E1, rules:R1,R4,R5,SR3,protocol-R7, phase:96, slice:P96-05A") {
    "retain the exact short or qualified Display component and query/read operation across representation suffixes" in {
      Given("short and qualified component identities, including the actual DisplayReadConsumer, and suffix-free/json/html query/read paths")
      val components = Vector("DisplayReadConsumer", "org.goldenport.cncf.test.DisplayReadConsumer")
      val paths = for {
        component <- components
        operation <- Vector("query", "read")
        suffix <- Vector("", ".json", ".html")
      } yield (Vector(component, "display", operation + suffix), Vector(component, "display", operation))

      When("the actual HTTP pre-normalizer interprets each three-part path with no aliases")
      val results = paths.map { case (path, expected) =>
        (PathPreNormalizer.rewriteSegments(path, RunMode.Server, AliasResolver.empty), expected)
      }

      Then("representation suffixes leave the canonical component, display service and operation exactly unchanged")
      results.foreach { case (actual, expected) => actual shouldBe expected }
    }
  }

  "E2 Alias and selector compatibility" should afterWord("in spec:path-resolution, example:E2, rules:R1,R4,R5, phase:96, slice:P96-05A") {
    "preserve mode-gated aliases, canonical HTTP and CLI selectors, unknown singletons and empty paths" in {
      Given("a Command-only ping alias, canonical suffix-free triples and qualified selectors, unknown singletons and an empty path")
      val admin = BuiltinComponentIdentity.ADMIN.name
      val canonical = Vector(admin, "system", "ping")
      val selector = canonical.mkString(".")
      val resolver = AliasResolver.from(Vector(Alias("ping", selector, Set(RunMode.Command), None)))
      val aliases = Vector("ping", "PING", " ping ")
      val modes = Vector(RunMode.Command, RunMode.Server)
      val displaypaths = Vector("query", "read").map(operation =>
        Vector("org.goldenport.cncf.test.DisplayReadConsumer", "display", operation))

      When("the actual HTTP and unchanged CLI pre-normalizers interpret both active and inactive modes")
      val aliasresults = for {
        mode <- modes
        alias <- aliases
      } yield (mode, alias, PathPreNormalizer.rewriteSegments(Vector(alias), mode, resolver),
        PathPreNormalizer.rewriteSelector(alias, mode, resolver))
      val canonicalresults = modes.map { mode =>
        (PathPreNormalizer.rewriteSegments(canonical, mode, resolver),
          PathPreNormalizer.rewriteSegments(Vector(selector), mode, resolver),
          PathPreNormalizer.rewriteSelector(selector, mode, resolver),
          PathPreNormalizer.rewriteSegments(Vector("unknown"), mode, resolver),
          PathPreNormalizer.rewriteSelector("unknown", mode, resolver),
          PathPreNormalizer.rewriteSegments(Vector.empty, mode, resolver),
          displaypaths.map(path => PathPreNormalizer.rewriteSegments(path, mode, resolver)))
      }

      Then("only active aliases expand while canonical identity and unchanged singleton/empty behavior are preserved")
      aliasresults.foreach { case (mode, alias, http, cli) =>
        if (mode == RunMode.Command) {
          http shouldBe canonical
          cli shouldBe selector
        } else {
          http shouldBe Vector(alias)
          cli shouldBe alias.trim
        }
      }
      canonicalresults.foreach { case (http, singleton, cli, unknownhttp, unknowncli, empty, display) =>
        http shouldBe canonical
        singleton shouldBe canonical
        cli shouldBe selector
        unknownhttp shouldBe Vector("unknown")
        unknowncli shouldBe "unknown"
        empty shouldBe Vector.empty
        display shouldBe displaypaths
      }
    }
  }

  "E3 Property-based suffix preservation" should afterWord("in spec:path-resolution, example:E3, rules:R1,R4,R5, phase:96, slice:P96-05A") {
    "preserve canonical HTTP triples with or without a suffix and the dotted CLI selector in every run mode" in {
      Given("finite unfiltered nonempty alphanumeric identifiers, short or one-to-four-depth namespace components, suffixes and actual run modes")
      val cases = for {
        component <- Gen.oneOf(_identifier, _qualified_component)
        service <- _identifier
        operation <- _identifier
        suffix <- Gen.oneOf("json", "html", "txt")
        mode <- Gen.oneOf(RunMode.values.toVector)
      } yield (component, service, operation, suffix, mode)
      val property = Prop.forAll(cases) { case (component, service, operation, suffix, mode) =>
        Given("one exact canonical triple and its final-operation representation suffix")
        val path = Vector(component, service, operation)
        val suffixed = Vector(component, service, operation + "." + suffix)
        val selector = path.mkString(".")

        When("the actual HTTP pre-normalizer interprets both paths and the unchanged CLI pre-normalizer interprets the dotted selector")
        val plainresult = PathPreNormalizer.rewriteSegments(path, mode, AliasResolver.empty)
        val suffixresult = PathPreNormalizer.rewriteSegments(suffixed, mode, AliasResolver.empty)
        val cliresult = PathPreNormalizer.rewriteSelector(selector, mode, AliasResolver.empty)

        Then("both HTTP forms retain exactly the original three parts and CLI retains exactly the canonical selector")
        plainresult shouldBe path
        suffixresult shouldBe path
        cliresult shouldBe selector
        true
      }

      When("ScalaCheck evaluates at least one hundred legal cases without implication or discarded inputs")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

      Then("the preservation law passes with at least one hundred successes and zero discards")
      result.passed shouldBe true
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }
  }
}
