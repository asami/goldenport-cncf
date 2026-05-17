package org.goldenport.cncf.http

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.ComponentOrigin
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.job.JobQueryReadModel
import org.goldenport.cncf.knowledge.{KnowledgeNodeId, KnowledgeSpaceProjection}
import org.goldenport.cncf.metrics.RuntimeMetricPoint
import org.goldenport.cncf.CncfVersion
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.observability.{DiagnosticPayloadExternalizationConfig, DiagnosticPayloadReference}
import org.goldenport.cncf.operation.{AssociationBindingOperationDefinition, CmlEntityRelationshipDefinition, CmlOperationAssociationBinding, CmlOperationImageBinding, ImageBindingOperationDefinition}
import org.goldenport.cncf.projection.{AuthorizationPolicyProjection, DescribeProjection, HelpProjection, SchemaProjection}
import org.goldenport.cncf.search.{SearchMode, SearchPlanningProfile, WebSearchQueryPlanner}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Property, Request as ProtocolRequest}
import org.goldenport.protocol.spec.ParameterDefinition
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.value.BaseContent
import org.goldenport.schema.{DataConfidentiality, Multiplicity, ValueDomain, XBoolean, XDateTime, XInt, XString}
import org.simplemodeling.model.datatype.EntityId
import io.circe.{Json, JsonObject}
import io.circe.parser.parse

/*
 * @since   May. 18, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
trait StaticFormAppRendererKnowledgePart {
  this: StaticFormAppRendererSupport with StaticFormAppRendererBlobTagPart with StaticFormAppRendererComponentAdminPart with StaticFormAppRendererCorePart with StaticFormAppRendererFormPart with StaticFormAppRendererSystemAdminPart =>
  import StaticFormAppRendererSupport.*

  def renderSystemAdminKnowledge(subsystem: Subsystem): Page =
    Page(knowledge_admin_page(subsystem))

  def renderSystemAdminKnowledgeComponent(
    subsystem: Subsystem,
    componentName: String
  ): Option[Page] =
    KnowledgeSpaceProjection
      .componentOption(subsystem.components, componentName)
      .map(component => Page(knowledge_component_page(subsystem, component)))

  def renderSystemAdminKnowledgeNode(
    subsystem: Subsystem,
    componentName: String,
    nodeId: String
  ): Option[Page] =
    for {
      component <- KnowledgeSpaceProjection.componentOption(subsystem.components, componentName)
      projection <- KnowledgeSpaceProjection.nodeOption(component, KnowledgeNodeId(nodeId))
    } yield Page(knowledge_node_page(subsystem, projection))

  protected def knowledge_admin_page(subsystem: Subsystem): String = {
    val projections = KnowledgeSpaceProjection.components(subsystem.components)
    val rows =
      if (projections.isEmpty)
        admin_empty_table_cell(13, "No components are loaded.")
      else
        projections.map { projection =>
          val path = escape_path_segment(projection.componentName)
          val status = projection.status
          val counts = projection.counts
          val source = projection.sourceDiagnostics
          s"""<tr>
             |  <td><a href="/web/system/admin/knowledge/${path}">${escape(projection.componentName)}</a></td>
             |  <td><span class="badge text-bg-secondary">${escape(status.state.label)}</span></td>
             |  <td>${escape(source.sourceKind)}</td>
             |  <td>${escape(source.providerStatus)}</td>
             |  <td>${counts.nodeCount}</td>
             |  <td>${counts.relationshipCount}</td>
             |  <td>${counts.frameCount}</td>
             |  <td>${counts.factCount}</td>
             |  <td>${counts.evidenceCount}</td>
             |  <td>${counts.provenanceCount}</td>
             |  <td>${counts.entityBindingCount}</td>
             |  <td>${counts.tagBindingCount}</td>
             |  <td>${escape(status.error.getOrElse(""))}</td>
             |</tr>""".stripMargin
        }.mkString("\n")
    simple_page(
      title = "System Knowledge",
      subtitle = "Component-owned KnowledgeSpace status and compact graph projection",
      body =
        s"""${admin_nav_card(Vector(
             "System admin" -> "/web/system/admin",
             "Performance summary" -> "/web/system/performance",
             "Observability" -> "/web/system/admin/observability"
           ))}
           |${admin_card(
             "KnowledgeSpace Components",
             s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Component</th><th>Status</th><th>Source</th><th>Provider</th><th>Nodes</th><th>Relationships</th><th>Frames</th><th>Facts</th><th>Evidence</th><th>Provenance</th><th>Entity bindings</th><th>Tag bindings</th><th>Error</th></tr></thead>
                |  <tbody>${rows}</tbody>
                |</table></div>""".stripMargin
           )}
           |${admin_card(
             "Projection policy",
             """<p class="mb-2">This page shows CNCF operational semantic projections, not raw RDF triples, Vector DB payloads, or source documents.</p>
               |<p class="mb-0">KnowledgeSpace is component-owned. Cross-component views are rendered as read-only aggregation.</p>""".stripMargin
           )}""".stripMargin
    )
  }

  protected def knowledge_component_page(
    subsystem: Subsystem,
    component: Component
  ): String = {
    val projection = KnowledgeSpaceProjection.component(component)
    val path = escape_path_segment(component.name)
    val previewlimit = renderer_config.previewLimit
    val sortednodes = projection.nodes.sortBy(_.id.print)
    val sortedrelationships = projection.relationships.sortBy(_.id.print)
    val sortedframes = projection.frames.sortBy(_.id.print)
    val sortedfacts = projection.facts.sortBy(_.id.print)
    val nodepreview = sortednodes.take(previewlimit)
    val relationshippreview = sortedrelationships.take(previewlimit)
    val framepreview = sortedframes.take(previewlimit)
    val factpreview = sortedfacts.take(previewlimit)
    val similarityrepresentations = projection.nodes.map(_.similarity.representations.size).sum + projection.relationships.map(_.similarity.representations.size).sum
    val similaritysearchentries = projection.nodes.map(_.similarity.searchEntries.size).sum + projection.relationships.map(_.similarity.searchEntries.size).sum
    val nodecaption =
      if (sortednodes.size > previewlimit)
        s"""<p class="text-secondary small mb-2">Showing first ${previewlimit} of ${sortednodes.size} nodes.</p>"""
      else
        ""
    val relationshipcaption =
      if (sortedrelationships.size > previewlimit)
        s"""<p class="text-secondary small mb-2">Showing first ${previewlimit} of ${sortedrelationships.size} relationships.</p>"""
      else
        ""
    val framecaption =
      if (sortedframes.size > previewlimit)
        s"""<p class="text-secondary small mb-2">Showing first ${previewlimit} of ${sortedframes.size} frames.</p>"""
      else
        ""
    val factcaption =
      if (sortedfacts.size > previewlimit)
        s"""<p class="text-secondary small mb-2">Showing first ${previewlimit} of ${sortedfacts.size} facts.</p>"""
      else
        ""
    val nodes =
      if (sortednodes.isEmpty)
        admin_empty_table_cell(4, "No knowledge nodes are loaded.")
      else
        nodepreview.map { node =>
          s"""<tr>
             |  <td><a href="/web/system/admin/knowledge/${path}/nodes/${escape_path_segment(node.id.print)}"><code>${escape(node.id.print)}</code></a></td>
             |  <td>${escape(node.category.print)}</td>
             |  <td>${escape(node.presentation.defaultLabel.getOrElse(""))}</td>
             |  <td>${node.identity.externalIdentifiers.size}</td>
             |</tr>""".stripMargin
        }.mkString("\n")
    val relationships =
      if (sortedrelationships.isEmpty)
        admin_empty_table_cell(7, "No knowledge relationships are loaded.")
      else
        relationshippreview.map(knowledge_relationship_row(_, Some(path))).mkString("\n")
    val frames = knowledge_frame_rows(framepreview, emptycolspan = 8, "No knowledge frames are loaded.")
    val facts = knowledge_fact_rows(factpreview, emptycolspan = 6, "No knowledge facts are loaded.")
    simple_page(
      title = s"System Knowledge ${component.name}",
      subtitle = "Component KnowledgeSpace compact projection",
      body =
        s"""${admin_nav_card(Vector(
             "System knowledge" -> "/web/system/admin/knowledge",
             "System admin" -> "/web/system/admin"
           ))}
           |${admin_card(
             "Status",
             field_table(Vector(
               "Component" -> component.name,
               "Subsystem" -> subsystem.name,
               "State" -> projection.status.state.label,
               "Ready" -> projection.status.isReady.toString,
               "Nodes" -> projection.counts.nodeCount.toString,
               "Relationships" -> projection.counts.relationshipCount.toString,
               "Frames" -> projection.counts.frameCount.toString,
               "Facts" -> projection.counts.factCount.toString,
               "Evidence" -> projection.counts.evidenceCount.toString,
               "Provenance" -> projection.counts.provenanceCount.toString,
               "External identifiers" -> projection.counts.externalIdentifierCount.toString,
               "Entity bindings" -> projection.counts.entityBindingCount.toString,
               "Tag bindings" -> projection.counts.tagBindingCount.toString,
               "Similarity representations" -> similarityrepresentations.toString,
               "Similarity search entries" -> similaritysearchentries.toString,
               "Projection source" -> projection.sourceDiagnostics.sourceKind,
               "Storage" -> projection.sourceDiagnostics.storage,
               "Provider status" -> projection.sourceDiagnostics.providerStatus,
               "Projection mode" -> projection.sourceDiagnostics.projectionMode,
               "Error" -> projection.status.error.getOrElse("")
             ))
           )}
           |${admin_card(
             "Nodes",
             s"""${nodecaption}<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Node</th><th>Category</th><th>Label</th><th>External identifiers</th></tr></thead>
                |  <tbody>${nodes}</tbody>
                |</table></div>""".stripMargin
           )}
           |${admin_card(
             "Relationships",
             s"""${relationshipcaption}<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Relationship</th><th>Kind</th><th>RDF predicate</th><th>Source</th><th>Target</th><th>Semantic types</th><th>Evidence</th></tr></thead>
                |  <tbody>${relationships}</tbody>
                |</table></div>""".stripMargin
           )}
           |${admin_card(
             "Frames",
             s"""${framecaption}<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Frame</th><th>Kind</th><th>Route</th><th>Provider</th><th>Purpose</th><th>Query</th><th>Focus nodes</th><th>Facts</th></tr></thead>
                |  <tbody>${frames}</tbody>
                |</table></div>""".stripMargin
           )}
           |${admin_card(
             "Facts",
             s"""${factcaption}<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Fact</th><th>Kind</th><th>Subject</th><th>Relationship</th><th>Predicate</th><th>Value</th></tr></thead>
                |  <tbody>${facts}</tbody>
                |</table></div>""".stripMargin
           )}""".stripMargin
    )
  }

  protected def knowledge_node_page(
    subsystem: Subsystem,
    projection: org.goldenport.cncf.knowledge.KnowledgeNodeProjection
  ): String = {
    val componentpath = escape_path_segment(projection.componentName)
    val node = projection.node
    val externalids = knowledge_external_identifier_table(node.identity.externalIdentifiers)
    val from =
      if (projection.relationshipsFrom.isEmpty)
        admin_empty_table_cell(7, "No outgoing relationships.")
      else
        projection.relationshipsFrom.sortBy(_.id.print).map(knowledge_relationship_row(_, Some(componentpath))).mkString("\n")
    val to =
      if (projection.relationshipsTo.isEmpty)
        admin_empty_table_cell(7, "No incoming relationships.")
      else
        projection.relationshipsTo.sortBy(_.id.print).map(knowledge_relationship_row(_, Some(componentpath))).mkString("\n")
    val evidence = knowledge_evidence_table(projection.evidence)
    val provenance = knowledge_provenance_table(projection.provenance)
    val frames = knowledge_frame_table(projection.frames)
    val facts = knowledge_fact_table(projection.facts)
    val identity = knowledge_identity_table(node)
    val presentation = knowledge_presentation_table(node.presentation)
    val semantics = knowledge_semantics_table(node.semantics)
    val structure = knowledge_structure_table(node.structure)
    val bindings = knowledge_bindings_table(node.bindings)
    val similarity = knowledge_similarity_table(node.similarity)
    val operations = knowledge_operations_table(node.operations)
    simple_page(
      title = s"Knowledge Node ${node.id.print}",
      subtitle = s"${projection.componentName} KnowledgeSpace node detail",
      body =
        s"""${admin_nav_card(Vector(
             "Component knowledge" -> s"/web/system/admin/knowledge/${componentpath}",
             "System knowledge" -> "/web/system/admin/knowledge"
           ))}
           |${admin_card("Node identity", identity)}
           |${admin_card("Presentation", presentation)}
           |${admin_card("Semantics", semantics)}
           |${admin_card("Structure", structure)}
           |${admin_card("Entity and Tag bindings", bindings)}
           |${admin_card("Similarity", similarity)}
           |${admin_card("Operations", operations)}
           |${admin_card("External identifiers", externalids)}
           |${admin_card("Frames", frames)}
           |${admin_card("Facts", facts)}
           |${admin_card(
             "Outgoing relationships",
             s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Relationship</th><th>Kind</th><th>RDF predicate</th><th>Source</th><th>Target</th><th>Semantic types</th><th>Evidence</th></tr></thead>
                |  <tbody>${from}</tbody>
                |</table></div>""".stripMargin
           )}
           |${admin_card(
             "Incoming relationships",
             s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
                |  <thead><tr><th>Relationship</th><th>Kind</th><th>RDF predicate</th><th>Source</th><th>Target</th><th>Semantic types</th><th>Evidence</th></tr></thead>
                |  <tbody>${to}</tbody>
                |</table></div>""".stripMargin
           )}
           |${admin_card("Evidence", evidence)}
           |${admin_card("Provenance", provenance)}""".stripMargin
    )
  }

  protected def knowledge_relationship_row(
    relationship: org.goldenport.cncf.knowledge.KnowledgeRelationship,
    componentpath: Option[String] = None
  ): String = {
    val source = knowledge_node_link(componentpath, relationship.sourceNodeId)
    val target = knowledge_node_link(componentpath, relationship.targetNodeId)
    val semantictypes = relationship.semanticTypes.map(x => s"${x.system}:${x.name}").mkString(", ")
    s"""<tr>
       |  <td><code>${escape(relationship.id.print)}</code></td>
       |  <td>${escape(relationship.kind.print)}</td>
       |  <td>${escape(relationship.rdfPredicate.map(_.print).getOrElse(""))}</td>
       |  <td>${source}</td>
       |  <td>${target}</td>
       |  <td>${escape(semantictypes)}</td>
       |  <td>${escape(relationship.evidenceIds.map(_.print).mkString(", "))}</td>
       |</tr>""".stripMargin
  }

  protected def knowledge_node_link(
    componentpath: Option[String],
    id: org.goldenport.cncf.knowledge.KnowledgeNodeId
  ): String =
    componentpath
      .map(path => s"""<a href="/web/system/admin/knowledge/${path}/nodes/${escape_path_segment(id.print)}"><code>${escape(id.print)}</code></a>""")
      .getOrElse(s"<code>${escape(id.print)}</code>")

  protected def knowledge_frame_table(
    frames: Vector[org.goldenport.cncf.knowledge.KnowledgeFrame]
  ): String =
    if (frames.isEmpty)
      admin_empty_state("No frames linked to this node projection.")
    else {
      val rows = knowledge_frame_rows(frames.sortBy(_.id.print), emptycolspan = 8, "No frames linked to this node projection.")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
         |  <thead><tr><th>Frame</th><th>Kind</th><th>Route</th><th>Provider</th><th>Purpose</th><th>Query</th><th>Focus nodes</th><th>Facts</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def knowledge_frame_rows(
    frames: Vector[org.goldenport.cncf.knowledge.KnowledgeFrame],
    emptycolspan: Int,
    emptymessage: String
  ): String =
    if (frames.isEmpty)
      admin_empty_table_cell(emptycolspan, emptymessage)
    else
      frames.sortBy(_.id.print).map { item =>
        s"""<tr>
           |  <td><code>${escape(item.id.print)}</code></td>
           |  <td>${escape(item.kind.print)}</td>
           |  <td>${escape(item.origin.route.print)}</td>
           |  <td>${escape(item.origin.provider.getOrElse(""))}</td>
           |  <td>${escape(item.purpose.map(_.print).getOrElse(""))}</td>
           |  <td>${escape(item.query.map(_.print).getOrElse(""))}</td>
           |  <td>${escape(item.focusNodeIds.map(_.print).mkString(", "))}</td>
           |  <td>${escape(item.factIds.map(_.print).mkString(", "))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")

  protected def knowledge_fact_table(
    facts: Vector[org.goldenport.cncf.knowledge.KnowledgeFact]
  ): String =
    if (facts.isEmpty)
      admin_empty_state("No facts linked to this node projection.")
    else {
      val rows = knowledge_fact_rows(facts.sortBy(_.id.print), emptycolspan = 6, "No facts linked to this node projection.")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
         |  <thead><tr><th>Fact</th><th>Kind</th><th>Subject</th><th>Relationship</th><th>Predicate</th><th>Value</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def knowledge_fact_rows(
    facts: Vector[org.goldenport.cncf.knowledge.KnowledgeFact],
    emptycolspan: Int,
    emptymessage: String
  ): String =
    if (facts.isEmpty)
      admin_empty_table_cell(emptycolspan, emptymessage)
    else
      facts.sortBy(_.id.print).map { item =>
        s"""<tr>
           |  <td><code>${escape(item.id.print)}</code></td>
           |  <td>${escape(item.kind.print)}</td>
           |  <td>${escape(item.subjectNodeId.map(_.print).getOrElse(""))}</td>
           |  <td>${escape(item.relationshipId.map(_.print).getOrElse(""))}</td>
           |  <td>${escape(item.predicate.getOrElse(""))}</td>
           |  <td>${escape(item.value.getOrElse(""))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")

  protected def knowledge_identity_table(
    node: org.goldenport.cncf.knowledge.KnowledgeNode
  ): String =
    field_table(Vector(
      "Id" -> node.id.print,
      "Category" -> node.category.print,
      "RDF node" -> node.identity.rdfNode.map(_.print).getOrElse(""),
      "Canonical" -> node.identity.identityLinks.canonical.map(_.print).getOrElse(""),
      "Same as" -> node.identity.identityLinks.sameAs.map(_.print).mkString(", "),
      "Equivalent to" -> node.identity.identityLinks.equivalentTo.map(_.print).mkString(", ")
    ))

  protected def knowledge_presentation_table(
    value: org.goldenport.cncf.knowledge.KnowledgeNodePresentation
  ): String =
    field_table(Vector(
      "Default label" -> value.labels.default.getOrElse(""),
      "Localized labels" -> value.labels.localized.toVector.sortBy(_._1).map { case (k, v) => s"$k=$v" }.mkString(", "),
      "Alternative labels" -> value.labels.alternatives.mkString(", "),
      "Canonical name" -> value.names.canonical.getOrElse(""),
      "Aliases" -> value.names.aliases.mkString(", "),
      "Description" -> value.descriptions.default.getOrElse(""),
      "Localized descriptions" -> value.descriptions.localized.toVector.sortBy(_._1).map { case (k, v) => s"$k=$v" }.mkString(", ")
    ))

  protected def knowledge_semantics_table(
    value: org.goldenport.cncf.knowledge.KnowledgeNodeSemantics
  ): String =
    field_table(Vector(
      "Semantic types" -> value.semanticTypes.map(x => s"${x.system}:${x.name}:${x.status.print}").mkString(", "),
      "Roles" -> value.roles.toVector.sorted.mkString(", "),
      "Confidence" -> value.confidence.value.map(_.toString).orElse(value.confidence.status).getOrElse(""),
      "Confidentiality" -> Vector(value.confidentiality.status, value.confidentiality.visibility).flatten.mkString(", "),
      "Valid from" -> value.temporal.validFrom.map(_.toString).getOrElse(""),
      "Valid to" -> value.temporal.validTo.map(_.toString).getOrElse(""),
      "Observed at" -> value.temporal.observedAt.map(_.toString).getOrElse(""),
      "Lifecycle state" -> value.lifecycle.state.getOrElse("")
    ))

  protected def knowledge_structure_table(
    value: org.goldenport.cncf.knowledge.KnowledgeNodeStructure
  ): String =
    field_table(Vector(
      "Translations" -> value.correspondences.translations.map(_.nodeId.print).mkString(", "),
      "Localized versions" -> value.correspondences.localizedVersions.map(_.nodeId.print).mkString(", "),
      "Same concepts" -> value.correspondences.sameConcepts.map(_.nodeId.print).mkString(", "),
      "Same resources" -> value.correspondences.sameResources.map(_.nodeId.print).mkString(", "),
      "Source alignments" -> value.correspondences.sourceAlignments.map(_.nodeId.print).mkString(", "),
      "Aliases" -> value.correspondences.aliases.map(_.nodeId.print).mkString(", "),
      "Primary classification" -> value.classifications.primary.map(_.print).getOrElse(""),
      "Broader" -> value.classifications.broader.map(_.print).mkString(", "),
      "Narrower" -> value.classifications.narrower.map(_.print).mkString(", "),
      "Additional classifications" -> value.classifications.additional.map(_.print).mkString(", "),
      "Parent" -> value.hierarchy.parent.map(_.print).getOrElse(""),
      "Children" -> value.hierarchy.children.map(_.print).mkString(", "),
      "Part of" -> value.partWhole.partOf.map(_.print).mkString(", "),
      "Has part" -> value.partWhole.hasPart.map(_.print).mkString(", "),
      "Member of" -> value.partWhole.memberOf.map(_.print).mkString(", "),
      "Has member" -> value.partWhole.hasMember.map(_.print).mkString(", ")
    ))

  protected def knowledge_bindings_table(
    value: org.goldenport.cncf.knowledge.KnowledgeNodeBindings
  ): String =
    field_table(Vector(
      "Entity bindings" -> value.entityBindings.map(x => Vector(x.component, Some(x.entityName), Some(x.entityId), x.entityVersion).flatten.mkString(":")).mkString(", "),
      "Tag bindings" -> value.tagBindings.map(x => s"${x.tagSpace}:${x.tagId}").mkString(", ")
    ))

  protected def knowledge_similarity_table(
    value: org.goldenport.cncf.knowledge.KnowledgeNodeSimilarity
  ): String =
    field_table(Vector(
      "Status" -> value.status.print,
      "Representations" -> value.representations.map(x => Vector(x.method, x.model, x.metric, x.context, x.payloadReference).flatten.mkString(":")).mkString(", "),
      "Search entries" -> value.searchEntries.map(x => Vector(x.provider, x.collection, x.searchId, x.indexedAt.map(_.toString)).flatten.mkString(":")).mkString(", ")
    ))

  protected def knowledge_operations_table(
    value: org.goldenport.cncf.knowledge.KnowledgeNodeOperations
  ): String =
    field_table(Vector(
      "Materialized at" -> value.materializedAt.map(_.toString).getOrElse(""),
      "Frame ids" -> value.frameIds.map(_.print).mkString(", "),
      "Validation status" -> value.validationStatus.print
    ))

  protected def knowledge_external_identifier_table(
    ids: Vector[org.goldenport.cncf.knowledge.ExternalKnowledgeIdentifier]
  ): String =
    if (ids.isEmpty)
      admin_empty_state("No external identifiers.")
    else {
      val rows = ids.sortBy(_.key).map { id =>
        s"""<tr><td>${escape(id.system)}</td><td>${escape(id.kind.getOrElse(""))}</td><td><code>${escape(id.value)}</code></td><td><code>${escape(id.key)}</code></td></tr>"""
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
         |  <thead><tr><th>System</th><th>Kind</th><th>Value</th><th>Key</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def knowledge_evidence_table(
    evidence: Vector[org.goldenport.cncf.knowledge.KnowledgeEvidence]
  ): String =
    if (evidence.isEmpty)
      admin_empty_state("No evidence linked to this node projection.")
    else {
      val rows = evidence.sortBy(_.id.print).map { item =>
        s"""<tr>
           |  <td><code>${escape(item.id.print)}</code></td>
           |  <td>${escape(item.kind)}</td>
           |  <td>${escape(item.source.kind)}</td>
           |  <td><code>${escape(item.source.value)}</code></td>
           |  <td>${escape(item.summary.getOrElse(""))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
         |  <thead><tr><th>Evidence</th><th>Kind</th><th>Source kind</th><th>Source</th><th>Summary</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

  protected def knowledge_provenance_table(
    provenance: Vector[org.goldenport.cncf.knowledge.KnowledgeProvenance]
  ): String =
    if (provenance.isEmpty)
      admin_empty_state("No provenance linked to this node projection.")
    else {
      val rows = provenance.sortBy(_.id.print).map { item =>
        s"""<tr>
           |  <td><code>${escape(item.id.print)}</code></td>
           |  <td>${escape(item.origin)}</td>
           |  <td>${escape(item.owner.getOrElse(""))}</td>
           |  <td>${escape(item.generatedBy.getOrElse(""))}</td>
           |  <td>${escape(item.confidence.map(_.toString).getOrElse(""))}</td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<div class="table-responsive"><table class="table table-sm table-hover align-middle mb-0">
         |  <thead><tr><th>Provenance</th><th>Origin</th><th>Owner</th><th>Generated by</th><th>Confidence</th></tr></thead>
         |  <tbody>${rows}</tbody>
         |</table></div>""".stripMargin
    }

}
