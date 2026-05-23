package org.goldenport.cncf.provider

import org.goldenport.Consequence
import org.goldenport.cncf.action.{Behavior, ProviderBehavior}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}
import org.goldenport.cncf.unitofwork.ExecUowM

/*
 * @since   May. 23, 2026
 * @version May. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ProviderRequest(
  provider: String,
  operation: String,
  attributes: Map[String, String] = Map.empty
) {
  def label: String =
    Vector(provider, operation).map(_.trim).filter(_.nonEmpty).mkString(".") match {
      case "" => "provider"
      case x => s"provider:$x"
    }
}

abstract class ProviderCall[A]
  extends ProviderBehavior
  with ProviderCall.Core.Holder {
  protected def build_Program: ExecUowM[A]

  final def run(): Consequence[A] =
    build_Program.value.foldMap(executionContext.runtime.unitOfWorkInterpreter).flatMap(identity)
}

object ProviderCall {
  final case class Core(
    request: ProviderRequest,
    executionContext: ExecutionContext,
    component: Option[Component],
    correlationId: Option[CorrelationId]
  )

  object Core {
    trait Holder extends Behavior.Core.Holder {
      def core: Core

      override def behaviorCore: Behavior.Core =
        Behavior.Core(executionContext, component, correlationId)

      def providerRequest: ProviderRequest = core.request
      override def executionContext: ExecutionContext = core.executionContext
      override def component: Option[Component] = core.component
      override def correlationId: Option[CorrelationId] = core.correlationId
    }
  }
}
