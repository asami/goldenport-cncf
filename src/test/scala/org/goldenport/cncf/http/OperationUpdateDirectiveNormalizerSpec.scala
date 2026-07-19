package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 19, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationUpdateDirectiveNormalizerSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  import OperationUpdateDirective.*
  import OperationUpdateDirectiveNormalizer.FieldOccurrence

  "Operation update directive normalization" should {
    "retain all plain and value-bearing field occurrences" in {
      Given("plain and append occurrences with repeated values")
      val plain = Record.create(Vector("tags" -> "red", "tags" -> "blue"))
      val append = Record.create(Vector("tags__append" -> "red", "tags__append" -> "blue"))

      When("each transport-neutral record is normalized")
      val plainresult = OperationUpdateDirectiveNormalizer.normalize(plain)
      val appendresult = OperationUpdateDirectiveNormalizer.normalize(append)

      Then("the directive model retains the occurrence vectors")
      plainresult shouldBe Consequence.success(OperationUpdateDirectiveSet(Map(
        "tags" -> PlainAssignment("tags", Vector("red", "blue"))
      )))
      appendresult shouldBe Consequence.success(OperationUpdateDirectiveSet(Map(
        "tags" -> ValueOperation("tags", ValueOperation.Kind.Append, Vector("red", "blue"))
      )))
    }

    "convert clear and null command carriers to operand-less directives" in {
      Given("the two canonical command values")
      val clear = Record.dataAuto("tags__update_command" -> "clear")
      val nullvalue = Record.dataAuto("memo__update_command" -> "NULL")

      When("the command carriers are normalized")
      val clearresult = OperationUpdateDirectiveNormalizer.normalize(clear)
      val nullresult = OperationUpdateDirectiveNormalizer.normalize(nullvalue)

      Then("the values select typed command kinds without a dummy operand")
      clearresult shouldBe Consequence.success(OperationUpdateDirectiveSet(Map(
        "tags" -> OperandlessCommand("tags", OperandlessCommand.Kind.Clear)
      )))
      nullresult shouldBe Consequence.success(OperationUpdateDirectiveSet(Map(
        "memo" -> OperandlessCommand("memo", OperandlessCommand.Kind.Null)
      )))
    }

    "represent an absent parameter explicitly as no directive" in {
      Given("an empty occurrence set")
      val normalized = OperationUpdateDirectiveNormalizer.normalize(Vector.empty)

      When("an operation parameter is queried")
      val directive = normalized.toOption.map(_.directive("memo"))

      Then("the result distinguishes omission from an explicit null command")
      directive shouldBe Some(NoDirective("memo"))
    }

    "reject conflicting directives independently of occurrence order" in {
      Given("command, plain-assignment, and value-operation conflict families")
      val conflicts = Vector(
        Vector(
          FieldOccurrence("tags__update_command", "clear"),
          FieldOccurrence("tags__append", "blue")
        ),
        Vector(
          FieldOccurrence("tags__update_command", "clear"),
          FieldOccurrence("tags", "blue")
        ),
        Vector(
          FieldOccurrence("tags__append", "blue"),
          FieldOccurrence("tags__remove", "red")
        ),
        Vector(
          FieldOccurrence("tags", "blue"),
          FieldOccurrence("tags__overwrite", "red")
        )
      )

      When("generated orderings are normalized repeatedly")
      val inputs = Gen.oneOf(conflicts).flatMap(conflict => Gen.pick(conflict.size, conflict))
      val property = Prop.forAll(inputs) { shuffled =>
        OperationUpdateDirectiveNormalizer.normalize(shuffled.toVector) match {
          case Consequence.Failure(conclusion) =>
            val facets = conclusion.observation.cause.descriptor.facets
            facets.contains(Descriptor.Facet.Parameter.argument("tags")) &&
              facets.contains(Descriptor.Facet.Policy("operation-update-directive"))
          case Consequence.Success(_) => false
        }
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("every ordering produces the same structured policy failure")
      checked.passed shouldBe true
    }

    "reject duplicate and unknown command values structurally" in {
      Given("duplicate and unknown command carriers")
      val duplicate = Record.create(Vector(
        "tags__update_command" -> "clear",
        "tags__update_command" -> "clear"
      ))
      val unknown = Record.dataAuto("tags__update_command" -> "reset")

      When("the invalid requests are normalized")
      val duplicateresult = OperationUpdateDirectiveNormalizer.normalize(duplicate)
      val unknownresult = OperationUpdateDirectiveNormalizer.normalize(unknown)

      Then("both failures use the common structured validation model")
      _failure_facets(duplicateresult) should contain (Descriptor.Facet.Policy("operation-update-directive"))
      _failure_facets(unknownresult) should contain allOf (
        Descriptor.Facet.Parameter.argument("tags"),
        Descriptor.Facet.Expected("clear or null"),
        Descriptor.Facet.Actual("reset")
      )
    }

    "reject a plain assignment combined with a value operation" in {
      Given("one plain value and one overwrite value")
      val request = Record.create(Vector(
        "memo" -> "before",
        "memo__overwrite" -> "after"
      ))

      When("the request is normalized")
      val result = OperationUpdateDirectiveNormalizer.normalize(request)

      Then("the normalizer rejects selecting a winner by request order")
      result shouldBe a[Consequence.Failure[?]]
    }
  }

  private def _failure_facets[A](value: Consequence[A]): Vector[Descriptor.Facet] =
    value match {
      case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets
      case Consequence.Success(_) => fail("expected structured failure")
    }
}
