package org.goldenport.cncf.processexecution

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 36 provider-neutral external-tool
 * integration through the deterministic Process Execution runtime fixture.
 *
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ProcessExecutionProviderAdapterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _nonzero_exit_codes = Gen.choose(1, 255)

  "Provider-neutral Process Execution adapter" should {
    "admit fixed command templates, arguments, environment, bounded inputs, and declared outputs before a fake driver starts" in {
      Given("a runtime Process capability with fixed command and environment policy")
      val capability = ProcessCapabilityId.parseC("external-tool").toOption.get
      val schema = ProcessArtifactName.parseC("schema").toOption.get
      val outputname = ProcessArtifactName.parseC("result").toOption.get
      val inputpath = WorkAreaRelativePath.parseC("input/schema.json").toOption.get
      val outputpath = WorkAreaRelativePath.parseC("output/result.json").toOption.get
      val input = ProcessExecutionInputFile.createC(schema, inputpath, Vector(1.toByte), 8L).toOption.get
      val output = ProcessExecutionOutputDeclaration.createC(outputname, outputpath, ProcessArtifactKind.File, 8L).toOption.get
      val definition = _definition(capability, schema, outputname)
      val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
      val admission = ProcessExecutionAdmission.createC(policy, Vector(ProcessExecutionGrant(capability))).toOption.get
      val profile = ProcessExecutionTestProfile(Map(capability -> _result(capability, ProcessExecutionTermination.Exited(0))))

      When("a provider submits admitted logical request data and an unapproved argument")
      val admitted = admission.admitC(
        ProcessExecutionRequest(
          capability,
          arguments = Vector("--strict"),
          inputFiles = Vector(input),
          outputs = Vector(output)
        )
      )
      val rejected = admission.admitC(ProcessExecutionRequest(capability, arguments = Vector("--unsafe")))
      val completed = admitted.flatMap { execution =>
        for {
          handle <- profile.driver.startC(execution)
          result <- handle.awaitC
        } yield result
      }

      Then("only the resolved fixed vector and runtime-owned environment reach the fake driver")
      admitted.toOption.map(_.effectiveArguments) shouldBe Some(Vector("run", "--json", "--strict"))
      admitted.toOption.map(_.definition._environment.safeNames) shouldBe Some(Vector("TOOL_MODE"))
      admitted.toOption.map(_.request.inputFiles.map(_.name.print)) shouldBe Some(Vector("schema"))
      admitted.toOption.map(_.request.outputs.map(_.name.print)) shouldBe Some(Vector("result"))
      profile.driver.executions.map(_.effectiveArguments) shouldBe Vector(Vector("run", "--json", "--strict"))
      profile.driver.executions.map(_.definition._environment.safeNames) shouldBe Vector(Vector("TOOL_MODE"))
      completed.toOption.map(_.termination) shouldBe Some(ProcessExecutionTermination.Exited(0))
      rejected.isFaillure shouldBe true
    }

    "preserve every terminal Process outcome until the provider adapter explicitly converts it" in {
      Given("configured fake Process results for every framework terminal category")
      val capability = ProcessCapabilityId.parseC("external-tool").toOption.get
      val outcomes = Vector[ProcessExecutionTermination](
        ProcessExecutionTermination.Exited(0),
        ProcessExecutionTermination.Exited(17),
        ProcessExecutionTermination.LaunchFailed,
        ProcessExecutionTermination.TimedOut,
        ProcessExecutionTermination.Cancelled,
        ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout),
        ProcessExecutionTermination.ArtifactLimitExceeded
      )

      When("the deterministic driver returns each terminal result to a provider-local adapter")
      val converted = outcomes.map { termination =>
        val profile = ProcessExecutionTestProfile(Map(capability -> _result(capability, termination)))
        val result = for {
          handle <- profile.driver.startC(_resolved(capability))
          completed <- handle.awaitC
        } yield _ProviderResultAdapter.convert(completed)
        result.toOption
      }

      Then("the adapter receives distinct lifecycle values rather than collapsed framework failures")
      converted shouldBe Vector(
        Some(_ProviderResult.Success),
        Some(_ProviderResult.NonZeroExit(17)),
        Some(_ProviderResult.LaunchFailure),
        Some(_ProviderResult.Timeout),
        Some(_ProviderResult.Cancelled),
        Some(_ProviderResult.OutputLimit(ProcessExecutionStream.Stdout)),
        Some(_ProviderResult.ArtifactLimit)
      )
    }

    "retain non-zero exit code interpretation as provider-local policy" in {
      Given("generated non-zero Process exit results from a deterministic fake driver")
      val capability = ProcessCapabilityId.parseC("external-tool").toOption.get
      val property = Prop.forAll(_nonzero_exit_codes) { exitcode =>
        val profile = ProcessExecutionTestProfile(Map(
          capability -> _result(capability, ProcessExecutionTermination.Exited(exitcode))
        ))
        val converted = for {
          handle <- profile.driver.startC(_resolved(capability))
          completed <- handle.awaitC
        } yield _ProviderResultAdapter.convert(completed)
        converted.toOption.contains(_ProviderResult.NonZeroExit(exitcode))
      }

      When("each fake terminal result is converted by provider-local policy")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("CNCF preserves the exit result without treating it as an admission or runtime failure")
      checked.passed shouldBe true
    }
  }

  private def _definition(
    capability: ProcessCapabilityId,
    schema: ProcessArtifactName,
    output: ProcessArtifactName
  ): ProcessProgramDefinition =
    ProcessProgramDefinition.fromRuntimeC(
      capability,
      "external-tool-test",
      "runtime-owned-test-location",
      Vector("run", "--json"),
      ProcessArgumentPolicy(Vector("run", "--json"), Set("--strict")),
      _limits,
      Set(output),
      allowedinputfiles = Set(schema),
      environment = Map("TOOL_MODE" -> "runtime")
    ).toOption.get

  private def _resolved(capability: ProcessCapabilityId): ResolvedProcessExecution = {
    val definition = ProcessProgramDefinition.fromRuntimeC(
      capability,
      "external-tool-test",
      "runtime-owned-test-location",
      Vector.empty,
      ProcessArgumentPolicy(Vector.empty, Set.empty),
      _limits,
      Set.empty
    ).toOption.get
    ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
      .resolveC(ProcessExecutionRequest(capability), ProcessExecutionGrant(capability)).toOption.get
  }

  private def _result(
    capability: ProcessCapabilityId,
    termination: ProcessExecutionTermination
  ): ProcessExecutionResult = {
    val capture = ProcessExecutionCapture(Vector.empty, 0L, truncated = false)
    ProcessExecutionResult(termination, capture, capture, Vector.empty, 1L, capability.print)
  }

  private val _limits = ProcessExecutionLimits(
    Some(100L), Some(100L), Some(100L), Some(100L), Some(100L), Some(100L),
    Some(100L), Some(100L), Some(100L), Some(100L), Some(100L)
  )
}

private enum _ProviderResult {
  case Success
  case NonZeroExit(exitCode: Int)
  case LaunchFailure
  case Timeout
  case Cancelled
  case OutputLimit(stream: ProcessExecutionStream)
  case ArtifactLimit
}

private object _ProviderResultAdapter {
  def convert(result: ProcessExecutionResult): _ProviderResult =
    result.termination match {
      case ProcessExecutionTermination.Exited(0) => _ProviderResult.Success
      case ProcessExecutionTermination.Exited(exitcode) => _ProviderResult.NonZeroExit(exitcode)
      case ProcessExecutionTermination.LaunchFailed => _ProviderResult.LaunchFailure
      case ProcessExecutionTermination.TimedOut => _ProviderResult.Timeout
      case ProcessExecutionTermination.Cancelled => _ProviderResult.Cancelled
      case ProcessExecutionTermination.OutputLimitExceeded(stream) => _ProviderResult.OutputLimit(stream)
      case ProcessExecutionTermination.ArtifactLimitExceeded => _ProviderResult.ArtifactLimit
    }
}
