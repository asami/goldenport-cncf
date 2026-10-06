package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkResource, UnitOfWorkTermination}

/*
 * Owns only an unpublished native lease, never a Job record or durable authority.
 *
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class JobUnitOfWorkSubmission(
  jobid: JobId,
  release: () => Unit,
  activate: (() => Unit) => Consequence[JobId]
) extends UnitOfWorkResource {
  private enum State { case Pending, Activated, Released }
  private var _state = State.Pending

  private[job] def _stage_c(unitofwork: UnitOfWork): Consequence[JobId] = {
    var staged = false
    try {
      unitofwork.registerResourceC(this).flatMap { _ =>
        synchronized {
          if (_state != State.Pending) _released_failure
          else {
            unitofwork.stagePostCommitC(_activate_c())
            staged = true
            Consequence.success(jobid)
          }
        }
      }
    } finally if (!staged) _release_pending()
  }

  def releaseC(termination: UnitOfWorkTermination): Consequence[Unit] = {
    _release_pending()
    Consequence.unit
  }

  private def _activate_c(): Consequence[Unit] = synchronized {
    if (_state != State.Pending) _released_failure
    else try activate(() => _mark_activated()).map(_ => ())
    finally _release_pending()
  }

  private def _mark_activated(): Unit = synchronized {
    require(_state == State.Pending, "released UOW Job reservation")
    _state = State.Activated
  }

  private def _release_pending(): Unit = synchronized {
    if (_state == State.Pending) {
      _state = State.Released
      release()
    }
  }

  private def _released_failure[A]: Consequence[A] =
    Consequence.stateConflict("UOW Job reservation has already terminated")
}
