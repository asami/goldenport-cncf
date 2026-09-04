package org.goldenport.cncf.information

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.information.entity.Information
import org.goldenport.cncf.information.value.{
  InformationBindingStatus,
  InformationConflictState,
  InformationFieldEvent,
  InformationFieldState,
  InformationIdentityBinding,
  InformationLifecycleState,
  InformationPublicationState,
  InformationSpaceCounts
}
import org.goldenport.cncf.knowledge.{ExternalKnowledgeIdentifier, KnowledgeFrameId, RdfNodeName}
import org.goldenport.observation.Taxonomy
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 31, 2026
 * @version Aug. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationCurationKnowledgeLifecycleSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private given ExecutionContext = ExecutionContext.test()

  "Information curation lifecycle" should {
    "preserve raw import data, working edits, field-event audit, and deterministic observations" in {
      Given("an InformationSpace with imported paper records and a pending curator edit")
      val space = new InformationSpace
      val imported = _success(space.registerInformation("paper", Vector(
        Record.data("title" -> "Imported title", "authors" -> "Alice"),
        Record.data("title" -> "Other title", "authors" -> "Bob")
      )))
      val first = imported.head
      val edited = Record.data("title" -> "Curated title", "authors" -> "Alice")
      val event = InformationFieldEvent(
        fieldPath = "title",
        state = InformationFieldState.editing,
        source = "curator",
        operation = Some("edit"),
        provider = None,
        transformation = None,
        valueBefore = Some("Imported title"),
        valueAfter = Some("Curated title"),
        evidence = None,
        note = None,
        occurredAt = Instant.EPOCH,
        actor = None
      )

      When("the working record is edited")
      _success(space.updateInformation(first.id, edited))

      When("the field event is appended to the edited record")
      val audited = _success(space.appendFieldEvent(first.id, event))

      Then("raw data, working data, and field-event audit remain distinct")
      audited.rawData shouldBe first.rawData
      audited.workingData shouldBe edited
      audited.fieldEvents shouldBe Vector(event)

      Then("snapshots, counts, and information queries expose the deterministic imported set")
      space.snapshot.information.map(_.id).toSet shouldBe imported.map(_.id).toSet
      space.counts shouldBe InformationSpaceCounts(2, 0, 0, 0, 0, 0)
      space.getInformation(first.id) shouldBe Some(audited)
      space.searchInformation(Some("paper")).map(_.id).toSet shouldBe imported.map(_.id).toSet
      space.searchInformation(Some("web-resource")) shouldBe Vector.empty
    }

    "select validation state from invalid and unresolved evidence and keep candidate bindings aligned" in {
      Given("imported invalid and unresolved paper records with an available resolution candidate")
      val space = new InformationSpace
      val invalid = _registered(space, "")
      val unresolved = _registered(space, "Resolvable")
      val candidate = _success(space.addResolutionCandidate(
        unresolved.id,
        "rdfSubject",
        "Resolvable RDF subject",
        _binding,
        Some(0.8),
        Some("resolver evidence")
      ))

      When("the invalid record is validated")
      val invalidvalidated = _success(space.validateInformation(invalid.id))

      Then("validation marks the invalid record as invalid and reports its field-level issue")
      invalidvalidated.state shouldBe InformationLifecycleState.invalid
      space.validationIssues(invalid.id).map(_.fieldPath) shouldBe Vector("title")

      When("the unresolved record is validated")
      val unresolvedvalidated = _success(space.validateInformation(unresolved.id))

      Then("validation marks the unresolved record as needing resolution")
      unresolvedvalidated.state shouldBe InformationLifecycleState.needs_resolution

      When("the resolution candidate is selected")
      val selected = _success(space.selectResolutionCandidate(unresolved.id, candidate.candidateKey))

      Then("selection marks the candidate and aligns its binding and lifecycle state")
      selected.selected shouldBe true
      selected.binding.status shouldBe InformationBindingStatus.selected
      space.getInformation(unresolved.id).map(_.identityBindings.map(_.status)) shouldBe
        Some(Vector(InformationBindingStatus.selected))
      space.getInformation(unresolved.id).map(_.state) shouldBe Some(InformationLifecycleState.ready_for_confirmation)

      When("the selected resolution candidate is cleared")
      val cleared = _success(space.clearResolutionCandidate(unresolved.id, candidate.candidateKey))

      Then("clearing removes the candidate binding without fabricating a lifecycle transition")
      cleared shouldBe selected
      space.resolutionCandidates(unresolved.id) shouldBe Vector.empty
      space.getInformation(unresolved.id).map(_.identityBindings) shouldBe Some(Vector.empty)
      space.getInformation(unresolved.id).map(_.state) shouldBe Some(InformationLifecycleState.ready_for_confirmation)
    }

    "reject each CML-permitted source-state facet" in {
      Vector(
        InformationLifecycleState.imported,
        InformationLifecycleState.invalid,
        InformationLifecycleState.needs_resolution,
        InformationLifecycleState.ready_for_confirmation
      ).foreach { sourcestate =>
        Given(s"an Information record in the $sourcestate lifecycle source state")
        val space = new InformationSpace
        val information = _information_in_state(space, sourcestate)

        When("rejecting the Information record")
        val rejected = _success(space.rejectInformation(information.id, "curation rejected"))

        Then(s"the $sourcestate source state transitions to rejected")
        rejected.state shouldBe InformationLifecycleState.rejected
        rejected.revision.value should be > information.revision.value
        space.getInformation(information.id) shouldBe Some(rejected)
        space.getInformation(information.id).map(_.revision) shouldBe Some(rejected.revision)
      }
    }

    "deny reject for each CML-forbidden source-state facet without mutation" in {
      Vector(
        InformationLifecycleState.confirmed,
        InformationLifecycleState.published,
        InformationLifecycleState.rejected,
        InformationLifecycleState.conflict
      ).foreach { sourcestate =>
        Given(s"an Information record in the $sourcestate lifecycle source state")
        val space = new InformationSpace
        val information = _information_in_state(space, sourcestate)
        val before = space.getInformation(information.id)
        val snapshotbefore = space.snapshot

        When("rejecting the Information record")
        val result = space.rejectInformation(information.id, "curation rejected")

        Then(s"the $sourcestate source state is denied with an argument-invalid failure")
        _argument_invalid(result)
        space.getInformation(information.id) shouldBe before
        space.getInformation(information.id).map(_.revision) shouldBe before.map(_.revision)
        space.snapshot shouldBe snapshotbefore
        _success(space.getInformationC(information.id)) shouldBe Some(before.getOrElse(fail("persisted Information is missing")))
      }
    }

    "reopen each CML-permitted source-state facet" in {
      Vector(
        InformationLifecycleState.confirmed,
        InformationLifecycleState.rejected
      ).foreach { sourcestate =>
        Given(s"an Information record in the $sourcestate lifecycle source state")
        val space = new InformationSpace
        val information = _information_in_state(space, sourcestate)

        When("reopening the Information record")
        val reopened = _success(space.reopenInformation(information.id))

        Then(s"the $sourcestate source state transitions to imported")
        reopened.state shouldBe InformationLifecycleState.imported
        reopened.revision.value should be > information.revision.value
        space.getInformation(information.id) shouldBe Some(reopened)
        space.getInformation(information.id).map(_.revision) shouldBe Some(reopened.revision)
      }
    }

    "deny reopen for each CML-forbidden source-state facet without mutation" in {
      Vector(
        InformationLifecycleState.imported,
        InformationLifecycleState.invalid,
        InformationLifecycleState.needs_resolution,
        InformationLifecycleState.ready_for_confirmation,
        InformationLifecycleState.published,
        InformationLifecycleState.conflict
      ).foreach { sourcestate =>
        Given(s"an Information record in the $sourcestate lifecycle source state")
        val space = new InformationSpace
        val information = _information_in_state(space, sourcestate)
        val before = space.getInformation(information.id)
        val snapshotbefore = space.snapshot

        When("reopening the Information record")
        val result = space.reopenInformation(information.id)

        Then(s"the $sourcestate source state is denied with an argument-invalid failure")
        _argument_invalid(result)
        space.getInformation(information.id) shouldBe before
        space.getInformation(information.id).map(_.revision) shouldBe before.map(_.revision)
        space.snapshot shouldBe snapshotbefore
        _success(space.getInformationC(information.id)) shouldBe Some(before.getOrElse(fail("persisted Information is missing")))
      }
    }

    "preserve failed-publication state and publish confirmed Information" in {
      Given("a confirmed Information record")
      val space = new InformationSpace
      val confirmed = _information_in_state(space, InformationLifecycleState.confirmed)

      When("publication failure is recorded for the confirmed record")
      val failed = _success(space.failInformationPublication(confirmed.id, "rdf", Some("provider failed")))

      Then("failed publication preserves the confirmed Information lifecycle state")
      failed.state shouldBe InformationPublicationState.failed
      space.getInformation(confirmed.id).map(_.state) shouldBe Some(InformationLifecycleState.confirmed)

      When("publication succeeds for the confirmed record")
      val published = _success(space.publishInformation(confirmed.id, "rdf", Some("provider succeeded"), Some(KnowledgeFrameId("frame-1"))))

      Then("successful publication advances publication and Information lifecycle state")
      published.state shouldBe InformationPublicationState.published
      space.getInformation(confirmed.id).map(_.state) shouldBe Some(InformationLifecycleState.published)
    }

    "record and resolve conflicts without overwriting Information or RDF evidence" in {
      Given("a confirmed Information record with no open conflict")
      val space = new InformationSpace
      val registered = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Information title")))).head
      val validated = _success(space.validateInformation(registered.id))
      val confirmed = _success(space.confirmInformation(validated.id))

      When("an Information-versus-RDF title conflict is recorded")
      val conflict = _success(space.recordConflict(confirmed.id, "title", "Information title", "RDF title"))

      Then("the Information lifecycle enters conflict and exposes the open conflict")
      space.getInformation(confirmed.id).map(_.state) shouldBe Some(InformationLifecycleState.conflict)
      space.conflicts(Some(InformationConflictState.open)) shouldBe Vector(conflict)

      When("the conflict is resolved in favor of Information")
      val resolved = _success(space.resolveConflict(confirmed.id, conflict.conflictKey, "keep-information"))

      Then("resolution preserves both evidence values and the confirmed Information state")
      resolved.state shouldBe InformationConflictState.resolved
      resolved.resolution shouldBe Some("keep-information")
      resolved.informationValue shouldBe "Information title"
      resolved.rdfValue shouldBe "RDF title"
      space.getInformation(confirmed.id).map(_.workingData.getString("title")) shouldBe Some(Some("Information title"))
      space.getInformation(confirmed.id).map(_.state) shouldBe Some(InformationLifecycleState.confirmed)
      space.counts.conflictCount shouldBe 1
    }

    "admit declared update routes and deny undeclared validation without mutation" in {
      Given("an invalid Information record and a ready-for-confirmation Information record")
      val space = new InformationSpace
      val invalid = _information_in_state(space, InformationLifecycleState.invalid)
      val ready = _information_in_state(space, InformationLifecycleState.ready_for_confirmation)

      Given("the invalid record and its persisted and cached values before revalidation")
      val invalidbefore = space.getInformation(invalid.id)
      val invalidsnapshotbefore = space.snapshot

      When("validation is repeated from invalid without an imported admission state")
      val invalidvalidation = space.validateInformation(invalid.id)

      Then("the undeclared invalid validation event is rejected without persisted, revision, snapshot, or cache mutation")
      _argument_invalid(invalidvalidation)
      space.getInformation(invalid.id) shouldBe invalidbefore
      space.getInformation(invalid.id).map(_.revision) shouldBe invalidbefore.map(_.revision)
      space.snapshot shouldBe invalidsnapshotbefore
      _success(space.getInformationC(invalid.id)) shouldBe Some(invalidbefore.getOrElse(fail("persisted Information is missing")))

      When("the invalid record is edited")
      val updated = _success(space.updateInformation(invalid.id, Record.data("title" -> "Corrected title")))

      Then("the declared invalid-to-imported update route is admitted")
      updated.state shouldBe InformationLifecycleState.imported
      updated.revision.value should be > invalid.revision.value

      Given("the ready record before an edit that advances it")

      When("the ready record is edited")
      val readyupdated = _success(space.updateInformation(ready.id, Record.data("title" -> "Changed ready title")))

      Then("the declared ready-to-imported update route is admitted")
      readyupdated.state shouldBe InformationLifecycleState.imported
      readyupdated.revision.value should be > ready.revision.value
    }

    "resolve a published-origin conflict to confirmed rather than restoring published" in {
      Given("a published Information record")
      val space = new InformationSpace
      val published = _information_in_state(space, InformationLifecycleState.published)

      When("a conflict is detected for the published record")
      val conflict = _success(space.recordConflict(published.id, "title", "Published title", "RDF title"))

      Then("the declared published-to-conflict route is admitted")
      space.getInformation(published.id).map(_.state) shouldBe Some(InformationLifecycleState.conflict)

      When("the published-origin conflict is resolved")
      val resolved = _success(space.resolveConflict(published.id, conflict.conflictKey, "keep-information"))

      Then("the only declared conflict-resolution route reaches confirmed")
      resolved.state shouldBe InformationConflictState.resolved
      space.getInformation(published.id).map(_.state) shouldBe Some(InformationLifecycleState.confirmed)
    }
  }

  private def _information_in_state(
    space: InformationSpace,
    targetstate: InformationLifecycleState
  ): Information =
    targetstate match {
      case InformationLifecycleState.imported =>
        _registered(space, "Imported")
      case InformationLifecycleState.invalid =>
        val information = _registered(space, "")
        _success(space.validateInformation(information.id))
      case InformationLifecycleState.needs_resolution =>
        val information = _registered(space, "Needs resolution")
        _success(space.addResolutionCandidate(information.id, "rdfSubject", "Candidate", _binding))
        _success(space.validateInformation(information.id))
      case InformationLifecycleState.ready_for_confirmation =>
        val information = _registered(space, "Ready")
        _success(space.validateInformation(information.id))
      case InformationLifecycleState.confirmed =>
        val information = _information_in_state(space, InformationLifecycleState.ready_for_confirmation)
        _success(space.confirmInformation(information.id))
      case InformationLifecycleState.published =>
        val information = _information_in_state(space, InformationLifecycleState.confirmed)
        _success(space.publishInformation(information.id, "rdf"))
        space.getInformation(information.id).getOrElse(fail("published Information is missing"))
      case InformationLifecycleState.rejected =>
        val information = _information_in_state(space, InformationLifecycleState.imported)
        _success(space.rejectInformation(information.id, "curation rejected"))
      case InformationLifecycleState.conflict =>
        val information = _information_in_state(space, InformationLifecycleState.confirmed)
        _success(space.recordConflict(information.id, "title", "Information title", "RDF title"))
        space.getInformation(information.id).getOrElse(fail("conflicted Information is missing"))
    }

  private def _registered(space: InformationSpace, title: String): Information =
    _success(space.registerInformation("paper", Vector(Record.data("title" -> title)))).head

  private def _argument_invalid[A](result: Consequence[A]): Unit =
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.observation.taxonomy.category shouldBe Taxonomy.Category.Argument
        conclusion.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Invalid
      case Consequence.Success(value) =>
        fail(s"expected argument-invalid failure, got $value")
    }

  private def _binding =
    InformationIdentityBinding(
      rdfSubject = Some(RdfNodeName("https://example.org/resource/resolvable")),
      externalIdentifiers = Vector(ExternalKnowledgeIdentifier("example", "resolvable", Some("resource"))),
      entityBindings = Vector.empty,
      knowledgeNodeId = None,
      authority = Some("example"),
      confidence = None,
      status = InformationBindingStatus.candidate
    )

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
