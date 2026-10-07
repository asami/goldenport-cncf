package org.goldenport.cncf.workflow

import scala.collection.mutable.ArrayBuffer
import java.util.concurrent.atomic.AtomicBoolean
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.TransactionContext
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Isolated coordinator conformance, not native atomicity or production UoW evidence.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class ManagedCompletionParticipantSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ManagedCompletionParticipant.*
  private val context = ExecutionContext.create()
  private def tx = TransactionContext.create(context.transactionContext, context.clock, context.idGeneration)
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected failure: $other")
  }
  private def failure(message: String): Conclusion = Consequence.stateConflict[Unit](message) match {
    case Consequence.Failure(conclusion) => conclusion
    case _ => fail("missing failure")
  }
  private class Fixture {
    val log = ArrayBuffer.empty[String]
    val batch = Vector("retained", "transient", "new")
    var nativeOutcome: () => Outcome[Vector[String]] = () => Outcome.Verified(batch)
    var callback: () => Consequence[Unit] = () => Consequence.unit
    var cleanup: () => Consequence[Unit] = () => Consequence.unit
    var delivery: () => Consequence[Unit] = () => Consequence.unit
    var discard: () => Consequence[Unit] = () => Consequence.unit
    val native = new Native[Vector[String]] {
      def commit(transaction: TransactionContext): Outcome[Vector[String]] = { log += "commit"; nativeOutcome() }
      def deliverC(value: Vector[String]): Consequence[Unit] = {
        log ++= value.map("deliver:" + _)
        delivery()
      }
      def discardC(): Consequence[Unit] = { log += "discard"; discard() }
    }
    val participant = take(createC(native, Vector(
      () => { log += "callback:1"; callback() },
      () => { log += "callback:2"; Consequence.unit }),
      () => { log += "cleanup"; cleanup() }))
  }

  "Isolated managed completion participant" should {
    "retain uncertain native state when control interrupts commit and never replay the attempt" in {
      Vector(false, true).foreach { interrupted =>
        Given("a trusted native boundary raising control instead of returning commit evidence")
        val f = new Fixture
        val control: Throwable = if (interrupted) new InterruptedException("fixture interruption") else new LinkageError("fixture linkage")
        f.nativeOutcome = () => throw control
        val transaction = tx
        take(f.participant.prepareC(transaction))
        When("control escapes from the single native attempt")
        try {
          intercept[Throwable](f.participant.commitC(transaction)) shouldBe control
          if (interrupted) Thread.currentThread().isInterrupted shouldBe true
        } finally {
          if (interrupted) Thread.interrupted()
        }
        Then("the retained outcome is uncertain and only local cleanup/discard can follow")
        take(f.participant.storageOutcomeC(transaction)) shouldBe a[Outcome.Indeterminate]
        f.participant.finishC(transaction).toOption shouldBe None
        f.participant.commitC(transaction).toOption shouldBe None
        f.log.toVector shouldBe Vector("commit", "cleanup", "discard")
      }
    }

    "finish local cleanup before rethrowing callback, cleanup or delivery control without replay" in {
      Vector("callback", "cleanup", "delivery", "discard").foreach { stage =>
        Vector(false, true).foreach { interrupted =>
          Given(s"verified storage followed by control in $stage")
          val f = new Fixture
          val control: Throwable = if (interrupted) new InterruptedException("fixture control") else new LinkageError("fixture control")
          val action: () => Consequence[Unit] = () => throw control
          stage match {
            case "callback" => f.callback = action
            case "cleanup" => f.cleanup = action
            case "delivery" => f.delivery = action
            case "discard" => f.discard = action
          }
          val transaction = tx
          take(f.participant.prepareC(transaction))
          take(f.participant.commitC(transaction))
          When("completion is interrupted after native verification")
          try {
            intercept[Throwable](f.participant.finishC(transaction)) shouldBe control
            if (interrupted) Thread.currentThread().isInterrupted shouldBe true
          } finally {
            if (interrupted) Thread.interrupted()
          }
          Then("cleanup and discard still run once, storage stays verified and terminal work cannot replay")
          f.log.count(_ == "cleanup") shouldBe 1
          f.log.count(_ == "discard") shouldBe 1
          if (stage == "callback") f.log should not contain "callback:2"
          if (stage == "callback" || stage == "cleanup") f.log.exists(_.startsWith("deliver:")) shouldBe false
          take(f.participant.storageOutcomeC(transaction)) shouldBe Outcome.Verified(f.batch)
          val before = f.log.toVector
          f.participant.finishC(transaction).toOption shouldBe None
          f.log.toVector shouldBe before
        }
      }
    }

    "fix one transaction and admit ordered delivery only after callbacks and cleanup succeed" in {
      Given("a non-activated participant with a test-only native verified boundary")
      val f = new Fixture
      val transaction = tx
      take(f.participant.prepareC(transaction))
      f.log shouldBe empty
      f.participant.finishC(transaction).toOption shouldBe None
      When("native storage verifies before participant completion")
      take(f.participant.commitC(transaction)) shouldBe Outcome.Verified(f.batch)
      f.log.toVector shouldBe Vector("commit")
      Then("no delivery is granted by storage verification alone")
      take(f.participant.finishC(transaction))
      f.log.toVector shouldBe Vector("commit", "callback:1", "callback:2", "cleanup",
        "deliver:retained", "deliver:transient", "deliver:new", "discard")
      take(f.participant.storageOutcomeC(transaction)) shouldBe Outcome.Verified(f.batch)
      val before = f.log.toVector
      f.participant.commitC(transaction).toOption shouldBe None
      f.participant.finishC(transaction).toOption shouldBe None
      f.participant.abortC(transaction).toOption shouldBe None
      f.log.toVector shouldBe before
    }

    "preserve rejected and indeterminate native evidence without callbacks, delivery or compensation" in {
      Vector(false, true).foreach { uncertain =>
        Given("a trusted boundary reporting rejection or uncertain native completion")
        val f = new Fixture
        val evidence = failure(if (uncertain) "uncertain" else "no-publication")
        val expected = if (uncertain) Outcome.Indeterminate(evidence) else Outcome.Rejected(evidence)
        f.nativeOutcome = () => expected
        val transaction = tx
        take(f.participant.prepareC(transaction))
        When("the single commit attempt returns its native evidence")
        take(f.participant.commitC(transaction)) shouldBe expected
        f.participant.abortC(transaction).toOption shouldBe None
        f.participant.finishC(transaction).toOption shouldBe None
        Then("only local cleanup and discard run, and the native disposition is unchanged")
        f.log.toVector shouldBe Vector("commit", "cleanup", "discard")
        take(f.participant.storageOutcomeC(transaction)) shouldBe expected
        f.participant.commitC(transaction).toOption shouldBe None
        f.log.count(_ == "commit") shouldBe 1
      }
    }

    "consume missing, malformed or throwing native outcomes as indeterminate without replay" in {
      Vector("null", "null-value", "null-rejection", "null-uncertainty", "throw").foreach { fault =>
        Given(s"a test native boundary with $fault return behavior")
        val f = new Fixture
        f.nativeOutcome = () => fault match {
          case "null" => null
          case "null-value" => Outcome.Verified(null)
          case "null-rejection" => Outcome.Rejected(null)
          case "null-uncertainty" => Outcome.Indeterminate(null)
          case "throw" => throw new IllegalStateException("fixture native")
        }
        val transaction = tx
        take(f.participant.prepareC(transaction))
        When("the boundary is attempted once")
        take(f.participant.commitC(transaction)) shouldBe a[Outcome.Indeterminate]
        f.participant.finishC(transaction).toOption shouldBe None
        Then("uncertainty never becomes a proven abort or a delivery permit")
        f.log.toVector shouldBe Vector("commit", "cleanup", "discard")
        f.participant.commitC(transaction).toOption shouldBe None
      }
    }

    "suppress delivery on callback or cleanup failure while retaining verified storage disposition" in {
      Vector("callback-failure", "callback-throw", "callback-null", "cleanup-failure", "cleanup-throw", "cleanup-null").foreach { fault =>
        Given(s"verified native storage followed by $fault")
        val f = new Fixture
        def faulty(): Consequence[Unit] =
          if (fault.endsWith("throw")) throw new IllegalStateException("fixture completion")
          else if (fault.endsWith("null")) null
          else Consequence.stateConflict("fixture completion")
        if (fault.startsWith("callback")) f.callback = () => faulty() else f.cleanup = () => faulty()
        val transaction = tx
        take(f.participant.prepareC(transaction))
        take(f.participant.commitC(transaction))
        When("the participant runs completion and local cleanup")
        f.participant.finishC(transaction).toOption shouldBe None
        Then("no record is delivered and cleanup/discard occur exactly once without storage compensation")
        f.log.toVector shouldBe Vector("commit", "callback:1", "callback:2", "cleanup", "discard")
        take(f.participant.storageOutcomeC(transaction)) shouldBe Outcome.Verified(f.batch)
        f.participant.finishC(transaction).toOption shouldBe None
      }
    }

    "never retry a failed delivery or discard after entering terminal completion" in {
      Vector("delivery", "discard").foreach { fault =>
        Given(s"a verified native result and $fault failure")
        val f = new Fixture
        if (fault == "delivery") f.delivery = () => Consequence.stateConflict("fixture delivery")
        else f.discard = () => Consequence.stateConflict("fixture discard")
        val transaction = tx
        take(f.participant.prepareC(transaction))
        take(f.participant.commitC(transaction))
        When("the completion attempt fails after delivery admission")
        f.participant.finishC(transaction).toOption shouldBe None
        val before = f.log.toVector
        f.participant.finishC(transaction).toOption shouldBe None
        f.participant.commitC(transaction).toOption shouldBe None
        Then("delivery and discard are not repeated and stored disposition remains Verified")
        f.log.toVector shouldBe before
        f.log.count(_ == "discard") shouldBe 1
        take(f.participant.storageOutcomeC(transaction)) shouldBe Outcome.Verified(f.batch)
      }
    }

    "deny a foreign transaction, wrong thread and reentrant calls before any extra native effects" in {
      Given("one prepared participant bound to its original transaction object and thread")
      val f = new Fixture
      val transaction = tx
      take(f.participant.prepareC(transaction))
      f.participant.commitC(transaction.copy()).toOption shouldBe None
      f.participant.prepareC(transaction).toOption shouldBe None
      val rejected = new AtomicBoolean(false)
      val thread = new Thread(() => rejected.set(f.participant.commitC(transaction).toOption.isEmpty))
      thread.start()
      thread.join(5000L)
      thread.isAlive shouldBe false
      rejected.get shouldBe true
      f.log shouldBe empty
      f.nativeOutcome = () => {
        f.participant.commitC(transaction).toOption shouldBe None
        f.participant.finishC(transaction).toOption shouldBe None
        Outcome.Verified(f.batch)
      }
      f.callback = () => {
        f.participant.finishC(transaction).toOption shouldBe None
        f.participant.commitC(transaction).toOption shouldBe None
        Consequence.unit
      }
      When("the authorized thread commits and finishes with reentrant test callbacks")
      take(f.participant.commitC(transaction))
      take(f.participant.finishC(transaction))
      Then("native commit and delivery each occur once")
      f.log.count(_ == "commit") shouldBe 1
      f.log.count(_ == "deliver:transient") shouldBe 1
    }

    "abort only a never-attempted preparation and reject incomplete dependency bindings" in {
      Given("one participant with no native commit attempt")
      val f = new Fixture
      f.participant.prepareC(null).toOption shouldBe None
      val transaction = tx
      take(f.participant.prepareC(transaction))
      When("the participant is explicitly aborted before committing")
      take(f.participant.abortC(transaction))
      Then("only memory cleanup/discard runs and later commit/delivery are denied")
      f.log.toVector shouldBe Vector("cleanup", "discard")
      f.participant.commitC(transaction).toOption shouldBe None
      f.participant.finishC(transaction).toOption shouldBe None
      createC[Vector[String]](null, Vector.empty, () => Consequence.unit).toOption shouldBe None
      createC(f.native, null, () => Consequence.unit).toOption shouldBe None
      createC(f.native, Vector(null), () => Consequence.unit).toOption shouldBe None
      createC(f.native, Vector.empty, null).toOption shouldBe None
    }
  }
}
