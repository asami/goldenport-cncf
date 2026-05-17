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
      "external_identifiers" -> counts.externalIdentifierCount,
      "frames" -> counts.frameCount,
      "facts" -> counts.factCount,
      "entity_bindings" -> counts.entityBindingCount,
      "tag_bindings" -> counts.tagBindingCount
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
    Record.dataAuto(
      "id" -> node.id.print,
      "category" -> node.category.print,
      "identity" -> toRecord(node.identity),
      "presentation" -> toRecord(node.presentation),
      "semantics" -> toRecord(node.semantics),
      "structure" -> toRecord(node.structure),
      "sources" -> toRecord(node.sources),
      "bindings" -> toRecord(node.bindings),
      "similarity" -> toRecord(node.similarity),
      "operations" -> toRecord(node.operations),
      "attributes" -> toRecord(node.attributes)
    )

  def toRecord(identity: KnowledgeNodeIdentity): Record =
    Record.dataOption(
      "rdf_node" -> identity.rdfNode.map(_.print),
      "identity_links" -> Some(toRecord(identity.identityLinks)),
      "external_identifiers" -> Some(identity.externalIdentifiers.map(toRecord))
    )

  def toRecord(links: KnowledgeIdentityLinks): Record =
    Record.dataOption(
      "canonical" -> links.canonical.map(_.print),
      "same_as" -> Some(links.sameAs.map(_.print)),
      "equivalent_to" -> Some(links.equivalentTo.map(_.print))
    )

  def toRecord(value: KnowledgeNodePresentation): Record =
    Record.dataAuto(
      "labels" -> toRecord(value.labels),
      "names" -> toRecord(value.names),
      "descriptions" -> toRecord(value.descriptions)
    )

  def toRecord(value: KnowledgeLabels): Record =
    Record.dataOption(
      "default" -> value.default,
      "localized" -> Some(Record.data(value.localized.toVector.sortBy(_._1)*)),
      "alternatives" -> Some(value.alternatives)
    )

  def toRecord(value: KnowledgeNames): Record =
    Record.dataOption(
      "canonical" -> value.canonical,
      "aliases" -> Some(value.aliases)
    )

  def toRecord(value: KnowledgeDescriptions): Record =
    Record.dataOption(
      "default" -> value.default,
      "localized" -> Some(Record.data(value.localized.toVector.sortBy(_._1)*))
    )

  def toRecord(value: KnowledgeNodeSemantics): Record =
    Record.dataAuto(
      "semantic_types" -> value.semanticTypes.map(toRecord),
      "roles" -> value.roles.toVector.sorted,
      "confidence" -> Record.dataOption("value" -> value.confidence.value, "status" -> value.confidence.status),
      "confidentiality" -> Record.dataOption("status" -> value.confidentiality.status, "visibility" -> value.confidentiality.visibility),
      "temporal" -> Record.dataOption(
        "valid_from" -> value.temporal.validFrom.map(_.toString),
        "valid_to" -> value.temporal.validTo.map(_.toString),
        "observed_at" -> value.temporal.observedAt.map(_.toString)
      ),
      "lifecycle" -> Record.dataOption(
        "state" -> value.lifecycle.state,
        "created_at" -> value.lifecycle.createdAt.map(_.toString),
        "updated_at" -> value.lifecycle.updatedAt.map(_.toString)
      )
    )

  def toRecord(value: KnowledgeSemanticType): Record =
    Record.dataOption(
      "system" -> Some(value.system),
      "name" -> Some(value.name),
      "label" -> value.label,
      "source" -> value.source.map(toRecord),
      "profile" -> value.profile,
      "confidence" -> value.confidence,
      "provenance_id" -> value.provenanceId.map(_.print),
      "status" -> Some(value.status.print)
    )

  def toRecord(value: KnowledgeNodeStructure): Record =
    Record.dataAuto(
      "correspondences" -> toRecord(value.correspondences),
      "classifications" -> toRecord(value.classifications),
      "hierarchy" -> toRecord(value.hierarchy),
      "part_whole" -> toRecord(value.partWhole)
    )

  def toRecord(value: KnowledgeNodeCorrespondences): Record =
    Record.dataAuto(
      "translations" -> value.translations.map(toRecord),
      "localized_versions" -> value.localizedVersions.map(toRecord),
      "same_concepts" -> value.sameConcepts.map(toRecord),
      "same_resources" -> value.sameResources.map(toRecord),
      "source_alignments" -> value.sourceAlignments.map(toRecord),
      "aliases" -> value.aliases.map(toRecord)
    )

  def toRecord(value: KnowledgeNodeCorrespondence): Record =
    Record.dataOption(
      "node_id" -> Some(value.nodeId.print),
      "language" -> value.language,
      "locale" -> value.locale,
      "relationship_kind" -> Some(value.relationshipKind.print)
    )

  def toRecord(value: KnowledgeClassifications): Record =
    Record.dataOption(
      "primary" -> value.primary.map(_.print),
      "broader" -> Some(value.broader.map(_.print)),
      "narrower" -> Some(value.narrower.map(_.print)),
      "additional" -> Some(value.additional.map(_.print))
    )

  def toRecord(value: KnowledgeHierarchy): Record =
    Record.dataOption(
      "parent" -> value.parent.map(_.print),
      "children" -> Some(value.children.map(_.print)),
      "ancestors" -> Some(value.ancestors.map(_.print)),
      "descendants" -> Some(value.descendants.map(_.print))
    )

  def toRecord(value: KnowledgePartWhole): Record =
    Record.dataOption(
      "part_of" -> Some(value.partOf.map(_.print)),
      "has_part" -> Some(value.hasPart.map(_.print)),
      "member_of" -> Some(value.memberOf.map(_.print)),
      "has_member" -> Some(value.hasMember.map(_.print)),
      "container" -> value.container.map(_.print)
    )

  def toRecord(value: KnowledgeNodeSources): Record =
    Record.dataAuto(
      "source_refs" -> toRecord(value.sourceRefs),
      "evidence_ids" -> value.evidenceIds.map(_.print),
      "provenance_ids" -> value.provenanceIds.map(_.print)
    )

  def toRecord(value: KnowledgeSourceRefs): Record =
    Record.dataOption(
      "primary_source" -> value.primarySource.map(toRecord),
      "source_documents" -> Some(value.sourceDocuments.map(toRecord)),
      "source_chunks" -> Some(value.sourceChunks.map(toRecord)),
      "extracted_from" -> Some(value.extractedFrom.map(_.print)),
      "observed_in" -> Some(value.observedIn.map(_.print))
    )

  def toRecord(value: KnowledgeNodeBindings): Record =
    Record.dataAuto(
      "entity_bindings" -> value.entityBindings.map(toRecord),
      "tag_bindings" -> value.tagBindings.map(toRecord)
    )

  def toRecord(value: KnowledgeEntityBinding): Record =
    Record.dataOption(
      "entity_name" -> Some(value.entityName),
      "entity_id" -> Some(value.entityId),
      "entity_version" -> value.entityVersion,
      "component" -> value.component
    )

  def toRecord(value: KnowledgeTagBinding): Record =
    Record.dataAuto(
      "tag_space" -> value.tagSpace,
      "tag_id" -> value.tagId
    )

  def toRecord(value: KnowledgeNodeSimilarity): Record =
    Record.dataAuto(
      "representations" -> value.representations.map(toRecord),
      "search_entries" -> value.searchEntries.map(toRecord),
      "status" -> value.status.print
    )

  def toRecord(value: KnowledgeSimilarityRepresentation): Record =
    Record.dataOption(
      "method" -> value.method,
      "model" -> value.model,
      "metric" -> value.metric,
      "context" -> value.context,
      "source" -> value.source.map(toRecord),
      "payload_reference" -> value.payloadReference
    )

  def toRecord(value: KnowledgeSimilaritySearchEntry): Record =
    Record.dataOption(
      "provider" -> value.provider,
      "collection" -> value.collection,
      "search_id" -> value.searchId,
      "indexed_at" -> value.indexedAt.map(_.toString)
    )

  def toRecord(value: KnowledgeNodeOperations): Record =
    Record.dataAuto(
      "materialized_at" -> value.materializedAt.map(_.toString),
      "frame_ids" -> value.frameIds.map(_.print),
      "validation_status" -> value.validationStatus.print
    )

  def toRecord(relationship: KnowledgeRelationship): Record =
    Record.dataOption(
      "id" -> Some(relationship.id.print),
      "kind" -> Some(relationship.kind.print),
      "source_node_id" -> Some(relationship.sourceNodeId.print),
      "target_node_id" -> Some(relationship.targetNodeId.print),
      "rdf_predicate" -> relationship.rdfPredicate.map(_.print),
      "semantic_types" -> Some(relationship.semanticTypes.map(toRecord)),
      "evidence_ids" -> Some(relationship.evidenceIds.map(_.print)),
      "provenance_id" -> relationship.provenanceId.map(_.print),
      "qualifiers" -> Some(toRecord(KnowledgeAttributes(relationship.qualifiers.values))),
      "similarity" -> Some(toRecord(relationship.similarity)),
      "attributes" -> Some(toRecord(relationship.attributes))
    )

  def toRecord(value: KnowledgeRelationshipSemanticType): Record =
    Record.dataOption(
      "system" -> Some(value.system),
      "name" -> Some(value.name),
      "property_kind" -> value.propertyKind,
      "direction_policy" -> value.directionPolicy,
      "profile" -> value.profile,
      "confidence" -> value.confidence,
      "provenance_id" -> value.provenanceId.map(_.print),
      "status" -> Some(value.status.print)
    )

  def toRecord(value: KnowledgeRelationshipSimilarity): Record =
    Record.dataAuto(
      "representations" -> value.representations.map(toRecord),
      "search_entries" -> value.searchEntries.map(toRecord),
      "status" -> value.status.print
    )

  def toRecord(fact: KnowledgeFact): Record =
    Record.dataOption(
      "id" -> Some(fact.id.print),
      "kind" -> Some(fact.kind.print),
      "subject_node_id" -> fact.subjectNodeId.map(_.print),
      "relationship_id" -> fact.relationshipId.map(_.print),
      "predicate" -> fact.predicate,
      "value" -> fact.value,
      "evidence_ids" -> Some(fact.evidenceIds.map(_.print)),
      "provenance_id" -> fact.provenanceId.map(_.print),
      "attributes" -> Some(toRecord(fact.attributes))
    )

  def toRecord(frame: KnowledgeFrame): Record =
    Record.dataOption(
      "id" -> Some(frame.id.print),
      "kind" -> Some(frame.kind.print),
      "focus_node_ids" -> Some(frame.focusNodeIds.map(_.print)),
      "node_ids" -> Some(frame.nodeIds.map(_.print)),
      "relationship_ids" -> Some(frame.relationshipIds.map(_.print)),
      "fact_ids" -> Some(frame.factIds.map(_.print)),
      "evidence_ids" -> Some(frame.evidenceIds.map(_.print)),
      "provenance_ids" -> Some(frame.provenanceIds.map(_.print)),
      "origin" -> Some(toRecord(frame.origin)),
      "source_refs" -> Some(frame.sourceRefs.map(toRecord)),
      "purpose" -> frame.purpose.map(_.print),
      "query" -> frame.query.map(_.print),
      "materialized_at" -> frame.materializedAt.map(_.toString),
      "confidence" -> frame.confidence,
      "attributes" -> Some(toRecord(frame.attributes))
    )

  def toRecord(origin: KnowledgeFrameOrigin): Record =
    Record.dataOption(
      "route" -> Some(origin.route.print),
      "provider" -> origin.provider,
      "operation" -> origin.operation,
      "component" -> origin.component,
      "source_graph" -> origin.sourceGraph,
      "source_dataset" -> origin.sourceDataset,
      "request_id" -> origin.requestId,
      "job_id" -> origin.jobId,
      "task_id" -> origin.taskId,
      "created_by" -> origin.createdBy,
      "provenance_id" -> origin.provenanceId.map(_.print)
    )

  def toRecord(evidence: KnowledgeEvidence): Record =
    Record.dataOption(
      "id" -> Some(evidence.id.print),
      "kind" -> Some(evidence.kind),
      "source" -> Some(toRecord(evidence.source)),
      "summary" -> evidence.summary,
      "provenance_id" -> evidence.provenanceId.map(_.print),
      "attributes" -> Some(toRecord(evidence.attributes))
    )

  def toRecord(provenance: KnowledgeProvenance): Record =
    Record.dataOption(
      "id" -> Some(provenance.id.print),
      "origin" -> Some(provenance.origin),
      "owner" -> provenance.owner,
      "generated_by" -> provenance.generatedBy,
      "confidence" -> provenance.confidence,
      "attributes" -> Some(toRecord(provenance.attributes))
    )

  def toRecord(attributes: KnowledgeAttributes): Record =
    Record.data(attributes.values.toVector.sortBy(_._1)*)
}
