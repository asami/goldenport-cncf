package org.goldenport.cncf.job

import java.time.{Duration, Instant}
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Explicit lifecycle owner of one non-overlapping fixed-delay registration.
 * The supplied timer registers nonblockingly and cannot fire before its due time.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobMaintenanceSchedulerStatus(
  started: Boolean,
  closed: Boolean,
  inFlight: Boolean,
  nextDueAt: Option[Instant],
  lastResult: Option[Consequence[DurableJobMaintenanceReport]]
)

private[job] final class DurableJobMaintenanceScheduler(
  maintenance: DurableJobMaintenance,
  source: DurableJobRetentionCandidateSource,
  maxCandidates: Int,
  period: Duration,
  timeSource: JobTimeSource,
  timer: JobTimer,
  lease: Option[DurableJobRuntimeLease] = None
)(using ctx: ExecutionContext) extends AutoCloseable {
  private val _monitor = new Object()
  private var _started = false
  private var _closed = false
  private var _in_flight = false
  private var _generation = 0L
  private var _registration: Option[JobTimerRegistration] = None
  private var _next_due_at: Option[Instant] = None
  private var _last_result: Option[Consequence[DurableJobMaintenanceReport]] = None

  def start(): Consequence[Unit] = _monitor.synchronized {
    if (_closed) _refused
    else if (_started) Consequence.unit
    else if (period == null || period.compareTo(Duration.ofSeconds(1)) < 0 || period.compareTo(Duration.ofHours(24)) > 0 ||
      maxCandidates < 1 || maxCandidates > 1024 || maintenance == null || source == null || timeSource == null || timer == null ||
      lease == null || lease.exists(_ == null))
      _fail_registration()
    else {
      _started = true
      _arm()
    }
  }

  def status: DurableJobMaintenanceSchedulerStatus = _monitor.synchronized {
    DurableJobMaintenanceSchedulerStatus(_started, _closed, _in_flight, _next_due_at, _last_result)
  }

  def close(): Unit = {
    val registration = _monitor.synchronized {
      _closed = true
      _started = false
      _generation += 1L
      _next_due_at = None
      val current = _registration
      _registration = None
      current
    }
    registration.foreach { value => try value.close() catch { case NonFatal(_) => () } }
  }

  private def _arm(): Consequence[Unit] =
    try {
      val now = timeSource.now()
      if (now == null || now == Instant.MAX || _generation == Long.MaxValue) _fail_registration()
      else {
        val due = now.plus(period)
        if (due == Instant.MAX || !due.isAfter(now)) _fail_registration()
        else {
          _generation += 1L
          val generation = _generation
          val registration = timer.schedule(due)(_run(generation))
          if (registration == null) _fail_registration()
          else {
            _registration = Some(registration)
            _next_due_at = Some(due)
            Consequence.unit
          }
        }
      }
    } catch {
      case NonFatal(_) => _fail_registration()
    }

  private def _run(generation: Long): Unit = {
    val admitted = _monitor.synchronized {
      if (_closed || !_started || _in_flight || generation != _generation || _registration.isEmpty) false
      else {
        _in_flight = true
        _registration = None
        _next_due_at = None
        _generation += 1L
        true
      }
    }
    if (admitted) {
      val result = try {
        val sweep = lease match {
          case Some(value) => value.use(maintenance.sweep(source, maxCandidates, timeSource.now()))
          case None => maintenance.sweep(source, maxCandidates, timeSource.now())
        }
        sweep match {
          case success @ Consequence.Success(_) => success
          case _ => _refused[DurableJobMaintenanceReport]
        }
      } catch {
        case NonFatal(_) => _refused[DurableJobMaintenanceReport]
      }
      _monitor.synchronized {
        _last_result = Some(result)
        _in_flight = false
        if (!_closed && _started) _arm()
      }
    }
  }

  private def _fail_registration(): Consequence[Unit] = {
    _started = false
    _registration = None
    _next_due_at = None
    _last_result = Some(_refused)
    _refused
  }

  private def _refused[A]: Consequence[A] = Consequence.stateInvalid("Durable Job maintenance scheduler refused")
}
