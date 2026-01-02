package org.goldenport.cncf.SCENARIO

import cats.data.NonEmptyVector
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec
import org.goldenport.protocol.Request
import org.goldenport.protocol.Argument
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.ingress.DefaultArgsIngress
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.logic.ProtocolLogic
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers

/*
 * Primary CNCF scenario (Phase 1).
 *
 * This executable spec verifies that a CNCF system can execute
 * a primary route from CLI-style arguments to an OperationRequest result.
 *
 * Internal execution is expected to be ActionCall-centered by design,
 * but this spec does not yet assert that fact explicitly.
 * ActionCall traversal will be verified in Phase 2 using observability trace.
 */
/*
 * @since   Jan.  1, 2026
 * @version Jan.  2, 2026
 * @author  ASAMI, Tomoharu
 */
class ArgsToStringScenarioSpec extends AnyWordSpec with GivenWhenThen with Matchers {

  "Primary scenario: args -> OperationRequest" should {

    "execute args to an OperationRequest result" in {
      Given("minimal CLI-like arguments")
      val args = Array("query", "hello")

      When("executing the primary scenario")
      val result =
        TestComponent.runCli(args)

      Then("execution succeeds and returns an OperationRequest string")
      result.isSuccess shouldBe true
      result.value shouldBe a[String]
      result.value.toString should include ("Query")
    }
  }
}

// Test-only OperationDefinition for Step 2
private object TestQueryOperation extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "query",
      request = spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(
            name = "query",
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      ),
      response = spec.ResponseDefinition(result = Nil)
    )

  override def createOperationRequest(
    req: Request
  ): org.goldenport.Consequence[OperationRequest] = {
    req.arguments.headOption match {
      case Some(arg) =>
        org.goldenport.Consequence.Success(
          new OperationRequest {
            override def toString: String = s"Query(${arg.value})"
          }
        )
      case None =>
        org.goldenport.Consequence.failure("missing argument: query")
    }
  }
}

private object TestProtocol {
  private val serviceDef =
    spec.ServiceDefinition(
      name = "test",
      operations =
        spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(TestQueryOperation)
        )
    )

  private val services =
    spec.ServiceDefinitionGroup(
      services = Vector(serviceDef)
    )

  val protocol: Protocol =
    Protocol(
      services = services,
      handler =
        ProtocolHandler(
          ingresses =
            IngressCollection(
              Vector(DefaultArgsIngress())
            ),
          egresses =
            EgressCollection(),
          projections =
            ProjectionCollection()
        )
    )
}

final case class ScenarioResult(
  isSuccess: Boolean,
  value: Any
)

object TestComponent {
  val protocol: Protocol = TestProtocol.protocol
  val protocolLogic = ProtocolLogic(protocol)

  def runCli(args: Array[String]): ScenarioResult = {
    protocolLogic.makeOperationRequest(args) match {
      case org.goldenport.Consequence.Success(opreq) =>
        ScenarioResult(isSuccess = true, value = opreq.toString)
      case org.goldenport.Consequence.Failure(err) =>
        println("MESSAGE      : " + err.message)
        println("STATUS       : " + err.status)
        println("OBSERVATION  : " + err.observation)
        println("DESCRIPTOR   : " + err.observation.descriptor)
        ScenarioResult(isSuccess = false, value = err.toString)
    }
  }
}
