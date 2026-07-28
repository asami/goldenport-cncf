/*
 * @since   Mar. 30, 2026
 * @version Jul. 28, 2026
 */
package org.goldenport.cncf.component.entity

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.entity.{
  EntityPersistable,
  EntityPersistent,
  EntityStoreDecodeContext
}
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
    override def fromStoreRecord(
      context: EntityStoreDecodeContext,
      record: Record
    ): Consequence[Order] =
      createC(record).map(entity =>
        entity.copy(id = entity.id.copy(collection = context.owningCollectionId))
      )

  def createC(r: Record): Consequence[Order] =
    Consequence.success(
      Order(
        id = _entity_id(r, "id"),
        name = r.getString("name").getOrElse(sys.error("name missing")),
        status = r.getString("status").getOrElse(sys.error("status missing"))
      )
    )

  private def _entity_id(record: Record, name: String): EntityId =
    record.getAny(name) match {
      case Some(id: EntityId) => id
      case Some(value: String) => EntityId.parse(value).TAKE
      case _ => sys.error(s"$name missing")
    }
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
    override def fromStoreRecord(
      context: EntityStoreDecodeContext,
      record: Record
    ): Consequence[OrderLine] =
      createC(record).map(entity =>
        entity.copy(
          id = entity.id.copy(collection = context.owningCollectionId),
          orderId = entity.orderId.copy(collection = Order.collectionId)
        )
      )

  def createC(r: Record): Consequence[OrderLine] =
    Consequence.success(
      OrderLine(
        id = EntityId.parse(r.getString("id").getOrElse(sys.error("id missing"))).TAKE,
        orderId = EntityId.parse(r.getString("orderId").getOrElse(sys.error("orderId missing"))).TAKE,
        name = r.getString("name").getOrElse(sys.error("name missing")),
        quantity = r.getInt("quantity").getOrElse(sys.error("quantity missing")),
        sortOrder = r.getInt("sortOrder")
      )
    )
}

final case class OrderLine(
  id: EntityId,
  orderId: EntityId,
  name: String,
  quantity: Int,
  sortOrder: Option[Int] = None
) extends EntityPersistable {
  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id.value,
      "orderId" -> orderId.value,
      "name" -> name,
      "quantity" -> quantity,
      "sortOrder" -> sortOrder,
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
    override def fromStoreRecord(
      context: EntityStoreDecodeContext,
      record: Record
    ): Consequence[Customer] =
      createC(record).map(entity =>
        entity.copy(
          id = entity.id.copy(collection = context.owningCollectionId),
          orderId = entity.orderId.copy(collection = Order.collectionId)
        )
      )

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
