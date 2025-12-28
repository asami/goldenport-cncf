package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.config.model.*
import org.goldenport.cncf.config.trace.*
import org.goldenport.cncf.config.source.{ConfigSource, ConfigSources}
import scala.util.boundary
import scala.util.boundary.break

/**
 * ConfigResolver is the single public entry point for configuration resolution.
 *
 * Responsibilities:
 *   - evaluate given configuration sources in precedence order
 *   - merge configurations deterministically
 *   - produce both final Config and full resolution trace
 *
 * Non-responsibilities:
 *   - configuration semantics
 *   - validation
 *   - defaults
 *   - logging
 *   - source discovery
 */
/*
 * @since   Dec. 18, 2025
 * @version Dec. 18, 2025
 * @author  ASAMI, Tomoharu
 */
trait ConfigResolver {

  def resolve(
    sources: Seq[ConfigSource]
  ): Consequence[ResolvedConfig]

  def resolve(
    sources: ConfigSources
  ): Consequence[ResolvedConfig] =
    resolve(sources.sources)
}

object ConfigResolver {

  def default: ConfigResolver =
    new DefaultConfigResolver
}

final class DefaultConfigResolver
  extends ConfigResolver {

  override def resolve(
    sources: Seq[ConfigSource]
  ): Consequence[ResolvedConfig] = boundary {

    val ordered = sources.sortBy(_.rank)

    var currentConfig: Config = Config.empty
    var currentTrace: ConfigTrace = ConfigTrace.empty

    ordered.foreach { source =>
      source.load() match {
        case Consequence.Success(cfg) =>
          val (nextConfig, nextTrace) =
            MergePolicy.merge(
              currentConfig,
              currentTrace,
              source
            )

          currentConfig = nextConfig
          currentTrace  = nextTrace

        case Consequence.Failure(err) =>
          break(Consequence.Failure(err))
      }
    }

    Consequence.Success(
      ResolvedConfig(
        config = currentConfig,
        trace  = currentTrace
      )
    )
  }
}
