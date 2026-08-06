package org.goldenport.cncf.cli

import java.nio.file.{Files, Path}

import org.goldenport.Consequence
import org.goldenport.cncf.config.{StandaloneUserProfile, StandaloneUserProfileResolver}
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemDescriptor, GenericSubsystemLocalSubjectBinding, GenericSubsystemSecurityBinding, Subsystem}
import org.goldenport.configuration.{Configuration, ConfigurationOrigin, ConfigurationTrace, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  5, 2026
 * @version Aug.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class FixedUserIdentitySafetyRuntimeSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:phase-55-fixed-user-runtime-safety, example:$example, rules:$rules, phase:55, slice:GCF-07G")

  "Fixed-user runtime identity safety" should {
    "E1 prefer the resolved standalone profile identity to the descriptor local-subject fallback" must _metadata("E1", "GCF07G-R1") {
      "when one canonical Textus HOME profile is admitted for a standalone Subsystem" in {
        Given("a descriptor fallback identity that differs from the admitted HOME profile identity")
        val cwd = _runtime_cwd()
        val subsystem = _subsystem("descriptor-local-subject")

        When("runtime snapshot admission resolves the final typed binding collection")
        val result = new CncfRuntime()._admit_runtime_configuration_snapshot(
          _snapshot(cwd),
          subsystem,
          _ => Consequence.success(Vector(_profile(cwd, StandaloneUserProfileResolver.Layer.TextusHome, "profile-fixed-user")))
        )

        Then("the resolved HOME profile identity wins over the lower-authority descriptor fallback")
        result shouldBe a[Consequence.Success[_]]
        subsystem.resolvedStandaloneUserProfile.map(_.id) shouldBe Some("profile-fixed-user")
        subsystem.resolvedStandaloneUserProfile.map(_.id) should not contain "descriptor-local-subject"
      }
    }

    "E2 reject conflicting HOME fixed identities before Subsystem binding or local-subject fallback" must _metadata("E2", "GCF07G-R2") {
      "when canonical Textus and CNCF HOME profiles carry different identities" in {
        Given("a standalone descriptor with a lower-authority local-subject fallback")
        val cwd = _runtime_cwd()
        val subsystem = _subsystem("descriptor-local-subject")

        When("runtime snapshot admission resolves the two admitted profile layers")
        val result = new CncfRuntime()._admit_runtime_configuration_snapshot(
          _snapshot(cwd),
          subsystem,
          _ => Consequence.success(Vector(
            _profile(cwd, StandaloneUserProfileResolver.Layer.TextusHome, "textus-fixed-user"),
            _profile(cwd, StandaloneUserProfileResolver.Layer.CncfHome, "cncf-fixed-user")
          ))
        )

        Then("admission fails value-safely before a resolved profile or fallback is installed")
        result shouldBe a[Consequence.Failure[_]]
        result.display should include ("explicit data migration")
        result.display should include ("isolated datastore")
        result.display should include ("silent data reuse is not admitted")
        result.display.contains("textus-fixed-user") shouldBe false
        result.display.contains("cncf-fixed-user") shouldBe false
        result.display.contains("descriptor-local-subject") shouldBe false
        result.display.contains("user-profile.yaml") shouldBe false
        subsystem.resolvedStandaloneUserProfile shouldBe None
      }
    }

    "E3 admit the descriptor local-subject id only when no HOME profile is admitted" must _metadata("E3", "GCF07G-R3") {
      "when a standalone Subsystem has a local subject and an empty HOME profile set" in {
        Given("a fixed standalone descriptor with no admitted HOME profile")
        val cwd = _runtime_cwd()
        val subsystem = _subsystem("descriptor-local-subject")

        When("runtime snapshot admission resolves the typed binding collection")
        val result = new CncfRuntime()._admit_runtime_configuration_snapshot(
          _snapshot(cwd),
          subsystem,
          _ => Consequence.success(Vector.empty)
        )

        Then("the descriptor local-subject id becomes the fixed-user fallback identity")
        result shouldBe a[Consequence.Success[_]]
        subsystem.resolvedStandaloneUserProfile.map(_.id) shouldBe Some("descriptor-local-subject")
      }
    }
  }

  private def _runtime_cwd(): Path = {
    val cwd = Files.createTempDirectory("gcf07g-fixed-user-runtime")
    Files.createDirectories(cwd.resolve(".textus"))
    Files.writeString(cwd.resolve(".textus/config.conf"), "textus.subsystem.user-mode = standalone\n")
    cwd
  }

  private def _snapshot(cwd: Path) =
    CncfRuntime.bootstrap(cwd, Array("command")).configurationSnapshot.getOrElse(
      fail("runtime configuration snapshot is required")
    )

  private def _subsystem(localsubject: String): Subsystem =
    Subsystem("gcf07g-fixed-user-runtime", configuration = ResolvedConfiguration(Configuration(Map.empty), ConfigurationTrace.empty))
      .withDescriptor(
        GenericSubsystemDescriptor(
          path = Path.of("gcf07g-fixed-user-runtime.car"),
          subsystemName = "gcf07g-fixed-user-runtime",
          security = Some(GenericSubsystemSecurityBinding(authentication = Some(
            GenericSubsystemAuthenticationBinding(
              localSubject = Some(GenericSubsystemLocalSubjectBinding(localsubject))
            )
          )))
        )
      )

  private def _profile(
    cwd: Path,
    layer: StandaloneUserProfileResolver.Layer,
    id: String
  ): StandaloneUserProfileResolver.Admitted =
    StandaloneUserProfileResolver.Admitted(
      layer,
      cwd.resolve("home").resolve(layer match {
        case StandaloneUserProfileResolver.Layer.TextusHome => ".textus"
        case StandaloneUserProfileResolver.Layer.CncfHome => ".cncf"
      }).resolve("user-profile.yaml"),
      ConfigurationOrigin.Home,
      StandaloneUserProfile.Document(user = Some(StandaloneUserProfile.User(id = Some(id))))
    )
}
