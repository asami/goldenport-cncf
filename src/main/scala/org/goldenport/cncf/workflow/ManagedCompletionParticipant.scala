package org.goldenport.cncf.workflow

import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.unitofwork.TransactionContext

/** Isolated internal participant lifecycle. Not installed in UnitOfWork,
  * EventEngine or a public Operation. Native implementations must separately
  * prove single-domain enlistment; this coordinator cannot manufacture it.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[cncf] final class ManagedCompletionParticipant[A] private (
  native: ManagedCompletionParticipant.Native[A],
  callbacks: Vector[() => Consequence[Unit]],
  cleanup: () => Consequence[Unit]
) {
  import ManagedCompletionParticipant.*
  private val owner = Thread.currentThread()
  private var transaction: Option[TransactionContext] = None
  private var phase = Phase.Initial
  private var outcome: Option[Outcome[A]] = None

  /** Bind one actual transaction object, without executing native effects. */
  def prepareC(tx: TransactionContext): Consequence[Unit] =
    checkThreadC.flatMap { _ =>
      if (tx == null || tx.id == null || tx.tx == null || phase != Phase.Initial)
        Consequence.stateConflict("Managed participant cannot prepare this transaction")
      else {
        transaction = Some(tx)
        phase = Phase.Prepared
        Consequence.unit
      }
    }

  /** Consume the attempt BEFORE calling the trusted native boundary. */
  def commitC(tx: TransactionContext): Consequence[Outcome[A]] =
    checkC(tx, Phase.Prepared).map { _ =>
      phase = Phase.Committing
      var control: Option[Throwable] = None
      val actual = try {
        native.commit(tx) match {
          case verified @ Outcome.Verified(value) if value != null => verified
          case rejected: Outcome.Rejected if rejected.conclusion != null => rejected
          case uncertain: Outcome.Indeterminate if uncertain.conclusion != null => uncertain
          case _ => indeterminate("Native managed commit returned incomplete evidence")
        }
      } catch {
        case NonFatal(e) => indeterminate("Native managed commit threw without an authoritative outcome", Some(Conclusion.from(e)))
        case e: Throwable =>
          control = Some(e)
          indeterminate("Native managed commit interrupted without an authoritative outcome", Some(Conclusion.from(e)))
      }
      outcome = Some(actual)
      phase = Phase.Committed
      control.foreach(rethrow)
      actual
    }

  def storageOutcomeC(tx: TransactionContext): Consequence[Outcome[A]] =
    checkThreadC.flatMap { _ =>
      if (!sameTransaction(tx) || outcome.isEmpty) Consequence.stateConflict("Managed storage outcome is unavailable")
      else Consequence.success(outcome.get)
    }

  /** Coordinator runs the frozen callbacks and cleanup itself. No supplied
    * success Boolean, reconstructed permit, or caller-selected delivery route.
    * Terminal state precedes every callback, so reentrancy cannot replay work.
    */
  def finishC(tx: TransactionContext): Consequence[Unit] =
    finishWithResourcesC(tx, _ => Consequence.unit)

  /** UoW-owned operational resources finish after callbacks and before delivery.
    * The coordinator supplies its actual storage outcome, never a success flag.
    */
  private[cncf] def finishWithResourcesC(tx: TransactionContext,
      release: Outcome[A] => Consequence[Unit]): Consequence[Unit] =
    if (release == null) Consequence.configurationInvalid("Managed resource completion is missing")
    else
    checkC(tx, Phase.Committed).flatMap { _ =>
      phase = Phase.Terminal
      outcome.get match {
        case Outcome.Verified(value) => finalizeC(Consequence.unit, true, Some(value), () => release(outcome.get))
        case Outcome.Rejected(conclusion) => finalizeC(Consequence.Failure[Unit](conclusion), false, None, () => release(outcome.get))
        case Outcome.Indeterminate(conclusion) => finalizeC(Consequence.Failure[Unit](conclusion), false, None, () => release(outcome.get))
      }
    }

  /** Only a never-attempted prepared participant can abort. Discard and cleanup
    * are local lifecycle actions; no compensation or native commit is called.
    */
  def abortC(tx: TransactionContext): Consequence[Unit] =
    checkC(tx, Phase.Prepared).flatMap { _ =>
      phase = Phase.Terminal
      finalizeC(Consequence.unit, false, None)
    }

  private[cncf] def cancelUnpreparedC(): Consequence[Unit] =
    checkThreadC.flatMap { _ =>
      if (phase != Phase.Initial) Consequence.stateConflict("Managed preparation cannot cancel an attempted participant")
      else {
        phase = Phase.Terminal
        finalizeC(Consequence.unit, false, None)
      }
    }

  private[cncf] def unattemptedC: Consequence[Unit] =
    checkThreadC.flatMap { _ =>
      if (phase == Phase.Initial) Consequence.unit
      else Consequence.stateConflict("Managed participant is already owned by another lifecycle")
    }

  private def finalizeC(primary: Consequence[Unit], runCallbacks: Boolean, value: Option[A],
      resources: () => Consequence[Unit] = () => Consequence.unit): Consequence[Unit] = {
    var control: Option[Throwable] = None
    def attempt(action: => Consequence[Unit]): Consequence[Unit] =
      try Option(action).getOrElse(Consequence.stateConflict("Managed lifecycle action returned no outcome"))
      catch {
        case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
        case e: Throwable =>
          if (control.isEmpty) control = Some(e)
          Consequence.Failure(Conclusion.from(e))
      }
    val completed = if (runCallbacks) callbacks.foldLeft(primary) { (acc, callback) =>
      if (control.nonEmpty) acc else combine(acc, attempt(callback()))
    } else primary
    val cleaned = combine(combine(completed, attempt(cleanup())), attempt(resources()))
    val delivered = if (cleaned.isSuccess && control.isEmpty)
      value.map(v => attempt(native.deliverC(v))).getOrElse(Consequence.unit)
      else Consequence.unit
    val result = combine(combine(cleaned, delivered), attempt(native.discardC()))
    control.foreach { original =>
      result match {
        case Consequence.Failure(conclusion) => conclusion.causes.flatMap(_.getException).filter(_ ne original).foreach { secondary =>
          try original.addSuppressed(secondary) catch { case NonFatal(_) => () }
        }
        case _ => ()
      }
      rethrow(original)
    }
    result
  }

  private def checkThreadC: Consequence[Unit] =
    if (Thread.currentThread() eq owner) Consequence.unit
    else Consequence.stateConflict("Managed participant belongs to its original thread")

  private def sameTransaction(tx: TransactionContext): Boolean =
    tx != null && transaction.exists(_ eq tx)

  private def checkC(tx: TransactionContext, required: Phase): Consequence[Unit] =
    checkThreadC.flatMap { _ =>
      if (sameTransaction(tx) && phase == required) Consequence.unit
      else Consequence.stateConflict("Managed participant transaction or lifecycle differs")
    }
}

private[cncf] object ManagedCompletionParticipant {
  /** Trusted boundary, NOT a claim that supplied callbacks form a transaction.
    * Rejected requires proven no publication; lack of such proof is Indeterminate.
    * Delivery consumes only the boundary's original-order local batch. Discard
    * must never mutate native storage or release/replay an uncertain claim.
    */
  trait Native[A] {
    def commit(tx: TransactionContext): Outcome[A]
    def deliverC(value: A): Consequence[Unit]
    def discardC(): Consequence[Unit]
  }
  sealed trait Outcome[+A]
  object Outcome {
    final case class Verified[A](value: A) extends Outcome[A]
    final case class Rejected(conclusion: Conclusion) extends Outcome[Nothing]
    final case class Indeterminate(conclusion: Conclusion) extends Outcome[Nothing]
  }
  private enum Phase { case Initial, Prepared, Committing, Committed, Terminal }

  def createC[A](native: Native[A], callbacks: Vector[() => Consequence[Unit]],
      cleanup: () => Consequence[Unit]): Consequence[ManagedCompletionParticipant[A]] =
    if (native == null || callbacks == null || callbacks.exists(_ == null) || cleanup == null)
      Consequence.configurationInvalid("Managed participant dependencies are incomplete")
    else Consequence.success(new ManagedCompletionParticipant(native, callbacks, cleanup))

  private def rethrow(control: Throwable): Nothing = {
    if (control.isInstanceOf[InterruptedException]) Thread.currentThread().interrupt()
    throw control
  }

  private def combine(primary: Consequence[Unit], secondary: Consequence[Unit]): Consequence[Unit] =
    secondary match {
      case Consequence.Failure(extra) => primary match {
        case Consequence.Failure(original) => Consequence.Failure(extra ++ original)
        case _ => Consequence.Failure(extra)
      }
      case _ => primary
    }

  private def indeterminate(message: String, extra: Option[Conclusion] = None): Outcome.Indeterminate =
    {
      val conclusion = DataStoreConditionalTransitionFailure.transactionIndeterminate[Unit](message).conclusion
      Outcome.Indeterminate(extra.map(_ ++ conclusion).getOrElse(conclusion))
    }
}
