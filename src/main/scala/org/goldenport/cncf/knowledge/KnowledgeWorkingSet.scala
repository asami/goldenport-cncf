package org.goldenport.cncf.knowledge

import java.time.Instant
import org.goldenport.Consequence

/*
 * @since   May. 17, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class KnowledgeWorkingSetSnapshot(
  nodes: Vector[KnowledgeNode] = Vector.empty,
  relationships: Vector[KnowledgeRelationship] = Vector.empty,
  evidence: Vector[KnowledgeEvidence] = Vector.empty,
  provenance: Vector[KnowledgeProvenance] = Vector.empty
)

enum KnowledgeWorkingSetState {
  case Disabled, NotStarted, Loading, Ready, Failed

  def label: String =
    this match {
      case Disabled => "disabled"
      case NotStarted => "not_started"
      case Loading => "loading"
      case Ready => "ready"
      case Failed => "failed"
    }
}

final case class KnowledgeWorkingSetStatus(
  state: KnowledgeWorkingSetState,
  startedAt: Option[Instant] = None,
  completedAt: Option[Instant] = None,
  error: Option[String] = None
) {
  def isReady: Boolean =
    state == KnowledgeWorkingSetState.Ready
}

final case class KnowledgeWorkingSetCounts(
  nodeCount: Int = 0,
  relationshipCount: Int = 0,
  evidenceCount: Int = 0,
  provenanceCount: Int = 0,
  externalIdentifierCount: Int = 0
)

final class KnowledgeWorkingSet private(
  val snapshot: KnowledgeWorkingSetSnapshot,
  val status: KnowledgeWorkingSetStatus,
  private val _nodes: Map[KnowledgeNodeId, KnowledgeNode],
  private val _relationships: Map[KnowledgeRelationshipId, KnowledgeRelationship],
  private val _relationships_by_source: Map[KnowledgeNodeId, Vector[KnowledgeRelationship]],
  private val _relationships_by_target: Map[KnowledgeNodeId, Vector[KnowledgeRelationship]],
  private val _evidence: Map[KnowledgeEvidenceId, KnowledgeEvidence],
  private val _provenance: Map[KnowledgeProvenanceId, KnowledgeProvenance],
  private val _external_identifiers: Map[ExternalKnowledgeIdentifier, Vector[KnowledgeNodeId]]
) {
  def counts: KnowledgeWorkingSetCounts =
    KnowledgeWorkingSetCounts(
      nodeCount = _nodes.size,
      relationshipCount = _relationships.size,
      evidenceCount = _evidence.size,
      provenanceCount = _provenance.size,
      externalIdentifierCount = _external_identifiers.values.map(_.size).sum
    )

  def nodeOption(id: KnowledgeNodeId): Option[KnowledgeNode] =
    _nodes.get(id)

  def relationshipOption(id: KnowledgeRelationshipId): Option[KnowledgeRelationship] =
    _relationships.get(id)

  def relationshipsFrom(id: KnowledgeNodeId): Vector[KnowledgeRelationship] =
    _relationships_by_source.getOrElse(id, Vector.empty)

  def relationshipsTo(id: KnowledgeNodeId): Vector[KnowledgeRelationship] =
    _relationships_by_target.getOrElse(id, Vector.empty)

  def evidenceOption(id: KnowledgeEvidenceId): Option[KnowledgeEvidence] =
    _evidence.get(id)

  def provenanceOption(id: KnowledgeProvenanceId): Option[KnowledgeProvenance] =
    _provenance.get(id)

  def nodeIdsByExternalIdentifier(id: ExternalKnowledgeIdentifier): Vector[KnowledgeNodeId] =
    _external_identifiers.getOrElse(id, Vector.empty)
}

object KnowledgeWorkingSet {
  val empty: KnowledgeWorkingSet =
    new KnowledgeWorkingSet(
      KnowledgeWorkingSetSnapshot(),
      KnowledgeWorkingSetStatus(KnowledgeWorkingSetState.NotStarted),
      Map.empty,
      Map.empty,
      Map.empty,
      Map.empty,
      Map.empty,
      Map.empty,
      Map.empty
    )

  def failed(
    previous: KnowledgeWorkingSet,
    error: String
  ): KnowledgeWorkingSet =
    new KnowledgeWorkingSet(
      previous.snapshot,
      KnowledgeWorkingSetStatus(
        state = KnowledgeWorkingSetState.Failed,
        completedAt = Some(Instant.now()),
        error = Some(error)
      ),
      previous._nodes,
      previous._relationships,
      previous._relationships_by_source,
      previous._relationships_by_target,
      previous._evidence,
      previous._provenance,
      previous._external_identifiers
    )

  def load(snapshot: KnowledgeWorkingSetSnapshot): Consequence[KnowledgeWorkingSet] =
    for {
      _ <- _validate_unique("knowledge node", snapshot.nodes.map(_.id.print))
      _ <- _validate_unique("knowledge relationship", snapshot.relationships.map(_.id.print))
      _ <- _validate_unique("knowledge evidence", snapshot.evidence.map(_.id.print))
      _ <- _validate_unique("knowledge provenance", snapshot.provenance.map(_.id.print))
      nodes = snapshot.nodes.map(x => x.id -> x).toMap
      evidence = snapshot.evidence.map(x => x.id -> x).toMap
      provenance = snapshot.provenance.map(x => x.id -> x).toMap
      _ <- _validate_relationship_endpoints(snapshot.relationships, nodes)
      _ <- _validate_relationship_evidence(snapshot.relationships, evidence)
      _ <- _validate_node_provenance(snapshot.nodes, provenance)
      _ <- _validate_relationship_provenance(snapshot.relationships, provenance)
      _ <- _validate_evidence_provenance(snapshot.evidence, provenance)
    } yield {
      val relationships = snapshot.relationships.map(x => x.id -> x).toMap
      val bysource = snapshot.relationships.groupBy(_.sourceNodeId).view.mapValues(_.toVector).toMap
      val bytarget = snapshot.relationships.groupBy(_.targetNodeId).view.mapValues(_.toVector).toMap
      val externalids = snapshot.nodes
        .flatMap(node => node.externalIdentifiers.map(_ -> node.id))
        .groupMap(_._1)(_._2)
      new KnowledgeWorkingSet(
        snapshot,
        KnowledgeWorkingSetStatus(
          state = KnowledgeWorkingSetState.Ready,
          completedAt = Some(Instant.now())
        ),
        nodes,
        relationships,
        bysource,
        bytarget,
        evidence,
        provenance,
        externalids
      )
    }

  private def _validate_unique(
    label: String,
    values: Vector[String]
  ): Consequence[Unit] = {
    val duplicate = values.groupBy(identity).collectFirst {
      case (k, xs) if xs.size > 1 => k
    }
    duplicate match {
      case Some(value) => Consequence.argumentInvalid(s"duplicate $label id: $value")
      case None => Consequence.unit
    }
  }

  private def _validate_relationship_endpoints(
    relationships: Vector[KnowledgeRelationship],
    nodes: Map[KnowledgeNodeId, KnowledgeNode]
  ): Consequence[Unit] =
    relationships.find(x => !nodes.contains(x.sourceNodeId) || !nodes.contains(x.targetNodeId)) match {
      case Some(value) =>
        Consequence.argumentInvalid(s"knowledge relationship references missing node: ${value.id.print}")
      case None =>
        Consequence.unit
    }

  private def _validate_relationship_evidence(
    relationships: Vector[KnowledgeRelationship],
    evidence: Map[KnowledgeEvidenceId, KnowledgeEvidence]
  ): Consequence[Unit] =
    relationships.find(_.evidenceIds.exists(x => !evidence.contains(x))) match {
      case Some(value) =>
        Consequence.argumentInvalid(s"knowledge relationship references missing evidence: ${value.id.print}")
      case None =>
        Consequence.unit
    }

  private def _validate_node_provenance(
    nodes: Vector[KnowledgeNode],
    provenance: Map[KnowledgeProvenanceId, KnowledgeProvenance]
  ): Consequence[Unit] =
    nodes.find(_.provenanceId.exists(x => !provenance.contains(x))) match {
      case Some(value) =>
        Consequence.argumentInvalid(s"knowledge node references missing provenance: ${value.id.print}")
      case None =>
        Consequence.unit
    }

  private def _validate_relationship_provenance(
    relationships: Vector[KnowledgeRelationship],
    provenance: Map[KnowledgeProvenanceId, KnowledgeProvenance]
  ): Consequence[Unit] =
    relationships.find(_.provenanceId.exists(x => !provenance.contains(x))) match {
      case Some(value) =>
        Consequence.argumentInvalid(s"knowledge relationship references missing provenance: ${value.id.print}")
      case None =>
        Consequence.unit
    }

  private def _validate_evidence_provenance(
    evidence: Vector[KnowledgeEvidence],
    provenance: Map[KnowledgeProvenanceId, KnowledgeProvenance]
  ): Consequence[Unit] =
    evidence.find(_.provenanceId.exists(x => !provenance.contains(x))) match {
      case Some(value) =>
        Consequence.argumentInvalid(s"knowledge evidence references missing provenance: ${value.id.print}")
      case None =>
        Consequence.unit
    }
}
