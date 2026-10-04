package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import scala.util.control.NonFatal

/*
 * Package-internal, provider-owned startup recovery.  Candidate discovery is
 * deliberately not an EntityStore/DataStore concern: the source supplies one
 * deterministic, bounded sequence of identities and the closed evidence
 * necessary to admit and classify each identity.
 *
 * @since   Sep.  9, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobStartupRecoveryCandidate(
  ordinal: Long,
  jobId: String,
  access: DurableJobRecordAccess,
  replayEvidence: DurableReplayEvidence
)

private[job] trait DurableJobStartupRecoverySource {
  def candidates(
    maxCandidates: Int
  ): Consequence[Vector[DurableJobStartupRecoveryCandidate]]
}

/*
 * These are closed startup facts.  They intentionally retain neither a
 * durable record nor a provider failure/body, and never carry a live task,
 * resolver, execution context, replay closure, or raw payload/result.
 */
private[job] enum DurableJobStartupRecoveryFact {
  case Decision(value: DurableJobRecoveryDecision)
  case Missing
  case Corrupt
  case Refused
}

private[job] final case class DurableJobStartupRecoveryCandidateReport(
  ordinal: Long,
  jobId: String,
  fact: DurableJobStartupRecoveryFact
)

private[job] final case class DurableJobStartupRecoveryReport(
  candidates: Vector[DurableJobStartupRecoveryCandidateReport]
)

private[job] final class DurableJobStartupRecoveryCoordinator(
  source: DurableJobStartupRecoverySource,
  store: DurableJobStore
) {
  private[job] def _admitted_candidates_c(
    maxcandidates: Int
  ): Consequence[Vector[DurableJobStartupRecoveryCandidate]] =
    _source_candidates(maxcandidates)

  def recover(
    maxCandidates: Int
  )(using ctx: ExecutionContext): Consequence[DurableJobStartupRecoveryReport] =
    _source_candidates(maxCandidates).map {
        candidates =>
          DurableJobStartupRecoveryReport(candidates.map(_recover_candidate))
      }

  def recoverRuntime(
    maxCandidates: Int,
    port: DurableJobRuntimeRehydrationPort
  )(
    register: (
      DurableJobStartupRecoveryCandidate,
      DurableJobStoreSnapshot,
      DurableJobRecoveryDecision,
      DurableJobRuntimeRehydration
    ) => DurableJobRuntimeRehydrationFact
  )(using ctx: ExecutionContext): Consequence[DurableJobRuntimeRehydrationReport] =
    if (port == null || register == null)
      _batch_refused
    else
      _source_candidates(maxCandidates).map {
        candidates =>
          DurableJobRuntimeRehydrationReport(
            candidates.map(_recover_runtime_candidate(_, port, register))
          )
      }

  def recoverTerminal(
    maxCandidates: Int
  )(
    register: (
      DurableJobStartupRecoveryCandidate,
      DurableJobStoreSnapshot,
      DurableJobRecoveryDecision,
      DurableJobTerminalProjection
    ) => DurableJobTerminalProjectionFact
  )(using ctx: ExecutionContext): Consequence[DurableJobTerminalProjectionReport] =
    if (register == null)
      _batch_refused
    else
      _source_candidates(maxCandidates).map {
        candidates =>
          DurableJobTerminalProjectionReport(
            candidates.map(_recover_terminal_candidate(_, register))
          )
      }

  private def _source_candidates(
    maxcandidates: Int
  ): Consequence[Vector[DurableJobStartupRecoveryCandidate]] =
    if (maxcandidates < 1 || maxcandidates > 1024 || source == null || store == null)
      _batch_refused
    else {
      val discovered = try {
        source.candidates(maxcandidates)
      } catch {
        case NonFatal(_) => _batch_refused[Vector[DurableJobStartupRecoveryCandidate]]
      }
      discovered match {
        case Consequence.Success(candidates) => _validate(candidates, maxcandidates)
        case _ => _batch_refused
      }
    }

  private def _batch_refused[A]: Consequence[A] =
    Consequence.argumentInvalid("Durable Job startup recovery batch refused")

  private def _validate(
    candidates: Vector[DurableJobStartupRecoveryCandidate],
    maxcandidates: Int
  ): Consequence[Vector[DurableJobStartupRecoveryCandidate]] =
    if (candidates == null || candidates.size > maxcandidates ||
        candidates.exists(candidate => !_candidate_shape(candidate)))
      _batch_refused
    else if (_duplicate_id(candidates).nonEmpty || _unstable_candidate(candidates).nonEmpty)
      _batch_refused
    else
      Consequence.success(candidates)

  private def _candidate_shape(candidate: DurableJobStartupRecoveryCandidate): Boolean =
    candidate != null && _nonblank(candidate.jobId) &&
      _access_shape(candidate.access) && _evidence_shape(candidate.replayEvidence)

  private def _nonblank(value: String): Boolean =
    value != null && value.trim.nonEmpty

  private def _access_shape(access: DurableJobRecordAccess): Boolean =
    access != null && _nonblank(access.tenantId) && _nonblank(access.subjectId) &&
      access.scopes != null && access.scopes.forall(_nonblank)

  private def _evidence_shape(evidence: DurableReplayEvidence): Boolean =
    evidence != null && evidence.idempotency != null && evidence.input != null &&
      evidence.definition != null && evidence.authorization != null &&
      evidence.provider != null && evidence.compatibility != null

  private def _duplicate_id(
    candidates: Vector[DurableJobStartupRecoveryCandidate]
  ): Option[String] =
    candidates
      .groupBy(_.jobId)
      .collectFirst { case (id, occurrences) if occurrences.size > 1 => id }

  private def _unstable_candidate(
    candidates: Vector[DurableJobStartupRecoveryCandidate]
  ): Option[DurableJobStartupRecoveryCandidate] =
    candidates.zipWithIndex.collectFirst {
      case (candidate, index) if candidate.ordinal != index.toLong => candidate
    }

  private def _recover_candidate(
    candidate: DurableJobStartupRecoveryCandidate
  )(using ctx: ExecutionContext): DurableJobStartupRecoveryCandidateReport =
    DurableJobStartupRecoveryCandidateReport(
      candidate.ordinal,
      candidate.jobId,
      _recovery_fact(candidate)
    )

  private def _recovery_fact(
    candidate: DurableJobStartupRecoveryCandidate
  )(using ctx: ExecutionContext): DurableJobStartupRecoveryFact =
    store.loadStartupRecovery(candidate.jobId, candidate.access) match {
      case DurableJobStoreStartupRecoveryLoad.Admitted(snapshot) =>
        DurableJobRecovery.classify(snapshot.record, candidate.replayEvidence) match {
          case Consequence.Success(decision) =>
            DurableJobStartupRecoveryFact.Decision(decision)
          case Consequence.Failure(_) => DurableJobStartupRecoveryFact.Corrupt
        }
      case DurableJobStoreStartupRecoveryLoad.Missing =>
        DurableJobStartupRecoveryFact.Missing
      case DurableJobStoreStartupRecoveryLoad.Corrupt =>
        DurableJobStartupRecoveryFact.Corrupt
      case DurableJobStoreStartupRecoveryLoad.Refused =>
        DurableJobStartupRecoveryFact.Refused
    }

  private def _recover_runtime_candidate(
    candidate: DurableJobStartupRecoveryCandidate,
    port: DurableJobRuntimeRehydrationPort,
    register: (
      DurableJobStartupRecoveryCandidate,
      DurableJobStoreSnapshot,
      DurableJobRecoveryDecision,
      DurableJobRuntimeRehydration
    ) => DurableJobRuntimeRehydrationFact
  )(using ctx: ExecutionContext): DurableJobRuntimeRehydrationCandidateReport =
    DurableJobRuntimeRehydrationCandidateReport(
      candidate.ordinal,
      candidate.jobId,
      _runtime_rehydration_fact(candidate, port, register)
    )

  private def _runtime_rehydration_fact(
    candidate: DurableJobStartupRecoveryCandidate,
    port: DurableJobRuntimeRehydrationPort,
    register: (
      DurableJobStartupRecoveryCandidate,
      DurableJobStoreSnapshot,
      DurableJobRecoveryDecision,
      DurableJobRuntimeRehydration
    ) => DurableJobRuntimeRehydrationFact
  )(using ctx: ExecutionContext): DurableJobRuntimeRehydrationFact =
    store.loadStartupRecovery(candidate.jobId, candidate.access) match {
      case DurableJobStoreStartupRecoveryLoad.Admitted(snapshot) =>
        DurableJobRecovery.classify(snapshot.record, candidate.replayEvidence) match {
          case Consequence.Success(decision) if _is_runtime_rehydratable(snapshot, decision) =>
            _port_rehydration_fact(candidate, snapshot, decision, port, register)
          case _ => DurableJobRuntimeRehydrationFact.Refused
        }
      case _ => DurableJobRuntimeRehydrationFact.Refused
    }

  private def _recover_terminal_candidate(
    candidate: DurableJobStartupRecoveryCandidate,
    register: (
      DurableJobStartupRecoveryCandidate,
      DurableJobStoreSnapshot,
      DurableJobRecoveryDecision,
      DurableJobTerminalProjection
    ) => DurableJobTerminalProjectionFact
  )(using ctx: ExecutionContext): DurableJobTerminalProjectionCandidateReport =
    DurableJobTerminalProjectionCandidateReport(
      candidate.ordinal,
      candidate.jobId,
      _terminal_projection_fact(candidate, register)
    )

  private def _terminal_projection_fact(
    candidate: DurableJobStartupRecoveryCandidate,
    register: (
      DurableJobStartupRecoveryCandidate,
      DurableJobStoreSnapshot,
      DurableJobRecoveryDecision,
      DurableJobTerminalProjection
    ) => DurableJobTerminalProjectionFact
  )(using ctx: ExecutionContext): DurableJobTerminalProjectionFact =
    store.loadStartupRecovery(candidate.jobId, candidate.access) match {
      case DurableJobStoreStartupRecoveryLoad.Admitted(snapshot) =>
        DurableJobRecovery.classify(snapshot.record, candidate.replayEvidence) match {
          case Consequence.Success(decision) if _is_terminal_projection(decision) =>
            DurableJobTerminalProjection.project(candidate, snapshot, decision) match {
              case Consequence.Success(projection) => register(candidate, snapshot, decision, projection)
              case Consequence.Failure(_) => DurableJobTerminalProjectionFact.Refused
            }
          case Consequence.Success(_) =>
            DurableJobTerminalProjectionFact.Refused
          case Consequence.Failure(_) => DurableJobTerminalProjectionFact.Corrupt
        }
      case DurableJobStoreStartupRecoveryLoad.Missing => DurableJobTerminalProjectionFact.Missing
      case DurableJobStoreStartupRecoveryLoad.Corrupt => DurableJobTerminalProjectionFact.Corrupt
      case DurableJobStoreStartupRecoveryLoad.Refused => DurableJobTerminalProjectionFact.Refused
    }

  private def _is_terminal_projection(
    decision: DurableJobRecoveryDecision
  ): Boolean =
    decision.outcome match {
      case DurableJobRecoveryOutcome.TerminalSucceeded |
          DurableJobRecoveryOutcome.TerminalFailed |
          DurableJobRecoveryOutcome.TerminalCancelled => true
      case _ => false
    }

  private def _is_runtime_rehydratable(
    snapshot: DurableJobStoreSnapshot,
    decision: DurableJobRecoveryDecision
  ): Boolean = {
    val lifecycle = snapshot.record.body.lifecycle
    val unstarted =
      snapshot.record.format == DurableRecordFormat.V2 &&
        lifecycle.status == DurableJobLifecycleStatus.Submitted &&
        lifecycle.schedule.startedAt.isEmpty &&
        !lifecycle.retry.recoveryRequired &&
        decision.failedEvidenceCategories.isEmpty
    decision.outcome match {
      case DurableJobRecoveryOutcome.Resumable =>
        unstarted && lifecycle.retry.nextRetryAt.isEmpty
      case DurableJobRecoveryOutcome.Retryable =>
        unstarted && lifecycle.retry.nextRetryAt.nonEmpty
      case _ => false
    }
  }

  private def _port_rehydration_fact(
    candidate: DurableJobStartupRecoveryCandidate,
    snapshot: DurableJobStoreSnapshot,
    decision: DurableJobRecoveryDecision,
    port: DurableJobRuntimeRehydrationPort,
    register: (
      DurableJobStartupRecoveryCandidate,
      DurableJobStoreSnapshot,
      DurableJobRecoveryDecision,
      DurableJobRuntimeRehydration
    ) => DurableJobRuntimeRehydrationFact
  ): DurableJobRuntimeRehydrationFact =
    try {
      port.rehydrate(candidate, snapshot, decision) match {
        case Consequence.Success(rehydration) =>
          Option(rehydration)
            .map(register(candidate, snapshot, decision, _))
            .getOrElse(DurableJobRuntimeRehydrationFact.Refused)
        case Consequence.Failure(_) => DurableJobRuntimeRehydrationFact.Refused
      }
    } catch {
      case NonFatal(_) => DurableJobRuntimeRehydrationFact.Refused
    }
}
