package org.goldenport.cncf.subsystem

import java.security.SecureRandom
import java.util.UUID
import org.goldenport.Consequence
import org.goldenport.cncf.config.SystemNodeShutdownConfiguration
import org.goldenport.cncf.datastore.sql.{ManagedSqlDataStoreResource, SqlDataStoreIdentity, SystemNodeSqlDataStoreRegistry}

/*
 * @since   Aug.  2, 2026
 * @version Aug.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class SystemNode private (
  val runtimeIdentity: String,
  val hmacKey: SqlDataStoreIdentity.HmacKey,
  onflightwait: () => Unit,
  val drainTimeoutMillis: Long,
  drainruntime: SystemNode.DrainRuntime
) {
  import SystemNode.*

  private var _state: State = State.Running
  private var _next_lease_id: Long = 0L
  private var _active_leases: Set[SystemNodeResourceLease] = Set.empty
  private var _bindings: Set[SystemNodeDataStoreBinding] = Set.empty
  private var _shutdown_in_progress: Boolean = false
  private var _shutdown_result: Option[Consequence[Unit]] = None
  private val _registry = new SystemNodeSqlDataStoreRegistry(_is_lease_admitted, onflightwait)

  def state: State = synchronized(_state)

  private[cncf] def activeLeaseCount: Int = synchronized(_active_leases.size)

  private[cncf] def bindingCount: Int = synchronized(_bindings.size)

  private[cncf] def managedDataStoreCount: Int = _registry.entryCount

  def beginStopping(): Unit = synchronized {
    if (_state == State.Running)
      _state = State.Stopping
  }

  def shutdownC(onTimeout: () => Unit = () => ()): Consequence[Unit] = {
    val owner = synchronized {
      _state match {
        case State.Stopped => false
        case State.Stopping if _shutdown_in_progress => false
        case _ =>
          _state = State.Stopping
          _shutdown_in_progress = true
          true
      }
    }
    if (owner) {
      // Shutdown must terminalize even when a timeout callback (for example,
      // job cancellation) itself fails.  Capturing that failure keeps the
      // registry close and waiter notification on the same shutdown path.
      val result = {
        var draininterrupted = false
        val drainresult = try {
          _drain_c(_ => true, onTimeout)
        } catch {
          case e: InterruptedException =>
            draininterrupted = true
            Consequence.Failure(org.goldenport.Conclusion.from(e))
          case e: Throwable => Consequence.Failure(org.goldenport.Conclusion.from(e))
        }
        // An interrupted bounded wait clears its thread flag. Remember that
        // condition, clear any already-set flag only while physical close
        // runs, then restore interruption after terminalization.
        val interruptflag = Thread.interrupted()
        val interrupted = draininterrupted || interruptflag
        val closeresult = try {
          _registry.closeAllC(force = drainresult match {
            case Consequence.Failure(_) => true
            case Consequence.Success(_) => false
          })
        } catch {
          case e: Throwable => Consequence.Failure(org.goldenport.Conclusion.from(e))
        } finally {
          if (interrupted)
            Thread.currentThread().interrupt()
        }
        _aggregate(Vector(drainresult, closeresult))
      }
      synchronized {
        _state = State.Stopped
        _shutdown_result = Some(result)
        _shutdown_in_progress = false
        notifyAll()
      }
      result
    } else
      _await_shutdown_c()
  }

  private[cncf] def bind(): SystemNodeDataStoreBinding =
    synchronized {
      val binding = new SystemNodeDataStoreBinding(this)
      _bindings = _bindings + binding
      binding
    }

  private[cncf] def isSoleBinding(binding: SystemNodeDataStoreBinding): Boolean =
    synchronized(_bindings == Set(binding))

  private[cncf] def releaseBinding(binding: SystemNodeDataStoreBinding): Unit = synchronized {
    _bindings = _bindings - binding
  }

  private[cncf] def grantLeaseC(
    binding: SystemNodeDataStoreBinding
  ): Consequence[SystemNodeResourceLease] =
    Consequence {
      synchronized {
        if (_state != State.Running)
          throw new IllegalStateException("SystemNode is not accepting datastore leases")
        _next_lease_id += 1
        val lease = new SystemNodeResourceLease(this, binding, _next_lease_id)
        _active_leases = _active_leases + lease
        lease
      }
    }

  private[cncf] def resolveC(
    lease: SystemNodeResourceLease,
    identity: SqlDataStoreIdentity
  )(
    factory: => Consequence[ManagedSqlDataStoreResource]
  ): Consequence[ManagedSqlDataStoreResource] =
    _registry.resolveC(lease, identity)(factory)

  private[cncf] def releaseLease(lease: SystemNodeResourceLease): Unit = synchronized {
    _active_leases = _active_leases - lease
    notifyAll()
  }

  private def _is_lease_admitted(lease: SystemNodeResourceLease): Boolean =
    synchronized((lease.node eq this) && _active_leases.contains(lease) && !lease.isReleased && !lease.isRevoked)

  private[subsystem] def drainBindingC(
    binding: SystemNodeDataStoreBinding,
    onTimeout: () => Unit = () => ()
  ): Consequence[Unit] =
    _drain_c(_.binding eq binding, onTimeout)

  private def _drain_c(
    predicate: SystemNodeResourceLease => Boolean,
    onTimeout: () => Unit
  ): Consequence[Unit] =
    Consequence {
      val deadline = drainruntime.nanoTime() + drainTimeoutMillis * 1000000L
      val timedout = synchronized {
        while (_active_leases.exists(predicate) && drainruntime.nanoTime() < deadline) {
          val remaining = deadline - drainruntime.nanoTime()
          val millis = math.max(1L, remaining / 1000000L)
          drainruntime.await(this, millis)
        }
        _active_leases.exists(predicate)
      }
      if (timedout) {
        val leases = synchronized(_active_leases.filter(predicate).toVector)
        leases.foreach(_.revoke())
        val timeoutresult = Consequence {
          throw new IllegalStateException(s"SystemNode drain timed out after $drainTimeoutMillis milliseconds")
        }
        val cancellationresult = Consequence(onTimeout())
        _aggregate(Vector(timeoutresult, cancellationresult))
      } else {
        Consequence.unit
      }
    }.flatMap(identity)

  private def _await_shutdown_c(): Consequence[Unit] =
    Consequence {
      synchronized {
        while (_state != State.Stopped)
          wait()
        _shutdown_result.getOrElse(Consequence.unit)
      }
    }.flatMap(identity)

  private def _aggregate(results: Vector[Consequence[Unit]]): Consequence[Unit] = {
    val failures = results.collect { case Consequence.Failure(conclusion) => conclusion }
    failures.reduceOption(_ ++ _) match {
      case Some(conclusion) => Consequence.Failure(conclusion)
      case None => Consequence.unit
    }
  }
}

object SystemNode {
  enum State {
    case Running
    case Stopping
    case Stopped
  }

  def create(): SystemNode =
    create(SystemNodeShutdownConfiguration.default)

  def create(configuration: SystemNodeShutdownConfiguration): SystemNode = {
    val timeout = _validate_drain_timeout(configuration)
    val bytes = new Array[Byte](32)
    new SecureRandom().nextBytes(bytes)
    new SystemNode(UUID.randomUUID().toString, SqlDataStoreIdentity.HmacKey(bytes), () => (), timeout, DrainRuntime.system)
  }

  def createC(
    configuration: SystemNodeShutdownConfiguration,
    drainTimeoutOverride: Option[Long] = None
  ): Consequence[SystemNode] =
    if (configuration == null || drainTimeoutOverride == null)
      Consequence.configurationInvalid("SystemNode shutdown configuration is required")
    else {
      val effective = drainTimeoutOverride.fold[Consequence[SystemNodeShutdownConfiguration]](
        Consequence.success(configuration)
      )(SystemNodeShutdownConfiguration.overrideC)
      effective.map { shutdownconfiguration =>
        val timeout = _validate_drain_timeout(shutdownconfiguration)
        val bytes = new Array[Byte](32)
        new SecureRandom().nextBytes(bytes)
        new SystemNode(UUID.randomUUID().toString, SqlDataStoreIdentity.HmacKey(bytes), () => (), timeout, DrainRuntime.system)
      }
    }

  private[cncf] def withHmacKey(key: SqlDataStoreIdentity.HmacKey): SystemNode =
    withHmacKey(key, () => ())

  private[cncf] def withHmacKey(
    key: SqlDataStoreIdentity.HmacKey,
    onFlightWait: () => Unit
  ): SystemNode =
    withHmacKey(key, onFlightWait, DEFAULT_DRAIN_TIMEOUT_MILLIS, DrainRuntime.system)

  private[cncf] def withHmacKey(
    key: SqlDataStoreIdentity.HmacKey,
    onFlightWait: () => Unit,
    drainTimeoutMillis: Long,
    drainRuntime: DrainRuntime
  ): SystemNode =
    new SystemNode(UUID.randomUUID().toString, key, onFlightWait, drainTimeoutMillis, drainRuntime)

  val DRAIN_TIMEOUT_KEY = "textus.system-node.shutdown.drain-timeout-millis"
  val DEFAULT_DRAIN_TIMEOUT_MILLIS: Long = 30000L
  val MAXIMUM_DRAIN_TIMEOUT_MILLIS: Long = 300000L

  private[cncf] trait DrainRuntime {
    def nanoTime(): Long
    def await(monitor: Object, millis: Long): Unit
  }

  private[cncf] object DrainRuntime {
    val system: DrainRuntime = new DrainRuntime {
      def nanoTime(): Long = System.nanoTime()
      def await(monitor: Object, millis: Long): Unit = monitor.wait(millis)
    }
  }

  private def _validate_drain_timeout(configuration: SystemNodeShutdownConfiguration): Long =
    Option(configuration).filter(x => x.drainTimeoutMillis >= 1L && x.drainTimeoutMillis <= MAXIMUM_DRAIN_TIMEOUT_MILLIS)
      .map(_.drainTimeoutMillis)
      .getOrElse(throw new IllegalArgumentException(s"$DRAIN_TIMEOUT_KEY requires an integer in 1..$MAXIMUM_DRAIN_TIMEOUT_MILLIS"))
}

/** Transfers one already-constructed node across the factory construction
 *  boundary. It carries no configuration and is cleared before factory work
 *  can return to callers. */
private[cncf] object SystemNodeConstruction {
  private val _node = new ThreadLocal[SystemNode]()

  def withNode[A](node: SystemNode)(f: => A): A = {
    if (node == null)
      throw new IllegalArgumentException("SystemNode construction node is required")
    if (_node.get != null)
      throw new IllegalStateException("SystemNode construction is already active")
    _node.set(node)
    try f
    finally _node.remove()
  }

  def current: Option[SystemNode] = Option(_node.get)
}

private[cncf] final class SystemNodeResourceLease private[subsystem] (
  private[cncf] val node: SystemNode,
  private[subsystem] val binding: SystemNodeDataStoreBinding,
  val leaseId: Long
) {
  private var _released: Boolean = false
  private var _revoked: Boolean = false

  def isReleased: Boolean = synchronized(_released)
  def isRevoked: Boolean = synchronized(_revoked)

  private[subsystem] def revoke(): Unit = synchronized {
    _revoked = true
  }

  def release(): Unit = {
    val shouldrelease = synchronized {
      if (_released)
        false
      else {
        _released = true
        true
      }
    }
    if (shouldrelease)
      binding.releaseLease(this)
    if (shouldrelease)
      node.releaseLease(this)
  }
}

private[cncf] final class SystemNodeDataStoreBinding private[subsystem] (
  node: SystemNode
) {
  private var _released: Boolean = false
  private var _closing: Boolean = false
  private var _identities: Map[String, SqlDataStoreIdentity] = Map.empty
  private var _leases: Vector[SystemNodeResourceLease] = Vector.empty

  def acquireLeaseC(): Consequence[SystemNodeResourceLease] =
    synchronized {
      if (_released || _closing)
        Consequence { throw new IllegalStateException("SystemNode datastore binding is released") }
      else
        node.grantLeaseC(this).map { lease =>
          synchronized {
            if (_released) {
              lease.release()
              throw new IllegalStateException("SystemNode datastore binding is released")
            }
            _leases = _leases :+ lease
            lease
          }
        }
    }

  def resolveC(
    lease: SystemNodeResourceLease,
    logicalName: String,
    identity: SqlDataStoreIdentity
  )(
    factory: => Consequence[ManagedSqlDataStoreResource]
  ): Consequence[ManagedSqlDataStoreResource] =
    synchronized {
      if (_released)
        Consequence { throw new IllegalStateException("SystemNode datastore binding is released") }
      else if (!(lease.binding eq this))
        Consequence { throw new IllegalStateException("SystemNode datastore lease belongs to another binding") }
      else if (lease.isReleased)
        Consequence { throw new IllegalStateException("SystemNode datastore lease is released") }
      else {
        val prioridentity = _identities.get(logicalName)
        prioridentity match {
          case Some(existing) if existing != identity =>
            Consequence { throw new IllegalStateException("logical datastore binding conflicts with its canonical identity") }
          case _ =>
            _identities = _identities.updated(logicalName, identity)
            node.resolveC(lease, identity)(factory).recoverWith { conclusion =>
              if (prioridentity.isEmpty)
                synchronized {
                  if (_identities.get(logicalName).contains(identity))
                    _identities = _identities - logicalName
                }
              Consequence.Failure(conclusion)
            }
        }
      }
    }

  def inheritLeaseC(
    lease: SystemNodeResourceLease
  ): Consequence[SystemNodeResourceLease] =
    synchronized {
      if (_released)
        Consequence { throw new IllegalStateException("SystemNode datastore binding is released") }
      else if (!(lease.binding eq this) || lease.isReleased)
        Consequence { throw new IllegalStateException("SystemNode datastore lease is not inheritable") }
      else
        Consequence.success(lease)
    }

  def release(): Unit = {
    val leases = synchronized {
      _released = true
      _identities = Map.empty
      val result = _leases
      _leases = Vector.empty
      result
    }
    leases.foreach(_.release())
    node.releaseBinding(this)
  }

  def drainC(onTimeout: () => Unit = () => ()): Consequence[Unit] = {
    val shoulddrain = synchronized {
      if (_released)
        false
      else {
        _closing = true
        true
      }
    }
    if (shoulddrain)
      node.drainBindingC(this, onTimeout)
    else
      Consequence.unit
  }

  private[subsystem] def releaseLease(lease: SystemNodeResourceLease): Unit =
    synchronized {
      _leases = _leases.filterNot(_ eq lease)
    }
}
