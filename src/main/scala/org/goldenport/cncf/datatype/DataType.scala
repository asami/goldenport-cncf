package org.goldenport.cncf.datatype

import scala.util.*
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.schema.XString

/*
 * @since   Apr. 11, 2025
 * @version Feb. 22, 2026
 * @author  ASAMI, Tomoharu
 */
// abstract class EntityId {
//   def string: String
// }
// object EntityId {
//   def readC(v: Any): Consequence[EntityId] = v match {
//     case s: String => Consequence.success(Instance(s))
//     case _ => Consequence.failValueInvalid(v, XString) // TODO
//   }

//   given ValueReader[EntityId] with
//     def readC(v: Any): Consequence[EntityId] = EntityId.readC(v)

//   case class Instance(string: String) extends EntityId
// }

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
