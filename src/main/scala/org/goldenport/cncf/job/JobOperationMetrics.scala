package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.metrics.RuntimeMetricPoint

/*
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class JobOperationMetrics {
  private val _counts = Array.fill[Long](11)(0L)

  def observeSubmission[A](result: Consequence[A]): Consequence[A] = {
    result match {
      case Consequence.Success(_) => _record(0)
      case Consequence.Failure(_) => _record(1)
    }
    result
  }

  def observeControl(result: Consequence[JobControlResponse]): Consequence[JobControlResponse] = {
    result match {
      case Consequence.Success(response) => _record(if (response.changed) 2 else 3)
      case Consequence.Failure(_) => _record(4)
    }
    result
  }

  def observePage[A](operation: JobOperationMetrics.PageOperation, result: Consequence[A]): Consequence[A] = {
    val offset = operation match {
      case JobOperationMetrics.PageOperation.Page => 5
      case JobOperationMetrics.PageOperation.Tasks => 7
      case JobOperationMetrics.PageOperation.Timeline => 9
      case null => -1
    }
    if (offset >= 0) result match {
      case Consequence.Success(_) => _record(offset)
      case Consequence.Failure(_) => _record(offset + 1)
    }
    result
  }

  private def _record(index: Int): Unit = synchronized {
    _counts(index) = JobOperationalMetrics.saturatingIncrement(_counts(index))
  }

  def snapshot(): Vector[RuntimeMetricPoint] = synchronized {
    _counts.indices.iterator.filter(_counts(_) > 0L).map { index =>
      val (operation, outcome) = JobOperationMetrics._cells(index)
      val count = _counts(index)
      RuntimeMetricPoint("job.operation", "calls", Map("operation" -> operation, "outcome" -> outcome),
        count, errorCount = if (outcome == "failed") count else 0L)
    }.toVector
  }
}

private[job] object JobOperationMetrics {
  enum PageOperation {
    case Page, Tasks, Timeline
  }

  private val _cells = Vector(
    "submit" -> "accepted", "submit" -> "failed",
    "control" -> "applied", "control" -> "replayed", "control" -> "failed",
    "page" -> "returned", "page" -> "failed",
    "tasks" -> "returned", "tasks" -> "failed",
    "timeline" -> "returned", "timeline" -> "failed"
  )
}
