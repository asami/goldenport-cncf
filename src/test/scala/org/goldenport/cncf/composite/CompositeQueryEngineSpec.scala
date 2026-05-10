package org.goldenport.cncf.composite

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.protocol.{Property, Protocol, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.XString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 10, 2026
 * @version May. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final class CompositeQueryEngineSpec extends AnyWordSpec with Matchers {
  "CompositeQueryEngine" should {
    "execute multiple Query requests and key results by name" in {
      given ExecutionContext = ExecutionContext.test()
      val response = _success(CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("first", _request("echo"), required = true),
        NamedQuery("second", _request("echo2"), required = true)
      ))))

      response.results.map(_.name) shouldBe Vector("first", "second")
      response.requiredRecord("first").toOption.flatMap(_.getString("operation")) shouldBe Some("echo")
      response.requiredRecord("second").toOption.flatMap(_.getString("operation")) shouldBe Some("echo2")
    }

    "reject duplicate query names" in {
      given ExecutionContext = ExecutionContext.test()
      CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("dup", _request("echo")),
        NamedQuery("dup", _request("echo2"))
      ))) match {
        case Consequence.Success(_) =>
          fail("duplicate query names should fail")
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("duplicate")
      }
    }

    "reject non-Query operations" in {
      given ExecutionContext = ExecutionContext.test()
      CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("mutate", _request("mutate"))
      ))) match {
        case Consequence.Success(_) =>
          fail("Command operation should fail")
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("only Query")
      }
    }

    "reject job-producing query execution modes" in {
      given ExecutionContext = ExecutionContext.test()
      val request = _request("echo").copy(
        properties = List(Property("textus.debug.trace-job", "true", None))
      )

      CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("trace", request)
      ))) match {
        case Consequence.Success(_) =>
          fail("trace-job query execution should fail")
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("trace-job")
      }
    }

    "reject trace-job execution in the query-only subsystem boundary" in {
      val request = _request("echo").copy(
        properties = List(Property("textus.debug.trace-job", "true", None))
      )

      _subsystem().executeQueryOnlyWithMetadata(request) match {
        case Consequence.Success(_) =>
          fail("query-only execution must not create trace jobs")
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("trace-job")
      }
    }

    "capture optional query failure and continue" in {
      given ExecutionContext = ExecutionContext.test()
      val response = _success(CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("optional-failure", _request("fail"), required = false),
        NamedQuery("after", _request("echo"), required = true)
      ))))

      response.result("optional-failure").exists(!_.isSuccess) shouldBe true
      response.diagnostics.map(_.name) should contain ("optional-failure")
      response.requiredRecord("after").toOption.flatMap(_.getString("operation")) shouldBe Some("echo")
    }

    "fail the composite request when a required query fails" in {
      given ExecutionContext = ExecutionContext.test()
      CompositeQueryEngine(_subsystem()).execute(CompositeQueryRequest(Vector(
        NamedQuery("required-failure", _request("fail"), required = true),
        NamedQuery("after", _request("echo"), required = true)
      ))) match {
        case Consequence.Success(_) =>
          fail("required failure should fail the composite request")
        case Consequence.Failure(conclusion) =>
          conclusion.show should include ("intentional query failure")
      }
    }
  }

  private def _subsystem(): Subsystem = {
    val subsystem = TestComponentFactory.emptySubsystem()
    subsystem.add(_component(subsystem))
  }

  private def _component(subsystem: Subsystem) =
    TestComponentFactory.create(
      "cq",
      Protocol(services = spec.ServiceDefinitionGroup(Vector(_SampleService))),
      subsystem = subsystem
    )

  private def _request(operation: String): Request =
    Request.of(component = "cq", service = "sample", operation = operation)

  private def _success[A](value: Consequence[A]): A =
    value match {
      case Consequence.Success(x) => x
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }
}

private object _SampleService extends spec.ServiceDefinition {
  val specification: spec.ServiceDefinition.Specification =
    spec.ServiceDefinition.Specification(
      name = "sample",
      operations = spec.OperationDefinitionGroup(NonEmptyVector.of(
        _EchoOperation("echo"),
        _EchoOperation("echo2"),
        _FailOperation,
        _MutateOperation
      ))
    )
}

private final case class _EchoOperation(
  operationName: String
) extends spec.OperationDefinition {
  val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = operationName,
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition(result = List(XString))
    )

  def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(_EchoAction(req))
}

private object _FailOperation extends spec.OperationDefinition {
  val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "fail",
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition(result = List(XString))
    )

  def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(_FailAction(req))
}

private object _MutateOperation extends spec.OperationDefinition {
  val specification: spec.OperationDefinition.Specification =
    spec.OperationDefinition.Specification(
      name = "mutate",
      request = spec.RequestDefinition(),
      response = spec.ResponseDefinition(result = List(XString))
    )

  def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.success(_MutateAction(req))
}

private final case class _EchoAction(request: Request) extends QueryAction {
  def createCall(core: ActionCall.Core): ActionCall =
    _EchoActionCall(core)
}

private final case class _EchoActionCall(core: ActionCall.Core) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.RecordResponse(Record.dataAuto(
      "operation" -> core.action.request.operation
    )))
}

private final case class _FailAction(request: Request) extends QueryAction {
  def createCall(core: ActionCall.Core): ActionCall =
    _FailActionCall(core)
}

private final case class _FailActionCall(core: ActionCall.Core) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] =
    Consequence.operationInvalid("intentional query failure")
}

private final case class _MutateAction(request: Request) extends CommandAction {
  def createCall(core: ActionCall.Core): ActionCall =
    _MutateActionCall(core)
}

private final case class _MutateActionCall(core: ActionCall.Core) extends ProcedureActionCall {
  def execute(): Consequence[OperationResponse] =
    Consequence.success(OperationResponse.Void())
}
