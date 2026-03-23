package org.goldenport.cncf.directive

import io.circe.syntax.*
import io.circe.parser.decode
import org.simplemodeling.model.directive.Update
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 17, 2026
 * @version Mar. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class UpdateCodecSpec
  extends AnyWordSpec
  with Matchers {

  "Update codec" should {
    "round-trip Noop" in {
      val u: Update[String] = Update.noop[String]
      val json = u.asJson.noSpaces
      decode[Update[String]](json) shouldBe Right(Update.noop[String])
    }

    "round-trip SetValue" in {
      val u: Update[Int] = Update.set(42)
      val json = u.asJson.noSpaces
      decode[Update[Int]](json) shouldBe Right(Update.set(42))
    }

    "round-trip SetNull" in {
      val u: Update[String] = Update.setNull[String]
      val json = u.asJson.noSpaces
      decode[Update[String]](json) shouldBe Right(Update.setNull[String])
    }
  }
}
