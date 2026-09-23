package org.goldenport.cncf.workflow

import java.util.UUID
import scala.collection.mutable
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.unitofwork.UnitOfWork

/** Component/Factory IoC hook for the continuation runtime. */
trait ContinuationRuntimeSource {
  def continuationRuntimeOption: Option[ContinuationRuntime] = None
}

/**
 * Durable continuation-state boundary. Implementations own atomic claim and
 * completion transitions; it deliberately does not alter WorkflowInstancePersistence.
 */
trait ContinuationRuntimePersistence {
  import ContinuationRuntimePersistence.*

  def createC(record: Record): Consequence[Unit]
  def loadC(identity: ContinuationIdentity): Consequence[Option[Record]]
  def claimC(identity: ContinuationIdentity): Consequence[Record]
  def releaseC(identity: ContinuationIdentity, claimId: String): Consequence[Record]
  def completeC(identity: ContinuationIdentity, claimId: String): Consequence[Record]
}

object ContinuationRuntimePersistence {
  enum Status {
    case Available, Claimed, Completed
  }

  final case class Record(
    continuation: Continuation,
    status: Status,
    claimId: Option[String] = None
  )

  /** Shared deterministic store used by the focused runtime fixture. */
  final class InMemory extends ContinuationRuntimePersistence {
    private val _records = mutable.Map.empty[ContinuationIdentity, Record]

    def createC(record: Record): Consequence[Unit] = synchronized {
      if (record == null || record.continuation == null)
        _invalid("missing continuation record")
      else if (_records.contains(record.continuation.continuationId))
        _invalid(s"duplicate continuation: ${record.continuation.continuationId.value}")
      else {
        _records.update(record.continuation.continuationId, record)
        Consequence.unit
      }
    }

    def loadC(identity: ContinuationIdentity): Consequence[Option[Record]] = synchronized {
      if (identity == null) _invalid("missing continuation identity")
      else Consequence.success(_records.get(identity))
    }

    def claimC(identity: ContinuationIdentity): Consequence[Record] = synchronized {
      _records.get(identity) match {
        case Some(record) if record.status == Status.Available =>
          val claimed = record.copy(status = Status.Claimed, claimId = Some(UUID.randomUUID().toString))
          _records.update(identity, claimed)
          Consequence.success(claimed)
        case Some(record) => _invalid(s"continuation cannot be claimed from ${record.status}")
        case None => _invalid(s"continuation unavailable: ${Option(identity).map(_.value).getOrElse("")}")
      }
    }

    def releaseC(identity: ContinuationIdentity, claimId: String): Consequence[Record] = synchronized {
      _records.get(identity) match {
        case Some(record) if record.status == Status.Claimed && record.claimId.contains(claimId) =>
          val available = record.copy(status = Status.Available, claimId = None)
          _records.update(identity, available)
          Consequence.success(available)
        case Some(record) => _invalid(s"continuation cannot be released from ${record.status}")
        case None => _invalid(s"continuation unavailable: ${Option(identity).map(_.value).getOrElse("")}")
      }
    }

    def completeC(identity: ContinuationIdentity, claimId: String): Consequence[Record] = synchronized {
      _records.get(identity) match {
        case Some(record) if record.status == Status.Claimed && record.claimId.contains(claimId) =>
          val completed = record.copy(status = Status.Completed)
          _records.update(identity, completed)
          Consequence.success(completed)
        case Some(record) => _invalid(s"continuation cannot be completed from ${record.status}")
        case None => _invalid(s"continuation unavailable: ${Option(identity).map(_.value).getOrElse("")}")
      }
    }
  }

  private[workflow] def _invalid[A](message: String): Consequence[A] =
    Consequence.Failure(Conclusion.from(new IllegalArgumentException(s"continuation persistence: $message")))
}

/**
 * Runtime backed by a continuation persistence boundary. A recreated runtime
 * observes the same pending work and therefore preserves the restart boundary.
 */
final class PersistentContinuationRuntime(
  persistence: ContinuationRuntimePersistence
) extends ContinuationRuntime {
  import ContinuationRuntime.*
  import ContinuationRuntimePersistence.*

  private val _staged = mutable.Set.empty[ContinuationIdentity]

  def stageSuspensionC(
    unitOfWork: UnitOfWork,
    continuation: Continuation
  ): Consequence[Unit] = synchronized {
    if (unitOfWork == null || continuation == null)
      _invalid("missing suspension boundary")
    else if (persistence == null)
      _invalid("persistence is not configured")
    else if (_staged.contains(continuation.continuationId))
      _invalid(s"duplicate staged continuation: ${continuation.continuationId.value}")
    else {
      _staged += continuation.continuationId
      unitOfWork.stagePostCommitC {
        synchronized { _staged -= continuation.continuationId }
        persistence.createC(Record(continuation, Status.Available))
      }
      Consequence.unit
    }
  }

  def claimC(identity: ContinuationIdentity): Consequence[Claim] =
    if (persistence == null) _invalid("persistence is not configured")
    else persistence.claimC(identity).flatMap { record =>
      record.claimId.map(claimId => Consequence.success(Claim(record.continuation, claimId))).getOrElse(
        _invalid("persistence returned a claim without an ownership token")
      )
    }

  def resumeC(
    claim: Claim,
    result: StateMachineOperationResult,
    freshUnitOfWork: () => UnitOfWork
  ): Consequence[Resume] =
    if (claim == null || claim.continuation == null || result == null || freshUnitOfWork == null)
      _invalid("missing resume input")
    else if (persistence == null)
      _invalid("persistence is not configured")
    else {
      val identity = claim.continuation.continuationId
      persistence.loadC(identity).flatMap {
      case Some(record) if record.status != Status.Claimed =>
        _invalid(s"continuation is not claimed: ${identity.value}")
      case Some(record) if !record.claimId.contains(claim.claimId) =>
        _invalid(s"claim ownership mismatch: ${identity.value}")
      case Some(record) if record.continuation.requiredOperation.resultType.exists(_ != result.typeReference) =>
        persistence.releaseC(identity, claim.claimId).flatMap(_ =>
          _invalid(s"incompatible result type: ${identity.value}")
        )
      case Some(record) =>
        _fresh_unit_of_work_c(freshUnitOfWork) match {
          case Consequence.Success(uow) =>
            uow.stagePostCommitC(persistence.completeC(identity, claim.claimId).map(_ => ()))
            uow.commit() match {
              case Consequence.Success(_) => Consequence.success(Resume(record.continuation, result))
              case Consequence.Failure(conclusion) => Consequence.Failure[Resume](conclusion)
            }
          case Consequence.Failure(conclusion) =>
            persistence.releaseC(identity, claim.claimId).flatMap(_ => Consequence.Failure[Resume](conclusion))
        }
      case None => _invalid(s"continuation unavailable: ${identity.value}")
      }
    }

  def finalizeC(claim: Claim): Consequence[Unit] =
    if (claim == null || claim.continuation == null) _invalid("missing completion claim")
    else if (persistence == null) _invalid("persistence is not configured")
    else persistence.completeC(claim.continuation.continuationId, claim.claimId).map(_ => ())

  private def _fresh_unit_of_work_c(
    factory: () => UnitOfWork
  ): Consequence[UnitOfWork] =
    try {
      Option(factory()).map(Consequence.success).getOrElse(
        _invalid("fresh UnitOfWork factory returned null")
      )
    } catch {
      case e: Throwable => Consequence.Failure(Conclusion.from(e))
    }
}
