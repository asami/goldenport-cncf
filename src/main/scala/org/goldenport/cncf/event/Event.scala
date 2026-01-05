package org.goldenport.cncf.event

import org.goldenport.cncf.job.{JobId, TaskId}

/*
 * @since   Apr. 11, 2025
 * @version Apr. 11, 2025
 * @author  ASAMI, Tomoharu
 */
trait Event {
  def jobId: Option[JobId] = None
  def taskId: Option[TaskId] = None
}
