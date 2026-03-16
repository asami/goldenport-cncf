package org.goldenport.cncf.entity.runtime

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datatype.EntityId
import org.goldenport.cncf.entity.{EntityPersistent, EntityStore}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp.EntityStoreLoad

/*
 * @since   Mar. 14, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
trait EntityLoader[E] {
  def load(id: EntityId): Option[E]
}

trait EntityLoaderFactory[E] {
  def create(using ctx: ExecutionContext): EntityLoader[E]
}

object EntityLoader {
  def apply[E](
    f: EntityId => Option[E]
  ): EntityLoader[E] = new EntityLoader[E] {
    def load(id: EntityId): Option[E] = f(id)
  }

  def fromEntityStore[E](
    store: EntityStore
  )(using tc: EntityPersistent[E], ctx: ExecutionContext): EntityLoader[E] =
    new EntityLoader[E] {
      def load(id: EntityId): Option[E] =
        store.load(id) match {
          case Consequence.Success(value) => value
          case Consequence.Failure(_) => None
        }
    }

  def fromExecutionContext[E](
  )(using tc: EntityPersistent[E]): EntityLoaderFactory[E] =
    new EntityLoaderFactory[E] {
      def create(using ctx: ExecutionContext): EntityLoader[E] =
        new EntityLoader[E] {
          def load(id: EntityId): Option[E] =
            ctx.entityStoreSpace.load(EntityStoreLoad(id, tc)) match {
              case Consequence.Success(value) => value
              case Consequence.Failure(_) => None
            }
        }
    }
}
