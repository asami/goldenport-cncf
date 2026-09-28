package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Apr. 14, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class GuardRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "RefGuard" should {
    "resolve and evaluate named guard" in {
      Given("a named resolver whose guard accepts positive state and event values")
      val resolver = new GuardBindingResolver[Int, Int] {
        def resolve(name: String): Consequence[Guard[Int, Int]] =
          Consequence.success(new Guard[Int, Int] {
            def eval(state: Int, event: Int): Consequence[Boolean] =
              Consequence.success(name == "ok" && state + event > 0)
          })
      }
      When("the referenced guard is constructed and evaluated")
      val guard = RefGuard[Int, Int]("ok", resolver)

      val result = guard.eval(1, 1)

      Then("the named guard accepts the values")
      result shouldBe Consequence.success(true)
    }

    "propagate resolver failure" in {
      Given("a resolver that refuses an unknown guard name")
      val resolver = new GuardBindingResolver[Int, Int] {
        def resolve(name: String): Consequence[Guard[Int, Int]] =
          Consequence.operationNotFound(s"guard not found: $name")
      }
      When("the referenced guard is constructed and evaluated")
      val guard = RefGuard[Int, Int]("missing", resolver)

      val result = guard.eval(1, 1)

      Then("the resolver failure is preserved")
      result shouldBe a[Consequence.Failure[_]]
    }
  }

  "GuardRuntime.build" should {
    "build RefGuard for ref expression" in {
      Given("a resolver for a referenced guard expression")
      val resolver = new GuardBindingResolver[Int, Int] {
        def resolve(name: String): Consequence[Guard[Int, Int]] =
          Consequence.success(new Guard[Int, Int] {
            def eval(state: Int, event: Int): Consequence[Boolean] =
              Consequence.success(true)
          })
      }
      When("the legacy build path constructs the guard")
      val guard = GuardRuntime.build[Int, Int](
        GuardExpr.Ref("always"),
        resolver,
        (_, _) => Map.empty
      )

      Then("the result retains the reference guard")
      guard shouldBe a[RefGuard[?, ?]]
    }

    "build ExpressionGuard for expression" in {
      Given("a legacy raw expression and its value projection")
      val resolver = new GuardBindingResolver[Int, Int] {
        def resolve(name: String): Consequence[Guard[Int, Int]] =
          Consequence.operationInvalid(s"unexpected: $name")
      }
      When("the legacy build path constructs the guard")
      val guard = GuardRuntime.build[Int, Int](
        GuardExpr.Expression("state > 0"),
        resolver,
        (s, e) => Map("state" -> s, "event" -> e)
      )

      Then("the result retains the expression guard")
      guard shouldBe a[ExpressionGuard[?, ?]]
    }
  }

  "GuardRuntime.buildNormalized" should {
    "admit a named guard binding" in {
      Given("a named guard binding")
      val resolver = new GuardBindingResolver[Int, Int] {
        def resolve(name: String): Consequence[Guard[Int, Int]] =
          Consequence.success(new Guard[Int, Int] {
            def eval(state: Int, event: Int): Consequence[Boolean] =
              Consequence.success(name == "named" && state == event)
          })
      }

      When("the normalized path builds and evaluates it")
      val result = GuardRuntime.buildNormalized[Int, Int](
        GuardExpr.Ref("named"),
        resolver
      ).flatMap(_.eval(2, 2))

      Then("the binding provides the guard behavior")
      result shouldBe Consequence.success(true)
    }

    "reject a legacy raw expression" in {
      Given("a raw expression retained for legacy compatibility")
      val resolver = new GuardBindingResolver[Int, Int] {
        def resolve(name: String): Consequence[Guard[Int, Int]] =
          Consequence.operationInvalid(s"unexpected: $name")
      }

      When("the normalized path is asked to build it")
      val result = GuardRuntime.buildNormalized[Int, Int](
        GuardExpr.Expression("state > 0"),
        resolver
      )

      Then("normalization fails closed")
      result shouldBe a[Consequence.Failure[_]]
    }
  }
}
