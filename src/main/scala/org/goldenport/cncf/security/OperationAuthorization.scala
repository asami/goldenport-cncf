package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.context.{ExecutionContext, SubjectKind}
import org.goldenport.record.Record

/*
 * @since   Apr. 18, 2026
 * @version Apr. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OperationAuthorizationRule(
  operationModes: Vector[OperationMode] = Vector.empty,
  allowAnonymous: Boolean = false,
  anonymousOperationModes: Vector[OperationMode] = Vector.empty
)

object OperationAuthorizationRule {
  def developAnonymousAdmin(
    runtimeConfig: RuntimeConfig
  ): OperationAuthorizationRule =
    OperationAuthorizationRule(
      allowAnonymous = runtimeConfig.webDevelopAnonymousAdmin,
      anonymousOperationModes = Vector(OperationMode.Develop, OperationMode.Test)
    )

  def fromRecord(record: Record): OperationAuthorizationRule =
    OperationAuthorizationRule(
      operationModes = _operation_modes(record, "operationModes", "operation_modes", "modes"),
      allowAnonymous = _boolean(record, "allowAnonymous", "allow_anonymous").getOrElse(false),
      anonymousOperationModes = _operation_modes(record, "anonymousOperationModes", "anonymous_operation_modes", "anonymousModes", "anonymous_modes")
    )

  private def _operation_modes(
    record: Record,
    keys: String*
  ): Vector[OperationMode] =
    _string_vector(record, keys.toList).flatMap(OperationMode.from)

  private def _boolean(record: Record, keys: String*): Option[Boolean] =
    keys.iterator.map(record.getString).collectFirst {
      case Some(s) if _truthy(s) => true
      case Some(s) if _falsy(s) => false
    }

  private def _truthy(s: String): Boolean = {
    val v = s.trim.toLowerCase(java.util.Locale.ROOT)
    v == "true" || v == "yes" || v == "on" || v == "1"
  }

  private def _falsy(s: String): Boolean = {
    val v = s.trim.toLowerCase(java.util.Locale.ROOT)
    v == "false" || v == "no" || v == "off" || v == "0"
  }

  private def _string_vector(
    record: Record,
    keys: List[String]
  ): Vector[String] =
    keys.iterator.map(record.getAny).collectFirst {
      case Some(xs: Seq[?]) =>
        xs.toVector.map(_.toString.trim).filter(_.nonEmpty)
      case Some(s: String) if s.trim.nonEmpty =>
        s.split("[,|\\s]+").toVector.map(_.trim).filter(_.nonEmpty)
    }.getOrElse(Vector.empty)
}

trait OperationAuthorizationProvider {
  def operationAuthorization(
    runtimeConfig: RuntimeConfig
  ): OperationAuthorizationRule
}

object OperationAuthorization {
  def authorize(
    selector: String,
    rule: OperationAuthorizationRule
  )(using ctx: ExecutionContext): Consequence[Unit] =
    if (rule.operationModes.nonEmpty && !rule.operationModes.contains(ctx.operationMode))
      _denied(selector, "operation-mode")
    else if (ctx.security.subjectKind == SubjectKind.Anonymous && !rule.allowAnonymous)
      _denied(selector, "anonymous")
    else if (
      ctx.security.subjectKind == SubjectKind.Anonymous &&
        rule.anonymousOperationModes.nonEmpty &&
        !rule.anonymousOperationModes.contains(ctx.operationMode)
    )
      _denied(selector, "anonymous-operation-mode")
    else
      Consequence.unit

  private def _denied[A](
    selector: String,
    reason: String
  )(using ctx: ExecutionContext): Consequence[A] =
    Consequence.securityPermissionDenied(
      s"Operation access is denied: ${selector}",
      Seq(
        Descriptor.Facet.Name("operation-authorization"),
        Descriptor.Facet.Parameter.argument("selector"),
        Descriptor.Facet.Value(selector),
        Descriptor.Facet.Parameter.argument("reason"),
        Descriptor.Facet.Value(reason)
      )
    )
}
