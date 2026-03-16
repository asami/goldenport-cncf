package org.goldenport.cncf.entity.aggregate

import org.goldenport.Consequence
import org.goldenport.cncf.directive.Query
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 16, 2026
 * @version Mar. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class AggregateSpaceResolveSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with TableDrivenPropertyChecks
  with AggregateSpaceSpecHelper {

  "AggregateSpace.resolve" should {
    "route by entity id collection name" in {
      Given("aggregate collections for sales order, user, and product")
      val aggregatespace = build_aggregate_space()
      val salesorderid = sales_order_id()
      val userid = user_id()
      val productid = product_id()

      val table = Table(
        ("entityid", "expected"),
        (salesorderid, expected_sales_order_aggregate(salesorderid)),
        (userid, expected_user_aggregate(userid)),
        (productid, expected_product_aggregate(productid))
      )

      forAll(table) { (entityid, expected) =>
        When("resolving with the entity id")
        val result = aggregatespace.resolve[Any](entityid)

        Then("the matching aggregate collection is used")
        result shouldBe Consequence.success(expected)
      }
    }
  }

  "AggregateSpace" should {
    "reject duplicate registration" in {
      Given("an aggregate space with a registered collection")
      val aggregatespace = new AggregateSpace
      val collection = new AggregateCollection(new UserBuilder)
      aggregatespace.register("user", collection)

      When("registering another collection with the same name")
      Then("an error is raised")
      intercept[IllegalStateException] {
        aggregatespace.register("user", collection)
      }
    }

    "fail on resolve when collection is missing" in {
      Given("an aggregate space without the target collection")
      val aggregatespace = new AggregateSpace
      val missingid = missing_id()

      When("resolving with an unknown collection name")
      Then("an error is raised")
      intercept[IllegalStateException] {
        aggregatespace.resolve[Any](missingid)
      }
    }

    "support query when collection provides query function" in {
      Given("an aggregate collection with query support")
      val aggregatespace = new AggregateSpace
      val expected = expected_user_aggregate(user_id())
      val collection = new AggregateCollection(
        new UserBuilder,
        _ => Consequence.success(Vector(expected))
      )
      aggregatespace.register("user", collection)

      When("querying by collection name")
      val result = aggregatespace.query[Any]("user", Query("any"))

      Then("the query result is returned")
      result shouldBe Consequence.success(Vector(expected))
    }
  }
}
