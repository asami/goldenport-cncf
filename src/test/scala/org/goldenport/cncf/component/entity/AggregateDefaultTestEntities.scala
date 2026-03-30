/*
 * @since   Mar. 30, 2026
 * @version Mar. 30, 2026
 */
package org.goldenport.cncf.component.entity

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

object Order {
  val collectionId: EntityCollectionId = EntityCollectionId("test", "1", "order")

  given EntityPersistent[Order] with
    def id(e: Order): EntityId = e.id
    def toRecord(e: Order): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[Order] = createC(r)

  def createC(r: Record): Consequence[Order] =
    Consequence.success(
      Order(
        id = EntityId.parse(r.asMap("id").toString).TAKE,
        name = r.asMap("name").toString,
        status = r.asMap("status").toString
      )
    )
}

final case class Order(
  id: EntityId,
  name: String,
  status: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id.value,
      "name" -> name,
      "status" -> status,
      "postStatus" -> "draft",
      "aliveness" -> "alive"
    )
}

object OrderLine {
  val collectionId: EntityCollectionId = EntityCollectionId("test", "1", "order_line")

  given EntityPersistent[OrderLine] with
    def id(e: OrderLine): EntityId = e.id
    def toRecord(e: OrderLine): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[OrderLine] = createC(r)

  def createC(r: Record): Consequence[OrderLine] =
    Consequence.success(
      OrderLine(
        id = EntityId.parse(r.asMap("id").toString).TAKE,
        orderId = EntityId.parse(r.asMap("orderId").toString).TAKE,
        name = r.asMap("name").toString,
        quantity = r.asMap("quantity").toString.toInt
      )
    )
}

final case class OrderLine(
  id: EntityId,
  orderId: EntityId,
  name: String,
  quantity: Int
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id.value,
      "orderId" -> orderId.value,
      "name" -> name,
      "quantity" -> quantity,
      "postStatus" -> "draft",
      "aliveness" -> "alive"
    )
}

object Customer {
  val collectionId: EntityCollectionId = EntityCollectionId("test", "1", "customer")

  given EntityPersistent[Customer] with
    def id(e: Customer): EntityId = e.id
    def toRecord(e: Customer): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[Customer] = createC(r)

  def createC(r: Record): Consequence[Customer] =
    Consequence.success(
      Customer(
        id = EntityId.parse(r.asMap("id").toString).TAKE,
        orderId = EntityId.parse(r.asMap("orderId").toString).TAKE,
        name = r.asMap("name").toString
      )
    )
}

final case class Customer(
  id: EntityId,
  orderId: EntityId,
  name: String
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id.value,
      "orderId" -> orderId.value,
      "name" -> name,
      "postStatus" -> "draft",
      "aliveness" -> "alive"
    )
}
