package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Provider-owned, process-local reconstruction of a runtime job.  This is
 * deliberately not a persistence format: the result may contain live task
 * and execution-context values, but neither it nor the port is retained by
 * durable recovery reports.
 *
 * @since   Sep.  9, 2026
 * @version Sep.  9, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobRuntimeRehydration(
  jobId: JobId,
  tasks: List[JobTask],
  context: ExecutionContext,
  input: Option[JobInput],
  definitionSnapshot: Option[JobDefinitionSnapshot]
)

/*
 * The caller explicitly supplies one already admitted candidate, canonical
 * durable snapshot, and allowed classification.  There is no registry or
 * framework-global provider selection at this boundary.
 */
private[job] trait DurableJobRuntimeRehydrationPort {
  def rehydrate(
    candidate: DurableJobStartupRecoveryCandidate,
    snapshot: DurableJobStoreSnapshot,
    decision: DurableJobRecoveryDecision
  ): Consequence[DurableJobRuntimeRehydration]
}

/* Runtime registration facts retain no durable body, port result, or failure. */
private[job] enum DurableJobRuntimeRehydrationFact {
  case Registered
  case Refused
}

private[job] final case class DurableJobRuntimeRehydrationCandidateReport(
  ordinal: Long,
  jobId: String,
  fact: DurableJobRuntimeRehydrationFact
)

private[job] final case class DurableJobRuntimeRehydrationReport(
  candidates: Vector[DurableJobRuntimeRehydrationCandidateReport]
)
