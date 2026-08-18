package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.observability.ObservabilityEngine
import org.goldenport.cncf.path.{AliasLoader, AliasResolver, PathPreNormalizer}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.CncfVersion
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.schema.DataConfidentiality
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan. 20, 2026
 *  version Feb.  1, 2026
 *  version Mar. 28, 2026
 *  version Apr. 11, 2026
 *  version Apr. 14, 2026
 *  version May. 11, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class AdminSystemPingExecutionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "ComponentLogic" should {
    "execute resolver-normalized admin.system.ping requests" in {
      Given("the default command subsystem and a ping alias")
      _with_alias_context(
        RunMode.Command,
        _alias_config("ping" -> s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system.ping")
      ) {
        (aliasResolver, context) =>
          val versionedping = context.formatPing
          val subsystem = DefaultSubsystemFactory.default(Some("command"))
          val admincomponent = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN)
            .getOrElse(fail("admin component not found"))
          val resolver = subsystem.resolver

          When("canonical and alias selectors are resolved and executed")
          Seq(
            s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system.ping",
            "ping"
          ).foreach { selector =>
            val normalized =
              PathPreNormalizer.rewriteSelector(selector, RunMode.Command, aliasResolver)
            val request = _build_request(resolver, normalized)
            val response = _execute_ping(admincomponent, request)

            Then("each selector produces the versioned ping scalar")
            response match {
              case Consequence.Success(OperationResponse.Scalar(output)) =>
                output shouldBe versionedping
              case other =>
                fail(s"expected ping scalar but got $other")
            }
          }
      }
    }

    "execute resolver-normalized admin.system.status requests" in {
      Given("the default command subsystem and a status alias")
      _with_alias_context(
        RunMode.Command,
        _alias_config("status" -> s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system.status")
      ) {
        (aliasResolver, context) =>
          val subsystem = DefaultSubsystemFactory.default(Some("command"))
          val admincomponent = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN)
            .getOrElse(fail("admin component not found"))
          val resolver = subsystem.resolver

          When("canonical and alias selectors are resolved and executed")
          Seq(
            s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system.status",
            "status"
          ).foreach { selector =>
            val normalized =
              PathPreNormalizer.rewriteSelector(selector, RunMode.Command, aliasResolver)
            val request = _build_request(resolver, normalized)
            val response = _execute_ping(admincomponent, request)

            Then("each selector produces an UP status record")
            response match {
              case Consequence.Success(OperationResponse.RecordResponse(record)) =>
                record.getString("status") shouldBe Some("UP")
                record.getString("timestamp").exists(_.nonEmpty) shouldBe true
                record.getString("uptime").exists(_.nonEmpty) shouldBe true
              case other =>
                fail(s"expected status record but got $other")
            }
          }
      }
    }

    "retain action execution history for admin execution inspection" in {
      Given("a command subsystem with observability history enabled")
      _with_alias_context(RunMode.Command, Configuration.empty) { (_, _) =>
        ObservabilityEngine.clearExecutionHistory()
        val subsystem = DefaultSubsystemFactory.default(Some("command"))
        val admincomponent = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN)
          .getOrElse(fail("admin component not found"))
        val resolver = subsystem.resolver
        val pingrequest = _build_request(
          resolver,
          s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.system.ping"
        )
        val historyrequest = _build_request(
          resolver,
          s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.execution.history"
        )
        val calltreerequest = _build_request(
          resolver,
          s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.execution.calltree"
        )
        val calltreecontext = ExecutionContext.withFrameworkCallTreeEnabled(admincomponent.logic.executionContext(), enabled = true)

        When("ping executes before history and calltree inspection")
        val pingresult = _execute_operation(admincomponent, pingrequest, Some(calltreecontext))
        val historyresult = _execute_operation(admincomponent, historyrequest)
        val calltreeresult = _execute_operation(admincomponent, calltreerequest)

        Then("history and calltree expose the canonical ping operation")
        pingresult match {
          case Consequence.Success(OperationResponse.Scalar(_)) =>
          case other =>
            fail(s"expected ping scalar but got $other")
        }

        historyresult match {
          case Consequence.Success(OperationResponse.RecordResponse(record)) =>
            record.getString("count") shouldBe Some("1")
            val executions = record.getAny("executions").collect { case xs: Seq[?] => xs }.getOrElse(fail("executions missing"))
            executions.size shouldBe 1
            val execution = executions.head.asInstanceOf[org.goldenport.record.Record]
            execution.getString("operation") shouldBe Some("org.goldenport.cncf.Admin.system.ping")
            execution.getAny("parameters") shouldBe defined
            execution.getString("outcome") shouldBe Some("success")
            execution.getString("result_type") shouldBe Some("Scalar")
            execution.getAny("calltree") shouldBe defined
          case other =>
            fail(s"expected execution history record but got $other")
        }

        calltreeresult match {
          case Consequence.Success(OperationResponse.RecordResponse(record)) =>
            record.getString("operation") shouldBe Some("org.goldenport.cncf.Admin.system.ping")
            record.getAny("calltree") shouldBe defined
          case other =>
            fail(s"expected execution calltree record but got $other")
        }
      }
    }

    "filter admin execution inspection by origin slot request arguments" in {
      Given("execution records from operation and background-js origin slots")
      _with_alias_context(RunMode.Command, Configuration.empty) { (_, _) =>
        ObservabilityEngine.clearExecutionHistory()
        ObservabilityEngine.recordActionExecution(
          operation = "app.page.render",
          parameters = Record.empty,
          parametersText = "",
          outcome = Right(OperationResponse.Scalar("page")),
          calltree = None,
          traceId = Some("trace-page"),
          executionId = Some("execution-page"),
          originSlot = "operation"
        )
        ObservabilityEngine.recordActionExecution(
          operation = "app.session.poll",
          parameters = Record.empty,
          parametersText = "",
          outcome = Right(OperationResponse.Scalar("poll")),
          calltree = None,
          traceId = Some("trace-poll"),
          executionId = Some("execution-poll"),
          originSlot = "background-js"
        )

        val subsystem = DefaultSubsystemFactory.default(Some("command"))
        val admincomponent = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN)
          .getOrElse(fail("admin component not found"))
        val resolver = subsystem.resolver
        val calltreerequest = _build_request(
          resolver,
          s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.execution.calltree"
        )
        val backgroundcalltreerequest =
          calltreerequest.copy(arguments = List(Argument("originSlot", "background-js", None)))
        val backgroundhistoryrequest =
          _build_request(
            resolver,
            s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.execution.history"
          )
            .copy(arguments = List(Argument("originSlot", "background-js", None)))
        val executioncalltreerequest =
          calltreerequest.copy(arguments = List(Argument("executionId", "execution-page", None)))
        val executionhistoryrequest =
          _build_request(
            resolver,
            s"${org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN.name}.execution.history"
          )
            .copy(arguments = List(Argument("executionId", "execution-page", None)))

        When("inspection requests select each origin slot and execution id")
        val calltreeresult = _execute_operation(admincomponent, calltreerequest)
        val backgroundcalltreeresult = _execute_operation(admincomponent, backgroundcalltreerequest)
        val backgroundhistoryresult = _execute_operation(admincomponent, backgroundhistoryrequest)
        val executioncalltreeresult = _execute_operation(admincomponent, executioncalltreerequest)
        val executionhistoryresult = _execute_operation(admincomponent, executionhistoryrequest)

        Then("the matching calltree and history records are returned")
        calltreeresult match {
          case Consequence.Success(OperationResponse.RecordResponse(record)) =>
            record.getString("operation") shouldBe Some("app.page.render")
            record.getString("origin_slot") shouldBe Some("operation")
          case other =>
            fail(s"expected default execution calltree record but got $other")
        }

        backgroundcalltreeresult match {
          case Consequence.Success(OperationResponse.RecordResponse(record)) =>
            record.getString("operation") shouldBe Some("app.session.poll")
            record.getString("origin_slot") shouldBe Some("background-js")
          case other =>
            fail(s"expected background execution calltree record but got $other")
        }

        backgroundhistoryresult match {
          case Consequence.Success(OperationResponse.RecordResponse(record)) =>
            record.getString("origin_slot_filter") shouldBe Some("background-js")
            record.getString("count") shouldBe Some("1")
            val executions = record.getAny("executions").collect { case xs: Seq[?] => xs }.getOrElse(fail("executions missing"))
            val execution = executions.head.asInstanceOf[org.goldenport.record.Record]
            execution.getString("operation") shouldBe Some("app.session.poll")
            execution.getString("origin_slot") shouldBe Some("background-js")
          case other =>
            fail(s"expected background execution history record but got $other")
        }

        executioncalltreeresult match {
          case Consequence.Success(OperationResponse.RecordResponse(record)) =>
            record.getString("operation") shouldBe Some("app.page.render")
            record.getString("execution_id") shouldBe Some("execution-page")
          case other =>
            fail(s"expected execution-specific calltree record but got $other")
        }

        executionhistoryresult match {
          case Consequence.Success(OperationResponse.RecordResponse(record)) =>
            record.getString("execution_id_filter") shouldBe Some("execution-page")
            record.getString("count") shouldBe Some("1")
            val executions = record.getAny("executions").collect { case xs: Seq[?] => xs }.getOrElse(fail("executions missing"))
            val execution = executions.head.asInstanceOf[org.goldenport.record.Record]
            execution.getString("operation") shouldBe Some("app.page.render")
            execution.getString("execution_id") shouldBe Some("execution-page")
          case other =>
            fail(s"expected execution-specific history record but got $other")
        }
      }
    }

    "redact sensitive scalar result summaries in execution history" in {
      Given("a scalar execution result containing secret material")
      ObservabilityEngine.clearExecutionHistory()

      ObservabilityEngine.recordActionExecution(
        operation = "test.secret.scalar",
        parameters = Record.data("name" -> "ok"),
        parametersText = "",
        outcome = Right(OperationResponse.Scalar("token=secret-token password=plain-secret")),
        calltree = None
      )

      When("the execution summary is rendered")
      val entry = ObservabilityEngine.latestExecution.getOrElse(fail("execution history missing"))
      Then("secret values are omitted from the rendered summary")
      entry.resultSummary should include ("scalar")
      entry.resultSummaryRecord.getString("kind") shouldBe Some("scalar")
      entry.resultSummaryRecord.getString("inline") shouldBe Some("false")
      entry.resultSummary should not include ("secret-token")
      entry.resultSummary should not include ("plain-secret")
      entry.resultSummaryRecord.show should not include ("secret-token")
      entry.resultSummaryRecord.show should not include ("plain-secret")
    }

    "redact metadata-sensitive record result summaries in execution history" in {
      Given("a record execution result with a secret field")
      ObservabilityEngine.clearExecutionHistory()

      ObservabilityEngine.recordActionExecution(
        operation = "test.secret.record",
        parameters = Record.data("name" -> "ok"),
        parametersText = "",
        outcome = Right(OperationResponse.RecordResponse(Record.data("proof" -> "123456", "status" -> "ok"))),
        resultConfidentiality = Map("proof" -> DataConfidentiality.Secret),
        calltree = None
      )

      When("the execution summary is rendered")
      val entry = ObservabilityEngine.latestExecution.getOrElse(fail("execution history missing"))
      Then("the secret field is redacted while status remains visible")
      entry.resultSummary should include ("record")
      entry.resultSummaryRecord.getString("kind") shouldBe Some("record")
      entry.resultSummaryRecord.show should include ("[redacted]")
      entry.resultSummaryRecord.show should include ("status=ok")
      entry.resultSummary should not include ("123456")
      entry.resultSummaryRecord.show should not include ("123456")
    }
  }

  private def _execute_ping(
    component: Component,
    request: Request
  ): Consequence[OperationResponse] =
    _execute_operation(component, request)

  private def _execute_operation(
    component: Component,
    request: Request,
    executioncontext: Option[ExecutionContext] = None
  ): Consequence[OperationResponse] = {
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action =>
        val call = executioncontext
          .map(ctx => component.logic.createActionCall(action, ctx))
          .getOrElse(component.logic.createActionCall(action))
        component.logic.execute(call)
      case other =>
        Consequence.operationInvalid(s"unexpected OperationRequest type: ${other.getClass.getName}")
    }
  }

  private def _build_request(
    resolver: OperationResolver,
    selector: String
  ): Request = {
    resolver.resolve(selector) match {
      case ResolutionResult.Resolved(_, component, service, operation) =>
        Request.of(
          component = component,
          service = service,
          operation = operation
        )
      case other =>
        fail(s"resolver failed for $selector: $other")
    }
  }

  private def _with_alias_context[T](
    mode: RunMode,
    configuration: Configuration
  )(body: (AliasResolver, GlobalRuntimeContext) => T): T = {
    val resolver = AliasLoader.load(configuration)
    val execution = ExecutionContext.create()
    val httpdriver = FakeHttpDriver.okText("noop")
    val runtimeconfig = RuntimeConfig.default.copy(
      httpDriver = httpdriver,
      mode = mode
    )
    val core = ScopeContext(
      kind = ScopeKind.Runtime,
      name = "ping-execution-spec",
      parent = None,
      observabilityContext = execution.observability,
      httpDriverOption = Some(httpdriver)
    ).core
    val context = new GlobalRuntimeContext(
      core = core,
      config = runtimeconfig,
      aliasResolver = resolver,
      runtimeMode = mode,
      commandExecutionMode = None,
      runtimeVersion = CncfVersion.current,
      subsystemName = GlobalRuntimeContext.SubsystemName,
      subsystemVersion = CncfVersion.current
    )
    val previous = GlobalRuntimeContext.current
    GlobalRuntimeContext.current = Some(context)
    try body(resolver, context)
    finally GlobalRuntimeContext.current = previous
  }

  private def _alias_config(defs: (String, String)*): Configuration = {
    val entries = defs.toVector.map { case (input, output) =>
      ConfigurationValue.ObjectValue(
        Map(
          "input" -> ConfigurationValue.StringValue(input),
          "output" -> ConfigurationValue.StringValue(output)
        )
      )
    }
    Configuration(Map(AliasLoader.configKey -> ConfigurationValue.ListValue(entries.toList)))
  }
}
