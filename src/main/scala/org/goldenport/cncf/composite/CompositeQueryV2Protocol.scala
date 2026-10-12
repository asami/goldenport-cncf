package org.goldenport.cncf.composite

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CompositeQueryV2Invocation(
  version: Int,
  branchId: String,
  targetId: String,
  request: Request
)

final case class CompositeQueryV2Reply(
  version: Int,
  branchId: String,
  targetId: String,
  response: OperationResponse
)

final case class CompositeQueryV2Targets private (
  private[composite] val _entries: Map[String, Subsystem]
) {
  def resolve(target: CompositeQueryV2Target, local: Subsystem): Consequence[(String, Subsystem)] =
    target match {
      case CompositeQueryV2Target.Local => Consequence.success("local" -> local)
      case CompositeQueryV2Target.Subsystem(id) =>
        _entries.get(id).map(x => id -> x) match {
          case Some(value) => Consequence.success(value)
          case None => Consequence.operationInvalid("composite-query-v2.unknown-target")
        }
    }
}

object CompositeQueryV2Targets {
  val empty: CompositeQueryV2Targets = CompositeQueryV2Targets(Map.empty)

  def create(entries: Vector[(String, Subsystem)]): Consequence[CompositeQueryV2Targets] = {
    val ids = entries.map(_._1.trim)
    if (ids.exists(_.isEmpty)) Consequence.argumentInvalid("composite-query-v2.target-id")
    else if (ids.exists(_.equalsIgnoreCase("local"))) Consequence.argumentInvalid("composite-query-v2.reserved-target")
    else if (ids.distinct.size != ids.size) Consequence.argumentInvalid("composite-query-v2.duplicate-target")
    else Consequence.success(CompositeQueryV2Targets(entries.map { case (id, subsystem) => id.trim -> subsystem }.toMap))
  }
}

final class CompositeQueryV2Protocol(
  local: Subsystem,
  targets: CompositeQueryV2Targets
) {
  def validate(branch: CompositeQueryV2Branch): Consequence[Unit] =
    targets.resolve(branch.target, local).map { _ => () }

  def invoke(
    invocation: CompositeQueryV2Invocation,
    branch: CompositeQueryV2Branch,
    context: ExecutionContext
  ): Consequence[CompositeQueryV2Reply] = {
    if (invocation.version != 2 || invocation.branchId != branch.branchId)
      Consequence.operationInvalid("composite-query-v2.protocol-envelope")
    else targets.resolve(branch.target, local).flatMap { case (targetid, subsystem) =>
      if (targetid != invocation.targetId || invocation.request != branch.request)
        Consequence.operationInvalid("composite-query-v2.protocol-correlation")
      else CompositeQueryV2ExecutionContext.branchContext(context, subsystem, branch.request).flatMap { branchcontext =>
        given ExecutionContext = branchcontext
        subsystem.executeQueryOnlyWithMetadata(invocation.request).flatMap { result =>
          val reply = CompositeQueryV2Reply(2, branch.branchId, targetid, result.response)
          CompositeQueryV2Protocol.validateReply(invocation, reply).map(_ => reply)
        }
      }
    }
  }
}

private[composite] object CompositeQueryV2Protocol {
  def validateReply(
    invocation: CompositeQueryV2Invocation,
    reply: CompositeQueryV2Reply
  ): Consequence[Unit] =
    if (reply.version != invocation.version || reply.branchId != invocation.branchId || reply.targetId != invocation.targetId)
      Consequence.operationInvalid("composite-query-v2.reply-correlation")
    else Consequence.unit
}
