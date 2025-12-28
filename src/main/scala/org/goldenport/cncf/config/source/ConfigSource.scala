package org.goldenport.cncf.config.source

import java.nio.file.Path

import org.goldenport.cncf.config.model.{Config, ConfigValue}
import org.goldenport.cncf.config.trace.ConfigOrigin
import org.goldenport.Consequence
import org.goldenport.cncf.config.source.file.FileConfigLoader
import org.goldenport.cncf.config.source.file.SimpleFileConfigLoader

/**
 * ConfigSource represents one configuration input source.
 *
 * This is an internal framework abstraction.
 * Concrete sources are defined as case classes under the companion object
 * to keep the public surface small and explicit.
 */
/*
 * @since   Dec. 18, 2025
 * @version Dec. 18, 2025
 * @author  ASAMI, Tomoharu
 */
sealed trait ConfigSource {
  def origin: ConfigOrigin
  def rank: Int
  def location: Option[String]

  /**
   * Load configuration values from this source.
   *
   * Rules:
   *   - Missing sources must return Success(empty Config)
   *   - IO / parse errors must return Failure
   */
  def load(): Consequence[Config]
}

object ConfigSource {

  /**
   * Home directory configuration source (~/.sie).
   */
  def home(): Option[ConfigSource] = {
    val home = sys.props.get("user.home").map(p => Path.of(p))
    home.map { base =>
      val path = base.resolve(".sie").resolve("config.conf")
      File(
        origin = ConfigOrigin.Home,
        path   = path,
        rank   = Rank.Home,
        loader = new SimpleFileConfigLoader
      )
    }
  }

  /**
   * Project-level configuration source (project root /.sie).
   * Project root resolution is handled elsewhere.
   */
  def project(cwd: Path): Option[ConfigSource] = {
    val path = cwd.resolve(".sie").resolve("config.conf")
    Some(
      File(
        origin = ConfigOrigin.Project,
        path   = path,
        rank   = Rank.Project,
        loader = new SimpleFileConfigLoader
      )
    )
  }

  /**
   * Current working directory configuration source (./.sie).
   */
  def cwd(cwd: Path): Option[ConfigSource] = {
    val path = cwd.resolve(".sie").resolve("config.conf")
    Some(
      File(
        origin = ConfigOrigin.Cwd,
        path   = path,
        rank   = Rank.Cwd,
        loader = new SimpleFileConfigLoader
      )
    )
  }

  /**
   * Environment variable configuration source.
   */
  def env(env: Map[String, String]): Option[ConfigSource] =
    Some(Env(env, Rank.Environment))

  /**
   * Command-line argument configuration source.
   */
  def args(args: Map[String, String]): Option[ConfigSource] =
    Some(Args(args, Rank.Arguments))

  /**
   * Precedence ranks (weak -> strong)
   */
  object Rank {
    val Home: Int        = 10
    val Project: Int     = 20
    val Cwd: Int         = 30
    val Environment: Int = 40
    val Arguments: Int   = 50
  }

  /**
   * File-based configuration source.
   *
   * This source delegates actual file loading to FileConfigLoader.
   */
  final case class File(
    origin: ConfigOrigin,
    path: Path,
    rank: Int,
    loader: FileConfigLoader
  ) extends ConfigSource {

    override def location: Option[String] =
      Some(path.toString)

    override def load(): Consequence[Config] =
      loader.load(path)
  }

  /**
   * Environment variable configuration source.
   */
  final case class Env(
    env: Map[String, String],
    rank: Int = Rank.Environment
  ) extends ConfigSource {

    override val origin: ConfigOrigin = ConfigOrigin.Environment

    override val location: Option[String] = None

    override def load(): Consequence[Config] =
      Consequence.success(
        Config(
          env.map { case (k, v) => k -> ConfigValue.StringValue(v) }
        )
      )
  }

  /**
   * Command-line argument configuration source.
   */
  final case class Args(
    args: Map[String, String],
    rank: Int = Rank.Arguments
  ) extends ConfigSource {

    override val origin: ConfigOrigin = ConfigOrigin.Arguments

    override val location: Option[String] = None

    override def load(): Consequence[Config] =
      Consequence.success(
        Config(
          args.map { case (k, v) => k -> ConfigValue.StringValue(v) }
        )
      )
  }
}
