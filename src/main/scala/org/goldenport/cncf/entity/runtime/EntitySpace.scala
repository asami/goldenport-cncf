package org.goldenport.cncf.entity.runtime

import scala.collection.mutable
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityIdentityScope
import org.goldenport.cncf.observability.CallTreeValueSummary
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Mar. 14, 2026
 *  version Mar. 27, 2026
 * @version May. 10, 2026
 * @author  ASAMI, Tomoharu
 */
// Component-local container for entity collections.
// Storage is owned by each EntityCollection via descriptor/storage accessors.
class EntitySpace {
  private val _entity_collections: mutable.Map[String, EntityCollection[_]] = mutable.Map.empty

  def registerEntity[E](name: String, collection: EntityCollection[E]): Unit = {
    val planentityname = collection.descriptor.plan.entityName
    if (planentityname != name)
      throw new IllegalStateException(
        s"EntityCollection registration mismatch: name='$name', plan.entityName='$planentityname'"
      )
    else if (_entity_collections.contains(name))
      throw new IllegalStateException(s"EntityCollection already registered: $name")
    else
      _entity_collections.update(name, collection)
  }

  def entity[E](name: String): EntityCollection[E] =
    _resolve(_entity_collections, name, "EntityCollection")

  def entityOption[E](name: String): Option[EntityCollection[E]] =
    _entity_collections.get(name).map(_.asInstanceOf[EntityCollection[E]])

  def entityNames: Vector[String] =
    _entity_collections.keys.toVector.sorted

  def resolveEntityId(
    name: String,
    idOrShortid: String
  ): Option[EntityId] =
    entityOption[Any](name).flatMap(_.resolveEntityId(idOrShortid))

  def uniqueValueExists[E](
    collectionId: EntityCollectionId,
    fieldName: String,
    value: String,
    excludeId: Option[EntityId],
    scope: EntityIdentityScope,
    includeEntityIdEntropy: Boolean
  )(using ctx: ExecutionContext): Boolean =
    _with_calltree("space:entity:unique-value-exists", _entity_space_attributes("unique-value-exists", collectionId) + ("field" -> fieldName)) {
      entityOption[E](collectionId.name)
        .orElse(entityOption(collectionId).map(_.asInstanceOf[EntityCollection[E]]))
        .exists(_.uniqueValueExists(fieldName, value, excludeId, scope, includeEntityIdEntropy))
    }

  def resolveIdentity[E](
    collectionId: EntityCollectionId,
    value: String,
    fieldNames: Vector[String],
    includeEntityIdEntropy: Boolean,
    scope: EntityIdentityScope
  )(using ctx: ExecutionContext): Option[EntityId] =
    _with_calltree("space:entity:resolve-identity", _entity_space_attributes("resolve-identity", collectionId)) {
      entityOption[E](collectionId.name)
        .orElse(entityOption(collectionId).map(_.asInstanceOf[EntityCollection[E]]))
        .flatMap(_.resolveIdentity(value, fieldNames, includeEntityIdEntropy, scope))
    }

  def entityOption(
    collectionId: EntityCollectionId
  ): Option[EntityCollection[?]] =
    _entity_collections.values.find(_.descriptor.collectionId == collectionId)

  private def _resolve[A](
    map: mutable.Map[String, _],
    name: String,
    kind: String
  ): A =
    map.get(name)
      .map(_.asInstanceOf[A])
      .getOrElse(
        throw new IllegalStateException(s"$kind not found: $name")
      )

  private def _with_calltree[A](
    label: String,
    attributes: Map[String, String]
  )(
    body: => A
  )(using ctx: ExecutionContext): A = {
    val calltree = ctx.observability.callTreeContext
    if (calltree.isEnabled) {
      calltree.enter(label, attributes ++ Map("calltree_kind" -> "space"))
      try {
        val result = body
        calltree.leave(Map("outcome" -> "success") ++ CallTreeValueSummary.resultAttributes(result))
        result
      } catch {
        case e: Throwable =>
          calltree.leave(Map("outcome" -> "failure", "error" -> e.getMessage))
          throw e
      }
    } else {
      body
    }
  }

  private def _entity_space_attributes(
    operation: String,
    collectionId: EntityCollectionId
  ): Map[String, String] =
    Map(
      "space" -> "entity",
      "operation" -> operation,
      "collection" -> collectionId.print
    )
}
