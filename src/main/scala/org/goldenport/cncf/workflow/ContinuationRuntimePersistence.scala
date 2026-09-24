package org.goldenport.cncf.workflow

import java.util.UUID
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest
import scala.collection.mutable
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkTermination}
import org.goldenport.cncf.unitofwork.ExecUowM

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

  /** A caller-owned local directory with atomic per-record replacement.
    * The lock serializes claim transitions across reopened adapters and JVMs.
    * It is not a transaction with WorkflowInstance or UnitOfWork persistence.
    */
  final class LocalJson(root: Path) extends ContinuationRuntimePersistence {
    def createC(record: Record): Consequence[Unit] =
      ContinuationRecordJsonV1.encodeC(record).flatMap { encoded =>
        _locked_c { directory =>
          val identity = record.continuation.continuationId
          val destination = _path(directory, identity)
          if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
            _invalid(s"duplicate continuation: ${identity.value}")
          else _publish_c(directory, destination, encoded, replace = false)
        }
      }

    def loadC(identity: ContinuationIdentity): Consequence[Option[Record]] =
      if (!_identity(identity)) _invalid("missing continuation identity")
      else _locked_c(directory => _load_c(directory, identity))

    def claimC(identity: ContinuationIdentity): Consequence[Record] =
      _transition_c(identity) {
        case record if record.status == Status.Available =>
          Consequence.success(record.copy(status = Status.Claimed, claimId = Some(UUID.randomUUID().toString)))
        case record => _invalid(s"continuation cannot be claimed from ${record.status}")
      }

    def releaseC(identity: ContinuationIdentity, claimId: String): Consequence[Record] =
      _transition_c(identity) {
        case record if record.status == Status.Claimed && record.claimId.contains(claimId) =>
          Consequence.success(record.copy(status = Status.Available, claimId = None))
        case record => _invalid(s"continuation cannot be released from ${record.status}")
      }

    def completeC(identity: ContinuationIdentity, claimId: String): Consequence[Record] =
      _transition_c(identity) {
        case record if record.status == Status.Claimed && record.claimId.contains(claimId) =>
          Consequence.success(record.copy(status = Status.Completed))
        case record => _invalid(s"continuation cannot be completed from ${record.status}")
      }

    private def _transition_c(
      identity: ContinuationIdentity
    )(
      transition: Record => Consequence[Record]
    ): Consequence[Record] =
      if (!_identity(identity)) _invalid("missing continuation identity")
      else _locked_c { directory =>
        _load_c(directory, identity).flatMap {
          case Some(record) => transition(record).flatMap { next =>
            ContinuationRecordJsonV1.encodeC(next).flatMap { encoded =>
              _publish_c(directory, _path(directory, identity), encoded, replace = true).map(_ => next)
            }
          }
          case None => _invalid(s"continuation unavailable: ${identity.value}")
        }
      }

    private def _locked_c[A](body: Path => Consequence[A]): Consequence[A] =
      ContinuationRuntimePersistence.synchronized {
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
          _invalid("local Continuation directory is unavailable")
        else {
          val lockPath = root.resolve(".continuation.lock")
          try {
            val channel = FileChannel.open(lockPath,
              StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
            try {
              val lock = channel.lock()
              try body(root)
              finally lock.release()
            } finally channel.close()
          } catch {
            case NonFatal(e) => _invalid(s"local Continuation operation failed: ${e.getMessage}")
          }
        }
      }

    private def _load_c(directory: Path, identity: ContinuationIdentity): Consequence[Option[Record]] = {
      val path = _path(directory, identity)
      if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) Consequence.success(None)
      else if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        _invalid("local Continuation entry is not a regular file")
      else ContinuationRecordJsonV1.decodeC(Files.readString(path, StandardCharsets.UTF_8)).flatMap { record =>
        if (record.continuation.continuationId == identity) Consequence.success(Some(record))
        else _invalid("local Continuation identity differs from its key")
      }
    }

    private def _publish_c(
      directory: Path,
      destination: Path,
      encoded: String,
      replace: Boolean
    ): Consequence[Unit] = {
      var temporary: Path = null
      try {
        temporary = Files.createTempFile(directory, ".continuation-", ".tmp")
        Files.writeString(temporary, encoded, StandardCharsets.UTF_8)
        if (replace) {
          if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS))
            return _invalid("local Continuation entry is not a regular file")
          Files.move(temporary, destination,
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
          temporary = null
        } else Files.createLink(destination, temporary)
        Consequence.unit
      } catch {
        case NonFatal(e) => _invalid(s"local Continuation write failed: ${e.getMessage}")
      } finally {
        if (temporary != null) try Files.deleteIfExists(temporary) catch {
          case NonFatal(_) => ()
        }
      }
    }

    private def _path(directory: Path, identity: ContinuationIdentity): Path = {
      val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.value.getBytes(StandardCharsets.UTF_8))
        .map(byte => f"${byte & 0xff}%02x").mkString
      directory.resolve(s"$digest.continuation.json")
    }

    private def _identity(value: ContinuationIdentity): Boolean =
      value != null && value.value != null && value.value.trim.nonEmpty
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
  ): Consequence[Unit] =
    stageSuspensionAfterC(unitOfWork, continuation, () => Consequence.unit)

  override def stageSuspensionAfterC(
    unitOfWork: UnitOfWork,
    continuation: Continuation,
    beforePublish: () => Consequence[Unit]
  ): Consequence[Unit] = synchronized {
    if (unitOfWork == null || continuation == null)
      _invalid("missing suspension boundary")
    else if (beforePublish == null)
      _invalid("missing suspension persistence prerequisite")
    else if (persistence == null)
      _invalid("persistence is not configured")
    else if (_staged.contains(continuation.continuationId))
      _invalid(s"duplicate staged continuation: ${continuation.continuationId.value}")
    else {
      _staged += continuation.continuationId
      unitOfWork.stagePostCommitC {
        synchronized { _staged -= continuation.continuationId }
        beforePublish().flatMap(_ => persistence.createC(Record(continuation, Status.Available)))
      }
      unitOfWork.stagePostAbortC {
        synchronized { _staged -= continuation.continuationId }
        Consequence.unit
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

  override def recoverClaimC(identity: ContinuationIdentity): Consequence[Claim] =
    if (identity == null || Option(identity.value).forall(_.trim.isEmpty))
      _invalid("missing continuation identity")
    else if (persistence == null)
      _invalid("persistence is not configured")
    else persistence.loadC(identity).flatMap {
      case Some(record) if record != null && record.status == Status.Claimed =>
        Option(record.claimId).flatten match {
          case Some(claimId) =>
            val claim = Claim(record.continuation, claimId)
            if (ContinuationRuntime.validClaim(claim) && claim.continuation.continuationId == identity)
              Consequence.success(claim)
            else _invalid(s"stored claim is incomplete: ${identity.value}")
          case None => _invalid(s"stored claim has no ownership token: ${identity.value}")
        }
      case _ => _invalid(s"continuation has no recoverable claim: ${identity.value}")
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
    if (program == null) _invalid("closing Action program is missing")
    else _resume_c(claim, result, freshUnitOfWork, Some(program))

  private def _resume_c(
    claim: Claim,
    result: StateMachineOperationResult,
    freshUnitOfWork: () => UnitOfWork,
    program: Option[ExecUowM[ActionExecution]]
  ): Consequence[Resume] =
    if (!ContinuationRuntime.validClaim(claim) || result == null || freshUnitOfWork == null)
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
      case Some(record) if record.continuation != claim.continuation =>
        _invalid(s"claim continuation mismatch: ${identity.value}")
      case Some(record) if !ContinuationRuntime.validResult(record.continuation, result) =>
        persistence.releaseC(identity, claim.claimId).flatMap(_ =>
          _invalid(s"incompatible or incomplete result: ${identity.value}")
        )
      case Some(record) =>
        _fresh_unit_of_work_c(freshUnitOfWork) match {
          case Consequence.Success(uow) =>
            ContinuationRuntime.stageClosingProgramC(uow, program) match {
              case Consequence.Success(_) =>
                uow.stagePostCommitC(persistence.completeC(identity, claim.claimId).map(_ => ()))
                uow.commit() match {
                  case Consequence.Success(_) => Consequence.success(Resume(record.continuation, result))
                  case Consequence.Failure(conclusion) =>
                    if (uow.lastCommitTermination.contains(UnitOfWorkTermination.Aborted))
                      _release_failed_resume(identity, claim.claimId, conclusion)
                    else
                      Consequence.Failure[Resume](conclusion)
                }
              case Consequence.Failure(conclusion) =>
                val aborted = try uow.abort() catch {
                  case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
                }
                aborted match {
                  case Consequence.Success(_) => _release_failed_resume(identity, claim.claimId, conclusion)
                  case Consequence.Failure(cleanup) =>
                    Consequence.Failure[Resume](cleanup ++ conclusion)
                }
            }
          case Consequence.Failure(conclusion) =>
            _release_failed_resume(identity, claim.claimId, conclusion)
        }
      case None => _invalid(s"continuation unavailable: ${identity.value}")
      }
    }

  def finalizeC(claim: Claim): Consequence[Unit] =
    if (!ContinuationRuntime.validClaim(claim)) _invalid("missing completion claim")
    else if (persistence == null) _invalid("persistence is not configured")
    else persistence.completeC(claim.continuation.continuationId, claim.claimId).map(_ => ())

  private def _release_failed_resume(
    identity: ContinuationIdentity,
    claimid: String,
    conclusion: Conclusion
  ): Consequence[Resume] =
    persistence.releaseC(identity, claimid) match {
      case Consequence.Success(_) => Consequence.Failure(conclusion)
      case Consequence.Failure(releasefailure) =>
        Consequence.Failure(releasefailure ++ conclusion)
    }

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
