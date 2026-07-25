package org.goldenport.cncf.entity

import org.goldenport.Consequence
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityConditionalTransitionModelSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _collectionid =
    EntityCollectionId("test", "phase49", "conditional_model")
  private val _model_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E5, rules:R6-R10, phase:49"
    )
  private val _successor_identity_metadata =
    afterWord(
      "in spec:entity-conflict-and-conditional-transition, example:E19, rules:R8,R14, phase:49"
    )

  "Entity conditional transition model" should {
    "admit only definition-owned exact fields and values" must _model_metadata {
      "when typed transition input is constructed" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R6-R10; Example: E5; one persistence codec and bounded exact values"
        )
        val admittedfield =
          EntityTransitionField
            .exact[Root, String]("status", _root_persistent)
            .TAKE
        val foreignfield =
          EntityTransitionField
            .exact[Root, String]("status", _root_persistent)
            .TAKE
        val definition =
          EntityTransitionDefinition
            .create(_root_persistent, Vector(admittedfield))
            .TAKE
        val values = Gen.alphaNumStr.suchThat(_.length <= 64)

        When("the definition admits its canonical field and generated exact values")
        val canonicalstorage = admittedfield.storageField
        val foreignresult =
          foreignfield.expected("open").flatMap(value =>
            definition.expectation(
              EntityRevision.INITIAL,
              value
            )
          )
        val property = Prop.forAll(values) { value =>
          val expected = admittedfield.expected(value)
          val duplicate = expected.flatMap(item =>
            definition.expectation(
              EntityRevision.INITIAL,
              item,
              item
            )
          )
          expected.isSuccess && duplicate.isFaillure
        }

        Then("storage identity is canonical and foreign or duplicate fields are rejected")
        admittedfield.logicalName shouldBe "status"
        canonicalstorage shouldBe "root_status"
        foreignresult shouldBe a[Consequence.Failure[_]]
        Test.check(Test.Parameters.default, property).passed shouldBe true
      }
    }

    "bind a create successor to one admitted collection identity" must
      _successor_identity_metadata {
      "when the candidate id belongs to another collection" in {
        Given(
          "Spec: docs/spec/entity-conflict-and-conditional-transition.md; Rules: R8,R14; Example: E19; a create codec declaring a local collection and returning a foreign Entity id"
        )
        val foreigncollection =
          EntityCollectionId("test", "foreign", "conditional_successor")
        val candidate =
          Root(
            EntityId("test", "foreign_successor", foreigncollection),
            "open"
          )
        val create = new EntityPersistentCreate[Root] {
          def id(entity: Root): Option[EntityId] =
            Some(entity.id)
          def collection(entity: Root): EntityCollectionId = {
            val _ = entity
            _collectionid
          }
          def toRecord(entity: Root): Record =
            _root_persistent.toRecord(entity)
        }

        When("the create successor intent admits the candidate identity")
        val result =
          EntitySuccessorIntent.create[Root, Root](candidate)(
            using create,
            _root_persistent
          )

        Then("construction fails with the mismatched id collection identified")
        result shouldBe a[Consequence.Failure[?]]
        result match {
          case Consequence.Failure(conclusion) =>
            val diagnostic = ConclusionDiagnostics.classify(conclusion)
            diagnostic.parameter shouldBe Some("successor.id.collection")
          case _ =>
            fail("expected successor collection mismatch")
        }
      }
    }
  }

  private final case class Root(
    id: EntityId,
    status: String
  )

  private val _root_persistent: EntityPersistent[Root] =
    new EntityPersistent[Root] {
      def id(entity: Root): EntityId = entity.id
      def toRecord(entity: Root): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "status" -> entity.status
        )
      override def toStoreRecord(entity: Root): Record =
        Record.dataAuto(
          "id" -> entity.id,
          "root_status" -> entity.status
        )
      def fromRecord(record: Record): Consequence[Root] =
        (record.getAs[EntityId]("id"), record.getString("status")) match {
          case (Some(id), Some(status)) =>
            Consequence.success(Root(id, status))
          case _ =>
            Consequence.argumentInvalid(
              "root",
              "id and status",
              record
            )
        }
      override def fromStoreRecord(record: Record): Consequence[Root] =
        (record.getAs[EntityId]("id"), record.getString("root_status")) match {
          case (Some(id), Some(status)) =>
            Consequence.success(Root(id, status))
          case _ =>
            Consequence.argumentInvalid(
              "root",
              "id and root_status",
              record
            )
        }
      override def storeFieldName(logicalName: String): String =
        if (logicalName == "status") "root_status" else logicalName
    }
}
