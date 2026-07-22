package org.goldenport.cncf.observability

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.cncf.config.{
  ComponentInitializationParameters,
  ComponentParameterDiagnosticOutcome,
  ComponentParameterDiagnostics,
  ComponentParameterKey
}
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.record.Record

/*
 * Bootstrap-owned, payload-safe parameter resolution observation.
 *
 * Component initialization precedes Action/ExecutionContext ownership, so
 * this boundary records runtime metrics without manufacturing a CallTree.
 *
 * @since   Jul. 22, 2026
 * @version Jul. 22, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object ComponentParameterBootstrapObservation {
  def record(
    componentid: ComponentId,
    instanceid: ComponentInstanceId,
    declarations: Vector[ComponentParameterKey[?]],
    result: Consequence[ComponentInitializationParameters]
  ): Unit =
    result match {
      case Consequence.Success(_) if declarations.isEmpty =>
        _record(
          componentid,
          instanceid,
          None,
          None,
          None,
          None,
          ComponentParameterDiagnosticOutcome.Empty.token,
          error = false,
          _context_record(
            componentid,
            instanceid,
            ComponentParameterDiagnosticOutcome.Empty.token
          ),
          None
        )
      case Consequence.Success(parameters) =>
        parameters.diagnosticSummaries.foreach { summary =>
          _record(
            componentid,
            instanceid,
            Some(summary.parameterName),
            Some(ComponentParameterDiagnostics.requirementToken(summary.requirement)),
            Some(ComponentParameterDiagnostics.confidentialityToken(summary.confidentiality)),
            Some(summary.provenance.token),
            summary.outcome.token,
            error = false,
            _context_record(componentid, instanceid, summary.outcome.token) ++ summary.toRecord,
            None
          )
        }
      case Consequence.Failure(conclusion) =>
        val owned = ComponentParameterDiagnostics.isParameterFailure(conclusion)
        val diagnostic = _bounded_diagnostic(
          ConclusionDiagnostics.classify(conclusion),
          owned
        )
        val parameter = diagnostic.parameter.filter(_ => owned)
        val declaration = parameter.flatMap { name =>
          declarations.find(_.name == name)
        }
        val provenance =
          if (owned)
            ComponentParameterDiagnostics.provenance(conclusion).map(_.token)
          else
            None
        val summary = Record.dataAuto(
          "component" -> componentid.name,
          "componentInstance" -> instanceid.instance,
          "parameter" -> parameter,
          "requirement" -> declaration.map(x => ComponentParameterDiagnostics.requirementToken(x.requirement)),
          "confidentiality" -> declaration.map(x => ComponentParameterDiagnostics.confidentialityToken(x.confidentiality)),
          "provenance" -> provenance,
          "outcome" -> diagnostic.diagnosticKey
        )
        _record(
          componentid,
          instanceid,
          parameter,
          declaration.map(x => ComponentParameterDiagnostics.requirementToken(x.requirement)),
          declaration.map(x => ComponentParameterDiagnostics.confidentialityToken(x.confidentiality)),
          provenance,
          diagnostic.diagnosticKey,
          error = true,
          summary,
          Some(diagnostic)
        )
    }

  private def _record(
    componentid: ComponentId,
    instanceid: ComponentInstanceId,
    parameter: Option[String],
    requirement: Option[String],
    confidentiality: Option[String],
    provenance: Option[String],
    outcome: String,
    error: Boolean,
    summary: Record,
    diagnostic: Option[ConclusionDiagnostics.Classification]
  ): Unit =
    RuntimeDashboardMetrics.recordComponentInitializationParameter(
      component = componentid.name,
      componentinstance = instanceid.instance,
      parameter = parameter,
      requirement = requirement,
      confidentiality = confidentiality,
      provenance = provenance,
      outcome = outcome,
      error = error,
      summary = Some(summary),
      diagnostic = diagnostic
    )

  private def _context_record(
    componentid: ComponentId,
    instanceid: ComponentInstanceId,
    outcome: String
  ): Record =
    Record.data(
      "component" -> componentid.name,
      "componentInstance" -> instanceid.instance,
      "outcome" -> outcome
    )

  private def _bounded_diagnostic(
    diagnostic: ConclusionDiagnostics.Classification,
    owned: Boolean
  ): ConclusionDiagnostics.Classification =
    if (owned)
      diagnostic.copy(previous = Vector.empty)
    else
      diagnostic.copy(
        diagnosticKey = "unknown",
        detailCode = None,
        appCode = None,
        appStatus = None,
        parameter = None,
        fieldPath = None,
        policy = None,
        reason = None,
        capability = None,
        permission = None,
        guard = None,
        relation = None,
        previous = Vector.empty
      )
}
