package org.goldenport.cncf.job

import org.goldenport.Consequence

/*
 * Per-invocation addressing for independently supplied closed lifecycle evidence.
 * These values are private protocol envelopes, not signed record schema fields.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobKeyedEvidenceRequest(
  jobid: JobId,
  expectedrevision: Long,
  request: DurableJobLifecycleWriteRequest
)

private[job] final case class DurableJobKeyedEvidenceResponse(
  jobid: JobId,
  expectedrevision: Long,
  evidence: DurableJobLifecycleWriteEvidence
)

private[job] trait DurableJobKeyedEvidenceSource
    extends Function1[DurableJobLifecycleWriteRequest, Consequence[DurableJobLifecycleWriteEvidence]] {
  def resolve(request: DurableJobKeyedEvidenceRequest): Consequence[DurableJobKeyedEvidenceResponse]

  final override def apply(request: DurableJobLifecycleWriteRequest): Consequence[DurableJobLifecycleWriteEvidence] =
    DurableJobKeyedEvidence.refused
}

private[job] object DurableJobKeyedEvidence {
  def refused[A]: Consequence[A] =
    Consequence.argumentInvalid("durable keyed lifecycle evidence refused")

  def fetch(
    jobId: JobId,
    expectedRevision: Long,
    request: DurableJobLifecycleWriteRequest,
    source: DurableJobKeyedEvidenceSource
  ): Consequence[DurableJobLifecycleWriteEvidence] =
    if (source == null || jobId == null || request == null || expectedRevision <= 0L ||
        !DurableJobStorageResourcePolicy.validId(jobId.value))
      refused
    else source.resolve(DurableJobKeyedEvidenceRequest(jobId, expectedRevision, request)) match {
      case null => refused
      case failure @ Consequence.Failure(_) =>
        failure.asInstanceOf[Consequence[DurableJobLifecycleWriteEvidence]]
      case Consequence.Success(response) =>
        if (response == null || response.jobid == null || response.jobid.value != jobId.value ||
            response.expectedrevision != expectedRevision)
          refused
        else Consequence.success(response.evidence)
    }
}
