package org.goldenport.cncf.information

import java.time.Instant
import domain.statemachine.informationLifecycle
import org.goldenport.Consequence
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.goldenport.cncf.information.entity.Information
import org.goldenport.cncf.information.value.{
  InformationBindingStatus,
  InformationConflict,
  InformationConflictState,
  InformationFieldEvent,
  InformationIdentityBinding,
  InformationLifecycleState,
  InformationPublicationState,
  InformationPublicationStatus,
  InformationResolutionCandidate,
  InformationSpaceCounts,
  InformationSpaceSnapshot,
  InformationValidationIssue
}
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
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.tag.TaggingWorkflow
import org.goldenport.record.Record

/*
 * @since   May. 20, 2026
 *  version May. 31, 2026
 *  version Aug. 31, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationSpace(
  owner: Option[Component] = None
) {
  def this(component: Component) =
    this(Some(component))

  private var _snapshot: InformationSpaceSnapshot = InformationSpaceSnapshot(Vector.empty)
  private val _repository = new InformationEntityRepository(owner)

  def snapshot: InformationSpaceSnapshot =
    _snapshot
  def snapshotC(using ctx: ExecutionContext): Consequence[InformationSpaceSnapshot] =
    _repository.search().map { information =>
      _cache_information_values(information)
      _snapshot
    }

  def counts: InformationSpaceCounts =
    _counts(_snapshot)
  def countsC(using ctx: ExecutionContext): Consequence[InformationSpaceCounts] =
    snapshotC.map(_counts)

  def clear()(using ctx: ExecutionContext): Consequence[Unit] = {
    val cleared = _repository.clear()
    val refreshed = _repository.search()
    (cleared, refreshed) match {
      case (Consequence.Success(_), Consequence.Success(information)) =>
        _cache_information_values(information)
        Consequence.unit
      case (failure @ Consequence.Failure(_), Consequence.Success(information)) =>
        _cache_information_values(information)
        failure
      case (Consequence.Success(_), Consequence.Failure(searchfailure)) =>
        _snapshot = InformationSpaceSnapshot(Vector.empty)
        Consequence.Failure(searchfailure)
      case (Consequence.Failure(clearfailure), Consequence.Failure(searchfailure)) =>
        Consequence.Failure(clearfailure ++ searchfailure)
    }
  }
  def registerInformation(
    domain: String,
    records: Vector[Record]
  )(using ctx: ExecutionContext): Consequence[Vector[Information]] =
    if (domain.trim.isEmpty)
      Consequence.argumentInvalid("information domain is required")
    else if (records.isEmpty)
      Consequence.argumentInvalid("information records are required")
    else {
      _repository.collectionIdC.flatMap { collectionid =>
        records.zipWithIndex.foldLeft(Consequence.success(Vector.empty[Information])) {
          case (z, (record, index)) =>
            z.flatMap { values =>
              val now = ctx.clock.instant()
              val information = Information.Builder()
                .withId(ctx.idGeneration.entityId(
                  collectionid,
                  s"information.register.${index + 1}"
                ))
                .withRevision(EntityRevision.INITIAL)
                .withLifecycleAttributes(InformationLifecycleSupport.lifecycleAttributes(now))
                .withDomain(domain)
                .withRawData(record)
                .withWorkingData(record)
                .withState(InformationLifecycleState.imported)
                .buildC()
                .TAKE
              _repository.create(information).map { persisted =>
                _cache_information(persisted)
                values :+ persisted
              }
            }
        }
      }
    }

  def getInformation(id: EntityId): Option[Information] =
    _snapshot.information.find(_.id == id)
  def getInformationC(
    informationId: EntityId
  )(using ctx: ExecutionContext): Consequence[Option[Information]] =
    _repository.load(informationId).map { information =>
      information match {
        case Some(value) => _cache_information(value)
        case None =>
          _snapshot = _snapshot.copy(
            information = _snapshot.information.filterNot(_.id == informationId)
          )
      }
      information
    }
  def updateInformation(
    informationid: EntityId,
    workingdata: Record
  )(using ctx: ExecutionContext): Consequence[Information] =
    _with_information(informationid) { information =>
      val updated = information.copy(
        workingData = workingdata,
        state = InformationLifecycleState.imported,
        validationIssues = Vector.empty,
        lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
      )
      _save_information_transition(
        information,
        if (information.state == updated.state) None else Some("update"),
        updated
      )
    }
  /**
   * Applies an edit selected by a strict ingress adapter that has retained the
   * revision it observed while presenting this Information.
   *
   * The revision remains execution metadata: ordinary Information operations
   * continue to use [[updateInformation]], while this explicit adapter-facing
   * route selects the standard observed-revision policy.
  */
  def updateInformationObserved(
    informationId: EntityId,
    workingData: Record,
    observedRevision: EntityRevision
  )(using ctx: ExecutionContext): Consequence[Information] =
    _with_information(informationId) { information =>
      val updated = information.copy(
        workingData = workingData,
        state = InformationLifecycleState.imported,
        validationIssues = Vector.empty,
        lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
      )
      _save_information_transition_observed(
        information,
        if (information.state == updated.state) None else Some("update"),
        updated,
        observedRevision
      )
    }

  def appendFieldEvent(
    informationid: EntityId,
    event: InformationFieldEvent
  )(using ctx: ExecutionContext): Consequence[Information] =
    _update_information(informationid) { information =>
      information.copy(
        fieldEvents = information.fieldEvents :+ event,
        lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
      )
    }

  def appendFieldEvents(
    informationid: EntityId,
    events: Vector[InformationFieldEvent]
  )(using ctx: ExecutionContext): Consequence[Information] =
    _update_information(informationid) { information =>
      information.copy(
        fieldEvents = information.fieldEvents ++ events,
        lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
      )
    }

  def validateInformation(informationid: EntityId)(using ctx: ExecutionContext): Consequence[Information] =
    _with_information(informationid) { information =>
      val issues = InformationSpace.validate(information)
      val (event, state) =
        if (issues.nonEmpty)
          "validateInvalid" -> InformationLifecycleState.invalid
        else if (information.resolutionCandidates.exists(!_.selected))
          "validateNeedsResolution" -> InformationLifecycleState.needs_resolution
        else
          "validateReady" -> InformationLifecycleState.ready_for_confirmation
      _save_information_transition(information, Some(event), information.copy(
        state = state,
        validationIssues = issues,
        lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
      ))
    }

  def validationIssues(informationid: EntityId): Vector[InformationValidationIssue] =
    getInformation(informationid).map(_.validationIssues).getOrElse(Vector.empty)

  def validationIssuesC(
    informationId: EntityId
  )(using ctx: ExecutionContext): Consequence[Vector[InformationValidationIssue]] =
    getInformationC(informationId).map(_.map(_.validationIssues).getOrElse(Vector.empty))

  def addResolutionCandidate(
    informationid: EntityId,
    fieldpath: String,
    label: String,
    binding: InformationIdentityBinding,
    confidence: Option[Double] = None,
    evidence: Option[String] = None
  )(using ctx: ExecutionContext): Consequence[InformationResolutionCandidate] =
    _with_information(informationid) { information =>
      val key = _next_key("candidate", information.resolutionCandidates.size + 1)
      val nextbinding = binding.copy(status = InformationBindingStatus.candidate)
      val candidate = InformationResolutionCandidate(key, fieldpath, label, nextbinding, confidence, evidence, selected = false)
      _save_information_transition(information, None, information.copy(
        resolutionCandidates = information.resolutionCandidates :+ candidate,
        identityBindings = information.identityBindings :+ nextbinding,
        lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
      )).map(_ => candidate)
    }

  def resolutionCandidates(informationid: EntityId): Vector[InformationResolutionCandidate] =
    getInformation(informationid).map(_.resolutionCandidates).getOrElse(Vector.empty)

  def selectResolutionCandidate(
    informationid: EntityId,
    candidatekey: String
  )(using ctx: ExecutionContext): Consequence[InformationResolutionCandidate] =
    _with_information(informationid) { information =>
      information.resolutionCandidates.find(_.candidateKey == candidatekey) match {
        case Some(candidate) =>
          val selectedbinding = candidate.binding.copy(status = InformationBindingStatus.selected)
          val selected = candidate.copy(binding = selectedbinding, selected = true)
          val candidates = information.resolutionCandidates.map(x => if (x.candidateKey == candidatekey) selected else x)
          val bindings = information.identityBindings.map { binding =>
            if (_same_binding(binding, candidate.binding)) selectedbinding else binding
          }
          val state = _state_after_resolution_selection(information, candidates)
          _save_information_transition(
            information,
            if (information.state == state) None else Some("selectResolution"),
            information.copy(
              state = state,
              resolutionCandidates = candidates,
              identityBindings = bindings,
              lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
            )
          ).map(_ => selected)
        case None =>
          Consequence.argumentInvalid(s"information resolution candidate not found: $candidatekey")
      }
    }

  def clearResolutionCandidate(
    informationid: EntityId,
    candidatekey: String
  )(using ctx: ExecutionContext): Consequence[InformationResolutionCandidate] =
    _with_information(informationid) { information =>
      if (information.state == InformationLifecycleState.confirmed || information.state == InformationLifecycleState.published) {
        Consequence.argumentInvalid(s"information is already confirmed: ${informationid.print}")
      } else {
        information.resolutionCandidates.find(_.candidateKey == candidatekey) match {
          case Some(candidate) =>
            val candidates = information.resolutionCandidates.filterNot(_.candidateKey == candidatekey)
            val bindings = information.identityBindings.filterNot(_same_binding(_, candidate.binding))
            _save_information_transition(information, None, information.copy(
              resolutionCandidates = candidates,
              identityBindings = bindings,
              lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
            )).map(_ => candidate)
          case None =>
            Consequence.argumentInvalid(s"information resolution candidate not found: $candidatekey")
        }
      }
    }

  def updateResolutionCandidateStatus(
    informationid: EntityId,
    candidatekey: String,
    status: InformationBindingStatus,
    selected: Option[Boolean] = None
  )(using ctx: ExecutionContext): Consequence[InformationResolutionCandidate] =
    _with_information(informationid) { information =>
      information.resolutionCandidates.find(_.candidateKey == candidatekey) match {
        case Some(candidate) =>
          val nextselected = selected.getOrElse(status == InformationBindingStatus.selected || status == InformationBindingStatus.confirmed)
          val nextbinding = candidate.binding.copy(status = status)
          val nextcandidate = candidate.copy(binding = nextbinding, selected = nextselected)
          val candidates = information.resolutionCandidates.map(x => if (x.candidateKey == candidatekey) nextcandidate else x)
          val bindings = information.identityBindings.map { binding =>
            if (_same_binding(binding, candidate.binding)) nextbinding else binding
          }
          _save_information_transition(information, None, information.copy(
            resolutionCandidates = candidates,
            identityBindings = bindings,
            lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
          )).map(_ => nextcandidate)
        case None =>
          Consequence.argumentInvalid(s"information resolution candidate not found: $candidatekey")
      }
    }

  def confirmInformation(informationid: EntityId)(using ctx: ExecutionContext): Consequence[Information] =
    _with_information(informationid) { information =>
      if (information.state != InformationLifecycleState.ready_for_confirmation && information.state != InformationLifecycleState.confirmed) {
        Consequence.argumentInvalid(s"information is not ready for confirmation: ${informationid.print}")
      } else {
        val now = ctx.clock.instant()
        val bindings = information.identityBindings.map(_.copy(status = InformationBindingStatus.confirmed))
        _save_information_transition(
          information,
          if (information.state == InformationLifecycleState.ready_for_confirmation) Some("confirm") else None,
          information.copy(
            state = InformationLifecycleState.confirmed,
            identityBindings = bindings,
            confirmedAt = information.confirmedAt.orElse(Some(now)),
            lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, now)
          )
        )
      }
    }

  def searchInformation(domain: Option[String] = None): Vector[Information] =
    domain match {
      case Some(value) => _snapshot.information.filter(_.domain == value)
      case None => _snapshot.information
    }

  def searchInformationC(
    domain: Option[String] = None
  )(using ctx: ExecutionContext): Consequence[Vector[Information]] =
    snapshotC.map { snapshot =>
      domain match {
        case Some(value) => snapshot.information.filter(_.domain == value)
        case None => snapshot.information
      }
    }

  def rejectInformation(
    informationid: EntityId,
    reason: String
  )(using ctx: ExecutionContext): Consequence[Information] =
    _with_information(informationid) { information =>
      _save_information_transition(information, Some("reject"), information.copy(
          state = InformationLifecycleState.rejected,
          lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
      ))
    }

  def reopenInformation(informationid: EntityId)(using ctx: ExecutionContext): Consequence[Information] =
    _with_information(informationid) { information =>
      _save_information_transition(information, Some("reopen"), information.copy(
          state = InformationLifecycleState.imported,
          lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
      ))
    }

  def publishInformation(
    informationid: EntityId,
    target: String,
    message: Option[String] = None,
    knowledgeframeid: Option[KnowledgeFrameId] = None
  )(using ctx: ExecutionContext): Consequence[InformationPublicationStatus] =
    _with_information(informationid) { information =>
      if (information.state == InformationLifecycleState.confirmed || information.state == InformationLifecycleState.published) {
        val now = ctx.clock.instant()
        val key = information.publicationStatuses.headOption.map(_.publicationKey).getOrElse(_next_key("publication", 1))
        val publication = InformationPublicationStatus(
          publicationKey = key,
          state = InformationPublicationState.published,
          target = target,
          message = message,
          knowledgeFrameId = knowledgeframeid,
          publishedAt = Some(now)
        )
        _save_information_transition(
          information,
          if (information.state == InformationLifecycleState.confirmed) Some("publish") else None,
          information.copy(
            state = InformationLifecycleState.published,
            publicationStatuses = information.publicationStatuses.filterNot(_.publicationKey == key) :+ publication,
            lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, now)
          )
        ).map(_ => publication)
      } else {
        Consequence.argumentInvalid(s"information is not confirmed: ${informationid.print}")
      }
    }

  def failInformationPublication(
    informationid: EntityId,
    target: String,
    message: Option[String] = None,
    knowledgeframeid: Option[KnowledgeFrameId] = None
  )(using ctx: ExecutionContext): Consequence[InformationPublicationStatus] =
    _with_information(informationid) { information =>
      if (information.state == InformationLifecycleState.confirmed || information.state == InformationLifecycleState.published) {
        val now = ctx.clock.instant()
        val key = information.publicationStatuses.headOption.map(_.publicationKey).getOrElse(_next_key("publication", 1))
        val publication = InformationPublicationStatus(
          publicationKey = key,
          state = InformationPublicationState.failed,
          target = target,
          message = message,
          knowledgeFrameId = knowledgeframeid,
          publishedAt = Some(now)
        )
        _save_information(information.copy(
          publicationStatuses = information.publicationStatuses.filterNot(_.publicationKey == key) :+ publication,
          lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, now)
        )).map(_ => publication)
      } else {
        Consequence.argumentInvalid(s"information is not confirmed: ${informationid.print}")
      }
    }

  def publicationStatusOption(
    informationid: EntityId,
    publicationkey: String
  ): Option[InformationPublicationStatus] =
    getInformation(informationid).flatMap(_.publicationStatuses.find(_.publicationKey == publicationkey))

  def recordConflict(
    informationid: EntityId,
    fieldpath: String,
    informationvalue: String,
    rdfvalue: String,
    severity: String = "warning"
  )(using ctx: ExecutionContext): Consequence[InformationConflict] =
    _with_information(informationid) { information =>
      val conflict = InformationConflict(
        conflictKey = _next_key("conflict", information.conflicts.size + 1),
        fieldPath = fieldpath,
        informationValue = informationvalue,
        rdfValue = rdfvalue,
        severity = severity,
        state = InformationConflictState.open,
        resolution = None
      )
      _save_information_transition(information, Some("detectConflict"), information.copy(
        state = InformationLifecycleState.conflict,
        conflicts = information.conflicts :+ conflict,
        lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
      )).map(_ => conflict)
    }

  def conflicts(filterstate: Option[InformationConflictState] = None): Vector[InformationConflict] = {
    val values = _snapshot.information.flatMap(_.conflicts)
    filterstate match {
      case Some(value) => values.filter(_.state == value)
      case None => values
    }
  }

  def resolveConflict(
    informationid: EntityId,
    conflictkey: String,
    decision: String
  )(using ctx: ExecutionContext): Consequence[InformationConflict] =
    _with_information(informationid) { information =>
      information.conflicts.find(_.conflictKey == conflictkey) match {
        case Some(conflict) =>
          val resolved = conflict.copy(
            state = InformationConflictState.resolved,
            resolution = Some(decision)
          )
          val conflicts = information.conflicts.map(x => if (x.conflictKey == conflictkey) resolved else x)
          val state =
            if (conflicts.forall(_.state == InformationConflictState.resolved))
              InformationLifecycleState.confirmed
            else
              InformationLifecycleState.conflict
          _save_information_transition(
            information,
            if (information.state == state) None else Some("resolveConflict"),
            information.copy(
              state = state,
              conflicts = conflicts,
              lifecycleAttributes = InformationLifecycleSupport.updatedLifecycleAttributes(information, ctx.clock.instant())
            )
          ).map(_ => resolved)
        case None =>
          Consequence.argumentInvalid(s"information conflict not found: $conflictkey")
      }
    }

  def materializeInformation(informationid: EntityId)(using ExecutionContext): Consequence[KnowledgeWorkingSetSnapshot] =
    _with_information(informationid) { information =>
      if (information.state == InformationLifecycleState.confirmed || information.state == InformationLifecycleState.published) {
        _repository.search().map { related =>
          _cache_information_values(related)
          InformationToKnowledgeProjection.materializeWithRelated(information, related)
        }
      } else {
        Consequence.argumentInvalid(s"information is not knowledge-ready: ${informationid.print}")
      }
    }

  private def _update_information(
    informationid: EntityId
  )(f: Information => Information)(using ctx: ExecutionContext): Consequence[Information] =
    _with_information(informationid) { information =>
      _save_information_transition(information, None, f(information))
    }

  private def _with_information[A](
    informationid: EntityId
  )(
    f: Information => Consequence[A]
  )(using ctx: ExecutionContext): Consequence[A] =
    _repository.load(informationid).flatMap {
      case Some(information) =>
        f(information)
      case None =>
        Consequence.argumentInvalid(s"information not found: ${informationid.print}")
    }

  private def _save_information(
    information: Information
  )(using ctx: ExecutionContext): Consequence[Information] =
    _repository.update(information).map { persisted =>
      _cache_information(persisted)
      persisted
    }

  private def _save_information_observed(
    information: Information,
    observedrevision: EntityRevision
  )(using ctx: ExecutionContext): Consequence[Information] =
    _repository.updateObserved(information, observedrevision).map { persisted =>
      _cache_information(persisted)
      persisted
    }

  private def _save_information_transition(
    current: Information,
    event: Option[String],
    next: Information
  )(using ctx: ExecutionContext): Consequence[Information] =
    _admit_information_transition(current, event, next).flatMap { _ =>
      _save_information(next)
    }

  private def _save_information_transition_observed(
    current: Information,
    event: Option[String],
    next: Information,
    observedrevision: EntityRevision
  )(using ctx: ExecutionContext): Consequence[Information] =
    _admit_information_transition(current, event, next).flatMap { _ =>
      _save_information_observed(next, observedrevision)
    }

  private def _admit_information_transition(
    current: Information,
    event: Option[String],
    next: Information
  ): Consequence[Unit] =
    event match {
      case Some(value) if informationLifecycle.permits(current.state.value, value, next.state.value) =>
        Consequence.unit
      case None if current.state == next.state =>
        Consequence.unit
      case Some(value) =>
        Consequence.argumentInvalid(
          s"information lifecycle transition is not permitted: ${current.state.value} --$value--> ${next.state.value}"
        )
      case None =>
        Consequence.argumentInvalid(
          s"information lifecycle transition requires a CML event: ${current.state.value} -> ${next.state.value}"
        )
    }

  private def _cache_information(information: Information): Unit =
    _snapshot = _snapshot.copy(
      information = _snapshot.information.filterNot(_.id == information.id) :+ information
    )

  private def _cache_information_values(
    information: Vector[Information]
  ): Unit =
    _snapshot = InformationSpaceSnapshot(information)

  private def _counts(snapshot: InformationSpaceSnapshot): InformationSpaceCounts =
    InformationSpaceCounts(
      informationCount = snapshot.information.size,
      validationIssueCount = snapshot.information.map(_.validationIssues.size).sum,
      resolutionCandidateCount = snapshot.information.map(_.resolutionCandidates.size).sum,
      identityBindingCount = snapshot.information.map(_.identityBindings.size).sum,
      publicationStatusCount = snapshot.information.map(_.publicationStatuses.size).sum,
      conflictCount = snapshot.information.map(_.conflicts.size).sum
    )

  private def _next_key(
    prefix: String,
    index: Int
  ): String =
    s"$prefix-$index"

  private def _state_after_resolution_selection(
    information: Information,
    candidates: Vector[InformationResolutionCandidate]
  ): InformationLifecycleState =
    if (
      information.state == InformationLifecycleState.needs_resolution &&
      candidates.nonEmpty &&
      candidates.forall(_.selected)
    )
      InformationLifecycleState.ready_for_confirmation
    else
      information.state

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

  def materializeInformation(information: Information)(using ExecutionContext): KnowledgeWorkingSetSnapshot =
    InformationToKnowledgeProjection.materialize(information)

  def materializeInformation(
    information: Information,
    relatedInformation: Vector[Information]
  )(using ExecutionContext): KnowledgeWorkingSetSnapshot =
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
