package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Mar. 19, 2026
 * @version Apr. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class TransitionSelectorSpec extends AnyWordSpec with Matchers {
  "TransitionSelector" should {
    "select by smaller priority first" in {
      val candidates = Vector(
        TransitionCandidate("t2", priority = 2, declarationOrder = 0),
        TransitionCandidate("t1", priority = 1, declarationOrder = 1)
      )

      val result = TransitionSelector.select(candidates)(_ => Consequence.success(true))

      result shouldBe Consequence.success(Some("t1"))
    }

    "select by declaration order when priority is same" in {
      val candidates = Vector(
        TransitionCandidate("later", priority = 1, declarationOrder = 2),
        TransitionCandidate("first", priority = 1, declarationOrder = 0),
        TransitionCandidate("middle", priority = 1, declarationOrder = 1)
      )

      val result = TransitionSelector.select(candidates)(_ => Consequence.success(true))

      result shouldBe Consequence.success(Some("first"))
    }

    "propagate guard failure as failure" in {
      val candidates = Vector(TransitionCandidate("t1", priority = 1, declarationOrder = 0))

      val result = TransitionSelector.select(candidates)(_ => Consequence.operationInvalid("guard evaluation failed"))

      result shouldBe a[Consequence.Failure[_]]
    }
  }
}

