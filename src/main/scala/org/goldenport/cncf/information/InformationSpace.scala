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
  KnowledgeNodeCategory,
  KnowledgeNodeId,
  KnowledgeNodeIdentity,
  KnowledgeNodePresentation,
  KnowledgeNodeSources,
  KnowledgeProvenance,
  KnowledgeProvenanceId,
  KnowledgeSourceRef,
  KnowledgeWorkingSetSnapshot
}
import org.goldenport.record.Record

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationSpace {
  private var _snapshot: InformationSpaceSnapshot = InformationSpaceSnapshot()

  def snapshot: InformationSpaceSnapshot =
    _snapshot

  def counts: InformationSpaceCounts =
    InformationSpaceCounts(
      batchCount = _snapshot.batches.size,
      recordCount = _snapshot.records.size,
      itemCount = _snapshot.items.size,
      validationIssueCount = _snapshot.validationIssues.size,
      resolutionCandidateCount = _snapshot.resolutionCandidates.size,
      identityBindingCount = _snapshot.identityBindings.size,
      publicationStatusCount = _snapshot.publicationStatuses.size,
      conflictCount = _snapshot.conflicts.size
    )

  def clear(): Unit =
    _snapshot = InformationSpaceSnapshot()

  def registerImportBatch(
    domain: String,
    records: Vector[Record]
  ): Consequence[InformationImportBatch] =
    if (domain.trim.isEmpty)
      Consequence.argumentInvalid("information import domain is required")
    else if (records.isEmpty)
      Consequence.argumentInvalid("information import records are required")
    else {
      val batchid = InformationImportBatchId(_next_id("info-batch", _snapshot.batches.size + 1))
      val nextrecords = records.zipWithIndex.map { case (record, index) =>
        InformationImportRecord(
          id = InformationRecordId(_next_id("info-record", _snapshot.records.size + index + 1)),
          batchId = batchid,
          domain = domain,
          rawData = record,
          workingData = record
        )
      }
      val batch = InformationImportBatch(batchid, domain, nextrecords.map(_.id))
      _snapshot = _snapshot.copy(
        batches = _snapshot.batches :+ batch,
        records = _snapshot.records ++ nextrecords
      )
      Consequence.success(batch)
    }

  def importBatchOption(id: InformationImportBatchId): Option[InformationImportBatch] =
    _snapshot.batches.find(_.id == id)

  def importRecordOption(id: InformationRecordId): Option[InformationImportRecord] =
    _snapshot.records.find(_.id == id)

  def listImportRecords(batchid: InformationImportBatchId): Vector[InformationImportRecord] =
    _snapshot.records.filter(_.batchId == batchid)

  def updateInformationRecord(
    recordid: InformationRecordId,
    workingdata: Record
  ): Consequence[InformationImportRecord] =
    _update_record(recordid) { record =>
      record.copy(
        workingData = workingdata,
        state = InformationLifecycleState.Imported,
        validationIssueIds = Vector.empty,
        updatedAt = Instant.now()
      )
    }

  def validateInformationRecord(recordid: InformationRecordId): Consequence[InformationImportRecord] =
    importRecordOption(recordid) match {
      case Some(record) =>
        val issues = InformationSpace.validate(record).zipWithIndex.map { case (issue, index) =>
          issue.copy(id = InformationValidationIssueId(_next_id("info-issue", _snapshot.validationIssues.size + index + 1)))
        }
        val state =
          if (issues.nonEmpty)
            InformationLifecycleState.Invalid
          else if (!record.resolutionCandidateIds.forall(id => _snapshot.resolutionCandidates.exists(x => x.id == id && x.selected)))
            InformationLifecycleState.NeedsResolution
          else
            InformationLifecycleState.ReadyForConfirmation
        val retainedissues = _snapshot.validationIssues.filterNot(_.recordId == recordid)
        val updated = record.copy(
          state = state,
          validationIssueIds = issues.map(_.id),
          updatedAt = Instant.now()
        )
        _snapshot = _snapshot.copy(
          records = _snapshot.records.map(x => if (x.id == recordid) updated else x),
          validationIssues = retainedissues ++ issues
        )
        Consequence.success(updated)
      case None =>
        Consequence.argumentInvalid(s"information record not found: ${recordid.print}")
    }

  def validationIssues(recordid: InformationRecordId): Vector[InformationValidationIssue] =
    _snapshot.validationIssues.filter(_.recordId == recordid)

  def addResolutionCandidate(
    recordid: InformationRecordId,
    fieldpath: String,
    label: String,
    binding: InformationIdentityBinding,
    confidence: Option[Double] = None,
    evidence: Option[String] = None
  ): Consequence[InformationResolutionCandidate] =
    importRecordOption(recordid) match {
      case Some(record) =>
        val bindingid = InformationIdentityBindingId(_next_id("info-binding", _snapshot.identityBindings.size + 1))
        val candidateid = InformationResolutionCandidateId(_next_id("info-candidate", _snapshot.resolutionCandidates.size + 1))
        val nextbinding = binding.copy(id = bindingid, recordId = Some(recordid), status = InformationBindingStatus.Candidate)
        val candidate = InformationResolutionCandidate(candidateid, recordid, fieldpath, label, nextbinding, confidence, evidence)
        val updated = record.copy(
          state = InformationLifecycleState.NeedsResolution,
          resolutionCandidateIds = record.resolutionCandidateIds :+ candidate.id,
          identityBindingIds = record.identityBindingIds :+ nextbinding.id,
          updatedAt = Instant.now()
        )
        _snapshot = _snapshot.copy(
          records = _snapshot.records.map(x => if (x.id == recordid) updated else x),
          identityBindings = _snapshot.identityBindings :+ nextbinding,
          resolutionCandidates = _snapshot.resolutionCandidates :+ candidate
        )
        Consequence.success(candidate)
      case None =>
        Consequence.argumentInvalid(s"information record not found: ${recordid.print}")
    }

  def resolutionCandidates(recordid: InformationRecordId): Vector[InformationResolutionCandidate] =
    _snapshot.resolutionCandidates.filter(_.recordId == recordid)

  def selectResolutionCandidate(candidateid: InformationResolutionCandidateId): Consequence[InformationResolutionCandidate] =
    _snapshot.resolutionCandidates.find(_.id == candidateid) match {
      case Some(candidate) =>
        val selectedbinding = candidate.binding.copy(status = InformationBindingStatus.Selected)
        val updatedcandidate = candidate.copy(binding = selectedbinding, selected = true)
        val record = importRecordOption(candidate.recordId)
        val selectedids = _snapshot.resolutionCandidates
          .map(x => if (x.id == candidateid) updatedcandidate else x)
          .filter(_.recordId == candidate.recordId)
          .filter(_.selected)
          .map(_.id)
          .toSet
        val nextstate = record.map { x =>
          if (x.validationIssueIds.nonEmpty)
            InformationLifecycleState.Invalid
          else if (x.resolutionCandidateIds.forall(selectedids.contains))
            InformationLifecycleState.ReadyForConfirmation
          else
            InformationLifecycleState.NeedsResolution
        }
        _snapshot = _snapshot.copy(
          records = _snapshot.records.map(x => if (x.id == candidate.recordId && nextstate.isDefined) x.copy(state = nextstate.get, updatedAt = Instant.now()) else x),
          resolutionCandidates = _snapshot.resolutionCandidates.map(x => if (x.id == candidateid) updatedcandidate else x),
          identityBindings = _snapshot.identityBindings.map(x => if (x.id == selectedbinding.id) selectedbinding else x)
        )
        Consequence.success(updatedcandidate)
      case None =>
        Consequence.argumentInvalid(s"information resolution candidate not found: ${candidateid.print}")
    }

  def confirmInformationRecord(recordid: InformationRecordId): Consequence[InformationItem] =
    importRecordOption(recordid) match {
      case Some(record) if record.state == InformationLifecycleState.Invalid =>
        Consequence.argumentInvalid(s"information record is invalid: ${recordid.print}")
      case Some(record) if record.state != InformationLifecycleState.ReadyForConfirmation && record.state != InformationLifecycleState.Confirmed =>
        Consequence.argumentInvalid(s"information record is not ready for confirmation: ${recordid.print}")
      case Some(record) =>
        val itemid = record.itemId.getOrElse(InformationItemId(_next_id("info-item", _snapshot.items.size + 1)))
        val bindingids = record.identityBindingIds
        val item = InformationItem(
          id = itemid,
          domain = record.domain,
          data = record.workingData,
          state = InformationLifecycleState.Confirmed,
          sourceRecordId = Some(record.id),
          identityBindingIds = bindingids,
          confirmedAt = Some(Instant.now())
        )
        val confirmedbindings = _snapshot.identityBindings.map { binding =>
          if (bindingids.contains(binding.id))
            binding.copy(informationItemId = Some(itemid), status = InformationBindingStatus.Confirmed)
          else
            binding
        }
        val updatedrecord = record.copy(
          state = InformationLifecycleState.Confirmed,
          itemId = Some(itemid),
          updatedAt = Instant.now()
        )
        _snapshot = _snapshot.copy(
          records = _snapshot.records.map(x => if (x.id == recordid) updatedrecord else x),
          items = _snapshot.items.filterNot(_.id == itemid) :+ item,
          identityBindings = confirmedbindings
        )
        Consequence.success(item)
      case None =>
        Consequence.argumentInvalid(s"information record not found: ${recordid.print}")
    }

  def informationItemOption(id: InformationItemId): Option[InformationItem] =
    _snapshot.items.find(_.id == id)

  def searchInformationItems(domain: Option[String] = None): Vector[InformationItem] =
    domain match {
      case Some(value) => _snapshot.items.filter(_.domain == value)
      case None => _snapshot.items
    }

  def rejectInformationRecord(
    recordid: InformationRecordId,
    reason: String
  ): Consequence[InformationImportRecord] =
    _update_record(recordid)(_.copy(state = InformationLifecycleState.Rejected, updatedAt = Instant.now()))

  def reopenInformationItem(itemid: InformationItemId): Consequence[InformationItem] =
    _update_item(itemid)(_.copy(state = InformationLifecycleState.ReadyForConfirmation, updatedAt = Instant.now()))

  def publishInformationItem(
    itemid: InformationItemId,
    target: String,
    message: Option[String] = None,
    knowledgeframeid: Option[KnowledgeFrameId] = None
  ): Consequence[InformationPublicationStatus] =
    informationItemOption(itemid) match {
      case Some(item) if item.state == InformationLifecycleState.Confirmed || item.state == InformationLifecycleState.Published =>
        val publicationid = item.publicationId.getOrElse(InformationPublicationId(_next_id("info-publication", _snapshot.publicationStatuses.size + 1)))
        val publication = InformationPublicationStatus(
          id = publicationid,
          itemId = itemid,
          state = InformationPublicationState.Published,
          target = target,
          message = message,
          knowledgeFrameId = knowledgeframeid,
          publishedAt = Some(Instant.now())
        )
        val publisheditem = item.copy(
          state = InformationLifecycleState.Published,
          publicationId = Some(publication.id),
          updatedAt = Instant.now()
        )
        _snapshot = _snapshot.copy(
          items = _snapshot.items.map(x => if (x.id == itemid) publisheditem else x),
          publicationStatuses = _snapshot.publicationStatuses.filterNot(_.id == publication.id) :+ publication
        )
        Consequence.success(publication)
      case Some(_) =>
        Consequence.argumentInvalid(s"information item is not confirmed: ${itemid.print}")
      case None =>
        Consequence.argumentInvalid(s"information item not found: ${itemid.print}")
    }

  def publicationStatusOption(id: InformationPublicationId): Option[InformationPublicationStatus] =
    _snapshot.publicationStatuses.find(_.id == id)

  def recordConflict(
    itemid: InformationItemId,
    fieldpath: String,
    informationvalue: String,
    rdfvalue: String,
    severity: String = "warning"
  ): Consequence[InformationConflict] =
    informationItemOption(itemid) match {
      case Some(item) =>
        val conflict = InformationConflict(
          InformationConflictId(_next_id("info-conflict", _snapshot.conflicts.size + 1)),
          itemid,
          fieldpath,
          informationvalue,
          rdfvalue,
          severity
        )
        _snapshot = _snapshot.copy(
          items = _snapshot.items.map(x => if (x.id == itemid) item.copy(state = InformationLifecycleState.Conflict) else x),
          conflicts = _snapshot.conflicts :+ conflict
        )
        Consequence.success(conflict)
      case None =>
        Consequence.argumentInvalid(s"information item not found: ${itemid.print}")
    }

  def conflicts(filterstate: Option[InformationConflictState] = None): Vector[InformationConflict] =
    filterstate match {
      case Some(value) => _snapshot.conflicts.filter(_.state == value)
      case None => _snapshot.conflicts
    }

  def conflictOption(id: InformationConflictId): Option[InformationConflict] =
    _snapshot.conflicts.find(_.id == id)

  def resolveConflict(
    conflictid: InformationConflictId,
    decision: String
  ): Consequence[InformationConflict] =
    conflictOption(conflictid) match {
      case Some(conflict) =>
        val resolved = conflict.copy(
          state = InformationConflictState.Resolved,
          resolution = Some(decision)
        )
        val nextconflicts = _snapshot.conflicts.map(x => if (x.id == conflictid) resolved else x)
        val itemconflicts = nextconflicts.filter(_.itemId == conflict.itemId)
        val nextitems =
          if (itemconflicts.forall(_.state == InformationConflictState.Resolved))
            _snapshot.items.map { item =>
              if (item.id == conflict.itemId)
                item.copy(
                  state = item.publicationId.fold(InformationLifecycleState.Confirmed)(_ => InformationLifecycleState.Published),
                  updatedAt = Instant.now()
                )
              else
                item
            }
          else
            _snapshot.items
        _snapshot = _snapshot.copy(
          items = nextitems,
          conflicts = nextconflicts
        )
        Consequence.success(resolved)
      case None =>
        Consequence.argumentInvalid(s"information conflict not found: ${conflictid.print}")
    }

  private def _update_record(
    recordid: InformationRecordId
  )(f: InformationImportRecord => InformationImportRecord): Consequence[InformationImportRecord] =
    importRecordOption(recordid) match {
      case Some(record) =>
        val updated = f(record)
        _snapshot = _snapshot.copy(records = _snapshot.records.map(x => if (x.id == recordid) updated else x))
        Consequence.success(updated)
      case None =>
        Consequence.argumentInvalid(s"information record not found: ${recordid.print}")
    }

  private def _update_item(
    itemid: InformationItemId
  )(f: InformationItem => InformationItem): Consequence[InformationItem] =
    informationItemOption(itemid) match {
      case Some(item) =>
        val updated = f(item)
        _snapshot = _snapshot.copy(items = _snapshot.items.map(x => if (x.id == itemid) updated else x))
        Consequence.success(updated)
      case None =>
        Consequence.argumentInvalid(s"information item not found: ${itemid.print}")
    }

  private def _next_id(
    prefix: String,
    index: Int
  ): String =
    s"$prefix-$index"
}

object InformationSpace {
  def validate(record: InformationImportRecord): Vector[InformationValidationIssue] =
    record.domain match {
      case "paper" => _validate_paper(record)
      case _ => Vector.empty
    }

  def materializeItem(item: InformationItem): KnowledgeWorkingSetSnapshot =
    InformationToKnowledgeProjection.materialize(item)

  private def _validate_paper(record: InformationImportRecord): Vector[InformationValidationIssue] = {
    val paper = PaperInformation.from(record.workingData)
    val base = Vector.newBuilder[InformationValidationIssue]
    if (paper.title.isEmpty)
      base += InformationValidationIssue(InformationValidationIssueId("pending"), record.id, "title", "error", "title is required")
    if (paper.authors.isEmpty)
      base += InformationValidationIssue(InformationValidationIssueId("pending"), record.id, "authors", "error", "at least one author is required")
    base.result()
  }
}

object InformationToKnowledgeProjection {
  def materialize(item: InformationItem): KnowledgeWorkingSetSnapshot = {
    val provenance = KnowledgeProvenance(
      KnowledgeProvenanceId(s"prov-${item.id.print}"),
      origin = "information-space",
      generatedBy = Some("information.materialize")
    )
    val evidence = KnowledgeEvidence(
      KnowledgeEvidenceId(s"ev-${item.id.print}"),
      "information-item",
      KnowledgeSourceRef("information-item", item.id.print),
      item.data.getString("title"),
      Some(provenance.id)
    )
    val node = KnowledgeNode(
      id = KnowledgeNodeId(s"information-${item.id.print}"),
      category = KnowledgeNodeCategory(item.domain),
      identity = KnowledgeNodeIdentity(
        externalIdentifiers = Vector(ExternalKnowledgeIdentifier("cncf.information", item.id.print, Some(item.domain)))
      ),
      presentation = KnowledgeNodePresentation.label(item.data.getString("title").getOrElse(item.id.print)),
      sources = KnowledgeNodeSources(
        evidenceIds = Vector(evidence.id),
        provenanceIds = Vector(provenance.id)
      ),
      attributes = KnowledgeAttributes("information_domain" -> item.domain)
    )
    val fact = KnowledgeFact(
      id = KnowledgeFactId(s"fact-${item.id.print}-title"),
      kind = KnowledgeFactKind.EntityDerived,
      subjectNodeId = Some(node.id),
      predicate = Some("information.title"),
      value = item.data.getString("title"),
      evidenceIds = Vector(evidence.id),
      provenanceId = Some(provenance.id)
    )
    val frame = KnowledgeFrame(
      id = KnowledgeFrameId(s"frame-${item.id.print}"),
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
      sourceRefs = Vector(KnowledgeSourceRef("information-item", item.id.print)),
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
