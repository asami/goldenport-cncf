package org.goldenport.cncf.workflow

import cats.free.Free
import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.unitofwork.*
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class WorkflowClosingProgramPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](c: Consequence[A]): A = c match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }
  private val owner = take(DataStoreComponentOwner.create("test.closing"))
  private val collection = DataStore.CollectionId.EntityStore(EntityCollectionId("cncf", "workflow", "closing"))
  private val completed: ActionExecution = ActionExecution.Completed(StateMachineOperationResult(
    StateMachineResultTypeReference("closing.result.v1"), ContextReference("closing-result", "1")))
  private def pure(result: ActionExecution): ExecUowM[ActionExecution] =
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], ActionExecution](result)
  private def stage(intent: WorkflowClosingLocalIntent): ExecUowM[Unit] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StageWorkflowClosingLocalIntent(intent)))
  private def create(id: String) = take(WorkflowClosingLocalIntent.createC(
    EntityVersionedSideEffect.Create(collection, DataStore.StringEntryId(id), Record.dataAuto("value" -> id))))

  "Managed closing program preparation" should {
    "interpret actual declarative stages and return the program's exact typed result without a stub outcome" in {
      Given("an actual ExecUowM program that declares two Creates before Completed")
      val program: ExecUowM[ActionExecution] = for {
        _ <- stage(create("first"))
        _ <- stage(create("second"))
      } yield completed
      When("the dedicated preparation interpreter consumes the program")
      val prepared = take(WorkflowClosingProgramPreparation.prepareC(program))
      Then("the exact Action result and original local targets are retained, without physical execution")
      prepared.result shouldBe completed.asInstanceOf[ActionExecution.Completed].result
      val local = take(prepared.localIntentsC)
      local._1 shouldBe empty
      local._2.map(_.entryId.print) shouldBe Vector("first", "second")
      take(WorkflowClosingProgramPreparation.prepareC(pure(completed))).localIntentsC.toOption.get._2 shouldBe empty
    }

    "freeze nested aliased sequence values before source arrays or exported projections can change" in {
      Given("a guarded patch containing an immutable ArraySeq backed by a caller-owned array")
      val source = Array("first", "second")
      val changes = Record.dataAuto("nested" -> Record.dataAuto("values" -> scala.collection.immutable.ArraySeq.unsafeWrapArray(source)))
      val guard = take(DataStoreConditionalRoot.create(owner, collection, DataStore.StringEntryId("guard"),
        "revision", Some(EntityRevision.INITIAL), Vector.empty, changes, take(EntityRevision.createC(2L))))
      val original = RecordEncoder.json(changes)
      val intent = take(WorkflowClosingLocalIntent.guardedC(guard))
      source(0) = "changed-after-freeze"
      val program: ExecUowM[ActionExecution] = stage(intent).map(_ => completed)
      When("the prepared plan projects local records after source alias mutation")
      val prepared = take(WorkflowClosingProgramPreparation.prepareC(program))
      val first = take(prepared.localIntentsC)._1.head
      val second = take(prepared.localIntentsC)._1.head
      Then("both fresh projections retain frozen bytes and none borrow the original Record")
      RecordEncoder.json(first.changes) shouldBe original
      RecordEncoder.json(second.changes) shouldBe original
      (first.changes.asInstanceOf[AnyRef] eq changes.asInstanceOf[AnyRef]) shouldBe false
      (first.changes.asInstanceOf[AnyRef] eq second.changes.asInstanceOf[AnyRef]) shouldBe false
    }

    "stop before an unsupported external or data operation and never visit later stages" in {
      Vector[UnitOfWorkOp[?]](UnitOfWorkOp.HttpGet("https://not-executed.invalid"),
        UnitOfWorkOp.DataStoreSave(null, Record.dataAuto("value" -> "not-written")),
        UnitOfWorkOp.Authorize(UnitOfWorkAuthorization("component", accessKind = "read"))).foreach { unsupported =>
        Given("a closing program with one staged intent followed by an unsupported operation")
        var later = false
        val program: ExecUowM[ActionExecution] = for {
          _ <- stage(create("before"))
          _ <- ConsequenceT.liftF(Free.liftF(unsupported))
          _ <- stage(create("after")).map { _ => later = true }
        } yield completed
        When("preparation reaches that operation")
        WorkflowClosingProgramPreparation.prepareC(program).isSuccess shouldBe false
        Then("no plan is published and subsequent program work is not interpreted")
        later shouldBe false
      }
    }

    "reject failed, suspended, malformed or throwing program outcomes without publishing a plan" in {
      Vector[ActionExecution](ActionExecution.Failed(StateMachineOperationFailure("failed", "fixture", Vector.empty)),
        ActionExecution.Suspended(null), ActionExecution.Completed(null),
        ActionExecution.Completed(StateMachineOperationResult(StateMachineResultTypeReference(""), ContextReference("x", "1"))), null)
        .foreach(outcome => WorkflowClosingProgramPreparation.prepareC(pure(outcome)).isSuccess shouldBe false)
      val throwing: ExecUowM[ActionExecution] = stage(create("first")).map { _ => throw new IllegalStateException("fixture program") }
      WorkflowClosingProgramPreparation.prepareC(throwing).isSuccess shouldBe false
      WorkflowClosingProgramPreparation.prepareC(null).isSuccess shouldBe false
    }

    "reject duplicate local targets and null staged intents rather than inferring an executable order" in {
      val duplicate: ExecUowM[ActionExecution] = for { _ <- stage(create("same")); _ <- stage(create("same")) } yield completed
      WorkflowClosingProgramPreparation.prepareC(duplicate).isSuccess shouldBe false
      WorkflowClosingProgramPreparation.prepareC(stage(null).map(_ => completed)).isSuccess shouldBe false
      WorkflowClosingLocalIntent.createC(EntityVersionedSideEffect.Create(collection, DataStore.StringEntryId("bad"),
        Record.dataAuto("value" -> new Object))).isSuccess shouldBe false
    }

    "enforce the Create limit without visiting work after an excessive declaration" in {
      def declared(count: Int): ExecUowM[Unit] = (0 until count).foldLeft(
        ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], Unit](())) { (program, index) =>
        program.flatMap(_ => stage(create(s"bounded-$index")))
      }
      take(WorkflowClosingProgramPreparation.prepareC(declared(64).map(_ => completed)))
        .localIntentsC.toOption.get._2.size shouldBe 64
      WorkflowClosingProgramPreparation.prepareC(declared(65).map(_ => completed)).isSuccess shouldBe false
      var later = false
      WorkflowClosingProgramPreparation.prepareC(declared(72).map { _ => later = true; completed }).isSuccess shouldBe false
      later shouldBe false
    }

    "preserve interruption rather than turning a control exception into a rejected plan" in {
      val interrupted: ExecUowM[ActionExecution] = stage(create("interrupted")).map { _ =>
        throw new InterruptedException("closing fixture")
      }
      try {
        intercept[InterruptedException](WorkflowClosingProgramPreparation.prepareC(interrupted))
        Thread.currentThread().isInterrupted shouldBe true
      } finally {
        Thread.interrupted()
      }
    }

    "deny the staging primitive in the ordinary active interpreter without committing or claiming a write" in {
      val uow = new UnitOfWork(ExecutionContext.create())
      val op = UnitOfWorkOp.StageWorkflowClosingLocalIntent(create("not-written"))
      new UnitOfWorkInterpreter(uow).interpret(op).isSuccess shouldBe false
      uow.lastCommitTermination shouldBe None
      UnitOfWorkOperationClassifier.classify(op) shouldBe UnitOfWorkEffectClass.Local
    }
  }
}
