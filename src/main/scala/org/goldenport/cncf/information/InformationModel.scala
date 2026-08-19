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
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   May. 20, 2026
 *  version May. 30, 2026
 * @version Aug.  8, 2026
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

final case class Information(
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
  updatedAt: Instant
) {
  def data: Record = workingData
}

final case class InformationValidationIssue(
  fieldPath: String,
  severity: String,
  message: String
)

final case class InformationIdentityBinding(
  rdfSubject: Option[RdfNodeName] = None,
  externalIdentifiers: Vector[ExternalKnowledgeIdentifier] = Vector.empty,
  entityBindings: Vector[KnowledgeEntityBinding] = Vector.empty,
  knowledgeNodeId: Option[KnowledgeNodeId] = None,
  authority: Option[String] = None,
  confidence: Option[Double] = None,
  status: InformationBindingStatus = InformationBindingStatus.candidate
)

object InformationIdentityBinding {
  def createC(record: Record): Consequence[InformationIdentityBinding] =
    for {
      rdfsubject <- _record_get_as_c[RdfNodeName](record, List("rdfSubject", "rdf_subject"))
      knowledgenodeid <- _record_get_as_c[KnowledgeNodeId](record, List("knowledgeNodeId", "knowledge_node_id"))
      authority <- record.getAsC[String]("authority")
      confidence <- record.getAsC[Double]("confidence")
    } yield InformationIdentityBinding(
      rdfSubject = rdfsubject,
      externalIdentifiers = Vector.empty,
      entityBindings = Vector.empty,
      knowledgeNodeId = knowledgenodeid,
      authority = authority,
      confidence = confidence
    )

  given ValueReader[InformationIdentityBinding] with {
    def readC(v: Any): Consequence[InformationIdentityBinding] = v match {
      case m: InformationIdentityBinding => Consequence.success(m)
      case m: Record => createC(m)
      case _ => Consequence.valueInvalid(v, org.goldenport.schema.XString)
    }
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
  candidateKey: String,
  fieldPath: String,
  candidateLabel: String,
  binding: InformationIdentityBinding,
  confidence: Option[Double] = None,
  evidence: Option[String] = None,
  selected: Boolean = false
) {
  def label: String = candidateLabel
}

final case class InformationPublicationStatus(
  publicationKey: String,
  state: InformationPublicationState,
  target: String,
  message: Option[String] = None,
  knowledgeFrameId: Option[KnowledgeFrameId] = None,
  publishedAt: Option[Instant] = None
)

final case class InformationConflict(
  conflictKey: String,
  fieldPath: String,
  informationValue: String,
  rdfValue: String,
  severity: String = "warning",
  state: InformationConflictState = InformationConflictState.open,
  resolution: Option[String] = None
)

final case class InformationFieldEvent(
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
)

final case class InformationSpaceSnapshot(
  information: Vector[Information] = Vector.empty
)

final case class InformationSpaceCounts(
  informationCount: Int = 0,
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
