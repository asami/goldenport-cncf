package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.knowledge.{ExternalKnowledgeIdentifier, KnowledgeFrameId, RdfNodeName}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 *  version May. 25, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationSpaceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private given ExecutionContext = ExecutionContext.test()

  "InformationSpace" should {
    "register validate confirm publish and clear information records" in {
      Given("an empty InformationSpace and a valid paper record")
      val space = new InformationSpace

      When("the record is registered")
      val batch = _success(space.registerInformation("paper", Vector(
        Record.data(
          "title" -> "Knowledge import",
          "authors" -> "Alice, Bob",
          "venue" -> "CNCF Journal"
        )
      )))
      val recordid = batch.head.id

      Then("the imported state is retained")
      space.counts.informationCount shouldBe 1
      space.getInformation(recordid).map(_.state) shouldBe Some(InformationLifecycleState.Imported)
      batch.map(_.id) shouldBe Vector(recordid)

      When("the imported record is validated")
      val validated = _success(space.validateInformation(recordid))

      Then("the record becomes ready for confirmation")
      validated.state shouldBe InformationLifecycleState.ReadyForConfirmation
      space.validationIssues(recordid) shouldBe Vector.empty

      When("the ready record is confirmed")
      val item = _success(space.confirmInformation(recordid))

      Then("the confirmed state is retained")
      item.state shouldBe InformationLifecycleState.Confirmed
      space.getInformation(item.id) shouldBe Some(item)

      When("the confirmed record is published")
      val publication = _success(space.publishInformation(item.id, "rdf-vector", Some("published"), Some(KnowledgeFrameId("frame-1"))))

      Then("the publication links the KnowledgeFrame and advances the lifecycle")
      publication.state shouldBe InformationPublicationState.Published
      publication.knowledgeFrameId shouldBe Some(KnowledgeFrameId("frame-1"))
      space.getInformation(item.id).map(_.state) shouldBe Some(InformationLifecycleState.Published)

      When("the InformationSpace is cleared")
      space.clear()

      Then("its lifecycle records are removed")
      space.counts shouldBe InformationSpaceCounts()
    }

    "record failed publication status without publishing the item" in {
      Given("a confirmed paper in InformationSpace")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Draft", "authors" -> "Alice"))))
      val recordid = batch.head.id
      _success(space.validateInformation(recordid))
      val item = _success(space.confirmInformation(recordid))

      When("publication failure is recorded")
      val publication = _success(space.failInformationPublication(
        item.id,
        "rdf-vector",
        Some("knowledge-space materialization failed"),
        Some(KnowledgeFrameId("frame-failed"))
      ))

      Then("the failure is retained without advancing the Information lifecycle")
      publication.state shouldBe InformationPublicationState.Failed
      publication.knowledgeFrameId shouldBe Some(KnowledgeFrameId("frame-failed"))
      space.getInformation(item.id).map(_.state) shouldBe Some(InformationLifecycleState.Confirmed)
      space.getInformation(item.id).flatMap(_.publicationStatuses.headOption.map(_.publicationKey)) shouldBe Some(publication.publicationKey)
    }

    "reject invalid records before confirmation" in {
      Given("a paper record with an invalid empty title")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> ""))))
      val recordid = batch.head.id

      When("the record is validated and confirmation is attempted")
      val validated = _success(space.validateInformation(recordid))

      Then("validation identifies the title and confirmation fails")
      validated.state shouldBe InformationLifecycleState.Invalid
      space.validationIssues(recordid).map(_.fieldPath) shouldBe Vector("title")
      space.confirmInformation(recordid) shouldBe a[Consequence.Failure[_]]
    }

    "allow paper confirmation with title only" in {
      Given("a paper with the minimum title field")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Title-only Paper"))))
      val recordid = batch.head.id

      When("the paper is validated and confirmed")
      val validated = _success(space.validateInformation(recordid))
      val item = _success(space.confirmInformation(recordid))

      Then("the title-only paper reaches the confirmed lifecycle")
      validated.state shouldBe InformationLifecycleState.ReadyForConfirmation
      item.domain shouldBe "paper"
    }

    "validate web resources with title and URL requirements" in {
      Given("an invalid web-resource record")
      val space = new InformationSpace
      val invalidbatch = _success(space.registerInformation("web-resource", Vector(Record.data("title" -> ""))))
      val invalidid = invalidbatch.head.id

      When("the incomplete record is validated")
      val invalid = _success(space.validateInformation(invalidid))

      Then("both required fields are reported")
      invalid.state shouldBe InformationLifecycleState.Invalid
      space.validationIssues(invalidid).map(_.fieldPath).toSet shouldBe Set("title", "url")

      Given("a web-resource record containing both required fields")
      val validbatch = _success(space.registerInformation("web-resource", Vector(Record.data(
        "title" -> "KnowledgeSpace Web Resource",
        "url" -> "https://example.org/knowledge"
      ))))
      val validid = validbatch.head.id
      When("the complete record is validated and confirmed")
      val validated = _success(space.validateInformation(validid))
      val item = _success(space.confirmInformation(validid))

      Then("it reaches the confirmed lifecycle")
      validated.state shouldBe InformationLifecycleState.ReadyForConfirmation
      item.domain shouldBe "web-resource"
    }

    "reject unvalidated records before confirmation" in {
      Given("an imported paper that has not been validated")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Draft", "authors" -> "Alice"))))

      When("confirmation is attempted directly")
      val result = space.confirmInformation(batch.head.id)

      Then("the lifecycle gate rejects the request")
      result shouldBe a[Consequence.Failure[_]]
    }

    "track conflicts explicitly" in {
      Given("a confirmed paper with divergent local and RDF values")
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Local", "authors" -> "Alice"))))
      val recordid = batch.head.id
      _success(space.validateInformation(recordid))
      val item = _success(space.confirmInformation(recordid))

      When("the conflict is recorded and resolved in favor of InformationSpace")
      val conflict = _success(space.recordConflict(item.id, "title", "Local", "Remote"))
      val resolved = _success(space.resolveConflict(item.id, conflict.conflictKey, "keep-information"))

      Then("the conflict history and confirmed lifecycle are preserved")
      conflict.state shouldBe InformationConflictState.Open
      resolved.state shouldBe InformationConflictState.Resolved
      resolved.resolution shouldBe Some("keep-information")
      space.getInformation(item.id).map(_.state) shouldBe Some(InformationLifecycleState.Confirmed)
    }

    "clear resolution candidate and its identity binding" in {
      Given("an imported paper with one resolution candidate and identity binding")
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

      And("the candidate and binding are visible before removal")
      space.counts.resolutionCandidateCount shouldBe 1
      space.counts.identityBindingCount shouldBe 1
      space.getInformation(recordid).map(_.state) shouldBe Some(InformationLifecycleState.NeedsResolution)

      When("the candidate is cleared before confirmation")
      val removed = _success(space.clearResolutionCandidate(recordid, candidate.candidateKey))

      Then("the candidate and its identity binding are both removed")
      removed.candidateKey shouldBe candidate.candidateKey
      space.resolutionCandidates(recordid) shouldBe Vector.empty
      space.counts.resolutionCandidateCount shouldBe 0
      space.counts.identityBindingCount shouldBe 0
      space.getInformation(recordid).map(_.state) shouldBe Some(InformationLifecycleState.Imported)
      space.getInformation(recordid).map(_.resolutionCandidates) shouldBe Some(Vector.empty)
      space.getInformation(recordid).map(_.identityBindings) shouldBe Some(Vector.empty)
    }

    "reject clearing candidates after record confirmation" in {
      Given("a confirmed paper whose selected candidate became a confirmed binding")
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

      When("candidate removal is attempted after confirmation")
      val result = space.clearResolutionCandidate(recordid, candidate.candidateKey)

      Then("the removal is rejected and the confirmed binding remains")
      result shouldBe a[Consequence.Failure[_]]
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
