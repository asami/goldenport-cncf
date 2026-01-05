package org.goldenport.cncf.context

/**
 * System-scoped runtime assumptions for CNCF.
 *
 * - MUST NOT reference core ExecutionContext.Core.
 * - MUST NOT contain security, request, trace, or CanonicalId data.
 * - Immutable and startup-only.
 */
/*
 * @since   Jan.  5, 2026
 * @version Jan.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SystemContext(
  componentIdentity: Option[String] = None,
  policyHandles: Map[String, String] = Map.empty,
  observabilityProvider: Option[String] = None,
  configSnapshot: Map[String, String] = Map.empty,
  featureFlags: Map[String, Boolean] = Map.empty
)

object SystemContext {
  val empty: SystemContext = SystemContext()
}
