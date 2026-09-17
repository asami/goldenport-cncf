package org.goldenport.cncf.entity.aggregate

import org.goldenport.Consequence
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
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
 *  version Apr. 10, 2026
 * @version Sep. 17, 2026
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
    org.goldenport.cncf.EntityIdFixtureBridge.fromParts("m1", "a", EntityCollectionId("c1", "a", "sales_order"), entropy = "a")

  protected final def user_id(): EntityId =
    org.goldenport.cncf.EntityIdFixtureBridge.fromParts("m2", "b", EntityCollectionId("c2", "b", "user"), entropy = "b")

  protected final def product_id(): EntityId =
    org.goldenport.cncf.EntityIdFixtureBridge.fromParts("m3", "c", EntityCollectionId("c3", "c", "product"), entropy = "c")

  protected final def missing_id(): EntityId =
    org.goldenport.cncf.EntityIdFixtureBridge.fromParts("m9", "z", EntityCollectionId("c9", "z", "missing"), entropy = "z")

  protected final def expected_sales_order_aggregate(id: EntityId): SalesOrderAggregate = {
    val quantity = id.minor.headOption.map(_.toInt).getOrElse(1) - 'a'.toInt + 1
    val line = SalesOrderLine(id, s"sku-${id.minor}", quantity)
    SalesOrderAggregate(SalesOrder(id, line), line)
  }

  protected final def expected_user_aggregate(id: EntityId): UserAggregate =
    UserAggregate(User(id, s"user-${id.minor}"))

  protected final def expected_product_aggregate(id: EntityId): ProductAggregate =
    ProductAggregate(Product(id, s"product-${id.minor}"))
}

final class SalesOrderBuilder extends AggregateBuilder[SalesOrderAggregate] {
  def build(id: EntityId): Consequence[SalesOrderAggregate] = {
    val quantity = id.minor.headOption.map(_.toInt).getOrElse(1) - 'a'.toInt + 1
    val line = SalesOrderLine(id, s"sku-${id.minor}", quantity)
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
