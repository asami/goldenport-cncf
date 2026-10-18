package org.goldenport.cncf.validation

import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{SbtOperation, SbtScenarioSelection, SbtTarget}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workspace.WorkspaceId

/** Bind an exact discovered operation to typed intent without admission or execution.
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
object ValidationOperationBindingV1 {
  final class BoundOperation private[ValidationOperationBindingV1] (
    val operation: ValidationDiscoveryV1.Operation,
    val intent: UnitOfWorkOp.SbtExec
  )

  def bindC(
    inventory: ValidationDiscoveryV1.Inventory,
    reference: ValidationDiscoveryV1.OperationReference,
    workspaceId: WorkspaceId
  ): Consequence[BoundOperation] =
    for {
      operation <- ValidationDiscoveryV1.resolveC(inventory, reference)
      _ <- WorkspaceId.parseC(workspaceId.value)
      selector <- SbtScenarioSelection(operation.selector.suiteClassName, operation.selector.testName).validateC
    } yield new BoundOperation(operation, UnitOfWorkOp.SbtExec(SbtTarget(workspaceId, Some(selector)), SbtOperation.TestOnly))
}
