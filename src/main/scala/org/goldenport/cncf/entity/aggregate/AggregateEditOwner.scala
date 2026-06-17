package org.goldenport.cncf.entity.aggregate

import org.goldenport.cncf.context.ExecutionContext

/*
 * @since   Jun. 14, 2026
 * @version Jun. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class AggregateEditOwner(
  principalId: Option[String],
  sessionId: Option[String] = None
) {
  def principalKey: Option[String] =
    principalId

  def sessionKey: Option[String] =
    for {
      principal <- principalId
      session <- sessionId
    } yield s"$principal@$session"
}

object AggregateEditOwner {
  val anonymous: AggregateEditOwner = AggregateEditOwner(None, None)

  def current(using ctx: ExecutionContext): AggregateEditOwner =
    AggregateEditOwner(
      Some(ctx.security.principal.id.value),
      ctx.security.session.flatMap(_.sessionId)
    )
}
