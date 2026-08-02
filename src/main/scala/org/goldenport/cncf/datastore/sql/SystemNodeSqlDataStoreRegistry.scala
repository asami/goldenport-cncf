package org.goldenport.cncf.datastore.sql

import java.util.concurrent.CountDownLatch
import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.SystemNodeResourceLease

/*
 * @since   Aug.  2, 2026
 * @version Aug.  2, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] final class SystemNodeSqlDataStoreRegistry(
  isLeaseAdmitted: SystemNodeResourceLease => Boolean,
  onFlightWait: () => Unit = () => ()
) {
  private var _entries: Map[SqlDataStoreIdentity, ManagedSqlDataStoreResource] = Map.empty
  private var _flights: Map[SqlDataStoreIdentity, SystemNodeSqlDataStoreRegistry.Flight] = Map.empty
  private var _terminal: Boolean = false

  def entryCount: Int = synchronized(_entries.size)

  def resolveC(
    lease: SystemNodeResourceLease,
    identity: SqlDataStoreIdentity
  )(
    factory: => Consequence[ManagedSqlDataStoreResource]
  ): Consequence[ManagedSqlDataStoreResource] = {
    if (!isLeaseAdmitted(lease))
      Consequence { throw new IllegalStateException("SystemNode datastore lease is not admitted") }
    else {
      val decision = synchronized {
        if (_terminal)
          SystemNodeSqlDataStoreRegistry.Decision.Terminal
        else {
          _entries.get(identity) match {
            case Some(resource) => SystemNodeSqlDataStoreRegistry.Decision.Ready(resource)
            case None =>
              _flights.get(identity) match {
                case Some(flight) => SystemNodeSqlDataStoreRegistry.Decision.Wait(flight)
                case None =>
                  val flight = new SystemNodeSqlDataStoreRegistry.Flight
                  _flights = _flights.updated(identity, flight)
                  SystemNodeSqlDataStoreRegistry.Decision.Create(flight)
              }
          }
        }
      }
      decision match {
        case SystemNodeSqlDataStoreRegistry.Decision.Terminal =>
          Consequence { throw new IllegalStateException("SystemNode datastore registry is terminal") }
        case SystemNodeSqlDataStoreRegistry.Decision.Ready(resource) =>
          if (isLeaseAdmitted(lease))
            Consequence.success(resource)
          else
            Consequence { throw new IllegalStateException("SystemNode datastore lease was revoked before resource use") }
        case SystemNodeSqlDataStoreRegistry.Decision.Wait(flight) =>
          onFlightWait()
          flight.awaitC().flatMap { resource =>
            if (isLeaseAdmitted(lease))
              Consequence.success(resource)
            else
              Consequence { throw new IllegalStateException("SystemNode datastore lease was revoked while waiting") }
          }
        case SystemNodeSqlDataStoreRegistry.Decision.Create(flight) =>
          // A factory may throw before it can return a structured failure.  It
          // must still complete the flight so that later callers can retry
          // rather than being left behind a permanently unpublished entry.
          val result: Consequence[ManagedSqlDataStoreResource] =
            Consequence { factory }.flatMap(result => result)
          val completedresult = synchronized {
            val terminalresult = result match {
              case Consequence.Success(resource) if !_terminal && isLeaseAdmitted(lease) =>
                _entries = _entries.updated(identity, resource)
                result
              case Consequence.Success(resource) =>
                resource.closeC()
                Consequence { throw new IllegalStateException("SystemNode datastore lease was revoked or registry terminal during creation") }
              case Consequence.Failure(_) => result
            }
            _flights = _flights - identity
            flight.complete(terminalresult)
            terminalresult
          }
          completedresult
      }
    }
  }

  def closeAllC(force: Boolean = false): Consequence[Unit] = {
    val resources = synchronized {
      _terminal = true
      val result = _entries.toVector.sortBy(_._1.canonicalKey).map(_._2)
      _entries = Map.empty
      result
    }
    val results = resources.map { resource =>
      if (force) resource.forceCloseC() else resource.closeC()
    }
    val failures = results.collect { case Consequence.Failure(conclusion) => conclusion }
    failures.reduceOption(_ ++ _) match {
      case Some(conclusion) => Consequence.Failure(conclusion)
      case None => Consequence.unit
    }
  }
}

private[cncf] object SystemNodeSqlDataStoreRegistry {
  private sealed trait Decision
  private object Decision {
    case object Terminal extends Decision
    final case class Ready(resource: ManagedSqlDataStoreResource) extends Decision
    final case class Wait(flight: Flight) extends Decision
    final case class Create(flight: Flight) extends Decision
  }

  private final class Flight {
    private val _latch = new CountDownLatch(1)
    @volatile private var _result: Option[Consequence[ManagedSqlDataStoreResource]] = None

    def complete(result: Consequence[ManagedSqlDataStoreResource]): Unit = {
      _result = Some(result)
      _latch.countDown()
    }

    def awaitC(): Consequence[ManagedSqlDataStoreResource] =
      Consequence {
        _latch.await()
        _result.getOrElse(throw new IllegalStateException("SystemNode datastore flight completed without a result"))
      }.flatMap(identity)
  }
}
