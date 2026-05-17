package org.goldenport.cncf.knowledge

import org.goldenport.record.Record

/*
 * @since   May. 18, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class KnowledgeSpaceQueryResult(
  nodes: Vector[KnowledgeNode] = Vector.empty,
  relationships: Vector[KnowledgeRelationship] = Vector.empty,
  frames: Vector[KnowledgeFrame] = Vector.empty,
  facts: Vector[KnowledgeFact] = Vector.empty,
  evidence: Vector[KnowledgeEvidence] = Vector.empty,
  provenance: Vector[KnowledgeProvenance] = Vector.empty
) {
  def isEmpty: Boolean =
    nodes.isEmpty &&
      relationships.isEmpty &&
      frames.isEmpty &&
      facts.isEmpty &&
      evidence.isEmpty &&
      provenance.isEmpty

  def toRecord: Record =
    Record.dataAuto(
      "nodes" -> nodes.map(KnowledgeRecordCodec.toRecord),
      "relationships" -> relationships.map(KnowledgeRecordCodec.toRecord),
      "frames" -> frames.map(KnowledgeRecordCodec.toRecord),
      "facts" -> facts.map(KnowledgeRecordCodec.toRecord),
      "evidence" -> evidence.map(KnowledgeRecordCodec.toRecord),
      "provenance" -> provenance.map(KnowledgeRecordCodec.toRecord)
    )
}

final class KnowledgeSpaceQuery(space: KnowledgeSpace) {
  def node(id: KnowledgeNodeId): KnowledgeSpaceQueryResult =
    KnowledgeSpaceQueryResult(nodes = space.nodeOption(id).toVector)

  def relationship(id: KnowledgeRelationshipId): KnowledgeSpaceQueryResult =
    KnowledgeSpaceQueryResult(relationships = space.relationshipOption(id).toVector)

  def frame(id: KnowledgeFrameId): KnowledgeSpaceQueryResult =
    KnowledgeSpaceQueryResult(frames = space.frameOption(id).toVector)

  def fact(id: KnowledgeFactId): KnowledgeSpaceQueryResult =
    KnowledgeSpaceQueryResult(facts = space.factOption(id).toVector)

  def rdfNode(name: RdfNodeName): KnowledgeSpaceQueryResult =
    KnowledgeSpaceQueryResult(nodes = _nodes.filter(_.identity.rdfNode.contains(name)))

  def externalIdentifier(id: ExternalKnowledgeIdentifier): KnowledgeSpaceQueryResult =
    KnowledgeSpaceQueryResult(nodes = space.nodeIdsByExternalIdentifier(id).flatMap(space.nodeOption))

  def entityBinding(binding: KnowledgeEntityBinding): KnowledgeSpaceQueryResult =
    KnowledgeSpaceQueryResult(nodes = space.nodeIdsByEntityBinding(binding).flatMap(space.nodeOption))

  def tagBinding(binding: KnowledgeTagBinding): KnowledgeSpaceQueryResult =
    KnowledgeSpaceQueryResult(nodes = space.nodeIdsByTagBinding(binding).flatMap(space.nodeOption))

  def semanticType(
    system: String,
    name: String
  ): KnowledgeSpaceQueryResult =
    KnowledgeSpaceQueryResult(
      nodes = _nodes.filter(_.semantics.semanticTypes.exists(x => x.system == system && x.name == name)),
      relationships = _relationships.filter(_.semanticTypes.exists(x => x.system == system && x.name == name))
    )

  private def _nodes: Vector[KnowledgeNode] =
    space.snapshot.nodes.sortBy(_.id.print)

  private def _relationships: Vector[KnowledgeRelationship] =
    space.snapshot.relationships.sortBy(_.id.print)
}

object KnowledgeSpaceQuery {
  def apply(space: KnowledgeSpace): KnowledgeSpaceQuery =
    new KnowledgeSpaceQuery(space)
}
