package org.goldenport.cncf.entity.aggregate

import org.goldenport.Consequence
import org.goldenport.cncf.datatype.{EntityCollectionId, EntityId}
import org.goldenport.cncf.entity.runtime.testdomain.{
  Product,
  ProductAggregate,
  SalesOrder,
  SalesOrderAggregate,
  SalesOrderLine,
  User,
  UserAggregate
}

/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
trait AggregateSpaceSpecHelper {
  protected final def build_aggregate_space(): AggregateSpace = {
    val aggregatespace = new AggregateSpace
    val salesordercollection = new AggregateCollection(new SalesOrderBuilder)
    val usercollection = new AggregateCollection(new UserBuilder)
    val productcollection = new AggregateCollection(new ProductBuilder)
    aggregatespace.register("sales_order", salesordercollection)
    aggregatespace.register("user", usercollection)
    aggregatespace.register("product", productcollection)
    aggregatespace
  }

  protected final def sales_order_id(): EntityId =
    EntityId("m1", "1", EntityCollectionId("c1", "1", "sales_order"))

  protected final def user_id(): EntityId =
    EntityId("m2", "2", EntityCollectionId("c2", "2", "user"))

  protected final def product_id(): EntityId =
    EntityId("m3", "3", EntityCollectionId("c3", "3", "product"))

  protected final def missing_id(): EntityId =
    EntityId("m9", "9", EntityCollectionId("c9", "9", "missing"))

  protected final def expected_sales_order_aggregate(id: EntityId): SalesOrderAggregate = {
    val line = SalesOrderLine(id, "sku-1", 1)
    SalesOrderAggregate(SalesOrder(id, line), line)
  }

  protected final def expected_user_aggregate(id: EntityId): UserAggregate =
    UserAggregate(User(id, "user-2"))

  protected final def expected_product_aggregate(id: EntityId): ProductAggregate =
    ProductAggregate(Product(id, "product-3"))
}

final class SalesOrderBuilder extends AggregateBuilder[SalesOrderAggregate] {
  def build(id: EntityId): Consequence[SalesOrderAggregate] = {
    val line = SalesOrderLine(id, s"sku-${id.minor}", id.minor.toInt)
    Consequence.success(
      SalesOrderAggregate(
        SalesOrder(id, line),
        line
      )
    )
  }
}

final class UserBuilder extends AggregateBuilder[UserAggregate] {
  def build(id: EntityId): Consequence[UserAggregate] =
    Consequence.success(UserAggregate(User(id, s"user-${id.minor}")))
}

final class ProductBuilder extends AggregateBuilder[ProductAggregate] {
  def build(id: EntityId): Consequence[ProductAggregate] =
    Consequence.success(ProductAggregate(Product(id, s"product-${id.minor}")))
}
