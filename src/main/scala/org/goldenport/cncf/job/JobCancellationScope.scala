package org.goldenport.cncf.job

import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Job-owned cancellation coordination for active runtime work.
 *
 * A Job engine owns the scope lifecycle. Runtime effects may register a
 * cancellation callback while executing under that Job, without making the
 * Job engine depend on the effect's SPI contract.
 */
final class JobCancellationScope {
  private var _cancelled = false
  private var _next_registration_id = 0L
  private var _callbacks = Map.empty[Long, () => Unit]

  def isCancelled: Boolean = synchronized(_cancelled)

  def register(cancel: => Consequence[Unit]): JobCancellationRegistration = {
    val callback = () => _invoke(cancel)
    val registrationid = synchronized {
      if (_cancelled)
        None
      else {
        _next_registration_id += 1L
        val id = _next_registration_id
        _callbacks = _callbacks.updated(id, callback)
        Some(id)
      }
    }
    registrationid match {
      case Some(id) => new _registration(this, id)
      case None =>
        callback()
        JobCancellationRegistration.noop
    }
  }

  /**
   * Signals currently registered work exactly once. Registration after this
   * point invokes its callback immediately so launch/cancel races are safe.
   */
  def cancel(): Unit = {
    val callbacks = synchronized {
      if (_cancelled)
        Vector.empty
      else {
        _cancelled = true
        val result = _callbacks.values.toVector
        _callbacks = Map.empty
        result
      }
    }
    callbacks.foreach(_())
  }

  private[job] def unregister(id: Long): Unit = synchronized {
    _callbacks = _callbacks - id
  }

  private def _invoke(cancel: => Consequence[Unit]): Unit =
    try {
      val _ = cancel
    } catch {
      case NonFatal(_) => () // Cancellation is best-effort and must not break Job control.
    }
}

trait JobCancellationRegistration extends AutoCloseable {
  def close(): Unit
}

object JobCancellationRegistration {
  val noop: JobCancellationRegistration = new JobCancellationRegistration {
    def close(): Unit = ()
  }
}

private final class _registration(
  scope: JobCancellationScope,
  id: Long
) extends JobCancellationRegistration {
  private var _closed = false

  def close(): Unit = synchronized {
    if (!_closed) {
      _closed = true
      scope.unregister(id)
    }
  }
}
