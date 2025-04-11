package org.simplemodeling.componentframework.entity

import org.simplemodeling.componentframework.*

case class EntityStore(name: String) {

}

object EntityStore {
  type Record = Map[String, Any]

  trait EntityInstance[T] {
  }

  sealed trait EntityId {
  }

  def create[T](store: EntityStore, data: Record)(using instance: EntityInstance[T]): Consequence[CreateResult[T]] = {
    ???
  }

  def get[T](store: EntityStore)(using instance: EntityInstance[T]): Consequence[GetResult[T]] = {
    ???
  }

  def list[T](store: EntityStore, directive: ListDirective)(using instance: EntityInstance[T]): Consequence[ListResult[T]] = {
    ???
  }

  def update[T](store: EntityStore, id: EntityId, data: Record)(using instance: EntityInstance[T]): Consequence[UpdateResult[T]] = {
    ???
  }

  def delete[T](store: EntityStore, data: Record)(using instance: EntityInstance[T]): Consequence[DeleteResult[T]] = {
    ???
  }

  case class ListDirective()

  case class CreateResult[T]()
  case class GetResult[T]()
  case class ListResult[T]()
  case class UpdateResult[T]()
  case class DeleteResult[T]()
}
