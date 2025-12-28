package org.goldenport.cncf.config

import org.goldenport.cncf.config.model.{Config, ConfigValue}
import org.goldenport.cncf.config.trace.{ConfigTrace, ConfigResolution}
import org.goldenport.cncf.config.source.ConfigSource

/**
 * MergePolicy defines how configuration values are merged.
 *
 * This object is intentionally boring and explicit.
 * It performs:
 *   - key-based overwrite
 *   - deterministic resolution order
 *   - trace generation
 *
 * It does NOT:
 *   - validate values
 *   - interpret semantics
 *   - apply defaults
 *   - perform IO
 */
/*
 * @since   Dec. 18, 2025
 * @version Dec. 18, 2025
 * @author  ASAMI, Tomoharu
 */
object MergePolicy {

  /**
   * Merge a new ConfigSource into the current Config and ConfigTrace.
   */
  def merge(
    current: Config,
    trace: ConfigTrace,
    source: ConfigSource
  ): (Config, ConfigTrace) = {
    source.load() match {
      case org.goldenport.Consequence.Success(cfg) =>
        cfg.values.foldLeft((current, trace)) {
          case ((cfg, tr), (key, value)) =>
            val newConfig = Config(cfg.values.updated(key, value))
            val newTrace  = updateTrace(tr, key, value, source)
            (newConfig, newTrace)
        }

      case org.goldenport.Consequence.Failure(_) =>
        (current, trace)
    }
  }

  private def updateTrace(
    trace: ConfigTrace,
    key: String,
    value: ConfigValue,
    source: ConfigSource
  ): ConfigTrace = {
    val prev = trace.entries.get(key)

    val history = prev match {
      case Some(res) =>
        res.history :+ ConfigResolution(
          key        = key,
          finalValue = res.finalValue,
          origin     = res.origin,
          history    = res.history
        )
      case None =>
        Nil
    }

    val resolution = ConfigResolution(
      key        = key,
      finalValue = value,
      origin     = source.origin,
      history    = history
    )

    trace.copy(entries = trace.entries.updated(key, resolution))
  }
}
