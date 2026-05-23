package org.goldenport.cncf.action

import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}

/*
 * @since   Mar. 30, 2026
 * @version May. 24, 2026
 * @author  ASAMI, Tomoharu
 */
trait Behavior
  extends Behavior.Core.Holder
  with BehaviorFeaturePart
  with BehaviorInformationPart

object Behavior {
  final case class Core(
    executionContext: ExecutionContext,
    component: Option[Component],
    correlationId: Option[CorrelationId]
  )

  object Core {
    trait Holder {
      def behaviorCore: Core

      def executionContext: ExecutionContext = behaviorCore.executionContext
      def component: Option[Component] = behaviorCore.component
      def correlationId: Option[CorrelationId] = behaviorCore.correlationId
    }
  }
}

trait ActionBehavior
  extends Behavior
  with ActionCall.Core.Holder
  with ActionCallRepositoryPart
  with ActionCallBrowserPart
  with ActionCallEntityStorePart
  with ActionCallBlobPart
  with ActionCallDataStorePart
  with ActionCallHttpPart
  with ActionCallShellCommandPart

trait ProviderBehavior
  extends Behavior
  with BehaviorHttpPart
  with ProviderBehaviorFeaturePart
