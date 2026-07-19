package org.goldenport.cncf.http

import org.goldenport.Consequence
import org.goldenport.cncf.operation.{CmlOperationDefinition, CmlOperationField, CmlOperationUpdateField}
import org.goldenport.protocol.{Argument, Property, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.directive.Update

/*
 * @since   Jul. 19, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationUpdateRequestNormalizerSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Operation update request normalization" should {
    "materialize equivalent Form properties and REST arguments" in {
      Given("property and argument requests carrying collection clear")
      val form = _request(properties = List(Property("tags__update_command", "clear", None)))
      val rest = _request(arguments = List(Argument("tags__update_command", "clear")))

      When("both requests pass through the shared operation boundary")
      val normalizedform = OperationUpdateRequestNormalizer.normalize(_operation, form).toOption.get
      val normalizedrest = OperationUpdateRequestNormalizer.normalize(_operation, rest).toOption.get

      Then("both expose the same empty typed collection value without the public carrier")
      normalizedform.properties.map(x => x.name -> x.value) shouldBe List("tags" -> Vector.empty)
      normalizedrest.arguments.map(x => x.name -> x.value) shouldBe List("tags" -> Vector.empty)
    }

    "preserve explicit null as the generated Update marker" in {
      Given("a nullable scalar null command")
      val request = _request(properties = List(Property("nickname__update_command", "null", None)))

      When("the request is normalized")
      val normalized = OperationUpdateRequestNormalizer.normalize(_operation, request).toOption.get

      Then("the generated binder receives an explicit SetNull marker")
      normalized.properties.map(x => x.name -> x.value) shouldBe List("nickname" -> Update.SetNull)
    }

    "leave ordinary JSON empty arrays unchanged" in {
      Given("a request with an ordinary empty collection and no command carrier")
      val request = _request(properties = List(Property("tags", Vector.empty, None)))

      When("the request passes through the shared boundary")
      val normalized = OperationUpdateRequestNormalizer.normalize(_operation, request)

      Then("the value is not reinterpreted as an operand-less command")
      normalized shouldBe Consequence.success(request)
    }

    "reject cross-transport duplicate command carriers deterministically" in {
      Given("the same command in both arguments and properties")
      val request = _request(
        arguments = List(Argument("tags__update_command", "clear")),
        properties = List(Property("tags__update_command", "clear", None))
      )

      When("the shared boundary groups all occurrences")
      val normalized = OperationUpdateRequestNormalizer.normalize(_operation, request)

      Then("the request fails instead of selecting one transport position")
      normalized shouldBe a[Consequence.Failure[?]]
    }
  }

  private def _request(
    arguments: List[Argument] = Nil,
    properties: List[Property] = Nil
  ): Request =
    Request(
      component = Some("example"),
      service = Some("entity"),
      operation = "updateExample",
      arguments = arguments,
      switches = Nil,
      properties = properties
    )

  private def _operation: CmlOperationDefinition =
    CmlOperationDefinition(
      name = "updateExample",
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
