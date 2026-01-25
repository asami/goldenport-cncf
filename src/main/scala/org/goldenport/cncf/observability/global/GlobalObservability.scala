package org.goldenport.cncf.observability.global

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.mutable

import org.goldenport.cncf.context.ScopeContext
import org.goldenport.cncf.context.ScopeKind
import org.goldenport.cncf.context.ObservabilityContext
import org.goldenport.cncf.context.TraceId
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.cncf.observability.ObservabilityEngine
import org.goldenport.cncf.observability.global.GlobalObservabilityGate
import org.goldenport.record.Record

/*
 * @since   Jan. 23, 2026
 * @version Jan. 23, 2026
 * @author  ASAMI, Tomoharu
 */
object GlobalObservability {
  private case class BufferedEvent(
    level: String,
    scope: ScopeContext,
    msg: String,
    clazz: Class[_],
    emitter: (
      ObservabilityEngine.type,
      ObservabilityContext,
      ScopeContext,
      String,
      Record
    ) => Unit
  )

  private[observability] val BufferLimit = 256
  private val _buffer = mutable.Queue.empty[BufferedEvent]
  private var _root_opt: Option[ObservabilityRoot] = None

  private[observability] def resetForTests(): Unit =
    this.synchronized {
      _root_opt = None
      _buffer.clear()
    }

  def initialize(root: ObservabilityRoot): Unit =
    this.synchronized {
      if (_root_opt.isEmpty) {
        _root_opt = Some(root)
        LogBackendHolder.install(root.backend)
        val events = _buffer.dequeueAll(_ => true)
        events.foreach(event => _emitWithRoot(root, event))
      }
    }

  def isInitialized: Boolean =
    _root_opt.isDefined

  def gate: Option[GlobalObservabilityGate] =
    _root_opt.map(_.gate)

  def observeError(scope: ScopeContext, msg: String, clazz: Class[_]): Unit =
    _observe("error", scope, msg, clazz, (engine, context, s, name, attributes) =>
      engine.emitError(context, s, name, attributes)
    )

  def observeWarn(scope: ScopeContext, msg: String, clazz: Class[_]): Unit =
    _observe("warn", scope, msg, clazz, (engine, context, s, name, attributes) =>
      engine.emitWarn(context, s, name, attributes)
    )

  def observeInfo(scope: ScopeContext, msg: String, clazz: Class[_]): Unit =
    _observe("info", scope, msg, clazz, (engine, context, s, name, attributes) =>
      engine.emitInfo(context, s, name, attributes)
    )

  def observeDebug(scope: ScopeContext, msg: String, clazz: Class[_]): Unit =
    _observe("debug", scope, msg, clazz, (engine, context, s, name, attributes) =>
      engine.emitDebug(context, s, name, attributes)
    )

  def observeTrace(scope: ScopeContext, msg: String, clazz: Class[_]): Unit =
    _observe("trace", scope, msg, clazz, (engine, context, s, name, attributes) =>
      engine.emitTrace(context, s, name, attributes)
    )

  private def _observe(
    level: String,
    scope: ScopeContext,
    msg: String,
    clazz: Class[_],
    emitter: (
      ObservabilityEngine.type,
      ObservabilityContext,
      ScopeContext,
      String,
      Record
    ) => Unit
  ): Unit = {
    val event = BufferedEvent(level, scope, msg, clazz, emitter)
    this.synchronized {
      _root_opt match {
        case Some(root) => _emitWithRoot(root, event)
        case None =>
          if (_buffer.size >= BufferLimit) _buffer.dequeue()
          _buffer.enqueue(event)
      }
    }
  }

  private def _emitWithRoot(root: ObservabilityRoot, event: BufferedEvent): Unit = {
    val packageName = Option(event.clazz.getPackage).map(_.getName).getOrElse("")
    val className = event.clazz.getName
    root.gate.pass(event.scope, packageName, className) {
      if (root.engine.shouldEmit(event.level, event.scope, packageName, className, root.backend)) {
        val context = ObservabilityContext(
          traceId = TraceId(event.scope.name, event.scope.kind.toString),
          spanId = None,
          correlationId = None
        )
        event.emitter(root.engine, context, event.scope, event.msg, Record.empty)
      }
    }
  }
}

  final case class ObservabilityRoot(
  engine: ObservabilityEngine.type,
  gate: GlobalObservabilityGate,
  backend: LogBackend
)

object ObservabilityScopeDefaults {
  private val _context = ObservabilityContext(
    traceId = TraceId("global", "observability"),
    spanId = None,
    correlationId = None
  )

  private val _bootstrap_context = ObservabilityContext(
    traceId = TraceId("bootstrap", "observability"),
    spanId = None,
    correlationId = None
  )

  val Subsystem: ScopeContext = ScopeContext(
    kind = ScopeKind.Subsystem,
    name = "Global",
    parent = None,
    observabilityContext = _context,
    httpDriverOption = None
  )

  val Bootstrap: ScopeContext = ScopeContext(
    kind = ScopeKind.Subsystem,
    name = "Bootstrap",
    parent = None,
    observabilityContext = _bootstrap_context,
    httpDriverOption = None
  )

  // This Bootstrap scope is permanent: bootstrap-phase logs remain buffered/replayed through GlobalObservability
  // so initialization diagnostics are visible once the backend and policy are ready.
}

/*
 * A JVM-global entry gate that currently performs no visibility decisions.
 * It exists to centralize future observability flow/volume control rules.
 */
final class GlobalObservabilityGate private (_open: AtomicBoolean) {
  def this() = this(new AtomicBoolean(true))

  def pass[T](scope: ScopeContext, packageName: String, className: String)(block: => T): Unit = {
    if (_open.get()) {
      block
    } else {
      ()
    }
  }

  def blockAll(): Unit =
    _open.set(false)

  def allowAll(): Unit =
    _open.set(true)
}

object GlobalObservabilityGate {
  def apply(): GlobalObservabilityGate =
    new GlobalObservabilityGate(new AtomicBoolean(true))

  def allowAll: GlobalObservabilityGate =
    apply()
}
