package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.cncf.display.*
import org.goldenport.cncf.unitofwork.UnitOfWorkTermination
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 15, 2026
 * @version Oct. 19, 2026
 */
final class DisplayMutationCommitObservationSpec extends AnyWordSpec with DisplayMutationServiceFixture.Vocabulary {
  import DisplayMutationServiceFixture.*
  private val _changes = Vector(field("title", DisplayValue.Text("Committed current title")))

  "Display current-operation commit evidence" should {
    "current actual commit" which {
      "C3-C1 permit one authoritative receipt and contextual read on the exact authorized and writing UnitOfWork" in {
        for (target <- Vector(Target.Entity, Target.Aggregate)) {
          Given(s"a normally registered $target command with a genuine provider and an independently declared full-state change")
          val session = open(target)
          try {
            val before = session.rows()
            val wanted = session.expected(before, session.physical(), _changes, true, 2L)
            val active = session.execution()
            When("the normal resolver and ActionEngine execute, actually commit and call the selected after-commit hook")
            val attempt = session.execute(session.request(_changes), active)
            val after = session.rows()
            Then("one capture, actual current committed termination/result and the selected contextual read share the same UnitOfWork")
            checkResult(attempt, session, "updated", 2L)
            checkRows(after, wanted.rows)
            checkCompleteState(session, wanted)
            session.log.events.count(_.event == "capture") shouldBe 1
            session.log.events.count(_.event == "commit") shouldBe 1
            session.log.events.count(_.event == "find") shouldBe 1
            session.log.events.foreach(event => (event.uow eq active.uow) shouldBe true)
            session.log.authorizations should not be empty
            session.log.authorizations.foreach { case (_, uow) => (uow eq active.uow) shouldBe true }
          } finally session.close()
        }
      }

      "C3-C2 reject absent and historical-only success when the current commit action is a no-op" in {
        for (historical <- Vector(false, true)) {
          Given(s"a canonical ordinary command whose commit action is a no-op, historical committed success=$historical")
          val session = open()
          try {
            val before = session.rows()
            val wanted = session.expected(before, session.physical(), _changes, true, 2L)
            val active = session.execution(Commit.Noop, historical = historical)
            session.log.reset()
            When("the command captures its actual provider acknowledgement but the current enclosing commit never invokes its callback")
            val attempt = session.execute(session.request(_changes), active)
            val after = session.rows()
            Then("transaction-indeterminate contains no receipt or reload and never mistakes historical state for a current witness")
            attempt.result shouldBe a[Consequence.Failure[?]]
            conclusion(attempt.result).observation.taxonomy shouldBe conclusion(DisplayMutationResult.indeterminateC[Unit]()).observation.taxonomy
            And("the fresh current Action has no commit witness while its caller retains only the selected history")
            (active.uow eq active.parentUnitOfWork) shouldBe false
            active.uow.lastCommitTermination shouldBe None
            active.uow.lastCommitResult shouldBe None
            active.parentUnitOfWork.lastCommitTermination shouldBe (if (historical) Some(UnitOfWorkTermination.Committed) else None)
            active.parentUnitOfWork.lastCommitResult shouldBe (if (historical) Some(Consequence.unit) else None)
            active.attempts shouldBe 1
            session.log.events.count(_.event == "capture") shouldBe 1
            session.log.events.count(_.event == "find") shouldBe 0
            session.log.validations shouldBe 1
            checkRows(after, wanted.rows)
            checkCompleteState(session, wanted)
          } finally session.close()
        }
      }

      "C3-C3 preserve real current rejection, abort, unknown, disposal and cleanup failures after prior success without fabricating JSON" in {
        for (mode <- Vector(Commit.Reject, Commit.Abort, Commit.Throw, Commit.Dispose, Commit.Cleanup)) {
          Given(s"a real historical commit followed by a selected current $mode attempt")
          val session = open(Target.Aggregate)
          try {
            val before = session.rows()
            val wanted = session.expected(before, session.physical(), _changes, true, 2L)
            val active = session.execution(mode, historical = true)
            session.log.reset()
            When("ordinary ActionEngine finalization encounters the current lifecycle boundary")
            val attempt = session.execute(session.request(_changes), active)
            val after = session.rows()
            Then("the existing framework failure remains a Failure with no receipt, contextual reload or writer replay")
            attempt.result shouldBe a[Consequence.Failure[?]]
            active.attempts shouldBe 1
            session.log.commands shouldBe 1
            session.log.events.count(_.event == "find") shouldBe 0
            session.log.events.count(_.event == "capture") shouldBe 1
            checkRows(after, wanted.rows)
            checkCompleteState(session, wanted)
            if (mode == Commit.Reject) active.uow.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Aborted)
            if (mode == Commit.Abort) active.uow.lastAbortResult shouldBe Some(Consequence.unit)
            if (mode == Commit.Cleanup) conclusion(attempt.result).observation.taxonomy shouldBe projectionFailure.conclusion.observation.taxonomy
          } finally session.close()
        }
        Given("an ordinary original Display read using its inherited default afterCommitResponseC behavior")
        val session = open()
        try {
          val before = session.rows()
          When("the registered read resolves and executes through the same canonical ActionEngine")
          val read = session.read()
          val after = session.rows()
          Then("the legacy read result remains the read-v1 object and performs no mutation")
          json(read.result).hcursor.get[String]("schema") shouldBe Right("cncf.display-model.v1")
          read.execution.attempts shouldBe 0
          checkRows(after, before)
        } finally session.close()
      }

      "C3-C4 retain capture projection and coherence failures through a real enclosing commit without another Browser or writer" in {
        for (target <- Vector(Target.Entity, Target.Aggregate); fault <- Vector(CaptureFault.Projection, CaptureFault.Coherence)) {
          Given(s"a real canonical $target with a delegated actual post-ACK $fault fault")
          val session = open(target)
          try {
            val before = session.rows()
            val wanted = session.expected(before, session.physical(), _changes, true, 2L, captureFault = fault)
            session.log.captureFault = fault
            When("ordinary execution retains the capture and completes the actual same-UnitOfWork commit")
            val attempt = session.execute(session.request(_changes))
            val after = session.rows()
            Then("the committed receipt survives as projection-unavailable and short-circuits before the registered Browser")
            checkResult(attempt, session, "updated", 2L, "pending", Some("projection-unavailable"))
            checkRows(after, wanted.rows)
            checkCompleteState(session, wanted)
            session.log.events.count(_.event == "capture") shouldBe 1
            session.log.events.count(_.event == "find") shouldBe 0
            session.log.validations shouldBe 1
            session.log.commands shouldBe (if (target == Target.Entity) 0 else 1)
          } finally session.close()
        }
      }
    }
  }
}
