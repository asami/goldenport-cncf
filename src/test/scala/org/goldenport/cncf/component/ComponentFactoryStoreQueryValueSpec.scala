package org.goldenport.cncf.component

import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.simplemodeling.model.directive.Condition
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
final class ComponentFactoryStoreQueryValueSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "ComponentFactory datastore query values" should {
    "normalize generated scalar and entity identity values before datastore search" in {
      Given("a query record containing generated scalar, entity id, option, and membership conditions")
      val factory = new ComponentFactory()
      val id = EntityId("test", "article_1", EntityCollectionId("test", "a", "article"))
      val source = Record.create(Vector[(String, Any)](
        "status" -> Condition.Is(_TestStatus("draft")),
        "statuses" -> Condition.In(Set(_TestStatus("draft"), _TestStatus("published"))),
        "owner" -> id,
        "optionalOwner" -> Some(id)
      ))

      When("ComponentFactory prepares the query for a datastore")
      val normalized = factory._searchable_query_record(source)

      Then("typed component values become datastore scalar values without changing query structure")
      normalized.getAny("status") shouldBe Some("draft")
      normalized.getAny("statuses").collect { case xs: Vector[?] => xs.toSet } shouldBe Some(Set("draft", "published"))
      normalized.getAny("owner") shouldBe Some(id.print)
      normalized.getAny("optionalOwner") shouldBe Some(id.print)
    }

    "normalize every generated nominal scalar value supplied through an equality condition" in {
      Given("arbitrary generated scalar values")
      val factory = new ComponentFactory()
      val generator = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString)

      When("ComponentFactory prepares equality conditions for datastore search")
      val property = Prop.forAll(generator) { value =>
        val source = Record.create(Vector[(String, Any)](
          "status" -> Condition.Is(_TestStatus(value))
        ))
        val normalized = factory._searchable_query_record(source)
        normalized.getAny("status").contains(value)
      }
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("every nominal value is passed to the datastore as its exact scalar value")
      result.passed shouldBe true
    }
  }
}

private final case class _TestStatus(value: String) extends NominalScalar
