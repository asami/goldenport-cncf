package org.goldenport.cncf.validation

import org.goldenport.Consequence
import io.circe.Json
import org.goldenport.cncf.processexecution.{SbtOperation, SbtScenarioParameters, SbtScenarioSelection, SbtTarget}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workspace.WorkspaceId

/** Bind an exact discovered operation to typed intent without admission or execution.
 * @since   Oct. 18, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
object ValidationOperationBindingV1 {
  final class BoundOperation private[ValidationOperationBindingV1] (
    val operation: ValidationDiscoveryV1.Operation,
    val intent: UnitOfWorkOp.SbtExec
  ) {
    val parameters: Map[String, SbtScenarioParameters.Value] = intent.target.scenario.get.parameters
  }

  def bindC(
    inventory: ValidationDiscoveryV1.Inventory,
    reference: ValidationDiscoveryV1.OperationReference,
    workspaceId: WorkspaceId,
    parameters: Map[String, Json] = Map.empty
  ): Consequence[BoundOperation] =
    for {
      operation <- ValidationDiscoveryV1.resolveC(inventory, reference)
      _ <- WorkspaceId.parseC(workspaceId.value)
      inputs <- ValidationInputsV1.bindC(operation.metadata.get.inputSchema, parameters)
      selector <- SbtScenarioSelection(operation.selector.suiteClassName, operation.selector.testName, inputs).validateC
    } yield new BoundOperation(operation, UnitOfWorkOp.SbtExec(SbtTarget(workspaceId, Some(selector)), SbtOperation.TestOnly))
}
