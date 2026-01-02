package org.goldenport.cncf.component

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
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec.{OperationDefinition, RequestDefinition, ResponseDefinition}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, SecurityContext, SecurityLevel, TraceId}
import org.goldenport.cncf.action.{Action, ActionCall, ActionCallBuilder, ActionLogic, Command, DefaultActionExecutor, ResourceAccess}
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.unitofwork.UnitOfWork.UnitOfWorkOp
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  1, 2026
 * @version Jan.  2, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentActionRouteSpec extends AnyWordSpec with Matchers {
  "ComponentActionEntry routes" should {
    "support Service and Receptor through ActionExecutor" in {
      val correlationid = _universal_id("corr")
      val executioncontext = _execution_context(correlationid)
      val opdef = new TestOperationDefinition()
      val logic = new TestActionLogic()
      val entry = TestEntry("test", opdef, logic)
      val builder = new RecordingActionCallBuilder()
      val executor = new DefaultActionExecutor(builder)

      val service = new TestService(Seq(entry), executor)
      val receptor = new TestReceptor(Seq(entry), executor)
      val ingress = TestIngress()
      val request = ingress.toRequest(
        name = "test",
        arguments = Map("count" -> 10)
      )

      service.call("test", request, executioncontext, Some(CorrelationId(correlationid)))
      receptor.receive("test", request, executioncontext, Some(CorrelationId(correlationid)))

      opdef.invoked shouldBe true
      builder.invoked shouldBe true
      logic.invoked shouldBe true
      logic.receivedExecutionContext shouldBe Some(executioncontext)
      logic.receivedCorrelationId shouldBe Some(CorrelationId(correlationid))
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
    generator.generate("cncf", "component-action-route-spec", kind, Clock.systemUTC())
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

final case class TestEntry(
  name: String,
  opdef: OperationDefinition,
  logic: ActionLogic
) extends ComponentActionEntry

final class TestService(
  entrylist: Seq[ComponentActionEntry],
  executor: DefaultActionExecutor
) extends Service {
  def entries: Seq[ComponentActionEntry] = entrylist

  def call(
    name: String,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[OperationResponse] = {
    val entry = entrylist.find(_.name == name).get
    executor.execute(entry, request, executionContext, correlationId)
  }
}

final class TestReceptor(
  entrylist: Seq[ComponentActionEntry],
  executor: DefaultActionExecutor
) extends Receptor {
  def entries: Seq[ComponentActionEntry] = entrylist

  def receive(
    name: String,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[OperationResponse] = {
    val entry = entrylist.find(_.name == name).get
    executor.execute(entry, request, executionContext, correlationId)
  }
}

final class TestOperationDefinition extends OperationDefinition {
  private var _invoked: Boolean = false

  def invoked: Boolean = _invoked

  val specification: OperationDefinition.Specification =
    OperationDefinition.Specification(
      name = "test",
      request = RequestDefinition(Nil),
      response = ResponseDefinition()
    )

  def createOperationRequest(req: Request): Consequence[OperationRequest] = {
    _invoked = true
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

final class TestActionLogic extends ActionLogic {
  private var _invoked: Boolean = false
  private var _received_execution_context: Option[ExecutionContext] = None
  private var _received_correlation_id: Option[CorrelationId] = None

  def invoked: Boolean = _invoked
  def receivedExecutionContext: Option[ExecutionContext] = _received_execution_context
  def receivedCorrelationId: Option[CorrelationId] =
    _received_correlation_id

  def execute(
    call: ActionCall
  ): Consequence[OperationResponse] = {
    _invoked = true
    _received_execution_context = Some(call.executionContext)
    _received_correlation_id = call.executionContext.observability.correlationId
    Consequence.success(OperationResponse.Void)
  }
}

final class RecordingActionCallBuilder extends ActionCallBuilder {
  private var _invoked: Boolean = false

  def invoked: Boolean = _invoked

  def build(
    opdef: OperationDefinition,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[ActionCall] = {
    _invoked = true
    val action =
      new Command(request.operation) {
        override def createCall(
          core: ActionCall.Core
        ): ActionCall =
          RecordingActionCall(core)
      }
    val _ = opdef.createOperationRequest(request)
    Consequence.success(
      RecordingActionCall(
        core = ActionCall.Core(
          action = action,
          executionContext = executionContext,
          correlationId = correlationId
        )
      )
    )
  }
}

final case class RecordingActionCall(
  override val core: ActionCall.Core
) extends ActionCall(core) {
  private val _action: Action =
    new Command("recording") {
      override def createCall(
        core: ActionCall.Core
      ): ActionCall =
        RecordingActionCall(core)
    }

  override def action: Action = _action
  def accesses: Seq[ResourceAccess] = Nil

  def execute(): Consequence[OperationResponse] =
    Consequence.failure("stub: Phase 3 Action execution not implemented")
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
