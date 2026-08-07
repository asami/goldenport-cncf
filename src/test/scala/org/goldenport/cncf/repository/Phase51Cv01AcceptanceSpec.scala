package org.goldenport.cncf.repository

import org.scalacheck.{Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

final class Phase51Cv01AcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Phase 51 CV-01 version identities" should {
    "register descriptor identity mismatch" in {
      Given("requested and declared descriptor identities represented by production catalog entries")
      When("the production catalog parser and renderer retain both identities")
      val requested = _catalog_boundary("0.5.1", Some("0.5.1"), None)
      val declared = _catalog_boundary("0.5.0", Some("0.5.0"), None)
      Then("CV-04 owns runtime descriptor target validation")
      requested shouldBe true
      declared shouldBe true
      cancel("CV-04 owns descriptor identity mismatch")
    }
    "register descriptor byte or digest tampering" in {
      Given("descriptor byte and digest identities represented at the production catalog boundary")
      When("the production catalog parser and renderer retain the descriptor identity")
      val retained = _catalog_boundary("0.5.1", Some("0.5.1"), None)
      Then("CV-04 owns runtime descriptor digest validation")
      retained shouldBe true
      cancel("CV-04 owns runtime descriptor digest validation")
    }
    "register provenance and digest" in {
      Given("generation input and output identities represented by a production catalog entry")
      When("the production catalog parser and renderer retain the artifact identity")
      val retained = _catalog_boundary("0.5.1", Some("0.5.1"), None)
      Then("CV-05 owns provenance and provenance digest tampering")
      retained shouldBe true
      cancel("CV-05 owns provenance and provenance digest tampering")
    }
    "register CAR compile target vs runtime range separation" in {
      Given("an exact compile target and an independent runtime selector")
      When("the production catalog boundary retains the exact target identity")
      val retained = _catalog_boundary("0.5.1", Some("0.5.1"), Some("0.5.1-SNAPSHOT"))
      Then("CV-06 owns CAR metadata consistency")
      retained shouldBe true
      cancel("CV-06 owns CAR metadata consistency")
    }
    "preserve canonical repository CAR version selectors through the production catalog boundary" in {
      Given("one canonical namespace local-id artifact and semantic or SNAPSHOT version selectors")
      val version = org.scalacheck.Gen.oneOf("1.2.3", "1.2.3-SNAPSHOT", "2.0.0+build.7", "2026.07.27-RC1")
      val property = Prop.forAll(version, version, version) { (releasevalue, stablervalue, snapshotvalue) =>
        _catalog_boundary(releasevalue, Some(stablervalue), Some(snapshotvalue))
      }
      When("the canonical v2 production catalog parser and renderer are exercised")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
      Then("exact namespace/id-derived artifact and version selectors are preserved")
      checked.passed shouldBe true
    }
  }

  private def _catalog_boundary(version: String, lateststable: Option[String], latestsnapshot: Option[String]): Boolean = {
    val index = ComponentRepositoryIndex(
      "cncf.component-repository-index.v2",
      Instant.parse("2026-07-27T00:00:00Z"),
      Vector(ComponentRepositoryIndexEntry(
        ComponentArtifactKind.Car,
        "textus-shared",
        "car/org/example/textus/textus-shared.yaml",
        "active",
        Some(version),
        lateststable,
        latestsnapshot,
        Some("org.example.textus"),
        Some("Shared")
      ))
    )
    ComponentRepositoryIndex.parse(index.render).toOption.exists(_.artifacts.headOption.exists { entry =>
      entry.identity == ("car", "org.example.textus", "Shared") &&
        entry.recommended.contains(version) &&
        entry.latestStable == lateststable &&
        entry.latestSnapshot == latestsnapshot
    })
  }
}
