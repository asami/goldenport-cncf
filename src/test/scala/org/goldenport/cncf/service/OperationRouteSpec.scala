package org.goldenport.cncf.service

import java.math.MathContext
import java.nio.charset.Charset
import java.time.{Clock, ZoneId}
import java.util.Locale

import cats.{Id, ~>}
import org.goldenport.Consequence
import org.goldenport.context.{EnvironmentContext as CoreEnvironmentContext, ExecutionContext as CoreExecutionContext, I18nContext, RandomContext, VirtualMachineContext}
import org.goldenport.id.{DefaultUniversalIdGenerator, EntropySource, UniversalId}
import org.goldenport.log.Logger
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec.{OperationDefinition, RequestDefinition, ResponseDefinition}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, SecurityContext, SecurityLevel, TraceId}
import org.goldenport.cncf.action.DefaultActionCallBuilder
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.unitofwork.UnitOfWork.UnitOfWorkOp
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Dec. 31, 2025
 * @version Dec. 31, 2025
 * @author  ASAMI, Tomoharu
 */
class OperationRouteSpec extends AnyWordSpec with Matchers {
  "Operation route" should {
    "build ActionCall from ingress -> Request -> OperationRequest" in {
      val ingress = TestIngress()
      val opdef = new TestOperationDefinition()
      val builder = new DefaultActionCallBuilder()
      val correlationid = _universal_id("corr")
      val executioncontext = _execution_context(correlationid)

      val request = ingress.toRequest(
        name = "test-operation",
        arguments = Map("count" -> 123, "label" -> "abc")
      )

      val callresult = builder.build(
        opdef = opdef,
        request = request,
        executionContext = executioncontext,
        correlationId = Some(CorrelationId(correlationid))
      )

      opdef.invoked shouldBe true
      opdef.receivedRequest shouldBe Some(request)

      callresult match {
        case Consequence.Success(c) =>
          c.executionContext.shouldBe(executioncontext)
          c.request match {
            case r: OperationRequest.Core.Holder =>
              r.operation.shouldBe("test-operation")
              r.arguments.map(a => a.name -> a.value).toMap.shouldBe(Map(
                "count" -> 123,
                "label" -> "abc"
              ))
            case _ =>
              fail("unexpected OperationRequest implementation")
          }
        case _ =>
          fail("unexpected ActionCall result")
      }
    }

    "avoid semantic interpretation outside OperationDefinition" in {
      val ingress = TestIngress()
      val opdef = new TestOperationDefinition()
      val builder = new DefaultActionCallBuilder()
      val correlationid = _universal_id("corr")
      val executioncontext = _execution_context(correlationid)

      val arguments = Map("raw" -> Map("nested" -> List(1, 2, 3)))
      val request = ingress.toRequest(
        name = "no-defaults",
        arguments = arguments
      )

      val callresult = builder.build(
        opdef = opdef,
        request = request,
        executionContext = executioncontext,
        correlationId = Some(CorrelationId(correlationid))
      )

      opdef.receivedRequest shouldBe Some(request)

      callresult match {
        case Consequence.Success(c) =>
          c.request match {
            case r: OperationRequest.Core.Holder =>
              r.arguments.map(a => a.name -> a.value).toMap.shouldBe(arguments)
            case _ =>
              fail("unexpected OperationRequest implementation")
          }
        case _ =>
          fail("unexpected ActionCall result")
      }
    }
  }

  private def _execution_context(correlationid: UniversalId): ExecutionContext = {
    val core = CoreExecutionContext.Core(
      environment = CoreEnvironmentContext.local(),
      vm = VirtualMachineContext.Instant(
        VirtualMachineContext.Core(
          clock = Clock.systemUTC(),
          timezone = ZoneId.of("UTC"),
          encoding = Charset.forName("UTF-8"),
          lineSeparator = "\n",
          mathContext = MathContext.DECIMAL64,
          environmentVariables = Map.empty,
          resourceBundleBaseNames = Nil,
          resourceBundleLocales = Nil,
          resourceBundleResolutionOrder = Nil
        )
      ),
      i18n = I18nContext.Instant(
        I18nContext.Core(
          textNormalizationPolicy = "default",
          textComparisonPolicy = "default",
          dateTimeFormatPolicy = "default",
          locale = Some(Locale.ROOT)
        )
      ),
      locale = Locale.ROOT,
      timezone = ZoneId.of("UTC"),
      encoding = Charset.forName("UTF-8"),
      clock = Clock.systemUTC(),
      math = MathContext.DECIMAL64,
      random = RandomContext.from("fixed"),
      logger = TestLogger
    )

    val security = SecurityContext(
      principal = TestPrincipal(),
      capabilities = Set.empty,
      level = SecurityLevel("test")
    )

    val observability = ObservabilityContext(
      traceId = TraceId(correlationid),
      spanId = None,
      correlationId = Some(CorrelationId(correlationid))
    )

    val cncfcore = ExecutionContext.CncfCore(
      security = security,
      observability = observability,
      runtime = new TestRuntimeContext()
    )

    ExecutionContext.Instance(
      core = core,
      cncfCore = cncfcore
    )
  }

  private def _universal_id(kind: String): UniversalId = {
    val generator = new DefaultUniversalIdGenerator(EntropySource.Default)
    generator.generate("cncf", "operation-route-spec", kind, Clock.systemUTC())
  }
}

final case class TestIngress() {
  def toRequest(
    name: String,
    arguments: Map[String, Any]
  ): Request = {
    val args = arguments.toList.map { case (k, v) => Argument(k, v, None) }
    Request(
      service = None,
      operation = name,
      arguments = args,
      switches = Nil,
      properties = Nil
    )
  }
}

final class TestOperationDefinition extends OperationDefinition {
  private var _invoked: Boolean = false
  private var _received_request: Option[Request] = None

  def invoked: Boolean = _invoked
  def receivedRequest: Option[Request] = _received_request

  val specification: OperationDefinition.Specification =
    OperationDefinition.Specification(
      name = "test-operation-definition",
      request = RequestDefinition(Nil),
      response = ResponseDefinition()
    )

  def createOperationRequest(req: Request): Consequence[OperationRequest] = {
    _invoked = true
    _received_request = Some(req)
    Consequence.success(
      OperationRequest(
        service = req.service,
        operation = req.operation,
        arguments = req.arguments,
        switches = req.switches,
        properties = req.properties
      )
    )
  }
}

final case class TestPrincipal() extends Principal {
  def id: PrincipalId = PrincipalId("test-principal")
  def attributes: Map[String, String] = Map.empty
}

final class TestRuntimeContext extends RuntimeContext {
  def unitOfWork: UnitOfWork = null

  def unitOfWorkInterpreter[T]: (UnitOfWorkOp ~> Id) =
    new (UnitOfWorkOp ~> Id) {
      def apply[A](fa: UnitOfWorkOp[A]): Id[A] =
        throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in specs")
    }

  def unitOfWorkTryInterpreter[T]: (UnitOfWorkOp ~> scala.util.Try) =
    new (UnitOfWorkOp ~> scala.util.Try) {
      def apply[A](fa: UnitOfWorkOp[A]): scala.util.Try[A] =
        throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not used in specs")
    }

  def unitOfWorkEitherInterpreter[T](op: UnitOfWorkOp[T]): Either[Throwable, T] =
    Left(new UnsupportedOperationException("unitOfWorkEitherInterpreter is not used in specs"))

  def commit(): Unit = {}
  def abort(): Unit = {}
  def dispose(): Unit = {}

  def toToken: String = "test-runtime-context"
}

object TestLogger extends Logger {
  def trace(message: => String): Unit = {}
  def debug(message: => String): Unit = {}
  def info(message: => String): Unit = {}
  def warn(message: => String): Unit = {}
  def error(message: => String): Unit = {}
  def error(cause: Throwable, message: => String): Unit = {}
  def fatal(message: => String): Unit = {}
  def fatal(cause: Throwable, message: => String): Unit = {}
}
