package org.goldenport.cncf.protocol

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.observability.ValidationDiagnostics
import org.goldenport.protocol.Request
import org.goldenport.protocol.spec.OperationDefinition
import org.goldenport.record.Record

/*
 * Optional CNCF extension point for OperationDefinition request parsing failures.
 *
 * Action generation is the standard parameter-validation boundary. Components
 * that need domain-specific diagnostics can observe that boundary without
 * moving parsing into ActionCall execution.
 *
 * @since   Apr. 29, 2026
 * @version Apr. 29, 2026
 * @author  ASAMI, Tomoharu
 */
trait OperationRequestValidationObserver { self: OperationDefinition =>
  def observeOperationRequestValidationFailure(
    request: Request,
    conclusion: Conclusion,
    context: ExecutionContext
  ): Unit
}

object OperationRequestValidationObserver {
  def observeFailure[A](
    componentName: String,
    serviceName: String,
    operationName: String,
    operation: Option[OperationDefinition],
    request: Request,
    result: Consequence[A],
    context: ExecutionContext
  ): Unit =
    result match {
      case Consequence.Failure(conclusion) =>
        val fqn = s"${componentName}.${serviceName}.${operationName}"
        val classification = OperationRequestValidationDiagnostics.classify(conclusion)
        RuntimeDashboardMetrics.recordOperationRequestValidation(
          operation = fqn,
          diagnosticKey = Some(classification.diagnosticKey)
        )
        if (ValidationDiagnostics.isValidation(conclusion))
          RuntimeDashboardMetrics.recordValidation(
            operation = fqn,
            diagnosticKey = Some(classification.diagnosticKey)
          )
        val _ = context.observability.emitInfo(
          context.cncfCore.scope,
          "operation.request_validation",
          Record.dataAuto(
            "operation.fqn" -> fqn,
            "operation.component" -> componentName,
            "operation.service" -> serviceName,
            "operation.name" -> operationName,
            "request.operation" -> request.operation,
            "result.success" -> false,
            "diagnostic" -> classification.toRecord,
            "error.kind" -> conclusion.observation.taxonomy.print,
            "error.code" -> conclusion.status.webCode.code
          )
        )
        operation.foreach(observe(_, request, conclusion, context))
      case _ =>
        ()
    }

  def observe(
    operation: OperationDefinition,
    request: Request,
    conclusion: Conclusion,
    context: ExecutionContext
  ): Unit =
    operation match {
      case observer: OperationRequestValidationObserver =>
        observer.observeOperationRequestValidationFailure(request, conclusion, context)
      case _ =>
        ()
    }
}
