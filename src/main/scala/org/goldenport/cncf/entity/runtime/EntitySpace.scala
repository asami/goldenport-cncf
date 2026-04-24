package org.goldenport.cncf.entity.runtime

import scala.collection.mutable
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * @since   Mar. 14, 2026
 *  version Mar. 27, 2026
 * @version Apr. 25, 2026
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
  ): Option[org.simplemodeling.model.datatype.EntityId] =
    entityOption[Any](name).flatMap(_.resolveEntityId(idOrShortid))

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
}
