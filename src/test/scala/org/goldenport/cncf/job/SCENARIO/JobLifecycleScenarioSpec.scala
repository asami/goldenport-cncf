package org.goldenport.cncf.job.SCENARIO

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.spec
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.ingress.{DefaultArgsIngress, IngressCollection}
import org.goldenport.protocol.handler.egress.{Egress, EgressCollection}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.test.matchers.ConsequenceMatchers
import org.goldenport.cncf.action.{Action, ActionCall, Command, Query, ResourceAccess}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.job.{ActionId, ActionTask, JobContext, JobId, JobResult, JobStatus}
import org.goldenport.cncf.service.Service
import org.goldenport.protocol.service.{Service as ProtocolService}
import org.scalatest.GivenWhenThen
import org.scalatest.Assertions.{fail, succeed}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

/*
 * Job lifecycle scenario (Phase 1).
 *
 * This executable specification treats CNCF as a black box and verifies
 * the public job lifecycle behavior for Command and Query actions.
 */
/*
 * @since   Jan.  4, 2026
 * @version Jan.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class JobLifecycleScenarioSpec extends AnyWordSpec with GivenWhenThen
    with Matchers with Eventually with ConsequenceMatchers {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(3, Seconds), interval = Span(50, Millis))

  "Job lifecycle scenario" should {
    "execute Command asynchronously as a Job (success)" in {
      Given("a valid Command invocation through the public boundary")
      val args = Array("command", "hello")

      When("the command is submitted")
      val result = ScenarioAdapter.invokeCli(args)

      Then("a JobId is returned immediately")
      result should be_success
      val jobIdValue = result.take
      jobIdValue should not be empty

      val jobId = ScenarioAdapter.lastJobId
      jobId.isDefined shouldBe true
      jobIdValue shouldBe jobId.get.value

      Then("eventually JobStatus becomes Succeeded")
      eventually {
        ScenarioAdapter.jobStatus(jobId.get) shouldBe Some(JobStatus.Succeeded)
      }

      Then("result is retrievable by JobId and matches scalar output")
      eventually {
        ScenarioAdapter.jobResult(jobId.get) match {
          case Some(JobResult.Success(response)) =>
            ScenarioAdapter.toStringResponse(response) should be_success("Command(hello)")
          case Some(JobResult.Failure(_)) =>
            fail("expected success result")
          case None =>
            fail("missing job result")
        }
      }
    }

    "execute Command asynchronously as a Job (failure)" in {
      Given("a Command that fails through the public boundary")
      val args = Array("command-fail", "boom")

      When("the command is submitted")
      val result = ScenarioAdapter.invokeCli(args)

      Then("a JobId is returned immediately")
      result should be_success
      val jobIdValue = result.take
      jobIdValue should not be empty

      val jobId = ScenarioAdapter.lastJobId
      jobId.isDefined shouldBe true
      jobIdValue shouldBe jobId.get.value

      Then("eventually JobStatus becomes Failed")
      eventually {
        ScenarioAdapter.jobStatus(jobId.get) shouldBe Some(JobStatus.Failed)
      }

      Then("failure result is retrievable by JobId")
      eventually {
        ScenarioAdapter.jobResult(jobId.get) match {
          case Some(JobResult.Failure(_)) => succeed
          case Some(JobResult.Success(_)) => fail("expected failure result")
          case None => fail("missing job result")
        }
      }
    }

    "execute Query synchronously without JobId" in {
      Given("a valid Query invocation through the public boundary")
      val args = Array("query", "hello")

      When("the query is executed")
      val result = ScenarioAdapter.invokeCli(args)

      Then("result is returned synchronously via protocol egress as a scalar")
      result should be_success("Query(hello)")
      result.take should not be JobId.generate().value
    }
  }
}

private object ScenarioAdapter {
  private val serviceFactory = new RecordingService.Factory()
  private val component: Component = Component.create(TestProtocol.protocol, serviceFactory)

  def invokeCli(args: Array[String]): Consequence[String] =
    component.service.invokeCli(args)

  def lastJobId: Option[JobId] =
    serviceFactory.lastJobId

  def jobStatus(jobId: JobId): Option[JobStatus] =
    component.logic.getJobStatus(jobId)

  def jobResult(jobId: JobId): Option[JobResult] =
    component.logic.getJobResult(jobId)

  def toStringResponse(response: OperationResponse): Consequence[String] =
    component.logic.makeStringOperationResponse(response)
}

private case class RecordingService(
  core: ProtocolService.Core,
  ccore: Service.CCore
) extends Service {
  @volatile private var _lastJobId: Option[JobId] = None

  def lastJobId: Option[JobId] = _lastJobId

  override def invokeRequest(
    request: Request
  ): Consequence[Response] = {
    val executioncontext = org.goldenport.cncf.context.ExecutionContext.create()
    logic.makeOperationRequest(request).flatMap {
      case action: Command =>
        val actionid = ActionId.generate()
        val task = ActionTask(actionid, action, logic.component.actionEngine)
        val jobid = logic.submitJob(List(task), executioncontext)
        _lastJobId = Some(jobid)
        Consequence.success(OperationResponse.Scalar(jobid.value).toResponse)
      case action: Query =>
        _lastJobId = None
        val actionid = ActionId.generate()
        val jobcontext = JobContext(None, None, Some(actionid))
        val ctx = org.goldenport.cncf.context.ExecutionContext.withJobContext(
          executioncontext,
          jobcontext
        )
        val task = ActionTask(actionid, action, logic.component.actionEngine)
        task.run(ctx).result.map(_.toResponse)
      case _ =>
        Consequence.failure("OperationRequest must be Action")
    }
  }
}

private object RecordingService {
  final class Factory extends Component.ServiceFactory() {
    private var _service: Option[RecordingService] = None

    def lastJobId: Option[JobId] = _service.flatMap(_.lastJobId)

    override def create(
      core: ProtocolService.Core,
      ccore: Service.CCore
    ): Service = {
      val service = RecordingService(core, ccore)
      _service = Some(service)
      service
    }
  }
}

private object TestCommandOperation extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "command",
      request = spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(
            name = "value",
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      ),
      response = spec.ResponseDefinition(result = Nil)
    )

  override def createOperationRequest(
    req: Request
  ): Consequence[OperationRequest] = {
    req.arguments.headOption match {
      case Some(arg) =>
        Consequence.Success(
          new Command("command") {
            override def createCall(
              core: ActionCall.Core
            ): ActionCall = {
              val actionself = this
              val _core_ = core
              new ActionCall {
                override val core: ActionCall.Core = _core_
                override def action: Action = actionself
                override def accesses: Seq[ResourceAccess] = Nil
                override def execute(): Consequence[OperationResponse] =
                  Consequence.success(OperationResponse.Scalar(s"Command(${arg.value})"))
              }
            }

            override def toString: String = s"Command(${arg.value})"
          }
        )
      case None =>
        Consequence.failure("missing argument: value")
    }
  }
}

private object TestCommandFailOperation extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "command-fail",
      request = spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(
            name = "value",
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      ),
      response = spec.ResponseDefinition(result = Nil)
    )

  override def createOperationRequest(
    req: Request
  ): Consequence[OperationRequest] = {
    req.arguments.headOption match {
      case Some(arg) =>
        Consequence.Success(
          new Command("command-fail") {
            override def createCall(
              core: ActionCall.Core
            ): ActionCall = {
              val actionself = this
              val _core_ = core
              new ActionCall {
                override val core: ActionCall.Core = _core_
                override def action: Action = actionself
                override def accesses: Seq[ResourceAccess] = Nil
                override def execute(): Consequence[OperationResponse] =
                  Consequence.failure(s"Command failed: ${arg.value}")
              }
            }

            override def toString: String = s"CommandFail(${arg.value})"
          }
        )
      case None =>
        Consequence.failure("missing argument: value")
    }
  }
}

private object TestQueryOperation extends spec.OperationDefinition {
  override val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "query",
      request = spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(
            name = "value",
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      ),
      response = spec.ResponseDefinition(result = Nil)
    )

  override def createOperationRequest(
    req: Request
  ): Consequence[OperationRequest] = {
    req.arguments.headOption match {
      case Some(arg) =>
        Consequence.Success(
          new Query("query") {
            override def createCall(
              core: ActionCall.Core
            ): ActionCall = {
              val actionself = this
              val _core_ = core
              new ActionCall {
                override val core: ActionCall.Core = _core_
                override def action: Action = actionself
                override def accesses: Seq[ResourceAccess] = Nil
                override def execute(): Consequence[OperationResponse] =
                  Consequence.success(OperationResponse.Scalar(s"Query(${arg.value})"))
              }
            }

            override def toString: String = s"Query(${arg.value})"
          }
        )
      case None =>
        Consequence.failure("missing argument: value")
    }
  }
}

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
          operations = NonEmptyVector.of(
            TestCommandOperation,
            TestCommandFailOperation,
            TestQueryOperation
          )
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
