package org.goldenport.cncf.knowledge

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record

/*
 * @since   May. 17, 2026
 * @version May. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final case class KnowledgeNodeId(value: String) {
  def print: String = value
}

object KnowledgeNodeId {
  given ValueReader[KnowledgeNodeId] with
    def readC(v: Any): Consequence[KnowledgeNodeId] = v match {
      case m: KnowledgeNodeId => Consequence.success(m)
      case s: String => Consequence.success(KnowledgeNodeId(s))
      case _ => Consequence.failValueInvalid(v, org.goldenport.schema.XString)
    }
}

final case class KnowledgeRelationshipId(value: String) {
  def print: String = value
}

final case class KnowledgeFrameId(value: String) {
  def print: String = value
}

object KnowledgeFrameId {
  given ValueReader[KnowledgeFrameId] with
    def readC(v: Any): Consequence[KnowledgeFrameId] = v match {
      case m: KnowledgeFrameId => Consequence.success(m)
      case s: String => Consequence.success(KnowledgeFrameId(s))
      case _ => Consequence.failValueInvalid(v, org.goldenport.schema.XString)
    }
}

final case class KnowledgeFactId(value: String) {
  def print: String = value
}

final case class KnowledgeEvidenceId(value: String) {
  def print: String = value
}

final case class KnowledgeProvenanceId(value: String) {
  def print: String = value
}

final case class RdfNodeName(value: String) {
  def print: String = value
}

object RdfNodeName {
  given ValueReader[RdfNodeName] with
    def readC(v: Any): Consequence[RdfNodeName] = v match {
      case m: RdfNodeName => Consequence.success(m)
      case s: String => Consequence.success(RdfNodeName(s))
      case _ => Consequence.failValueInvalid(v, org.goldenport.schema.XString)
    }
}

final case class RdfPredicateName(value: String) {
  def print: String = value
}

final case class KnowledgeNodeCategory(value: String) {
  def print: String = value
}

object KnowledgeNodeCategory {
  val Entity: KnowledgeNodeCategory = KnowledgeNodeCategory("entity")
  val Tag: KnowledgeNodeCategory = KnowledgeNodeCategory("tag")
  val Concept: KnowledgeNodeCategory = KnowledgeNodeCategory("concept")
  val Source: KnowledgeNodeCategory = KnowledgeNodeCategory("source")
  val Document: KnowledgeNodeCategory = KnowledgeNodeCategory("document")
  val Chunk: KnowledgeNodeCategory = KnowledgeNodeCategory("chunk")
  val ExternalSubject: KnowledgeNodeCategory = KnowledgeNodeCategory("external-subject")
  val Generated: KnowledgeNodeCategory = KnowledgeNodeCategory("generated")
  val Publication: KnowledgeNodeCategory = KnowledgeNodeCategory("publication")
  val TextualWork: KnowledgeNodeCategory = KnowledgeNodeCategory("textual-work")
  val Edition: KnowledgeNodeCategory = KnowledgeNodeCategory("edition")
  val Series: KnowledgeNodeCategory = KnowledgeNodeCategory("series")
  val Volume: KnowledgeNodeCategory = KnowledgeNodeCategory("volume")
  val VisualWork: KnowledgeNodeCategory = KnowledgeNodeCategory("visual-work")
  val BuiltWork: KnowledgeNodeCategory = KnowledgeNodeCategory("built-work")
  val PhysicalObject: KnowledgeNodeCategory = KnowledgeNodeCategory("physical-object")
  val CollectionItem: KnowledgeNodeCategory = KnowledgeNodeCategory("collection-item")
  val Holding: KnowledgeNodeCategory = KnowledgeNodeCategory("holding")
}

final case class KnowledgeRelationshipKind(value: String) {
  def print: String = value
}

object KnowledgeRelationshipKind {
  val Related: KnowledgeRelationshipKind = KnowledgeRelationshipKind("related")
  val ClassifiedBy: KnowledgeRelationshipKind = KnowledgeRelationshipKind("classified-by")
  val TranslationOf: KnowledgeRelationshipKind = KnowledgeRelationshipKind("translation-of")
  val LocalizedVersionOf: KnowledgeRelationshipKind = KnowledgeRelationshipKind("localized-version-of")
  val SameConceptAs: KnowledgeRelationshipKind = KnowledgeRelationshipKind("same-concept-as")
  val SameResourceAs: KnowledgeRelationshipKind = KnowledgeRelationshipKind("same-resource-as")
  val SourceAlignedWith: KnowledgeRelationshipKind = KnowledgeRelationshipKind("source-aligned-with")
  val AliasOf: KnowledgeRelationshipKind = KnowledgeRelationshipKind("alias-of")
  val SameAs: KnowledgeRelationshipKind = KnowledgeRelationshipKind("same-as")
  val EquivalentTo: KnowledgeRelationshipKind = KnowledgeRelationshipKind("equivalent-to")
  val Broader: KnowledgeRelationshipKind = KnowledgeRelationshipKind("broader")
  val Narrower: KnowledgeRelationshipKind = KnowledgeRelationshipKind("narrower")
  val ParentOf: KnowledgeRelationshipKind = KnowledgeRelationshipKind("parent-of")
  val HasChild: KnowledgeRelationshipKind = KnowledgeRelationshipKind("has-child")
  val PartOf: KnowledgeRelationshipKind = KnowledgeRelationshipKind("part-of")
  val HasPart: KnowledgeRelationshipKind = KnowledgeRelationshipKind("has-part")
  val MemberOf: KnowledgeRelationshipKind = KnowledgeRelationshipKind("member-of")
  val HasMember: KnowledgeRelationshipKind = KnowledgeRelationshipKind("has-member")
  val EntityBinding: KnowledgeRelationshipKind = KnowledgeRelationshipKind("entity-binding")
  val TagBinding: KnowledgeRelationshipKind = KnowledgeRelationshipKind("tag-binding")
}

final case class KnowledgeSemanticTypeStatus(value: String) {
  def print: String = value
}

object KnowledgeSemanticTypeStatus {
  val Asserted: KnowledgeSemanticTypeStatus = KnowledgeSemanticTypeStatus("asserted")
  val Inferred: KnowledgeSemanticTypeStatus = KnowledgeSemanticTypeStatus("inferred")
  val Generated: KnowledgeSemanticTypeStatus = KnowledgeSemanticTypeStatus("generated")
  val Candidate: KnowledgeSemanticTypeStatus = KnowledgeSemanticTypeStatus("candidate")
  val Rejected: KnowledgeSemanticTypeStatus = KnowledgeSemanticTypeStatus("rejected")
}

final case class KnowledgeSemanticType(
  system: String,
  name: String,
  label: Option[String] = None,
  source: Option[KnowledgeSourceRef] = None,
  profile: Option[String] = None,
  confidence: Option[Double] = None,
  provenanceId: Option[KnowledgeProvenanceId] = None,
  status: KnowledgeSemanticTypeStatus = KnowledgeSemanticTypeStatus.Asserted
)

final case class KnowledgeRelationshipSemanticType(
  system: String,
  name: String,
  propertyKind: Option[String] = None,
  directionPolicy: Option[String] = None,
  profile: Option[String] = None,
  confidence: Option[Double] = None,
  provenanceId: Option[KnowledgeProvenanceId] = None,
  status: KnowledgeSemanticTypeStatus = KnowledgeSemanticTypeStatus.Asserted
)

final case class ExternalKnowledgeIdentifier(
  system: String,
  value: String,
  kind: Option[String] = None
) {
  def key: String =
    Vector(
      _key_part("system", Some(system)),
      _key_part("kind", kind),
      _key_part("value", Some(value))
    ).mkString("|")

  private def _key_part(
    label: String,
    value: Option[String]
  ): String =
    value match {
      case Some(v) => s"$label=${v.length}:$v"
      case None => s"$label=-"
    }
}

object ExternalKnowledgeIdentifier {
  val ENTITY_SYSTEM = "cncf.entity"
  val TAG_SYSTEM = "cncf.tag"

  def createC(record: Record): Consequence[ExternalKnowledgeIdentifier] =
    for {
      system <- record.getAsC[String]("system").flatMap(Consequence.successOrPropertyNotFound("system", _))
      value <- record.getAsC[String]("value").flatMap(Consequence.successOrPropertyNotFound("value", _))
      kind <- record.getAsC[String]("kind")
    } yield ExternalKnowledgeIdentifier(system, value, kind)

  given ValueReader[ExternalKnowledgeIdentifier] with
    def readC(v: Any): Consequence[ExternalKnowledgeIdentifier] = v match {
      case m: ExternalKnowledgeIdentifier => Consequence.success(m)
      case m: Record => createC(m)
      case _ => Consequence.failValueInvalid(v, org.goldenport.schema.XString)
    }

  def entity(
    entityname: String,
    entityid: String
  ): ExternalKnowledgeIdentifier =
    ExternalKnowledgeIdentifier(ENTITY_SYSTEM, entityid, Some(entityname))

  def tag(
    tagspace: String,
    tagid: String
  ): ExternalKnowledgeIdentifier =
    ExternalKnowledgeIdentifier(TAG_SYSTEM, tagid, Some(tagspace))
}

final case class KnowledgeSourceRef(
  kind: String,
  value: String,
  uri: Option[String] = None
)

final case class KnowledgeLabels(
  default: Option[String] = None,
  localized: Map[String, String] = Map.empty,
  alternatives: Vector[String] = Vector.empty
)

object KnowledgeLabels {
  val empty: KnowledgeLabels = KnowledgeLabels()
}

final case class KnowledgeNames(
  canonical: Option[String] = None,
  aliases: Vector[String] = Vector.empty
)

object KnowledgeNames {
  val empty: KnowledgeNames = KnowledgeNames()
}

final case class KnowledgeDescriptions(
  default: Option[String] = None,
  localized: Map[String, String] = Map.empty
)

object KnowledgeDescriptions {
  val empty: KnowledgeDescriptions = KnowledgeDescriptions()
}

final case class KnowledgeIdentityLinks(
  canonical: Option[KnowledgeNodeId] = None,
  sameAs: Vector[KnowledgeNodeId] = Vector.empty,
  equivalentTo: Vector[KnowledgeNodeId] = Vector.empty
)

object KnowledgeIdentityLinks {
  val empty: KnowledgeIdentityLinks = KnowledgeIdentityLinks()
}

final case class KnowledgeNodeIdentity(
  rdfNode: Option[RdfNodeName] = None,
  identityLinks: KnowledgeIdentityLinks = KnowledgeIdentityLinks.empty,
  externalIdentifiers: Vector[ExternalKnowledgeIdentifier] = Vector.empty
)

object KnowledgeNodeIdentity {
  val empty: KnowledgeNodeIdentity = KnowledgeNodeIdentity()
}

final case class KnowledgeNodePresentation(
  labels: KnowledgeLabels = KnowledgeLabels.empty,
  names: KnowledgeNames = KnowledgeNames.empty,
  descriptions: KnowledgeDescriptions = KnowledgeDescriptions.empty
) {
  def defaultLabel: Option[String] =
    labels.default.orElse(names.canonical)
}

object KnowledgeNodePresentation {
  val empty: KnowledgeNodePresentation = KnowledgeNodePresentation()

  def label(value: String): KnowledgeNodePresentation =
    KnowledgeNodePresentation(labels = KnowledgeLabels(default = Some(value)))
}

final case class KnowledgeConfidenceProfile(
  value: Option[Double] = None,
  status: Option[String] = None
)

object KnowledgeConfidenceProfile {
  val empty: KnowledgeConfidenceProfile = KnowledgeConfidenceProfile()
}

final case class KnowledgeConfidentialityProfile(
  status: Option[String] = None,
  visibility: Option[String] = None
)

object KnowledgeConfidentialityProfile {
  val empty: KnowledgeConfidentialityProfile = KnowledgeConfidentialityProfile()
}

final case class KnowledgeTemporalProfile(
  validFrom: Option[Instant] = None,
  validTo: Option[Instant] = None,
  observedAt: Option[Instant] = None
)

object KnowledgeTemporalProfile {
  val empty: KnowledgeTemporalProfile = KnowledgeTemporalProfile()
}

final case class KnowledgeNodeLifecycle(
  state: Option[String] = None,
  createdAt: Option[Instant] = None,
  updatedAt: Option[Instant] = None
)

object KnowledgeNodeLifecycle {
  val empty: KnowledgeNodeLifecycle = KnowledgeNodeLifecycle()
}

final case class KnowledgeNodeSemantics(
  semanticTypes: Vector[KnowledgeSemanticType] = Vector.empty,
  roles: Set[String] = Set.empty,
  confidence: KnowledgeConfidenceProfile = KnowledgeConfidenceProfile.empty,
  confidentiality: KnowledgeConfidentialityProfile = KnowledgeConfidentialityProfile.empty,
  temporal: KnowledgeTemporalProfile = KnowledgeTemporalProfile.empty,
  lifecycle: KnowledgeNodeLifecycle = KnowledgeNodeLifecycle.empty
)

object KnowledgeNodeSemantics {
  val empty: KnowledgeNodeSemantics = KnowledgeNodeSemantics()
}

final case class KnowledgeNodeCorrespondence(
  nodeId: KnowledgeNodeId,
  language: Option[String] = None,
  locale: Option[String] = None,
  relationshipKind: KnowledgeRelationshipKind
)

final case class KnowledgeNodeCorrespondences(
  translations: Vector[KnowledgeNodeCorrespondence] = Vector.empty,
  localizedVersions: Vector[KnowledgeNodeCorrespondence] = Vector.empty,
  sameConcepts: Vector[KnowledgeNodeCorrespondence] = Vector.empty,
  sameResources: Vector[KnowledgeNodeCorrespondence] = Vector.empty,
  sourceAlignments: Vector[KnowledgeNodeCorrespondence] = Vector.empty,
  aliases: Vector[KnowledgeNodeCorrespondence] = Vector.empty
)

object KnowledgeNodeCorrespondences {
  val empty: KnowledgeNodeCorrespondences = KnowledgeNodeCorrespondences()
}

final case class KnowledgeClassifications(
  primary: Option[KnowledgeNodeId] = None,
  broader: Vector[KnowledgeNodeId] = Vector.empty,
  narrower: Vector[KnowledgeNodeId] = Vector.empty,
  additional: Vector[KnowledgeNodeId] = Vector.empty
)

object KnowledgeClassifications {
  val empty: KnowledgeClassifications = KnowledgeClassifications()
}

final case class KnowledgeHierarchy(
  parent: Option[KnowledgeNodeId] = None,
  children: Vector[KnowledgeNodeId] = Vector.empty,
  ancestors: Vector[KnowledgeNodeId] = Vector.empty,
  descendants: Vector[KnowledgeNodeId] = Vector.empty
)

object KnowledgeHierarchy {
  val empty: KnowledgeHierarchy = KnowledgeHierarchy()
}

final case class KnowledgePartWhole(
  partOf: Vector[KnowledgeNodeId] = Vector.empty,
  hasPart: Vector[KnowledgeNodeId] = Vector.empty,
  memberOf: Vector[KnowledgeNodeId] = Vector.empty,
  hasMember: Vector[KnowledgeNodeId] = Vector.empty,
  container: Option[KnowledgeNodeId] = None
)

object KnowledgePartWhole {
  val empty: KnowledgePartWhole = KnowledgePartWhole()
}

final case class KnowledgeNodeStructure(
  correspondences: KnowledgeNodeCorrespondences = KnowledgeNodeCorrespondences.empty,
  classifications: KnowledgeClassifications = KnowledgeClassifications.empty,
  hierarchy: KnowledgeHierarchy = KnowledgeHierarchy.empty,
  partWhole: KnowledgePartWhole = KnowledgePartWhole.empty
)

object KnowledgeNodeStructure {
  val empty: KnowledgeNodeStructure = KnowledgeNodeStructure()
}

final case class KnowledgeSourceRefs(
  primarySource: Option[KnowledgeSourceRef] = None,
  sourceDocuments: Vector[KnowledgeSourceRef] = Vector.empty,
  sourceChunks: Vector[KnowledgeSourceRef] = Vector.empty,
  extractedFrom: Vector[KnowledgeEvidenceId] = Vector.empty,
  observedIn: Vector[KnowledgeEvidenceId] = Vector.empty
)

object KnowledgeSourceRefs {
  val empty: KnowledgeSourceRefs = KnowledgeSourceRefs()
}

final case class KnowledgeNodeSources(
  sourceRefs: KnowledgeSourceRefs = KnowledgeSourceRefs.empty,
  evidenceIds: Vector[KnowledgeEvidenceId] = Vector.empty,
  provenanceIds: Vector[KnowledgeProvenanceId] = Vector.empty
)

object KnowledgeNodeSources {
  val empty: KnowledgeNodeSources = KnowledgeNodeSources()
}

final case class KnowledgeEntityBinding(
  entityName: String,
  entityId: String,
  entityVersion: Option[String] = None,
  component: Option[String] = None
) {
  def externalIdentifier: ExternalKnowledgeIdentifier =
    ExternalKnowledgeIdentifier.entity(entityName, entityId)
}

object KnowledgeEntityBinding {
  def createC(record: Record): Consequence[KnowledgeEntityBinding] =
    for {
      entityname <- _record_get_as_c[String](record, List("entityName", "entity_name")).flatMap(Consequence.successOrPropertyNotFound("entityName", _))
      entityid <- _record_get_as_c[String](record, List("entityId", "entity_id")).flatMap(Consequence.successOrPropertyNotFound("entityId", _))
      entityversion <- _record_get_as_c[String](record, List("entityVersion", "entity_version"))
      component <- record.getAsC[String]("component")
    } yield KnowledgeEntityBinding(entityname, entityid, entityversion, component)

  def from(id: ExternalKnowledgeIdentifier): Option[KnowledgeEntityBinding] =
    id match {
      case ExternalKnowledgeIdentifier(ExternalKnowledgeIdentifier.ENTITY_SYSTEM, value, Some(kind)) =>
        Some(KnowledgeEntityBinding(kind, value))
      case _ =>
        None
    }

  given ValueReader[KnowledgeEntityBinding] with
    def readC(v: Any): Consequence[KnowledgeEntityBinding] = v match {
      case m: KnowledgeEntityBinding => Consequence.success(m)
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

final case class KnowledgeTagBinding(
  tagSpace: String,
  tagId: String
) {
  def externalIdentifier: ExternalKnowledgeIdentifier =
    ExternalKnowledgeIdentifier.tag(tagSpace, tagId)
}

object KnowledgeTagBinding {
  def from(id: ExternalKnowledgeIdentifier): Option[KnowledgeTagBinding] =
    id match {
      case ExternalKnowledgeIdentifier(ExternalKnowledgeIdentifier.TAG_SYSTEM, value, Some(kind)) =>
        Some(KnowledgeTagBinding(kind, value))
      case _ =>
        None
    }
}

final case class KnowledgeNodeBindings(
  entityBindings: Vector[KnowledgeEntityBinding] = Vector.empty,
  tagBindings: Vector[KnowledgeTagBinding] = Vector.empty
)

object KnowledgeNodeBindings {
  val empty: KnowledgeNodeBindings = KnowledgeNodeBindings()

  def from(ids: Vector[ExternalKnowledgeIdentifier]): KnowledgeNodeBindings =
    KnowledgeNodeBindings(
      entityBindings = ids.flatMap(KnowledgeEntityBinding.from),
      tagBindings = ids.flatMap(KnowledgeTagBinding.from)
    )
}

final case class KnowledgeSimilarityRepresentation(
  method: Option[String] = None,
  model: Option[String] = None,
  metric: Option[String] = None,
  context: Option[String] = None,
  source: Option[KnowledgeSourceRef] = None,
  payloadReference: Option[String] = None
)

final case class KnowledgeSimilaritySearchEntry(
  provider: Option[String] = None,
  collection: Option[String] = None,
  searchId: Option[String] = None,
  indexedAt: Option[Instant] = None
)

final case class KnowledgeSimilarityStatus(value: String) {
  def print: String = value
}

object KnowledgeSimilarityStatus {
  val Missing: KnowledgeSimilarityStatus = KnowledgeSimilarityStatus("missing")
  val Indexed: KnowledgeSimilarityStatus = KnowledgeSimilarityStatus("indexed")
  val Stale: KnowledgeSimilarityStatus = KnowledgeSimilarityStatus("stale")
  val Failed: KnowledgeSimilarityStatus = KnowledgeSimilarityStatus("failed")
}

final case class KnowledgeNodeSimilarity(
  representations: Vector[KnowledgeSimilarityRepresentation] = Vector.empty,
  searchEntries: Vector[KnowledgeSimilaritySearchEntry] = Vector.empty,
  status: KnowledgeSimilarityStatus = KnowledgeSimilarityStatus.Missing
)

object KnowledgeNodeSimilarity {
  val empty: KnowledgeNodeSimilarity = KnowledgeNodeSimilarity()
}

final case class KnowledgeValidationStatus(value: String) {
  def print: String = value
}

object KnowledgeValidationStatus {
  val Unknown: KnowledgeValidationStatus = KnowledgeValidationStatus("unknown")
  val Valid: KnowledgeValidationStatus = KnowledgeValidationStatus("valid")
  val Invalid: KnowledgeValidationStatus = KnowledgeValidationStatus("invalid")
}

final case class KnowledgeNodeOperations(
  materializedAt: Option[Instant] = None,
  frameIds: Vector[KnowledgeFrameId] = Vector.empty,
  validationStatus: KnowledgeValidationStatus = KnowledgeValidationStatus.Unknown
)

object KnowledgeNodeOperations {
  val empty: KnowledgeNodeOperations = KnowledgeNodeOperations()
}

final case class KnowledgeAttributes(values: Map[String, String] = Map.empty)

object KnowledgeAttributes {
  val empty: KnowledgeAttributes = KnowledgeAttributes()

  def apply(values: (String, String)*): KnowledgeAttributes =
    KnowledgeAttributes(values.toMap)
}

final case class KnowledgeNode(
  id: KnowledgeNodeId,
  category: KnowledgeNodeCategory,
  identity: KnowledgeNodeIdentity = KnowledgeNodeIdentity.empty,
  presentation: KnowledgeNodePresentation = KnowledgeNodePresentation.empty,
  semantics: KnowledgeNodeSemantics = KnowledgeNodeSemantics.empty,
  structure: KnowledgeNodeStructure = KnowledgeNodeStructure.empty,
  sources: KnowledgeNodeSources = KnowledgeNodeSources.empty,
  bindings: KnowledgeNodeBindings = KnowledgeNodeBindings.empty,
  similarity: KnowledgeNodeSimilarity = KnowledgeNodeSimilarity.empty,
  operations: KnowledgeNodeOperations = KnowledgeNodeOperations.empty,
  attributes: KnowledgeAttributes = KnowledgeAttributes.empty
) {
  def label: Option[String] =
    presentation.defaultLabel

  def externalIdentifiers: Vector[ExternalKnowledgeIdentifier] =
    identity.externalIdentifiers

  def provenanceId: Option[KnowledgeProvenanceId] =
    sources.provenanceIds.headOption

  def withStructure(value: KnowledgeNodeStructure): KnowledgeNode =
    copy(structure = value)

  def withBindings(value: KnowledgeNodeBindings): KnowledgeNode =
    copy(bindings = value)

  def withOperations(value: KnowledgeNodeOperations): KnowledgeNode =
    copy(operations = value)
}

object KnowledgeNode {
  def apply(
    id: KnowledgeNodeId,
    kind: String
  ): KnowledgeNode =
    apply(id, kind, None, Vector.empty, None, Map.empty)

  def apply(
    id: KnowledgeNodeId,
    kind: String,
    externalidentifiers: Vector[ExternalKnowledgeIdentifier]
  ): KnowledgeNode =
    apply(id, kind, None, externalidentifiers, None, Map.empty)

  def apply(
    id: KnowledgeNodeId,
    kind: String,
    label: Option[String]
  ): KnowledgeNode =
    apply(id, kind, label, Vector.empty, None, Map.empty)

  def apply(
    id: KnowledgeNodeId,
    kind: String,
    label: Option[String],
    externalidentifiers: Vector[ExternalKnowledgeIdentifier]
  ): KnowledgeNode =
    apply(id, kind, label, externalidentifiers, None, Map.empty)

  def apply(
    id: KnowledgeNodeId,
    kind: String,
    label: Option[String],
    externalidentifiers: Vector[ExternalKnowledgeIdentifier],
    provenanceid: Option[KnowledgeProvenanceId]
  ): KnowledgeNode =
    apply(id, kind, label, externalidentifiers, provenanceid, Map.empty)

  def apply(
    id: KnowledgeNodeId,
    kind: String,
    label: Option[String],
    externalidentifiers: Vector[ExternalKnowledgeIdentifier],
    provenanceid: Option[KnowledgeProvenanceId],
    attributes: Map[String, String]
  ): KnowledgeNode =
    KnowledgeNode(
      id = id,
      category = KnowledgeNodeCategory(kind),
      identity = KnowledgeNodeIdentity(externalIdentifiers = externalidentifiers),
      presentation = label.map(KnowledgeNodePresentation.label).getOrElse(KnowledgeNodePresentation.empty),
      sources = KnowledgeNodeSources(provenanceIds = provenanceid.toVector),
      bindings = KnowledgeNodeBindings.from(externalidentifiers),
      attributes = KnowledgeAttributes(attributes)
    )
}

final case class KnowledgeRelationshipQualifiers(values: Map[String, String] = Map.empty)

object KnowledgeRelationshipQualifiers {
  val empty: KnowledgeRelationshipQualifiers = KnowledgeRelationshipQualifiers()
}

final case class KnowledgeRelationshipSimilarity(
  representations: Vector[KnowledgeSimilarityRepresentation] = Vector.empty,
  searchEntries: Vector[KnowledgeSimilaritySearchEntry] = Vector.empty,
  status: KnowledgeSimilarityStatus = KnowledgeSimilarityStatus.Missing
)

object KnowledgeRelationshipSimilarity {
  val empty: KnowledgeRelationshipSimilarity = KnowledgeRelationshipSimilarity()
}

final case class KnowledgeRelationship(
  id: KnowledgeRelationshipId,
  kind: KnowledgeRelationshipKind,
  sourceNodeId: KnowledgeNodeId,
  targetNodeId: KnowledgeNodeId,
  rdfPredicate: Option[RdfPredicateName] = None,
  semanticTypes: Vector[KnowledgeRelationshipSemanticType] = Vector.empty,
  evidenceIds: Vector[KnowledgeEvidenceId] = Vector.empty,
  provenanceId: Option[KnowledgeProvenanceId] = None,
  qualifiers: KnowledgeRelationshipQualifiers = KnowledgeRelationshipQualifiers.empty,
  similarity: KnowledgeRelationshipSimilarity = KnowledgeRelationshipSimilarity.empty,
  attributes: KnowledgeAttributes = KnowledgeAttributes.empty
)

object KnowledgeRelationship {
  def apply(
    id: KnowledgeRelationshipId,
    kind: String,
    sourcenodeid: KnowledgeNodeId,
    targetnodeid: KnowledgeNodeId
  ): KnowledgeRelationship =
    apply(id, kind, sourcenodeid, targetnodeid, Vector.empty, None, Map.empty)

  def apply(
    id: KnowledgeRelationshipId,
    kind: String,
    sourcenodeid: KnowledgeNodeId,
    targetnodeid: KnowledgeNodeId,
    evidenceids: Vector[KnowledgeEvidenceId],
    provenanceid: Option[KnowledgeProvenanceId]
  ): KnowledgeRelationship =
    apply(id, kind, sourcenodeid, targetnodeid, evidenceids, provenanceid, Map.empty)

  def apply(
    id: KnowledgeRelationshipId,
    kind: String,
    sourcenodeid: KnowledgeNodeId,
    targetnodeid: KnowledgeNodeId,
    evidenceids: Vector[KnowledgeEvidenceId],
    provenanceid: Option[KnowledgeProvenanceId],
    attributes: Map[String, String]
  ): KnowledgeRelationship =
    KnowledgeRelationship(
      id = id,
      kind = KnowledgeRelationshipKind(kind),
      sourceNodeId = sourcenodeid,
      targetNodeId = targetnodeid,
      evidenceIds = evidenceids,
      provenanceId = provenanceid,
      attributes = KnowledgeAttributes(attributes)
    )
}

final case class KnowledgeFactKind(value: String) {
  def print: String = value
}

object KnowledgeFactKind {
  val EntityDerived: KnowledgeFactKind = KnowledgeFactKind("entity-derived")
  val RdfDerived: KnowledgeFactKind = KnowledgeFactKind("rdf-derived")
  val Generated: KnowledgeFactKind = KnowledgeFactKind("generated")
}

final case class KnowledgeFact(
  id: KnowledgeFactId,
  kind: KnowledgeFactKind,
  subjectNodeId: Option[KnowledgeNodeId] = None,
  relationshipId: Option[KnowledgeRelationshipId] = None,
  predicate: Option[String] = None,
  value: Option[String] = None,
  evidenceIds: Vector[KnowledgeEvidenceId] = Vector.empty,
  provenanceId: Option[KnowledgeProvenanceId] = None,
  attributes: KnowledgeAttributes = KnowledgeAttributes.empty
)

final case class KnowledgeFrameKind(value: String) {
  def print: String = value
}

object KnowledgeFrameKind {
  val RetrievalResult: KnowledgeFrameKind = KnowledgeFrameKind("retrieval-result")
  val EntityContext: KnowledgeFrameKind = KnowledgeFrameKind("entity-context")
  val Explanation: KnowledgeFrameKind = KnowledgeFrameKind("explanation")
  val SourceContext: KnowledgeFrameKind = KnowledgeFrameKind("source-context")
  val ClassificationContext: KnowledgeFrameKind = KnowledgeFrameKind("classification-context")
  val Curated: KnowledgeFrameKind = KnowledgeFrameKind("curated")
  val Generated: KnowledgeFrameKind = KnowledgeFrameKind("generated")
}

final case class KnowledgeFrameInputRoute(value: String) {
  def print: String = value
}

object KnowledgeFrameInputRoute {
  val SieRetrieval: KnowledgeFrameInputRoute = KnowledgeFrameInputRoute("sie-retrieval")
  val EntityProjection: KnowledgeFrameInputRoute = KnowledgeFrameInputRoute("entity-projection")
  val TagAssociationProjection: KnowledgeFrameInputRoute = KnowledgeFrameInputRoute("tag-association-projection")
  val BatchImport: KnowledgeFrameInputRoute = KnowledgeFrameInputRoute("batch-import")
  val ManualCuration: KnowledgeFrameInputRoute = KnowledgeFrameInputRoute("manual-curation")
  val ExternalGraphImport: KnowledgeFrameInputRoute = KnowledgeFrameInputRoute("external-graph-import")
  val OperationGenerated: KnowledgeFrameInputRoute = KnowledgeFrameInputRoute("operation-generated")
  val AdminApi: KnowledgeFrameInputRoute = KnowledgeFrameInputRoute("admin-api")
}

final case class KnowledgeFrameOrigin(
  route: KnowledgeFrameInputRoute,
  provider: Option[String] = None,
  operation: Option[String] = None,
  component: Option[String] = None,
  sourceGraph: Option[String] = None,
  sourceDataset: Option[String] = None,
  requestId: Option[String] = None,
  jobId: Option[String] = None,
  taskId: Option[String] = None,
  createdBy: Option[String] = None,
  provenanceId: Option[KnowledgeProvenanceId] = None
)

final case class KnowledgeFramePurpose(value: String) {
  def print: String = value
}

final case class KnowledgeQueryRef(value: String) {
  def print: String = value
}

final case class KnowledgeFrame(
  id: KnowledgeFrameId,
  kind: KnowledgeFrameKind,
  focusNodeIds: Vector[KnowledgeNodeId] = Vector.empty,
  nodeIds: Vector[KnowledgeNodeId] = Vector.empty,
  relationshipIds: Vector[KnowledgeRelationshipId] = Vector.empty,
  factIds: Vector[KnowledgeFactId] = Vector.empty,
  evidenceIds: Vector[KnowledgeEvidenceId] = Vector.empty,
  provenanceIds: Vector[KnowledgeProvenanceId] = Vector.empty,
  origin: KnowledgeFrameOrigin,
  sourceRefs: Vector[KnowledgeSourceRef] = Vector.empty,
  purpose: Option[KnowledgeFramePurpose] = None,
  query: Option[KnowledgeQueryRef] = None,
  materializedAt: Option[Instant] = None,
  confidence: Option[Double] = None,
  attributes: KnowledgeAttributes = KnowledgeAttributes.empty
)

final case class KnowledgeEvidence(
  id: KnowledgeEvidenceId,
  kind: String,
  source: KnowledgeSourceRef,
  summary: Option[String] = None,
  provenanceId: Option[KnowledgeProvenanceId] = None,
  attributes: KnowledgeAttributes = KnowledgeAttributes.empty
)

object KnowledgeEvidence {
  def apply(
    id: KnowledgeEvidenceId,
    kind: String,
    source: KnowledgeSourceRef,
    summary: Option[String],
    provenanceId: Option[KnowledgeProvenanceId],
    attributes: Map[String, String]
  ): KnowledgeEvidence =
    KnowledgeEvidence(id, kind, source, summary, provenanceId, KnowledgeAttributes(attributes))
}

final case class KnowledgeProvenance(
  id: KnowledgeProvenanceId,
  origin: String,
  owner: Option[String] = None,
  generatedBy: Option[String] = None,
  confidence: Option[Double] = None,
  attributes: KnowledgeAttributes = KnowledgeAttributes.empty
)

object KnowledgeProvenance {
  def apply(
    id: KnowledgeProvenanceId,
    origin: String,
    owner: Option[String],
    generatedBy: Option[String],
    confidence: Option[Double],
    attributes: Map[String, String]
  ): KnowledgeProvenance =
    KnowledgeProvenance(id, origin, owner, generatedBy, confidence, KnowledgeAttributes(attributes))
}
