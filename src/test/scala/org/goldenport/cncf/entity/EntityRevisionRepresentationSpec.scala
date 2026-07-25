package org.goldenport.cncf.entity

import org.goldenport.record.Record
import org.goldenport.record.RecordDecoder
import org.goldenport.cncf.component.ComponentDescriptor.given
import org.goldenport.cncf.entity.runtime.EntityRuntimeDescriptor
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 25, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityRevisionRepresentationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with TableDrivenPropertyChecks {
  "Entity revision representation binding" should {
    "admit exactly one compatible model and collection representation" in {
      Given("the valid SimpleEntity and explicit detached declaration matrix")
      val examples = Table(
        ("model", "collection", "expected"),
        (
          EntityRevisionModelMetadata(
            EntityRevisionModelKind.SimpleEntity,
            Some(EntityRevisionRepresentation.Embedded)
          ),
          Option.empty[EntityRevisionRepresentation],
          EntityRevisionRepresentation.Embedded
        ),
        (
          EntityRevisionModelMetadata(
            EntityRevisionModelKind.SimpleEntity,
            Some(EntityRevisionRepresentation.Embedded)
          ),
          Some(EntityRevisionRepresentation.Embedded),
          EntityRevisionRepresentation.Embedded
        ),
        (
          EntityRevisionModelMetadata(
            EntityRevisionModelKind.NonSimpleEntity,
            Some(EntityRevisionRepresentation.Detached)
          ),
          Option.empty[EntityRevisionRepresentation],
          EntityRevisionRepresentation.Detached
        ),
        (
          EntityRevisionModelMetadata(
            EntityRevisionModelKind.NonSimpleEntity,
            Some(EntityRevisionRepresentation.Detached)
          ),
          Some(EntityRevisionRepresentation.Detached),
          EntityRevisionRepresentation.Detached
        ),
        (
          EntityRevisionModelMetadata(
            EntityRevisionModelKind.NonSimpleEntity,
            None
          ),
          Some(EntityRevisionRepresentation.Detached),
          EntityRevisionRepresentation.Detached
        )
      )

      When("the model and collection declarations are resolved")
      val results = examples.toVector.map { case (model, collection, expected) =>
        EntityRevisionBinding
          .resolve(model, collection)
          .toOption
          .map(_.representation) -> expected
      }

      Then("the one authoritative representation is selected")
      results.foreach { case (actual, expected) =>
        actual shouldBe Some(expected)
      }
    }

    "reject conflicts, missing model evidence, and implicit detached representation" in {
      Given("SimpleEntity, non-SimpleEntity, and unknown model declaration failures")
      val examples = Table(
        ("model", "collection"),
        (
          EntityRevisionModelMetadata(
            EntityRevisionModelKind.SimpleEntity,
            Some(EntityRevisionRepresentation.Embedded)
          ),
          Some(EntityRevisionRepresentation.Detached)
        ),
        (
          EntityRevisionModelMetadata(
            EntityRevisionModelKind.SimpleEntity,
            Some(EntityRevisionRepresentation.Detached)
          ),
          Option.empty[EntityRevisionRepresentation]
        ),
        (
          EntityRevisionModelMetadata(
            EntityRevisionModelKind.SimpleEntity,
            None
          ),
          Some(EntityRevisionRepresentation.Embedded)
        ),
        (
          EntityRevisionModelMetadata(
            EntityRevisionModelKind.NonSimpleEntity,
            Some(EntityRevisionRepresentation.Embedded)
          ),
          Option.empty[EntityRevisionRepresentation]
        ),
        (
          EntityRevisionModelMetadata(
            EntityRevisionModelKind.NonSimpleEntity,
            None
          ),
          Some(EntityRevisionRepresentation.Embedded)
        ),
        (
          EntityRevisionModelMetadata(
            EntityRevisionModelKind.NonSimpleEntity,
            None
          ),
          Option.empty[EntityRevisionRepresentation]
        )
      )

      When("the declarations are resolved")
      val results = examples.toVector.map { case (model, collection) =>
        EntityRevisionBinding.resolve(model, collection).toOption
      }

      Then("assembly-oriented binding fails deterministically")
      results.foreach { actual =>
        actual shouldBe None
      }
    }

    "reject missing, mirrored, and wrong physical revision fields" in {
      Given("one embedded binding and one detached binding")
      val embedded = EntityRevisionBinding(EntityRevisionRepresentation.Embedded)
      val detached = EntityRevisionBinding(EntityRevisionRepresentation.Detached)

      When("persisted records use the authoritative physical field")
      val embeddedvalid = embedded
        .validatePersistedRecord(Record.data("revision" -> 1L))
        .toOption
      val detachedvalid = detached
        .validatePersistedRecord(Record.data("cncf_revision" -> 1L))
        .toOption

      val embeddedmissing = embedded.validatePersistedRecord(Record.empty).toOption
      val detachedmissing = detached.validatePersistedRecord(Record.empty).toOption
      val embeddedwrong = embedded
        .validatePersistedRecord(Record.data("cncf_revision" -> 1L))
        .toOption
      val detachedwrong = detached
        .validatePersistedRecord(Record.data("revision" -> 1L))
        .toOption
      val detachedcamelcase = detached
        .validatePersistedRecord(Record.data("cncfRevision" -> 1L))
        .toOption
      val dual = embedded
        .validatePersistedRecord(
          Record.data("revision" -> 1L, "cncf_revision" -> 1L)
        )
        .toOption

      Then("each binding accepts only its one field")
      embeddedvalid shouldBe Some(())
      detachedvalid shouldBe Some(())

      And("missing, wrong, and dual fields are rejected")
      embeddedmissing shouldBe None
      detachedmissing shouldBe None
      embeddedwrong shouldBe None
      detachedwrong shouldBe None
      detachedcamelcase shouldBe None
      dual shouldBe None
    }

    "decode collection declarations without inventing a default" in {
      Given("camelCase, snake_case, and undeclared Entity descriptors")
      val camelcase = Record.data(
        "entity" -> "Person",
        "revisionRepresentation" -> "embedded"
      )
      val snakecase = Record.data(
        "entity" -> "Document",
        "revision_representation" -> "detached"
      )
      val undeclared = Record.data("entity" -> "Legacy")

      When("component descriptor records are decoded")
      val decoder = summon[RecordDecoder[EntityRuntimeDescriptor]]
      val camelcaseresult = decoder.fromRecord(camelcase).toOption
      val snakecaseresult = decoder.fromRecord(snakecase).toOption
      val undeclaredresult = decoder.fromRecord(undeclared).toOption
      val invalidresult = decoder
        .fromRecord(
          Record.data(
            "entity" -> "Invalid",
            "revisionRepresentation" -> "automatic"
          )
        )
        .toOption
      val blankresult = decoder
        .fromRecord(
          Record.data(
            "entity" -> "Blank",
            "revisionRepresentation" -> ""
          )
        )
        .toOption

      Then("only explicit declarations become runtime metadata")
      camelcaseresult.flatMap(_.revisionRepresentation) shouldBe
        Some(EntityRevisionRepresentation.Embedded)
      snakecaseresult.flatMap(_.revisionRepresentation) shouldBe
        Some(EntityRevisionRepresentation.Detached)
      undeclaredresult.flatMap(_.revisionRepresentation) shouldBe None

      And("invalid and blank declarations fail instead of falling back")
      invalidresult shouldBe None
      blankresult shouldBe None
    }

    "accept only canonical representation labels" in {
      Given("canonical representation labels and prohibited compatibility aliases")
      val canonical = Vector("embedded", "detached")
      val aliases = Vector("simple-entity", "simpleentity", "carrier")

      When("the labels are parsed")
      val canonicalresults = canonical.map(EntityRevisionRepresentation.parseC(_).toOption)
      val aliasresults = aliases.map(EntityRevisionRepresentation.parseC(_).toOption)

      Then("only the canonical labels resolve")
      canonicalresults shouldBe Vector(
        Some(EntityRevisionRepresentation.Embedded),
        Some(EntityRevisionRepresentation.Detached)
      )
      aliasresults should contain only None
    }
  }
}
