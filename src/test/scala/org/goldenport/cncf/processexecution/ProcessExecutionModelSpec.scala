package org.goldenport.cncf.processexecution

import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for Phase 34 Process Execution capability admission
 * and runtime-owned program-policy models.
 *
 * @since   Jul. 17, 2026
 * @version Jul. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class ProcessExecutionModelSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _capabilities =
    Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(24))

  "Process Execution capability model" should {
    "canonicalize valid capability names while rejecting unsafe logical identifiers" in {
      Given("generated lower-case capability names and invalid capability input")
      val property = Prop.forAll(_capabilities) { value =>
        ProcessCapabilityId.parseC(value.toUpperCase).toOption.exists(_.print == value)
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)
      val invalid = Vector("", "../shell", "shell_exec", "shell command", "A/B")

      When("the capability parser receives the values")
      val rejected = invalid.map(ProcessCapabilityId.parseC)

      Then("only canonical logical capability identifiers are admitted")
      checked.passed shouldBe true
      rejected.forall(_.isFaillure) shouldBe true
    }

    "accept only safe WorkArea-relative paths and declared artifact metadata" in {
      Given("a safe output declaration and invalid host-oriented paths")
      val name = ProcessArtifactName.parseC("result-json").toOption.get
      val path = WorkAreaRelativePath.parseC("output/result.json").toOption.get
      val output = ProcessExecutionOutputDeclaration.createC(name, path, ProcessArtifactKind.File, 128L)
      val invalid = Vector("/tmp/result", "../result", "output/../../result", "output\\result", "")

      When("the paths are parsed for a Process Execution request")
      val rejected = invalid.map(WorkAreaRelativePath.parseC)
      val display = rejected.head match {
        case Consequence.Failure(conclusion) => conclusion.display
        case _ => ""
      }

      Then("the model retains only declared WorkArea-confined output intent")
      output.toOption.map(_.path.print) shouldBe Some("output/result.json")
      rejected.forall(_.isFaillure) shouldBe true
      display should not include "/tmp/result"
    }

    "resolve only a granted registered capability before any driver exists" in {
      Given("one registered runtime program definition and a matching request")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val definition = _definition(capability)
      val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
      val request = ProcessExecutionRequest(capability, arguments = Vector("--json"))
      val grant = ProcessExecutionGrant(capability)
      val unknown = ProcessCapabilityId.parseC("unknown-cli").toOption.get

      When("the policy resolves matching, unknown, and ungranted requests")
      val resolved = policy.resolveC(request, grant)
      val missing = policy.resolveC(request.copy(capability = unknown), ProcessExecutionGrant(unknown))
      val denied = policy.resolveC(request, ProcessExecutionGrant(unknown))

      Then("only the matching capability reaches a resolved execution intent")
      resolved.toOption.map(_.definition.safeProgramIdentity) shouldBe Some("codex-cli")
      resolved.toOption.map(_.effectiveArguments) shouldBe Some(Vector("exec", "--json"))
      missing.isFaillure shouldBe true
      denied.isFaillure shouldBe true
    }

    "tighten runtime limits monotonically and reject widening overrides" in {
      Given("finite runtime limits with tighter grant and request limits")
      val maximum = _limits(100L)
      val grant = _limits(90L)
      val request = _limits(80L)

      When("the effective policy is resolved")
      val effective = ProcessExecutionLimits.effectiveC(maximum, grant, request)
      val widening = ProcessExecutionLimits.effectiveC(maximum, ProcessExecutionLimits.empty, _limits(101L))

      Then("the strictest limits survive and widening is rejected")
      effective.toOption.flatMap(_.stdoutBytes) shouldBe Some(80L)
      effective.toOption.flatMap(_.workAreaBytes) shouldBe Some(80L)
      widening.isFaillure shouldBe true
    }

    "apply the effective grant limit to input and declared artifacts before execution" in {
      Given("a registered output with a grant tighter than the runtime maximum")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val name = ProcessArtifactName.parseC("result").toOption.get
      val path = WorkAreaRelativePath.parseC("output/result.json").toOption.get
      val output = ProcessExecutionOutputDeclaration.createC(name, path, ProcessArtifactKind.File, 11L).toOption.get
      val definition = _definition(capability, Set(name))
      val policy = ProcessExecutionPolicy.createC(Vector(definition)).toOption.get
      val grant = ProcessExecutionGrant(capability, _limits(10L))
      val input = ProcessExecutionInput.Bytes(Vector.fill(11)(0.toByte))

      When("the request exceeds the grant through input or declared output size")
      val inputrejected = policy.resolveC(ProcessExecutionRequest(capability, input = input), grant)
      val outputrejected = policy.resolveC(ProcessExecutionRequest(capability, outputs = Vector(output)), grant)

      Then("the policy rejects both requests before a driver is selected")
      inputrejected.isFaillure shouldBe true
      outputrejected.isFaillure shouldBe true
    }

    "reject unsafe runtime metadata without reflecting raw executable locations" in {
      Given("a malformed safe program identity and a raw host executable location")
      val capability = ProcessCapabilityId.parseC("codex-cli").toOption.get
      val rawpath = "/private/runtime/credentials/codex"

      When("the runtime program definition is constructed")
      val rejected = ProcessProgramDefinition.fromRuntimeC(
        capability,
        rawpath,
        rawpath,
        Vector("exec"),
        ProcessArgumentPolicy(Vector("exec"), Set.empty),
        _limits(100L),
        Set.empty
      )
      val display = rejected match {
        case Consequence.Failure(conclusion) => conclusion.display
        case _ => ""
      }

      Then("only a safe logical identity is accepted and diagnostics omit the raw path")
      rejected.isFaillure shouldBe true
      display should not include rawpath
    }

    "keep executable and shell selection outside the component request shape" in {
      Given("the public Process Execution request type")
      val fields = classOf[ProcessExecutionRequest].getDeclaredFields.map(_.getName).toSet

      When("the caller-visible request fields are inspected")
      val forbidden = Set("executable", "command", "shell", "environment")

      Then("the request expresses a capability and never raw host execution")
      fields should contain ("capability")
      fields.intersect(forbidden) shouldBe empty
    }
  }

  private def _definition(
    capability: ProcessCapabilityId,
    artifacts: Set[ProcessArtifactName] = Set.empty
  ): ProcessProgramDefinition =
    ProcessProgramDefinition.fromRuntimeC(
      capability,
      "codex-cli",
      "/opt/cncf/bin/codex",
      Vector("exec"),
      ProcessArgumentPolicy(Vector("exec"), Set("--json")),
      _limits(100L),
      artifacts,
      allowsworkingdirectory = false
    ).toOption.get

  private def _limits(value: Long): ProcessExecutionLimits =
    ProcessExecutionLimits(
      Some(value),
      Some(value),
      Some(value),
      Some(value),
      Some(value),
      Some(value),
      Some(value),
      Some(value),
      Some(value),
      Some(value),
      Some(value)
    )
}
