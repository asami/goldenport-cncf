package org.goldenport.cncf.operation.evaluation

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.util.control.NonFatal

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext}
import org.goldenport.cncf.job.{ActionId, ActionTask, JobPersistencePolicy, JobTask, TaskFailed, TaskId, TaskOutcome, TaskSucceeded}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.protocol.operation.OperationResponse

/*
 * Framework capture wrapper around the canonical ActionTask execution point.
 * It deliberately preserves the wrapped task outcome when auxiliary delivery
 * is unavailable, slow, saturated, or failed.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final class OperationEvaluationActionTask(
  underlying: ActionTask,
  attemptcapture: OperationEvaluationAttemptCapture,
  responsebinding: (OperationResponse, ExecutionContext) => Consequence[OperationResponse],
  executionscope: Option[ScopeContext] = None
) extends JobTask {
  import OperationEvaluationActionTask.{CaptureStart, PendingTerminal}

  private val _pending_terminals = new ConcurrentHashMap[TaskId, PendingTerminal]()

  def actionId: ActionId = underlying.actionId
  override def taskKind: String = underlying.taskKind
  override def targetKind: Option[String] = underlying.targetKind
  override def relation: Option[String] = underlying.relation
  override def transactionRole: Option[String] = underlying.transactionRole
  override def transactionScope: Option[String] = underlying.transactionScope
  override def compensationActionRef: Option[String] = underlying.compensationActionRef
  override def compensationTask: Option[JobTask] = underlying.compensationTask
  override def componentName: Option[String] = underlying.componentName
  override def serviceName: Option[String] = underlying.serviceName
  override def operationName: Option[String] = underlying.operationName
  override def requestSummary: Option[String] = underlying.requestSummary
  override def requestParameters: Map[String, String] = underlying.requestParameters
  override def defaultPersistence: JobPersistencePolicy = underlying.defaultPersistence

  def run(context: ExecutionContext): TaskOutcome = {
    val executioncontext = _bind_context(context)
    attemptcapture._mark_task_execution_started()
    _attempt_context(executioncontext) match {
      case Some(attemptcontext) => _run_attempt(attemptcontext)
      case None => _run_canonical(executioncontext)
    }
  }

  override def observeCanonicalOutcome(
    outcome: TaskOutcome,
    context: ExecutionContext,
    cancelled: Boolean
  ): Unit = {
    val executioncontext = _bind_context(context)
    _task_id(executioncontext).flatMap(taskid => Option(_pending_terminals.remove(taskid))).foreach { pending =>
      _complete_terminal(pending, outcome, executioncontext, cancelled)
    }
  }

  override def observeAdmissionFailure(
    conclusion: Conclusion,
    context: ExecutionContext
  ): Unit =
    attemptcapture._record_admission_failure(conclusion, _bind_context(context))

  private def _bind_context(context: ExecutionContext): ExecutionContext =
    executionscope.map(context.withScope).getOrElse(context)

  private def _attempt_context(context: ExecutionContext): Option[ExecutionContext] =
    try {
      val prepared = context.operationEvaluation.invocation match {
        case Some(invocation) if invocation.operation == attemptcapture.operationidentity =>
          Consequence.success(context)
        case _ =>
          ExecutionContext.prepareOperationEvaluation(context, attemptcapture.operationidentity)
      }
      prepared.flatMap(ExecutionContext.beginOperationEvaluationAttempt).toOption
    } catch {
      case _: InterruptedException =>
        Thread.currentThread.interrupt()
        None
      case NonFatal(_) => None
    }

  private def _run_attempt(context: ExecutionContext): TaskOutcome = {
    val pending = _start_capture(context)
    val outcome = _run_canonical(context)
    pending.foreach(_stage_or_complete_terminal(_, outcome, context))
    outcome
  }

  private def _start_capture(context: ExecutionContext): Option[CaptureStart] =
    try {
      context.operationEvaluation.correlation.map { correlation =>
        val startedat = context.clock.instant()
        val start = OperationEvaluationStartFact.create(
          OperationEvaluationFactId.create("start", startedat, context.idGeneration),
          correlation,
          startedat
        )
        _deliver_fact(start, context)
        CaptureStart(startedat, correlation)
      }
    } catch {
      case _: InterruptedException =>
        Thread.currentThread.interrupt()
        None
      case NonFatal(_) => None
    }

  private def _stage_or_complete_terminal(
    start: CaptureStart,
    outcome: TaskOutcome,
    context: ExecutionContext
  ): Unit =
    try {
      val pending = PendingTerminal(start.startedat, context.clock.instant(), start.correlation)
      _task_id(context) match {
        case Some(taskid)
            if context.jobContext.jobId.nonEmpty &&
              context.jobContext.actionId.contains(actionId) =>
          _pending_terminals.put(taskid, pending)
        case _ =>
          val cancelled = context.jobContext.cancellationScope.exists(_.isCancelled)
          _complete_terminal(pending, outcome, context, cancelled)
      }
    } catch {
      case _: InterruptedException =>
        _discard_supplemental(context)
        Thread.currentThread.interrupt()
      case NonFatal(_) =>
        _discard_supplemental(context)
    }

  private def _complete_terminal(
    pending: PendingTerminal,
    outcome: TaskOutcome,
    context: ExecutionContext,
    cancelled: Boolean
  ): Unit =
    try {
      val (evaluationoutcome, diagnostic) = _evaluation_outcome(outcome, cancelled)
      val terminal = OperationEvaluationTerminalFact.createC(
        OperationEvaluationFactId.create("terminal", pending.completedat, context.idGeneration),
        pending.correlation,
        pending.completedat,
        evaluationoutcome,
        _non_negative_duration(pending.startedat, pending.completedat),
        diagnostic
      )
      val terminaldelivered = terminal match {
        case Consequence.Success(fact) =>
          _deliver_fact(fact, context)
          true
        case Consequence.Failure(_) =>
          false
      }
      if (evaluationoutcome == OperationEvaluationOutcome.Success && terminaldelivered)
        _release_supplemental(pending.correlation.attemptId, context)
      else
        context.runtime.unitOfWork
          .discardOperationEvaluationSupplemental(pending.correlation.attemptId)
    } catch {
      case _: InterruptedException =>
        context.runtime.unitOfWork
          .discardOperationEvaluationSupplemental(pending.correlation.attemptId)
        Thread.currentThread.interrupt()
      case NonFatal(_) =>
        context.runtime.unitOfWork
          .discardOperationEvaluationSupplemental(pending.correlation.attemptId)
    }

  private def _release_supplemental(
    attemptid: OperationEvaluationAttemptId,
    context: ExecutionContext
  ): Unit =
    context.runtime.unitOfWork
      .releaseCommittedOperationEvaluationSupplemental(attemptid)
      .foreach(intent => attemptcapture.deliveryruntime.deliverSupplemental(intent, context))

  private def _discard_supplemental(
    context: ExecutionContext
  ): Unit =
    context.operationEvaluation.correlation.foreach(correlation =>
      context.runtime.unitOfWork
        .discardOperationEvaluationSupplemental(correlation.attemptId)
    )

  private def _task_id(context: ExecutionContext): Option[TaskId] =
    context.jobContext.currentTask.orElse(context.jobContext.taskId)

  private def _deliver_fact(
    fact: OperationEvaluationFact,
    context: ExecutionContext
  ): Unit =
    try {
      val _ = attemptcapture.deliveryruntime.deliverAutomatic(fact, context)
    } catch {
      case _: InterruptedException => Thread.currentThread.interrupt()
      case NonFatal(_) => ()
    }

  private def _run_canonical(context: ExecutionContext): TaskOutcome =
    try {
      underlying.run(context) match {
        case TaskSucceeded(response) =>
          responsebinding(response, context) match {
            case Consequence.Success(bound) => TaskSucceeded(bound)
            case Consequence.Failure(conclusion) => TaskFailed(conclusion)
          }
        case failure: TaskFailed => failure
      }
    } catch {
      case NonFatal(e) => TaskFailed(Conclusion.from(e))
    }

  private def _evaluation_outcome(
    outcome: TaskOutcome,
    cancelled: Boolean
  ): (OperationEvaluationOutcome, Option[ConclusionDiagnostics.Classification]) = {
    if (cancelled)
      OperationEvaluationOutcome.Cancellation -> _diagnostic(outcome)
    else
      outcome match {
        case _: TaskSucceeded => OperationEvaluationOutcome.Success -> None
        case TaskFailed(conclusion) =>
          val diagnostic = ConclusionDiagnostics.classify(conclusion)
          val kind =
            if (diagnostic.causeKind.exists(_.equalsIgnoreCase("Timeout")))
              OperationEvaluationOutcome.Timeout
            else
              OperationEvaluationOutcome.Failure
          kind -> Some(diagnostic)
      }
  }

  private def _diagnostic(
    outcome: TaskOutcome
  ): Option[ConclusionDiagnostics.Classification] =
    outcome match {
      case TaskFailed(conclusion) => Some(ConclusionDiagnostics.classify(conclusion))
      case _: TaskSucceeded => None
    }

  private def _non_negative_duration(startedat: java.time.Instant, completedat: java.time.Instant): Duration = {
    val duration = Duration.between(startedat, completedat)
    if (duration.isNegative) Duration.ZERO else duration
  }
}

private[cncf] object OperationEvaluationActionTask {
  private final case class CaptureStart(
    startedat: java.time.Instant,
    correlation: OperationEvaluationCorrelation
  )

  private final case class PendingTerminal(
    startedat: java.time.Instant,
    completedat: java.time.Instant,
    correlation: OperationEvaluationCorrelation
  )
}

/*
 * Coordinates failures that occur after authorization but before a JobTask
 * reaches its execution boundary. Actual task runs retain independent attempt
 * identities so Job retries remain observable as separate attempts.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final class OperationEvaluationAttemptCapture(
  val operationidentity: OperationEvaluationOperationIdentity,
  val deliveryruntime: OperationEvaluationDeliveryRuntime
) {
  private val _admission_state = new AtomicInteger(OperationEvaluationAttemptCapture.PENDING)

  private[evaluation] def _mark_task_execution_started(): Unit = {
    val _ = _admission_state.compareAndSet(
      OperationEvaluationAttemptCapture.PENDING,
      OperationEvaluationAttemptCapture.TASK_EXECUTION_STARTED
    )
  }

  private[cncf] def _record_admission_failure(
    conclusion: Conclusion,
    context: ExecutionContext
  ): Unit =
    if (_admission_state.compareAndSet(
      OperationEvaluationAttemptCapture.PENDING,
      OperationEvaluationAttemptCapture.ADMISSION_FAILURE_RECORDED
    ))
      _record_failure(conclusion, context)

  private def _record_failure(
    conclusion: Conclusion,
    context: ExecutionContext
  ): Unit =
    try {
      val prepared = context.operationEvaluation.invocation match {
        case Some(invocation) if invocation.operation == operationidentity =>
          Consequence.success(context)
        case _ =>
          ExecutionContext.prepareOperationEvaluation(context, operationidentity)
      }
      prepared.flatMap(ExecutionContext.beginOperationEvaluationAttempt).toOption.foreach { attempted =>
        attempted.operationEvaluation.correlation.foreach { correlation =>
          val startedat = attempted.clock.instant()
          val start = OperationEvaluationStartFact.create(
            OperationEvaluationFactId.create("admission-failure.start", startedat, attempted.idGeneration),
            correlation,
            startedat
          )
          val _ = deliveryruntime.deliverAutomatic(start, attempted)
          val completedat = attempted.clock.instant()
          val diagnostic = ConclusionDiagnostics.classify(conclusion)
          val outcome =
            if (diagnostic.causeKind.exists(_.equalsIgnoreCase("Timeout")))
              OperationEvaluationOutcome.Timeout
            else
              OperationEvaluationOutcome.Failure
          OperationEvaluationTerminalFact.createC(
            OperationEvaluationFactId.create("admission-failure.terminal", completedat, attempted.idGeneration),
            correlation,
            completedat,
            outcome,
            _non_negative_duration(startedat, completedat),
            Some(diagnostic)
          ).toOption.foreach(fact => deliveryruntime.deliverAutomatic(fact, attempted))
        }
      }
    } catch {
      case _: InterruptedException => Thread.currentThread.interrupt()
      case NonFatal(_) => ()
    }

  private def _non_negative_duration(
    startedat: java.time.Instant,
    completedat: java.time.Instant
  ): Duration = {
    val duration = Duration.between(startedat, completedat)
    if (duration.isNegative) Duration.ZERO else duration
  }
}

private object OperationEvaluationAttemptCapture {
  private val PENDING = 0
  private val TASK_EXECUTION_STARTED = 1
  private val ADMISSION_FAILURE_RECORDED = 2
}
