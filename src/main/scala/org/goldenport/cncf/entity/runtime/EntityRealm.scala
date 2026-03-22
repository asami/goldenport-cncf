package org.goldenport.cncf.entity.runtime

import cats.effect.Ref
import org.goldenport.Consequence
import org.goldenport.model.datatype.EntityId
import org.goldenport.cncf.entity.EntityPersistent

/*
 * @since   Mar. 14, 2026
 * @version Mar. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final case class EntityRealmState[E](
  workingSet: Map[EntityId, E]
)

class EntityRealm[E](
  val entityName: String,
  loader: EntityLoader[E],
  state: Ref[cats.Id, EntityRealmState[E]]
)(using tc: EntityPersistent[E]) {
  private val _missing_entity_message = "entity not found"

  def get(id: EntityId): Option[E] =
    state.modify { s =>
      s.workingSet.get(id) match {
        case Some(e) =>
          (s, Some(e))
        case None =>
          loader.load(id) match {
            case Some(entity) =>
              val ns = s.copy(workingSet = s.workingSet.updated(tc.id(entity), entity))
              (ns, Some(entity))
            case None =>
              (s, None)
          }
      }
    }

  private def _update(entity: E): Unit =
    state.update(s => s.copy(workingSet = s.workingSet.updated(tc.id(entity), entity)))

  def put(entity: E): Unit =
    _update(entity)

  def contains(id: EntityId): Boolean =
    state.get.workingSet.contains(id)

  def values: Vector[E] =
    state.get.workingSet.values.toVector

  def resolve(id: EntityId): Consequence[E] =
    Consequence.successOrEntityNotFound(get(id))(id)
}
