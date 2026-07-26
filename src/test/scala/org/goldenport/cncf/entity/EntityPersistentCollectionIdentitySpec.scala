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
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityPersistentCollectionIdentitySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with TableDrivenPropertyChecks {

  "EntityPersistent store decoding" should {
    "accept the owning collection namespace after scalar EntityId round-trip" in {
      val collections = Table(
        ("name", "value"),
        ("facility", "museum"),
        ("exhibition", "summer")
      )

      forAll(collections) { (name, value) =>
        Given(s"a record for $name whose scalar EntityId carries the runtime namespace")
        val canonicalcollection = EntityCollectionId("major", "minor", name)
        val runtimecollection = EntityCollectionId("single", "global", name)
        val runtimeid = EntityId("single", "global", runtimecollection)
        val record = Record.dataAuto(
          "id" -> runtimeid.value,
          "value" -> value
        )

        When("the persistent codec decodes the record in its requested collection")
        val decoded =
          EntityPersistent._decode_store_record(
            _persistent,
            canonicalcollection,
            record
          )

        Then("the decoded Entity retains its scalar identity representation")
        decoded.map(_.id.collection) shouldBe
          Consequence.success(runtimecollection)

        And("business data survives logical collection validation")
        decoded.map(_.value) shouldBe Consequence.success(value)
      }
    }

    "reject a record belonging to another logical collection" in {
      Given("a Facility request and an Exhibition EntityId")
      val requestedcollection =
        EntityCollectionId("major", "minor", "facility")
      val actualcollection =
        EntityCollectionId("single", "global", "exhibition")
      val record = Record.dataAuto(
        "id" -> EntityId("single", "global", actualcollection).value,
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
          val facets = conclusion.observation.cause.descriptor.facets
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

    "decode a custom codec exactly once without changing its physical input" in {
      Given("a custom codec that accepts only a scalar physical id")
      val requestedcollection =
        EntityCollectionId("major", "minor", "facility")
      val runtimecollection =
        EntityCollectionId("single", "global", "facility")
      val record = Record.dataAuto(
        "id" -> EntityId("single", "global", runtimecollection).value,
        "value" -> "museum"
      )
      var decodecount = 0
      val persistent = new EntityPersistent[FixtureEntity] {
        def id(e: FixtureEntity): EntityId =
          e.id

        def toRecord(e: FixtureEntity): Record =
          Record.dataAuto(
            "id" -> e.id.value,
            "value" -> e.value
          )

        def fromRecord(r: Record): Consequence[FixtureEntity] = {
          decodecount += 1
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

      When("the codec decodes a record under the owning logical collection")
      val decoded =
        EntityPersistent._decode_store_record(
          persistent,
          requestedcollection,
          record
        )

      Then("CNCF accepts the logical collection without a second decode")
      decoded shouldBe a[Consequence.Success[?]]
      decodecount shouldBe 1
    }

    "preserve a raw storage Record while validating its logical collection" in {
      Given("an internal raw Record adapter and a scalar runtime-namespaced id")
      val requestedcollection =
        EntityCollectionId("major", "minor", "facility")
      val runtimecollection =
        EntityCollectionId("single", "global", "facility")
      val record = Record.dataAuto(
        "id" -> EntityId("single", "global", runtimecollection).value,
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

      Then("logical collection validation does not rewrite raw fields")
      decoded shouldBe Consequence.success(record)
      decoded.toOption.flatMap(_.getString("id")) shouldBe record.getString("id")
    }
  }

  private val _persistent: EntityPersistent[FixtureEntity] =
    new EntityPersistent[FixtureEntity] {
      def id(e: FixtureEntity): EntityId =
        e.id

      def toRecord(e: FixtureEntity): Record =
        Record.dataAuto(
          "id" -> e.id.value,
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
}
