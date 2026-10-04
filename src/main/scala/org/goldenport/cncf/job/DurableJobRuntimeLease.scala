package org.goldenport.cncf.job

import org.goldenport.Consequence
import scala.util.control.NonFatal
import org.goldenport.cncf.subsystem.Subsystem

/*
 * Nesting borrows the admitted SystemNode binding lease; close owns only a gate.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] trait DurableJobRuntimeLease {
  def use[A](body: => Consequence[A]): Consequence[A]
  def close(): Unit
  def available: Boolean
}

private[job] object DurableJobRuntimeLease {
  def subsystem(subsystem: Subsystem): DurableJobRuntimeLease = new BindingLease(subsystem)

  def ready(lease: DurableJobRuntimeLease, engine: InMemoryJobEngine, observe: Consequence[?] => Unit): DurableJobRuntimeLease =
    new DurableJobRuntimeLease {
      def use[A](body: => Consequence[A]): Consequence[A] =
        if (!engine._durable_runtime_ready) DurableJobRuntimeOwner.refused
        else {
          try {
            val result = lease.use(body)
            observe(result)
            result
          } catch {
            case exception: InterruptedException =>
              observe(DurableJobRuntimeOwner.refused[Unit]); Thread.currentThread().interrupt(); throw exception
            case NonFatal(exception) => observe(DurableJobRuntimeOwner.refused[Unit]); throw exception
          }
        }
      def close(): Unit = lease.close()
      def available: Boolean = lease.available && engine._durable_runtime_ready
    }

  private final class BindingLease(subsystem: Subsystem) extends DurableJobRuntimeLease {
    private val _monitor = new Object
    private val _entered = new ThreadLocal[java.lang.Boolean]()
    private var _closed = false

    def available: Boolean = _monitor.synchronized { !_closed }
    def close(): Unit = _monitor.synchronized { _closed = true }

    def use[A](body: => Consequence[A]): Consequence[A] =
      if (java.lang.Boolean.TRUE == _entered.get()) body
      else if (!_monitor.synchronized { !_closed }) DurableJobRuntimeOwner.refused
      else subsystem._with_managed_datastore_lease_c { _ =>
        // Recheck after acquisition. No gate monitor is held across user code.
        if (!_monitor.synchronized { !_closed }) DurableJobRuntimeOwner.refused
        else {
          _entered.set(java.lang.Boolean.TRUE)
          try body finally _entered.remove()
        }
      }
  }
}
