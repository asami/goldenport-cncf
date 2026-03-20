package org.goldenport.cncf.security

import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.provisional.observation.Taxonomy
import org.goldenport.protocol.Request

/*
 * Common ingress security resolver for external entry points.
 *
 * - Operation request ingress
 * - Reception ingress
 *
 * @since   Mar. 20, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ResolvedIngressSecurity(
  executionContext: ExecutionContext,
  privilege: SecurityContext.Privilege,
  requestedCapabilities: Set[String]
)

trait IngressSecurityResolver {
  def resolve(request: Request): Consequence[ResolvedIngressSecurity]
  def resolve(attributes: Map[String, String]): Consequence[ResolvedIngressSecurity]
}

object IngressSecurityResolver {
  val default: IngressSecurityResolver = new DefaultIngressSecurityResolver

  def resolve(request: Request): Consequence[ResolvedIngressSecurity] =
    default.resolve(request)

  def resolve(attributes: Map[String, String]): Consequence[ResolvedIngressSecurity] =
    default.resolve(attributes)
}

private final class DefaultIngressSecurityResolver extends IngressSecurityResolver {
  private val _privilege_keys = Vector(
    "cncf.security.privilege",
    "security.privilege",
    "privilege"
  )

  private val _capability_keys = Vector(
    "cncf.security.capabilities",
    "cncf.security.capability",
    "security.capabilities",
    "security.capability",
    "capabilities",
    "capability"
  )

  def resolve(request: Request): Consequence[ResolvedIngressSecurity] = {
    val attrs = request.properties.foldLeft(Map.empty[String, String]) { (z, p) =>
      val value = Option(p.value).map(_.toString).getOrElse("")
      if (p.name.nonEmpty && value.nonEmpty) z.updated(p.name, value) else z
    }
    resolve(attrs)
  }

  def resolve(attributes: Map[String, String]): Consequence[ResolvedIngressSecurity] = {
    val privilege = _resolve_privilege(attributes)
    val caps = _resolve_requested_capabilities(attributes)
    privilege.flatMap { p =>
      val ctx = ExecutionContext.create(p)
      if (caps.isEmpty || ctx.security.hasAnyCapability(caps))
        Consequence.success(ResolvedIngressSecurity(ctx, p, caps))
      else
        Consequence.fail(
          Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Illegal),
          Facet.Operation("security.resolve"),
          Facet.Message(s"required capability: ${caps.toVector.sorted.mkString("|")}")
        )
    }
  }

  private def _resolve_privilege(
    attributes: Map[String, String]
  ): Consequence[SecurityContext.Privilege] = {
    val value = _find_first(attributes, _privilege_keys).map(_normalize_token)
    value match {
      case None =>
        Consequence.success(SecurityContext.Privilege.User)
      case Some("user") =>
        Consequence.success(SecurityContext.Privilege.User)
      case Some("applicationcontentmanager") | Some("contentmanager") | Some("contentadmin") =>
        Consequence.success(SecurityContext.Privilege.ApplicationContentManager)
      case Some(other) =>
        Consequence.fail(
          Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Invalid),
          Facet.Operation("security.resolve"),
          Facet.Message(s"invalid privilege: $other")
        )
    }
  }

  private def _resolve_requested_capabilities(
    attributes: Map[String, String]
  ): Set[String] =
    _capability_keys
      .flatMap(attributes.get)
      .flatMap(_split_tokens)
      .map(_normalize_token)
      .filter(_.nonEmpty)
      .toSet

  private def _find_first(
    attributes: Map[String, String],
    keys: Vector[String]
  ): Option[String] = {
    val normalized = attributes.map { case (k, v) => _normalize_token(k) -> v }
    keys.iterator.flatMap { key =>
      normalized.get(_normalize_token(key))
    }.find(_.trim.nonEmpty)
  }

  private def _split_tokens(p: String): Vector[String] =
    p.split("[,|\\s]+")
      .toVector

  private def _normalize_token(p: String): String =
    p.trim.toLowerCase.replace("_", "").replace("-", "")
}
