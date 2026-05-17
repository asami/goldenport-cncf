package org.goldenport.cncf.knowledge

import org.goldenport.record.Record

/*
 * @since   May. 17, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
object KnowledgeRecordCodec {
  def toRecord(status: KnowledgeWorkingSetStatus): Record =
    Record.dataOption(
      "state" -> Some(status.state.label),
      "ready" -> Some(status.isReady),
      "started_at" -> status.startedAt.map(_.toString),
      "completed_at" -> status.completedAt.map(_.toString),
      "error" -> status.error
    )

  def toRecord(counts: KnowledgeWorkingSetCounts): Record =
    Record.dataAuto(
      "nodes" -> counts.nodeCount,
      "relationships" -> counts.relationshipCount,
      "evidence" -> counts.evidenceCount,
      "provenance" -> counts.provenanceCount,
      "external_identifiers" -> counts.externalIdentifierCount
    )

  def toRecord(source: KnowledgeProjectionSourceDiagnostics): Record =
    Record.dataOption(
      "source_kind" -> Some(source.sourceKind),
      "storage" -> Some(source.storage),
      "provider_status" -> Some(source.providerStatus),
      "projection_mode" -> Some(source.projectionMode),
      "message" -> source.message
    )

  def toRecord(id: ExternalKnowledgeIdentifier): Record =
    Record.dataOption(
      "system" -> Some(id.system),
      "kind" -> id.kind,
      "value" -> Some(id.value),
      "key" -> Some(id.key)
    )

  def toRecord(ref: KnowledgeSourceRef): Record =
    Record.dataOption(
      "kind" -> Some(ref.kind),
      "value" -> Some(ref.value),
      "uri" -> ref.uri
    )

  def toRecord(node: KnowledgeNode): Record =
    Record.dataOption(
      "id" -> Some(node.id.print),
      "kind" -> Some(node.kind),
      "label" -> node.label,
      "external_identifiers" -> Some(node.externalIdentifiers.map(toRecord)),
      "provenance_id" -> node.provenanceId.map(_.print),
      "attributes" -> Some(Record.data(node.attributes.toVector.sortBy(_._1)*))
    )

  def toRecord(relationship: KnowledgeRelationship): Record =
    Record.dataOption(
      "id" -> Some(relationship.id.print),
      "kind" -> Some(relationship.kind),
      "source_node_id" -> Some(relationship.sourceNodeId.print),
      "target_node_id" -> Some(relationship.targetNodeId.print),
      "evidence_ids" -> Some(relationship.evidenceIds.map(_.print)),
      "provenance_id" -> relationship.provenanceId.map(_.print),
      "attributes" -> Some(Record.data(relationship.attributes.toVector.sortBy(_._1)*))
    )

  def toRecord(evidence: KnowledgeEvidence): Record =
    Record.dataOption(
      "id" -> Some(evidence.id.print),
      "kind" -> Some(evidence.kind),
      "source" -> Some(toRecord(evidence.source)),
      "summary" -> evidence.summary,
      "provenance_id" -> evidence.provenanceId.map(_.print),
      "attributes" -> Some(Record.data(evidence.attributes.toVector.sortBy(_._1)*))
    )

  def toRecord(provenance: KnowledgeProvenance): Record =
    Record.dataOption(
      "id" -> Some(provenance.id.print),
      "origin" -> Some(provenance.origin),
      "owner" -> provenance.owner,
      "generated_by" -> provenance.generatedBy,
      "confidence" -> provenance.confidence,
      "attributes" -> Some(Record.data(provenance.attributes.toVector.sortBy(_._1)*))
    )
}
