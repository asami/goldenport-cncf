package org.goldenport.cncf.config.trace

import org.goldenport.cncf.config.model.ConfigValue

/**
 * ConfigTrace captures how configuration values were resolved.
 *
 * This is a descriptive data structure:
 *   - no execution semantics
 *   - no side effects
 *   - no policy decisions
 *
 * Intended usage:
 *   - explain-config / debug output
 *   - CLI inspection
 *   - test assertions
 *   - AI-assisted reasoning
 */
/*
 * @since   Dec. 18, 2025
 * @version Dec. 18, 2025
 * @author  ASAMI, Tomoharu
 */
case class ConfigTrace(
  entries: Map[String, ConfigResolution]
) {
  def get(key: String): Option[ConfigResolution] =
    entries.get(key)
}

object ConfigTrace {
  val empty: ConfigTrace = ConfigTrace(Map.empty)
}

/**
 * ConfigResolution represents the resolution result of a single config key.
 */
case class ConfigResolution(
  key: String,
  finalValue: ConfigValue,
  origin: ConfigOrigin,
  history: List[ConfigResolution]
)

/**
 * ConfigOrigin represents the classification of where a value came from.
 *
 * NOTE:
 *   - This is classification only.
 *   - It must not imply behavior.
 */
sealed trait ConfigOrigin

object ConfigOrigin {
  case object Default extends ConfigOrigin
  case object Home extends ConfigOrigin
  case object Project extends ConfigOrigin
  case object Cwd extends ConfigOrigin
  case object Environment extends ConfigOrigin
  case object Arguments extends ConfigOrigin
}
