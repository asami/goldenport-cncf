package org.goldenport.cncf.component

import java.math.MathContext
import java.nio.charset.Charset
import java.time.{Clock, ZoneId}
import java.util.Locale

import org.goldenport.Consequence
import org.goldenport.context.{EnvironmentContext as CoreEnvironmentContext, ExecutionContext as CoreExecutionContext, I18nContext, RandomContext, VirtualMachineContext}
import org.goldenport.id.{DefaultUniversalIdGenerator, EntropySource, UniversalId}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec.{OperationDefinition, RequestDefinition, ResponseDefinition}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ObservabilityContext, Principal, PrincipalId, RuntimeContext, SecurityContext, SecurityLevel, TraceId}
import org.goldenport.cncf.action.{ActionCall, ActionCallBuilder, ActionLogic, Command, DefaultActionCall, DefaultActionExecutor}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  1, 2026
 * @version Jan.  1, 2026
 * @author  ASAMI, Tomoharu
 */
class ActionCallFailureSpec extends AnyWordSpec with Matchers {
  "ActionCall failure propagation" should {
    "propagate failure and avoid builder and logic" in {
      val correlationid = _universal_id("corr")
      val executioncontext = _execution_context(correlationid)
      val opdef = new FailingOperationDefinition()
      val logic = new RecordingActionLogic()
      val entry = TestEntry("fail", opdef, logic)
      val builder = new FailureActionCallBuilder()
      val executor = new DefaultActionExecutor(builder)

      val request = Request(
        service = None,
        operation = "fail",
        arguments = Nil,
        switches = Nil,
        properties = Nil
      )

      val result = executor.execute(
        entry,
        request,
        executioncontext,
        Some(CorrelationId(correlationid))
      )

      result.isInstanceOf[Consequence.Failure[OperationResponse]] shouldBe true
      builder.invoked shouldBe true
      logic.invoked shouldBe false
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
    generator.generate("cncf", "operation-call-failure-spec", kind, Clock.systemUTC())
  }
}

final class FailingOperationDefinition extends OperationDefinition {
  val specification: OperationDefinition.Specification =
    OperationDefinition.Specification(
      name = "fail",
      request = RequestDefinition(Nil),
      response = ResponseDefinition()
    )

  def createOperationRequest(req: Request): Consequence[OperationRequest] =
    Consequence.failure("createOperationRequest failed")
}

final class RecordingActionLogic extends ActionLogic {
  private var _invoked: Boolean = false

  def invoked: Boolean = _invoked

  def execute(
    call: ActionCall
  ): Consequence[OperationResponse] = {
    _invoked = true
    Consequence.success(new OperationResponse() {})
  }
}

final class FailureActionCallBuilder extends ActionCallBuilder {
  private var _invoked: Boolean = false

  def invoked: Boolean = _invoked

  def build(
    opdef: OperationDefinition,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[ActionCall] = {
    _invoked = true
    opdef.createOperationRequest(request).map { opreq =>
      DefaultActionCall(
        action = new Command(request.operation) {},
        executionContext = executionContext,
        correlationId = correlationId,
        request = opreq
      )
    }
  }
}
