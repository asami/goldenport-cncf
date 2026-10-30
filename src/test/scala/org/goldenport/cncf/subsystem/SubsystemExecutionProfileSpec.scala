package org.goldenport.cncf.subsystem

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeKind}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.resource.ResourceReadLimits
import org.goldenport.cncf.testutil.{RuntimeBindingAdmissionFixture, TestComponentFactory}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Property, Protocol, Request, Response}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.record.Record
import org.goldenport.schema.XString
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 31, 2026
 *  version Aug.  1, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemExecutionProfileSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _in_phase53_spec =
    afterWord("in spec:subsystem-user-mode-admission, example:PM-53-01, rules:PM-53-01, phase:53")

  "SubsystemExecutionProfile" must _in_phase53_spec {
    "remain mode-free while a Subsystem user mode selects its execution profile" in {
      Given("the two Subsystem user modes")

      When("each Subsystem mode is admitted into a Subsystem execution profile")
      val standalone = SubsystemUserMode.Standalone.toExecutionProfile
      val multiuser = SubsystemUserMode.MultiUser.toExecutionProfile

      Then("only current-user evidence crosses into the Subsystem contract")
      standalone shouldBe SubsystemExecutionProfile.Fixed
      multiuser shouldBe SubsystemExecutionProfile.Authenticated
      classOf[SubsystemExecutionProfile].getDeclaredFields.map(_.getType.getName).toSet should not contain "org.goldenport.cncf.http.WebApplicationMode"
      classOf[SubsystemExecutionProfile].getDeclaredFields.map(_.getType.getName).toSet should not contain "org.goldenport.cncf.context.OperationMode"
    }

    "allow controlled tests to select explicit identity evidence without a mode type" in {
      Given("the mode-free profile contract")

      When("a controlled test profile is selected")
      val profile = SubsystemExecutionProfile.ControlledTest

      Then("the selected evidence is explicit and ingress independent")
      profile.currentUserEvidence shouldBe SubsystemCurrentUserEvidence.ControlledTest
    }
  }

  "Actual admitted checked output" should {
    "use the same admitted owner for pure Record and non-ASCII Json previews and final CLI bytes" in {
      Given("normal checked formatted-call admission and text requiring JSON escaping")
      val payload = "日本\n\"\\😀"
      Vector(false, true).foreach { json =>
        _with_checked(Some("true"), payload = payload, json = json) { (subsystem, component, state) =>
          When("the component previews its own actual response and returns that response once")
          val result = subsystem.executeResponseWithExecutionResponse(_checked_request(component))
          Then("preview includes the actual print separator and is byte-identical to the final owner body")
          val body = result.toOption.get.response match {
            case Response.Json(text) => text
            case other => fail("Expected checked JSON, got " + other)
          }
          state.preview.flatMap(_.toOption) shouldBe Some(body + System.lineSeparator())
          state.calls shouldBe 1
          if (json) {
            io.circe.parser.parse(body).toOption.get.hcursor.get[String]("sourceName").toOption shouldBe Some(payload)
            body should include("\\u65e5")
          } else {
            io.circe.parser.parse(body).toOption.get.hcursor.get[String]("source_name").toOption shouldBe Some(payload)
            body should include("\\n")
          }
        }
      }
    }

    "use actual late metadata for preview and final rejection while retaining the native fact" in {
      Given("valid pre-effect admission followed by actual runtime inline-debug metadata")
      _with_checked(Some("true"), lateDebug = true) { (subsystem, component, state) =>
        When("the component publishes metadata before requesting its pure preview")
        val result = subsystem.executeResponseWithExecutionResponse(_checked_request(component))
        Then("neither preview nor final formatting substitutes empty metadata or reexecutes the component")
        state.preview.exists(_.isSuccess) shouldBe false
        result.isSuccess shouldBe false
        state.calls shouldBe 1
        state.retained shouldBe Some("completed")
      }
    }

    "reject actual restored inline debugging from canonical priority, arguments and ingress-header spelling" in {
      Given("enabled checked output and conflicting raw aliases that cannot override actual ingress admission")
      _with_checked(Some("true")) { (subsystem, component, state) =>
        val request = _checked_request(component)
        val cases = Vector(
          request.copy(properties = request.properties ++ List(
            Property(RuntimeConfig.debugCallTreeKey, "true", None), Property("cncf.debug.calltree", "false", None))),
          request.copy(properties = request.properties :+ Property(RuntimeConfig.debugCallTreeKey, "false", None),
            arguments = List(org.goldenport.protocol.Argument(RuntimeConfig.debugCallTreeKey, "true", None))),
          request.copy(properties = request.properties :+ Property("x-textus-debug-calltree", "true", None)))
        When("each actual admitted context restores its inline debug mode")
        val rejected = cases.map(subsystem.executeResponseWithExecutionResponse)
        Then("every rejection happens before component request creation or retained effects")
        rejected.foreach(_.isSuccess shouldBe false)
        state.calls shouldBe 0
        state.retained shouldBe None
      }
    }

    "reject invalid Boolean and unsupported output before invoking the component" in {
      Given("an actual runtime with invalid opt-in or an envelope selection")
      Vector(Some("perhaps"), Some("true")).foreach { enabled =>
        _with_checked(enabled) { (subsystem, component, state) =>
          val request = _checked_request(component, if (enabled.contains("true")) "envelope" else "data")
          When("the ordinary formatted API receives the request")
          val result = subsystem.executeResponseWithExecutionResponse(request)
          Then("a typed admission rejection leaves actual component effects absent")
          result.isSuccess shouldBe false
          state.calls shouldBe 0
          state.retained shouldBe None
        }
      }
    }

    "preserve a post-effect native fact and expose it through collection without replay" in {
      Given("an admitted 128-byte runtime while the unrelated current global permits more")
      _with_checked(Some("true"), payload = "x" * 1024) { (subsystem, component, state) =>
        GlobalRuntimeContext.create("checked-output-unrelated", RuntimeConfig.default,
          ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
          ExecutionContext.create().observability, AliasResolver.empty)
        When("the component completes once but actual checked output exceeds its admitted cap")
        val failed = subsystem.executeResponseWithExecutionResponse(_checked_request(component))
        Then("the output failure is bounded and the acknowledged fact remains available")
        failed.isSuccess shouldBe false
        failed.toString should not include ("x" * 256)
        state.calls shouldBe 1
        state.retained shouldBe Some("completed")
        When("a separate read-only operation collects that retained fact through the raw API")
        val collected = subsystem.executeOperationResponse(Request.of(component, "sample", "collect"))
        Then("collection returns the actual fact and never replays the failed-output operation")
        collected.toOption shouldBe Some(OperationResponse.RecordResponse(Record.data("status" -> "completed")))
        state.calls shouldBe 1
      }
    }

    "keep absent and false opt-in legacy and execute enabled valid output at most once" in {
      Given("absent, false and true actual canonical runtime values")
      Vector(None, Some("false"), Some("true")).foreach { enabled =>
        _with_checked(enabled) { (subsystem, component, state) =>
          When("one formatted execution is requested")
          val result = subsystem.executeResponseWithExecutionResponse(_checked_request(component))
          Then("the normal Record result succeeds and exactly one component call occurs")
          result.isSuccess shouldBe true
          result.toOption.get.response shouldBe Response.Json("{\"source_name\":\"ok\"}")
          state.calls shouldBe 1
          state.preview.exists(_.isSuccess) shouldBe enabled.contains("true")
        }
      }
    }
  }

  private final class CheckedState(val payload: String, val json: Boolean, val lateDebug: Boolean) {
    var calls = 0
    var retained = Option.empty[String]
    var preview = Option.empty[Consequence[String]]
  }
  private final class CheckedService(state: CheckedState) extends spec.ServiceDefinition {
    val specification: spec.ServiceDefinition.Specification = spec.ServiceDefinition.Specification(
      name = "sample", operations = spec.OperationDefinitionGroup(NonEmptyVector.of(
        CheckedOperation("perform", state), CheckedOperation("collect", state))))
  }
  private final case class CheckedOperation(operationName: String, state: CheckedState) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification = spec.OperationDefinition.Specification(
      name = operationName, request = spec.RequestDefinition(), response = spec.ResponseDefinition(result = List(XString)))
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      Consequence.success(CheckedAction(request, state))
  }
  private final case class CheckedAction(request: Request, state: CheckedState) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall = CheckedCall(core, request, state)
  }
  private final case class CheckedCall(core: ActionCall.Core, request: Request, state: CheckedState) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      if (request.operation == "collect") Consequence.success(OperationResponse.RecordResponse(
        Record.data("status" -> state.retained.getOrElse("absent"))))
      else {
        state.calls += 1
        state.retained = Some("completed")
        val response = if (state.json) OperationResponse.Json(io.circe.Json.obj("sourceName" -> io.circe.Json.fromString(state.payload)))
          else OperationResponse.RecordResponse(Record.data("sourceName" -> state.payload))
        if (state.lateDebug) core.executionContext.runtime.updateExecutionMetadata(
          _.copy(inlineCallTree = Some(Record.data("actual" -> "late-debug"))))
        state.preview = Some(core.executionContext.checkedDataJsonTextC(response))
        Consequence.success(response)
      }
    }
  }
  private def _checked_request(component: String, shape: String = "data"): Request =
    Request.of(component, "sample", "perform").copy(properties = List(
      Property("textus.output.format", "json", None), Property("textus.output.shape", shape, None)))

  private def _with_checked(enabled: Option[String], payload: String = "ok", json: Boolean = false,
    lateDebug: Boolean = false)(
    body: (Subsystem, String, CheckedState) => Unit
  ): Unit = {
    val values = enabled.map(value => "textus.output.resource-preflight" -> ConfigurationValue.StringValue(value)).toMap
    val configuration = ResolvedConfiguration(Configuration(values), ConfigurationTrace.empty)
    val global = GlobalRuntimeContext.create("checked-output-actual", RuntimeConfig.default.copy(
      resourceReadLimits = ResourceReadLimits(128)), configuration, ExecutionContext.create().observability, AliasResolver.empty)
    val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "checked-output-actual",
      scopeContext = Some(global.createChildScope(ScopeKind.Subsystem, "checked-output-actual")),
      configuration = configuration, aliasResolver = AliasResolver.empty, runMode = RunMode.Command).enableControlledTestExecution())
    try {
      val state = new CheckedState(payload, json, lateDebug)
      val component = TestComponentFactory.create("CheckedOutput", Protocol(services = spec.ServiceDefinitionGroup(
        Vector(new CheckedService(state)))), subsystem = subsystem)
      subsystem.add(component)
      body(subsystem, component.componentId.name, state)
    } finally Subsystem.shutdownOwned(subsystem)
  }
}
