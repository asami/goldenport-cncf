package org.goldenport.cncf.unitofwork

import org.goldenport.{Conclusion, Consequence}
import scala.util.control.NonFatal

/*
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Non-transactional runtime resource owned by one UnitOfWork.
 *
 * Transaction participants belong to the commit protocol. This contract owns
 * operational resources such as a process handle and its execution WorkArea.
 */
trait UnitOfWorkResource {
  def releaseC(termination: UnitOfWorkTermination): Consequence[Unit]
}

enum UnitOfWorkTermination {
  case Committed
  case Aborted
  case Disposed
}

trait UnitOfWorkResourceRegistration extends AutoCloseable {
  /** Removes a resource that has already reached its own terminal state. */
  def close(): Unit
}

object UnitOfWorkResourceRegistration {
  val noop: UnitOfWorkResourceRegistration = new UnitOfWorkResourceRegistration {
    def close(): Unit = ()
  }
}

private[unitofwork] final class UnitOfWorkResourceRegistry {
  private var _termination: Option[UnitOfWorkTermination] = None
  private var _next_id = 0L
  private var _resources = Vector.empty[(Long, UnitOfWorkResource)]

  def registerC(resource: UnitOfWorkResource): Consequence[UnitOfWorkResourceRegistration] = {
    val decision = synchronized {
      _termination match {
        case Some(termination) => Left(termination)
        case None =>
          _next_id += 1L
          val id = _next_id
          _resources = _resources :+ (id -> resource)
          Right(id)
      }
    }
    decision match {
      case Left(termination) =>
        _release_c(resource, termination).map(_ => UnitOfWorkResourceRegistration.noop)
      case Right(id) =>
        Consequence.success(new UnitOfWorkResourceRegistrationHandle(this, id))
    }
  }

  def terminateC(termination: UnitOfWorkTermination): Consequence[Unit] = {
    val resources = synchronized {
      _termination match {
        case Some(_) => Vector.empty
        case None =>
          _termination = Some(termination)
          val result = _resources.reverse
          _resources = Vector.empty
          result
      }
    }
    _release_all_c(resources, termination)
  }

  private[unitofwork] def unregister(id: Long): Unit = synchronized {
    _resources = _resources.filterNot(_._1 == id)
  }

  private def _release_all_c(
    resources: Vector[(Long, UnitOfWorkResource)],
    termination: UnitOfWorkTermination
  ): Consequence[Unit] = {
    var failures = Vector.empty[Conclusion]
    resources.foreach { case (_, resource) =>
      _release_c(resource, termination) match {
        case Consequence.Failure(conclusion) => failures = failures :+ conclusion
        case _ => ()
      }
    }
    failures.reduceOption(_ ++ _).map(Consequence.Failure(_)).getOrElse(Consequence.unit)
  }

  private def _release_c(
    resource: UnitOfWorkResource,
    termination: UnitOfWorkTermination
  ): Consequence[Unit] =
    try {
      resource.releaseC(termination)
    } catch {
      case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
    }
}

private final class UnitOfWorkResourceRegistrationHandle(
  registry: UnitOfWorkResourceRegistry,
  id: Long
) extends UnitOfWorkResourceRegistration {
  private var _closed = false

  def close(): Unit = synchronized {
    if (!_closed) {
      _closed = true
      registry.unregister(id)
    }
  }
}
