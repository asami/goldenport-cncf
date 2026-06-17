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
  KnowledgeRelationship,
  KnowledgeRelationshipId,
  KnowledgeRelationshipKind,
  KnowledgeRelationshipQualifiers,
  KnowledgeProvenance,
  KnowledgeProvenanceId,
  RdfPredicateName,
  RdfNodeName,
  KnowledgeSourceRef,
  KnowledgeTagBinding,
  KnowledgeWorkingSetSnapshot
}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.tag.TaggingWorkflow
import org.goldenport.record.Record

/*
 * @since   May. 20, 2026
 *  version May. 31, 2026
 * @version Jun. 18, 2026
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

  def appendFieldEvent(
    informationid: InformationId,
    event: InformationFieldEvent
  ): Consequence[Information] =
    _update_information(informationid) { information =>
      information.copy(
        fieldEvents = information.fieldEvents :+ event,
        updatedAt = Instant.now()
      )
    }

  def appendFieldEvents(
    informationid: InformationId,
    events: Vector[InformationFieldEvent]
  ): Consequence[Information] =
    _update_information(informationid) { information =>
      information.copy(
        fieldEvents = information.fieldEvents ++ events,
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

  def updateResolutionCandidateStatus(
    informationid: InformationId,
    candidatekey: String,
    status: InformationBindingStatus,
    selected: Option[Boolean] = None
  ): Consequence[InformationResolutionCandidate] =
    getInformation(informationid) match {
      case Some(information) =>
        information.resolutionCandidates.find(_.candidateKey == candidatekey) match {
          case Some(candidate) =>
            val nextselected = selected.getOrElse(status == InformationBindingStatus.Selected || status == InformationBindingStatus.Confirmed)
            val nextbinding = candidate.binding.copy(status = status)
            val nextcandidate = candidate.copy(binding = nextbinding, selected = nextselected)
            val candidates = information.resolutionCandidates.map(x => if (x.candidateKey == candidatekey) nextcandidate else x)
            val bindings = information.identityBindings.map { binding =>
              if (_same_binding(binding, candidate.binding)) nextbinding else binding
            }
            val updated = information.copy(
              state = _state_after_candidate_update(information.copy(resolutionCandidates = candidates)),
              resolutionCandidates = candidates,
              identityBindings = bindings,
              updatedAt = Instant.now()
            )
            _replace_information(updated)
            Consequence.success(nextcandidate)
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
        Consequence.success(InformationToKnowledgeProjection.materializeWithRelated(information, _snapshot.information))
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
      case "person" => _validate_named_information(information)
      case "organization" => _validate_named_information(information)
      case "textual-work" => _validate_textual_work(information)
      case "textual-edition" => _validate_textual_work(information)
      case "textual-volume" => _validate_textual_work(information)
      case _ => Vector.empty
    }

  def materializeInformation(information: Information): KnowledgeWorkingSetSnapshot =
    InformationToKnowledgeProjection.materialize(information)

  def materializeInformation(
    information: Information,
    relatedInformation: Vector[Information]
  ): KnowledgeWorkingSetSnapshot =
    InformationToKnowledgeProjection.materializeWithRelated(information, relatedInformation)

  def materializeInformationWithTags(information: Information)(using ExecutionContext): Consequence[KnowledgeWorkingSetSnapshot] =
    InformationTagging.knowledgeTagBindings(information.id).map { tagbindings =>
      InformationToKnowledgeProjection.materialize(information, tagbindings, InformationRdfNodeNaming.fromExecutionContext)
    }

  def materializeInformationWithTags(
    information: Information,
    relatedInformation: Vector[Information]
  )(using ExecutionContext): Consequence[KnowledgeWorkingSetSnapshot] =
    InformationTagging.knowledgeTagBindings(information.id).map { tagbindings =>
      InformationToKnowledgeProjection.materializeWithRelated(information, tagbindings, relatedInformation, InformationRdfNodeNaming.fromExecutionContext)
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

  private def _validate_named_information(information: Information): Vector[InformationValidationIssue] = {
    val name = information.workingData.getString("name").getOrElse("").trim
    if (name.isEmpty)
      Vector(InformationValidationIssue("name", "error", "name is required"))
    else
      Vector.empty
  }

  private def _validate_textual_work(information: Information): Vector[InformationValidationIssue] = {
    val title = information.workingData.getString("title").getOrElse("").trim
    if (title.isEmpty)
      Vector(InformationValidationIssue("title", "error", "title is required"))
    else
      Vector.empty
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

final case class InformationRdfNodeNaming(
  prefix: String = InformationRdfNodeNaming.DEFAULT_PREFIX,
  publicBaseUri: Option[String] = None,
  namespaces: Vector[InformationRdfNamespace] = Vector.empty
) {
  def rdfNodeName(
    information: Information
  ): RdfNodeName =
    RdfNodeName(rdfNodeName(information.domain, shortInformationId(information.id)))

  def rdfNodeName(
    domain: String,
    shortid: String
  ): String =
    s"${currentPrefix}:${_rdf_domain(domain)}/${shortid.trim}"

  def publishedRdfNodeUri(
    domain: String,
    shortid: String
  ): Option[String] =
    currentNamespaceUri.map { namespaceuri =>
      Vector(_strip_trailing_slash(namespaceuri), _rdf_domain(domain), shortid.trim).mkString("/")
    }

  def currentPrefix: String =
    _normalize_prefix(prefix)

  def currentNamespaceUri: Option[String] =
    namespaceUri(currentPrefix).orElse(publicBaseUri)

  def namespaceMappings: Vector[InformationRdfNamespace] = {
    val current =
      currentNamespaceUri.map(uri => InformationRdfNamespace(currentPrefix, uri)).toVector
    (current ++ namespaces.map(_.normalized)).foldLeft(Vector.empty[InformationRdfNamespace]) { (z, x) =>
      if (x.prefix.isEmpty || x.namespaceUri.trim.isEmpty || z.exists(_.prefix == x.prefix))
        z
      else
        z :+ x
    }
  }

  def namespaceUri(
    prefix: String
  ): Option[String] = {
    val key = _normalize_prefix(prefix)
    namespaces.map(_.normalized).find(_.prefix == key).map(_.namespaceUri)
  }

  def shortInformationId(informationid: InformationId): String =
    informationid.entropy.getOrElse {
      val normalized = informationid.print.trim
      if (normalized.length <= 20)
        normalized
      else
        normalized.split("-").lastOption.getOrElse(normalized).take(10)
    }

  private def _normalize_prefix(value: String): String = {
    val normalized = value.trim.toLowerCase.replace('_', '-').replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-+|-+$)", "")
    if (normalized.isEmpty) InformationRdfNodeNaming.DEFAULT_PREFIX else normalized
  }

  private def _rdf_domain(domain: String): String = {
    val replaced = domain.trim.toLowerCase.replace('_', '-')
    val normalized = replaced.replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-+|-+$)", "")
    if (normalized.isEmpty) "information" else normalized
  }

  private def _strip_trailing_slash(value: String): String =
    value.trim.replaceAll("/+$", "")
}

final case class InformationRdfNamespace(
  prefix: String,
  namespaceUri: String
) {
  def normalized: InformationRdfNamespace =
    copy(prefix = _normalize_prefix(prefix), namespaceUri = namespaceUri.trim)

  private def _normalize_prefix(value: String): String =
    value.trim.toLowerCase.replace('_', '-').replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-+|-+$)", "")
}

object InformationRdfNodeNaming {
  val DEFAULT_PREFIX = "test"
  val DEFAULT_NAMESPACE_URI = "https://example.org/textus/test"
  val SM_PREFIX = "sm"
  val SM_NAMESPACE_URI = "https://www.simplemodeling.org"

  val BUILT_IN_NAMESPACES: Vector[InformationRdfNamespace] =
    Vector(
      InformationRdfNamespace(DEFAULT_PREFIX, DEFAULT_NAMESPACE_URI),
      InformationRdfNamespace(SM_PREFIX, SM_NAMESPACE_URI)
    )

  private val _prefix_keys = Vector(
    "textus.knowledge.rdf.current-prefix",
    "textus.knowledge.rdf.current_prefix",
    "textus.knowledge.rdf.currentPrefix",
    "textus.knowledge.rdf.namespace-prefix",
    "textus.knowledge.rdf.namespace_prefix",
    "textus.knowledge.rdf.namespacePrefix",
    "textus.knowledge.rdf.node-prefix",
    "textus.knowledge.rdf.node_prefix",
    "textus.knowledge.rdf.nodePrefix",
    "textus.knowledge.rdf.prefix"
  )
  private val _namespace_uri_keys = Vector(
    "textus.knowledge.rdf.namespace-uri",
    "textus.knowledge.rdf.namespace_uri",
    "textus.knowledge.rdf.namespaceUri"
  )
  private val _public_base_uri_keys = Vector(
    "textus.knowledge.rdf.public-base-uri",
    "textus.knowledge.rdf.public_base_uri",
    "textus.knowledge.rdf.publicBaseUri",
    "textus.knowledge.rdf.public-base-url",
    "textus.knowledge.rdf.public_base_url",
    "textus.knowledge.rdf.publicBaseUrl"
  )

  val default: InformationRdfNodeNaming =
    InformationRdfNodeNaming(
      prefix = DEFAULT_PREFIX,
      publicBaseUri = Some(DEFAULT_NAMESPACE_URI),
      namespaces = BUILT_IN_NAMESPACES
    )

  def fromExecutionContext(using ctx: ExecutionContext): InformationRdfNodeNaming =
    {
      val prefix = _config_string(_prefix_keys).getOrElse(DEFAULT_PREFIX)
      val namespaceuri =
        _namespace_uri_for_prefix(prefix)
          .orElse(_config_string(_namespace_uri_keys))
          .orElse(_config_string(_public_base_uri_keys))
          .orElse(_built_in_namespace_uri(prefix))
      InformationRdfNodeNaming(
        prefix = prefix,
        publicBaseUri = namespaceuri,
        namespaces = _namespace_mappings(prefix, namespaceuri)
      )
    }

  private def _config_string(
    keys: Vector[String]
  )(using ctx: ExecutionContext): Option[String] =
    _config_values(keys).headOption

  private def _config_values(
    keys: Vector[String]
  )(using ctx: ExecutionContext): Vector[String] =
    keys.flatMap { key =>
      _resolved_parameter_values(key).map(_.trim).filter(_.nonEmpty)
    }

  private def _resolved_parameter_values(
    key: String
  )(using ctx: ExecutionContext): Vector[String] =
    ctx.runtime.resolvedParameters.get(key).toVector.flatMap(param => _string_values(param.value))

  private def _string_values(
    value: ConfigurationValue
  ): Vector[String] =
    value match {
      case ConfigurationValue.StringValue(v) => Vector(v)
      case ConfigurationValue.NumberValue(v) => Vector(v.toString)
      case ConfigurationValue.BooleanValue(v) => Vector(v.toString)
      case ConfigurationValue.ListValue(vs) => vs.toVector.flatMap(_string_values)
      case _ => Vector.empty
    }

  private def _namespace_mappings(
    currentprefix: String,
    currenturi: Option[String]
  )(using ExecutionContext): Vector[InformationRdfNamespace] = {
    val current =
      currenturi.map(uri => InformationRdfNamespace(currentprefix, uri)).toVector
    val configured =
      _namespace_prefixes().flatMap { prefix =>
        _namespace_uri_for_prefix(prefix).map(uri => InformationRdfNamespace(prefix, uri))
      }
    (current ++ configured ++ BUILT_IN_NAMESPACES).foldLeft(Vector.empty[InformationRdfNamespace]) { (z, x) =>
      val normalized = x.normalized
      if (normalized.prefix.isEmpty || normalized.namespaceUri.isEmpty || z.exists(_.prefix == normalized.prefix))
        z
      else
        z :+ normalized
    }
  }

  private def _namespace_prefixes()(using ExecutionContext): Vector[String] =
    _config_values(Vector(
      "textus.knowledge.rdf.namespace-prefixes",
      "textus.knowledge.rdf.namespace_prefixes",
      "textus.knowledge.rdf.namespacePrefixes"
    )).flatMap(_.split(",").toVector.map(_.trim).filter(_.nonEmpty)).distinct

  private def _namespace_uri_for_prefix(
    prefix: String
  )(using ExecutionContext): Option[String] = {
    val normalized = prefix.trim.toLowerCase.replace('_', '-').replaceAll("[^a-z0-9-]+", "-").replaceAll("(^-+|-+$)", "")
    _config_string(Vector(
      s"textus.knowledge.rdf.namespaces.${normalized}",
      s"textus.knowledge.rdf.namespace.${normalized}"
    ))
  }

  private def _built_in_namespace_uri(
    prefix: String
  ): Option[String] = {
    val normalized = InformationRdfNamespace(prefix, "").normalized.prefix
    BUILT_IN_NAMESPACES.find(_.prefix == normalized).map(_.namespaceUri)
  }
}

object InformationToKnowledgeProjection {
  private final val CULTURAL_RESOURCE_FAMILY = "cultural-resource"
  private final val BOOK_DOMAIN_PROFILE = "book"
  private final val CULTURAL_RESOURCE_KINDS = Set(
    "textual-work",
    "edition",
    "series",
    "volume",
    "publication",
    "visual-work",
    "built-work",
    "physical-object",
    "collection-item",
    "holding"
  )

  def materialize(information: Information): KnowledgeWorkingSetSnapshot =
    materialize(information, Vector.empty)

  private final case class BookCulturalResourceLayers(
    publicationNodeId: KnowledgeNodeId,
    textualWorkNodeId: Option[KnowledgeNodeId],
    editionNodeId: Option[KnowledgeNodeId],
    volumeNodeId: Option[KnowledgeNodeId],
    linkedInformationById: Map[String, Information] = Map.empty
  ) {
    def authorshipSourceNodeId: KnowledgeNodeId =
      textualWorkNodeId.getOrElse(publicationNodeId)

    def publisherSourceNodeId: KnowledgeNodeId =
      publicationNodeId

    def textualWorkInformation: Option[Information] =
      _linked_information("textualWorkInformationId")

    def editionInformation: Option[Information] =
      _linked_information("textualEditionInformationId")

    def volumeInformation: Option[Information] =
      _linked_information("textualVolumeInformationId")

    private def _linked_information(fieldpath: String): Option[Information] =
      linkedInformationById.get(fieldpath)
  }

  def materialize(
    information: Information,
    tagbindings: Vector[KnowledgeTagBinding],
    naming: InformationRdfNodeNaming = InformationRdfNodeNaming.default
  ): KnowledgeWorkingSetSnapshot =
    materializeWithRelated(information, tagbindings, Vector.empty, naming)

  def materializeWithRelated(
    information: Information,
    relatedInformation: Vector[Information]
  ): KnowledgeWorkingSetSnapshot =
    materializeWithRelated(information, Vector.empty, relatedInformation, InformationRdfNodeNaming.default)

  def materializeWithRelated(
    information: Information,
    tagbindings: Vector[KnowledgeTagBinding],
    relatedInformation: Vector[Information],
    naming: InformationRdfNodeNaming = InformationRdfNodeNaming.default
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
    val booklayers = _book_knowledge_layers(information, KnowledgeNodeId(s"information-${information.id.print}"), relatedInformation)
    val node = KnowledgeNode(
      id = KnowledgeNodeId(s"information-${information.id.print}"),
      category = KnowledgeNodeCategory(if (information.domain == "book") "publication" else information.domain),
      identity = KnowledgeNodeIdentity(
        rdfNode = Some(naming.rdfNodeName(information)),
        externalIdentifiers = Vector(ExternalKnowledgeIdentifier("cncf.information", information.id.print, Some(information.domain)))
      ),
      presentation = KnowledgeNodePresentation.label(information.data.getString("title").getOrElse(information.id.print)),
      sources = KnowledgeNodeSources(
        evidenceIds = Vector(evidence.id),
        provenanceIds = Vector(provenance.id)
      ),
      bindings = KnowledgeNodeBindings(tagBindings = tagbindings),
      attributes = KnowledgeAttributes(
        Map("information_domain" -> information.domain) ++
          Option.when(information.domain == "book")("knowledge_layer" -> "publication") ++
          (if (information.domain == "book") _cultural_resource_attributes("publication", BOOK_DOMAIN_PROFILE) else Map.empty) ++
          _information_cultural_resource_attributes(information.domain)
      )
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
    val support = _book_support_nodes_and_relationships(information, booklayers, evidence.id, provenance.id, naming)
    val supportnodes = _distinct_nodes(support.map(_._1))
    val supportrelationships = _distinct_relationships(support.map(_._2))
    val frame = KnowledgeFrame(
      id = KnowledgeFrameId(s"frame-${information.id.print}"),
      kind = KnowledgeFrameKind.Curated,
      focusNodeIds = Vector(node.id),
      nodeIds = Vector(node.id) ++ supportnodes.map(_.id),
      relationshipIds = supportrelationships.map(_.id),
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
      purpose = None,
      query = None,
      materializedAt = Some(Instant.now())
    )
    KnowledgeWorkingSetSnapshot(
      nodes = Vector(node) ++ supportnodes,
      relationships = supportrelationships,
      evidence = Vector(evidence),
      provenance = Vector(provenance),
      frames = Vector(frame),
      facts = Vector(fact)
    )
  }

  private def _book_support_nodes_and_relationships(
    information: Information,
    layers: BookCulturalResourceLayers,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId,
    naming: InformationRdfNodeNaming
  ): Vector[(KnowledgeNode, KnowledgeRelationship)] = {
    if (information.domain != "book")
      Vector.empty
    else {
      val generated = _book_layer_nodes_and_relationships(information, layers, evidenceid, provenanceid, naming) ++
        _book_candidate_support_nodes_and_relationships(information, layers, evidenceid, provenanceid) ++
        _book_information_association_nodes_and_relationships(information, layers, evidenceid, provenanceid) ++
        _book_classification_nodes_and_relationships(information, layers.publicationNodeId, evidenceid, provenanceid)
      val reviews = _book_information_link_reviews(information)
      generated.map { case (node, relationship) =>
        node -> _apply_book_information_link_review(relationship, reviews)
      }
    }
  }

  private final case class BookInformationLinkReview(
    linkKey: String,
    kind: Option[String],
    rdfPredicate: Option[String],
    state: Option[String],
    qualifiers: Map[String, String],
    source: Option[String],
    evidenceSummary: Option[String]
  )

  private def _book_information_link_reviews(
    information: Information
  ): Map[String, BookInformationLinkReview] =
    information.fieldEvents.
      filter(_.fieldPath == "informationLinks").
      filter(_.transformation.contains("information-link-review")).
      flatMap(_book_information_link_review).
      groupBy(_.linkKey).
      view.mapValues(_.last).toMap

  private def _book_information_link_review(
    event: InformationFieldEvent
  ): Option[BookInformationLinkReview] = {
    val values = _key_value_pairs(event.evidence.getOrElse(""))
    for {
      key <- values.get("linkKey")
      kind <- values.get("kind").flatMap(_normalize_book_information_link_kind)
    } yield {
      val qualifiers = _book_information_link_qualifier_keys.flatMap { name =>
        values.get(name).map(name -> _)
      }.toMap
      BookInformationLinkReview(
        linkKey = key,
        kind = Some(kind),
        rdfPredicate = values.get("rdfPredicate").map(_.trim).filter(_.nonEmpty),
        state = Some(event.state.value),
        qualifiers = qualifiers,
        source = values.get("source").map(_.trim).filter(_.nonEmpty),
        evidenceSummary = values.get("evidenceSummary").map(_.trim).filter(_.nonEmpty)
      )
    }
  }

  private val _book_information_link_allowed_kinds: Set[String] =
    Set(
      "authored-by",
      "edited-by",
      "translated-by",
      "contributed-by",
      "published-by",
      "publication-of",
      "volume-of",
      "edition-of",
      "part-of-series",
      "has-part",
      "cites",
      "has-subject"
    )

  private def _normalize_book_information_link_kind(value: String): Option[String] = {
    val normalized = value.trim.toLowerCase
    _book_information_link_allowed_kinds.find(_ == normalized)
  }

  private val _book_information_link_qualifier_keys: Vector[String] =
    Vector(
      "order",
      "role",
      "editionNumber",
      "volumeNumber",
      "language",
      "pageRange",
      "citationContext",
      "confidence",
      "source",
      "evidenceSummary"
    )

  private def _apply_book_information_link_review(
    relationship: KnowledgeRelationship,
    reviews: Map[String, BookInformationLinkReview]
  ): KnowledgeRelationship = {
    val key = _book_information_link_key(relationship)
    reviews.get(key).map { review =>
      relationship.copy(
        kind = review.kind.map(KnowledgeRelationshipKind.apply).getOrElse(relationship.kind),
        rdfPredicate = review.rdfPredicate.map(RdfPredicateName.apply).
          orElse(review.kind.map(RdfPredicateName.apply)).
          orElse(relationship.rdfPredicate),
        qualifiers = KnowledgeRelationshipQualifiers(relationship.qualifiers.values ++ review.qualifiers),
        attributes = KnowledgeAttributes(relationship.attributes.values ++ Map(
          "information_link_key" -> key,
          "information_link_review_state" -> review.state.getOrElse(""),
          "information_link_review_source" -> review.source.getOrElse("manual"),
          "information_link_evidence_summary" -> review.evidenceSummary.getOrElse("")
        ))
      )
    }.getOrElse(relationship.copy(
      attributes = KnowledgeAttributes(relationship.attributes.values ++ Map("information_link_key" -> key))
    ))
  }

  private def _book_information_link_key(
    relationship: KnowledgeRelationship
  ): String =
    relationship.attributes.values.get("candidate_key").
      map(x => s"association:$x").
      orElse {
        for {
          field <- relationship.attributes.values.get("source_field")
          target <- relationship.attributes.values.get("target_information_id")
        } yield s"information:$field:$target"
      }.
      orElse {
        for {
          source <- relationship.attributes.values.get("source_layer")
          target <- relationship.attributes.values.get("target_layer")
        } yield s"layer:$source:$target"
      }.
      orElse(relationship.attributes.values.get("classification_entry_key").map(x => s"classification:$x")).
      getOrElse(relationship.id.print)

  private final case class BookClassificationEntry(
    entryKey: String,
    kind: String,
    system: String,
    code: String,
    label: String,
    rdfUri: String,
    source: String,
    evidence: String,
    state: String,
    primary: Boolean
  )

  private def _book_knowledge_layers(
    information: Information,
    publicationnodeid: KnowledgeNodeId,
    relatedInformation: Vector[Information] = Vector.empty
  ): BookCulturalResourceLayers = {
    val textualworktitle = _book_textual_work_title(information)
    val textualworkid = information.data.getString("textualWorkInformationId").map(_.trim).filter(_.nonEmpty)
    val textualeditionid = information.data.getString("textualEditionInformationId").map(_.trim).filter(_.nonEmpty)
    val textualvolumeid = information.data.getString("textualVolumeInformationId").map(_.trim).filter(_.nonEmpty)
    val editiontitle = _book_edition_title(information, textualworktitle)
    val volume = _book_volume(information)
    val relatedbyid = relatedInformation.map(x => x.id.print -> x).toMap
    val linked = Vector(
      textualworkid.flatMap(relatedbyid.get).filter(_.domain == "textual-work").map("textualWorkInformationId" -> _),
      textualeditionid.flatMap(relatedbyid.get).filter(_.domain == "textual-edition").map("textualEditionInformationId" -> _),
      textualvolumeid.flatMap(relatedbyid.get).filter(_.domain == "textual-volume").map("textualVolumeInformationId" -> _)
    ).flatten.toMap
    BookCulturalResourceLayers(
      publicationnodeid,
      linked.get("textualWorkInformationId").map(x => KnowledgeNodeId(s"information-${x.id.print}")).
        orElse(textualworktitle.map(title => KnowledgeNodeId(s"textual-work-${_safe_key(title, 0)}"))),
      linked.get("textualEditionInformationId").map(x => KnowledgeNodeId(s"information-${x.id.print}")).
        orElse(editiontitle.map(title => KnowledgeNodeId(s"edition-${_safe_key(title, 0)}"))),
      linked.get("textualVolumeInformationId").map(x => KnowledgeNodeId(s"information-${x.id.print}")).
        orElse(volume.map(value => KnowledgeNodeId(s"volume-${_safe_key(Vector(textualworktitle, Some(value)).flatten.mkString("-"), 0)}"))),
      linked
    )
  }

  private def _book_textual_work_title(information: Information): Option[String] =
    information.data.getString("workTitle").map(_.trim).filter(_.nonEmpty).
      orElse(information.data.getString("title").map(_.trim).filter(_.nonEmpty))

  private def _book_edition_title(
    information: Information,
    textualworktitle: Option[String]
  ): Option[String] =
    information.data.getString("editionTitle").map(_.trim).filter(_.nonEmpty).
      orElse(information.data.getString("series").map(_.trim).filter(_.nonEmpty)).
      orElse(information.data.getString("edition").map(_.trim).filter(_.nonEmpty)).
      orElse(information.data.getString("title").map(_.trim).filter(_.nonEmpty).filterNot(title => textualworktitle.contains(title)))

  private def _book_volume(information: Information): Option[String] =
    information.data.getString("volume").map(_.trim).filter(_.nonEmpty).
      orElse(information.data.getString("volumeNumber").map(_.trim).filter(_.nonEmpty))

  private def _book_layer_nodes_and_relationships(
    information: Information,
    layers: BookCulturalResourceLayers,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId,
    naming: InformationRdfNodeNaming
  ): Vector[(KnowledgeNode, KnowledgeRelationship)] = {
    val textualworktitle = _book_textual_work_title(information)
    val editiontitle = _book_edition_title(information, textualworktitle)
    val volume = _book_volume(information)
    val volumetitle = information.data.getString("volumeTitle").map(_.trim).filter(_.nonEmpty).
      orElse(volume.map(v => Vector(textualworktitle.getOrElse("Volume"), v).mkString(" ")))
    val textualwork = for {
      nodeid <- layers.textualWorkNodeId
      label <- layers.textualWorkInformation.flatMap(_information_label).orElse(textualworktitle)
    } yield _book_layer_node(information, nodeid, "textual-work", label, evidenceid, provenanceid, Map("textual_work_title" -> label), layers.textualWorkInformation, naming)
    val edition = for {
      nodeid <- layers.editionNodeId
      label <- layers.editionInformation.flatMap(_information_label).orElse(editiontitle)
    } yield _book_layer_node(information, nodeid, "edition", label, evidenceid, provenanceid, Map("edition_title" -> label), layers.editionInformation, naming)
    val volumenode = for {
      nodeid <- layers.volumeNodeId
      label <- layers.volumeInformation.flatMap(_information_label).orElse(volumetitle)
    } yield _book_layer_node(information, nodeid, "volume", label, evidenceid, provenanceid, Map("volume" -> volume.getOrElse(""), "volume_title" -> label), layers.volumeInformation, naming)
    val relations =
      (for {
        volumeid <- layers.volumeNodeId
      } yield _book_layer_relationship(information, "publication-of", layers.publicationNodeId, volumeid, evidenceid, provenanceid, Map("source_layer" -> "publication", "target_layer" -> "volume"))).toVector ++
      (for {
        volumeid <- layers.volumeNodeId
        editionid <- layers.editionNodeId
      } yield _book_layer_relationship(information, "volume-of", volumeid, editionid, evidenceid, provenanceid, Map("source_layer" -> "volume", "target_layer" -> "edition"))).toVector ++
      (for {
        editionid <- layers.editionNodeId
        workid <- layers.textualWorkNodeId
      } yield _book_layer_relationship(information, "edition-of", editionid, workid, evidenceid, provenanceid, Map("source_layer" -> "edition", "target_layer" -> "textual-work"))).toVector ++
      (for {
        workid <- layers.textualWorkNodeId
        if layers.volumeNodeId.isEmpty && layers.editionNodeId.isEmpty
      } yield _book_layer_relationship(information, "publication-of", layers.publicationNodeId, workid, evidenceid, provenanceid, Map("source_layer" -> "publication", "target_layer" -> "textual-work"))).toVector ++
      (for {
        editionid <- layers.editionNodeId
        if layers.volumeNodeId.isEmpty
      } yield _book_layer_relationship(information, "publication-of", layers.publicationNodeId, editionid, evidenceid, provenanceid, Map("source_layer" -> "publication", "target_layer" -> "edition"))).toVector ++
      (for {
        volumeid <- layers.volumeNodeId
        workid <- layers.textualWorkNodeId
        if layers.editionNodeId.isEmpty
      } yield _book_layer_relationship(information, "volume-of", volumeid, workid, evidenceid, provenanceid, Map("source_layer" -> "volume", "target_layer" -> "textual-work"))).toVector
    val nodes = textualwork.toVector ++ edition.toVector ++ volumenode.toVector
    val nodemap = nodes.map(node => node.id -> node).toMap
    val knownnodeids = nodemap.keySet + layers.publicationNodeId
    relations.filter(relation =>
      knownnodeids.contains(relation.sourceNodeId) && knownnodeids.contains(relation.targetNodeId)
    ).flatMap { relation =>
      nodemap.get(relation.targetNodeId).orElse(nodemap.get(relation.sourceNodeId)).map(_ -> relation)
    }
  }

  private def _book_layer_node(
    information: Information,
    nodeid: KnowledgeNodeId,
    layer: String,
    label: String,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId,
    attributes: Map[String, String],
    linkedInformation: Option[Information],
    naming: InformationRdfNodeNaming
  ): KnowledgeNode =
    KnowledgeNode(
      id = nodeid,
      category = KnowledgeNodeCategory(layer),
      identity = KnowledgeNodeIdentity(
        rdfNode = linkedInformation.map(naming.rdfNodeName),
        externalIdentifiers = linkedInformation.
          map(x => Vector(ExternalKnowledgeIdentifier("cncf.information", x.id.print, Some(x.domain)))).
          getOrElse(Vector(ExternalKnowledgeIdentifier("cncf.information", information.id.print, Some(s"book-$layer"))))
      ),
      presentation = KnowledgeNodePresentation.label(label),
      sources = KnowledgeNodeSources(
        evidenceIds = Vector(evidenceid),
        provenanceIds = Vector(provenanceid)
      ),
      attributes = KnowledgeAttributes(attributes ++ Map(
        "information_domain" -> linkedInformation.map(_.domain).getOrElse("book"),
        "source_information_id" -> information.id.print,
        "source_information_domain" -> "book",
        "knowledge_layer" -> layer
      ) ++ linkedInformation.map(x => Map(
        "linked_information_id" -> x.id.print,
        "linked_information_domain" -> x.domain
      )).getOrElse(Map.empty) ++ _cultural_resource_attributes(layer, BOOK_DOMAIN_PROFILE))
    )

  private def _information_label(information: Information): Option[String] =
    information.data.getString("title").map(_.trim).filter(_.nonEmpty).
      orElse(information.data.getString("displayTitle").map(_.trim).filter(_.nonEmpty)).
      orElse(information.data.getString("label").map(_.trim).filter(_.nonEmpty)).
      orElse(information.data.getString("name").map(_.trim).filter(_.nonEmpty))

  private def _cultural_resource_attributes(
    kind: String,
    profile: String
  ): Map[String, String] = {
    val normalized = if (CULTURAL_RESOURCE_KINDS.contains(kind)) kind else "cultural-resource"
    Map(
      "resource_family" -> CULTURAL_RESOURCE_FAMILY,
      "cultural_resource_kind" -> normalized,
      "domain_profile" -> profile
    )
  }

  private def _information_cultural_resource_attributes(
    domain: String
  ): Map[String, String] =
    domain match {
      case "textual-work" => _cultural_resource_attributes("textual-work", "textual-work")
      case "textual-edition" => _cultural_resource_attributes("edition", "textual-edition")
      case "textual-volume" => _cultural_resource_attributes("volume", "textual-volume")
      case _ => Map.empty
    }

  private def _book_layer_relationship(
    information: Information,
    kind: String,
    sourceid: KnowledgeNodeId,
    targetid: KnowledgeNodeId,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId,
    attributes: Map[String, String]
  ): KnowledgeRelationship =
    KnowledgeRelationship(
      id = KnowledgeRelationshipId(s"rel-${information.id.print}-${kind}-${_safe_key(targetid.print, 0)}"),
      kind = KnowledgeRelationshipKind(kind),
      sourceNodeId = sourceid,
      targetNodeId = targetid,
      rdfPredicate = Some(RdfPredicateName(kind)),
      evidenceIds = Vector(evidenceid),
      provenanceId = Some(provenanceid),
      attributes = KnowledgeAttributes(attributes)
    )

  private def _book_candidate_support_nodes_and_relationships(
    information: Information,
    layers: BookCulturalResourceLayers,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId
  ): Vector[(KnowledgeNode, KnowledgeRelationship)] =
    information.resolutionCandidates
        .filter(_.selected)
        .filter(candidate => _is_active_candidate_status(candidate.binding.status))
        .filter(candidate => Set("authors", "editors", "publisher").contains(candidate.fieldPath))
        .zipWithIndex
        .map { case (candidate, index) =>
          val domain = _candidate_domain(candidate)
          val relationship = _candidate_relationship(candidate.fieldPath)
          val sourcenodeid = _book_association_source_node(layers, candidate.fieldPath)
          val suffix = _safe_key(candidate.candidateKey, index)
          val targetnodeid = KnowledgeNodeId(s"information-${information.id.print}-${domain}-$suffix")
          val node = KnowledgeNode(
            id = targetnodeid,
            category = KnowledgeNodeCategory(domain),
            identity = KnowledgeNodeIdentity(
              rdfNode = candidate.binding.rdfSubject,
              externalIdentifiers = candidate.binding.externalIdentifiers
            ),
            presentation = KnowledgeNodePresentation.label(candidate.label),
            sources = KnowledgeNodeSources(
              evidenceIds = Vector(evidenceid),
              provenanceIds = Vector(provenanceid)
            ),
            attributes = KnowledgeAttributes(
              "information_domain" -> domain,
              "source_information_id" -> information.id.print,
              "source_field" -> candidate.fieldPath,
              "candidate_key" -> candidate.candidateKey
            )
          )
          val relation = KnowledgeRelationship(
            id = KnowledgeRelationshipId(s"rel-${information.id.print}-${relationship}-$suffix"),
            kind = KnowledgeRelationshipKind(relationship),
            sourceNodeId = sourcenodeid,
            targetNodeId = targetnodeid,
            rdfPredicate = Some(RdfPredicateName(relationship)),
            evidenceIds = Vector(evidenceid),
            provenanceId = Some(provenanceid),
            attributes = KnowledgeAttributes(
              "source_field" -> candidate.fieldPath,
              "candidate_key" -> candidate.candidateKey
            )
          )
          node -> relation
        }

  private def _is_active_candidate_status(status: InformationBindingStatus): Boolean =
    status == InformationBindingStatus.Candidate ||
      status == InformationBindingStatus.Selected ||
      status == InformationBindingStatus.Confirmed

  private def _book_information_association_nodes_and_relationships(
    information: Information,
    layers: BookCulturalResourceLayers,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId
  ): Vector[(KnowledgeNode, KnowledgeRelationship)] =
    Vector(
      ("authorInformationIds", "person", "authored-by"),
      ("editorInformationIds", "person", "edited-by"),
      ("publisherInformationIds", "organization", "published-by")
    ).flatMap { case (fieldpath, domain, relationship) =>
      val sourcenodeid = _book_association_source_node(layers, fieldpath)
      _line_values(information.data.getString(fieldpath).getOrElse("")).zipWithIndex.map { case (targetinformationid, index) =>
        val suffix = _safe_key(targetinformationid, index)
        val targetnodeid = KnowledgeNodeId(s"information-${information.id.print}-${domain}-information-$suffix")
        val node = KnowledgeNode(
          id = targetnodeid,
          category = KnowledgeNodeCategory(domain),
          presentation = KnowledgeNodePresentation.label(targetinformationid),
          sources = KnowledgeNodeSources(
            evidenceIds = Vector(evidenceid),
            provenanceIds = Vector(provenanceid)
          ),
          attributes = KnowledgeAttributes(
            "information_domain" -> domain,
            "source_information_id" -> information.id.print,
            "target_information_id" -> targetinformationid,
            "source_field" -> fieldpath
          )
        )
        val relation = KnowledgeRelationship(
          id = KnowledgeRelationshipId(s"rel-${information.id.print}-${relationship}-information-$suffix"),
          kind = KnowledgeRelationshipKind(relationship),
          sourceNodeId = sourcenodeid,
          targetNodeId = targetnodeid,
          rdfPredicate = Some(RdfPredicateName(relationship)),
          evidenceIds = Vector(evidenceid),
          provenanceId = Some(provenanceid),
          attributes = KnowledgeAttributes(
            "source_field" -> fieldpath,
            "target_information_id" -> targetinformationid
          )
        )
        node -> relation
      }
    }

  private def _book_association_source_node(
    layers: BookCulturalResourceLayers,
    fieldpath: String
  ): KnowledgeNodeId =
    fieldpath match {
      case "authors" | "authorInformationIds" => layers.authorshipSourceNodeId
      case "editors" | "editorInformationIds" => layers.editionNodeId.orElse(layers.textualWorkNodeId).getOrElse(layers.publicationNodeId)
      case "publisher" | "publisherInformationIds" => layers.publisherSourceNodeId
      case _ => layers.publicationNodeId
    }

  private def _line_values(value: String): Vector[String] =
    value.linesIterator.toVector.map(_.trim).filter(_.nonEmpty).distinct

  private def _book_classification_nodes_and_relationships(
    information: Information,
    booknodeid: KnowledgeNodeId,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId
  ): Vector[(KnowledgeNode, KnowledgeRelationship)] =
    _book_classification_entries(information).filter(entry =>
      entry.primary || entry.state == "stable"
    ).zipWithIndex.map { case (entry, index) =>
      val relationship = _classification_relationship(entry.kind)
      val suffix = _safe_key(Vector(entry.code, entry.rdfUri, entry.label, entry.entryKey).find(_.nonEmpty).getOrElse(entry.kind), index)
      val nodeid = KnowledgeNodeId(s"classification-${entry.system}-$suffix")
      val identifiers =
        Vector(
          Option.when(entry.code.nonEmpty)(ExternalKnowledgeIdentifier(entry.system, entry.code, Some(entry.kind))),
          Option.when(entry.rdfUri.nonEmpty)(ExternalKnowledgeIdentifier("rdf", entry.rdfUri, Some("classification")))
        ).flatten
      val node = KnowledgeNode(
        id = nodeid,
        category = KnowledgeNodeCategory.Concept,
        identity = KnowledgeNodeIdentity(
          rdfNode = Option.when(entry.rdfUri.nonEmpty)(RdfNodeName(entry.rdfUri)),
          externalIdentifiers = identifiers
        ),
        presentation = KnowledgeNodePresentation.label(entry.label),
        sources = KnowledgeNodeSources(
          evidenceIds = Vector(evidenceid),
          provenanceIds = Vector(provenanceid)
        ),
        attributes = KnowledgeAttributes(
          "classification_entry_key" -> entry.entryKey,
          "classification_kind" -> entry.kind,
          "classification_system" -> entry.system,
          "classification_code" -> entry.code,
          "classification_source" -> entry.source,
          "classification_evidence" -> entry.evidence
        )
      )
      val relation = KnowledgeRelationship(
        id = KnowledgeRelationshipId(s"rel-${information.id.print}-${relationship}-${suffix}"),
        kind = KnowledgeRelationshipKind(relationship),
        sourceNodeId = booknodeid,
        targetNodeId = nodeid,
        rdfPredicate = Some(RdfPredicateName(relationship)),
        evidenceIds = Vector(evidenceid),
        provenanceId = Some(provenanceid),
        attributes = KnowledgeAttributes(
          "classification_entry_key" -> entry.entryKey,
          "classification_kind" -> entry.kind,
          "classification_system" -> entry.system,
          "classification_code" -> entry.code,
          "classification_source" -> entry.source
        )
      )
      node -> relation
    }

  private def _book_classification_entries(information: Information): Vector[BookClassificationEntry] =
    information.data.getString("classificationEntries").toVector.flatMap { value =>
      value.linesIterator.toVector.flatMap(_book_classification_entry)
    }

  private def _book_classification_entry(line: String): Option[BookClassificationEntry] = {
    val fields = line.split(";").toVector.map(_.trim).filter(_.nonEmpty).flatMap { segment =>
      segment.indexOf("=") match {
        case -1 => None
        case index => Some(segment.take(index).trim -> _classification_value_decode(segment.drop(index + 1).trim))
      }
    }.toMap
    val kind = _classification_kind(fields.getOrElse("kind", "subject"))
    val system = _classification_system(fields.getOrElse("system", "local"))
    val code = fields.getOrElse("code", "").trim
    val label = fields.getOrElse("label", "").trim match {
      case "" if system == "ndc" && code.nonEmpty => s"NDC $code"
      case "" => code
      case x => x
    }
    val rdfuri = fields.get("rdfUri").orElse(fields.get("rdfURI")).orElse(fields.get("rdf")).map(_.trim).getOrElse("")
    val entrykey = fields.getOrElse("entryKey", _safe_key(Vector(system, code, label, rdfuri).find(_.nonEmpty).getOrElse(kind), 0)).trim
    if (entrykey.isEmpty || (code.isEmpty && label.isEmpty && rdfuri.isEmpty))
      None
    else
      Some(BookClassificationEntry(
        entryKey = entrykey,
        kind = kind,
        system = system,
        code = code,
        label = label,
        rdfUri = rdfuri,
        source = fields.getOrElse("source", "").trim,
        evidence = fields.getOrElse("evidence", "").trim,
        state = fields.getOrElse("state", "editing").trim.toLowerCase,
        primary = fields.get("primary").exists(value => value == "true" || value == "on" || value == "1")
      ))
  }

  private def _classification_value_decode(value: String): String = {
    val builder = new StringBuilder
    var i = 0
    while (i < value.length) {
      if (value.charAt(i) == '%' && i + 2 < value.length) {
        value.substring(i + 1, i + 3).toLowerCase(java.util.Locale.ROOT) match {
          case "25" =>
            builder.append('%')
            i += 3
          case "3b" =>
            builder.append(';')
            i += 3
          case "3d" =>
            builder.append('=')
            i += 3
          case "0a" =>
            builder.append('\n')
            i += 3
          case "0d" =>
            builder.append('\r')
            i += 3
          case _ =>
            builder.append(value.charAt(i))
            i += 1
        }
      } else {
        builder.append(value.charAt(i))
        i += 1
      }
    }
    builder.toString
  }

  private def _classification_kind(value: String): String =
    value.trim.toLowerCase.replace("_", "-") match {
      case "library" => "library"
      case "subject" => "subject"
      case "genre" => "genre"
      case "commercial" => "commercial"
      case "knowledge-domain" | "knowledgedomain" | "domain" => "knowledge-domain"
      case _ => "subject"
    }

  private def _classification_system(value: String): String =
    value.trim.toLowerCase.replace(" ", "").replace("_", "-") match {
      case "open-library" => "openlibrary"
      case "nippondecimalclassification" => "ndc"
      case "deweydecimalclassification" => "ddc"
      case "libraryofcongressclassification" => "lcc"
      case "libraryofcongresssubjectheadings" => "lcsh"
      case x if Set("ndc", "ddc", "lcc", "lcsh", "fast", "bisac", "wikidata", "dbpedia", "openlibrary", "local").contains(x) => x
      case _ => "local"
    }

  private def _classification_relationship(kind: String): String =
    kind match {
      case "library" => KnowledgeRelationshipKind.ClassifiedBy.print
      case "genre" => "has-genre"
      case "commercial" => "has-commercial-category"
      case "knowledge-domain" => "has-knowledge-domain"
      case _ => "has-subject"
    }

  private def _candidate_domain(candidate: InformationResolutionCandidate): String = {
    val kinds = candidate.binding.externalIdentifiers.flatMap(_.kind).map(_.toLowerCase)
    if (candidate.fieldPath == "publisher" || kinds.exists(_.contains("organization")))
      "organization"
    else
      "person"
  }

  private def _candidate_relationship(fieldpath: String): String =
    fieldpath match {
      case "publisher" => "published-by"
      case "editors" => "edited-by"
      case _ => "authored-by"
    }

  private def _distinct_nodes(nodes: Vector[KnowledgeNode]): Vector[KnowledgeNode] =
    nodes.foldLeft(Vector.empty[KnowledgeNode]) { (result, node) =>
      if (result.exists(_.id == node.id))
        result
      else
        result :+ node
    }

  private def _distinct_relationships(
    relationships: Vector[KnowledgeRelationship]
  ): Vector[KnowledgeRelationship] =
    relationships.foldLeft(Vector.empty[KnowledgeRelationship]) { (result, relationship) =>
      if (result.exists(_.id == relationship.id))
        result
      else
        result :+ relationship
    }

  private def _key_value_pairs(value: String): Map[String, String] =
    value.split(";").toVector.flatMap { segment =>
      val trimmed = segment.trim
      val index = trimmed.indexOf("=")
      if (index <= 0)
        None
      else
        Some(trimmed.take(index).trim -> _relationship_value_decode(trimmed.drop(index + 1).trim))
    }.toMap

  private def _relationship_value_decode(value: String): String =
    if (value.contains("%"))
      try {
        java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8)
      } catch {
        case _: IllegalArgumentException => value
      }
    else
      value

  private def _safe_key(
    value: String,
    index: Int
  ): String = {
    val normalized = value.trim.toLowerCase.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "")
    val hash = Integer.toUnsignedString(value.hashCode, 36)
    if (normalized.nonEmpty && (normalized.length >= 4 || normalized == value.trim.toLowerCase))
      normalized
    else if (normalized.nonEmpty)
      s"$normalized-$hash"
    else if (value.trim.nonEmpty)
      s"u$hash"
    else
      s"candidate-${index + 1}"
  }
}
