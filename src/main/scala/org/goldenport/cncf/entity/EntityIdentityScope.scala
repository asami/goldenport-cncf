package org.goldenport.cncf.entity

import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record

/*
 * @since   May. 2, 2026
 * @version May.  2, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait EntityIdentityScope {
  def criteria(using ExecutionContext): Vector[EntityIdentityScope.Criterion]

  final def matches(record: Record)(using ExecutionContext): Boolean =
    criteria.forall(_.matches(record))
}

object EntityIdentityScope {
  final case class Criterion(fieldName: String, value: String) {
    def matches(record: Record): Boolean =
      SimpleEntityStorageShapePolicy
        .stringValue(record, fieldName)
        .exists(_ == value)
  }

  case object CurrentContext extends EntityIdentityScope {
    def criteria(using ctx: ExecutionContext): Vector[Criterion] = {
      val attrs = ctx.security.principal.attributes.map { case (k, v) =>
        _normalize(k) -> v.trim
      }
      Vector(
        _attribute(attrs, "tenantId", "tenant_id", "tenant"),
        _attribute(attrs, "organizationId", "organization_id", "organization", "orgId", "org_id")
      ).flatten
    }
  }

  case object Global extends EntityIdentityScope {
    def criteria(using ExecutionContext): Vector[Criterion] = Vector.empty
  }

  final case class Explicit(fields: Vector[Criterion]) extends EntityIdentityScope {
    def criteria(using ExecutionContext): Vector[Criterion] = fields
  }

  object Explicit {
    def apply(fields: (String, String)*): Explicit =
      Explicit(fields.toVector.map { case (k, v) => Criterion(k, v) })
  }

  private def _attribute(
    attrs: Map[String, String],
    canonical: String,
    names: String*
  ): Option[Criterion] =
    names
      .iterator
      .map(_normalize)
      .flatMap(attrs.get)
      .find(_.nonEmpty)
      .map(Criterion(canonical, _))

  private def _normalize(p: String): String =
    p.filter(_.isLetterOrDigit).toLowerCase(java.util.Locale.ROOT)
}
