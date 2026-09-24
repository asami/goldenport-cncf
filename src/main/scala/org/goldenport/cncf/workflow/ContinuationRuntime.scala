package org.goldenport.cncf.workflow

import java.util.UUID
import scala.collection.mutable
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkInterpreter}

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

  /** A post-commit prerequisite must succeed before the continuation becomes claimable. */
  def stageSuspensionAfterC(
    unitOfWork: UnitOfWork,
    continuation: Continuation,
    beforePublish: () => Consequence[Unit]
  ): Consequence[Unit] =
    Consequence.stateConflict("Continuation runtime does not support ordered suspension persistence")

  def claimC(identity: ContinuationIdentity): Consequence[Claim]

  /** Trusted runtime rehydration of an existing claim; never a public WorkOrder field. */
  def recoverClaimC(identity: ContinuationIdentity): Consequence[Claim] =
    Consequence.stateConflict("Continuation runtime does not support claim recovery")

  def resumeC(
    claim: Claim,
    result: StateMachineOperationResult,
    freshUnitOfWork: () => UnitOfWork
  ): Consequence[Resume]

  /** Resume while interpreting a selected closing Action in the same fresh UnitOfWork. */
  def resumeWithProgramC(
    claim: Claim,
    result: StateMachineOperationResult,
    freshUnitOfWork: () => UnitOfWork,
    program: ExecUowM[ActionExecution]
  ): Consequence[Resume] =
    _invalid("continuation", "closing Action program is not supported by this runtime")

  /** Completes a claim after its fresh UnitOfWork committed but post-commit persistence failed. */
  def finalizeC(claim: Claim): Consequence[Unit]
}

object ContinuationRuntime {
  val empty: ContinuationRuntime = new ContinuationRuntime {
    def stageSuspensionC(
      unitOfWork: UnitOfWork,
      continuation: Continuation
    ): Consequence[Unit] =
      _invalid("continuation", "runtime is not configured")

    def claimC(identity: ContinuationIdentity): Consequence[Claim] =
      _invalid("continuation", "runtime is not configured")

    override def recoverClaimC(identity: ContinuationIdentity): Consequence[Claim] =
      _invalid("continuation", "runtime is not configured")

    def resumeC(
      claim: Claim,
      result: StateMachineOperationResult,
      freshUnitOfWork: () => UnitOfWork
    ): Consequence[Resume] =
      _invalid("continuation", "runtime is not configured")

    override def resumeWithProgramC(
      claim: Claim,
      result: StateMachineOperationResult,
      freshUnitOfWork: () => UnitOfWork,
      program: ExecUowM[ActionExecution]
    ): Consequence[Resume] =
      _invalid("continuation", "runtime is not configured")

    def finalizeC(claim: Claim): Consequence[Unit] =
      _invalid("continuation", "runtime is not configured")
  }

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
    private val _claimed = mutable.Map.empty[ContinuationIdentity, Claim]
    private val _completed = mutable.Set.empty[ContinuationIdentity]

    def stageSuspensionC(
      unitOfWork: UnitOfWork,
      continuation: Continuation
    ): Consequence[Unit] = stageSuspensionAfterC(unitOfWork, continuation, () => Consequence.unit)

    override def stageSuspensionAfterC(
      unitOfWork: UnitOfWork,
      continuation: Continuation,
      beforePublish: () => Consequence[Unit]
    ): Consequence[Unit] =
      if (unitOfWork == null || continuation == null)
        _invalid("continuation", "missing suspension boundary")
      else if (beforePublish == null)
        _invalid("continuation", "missing suspension persistence prerequisite")
      else if (_staged.contains(continuation.continuationId) || _available.contains(continuation.continuationId) || _completed.contains(continuation.continuationId))
        _invalid("continuation", s"duplicate continuation: ${continuation.continuationId.value}")
      else {
        _staged += continuation.continuationId
        unitOfWork.stagePostCommitC {
          _staged -= continuation.continuationId
          beforePublish().map { _ =>
            _available.update(continuation.continuationId, continuation)
            ()
          }
        }
        unitOfWork.stagePostAbortC {
          _staged -= continuation.continuationId
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
          val claim = Claim(continuation, UUID.randomUUID().toString)
          _claimed.update(identity, claim)
          Consequence.success(claim)
        case None => _invalid("continuation", s"continuation unavailable: ${identity.value}")
      }

    override def recoverClaimC(identity: ContinuationIdentity): Consequence[Claim] =
      if (identity == null || Option(identity.value).forall(_.trim.isEmpty))
        _invalid("continuation", "missing continuation identity")
      else _claimed.get(identity) match {
        case Some(claim) if validClaim(claim) && claim.continuation.continuationId == identity =>
          Consequence.success(claim)
        case _ => _invalid("continuation", s"continuation has no recoverable claim: ${identity.value}")
      }

    def resumeC(
      claim: Claim,
      result: StateMachineOperationResult,
      freshUnitOfWork: () => UnitOfWork
    ): Consequence[Resume] =
      _resume_c(claim, result, freshUnitOfWork, None)

    override def resumeWithProgramC(
      claim: Claim,
      result: StateMachineOperationResult,
      freshUnitOfWork: () => UnitOfWork,
      program: ExecUowM[ActionExecution]
    ): Consequence[Resume] =
      if (program == null) _invalid("continuation", "closing Action program is missing")
      else _resume_c(claim, result, freshUnitOfWork, Some(program))

    private def _resume_c(
      claim: Claim,
      result: StateMachineOperationResult,
      freshUnitOfWork: () => UnitOfWork,
      program: Option[ExecUowM[ActionExecution]]
    ): Consequence[Resume] =
      if (!validClaim(claim) || result == null || freshUnitOfWork == null)
        _invalid("continuation", "missing resume input")
      else {
        val identity = claim.continuation.continuationId
        if (_completed.contains(identity))
          _invalid("continuation", s"duplicate resume: ${identity.value}")
        else _available.get(identity) match {
          case None => _invalid("continuation", s"continuation unavailable: ${identity.value}")
          case Some(continuation) if !_claimed.contains(identity) =>
            _invalid("continuation", s"continuation not claimed: ${identity.value}")
          case Some(continuation) if _claimed(identity).claimId != claim.claimId =>
            _invalid("continuation", s"claim ownership mismatch: ${identity.value}")
          case Some(continuation) if continuation != claim.continuation =>
            _invalid("continuation", s"claim continuation mismatch: ${identity.value}")
          case Some(continuation) if !validResult(continuation, result) =>
            _claimed.remove(identity)
            _invalid("continuation", s"incompatible or incomplete result: ${identity.value}")
          case Some(continuation) =>
            try {
              Option(freshUnitOfWork()).map { uow =>
                stageClosingProgramC(uow, program) match {
                  case Consequence.Success(_) =>
                    uow.stagePostCommitC {
                      _completed += identity
                      _available -= identity
                      _claimed.remove(identity)
                      Consequence.unit
                    }
                    uow.commit() match {
                      case Consequence.Success(_) => Consequence.success(Resume(continuation, result))
                      case Consequence.Failure(conclusion) =>
                        _claimed.remove(identity)
                        Consequence.Failure[Resume](conclusion)
                    }
                  case Consequence.Failure(conclusion) =>
                    val aborted = try uow.abort() catch {
                      case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
                    }
                    aborted match {
                      case Consequence.Success(_) =>
                        _claimed.remove(identity)
                        Consequence.Failure[Resume](conclusion)
                      case Consequence.Failure(cleanup) => Consequence.Failure[Resume](cleanup ++ conclusion)
                    }
                }
              }.getOrElse {
                _claimed.remove(identity)
                _invalid("continuation", "fresh UnitOfWork factory returned null")
              }
            } catch {
              case e: Throwable =>
                _claimed.remove(identity)
                Consequence.Failure(Conclusion.from(e))
            }
        }
      }

    def finalizeC(claim: Claim): Consequence[Unit] =
      _invalid("continuation", "in-memory continuation completes inside resume")

    def isAvailable(identity: ContinuationIdentity): Boolean = _available.contains(identity)
    def isCompleted(identity: ContinuationIdentity): Boolean = _completed.contains(identity)
  }

  private def _invalid[A](kind: String, message: String): Consequence[A] =
    Consequence.Failure(Conclusion.from(new IllegalArgumentException(s"$kind: $message")))

  private[workflow] def stageClosingProgramC(
    uow: UnitOfWork,
    program: Option[ExecUowM[ActionExecution]]
  ): Consequence[Unit] = program match {
    case None => Consequence.unit
    case Some(value) =>
      new UnitOfWorkInterpreter(uow).evaluateInActiveUnitOfWorkC(value).flatMap {
        case _: ActionExecution.Completed => Consequence.unit
        case _: ActionExecution.Suspended =>
          Consequence.stateConflict("closing Action cannot suspend without a new Continuation boundary")
        case ActionExecution.Failed(failure) =>
          Consequence.stateConflict(s"closing Action failed: ${Option(failure).map(_.code).getOrElse("unknown")}")
      }
  }

  private[workflow] def validClaim(claim: Claim): Boolean =
    claim != null && claim.continuation != null &&
      claim.continuation.continuationId != null &&
      Option(claim.continuation.continuationId.value).exists(_.trim.nonEmpty) &&
      Option(claim.claimId).exists(_.trim.nonEmpty)

  private[workflow] def validResult(
    continuation: Continuation,
    result: StateMachineOperationResult
  ): Boolean =
    continuation != null && continuation.requiredOperation != null &&
      continuation.requiredOperation.resultType.exists(expected =>
        expected != null && Option(expected.value).exists(_.trim.nonEmpty) &&
          result != null && result.typeReference != null &&
          Option(result.typeReference.value).exists(_.trim.nonEmpty) &&
          expected == result.typeReference &&
          result.contextReference != null &&
          Option(result.contextReference.identity).exists(_.trim.nonEmpty) &&
          Option(result.contextReference.revision).exists(_.trim.nonEmpty)
      )
}
