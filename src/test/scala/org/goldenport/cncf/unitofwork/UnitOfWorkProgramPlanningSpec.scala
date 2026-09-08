package org.goldenport.cncf.unitofwork

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for metadata-independent UnitOfWork planning.
 *
 * @since   Sep.  8, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class UnitOfWorkProgramPlanningSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _authorization = UnitOfWorkAuthorization(
    resourceFamily = "component",
    accessKind = "read"
  )
  private val _control = UnitOfWorkOp.Authorize(_authorization)
  private val _local = UnitOfWorkOp.LocalDataDir("component")
  private val _external = UnitOfWorkOp.HttpGet("/external")

  "UnitOfWorkProgramPlanner" should {
    "classify representative control, local, and external operations" in {
      Given("one representative operation from each base effect category")
      val operations = Vector(_control, _local, _external)

      When("the operation classifier classifies them")
      val classes = operations.map(UnitOfWorkOperationClassifier.classify)

      Then("the metadata-independent base classes are Control, Local, and External")
      classes shouldBe Vector(
        UnitOfWorkEffectClass.Control,
        UnitOfWorkEffectClass.Local,
        UnitOfWorkEffectClass.External
      )
    }

    "isolate external occurrences between contiguous local atomic segments" in {
      Given("control and local operations on both sides of one external operation")
      val operations = Vector(_control, _local, _external, _local, _control)

      When("the ordered operations are planned")
      val plan = UnitOfWorkProgramPlanner.plan(operations)

      Then("only contiguous control/local runs coalesce and the external boundary stays isolated")
      plan.segments shouldBe Vector(
        UnitOfWorkPlanSegment.LocalAtomic(
          Vector(
            UnitOfWorkOperationOccurrence(
              _control,
              0,
              UnitOfWorkEffectClass.Control
            ),
            UnitOfWorkOperationOccurrence(_local, 1, UnitOfWorkEffectClass.Local)
          )
        ),
        UnitOfWorkPlanSegment.ExternalBoundary(
          UnitOfWorkOperationOccurrence(_external, 2, UnitOfWorkEffectClass.External)
        ),
        UnitOfWorkPlanSegment.LocalAtomic(
          Vector(
            UnitOfWorkOperationOccurrence(_local, 3, UnitOfWorkEffectClass.Local),
            UnitOfWorkOperationOccurrence(
              _control,
              4,
              UnitOfWorkEffectClass.Control
            )
          )
        )
      )
    }

    "retain repeated equal operations with their input order and ordinals" in {
      Given("two equal occurrences of one local operation")
      val repeated = UnitOfWorkOp.LocalDataDir("same")
      val operations = Vector(repeated, repeated)

      When("the repeated operations are planned")
      val plan = UnitOfWorkProgramPlanner.plan(operations)

      Then("both occurrences remain ordered and receive distinct zero-based ordinals")
      plan.occurrences.map(_.operation) shouldBe operations
      plan.occurrences.map(_.ordinal) shouldBe Vector(0, 1)
      plan.segments shouldBe Vector(
        UnitOfWorkPlanSegment.LocalAtomic(plan.occurrences)
      )
    }

    "produce no occurrences or segments for empty input" in {
      Given("an empty ordered operation sequence")
      val operations = Vector.empty[UnitOfWorkOp[?]]

      When("the empty sequence is planned")
      val plan = UnitOfWorkProgramPlanner.plan(operations)

      Then("the immutable plan is empty")
      plan.occurrences shouldBe empty
      plan.segments shouldBe empty
    }

    "preserve arbitrary safe category order without merging across external occurrences" in {
      Given("generated sequences made only from control, local, and external representatives")
      val operations = Gen.listOf(
        Gen.oneOf(_control, _local, _external)
      ).map(_.toVector)

      When("each generated sequence is planned")
      val property = Prop.forAll(operations) { input =>
        val plan = UnitOfWorkProgramPlanner.plan(input)
        val planned = plan.segments.flatMap {
          case UnitOfWorkPlanSegment.LocalAtomic(occurrences) => occurrences
          case UnitOfWorkPlanSegment.ExternalBoundary(occurrence) => Vector(occurrence)
        }
        val externalordinals = planned.collect {
          case occurrence if occurrence.effectClass == UnitOfWorkEffectClass.External =>
            occurrence.ordinal
        }
        val localsegmentordinals = plan.segments.collect {
          case UnitOfWorkPlanSegment.LocalAtomic(occurrences) =>
            occurrences.map(_.ordinal)
        }
        planned == plan.occurrences &&
        planned.map(_.ordinal) == input.indices.toVector &&
        externalordinals.forall { ordinal =>
          !localsegmentordinals.exists(ordinals =>
            ordinals.minOption.exists(_ < ordinal) && ordinals.maxOption.exists(_ > ordinal)
          )
        }
      }

      Then("the bounded ScalaCheck run proves order preservation and external separation")
      val result = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(50),
        property
      )
      result.passed shouldBe true
    }
  }
}
