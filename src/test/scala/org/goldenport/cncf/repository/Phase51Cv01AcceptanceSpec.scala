package org.goldenport.cncf.repository

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

final class Phase51Cv01AcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Phase 51 CV-01 version identities" should {
    "register descriptor identity mismatch" in {
      Given("requested and declared descriptor identities represented by production catalog entries")
      When("the production catalog parser and renderer retain both identities")
      val requested = _catalog_boundary("requested-descriptor", "0.5.1", Some("0.5.1"), None)
      val declared = _catalog_boundary("declared-descriptor", "0.5.0", Some("0.5.0"), None)
      Then("CV-04 owns runtime descriptor target validation")
      requested shouldBe true
      declared shouldBe true
      cancel("CV-04 owns descriptor identity mismatch")
    }
    "register descriptor byte or digest tampering" in {
      Given("descriptor byte and digest identities represented at the production catalog boundary")
      When("the production catalog parser and renderer retain the descriptor identity")
      val retained = _catalog_boundary("descriptor-integrity", "0.5.1", Some("0.5.1"), None)
      Then("CV-04 owns runtime descriptor digest validation")
      retained shouldBe true
      cancel("CV-04 owns runtime descriptor digest validation")
    }
    "register provenance and digest" in {
      Given("generation input and output identities represented by a production catalog entry")
      When("the production catalog parser and renderer retain the artifact identity")
      val retained = _catalog_boundary("provenance", "0.5.1", Some("0.5.1"), None)
      Then("CV-05 owns provenance and provenance digest tampering")
      retained shouldBe true
      cancel("CV-05 owns provenance and provenance digest tampering")
    }
    "register CAR compile target vs runtime range separation" in {
      Given("an exact compile target and an independent runtime selector")
      When("the production catalog boundary retains the exact target identity")
      val retained = _catalog_boundary("runtime-range", "0.5.1", Some("0.5.1"), Some("0.5.1-SNAPSHOT"))
      Then("CV-06 owns CAR metadata consistency")
      retained shouldBe true
      cancel("CV-06 owns CAR metadata consistency")
    }
    "preserve Maven and semantic version identities through the production catalog boundary" in {
      Given("valid Maven-style artifact IDs and semantic or SNAPSHOT version selectors")
      val artifactid = Gen.nonEmptyListOf(Gen.oneOf(("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-").toSeq)).map(_.mkString).suchThat(_.head.isLetterOrDigit)
      val version = Gen.oneOf("1.2.3", "1.2.3-SNAPSHOT", "2.0.0+build.7", "2026.07.27-RC1")
      val property = Prop.forAll(artifactid, version, version) { (artifactidvalue, stablervalue, snapshotvalue) =>
        _catalog_boundary(artifactidvalue, stablervalue, Some(stablervalue), Some(snapshotvalue))
      }
      When("the production catalog parser and renderer are exercised")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
      Then("exact artifact and version identities are preserved or rejected by the production validator")
      checked.passed shouldBe true
    }
  }

  private def _catalog_boundary(artifactid: String, version: String, lateststable: Option[String], latestsnapshot: Option[String]): Boolean = {
    val index = ComponentRepositoryIndex(
      "cncf.component-repository-index.v1",
      Instant.parse("2026-07-27T00:00:00Z"),
      Vector(ComponentRepositoryIndexEntry(ComponentArtifactKind.Car, artifactid, s"car/$artifactid.json", "active", None, lateststable, latestsnapshot))
    )
    ComponentRepositoryIndex.parse(index.render).toOption.exists(_.artifacts.headOption.exists { entry =>
      entry.identity == ("car", artifactid) && entry.latestStable == lateststable && entry.latestSnapshot == latestsnapshot
    })
  }
}
