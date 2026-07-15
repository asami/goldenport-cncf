package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}
import org.goldenport.record.Record

/*
 * Runtime hook for pre-mutation transition validation.
 *
 * SM-02 integrates this hook in UnitOfWork interpreter before
 * EntityStore mutations (save/update/updateById).
 *
 * @since   Mar. 19, 2026
 *  version Mar. 24, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
trait TransitionValidationHook {
  def beforeSave[T](
    entity: T,
    tc: EntityPersistent[T]
  )(using ExecutionContext): Consequence[Unit]

  def beforeUpdate[T](
    entity: T,
    tc: EntityPersistent[T]
  )(using ExecutionContext): Consequence[Unit]

  def beforeUpdate[T](
    entity: T,
    tc: EntityPersistent[T],
    current: Record,
    proposed: Record
  )(using ExecutionContext): Consequence[Unit] = {
    val _ = (current, proposed)
    beforeUpdate(entity, tc)
  }

  def beforeUpdateById[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P]
  )(using ExecutionContext): Consequence[Unit]

  def beforeUpdateById[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P],
    current: Record,
    proposed: Record
  )(using ExecutionContext): Consequence[Unit] = {
    val _ = (current, proposed)
    beforeUpdateById(id, patch, tc)
  }
}

object TransitionValidationHook {
  val noop: TransitionValidationHook = new TransitionValidationHook {
    def beforeSave[T](
      entity: T,
      tc: EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      Consequence.unit
    }

    def beforeUpdate[T](
      entity: T,
      tc: EntityPersistent[T]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (entity, tc)
      Consequence.unit
    }

    def beforeUpdateById[P](
      id: EntityId,
      patch: P,
      tc: EntityPersistentUpdate[P]
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = (id, patch, tc)
      Consequence.unit
    }
  }
}
