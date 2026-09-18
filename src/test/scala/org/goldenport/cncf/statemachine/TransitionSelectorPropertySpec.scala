package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Sep. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class TransitionSelectorPropertySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "TransitionSelector" should {
    "reject duplicate canonical candidate ordering" in {
      Given("two candidates with one canonical ordering key")
      val candidates = Vector(
        TransitionCandidate("first", priority = 1, declarationOrder = 2),
        TransitionCandidate("second", priority = 1, declarationOrder = 2)
      )

      When("canonical order is requested")
      val result = TransitionSelector.orderedCanonical(candidates)

      Then("normalization fails closed")
      result shouldBe a[Consequence.Failure[_]]
    }

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

    "deterministically pick the canonical ordered head as property" in {
      Given("a generated non-empty candidate vector with unique canonical keys")
      val geninput = Gen.nonEmptyListOf(Gen.alphaStr.suchThat(_.nonEmpty)).map {
        values =>
          values.zipWithIndex.map { case (value, index) =>
            TransitionCandidate(value, priority = index % 11, declarationOrder = index)
          }.toVector
      }

      When("canonical selection is evaluated with an always-true predicate")
      val property = Prop.forAll(geninput) { input =>
        val expected = TransitionSelector.ordered(input).headOption.map(_.transition)
        val result = TransitionSelector.selectCanonical(input)(_ => Consequence.success(true))
        result == Consequence.success(expected)
      }

      val checked = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(100),
        property
      )

      Then("selection always preserves canonical transition order")
      checked.passed shouldBe true
    }

    "return no canonical selection when every guard is false" in {
      Given("a non-empty vector of valid canonical candidates")
      val candidates = Vector(
        TransitionCandidate("first", priority = 1, declarationOrder = 0),
        TransitionCandidate("second", priority = 2, declarationOrder = 1)
      )

      When("canonical selection evaluates an always-false predicate")
      val result = TransitionSelector.selectCanonical(candidates) { _ =>
        Consequence.success(false)
      }

      Then("selection returns exactly a successful empty result")
      result shouldBe Consequence.success(None)
    }

    "reject negative canonical priority and declaration order" in {
      Given("candidates with negative canonical priority and declaration order")
      val negativepriority = TransitionCandidate("negative-priority", priority = -1, declarationOrder = 0)
      val negativedeclarationorder = TransitionCandidate("negative-declaration-order", priority = 0, declarationOrder = -1)

      When("canonical ordering is requested for each invalid candidate")
      val priorityresult = TransitionSelector.orderedCanonical(Vector(negativepriority))
      val declarationorderresult = TransitionSelector.orderedCanonical(Vector(negativedeclarationorder))

      Then("each candidate is rejected by the canonical admission path")
      priorityresult shouldBe a[Consequence.Failure[_]]
      declarationorderresult shouldBe a[Consequence.Failure[_]]
    }

    "continue through a false canonical guard using the core selector" in {
      Given("an earlier false candidate and a later true candidate")
      val candidates = Vector(
        TransitionCandidate("first", priority = 1, declarationOrder = 0),
        TransitionCandidate("second", priority = 1, declarationOrder = 1)
      )

      When("canonical selection delegates the predicate to the core transition guard")
      val result = TransitionSelector.selectCanonical(candidates) { candidate =>
        Consequence.success(candidate == "second")
      }

      Then("the core false-guard continuation selects the next candidate")
      result shouldBe Consequence.success(Some("second"))
    }

    "preserve a canonical guard evaluation failure from the core selector" in {
      Given("an earlier candidate whose predicate cannot be evaluated")
      val candidates = Vector(
        TransitionCandidate("first", priority = 1, declarationOrder = 0),
        TransitionCandidate("second", priority = 1, declarationOrder = 1)
      )

      When("canonical selection delegates the failure to the core transition guard")
      val result = TransitionSelector.selectCanonical(candidates) { candidate =>
        if (candidate == "first")
          Consequence.operationInvalid("guard evaluation error")
        else
          Consequence.success(true)
      }

      Then("the failure is terminal and no later candidate is selected")
      result shouldBe a[Consequence.Failure[_]]
    }
  }
}
