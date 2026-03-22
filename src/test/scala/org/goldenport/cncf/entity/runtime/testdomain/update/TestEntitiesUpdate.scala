package org.goldenport.cncf.entity.runtime.testdomain.update

import org.goldenport.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.EntityPersistable
import org.goldenport.record.Record
/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SalesOrderLine(
  id: EntityId,
  sku: Option[String],
  quantity: Option[Int]
) extends EntityPersistable {
  import SalesOrderLine.*

  def toRecord(): Record =
    Record.dataAuto(
      PROP_ID -> id,
      PROP_SKU -> sku,
      PROP_QUANTITY -> quantity
    )
}

object SalesOrderLine {
  final val PROP_ID = "id"
  final val PROP_SKU = "sku"
  final val PROP_QUANTITY = "quantity"

  val collectionId: EntityCollectionId =
    EntityCollectionId("test", "1", "sales_order_line")
}

final case class SalesOrder(
  id: EntityId,
  line: Option[SalesOrderLine]
) extends EntityPersistable {
  import SalesOrder.*

  def toRecord(): Record =
    Record.dataAuto(
      PROP_ID -> id,
      PROP_LINE -> line
    )
}

object SalesOrder {
  final val PROP_ID = "id"
  final val PROP_LINE = "line"

  val collectionId: EntityCollectionId =
    EntityCollectionId("test", "1", "sales_order")
}

final case class User(
  id: EntityId,
  name: Option[String]
) extends EntityPersistable {
  import User.*

  def toRecord(): Record =
    Record.dataAuto(
      PROP_ID -> id,
      PROP_NAME -> name
    )
}

object User {
  final val PROP_ID = "id"
  final val PROP_NAME = "name"

  val collectionId: EntityCollectionId =
    EntityCollectionId("test", "1", "user")
}

final case class Product(
  id: EntityId,
  name: Option[String]
) extends EntityPersistable {
  import Product.*

  def toRecord(): Record =
    Record.dataAuto(
      PROP_ID -> id,
      PROP_NAME -> name
    )
}

object Product {
  final val PROP_ID = "id"
  final val PROP_NAME = "name"

  val collectionId: EntityCollectionId =
    EntityCollectionId("test", "1", "product")
}
