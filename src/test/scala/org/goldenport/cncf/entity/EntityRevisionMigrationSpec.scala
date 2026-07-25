package org.goldenport.cncf.entity

import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityRevisionMigrationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Persisted Entity revision admission" should {
    "reject missing revision without synthesizing it from lifecycle metadata" in {
      Given(
        "Phase 50 ER-08; embedded and detached records with timestamps but no admitted revision"
      )
      val embedded = EntityRevisionBinding(
        EntityRevisionRepresentation.Embedded
      )
      val detached = EntityRevisionBinding(
        EntityRevisionRepresentation.Detached
      )
      val record = Record.dataAuto(
        "id" -> "legacy",
        "created_at" -> "2026-07-25T00:00:00Z",
        "updated_at" -> "2026-07-25T01:00:00Z"
      )

      When("both representation bindings admit the persisted record")
      val embeddedresult = embedded.revision(record)
      val detachedresult = detached.revision(record)

      Then("both require explicit migration or recreation")
      embeddedresult.toOption shouldBe None
      detachedresult.toOption shouldBe None
    }

    "reject dual embedded and detached revision representations" in {
      Given(
        "Phase 50 ER-08; one persisted record containing both managed revision fields"
      )
      val embedded = EntityRevisionBinding(
        EntityRevisionRepresentation.Embedded
      )
      val detached = EntityRevisionBinding(
        EntityRevisionRepresentation.Detached
      )
      val record = Record.dataAuto(
        "id" -> "dual",
        "revision" -> 3L,
        "cncf_revision" -> 3L
      )

      When("either representation binding admits the persisted record")
      val embeddedresult = embedded.revision(record)
      val detachedresult = detached.revision(record)

      Then("neither chooses a precedence winner")
      embeddedresult.toOption shouldBe None
      detachedresult.toOption shouldBe None
    }

    "reject every non-positive persisted revision" in {
      Given(
        "Phase 50 ER-08; generated invalid revision values in each physical representation"
      )
      val property = Prop.forAll(Gen.chooseNum(Long.MinValue, 0L)) {
        value =>
          val embedded = EntityRevisionBinding(
            EntityRevisionRepresentation.Embedded
          ).revision(
            Record.dataAuto("id" -> "embedded", "revision" -> value)
          )
          val detached = EntityRevisionBinding(
            EntityRevisionRepresentation.Detached
          ).revision(
            Record.dataAuto(
              "id" -> "detached",
              "cncf_revision" -> value
            )
          )
          embedded.toOption.isEmpty && detached.toOption.isEmpty
      }

      When("the property runner checks the persisted admission boundary")
      val checked = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(100),
        property
      )

      Then("no invalid value is treated as revision zero or initial revision")
      checked.passed shouldBe true
    }
  }
}
