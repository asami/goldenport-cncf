package org.goldenport.cncf.event

import org.goldenport.cncf.job.{JobEngine, JobId}

/*
 * @since   Apr. 21, 2026
 * @version Apr. 21, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object EventAwaitSupport {
  def awaitVisible(
    probe: => Boolean,
    timeoutMillis: Long = 3000L,
    pollMillis: Long = 10L
  ): Boolean = {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var done = probe
    while (!done && System.currentTimeMillis() < deadline) {
      Thread.sleep(pollMillis)
      done = probe
    }
    done
  }

  def awaitJobVisible(
    engine: JobEngine,
    jobId: JobId,
    timeoutMillis: Long = 3000L,
    pollMillis: Long = 10L
  ): Boolean =
    awaitVisible(
      probe = engine.query(jobId).isDefined,
      timeoutMillis = timeoutMillis,
      pollMillis = pollMillis
    )
}
