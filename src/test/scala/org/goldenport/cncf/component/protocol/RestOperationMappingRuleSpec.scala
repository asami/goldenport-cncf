package org.goldenport.cncf.component.protocol

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.goldenport.protocol._
import org.goldenport.protocol.spec.{OperationDefinition, RequestDefinition, ResponseDefinition}
import org.goldenport.bag.Bag

/*
 * @since   Feb.  7, 2026
 * @version Feb.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class RestOperationMappingRuleSpec extends AnyWordSpec with Matchers {

  "RestOperationDefinition mapping rule resolution" should {

    "prefer operation-side mappingRule over service-side mappingRule" in {

      val serviceRule = new RestParameterMappingRule {
        def parameters(
          operation: OperationDefinition,
          request: Request
        ): RestParameterMappingRule.Parameters =
          RestParameterMappingRule.Parameters(
            path = Vector("service"),
            body = None
          )
      }

      val operationRule = new RestParameterMappingRule {
        def parameters(
          operation: OperationDefinition,
          request: Request
        ): RestParameterMappingRule.Parameters =
          RestParameterMappingRule.Parameters(
            path = Vector("operation"),
            body = None
          )
      }

      val operation = new RestOperationDefinition {
        override val specification: OperationDefinition.Specification =
          OperationDefinition.Specification(
            name = "test",
            request = RequestDefinition(),
            response = ResponseDefinition()
          )

        override def restSpecification: RestSpecification =
          RestSpecification(
            baseUrl = "/api",
            mappingRule = serviceRule
          )

        override def restOperationSpecification: RestOperationSpecification =
          RestOperationSpecification(
            mappingRule = Some(operationRule)
          )
      }

      val request = Request(
        component = None,
        service = None,
        operation = "test",
        arguments = Nil,
        switches = Nil,
        properties = Nil,
        source = None
      )

      val resolvedRule =
        operation.restOperationSpecification.mappingRule
          .getOrElse(operation.restSpecification.mappingRule)

      val params = resolvedRule.parameters(operation, request)

      params.path shouldBe Vector("operation")
    }
  }
}
