package org.goldenport.cncf.context

import java.time.{Duration, Instant}

/** Runtime-owned operational scheduling selected by an execution profile. */
/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionSchedulingRuntime private[context] (
  runtimeclock: RuntimeClock,
  mode: ExecutionSchedulerMode
) {
  private final case class TimerEntry(
    dueat: Instant,
    sequence: Long,
    body: () => Unit
  )

  private var _timer_sequence = 0L
  private var _work_sequence = 0L
  private var _timers = Vector.empty[TimerEntry]
  private var _work_queues = Vector.empty[(Long, () => Boolean)]

  def clock: java.time.Clock = runtimeclock.clock

  def testControl: Option[ExecutionTestControl] =
    runtimeclock.manual_clock.filter(_ => mode == ExecutionSchedulerMode.Manual).map { clock =>
      new ExecutionTestControl {
        def now: Instant = clock.instant()

        def advanceBy(duration: Duration): Unit = {
          val _ = clock.advanceBy(duration)
          val _ = _fire_due()
        }

        def runUntilIdle(limit: Int): Int =
          _run_until_idle(limit)
      }
    }

  private[cncf] def schedule(dueat: Instant)(body: => Unit): ExecutionSchedulingRegistration =
    synchronized {
      _timer_sequence += 1L
      val sequence = _timer_sequence
      _timers :+= TimerEntry(dueat, sequence, () => body)
      new ExecutionSchedulingRegistration {
        def close(): Unit =
          ExecutionSchedulingRuntime.this.synchronized {
            _timers = _timers.filterNot(_.sequence == sequence)
          }
      }
    }

  private[cncf] def register_work_queue(drainone: () => Boolean): ExecutionSchedulingRegistration =
    synchronized {
      _work_sequence += 1L
      val sequence = _work_sequence
      _work_queues :+= sequence -> drainone
      new ExecutionSchedulingRegistration {
        def close(): Unit =
          ExecutionSchedulingRuntime.this.synchronized {
            _work_queues = _work_queues.filterNot(_._1 == sequence)
          }
      }
    }

  private def _fire_due(): Int = {
    val due =
      synchronized {
        val now = runtimeclock.clock.instant()
        val (ready, pending) = _timers.partition(!_.dueat.isAfter(now))
        _timers = pending
        ready.sortBy(x => (x.dueat, x.sequence))
      }
    due.foreach(_.body())
    due.size
  }

  private def _run_until_idle(limit: Int): Int = {
    require(limit > 0, "run-until-idle limit must be positive")
    var workcount = 0
    var iterations = 0
    var progressed = true
    while (progressed && iterations < limit && workcount < limit) {
      iterations += 1
      progressed = false
      val timercount = _fire_due()
      progressed = timercount > 0
      val queues = synchronized(_work_queues.sortBy(_._1).map(_._2))
      queues.foreach { drainone =>
        while (workcount < limit && drainone()) {
          workcount += 1
          progressed = true
        }
      }
    }
    workcount
  }
}

/** In-process control for a manual execution profile; never a component DSL. */
trait ExecutionTestControl {
  def now: Instant
  def advanceBy(duration: Duration): Unit
  def runUntilIdle(limit: Int = 10000): Int
}

private[cncf] trait ExecutionSchedulingRegistration {
  def close(): Unit
}

object ExecutionSchedulingRuntime {
  private[context] def create(
    runtimeclock: RuntimeClock,
    mode: ExecutionSchedulerMode
  ): ExecutionSchedulingRuntime =
    new ExecutionSchedulingRuntime(runtimeclock, mode)
}
