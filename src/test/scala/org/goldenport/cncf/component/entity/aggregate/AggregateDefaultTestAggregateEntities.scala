/*
 * @since   Mar. 30, 2026
 *  version Mar. 30, 2026
 * @version Apr. 14, 2026
 */
package org.goldenport.cncf.component.entity.aggregate

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.entity.{EntityPersistable, EntityPersistent}
import org.goldenport.cncf.entity.aggregate.AggregateAssembler
import org.simplemodeling.model.datatype.EntityId

object Order extends AggregateAssembler[Order] {
  given EntityPersistent[Order] with
    def id(e: Order): EntityId = e.id
    def toRecord(e: Order): Record = e.toRecord()
    def fromRecord(r: Record): Consequence[Order] = createC(r)

  def createC(r: Record): Consequence[Order] =
    Consequence.success(
      Order(
        id = EntityId.parse(r.getString("id").getOrElse(sys.error("id missing"))).TAKE,
        name = r.getString("name").getOrElse(sys.error("name missing")),
        status = r.getString("status").getOrElse(sys.error("status missing")),
        customer = None,
        lines = Vector.empty
      )
    )

  def create_from_record(record: Record): Consequence[Order] =
    createC(record)

  def attach_member(
    aggregate: Order,
    member_name: String,
    members: Vector[Any]
  ): Consequence[Order] =
    member_name match {
      case "customer" =>
        Consequence.success(aggregate.withCustomer(members.collectFirst { case m: Customer => m }))
      case "lines" =>
        Consequence.success(aggregate.withLines(members.collect { case m: OrderLine => m }))
      case _ =>
        Consequence.operationInvalid(s"Unknown aggregate member: ${member_name}")
    }
}

final case class Order(
  id: EntityId,
  name: String,
  status: String,
  customer: Option[Customer],
  lines: Vector[OrderLine]
) extends EntityPersistable {
  def withCustomer(p: Option[Customer]): Order = copy(customer = p)
  def withLines(p: Vector[OrderLine]): Order = copy(lines = p)

  def toRecord(): Record =
    Record.dataAuto(
      "id" -> id.value,
      "name" -> name,
      "status" -> status,
      "customer" -> customer.map(_.toRecord()),
      "lines" -> lines.map(_.toRecord())
    )
}

object OrderLine extends AggregateAssembler[OrderLine] {
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

  def create_from_record(record: Record): Consequence[OrderLine] =
    createC(record)

  def attach_member(
    aggregate: OrderLine,
    member_name: String,
    members: Vector[Any]
  ): Consequence[OrderLine] =
    Consequence.operationInvalid(s"Unknown aggregate member: ${member_name}")
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
      "quantity" -> quantity
    )
}

object Customer extends AggregateAssembler[Customer] {
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

  def create_from_record(record: Record): Consequence[Customer] =
    createC(record)

  def attach_member(
    aggregate: Customer,
    member_name: String,
    members: Vector[Any]
  ): Consequence[Customer] =
    Consequence.operationInvalid(s"Unknown aggregate member: ${member_name}")
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
      "name" -> name
    )
}
