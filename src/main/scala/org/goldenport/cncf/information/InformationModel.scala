package org.goldenport.cncf.information

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.knowledge.{
  ExternalKnowledgeIdentifier,
  KnowledgeEntityBinding,
  KnowledgeFrameId,
  KnowledgeNodeId,
  RdfNodeName
}
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   May. 20, 2026
 * @version May. 23, 2026
 * @author  ASAMI, Tomoharu
 */
type InformationId = EntityId
object InformationId {
  def apply(value: String): InformationId =
    _information_entity_id(value, "information")
}

private def _information_entity_id(
  value: String,
  collection: String
): EntityId = {
  val normalized = value.trim.map {
    case c if c.isLetterOrDigit || c == '_' => c
    case _ => '_'
  }.mkString
  val minor =
    if (normalized.headOption.exists(_.isLetter))
      normalized
    else
      s"information_${normalized}"
  EntityId("cncf", minor, EntityCollectionId("cncf", "information", collection))
}

type ConfirmedInformationId = InformationId
object ConfirmedInformationId {
  def apply(value: String): ConfirmedInformationId = InformationId(value)
}

type InformationRecordId = InformationId
object InformationRecordId {
  def apply(value: String): InformationRecordId = InformationId(value)
}

type InformationItemId = ConfirmedInformationId
object InformationItemId {
  def apply(value: String): InformationItemId = ConfirmedInformationId(value)
}

type InformationImportBatchId = EntityId
object InformationImportBatchId {
  def apply(value: String): InformationImportBatchId =
    _information_entity_id(value, "information_import_context")
}

type InformationValidationIssueId = EntityId
object InformationValidationIssueId {
  def apply(value: String): InformationValidationIssueId =
    _information_entity_id(value, "information_validation_issue")
}

type InformationResolutionCandidateId = EntityId
object InformationResolutionCandidateId {
  def apply(value: String): InformationResolutionCandidateId =
    _information_entity_id(value, "information_resolution_candidate")
}

type InformationIdentityBindingId = EntityId
object InformationIdentityBindingId {
  def apply(value: String): InformationIdentityBindingId =
    _information_entity_id(value, "information_identity_binding")
}

type InformationPublicationId = EntityId
object InformationPublicationId {
  def apply(value: String): InformationPublicationId =
    _information_entity_id(value, "information_publication")
}

type InformationConflictId = EntityId
object InformationConflictId {
  def apply(value: String): InformationConflictId =
    _information_entity_id(value, "information_conflict")
}

type InformationLifecycleState = value.InformationLifecycleState
object InformationLifecycleState {
  val Imported: InformationLifecycleState = value.InformationLifecycleState.imported
  val Invalid: InformationLifecycleState = value.InformationLifecycleState.invalid
  val NeedsResolution: InformationLifecycleState = value.InformationLifecycleState.needs_resolution
  val ReadyForConfirmation: InformationLifecycleState = value.InformationLifecycleState.ready_for_confirmation
  val Confirmed: InformationLifecycleState = value.InformationLifecycleState.confirmed
  val Published: InformationLifecycleState = value.InformationLifecycleState.published
  val Rejected: InformationLifecycleState = value.InformationLifecycleState.rejected
  val Conflict: InformationLifecycleState = value.InformationLifecycleState.conflict
}

type InformationBindingStatus = value.InformationBindingStatus
object InformationBindingStatus {
  val Candidate: InformationBindingStatus = value.InformationBindingStatus.candidate
  val Selected: InformationBindingStatus = value.InformationBindingStatus.selected
  val Confirmed: InformationBindingStatus = value.InformationBindingStatus.confirmed
  val Rejected: InformationBindingStatus = value.InformationBindingStatus.rejected
  val Superseded: InformationBindingStatus = value.InformationBindingStatus.superseded
  val Conflict: InformationBindingStatus = value.InformationBindingStatus.conflict
}

type InformationPublicationState = value.InformationPublicationState
object InformationPublicationState {
  val NotPublished: InformationPublicationState = value.InformationPublicationState.not_published
  val Published: InformationPublicationState = value.InformationPublicationState.published
  val Failed: InformationPublicationState = value.InformationPublicationState.failed
}

type InformationConflictState = value.InformationConflictState
object InformationConflictState {
  val Open: InformationConflictState = value.InformationConflictState.open
  val Resolved: InformationConflictState = value.InformationConflictState.resolved
}

type EditableInformation = InformationImportRecord
type ConfirmedInformation = InformationItem

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

object InformationIdentityBinding {
  def createC(record: Record): Consequence[InformationIdentityBinding] =
    for {
      id <- record.getAsC[InformationIdentityBindingId]("id").flatMap(Consequence.successOrPropertyNotFound("id", _))
      informationitemid <- _record_get_as_c[InformationItemId](record, List("informationItemId", "information_item_id", "confirmedInformationId", "confirmed_information_id"))
      recordid <- _record_get_as_c[InformationRecordId](record, List("recordId", "record_id", "informationId", "information_id"))
      rdfsubject <- _record_get_as_c[RdfNodeName](record, List("rdfSubject", "rdf_subject"))
      knowledgenodeid <- _record_get_as_c[KnowledgeNodeId](record, List("knowledgeNodeId", "knowledge_node_id"))
      authority <- record.getAsC[String]("authority")
      confidence <- record.getAsC[Double]("confidence")
    } yield InformationIdentityBinding(
      id = id,
      informationItemId = informationitemid,
      recordId = recordid,
      rdfSubject = rdfsubject,
      externalIdentifiers = Vector.empty,
      entityBindings = Vector.empty,
      knowledgeNodeId = knowledgenodeid,
      authority = authority,
      confidence = confidence
    )

  given ValueReader[InformationIdentityBinding] with
    def readC(v: Any): Consequence[InformationIdentityBinding] = v match {
      case m: InformationIdentityBinding => Consequence.success(m)
      case m: Record => createC(m)
      case _ => Consequence.failValueInvalid(v, org.goldenport.schema.XString)
    }

  private def _record_get_as_c[A](
    record: Record,
    keys: List[String]
  )(using vr: ValueReader[A]): Consequence[Option[A]] =
    keys.foldLeft(Consequence.success(Option.empty[A])) { (z, key) =>
      z.flatMap {
        case s @ Some(_) => Consequence.success(s)
        case None => record.getAsC[A](key)
      }
    }
}

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
