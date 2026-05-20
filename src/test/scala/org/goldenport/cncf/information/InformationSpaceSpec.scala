package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.knowledge.KnowledgeFrameId
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationSpaceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "InformationSpace" should {
    "register validate confirm publish and clear information records" in {
      val space = new InformationSpace
      val batch = _success(space.registerImportBatch("paper", Vector(
        Record.data(
          "title" -> "Knowledge import",
          "authors" -> "Alice, Bob",
          "venue" -> "CNCF Journal"
        )
      )))
      val recordid = batch.recordIds.head

      space.counts.batchCount shouldBe 1
      space.counts.recordCount shouldBe 1
      space.importRecordOption(recordid).map(_.state) shouldBe Some(InformationLifecycleState.Imported)
      space.listImportRecords(batch.id).map(_.id) shouldBe Vector(recordid)

      val validated = _success(space.validateInformationRecord(recordid))
      validated.state shouldBe InformationLifecycleState.ReadyForConfirmation
      space.validationIssues(recordid) shouldBe Vector.empty

      val item = _success(space.confirmInformationRecord(recordid))
      item.state shouldBe InformationLifecycleState.Confirmed
      space.informationItemOption(item.id) shouldBe Some(item)

      val publication = _success(space.publishInformationItem(item.id, "rdf-vector", Some("published"), Some(KnowledgeFrameId("frame-1"))))
      publication.state shouldBe InformationPublicationState.Published
      publication.knowledgeFrameId shouldBe Some(KnowledgeFrameId("frame-1"))
      space.informationItemOption(item.id).map(_.state) shouldBe Some(InformationLifecycleState.Published)

      space.clear()
      space.counts shouldBe InformationSpaceCounts()
    }

    "reject invalid records before confirmation" in {
      val space = new InformationSpace
      val batch = _success(space.registerImportBatch("paper", Vector(Record.data("title" -> ""))))
      val recordid = batch.recordIds.head

      val validated = _success(space.validateInformationRecord(recordid))

      validated.state shouldBe InformationLifecycleState.Invalid
      space.validationIssues(recordid).map(_.fieldPath) should contain allOf ("title", "authors")
      space.confirmInformationRecord(recordid) shouldBe a[Consequence.Failure[_]]
    }

    "reject unvalidated records before confirmation" in {
      val space = new InformationSpace
      val batch = _success(space.registerImportBatch("paper", Vector(Record.data("title" -> "Draft", "authors" -> "Alice"))))

      space.confirmInformationRecord(batch.recordIds.head) shouldBe a[Consequence.Failure[_]]
    }

    "track conflicts explicitly" in {
      val space = new InformationSpace
      val batch = _success(space.registerImportBatch("paper", Vector(Record.data("title" -> "Local", "authors" -> "Alice"))))
      val recordid = batch.recordIds.head
      _success(space.validateInformationRecord(recordid))
      val item = _success(space.confirmInformationRecord(recordid))

      val conflict = _success(space.recordConflict(item.id, "title", "Local", "Remote"))
      val resolved = _success(space.resolveConflict(conflict.id, "keep-information"))

      conflict.state shouldBe InformationConflictState.Open
      resolved.state shouldBe InformationConflictState.Resolved
      resolved.resolution shouldBe Some("keep-information")
      space.informationItemOption(item.id).map(_.state) shouldBe Some(InformationLifecycleState.Confirmed)
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
