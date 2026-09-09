package org.goldenport.cncf.job

import org.goldenport.Consequence

/*
 * Pure, fail-closed classification for an already authorized durable record.
 *
 * The classifier consumes only the closed durable record and its six opaque
 * replay-proof tokens.  It neither resolves a descriptor nor reaches a
 * provider, scheduler, UnitOfWork, task, clock, thread, or external effect.
 * A later recovery owner may use this classification as input, but must not
 * treat it as permission to reconstruct or execute live work.
 *
 * @since   Sep.  9, 2026
 * @version Sep.  9, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] enum DurableJobRecoveryOutcome(val wire: String) {
  case TerminalSucceeded extends DurableJobRecoveryOutcome("terminal-succeeded")
  case TerminalFailed extends DurableJobRecoveryOutcome("terminal-failed")
  case TerminalCancelled extends DurableJobRecoveryOutcome("terminal-cancelled")
  case Resumable extends DurableJobRecoveryOutcome("resumable")
  case Retryable extends DurableJobRecoveryOutcome("retryable")
  case RecoveryRequired extends DurableJobRecoveryOutcome("recovery-required")
}

/*
 * Reasons are closed facts about the record and assessment; they never carry
 * a task, action, provider, credential, payload body, or arbitrary object.
 */
private[job] enum DurableJobRecoveryReason(val wire: String) {
  case TerminalSucceeded extends DurableJobRecoveryReason("terminal-succeeded")
  case TerminalFailed extends DurableJobRecoveryReason("terminal-failed")
  case TerminalCancelled extends DurableJobRecoveryReason("terminal-cancelled")
  case SubmittedWithoutStart extends DurableJobRecoveryReason("submitted-without-start")
  case DelayedRetry extends DurableJobRecoveryReason("delayed-retry")
  case ReplayEvidenceIncomplete extends DurableJobRecoveryReason("replay-evidence-incomplete")
  case RecoveryRequiredFlag extends DurableJobRecoveryReason("recovery-required-flag")
  case ActiveLifecycle extends DurableJobRecoveryReason("active-lifecycle")
  case ExecutionAlreadyStarted extends DurableJobRecoveryReason("execution-already-started")
}

/*
 * `failedEvidenceCategories` is populated only when otherwise eligible work
 * was refused because one or more of the six replay proofs was missing or
 * blank.  Its order is the DurableReplayAssessment category order.
 */
private[job] final case class DurableJobRecoveryDecision(
  outcome: DurableJobRecoveryOutcome,
  reasons: Vector[DurableJobRecoveryReason],
  failedEvidenceCategories: Vector[DurableReplayEvidenceCategory]
)

private[job] object DurableJobRecovery {
  def classify(
    record: DurableJobRecord,
    replayEvidence: DurableReplayEvidence
  ): Consequence[DurableJobRecoveryDecision] =
    _validate_lifecycle_result(record) match {
      case Left(message) => Consequence.argumentInvalid(s"durable recovery refused: $message")
      case Right(_) => Consequence.success(_classify(record, replayEvidence))
    }

  private def _classify(
    record: DurableJobRecord,
    replayEvidence: DurableReplayEvidence
  ): DurableJobRecoveryDecision = {
    val lifecycle = record.body.lifecycle
    lifecycle.status match {
      case DurableJobLifecycleStatus.Succeeded =>
        _decision(
          DurableJobRecoveryOutcome.TerminalSucceeded,
          DurableJobRecoveryReason.TerminalSucceeded
        )
      case DurableJobLifecycleStatus.Failed =>
        _decision(
          DurableJobRecoveryOutcome.TerminalFailed,
          DurableJobRecoveryReason.TerminalFailed
        )
      case DurableJobLifecycleStatus.Cancelled =>
        _decision(
          DurableJobRecoveryOutcome.TerminalCancelled,
          DurableJobRecoveryReason.TerminalCancelled
        )
      case DurableJobLifecycleStatus.Submitted |
          DurableJobLifecycleStatus.Running |
          DurableJobLifecycleStatus.Suspended =>
        _unsafe_recovery_reasons(lifecycle) match {
          case nonempty if nonempty.nonEmpty =>
            DurableJobRecoveryDecision(
              DurableJobRecoveryOutcome.RecoveryRequired,
              nonempty,
              Vector.empty
            )
          case _ => _eligible_submitted_decision(lifecycle, replayEvidence)
        }
    }
  }

  private def _eligible_submitted_decision(
    lifecycle: DurableJobLifecycle,
    replayEvidence: DurableReplayEvidence
  ): DurableJobRecoveryDecision =
    DurableReplayAssessment.assess(replayEvidence) match {
      case DurableReplayDecision.Allowed =>
        lifecycle.retry.nextRetryAt match {
          case Some(_) =>
            _decision(
              DurableJobRecoveryOutcome.Retryable,
              DurableJobRecoveryReason.SubmittedWithoutStart,
              DurableJobRecoveryReason.DelayedRetry
            )
          case None =>
            _decision(
              DurableJobRecoveryOutcome.Resumable,
              DurableJobRecoveryReason.SubmittedWithoutStart
            )
        }
      case DurableReplayDecision.Refused(failedcategories, _) =>
        DurableJobRecoveryDecision(
          DurableJobRecoveryOutcome.RecoveryRequired,
          Vector(DurableJobRecoveryReason.ReplayEvidenceIncomplete),
          failedcategories
        )
    }

  private def _unsafe_recovery_reasons(
    lifecycle: DurableJobLifecycle
  ): Vector[DurableJobRecoveryReason] =
    Vector(
      Option.when(lifecycle.retry.recoveryRequired)(DurableJobRecoveryReason.RecoveryRequiredFlag),
      Option.when(
        lifecycle.status == DurableJobLifecycleStatus.Running ||
          lifecycle.status == DurableJobLifecycleStatus.Suspended
      )(DurableJobRecoveryReason.ActiveLifecycle),
      Option.when(lifecycle.schedule.startedAt.nonEmpty)(DurableJobRecoveryReason.ExecutionAlreadyStarted)
    ).flatten

  private def _validate_lifecycle_result(record: DurableJobRecord): Either[String, Unit] =
    (record.format, record.body.lifecycle.status, record.body.result) match {
      case (DurableRecordFormat.V1 | DurableRecordFormat.V2, DurableJobLifecycleStatus.Succeeded, DurableResultOutcome.Succeeded(_)) =>
        Right(())
      case (DurableRecordFormat.V1 | DurableRecordFormat.V2, DurableJobLifecycleStatus.Failed, DurableResultOutcome.Failed(_)) =>
        Right(())
      case (DurableRecordFormat.V1 | DurableRecordFormat.V2, DurableJobLifecycleStatus.Cancelled, DurableResultOutcome.Cancelled(_)) =>
        Right(())
      case (DurableRecordFormat.V2, DurableJobLifecycleStatus.Submitted | DurableJobLifecycleStatus.Running | DurableJobLifecycleStatus.Suspended, DurableResultOutcome.Pending) =>
        Right(())
      case (DurableRecordFormat.V1, DurableJobLifecycleStatus.Submitted | DurableJobLifecycleStatus.Running | DurableJobLifecycleStatus.Suspended, _) =>
        Left("v1 durable records cannot represent a non-terminal recovery lifecycle")
      case (_, status, _) =>
        Left(s"durable lifecycle/result combination is structurally impossible: ${status.wire}")
    }

  private def _decision(
    outcome: DurableJobRecoveryOutcome,
    first: DurableJobRecoveryReason,
    rest: DurableJobRecoveryReason*
  ): DurableJobRecoveryDecision =
    DurableJobRecoveryDecision(outcome, first +: rest.toVector, Vector.empty)
}
