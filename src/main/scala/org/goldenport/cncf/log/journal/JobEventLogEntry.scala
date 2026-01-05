package org.goldenport.cncf.log.journal

import java.time.Instant
import org.goldenport.cncf.context.ExecutionContextId
import org.goldenport.cncf.event.{EventId, EventTypeId}
import org.goldenport.cncf.job.{JobId, TaskId}

/*
 * @since   Jan.  6, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class JobEventLogEntry(
  eventId: EventId,
  eventType: EventTypeId,
  occurredAt: Instant,
  jobId: Option[JobId],
  taskId: Option[TaskId],
  executionId: Option[ExecutionContextId],
  traceId: Option[String],
  spanId: Option[String],
  attributes: Map[String, Any],
  receivedAt: Instant
)
