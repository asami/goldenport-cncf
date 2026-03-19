package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.provisional.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 20, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class BindingResolutionFailureSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Binding resolver failures" should {
    "return failure for missing action binding with taxonomy/facets" in {
      Given("an action binding resolver with no registered action")
      val resolver = new _ActionResolver(Map.empty)

      When("resolving an unknown action binding name")
      val result = resolver.resolve("missingAction")

      Then("resolution fails with not-found taxonomy and operation facet")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(c) =>
          c.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          c.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.NotFound
          _has_operation_facet(c, "missingAction") shouldBe true
        case _ =>
          fail("expected failure")
      }
    }

    "return failure for ambiguous action binding with taxonomy/facets" in {
      Given("an action binding resolver with duplicated action names")
      val effect = _noop_effect
      val resolver = new _ActionResolver(
        Map("dupAction" -> Vector(effect, effect))
      )

      When("resolving an ambiguous action binding name")
      val result = resolver.resolve("dupAction")

      Then("resolution fails with conflict taxonomy and operation facet")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(c) =>
          c.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          c.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Conflict
          _has_operation_facet(c, "dupAction") shouldBe true
          _has_value_facet(c, "2") shouldBe true
        case _ =>
          fail("expected failure")
      }
    }

    "return failure for missing guard binding with taxonomy/facets" in {
      Given("a guard binding resolver with no registered guard")
      val resolver = new _GuardResolver[Int, Int](Map.empty)

      When("resolving an unknown guard binding name")
      val result = resolver.resolve("missingGuard")

      Then("resolution fails with not-found taxonomy and operation facet")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(c) =>
          c.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          c.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.NotFound
          _has_operation_facet(c, "missingGuard") shouldBe true
        case _ =>
          fail("expected failure")
      }
    }

    "return failure for ambiguous guard binding with taxonomy/facets" in {
      Given("a guard binding resolver with duplicated guard names")
      val guard = new Guard[Int, Int] {
        def eval(state: Int, event: Int): Consequence[Boolean] = {
          val _ = (state, event)
          Consequence.success(true)
        }
      }
      val resolver = new _GuardResolver[Int, Int](
        Map("dupGuard" -> Vector(guard, guard))
      )

      When("resolving an ambiguous guard binding name")
      val result = resolver.resolve("dupGuard")

      Then("resolution fails with conflict taxonomy and operation facet")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(c) =>
          c.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          c.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Conflict
          _has_operation_facet(c, "dupGuard") shouldBe true
          _has_value_facet(c, "2") shouldBe true
        case _ =>
          fail("expected failure")
      }
    }
  }

  private val _noop_effect: ActionEffect[Int, TransitionEvent] =
    new ActionEffect[Int, TransitionEvent] {
      def execute(state: Int, event: TransitionEvent): Consequence[Unit] = {
        val _ = (state, event)
        Consequence.unit
      }
    }

  private final class _ActionResolver(
    values: Map[String, Vector[ActionEffect[Int, TransitionEvent]]]
  ) extends ActionBindingResolver[Int, TransitionEvent] {
    def resolve(name: String): Consequence[ActionEffect[Int, TransitionEvent]] =
      values.get(name).map(_.toVector).getOrElse(Vector.empty) match {
        case Vector(single) => Consequence.success(single)
        case Vector() =>
          Consequence.fail(
            Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.NotFound),
            Facet.Operation(name),
            Facet.Message("action-binding-not-found")
          )
        case many =>
          Consequence.fail(
            Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Conflict),
            Facet.Operation(name),
            Facet.Value(many.size),
            Facet.Message("action-binding-ambiguous")
          )
      }
  }

  private final class _GuardResolver[S, E](
    values: Map[String, Vector[Guard[S, E]]]
  ) extends GuardBindingResolver[S, E] {
    def resolve(name: String): Consequence[Guard[S, E]] =
      values.get(name).map(_.toVector).getOrElse(Vector.empty) match {
        case Vector(single) => Consequence.success(single)
        case Vector() =>
          Consequence.fail(
            Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.NotFound),
            Facet.Operation(name),
            Facet.Message("guard-binding-not-found")
          )
        case many =>
          Consequence.fail(
            Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Conflict),
            Facet.Operation(name),
            Facet.Value(many.size),
            Facet.Message("guard-binding-ambiguous")
          )
      }
  }

  private def _has_operation_facet(c: Conclusion, expected: String): Boolean =
    c.observation.cause.descriptor.facets.exists {
      case Facet.Operation(name) => name == expected
      case _ => false
    }

  private def _has_value_facet(c: Conclusion, expected: String): Boolean =
    c.observation.cause.descriptor.facets.exists {
      case Facet.Value(value) => value.toString == expected
      case _ => false
    }
}
