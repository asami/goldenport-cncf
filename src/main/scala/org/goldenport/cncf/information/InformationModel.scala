package org.goldenport.cncf.information

import java.time.Instant
import org.goldenport.cncf.knowledge.{
  ExternalKnowledgeIdentifier,
  KnowledgeEntityBinding,
  KnowledgeFrameId,
  KnowledgeNodeId,
  RdfNodeName
}
import org.goldenport.record.Record

/*
 * @since   May. 20, 2026
 * @version May. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final case class InformationImportBatchId(value: String) {
  def print: String = value
}

final case class InformationRecordId(value: String) {
  def print: String = value
}

final case class InformationItemId(value: String) {
  def print: String = value
}

final case class InformationValidationIssueId(value: String) {
  def print: String = value
}

final case class InformationResolutionCandidateId(value: String) {
  def print: String = value
}

final case class InformationIdentityBindingId(value: String) {
  def print: String = value
}

final case class InformationPublicationId(value: String) {
  def print: String = value
}

final case class InformationConflictId(value: String) {
  def print: String = value
}

enum InformationLifecycleState {
  case Imported, Invalid, NeedsResolution, ReadyForConfirmation, Confirmed, Published, Rejected, Conflict

  def label: String =
    this match {
      case Imported => "imported"
      case Invalid => "invalid"
      case NeedsResolution => "needs_resolution"
      case ReadyForConfirmation => "ready_for_confirmation"
      case Confirmed => "confirmed"
      case Published => "published"
      case Rejected => "rejected"
      case Conflict => "conflict"
    }
}

enum InformationBindingStatus {
  case Candidate, Selected, Confirmed, Rejected, Superseded, Conflict

  def label: String =
    this match {
      case Candidate => "candidate"
      case Selected => "selected"
      case Confirmed => "confirmed"
      case Rejected => "rejected"
      case Superseded => "superseded"
      case Conflict => "conflict"
    }
}

enum InformationPublicationState {
  case NotPublished, Published, Failed

  def label: String =
    this match {
      case NotPublished => "not_published"
      case Published => "published"
      case Failed => "failed"
    }
}

enum InformationConflictState {
  case Open, Resolved

  def label: String =
    this match {
      case Open => "open"
      case Resolved => "resolved"
    }
}

final case class InformationImportBatch(
  id: InformationImportBatchId,
  domain: String,
  recordIds: Vector[InformationRecordId],
  importedAt: Instant = Instant.now()
)

final case class InformationImportRecord(
  id: InformationRecordId,
  batchId: InformationImportBatchId,
  domain: String,
  rawData: Record,
  workingData: Record,
  state: InformationLifecycleState = InformationLifecycleState.Imported,
  itemId: Option[InformationItemId] = None,
  validationIssueIds: Vector[InformationValidationIssueId] = Vector.empty,
  resolutionCandidateIds: Vector[InformationResolutionCandidateId] = Vector.empty,
  identityBindingIds: Vector[InformationIdentityBindingId] = Vector.empty,
  updatedAt: Instant = Instant.now()
)

final case class InformationItem(
  id: InformationItemId,
  domain: String,
  data: Record,
  state: InformationLifecycleState = InformationLifecycleState.Confirmed,
  sourceRecordId: Option[InformationRecordId] = None,
  identityBindingIds: Vector[InformationIdentityBindingId] = Vector.empty,
  publicationId: Option[InformationPublicationId] = None,
  confirmedAt: Option[Instant] = None,
  updatedAt: Instant = Instant.now()
)

final case class InformationValidationIssue(
  id: InformationValidationIssueId,
  recordId: InformationRecordId,
  fieldPath: String,
  severity: String,
  message: String
)

final case class InformationIdentityBinding(
  id: InformationIdentityBindingId,
  informationItemId: Option[InformationItemId] = None,
  recordId: Option[InformationRecordId] = None,
  rdfSubject: Option[RdfNodeName] = None,
  externalIdentifiers: Vector[ExternalKnowledgeIdentifier] = Vector.empty,
  entityBindings: Vector[KnowledgeEntityBinding] = Vector.empty,
  knowledgeNodeId: Option[KnowledgeNodeId] = None,
  authority: Option[String] = None,
  confidence: Option[Double] = None,
  status: InformationBindingStatus = InformationBindingStatus.Candidate
)

final case class InformationResolutionCandidate(
  id: InformationResolutionCandidateId,
  recordId: InformationRecordId,
  fieldPath: String,
  label: String,
  binding: InformationIdentityBinding,
  confidence: Option[Double] = None,
  evidence: Option[String] = None,
  selected: Boolean = false
)

final case class InformationPublicationStatus(
  id: InformationPublicationId,
  itemId: InformationItemId,
  state: InformationPublicationState,
  target: String,
  message: Option[String] = None,
  knowledgeFrameId: Option[KnowledgeFrameId] = None,
  publishedAt: Option[Instant] = None
)

final case class InformationConflict(
  id: InformationConflictId,
  itemId: InformationItemId,
  fieldPath: String,
  informationValue: String,
  rdfValue: String,
  severity: String = "warning",
  state: InformationConflictState = InformationConflictState.Open,
  resolution: Option[String] = None
)

final case class InformationSpaceSnapshot(
  batches: Vector[InformationImportBatch] = Vector.empty,
  records: Vector[InformationImportRecord] = Vector.empty,
  items: Vector[InformationItem] = Vector.empty,
  validationIssues: Vector[InformationValidationIssue] = Vector.empty,
  resolutionCandidates: Vector[InformationResolutionCandidate] = Vector.empty,
  identityBindings: Vector[InformationIdentityBinding] = Vector.empty,
  publicationStatuses: Vector[InformationPublicationStatus] = Vector.empty,
  conflicts: Vector[InformationConflict] = Vector.empty
)

final case class InformationSpaceCounts(
  batchCount: Int = 0,
  recordCount: Int = 0,
  itemCount: Int = 0,
  validationIssueCount: Int = 0,
  resolutionCandidateCount: Int = 0,
  identityBindingCount: Int = 0,
  publicationStatusCount: Int = 0,
  conflictCount: Int = 0
)

final case class PaperInformation(
  title: String,
  authors: Vector[String],
  publicationIdentity: Option[String] = None,
  venue: Option[String] = None,
  publicationDate: Option[String] = None,
  abstractText: Option[String] = None,
  keywords: Vector[String] = Vector.empty,
  citations: Vector[String] = Vector.empty,
  resolverHooks: Vector[String] = Vector.empty
)

object PaperInformation {
  def from(record: Record): PaperInformation =
    PaperInformation(
      title = record.getString("title").getOrElse("").trim,
      authors = _strings(record, "authors") ++ record.getString("author").toVector.map(_.trim).filter(_.nonEmpty),
      publicationIdentity = record.getString("publicationIdentity").orElse(record.getString("doi")).filter(_.trim.nonEmpty),
      venue = record.getString("venue").filter(_.trim.nonEmpty),
      publicationDate = record.getString("publicationDate").orElse(record.getString("date")).filter(_.trim.nonEmpty),
      abstractText = record.getString("abstract").filter(_.trim.nonEmpty),
      keywords = _strings(record, "keywords"),
      citations = _strings(record, "citations"),
      resolverHooks = _strings(record, "resolverHooks")
    )

  private def _strings(
    record: Record,
    key: String
  ): Vector[String] =
    record.getString(key).toVector.flatMap { value =>
      value.split("[,\n]").toVector.map(_.trim).filter(_.nonEmpty)
    }
}

final case class WebResourceInformation(
  title: String,
  url: Option[String] = None,
  canonicalUrl: Option[String] = None,
  finalUrl: Option[String] = None,
  siteName: Option[String] = None,
  publisher: Option[String] = None,
  author: Option[String] = None,
  retrievedAt: Option[String] = None,
  summary: Option[String] = None,
  language: Option[String] = None,
  keywords: Vector[String] = Vector.empty,
  links: Vector[String] = Vector.empty,
  sourceUrl: Option[String] = None
)

object WebResourceInformation {
  def from(record: Record): WebResourceInformation =
    WebResourceInformation(
      title = record.getString("title").getOrElse("").trim,
      url = _string(record, "url"),
      canonicalUrl = _string(record, "canonicalUrl"),
      finalUrl = _string(record, "finalUrl"),
      siteName = _string(record, "siteName"),
      publisher = _string(record, "publisher"),
      author = _string(record, "author"),
      retrievedAt = _string(record, "retrievedAt"),
      summary = _string(record, "summary"),
      language = _string(record, "language"),
      keywords = _strings(record, "keywords"),
      links = _strings(record, "links"),
      sourceUrl = _string(record, "sourceUrl")
    )

  private def _string(
    record: Record,
    key: String
  ): Option[String] =
    record.getString(key).map(_.trim).filter(_.nonEmpty)

  private def _strings(
    record: Record,
    key: String
  ): Vector[String] =
    record.getString(key).toVector.flatMap { value =>
      value.split("[,\n]").toVector.map(_.trim).filter(_.nonEmpty)
    }
}

object InformationCapabilities {
  val Read = "information:read"
  val Import = "information:import"
  val Edit = "information:edit"
  val Validate = "information:validate"
  val Resolve = "information:resolve"
  val Confirm = "information:confirm"
  val Reject = "information:reject"
  val Publish = "information:publish"
  val ConflictRead = "information:conflict:read"
  val ConflictResolve = "information:conflict:resolve"
  val AuditRead = "information:audit:read"

  val all: Vector[String] = Vector(
    Read,
    Import,
    Edit,
    Validate,
    Resolve,
    Confirm,
    Reject,
    Publish,
    ConflictRead,
    ConflictResolve,
    AuditRead
  )
}
