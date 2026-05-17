package org.goldenport.cncf.knowledge

/*
 * @since   May. 17, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class KnowledgeNodeId(value: String) {
  def print: String = value
}

final case class KnowledgeRelationshipId(value: String) {
  def print: String = value
}

final case class KnowledgeEvidenceId(value: String) {
  def print: String = value
}

final case class KnowledgeProvenanceId(value: String) {
  def print: String = value
}

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

  def entity(
    entityName: String,
    entityId: String
  ): ExternalKnowledgeIdentifier =
    ExternalKnowledgeIdentifier(ENTITY_SYSTEM, entityId, Some(entityName))
}

final case class KnowledgeSourceRef(
  kind: String,
  value: String,
  uri: Option[String] = None
)

final case class KnowledgeNode(
  id: KnowledgeNodeId,
  kind: String,
  label: Option[String] = None,
  externalIdentifiers: Vector[ExternalKnowledgeIdentifier] = Vector.empty,
  provenanceId: Option[KnowledgeProvenanceId] = None,
  attributes: Map[String, String] = Map.empty
)

final case class KnowledgeRelationship(
  id: KnowledgeRelationshipId,
  kind: String,
  sourceNodeId: KnowledgeNodeId,
  targetNodeId: KnowledgeNodeId,
  evidenceIds: Vector[KnowledgeEvidenceId] = Vector.empty,
  provenanceId: Option[KnowledgeProvenanceId] = None,
  attributes: Map[String, String] = Map.empty
)

final case class KnowledgeEvidence(
  id: KnowledgeEvidenceId,
  kind: String,
  source: KnowledgeSourceRef,
  summary: Option[String] = None,
  provenanceId: Option[KnowledgeProvenanceId] = None,
  attributes: Map[String, String] = Map.empty
)

final case class KnowledgeProvenance(
  id: KnowledgeProvenanceId,
  origin: String,
  owner: Option[String] = None,
  generatedBy: Option[String] = None,
  confidence: Option[Double] = None,
  attributes: Map[String, String] = Map.empty
)
