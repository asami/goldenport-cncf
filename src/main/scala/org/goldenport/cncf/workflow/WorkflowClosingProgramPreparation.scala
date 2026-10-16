package org.goldenport.cncf.workflow

import cats.~>
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}

/** Completed declarative closing program, not executed data effects or a host
  * grant. Every exported local projection is fresh; native admission remains
  * mandatory. No stub provider, supplied commit Boolean or interpreted I/O.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[cncf] final class WorkflowClosingProgramPreparation private (
  val result: StateMachineOperationResult,
  intents: Vector[WorkflowClosingLocalIntent]
) {
  private[workflow] def localIntentsC:
      Consequence[(Vector[DataStoreConditionalRoot], Vector[EntityVersionedSideEffect.Create])] =
    intents.foldLeft(Consequence.success((Vector.empty[DataStoreConditionalRoot],
      Vector.empty[EntityVersionedSideEffect.Create]))) { (acc, intent) => for {
      current <- acc
      projected <- intent.projectC
    } yield projected match {
      case Left(guard) => (current._1 :+ guard, current._2)
      case Right(create) => (current._1, current._2 :+ create)
    }}
}

private[cncf] object WorkflowClosingProgramPreparation {
  private val maxIntents = 71 // Seven local guards plus at most 64 Creates; native combined limits still apply.
  def prepareC(program: ExecUowM[ActionExecution]): Consequence[WorkflowClosingProgramPreparation] =
    if (program == null) Consequence.configurationInvalid("Closing program is missing")
    else {
      var captured = Vector.empty[WorkflowClosingLocalIntent]
      val stage = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.StageWorkflowClosingLocalIntent(intent) =>
            if (intent == null || captured.size >= maxIntents)
              Consequence.stateConflict("Closing local intent is missing or exceeds the bounded plan")
            else intent.projectC.map { _ => captured :+= intent; () }
          case _ => Consequence.stateConflict("Closing preparation rejects an unsupported operation before interpretation")
        }
      }
      try program.value.foldMap(stage).flatMap(identity).flatMap {
        case ActionExecution.Completed(result) if validResult(result) =>
          val prepared = new WorkflowClosingProgramPreparation(result, captured)
          prepared.localIntentsC.flatMap { case (guards, creates) =>
            val targets = guards.map(g => g.collection.print -> g.entryId.print) ++
              creates.map(c => c.collection.print -> c.entryId.print)
            if (guards.size >= DataStoreConditionalMultiGuardPlan.MAX_ADDITIONAL_GUARDS || creates.size > 64 ||
                targets.distinct.size != targets.size)
              Consequence.stateConflict("Closing plan exceeds canonical limits or duplicates local targets")
            else Consequence.success(prepared)
          }
        case _ => Consequence.stateConflict("Closing preparation requires a complete typed Completed result; suspension/failure is not admitted")
      } catch {
        case e: InterruptedException => Thread.currentThread().interrupt(); throw e
        case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
      }
    }
  private def validResult(result: StateMachineOperationResult): Boolean =
    result != null && result.typeReference != null && result.contextReference != null &&
      Vector(result.typeReference.value, result.contextReference.identity, result.contextReference.revision)
        .forall(value => value != null && value.trim.nonEmpty)
}
