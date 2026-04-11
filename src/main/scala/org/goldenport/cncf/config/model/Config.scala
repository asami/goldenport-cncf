package org.goldenport.cncf.config.model

import scala.annotation.nowarn

/**
 * Config represents a resolved configuration set.
 *
 * This is a simple structural container:
 *   - no validation
 *   - no schema awareness
 *   - no resolution logic
 *
 * All semantics (meaning, constraints, defaults) must be applied
 * by higher layers using this structure.
 */
/*
 * @since   Dec. 18, 2025
 * @version Apr. 11, 2026
 * @author  ASAMI, Tomoharu
 */
@deprecated(
  "Use org.goldenport.configuration.* (Phase 2.8)",
  "Phase 2.8"
)
@nowarn("cat=deprecation")
case class Config(
  values: Map[String, ConfigValue]
) {

  /**
   * Get a raw ConfigValue by key.
   */
  def get(key: String): Option[ConfigValue] =
    values.get(key)

  /**
   * Convenience accessors (non-normative).
   * These helpers intentionally avoid implicit conversion or coercion.
   */
  def string(key: String): Option[String] =
    values.get(key).collect { case ConfigValue.StringValue(v) => v }

  def boolean(key: String): Option[Boolean] =
    values.get(key).collect { case ConfigValue.BooleanValue(v) => v }

  def number(key: String): Option[BigDecimal] =
    values.get(key).collect { case ConfigValue.NumberValue(v) => v }
}

@nowarn("cat=deprecation")
object Config {
  val empty: Config = Config(Map.empty)
}
