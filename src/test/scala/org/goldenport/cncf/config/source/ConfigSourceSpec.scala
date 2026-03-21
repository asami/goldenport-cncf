package org.goldenport.cncf.config.source

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.config.trace.ConfigOrigin
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version Mar. 22, 2026
 * @author  ASAMI, Tomoharu
 */
class ConfigSourceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with TableDrivenPropertyChecks {

  "ConfigSource" should {

    "resolve project/cwd source path to <cwd>/.cncf/config.conf (legacy effective behavior)" in {
      Given("a temporary cwd path")
      val cwd = Files.createTempDirectory("cncf-configsource-spec")
      val expected = cwd.resolve(".cncf").resolve("config.conf")

      When("creating project and cwd sources")
      val project = ConfigSource.project(cwd).getOrElse(fail("missing project source"))
      val current = ConfigSource.cwd(cwd).getOrElse(fail("missing cwd source"))

      Then("both point to the same file location with different origins")
      project.location shouldBe Some(expected.toString)
      current.location shouldBe Some(expected.toString)
      project.origin shouldBe ConfigOrigin.Project
      current.origin shouldBe ConfigOrigin.Cwd
    }

    "gracefully load empty config when optional file source is missing" in {
      Given("file based project/cwd sources with no config file")
      val cwd = Files.createTempDirectory("cncf-configsource-missing")
      val table = Table(
        ("source", "origin"),
        (ConfigSource.project(cwd).getOrElse(fail("missing project source")), ConfigOrigin.Project),
        (ConfigSource.cwd(cwd).getOrElse(fail("missing cwd source")), ConfigOrigin.Cwd)
      )

      When("loading each optional source")
      Then("each load succeeds with empty config")
      forAll(table) { (source, origin) =>
        source.origin shouldBe origin
        source.load() match {
          case Consequence.Success(config) =>
            config.values shouldBe Map.empty
          case Consequence.Failure(c) =>
            fail(s"expected graceful success for missing file source, got failure: $c")
        }
      }
    }
  }
}
