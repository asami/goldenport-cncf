package org.goldenport.cncf.entity.runtime

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityIdentityScope
import org.goldenport.cncf.observability.CallTreeValueSummary
import org.goldenport.observation.Descriptor
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Mar. 14, 2026
 *  version Mar. 27, 2026
 *  version May. 10, 2026
 * @version Jul. 29, 2026
 * @author  ASAMI, Tomoharu
 */
// Component-local container for entity collections.
// Storage is owned by each EntityCollection via descriptor/storage accessors.
class EntitySpace {
  private val _entity_collections
      : mutable.Map[EntityCollectionId, EntityCollection[_]] =
    mutable.Map.empty

  def registerEntity[E](name: String, collection: EntityCollection[E]): Unit = {
    val planentityname = collection.descriptor.plan.entityName
    val collectionid = collection.descriptor.collectionId
    if (planentityname != name)
      throw new IllegalStateException(
        s"EntityCollection registration mismatch: name='$name', plan.entityName='$planentityname'"
      )
    else if (_entity_collections.contains(collectionid))
      throw new IllegalStateException(
        s"EntityCollection already registered: ${collectionid.print}"
      )
    else
      _entity_collections.update(collectionid, collection)
  }

  def entity[E](name: String): EntityCollection[E] =
    entityByNameC[E](name) match {
      case Consequence.Success(collection) => collection
      case Consequence.Failure(conclusion) =>
        throw new IllegalStateException(conclusion.displayMessage)
    }

  def entityOption[E](name: String): Option[EntityCollection[E]] =
    _collections_by_name(name) match {
      case Vector(collection) =>
        Some(collection.asInstanceOf[EntityCollection[E]])
      case _ =>
        None
    }

  def entityByNameC[E](
    name: String
  ): Consequence[EntityCollection[E]] =
    _collections_by_name(name) match {
      case Vector(collection) =>
        Consequence.success(
          collection.asInstanceOf[EntityCollection[E]]
        )
      case Vector() =>
        Consequence.resourceNotFound(
          s"EntityCollection not found: $name"
        )
      case collections =>
        val candidates =
          collections
            .map(_.descriptor.collectionId.print)
            .sorted
            .mkString(", ")
        Consequence.stateInvalid(
          s"EntityCollection name is ambiguous: $name",
          Vector(
            Descriptor.Facet.Policy("entity.collection.identity"),
            Descriptor.Facet.Reason("entity-collection-name-ambiguous"),
            Descriptor.Facet.Actual(candidates)
          )
        )
    }

  def canonicalCollectionIdC(
    collectionId: EntityCollectionId
  ): Consequence[EntityCollectionId] =
    entityOption(collectionId) match {
      case Some(_) =>
        Consequence.success(collectionId)
      case None =>
        Consequence.resourceNotFound(
          s"EntityCollection not found: ${collectionId.print}"
        )
    }

  def canonicalEntityIdC(
    id: EntityId
  ): Consequence[EntityId] =
    canonicalCollectionIdC(id.collection).map(_ => id)

  def entityNames: Vector[String] =
    _entity_collections.valuesIterator
      .map(_.descriptor.plan.entityName)
      .toSet
      .toVector
      .sorted

  def entityCollectionIds: Vector[EntityCollectionId] =
    _entity_collections.keysIterator.toVector.sortBy(_.print)

  def entityCollections: Vector[EntityCollection[?]] =
    entityCollectionIds.flatMap(_entity_collections.get)

  def entityCollectionsByName(
    name: String
  ): Vector[EntityCollection[?]] =
    _collections_by_name(name)

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
      entityOption(collectionId)
        .map(_.asInstanceOf[EntityCollection[E]])
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
      entityOption(collectionId)
        .map(_.asInstanceOf[EntityCollection[E]])
        .flatMap(_.resolveIdentity(value, fieldNames, includeEntityIdEntropy, scope))
    }

  def entityOption(
    collectionId: EntityCollectionId
  ): Option[EntityCollection[?]] =
    _entity_collections.get(collectionId)

  private def _collections_by_name(
    name: String
  ): Vector[EntityCollection[_]] =
    _entity_collections.iterator.collect {
      case (collectionid, collection)
          if collectionid.name == name ||
            collection.descriptor.plan.entityName == name =>
        collection
    }.toVector

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
    collectionid: EntityCollectionId
  ): Map[String, String] =
    Map(
      "space" -> "entity",
      "operation" -> operation,
      "collection" -> collectionid.print
    )
}
