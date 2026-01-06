package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.id.UniversalId
import org.goldenport.cncf.datastore.{DataStore, QueryDirective, SelectResult}
import org.goldenport.cncf.*

/*
 * @since   Apr. 11, 2025
 * @version Dec. 18, 2025
 * @author  ASAMI, Tomoharu
 */
trait EntityStore[E] {
  def name: String
  def serialize(entity: E): DataStore.Record
  def deserialize(record: DataStore.Record): E
  def create(id: UniversalId, entity: E): Unit
  def load(id: UniversalId): Option[E]
  def store(id: UniversalId, entity: E): Unit
  def update(id: UniversalId, changes: DataStore.Record): Unit
  def delete(id: UniversalId): Unit
  def select(directive: QueryDirective): SelectResult
}

object EntityStore {
  type Record = DataStore.Record

  trait EntityInstance[T] {
  }

  final case class EntityId(value: UniversalId)

  def create[T](store: EntityStore[T], data: Record)(using instance: EntityInstance[T]): Consequence[CreateResult[T]] = {
    ???
  }

  def load[T](store: EntityStore[T])(using instance: EntityInstance[T]): Consequence[GetResult[T]] = {
    ???
  }

  def select[T](store: EntityStore[T], directive: QueryDirective)(using instance: EntityInstance[T]): Consequence[SelectResult] = {
    ???
  }

  def store[T](store: EntityStore[T], id: EntityId, data: Record)(using instance: EntityInstance[T]): Consequence[UpdateResult[T]] = {
    ???
  }

  def update[T](store: EntityStore[T], id: EntityId, changes: Record)(using instance: EntityInstance[T]): Consequence[UpdateResult[T]] = {
    ???
  }

  def delete[T](store: EntityStore[T], data: Record)(using instance: EntityInstance[T]): Consequence[DeleteResult[T]] = {
    ???
  }

  case class CreateResult[T]()
  case class GetResult[T]()
  case class UpdateResult[T]()
  case class DeleteResult[T]()
}
