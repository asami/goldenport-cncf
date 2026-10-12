package org.goldenport.cncf.composite

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.operation.evaluation.OperationEvaluationContext
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.protocol.Request

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[composite] object CompositeQueryV2ExecutionContext {
  def branchContext(
    caller: ExecutionContext,
    target: Subsystem,
    request: Request
  ): Consequence[ExecutionContext] =
    caller match {
      case source: ExecutionContext.Instance =>
        _target_template(target, request).map { template =>
          val base = source.copy(
            cncfCore = source.cncfCore.copy(
              scope = template.cncfCore.scope,
              runtime = template.cncfCore.runtime,
              resources = template.cncfCore.resources,
              resourceTrees = template.cncfCore.resourceTrees,
              operationEvaluation = OperationEvaluationContext.empty,
              framework = source.cncfCore.framework.copy(
                callTreeEnabled = false,
                inlineCallTree = false,
                traceJob = false,
                saveCallTree = false
              )
            )
          )
          val isolated = ExecutionContext.withFrameworkCallTreeEnabled(base, enabled = false)
          ExecutionContext.withFreshExecutionResponseCell(isolated)
        }
      case _ => Consequence.operationInvalid("composite-query-v2.context-unavailable")
    }

  private def _target_template(
    target: Subsystem,
    request: Request
  ): Consequence[ExecutionContext] =
    _selector(request).flatMap { selector =>
      target.operationResolver.resolve(selector) match {
        case ResolutionResult.Resolved(_, componentid, _, _) =>
          target.findComponent(ComponentId(componentid)) match {
            case Some(component) => Consequence.success(component.logic.executionContext())
            case None => Consequence.operationInvalid("composite-query-v2.target-component")
          }
        case _ => Consequence.operationInvalid("composite-query-v2.target-component")
      }
    }

  private def _selector(request: Request): Consequence[String] =
    (request.component, request.service) match {
      case (Some(component), Some(service)) => Consequence.success(s"$component.$service.${request.operation}")
      case (None, Some(service)) => Consequence.success(s"$service.${request.operation}")
      case _ => Consequence.operationInvalid("composite-query-v2.target-component")
    }
}
