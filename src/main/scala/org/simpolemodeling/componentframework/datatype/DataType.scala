package org.simplemodeling.componentframework.datatype

import scala.util.*

trait EntityId {
  def string: String
}

case class ResourceId(id: String) {
  require (ResourceId.validate(id), s"Bad id: $id")
}

object ResourceId {
  def validate(id: String): Boolean = true

  def create(id: String): Try[ResourceId] =
    if (validate(id))
      Success(ResourceId(id))
    else
      Failure(new IllegalArgumentException(s"Bad id: $id"))
}

case class UserId(id: String) {
  require (UserId.validate(id), s"Bad id: $id")
}

object UserId {
  def validate(id: String): Boolean = true

  def create(id: String): Try[UserId] =
    if (validate(id))
      Success(UserId(id))
    else
      Failure(new IllegalArgumentException(s"Bad id: $id"))
}
