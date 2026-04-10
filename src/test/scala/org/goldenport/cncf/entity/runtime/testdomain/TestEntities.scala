package org.goldenport.cncf.entity.runtime.testdomain

import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.EntityPersistable
import org.goldenport.record.Record

/*
 * @since   Mar. 19, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final case class SalesOrderLine(
  id: EntityId,
  sku: String,
  quantity: Int
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
    EntityCollectionId("test", "a", "sales_order_line")
}

final case class SalesOrder(
  id: EntityId,
  line: SalesOrderLine
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
    EntityCollectionId("test", "a", "sales_order")
}

final case class User(
  id: EntityId,
  name: String
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
    EntityCollectionId("test", "a", "user")
}

final case class Product(
  id: EntityId,
  name: String
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
    EntityCollectionId("test", "a", "product")
}

final case class SalesOrderAggregate(
  order: SalesOrder,
  line: SalesOrderLine
)

final case class SalesOrderView(
  order: SalesOrder,
  line: SalesOrderLine,
  user: User,
  product: Product
)

final case class UserAggregate(
  user: User
)

final case class ProductAggregate(
  product: Product
)
