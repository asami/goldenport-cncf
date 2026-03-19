package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class EffectAdapterSpec extends AnyWordSpec with Matchers {
  "EffectAdapter" should {
    "adapt core effect into runtime resolved action" in {
      val trace = scala.collection.mutable.ArrayBuffer.empty[String]
      val effect = new org.goldenport.statemachine.Effect[String, String] {
        def execute(state: String, event: String): Consequence[Unit] = {
          trace += s"$state:$event"
          Consequence.unit
        }
      }
      val adapter = EffectAdapter(effect)

      adapter.run("s1", "e1") shouldBe Consequence.unit
      trace.toVector shouldBe Vector("s1:e1")
    }
  }
}
