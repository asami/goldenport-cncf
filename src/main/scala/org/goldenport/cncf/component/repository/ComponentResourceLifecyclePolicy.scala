package org.goldenport.cncf.component.repository

import java.util.concurrent.{ConcurrentHashMap, CountDownLatch}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.observability.CallTreeContext

import scala.util.control.NonFatal

/*
 * Lifecycle coordination for immutable resolved component-resource evidence.
 * It neither discovers content nor grants any resource or runtime authority.
 *
 * @since   Aug. 21, 2026
 * @version Aug. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentResourceLifecycleKey(
  componentId: ComponentId,
  logicalRelease: String,
  role: String,
  sourceKind: ComponentResourceSourceKind,
  artifactDigest: String
) {
  require(ComponentResourceLifecycleKey._is_safe(componentId, logicalRelease, role, sourceKind, artifactDigest), ComponentResourceLifecycleKey.INVALID_KEY_MESSAGE)
}

object ComponentResourceLifecycleKey {
  private val COMPONENT_ID_PATTERN = "[A-Za-z][A-Za-z0-9.]{0,127}".r
  private val RELEASE_PATTERN = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}".r
  private val ROLE_PATTERN = "[A-Za-z][A-Za-z0-9._-]{0,63}".r
  private val SHA256_PATTERN = "[0-9a-f]{64}".r
  private val INVALID_KEY_MESSAGE = "invalid component resource lifecycle key"

  private def _is_safe(
    componentid: ComponentId,
    logicalrelease: String,
    role: String,
    sourcekind: ComponentResourceSourceKind,
    artifactdigest: String
  ): Boolean =
    Option(componentid).exists(id => Option(id.name).exists(value => COMPONENT_ID_PATTERN.matches(value))) &&
      Option(logicalrelease).exists(value => RELEASE_PATTERN.matches(value)) &&
      Option(role).exists(value => ROLE_PATTERN.matches(value)) &&
      sourcekind != null &&
      Option(artifactdigest).exists(value => SHA256_PATTERN.matches(value))
}

final class ComponentResourceLifecycleCancellation {
  private val _cancelled = new AtomicBoolean(false)
  private val _monitor = new Object()
  private var _callbacks = Vector.empty[() => Unit]

  def cancel(): Boolean =
    if (_cancelled.compareAndSet(false, true)) {
      val callbacks = _monitor.synchronized {
        val result = _callbacks
        _callbacks = Vector.empty
        result
      }
      callbacks.foreach(_())
      true
    } else
      false

  def isCancelled: Boolean = _cancelled.get()

  def cancelled: Boolean = isCancelled

  private[repository] def _on_cancelled(callback: () => Unit): Unit = {
    val invoke = _monitor.synchronized {
      if (isCancelled)
        true
      else {
        _callbacks = _callbacks :+ callback
        false
      }
    }
    if (invoke)
      callback()
  }
}

enum ComponentResourceLifecycleOutcome {
  case Pending, Ready, Released, Unloaded, Shutdown, Cancelled, Failed
}

enum ComponentResourceLifecycleState {
  case Empty, Pending, Ready, Invalidated, Released, Unloaded, Shutdown, Cancelled, Failed
}

final case class ComponentResourceLifecycleSnapshot(
  resource: Option[ResolvedComponentResource],
  generation: Long,
  owners: Set[String],
  state: ComponentResourceLifecycleState,
  outcome: ComponentResourceLifecycleOutcome
)

final case class ComponentResourceLifecycleMetrics(
  cancellationCount: Long,
  failureCount: Long
)

final case class ComponentResourceLifecycleDiagnostic(
  key: ComponentResourceLifecycleKey,
  componentId: ComponentId,
  release: String,
  role: String,
  sourceKind: ComponentResourceSourceKind,
  artifactDigest: String,
  operation: String,
  outcome: ComponentResourceLifecycleOutcome
)

final class ComponentResourceLifecycleStore(
  diagnosticLimit: Int = ComponentResourceLifecycleStore.DEFAULT_DIAGNOSTIC_LIMIT,
  callTreeContext: Option[CallTreeContext] = None,
  callTreeEventLimit: Int = ComponentResourceLifecycleStore.DEFAULT_CALL_TREE_EVENT_LIMIT
) {
  require(diagnosticLimit > 0, "diagnosticLimit must be positive")
  require(callTreeEventLimit > 0, "callTreeEventLimit must be positive")

  private val _entries = new ConcurrentHashMap[ComponentResourceLifecycleKey, Entry]()
  private val _store_shutdown = new AtomicBoolean(false)
  private val _cancellation_count = new AtomicLong(0L)
  private val _failure_count = new AtomicLong(0L)
  private val _call_tree_node_count = new AtomicLong(0L)
  private val _diagnostics_monitor = new Object()
  private val _observability_monitor = new Object()
  private var _diagnostics = Vector.empty[ComponentResourceLifecycleDiagnostic]

  def resolveBlocking(
    key: ComponentResourceLifecycleKey,
    owner: String,
    loader: () => ResolvedComponentResource
  ): ComponentResourceLifecycleSnapshot =
    _resolve_blocking(key, owner, loader, None)

  def resolveBlocking(
    key: ComponentResourceLifecycleKey,
    owner: String,
    loader: () => ResolvedComponentResource,
    cancellation: ComponentResourceLifecycleCancellation
  ): ComponentResourceLifecycleSnapshot =
    _resolve_blocking(key, owner, loader, Option(cancellation))

  def refreshBlocking(
    key: ComponentResourceLifecycleKey,
    owner: String,
    loader: () => ResolvedComponentResource
  ): ComponentResourceLifecycleSnapshot =
    _admit_blocking(key, owner, loader, None, refresh = true, operation = "refresh")

  def invalidate(key: ComponentResourceLifecycleKey): ComponentResourceLifecycleSnapshot =
    _settle(key, "invalidate", _entry(key).invalidate(_store_shutdown.get()))

  def release(
    key: ComponentResourceLifecycleKey,
    owner: String
  ): ComponentResourceLifecycleSnapshot =
    _settle(
      key,
      "release",
      _entry(key).release(owner, ComponentResourceLifecycleOutcome.Released, _store_shutdown.get())
    )

  def unload(key: ComponentResourceLifecycleKey): ComponentResourceLifecycleSnapshot =
    _settle(key, "unload", _entry(key).unload(_store_shutdown.get()))

  def shutdown(
    key: ComponentResourceLifecycleKey,
    owner: String
  ): ComponentResourceLifecycleSnapshot =
    _settle(
      key,
      "shutdown",
      _entry(key).release(owner, ComponentResourceLifecycleOutcome.Shutdown, _store_shutdown.get())
    )

  def shutdown(): ComponentResourceLifecycleSnapshot = {
    if (_store_shutdown.compareAndSet(false, true)) {
      val entries = _entries.values().iterator()
      while (entries.hasNext)
        entries.next().shutdown()
    }
    _global_shutdown_snapshot
  }

  def snapshot(key: ComponentResourceLifecycleKey): ComponentResourceLifecycleSnapshot = {
    val entry = _entries.get(key)
    if (entry == null)
      if (_store_shutdown.get()) _global_shutdown_snapshot else _empty_snapshot
    else
      entry.snapshot()
  }

  def metrics: ComponentResourceLifecycleMetrics =
    ComponentResourceLifecycleMetrics(
      cancellationCount = _cancellation_count.get(),
      failureCount = _failure_count.get()
    )

  def diagnostics: Vector[ComponentResourceLifecycleDiagnostic] =
    _diagnostics_monitor.synchronized(_diagnostics)

  private def _resolve_blocking(
    key: ComponentResourceLifecycleKey,
    owner: String,
    loader: () => ResolvedComponentResource,
    cancellation: Option[ComponentResourceLifecycleCancellation]
  ): ComponentResourceLifecycleSnapshot =
    _admit_blocking(key, owner, loader, cancellation, refresh = false, operation = "resolve")

  private def _admit_blocking(
    key: ComponentResourceLifecycleKey,
    owner: String,
    loader: () => ResolvedComponentResource,
    cancellation: Option[ComponentResourceLifecycleCancellation],
    refresh: Boolean,
    operation: String
  ): ComponentResourceLifecycleSnapshot = {
    val entry = _entry(key)
    entry.admit(owner, cancellation, refresh, _store_shutdown.get()) match {
      case Immediate(transition) =>
        _settle(key, operation, transition)
      case Start(flight) =>
        _load_blocking(key, entry, flight, loader, operation)
      case Wait(flight) =>
        _settle(key, operation, entry.awaitBlocking(flight, owner, cancellation))
    }
  }

  private def _load_blocking(
    key: ComponentResourceLifecycleKey,
    entry: Entry,
    flight: InFlight,
    loader: () => ResolvedComponentResource,
    operation: String
  ): ComponentResourceLifecycleSnapshot =
    try {
      val resource = loader()
      val transition =
        if (resource == null)
          entry.completeFailure(flight, _store_shutdown.get())
        else
          entry.completeSuccess(flight, resource, _store_shutdown.get())
      _settle(key, operation, transition)
    } catch {
      case interrupted: InterruptedException =>
        entry.abandon(flight, interrupted, _store_shutdown.get())
        throw interrupted
      case NonFatal(_) =>
        _settle(key, operation, entry.completeFailure(flight, _store_shutdown.get()))
      case fatal: Throwable =>
        entry.abandon(flight, fatal, _store_shutdown.get())
        throw fatal
    }

  private def _settle(
    key: ComponentResourceLifecycleKey,
    operation: String,
    transition: Transition
  ): ComponentResourceLifecycleSnapshot = {
    if (transition.cancellationWon)
      _cancellation_count.incrementAndGet()
    if (transition.failureWon) {
      _failure_count.incrementAndGet()
      _append_failure_diagnostic(key, operation)
    }
    _observe(key, operation, transition.snapshot.outcome)
    transition.snapshot
  }

  private def _entry(key: ComponentResourceLifecycleKey): Entry = {
    val current = _entries.get(key)
    if (current != null)
      current
    else {
      val created = new Entry(key)
      val previous = _entries.putIfAbsent(key, created)
      if (previous == null) created else previous
    }
  }

  private def _append_failure_diagnostic(
    key: ComponentResourceLifecycleKey,
    operation: String
  ): Unit =
    _diagnostics_monitor.synchronized {
      val diagnostic = ComponentResourceLifecycleDiagnostic(
        key = key,
        componentId = key.componentId,
        release = key.logicalRelease,
        role = key.role,
        sourceKind = key.sourceKind,
        artifactDigest = key.artifactDigest,
        operation = operation,
        outcome = ComponentResourceLifecycleOutcome.Failed
      )
      _diagnostics = (_diagnostics :+ diagnostic).takeRight(diagnosticLimit)
    }

  private def _observe(
    key: ComponentResourceLifecycleKey,
    operation: String,
    outcome: ComponentResourceLifecycleOutcome
  ): Unit =
    callTreeContext.foreach { context =>
      if (context.isEnabled) {
        val attributes = Map(
          "componentId" -> key.componentId.name,
          "logicalRelease" -> key.logicalRelease,
          "role" -> key.role,
          "sourceKind" -> key.sourceKind.toString,
          "artifactDigest" -> key.artifactDigest,
          "operation" -> operation,
          "outcome" -> outcome.toString
        )
        _observability_monitor.synchronized {
          try {
            _mark_call_tree(context, attributes)
            if (outcome == ComponentResourceLifecycleOutcome.Failed)
              _fail_call_tree(context, attributes)
          } catch {
            case NonFatal(_) => ()
          }
        }
      }
    }

  private def _mark_call_tree(context: CallTreeContext, attributes: Map[String, String]): Unit =
    if (_reserve_call_tree_nodes(CALL_TREE_MARK_NODE_COUNT))
      context.mark(CALL_TREE_LABEL, attributes)

  private def _fail_call_tree(context: CallTreeContext, attributes: Map[String, String]): Unit =
    if (_reserve_call_tree_nodes(CALL_TREE_FAILURE_NODE_COUNT))
      context.failure(CALL_TREE_LABEL, SAFE_FAILURE_MESSAGE, attributes)

  private def _reserve_call_tree_nodes(nodecount: Long): Boolean = {
    var reserved = false
    while (!reserved) {
      val current = _call_tree_node_count.get()
      if (current + nodecount > callTreeEventLimit)
        return false
      reserved = _call_tree_node_count.compareAndSet(current, current + nodecount)
    }
    true
  }

  private final class Entry(key: ComponentResourceLifecycleKey) {
    private val _monitor = new Object()
    private var _resource = Option.empty[ResolvedComponentResource]
    private var _generation = 0L
    private var _owners = Set.empty[String]
    private var _state = ComponentResourceLifecycleState.Empty
    private var _outcome = ComponentResourceLifecycleOutcome.Pending
    private var _in_flight = Option.empty[InFlight]

    def admit(
      owner: String,
      cancellation: Option[ComponentResourceLifecycleCancellation],
      refresh: Boolean,
      storeShutdown: Boolean
    ): Admission =
      _monitor.synchronized {
        if (storeShutdown)
          Immediate(_terminal_locked(ComponentResourceLifecycleOutcome.Shutdown))
        else if (_is_terminal(_state))
          Immediate(Transition(_snapshot_locked()))
        else if (cancellation.exists(_.isCancelled)) {
          if (_state == ComponentResourceLifecycleState.Empty && _in_flight.isEmpty)
            Immediate(_terminal_locked(ComponentResourceLifecycleOutcome.Cancelled))
          else {
            _owners = _owners - owner
            Immediate(Transition(_cancelled_snapshot_locked(), cancellationWon = true))
          }
        } else if (_state == ComponentResourceLifecycleState.Ready && !refresh) {
          _owners = _owners + owner
          Immediate(Transition(_snapshot_locked()))
        } else {
          _owners = _owners + owner
          _in_flight match {
            case Some(flight) => Wait(flight)
            case None =>
              val flight = new InFlight(owner, cancellation)
              _resource = None
              _state = ComponentResourceLifecycleState.Pending
              _outcome = ComponentResourceLifecycleOutcome.Pending
              _in_flight = Some(flight)
              Start(flight)
          }
        }
      }

    def awaitBlocking(
      flight: InFlight,
      owner: String,
      cancellation: Option[ComponentResourceLifecycleCancellation]
    ): Transition =
      try {
        flight.awaitBlockingCompletionOrCancellation(cancellation) match {
          case Some(FlightSnapshot(snapshot)) => Transition(snapshot)
          case Some(FlightFailure(failure)) => throw failure
          case None =>
            _monitor.synchronized {
              _owners = _owners - owner
              Transition(_cancelled_snapshot_locked(), cancellationWon = true)
            }
        }
      } catch {
        case interrupted: InterruptedException =>
          _monitor.synchronized {
            _owners = _owners - owner
          }
          throw interrupted
      }

    def completeSuccess(
      flight: InFlight,
      resource: ResolvedComponentResource,
      storeShutdown: Boolean
    ): Transition =
      _monitor.synchronized {
        if (!_in_flight.contains(flight))
          Transition(_snapshot_locked())
        else if (storeShutdown)
          _terminal_locked(ComponentResourceLifecycleOutcome.Shutdown)
        else if (!_matches_key(resource))
          _complete_failure_locked(flight)
        else if (flight.loadingCancellationCancelled) {
          _owners = _owners - flight.loadingOwner
          if (_owners.isEmpty)
            _terminal_locked(ComponentResourceLifecycleOutcome.Cancelled)
          else {
            _resource = Some(resource)
            _generation = _generation + 1L
            _state = ComponentResourceLifecycleState.Ready
            _outcome = ComponentResourceLifecycleOutcome.Ready
            val snapshot = _snapshot_locked()
            _in_flight = None
            flight.complete(snapshot)
            Transition(_cancelled_snapshot_locked(), cancellationWon = true)
          }
        } else {
          _resource = Some(resource)
          _generation = _generation + 1L
          _state = ComponentResourceLifecycleState.Ready
          _outcome = ComponentResourceLifecycleOutcome.Ready
          val snapshot = _snapshot_locked()
          _in_flight = None
          flight.complete(snapshot)
          Transition(snapshot)
        }
      }

    def completeFailure(
      flight: InFlight,
      storeShutdown: Boolean
    ): Transition =
      _monitor.synchronized {
        if (!_in_flight.contains(flight))
          Transition(_snapshot_locked())
        else if (storeShutdown)
          _terminal_locked(ComponentResourceLifecycleOutcome.Shutdown)
        else if (flight.loadingCancellationCancelled) {
          _owners = _owners - flight.loadingOwner
          if (_owners.isEmpty)
            _terminal_locked(ComponentResourceLifecycleOutcome.Cancelled)
          else {
            _complete_failure_locked(flight)
            Transition(
              _cancelled_snapshot_locked(),
              cancellationWon = true,
              failureWon = true
            )
          }
        } else
          _complete_failure_locked(flight)
      }

    def abandon(
      flight: InFlight,
      failure: Throwable,
      storeShutdown: Boolean
    ): Unit =
      _monitor.synchronized {
        if (_in_flight.contains(flight)) {
          if (storeShutdown)
            _terminal_locked(ComponentResourceLifecycleOutcome.Shutdown)
          else {
            _resource = None
            _owners = Set.empty
            _state = ComponentResourceLifecycleState.Empty
            _outcome = ComponentResourceLifecycleOutcome.Pending
            _in_flight = None
            flight.abandon(failure)
          }
        }
      }

    def invalidate(storeShutdown: Boolean): Transition =
      _monitor.synchronized {
        if (storeShutdown)
          _terminal_locked(ComponentResourceLifecycleOutcome.Shutdown)
        else if (_is_terminal(_state))
          Transition(_snapshot_locked())
        else {
          _resource = None
          _state = ComponentResourceLifecycleState.Invalidated
          _outcome = ComponentResourceLifecycleOutcome.Pending
          val snapshot = _snapshot_locked()
          _in_flight.foreach(_.complete(snapshot))
          _in_flight = None
          Transition(snapshot)
        }
      }

    def release(
      owner: String,
      outcome: ComponentResourceLifecycleOutcome,
      storeShutdown: Boolean
    ): Transition =
      _monitor.synchronized {
        if (storeShutdown)
          _terminal_locked(ComponentResourceLifecycleOutcome.Shutdown)
        else if (_is_terminal(_state))
          Transition(_snapshot_locked())
        else if (_owners.contains(owner)) {
          _owners = _owners - owner
          if (_owners.nonEmpty)
            Transition(_snapshot_locked())
          else
            _terminal_locked(outcome)
        } else
          Transition(_snapshot_locked())
      }

    def unload(storeShutdown: Boolean): Transition =
      _monitor.synchronized {
        if (storeShutdown)
          _terminal_locked(ComponentResourceLifecycleOutcome.Shutdown)
        else
          _terminal_locked(ComponentResourceLifecycleOutcome.Unloaded)
      }

    def shutdown(): Transition =
      _monitor.synchronized(_terminal_locked(ComponentResourceLifecycleOutcome.Shutdown))

    def snapshot(): ComponentResourceLifecycleSnapshot =
      _monitor.synchronized(_snapshot_locked())

    private def _complete_failure_locked(flight: InFlight): Transition = {
      _resource = None
      _state = ComponentResourceLifecycleState.Failed
      _outcome = ComponentResourceLifecycleOutcome.Failed
      val snapshot = _snapshot_locked()
      _in_flight = None
      flight.complete(snapshot)
      Transition(snapshot, failureWon = true)
    }

    private def _matches_key(resource: ResolvedComponentResource): Boolean =
      resource.logicalIdentity.componentId == key.componentId &&
        resource.logicalIdentity.logicalRelease == key.logicalRelease &&
        resource.logicalIdentity.childRole == key.role &&
        resource.provenance.childRole == key.role &&
        resource.provenance.sourceKind == key.sourceKind &&
        resource.provenance.sha256 == key.artifactDigest

    private def _terminal_locked(
      outcome: ComponentResourceLifecycleOutcome
    ): Transition =
      if (_is_terminal(_state))
        Transition(_snapshot_locked())
      else {
        _resource = None
        _owners = Set.empty
        _state = _state_for(outcome)
        _outcome = outcome
        val snapshot = _snapshot_locked()
        _in_flight.foreach(_.complete(snapshot))
        _in_flight = None
        Transition(
          snapshot,
          cancellationWon = outcome == ComponentResourceLifecycleOutcome.Cancelled
        )
      }

    private def _cancelled_snapshot_locked(): ComponentResourceLifecycleSnapshot =
      ComponentResourceLifecycleSnapshot(
        resource = None,
        generation = _generation,
        owners = _owners,
        state = ComponentResourceLifecycleState.Cancelled,
        outcome = ComponentResourceLifecycleOutcome.Cancelled
      )

    private def _snapshot_locked(): ComponentResourceLifecycleSnapshot =
      ComponentResourceLifecycleSnapshot(
        resource = _resource,
        generation = _generation,
        owners = _owners,
        state = _state,
        outcome = _outcome
      )
  }

  private final class InFlight(
    val loadingOwner: String,
    loadingcancellation: Option[ComponentResourceLifecycleCancellation]
  ) {
    private val _monitor = new Object()
    private var _completed = false
    private var _completion = Option.empty[FlightCompletion]
    private var _completion_waiters = Vector.empty[CountDownLatch]

    def loadingCancellationCancelled: Boolean = loadingcancellation.exists(_.isCancelled)

    def complete(snapshot: ComponentResourceLifecycleSnapshot): Unit = {
      val waiters = _monitor.synchronized {
        _completed = true
        _completion = Some(FlightSnapshot(snapshot))
        val result = _completion_waiters
        _completion_waiters = Vector.empty
        result
      }
      waiters.foreach(_.countDown())
    }

    def abandon(failure: Throwable): Unit = {
      val waiters = _monitor.synchronized {
        _completed = true
        _completion = Some(FlightFailure(failure))
        val result = _completion_waiters
        _completion_waiters = Vector.empty
        result
      }
      waiters.foreach(_.countDown())
    }

    def awaitBlockingCompletionOrCancellation(cancellation: Option[ComponentResourceLifecycleCancellation]): Option[FlightCompletion] = {
      val signal = new CountDownLatch(1)
      val completed = _monitor.synchronized {
        if (_completed)
          true
        else {
          _completion_waiters = _completion_waiters :+ signal
          false
        }
      }
      if (!completed && !cancellation.exists(_.isCancelled)) {
        cancellation.foreach(_._on_cancelled(() => signal.countDown()))
        if (!cancellation.exists(_.isCancelled))
          signal.await()
      }
      if (cancellation.exists(_.isCancelled))
        None
      else
        _monitor.synchronized(_completion)
    }
  }

  private sealed trait FlightCompletion
  private final case class FlightSnapshot(snapshot: ComponentResourceLifecycleSnapshot) extends FlightCompletion
  private final case class FlightFailure(failure: Throwable) extends FlightCompletion

  private sealed trait Admission
  private final case class Immediate(transition: Transition) extends Admission
  private final case class Start(flight: InFlight) extends Admission
  private final case class Wait(flight: InFlight) extends Admission

  private final case class Transition(
    snapshot: ComponentResourceLifecycleSnapshot,
    cancellationWon: Boolean = false,
    failureWon: Boolean = false
  )

  private def _is_terminal(state: ComponentResourceLifecycleState): Boolean =
    state match {
      case ComponentResourceLifecycleState.Released |
          ComponentResourceLifecycleState.Unloaded |
          ComponentResourceLifecycleState.Shutdown |
          ComponentResourceLifecycleState.Cancelled => true
      case _ => false
    }

  private def _state_for(
    outcome: ComponentResourceLifecycleOutcome
  ): ComponentResourceLifecycleState =
    outcome match {
      case ComponentResourceLifecycleOutcome.Released => ComponentResourceLifecycleState.Released
      case ComponentResourceLifecycleOutcome.Unloaded => ComponentResourceLifecycleState.Unloaded
      case ComponentResourceLifecycleOutcome.Shutdown => ComponentResourceLifecycleState.Shutdown
      case ComponentResourceLifecycleOutcome.Cancelled => ComponentResourceLifecycleState.Cancelled
      case ComponentResourceLifecycleOutcome.Failed => ComponentResourceLifecycleState.Failed
      case ComponentResourceLifecycleOutcome.Ready => ComponentResourceLifecycleState.Ready
      case ComponentResourceLifecycleOutcome.Pending => ComponentResourceLifecycleState.Pending
    }

  private val CALL_TREE_LABEL = "component-resource-lifecycle"
  private val SAFE_FAILURE_MESSAGE = "component resource lifecycle failure"
  private val CALL_TREE_MARK_NODE_COUNT = 2L
  private val CALL_TREE_FAILURE_NODE_COUNT = 1L

  private val _empty_snapshot = ComponentResourceLifecycleSnapshot(
    resource = None,
    generation = 0L,
    owners = Set.empty,
    state = ComponentResourceLifecycleState.Empty,
    outcome = ComponentResourceLifecycleOutcome.Pending
  )

  private val _global_shutdown_snapshot = ComponentResourceLifecycleSnapshot(
    resource = None,
    generation = 0L,
    owners = Set.empty,
    state = ComponentResourceLifecycleState.Shutdown,
    outcome = ComponentResourceLifecycleOutcome.Shutdown
  )
}

object ComponentResourceLifecycleStore {
  val DEFAULT_DIAGNOSTIC_LIMIT: Int = 128
  val DEFAULT_CALL_TREE_EVENT_LIMIT: Int = 256
}
