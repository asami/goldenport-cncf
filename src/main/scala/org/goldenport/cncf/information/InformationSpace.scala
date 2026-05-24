package org.goldenport.cncf.information

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.knowledge.{
  ExternalKnowledgeIdentifier,
  KnowledgeAttributes,
  KnowledgeEvidence,
  KnowledgeEvidenceId,
  KnowledgeFact,
  KnowledgeFactId,
  KnowledgeFactKind,
  KnowledgeFrame,
  KnowledgeFrameId,
  KnowledgeFrameInputRoute,
  KnowledgeFrameKind,
  KnowledgeFrameOrigin,
  KnowledgeNode,
  KnowledgeNodeBindings,
  KnowledgeNodeCategory,
  KnowledgeNodeId,
  KnowledgeNodeIdentity,
  KnowledgeNodePresentation,
  KnowledgeNodeSources,
  KnowledgeProvenance,
  KnowledgeProvenanceId,
  KnowledgeSourceRef,
  KnowledgeTagBinding,
  KnowledgeWorkingSetSnapshot
}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.tag.TaggingWorkflow
import org.goldenport.record.Record

/*
 * @since   May. 20, 2026
 * @version May. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationSpace {
  private var _snapshot: InformationSpaceSnapshot = InformationSpaceSnapshot()

  def snapshot: InformationSpaceSnapshot =
    _snapshot

  def counts: InformationSpaceCounts =
    InformationSpaceCounts(
      informationCount = _snapshot.information.size,
      validationIssueCount = _snapshot.information.map(_.validationIssues.size).sum,
      resolutionCandidateCount = _snapshot.information.map(_.resolutionCandidates.size).sum,
      identityBindingCount = _snapshot.information.map(_.identityBindings.size).sum,
      publicationStatusCount = _snapshot.information.map(_.publicationStatuses.size).sum,
      conflictCount = _snapshot.information.map(_.conflicts.size).sum
    )

  def clear(): Unit =
    _snapshot = InformationSpaceSnapshot()

  def registerInformation(
    domain: String,
    records: Vector[Record]
  ): Consequence[Vector[Information]] =
    if (domain.trim.isEmpty)
      Consequence.argumentInvalid("information domain is required")
    else if (records.isEmpty)
      Consequence.argumentInvalid("information records are required")
    else {
      val base = _snapshot.information.size
      val values = records.zipWithIndex.map { case (record, index) =>
        Information(
          id = InformationId(_next_id("information", base + index + 1)),
          domain = domain,
          rawData = record,
          workingData = record
        )
      }
      _snapshot = _snapshot.copy(information = _snapshot.information ++ values)
      Consequence.success(values)
    }

  def getInformation(id: InformationId): Option[Information] =
    _snapshot.information.find(_.id == id)

  def updateInformation(
    informationid: InformationId,
    workingdata: Record
  ): Consequence[Information] =
    _update_information(informationid) { information =>
      information.copy(
        workingData = workingdata,
        state = InformationLifecycleState.Imported,
        validationIssues = Vector.empty,
        updatedAt = Instant.now()
      )
    }

  def validateInformation(informationid: InformationId): Consequence[Information] =
    getInformation(informationid) match {
      case Some(information) =>
        val issues = InformationSpace.validate(information)
        val state =
          if (issues.nonEmpty)
            InformationLifecycleState.Invalid
          else if (information.resolutionCandidates.exists(!_.selected))
            InformationLifecycleState.NeedsResolution
          else
            InformationLifecycleState.ReadyForConfirmation
        val updated = information.copy(
          state = state,
          validationIssues = issues,
          updatedAt = Instant.now()
        )
        _replace_information(updated)
        Consequence.success(updated)
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def validationIssues(informationid: InformationId): Vector[InformationValidationIssue] =
    getInformation(informationid).map(_.validationIssues).getOrElse(Vector.empty)

  def addResolutionCandidate(
    informationid: InformationId,
    fieldpath: String,
    label: String,
    binding: InformationIdentityBinding,
    confidence: Option[Double] = None,
    evidence: Option[String] = None
  ): Consequence[InformationResolutionCandidate] =
    getInformation(informationid) match {
      case Some(information) =>
        val key = _next_key("candidate", information.resolutionCandidates.size + 1)
        val nextbinding = binding.copy(status = InformationBindingStatus.Candidate)
        val candidate = InformationResolutionCandidate(key, fieldpath, label, nextbinding, confidence, evidence)
        val updated = information.copy(
          state = InformationLifecycleState.NeedsResolution,
          resolutionCandidates = information.resolutionCandidates :+ candidate,
          identityBindings = information.identityBindings :+ nextbinding,
          updatedAt = Instant.now()
        )
        _replace_information(updated)
        Consequence.success(candidate)
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def resolutionCandidates(informationid: InformationId): Vector[InformationResolutionCandidate] =
    getInformation(informationid).map(_.resolutionCandidates).getOrElse(Vector.empty)

  def selectResolutionCandidate(
    informationid: InformationId,
    candidatekey: String
  ): Consequence[InformationResolutionCandidate] =
    getInformation(informationid) match {
      case Some(information) =>
        information.resolutionCandidates.find(_.candidateKey == candidatekey) match {
          case Some(candidate) =>
            val selectedbinding = candidate.binding.copy(status = InformationBindingStatus.Selected)
            val selected = candidate.copy(binding = selectedbinding, selected = true)
            val candidates = information.resolutionCandidates.map(x => if (x.candidateKey == candidatekey) selected else x)
            val bindings = information.identityBindings.map { binding =>
              if (_same_binding(binding, candidate.binding)) selectedbinding else binding
            }
            val state = _state_after_candidate_update(information.copy(resolutionCandidates = candidates))
            val updated = information.copy(
              state = state,
              resolutionCandidates = candidates,
              identityBindings = bindings,
              updatedAt = Instant.now()
            )
            _replace_information(updated)
            Consequence.success(selected)
          case None =>
            Consequence.argumentInvalid(s"information resolution candidate not found: $candidatekey")
        }
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def clearResolutionCandidate(
    informationid: InformationId,
    candidatekey: String
  ): Consequence[InformationResolutionCandidate] =
    getInformation(informationid) match {
      case Some(information) if information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published =>
        Consequence.argumentInvalid(s"information is already confirmed: ${informationid.print}")
      case Some(information) =>
        information.resolutionCandidates.find(_.candidateKey == candidatekey) match {
          case Some(candidate) =>
            val candidates = information.resolutionCandidates.filterNot(_.candidateKey == candidatekey)
            val bindings = information.identityBindings.filterNot(_same_binding(_, candidate.binding))
            val updated = information.copy(
              state = _state_after_candidate_update(information.copy(resolutionCandidates = candidates)),
              resolutionCandidates = candidates,
              identityBindings = bindings,
              updatedAt = Instant.now()
            )
            _replace_information(updated)
            Consequence.success(candidate)
          case None =>
            Consequence.argumentInvalid(s"information resolution candidate not found: $candidatekey")
        }
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def confirmInformation(informationid: InformationId): Consequence[Information] =
    getInformation(informationid) match {
      case Some(information) if information.state == InformationLifecycleState.Invalid =>
        Consequence.argumentInvalid(s"information is invalid: ${informationid.print}")
      case Some(information) if information.state != InformationLifecycleState.ReadyForConfirmation && information.state != InformationLifecycleState.Confirmed =>
        Consequence.argumentInvalid(s"information is not ready for confirmation: ${informationid.print}")
      case Some(information) =>
        val bindings = information.identityBindings.map(_.copy(status = InformationBindingStatus.Confirmed))
        val confirmed = information.copy(
          state = InformationLifecycleState.Confirmed,
          identityBindings = bindings,
          confirmedAt = information.confirmedAt.orElse(Some(Instant.now())),
          updatedAt = Instant.now()
        )
        _replace_information(confirmed)
        Consequence.success(confirmed)
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def searchInformation(domain: Option[String] = None): Vector[Information] =
    domain match {
      case Some(value) => _snapshot.information.filter(_.domain == value)
      case None => _snapshot.information
    }

  def rejectInformation(
    informationid: InformationId,
    reason: String
  ): Consequence[Information] =
    _update_information(informationid)(_.copy(state = InformationLifecycleState.Rejected, updatedAt = Instant.now()))

  def reopenInformation(informationid: InformationId): Consequence[Information] =
    _update_information(informationid)(_.copy(state = InformationLifecycleState.ReadyForConfirmation, updatedAt = Instant.now()))

  def publishInformation(
    informationid: InformationId,
    target: String,
    message: Option[String] = None,
    knowledgeframeid: Option[KnowledgeFrameId] = None
  ): Consequence[InformationPublicationStatus] =
    getInformation(informationid) match {
      case Some(information) if information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published =>
        val key = information.publicationStatuses.headOption.map(_.publicationKey).getOrElse(_next_key("publication", 1))
        val publication = InformationPublicationStatus(
          publicationKey = key,
          state = InformationPublicationState.Published,
          target = target,
          message = message,
          knowledgeFrameId = knowledgeframeid,
          publishedAt = Some(Instant.now())
        )
        val published = information.copy(
          state = InformationLifecycleState.Published,
          publicationStatuses = information.publicationStatuses.filterNot(_.publicationKey == key) :+ publication,
          updatedAt = Instant.now()
        )
        _replace_information(published)
        Consequence.success(publication)
      case Some(_) =>
        Consequence.argumentInvalid(s"information is not confirmed: ${informationid.print}")
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def failInformationPublication(
    informationid: InformationId,
    target: String,
    message: Option[String] = None,
    knowledgeframeid: Option[KnowledgeFrameId] = None
  ): Consequence[InformationPublicationStatus] =
    getInformation(informationid) match {
      case Some(information) if information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published =>
        val key = information.publicationStatuses.headOption.map(_.publicationKey).getOrElse(_next_key("publication", 1))
        val publication = InformationPublicationStatus(
          publicationKey = key,
          state = InformationPublicationState.Failed,
          target = target,
          message = message,
          knowledgeFrameId = knowledgeframeid,
          publishedAt = Some(Instant.now())
        )
        val failed = information.copy(
          publicationStatuses = information.publicationStatuses.filterNot(_.publicationKey == key) :+ publication,
          updatedAt = Instant.now()
        )
        _replace_information(failed)
        Consequence.success(publication)
      case Some(_) =>
        Consequence.argumentInvalid(s"information is not confirmed: ${informationid.print}")
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def publicationStatusOption(
    informationid: InformationId,
    publicationkey: String
  ): Option[InformationPublicationStatus] =
    getInformation(informationid).flatMap(_.publicationStatuses.find(_.publicationKey == publicationkey))

  def recordConflict(
    informationid: InformationId,
    fieldpath: String,
    informationvalue: String,
    rdfvalue: String,
    severity: String = "warning"
  ): Consequence[InformationConflict] =
    getInformation(informationid) match {
      case Some(information) =>
        val conflict = InformationConflict(
          conflictKey = _next_key("conflict", information.conflicts.size + 1),
          fieldPath = fieldpath,
          informationValue = informationvalue,
          rdfValue = rdfvalue,
          severity = severity
        )
        val updated = information.copy(
          state = InformationLifecycleState.Conflict,
          conflicts = information.conflicts :+ conflict,
          updatedAt = Instant.now()
        )
        _replace_information(updated)
        Consequence.success(conflict)
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def conflicts(filterstate: Option[InformationConflictState] = None): Vector[InformationConflict] = {
    val values = _snapshot.information.flatMap(_.conflicts)
    filterstate match {
      case Some(value) => values.filter(_.state == value)
      case None => values
    }
  }

  def resolveConflict(
    informationid: InformationId,
    conflictkey: String,
    decision: String
  ): Consequence[InformationConflict] =
    getInformation(informationid) match {
      case Some(information) =>
        information.conflicts.find(_.conflictKey == conflictkey) match {
          case Some(conflict) =>
            val resolved = conflict.copy(
              state = InformationConflictState.Resolved,
              resolution = Some(decision)
            )
            val conflicts = information.conflicts.map(x => if (x.conflictKey == conflictkey) resolved else x)
            val state =
              if (conflicts.forall(_.state == InformationConflictState.Resolved))
                information.publicationStatuses.find(_.state == InformationPublicationState.Published).fold(InformationLifecycleState.Confirmed)(_ => InformationLifecycleState.Published)
              else
                InformationLifecycleState.Conflict
            _replace_information(information.copy(state = state, conflicts = conflicts, updatedAt = Instant.now()))
            Consequence.success(resolved)
          case None =>
            Consequence.argumentInvalid(s"information conflict not found: $conflictkey")
        }
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  def materializeInformation(informationid: InformationId): Consequence[KnowledgeWorkingSetSnapshot] =
    getInformation(informationid) match {
      case Some(information) if information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published =>
        Consequence.success(InformationToKnowledgeProjection.materialize(information))
      case Some(_) =>
        Consequence.argumentInvalid(s"information is not knowledge-ready: ${informationid.print}")
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  private def _update_information(
    informationid: InformationId
  )(f: Information => Information): Consequence[Information] =
    getInformation(informationid) match {
      case Some(information) =>
        val updated = f(information)
        _replace_information(updated)
        Consequence.success(updated)
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  private def _replace_information(information: Information): Unit =
    _snapshot = _snapshot.copy(
      information = _snapshot.information.map(x => if (x.id == information.id) information else x)
    )

  private def _next_id(
    prefix: String,
    index: Int
  ): String =
    s"$prefix-$index"

  private def _next_key(
    prefix: String,
    index: Int
  ): String =
    s"$prefix-$index"

  private def _state_after_candidate_update(information: Information): InformationLifecycleState =
    if (information.state == InformationLifecycleState.Confirmed || information.state == InformationLifecycleState.Published)
      information.state
    else if (information.validationIssues.nonEmpty)
      InformationLifecycleState.Invalid
    else if (information.resolutionCandidates.isEmpty)
      InformationLifecycleState.Imported
    else if (information.resolutionCandidates.forall(_.selected))
      InformationLifecycleState.ReadyForConfirmation
    else
      InformationLifecycleState.NeedsResolution

  private def _same_binding(
    lhs: InformationIdentityBinding,
    rhs: InformationIdentityBinding
  ): Boolean =
    lhs.rdfSubject == rhs.rdfSubject &&
      lhs.externalIdentifiers == rhs.externalIdentifiers &&
      lhs.entityBindings == rhs.entityBindings &&
      lhs.knowledgeNodeId == rhs.knowledgeNodeId &&
      lhs.authority == rhs.authority
}

object InformationSpace {
  def validate(information: Information): Vector[InformationValidationIssue] =
    information.domain match {
      case "paper" => _validate_paper(information)
      case "book" => _validate_book(information)
      case "web-resource" => _validate_web_resource(information)
      case _ => Vector.empty
    }

  def materializeInformation(information: Information): KnowledgeWorkingSetSnapshot =
    InformationToKnowledgeProjection.materialize(information)

  def materializeInformationWithTags(information: Information)(using ExecutionContext): Consequence[KnowledgeWorkingSetSnapshot] =
    InformationTagging.knowledgeTagBindings(information.id).map { tagbindings =>
      InformationToKnowledgeProjection.materialize(information, tagbindings)
    }

  private def _validate_paper(information: Information): Vector[InformationValidationIssue] = {
    val paper = PaperInformation.from(information.workingData)
    val base = Vector.newBuilder[InformationValidationIssue]
    if (paper.title.isEmpty)
      base += InformationValidationIssue("title", "error", "title is required")
    base.result()
  }

  private def _validate_book(information: Information): Vector[InformationValidationIssue] = {
    val title = information.workingData.getString("title").getOrElse("").trim
    val base = Vector.newBuilder[InformationValidationIssue]
    if (title.isEmpty)
      base += InformationValidationIssue("title", "error", "title is required")
    base.result()
  }

  private def _validate_web_resource(information: Information): Vector[InformationValidationIssue] = {
    val webresource = WebResourceInformation.from(information.workingData)
    val base = Vector.newBuilder[InformationValidationIssue]
    if (webresource.title.isEmpty)
      base += InformationValidationIssue("title", "error", "title is required")
    if (webresource.url.isEmpty && webresource.canonicalUrl.isEmpty)
      base += InformationValidationIssue("url", "error", "url or canonicalUrl is required")
    base.result()
  }
}

object InformationTagging {
  val TagSpace: String = InformationSpaceEditorProjection.InformationTagSpace
  val Role: String = InformationSpaceEditorProjection.InformationTagRole

  def workflow(tagspace: String = TagSpace): TaggingWorkflow =
    TaggingWorkflow(tagSpace = tagspace)

  def knowledgeTagBindings(
    informationid: InformationId,
    tagspace: String = TagSpace
  )(using ExecutionContext): Consequence[Vector[KnowledgeTagBinding]] =
    workflow(tagspace).listEntityTags(informationid.print, Some(Role)).map { summary =>
      summary.tags.map(tag => KnowledgeTagBinding(tag.tagSpace, tag.id.value))
    }
}

object InformationToKnowledgeProjection {
  def materialize(information: Information): KnowledgeWorkingSetSnapshot =
    materialize(information, Vector.empty)

  def materialize(
    information: Information,
    tagbindings: Vector[KnowledgeTagBinding]
  ): KnowledgeWorkingSetSnapshot = {
    val provenance = KnowledgeProvenance(
      KnowledgeProvenanceId(s"prov-${information.id.print}"),
      origin = "information-space",
      generatedBy = Some("information.materialize")
    )
    val evidence = KnowledgeEvidence(
      KnowledgeEvidenceId(s"ev-${information.id.print}"),
      "information",
      KnowledgeSourceRef("information", information.id.print),
      information.data.getString("title"),
      Some(provenance.id)
    )
    val node = KnowledgeNode(
      id = KnowledgeNodeId(s"information-${information.id.print}"),
      category = KnowledgeNodeCategory(information.domain),
      identity = KnowledgeNodeIdentity(
        externalIdentifiers = Vector(ExternalKnowledgeIdentifier("cncf.information", information.id.print, Some(information.domain)))
      ),
      presentation = KnowledgeNodePresentation.label(information.data.getString("title").getOrElse(information.id.print)),
      sources = KnowledgeNodeSources(
        evidenceIds = Vector(evidence.id),
        provenanceIds = Vector(provenance.id)
      ),
      bindings = KnowledgeNodeBindings(tagBindings = tagbindings),
      attributes = KnowledgeAttributes("information_domain" -> information.domain)
    )
    val fact = KnowledgeFact(
      id = KnowledgeFactId(s"fact-${information.id.print}-title"),
      kind = KnowledgeFactKind.EntityDerived,
      subjectNodeId = Some(node.id),
      predicate = Some("information.title"),
      value = information.data.getString("title"),
      evidenceIds = Vector(evidence.id),
      provenanceId = Some(provenance.id)
    )
    val frame = KnowledgeFrame(
      id = KnowledgeFrameId(s"frame-${information.id.print}"),
      kind = KnowledgeFrameKind.Curated,
      focusNodeIds = Vector(node.id),
      nodeIds = Vector(node.id),
      factIds = Vector(fact.id),
      evidenceIds = Vector(evidence.id),
      provenanceIds = Vector(provenance.id),
      origin = KnowledgeFrameOrigin(
        KnowledgeFrameInputRoute.BatchImport,
        provider = Some("cncf-information"),
        operation = Some("information.materialize"),
        provenanceId = Some(provenance.id)
      ),
      sourceRefs = Vector(KnowledgeSourceRef("information", information.id.print)),
      materializedAt = Some(Instant.now())
    )
    KnowledgeWorkingSetSnapshot(
      nodes = Vector(node),
      evidence = Vector(evidence),
      provenance = Vector(provenance),
      frames = Vector(frame),
      facts = Vector(fact)
    )
  }
}
