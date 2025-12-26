package org.simplemodeling.componentframework.config

import org.simplemodeling.componentframework.config.model.Config
import org.simplemodeling.componentframework.config.trace.ConfigTrace

/**
 * ResolvedConfig represents the result of configuration resolution.
 *
 * It contains both:
 *   - the final resolved configuration values
 *   - the trace information describing how each value was determined
 *
 * Design principles:
 *   - Boring over clever
 *   - Explicit over convenient
 *   - Deterministic over flexible
 *
 * Notes:
 *   - Consumers may ignore `trace` in normal operation.
 *   - `trace` exists for debugging, explanation, CLI output,
 *     and AI-assisted reasoning.
 */
/*
 * @since   Dec. 18, 2025
 * @version Dec. 18, 2025
 * @author  ASAMI, Tomoharu
 */
case class ResolvedConfig(
  config: Config,
  trace: ConfigTrace
)
