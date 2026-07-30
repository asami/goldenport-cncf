package org.goldenport.cncf.datatype

import org.simplemodeling.model.datatype.EntityId
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 17, 2026
 *  version Mar. 24, 2026
 * @version Jul. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityIdParseSpec extends AnyWordSpec with GivenWhenThen with Matchers {
  private val _in_eid02_restore_spec =
    afterWord("in spec:entity-collection-identity, example:E8, rules:R1,R3,R4, phase:52")
  private val _in_eid02_rejection_spec =
    afterWord("in spec:entity-collection-identity, example:E9, rules:R1,R4, phase:52")
  private val _in_eid02_property_spec =
    afterWord("in spec:entity-collection-identity, example:E10, rules:R1,R3,R4, phase:52")
  private val _label = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString)

  "EntityId.parse" should {
    "which records EID-02 canonical UniversalId parsing" which {
      "restore an independently namespaced collection from the canonical UniversalId form" must _in_eid02_restore_spec {
        "restore the complete collection namespace without a selected owner" in {
      Given("a canonical EntityId whose entry and collection namespaces differ")
      val canonical =
        "single-global-entity-ec1_6_textus_8_artscene_8_facility-0-stable"

      When("CNCF parses the canonical scalar without a selected collection")
      val result = EntityId.parse(canonical).toOption

      Then("the parsed ID carries the exact independent collection namespace")
      result.map(_.major) shouldBe Some("single")
      result.map(_.minor) shouldBe Some("global")
      result.map(_.collection.major) shouldBe Some("textus")
      result.map(_.collection.minor) shouldBe Some("artscene")
      result.map(_.collection.name) shouldBe Some("facility")

      And("the rendered canonical value remains the same UniversalId value")
      result.map(_.print) shouldBe Some(canonical)
        }
      }
    }

    "reject incomplete, unsupported, and non-entity canonical values" must _in_eid02_rejection_spec {
      "reject every non-canonical scalar without synthetic ownership" in {
      Given("canonical-looking values outside the exact EntityId contract")
      val invalid = Vector(
        "single-global-entity-facility-0-stable",
        "single-global-entity-ec2_6_textus_8_artscene_8_facility-0-stable",
        "single-global-aggregate-ec1_6_textus_8_artscene_8_facility-0-stable"
      )

      invalid.foreach { value =>
        When(s"CNCF parses the invalid EntityId value '$value'")
        val result = EntityId.parse(value).toOption

        Then("parsing fails without a name-only or entry-namespace fallback")
        result shouldBe None
      }
      }
    }

    "round-trip arbitrary label-safe canonical outer values" must _in_eid02_property_spec {
      "preserve every generated entry and collection component" in {
        Given("label-safe entry and independently namespaced collection components")
        val property = Prop.forAll(_label, _label, _label, _label, _label) {
          (entrymajor, entryminor, collectionmajor, collectionminor, collectionname) =>
            val payload =
              s"ec1_${collectionmajor.length}_${collectionmajor}_${collectionminor.length}_${collectionminor}_${collectionname.length}_${collectionname}"
            val canonical =
              s"${entrymajor}-${entryminor}-entity-${payload}-0-stable"

            When("CNCF parses the generated canonical UniversalId value")
            val result = EntityId.parse(canonical).toOption

            Then("the parser restores the exact entry and collection components")
            result.exists { id =>
              id.major == entrymajor &&
              id.minor == entryminor &&
              id.collection.major == collectionmajor &&
              id.collection.minor == collectionminor &&
              id.collection.name == collectionname &&
              id.value == canonical
            }
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(40), property)

        Then("every generated canonical value round-trips through the public parser")
        result.passed shouldBe true
      }
    }
  }
}
