package org.goldenport.cncf.datatype

import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 17, 2026
 * @version Mar. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class
  EntityIdParseSpec
  extends AnyWordSpec
  with Matchers {

  "EntityId.parse" should {
    "parse canonical entity id prefix" in {
      val s = "tokyo-sales-entity-person-1742198400000-abcd1234"
      val r = EntityId.parse(s)
      r match {
        case Consequence.Success(id) =>
          id.major shouldBe "tokyo"
          id.minor shouldBe "sales"
          id.collection.major shouldBe "tokyo"
          id.collection.minor shouldBe "sales"
          id.collection.name shouldBe "person"
        case m =>
          fail(s"Expected success but got: $m")
      }
      r.map(_.print) shouldBe Consequence.success(s)
    }

    "reject non-entity kind" in {
      val s = "tokyo-sales-aggregate-person-1742198400000-abcd1234"
      EntityId.parse(s) match {
        case Consequence.Failure(_) => succeed
        case _ => fail("Expected failure for non-entity kind")
      }
    }
  }
}
