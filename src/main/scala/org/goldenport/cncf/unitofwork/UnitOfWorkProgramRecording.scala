package org.goldenport.cncf.unitofwork

import cats.~>
import org.goldenport.{Consequence, Conclusion}

/*
 * Package-private deterministic recording boundary for executable UnitOfWork
 * programs.  The recorder consumes only caller-supplied typed outcomes; it is
 * deliberately independent from UnitOfWork, its interpreter, and providers.
 *
 * @since   Sep.  8, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] trait UnitOfWorkProgramStubProvider {
  def resultFor[A](ordinal: Int, operation: UnitOfWorkOp[A]): Consequence[A]
}

private[cncf] final case class UnitOfWorkFailureInjection(
  ordinal: Int,
  failure: Conclusion
)

private[cncf] final case class UnitOfWorkProgramRecordingOccurrence(
  operation: UnitOfWorkOp[?],
  ordinal: Int,
  effectClass: UnitOfWorkEffectClass,
  outcome: Consequence[?]
)

private[cncf] final case class UnitOfWorkProgramRecording[A](
  result: Consequence[A],
  occurrences: Vector[UnitOfWorkProgramRecordingOccurrence],
  plan: UnitOfWorkProgramPlan
)

private[cncf] object UnitOfWorkProgramRecorder {
  def record[A](
    program: ExecUowM[A],
    provider: UnitOfWorkProgramStubProvider,
    failureInjection: Option[UnitOfWorkFailureInjection] = None
  ): UnitOfWorkProgramRecording[A] = {
    var captured = Vector.empty[UnitOfWorkProgramRecordingOccurrence]
    val step: UnitOfWorkOp ~> Consequence =
      new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = {
          val ordinal = captured.size
          val supplied = provider.resultFor(ordinal, operation)
          val outcome = failureInjection match {
            case Some(injection) if injection.ordinal == ordinal =>
              Consequence.Failure[A](injection.failure)
            case _ =>
              supplied
          }
          captured :+= UnitOfWorkProgramRecordingOccurrence(
            operation,
            ordinal,
            UnitOfWorkOperationClassifier.classify(operation),
            outcome
          )
          outcome
        }
      }
    val result = program.value.foldMap(step).flatMap(value => value)
    val occurrences = captured
    UnitOfWorkProgramRecording(
      result,
      occurrences,
      UnitOfWorkProgramPlanner.plan(occurrences.map(_.operation))
    )
  }
}
