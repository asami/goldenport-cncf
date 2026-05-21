package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.knowledge.{ExternalKnowledgeIdentifier, KnowledgeFrameId, RdfNodeName}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 * @version May. 22, 2026
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

    "record failed publication status without publishing the item" in {
      val space = new InformationSpace
      val batch = _success(space.registerImportBatch("paper", Vector(Record.data("title" -> "Draft", "authors" -> "Alice"))))
      val recordid = batch.recordIds.head
      _success(space.validateInformationRecord(recordid))
      val item = _success(space.confirmInformationRecord(recordid))

      val publication = _success(space.failInformationItemPublication(
        item.id,
        "rdf-vector",
        Some("knowledge-space materialization failed"),
        Some(KnowledgeFrameId("frame-failed"))
      ))

      publication.state shouldBe InformationPublicationState.Failed
      publication.knowledgeFrameId shouldBe Some(KnowledgeFrameId("frame-failed"))
      space.informationItemOption(item.id).map(_.state) shouldBe Some(InformationLifecycleState.Confirmed)
      space.informationItemOption(item.id).flatMap(_.publicationId) shouldBe Some(publication.id)
    }

    "reject invalid records before confirmation" in {
      val space = new InformationSpace
      val batch = _success(space.registerImportBatch("paper", Vector(Record.data("title" -> ""))))
      val recordid = batch.recordIds.head

      val validated = _success(space.validateInformationRecord(recordid))

      validated.state shouldBe InformationLifecycleState.Invalid
      space.validationIssues(recordid).map(_.fieldPath) shouldBe Vector("title")
      space.confirmInformationRecord(recordid) shouldBe a[Consequence.Failure[_]]
    }

    "allow paper confirmation with title only" in {
      val space = new InformationSpace
      val batch = _success(space.registerImportBatch("paper", Vector(Record.data("title" -> "Title-only Paper"))))
      val recordid = batch.recordIds.head

      val validated = _success(space.validateInformationRecord(recordid))
      val item = _success(space.confirmInformationRecord(recordid))

      validated.state shouldBe InformationLifecycleState.ReadyForConfirmation
      item.domain shouldBe "paper"
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

    "clear resolution candidate and its identity binding" in {
      val space = new InformationSpace
      val batch = _success(space.registerImportBatch("paper", Vector(Record.data("title" -> "Knowledge import", "authors" -> "Alice"))))
      val recordid = batch.recordIds.head
      val candidate = _success(space.addResolutionCandidate(
        recordid,
        "dbpediaUri",
        "Knowledge import",
        InformationIdentityBinding(
          id = InformationIdentityBindingId("pending"),
          recordId = Some(recordid),
          rdfSubject = Some(RdfNodeName("https://dbpedia.org/resource/Knowledge_graph")),
          externalIdentifiers = Vector(ExternalKnowledgeIdentifier("dbpedia", "https://dbpedia.org/resource/Knowledge_graph", Some("resource"))),
          authority = Some("dbpedia"),
          confidence = Some(0.72)
        ),
        Some(0.72),
        Some("dbpedia lookup")
      ))

      space.counts.resolutionCandidateCount shouldBe 1
      space.counts.identityBindingCount shouldBe 1
      space.importRecordOption(recordid).map(_.state) shouldBe Some(InformationLifecycleState.NeedsResolution)

      val removed = _success(space.clearResolutionCandidate(candidate.id))

      removed.id shouldBe candidate.id
      space.resolutionCandidates(recordid) shouldBe Vector.empty
      space.counts.resolutionCandidateCount shouldBe 0
      space.counts.identityBindingCount shouldBe 0
      space.importRecordOption(recordid).map(_.state) shouldBe Some(InformationLifecycleState.Imported)
      space.importRecordOption(recordid).map(_.resolutionCandidateIds) shouldBe Some(Vector.empty)
      space.importRecordOption(recordid).map(_.identityBindingIds) shouldBe Some(Vector.empty)
    }

    "reject clearing candidates after record confirmation" in {
      val space = new InformationSpace
      val batch = _success(space.registerImportBatch("paper", Vector(Record.data("title" -> "Knowledge import", "authors" -> "Alice"))))
      val recordid = batch.recordIds.head
      _success(space.validateInformationRecord(recordid))
      val candidate = _success(space.addResolutionCandidate(
        recordid,
        "dbpediaUri",
        "Knowledge import",
        InformationIdentityBinding(
          id = InformationIdentityBindingId("pending"),
          recordId = Some(recordid),
          rdfSubject = Some(RdfNodeName("https://dbpedia.org/resource/Knowledge_graph")),
          externalIdentifiers = Vector(ExternalKnowledgeIdentifier("dbpedia", "https://dbpedia.org/resource/Knowledge_graph", Some("resource"))),
          authority = Some("dbpedia"),
          confidence = Some(0.72)
        ),
        Some(0.72),
        Some("dbpedia lookup")
      ))
      val selected = _success(space.selectResolutionCandidate(candidate.id))
      val item = _success(space.confirmInformationRecord(recordid))

      space.clearResolutionCandidate(candidate.id) shouldBe a[Consequence.Failure[_]]
      space.resolutionCandidates(recordid).map(_.id) shouldBe Vector(candidate.id)
      space.counts.identityBindingCount shouldBe 1
      space.informationItemOption(item.id).map(_.identityBindingIds) shouldBe Some(Vector(selected.binding.id))
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
