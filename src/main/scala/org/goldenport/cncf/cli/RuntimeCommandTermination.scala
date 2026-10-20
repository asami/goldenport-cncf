package org.goldenport.cncf.cli

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.control.NonFatal
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.LocalProcessExecutionDriver
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Oct. 20, 2026
 * @version Oct. 20, 2026
 * @author  ASAMI, Tomoharu
 */
/** CLI invocation scope only. Storage shutdown stays on the main command's normal finally path. */
private[cli] final class RuntimeCommandTermination private (
  grace: Long,
  stop: () => Consequence[Unit],
  register: Thread => Unit,
  remove: Thread => Unit,
  report: String => Unit,
  waitfor: (CountDownLatch, Long) => Boolean,
  confirm: () => Boolean
) {
  private val _completed = new CountDownLatch(1)
  private val _stop_completed = new CountDownLatch(1)
  private val _succeeded = new AtomicBoolean(false)
  private val _stop_succeeded = new AtomicBoolean(false)
  private val _requested = new AtomicBoolean(false)
  private val _hook = new Thread(new Runnable {
    def run(): Unit = _request_stop_and_wait()
  }, "cncf-runtime-command-termination")
  register(_hook)
  _report("runtime-started")

  def run[A](command: => A)(shutdown: => Unit): A = {
    var commandcompleted = false
    var commandfailure = Option.empty[Throwable]
    try {
      val result = command
      commandcompleted = true
      result
    } catch {
      case error: Throwable =>
        commandfailure = Some(error)
        throw error
    } finally {
      var shutdowncompleted = false
      try {
        shutdown
        shutdowncompleted = true
      } catch {
        case error: Throwable => commandfailure match {
          case Some(primary) => if (!(primary eq error)) primary.addSuppressed(error)
          case None => throw error
        }
      } finally {
        _succeeded.set(commandcompleted && shutdowncompleted)
        _completed.countDown()
        try remove(_hook)
        catch { case _: IllegalStateException => () } // JVM shutdown has already started.
      }
    }
  }

  private def _request_stop_and_wait(): Unit =
    if (_requested.compareAndSet(false, true)) {
      val startedat = System.nanoTime()
      _report("cancellation-requested")
      // Stop may itself wait for process-tree confirmation. The hook's total wait
      // remains bounded by the existing runtime grace, independently of that work.
      val stopper = new Thread(new Runnable {
        def run(): Unit =
          try _stop_succeeded.set(stop().isSuccess)
          catch { case NonFatal(_) => () }
          finally _stop_completed.countDown()
      }, "cncf-runtime-command-stop")
      stopper.setDaemon(true)
      def _remaining_(): Long =
        math.max(0L, grace - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedat))
      try {
        stopper.start()
        val stopped = waitfor(_stop_completed, _remaining_())
        val completed = waitfor(_completed, _remaining_())
        _report(if (stopped && completed && _stop_succeeded.get && _succeeded.get && confirm()) "settled" else "uncertain")
      } catch {
        case _: InterruptedException =>
          Thread.currentThread().interrupt()
          _report("uncertain")
        case NonFatal(_) => _report("uncertain")
      }
    }

  private def _report(state: String): Unit =
    try report(state)
    catch { case NonFatal(_) => () }
}

private[cli] object RuntimeCommandTermination {
  def install(subsystem: Subsystem): RuntimeCommandTermination = {
    val driver = subsystem.globalRuntimeContextOption.flatMap(_.processExecutionDriverOption).collect {
      case local: LocalProcessExecutionDriver => local
    }
    create(subsystem.systemNode.drainTimeoutMillis, () => driver.fold(Consequence.unit)(_.requestStopC()),
      hook => Runtime.getRuntime.addShutdownHook(hook),
      hook => { Runtime.getRuntime.removeShutdownHook(hook); () },
      state => System.err.println("[cncf-runtime-event] " + Json.obj(
        "state" -> Json.fromString(state),
        "pid" -> Json.fromLong(ProcessHandle.current().pid())).noSpaces),
      confirmTermination = () => driver.forall(_.terminationConfirmed))
  }

  /** Deterministic hook-registration and wait seams, without installing a real JVM hook. */
  private[cli] def create(
    graceMillis: Long,
    requestStop: () => Consequence[Unit],
    registerHook: Thread => Unit,
    removeHook: Thread => Unit,
    reportState: String => Unit,
    awaitCompletion: (CountDownLatch, Long) => Boolean = (latch, millis) => latch.await(millis, TimeUnit.MILLISECONDS),
    confirmTermination: () => Boolean = () => true
  ): RuntimeCommandTermination =
    new RuntimeCommandTermination(graceMillis, requestStop, registerHook, removeHook, reportState, awaitCompletion, confirmTermination)
}
