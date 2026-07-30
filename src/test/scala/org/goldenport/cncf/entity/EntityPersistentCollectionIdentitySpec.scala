package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 26, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityPersistentCollectionIdentitySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with TableDrivenPropertyChecks {
  private val _in_eid01_spec =
    afterWord("in spec:entity-collection-identity, example:E1, rules:R1,R4, phase:52")
  private val _in_eid01_selected_owner_spec =
    afterWord("in spec:entity-collection-identity, example:E1b, rules:R1,R4, phase:52")
  private val _in_eid03_generated_reference_spec =
    afterWord("in spec:entity-collection-identity, example:E1c, rules:R1,R3,R4, phase:52")
  private val _in_phase52_spec =
    afterWord("in spec:entity-collection-identity, example:persistence-decoding, rules:R1,R3,R4, phase:52")

  "EntityPersistent store decoding" must _in_phase52_spec {
    "which records EID-01 persistence decoding" which {
      "E1 accept exact parsed collection ownership after canonical EntityId round-trip" must _in_eid01_spec {
        "decode the requested collection without ownership repair" in {
          val collections = Table(
            ("name", "value"),
            ("facility", "museum"),
            ("exhibition", "summer")
          )

          forAll(collections) { (name, value) =>
            Given(
              s"Spec: docs/spec/entity-collection-identity.md; Rules: R1,R4; Example: E1; a record for $name whose canonical EntityId carries its exact namespace"
            )
            val canonicalcollection = EntityCollectionId("major", "minor", name)
            val runtimeid           = EntityId("single", "global", canonicalcollection)
            val record = Record.dataAuto(
              "id"    -> runtimeid.value,
              "value" -> value
            )

            And("the canonical parser restores the stored exact namespace")
            EntityId.parse(runtimeid.value).map(_.collection) shouldBe
              Consequence.success(canonicalcollection)

            When("the persistent codec decodes the record in its requested collection")
            val decoded =
              EntityPersistent._decode_store_record(
                _persistent,
                canonicalcollection,
                record
              )

            Then("the decoded Entity retains the exact owning collection")
            decoded.map(_.id.collection) shouldBe
              Consequence.success(canonicalcollection)

            And("business data survives exact collection validation")
            decoded.map(_.value) shouldBe Consequence.success(value)

          }
        }
      }
    }

    "E1b register selected-owner mismatch rejection for EID-04" must _in_eid01_selected_owner_spec {
      "reject a selected-owner mismatch without repair" in {
        Given(
          "Spec: docs/spec/entity-collection-identity.md; Rules: R1,R4; Example: E1b; a scalar ID whose fabricated collection differs from the selected owner"
        )
        val selectedcollection =
          EntityCollectionId("textus", "artscene", "facility")
        val scalarcollection =
          EntityCollectionId("single", "global", "facility")
        val record = Record.dataAuto(
          "id"    -> EntityId("single", "global", scalarcollection).value,
          "value" -> "museum"
        )

        When("the persistence boundary decodes it under the selected collection")
        val decoded =
          EntityPersistent._decode_store_record(
            _persistent,
            selectedcollection,
            record
          )

        Then("the differing parsed owner is rejected before resident projection")
        decoded match {
          case Consequence.Failure(conclusion) =>
            ConclusionDiagnostics
              .classify(conclusion)
              .reason shouldBe Some("entity-codec-collection-mismatch")
          case Consequence.Success(_) =>
            fail("A selected collection must not rewrite a parsed EntityId")
        }
      }
    }

    "E1c prove generated primary and repeated reference store round-trip for EID-03" must _in_eid03_generated_reference_spec {
      "round-trip generated primary and repeated references without collection repair" in {
        Given(
          "Spec: docs/spec/entity-collection-identity.md; Rules: R1,R3,R4; Example: E1c; a generated-shape Entity with optional and repeated references from independent collections"
        )
        val facilitycollection =
          EntityCollectionId("textus", "artscene", "facility")
        val exhibitcollection =
          EntityCollectionId("textus", "collections", "exhibit")
        val archivecollection =
          EntityCollectionId("textus", "archive", "exhibit")
        val original = GeneratedReferenceFixture(
          id = EntityId("entry", "facility_1", facilitycollection),
          primaryreference = Some(EntityId("entry", "exhibit_primary", exhibitcollection)),
          relatedreferences = Vector(
            EntityId("entry", "exhibit_collection", exhibitcollection),
            EntityId("entry", "exhibit_archive", archivecollection)
          )
        )

        When("the generated-shape persistence codec stores and decodes its EntityId fields")
        val stored  = _generated_reference_persistent.toStoreRecord(original)
        val decoded = _generated_reference_persistent.fromStoreRecord(stored)

        Then("the scalar record stores every exact primary and repeated reference")
        stored.getString("id") shouldBe Some(original.id.value)
        stored.getString("primaryReference") shouldBe Some(original.primaryreference.get.value)
        stored.getVector("relatedReferences").map(_.map(_.toString)) shouldBe Some(
          original.relatedreferences.map(_.value)
        )

        And("decoding restores the exact primary and repeated reference owners")
        decoded shouldBe Consequence.success(original)
      }
    }

    "reject a codec whose canonical ID names a different exact collection" in {
      Given("a codec and a canonical ID whose exact collection differs from the requested collection")
      val requestedcollection =
        EntityCollectionId("major", "minor", "facility")
      val runtimecollection =
        EntityCollectionId("single", "global", "facility")
      val record = Record.dataAuto(
        "id"    -> EntityId("single", "global", runtimecollection).value,
        "value" -> "museum"
      )

      When("the legacy codec crosses the exact store boundary")
      val decoded =
        EntityPersistent._decode_store_record(
          _legacy_persistent,
          requestedcollection,
          record
        )

      Then("the legacy codec receives the exact collection-mismatch diagnostic")
      decoded shouldBe a[Consequence.Failure[?]]
      decoded match {
        case Consequence.Failure(conclusion) =>
          val diagnostic = ConclusionDiagnostics.classify(conclusion)
          val facets     = conclusion.observation.cause.descriptor.facets
          diagnostic.policy shouldBe Some("entity.persistence.collection")
          diagnostic.reason shouldBe Some(
            "entity-codec-collection-mismatch"
          )
          facets should contain(Descriptor.Facet.Expected(requestedcollection.print))
          facets should contain(Descriptor.Facet.Actual(runtimecollection.print))
        case Consequence.Success(_) =>
          fail("A legacy logical-name match must not satisfy exact ownership")
      }
    }

    "reject a record belonging to another logical collection" in {
      Given("a Facility request and an Exhibition EntityId")
      val requestedcollection =
        EntityCollectionId("major", "minor", "facility")
      val actualcollection =
        EntityCollectionId("single", "global", "exhibition")
      val record = Record.dataAuto(
        "id"    -> EntityId("single", "global", actualcollection).value,
        "value" -> "summer"
      )

      When("the Facility codec boundary decodes the record")
      val decoded =
        EntityPersistent._decode_store_record(
          _persistent,
          requestedcollection,
          record
        )

      Then("the collection mismatch remains a structured failure")
      decoded match {
        case Consequence.Failure(conclusion) =>
          val diagnostic = ConclusionDiagnostics.classify(conclusion)
          val facets     = conclusion.observation.cause.descriptor.facets
          diagnostic.causeKind shouldBe Some("inconsistency")
          diagnostic.policy shouldBe Some("entity.persistence.collection")
          diagnostic.reason shouldBe Some("entity-codec-collection-mismatch")
          diagnostic.webStatus shouldBe 500
          facets should contain(Descriptor.Facet.Expected(requestedcollection.print))
          facets should contain(Descriptor.Facet.Actual(actualcollection.print))
        case Consequence.Success(_) =>
          fail("A different logical collection must not be rebound")
      }
    }

    "decode a custom codec exactly once without invoking context repair" in {
      Given("a custom codec that accepts only a scalar physical id")
      val requestedcollection =
        EntityCollectionId("major", "minor", "facility")
      val runtimecollection =
        EntityCollectionId("single", "global", "facility")
      val record = Record.dataAuto(
        "id"    -> EntityId("single", "global", runtimecollection).value,
        "value" -> "museum"
      )
      var decodecount    = 0
      var observedrecord = Option.empty[Record]
      val persistent = new EntityPersistent[FixtureEntity] {
        def id(e: FixtureEntity): EntityId =
          e.id

        def toRecord(e: FixtureEntity): Record =
          Record.dataAuto(
            "id"    -> e.id.value,
            "value" -> e.value
          )

        def fromRecord(r: Record): Consequence[FixtureEntity] = {
          decodecount += 1
          observedrecord = Some(r)
          r.getAny("id") match {
            case Some(_: String) =>
              _decode_fixture(r)
            case _ =>
              Consequence.argumentInvalid(
                "custom codec accepts only the scalar physical id"
              )
          }
        }

      }

      When("the codec decodes a record under a different owning collection")
      val decoded =
        EntityPersistent._decode_store_record(
          persistent,
          requestedcollection,
          record
        )

      Then("CNCF rejects the mismatch without a second decode, input rewrite, or context repair")
      decoded shouldBe a[Consequence.Failure[?]]
      decodecount shouldBe 1
      observedrecord shouldBe Some(record)
    }

    "preserve a raw storage Record while validating its logical collection" in {
      Given("an internal raw Record adapter and a canonical exact id")
      val requestedcollection =
        EntityCollectionId("major", "minor", "facility")
      val runtimeid =
        EntityId("single", "global", requestedcollection)
      val record = Record.dataAuto(
        "id"    -> runtimeid.value,
        "value" -> "museum"
      )
      def _required_id_(source: Record): EntityId =
        EntityId.createC(source) match {
          case Consequence.Success(id) => id
          case Consequence.Failure(conclusion) =>
            fail(s"Fixture EntityId must decode: ${conclusion.display}")
        }
      val persistent = new EntityPersistent[Record] {
        def id(e: Record): EntityId =
          _required_id_(e)

        def toRecord(e: Record): Record =
          e

        def fromRecord(r: Record): Consequence[Record] =
          Consequence.success(r)

      }

      When("the raw adapter crosses the store decoding boundary")
      val decoded =
        EntityPersistent._decode_store_record(
          persistent,
          requestedcollection,
          record
        )

      Then("the returned domain Record carries its stored exact ownership")
      decoded.map(_required_id_).map(_.collection) shouldBe
        Consequence.success(requestedcollection)

      And("the physical Record remains unchanged")
      record.getString("id") shouldBe
        Some(runtimeid.value)
    }
  }

  private lazy val _persistent: EntityPersistent[FixtureEntity] =
    new EntityPersistent[FixtureEntity] {
      def id(e: FixtureEntity): EntityId =
        e.id

      def toRecord(e: FixtureEntity): Record =
        Record.dataAuto(
          "id"    -> e.id.value,
          "value" -> e.value
        )

      def fromRecord(r: Record): Consequence[FixtureEntity] =
        _decode_fixture(r)

    }

  private lazy val _generated_reference_persistent: EntityPersistent[GeneratedReferenceFixture] =
    new EntityPersistent[GeneratedReferenceFixture] {
      def id(e: GeneratedReferenceFixture): EntityId = e.id

      def toRecord(e: GeneratedReferenceFixture): Record =
        Record.dataAuto(
          "id"                -> e.id.value,
          "primaryReference"  -> e.primaryreference.map(_.value),
          "relatedReferences" -> e.relatedreferences.map(_.value)
        )

      def fromRecord(r: Record): Consequence[GeneratedReferenceFixture] =
        for {
          id <- EntityId.createC(r)
          primaryreference <- r.getString("primaryReference") match {
            case Some(value) => EntityId.parse(value).map(Some(_))
            case None        => Consequence.success(Option.empty[EntityId])
          }
          relatedreferences <- r.getVector("relatedReferences") match {
            case Some(values) => values.foldLeft(Consequence.success(Vector.empty[EntityId])) {
                case (z, value) => z.flatMap(xs => EntityId.parse(value.toString).map(xs :+ _))
              }
            case None => Consequence.success(Vector.empty[EntityId])
          }
        } yield GeneratedReferenceFixture(id, primaryreference, relatedreferences)
    }

  private val _legacy_persistent: EntityPersistent[FixtureEntity] =
    new EntityPersistent[FixtureEntity] {
      def id(e: FixtureEntity): EntityId =
        e.id

      def toRecord(e: FixtureEntity): Record =
        Record.dataAuto(
          "id"    -> e.id.value,
          "value" -> e.value
        )

      def fromRecord(r: Record): Consequence[FixtureEntity] =
        _decode_fixture(r)
    }

  private def _decode_fixture(
      record: Record
  ): Consequence[FixtureEntity] =
    for {
      id <- EntityId.createC(record)
      value <- record
        .getString("value")
        .map(Consequence.success)
        .getOrElse(Consequence.argumentMissing("value"))
    } yield FixtureEntity(id, value)

  private final case class FixtureEntity(
      id: EntityId,
      value: String
  )

  private final case class GeneratedReferenceFixture(
      id: EntityId,
      primaryreference: Option[EntityId],
      relatedreferences: Vector[EntityId]
  )
}
