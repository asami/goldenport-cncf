package org.goldenport.cncf.directive

import org.simplemodeling.model.value.NominalScalar
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class QueryNominalScalarSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "Query nominal scalar matching" should {
    "match generated scalar values through equality and membership query expressions" in {
      Given("an entity record whose status is a generated nominal scalar")
      val entity = Map[String, Any]("status" -> _TestStatus("draft"))

      When("equality and membership queries use nominal and external scalar values")
      val nominal = Query.matches(Query.Eq("status", _TestStatus("draft")), entity)
      val external = Query.matches(Query.Eq("status", "draft"), entity)
      val membership = Query.matches(Query.In("status", Vector(_TestStatus("published"), _TestStatus("draft"))), entity)

      Then("both query and entity values are compared through their scalar representation")
      nominal shouldBe true
      external shouldBe true
      membership shouldBe true
    }

    "match every generated scalar through an external equality value" in {
      Given("arbitrary generated nominal scalar values")
      val generator = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString)

      When("the same external scalar is used in an equality expression")
      val property = Prop.forAll(generator) { value =>
        Query.matches(
          Query.Eq("status", value),
          Map[String, Any]("status" -> _TestStatus(value))
        )
      }
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("the match remains true for every generated scalar value")
      result.passed shouldBe true
    }
  }
}

private final case class _TestStatus(value: String) extends NominalScalar
