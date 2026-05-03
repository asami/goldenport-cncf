package org.goldenport.cncf.id

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Kind-dispatch repository for Textus URNs.
 *
 * Textus URN is a generic in-document identity form. Concrete kinds,
 * such as blob, register resolvers here so application code does not
 * hard-code kind-specific lookup rules.
 *
 * @since   May.  3, 2026
 * @version May.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final case class UrnResolution(
  urn: TextusUrn,
  collection: EntityCollectionId,
  entityId: EntityId
)

trait TextusUrnResolver {
  def kind: String
  def resolve(urn: TextusUrn)(using ExecutionContext): Consequence[Option[UrnResolution]]
}

final class UrnRepository private (
  resolvers: Vector[TextusUrnResolver]
) {
  def resolve(urn: TextusUrn)(using ExecutionContext): Consequence[Option[UrnResolution]] =
    resolvers.find(_.kind == urn.kind) match {
      case Some(resolver) => resolver.resolve(urn)
      case None => Consequence.success(None)
    }

  def resolveRequired(urn: TextusUrn)(using ExecutionContext): Consequence[UrnResolution] =
    resolve(urn).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.operationNotFound(s"textus urn:${urn.print}")
    }
}

object UrnRepository {
  val empty: UrnRepository =
    new UrnRepository(Vector.empty)

  def apply(resolvers: TextusUrnResolver*): UrnRepository =
    new UrnRepository(resolvers.toVector)

  def from(resolvers: Iterable[TextusUrnResolver]): UrnRepository =
    new UrnRepository(resolvers.toVector)
}
