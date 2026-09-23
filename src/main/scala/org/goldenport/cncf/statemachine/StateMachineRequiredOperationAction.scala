package org.goldenport.cncf.statemachine

import cats.free.Free
import org.goldenport.ConsequenceT
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.{ActionExecution, ProviderExecutionRequest}

/*
 * Provider-neutral Required-SPI action lowering.
 *
 * Provider selection and invocation are deliberately owned by the active
 * UnitOfWork interpreter. This action only constructs its one typed intent.
 *
 * @since   Sep. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class StateMachineRequiredOperationAction[S, E](
  requestFactory: (S, E) => ProviderExecutionRequest
) extends ResolvedAction[S, E] {
  def program(state: S, event: E): org.goldenport.cncf.unitofwork.ExecUowM[ActionExecution] =
    ConsequenceT.liftF(
      Free.liftF(UnitOfWorkOp.StateMachineProviderExecute(requestFactory(state, event)))
    )
}
