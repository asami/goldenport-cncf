package org.goldenport.cncf.component.repository

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.observability.CallTreeContext

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
)

final class ComponentResourceLifecycleCancellation {
  private val _cancelled = new AtomicBoolean(false)

  def cancel(): Boolean = _cancelled.compareAndSet(false, true)

  def isCancelled: Boolean = _cancelled.get()

  def cancelled: Boolean = isCancelled
}

enum ComponentResourceLifecycleOutcome:
  case Pending, Ready, Released, Unloaded, Shutdown, Cancelled, Failed

enum ComponentResourceLifecycleState:
  case Empty, Pending, Ready, Invalidated, Released, Unloaded, Shutdown, Cancelled, Failed

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
  diagnosticLimit: Int = ComponentResourceLifecycleStore.DefaultDiagnosticLimit,
  callTreeContext: Option[CallTreeContext] = None
) {
  require(diagnosticLimit > 0, "diagnosticLimit must be positive")

  private val _entries = new ConcurrentHashMap[ComponentResourceLifecycleKey, Entry]()
  private val _store_shutdown = new AtomicBoolean(false)
  private val _cancellation_count = new AtomicLong(0L)
  private val _failure_count = new AtomicLong(0L)
  private val _diagnostics_monitor = new Object()
  private val _observability_monitor = new Object()
  private var _diagnostics = Vector.empty[ComponentResourceLifecycleDiagnostic]

  def resolve(
    key: ComponentResourceLifecycleKey,
    owner: String,
    loader: () => ResolvedComponentResource
  ): ComponentResourceLifecycleSnapshot =
    _resolve(key, owner, loader, None)

  def resolve(
    key: ComponentResourceLifecycleKey,
    owner: String,
    loader: () => ResolvedComponentResource,
    cancellation: ComponentResourceLifecycleCancellation
  ): ComponentResourceLifecycleSnapshot =
    _resolve(key, owner, loader, Option(cancellation))

  def refresh(
    key: ComponentResourceLifecycleKey,
    owner: String,
    loader: () => ResolvedComponentResource
  ): ComponentResourceLifecycleSnapshot = {
    val entry = _entry(key)
    var result = Option.empty[ComponentResourceLifecycleSnapshot]
    while (result.isEmpty) {
      entry.admit(owner, None, refresh = true, _store_shutdown.get()) match {
        case Immediate(transition) =>
          result = Some(_settle(key, "refresh", transition))
        case Start(flight) =>
          result = Some(_load(key, entry, flight, loader, "refresh"))
        case Wait(flight) =>
          flight.awaitCompletion()
      }
    }
    result.get
  }

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

  private def _resolve(
    key: ComponentResourceLifecycleKey,
    owner: String,
    loader: () => ResolvedComponentResource,
    cancellation: Option[ComponentResourceLifecycleCancellation]
  ): ComponentResourceLifecycleSnapshot = {
    val entry = _entry(key)
    entry.admit(owner, cancellation, refresh = false, _store_shutdown.get()) match {
      case Immediate(transition) =>
        _settle(key, "resolve", transition)
      case Start(flight) =>
        _load(key, entry, flight, loader, "resolve")
      case Wait(flight) =>
        flight.awaitCompletion()
        _settle(key, "resolve", Transition(entry.snapshot()))
    }
  }

  private def _load(
    key: ComponentResourceLifecycleKey,
    entry: Entry,
    flight: InFlight,
    loader: () => ResolvedComponentResource,
    operation: String
  ): ComponentResourceLifecycleSnapshot = {
    val transition =
      try {
        val resource = loader()
        if (resource == null)
          throw new IllegalStateException("component resource loader returned no evidence")
        entry.completeSuccess(flight, resource, _store_shutdown.get())
      } catch {
        case _: Throwable =>
          entry.completeFailure(flight, _store_shutdown.get())
      }
    _settle(key, operation, transition)
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
      val created = new Entry()
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
            context.mark("component-resource-lifecycle", attributes)
            if (outcome == ComponentResourceLifecycleOutcome.Failed)
              context.failure(
                "component-resource-lifecycle",
                "component resource lifecycle failure",
                attributes
              )
          } catch {
            case _: Throwable => ()
          }
        }
      }
    }

  private final class Entry {
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
        else if (_state == ComponentResourceLifecycleState.Ready && !refresh) {
          _owners = _owners + owner
          Immediate(Transition(_snapshot_locked()))
        } else {
          _owners = _owners + owner
          _in_flight match {
            case Some(flight) =>
              cancellation.foreach(flight.add)
              if (flight.isCancelled)
                Immediate(_terminal_locked(ComponentResourceLifecycleOutcome.Cancelled))
              else
                Wait(flight)
            case None =>
              val flight = new InFlight()
              cancellation.foreach(flight.add)
              if (flight.isCancelled)
                Immediate(_terminal_locked(ComponentResourceLifecycleOutcome.Cancelled))
              else {
                _resource = None
                _state = ComponentResourceLifecycleState.Pending
                _outcome = ComponentResourceLifecycleOutcome.Pending
                _in_flight = Some(flight)
                Start(flight)
              }
          }
        }
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
        else if (flight.isCancelled)
          _terminal_locked(ComponentResourceLifecycleOutcome.Cancelled)
        else {
          _resource = Some(resource)
          _generation = _generation + 1L
          _state = ComponentResourceLifecycleState.Ready
          _outcome = ComponentResourceLifecycleOutcome.Ready
          _in_flight = None
          flight.complete()
          Transition(_snapshot_locked())
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
        else if (flight.isCancelled)
          _terminal_locked(ComponentResourceLifecycleOutcome.Cancelled)
        else {
          _resource = None
          _state = ComponentResourceLifecycleState.Failed
          _outcome = ComponentResourceLifecycleOutcome.Failed
          _in_flight = None
          flight.complete()
          Transition(_snapshot_locked(), failureWon = true)
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
          _in_flight.foreach(_.complete())
          _in_flight = None
          Transition(_snapshot_locked())
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
        _in_flight.foreach(_.complete())
        _in_flight = None
        Transition(
          _snapshot_locked(),
          cancellationWon = outcome == ComponentResourceLifecycleOutcome.Cancelled
        )
      }

    private def _snapshot_locked(): ComponentResourceLifecycleSnapshot =
      ComponentResourceLifecycleSnapshot(
        resource = _resource,
        generation = _generation,
        owners = _owners,
        state = _state,
        outcome = _outcome
      )
  }

  private final class InFlight {
    private val _completion = new CountDownLatch(1)
    private var _cancellations = Vector.empty[ComponentResourceLifecycleCancellation]

    def add(cancellation: ComponentResourceLifecycleCancellation): Unit =
      _cancellations = _cancellations :+ cancellation

    def isCancelled: Boolean = _cancellations.exists(_.isCancelled)

    def complete(): Unit = _completion.countDown()

    def awaitCompletion(): Unit =
      try _completion.await()
      catch {
        case _: InterruptedException => Thread.currentThread.interrupt()
      }
  }

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
  val DefaultDiagnosticLimit: Int = 128
}
