package org.goldenport.cncf.workflow

import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.DomainEvent
import org.goldenport.cncf.unitofwork.{TransactionContext, UnitOfWork, UnitOfWorkTermination}

/** Explicit internal opt-in. No default engine replacement or public activation.
  * The prepared participant owns all enlisted data/events/closing effects.
  * Other pending UoW effects reject; none are committed independently.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[cncf] final class ManagedCompletionUnitOfWork[A](context: ExecutionContext,
    participant: ManagedCompletionParticipant[A]) extends UnitOfWork(context) {
  import ManagedCompletionParticipant.Outcome
  private val owner = Thread.currentThread()
  private var consumed = false
  private var termination = UnitOfWorkTermination.Aborted
  private var storage: Option[Outcome[A]] = None
  def storageOutcome: Option[Outcome[A]] = storage

  override private[cncf] def executionOperationAdmissionC: Consequence[Unit] =
    Consequence.stateConflict("Managed completion cannot interpret unenlisted UnitOfWork operations")

  override def withContext(ctx: ExecutionContext): UnitOfWork =
    throw new IllegalStateException("A managed completion cannot be copied to a different UnitOfWork")

  override def commit(): Consequence[Unit] = commit(Vector.empty)
  override def commit(events: Seq[DomainEvent]): Consequence[Unit] = {
    if (consumed || Thread.currentThread() != owner || participant == null)
      return Consequence.stateConflict("Managed UnitOfWork is missing, foreign or finalized")
    consumed = true // Before any preparation/native call; no reentrant retry.
    participant.unattemptedC match {
      case Consequence.Failure(primary) =>
        termination = UnitOfWorkTermination.Indeterminate
        val rejected = completeManagedResourcesC(termination) match {
          case Consequence.Failure(cleanup) => Consequence.Failure[Unit](cleanup ++ primary)
          case _ => Consequence.Failure[Unit](primary)
        }
        recordManagedCommit(rejected, termination)
        return rejected
      case _ => ()
    }
    val tx = TransactionContext.create(context.transactionContext, context.clock, context.idGeneration)
    var control: Option[Throwable] = None
    val result: Consequence[Unit] = try {
      val admitted = if (events == null || events.nonEmpty)
        Consequence.stateConflict[Unit]("Managed completion rejects unenlisted event input")
        else managedCompletionAdmissionC
      admitted.flatMap(_ => participant.prepareC(tx)) match {
        case Consequence.Failure(primary) =>
          cancelLocalC(UnitOfWorkTermination.Aborted) match {
            case Consequence.Failure(cleanup) => Consequence.Failure(cleanup ++ primary)
            case _ => Consequence.Failure(primary)
          }
        case _ =>
          try participant.commitC(tx) catch {
            case e: Throwable => control = Some(e)
          }
          storage = participant.storageOutcomeC(tx).toOption
          termination = storage match {
            case Some(_: Outcome.Verified[?]) => UnitOfWorkTermination.Committed
            case Some(_: Outcome.Rejected) => UnitOfWorkTermination.Aborted
            case _ => UnitOfWorkTermination.Indeterminate
          }
          val finished = participant.finishWithResourcesC(tx, _ => completeManagedResourcesC(termination))
          completeManagedResourcesC(termination) match {
            case Consequence.Failure(extra) => finished match {
              case Consequence.Failure(primary) => Consequence.Failure(extra ++ primary)
              case _ => Consequence.Failure(extra)
            }
            case _ => finished
          }
      }
    } catch {
      case e: Throwable =>
        if (control.isEmpty) control = Some(e)
        val cleanup = try completeManagedResourcesC(termination) catch {
          case secondary: Throwable =>
            if (secondary ne e) e.addSuppressed(secondary)
            Consequence.Failure[Unit](Conclusion.from(secondary))
        }
        cleanup match {
          case Consequence.Failure(extra) => Consequence.Failure(extra ++ Conclusion.from(e))
          case _ => Consequence.Failure(Conclusion.from(e))
        }
    }
    recordManagedCommit(result, termination)
    control.foreach { original =>
      result match {
        case Consequence.Failure(conclusion) => conclusion.causes.flatMap(_.getException).filter(_ ne original).foreach { extra =>
          try original.addSuppressed(extra) catch { case NonFatal(_) => () }
        }
        case _ => ()
      }
      if (original.isInstanceOf[InterruptedException]) Thread.currentThread().interrupt()
      throw original
    }
    result
  }

  /** Before a native attempt, disposal only drops local UoW resources. After an
    * attempt it never invokes native abort, claim release or compensation.
    */
  override def abort(): Consequence[Unit] =
    if (consumed || Thread.currentThread() != owner)
      Consequence.stateConflict("Managed UnitOfWork cannot abort a finalized or foreign attempt")
    else {
      consumed = true
      val result = cancelLocalC(UnitOfWorkTermination.Aborted)
      recordManagedAbort(result)
      result
    }
  override def rollback(): Consequence[Unit] = abort()
  override def dispose(): Consequence[Unit] =
    if (Thread.currentThread() != owner) Consequence.stateConflict("Managed UnitOfWork belongs to its original thread")
    else {
      val unconsumed = !consumed
      consumed = true
      if (unconsumed) cancelLocalC(UnitOfWorkTermination.Disposed)
      else completeManagedResourcesC(if (lastCommitTermination.nonEmpty) termination else UnitOfWorkTermination.Disposed)
    }

  private def cancelLocalC(end: UnitOfWorkTermination): Consequence[Unit] = {
    var control: Option[Throwable] = None
    def attempt(action: => Consequence[Unit]): Consequence[Unit] =
      try action catch {
        case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
        case e: Throwable =>
          if (control.isEmpty) control = Some(e)
          Consequence.Failure(Conclusion.from(e))
      }
    val canceled = attempt {
      if (participant == null) Consequence.unit else participant.cancelUnpreparedC()
    }
    val released = attempt(completeManagedResourcesC(end))
    val result = (canceled, released) match {
      case (Consequence.Failure(primary), Consequence.Failure(cleanup)) => Consequence.Failure(cleanup ++ primary)
      case (failure: Consequence.Failure[Unit], _) => failure
      case (_, failure: Consequence.Failure[Unit]) => failure
      case _ => Consequence.unit
    }
    control.foreach { original =>
      result match {
        case Consequence.Failure(conclusion) => conclusion.causes.flatMap(_.getException).filter(_ ne original).foreach { extra =>
          try original.addSuppressed(extra) catch { case NonFatal(_) => () }
        }
        case _ => ()
      }
      if (original.isInstanceOf[InterruptedException]) Thread.currentThread().interrupt()
      throw original
    }
    result
  }
}
