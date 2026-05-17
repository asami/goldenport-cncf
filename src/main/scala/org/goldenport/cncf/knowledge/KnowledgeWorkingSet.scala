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
  provenance: Vector[KnowledgeProvenance] = Vector.empty,
  frames: Vector[KnowledgeFrame] = Vector.empty,
  facts: Vector[KnowledgeFact] = Vector.empty
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
  externalIdentifierCount: Int = 0,
  frameCount: Int = 0,
  factCount: Int = 0,
  entityBindingCount: Int = 0,
  tagBindingCount: Int = 0
)

final class KnowledgeWorkingSet private(
  val snapshot: KnowledgeWorkingSetSnapshot,
  val status: KnowledgeWorkingSetStatus,
  private val _nodes: Map[KnowledgeNodeId, KnowledgeNode],
  private val _relationships: Map[KnowledgeRelationshipId, KnowledgeRelationship],
  private val _relationships_by_source: Map[KnowledgeNodeId, Vector[KnowledgeRelationship]],
  private val _relationships_by_target: Map[KnowledgeNodeId, Vector[KnowledgeRelationship]],
  private val _frames: Map[KnowledgeFrameId, KnowledgeFrame],
  private val _frames_by_focus: Map[KnowledgeNodeId, Vector[KnowledgeFrame]],
  private val _facts: Map[KnowledgeFactId, KnowledgeFact],
  private val _facts_by_node: Map[KnowledgeNodeId, Vector[KnowledgeFact]],
  private val _facts_by_relationship: Map[KnowledgeRelationshipId, Vector[KnowledgeFact]],
  private val _evidence: Map[KnowledgeEvidenceId, KnowledgeEvidence],
  private val _provenance: Map[KnowledgeProvenanceId, KnowledgeProvenance],
  private val _external_identifiers: Map[ExternalKnowledgeIdentifier, Vector[KnowledgeNodeId]],
  private val _entity_bindings: Map[KnowledgeEntityBinding, Vector[KnowledgeNodeId]],
  private val _tag_bindings: Map[KnowledgeTagBinding, Vector[KnowledgeNodeId]]
) {
  def counts: KnowledgeWorkingSetCounts =
    KnowledgeWorkingSetCounts(
      nodeCount = _nodes.size,
      relationshipCount = _relationships.size,
      evidenceCount = _evidence.size,
      provenanceCount = _provenance.size,
      externalIdentifierCount = _external_identifiers.values.map(_.size).sum,
      frameCount = _frames.size,
      factCount = _facts.size,
      entityBindingCount = _entity_bindings.values.map(_.size).sum,
      tagBindingCount = _tag_bindings.values.map(_.size).sum
    )

  def nodeOption(id: KnowledgeNodeId): Option[KnowledgeNode] =
    _nodes.get(id)

  def relationshipOption(id: KnowledgeRelationshipId): Option[KnowledgeRelationship] =
    _relationships.get(id)

  def relationshipsFrom(id: KnowledgeNodeId): Vector[KnowledgeRelationship] =
    _relationships_by_source.getOrElse(id, Vector.empty)

  def relationshipsTo(id: KnowledgeNodeId): Vector[KnowledgeRelationship] =
    _relationships_by_target.getOrElse(id, Vector.empty)

  def frameOption(id: KnowledgeFrameId): Option[KnowledgeFrame] =
    _frames.get(id)

  def framesForNode(id: KnowledgeNodeId): Vector[KnowledgeFrame] =
    _frames_by_focus.getOrElse(id, Vector.empty)

  def factOption(id: KnowledgeFactId): Option[KnowledgeFact] =
    _facts.get(id)

  def factsForNode(id: KnowledgeNodeId): Vector[KnowledgeFact] =
    _facts_by_node.getOrElse(id, Vector.empty)

  def factsForRelationship(id: KnowledgeRelationshipId): Vector[KnowledgeFact] =
    _facts_by_relationship.getOrElse(id, Vector.empty)

  def evidenceOption(id: KnowledgeEvidenceId): Option[KnowledgeEvidence] =
    _evidence.get(id)

  def provenanceOption(id: KnowledgeProvenanceId): Option[KnowledgeProvenance] =
    _provenance.get(id)

  def nodeIdsByExternalIdentifier(id: ExternalKnowledgeIdentifier): Vector[KnowledgeNodeId] =
    _external_identifiers.getOrElse(id, Vector.empty)

  def nodeIdsByEntityBinding(id: KnowledgeEntityBinding): Vector[KnowledgeNodeId] =
    _entity_bindings.getOrElse(id, Vector.empty)

  def nodeIdsByTagBinding(id: KnowledgeTagBinding): Vector[KnowledgeNodeId] =
    _tag_bindings.getOrElse(id, Vector.empty)
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
      Map.empty,
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
      previous._frames,
      previous._frames_by_focus,
      previous._facts,
      previous._facts_by_node,
      previous._facts_by_relationship,
      previous._evidence,
      previous._provenance,
      previous._external_identifiers,
      previous._entity_bindings,
      previous._tag_bindings
    )

  def load(snapshot: KnowledgeWorkingSetSnapshot): Consequence[KnowledgeWorkingSet] =
    for {
      _ <- _validate_unique("knowledge node", snapshot.nodes.map(_.id.print))
      _ <- _validate_unique("knowledge relationship", snapshot.relationships.map(_.id.print))
      _ <- _validate_unique("knowledge frame", snapshot.frames.map(_.id.print))
      _ <- _validate_unique("knowledge fact", snapshot.facts.map(_.id.print))
      _ <- _validate_unique("knowledge evidence", snapshot.evidence.map(_.id.print))
      _ <- _validate_unique("knowledge provenance", snapshot.provenance.map(_.id.print))
      basenodes = snapshot.nodes.map(x => x.id -> x).toMap
      relationships = snapshot.relationships.map(x => x.id -> x).toMap
      evidence = snapshot.evidence.map(x => x.id -> x).toMap
      provenance = snapshot.provenance.map(x => x.id -> x).toMap
      facts = snapshot.facts.map(x => x.id -> x).toMap
      _ <- _validate_relationship_endpoints(snapshot.relationships, basenodes)
      _ <- _validate_relationship_evidence(snapshot.relationships, evidence)
      _ <- _validate_node_sources(snapshot.nodes, evidence, provenance)
      _ <- _validate_relationship_provenance(snapshot.relationships, provenance)
      _ <- _validate_evidence_provenance(snapshot.evidence, provenance)
      _ <- _validate_fact_references(snapshot.facts, basenodes, relationships, evidence, provenance)
      _ <- _validate_frame_references(snapshot.frames, basenodes, relationships, facts, evidence, provenance)
    } yield {
      val projected = _materialize_nodes(snapshot)
      val nodes = projected.map(x => x.id -> x).toMap
      val materialized = snapshot.copy(nodes = projected)
      val bysource = snapshot.relationships.groupBy(_.sourceNodeId).view.mapValues(_.toVector).toMap
      val bytarget = snapshot.relationships.groupBy(_.targetNodeId).view.mapValues(_.toVector).toMap
      val frames = snapshot.frames.map(x => x.id -> x).toMap
      val framesbyfocus = snapshot.frames
        .flatMap(x => _distinct_ids(x.focusNodeIds ++ x.nodeIds).map(_ -> x))
        .groupMap(_._1)(_._2)
      val factsbynode = snapshot.facts.flatMap(x => x.subjectNodeId.map(_ -> x)).groupMap(_._1)(_._2)
      val factsbyrelationship = snapshot.facts.flatMap(x => x.relationshipId.map(_ -> x)).groupMap(_._1)(_._2)
      val externalids = projected
        .flatMap(node => node.identity.externalIdentifiers.map(_ -> node.id))
        .groupMap(_._1)(_._2)
      val entitybindings = projected
        .flatMap(node => node.bindings.entityBindings.map(_ -> node.id))
        .groupMap(_._1)(_._2)
      val tagbindings = projected
        .flatMap(node => node.bindings.tagBindings.map(_ -> node.id))
        .groupMap(_._1)(_._2)
      new KnowledgeWorkingSet(
        materialized,
        KnowledgeWorkingSetStatus(
          state = KnowledgeWorkingSetState.Ready,
          completedAt = Some(Instant.now())
        ),
        nodes,
        relationships,
        bysource,
        bytarget,
        frames,
        framesbyfocus,
        facts,
        factsbynode,
        factsbyrelationship,
        evidence,
        provenance,
        externalids,
        entitybindings,
        tagbindings
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

  private def _validate_node_sources(
    nodes: Vector[KnowledgeNode],
    evidence: Map[KnowledgeEvidenceId, KnowledgeEvidence],
    provenance: Map[KnowledgeProvenanceId, KnowledgeProvenance]
  ): Consequence[Unit] =
    nodes.find(x => x.sources.evidenceIds.exists(y => !evidence.contains(y)) || x.sources.provenanceIds.exists(y => !provenance.contains(y))) match {
      case Some(value) =>
        Consequence.argumentInvalid(s"knowledge node references missing source evidence/provenance: ${value.id.print}")
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

  private def _validate_fact_references(
    facts: Vector[KnowledgeFact],
    nodes: Map[KnowledgeNodeId, KnowledgeNode],
    relationships: Map[KnowledgeRelationshipId, KnowledgeRelationship],
    evidence: Map[KnowledgeEvidenceId, KnowledgeEvidence],
    provenance: Map[KnowledgeProvenanceId, KnowledgeProvenance]
  ): Consequence[Unit] =
    facts.find { x =>
      x.subjectNodeId.exists(y => !nodes.contains(y)) ||
        x.relationshipId.exists(y => !relationships.contains(y)) ||
        x.evidenceIds.exists(y => !evidence.contains(y)) ||
        x.provenanceId.exists(y => !provenance.contains(y))
    } match {
      case Some(value) =>
        Consequence.argumentInvalid(s"knowledge fact references missing item: ${value.id.print}")
      case None =>
        Consequence.unit
    }

  private def _validate_frame_references(
    frames: Vector[KnowledgeFrame],
    nodes: Map[KnowledgeNodeId, KnowledgeNode],
    relationships: Map[KnowledgeRelationshipId, KnowledgeRelationship],
    facts: Map[KnowledgeFactId, KnowledgeFact],
    evidence: Map[KnowledgeEvidenceId, KnowledgeEvidence],
    provenance: Map[KnowledgeProvenanceId, KnowledgeProvenance]
  ): Consequence[Unit] =
    frames.find { x =>
      x.focusNodeIds.exists(y => !nodes.contains(y)) ||
        x.nodeIds.exists(y => !nodes.contains(y)) ||
        x.relationshipIds.exists(y => !relationships.contains(y)) ||
        x.factIds.exists(y => !facts.contains(y)) ||
        x.evidenceIds.exists(y => !evidence.contains(y)) ||
        x.provenanceIds.exists(y => !provenance.contains(y)) ||
        x.origin.provenanceId.exists(y => !provenance.contains(y))
    } match {
      case Some(value) =>
        Consequence.argumentInvalid(s"knowledge frame references missing item: ${value.id.print}")
      case None =>
        Consequence.unit
    }

  private def _materialize_nodes(snapshot: KnowledgeWorkingSetSnapshot): Vector[KnowledgeNode] =
    snapshot.nodes.map { node =>
      val relationships = snapshot.relationships.filter(x => x.sourceNodeId == node.id || x.targetNodeId == node.id)
      val facts = snapshot.facts.filter(x =>
        x.subjectNodeId.contains(node.id) ||
          x.relationshipId.exists(y => snapshot.relationships.exists(z => z.id == y && (z.sourceNodeId == node.id || z.targetNodeId == node.id)))
      )
      val frames = snapshot.frames.filter(x => x.focusNodeIds.contains(node.id) || x.nodeIds.contains(node.id))
      val identity = _project_identity(node.id, node.identity, relationships)
      val structure = _project_structure(node.id, node.structure, relationships)
      val bindings = _project_bindings(node.bindings, node.identity.externalIdentifiers, facts)
      val sources = _project_sources(node.sources, relationships, facts)
      val operations = node.operations.copy(frameIds = _distinct_ids(node.operations.frameIds ++ frames.map(_.id)))
      node
        .copy(identity = identity)
        .copy(structure = structure)
        .copy(bindings = bindings)
        .copy(sources = sources)
        .copy(operations = operations)
    }

  private def _project_identity(
    nodeid: KnowledgeNodeId,
    base: KnowledgeNodeIdentity,
    relationships: Vector[KnowledgeRelationship]
  ): KnowledgeNodeIdentity = {
    val sameas = _related_nodes(nodeid, relationships, Set(KnowledgeRelationshipKind.SameAs, KnowledgeRelationshipKind.SameResourceAs))
    val equivalent = _related_nodes(nodeid, relationships, Set(KnowledgeRelationshipKind.EquivalentTo))
    base.copy(
      identityLinks = base.identityLinks.copy(
        sameAs = _distinct_ids(base.identityLinks.sameAs ++ sameas),
        equivalentTo = _distinct_ids(base.identityLinks.equivalentTo ++ equivalent)
      )
    )
  }

  private def _project_structure(
    nodeid: KnowledgeNodeId,
    base: KnowledgeNodeStructure,
    relationships: Vector[KnowledgeRelationship]
  ): KnowledgeNodeStructure = {
    val translations = _correspondences(nodeid, relationships, KnowledgeRelationshipKind.TranslationOf)
    val localized = _correspondences(nodeid, relationships, KnowledgeRelationshipKind.LocalizedVersionOf)
    val sameconcepts = _correspondences(nodeid, relationships, KnowledgeRelationshipKind.SameConceptAs)
    val sameresources = _correspondences(nodeid, relationships, KnowledgeRelationshipKind.SameResourceAs)
    val alignments = _correspondences(nodeid, relationships, KnowledgeRelationshipKind.SourceAlignedWith)
    val aliases = _correspondences(nodeid, relationships, KnowledgeRelationshipKind.AliasOf)
    val classifications = base.classifications.copy(
      primary = base.classifications.primary.orElse(_outgoing_nodes(nodeid, relationships, Set(KnowledgeRelationshipKind.ClassifiedBy)).headOption),
      broader = _distinct_ids(base.classifications.broader ++ _outgoing_nodes(nodeid, relationships, Set(KnowledgeRelationshipKind.Broader))),
      narrower = _distinct_ids(base.classifications.narrower ++ _outgoing_nodes(nodeid, relationships, Set(KnowledgeRelationshipKind.Narrower))),
      additional = _distinct_ids(base.classifications.additional ++ _outgoing_nodes(nodeid, relationships, Set(KnowledgeRelationshipKind.ClassifiedBy)))
    )
    val parent = _incoming_sources(nodeid, relationships, Set(KnowledgeRelationshipKind.ParentOf, KnowledgeRelationshipKind.HasChild)).headOption
      .orElse(base.hierarchy.parent)
    val children = _distinct_ids(
      base.hierarchy.children ++ _outgoing_nodes(nodeid, relationships, Set(KnowledgeRelationshipKind.ParentOf, KnowledgeRelationshipKind.HasChild))
    )
    val partof = _distinct_ids(
      base.partWhole.partOf ++
        _outgoing_nodes(nodeid, relationships, Set(KnowledgeRelationshipKind.PartOf)) ++
        _incoming_sources(nodeid, relationships, Set(KnowledgeRelationshipKind.HasPart))
    )
    val haspart = _distinct_ids(
      base.partWhole.hasPart ++
        _outgoing_nodes(nodeid, relationships, Set(KnowledgeRelationshipKind.HasPart)) ++
        _incoming_sources(nodeid, relationships, Set(KnowledgeRelationshipKind.PartOf))
    )
    val memberof = _distinct_ids(
      base.partWhole.memberOf ++
        _outgoing_nodes(nodeid, relationships, Set(KnowledgeRelationshipKind.MemberOf)) ++
        _incoming_sources(nodeid, relationships, Set(KnowledgeRelationshipKind.HasMember))
    )
    val hasmember = _distinct_ids(
      base.partWhole.hasMember ++
        _outgoing_nodes(nodeid, relationships, Set(KnowledgeRelationshipKind.HasMember)) ++
        _incoming_sources(nodeid, relationships, Set(KnowledgeRelationshipKind.MemberOf))
    )
    base.copy(
      correspondences = KnowledgeNodeCorrespondences(
        translations = _distinct_correspondences(base.correspondences.translations ++ translations),
        localizedVersions = _distinct_correspondences(base.correspondences.localizedVersions ++ localized),
        sameConcepts = _distinct_correspondences(base.correspondences.sameConcepts ++ sameconcepts),
        sameResources = _distinct_correspondences(base.correspondences.sameResources ++ sameresources),
        sourceAlignments = _distinct_correspondences(base.correspondences.sourceAlignments ++ alignments),
        aliases = _distinct_correspondences(base.correspondences.aliases ++ aliases)
      ),
      classifications = classifications,
      hierarchy = base.hierarchy.copy(parent = parent, children = children),
      partWhole = base.partWhole.copy(partOf = partof, hasPart = haspart, memberOf = memberof, hasMember = hasmember)
    )
  }

  private def _project_bindings(
    base: KnowledgeNodeBindings,
    ids: Vector[ExternalKnowledgeIdentifier],
    facts: Vector[KnowledgeFact]
  ): KnowledgeNodeBindings = {
    val entityfromids = ids.flatMap(KnowledgeEntityBinding.from)
    val tagfromids = ids.flatMap(KnowledgeTagBinding.from)
    val entityfromfacts = facts.flatMap { fact =>
      for {
        entityname <- fact.attributes.values.get("entity_name")
        entityid <- fact.attributes.values.get("entity_id")
      } yield KnowledgeEntityBinding(entityname, entityid)
    }
    val tagfromfacts = facts.flatMap { fact =>
      for {
        tagspace <- fact.attributes.values.get("tag_space")
        tagid <- fact.attributes.values.get("tag_id")
      } yield KnowledgeTagBinding(tagspace, tagid)
    }
    base.copy(
      entityBindings = _distinct_values(base.entityBindings ++ entityfromids ++ entityfromfacts),
      tagBindings = _distinct_values(base.tagBindings ++ tagfromids ++ tagfromfacts)
    )
  }

  private def _project_sources(
    base: KnowledgeNodeSources,
    relationships: Vector[KnowledgeRelationship],
    facts: Vector[KnowledgeFact]
  ): KnowledgeNodeSources =
    base.copy(
      evidenceIds = _distinct_ids(base.evidenceIds ++ relationships.flatMap(_.evidenceIds) ++ facts.flatMap(_.evidenceIds)),
      provenanceIds = _distinct_ids(base.provenanceIds ++ relationships.flatMap(_.provenanceId) ++ facts.flatMap(_.provenanceId))
    )

  private def _correspondences(
    nodeid: KnowledgeNodeId,
    relationships: Vector[KnowledgeRelationship],
    kind: KnowledgeRelationshipKind
  ): Vector[KnowledgeNodeCorrespondence] =
    relationships
      .filter(_.kind == kind)
      .flatMap(x => _other_node_id(nodeid, x).map(y => KnowledgeNodeCorrespondence(y, relationshipKind = x.kind)))

  private def _related_nodes(
    nodeid: KnowledgeNodeId,
    relationships: Vector[KnowledgeRelationship],
    kinds: Set[KnowledgeRelationshipKind]
  ): Vector[KnowledgeNodeId] =
    relationships.filter(x => kinds.contains(x.kind)).flatMap(_other_node_id(nodeid, _))

  private def _outgoing_nodes(
    nodeid: KnowledgeNodeId,
    relationships: Vector[KnowledgeRelationship],
    kinds: Set[KnowledgeRelationshipKind]
  ): Vector[KnowledgeNodeId] =
    relationships.filter(x => x.sourceNodeId == nodeid && kinds.contains(x.kind)).map(_.targetNodeId)

  private def _incoming_sources(
    nodeid: KnowledgeNodeId,
    relationships: Vector[KnowledgeRelationship],
    kinds: Set[KnowledgeRelationshipKind]
  ): Vector[KnowledgeNodeId] =
    relationships.filter(x => x.targetNodeId == nodeid && kinds.contains(x.kind)).map(_.sourceNodeId)

  private def _other_node_id(
    nodeid: KnowledgeNodeId,
    relationship: KnowledgeRelationship
  ): Option[KnowledgeNodeId] =
    if (relationship.sourceNodeId == nodeid)
      Some(relationship.targetNodeId)
    else if (relationship.targetNodeId == nodeid)
      Some(relationship.sourceNodeId)
    else
      None

  private def _distinct_ids[A](values: Vector[A]): Vector[A] =
    values.foldLeft(Vector.empty[A]) { (xs, x) =>
      if (xs.contains(x)) xs else xs :+ x
    }

  private def _distinct_values[A](values: Vector[A]): Vector[A] =
    _distinct_ids(values)

  private def _distinct_correspondences(values: Vector[KnowledgeNodeCorrespondence]): Vector[KnowledgeNodeCorrespondence] =
    values.foldLeft(Vector.empty[KnowledgeNodeCorrespondence]) { (xs, x) =>
      if (xs.exists(y => y.nodeId == x.nodeId && y.relationshipKind == x.relationshipKind)) xs else xs :+ x
    }
}
