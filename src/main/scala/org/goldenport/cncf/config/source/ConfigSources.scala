package org.goldenport.cncf.config.source

import java.nio.file.Path

/*
 * @since   Dec. 18, 2025
 * @version Dec. 18, 2025
 * @author  ASAMI, Tomoharu
 */
final case class ConfigSources(
  sources: Seq[ConfigSource]
)

object ConfigSources {
  def standard(
    cwd: Path,
    args: Map[String, String] = Map.empty,
    env: Map[String, String]  = sys.env
  ): ConfigSources = {

    val home    = ConfigSource.home()
    val project = ConfigSource.project(cwd)
    val current = ConfigSource.cwd(cwd)
    val envSrc  = ConfigSource.env(env)
    val argSrc  = ConfigSource.args(args)

    ConfigSources(
      Seq(
        home,
        project,
        current,
        envSrc,
        argSrc
      ).flatten
    )
  }
}
