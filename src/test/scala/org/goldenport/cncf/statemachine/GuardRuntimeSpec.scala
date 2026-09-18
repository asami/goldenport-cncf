package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 19, 2026
 *  version Apr. 14, 2026
 * @version Sep. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class GuardRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "RefGuard" should {
    "resolve and evaluate named guard" in {
      val resolver = new GuardBindingResolver[Int, Int] {
        def resolve(name: String): Consequence[Guard[Int, Int]] =
          Consequence.success(new Guard[Int, Int] {
            def eval(state: Int, event: Int): Consequence[Boolean] =
              Consequence.success(name == "ok" && state + event > 0)
          })
      }
      val guard = RefGuard[Int, Int]("ok", resolver)

      guard.eval(1, 1) shouldBe Consequence.success(true)
    }

    "propagate resolver failure" in {
      val resolver = new GuardBindingResolver[Int, Int] {
        def resolve(name: String): Consequence[Guard[Int, Int]] =
          Consequence.operationNotFound(s"guard not found: $name")
      }
      val guard = RefGuard[Int, Int]("missing", resolver)

      guard.eval(1, 1) shouldBe a[Consequence.Failure[_]]
    }
  }

  "GuardRuntime.build" should {
    "build RefGuard for ref expression" in {
      val resolver = new GuardBindingResolver[Int, Int] {
        def resolve(name: String): Consequence[Guard[Int, Int]] =
          Consequence.success(new Guard[Int, Int] {
            def eval(state: Int, event: Int): Consequence[Boolean] =
              Consequence.success(true)
          })
      }
      val guard = GuardRuntime.build[Int, Int](
        GuardExpr.Ref("always"),
        resolver,
        (_, _) => Map.empty
      )

      guard shouldBe a[RefGuard[?, ?]]
    }

    "build ExpressionGuard for expression" in {
      val resolver = new GuardBindingResolver[Int, Int] {
        def resolve(name: String): Consequence[Guard[Int, Int]] =
          Consequence.operationInvalid(s"unexpected: $name")
      }
      val guard = GuardRuntime.build[Int, Int](
        GuardExpr.Expression("state > 0"),
        resolver,
        (s, e) => Map("state" -> s, "event" -> e)
      )

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
