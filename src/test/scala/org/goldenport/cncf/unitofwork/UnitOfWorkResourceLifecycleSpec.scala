package org.goldenport.cncf.unitofwork

import scala.collection.mutable
import org.goldenport.{Conclusion, Consequence, ConsequenceT}
import org.goldenport.cncf.context.ExecutionContext
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for UnitOfWork-owned runtime resources.
 *
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkResourceLifecycleSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  private val _e1_metadata =
    afterWord("in spec:unit-of-work-resource-lifecycle, example:E1, rules:R1, phase:35")
  private val _e2_metadata =
    afterWord("in spec:unit-of-work-resource-lifecycle, example:E2, rules:R2, phase:35")
  private val _e3_metadata =
    afterWord("in spec:unit-of-work-resource-lifecycle, example:E3, rules:R2, phase:35")
  private val _e4_metadata =
    afterWord("in spec:unit-of-work-resource-lifecycle, example:E4, rules:R3, phase:35")
  private val _e5_metadata =
    afterWord("in spec:unit-of-work-resource-lifecycle, example:E5, rules:R4, phase:35")

  "UnitOfWork resource lifecycle" should {
    "E1 release registered resources in LIFO order when commit completes" must _e1_metadata {
      Given("Spec: docs/design/unit-of-work-resource-lifecycle.md; Rules: R1; Example: E1; an open UnitOfWork with two operational resources")
      val events = mutable.ArrayBuffer.empty[String]
      val uow = new UnitOfWork(ExecutionContext.create())
      uow.registerResourceC(new RecordingResource("first", events)).isSuccess shouldBe true
      uow.registerResourceC(new RecordingResource("second", events)).isSuccess shouldBe true

      When("the UnitOfWork commits")
      val result = uow.commit()

      Then("each resource is released once in reverse registration order")
      result.isSuccess shouldBe true
      events.toVector shouldBe Vector("second:Committed", "first:Committed")
      uow.dispose().isSuccess shouldBe true
      events.toVector shouldBe Vector("second:Committed", "first:Committed")
    }

    "E2 release resources after abort despite an earlier release failure" must _e2_metadata {
      Given("Spec: docs/design/unit-of-work-resource-lifecycle.md; Rules: R2; Example: E2; an open UnitOfWork with a failing resource between valid resources")
      val events = mutable.ArrayBuffer.empty[String]
      val uow = new UnitOfWork(ExecutionContext.create())
      uow.registerResourceC(new RecordingResource("first", events)).isSuccess shouldBe true
      uow.registerResourceC(new RecordingResource("failing", events, fail = true)).isSuccess shouldBe true
      uow.registerResourceC(new RecordingResource("last", events)).isSuccess shouldBe true

      When("the UnitOfWork aborts")
      val result = uow.abort()

      Then("all resources are reclaimed and the cleanup failure is structured")
      result.toOption shouldBe None
      events.toVector shouldBe Vector("last:Aborted", "failing:Aborted", "first:Aborted")
    }

    "E3 preserve primary failure while retaining cleanup diagnostics" must _e3_metadata {
      Given("Spec: docs/design/unit-of-work-resource-lifecycle.md; Rules: R2; Example: E3; a failing program and a failing UnitOfWork resource")
      val events = mutable.ArrayBuffer.empty[String]
      val uow = new UnitOfWork(ExecutionContext.create())
      val primary = Conclusion.operationInvalid("primary-program-failure")
      val program = ConsequenceT.fromConsequence[ExecProgram, Unit](Consequence.Failure(primary))
      uow.registerResourceC(new RecordingResource("cleanup", events, fail = true)).isSuccess shouldBe true

      When("the interpreter aborts after the primary failure")
      val result = new UnitOfWorkInterpreter(uow).run(program)

      Then("the primary Conclusion remains authoritative and cleanup is retained in its causal chain")
      val conclusion = result match {
        case Consequence.Failure(value) => value
        case _ => fail("expected combined primary and cleanup failure")
      }
      conclusion.causes.size shouldBe 2
      conclusion.causes.last.isMatch(primary) shouldBe true
      events.toVector shouldBe Vector("cleanup:Aborted")
    }

    "E4 not release a resource that unregisters after reaching its own terminal state" must _e4_metadata {
      Given("Spec: docs/design/unit-of-work-resource-lifecycle.md; Rules: R3; Example: E4; a resource that finishes before the UnitOfWork")
      val events = mutable.ArrayBuffer.empty[String]
      val uow = new UnitOfWork(ExecutionContext.create())
      val registration = uow.registerResourceC(new RecordingResource("finished", events)).toOption.get

      When("the resource unregisters before UnitOfWork completion")
      registration.close()
      val result = uow.abort()

      Then("the UnitOfWork does not release it again")
      result.isSuccess shouldBe true
      events.toVector shouldBe empty
    }

    "E5 release a resource immediately when registration occurs after termination" must _e5_metadata {
      Given("Spec: docs/design/unit-of-work-resource-lifecycle.md; Rules: R4; Example: E5; an already disposed UnitOfWork")
      val events = mutable.ArrayBuffer.empty[String]
      val uow = new UnitOfWork(ExecutionContext.create())
      uow.dispose().isSuccess shouldBe true

      When("a late resource is registered")
      val registration = uow.registerResourceC(new RecordingResource("late", events))

      Then("it is immediately released with the terminal reason")
      registration.isSuccess shouldBe true
      events.toVector shouldBe Vector("late:Disposed")
    }
  }
}

private final class RecordingResource(
  label: String,
  events: mutable.ArrayBuffer[String],
  fail: Boolean = false
) extends UnitOfWorkResource {
  def releaseC(termination: UnitOfWorkTermination): Consequence[Unit] = {
    events.synchronized {
      events += s"$label:$termination"
    }
    if (fail)
      Consequence.operationIllegal("unit_of_work", s"resource release failed: $label")
    else
      Consequence.unit
  }
}
