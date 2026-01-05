package org.goldenport.cncf.job

import org.goldenport.cncf.event.EventTypeId
import org.goldenport.cncf.log.journal.JobEventLogEntry

/*
 * @since   Jan.  6, 2026
 * @version Jan.  6, 2026
 * @author  ASAMI, Tomoharu
 */
trait JobStateProjector {
  def project(plan: JobPlan, entries: Seq[JobEventLogEntry]): JobState
}

final class InMemoryJobStateProjector extends JobStateProjector {
  def project(plan: JobPlan, entries: Seq[JobEventLogEntry]): JobState = {
    val relevant = entries.filter(_.jobId.contains(plan.jobId))
    val allsatisfied = plan.expected.forall { expected =>
      relevant.exists(e => _matches(plan.jobId, expected, e))
    }
    if (allsatisfied) JobState.Done else JobState.Running
  }

  private def _matches(
    jobid: JobId,
    expected: ExpectedEvent,
    entry: JobEventLogEntry
  ): Boolean = {
    val jobok = entry.jobId.contains(jobid)
    val typeok = entry.eventType == expected.matcher.eventType
    val taskok = expected.taskId.forall(t => entry.taskId.contains(t))
    jobok && typeok && taskok
  }
}

sealed trait JobState

object JobState {
  case object Running extends JobState
  case object Done extends JobState
  case object Failed extends JobState
}
