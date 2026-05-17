package org.goldenport.cncf.knowledge

import org.goldenport.Consequence

/*
 * @since   May. 17, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class KnowledgeSpace {
  private var _working_set: KnowledgeWorkingSet = KnowledgeWorkingSet.empty

  def replace(snapshot: KnowledgeWorkingSetSnapshot): Consequence[KnowledgeWorkingSetStatus] =
    KnowledgeWorkingSet.load(snapshot) match {
      case Consequence.Success(workingset) =>
        _working_set = workingset
        Consequence.success(workingset.status)
      case Consequence.Failure(conclusion) =>
        _working_set = KnowledgeWorkingSet.failed(_working_set, conclusion.toString)
        Consequence.Failure(conclusion)
    }

  def clear(): Unit =
    _working_set = KnowledgeWorkingSet.empty

  def status: KnowledgeWorkingSetStatus =
    _working_set.status

  def snapshot: KnowledgeWorkingSetSnapshot =
    _working_set.snapshot

  def counts: KnowledgeWorkingSetCounts =
    _working_set.counts

  def query: KnowledgeSpaceQuery =
    KnowledgeSpaceQuery(this)

  def nodeOption(id: KnowledgeNodeId): Option[KnowledgeNode] =
    _working_set.nodeOption(id)

  def relationshipOption(id: KnowledgeRelationshipId): Option[KnowledgeRelationship] =
    _working_set.relationshipOption(id)

  def relationshipsFrom(id: KnowledgeNodeId): Vector[KnowledgeRelationship] =
    _working_set.relationshipsFrom(id)

  def relationshipsTo(id: KnowledgeNodeId): Vector[KnowledgeRelationship] =
    _working_set.relationshipsTo(id)

  def frameOption(id: KnowledgeFrameId): Option[KnowledgeFrame] =
    _working_set.frameOption(id)

  def framesForNode(id: KnowledgeNodeId): Vector[KnowledgeFrame] =
    _working_set.framesForNode(id)

  def factOption(id: KnowledgeFactId): Option[KnowledgeFact] =
    _working_set.factOption(id)

  def factsForNode(id: KnowledgeNodeId): Vector[KnowledgeFact] =
    _working_set.factsForNode(id)

  def factsForRelationship(id: KnowledgeRelationshipId): Vector[KnowledgeFact] =
    _working_set.factsForRelationship(id)

  def nodeIdsByExternalIdentifier(id: ExternalKnowledgeIdentifier): Vector[KnowledgeNodeId] =
    _working_set.nodeIdsByExternalIdentifier(id)

  def nodeIdsByEntityBinding(id: KnowledgeEntityBinding): Vector[KnowledgeNodeId] =
    _working_set.nodeIdsByEntityBinding(id)

  def nodeIdsByTagBinding(id: KnowledgeTagBinding): Vector[KnowledgeNodeId] =
    _working_set.nodeIdsByTagBinding(id)

  def evidenceOption(id: KnowledgeEvidenceId): Option[KnowledgeEvidence] =
    _working_set.evidenceOption(id)

  def provenanceOption(id: KnowledgeProvenanceId): Option[KnowledgeProvenance] =
    _working_set.provenanceOption(id)
}
