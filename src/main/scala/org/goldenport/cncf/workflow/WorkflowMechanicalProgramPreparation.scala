package org.goldenport.cncf.workflow

import cats.~>
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.datastore.{EntityVersionedSideEffect, WorkflowClosingLocalIntent}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}

/**
 * Capture Create-only declarative effects, without a fabricated AI completion.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[cncf] final class WorkflowMechanicalProgramPreparation private (
  private val intents: Vector[WorkflowClosingLocalIntent]
) {
  private[workflow] def createsC: Consequence[Vector[EntityVersionedSideEffect.Create]] =
    intents.foldLeft(Consequence.success(Vector.empty[EntityVersionedSideEffect.Create])) { (acc, intent) =>
      for {
        prior <- acc
        projected <- intent.projectC
        create <- projected match {
          case Right(value) => Consequence.success(value)
          case _ => Consequence.stateConflict("Mechanical program does not admit local guards")
        }
      } yield prior :+ create
    }
}

private[cncf] object WorkflowMechanicalProgramPreparation {
  def prepareC(program: ExecUowM[Unit]): Consequence[WorkflowMechanicalProgramPreparation] =
    if (program == null) Consequence.stateConflict("Mechanical program is missing")
    else {
      var captured = Vector.empty[WorkflowClosingLocalIntent]
      val stage = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](operation: UnitOfWorkOp[A]): Consequence[A] = operation match {
          case UnitOfWorkOp.StageWorkflowClosingLocalIntent(intent) =>
            if (intent == null || captured.size >= 64)
              Consequence.stateConflict("Mechanical program exceeds its bounded Create plan")
            else intent.projectC.flatMap {
              case Right(_) => captured :+= intent; Consequence.unit
              case _ => Consequence.stateConflict("Mechanical program rejects local guards")
            }
          case _ => Consequence.stateConflict("Mechanical preparation rejects unsupported effects")
        }
      }
      try program.value.foldMap(stage).flatMap(identity).flatMap { _ =>
        val prepared = new WorkflowMechanicalProgramPreparation(captured)
        prepared.createsC.flatMap { creates =>
          val targets = creates.map(c => c.collection.print -> c.entryId.print)
          if (targets.distinct.size != targets.size)
            Consequence.stateConflict("Mechanical program duplicates a Create target")
          else Consequence.success(prepared)
        }
      } catch {
        case e: InterruptedException => Thread.currentThread().interrupt(); throw e
        case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
      }
    }
}
