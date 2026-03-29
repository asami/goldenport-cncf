package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.ExecUowM

/*
 * @since   Mar. 30, 2026
 * @version Mar. 30, 2026
 * @author  ASAMI, Tomoharu
 */
trait AggregateBehavior[A] extends Behavior {
  protected def build_Program(target: A): ExecUowM[OperationResponse]

  final def run(target: A, ctx: ExecutionContext): Consequence[OperationResponse] =
    build_Program(target).value.foldMap(ctx.runtime.unitOfWorkInterpreter).flatMap(identity)
}
