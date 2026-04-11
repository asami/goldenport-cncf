package org.goldenport.cncf.config

import java.nio.file.Files
import scala.annotation.nowarn
import org.goldenport.Consequence
import org.goldenport.cncf.config.model.{Config, ConfigValue}
import org.goldenport.cncf.config.source.ConfigSource
import org.goldenport.cncf.config.source.file.FileConfigLoader
import org.goldenport.cncf.config.trace.ConfigOrigin
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 *  version Mar. 22, 2026
 * @version Apr. 11, 2026
 * @author  ASAMI, Tomoharu
 */
@nowarn("cat=deprecation")
class ConfigResolverSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with TableDrivenPropertyChecks {

  "ConfigResolver" should {

    "apply deterministic overwrite semantics by rank (HOME < PROJECT < CWD < ENV < ARGS) for same keys" in {
      Given("sources provided in random order with overlapping keys")
      val resolver = ConfigResolver.default
      val sources = Seq(
        _source(ConfigOrigin.Arguments, ConfigSource.Rank.Arguments, Map("k" -> "args")),
        _source(ConfigOrigin.Home, ConfigSource.Rank.Home, Map("k" -> "home")),
        _source(ConfigOrigin.Cwd, ConfigSource.Rank.Cwd, Map("k" -> "cwd")),
        _source(ConfigOrigin.Project, ConfigSource.Rank.Project, Map("k" -> "project")),
        _source(ConfigOrigin.Environment, ConfigSource.Rank.Environment, Map("k" -> "env"))
      )

      When("resolving configs")
      val resolved = resolver.resolve(sources)

      Then("highest-precedence source wins deterministically")
      resolved match {
        case Consequence.Success(rc) =>
          rc.config.string("k") shouldBe Some("args")
          rc.trace.get("k").map(_.origin) shouldBe Some(ConfigOrigin.Arguments)
        case Consequence.Failure(c) =>
          fail(s"unexpected failure: $c")
      }
    }

    "remain deterministic regardless of source input order and keep missing file sources graceful" in {
      Given("multiple source permutations and missing optional file sources")
      val resolver = ConfigResolver.default
      val cwd = Files.createTempDirectory("cncf-configresolver-missing")
      val missingProject = ConfigSource.project(cwd).getOrElse(fail("missing project source"))
      val missingCwd = ConfigSource.cwd(cwd).getOrElse(fail("missing cwd source"))

      val table = Table(
        "sources",
        Seq(
          _source(ConfigOrigin.Environment, ConfigSource.Rank.Environment, Map("k" -> "env")),
          _source(ConfigOrigin.Arguments, ConfigSource.Rank.Arguments, Map("k" -> "args")),
          missingProject,
          missingCwd
        ),
        Seq(
          missingCwd,
          _source(ConfigOrigin.Arguments, ConfigSource.Rank.Arguments, Map("k" -> "args")),
          _source(ConfigOrigin.Environment, ConfigSource.Rank.Environment, Map("k" -> "env")),
          missingProject
        )
      )

      When("resolving each source order")
      Then("results are equivalent and missing file sources do not fail hard")
      forAll(table) { sources =>
        resolver.resolve(sources) match {
          case Consequence.Success(rc) =>
            rc.config.string("k") shouldBe Some("args")
            rc.trace.get("k").map(_.origin) shouldBe Some(ConfigOrigin.Arguments)
          case Consequence.Failure(c) =>
            fail(s"expected graceful success, got failure: $c")
        }
      }
    }
  }

  private def _source(
    origin0: ConfigOrigin,
    rank0: Int,
    values: Map[String, String]
  ): ConfigSource =
    ConfigSource.File(
      origin = origin0,
      path = Files.createTempFile("configresolver-spec-source", ".conf"),
      rank = rank0,
      loader = new FileConfigLoader {
        override def load(path: java.nio.file.Path): Consequence[Config] = {
          val _ = path
          Consequence.success(
            Config(values.map { case (k, v) => k -> ConfigValue.StringValue(v) })
          )
        }
      }
    )
}
