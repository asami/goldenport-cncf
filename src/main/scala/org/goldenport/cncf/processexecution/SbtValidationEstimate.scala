package org.goldenport.cncf.processexecution

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/** Selected test-leaf estimates are data, not process execution authority.
  * The executing UoW revalidates them against its admitted fixed SBT program.
  * @since   Oct. 30, 2026
  * @version Oct. 30, 2026
  * @author  ASAMI, Tomoharu
  */
final case class SbtValidationEstimate(
  selectionReason: String,
  sourceRevision: String,
  leaves: Vector[SbtValidationEstimate.Leaf],
  preparation: SbtValidationEstimate.Preparation,
  multiplier: BigDecimal,
  completedPreparationNanos: Long = 0L
) {
  def timeoutMillisC: Consequence[Long] = SbtValidationEstimate.timeoutMillisC(this)
}

object SbtValidationEstimate {
  /** Observation only: execution always repeats current-context admission.
    * No executable, argv or reusable execution authority crosses this boundary.
    */
  def selectedTestClassesC(context: ExecutionContext, target: SbtTarget): Consequence[Vector[String]] =
    if (target == null || context == null) invalid("Current context and typed target are required")
    else SbtExecutionAdmission.resolveC(context, target.copy(validationEstimate = None), SbtOperation.TestOnly).flatMap { execution =>
      execution.definition._sbt_metadata.map(m => Consequence.success(m.testClasses))
        .getOrElse(invalid("Typed TestOnly program metadata is unavailable"))
    }
  /** Registered test-body inputs are observation metadata, never extra SBT
    * arguments. Each independently executed registration keeps its selector. */
  final case class Leaf(suiteClassName: String, testName: String, expectedNanos: Long,
    usedDefault: Boolean, registeredInputs: Option[Map[String, io.circe.Json]] = None)
  /** Each cost occurs once per check, never once per leaf. Observed TestCompile
    * preparation consumes part of the aggregate allowance; only the remainder
    * is passed to TestOnly's process deadline.
    */
  final case class Preparation(startupNanos: Long, compilationNanos: Long,
    sharedPreparationNanos: Long, cleanupNanos: Long) {
    def values: Vector[Long] = Vector(startupNanos, compilationNanos, sharedPreparationNanos, cleanupNanos)
  }

  def timeoutMillisC(value: SbtValidationEstimate): Consequence[Long] = for {
    _ <- if (value != null && text(value.selectionReason) && text(value.sourceRevision) &&
        value.leaves != null && value.leaves.nonEmpty && value.leaves.size <= 100000 &&
        value.leaves.forall(x => x != null && text(x.suiteClassName) && text(x.testName) && x.expectedNanos > 0 &&
          x.registeredInputs != null && x.registeredInputs.forall(inputs => inputs != null && inputs.nonEmpty &&
            inputs.forall { case (key, input) => text(key) && input != null && !input.isNull })) &&
        value.leaves.map(x => (x.suiteClassName, x.testName)).distinct.size == value.leaves.size &&
        value.preparation != null && value.preparation.values.forall(_ >= 0) &&
        value.completedPreparationNanos >= 0 &&
        BigInt(value.completedPreparationNanos) <= value.preparation.values.foldLeft(BigInt(0))(_ + _) &&
        value.multiplier != null && value.multiplier > 0) Consequence.unit
      else invalid("A complete nonempty selected leaf estimate and once-only preparation are required")
    nanos = value.leaves.foldLeft(BigInt(0))((n, leaf) => n + leaf.expectedNanos) +
      value.preparation.values.foldLeft(BigInt(0))(_ + _)
    remaining = BigDecimal(nanos) * value.multiplier - BigDecimal(value.completedPreparationNanos)
    millis = (remaining / BigDecimal(1000000))
      .setScale(0, BigDecimal.RoundingMode.CEILING).toBigInt
    _ <- if (millis > 0 && millis.isValidLong) Consequence.unit
      else invalid("Estimated SBT execution deadline exceeds the supported duration range")
  } yield millis.longValue

  private[cncf] def applyC(target: SbtTarget, operation: SbtOperation, admittedClasses: Vector[String],
    host: ProcessExecutionLimits): Consequence[ProcessExecutionLimits] =
    if (target.validationEstimate == null) invalid("Estimate option is missing")
    else target.validationEstimate match {
      case None => Consequence.success(host)
      case Some(estimate) => for {
        millis <- timeoutMillisC(estimate)
        _ <- if (operation == SbtOperation.TestOnly && SbtTestSelectionPolicy.validClasses(admittedClasses) &&
            estimate.leaves.map(_.suiteClassName).toSet == admittedClasses.toSet &&
            target.scenario.forall(s => estimate.leaves.size == 1 &&
              estimate.leaves.head.suiteClassName == s.suiteClassName && estimate.leaves.head.testName == s.testName))
          Consequence.unit else invalid("Estimate differs from the admitted exact TestOnly selection")
        _ <- if (host.executionTimeoutMillis.exists(_ >= millis)) Consequence.unit
          else invalid("Estimated SBT deadline exceeds the explicit host execution ceiling")
      } yield host.copy(executionTimeoutMillis = Some(millis))
    }

  private def text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.exists(_.isControl)
  private def invalid[A](message: String): Consequence[A] = Consequence.argumentPolicyViolation(
    "validationEstimate", "sbt.execution.validation-estimate", message, "invalid")
}
