package org.goldenport.cncf.component.protocol

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.goldenport.protocol.*
import org.goldenport.protocol.operation.*
import org.goldenport.protocol.spec.{RequestDefinition, ResponseDefinition, OperationDefinition}
import org.goldenport.http.HttpRequest
import org.goldenport.cncf.component.protocol.ComponentOperationDefinition.OperationAttribute

/*
 * @since   Feb.  6, 2026
 * @version Feb.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class RestOperationDefinitionSpec extends AnyWordSpec with Matchers with GivenWhenThen {

  "RestOperationDefinition" should {

    "derive base URL from the operation name" in {
      Given("a Command operation definition")
      val op = new TestRestOperation(OperationAttribute.Command)

      When("its RestSpecification is created")
      val spec = op.restSpecification

      Then("the base URL starts with a slash-prefixed name")
      spec.baseUrl shouldBe "/test"
    }

    "default to POST for Command operations" in {
      Given("a Command operation definition")
      val op = new TestRestOperation(OperationAttribute.Command)

      When("the resolved method is derived")
      val method = resolve_method(op)

      Then("POST is selected")
      method shouldBe HttpRequest.POST
    }

    "default to GET for Query operations" in {
      Given("a Query operation definition")
      val op = new TestRestOperation(OperationAttribute.Query)

      When("the resolved method is derived")
      val method = resolve_method(op)

      Then("GET is selected")
      method shouldBe HttpRequest.GET
    }

    "honor explicit method overrides" in {
      Given("an operation that targets a PUT override")
      val op = new PutRestOperation()

      When("the resolved method is derived")
      val method = resolve_method(op)

      Then("PUT is selected")
      method shouldBe HttpRequest.PUT
    }

    "allow overriding the base URL" in {
      Given("an operation that overrides the base URL")
      val op = new PutRestOperation()

      When("the resolved URL is derived")
      val base = resolve_base_url(op)

      Then("the override is used")
      base shouldBe "http://override"
    }
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------
  private def resolve_method(op: RestOperationDefinition): HttpRequest.Method =
    op.restOperationSpecification.method.getOrElse {
      op.operationAttribute match {
        case OperationAttribute.Query => HttpRequest.GET
        case OperationAttribute.Command => HttpRequest.POST
        case _ => HttpRequest.POST
      }
    }

  private def resolve_base_url(op: RestOperationDefinition): String =
    op.restOperationSpecification.baseUrl.getOrElse(op.restSpecification.baseUrl)

  // ------------------------------------------------------------------
  // Test fixtures
  // ------------------------------------------------------------------
  private class TestRestOperation(
    attr: OperationAttribute
  ) extends RestOperationDefinition {
    override def name: String = "test"
    override def operationAttribute: OperationAttribute = attr
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "test",
        request = RequestDefinition(),
        response = ResponseDefinition()
      )
  }

  private class PutRestOperation extends TestRestOperation(OperationAttribute.Command) {
    override def restOperationSpecification: RestOperationSpecification =
      RestOperationSpecification(
        method = Some(HttpRequest.PUT),
        baseUrl = Some("http://override")
      )
  }
}
