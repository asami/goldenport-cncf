package org.goldenport.cncf.datastore.sql

import javax.sql.DataSource
import org.goldenport.Consequence

/*
 * @since   Aug.  2, 2026
 * @version Aug.  2, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * An explicitly transferred SQL resource ownership boundary.
 *
 * This is deliberately local to the SQL datastore implementation: SystemNode
 * registration, leases, and shutdown ordering are separate Phase 54 slices.
 */
private[cncf] final class ManagedSqlDataStoreResource private (
  val datasource: DataSource,
  physicalclose: () => Unit
) {
  import ManagedSqlDataStoreResource.*

  private var _state: State = State.Open
  private var _active_borrows: Int = 0
  private var _force_close: Boolean = false

  def isOpen: Boolean = synchronized(_state == State.Open)

  def borrowC[A](f: DataSource => Consequence[A]): Consequence[A] =
    {
      val admitted = synchronized {
      _state match {
          case State.Open =>
            _active_borrows += 1
            true
          case _ => false
        }
      }
      if (!admitted)
        Consequence {
          throw new IllegalStateException("managed SQL datastore resource is closed")
        }
      else
        try {
          f(datasource)
        } finally {
          synchronized {
            _active_borrows -= 1
            notifyAll()
          }
        }
    }

  def closeC(): Consequence[Unit] =
    _close_c(force = false)

  def forceCloseC(): Consequence[Unit] =
    _close_c(force = true)

  private def _close_c(force: Boolean): Consequence[Unit] = {
    val owner = synchronized {
      if (force) {
        _force_close = true
        notifyAll()
      }
      _state match {
        case State.Open =>
          _state = State.Closing
          true
        case _ => false
      }
    }
    if (owner) {
      val result = Consequence {
        _await_borrows_c
        physicalclose()
      }
      synchronized {
        _state = State.Closed(result)
        notifyAll()
      }
      result
    } else {
      _await_closed_c
    }
  }

  private def _await_closed_c: Consequence[Unit] =
    Consequence {
      synchronized {
        while (_state == State.Closing)
          wait()
        _state match {
          case State.Closed(result) => result
          case State.Open =>
            throw new IllegalStateException("managed SQL datastore resource close did not linearize")
          case State.Closing =>
            throw new IllegalStateException("managed SQL datastore resource close did not finish")
        }
      }
    }.flatMap(identity)

  private def _await_borrows_c: Unit =
    synchronized {
      while (_active_borrows > 0 && !_force_close)
        wait()
    }
}

private[cncf] object ManagedSqlDataStoreResource {
  private enum State {
    case Open
    case Closing
    case Closed(result: Consequence[Unit])
  }

  def hikari(datasource: DataSource, close: () => Unit): ManagedSqlDataStoreResource =
    new ManagedSqlDataStoreResource(datasource, close)

  def closeUnpublished[A](resource: ManagedSqlDataStoreResource)(f: => A): A =
    try f
    catch {
      case e: Throwable =>
        resource.closeC()
        throw e
    }
}
