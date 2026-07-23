package org.goldenport.cncf.operation.evaluation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, ExecutionContextId, IdGenerationContext}
import org.goldenport.record.Record

/*
 * Immutable causal context for operation evaluation capture.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OperationEvaluationInvocation(
  executionId: OperationEvaluationExecutionId,
  operation: OperationEvaluationOperationIdentity,
  parentExecutionId: Option[OperationEvaluationExecutionId],
  executionContextId: ExecutionContextId,
  corpus: Option[CorpusEvaluationCorrelation] = None,
  experiment: Option[ExperimentEvaluationCorrelation] = None
) {
  def toRecord: Record = Record.dataAuto(
    "executionId" -> executionId.toString,
    "operation" -> operation.toRecord,
    "parentExecutionId" -> parentExecutionId.map(_.toString),
    "executionContextId" -> executionContextId.toString,
    "corpus" -> corpus.map(_.toRecord),
    "experiment" -> experiment.map(_.toRecord)
  )
}

final case class OperationEvaluationContext(
  invocation: Option[OperationEvaluationInvocation] = None,
  correlation: Option[OperationEvaluationCorrelation] = None,
  activeSinks: Vector[OperationEvaluationSinkIdentity] = Vector.empty
) {
  def prepareC(
    operation: OperationEvaluationOperationIdentity,
    occurredat: Instant,
    idgeneration: IdGenerationContext,
    corpus: Option[CorpusEvaluationCorrelation] = None,
    experiment: Option[ExperimentEvaluationCorrelation] = None
  ): Consequence[OperationEvaluationContext] = {
    val parentexecutionid = invocation.map(_.executionId)
    val executionid = OperationEvaluationExecutionId.create(operation.print, occurredat, idgeneration)
    val contextid = ExecutionContextId.create(operation.print, occurredat, idgeneration)
    Consequence.success(copy(
      invocation = Some(OperationEvaluationInvocation(
        executionid,
        operation,
        parentexecutionid,
        contextid,
        corpus,
        experiment
      )),
      correlation = None
    ))
  }

  def beginAttemptC(
    occurredat: Instant,
    idgeneration: IdGenerationContext,
    executioncontext: ExecutionContext
  ): Consequence[OperationEvaluationContext] =
    invocation match {
      case Some(invocation) =>
        val attemptid = OperationEvaluationAttemptId.create(invocation.operation.print, occurredat, idgeneration)
        val jobcontext = executioncontext.jobContext
        Consequence.success(copy(correlation = Some(OperationEvaluationCorrelation(
          executionId = invocation.executionId,
          attemptId = attemptid,
          operation = invocation.operation,
          parentExecutionId = invocation.parentExecutionId,
          executionContextId = Some(invocation.executionContextId),
          jobId = jobcontext.jobId,
          taskId = jobcontext.currentTask.orElse(jobcontext.taskId),
          traceId = Some(executioncontext.observability.traceId),
          observabilityCorrelationId = executioncontext.observability.correlationId,
          corpus = invocation.corpus,
          experiment = invocation.experiment
        ))))
      case None =>
        Consequence.stateInvalid("operation evaluation invocation is not prepared")
    }

  def isSinkActive(sink: OperationEvaluationSinkIdentity): Boolean =
    activeSinks.contains(sink)

  def enterSinkC(sink: OperationEvaluationSinkIdentity): Consequence[OperationEvaluationContext] =
    if (isSinkActive(sink))
      Consequence.stateConflict("operation evaluation sink is already active")
    else if (activeSinks.size >= OperationEvaluationContext.MAXIMUM_ACTIVE_SINKS)
      Consequence.argumentLimitExceeded(
        "activeSinks",
        OperationEvaluationContext.MAXIMUM_ACTIVE_SINKS,
        activeSinks.size + 1,
        "operation-evaluation.active-sinks"
      )
    else
      Consequence.success(copy(activeSinks = activeSinks :+ sink))

  def toRecord: Record = Record.dataAuto(
    "invocation" -> invocation.map(_.toRecord),
    "correlation" -> correlation.map(_.toRecord),
    "activeSinks" -> activeSinks.map(_.toRecord)
  )
}

object OperationEvaluationContext {
  val MAXIMUM_ACTIVE_SINKS: Int = 16
  val empty: OperationEvaluationContext = OperationEvaluationContext()
}
