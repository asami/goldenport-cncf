package org.goldenport.cncf.entity

import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 28, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityStoreRecordProjectionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Entity store Record projection" should {
    "restore a datastore JSON object to its declared scalar String field" in {
      Given("a physical Record whose declared scalar String field is materialized as a Record")
      val physical = Record.data(
        "metadata_json" -> Record.data(
          "eventType" -> "created",
          "facilityId" -> "facility-1"
        ),
        "status" -> "pending"
      )

      When("the declared Entity storage metadata projects the physical Record")
      val projected = EntityStoreRecordProjection.project(
        physical,
        Vector(
          EntityStoreAttribute.scalarString(
            logicalName = "metadataJson",
            storageName = "metadata_json"
          )
        )
      )

      Then("the JSON object is restored as compact scalar text")
      projected.flatMap(_.asC[String]("metadataJson")) shouldBe
        org.goldenport.Consequence.success(
          """{"eventType":"created","facilityId":"facility-1"}"""
        )

      And("the original physical field remains available")
      projected.map(_.getRecord("metadata_json")) shouldBe
        org.goldenport.Consequence.success(
          physical.getRecord("metadata_json")
        )

      And("unrelated physical fields remain unchanged")
      projected.flatMap(_.asC[String]("status")) shouldBe
        org.goldenport.Consequence.success("pending")
    }

    "leave ordinary scalar and optional storage representations unchanged" in {
      Given("present scalar text under its physical name and an absent optional scalar field")
      val physical = Record.data(
        "metadata_json" -> """{"eventType":"created"}""",
        "status" -> "pending"
      )
      val attributes = Vector(
        EntityStoreAttribute.scalarString(
          logicalName = "metadataJson",
          storageName = "metadata_json"
        ),
        EntityStoreAttribute.scalarString(
          logicalName = "optionalMetadataJson",
          storageName = "optional_metadata_json"
        )
      )

      When("the physical Record is projected")
      val projected = EntityStoreRecordProjection.project(
        physical,
        attributes
      )

      Then("the existing scalar text retains its exact identity")
      projected.flatMap(_.asC[String]("metadataJson")) shouldBe
        org.goldenport.Consequence.success("""{"eventType":"created"}""")

      And("the absent optional field remains absent")
      projected.map(_.getAny("optional_metadata_json")) shouldBe
        org.goldenport.Consequence.success(None)
    }

    "surface malformed persisted objects as a projection failure" in {
      Given("a persisted object containing a value that cannot be rendered")
      val malformedvalue = new Object {
        override def toString: String =
          throw new IllegalArgumentException("malformed persisted value")
      }
      val physical = Record.data(
        "metadata_json" -> Record.data("broken" -> malformedvalue)
      )

      When("the scalar String projection renders the persisted object")
      val projected = EntityStoreRecordProjection.project(
        physical,
        Vector(
          EntityStoreAttribute.scalarString(
            logicalName = "metadataJson",
            storageName = "metadata_json"
          )
        )
      )

      Then("the malformed representation fails instead of becoming sentinel text")
      projected.isSuccess shouldBe false
    }

    "ignore Records for fields without scalar String metadata" in {
      Given("a structured field that is not declared for scalar String projection")
      val structured = Record.data("latitude" -> 35.68, "longitude" -> 139.76)
      val physical = Record.data(
        "location" -> structured,
        "metadata_json" -> "plain"
      )

      When("metadata names only the scalar String storage field")
      val projected = EntityStoreRecordProjection.project(
        physical,
        Vector(
          EntityStoreAttribute.scalarString(
            logicalName = "metadataJson",
            storageName = "metadata_json"
          )
        )
      )

      Then("the unrelated structured Record retains its declared representation")
      projected.map(_.getRecord("location")) shouldBe
        org.goldenport.Consequence.success(Some(structured))
    }
  }
}
