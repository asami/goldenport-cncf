package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.knowledge.{ExternalKnowledgeIdentifier, KnowledgeFrameId, RdfNodeName}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 * @version May. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationSpaceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "InformationSpace" should {
    "register validate confirm publish and clear information records" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(
        Record.data(
          "title" -> "Knowledge import",
          "authors" -> "Alice, Bob",
          "venue" -> "CNCF Journal"
        )
      )))
      val recordid = batch.head.id

      space.counts.informationCount shouldBe 1
      space.getInformation(recordid).map(_.state) shouldBe Some(InformationLifecycleState.Imported)
      batch.map(_.id) shouldBe Vector(recordid)

      val validated = _success(space.validateInformation(recordid))
      validated.state shouldBe InformationLifecycleState.ReadyForConfirmation
      space.validationIssues(recordid) shouldBe Vector.empty

      val item = _success(space.confirmInformation(recordid))
      item.state shouldBe InformationLifecycleState.Confirmed
      space.getInformation(item.id) shouldBe Some(item)

      val publication = _success(space.publishInformation(item.id, "rdf-vector", Some("published"), Some(KnowledgeFrameId("frame-1"))))
      publication.state shouldBe InformationPublicationState.Published
      publication.knowledgeFrameId shouldBe Some(KnowledgeFrameId("frame-1"))
      space.getInformation(item.id).map(_.state) shouldBe Some(InformationLifecycleState.Published)

      space.clear()
      space.counts shouldBe InformationSpaceCounts()
    }

    "record failed publication status without publishing the item" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Draft", "authors" -> "Alice"))))
      val recordid = batch.head.id
      _success(space.validateInformation(recordid))
      val item = _success(space.confirmInformation(recordid))

      val publication = _success(space.failInformationPublication(
        item.id,
        "rdf-vector",
        Some("knowledge-space materialization failed"),
        Some(KnowledgeFrameId("frame-failed"))
      ))

      publication.state shouldBe InformationPublicationState.Failed
      publication.knowledgeFrameId shouldBe Some(KnowledgeFrameId("frame-failed"))
      space.getInformation(item.id).map(_.state) shouldBe Some(InformationLifecycleState.Confirmed)
      space.getInformation(item.id).flatMap(_.publicationStatuses.headOption.map(_.publicationKey)) shouldBe Some(publication.publicationKey)
    }

    "reject invalid records before confirmation" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> ""))))
      val recordid = batch.head.id

      val validated = _success(space.validateInformation(recordid))

      validated.state shouldBe InformationLifecycleState.Invalid
      space.validationIssues(recordid).map(_.fieldPath) shouldBe Vector("title")
      space.confirmInformation(recordid) shouldBe a[Consequence.Failure[_]]
    }

    "allow paper confirmation with title only" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Title-only Paper"))))
      val recordid = batch.head.id

      val validated = _success(space.validateInformation(recordid))
      val item = _success(space.confirmInformation(recordid))

      validated.state shouldBe InformationLifecycleState.ReadyForConfirmation
      item.domain shouldBe "paper"
    }

    "validate web resources with title and URL requirements" in {
      val space = new InformationSpace
      val invalidbatch = _success(space.registerInformation("web-resource", Vector(Record.data("title" -> ""))))
      val invalidid = invalidbatch.head.id

      val invalid = _success(space.validateInformation(invalidid))

      invalid.state shouldBe InformationLifecycleState.Invalid
      space.validationIssues(invalidid).map(_.fieldPath).toSet shouldBe Set("title", "url")

      val validbatch = _success(space.registerInformation("web-resource", Vector(Record.data(
        "title" -> "KnowledgeSpace Web Resource",
        "url" -> "https://example.org/knowledge"
      ))))
      val validid = validbatch.head.id
      val validated = _success(space.validateInformation(validid))
      val item = _success(space.confirmInformation(validid))

      validated.state shouldBe InformationLifecycleState.ReadyForConfirmation
      item.domain shouldBe "web-resource"
    }

    "reject unvalidated records before confirmation" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Draft", "authors" -> "Alice"))))

      space.confirmInformation(batch.head.id) shouldBe a[Consequence.Failure[_]]
    }

    "track conflicts explicitly" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Local", "authors" -> "Alice"))))
      val recordid = batch.head.id
      _success(space.validateInformation(recordid))
      val item = _success(space.confirmInformation(recordid))

      val conflict = _success(space.recordConflict(item.id, "title", "Local", "Remote"))
      val resolved = _success(space.resolveConflict(item.id, conflict.conflictKey, "keep-information"))

      conflict.state shouldBe InformationConflictState.Open
      resolved.state shouldBe InformationConflictState.Resolved
      resolved.resolution shouldBe Some("keep-information")
      space.getInformation(item.id).map(_.state) shouldBe Some(InformationLifecycleState.Confirmed)
    }

    "clear resolution candidate and its identity binding" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Knowledge import", "authors" -> "Alice"))))
      val recordid = batch.head.id
      val candidate = _success(space.addResolutionCandidate(
        recordid,
        "dbpediaUri",
        "Knowledge import",
        InformationIdentityBinding(
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
      space.getInformation(recordid).map(_.state) shouldBe Some(InformationLifecycleState.NeedsResolution)

      val removed = _success(space.clearResolutionCandidate(recordid, candidate.candidateKey))

      removed.candidateKey shouldBe candidate.candidateKey
      space.resolutionCandidates(recordid) shouldBe Vector.empty
      space.counts.resolutionCandidateCount shouldBe 0
      space.counts.identityBindingCount shouldBe 0
      space.getInformation(recordid).map(_.state) shouldBe Some(InformationLifecycleState.Imported)
      space.getInformation(recordid).map(_.resolutionCandidates) shouldBe Some(Vector.empty)
      space.getInformation(recordid).map(_.identityBindings) shouldBe Some(Vector.empty)
    }

    "reject clearing candidates after record confirmation" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Knowledge import", "authors" -> "Alice"))))
      val recordid = batch.head.id
      _success(space.validateInformation(recordid))
      val candidate = _success(space.addResolutionCandidate(
        recordid,
        "dbpediaUri",
        "Knowledge import",
        InformationIdentityBinding(
          rdfSubject = Some(RdfNodeName("https://dbpedia.org/resource/Knowledge_graph")),
          externalIdentifiers = Vector(ExternalKnowledgeIdentifier("dbpedia", "https://dbpedia.org/resource/Knowledge_graph", Some("resource"))),
          authority = Some("dbpedia"),
          confidence = Some(0.72)
        ),
        Some(0.72),
        Some("dbpedia lookup")
      ))
      _success(space.selectResolutionCandidate(recordid, candidate.candidateKey))
      val item = _success(space.confirmInformation(recordid))

      space.clearResolutionCandidate(recordid, candidate.candidateKey) shouldBe a[Consequence.Failure[_]]
      space.resolutionCandidates(recordid).map(_.candidateKey) shouldBe Vector(candidate.candidateKey)
      space.counts.identityBindingCount shouldBe 1
      space.getInformation(item.id).map(_.identityBindings.map(_.status)) shouldBe Some(Vector(InformationBindingStatus.Confirmed))
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
