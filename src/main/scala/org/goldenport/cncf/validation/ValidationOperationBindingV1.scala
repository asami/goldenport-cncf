package org.goldenport.cncf.validation

import org.goldenport.Consequence
import io.circe.Json
import org.goldenport.cncf.processexecution.{SbtOperation, SbtScenarioParameters, SbtScenarioSelection, SbtTarget}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workspace.WorkspaceId

/** Bind an exact discovered operation to typed intent without admission or execution.
 * @since   Oct. 18, 2026
 *  version Oct. 19, 2026
 * @version Nov.  6, 2026
 * @author  ASAMI, Tomoharu
 */
object ValidationOperationBindingV1 {
  final class BoundOperation private[ValidationOperationBindingV1] (
    val operation: ValidationDiscoveryV1.Operation,
    val intent: UnitOfWorkOp.SbtExec
  ) {
    val parameters: Map[String, SbtScenarioParameters.Value] = intent.target.scenario.get.parameters
  }

  def bindVariantsC(
    inventory: ValidationDiscoveryV1.Inventory,
    reference: ValidationDiscoveryV1.OperationReference,
    workspaceId: WorkspaceId,
    parameters: Map[String, Json] = Map.empty,
    defaultModes: Set[String] = Set("develop")
  ): Consequence[Vector[BoundOperation]] = for {
    operation <- ValidationDiscoveryV1.resolveC(inventory, reference)
    modes <- ValidationExecutionModeV1.variantsC(operation.metadata.get.executionModes, defaultModes)
    bound <- modes.foldLeft(Consequence.success(Vector.empty[BoundOperation])) { (prior, mode) => for {
      values <- prior
      value <- _bind_c(inventory, reference, workspaceId, parameters, Some(mode))
    } yield values :+ value }
  } yield bound

  def bindC(
    inventory: ValidationDiscoveryV1.Inventory,
    reference: ValidationDiscoveryV1.OperationReference,
    workspaceId: WorkspaceId,
    parameters: Map[String, Json] = Map.empty
  ): Consequence[BoundOperation] = for {
    operation <- ValidationDiscoveryV1.resolveC(inventory, reference)
    modes = operation.metadata.get.executionModes
    _ <- if (modes.forall(_.size == 1)) Consequence.unit
      else Consequence.serviceUnavailable("Required mode variants need bindVariantsC; a single binding cannot drop coverage")
    bound <- _bind_c(inventory, reference, workspaceId, parameters, modes.flatMap(_.headOption))
  } yield bound

  private def _bind_c(inventory: ValidationDiscoveryV1.Inventory,
    reference: ValidationDiscoveryV1.OperationReference, workspaceid: WorkspaceId,
    parameters: Map[String, Json], mode: Option[String]): Consequence[BoundOperation] =
    for {
      operation <- ValidationDiscoveryV1.resolveC(inventory, reference)
      _ <- WorkspaceId.parseC(workspaceid.value)
      inputs <- ValidationInputsV1.bindC(operation.metadata.get.inputSchema, parameters)
      selector <- SbtScenarioSelection(operation.selector.suiteClassName, operation.selector.testName, inputs).validateC
    } yield new BoundOperation(operation, UnitOfWorkOp.SbtExec(SbtTarget(workspaceid, Some(selector), validationMode = mode,
      validationProvider = if (mode.nonEmpty && operation.metadata.get.executionRequirement.exists(
        _.capabilities.exists(_.identity == "external-service"))) Some("live") else None), SbtOperation.TestOnly))
}
