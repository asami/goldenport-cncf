package org.goldenport.cncf.config.source

import java.nio.file.Files
import org.goldenport.cncf.config.trace.ConfigOrigin
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 23, 2025
 * @version May.  2, 2026
 * @author  ASAMI, Tomoharu
 */
class ConfigSourcesSpec extends AnyWordSpec with Matchers with GivenWhenThen {

  "ConfigSources" should {

    "build standard sources in documented precedence order HOME < PROJECT < CWD < TEXTUS-CWD < ENV < ARGS" in {
      Given("standard source assembly input")
      val cwd = Files.createTempDirectory("cncf-configsources-standard")
      val env = Map("cncf.env.key" -> "from-env")
      val args = Map("cncf.args.key" -> "from-args")

      When("building standard sources")
      val sources = ConfigSources.standard(cwd, args = args, env = env).sources

      Then("source origins follow fixed precedence order")
      sources.map(_.origin) shouldBe Seq(
        ConfigOrigin.Home,
        ConfigOrigin.Project,
        ConfigOrigin.Cwd,
        ConfigOrigin.Cwd,
        ConfigOrigin.Environment,
        ConfigOrigin.Arguments
      )
      sources.map(_.rank) shouldBe Seq(
        ConfigSource.Rank.Home,
        ConfigSource.Rank.Project,
        ConfigSource.Rank.Cwd,
        ConfigSource.Rank.CwdTextus,
        ConfigSource.Rank.Environment,
        ConfigSource.Rank.Arguments
      )
    }

    "preserve deterministic source shape including project/cwd same path and distinct origin" in {
      Given("a temporary cwd")
      val cwd = Files.createTempDirectory("cncf-configsources-shape")
      val sources = ConfigSources.standard(cwd, args = Map.empty, env = Map.empty).sources

      When("extracting project and cwd entries")
      val project = sources.find(_.origin == ConfigOrigin.Project).getOrElse(fail("missing project"))
      val current = sources.find(_.origin == ConfigOrigin.Cwd).getOrElse(fail("missing cwd"))

      Then("both use same location but stay distinct sources")
      project.location shouldBe current.location
      project.origin should not be current.origin
    }
  }
}
