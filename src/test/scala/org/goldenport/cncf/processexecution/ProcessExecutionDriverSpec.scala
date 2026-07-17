package org.goldenport.cncf.processexecution

import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 34 Process Execution driver resolution
 * and deterministic test fixtures.
 *
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ProcessExecutionDriverSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _scope_names =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(20))

  "Process Execution driver resolution" should {
    "inherit an explicit runtime driver through generated child scopes" in {
      Given("generated child scope names and one explicit deterministic runtime driver")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val profile = ProcessExecutionTestProfile(Map(capability -> _result(capability, exitcode = 0)))
      val runtime = _scope("runtime", None, Some(profile.driver))

      When("component child scopes resolve their Process Execution driver")
      val property = Prop.forAll(_scope_names) { name =>
        val child = runtime.createChildScope(ScopeKind.Component, name)
        ProcessExecutionDriver.resolveC(child).toOption.contains(profile.driver)
      }

      Then("the runtime-owned driver is selected without a host fallback")
      Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property).passed shouldBe true
    }

    "let a child scope override the inherited driver for an executable specification" in {
      Given("a runtime driver and an explicitly different component test profile")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val runtimeprofile = ProcessExecutionTestProfile(Map(capability -> _result(capability, exitcode = 0)))
      val componentprofile = ProcessExecutionTestProfile(Map(capability -> _result(capability, exitcode = 9)))
      val runtime = _scope("runtime", None, Some(runtimeprofile.driver))
      val component = _scope("component", Some(runtime), Some(componentprofile.driver))

      When("the component resolves the Process Execution driver")
      val resolved = ProcessExecutionDriver.resolveC(component)

      Then("the explicit child override wins over inherited runtime configuration")
      resolved.toOption shouldBe Some(componentprofile.driver)
      resolved.toOption.map(_.safeIdentity) shouldBe Some("deterministic-test")
    }

    "inherit runtime-installed admission through a component scope" in {
      Given("an admitted codex-cli capability installed on the runtime scope")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val fixture = ProcessExecutionTestProfile.admittedC(capability, _result(capability, exitcode = 0)).toOption.get
      val runtime = _scope("runtime", None, Some(fixture.profile.driver), Some(fixture.admission))
      val component = runtime.createChildScope(ScopeKind.Component, "component")

      When("a component provider resolves its logical Process Execution request")
      val resolved = ProcessExecutionAdmission.resolveC(component, ProcessExecutionRequest(capability))

      Then("the inherited runtime admission produces the configured resolved intent")
      resolved.toOption shouldBe Some(fixture.execution)
    }

    "return a structured unavailable-service failure when no scope provides a driver" in {
      Given("a scope tree with no Process Execution driver")
      val runtime = _scope("runtime", None, None)
      val component = runtime.createChildScope(ScopeKind.Component, "component")

      When("the Process Execution runtime attempts driver resolution")
      val resolved = ProcessExecutionDriver.resolveC(component)

      Then("the absence is deterministic and never selects an ambient host process")
      resolved.isFaillure shouldBe true
    }

    "execute deterministic test results and model cancellation without a host process" in {
      Given("an admitted execution and a configured deterministic test profile")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val profile = ProcessExecutionTestProfile(Map(capability -> _result(capability, exitcode = 0)))
      val scope = _scope("runtime", None, Some(profile.driver))
      val execution = _execution(capability)

      When("the resolved driver starts the admitted execution and its handle is cancelled")
      val result = for {
        driver <- ProcessExecutionDriver.resolveC(scope)
        handle <- driver.startC(execution)
        _ <- handle.cancelC
        completed <- handle.awaitC
      } yield completed

      Then("the fixture records only the resolved intent and returns a cancellation terminal result")
      result.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.Cancelled)
      profile.driver.executions shouldBe Vector(execution)
    }
  }

  private def _scope(
    name: String,
    parent: Option[ScopeContext],
    driver: Option[ProcessExecutionDriver],
    admission: Option[ProcessExecutionAdmission] = None
  ): ScopeContext =
    ScopeContext(
      kind = ScopeKind.Runtime,
      name = name,
      parent = parent,
      observabilityContext = ExecutionContext.create().observability,
      processExecutionDriverOption = driver,
      processExecutionAdmissionOption = admission
    )

  private def _execution(capability: ProcessCapabilityId): ResolvedProcessExecution = {
    val definition = ProcessProgramDefinition.fromRuntimeC(
      capability,
      "codex-cli",
      "/opt/cncf/bin/codex",
      Vector("exec"),
      ProcessArgumentPolicy(Vector("exec"), Set.empty),
      _limits,
      Set.empty
    ).toOption.get
    val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
    policy.resolveC(ProcessExecutionRequest(capability), ProcessExecutionGrant(capability)).toOption.get
  }

  private def _result(
    capability: ProcessCapabilityId,
    exitcode: Int
  ): ProcessExecutionResult = {
    val capture = ProcessExecutionCapture(Vector.empty, 0L, truncated = false)
    ProcessExecutionResult(
      ProcessExecutionTermination.Exited(exitcode),
      capture,
      capture,
      Vector.empty,
      elapsedMillis = 1L,
      safeProgramIdentity = capability.print
    )
  }

  private def _limits: ProcessExecutionLimits =
    ProcessExecutionLimits(
      Some(100L),
      Some(100L),
      Some(100L),
      Some(100L),
      Some(100L),
      Some(100L),
      Some(100L),
      Some(100L),
      Some(100L),
      Some(100L),
      Some(100L)
    )
}
