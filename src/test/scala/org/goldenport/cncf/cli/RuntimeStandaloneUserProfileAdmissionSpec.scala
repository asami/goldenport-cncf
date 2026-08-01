package org.goldenport.cncf.cli

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemDescriptor, GenericSubsystemLocalSubjectBinding, GenericSubsystemSecurityBinding, Subsystem, SubsystemExecutionProfile, SubsystemUserMode}
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
    afterWord(s"in spec:phase-53-pm-53-01-runtime-admission, example:$example, rules:$rules, phase:53, slice:PM-53-01")

  "Runtime standalone-user profile admission" should {
    "E1 resolve the canonical Subsystem user mode before fixed-user HOME admission" must _metadata("E1", "PM53-R1") {
    "when server admission receives canonical standalone execution" in {
      Given("a descriptor-owned Subsystem with canonical standalone user-mode admission")
      val subsystem = _subsystem(Map(SubsystemUserMode.CONFIGURATION_KEY -> "standalone"))
      var observed = Vector.empty[SubsystemExecutionProfile]

      When("server admission is evaluated")
      val result = RuntimeStandaloneUserProfileAdmission.admit(
        subsystem,
        serverExecution = true,
        profile => {
          observed :+= profile
          Consequence.success(Vector.empty)
        },
        _ => Consequence.success(SubsystemExecutionProfile.Fixed)
      )

      Then("only the mode-free fixed profile reaches the HOME admission seam")
      result shouldBe a[Consequence.Success[_]]
      observed shouldBe Vector(SubsystemExecutionProfile.Fixed)
    }
    }

    "E2 exclude authenticated and controlled execution from HOME admission" must _metadata("E2", "PM53-R2") {
    "when server admission receives multi-user and controlled execution" in {
      Given("multi-user Subsystem admission and a controlled test profile")
      val multiuser = _subsystem(Map(SubsystemUserMode.CONFIGURATION_KEY -> "multi-user"))
      val controlled = _subsystem(Map(SubsystemUserMode.CONFIGURATION_KEY -> "standalone"))
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

    "E3 fail closed when fixed admission is malformed or lacks stable identity" must _metadata("E3", "PM53-R3") {
    "when fixed server admission has invalid profile or identity evidence" in {
      Given("a standalone Subsystem server and failing fixed-profile admission")
      val identified = _subsystem(Map(SubsystemUserMode.CONFIGURATION_KEY -> "standalone"))
      val unidentified = Subsystem("unidentified", configuration = _configuration(Map(SubsystemUserMode.CONFIGURATION_KEY -> "standalone")))

      When("profile admission or stable identity validation fails")
      val malformed = RuntimeStandaloneUserProfileAdmission.admit(
        identified,
        serverExecution = true,
        _ => Consequence.configurationInvalid("malformed StandaloneUserProfile")
      )
      val missingidentity = RuntimeStandaloneUserProfileAdmission.admit(
        unidentified,
        serverExecution = true,
        _ => Consequence.success(Vector.empty),
        _ => Consequence.success(SubsystemExecutionProfile.Fixed)
      )

      Then("the runtime seam returns the failures without fallback")
      malformed shouldBe a[Consequence.Failure[_]]
      missingidentity shouldBe a[Consequence.Failure[_]]
    }
    }
  }

  private def _subsystem(values: Map[String, String]): Subsystem =
    val mode = values.get(SubsystemUserMode.CONFIGURATION_KEY)
    val authentication = mode match {
      case Some("multi-user") => GenericSubsystemAuthenticationBinding(
        providers = Vector(GenericSubsystemAuthenticationProviderBinding(
          name = "runtime-admission",
          component = "runtime-admission",
          enabled = Some(true)
        ))
      )
      case _ => GenericSubsystemAuthenticationBinding(
        localSubject = Some(GenericSubsystemLocalSubjectBinding("runtime-admission"))
      )
    }
    Subsystem("runtime-admission", configuration = _configuration(values))
      .withDescriptor(
        GenericSubsystemDescriptor(
          path = Path.of("runtime-admission.car"),
          subsystemName = "runtime-admission",
          security = Some(GenericSubsystemSecurityBinding(authentication = Some(authentication)))
        )
      )

  private def _configuration(values: Map[String, String]): ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(values.view.mapValues(ConfigurationValue.StringValue(_)).toMap),
      ConfigurationTrace.empty
    )
}
