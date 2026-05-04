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
import org.goldenport.value.BaseContent
import org.goldenport.test.matchers.ConsequenceMatchers
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction, QueryAction, ResourceAccess}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.job.{ActionId, ActionTask, JobEngineTestFixture, JobId, JobResult, JobStatus}
import org.goldenport.cncf.service.Service
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.service.{Service as ProtocolService}
import org.scalatest.GivenWhenThen
import org.scalatest.Assertions.{fail, succeed}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Job lifecycle scenario (Phase 1).
 *
 * This executable specification treats CNCF as a black box and verifies
 * the public job lifecycle behavior for Command and Query actions.
 */
/*
 * @since   Jan.  4, 2026
 *  version Feb. 27, 2026
 *  version Apr. 22, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class JobLifecycleScenarioSpec extends AnyWordSpec with GivenWhenThen
    with Matchers with ConsequenceMatchers with JobEngineTestFixture {
  "Job lifecycle scenario" should {
    "execute Command asynchronously as a Job (success)" in {
      TestComponentFactory.withEmptySubsystem("job_lifecycle_scenario_success") { subsystem =>
        val adapter = new ScenarioAdapter(subsystem)
        Given("a valid Command invocation through the public boundary")
        val args = Array("command", "hello")

        When("the command is submitted")
        val result = adapter.invokeCli(args)

        Then("a JobId is returned immediately")
        result should be_success
        val jobIdValue = result.toOption.get
        jobIdValue should not be empty

        val jobId = adapter.lastJobId
        jobId.isDefined shouldBe true
        jobIdValue shouldBe jobId.get.value

        Then("JobStatus becomes Succeeded")
        awaitCondition(adapter.jobStatus(jobId.get).contains(JobStatus.Succeeded)) shouldBe true

        Then("result is retrievable by JobId and matches scalar output")
        awaitCondition(adapter.jobResult(jobId.get).nonEmpty) shouldBe true
        adapter.jobResult(jobId.get) match {
          case Some(JobResult.Success(response)) =>
            adapter.toStringResponse(response) should be_success("Command(hello)")
          case Some(JobResult.Failure(_)) =>
            fail("expected success result")
          case None =>
            fail("missing job result")
        }
      }
    }

    "execute Command asynchronously as a Job (failure)" in {
      TestComponentFactory.withEmptySubsystem("job_lifecycle_scenario_failure") { subsystem =>
        val adapter = new ScenarioAdapter(subsystem)
        Given("a Command that fails through the public boundary")
        val args = Array("command-fail", "boom")

        When("the command is submitted")
        val result = adapter.invokeCli(args)

        Then("a JobId is returned immediately")
        result should be_success
        val jobIdValue = result.toOption.get
        jobIdValue should not be empty

        val jobId = adapter.lastJobId
        jobId.isDefined shouldBe true
        jobIdValue shouldBe jobId.get.value

        Then("JobStatus becomes Failed")
        awaitCondition(adapter.jobStatus(jobId.get).contains(JobStatus.Failed)) shouldBe true

        Then("failure result is retrievable by JobId")
        awaitCondition(adapter.jobResult(jobId.get).nonEmpty) shouldBe true
        adapter.jobResult(jobId.get) match {
          case Some(JobResult.Failure(_)) => succeed
          case Some(JobResult.Success(_)) => fail("expected failure result")
          case None => fail("missing job result")
        }
      }
    }

    "execute Query synchronously without JobId" in {
      TestComponentFactory.withEmptySubsystem("job_lifecycle_scenario_query") { subsystem =>
        val adapter = new ScenarioAdapter(subsystem)
        Given("a valid Query invocation through the public boundary")
        val args = Array("query", "hello")

        When("the query is executed")
        val result = adapter.invokeCli(args)

        Then("result is returned synchronously via protocol egress as a scalar")
        result should be_success("Query(hello)")
        result.toOption.get should not be JobId.generate().value
      }
    }
  }
}

private final class ScenarioAdapter(subsystem: Subsystem) {
  private val serviceFactory = new RecordingService.Factory()
  private val component: Component =
    TestComponentFactory.create("test", TestProtocol.protocol, Some(serviceFactory), subsystem)

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
      case action: CommandAction =>
        val actionid = ActionId.generate()
        val task = ActionTask(actionid, action, logic.component.actionEngine, Some(logic.component))
        logic.submitJob(List(task), executioncontext).map { jobid =>
          _lastJobId = Some(jobid)
          OperationResponse.Scalar(jobid.value).toResponse
        }
      case action: QueryAction =>
        _lastJobId = None
        val actionid = ActionId.generate()
        val task = ActionTask(actionid, action, logic.component.actionEngine, Some(logic.component))
        logic.submitJob(List(task), executioncontext).flatMap(jobid => logic.awaitJobResult(jobid).map(_.toResponse))
      case _ =>
        Consequence.operationInvalid("OperationRequest must be Action")
    }
  }

}

private object RecordingService {
  final class Factory extends Component.ServiceFactory() {
    private var _services: Vector[RecordingService] = Vector.empty

    def lastJobId: Option[JobId] =
      _services.reverseIterator.flatMap(_.lastJobId).toSeq.headOption

    override def create(
      core: ProtocolService.Core,
      ccore: Service.CCore
    ): Service = {
      val service = RecordingService(core, ccore)
      _services = _services :+ service
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
            content = BaseContent.simple("value"),
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      ),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(
    req: Request
  ): Consequence[OperationRequest] = {
    req.arguments.headOption match {
      case Some(arg) =>
        Consequence.Success(
          new CommandAction() {
            // val name = "command"
            val request = Request.ofOperation("command")
            override def createCall(
              core: ActionCall.Core
            ): ActionCall = {
              val actionself = this
              val _core_ = core
              new ActionCall {
                override val core: ActionCall.Core = _core_
                override def action: Action = actionself
                override def execute(): Consequence[OperationResponse] =
                  Consequence.success(OperationResponse.Scalar(s"Command(${arg.value})"))
              }
            }

            override def show: String = s"Command(${arg.value})"
          }
        )
      case None =>
        Consequence.argumentMissing("value")
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
            content = BaseContent.simple("value"),
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      ),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(
    req: Request
  ): Consequence[OperationRequest] = {
    req.arguments.headOption match {
      case Some(arg) =>
        Consequence.Success(
          new CommandAction() {
            // val name = "command-fail"
            val request = Request.ofOperation("command-fail")
            override def createCall(
              core: ActionCall.Core
            ): ActionCall = {
              val actionself = this
              val _core_ = core
              new ActionCall {
                override val core: ActionCall.Core = _core_
                override def action: Action = actionself
                override def execute(): Consequence[OperationResponse] =
                  Consequence.operationInvalid(s"Command failed: ${arg.value}")
              }
            }

            override def show: String = s"CommandFail(${arg.value})"
          }
        )
      case None =>
        Consequence.argumentMissing("value")
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
            content = BaseContent.simple("value"),
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      ),
      response = spec.ResponseDefinition.void
    )

  override def createOperationRequest(
    req: Request
  ): Consequence[OperationRequest] = {
    req.arguments.headOption match {
      case Some(arg) =>
        Consequence.Success(
          new QueryAction() {
            // val name = "query"
            val request = Request.ofOperation("query")
            override def createCall(
              core: ActionCall.Core
            ): ActionCall = {
              val actionself = this
              val _core_ = core
              new ActionCall {
                override val core: ActionCall.Core = _core_
                override def action: Action = actionself
                override def execute(): Consequence[OperationResponse] =
                  Consequence.success(OperationResponse.Scalar(s"Query(${arg.value})"))
              }
            }

            override def show: String = s"Query(${arg.value})"
          }
        )
      case None =>
        Consequence.argumentMissing("value")
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
        Consequence.operationInvalid("unsupported response type")
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
