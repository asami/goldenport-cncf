package org.goldenport.cncf.workflow

import scala.collection.mutable
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.unitofwork.UnitOfWork

/**
 * CNCF-owned durable continuation boundary.  A continuation is admitted only
 * after the surrounding UnitOfWork commits; a result is consumed once by a
 * caller-supplied fresh UnitOfWork.
 */
trait ContinuationRuntime {
  import ContinuationRuntime.*

  def stageSuspensionC(
    unitOfWork: UnitOfWork,
    continuation: Continuation
  ): Consequence[Unit]

  def claimC(identity: ContinuationIdentity): Consequence[Claim]

  def resumeC(
    identity: ContinuationIdentity,
    result: StateMachineOperationResult,
    freshUnitOfWork: () => UnitOfWork
  ): Consequence[Resume]
}

object ContinuationRuntime {
  final case class Claim(
    continuation: Continuation,
    claimId: String
  )

  final case class Resume(
    continuation: Continuation,
    result: StateMachineOperationResult
  )

  /** Deterministic runtime for focused execution and tests. */
  final class InMemory extends ContinuationRuntime {
    private val _available = mutable.Map.empty[ContinuationIdentity, Continuation]
    private val _staged = mutable.Set.empty[ContinuationIdentity]
    private val _claimed = mutable.Set.empty[ContinuationIdentity]
    private val _completed = mutable.Set.empty[ContinuationIdentity]

    def stageSuspensionC(
      unitOfWork: UnitOfWork,
      continuation: Continuation
    ): Consequence[Unit] =
      if (unitOfWork == null || continuation == null)
        _invalid("continuation", "missing suspension boundary")
      else if (_staged.contains(continuation.continuationId) || _available.contains(continuation.continuationId) || _completed.contains(continuation.continuationId))
        _invalid("continuation", s"duplicate continuation: ${continuation.continuationId.value}")
      else {
        _staged += continuation.continuationId
        unitOfWork.stagePostCommitC {
          _staged -= continuation.continuationId
          _available.update(continuation.continuationId, continuation)
          Consequence.unit
        }
        Consequence.unit
      }

    def claimC(identity: ContinuationIdentity): Consequence[Claim] =
      if (identity == null)
        _invalid("continuation", "missing continuation identity")
      else if (_completed.contains(identity))
        _invalid("continuation", s"continuation already completed: ${identity.value}")
      else if (_claimed.contains(identity))
        _invalid("continuation", s"continuation already claimed: ${identity.value}")
      else _available.get(identity) match {
        case Some(continuation) =>
          _claimed += identity
          Consequence.success(Claim(continuation, s"claim:${identity.value}"))
        case None => _invalid("continuation", s"continuation unavailable: ${identity.value}")
      }

    def resumeC(
      identity: ContinuationIdentity,
      result: StateMachineOperationResult,
      freshUnitOfWork: () => UnitOfWork
    ): Consequence[Resume] =
      if (identity == null || result == null || freshUnitOfWork == null)
        _invalid("continuation", "missing resume input")
      else if (_completed.contains(identity))
        _invalid("continuation", s"duplicate resume: ${identity.value}")
      else _available.get(identity) match {
        case None => _invalid("continuation", s"continuation unavailable: ${identity.value}")
        case Some(continuation) if !_claimed.contains(identity) =>
          _invalid("continuation", s"continuation not claimed: ${identity.value}")
        case Some(continuation) if continuation.requiredOperation.resultType.exists(_ != result.typeReference) =>
          _invalid("continuation", s"incompatible result type: ${identity.value}")
        case Some(continuation) =>
          val uow = freshUnitOfWork()
          if (uow == null)
            _invalid("continuation", "fresh UnitOfWork factory returned null")
          else {
            uow.stagePostCommitC {
              _completed += identity
              _available -= identity
              _claimed -= identity
              Consequence.unit
            }
            uow.commit() match {
              case Consequence.Success(_) =>
                Consequence.success(Resume(continuation, result))
              case Consequence.Failure(conclusion) =>
                _claimed -= identity
                Consequence.Failure[Resume](conclusion)
            }
          }
      }

    def isAvailable(identity: ContinuationIdentity): Boolean = _available.contains(identity)
    def isCompleted(identity: ContinuationIdentity): Boolean = _completed.contains(identity)
  }

  private def _invalid[A](kind: String, message: String): Consequence[A] =
    Consequence.Failure(Conclusion.from(new IllegalArgumentException(s"$kind: $message")))
}
