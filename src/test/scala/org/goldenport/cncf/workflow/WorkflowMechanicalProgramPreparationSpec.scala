package org.goldenport.cncf.workflow

import cats.free.Free
import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityRevision}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class WorkflowMechanicalProgramPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"Mechanical program fixture failed: $other")
  }
  private val collection = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "mechanical", "documents"))
  private def stage(intent: WorkflowClosingLocalIntent): ExecUowM[Unit] =
    ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.StageWorkflowClosingLocalIntent(intent)))
  private def create(id: String) = take(WorkflowClosingLocalIntent.createC(
    EntityVersionedSideEffect.Create(collection, DataStore.StringEntryId(id), Record.dataAuto("value" -> id))))
  private def declared(count: Int): ExecUowM[Unit] = (0 until count).foldLeft(
    ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], Unit](())) { (program, index) =>
      program.flatMap(_ => stage(create(s"document-$index")))
    }

  "Mechanical Create-only program preparation" should {
    "capture at most 64 distinct document Creates without any completion result" in {
      Given("a Unit-valued program that stages 64 distinct immutable document targets")
      val program = declared(64)
      When("the closed preparation interpreter captures its local declarations")
      val prepared = take(WorkflowMechanicalProgramPreparation.prepareC(program))
      Then("all exact Creates are available and an empty program remains a valid mechanical transition")
      take(prepared.createsC).map(_.entryId.print) shouldBe (0 until 64).map(i => s"document-$i").toVector
      take(WorkflowMechanicalProgramPreparation.prepareC(declared(0))).createsC.toOption.get shouldBe empty
    }

    "reject excessive, duplicate and null intents before publishing a plan" in {
      Given("programs containing excessive targets, duplicate identities or a missing intent")
      val duplicate = stage(create("same")).flatMap(_ => stage(create("same")))
      When("mechanical preparation checks the complete bounded declaration")
      val results = Vector(declared(65), duplicate, stage(null)).map(WorkflowMechanicalProgramPreparation.prepareC)
      Then("none is an executable mechanical plan")
      results.foreach(_.isSuccess shouldBe false)
      WorkflowMechanicalProgramPreparation.prepareC(null).isSuccess shouldBe false
    }

    "reject revision guards and external operations without visiting later work" in {
      Given("a guard declaration and an external HTTP operation that are outside Create-only preparation")
      val owner = take(DataStoreComponentOwner.create("test.Mechanical"))
      val guard = take(DataStoreConditionalRoot.create(owner, collection, DataStore.StringEntryId("guard"),
        "revision", Some(EntityRevision.INITIAL), Vector.empty, Record.dataAuto("value" -> "next"),
        take(EntityRevision.createC(2L))))
      var later = false
      val external: ExecUowM[Unit] = ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.HttpGet("https://not-executed.invalid")))
        .map(_ => { later = true; () })
      When("the mechanical interpreter encounters an unsupported opcode or projected guard")
      val denied = Vector(WorkflowMechanicalProgramPreparation.prepareC(external),
        WorkflowMechanicalProgramPreparation.prepareC(stage(take(WorkflowClosingLocalIntent.guardedC(guard)))))
      Then("both refuse and no later external program body is interpreted")
      denied.foreach(_.isSuccess shouldBe false)
      later shouldBe false
    }

    "retain frozen Create values after caller-owned arrays change" in {
      Given("a captured Create with an ArraySeq backed by caller-owned mutable storage")
      val values = Array("original", "second")
      val source = Record.dataAuto("values" -> scala.collection.immutable.ArraySeq.unsafeWrapArray(values))
      val original = RecordEncoder.json(source)
      val intent = take(WorkflowClosingLocalIntent.createC(EntityVersionedSideEffect.Create(collection,
        DataStore.StringEntryId("frozen"), source)))
      values(0) = "changed"
      When("the mechanical preparation projects the captured data twice")
      val prepared = take(WorkflowMechanicalProgramPreparation.prepareC(stage(intent)))
      val first = take(prepared.createsC).head.record
      val second = take(prepared.createsC).head.record
      Then("neither projection borrows the source array or the other exported Record")
      RecordEncoder.json(first) shouldBe original
      RecordEncoder.json(second) shouldBe original
      (first.asInstanceOf[AnyRef] eq second.asInstanceOf[AnyRef]) shouldBe false
    }
  }
}
