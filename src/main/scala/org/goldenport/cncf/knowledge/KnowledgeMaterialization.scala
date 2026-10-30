package org.goldenport.cncf.knowledge

import java.time.Instant
import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.model.value.SecurityAttributes

/*
 * An immutable canonical graph and its external semantic owners. This model
 * retains decisions; it neither makes nor authenticates semantic decisions.
 *
 * @since   Oct. 29, 2026
 * @version Oct. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final case class KnowledgeOwnerReference(owner: String, identity: String, revision: String)

final case class KnowledgeDecisionReference(
  reference: KnowledgeOwnerReference,
  actor: String,
  occurredAt: Instant,
  reason: String
)

final case class KnowledgeMaterialization(
  id: EntityId,
  source: KnowledgeOwnerReference,
  semanticTarget: KnowledgeOwnerReference,
  context: KnowledgeOwnerReference,
  decision: KnowledgeDecisionReference,
  snapshot: KnowledgeWorkingSetSnapshot
)

/** Native storage envelope; security is decoded from framework-managed columns. */
final case class KnowledgeMaterializationEntity(
  materialization: KnowledgeMaterialization,
  securityAttributes: Option[SecurityAttributes]
)

object KnowledgeMaterialization {
  def validate(value: KnowledgeMaterialization): Consequence[Unit] =
    if (value == null || value.id == null || value.decision == null || value.snapshot == null)
      Consequence.argumentInvalid("Knowledge materialization requires identity, decision and graph")
    else for {
      _ <- validateReference(value.source)
      _ <- validateReference(value.semanticTarget)
      _ <- validateReference(value.context)
      _ <- validateReference(value.decision.reference)
      _ <- if (_text(value.decision.actor) && _text(value.decision.reason) && value.decision.occurredAt != null)
        Consequence.unit else Consequence.argumentInvalid("Knowledge decision requires actor, instant and reason")
      _ <- if (value.snapshot.nodes.nonEmpty && value.snapshot.frames.nonEmpty)
        Consequence.unit else Consequence.argumentInvalid("Knowledge materialization requires nodes and a frame")
      _ <- KnowledgeWorkingSet.validateSnapshot(value.snapshot)
      _ <- _validate_graph(value.snapshot)
    } yield ()

  def validateReference(reference: KnowledgeOwnerReference): Consequence[Unit] =
    if (reference != null && _text(reference.owner) && _text(reference.identity) && _text(reference.revision))
      Consequence.unit
    else Consequence.argumentInvalid("Knowledge owner reference requires exact owner, identity and revision")

  private def _text(text: String): Boolean =
    text != null && text.trim.nonEmpty && !text.exists(_.isControl)

  private def _validate_graph(snapshot: KnowledgeWorkingSetSnapshot): Consequence[Unit] = {
    val nodeids = snapshot.nodes.map(_.id).toSet
    val provenanceids = snapshot.provenance.map(_.id).toSet
    val evidenceids = snapshot.evidence.map(_.id).toSet
    val frameids = snapshot.frames.map(_.id).toSet
    val identifiers = snapshot.nodes.map(_.id.print) ++ snapshot.relationships.map(_.id.print) ++
      snapshot.frames.map(_.id.print) ++ snapshot.facts.map(_.id.print) ++
      snapshot.evidence.map(_.id.print) ++ snapshot.provenance.map(_.id.print)
    val invalid = snapshot.nodes.exists { node =>
      val links = node.identity.identityLinks
      val structure = node.structure
      val correspondences = structure.correspondences
      val related = links.canonical.toVector ++ links.sameAs ++ links.equivalentTo ++
        structure.classifications.primary.toVector ++ structure.classifications.broader ++
        structure.classifications.narrower ++ structure.classifications.additional ++
        structure.hierarchy.parent.toVector ++ structure.hierarchy.children ++
        structure.hierarchy.ancestors ++ structure.hierarchy.descendants ++
        structure.partWhole.partOf ++ structure.partWhole.hasPart ++ structure.partWhole.memberOf ++
        structure.partWhole.hasMember ++ structure.partWhole.container.toVector ++
        (correspondences.translations ++ correspondences.localizedVersions ++ correspondences.sameConcepts ++
          correspondences.sameResources ++ correspondences.sourceAlignments ++ correspondences.aliases).map(_.nodeId)
      related.exists(x => !nodeids.contains(x)) ||
        node.operations.frameIds.exists(x => !frameids.contains(x)) ||
        node.sources.sourceRefs.extractedFrom.exists(x => !evidenceids.contains(x)) ||
        node.sources.sourceRefs.observedIn.exists(x => !evidenceids.contains(x)) ||
        node.semantics.semanticTypes.exists(x => x.provenanceId.exists(y => !provenanceids.contains(y)))
    } || snapshot.relationships.exists(_.semanticTypes.exists(_.provenanceId.exists(x => !provenanceids.contains(x))))
    val confidences = snapshot.nodes.flatMap(node => node.semantics.confidence.value.toVector ++
      node.semantics.semanticTypes.flatMap(_.confidence)) ++
      snapshot.relationships.flatMap(_.semanticTypes.flatMap(_.confidence)) ++ snapshot.frames.flatMap(_.confidence)
    if (identifiers.forall(_text) && confidences.forall(_.isFinite) && !invalid) Consequence.unit
    else Consequence.argumentInvalid("Knowledge materialization has a blank identity or dangling graph reference")
  }
}
