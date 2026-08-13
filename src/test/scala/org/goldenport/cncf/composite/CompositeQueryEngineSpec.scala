package org.goldenport.cncf.composite

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.context.{ExecutionContext, Principal, PrincipalId, SecurityContext}
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.{Property, Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.XString
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 10, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CompositeQueryEngineSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CompositeQueryEngine" should {
    "execute multiple Query requests and key results by name" in {
      Given("an admitted query-only subsystem with two named queries")
      given ExecutionContext = ExecutionContext.test()
      When("the composite query request is executed")
      val response = _success(CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("first", _request("echo"), required = true),
        NamedQuery("second", _request("echo2"), required = true)
      ))))

      Then("both query results retain their names and operation values")
      response.results.map(_.name) shouldBe Vector("first", "second")
      response.requiredRecord("first").toOption.flatMap(_.getString("operation")) shouldBe Some("echo")
      response.requiredRecord("second").toOption.flatMap(_.getString("operation")) shouldBe Some("echo2")
    }

    "reject duplicate query names" in {
      Given("an admitted query-only subsystem with duplicate query names")
      given ExecutionContext = ExecutionContext.test()
      When("the composite query request is executed")
      val result = CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("dup", _request("echo")),
        NamedQuery("dup", _request("echo2"))
      )))

      Then("the duplicate names are rejected")
      result match {
        case Consequence.Success(_) =>
          fail("duplicate query names should fail")
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("duplicate")
      }
    }

    "reject non-Query operations" in {
      Given("an admitted query-only subsystem with a command request")
      given ExecutionContext = ExecutionContext.test()
      When("the composite query request is executed")
      val result = CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("mutate", _request("mutate"))
      )))

      Then("the command request is rejected")
      result match {
        case Consequence.Success(_) =>
          fail("Command operation should fail")
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("only Query")
      }
    }

    "reject job-producing query execution modes" in {
      Given("a query request that asks for trace-job execution")
      given ExecutionContext = ExecutionContext.test()
      val request = _request("echo").copy(
        properties = List(Property("textus.debug.trace-job", "true", None))
      )

      When("the composite query request is executed")
      val result = CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("trace", request)
      )))

      Then("the job-producing execution mode is rejected")
      result match {
        case Consequence.Success(_) =>
          fail("trace-job query execution should fail")
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("trace-job")
      }
    }

    "reject trace-job execution in the query-only subsystem boundary" in {
      Given("a query-only subsystem and a trace-job request")
      given ExecutionContext = ExecutionContext.test()
      val request = _request("echo").copy(
        properties = List(Property("textus.debug.trace-job", "true", None))
      )

      When("the query-only boundary executes the request")
      val result = _subsystem().executeQueryOnlyWithMetadata(request)

      Then("the boundary rejects trace-job execution")
      result match {
        case Consequence.Success(_) =>
          fail("query-only execution must not create trace jobs")
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("trace-job")
      }
    }

    "preserve the caller security subject across the query-only boundary" in {
      Given("an authenticated caller security subject")
      val base = ExecutionContext.create(SecurityContext.Privilege.User)
      given ExecutionContext = ExecutionContext.withSecurityContext(
        base,
        base.security.copy(principal = new Principal {
          val id: PrincipalId = PrincipalId("composite-query-user")
          val attributes: Map[String, String] =
            base.security.principal.attributes ++ Map("authenticated" -> "true")
        })
      )

      When("the composite query request is executed")
      val response = _success(CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("subject", _request("echo"), required = true)
      ))))

      Then("the result retains the caller security subject")
      response.requiredRecord("subject").toOption.flatMap(_.getString("subject_id")) shouldBe
        Some("composite-query-user")
      response.requiredRecord("subject").toOption.flatMap(_.getBoolean("authenticated")) shouldBe
        Some(true)
    }

    "capture optional query failure and continue" in {
      Given("an optional failing query followed by a required query")
      given ExecutionContext = ExecutionContext.test()
      When("the composite query request is executed")
      val response = _success(CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("optional-failure", _request("fail"), required = false),
        NamedQuery("after", _request("echo"), required = true)
      ))))

      Then("the optional failure is recorded and the required query continues")
      response.result("optional-failure").exists(!_.isSuccess) shouldBe true
      response.diagnostics.map(_.name) should contain ("optional-failure")
      response.requiredRecord("after").toOption.flatMap(_.getString("operation")) shouldBe Some("echo")
    }

    "fail the composite request when a required query fails" in {
      Given("a required failing query")
      given ExecutionContext = ExecutionContext.test()
      When("the composite query request is executed")
      val result = CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("required-failure", _request("fail"), required = true),
        NamedQuery("after", _request("echo"), required = true)
      )))

      Then("the composite request fails")
      result match {
        case Consequence.Success(_) =>
          fail("required failure should fail the composite request")
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("intentional query failure")
      }
    }
  }

  private def _subsystem(): Subsystem = {
    val subsystem = TestComponentFactory.admittedEmptySubsystem()
    subsystem.add(_component(subsystem))
  }

  private def _component(subsystem: Subsystem) =
    TestComponentFactory.create(
      "cq",
      Protocol(services = spec.ServiceDefinitionGroup(Vector(SampleService))),
      subsystem = subsystem
    )

  private def _request(operation: String): Request =
    Request.of(component = "org.goldenport.cncf.test.Cq", service = "sample", operation = operation)

  private def _success[A](value: Consequence[A]): A =
    value match {
      case Consequence.Success(x) => x
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }
}

private object SampleService extends spec.ServiceDefinition {
  val specification: spec.ServiceDefinition.Specification =
    spec.ServiceDefinition.Specification(
      name = "sample",
      operations = spec.OperationDefinitionGroup(NonEmptyVector.of(
        EchoOperation("echo"),
        EchoOperation("echo2"),
        FailOperation,
        MutateOperation
      ))
    )
}

private final case class EchoOperation(
  operationName: String
) extends spec.OperationDefinition {
  val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = operationName,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition(result = List(XString))
    )

  def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(EchoAction(req))
}

private object FailOperation extends spec.OperationDefinition {
  val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "fail",
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition(result = List(XString))
    )

  def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(FailAction(req))
}

private object MutateOperation extends spec.OperationDefinition {
  val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "mutate",
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition(result = List(XString))
    )

  def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(MutateAction(req))
}

private final case class EchoAction(request: Request) extends QueryAction {
  def createCall(core: ActionCall.Core): ActionCall =
    EchoActionCall(core)
}

private final case class EchoActionCall(core: ActionCall.Core) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] = {
    val subject = SecuritySubject.current(using core.executionContext)
    Consequence.success(OperationResponse.RecordResponse(Record.dataAuto(
      "operation" -> core.action.request.operation,
      "subject_id" -> subject.subjectId,
      "authenticated" -> subject.isAuthenticated
    )))
  }
}

private final case class FailAction(request: Request) extends QueryAction {
  def createCall(core: ActionCall.Core): ActionCall =
    FailActionCall(core)
}

private final case class FailActionCall(core: ActionCall.Core) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] =
    Consequence.operationInvalid("intentional query failure")
}

private final case class MutateAction(request: Request) extends CommandAction {
  def createCall(core: ActionCall.Core): ActionCall =
    MutateActionCall(core)
}

private final case class MutateActionCall(core: ActionCall.Core) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Void())
}
