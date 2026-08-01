package org.goldenport.cncf.cli

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.cncf.http.WebExecutionResolutionPolicy
import org.goldenport.cncf.subsystem.{GenericSubsystemDescriptor, Subsystem, SubsystemExecutionProfile}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  1, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeStandaloneUserProfileAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:phase-53-cs05-runtime-admission, example:$example, rules:$rules, phase:53, slice:CS-05I")

  "Runtime standalone-user profile admission" should {
    "E1 resolve the canonical Web operation before fixed-user HOME admission" must _metadata("E1", "CS05I-R1") {
    "when server admission receives canonical standalone execution" in {
      Given("Spec: docs/journal/2026/07/2026-07-31-phase-53-cs05-standalone-user-configuration-admission.md; Rules: CS05I-R1; Example: E1; a descriptor-owned Subsystem with canonical standalone Web execution")
      val subsystem = _subsystem(Map(WebExecutionResolutionPolicy.APPLICATION_MODE_KEY -> "standalone"))
      var observed = Vector.empty[SubsystemExecutionProfile]

      When("server admission is evaluated")
      val result = RuntimeStandaloneUserProfileAdmission.admit(
        subsystem,
        serverExecution = true,
        profile => {
          observed :+= profile
          Consequence.success(Vector.empty)
        }
      )

      Then("only the mode-free fixed profile reaches the HOME admission seam")
      result shouldBe a[Consequence.Success[_]]
      observed shouldBe Vector(SubsystemExecutionProfile.Fixed)
    }
    }

    "E2 exclude authenticated and controlled execution from HOME admission" must _metadata("E2", "CS05I-R2") {
    "when server admission receives multi-user and controlled execution" in {
      Given("Spec: docs/journal/2026/07/2026-07-31-phase-53-cs05-standalone-user-configuration-admission.md; Rules: CS05I-R2; Example: E2; multi-user Web execution and a controlled profile")
      val multiuser = _subsystem(Map(WebExecutionResolutionPolicy.APPLICATION_MODE_KEY -> "multi-user"))
      val controlled = _subsystem(Map(WebExecutionResolutionPolicy.APPLICATION_MODE_KEY -> "standalone"))
      var calls = 0
      val admission: RuntimeStandaloneUserProfileAdmission.ProfileAdmission = _ => {
        calls += 1
        Consequence.success(Vector.empty)
      }

      When("runtime admission is evaluated")
      val authenticated = RuntimeStandaloneUserProfileAdmission.admit(multiuser, serverExecution = true, admission)
      val controlledresult = RuntimeStandaloneUserProfileAdmission.admit(
        controlled,
        serverExecution = true,
        admission,
        _ => Consequence.success(SubsystemExecutionProfile.ControlledTest)
      )

      Then("no HOME profile reader is invoked for multi-user or controlled execution")
      authenticated shouldBe a[Consequence.Success[_]]
      controlledresult shouldBe a[Consequence.Success[_]]
      calls shouldBe 0
    }
    }

    "E3 fail closed when fixed admission is malformed or lacks stable identity" must _metadata("E3", "CS05I-R3") {
    "when fixed server admission has invalid profile or identity evidence" in {
      Given("Spec: docs/journal/2026/07/2026-07-31-phase-53-cs05-standalone-user-configuration-admission.md; Rules: CS05I-R3; Example: E3; a standalone server and failing fixed-profile admission")
      val identified = _subsystem(Map(WebExecutionResolutionPolicy.APPLICATION_MODE_KEY -> "standalone"))
      val unidentified = Subsystem("unidentified", configuration = _configuration(Map(WebExecutionResolutionPolicy.APPLICATION_MODE_KEY -> "standalone")))

      When("profile admission or stable identity validation fails")
      val malformed = RuntimeStandaloneUserProfileAdmission.admit(
        identified,
        serverExecution = true,
        _ => Consequence.configurationInvalid("malformed StandaloneUserProfile")
      )
      val missingidentity = RuntimeStandaloneUserProfileAdmission.admit(
        unidentified,
        serverExecution = true,
        _ => Consequence.success(Vector.empty)
      )

      Then("the runtime seam returns the failures without fallback")
      malformed shouldBe a[Consequence.Failure[_]]
      missingidentity shouldBe a[Consequence.Failure[_]]
    }
    }
  }

  private def _subsystem(values: Map[String, String]): Subsystem =
    Subsystem("runtime-admission", configuration = _configuration(values))
      .withDescriptor(
        GenericSubsystemDescriptor(
          path = Path.of("runtime-admission.car"),
          subsystemName = "runtime-admission"
        )
      )

  private def _configuration(values: Map[String, String]): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.view.mapValues(ConfigurationValue.StringValue(_)).toMap),
      ConfigurationTrace.empty
    )
}
