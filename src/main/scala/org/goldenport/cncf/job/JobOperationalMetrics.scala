package org.goldenport.cncf.job

import org.goldenport.cncf.metrics.RuntimeMetricPoint

/*
 * @since   Oct. 3, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class JobOperationalMetrics {
  private val _counts = Array.fill[Long](56)(0L)

  def observe(name: String, persistence: JobPersistencePolicy, runMode: JobRunMode): Unit =
    synchronized {
      val factindex = JobOperationalMetrics._facts.indexWhere(_.name == name)
      val persistenceindex = persistence match {
        case JobPersistencePolicy.Persistent => 0
        case JobPersistencePolicy.Ephemeral => 1
        case null => -1
      }
      val modeindex = runMode match {
        case JobRunMode.Sync => 0
        case JobRunMode.Async => 1
        case null => -1
      }
      if (factindex >= 0 && persistenceindex >= 0 && modeindex >= 0) {
        val cell = factindex * 4 + persistenceindex * 2 + modeindex
        _counts(cell) = JobOperationalMetrics.saturatingIncrement(_counts(cell))
      }
    }

  def snapshot(): Vector[RuntimeMetricPoint] = synchronized {
    _counts.indices.iterator.filter(_counts(_) > 0L).map { cell =>
      val fact = JobOperationalMetrics._facts(cell / 4)
      val count = _counts(cell)
      RuntimeMetricPoint(
        scope = "job.lifecycle",
        name = "facts",
        labels = Map(
          "family" -> fact.family,
          "operation" -> fact.operation,
          "outcome" -> fact.outcome,
          "persistence" -> (if ((cell % 4) / 2 == 0) "persistent" else "ephemeral"),
          "run_mode" -> (if (cell % 2 == 0) "sync" else "async")
        ),
        count = count,
        errorCount = if (fact.error) count else 0L
      )
    }.toVector
  }
}

private[job] object JobOperationalMetrics {
  private final case class Fact(
    name: String,
    family: String,
    operation: String,
    outcome: String,
    error: Boolean = false
  )

  private val _facts = Vector(
    Fact("job.submitted", "admission", "submit", "accepted"),
    Fact("job.running", "execution", "run", "started"),
    Fact("job.succeeded", "execution", "run", "succeeded"),
    Fact("job.failed", "execution", "run", "failed", error = true),
    Fact("job.delayed.scheduled", "queue", "delay", "scheduled"),
    Fact("job.delayed.enqueued", "queue", "delay", "enqueued"),
    Fact("job.retry.immediate.submitted", "retry", "immediate", "submitted"),
    Fact("job.retry.delayed.scheduled", "retry", "delayed", "scheduled"),
    Fact("job.retry.delayed.submitted", "retry", "delayed", "submitted"),
    Fact("job.retry.submitted", "retry", "control", "submitted"),
    Fact("job.recovery-required", "recovery", "checkpoint", "required", error = true),
    Fact("job.cancelled", "control", "cancel", "applied"),
    Fact("job.suspended", "control", "suspend", "applied"),
    Fact("job.resumed", "control", "resume", "applied")
  )

  def saturatingIncrement(value: Long): Long =
    if (value == Long.MaxValue) Long.MaxValue else value + 1L
}
