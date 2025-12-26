package org.simplemodeling.componentframework.config.trace

import org.simplemodeling.componentframework.config.model.ConfigValue

/**
 * ConfigTracePrinter renders ConfigTrace in human-readable form.
 *
 * This is a presentation utility for CLI / debugging purposes.
 *
 * Responsibilities:
 *   - render effective configuration values
 *   - explain where values came from
 *
 * Non-responsibilities:
 *   - interpretation of configuration semantics
 *   - validation
 *   - formatting policies beyond plain text
 */
/*
 * @since   Dec. 18, 2025
 * @version Dec. 18, 2025
 * @author  ASAMI, Tomoharu
 */
object ConfigTracePrinter {

  /**
   * Print all resolved configuration entries.
   */
  def print(trace: ConfigTrace): Unit = {
    if (trace.entries.isEmpty) {
      println("(no configuration entries)")
    } else {
      trace.entries.toSeq.sortBy(_._1).foreach {
        case (key, resolution) =>
          printResolution(resolution)
      }
    }
  }

  /**
   * Explain a specific configuration key.
   */
  def explain(
    key: String,
    trace: ConfigTrace
  ): Unit = {
    trace.entries.get(key) match {
      case Some(resolution) =>
        printResolution(resolution)
      case None =>
        println(s"(no configuration found for key: $key)")
    }
  }

  // ---- internal helpers -----------------------------------------------

  private def printResolution(
    resolution: ConfigResolution
  ): Unit = {
    println(s"${resolution.key} = ${renderValue(resolution.finalValue)}")
    println(s"  origin : ${resolution.origin}")
    printHistory(resolution.history, indent = "  ")
  }

  private def printHistory(
    history: List[ConfigResolution],
    indent: String
  ): Unit = {
    if (history.nonEmpty) {
      println(s"${indent}history:")
      history.reverse.foreach { h =>
        println(
          s"$indent- ${h.origin}: ${renderValue(h.finalValue)}"
        )
      }
    }
  }

  private def renderValue(
    value: ConfigValue
  ): String =
    value match {
      case ConfigValue.StringValue(v) => v
      case other                      => other.toString
    }
}
