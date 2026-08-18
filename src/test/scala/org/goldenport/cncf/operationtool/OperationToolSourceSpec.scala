package org.goldenport.cncf.operationtool

import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture

import java.time.{Clock, Instant, ZoneOffset}
import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.security.OperationAuthorizationRule
import org.goldenport.cncf.subsystem.{DefaultSubsystemFactory, GenericSubsystemDescriptor}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the provider-neutral internal Operation source.
 *
 * @since   Jul. 21, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationToolSourceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private val _tool_time = s"${BuiltinComponentIdentity.TOOL.name}.time.now"
  private val _tool_decimal = s"${BuiltinComponentIdentity.TOOL.name}.decimal.calculate"

  "Internal Operation tool identity" should {
    "round-trip exact runtime identities without entering the remote MCP namespace" in {
      val component = Gen.oneOf(
        BuiltinComponentIdentity.TOOL.name,
        BuiltinComponentIdentity.ADMIN.name
      )
      val segment = Gen.oneOf("tool", "admin", "time_now", "decimal-calculate", "Query")

      Given("arbitrary qualified ComponentId values and valid service and operation segments")
      val property = Prop.forAll(component, segment, segment) { (componentname, service, operation) =>
        val identity = _success(OperationToolIdentity.createC(componentname, service, operation))
        val parsed = OperationToolIdentity.parseC(identity.print)
        parsed == Consequence.success(identity) && !identity.print.contains("/")
      }

      When("each complete internal identity is parsed again")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)

      Then("every tuple remains exact and never acquires server/tool syntax")
      checked.passed shouldBe true
    }
  }

  "Internal Operation tool catalog" should {
    "remain empty when runtime admission contains no Operation identity" in {
      Given("an assembled subsystem and an explicit empty tool-set admission")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("operation-tool-empty"))
      val admission = _admission(Vector.empty)

      When("the runtime constructs the internal catalog")
      val catalog = _success(OperationToolCatalogBuilder.createC(subsystem, admission))

      Then("default discovery exposes no Operation")
      catalog.definitions shouldBe empty
    }

    "project only admitted Operations in deterministic order with typed input" in {
      Given("two builtin Operations admitted in reverse lexical order")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("operation-tool-catalog"))
      val admission = _admission(Vector(
        _identity(_tool_time),
        _identity(_tool_decimal)
      ))

      When("the internal catalog is constructed from the assembled runtime")
      val catalog = _success(OperationToolCatalogBuilder.createC(subsystem, admission))

      Then("only exact admitted definitions are sorted and retain typed required fields")
      catalog.definitions.map(_.identity.print) shouldBe Vector(
        _tool_decimal,
        _tool_time
      )
      val decimal = catalog.definition(_identity(_tool_decimal)).getOrElse(
        fail("decimal tool definition is missing")
      )
      decimal.inputSchema.fields.map(x => x.name -> x.required) shouldBe Vector(
        "left" -> true,
        "operator" -> true,
        "right" -> true
      )
    }

    "reject an admitted identity that is unavailable in the assembled subsystem" in {
      Given("an admission naming an Operation absent from the runtime")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("operation-tool-unavailable"))
      val admission = _admission(Vector(_identity("org.goldenport.cncf.Missing.service.operation")))

      When("catalog construction resolves exact runtime routes")
      val result = OperationToolCatalogBuilder.createC(subsystem, admission)

      Then("construction fails instead of silently dropping or normalizing the identity")
      result.isSuccess shouldBe false
      result.display should include("admitted Operation tool is unavailable")
    }

    "reject a runtime Operation whose names cannot form one exact identity" in {
      Given("an assembled Operation and an unqualified component name")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("operation-tool-invalid-identity"))
      val component = subsystem.findComponent(BuiltinComponentIdentity.TOOL).getOrElse(fail("tool component is unavailable"))
      val service = component.protocol.services.services
        .find(_.name == "time").getOrElse(fail("time service is unavailable"))
      val operation = service.operations.operations.toVector
        .find(_.name == "now").getOrElse(fail("now operation is unavailable"))

      When("the provider-neutral definition builder validates the runtime names")
      val result = OperationToolDefinitionBuilder.definitionC(
        componentName = "tool",
        service = service,
        operation = operation
      )

      Then("definition construction fails structurally rather than publishing an ambiguous name")
      result.isSuccess shouldBe false
      result.display should include("component.identity.id.qualified")
    }

    "install one exact runtime-owned tool set into a consumer socket" in {
      Given("a consumer socket requiring one admitted internal tool set")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("operation-tool-socket"))
      val admission = _admission(Vector(_identity(_tool_time)))
      val registry = _success(OperationToolRuntimeRegistry.createC(subsystem, Vector(admission)))
      val socket = _success(OperationToolSocket.createC(Vector(
        OperationToolRequirement(admission.toolSetId)
      )))
      val consumer = new org.goldenport.cncf.component.Component() {}
        .withPort(org.goldenport.cncf.component.Component.Port.input(socket))

      When("runtime assembly installs all socket requirements atomically")
      val installed = registry.install(consumer)

      Then("the consumer can resolve only the runtime-owned admitted service")
      installed.isSuccess shouldBe true
      socket.isInstalled shouldBe true
      _success(socket.service(admission.toolSetId)).toolSetId shouldBe admission.toolSetId
    }
  }

  "Internal Operation tool invocation" should {
    "execute through the Subsystem with the caller ExecutionContext" in {
      Given("an admitted runtime-clock Operation and a deterministic caller clock")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("operation-tool-execution"))
      val admission = _admission(Vector(_identity(_tool_time)))
      val registry = _success(OperationToolRuntimeRegistry.createC(subsystem, Vector(admission)))
      val service = _success(registry.resolve(admission.toolSetId))
      val expectedinstant = Instant.parse("2026-07-21T08:30:00Z")
      val context = ExecutionContext.withFrameworkCallTreeEnabled(
        ExecutionContext.create(Clock.fixed(expectedinstant, ZoneOffset.UTC)),
        enabled = true
      )
      val actioncount = RuntimeDashboardMetrics.actionCallSnapshot.summary.cumulative.total
      given ExecutionContext = context

      When("the admitted tool is invoked in-process")
      val result = service.withInvocation { invocation =>
        invocation.invoke(OperationToolCall(_identity(_tool_time), Record.empty))
      }

      Then("the normal Operation result observes the caller context")
      val toolresult = _success(result)
      toolresult.response match {
        case OperationResponse.RecordResponse(record) =>
          record.getString("instant") shouldBe Some("2026-07-21T08:30:00Z")
        case other => fail(s"unexpected Operation response: ${other.getClass.getName}")
      }
      context.observability.callTreeContext.build() should not be empty
      RuntimeDashboardMetrics.actionCallSnapshot.summary.cumulative.total should be > actioncount
    }

    "reject unknown and malformed calls before business Operation execution" in {
      Given("one admitted Operation with required typed parameters")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("operation-tool-denial"))
      val admission = _admission(Vector(_identity(_tool_decimal)))
      val service = _success(OperationToolRuntimeRegistry.createC(subsystem, Vector(admission)))
        .resolve(admission.toolSetId).toOption.getOrElse(fail("tool service is unavailable"))
      given ExecutionContext = ExecutionContext.create()

      When("an unadmitted identity and a missing-required-field call are submitted")
      val unknown = service.withInvocation(_.invoke(
        OperationToolCall(_identity(_tool_time), Record.empty)
      ))
      val malformed = service.withInvocation(_.invoke(
        OperationToolCall(_identity(_tool_decimal), Record.empty)
      ))

      Then("both calls fail at the internal admission or request boundary")
      unknown.isSuccess shouldBe false
      unknown.display should include("Operation tool not admitted")
      malformed.isSuccess shouldBe false
      malformed.display should include("required operation parameter")
    }

    "preserve normal Subsystem operation authorization" in {
      Given("an admitted internal tool denied by the subsystem authorization descriptor")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("operation-tool-authorization"))
      subsystem.withDescriptor(GenericSubsystemDescriptor(
        path = Path.of("<operation-tool-authorization>"),
        subsystemName = subsystem.name,
        operationAuthorization = Map(
          _tool_time -> OperationAuthorizationRule(deny = true)
        )
      ))
      val admission = _admission(Vector(_identity(_tool_time)))
      val service = _success(OperationToolRuntimeRegistry.createC(subsystem, Vector(admission)))
        .resolve(admission.toolSetId).toOption.getOrElse(fail("tool service is unavailable"))
      given ExecutionContext = ExecutionContext.create()

      When("the admitted identity is invoked in-process")
      val result = service.withInvocation(_.invoke(
        OperationToolCall(_identity(_tool_time), Record.empty)
      ))

      Then("the canonical authorization failure is returned before ActionCall execution")
      result.isSuccess shouldBe false
      result match {
        case Consequence.Failure(conclusion) => conclusion.status.webCode.code shouldBe 403
        case _ => fail("authorization denial was not returned")
      }
      result.display should include(BuiltinComponentIdentity.TOOL.name + ".time.now")
    }

    "enforce invocation input and call-count limits before dispatch" in {
      Given("an admitted Operation with a one-call invocation budget")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("operation-tool-limits"))
      val strictlimits = _success(OperationToolLimits.createC(
        maximumCalls = 1,
        maximumInputBytes = 1024L,
        maximumResultBytes = 65536L,
        maximumConcurrency = 1
      ))
      val admission = _success(OperationToolAdmission.createC(
        toolSetId = _tool_set_id,
        identities = Vector(_identity(_tool_time)),
        limits = strictlimits
      ))
      val service = _success(OperationToolRuntimeRegistry.createC(subsystem, Vector(admission)))
        .resolve(admission.toolSetId).toOption.getOrElse(fail("tool service is unavailable"))
      given ExecutionContext = ExecutionContext.create()

      When("two calls are attempted in one invocation")
      val results = service.withInvocation { invocation =>
        val first = invocation.invoke(OperationToolCall(_identity(_tool_time), Record.empty))
        val second = invocation.invoke(OperationToolCall(_identity(_tool_time), Record.empty))
        Consequence.success(first -> second)
      }

      Then("the first executes and the second is rejected by the structured limit")
      val (first, second) = _success(results)
      first.isSuccess shouldBe true
      second.isSuccess shouldBe false
      second.display should include("Operation tool invocation limit exceeded")
    }

    "enforce input size before dispatch and result size before boundary return" in {
      Given("separate tool sets with byte ceilings smaller than the encoded request or result")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("operation-tool-byte-limits"))
      val inputset = _success(OperationToolSetId.parseC("input-limited"))
      val resultset = _success(OperationToolSetId.parseC("result-limited"))
      val identity = _identity(_tool_time)
      val inputadmission = _success(OperationToolAdmission.createC(
        toolSetId = inputset,
        identities = Vector(identity),
        limits = _success(OperationToolLimits.createC(
          maximumCalls = 1,
          maximumInputBytes = 1L,
          maximumResultBytes = 65536L,
          maximumConcurrency = 1
        ))
      ))
      val resultadmission = _success(OperationToolAdmission.createC(
        toolSetId = resultset,
        identities = Vector(identity),
        limits = _success(OperationToolLimits.createC(
          maximumCalls = 1,
          maximumInputBytes = 1024L,
          maximumResultBytes = 1L,
          maximumConcurrency = 1
        ))
      ))
      val registry = _success(OperationToolRuntimeRegistry.createC(
        subsystem,
        Vector(inputadmission, resultadmission)
      ))
      given ExecutionContext = ExecutionContext.create()

      When("the same deterministic Operation is called through both admitted tool sets")
      val inputresult = _success(registry.resolve(inputset)).withInvocation(
        _.invoke(OperationToolCall(identity, Record.empty))
      )
      val outputresult = _success(registry.resolve(resultset)).withInvocation(
        _.invoke(OperationToolCall(identity, Record.empty))
      )

      Then("both byte ceilings fail structurally at their respective boundaries")
      inputresult.isSuccess shouldBe false
      inputresult.display should include("Operation tool invocation limit exceeded")
      outputresult.isSuccess shouldBe false
      outputresult.display should include("Operation tool invocation limit exceeded")
    }
  }

  private def _admission(
    identities: Vector[OperationToolIdentity]
  ): OperationToolAdmission = {
    val limits = _success(OperationToolLimits.createC(
      maximumCalls = 4,
      maximumInputBytes = 65536L,
      maximumResultBytes = 65536L,
      maximumConcurrency = 1
    ))
    _success(OperationToolAdmission.createC(
      toolSetId = _tool_set_id,
      identities = identities,
      limits = limits
    ))
  }

  private def _tool_set_id: OperationToolSetId =
    _success(OperationToolSetId.parseC("builtin-tools"))

  private def _identity(value: String): OperationToolIdentity =
    _success(OperationToolIdentity.parseC(value))

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.display)
    }
}
