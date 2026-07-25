package org.goldenport.cncf.observability

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{
  EntityConditionalTransitionResult,
  EntitySuccessorIntent
}
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.observation.Taxonomy
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId
import scala.util.control.NonFatal

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object EntityConditionalTransitionObservation {
  private val _operation = "entity-conditional-transition"

  sealed abstract class Outcome(
    val name: String,
    val isError: Boolean
  )

  object Outcome {
    case object Transitioned extends Outcome("transitioned", false)
    case object NotMatched extends Outcome("not-matched", false)
    case object Conflict extends Outcome("conflict", true)
    case object Unauthorized extends Outcome("unauthorized", true)
    case object UnsupportedCapability
        extends Outcome("unsupported-capability", true)
    case object ProviderFailure extends Outcome("provider-failure", true)
    case object TransactionFailure extends Outcome("transaction-failure", true)
    case object Rejected extends Outcome("rejected", true)
  }

  final case class Classification(
    outcome: Outcome,
    diagnostic: Option[ConclusionDiagnostics.Classification]
  )

  final case class Context(
    operation: String,
    component: Option[String],
    rootid: EntityId,
    successorcollection: String,
    successorid: Option[EntityId]
  )

  def classify[R, S](
    result: Consequence[EntityConditionalTransitionResult[R, S]]
  ): Classification =
    result match {
      case Consequence.Success(
            _: EntityConditionalTransitionResult.Transitioned[?, ?]
          ) =>
        Classification(Outcome.Transitioned, None)
      case Consequence.Success(
            _: EntityConditionalTransitionResult.NotMatched[?]
          ) =>
        Classification(Outcome.NotMatched, None)
      case Consequence.Failure(conclusion) =>
        val diagnostic = ConclusionDiagnostics.classify(conclusion)
        Classification(_failure_outcome(diagnostic), Some(diagnostic))
    }

  def observe[R, S](
    context: Context,
    result: Consequence[EntityConditionalTransitionResult[R, S]]
  )(using
    executionContext: ExecutionContext
  ): Consequence[EntityConditionalTransitionResult[R, S]] = {
    val classification = classify(result)
    RuntimeDashboardMetrics.recordEntityConditionalTransition(
      classification.outcome.name,
      classification.outcome.isError,
      classification.diagnostic
    )
    val _ = executionContext.observability.emitInfo(
      executionContext.cncfCore.scope,
      "entity.conditional-transition",
      _audit_record(context, result, classification)
    )
    result
  }

  def context[S](
    component: Option[String],
    rootId: EntityId,
    successor: EntitySuccessorIntent[S],
    result: Consequence[EntityConditionalTransitionResult[?, S]]
  ): Context = {
    val successorid = _successor_id(successor, result)
    Context(
      _operation,
      component,
      rootId,
      successor.collection.name,
      successorid
    )
  }

  private def _failure_outcome(
    diagnostic: ConclusionDiagnostics.Classification
  ): Outcome = {
    val reason = diagnostic.reason.getOrElse("")
    if (
      reason == "unsupported-capability" ||
      diagnostic.capability.exists(_.contains("conditional-transition")) &&
        diagnostic.webStatus == 400
    )
      Outcome.UnsupportedCapability
    else if (
      Set(
        "transaction-failure",
        "transaction-indeterminate",
        "transaction-rollback"
      ).contains(reason)
    )
      Outcome.TransactionFailure
    else if (
      Set(
        "provider-failure",
        "datastore-failure",
        "committed-entity-projection-failure"
      ).contains(reason)
    )
      Outcome.ProviderFailure
    else if (
      diagnostic.webStatus == 401 ||
      diagnostic.webStatus == 403 ||
      diagnostic.taxonomySymptom == Taxonomy.Symptom.PermissionDenied.name
    )
      Outcome.Unauthorized
    else if (
      diagnostic.webStatus == 409 ||
      diagnostic.taxonomySymptom == Taxonomy.Symptom.Conflict.name
    )
      Outcome.Conflict
    else
      Outcome.Rejected
  }

  private def _audit_record[R, S](
    context: Context,
    result: Consequence[EntityConditionalTransitionResult[R, S]],
    classification: Classification
  )(using
    executioncontext: ExecutionContext
  ): Record = {
    val revisions =
      result match {
        case Consequence.Success(
              transitioned:
                EntityConditionalTransitionResult.Transitioned[?, ?]
            ) =>
          Record.dataAuto(
            "root-revision" -> transitioned.root.revision.value,
            "successor-revision" -> transitioned.successor.revision.value
          )
        case Consequence.Success(
              notmatched: EntityConditionalTransitionResult.NotMatched[?]
            ) =>
          Record.dataAuto(
            "root-revision" -> notmatched.existing.revision.value
          )
        case _ =>
          Record.empty
      }
    Record.dataAuto(
      "audit.kind" -> "entity-conditional-transition",
      "operation" -> context.operation,
      "component" -> context.component,
      "root-collection" -> context.rootid.collection.name,
      "root-id" -> context.rootid.print,
      "successor-collection" -> context.successorcollection,
      "successor-id" -> context.successorid.map(_.print),
      "outcome" -> classification.outcome.name,
      "diagnostic" -> classification.diagnostic.map(_.toBoundedRecord),
      "trace-id" -> executioncontext.observability.traceId.value,
      "correlation-id" ->
        executioncontext.observability.correlationId.map(_.value),
      "saga-id" -> executioncontext.observability.sagaId,
      "principal-id" -> executioncontext.security.principal.id.value
    ) ++ revisions
  }

  private def _successor_id[S](
    successor: EntitySuccessorIntent[S],
    result: Consequence[EntityConditionalTransitionResult[?, S]]
  ): Option[EntityId] =
    result match {
      case Consequence.Success(
            transitioned:
              EntityConditionalTransitionResult.Transitioned[?, S] @unchecked
          ) =>
        try
          Some(successor.persisted.id(transitioned.successor.entity))
        catch {
          case NonFatal(_) => _requested_successor_id(successor)
        }
      case _ =>
        _requested_successor_id(successor)
    }

  private def _requested_successor_id[S](
    successor: EntitySuccessorIntent[S]
  ): Option[EntityId] =
    successor match {
      case create: EntitySuccessorIntent.Create[?, ?] =>
        create.candidateId
      case bind: EntitySuccessorIntent.Bind[?] =>
        Some(bind.id)
    }
}
