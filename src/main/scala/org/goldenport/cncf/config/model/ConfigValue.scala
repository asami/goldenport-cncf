package org.goldenport.cncf.config.model

/**
 * ConfigValue represents a raw configuration value.
 *
 * Design goals:
 *   - Schema-agnostic
 *   - Serialization-friendly (JSON / YAML / HOCON)
 *   - Traceable and explainable
 *   - No validation or coercion logic
 *
 * This is a structural model, not a semantic one.
 */
/*
 * @since   Dec. 18, 2025
 * @version Dec. 18, 2025
 * @author  ASAMI, Tomoharu
 */
@deprecated(
  "Use org.goldenport.configuration.* (Phase 2.8)",
  "Phase 2.8"
)
sealed trait ConfigValue

object ConfigValue {

  /** Primitive values */
  final case class StringValue(value: String) extends ConfigValue
  final case class NumberValue(value: BigDecimal) extends ConfigValue
  final case class BooleanValue(value: Boolean) extends ConfigValue

  /** Composite values */
  final case class ListValue(values: List[ConfigValue]) extends ConfigValue
  final case class ObjectValue(values: Map[String, ConfigValue]) extends ConfigValue

  /** Explicit null / disabled */
  case object NullValue extends ConfigValue

  /**
   * Utility helpers (non-normative)
   *
   * These helpers are intentionally minimal and optional.
   */
  object syntax {
    def str(p: String): ConfigValue = StringValue(p)
    def num(p: BigDecimal): ConfigValue = NumberValue(p)
    def bool(p: Boolean): ConfigValue = BooleanValue(p)
    def list(p: List[ConfigValue]): ConfigValue = ListValue(p)
    def obj(p: Map[String, ConfigValue]): ConfigValue = ObjectValue(p)
    val nullValue: ConfigValue = NullValue
  }
}
