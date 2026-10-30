package org.goldenport.cncf.processexecution

import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** @since Oct. 30, 2026
  * @version Oct. 30, 2026
  * @author ASAMI, Tomoharu
  */
final class SbtValidationEstimateSpec extends AnyWordSpec with Matchers {
  private val suite = "org.example.SelectedSpec"
  private val estimate = SbtValidationEstimate("changed selection", "source-1",
    Vector(SbtValidationEstimate.Leaf(suite, "first", 1000000000L, true),
      SbtValidationEstimate.Leaf(suite, "second", 2000000000L, false)),
    SbtValidationEstimate.Preparation(1000000000L, 2000000000L, 3000000000L, 1000000000L), BigDecimal("1.5"))
  private val target = SbtTarget(WorkspaceId.parseC("project").toOption.get, validationEstimate = Some(estimate))
  private val host = ProcessExecutionLimits(executionTimeoutMillis = Some(30000L), terminationGraceMillis = Some(50L))

  "Aggregate validation deadline" should {
    "admit only a fixed TestCompile program with no test-class or scenario argv" in {
      SbtTestSelectionPolicy.validateC(SbtOperation.TestCompile, Vector.empty).isSuccess shouldBe true
      SbtTestSelectionPolicy.validateC(SbtOperation.TestCompile, Vector(suite)).isFaillure shouldBe true
      SbtScenarioSelection.validateOptionC(Some(SbtScenarioSelection(suite, "first")),
        SbtOperation.TestCompile).isFaillure shouldBe true
    }
    "add each overhead once and round up only the final aggregate" in {
      estimate.timeoutMillisC.toOption shouldBe Some(15000L)
      estimate.copy(completedPreparationNanos = 2000000000L).timeoutMillisC.toOption shouldBe Some(13000L)
      val tiny = estimate.copy(leaves = Vector(estimate.leaves.head.copy(expectedNanos = 1L)),
        preparation = SbtValidationEstimate.Preparation(0L, 0L, 0L, 0L))
      tiny.timeoutMillisC.toOption shouldBe Some(1L)
      SbtValidationEstimate.applyC(target, SbtOperation.TestOnly, Vector(suite), host).toOption shouldBe
        Some(host.copy(executionTimeoutMillis = Some(15000L)))
    }
    "refuse incomplete, duplicated, nonpositive and overflowing estimate data" in {
      val invalid = Vector(estimate.copy(leaves = Vector.empty), estimate.copy(sourceRevision = ""),
        estimate.copy(completedPreparationNanos = -1L), estimate.copy(completedPreparationNanos = 8000000000L),
        estimate.copy(selectionReason = ""), estimate.copy(leaves = estimate.leaves ++ estimate.leaves),
        estimate.copy(leaves = Vector(estimate.leaves.head.copy(expectedNanos = 0))),
        estimate.copy(preparation = SbtValidationEstimate.Preparation(-1, 0, 0, 0)),
        estimate.copy(multiplier = BigDecimal(0)), estimate.copy(multiplier = BigDecimal("1e30")))
      invalid.foreach(_.timeoutMillisC.isFaillure shouldBe true)
    }
    "retain registered typed inputs without turning them into executable variants" in {
      val typed = estimate.copy(leaves = estimate.leaves.zipWithIndex.map { case (leaf, index) =>
        leaf.copy(registeredInputs = Some(Map("count" -> io.circe.Json.fromInt(index + 1))))
      })
      typed.timeoutMillisC.toOption shouldBe estimate.timeoutMillisC.toOption
      SbtValidationEstimate.applyC(target.copy(validationEstimate = Some(typed)), SbtOperation.TestOnly,
        Vector(suite), host).isSuccess shouldBe true
      typed.copy(leaves = Vector(typed.leaves.head, typed.leaves.head.copy(
        registeredInputs = typed.leaves.last.registeredInputs))).timeoutMillisC.isFaillure shouldBe true
      typed.copy(leaves = Vector(typed.leaves.head.copy(
        registeredInputs = Some(Map("count" -> io.circe.Json.Null))))).timeoutMillisC.isFaillure shouldBe true
    }
    "preserve host limits without an estimate and refuse attempts to exceed or cross their authority" in {
      SbtValidationEstimate.applyC(target.copy(validationEstimate = None), SbtOperation.Test,
        Vector.empty, host).toOption shouldBe Some(host)
      SbtValidationEstimate.applyC(target, SbtOperation.TestOnly, Vector(suite),
        host.copy(executionTimeoutMillis = Some(14999L))).isFaillure shouldBe true
      SbtValidationEstimate.applyC(target, SbtOperation.TestOnly, Vector(suite),
        host.copy(executionTimeoutMillis = None)).isFaillure shouldBe true
      Vector(SbtOperation.Compile, SbtOperation.TestCompile, SbtOperation.Test, SbtOperation.PublishLocal).foreach { operation =>
        SbtValidationEstimate.applyC(target, operation, Vector(suite), host).isFaillure shouldBe true
      }
      val scenario = SbtScenarioSelection(suite, "first")
      SbtValidationEstimate.applyC(target.copy(scenario = Some(scenario)), SbtOperation.TestOnly,
        Vector(suite), host).isFaillure shouldBe true
    }
  }
}
