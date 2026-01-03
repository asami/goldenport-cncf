package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec.OperationDefinition
//import org.goldenport.cncf.component.ComponentActionEntry
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}
import org.goldenport.cncf.security.{Action as SecurityAction, SecuredResource}

// type Engine = ActionEngine

// trait ActionCallBuilder {
//   def build(
//     opdef: OperationDefinition,
//     request: Request,
//     executionContext: ExecutionContext,
//     correlationId: Option[CorrelationId]
//   ): Consequence[ActionCall]
// }

/*
 * @since   Apr. 11, 2025
 *  version Apr. 15, 2025
 *  version Dec. 21, 2025
 *  version Jan.  1, 2026
 *  version Jan.  2, 2026
 * @version Jan.  3, 2026
 * @author  ASAMI, Tomoharu
 */
// trait ActionExecutor {
//   def execute(
//     entry: ComponentActionEntry,
//     request: Request,
//     executionContext: ExecutionContext,
//     correlationId: Option[CorrelationId]
//   ): Consequence[OperationResponse]
// }

// class DefaultActionExecutor(
//   builder: ActionCallBuilder
// ) extends ActionExecutor {

//   override def execute(
//     entry: ComponentActionEntry,
//     request: Request,
//     executionContext: ExecutionContext,
//     correlationId: Option[CorrelationId]
//   ): Consequence[OperationResponse] = {
//     builder.build(entry.opdef, request, executionContext, correlationId).flatMap { basecall =>
//       val call = ActionLogicCall(basecall, entry.logic)
//       call.execute()
//     }
//   }
// }

// class EngineActionExecutor(
//   engine: ActionEngine,
//   builder: ActionCallBuilder
// ) extends ActionExecutor {

//   override def execute(
//     entry: ComponentActionEntry,
//     request: Request,
//     executionContext: ExecutionContext,
//     correlationId: Option[CorrelationId]
//   ): Consequence[OperationResponse] = {
//     builder.build(entry.opdef, request, executionContext, correlationId).flatMap { basecall =>
//       val call = ActionLogicCall(basecall, entry.logic)
//       engine.execute(call)
//     }
//   }
// }

// final case class ActionLogicCall(
//   base: ActionCall,
//   logic: ActionLogic
// ) extends ActionCall(base.core) {
//   override def action: Action = base.action
//   def accesses: Seq[ResourceAccess] = base.accesses

//   def execute(): Consequence[OperationResponse] =
//     logic.execute(this)
// }

// abstract class ActionLogic {
//   def execute(
//     call: ActionCall
//   ): Consequence[OperationResponse]
// }

// final class DefaultActionCallBuilder extends ActionCallBuilder {
//   def build(
//     opdef: OperationDefinition,
//     request: Request,
//     executionContext: ExecutionContext,
//     correlationId: Option[CorrelationId]
//   ): Consequence[ActionCall] = {
//     opdef.createOperationRequest(request).map { opreq =>
//       val action = ProtocolAction(request.operation)
//       DefaultActionCall(
//         action,
//         core = ActionCall.Core(
//           action = action,
//           executionContext = executionContext,
//           correlationId = correlationId
//         )
//       )
//     }
//   }
// }

// final case class DefaultActionCall(
//   override val action: Action,
//   override val core: ActionCall.Core
// ) extends ActionCall(core) {
//   def accesses: Seq[ResourceAccess] = Nil

//   def execute(): Consequence[OperationResponse] =
//     Consequence.failure("ActionCall execution is not implemented in Phase 1")
// }

final case class ResourceAccess(
  resource: SecuredResource,
  action: SecurityAction
)

// private final case class ProtocolAction(
//   name: String
// ) extends Action {
//   def createCall(core: ActionCall.Core): ActionCall =
//     DefaultActionCall(this, core)
// }
