package org.goldenport.cncf.workflow

import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class ManagedCompletionUnitOfWorkSpec extends AnyWordSpec with Matchers {
  import ManagedCompletionParticipant.Outcome
  def fixture(outcome: Outcome[String], trace: ArrayBuffer[String], failCleanup: Boolean = false,
      nativeControl: Option[Throwable] = None) = {
    val native = new ManagedCompletionParticipant.Native[String] {
      def commit(tx: TransactionContext): Outcome[String] = { trace += "native"; nativeControl.foreach(throw _); outcome }
      def deliverC(value: String): Consequence[Unit] = { trace += s"deliver:$value"; Consequence.unit }
      def discardC(): Consequence[Unit] = { trace += "discard"; Consequence.unit }
    }
    val participant = ManagedCompletionParticipant.createC(native,
      Vector(() => { trace += "callback"; Consequence.unit }), () => { trace += "cleanup"; Consequence.unit }).toOption.get
    val uow = new ManagedCompletionUnitOfWork(ExecutionContext.create(), participant)
    uow.registerResourceC(new UnitOfWorkResource {
      def releaseC(termination: UnitOfWorkTermination): Consequence[Unit] = {
        trace += s"resource:$termination"
        if (failCleanup) Consequence.stateConflict("resource fixture") else Consequence.unit
      }
    }).isSuccess shouldBe true
    uow
  }
  "Explicit managed completion UnitOfWork" should {
    "retain abort cleanup failures for RuntimeContext consumers without invoking native commit" in {
      val trace = ArrayBuffer.empty[String]
      val uow = fixture(Outcome.Verified("not-committed"), trace, failCleanup = true)
      uow.abort().isSuccess shouldBe false
      uow.lastAbortResult.get.isSuccess shouldBe false
      trace.toVector shouldBe Vector("cleanup", "discard", "resource:Aborted")
      uow.commit().isSuccess shouldBe false
    }
    "commit once and finish callbacks and owned resources before delivery" in {
      val trace = ArrayBuffer.empty[String]
      val uow = fixture(Outcome.Verified("actual"), trace)
      uow.commit().isSuccess shouldBe true
      uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      trace.toVector shouldBe Vector("native", "callback", "cleanup", "resource:Committed", "deliver:actual", "discard")
      uow.commit().isSuccess shouldBe false
      uow.abort().isSuccess shouldBe false
      uow.dispose().isSuccess shouldBe true
      trace.count(_ == "native") shouldBe 1
      trace.count(_.startsWith("resource:")) shouldBe 1
    }
    "preserve uncertainty independently from rejection and run no delivery or success callbacks" in {
      Vector(Outcome.Indeterminate(Consequence.stateConflict[Unit]("uncertain").conclusion) -> UnitOfWorkTermination.Indeterminate,
        Outcome.Rejected(Consequence.stateConflict[Unit]("rejected").conclusion) -> UnitOfWorkTermination.Aborted).foreach { (outcome, termination) =>
        val trace = ArrayBuffer.empty[String]
        val uow = fixture(outcome, trace)
        uow.commit().isSuccess shouldBe false
        uow.lastCommitTermination shouldBe Some(termination)
        trace.toVector shouldBe Vector("native", "cleanup", s"resource:$termination", "discard")
        uow.abort().isSuccess shouldBe false
      }
    }
    "retain committed storage disposition while suppressing delivery on owned resource failure" in {
      val trace = ArrayBuffer.empty[String]
      val uow = fixture(Outcome.Verified("actual"), trace, failCleanup = true)
      uow.commit().isSuccess shouldBe false
      uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      trace should not contain "deliver:actual"
      trace.last shouldBe "discard"
    }
    "deny unenlisted legacy callbacks before native commit and never execute them as cleanup" in {
      val trace = ArrayBuffer.empty[String]
      val uow = fixture(Outcome.Verified("actual"), trace)
      uow.stagePostCommitC { trace += "legacy"; Consequence.unit }
      uow.commit().isSuccess shouldBe false
      uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Aborted)
      trace.toVector shouldBe Vector("cleanup", "discard", "resource:Aborted")
      uow.commit().isSuccess shouldBe false
    }
    "deny interpreter operations before execution, including after managed completion" in {
      val trace = ArrayBuffer.empty[String]
      val uow = fixture(Outcome.Verified("actual"), trace)
      val interpreter = new UnitOfWorkInterpreter(uow)
      interpreter.interpret(UnitOfWorkOp.HttpGet("https://not-executed.invalid")).isSuccess shouldBe false
      trace shouldBe empty
      uow.commit().isSuccess shouldBe true
      interpreter.interpret(UnitOfWorkOp.HttpGet("https://not-executed.invalid")).isSuccess shouldBe false
      trace.count(_ == "native") shouldBe 1
    }
    "finish uncertain resources and discard before rethrowing native interruption" in {
      val trace = ArrayBuffer.empty[String]
      val control = new InterruptedException("managed native fixture")
      val uow = fixture(Outcome.Verified("not-proven"), trace, nativeControl = Some(control))
      try {
        intercept[InterruptedException](uow.commit()) shouldBe control
        uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Indeterminate)
        Thread.currentThread().isInterrupted shouldBe true
        trace.toVector shouldBe Vector("native", "cleanup", "resource:Indeterminate", "discard")
        uow.commit().isSuccess shouldBe false
      } finally Thread.interrupted()
    }
  }
}
