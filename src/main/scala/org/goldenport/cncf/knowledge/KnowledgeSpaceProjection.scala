package org.goldenport.cncf.knowledge

import org.goldenport.record.Record
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.naming.NamingConventions

/*
 * @since   May. 17, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentKnowledgeProjection(
  componentName: String,
  status: KnowledgeWorkingSetStatus,
  counts: KnowledgeWorkingSetCounts,
  sourceDiagnostics: KnowledgeProjectionSourceDiagnostics,
  nodes: Vector[KnowledgeNode],
  relationships: Vector[KnowledgeRelationship],
  evidence: Vector[KnowledgeEvidence],
  provenance: Vector[KnowledgeProvenance]
) {
  def toRecord: Record =
    Record.dataAuto(
      "component" -> componentName,
      "status" -> KnowledgeRecordCodec.toRecord(status),
      "counts" -> KnowledgeRecordCodec.toRecord(counts),
      "source_diagnostics" -> KnowledgeRecordCodec.toRecord(sourceDiagnostics),
      "nodes" -> nodes.map(KnowledgeRecordCodec.toRecord),
      "relationships" -> relationships.map(KnowledgeRecordCodec.toRecord),
      "evidence" -> evidence.map(KnowledgeRecordCodec.toRecord),
      "provenance" -> provenance.map(KnowledgeRecordCodec.toRecord)
    )
}

final case class KnowledgeNodeProjection(
  componentName: String,
  node: KnowledgeNode,
  relationshipsFrom: Vector[KnowledgeRelationship],
  relationshipsTo: Vector[KnowledgeRelationship],
  evidence: Vector[KnowledgeEvidence],
  provenance: Vector[KnowledgeProvenance]
) {
  def toRecord: Record =
    Record.dataAuto(
      "component" -> componentName,
      "node" -> KnowledgeRecordCodec.toRecord(node),
      "relationships_from" -> relationshipsFrom.map(KnowledgeRecordCodec.toRecord),
      "relationships_to" -> relationshipsTo.map(KnowledgeRecordCodec.toRecord),
      "evidence" -> evidence.map(KnowledgeRecordCodec.toRecord),
      "provenance" -> provenance.map(KnowledgeRecordCodec.toRecord)
    )
}

final case class KnowledgeLookupResult(
  componentName: String,
  node: KnowledgeNode
) {
  def toRecord: Record =
    Record.dataAuto(
      "component" -> componentName,
      "node" -> KnowledgeRecordCodec.toRecord(node)
    )
}

final case class KnowledgeProjectionSourceDiagnostics(
  sourceKind: String,
  storage: String,
  providerStatus: String,
  projectionMode: String,
  message: Option[String] = None
)

object KnowledgeSpaceProjection {
  def component(component: Component): ComponentKnowledgeProjection = {
    val snapshot = component.knowledgeSpace.snapshot
    ComponentKnowledgeProjection(
      component.name,
      component.knowledgeSpace.status,
      component.knowledgeSpace.counts,
      KnowledgeProjectionSourceDiagnostics(
        sourceKind = "component.knowledge-space",
        storage = "memory",
        providerStatus = "not_attached",
        projectionMode = "snapshot",
        message = Some("KS-09 exposes the component-owned in-memory KnowledgeSpace projection; provider-backed RDF/Vector loading starts in KS-10.")
      ),
      snapshot.nodes,
      snapshot.relationships,
      snapshot.evidence,
      snapshot.provenance
    )
  }

  def components(components: Vector[Component]): Vector[ComponentKnowledgeProjection] =
    components.sortBy(_.name).map(component)

  def componentOption(
    components: Vector[Component],
    componentName: String
  ): Option[Component] =
    components.find(x => _matches_component_name(x, componentName))

  def nodeOption(
    component: Component,
    nodeId: KnowledgeNodeId
  ): Option[KnowledgeNodeProjection] =
    component.knowledgeSpace.nodeOption(nodeId).map { node =>
      val from = component.knowledgeSpace.relationshipsFrom(node.id)
      val to = component.knowledgeSpace.relationshipsTo(node.id)
      val evidenceids = (from ++ to).flatMap(_.evidenceIds).distinct
      val evidence = evidenceids.flatMap(component.knowledgeSpace.evidenceOption)
      val provenanceids =
        (node.provenanceId.toVector ++
          (from ++ to).flatMap(_.provenanceId) ++
          evidence.flatMap(_.provenanceId)).distinct
      val provenance = provenanceids.flatMap(component.knowledgeSpace.provenanceOption)
      KnowledgeNodeProjection(component.name, node, from, to, evidence, provenance)
    }

  def lookupExternalIdentifier(
    components: Vector[Component],
    id: ExternalKnowledgeIdentifier
  ): Vector[KnowledgeLookupResult] =
    components.sortBy(_.name).flatMap { component =>
      component.knowledgeSpace.nodeIdsByExternalIdentifier(id).flatMap { nodeid =>
        component.knowledgeSpace.nodeOption(nodeid).map(KnowledgeLookupResult(component.name, _))
      }
    }

  def lookupEntity(
    components: Vector[Component],
    entityName: String,
    entityId: String
  ): Vector[KnowledgeLookupResult] =
    lookupExternalIdentifier(components, ExternalKnowledgeIdentifier.entity(entityName, entityId))

  private def _matches_component_name(
    component: Component,
    name: String
  ): Boolean =
    NamingConventions.equivalentByNormalized(component.name, name) ||
      component.artifactMetadata.toVector.exists { metadata =>
        metadata.component.exists(NamingConventions.equivalentByNormalized(_, name)) ||
          NamingConventions.equivalentByNormalized(metadata.name, name)
      }
}
