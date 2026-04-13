package org.goldenport.cncf.event

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.provisional.observation.Taxonomy

/*
 * EV-04 policy boundary for event surfaces.
 *
 * Defaults:
 * - Introspection (`subscriptions`, `query`, `replay`): default-deny
 * - Execution (`publish`, `dispatch`): default-deny
 *
 * Content-manager-level capabilities are treated as baseline allow.
 *
 * @since   Mar. 20, 2026
 *  version Mar. 20, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
trait EventPolicyEngine {
  def authorizePublish(using ExecutionContext): Consequence[Unit]
  def authorizeDispatch(using ExecutionContext): Consequence[Unit]
  def authorizeIntrospection(using ExecutionContext): Consequence[Unit]
  def authorizeReplay(using ExecutionContext): Consequence[Unit]
}

object EventPolicyEngine {
  val default: EventPolicyEngine = new Default

  private final class Default extends EventPolicyEngine {
    private val _publish_caps = Set("event_publish", "event_admin", "content_manager", "content_admin")
    private val _dispatch_caps = Set("event_dispatch", "event_admin", "content_manager", "content_admin")
    private val _introspection_caps = Set("event_view", "event_admin", "content_manager", "content_admin")
    private val _replay_caps = Set("event_replay", "event_admin", "content_manager", "content_admin")

    def authorizePublish(using ctx: ExecutionContext): Consequence[Unit] =
      _allow_or_deny("event.publish", _publish_caps)

    def authorizeDispatch(using ctx: ExecutionContext): Consequence[Unit] =
      _allow_or_deny("event.dispatch", _dispatch_caps)

    def authorizeIntrospection(using ctx: ExecutionContext): Consequence[Unit] =
      _allow_or_deny("event.introspection", _introspection_caps)

    def authorizeReplay(using ctx: ExecutionContext): Consequence[Unit] =
      _allow_or_deny("event.replay", _replay_caps)

    private def _allow_or_deny(
      operation: String,
      requiredCaps: Set[String]
    )(using ctx: ExecutionContext): Consequence[Unit] =
      if (_has_any_capability(requiredCaps))
        Consequence.unit
      else
        Consequence.operationIllegal(
          operation,
          Seq(Facet.Message(s"required capability: ${requiredCaps.toVector.sorted.mkString("|")}"))
        )

    private def _has_any_capability(
      names: Set[String]
    )(using ctx: ExecutionContext): Boolean =
      ctx.security.hasAnyCapability(names)
  }
}
