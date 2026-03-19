package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class TransitionSelectorPropertySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "TransitionSelector" should {
    "deterministically pick the ordered head as property" in {
      Given("a generated non-empty candidate vector")
      val gencandidate = for {
        value <- Gen.alphaStr.suchThat(_.nonEmpty)
        priority <- Gen.chooseNum(0, 10)
        declarationorder <- Gen.chooseNum(0, 10)
      } yield TransitionCandidate(value, priority, declarationorder)
      val geninput = Gen.nonEmptyListOf(gencandidate).map(_.toVector)

      When("select is evaluated with always-true predicate")
      val property = Prop.forAll(geninput) { input =>
        val expected = TransitionSelector.ordered(input).headOption.map(_.transition)
        val result = TransitionSelector.select(input)(_ => Consequence.success(true))
        result == Consequence.success(expected)
      }

      val checked = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(100),
        property
      )

      Then("selection always matches deterministic order rule")
      checked.passed shouldBe true
    }
  }
}
