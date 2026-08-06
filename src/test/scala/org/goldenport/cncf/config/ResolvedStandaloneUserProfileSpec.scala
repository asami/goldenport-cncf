package org.goldenport.cncf.config

import java.time.ZoneId
import java.util.Locale

import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationBindingCandidate, ConfigurationBindingCandidates, ConfigurationBindingResolver, ConfigurationOrigin, ConfigurationProvenance}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class ResolvedStandaloneUserProfileSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e1 = afterWord("in spec:phase-55-gcf07g-fixed-user-execution-adoption, example:E1, rules:GCF07G-R1,R2, phase:55, slice:GCF-07G")

  "Resolved standalone-user profile" should {
    "E1 resolve exact typed witnesses and reject an identity change in override history" must _e1 {
      "when fixed-user candidates share one Global target" in {
        Given("typed fixed-user candidates with both stable and changed identity histories")
        val stable = _collection(Vector(_candidate(CncfConfigurationParameterCatalog.fixedUserId, "alice", 1), _candidate(CncfConfigurationParameterCatalog.fixedUserId, "alice", 2), _candidate(CncfConfigurationParameterCatalog.fixedUserLocale, Locale.forLanguageTag("ja-JP"), 1), _candidate(CncfConfigurationParameterCatalog.fixedUserTimezone, ZoneId.of("Asia/Tokyo"), 1)))
        val changed = _collection(Vector(_candidate(CncfConfigurationParameterCatalog.fixedUserId, "alice", 1), _candidate(CncfConfigurationParameterCatalog.fixedUserId, "bob", 2)))

        When("the profile uses only exact catalog witnesses")
        val stableprofile = ResolvedStandaloneUserProfile.resolve(stable)
        val changedprofile = ResolvedStandaloneUserProfile.resolve(changed)

        Then("stable identity resolves with typed formatting while changed identity fails without revealing either value")
        stableprofile.toOption.map(_.id) shouldBe Some("alice")
        stableprofile.toOption.flatMap(_.locale) shouldBe Some(Locale.forLanguageTag("ja-JP"))
        stableprofile.toOption.flatMap(_.timezone) shouldBe Some(ZoneId.of("Asia/Tokyo"))
        changedprofile shouldBe a[Consequence.Failure[_]]
        changedprofile.display should include ("explicit data migration")
        changedprofile.display should include ("isolated datastore")
        changedprofile.display should include ("silent data reuse is not admitted")
        changedprofile.display.contains("alice") shouldBe false
        changedprofile.display.contains("bob") shouldBe false
        changedprofile.display.contains("profile-1") shouldBe false
        changedprofile.display.contains("profile-2") shouldBe false
        changedprofile.display.contains("standalone-local") shouldBe false
      }
    }
  }

  private def _collection(candidates: Vector[ConfigurationBindingCandidate[?, CncfConfigurationTarget]]) =
    ConfigurationBindingResolver.resolve(ConfigurationBindingCandidates.from(candidates).getOrElse(fail("candidates")), CncfConfigurationResolutionContext.forSubsystem(SubsystemInstanceId.create("platform", "default").getOrElse(fail("identity"))).getOrElse(fail("context")).generic).getOrElse(fail("collection"))

  private def _candidate[A](parameter: org.goldenport.configuration.ConfigurationParameter[A], value: A, rank: Int): ConfigurationBindingCandidate[A, CncfConfigurationTarget] = {
    val target: CncfConfigurationTarget = CncfConfigurationTarget.Global
    ConfigurationBindingCandidate.create(parameter, target, value, ConfigurationProvenance.create(ConfigurationOrigin.Home, "textus", s"profile-$rank", Some(parameter.id.value), Some(parameter.id.value), rank, rank, Vector("phase-55: gcf07g"), false, Some("file")).getOrElse(fail("provenance"))).getOrElse(fail("candidate"))
  }
}
