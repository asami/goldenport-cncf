/*
 * @since   Mar. 30, 2026
 *  version Apr. 10, 2026
 * @version Apr. 16, 2026
 */
package org.goldenport.cncf.component.entity

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent}
import org.goldenport.schema.{Column, Schema, ValueDomain, WebColumn, XString}
import org.goldenport.value.BaseContent
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

object Order {
  val collectionId: EntityCollectionId = EntityCollectionId("test", "a", "order")
  val schema: Schema = Schema(Vector(
    Column(BaseContent.simple("id"), ValueDomain(datatype = XString)),
    Column(BaseContent.simple("name"), ValueDomain(datatype = XString)),
    Column(
      BaseContent.Builder("status").label("Order status").build(),
      ValueDomain(datatype = XString),
      web = WebColumn(
        controlType = Some("select"),
        values = Vector("draft", "submitted", "approved"),
        required = Some(true),
        help = Some("CML generated status hint.")
      )
    )
  ))

  given EntityPersistent[Order] with
    def id(e: Order): EntityId = e.id
    def toRecord(e: Order): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[Order] = createC(r)

  def createC(r: Record): Consequence[Order] =
    Consequence.success(
      Order(
        id = EntityId.parse(r.getString("id").getOrElse(sys.error("id missing"))).TAKE,
        name = r.getString("name").getOrElse(sys.error("name missing")),
        status = r.getString("status").getOrElse(sys.error("status missing"))
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
  val collectionId: EntityCollectionId = EntityCollectionId("test", "a", "order_line")

  given EntityPersistent[OrderLine] with
    def id(e: OrderLine): EntityId = e.id
    def toRecord(e: OrderLine): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[OrderLine] = createC(r)

  def createC(r: Record): Consequence[OrderLine] =
    Consequence.success(
      OrderLine(
        id = EntityId.parse(r.getString("id").getOrElse(sys.error("id missing"))).TAKE,
        orderId = EntityId.parse(r.getString("orderId").getOrElse(sys.error("orderId missing"))).TAKE,
        name = r.getString("name").getOrElse(sys.error("name missing")),
        quantity = r.getInt("quantity").getOrElse(sys.error("quantity missing"))
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
  val collectionId: EntityCollectionId = EntityCollectionId("test", "a", "customer")

  given EntityPersistent[Customer] with
    def id(e: Customer): EntityId = e.id
    def toRecord(e: Customer): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[Customer] = createC(r)

  def createC(r: Record): Consequence[Customer] =
    Consequence.success(
      Customer(
        id = EntityId.parse(r.getString("id").getOrElse(sys.error("id missing"))).TAKE,
        orderId = EntityId.parse(r.getString("orderId").getOrElse(sys.error("orderId missing"))).TAKE,
        name = r.getString("name").getOrElse(sys.error("name missing"))
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
