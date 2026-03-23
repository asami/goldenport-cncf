package org.goldenport.cncf.directive

import io.circe.syntax.*
import io.circe.parser.decode
import org.simplemodeling.model.directive.Condition
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 17, 2026
 * @version Mar. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class ConditionCodecSpec
  extends AnyWordSpec
  with Matchers {

  "Condition codec" should {
    "round-trip Any" in {
      val c: Condition[String] = Condition.any[String]
      val json = c.asJson.noSpaces
      decode[Condition[String]](json) shouldBe Right(Condition.any[String])
    }

    "round-trip Is" in {
      val c: Condition[Int] = Condition.is(42)
      val json = c.asJson.noSpaces
      decode[Condition[Int]](json) shouldBe Right(Condition.is(42))
    }

    "round-trip In" in {
      val c: Condition[String] = Condition.in(Set("a", "b"))
      val json = c.asJson.noSpaces
      decode[Condition[String]](json) shouldBe Right(Condition.in(Set("a", "b")))
    }

    "reject Predicate on encode" in {
      val c: Condition[Int] = Condition.predicate(_ > 0)
      an[IllegalArgumentException] should be thrownBy c.asJson
    }

    "reject Predicate on decode" in {
      val json = """{"op":"predicate"}"""
      decode[Condition[Int]](json).isLeft shouldBe true
    }
  }
}
