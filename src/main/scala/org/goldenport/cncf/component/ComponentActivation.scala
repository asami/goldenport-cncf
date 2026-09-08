package org.goldenport.cncf.component

import java.time.{Duration, Instant}
import java.util.WeakHashMap
import java.util.concurrent.{Callable, CancellationException, ExecutionException, Executors, Future, ThreadFactory, TimeUnit, TimeoutException}
import java.util.concurrent.atomic.AtomicBoolean

import org.goldenport.{Conclusion, Consequence}
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.observation.{Cause, Observation, Taxonomy}

/*
 * @since   Sep.  7, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
trait ComponentActivation {
  def activateC(context: ComponentActivationContext): Consequence[Unit]
}

final case class ComponentActivationContext(
  subsystem: Subsystem,
  configuration: ResolvedConfiguration,
  runMode: RunMode,
  deadline: Instant,
  cancellation: ComponentActivationCancellation
)

final class ComponentActivationCancellation {
  private val _cancelled = new AtomicBoolean(false)

  def cancel(): Unit = {
    _cancelled.set(true)
    ()
  }

  def isCancelled: Boolean = _cancelled.get
}

final case class ComponentActivationDiagnostic(
  category: String,
  component: String,
  mode: RunMode,
  message: String,
  conclusion: Conclusion
)

private[cncf] final class ComponentActivationTestProbe {
  private var _coordinator_cleanup_count = 0
  private var _diagnostics = Vector.empty[ComponentActivationDiagnostic]

  def coordinatorCleanupCount: Int = synchronized(_coordinator_cleanup_count)
  def diagnostics: Vector[ComponentActivationDiagnostic] = synchronized(_diagnostics)

  private[cncf] def recordCoordinatorCleanup(): Unit = synchronized {
    _coordinator_cleanup_count += 1
  }

  private[cncf] def recordDiagnostic(diagnostic: ComponentActivationDiagnostic): Unit = synchronized {
    _diagnostics = _diagnostics :+ diagnostic
  }
}

object ComponentActivation {
  private val _runtime_activation_timeout = Duration.ofSeconds(30L)
  private val _cancellation_poll_nanos = TimeUnit.MILLISECONDS.toNanos(25L)
  private val _states = new WeakHashMap[Subsystem, ActivationState]()

  private final class ActivationState {
    var inProgress: Boolean = false
    var terminalResult: Option[Consequence[Unit]] = None
    var _terminal_diagnostic: Option[ComponentActivationDiagnostic] = None
  }

  private sealed trait ActivationOwnership
  private final case class ActivationAlreadyTerminal(result: Consequence[Unit]) extends ActivationOwnership
  private final case class ActivationOwner(state: ActivationState) extends ActivationOwnership

  private sealed trait CallbackOutcome
  private final case class CallbackCompleted(result: Consequence[Unit]) extends CallbackOutcome
  private case object CallbackTerminal extends CallbackOutcome
  private case object CallbackCrashed extends CallbackOutcome

  private[cncf] def activateForControlledTestC(
    subsystem: Subsystem,
    mode: RunMode,
    deadline: Instant,
    cancellation: ComponentActivationCancellation,
    probe: ComponentActivationTestProbe
  ): Consequence[Unit] =
    if (subsystem == null || mode == null || deadline == null || cancellation == null)
      _activation_unavailable_failure()
    else if (!subsystem.controlledTestExecutionEnabled)
      _activation_unavailable_failure()
    else
      _activate_c(subsystem, mode, deadline, cancellation, Option(probe))

  private[cncf] def activateForServerRuntimeC(subsystem: Subsystem): Consequence[Unit] =
    if (subsystem == null)
      _activation_unavailable_failure()
    else if (subsystem.controlledTestExecutionEnabled)
      Consequence.unit
    else
      _activate_c(
        subsystem,
        RunMode.Server,
        Instant.now.plus(_runtime_activation_timeout),
        new ComponentActivationCancellation,
        None
      )

  def diagnosticFor(subsystem: Subsystem): Option[ComponentActivationDiagnostic] =
    if (subsystem == null)
      None
    else
      _states.synchronized(Option(_states.get(subsystem))).flatMap { state =>
        state.synchronized(state._terminal_diagnostic)
      }

  private def _activate_c(
    subsystem: Subsystem,
    mode: RunMode,
    deadline: Instant,
    cancellation: ComponentActivationCancellation,
    probe: Option[ComponentActivationTestProbe]
  ): Consequence[Unit] =
    _acquire(subsystem, deadline, cancellation) match {
      case ActivationAlreadyTerminal(result) =>
        result
      case ActivationOwner(state) =>
        val result =
          try {
            _activate_owned_c(subsystem, mode, deadline, cancellation, probe)
          } catch {
            case _: Throwable =>
              _activation_failure("Component activation failed.")
        }
        state.synchronized {
          state.terminalResult = Some(result)
          state._terminal_diagnostic = _terminal_diagnostic(result, mode)
          state._terminal_diagnostic.foreach(diagnostic => probe.foreach(_.recordDiagnostic(diagnostic)))
        }
        result match {
          case failure: Consequence.Failure[?] =>
            _shutdown_owned(subsystem)
            probe.foreach(_.recordCoordinatorCleanup())
          case _ =>
        }
        state.synchronized {
          state.inProgress = false
          state.notifyAll()
        }
        result
    }

  private def _acquire(
    subsystem: Subsystem,
    deadline: Instant,
    cancellation: ComponentActivationCancellation
  ): ActivationOwnership = {
    val state = _states.synchronized {
      Option(_states.get(subsystem)).getOrElse {
        val created = new ActivationState
        _states.put(subsystem, created)
        created
      }
    }
    state.synchronized {
      while (state.inProgress) {
        if (_is_terminal(deadline, cancellation))
          return ActivationAlreadyTerminal(_terminal_failure())
        val remaining = _remaining_nanos(deadline)
        if (remaining <= 0L)
          return ActivationAlreadyTerminal(_terminal_failure())
        try {
          state.wait(math.max(1L, TimeUnit.NANOSECONDS.toMillis(math.min(remaining, _cancellation_poll_nanos))))
        } catch {
          case _: InterruptedException =>
            cancellation.cancel()
            Thread.currentThread.interrupt()
            return ActivationAlreadyTerminal(_terminal_failure())
        }
      }
      state.terminalResult match {
        case Some(result) =>
          if (_is_terminal(deadline, cancellation))
            ActivationAlreadyTerminal(_terminal_failure())
          else
            ActivationAlreadyTerminal(result)
        case None =>
          state.inProgress = true
          ActivationOwner(state)
      }
    }
  }

  private def _activate_owned_c(
    subsystem: Subsystem,
    mode: RunMode,
    deadline: Instant,
    cancellation: ComponentActivationCancellation,
    probe: Option[ComponentActivationTestProbe]
  ): Consequence[Unit] = {
    val coordinator = Executors.newSingleThreadExecutor(_daemon_thread_factory())
    val result =
      try {
        val targets = subsystem.components.flatMap { component =>
          component match {
            case activation: ComponentActivation => Some(ActivationTarget(activation))
            case _ => None
          }
        }
        _activate_targets_c(subsystem, targets, mode, deadline, cancellation, probe, coordinator)
      } catch {
        case _: Throwable => _activation_failure("Component activation failed.")
      } finally {
        coordinator.shutdownNow()
      }
    result
  }

  private def _activate_targets_c(
    subsystem: Subsystem,
    targets: Vector[ActivationTarget],
    mode: RunMode,
    deadline: Instant,
    cancellation: ComponentActivationCancellation,
    probe: Option[ComponentActivationTestProbe],
    coordinator: java.util.concurrent.ExecutorService
  ): Consequence[Unit] = {
    val context = ComponentActivationContext(subsystem, subsystem.configuration, mode, deadline, cancellation)
    if (_is_terminal(deadline, cancellation))
      _terminal_failure()
    else
      targets.iterator.foldLeft[Consequence[Unit]](Consequence.unit) { (result, target) =>
        result match {
          case failure: Consequence.Failure[?] => failure
          case Consequence.Success(_) if _is_terminal(deadline, cancellation) =>
            _terminal_failure()
          case Consequence.Success(_) =>
            val callbackresult = _activate_target_c(target, context, mode, deadline, cancellation, probe, coordinator)
            callbackresult match {
              case failure: Consequence.Failure[?] =>
                failure
              case Consequence.Success(_) if _is_terminal(deadline, cancellation) =>
                _terminal_failure()
              case Consequence.Success(_) =>
                Consequence.unit
            }
        }
      }
  }

  private def _activate_target_c(
    target: ActivationTarget,
    context: ComponentActivationContext,
    mode: RunMode,
    deadline: Instant,
    cancellation: ComponentActivationCancellation,
    probe: Option[ComponentActivationTestProbe],
    coordinator: java.util.concurrent.ExecutorService
  ): Consequence[Unit] = {
    val callback = coordinator.submit(new Callable[Consequence[Unit]] {
      override def call(): Consequence[Unit] = target.activation.activateC(context)
    })
    _await_callback(callback, deadline, cancellation) match {
      case CallbackCompleted(Consequence.Success(_)) if !_is_terminal(deadline, cancellation) =>
        Consequence.unit
      case CallbackCompleted(_: Consequence.Failure[?]) =>
        _callback_failure()
      case CallbackCompleted(_) =>
        _callback_failure()
      case CallbackTerminal =>
        _terminal_failure()
      case CallbackCrashed =>
        _callback_failure()
    }
  }

  private def _await_callback(
    callback: Future[Consequence[Unit]],
    deadline: Instant,
    cancellation: ComponentActivationCancellation
  ): CallbackOutcome = {
    var completed: Option[Consequence[Unit]] = None
    while (completed.isEmpty && !_is_terminal(deadline, cancellation)) {
      val remaining = _remaining_nanos(deadline)
      if (remaining <= 0L)
        cancellation.cancel()
      else {
        try {
          completed = Option(callback.get(math.min(remaining, _cancellation_poll_nanos), TimeUnit.NANOSECONDS))
        } catch {
          case _: TimeoutException => ()
          case _: InterruptedException =>
            cancellation.cancel()
            callback.cancel(true)
            Thread.currentThread.interrupt()
            return CallbackTerminal
          case _: ExecutionException | _: CancellationException =>
            return CallbackCrashed
        }
      }
    }
    completed match {
      case Some(result) if !_is_terminal(deadline, cancellation) => CallbackCompleted(result)
      case _ =>
        cancellation.cancel()
        callback.cancel(true)
        CallbackTerminal
    }
  }

  private final case class ActivationTarget(activation: ComponentActivation)

  private def _is_terminal(deadline: Instant, cancellation: ComponentActivationCancellation): Boolean =
    cancellation.isCancelled || !Instant.now.isBefore(deadline)

  private def _remaining_nanos(deadline: Instant): Long = {
    val remaining = Duration.between(Instant.now, deadline)
    if (remaining.isNegative || remaining.isZero) 0L else remaining.toNanos
  }

  private def _daemon_thread_factory(): ThreadFactory = new ThreadFactory {
    override def newThread(runnable: Runnable): Thread = {
      val thread = new Thread(runnable, "cncf-component-activation-coordinator")
      thread.setDaemon(true)
      thread
    }
  }

  private def _callback_failure(): Consequence.Failure[Unit] =
    _activation_failure("Component activation failed.")

  private def _activation_unavailable_failure(): Consequence.Failure[Unit] =
    _activation_failure("Component activation is not admitted.")

  private def _terminal_failure(): Consequence.Failure[Unit] =
    _activation_failure("Component activation deadline or cancellation reached.")

  private def _activation_failure(message: String): Consequence.Failure[Unit] =
    Consequence.Failure[Unit](
      Conclusion.serviceUnavailable(Observation.failure(Taxonomy.serviceUnavailable, Cause.message(message)))
    )

  private def _terminal_diagnostic(
    result: Consequence[Unit],
    mode: RunMode
  ): Option[ComponentActivationDiagnostic] =
    result match {
      case failure: Consequence.Failure[?] =>
        Some(ComponentActivationDiagnostic(
          category = "activation-terminal-failure",
          component = "unknown",
          mode = mode,
          message = failure.conclusion.show,
          conclusion = failure.conclusion
        ))
      case _ => None
    }

  private def _shutdown_owned(subsystem: Subsystem): Unit =
    try {
      Subsystem.shutdownOwned(subsystem)
    } catch {
      case _: Throwable => ()
    }
}
