package org.goldenport.cncf.projection

import org.goldenport.cncf.entity.{
  EntityRevisionBinding,
  EntityRevisionRepresentation
}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.protocol.OperationResponseFormatter
import org.goldenport.protocol.{Property, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityRevisionProjectionSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Entity revision projection" should {
    "expose embedded revision directly on mutation-capable records" in {
      Given("bounded positive revisions and Entity, search, View, and Aggregate projection contexts")
      val contexts = Vector("entity", "search", "view", "aggregate")
      val property = Prop.forAll(Gen.chooseNum(1L, 1000000L)) { number =>
        val revision = EntityRevision.createC(number).toOption
        contexts.forall { _ =>
          val projected = EntityRevisionProjection.projectRecord(
            Some(
              EntityRevisionBinding(
                EntityRevisionRepresentation.Embedded
              )
            ),
            Record.data(
              "id" -> "sample-1",
              "name" -> "Sample"
            ),
            revision
          )
          projected.getAny("revision").contains(number) &&
          projected.getAny("cncf_revision").isEmpty &&
          projected.getAny("version").isEmpty
        }
      }

      When("the property runner projects each standard mutation-capable surface")
      val checked = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(20),
        property
      )

      Then("the embedded domain record is the only standard revision projection")
      checked.passed shouldBe true
    }

    "keep detached revision in an explicit response metadata channel" in {
      Given("an explicitly admitted non-SimpleEntity detached binding")
      val binding = Some(
        EntityRevisionBinding(
          EntityRevisionRepresentation.Detached
        )
      )
      val revision = EntityRevision.createC(5L).toOption
      val domain = Record.data(
        "id" -> "legacy-1",
        "name" -> "Legacy"
      )

      When("the detached extension projects its domain and response records")
      val projected = EntityRevisionProjection.projectRecord(
        binding,
        domain,
        revision
      )
      val response = EntityRevisionProjection.projectResponse(
        binding,
        Record.data("record" -> projected),
        revision
      )

      Then("the domain remains revision-free and only explicit version metadata is added")
      projected shouldBe domain
      projected.getAny("revision") shouldBe None
      projected.getAny("cncf_revision") shouldBe None
      response.getString("version") shouldBe Some("5")
    }

    "recover transport revision from embedded and detached response shapes" in {
      Given("an Embedded admin response and a Detached response whose domain record owns an unrelated revision")
      val embedded = Record.data(
        "record" -> Record.data(
          "id" -> "simple-1",
          "revision" -> 7L
        )
      )
      val detached = Record.data(
        "version" -> "11",
        "record" -> Record.data(
          "id" -> "legacy-1",
          "revision" -> 99L
        )
      )

      When("the transport boundary resolves each observed revision")
      val embeddedrevision =
        EntityRevisionProjection.responseRevision(embedded)
      val detachedrevision =
        EntityRevisionProjection.responseRevision(detached)

      Then("Embedded uses nested managed revision and Detached metadata takes precedence over domain data")
      embeddedrevision.map(_.value) shouldBe Some(7L)
      detachedrevision.map(_.value) shouldBe Some(11L)
    }

    "restore embedded revision after a view filters managed metadata" in {
      Given("an Embedded source record and a view containing only business fields")
      val binding = Some(
        EntityRevisionBinding(
          EntityRevisionRepresentation.Embedded
        )
      )
      val source = Record.data(
        "id" -> "simple-1",
        "name" -> "Sample",
        "revision" -> 13L
      )
      val view = Record.data(
        "id" -> "simple-1",
        "name" -> "Sample"
      )

      When("the mutation-capable view is projected")
      val projected =
        EntityRevisionProjection.projectViewRecord(binding, source, view)

      Then("managed revision remains visible without adding detached aliases")
      projected.getAny("revision") shouldBe Some(13L)
      projected.getAny("version") shouldBe None
      projected.getAny("cncf_revision") shouldBe None
    }

    "never turn an embedded revision into a detached response alias" in {
      Given("an embedded SimpleEntity binding and projected revision")
      val binding = Some(
        EntityRevisionBinding(
          EntityRevisionRepresentation.Embedded
        )
      )
      val revision = EntityRevision.createC(3L).toOption

      When("response metadata projection is requested")
      val response = EntityRevisionProjection.projectResponse(
        binding,
        Record.data("status" -> "ok"),
        revision
      )

      Then("no version or detached carrier alias is emitted")
      response.getAny("version") shouldBe None
      response.getAny("cncf_revision") shouldBe None
    }

    "reject managed revision fields from application mutation records" in {
      Given("embedded and detached bindings with application-supplied managed fields")
      val embedded = EntityRevisionBinding(
        EntityRevisionRepresentation.Embedded
      )
      val detached = EntityRevisionBinding(
        EntityRevisionRepresentation.Detached
      )

      When("the records are admitted as application patches")
      val embeddedresult = embedded.rejectManagedPatch(
        Record.data("name" -> "changed", "revision" -> 8L),
        "entity"
      )
      val detachedresult = detached.rejectManagedPatch(
        Record.data("name" -> "changed", "cncf_revision" -> 8L),
        "entity"
      )

      Then("both managed representations remain read-only")
      embeddedresult.toOption shouldBe None
      detachedresult.toOption shouldBe None
    }

    "preserve embedded revision through JSON YAML and XML response formats" in {
      Given("one embedded Entity projection and every structured CNCF response format")
      val projected = EntityRevisionProjection.projectRecord(
        Some(
          EntityRevisionBinding(
            EntityRevisionRepresentation.Embedded
          )
        ),
        Record.data("id" -> "sample-1"),
        EntityRevision.createC(13L).toOption
      )
      val formats = Vector("json", "yaml", "xml")

      When("the response formatter renders each transport representation")
      val rendered = formats.map { format =>
        val request = Request.of(
          component = "sample",
          service = "entity",
          operation = "read",
          properties = List(
            Property("textus.format", format, None)
          )
        )
        format -> OperationResponseFormatter.toResponse(
          request,
          OperationResponse.RecordResponse(projected),
          RunMode.Command
        )
      }.toMap

      Then("revision remains readable data in every structured representation")
      rendered("json") match {
        case Response.Json(value) =>
          value should include (""""revision":13""")
        case other =>
          fail(s"unexpected JSON response: ${other}")
      }
      rendered("yaml") match {
        case Response.Yaml(value) =>
          value should include ("revision: 13")
        case other =>
          fail(s"unexpected YAML response: ${other}")
      }
      rendered("xml") match {
        case Response.Xml(value) =>
          value should include ("<revision>13</revision>")
        case other =>
          fail(s"unexpected XML response: ${other}")
      }
    }
  }
}
