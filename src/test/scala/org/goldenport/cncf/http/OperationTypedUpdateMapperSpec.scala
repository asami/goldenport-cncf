package org.goldenport.cncf.http

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import org.goldenport.cncf.operation.{CmlOperationDefinition, CmlOperationField, CmlOperationUpdateField}
import org.simplemodeling.model.directive.Update

/*
 * @since   Jul. 19, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationTypedUpdateMapperSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  import OperationUpdateDirective.*
  import OperationUpdateDirective.OperandlessCommand.Kind
  import OperationTypedUpdateDirective.*

  "The typed update mapper" should {
    "map collection clear and nullable scalar null to generated Update values" in {
      Given("an entity update operation retaining source field semantics")
      val operation = _operation
      val directives = OperationUpdateDirectiveSet(
        Map(
          "tags" -> OperandlessCommand("tags", Kind.Clear),
          "nickname" -> OperandlessCommand("nickname", Kind.Null)
        )
      )

      When("the normalized directives are mapped")
      val result = OperationTypedUpdateMapper.map(operation, directives).toOption.get

      Then("clear is an empty collection assignment and null is an explicit null assignment")
      val clear = result.directives("tags").asInstanceOf[TypedAssignment]
      val setnull = result.directives("nickname").asInstanceOf[TypedAssignment]
      clear.elementDatatype shouldBe "Tag"
      clear.update shouldBe Update.set(Vector.empty[Any])
      setnull.elementDatatype shouldBe "string"
      setnull.update shouldBe Update.setNull[Any]
    }

    "reject incompatible commands before generated ActionCall construction" in {
      Given("clear on a scalar and null on a non-nullable collection")
      val clear = OperationUpdateDirectiveSet(
        Map("nickname" -> OperandlessCommand("nickname", Kind.Clear))
      )
      val setnull = OperationUpdateDirectiveSet(
        Map("tags" -> OperandlessCommand("tags", Kind.Null))
      )

      When("the directives are mapped")
      val clearresult = OperationTypedUpdateMapper.map(_operation, clear)
      val nullresult = OperationTypedUpdateMapper.map(_operation, setnull)

      Then("both fail as structured argument policy violations")
      clearresult.isFaillure shouldBe true
      nullresult.isFaillure shouldBe true
    }

    "reject unknown parameters and missing update semantics instead of inferring them" in {
      Given("an unknown parameter and a legacy field without source update metadata")
      val unknown = OperationUpdateDirectiveSet(
        Map("missing" -> PlainAssignment("missing", Vector("value")))
      )
      val legacy = OperationUpdateDirectiveSet(
        Map("legacy" -> OperandlessCommand("legacy", Kind.Null))
      )
      val operation = _operation.copy(
        parameters = _operation.parameters :+ CmlOperationField("legacy", "string", "?")
      )

      When("the directives are mapped")
      val unknownresult = OperationTypedUpdateMapper.map(operation, unknown)
      val legacyresult = OperationTypedUpdateMapper.map(operation, legacy)

      Then("the boundary reports deterministic failures")
      unknownresult.isFaillure shouldBe true
      legacyresult.isFaillure shouldBe true
    }

    "accept operand-less commands exactly when source field semantics permit them" in {
      Given("generated source multiplicity and null-assignment metadata")

      When("clear and null compatibility are checked across metadata combinations")
      val combinations = for {
        collectionvalued <- Gen.oneOf(true, false)
        nullallowed <- Gen.oneOf(true, false)
      } yield collectionvalued -> nullallowed
      val property = Prop.forAll(combinations) { case (collectionvalued, nullallowed) =>
        val sourcemultiplicity = if (collectionvalued) "*" else "?"
        val operation = _operation.copy(
          parameters = Vector(
            CmlOperationField(
              name = "value",
              datatype = "string",
              multiplicity = sourcemultiplicity,
              update = Some(CmlOperationUpdateField(sourcemultiplicity, nullallowed))
            )
          )
        )
        val clear = OperationUpdateDirectiveSet(
          Map("value" -> OperandlessCommand("value", Kind.Clear))
        )
        val setnull = OperationUpdateDirectiveSet(
          Map("value" -> OperandlessCommand("value", Kind.Null))
        )

        (OperationTypedUpdateMapper.map(operation, clear).isSuccess == collectionvalued) &&
          (OperationTypedUpdateMapper.map(operation, setnull).isSuccess ==
            (nullallowed && !collectionvalued))
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("clear follows collection-valued semantics and null requires a nullable scalar")
      checked.passed shouldBe true
    }
  }

  private def _operation: CmlOperationDefinition =
    CmlOperationDefinition(
      name = "update",
      kind = "COMMAND",
      inputType = "Example",
      outputType = "unit",
      inputValueKind = "ENTITY_UPDATE",
      parameters = Vector(
        CmlOperationField(
          name = "tags",
          datatype = "Tag",
          multiplicity = "*",
          update = Some(CmlOperationUpdateField("*", nullAllowed = false))
        ),
        CmlOperationField(
          name = "nickname",
          datatype = "string",
          multiplicity = "?",
          update = Some(CmlOperationUpdateField("?", nullAllowed = true))
        )
      )
    )
}
