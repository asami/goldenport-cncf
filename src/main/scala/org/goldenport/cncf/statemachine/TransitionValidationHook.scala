package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.model.datatype.EntityId
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}

/*
 * Runtime hook for pre-mutation transition validation.
 *
 * SM-02 integrates this hook in UnitOfWork interpreter before
 * EntityStore mutations (save/update/updateById).
 *
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
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

  def beforeUpdateById[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P]
  )(using ExecutionContext): Consequence[Unit]
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

