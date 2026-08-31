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
import org.goldenport.datatype.Identifier
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}

/*
 * @since   May. 20, 2026
 *  version May. 30, 2026
 * @version Aug. 31, 2026
 * @author  ASAMI, Tomoharu
 */
type InformationId = EntityId
object InformationId {
  def apply(value: String): InformationId = createC(value).TAKE

  def createC(value: String): Consequence[InformationId] =
    Option(value)
      .map(_.trim)
      .map(EntityId.parse)
      .getOrElse(Consequence.valueInvalid("Invalid InformationId value: null"))
}

type InformationLifecycleState = value.InformationLifecycleState
object InformationLifecycleState {
  val imported: InformationLifecycleState = value.InformationLifecycleState.imported
  val invalid: InformationLifecycleState = value.InformationLifecycleState.invalid
  val needsResolution: InformationLifecycleState = value.InformationLifecycleState.needs_resolution
  val readyForConfirmation: InformationLifecycleState = value.InformationLifecycleState.ready_for_confirmation
  val confirmed: InformationLifecycleState = value.InformationLifecycleState.confirmed
  val published: InformationLifecycleState = value.InformationLifecycleState.published
  val rejected: InformationLifecycleState = value.InformationLifecycleState.rejected
  val conflict: InformationLifecycleState = value.InformationLifecycleState.conflict
}

type InformationBindingStatus = value.InformationBindingStatus
object InformationBindingStatus {
  val candidate: InformationBindingStatus = value.InformationBindingStatus.candidate
  val selected: InformationBindingStatus = value.InformationBindingStatus.selected
  val confirmed: InformationBindingStatus = value.InformationBindingStatus.confirmed
  val rejected: InformationBindingStatus = value.InformationBindingStatus.rejected
  val superseded: InformationBindingStatus = value.InformationBindingStatus.superseded
  val conflict: InformationBindingStatus = value.InformationBindingStatus.conflict
}

type InformationPublicationState = value.InformationPublicationState
object InformationPublicationState {
  val notPublished: InformationPublicationState = value.InformationPublicationState.not_published
  val published: InformationPublicationState = value.InformationPublicationState.published
  val failed: InformationPublicationState = value.InformationPublicationState.failed
}

type InformationConflictState = value.InformationConflictState
object InformationConflictState {
  val open: InformationConflictState = value.InformationConflictState.open
  val resolved: InformationConflictState = value.InformationConflictState.resolved
}

type InformationFieldState = value.InformationFieldState
object InformationFieldState {
  val untouched: InformationFieldState = value.InformationFieldState.untouched
  val unresolved: InformationFieldState = value.InformationFieldState.unresolved
  val inferred: InformationFieldState = value.InformationFieldState.inferred
  val imported: InformationFieldState = value.InformationFieldState.imported
  val editing: InformationFieldState = value.InformationFieldState.editing
  val stable: InformationFieldState = value.InformationFieldState.stable
}

type InformationImportContext = value.InformationImportContext

// The CML-generated types are the only Information runtime model.  These
// root-level aliases keep the established source surface while deliberately
// confining compatibility behavior to construction and legacy wire decoding.
type Information = entity.Information
object Information {
  def apply(
    id: InformationId,
    domain: String,
    rawData: Record,
    workingData: Record,
    state: InformationLifecycleState = InformationLifecycleState.imported,
    importContext: Option[InformationImportContext] = None,
    validationIssues: Vector[InformationValidationIssue] = Vector.empty,
    resolutionCandidates: Vector[InformationResolutionCandidate] = Vector.empty,
    identityBindings: Vector[InformationIdentityBinding] = Vector.empty,
    publicationStatuses: Vector[InformationPublicationStatus] = Vector.empty,
    conflicts: Vector[InformationConflict] = Vector.empty,
    fieldEvents: Vector[InformationFieldEvent] = Vector.empty,
    confirmedAt: Option[Instant] = None,
    updatedAt: Instant = Instant.EPOCH
  ): Information =
    entity.Information.Builder()
      .withId(id)
      .withRevision(EntityRevision.INITIAL)
      .withLifecycleAttributes(Information.lifecycleAttributes(updatedAt))
      .withDomain(domain)
      .withRawData(rawData)
      .withWorkingData(workingData)
      .withState(state)
      .withImportContext(importContext)
      .withValidationIssues(validationIssues)
      .withResolutionCandidates(resolutionCandidates)
      .withIdentityBindings(identityBindings)
      .withPublicationStatuses(publicationStatuses)
      .withConflicts(conflicts)
      .withFieldEvents(fieldEvents)
      .withConfirmedAt(confirmedAt)
      .buildC()
      .TAKE

  private[information] def lifecycleAttributes(updatedAt: Instant) =
    org.simplemodeling.model.value.LifecycleAttributes(
      updatedAt,
      updatedAt,
      org.goldenport.datatype.Identifier("system"),
      org.goldenport.datatype.Identifier("system"),
      org.simplemodeling.model.statemachine.PostStatus.default,
      org.simplemodeling.model.statemachine.Aliveness.default
    )

  private[information] def updatedLifecycleAttributes(
    information: Information,
    updatedAt: Instant
  ) =
    information.lifecycleAttributes.copy(
      updatedAt = updatedAt,
      updatedBy = Identifier("system")
    )
}

extension (information: Information) {
  def data: Record = information.workingData
  def updatedAt: Instant = information.lifecycleAttributes.updatedAt
}

type InformationValidationIssue = value.InformationValidationIssue
object InformationValidationIssue {
  def apply(fieldPath: String, severity: String, message: String): InformationValidationIssue =
    value.InformationValidationIssue(fieldPath, severity, message)
}

type InformationIdentityBinding = value.InformationIdentityBinding
object InformationIdentityBinding {
  def apply(
    rdfSubject: Option[RdfNodeName] = None,
    externalIdentifiers: Vector[ExternalKnowledgeIdentifier] = Vector.empty,
    entityBindings: Vector[KnowledgeEntityBinding] = Vector.empty,
    knowledgeNodeId: Option[KnowledgeNodeId] = None,
    authority: Option[String] = None,
    confidence: Option[Double] = None,
    status: InformationBindingStatus = InformationBindingStatus.candidate
  ): InformationIdentityBinding =
    value.InformationIdentityBinding(
      rdfSubject,
      externalIdentifiers,
      entityBindings,
      knowledgeNodeId,
      authority,
      confidence,
      status
    )

  def createC(record: Record): Consequence[InformationIdentityBinding] =
    _normalize_aliases(record)
      .flatMap(_supply_default_status)
      .flatMap(value.InformationIdentityBinding.createC)

  given ValueReader[InformationIdentityBinding] with {
    def readC(v: Any): Consequence[InformationIdentityBinding] = v match {
      case m: InformationIdentityBinding => Consequence.success(m)
      case m: Record => createC(m)
      case _ => Consequence.valueInvalid(v, org.goldenport.schema.XString)
    }
  }

  private def _normalize_aliases(record: Record): Consequence[Record] =
    for {
      withrdfsubject <- _normalize_alias[RdfNodeName](record, "rdfSubject", "rdf_subject")
      withknowledgenodeid <- _normalize_alias[KnowledgeNodeId](withrdfsubject, "knowledgeNodeId", "knowledge_node_id")
    } yield withknowledgenodeid

  private def _normalize_alias[A](
    record: Record,
    canonical: String,
    alias: String
  )(using ValueReader[A]): Consequence[Record] =
    for {
      canonicalvalue <- record.getAsC[A](canonical)
      aliasvalue <- record.getAsC[A](alias)
      normalized <- (canonicalvalue, aliasvalue) match {
        case (Some(left), Some(right)) if left != right =>
          Consequence.argumentInvalid(s"conflicting Information compatibility aliases: $canonical and $alias")
        case (None, Some(value)) =>
          Consequence.success(record ++ Record.dataAuto(canonical -> value))
        case _ =>
          Consequence.success(record)
      }
    } yield normalized

  private def _supply_default_status(record: Record): Consequence[Record] =
    record.getAsC[InformationBindingStatus]("status").map {
      case Some(_) => record
      case None => record ++ Record.dataAuto("status" -> InformationBindingStatus.candidate)
    }
}

type InformationResolutionCandidate = value.InformationResolutionCandidate
object InformationResolutionCandidate {
  def apply(
    candidateKey: String,
    fieldPath: String,
    candidateLabel: String,
    binding: InformationIdentityBinding,
    confidence: Option[Double] = None,
    evidence: Option[String] = None,
    selected: Boolean = false
  ): InformationResolutionCandidate =
    value.InformationResolutionCandidate(candidateKey, fieldPath, candidateLabel, binding, confidence, evidence, selected)
}

extension (candidate: InformationResolutionCandidate)
  def label: String = candidate.candidateLabel

type InformationPublicationStatus = value.InformationPublicationStatus
object InformationPublicationStatus {
  def apply(
    publicationKey: String,
    state: InformationPublicationState,
    target: String,
    message: Option[String] = None,
    knowledgeFrameId: Option[KnowledgeFrameId] = None,
    publishedAt: Option[Instant] = None
  ): InformationPublicationStatus =
    value.InformationPublicationStatus(publicationKey, state, target, message, knowledgeFrameId, publishedAt)
}

type InformationConflict = value.InformationConflict
object InformationConflict {
  def apply(
    conflictKey: String,
    fieldPath: String,
    informationValue: String,
    rdfValue: String,
    severity: String = "warning",
    state: InformationConflictState = InformationConflictState.open,
    resolution: Option[String] = None
  ): InformationConflict =
    value.InformationConflict(conflictKey, fieldPath, informationValue, rdfValue, severity, state, resolution)
}

type InformationFieldEvent = value.InformationFieldEvent
object InformationFieldEvent {
  def apply(
    fieldPath: String,
    state: InformationFieldState,
    source: String,
    operation: Option[String] = None,
    provider: Option[String] = None,
    transformation: Option[String] = None,
    valueBefore: Option[String] = None,
    valueAfter: Option[String] = None,
    evidence: Option[String] = None,
    note: Option[String] = None,
    occurredAt: Instant,
    actor: Option[String] = None
  ): InformationFieldEvent =
    value.InformationFieldEvent(fieldPath, state, source, operation, provider, transformation, valueBefore, valueAfter, evidence, note, occurredAt, actor)
}

type InformationSpaceSnapshot = value.InformationSpaceSnapshot
object InformationSpaceSnapshot {
  def apply(information: Vector[Information] = Vector.empty): InformationSpaceSnapshot =
    value.InformationSpaceSnapshot(information)
}

type InformationSpaceCounts = value.InformationSpaceCounts
object InformationSpaceCounts {
  def apply(
    informationCount: Int = 0,
    validationIssueCount: Int = 0,
    resolutionCandidateCount: Int = 0,
    identityBindingCount: Int = 0,
    publicationStatusCount: Int = 0,
    conflictCount: Int = 0
  ): InformationSpaceCounts =
    value.InformationSpaceCounts(informationCount, validationIssueCount, resolutionCandidateCount, identityBindingCount, publicationStatusCount, conflictCount)
}

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
  val read = "information:read"
  val `import` = "information:import"
  val edit = "information:edit"
  val validate = "information:validate"
  val resolve = "information:resolve"
  val confirm = "information:confirm"
  val reject = "information:reject"
  val publish = "information:publish"
  val conflictRead = "information:conflict:read"
  val conflictResolve = "information:conflict:resolve"
  val auditRead = "information:audit:read"

  val all: Vector[String] = Vector(
    read,
    `import`,
    edit,
    validate,
    resolve,
    confirm,
    reject,
    publish,
    conflictRead,
    conflictResolve,
    auditRead
  )
}
