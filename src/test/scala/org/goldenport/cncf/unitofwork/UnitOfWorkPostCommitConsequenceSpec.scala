package org.goldenport.cncf.unitofwork

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 12, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkPostCommitConsequenceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _e8 = afterWord("in spec:action-execution-semantics, example:E8, rules:R7,R10,R11,R12, phase:57.2, slice:AES-04A")

  "UnitOfWork post-commit consequence handoff" should {
    "E8 shared direct and Job lifecycle" must _e8 {
      "return structured callback success from commit processing" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R7,R10,R12; Example: E8; a UnitOfWork with an admitted successful post-commit callback")
        val recorder = new RecordingCommitRecorder
        val unitofwork = _unit_of_work(recorder)
        unitofwork.stagePostCommitC(Consequence.unit)

        When("the UnitOfWork commits")
        val result = unitofwork.commit()

        Then("the commit remains successful and records normal transaction completion")
        result.isSuccess shouldBe true
        recorder.entries shouldBe Vector("UnitOfWork.prepare", "EventEngine.prepare", "UnitOfWork.commit", "EventEngine.commit", "DataStore.commit")
      }

      "surface a structured callback Failure after commit without aborting the transaction" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R7,R10,R11,R12; Example: E6,E8; a UnitOfWork whose admitted post-commit dispatch returns Failure")
        val recorder = new RecordingCommitRecorder
        val terminations = ArrayBuffer.empty[UnitOfWorkTermination]
        val unitofwork = _unit_of_work(recorder)
        unitofwork.registerResourceC(new RecordingResource(terminations)).isSuccess shouldBe true
        unitofwork.stagePostCommitC(Consequence.operationInvalid("event.dispatch", "post-commit admission rejected"))

        When("the primary transaction prepares and commits before post-commit dispatch")
        val result = unitofwork.commit()

        Then("callback Failure is observable while transaction and resource evidence never report abort or rollback")
        result.isFaillure shouldBe true
        recorder.entries should contain inOrderOnly (
          "UnitOfWork.prepare",
          "EventEngine.prepare",
          "UnitOfWork.commit",
          "EventEngine.commit",
          "DataStore.commit"
        )
        recorder.entries should not contain "UnitOfWork.abort"
        unitofwork.lastAbortResult shouldBe None
        unitofwork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
        terminations.toVector shouldBe Vector(UnitOfWorkTermination.Committed)
        terminations.toVector should not contain UnitOfWorkTermination.Aborted
      }

      "run every staged callback in registration order and aggregate their Failures after commit" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R7,R10,R11,R12; Example: E8; a UnitOfWork with successful and failing structured callbacks")
        val recorder = new RecordingCommitRecorder
        val callbacks = ArrayBuffer.empty[String]
        val unitofwork = _unit_of_work(recorder)
        unitofwork.stagePostCommitC {
          callbacks += "first"
          Consequence.operationInvalid("event.dispatch", "first post-commit admission rejected")
        }
        unitofwork.stagePostCommitC {
          callbacks += "second"
          Consequence.unit
        }
        unitofwork.stagePostCommitC {
          callbacks += "third"
          Consequence.operationInvalid("event.dispatch", "third post-commit admission rejected")
        }

        When("the UnitOfWork commits its transaction before processing each callback")
        val result = unitofwork.commit()

        Then("all callbacks run once in registration order and their failures remain ordered under committed termination")
        result.isFaillure shouldBe true
        callbacks.toVector shouldBe Vector("first", "second", "third")
        val conclusion = result match {
          case Consequence.Failure(value) => value
          case Consequence.Success(value) => fail(s"post-commit failure expected: $value")
        }
        val causes = conclusion.causes.map(_.display)
        causes.exists(_.contains("first post-commit admission rejected")) shouldBe true
        causes.exists(_.contains("third post-commit admission rejected")) shouldBe true
        causes.indexWhere(_.contains("first post-commit admission rejected")) should be <
          causes.indexWhere(_.contains("third post-commit admission rejected"))
        unitofwork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
      }

      "rethrow an InterruptedException after committed cleanup without running later callbacks" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R7,R10,R11,R12; Example: E8; a UnitOfWork whose first post-commit callback interrupts control flow")
        val recorder = new RecordingCommitRecorder
        val terminations = ArrayBuffer.empty[UnitOfWorkTermination]
        val callbacks = ArrayBuffer.empty[String]
        val interruption = new InterruptedException("planned post-commit interruption")
        val unitofwork = _unit_of_work(recorder)
        unitofwork.registerResourceC(new RecordingResource(terminations)).isSuccess shouldBe true
        unitofwork.stagePostCommit {
          callbacks += "first"
          throw interruption
        }
        unitofwork.stagePostCommitC {
          callbacks += "later"
          Consequence.unit
        }

        When("the UnitOfWork commits and the first callback interrupts")
        val (thrown, interruptstatus) = try {
          val captured = intercept[InterruptedException] {
            unitofwork.commit()
          }
          captured -> Thread.currentThread.isInterrupted
        } finally {
          Thread.interrupted()
        }

        Then("the original interruption escapes after committed cleanup and records its failed commit result")
        thrown should be theSameInstanceAs interruption
        thrown.getMessage shouldBe "planned post-commit interruption"
        interruptstatus shouldBe true
        callbacks.toVector shouldBe Vector("first")
        terminations.toVector shouldBe Vector(UnitOfWorkTermination.Committed)
        unitofwork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
        unitofwork.lastCommitResult.exists(_.isFaillure) shouldBe true
      }

      "rethrow a LinkageError after committed cleanup without running later callbacks" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R7,R10,R11,R12; Example: E8; a UnitOfWork whose first post-commit callback raises a fatal linkage error")
        val recorder = new RecordingCommitRecorder
        val terminations = ArrayBuffer.empty[UnitOfWorkTermination]
        val callbacks = ArrayBuffer.empty[String]
        val fatal = new LinkageError("planned post-commit linkage failure")
        val unitofwork = _unit_of_work(recorder)
        unitofwork.registerResourceC(new RecordingResource(terminations)).isSuccess shouldBe true
        unitofwork.stagePostCommitC {
          callbacks += "first"
          throw fatal
        }
        unitofwork.stagePostCommitC {
          callbacks += "later"
          Consequence.unit
        }

        When("the UnitOfWork commits and the first callback raises the fatal error")
        val thrown = intercept[LinkageError] {
          unitofwork.commit()
        }

        Then("the original fatal error escapes after committed cleanup and records its failed commit result")
        thrown should be theSameInstanceAs fatal
        thrown.getMessage shouldBe "planned post-commit linkage failure"
        callbacks.toVector shouldBe Vector("first")
        terminations.toVector shouldBe Vector(UnitOfWorkTermination.Committed)
        unitofwork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Committed)
        unitofwork.lastCommitResult.exists(_.isFaillure) shouldBe true
      }

      "skip staged callbacks when prepare rejects the transaction" in {
        Given("Spec: docs/spec/action-execution-semantics.md; Rules: R7,R10,R12; Example: E8; a UnitOfWork whose EventEngine rejects prepare")
        val recorder = new RecordingCommitRecorder
        val callbacks = ArrayBuffer.empty[String]
        val unitofwork = _rejected_unit_of_work(recorder)
        unitofwork.stagePostCommitC {
          callbacks += "must-not-run"
          Consequence.unit
        }

        When("prepare rejects before transaction commit")
        val result = unitofwork.commit()

        Then("the transaction terminates as aborted and no post-commit callback runs")
        result.isFaillure shouldBe true
        callbacks.toVector shouldBe empty
        recorder.entries shouldBe Vector(
          "UnitOfWork.prepare",
          "EventEngine.prepare",
          "UnitOfWork.abort",
          "EventEngine.abort"
        )
        unitofwork.lastCommitTermination shouldBe Some(UnitOfWorkTermination.Aborted)
      }
    }
  }

  private def _unit_of_work(recorder: RecordingCommitRecorder): UnitOfWork =
    new UnitOfWork(
      ExecutionContext.create(),
      EventEngine.noop(DataStore.noop(recorder), recorder),
      recorder
    )

  private def _rejected_unit_of_work(recorder: RecordingCommitRecorder): UnitOfWork =
    new UnitOfWork(
      ExecutionContext.create(),
      Proxy.newProxyInstance(
        classOf[EventEngine].getClassLoader,
        Array(classOf[EventEngine]),
        new InvocationHandler {
          def invoke(proxy: Object, method: Method, args: Array[Object]): Object =
            method.getName match {
              case "stage" => null
              case "prepare" =>
                recorder.record("EventEngine.prepare")
                PrepareResult.Rejected("planned prepare rejection")
              case "abort" =>
                recorder.record("EventEngine.abort")
                null
              case name =>
                throw new IllegalStateException(s"unexpected EventEngine method: $name")
            }
        }
      ).asInstanceOf[EventEngine],
      recorder
    )

  private final class RecordingCommitRecorder extends CommitRecorder {
    private val _entries = ArrayBuffer.empty[String]

    def entries: Vector[String] = _entries.toVector

    def record(message: String): Unit = _entries += message
  }

  private final class RecordingResource(
    terminations: ArrayBuffer[UnitOfWorkTermination]
  ) extends UnitOfWorkResource {
    def releaseC(termination: UnitOfWorkTermination): Consequence[Unit] = {
      terminations += termination
      Consequence.unit
    }
  }
}
