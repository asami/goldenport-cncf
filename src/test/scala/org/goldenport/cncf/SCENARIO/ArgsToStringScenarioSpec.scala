package org.goldenport.cncf.SCENARIO

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.Argument
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.ingress.IngressCollection
import org.goldenport.protocol.handler.ingress.DefaultArgsIngress
import org.goldenport.protocol.handler.egress.EgressCollection
import org.goldenport.protocol.handler.egress.Egress
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.service.{Service => ProtocolService}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.logic.ProtocolLogic
import org.goldenport.test.matchers.ConsequenceMatchers
import org.goldenport.cncf.action.{Action, ActionCall, Query, ResourceAccess}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.service.Service
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.testutil.TestComponentFactory
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
 * @version Feb.  6, 2026
 * @author  ASAMI, Tomoharu
 */
class ArgsToStringScenarioSpec extends AnyWordSpec with GivenWhenThen
    with Matchers with ConsequenceMatchers {

  "Primary scenario: args -> OperationRequest" should {
    "execute args to an OperationRequest result using inner logic" in {
      Given("minimal CLI-like arguments")
      val args = Array("query", "hello")

      When("executing the primary scenario")
      val result = TestComponent.runCli(args)

      Then("execution succeeds and returns an OperationRequest string")
      result.isSuccess shouldBe true
      result.value shouldBe a[String]
      result.value.toString should include ("Query(hello)")
    }

    "execute args to an OperationRequest result using Component" in {
      Given("minimal CLI-like arguments")
      val args = Array("query", "hello")

      When("executing the primary scenario")
      val result = TestComponentUsingComponent.service.invokeCli(args)

      Then("execution succeeds and returns an OperationRequest string")
      result should be_success("Query(hello)")
    }

    "execute args to an OperationRequest result using Component with custom Service" in {
      Given("minimal CLI-like arguments")
      val args = Array("query", "hello")

      When("executing the primary scenario")
      val result = TestComponentUsingComponentWithCustomService.service.invokeCli(args)

      Then("execution succeeds and returns an OperationRequest string")
      result should be_success("Query(hello)")
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
          new Query() {
            // val name = "query"
            def request = req
            override def createCall(
              core: ActionCall.Core
            ): ActionCall = {
              val actionself = this
              val _core_ = core
              new ActionCall {
                override val core: ActionCall.Core = _core_
                override def action: Action = actionself
                override def execute(): org.goldenport.Consequence[OperationResponse] =
                  org.goldenport.Consequence.Success(
                    new OperationResponse {
                      override def toResponse: Response = Response.Scalar[String](toString)
                      override def show: String = s"Query(${arg.value})"
                      def print = show
//                      override def toString: String = show
                    }
                  )
              }
            }

            override def show: String = s"Query(${arg.value})"
          }
        )
      case None =>
        org.goldenport.Consequence.failure("missing argument: query")
    }
  }
}

import org.goldenport.protocol.handler.egress.Egress

private object TestStringEgress extends Egress[String] {
  def kind: Egress.Kind[String] = Egress.Kind.`String`

  override def egress(res: Response): Consequence[String] = {
    res match {
      case Response.Scalar(value: String) =>
        Consequence.success(value)
      case _ =>
        Consequence.failure("unsupported response type")
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
            EgressCollection(
              Vector(TestStringEgress)
            ),
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
  val component: Component = TestComponentUsingComponent

  def runCli(args: Array[String]): ScenarioResult = {
    component.logic.makeOperationRequest(args) match {
      case org.goldenport.Consequence.Success(opreq) =>
        opreq match {
          case action: Action =>
            val executioncontext = ExecutionContext.test()
            val correlationid = executioncontext.observability.correlationId
            val core = ActionCall.Core(action, executioncontext, None, correlationid)
            val ac = action.createCall(core)
            val response = for {
              res <- component.logic.execute(ac)
              text <- component.logic.makeStringOperationResponse(res)
            } yield text
            ScenarioResult(isSuccess = true, value = response.toOption.get)
        }
      case org.goldenport.Consequence.Failure(err) =>
        // println("MESSAGE      : " + err.message)
        // println("STATUS       : " + err.status)
        // println("OBSERVATION  : " + err.observation)
        // println("DESCRIPTOR   : " + err.observation.descriptor)
        ScenarioResult(isSuccess = false, value = err.toString)
    }
  }
}

val TestComponentUsingComponent =
  TestComponentFactory.create("test", TestProtocol.protocol)

val TestComponentUsingComponentWithCustomService = {
  TestComponentFactory.create(
    "test",
    TestProtocol.protocol,
    Some(CustomTestService.Factory())
  )
}

case class CustomTestService(
  core: ProtocolService.Core,
  ccore: Service.CCore
) extends Service
object CustomTestService {
  class Factory() extends Component.ServiceFactory() {
    def create(
      core: ProtocolService.Core,
      ccore: Service.CCore
    ): Service = CustomTestService(core, ccore)
  }
}
