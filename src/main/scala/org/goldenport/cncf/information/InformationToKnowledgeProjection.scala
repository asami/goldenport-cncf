package org.goldenport.cncf.information

import java.time.Instant
import domain.statemachine.informationLifecycle
import org.goldenport.Consequence
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId, EntityRevision}
import org.goldenport.cncf.information.entity.Information
import org.goldenport.cncf.information.value.{
  InformationBindingStatus,
  InformationConflict,
  InformationConflictState,
  InformationFieldEvent,
  InformationIdentityBinding,
  InformationLifecycleState,
  InformationPublicationState,
  InformationPublicationStatus,
  InformationResolutionCandidate,
  InformationSpaceCounts,
  InformationSpaceSnapshot,
  InformationValidationIssue
}
import org.goldenport.cncf.knowledge.{
  ExternalKnowledgeIdentifier,
  KnowledgeAttributes,
  KnowledgeEvidence,
  KnowledgeEvidenceId,
  KnowledgeFact,
  KnowledgeFactId,
  KnowledgeFactKind,
  KnowledgeFrame,
  KnowledgeFrameId,
  KnowledgeFrameInputRoute,
  KnowledgeFrameKind,
  KnowledgeFrameOrigin,
  KnowledgeNode,
  KnowledgeNodeBindings,
  KnowledgeNodeCategory,
  KnowledgeNodeId,
  KnowledgeNodeIdentity,
  KnowledgeNodePresentation,
  KnowledgeNodeSources,
  KnowledgeRelationship,
  KnowledgeRelationshipId,
  KnowledgeRelationshipKind,
  KnowledgeRelationshipQualifiers,
  KnowledgeProvenance,
  KnowledgeProvenanceId,
  RdfPredicateName,
  RdfNodeName,
  KnowledgeSourceRef,
  KnowledgeTagBinding,
  KnowledgeWorkingSetSnapshot
}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.tag.TaggingWorkflow
import org.goldenport.record.Record

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
object InformationToKnowledgeProjection {
  private final val CULTURAL_RESOURCE_FAMILY = "cultural-resource"
  private final val BOOK_DOMAIN_PROFILE = "book"
  private final val CULTURAL_RESOURCE_KINDS = Set(
    "textual-work",
    "edition",
    "series",
    "volume",
    "publication",
    "visual-work",
    "built-work",
    "physical-object",
    "collection-item",
    "holding"
  )

  def materialize(information: Information)(using ExecutionContext): KnowledgeWorkingSetSnapshot =
    materialize(information, Vector.empty)

  private final case class BookCulturalResourceLayers(
    publicationNodeId: KnowledgeNodeId,
    textualWorkNodeId: Option[KnowledgeNodeId],
    editionNodeId: Option[KnowledgeNodeId],
    volumeNodeId: Option[KnowledgeNodeId],
    linkedInformationById: Map[String, Information] = Map.empty
  ) {
    def authorshipSourceNodeId: KnowledgeNodeId =
      textualWorkNodeId.getOrElse(publicationNodeId)

    def publisherSourceNodeId: KnowledgeNodeId =
      publicationNodeId

    def textualWorkInformation: Option[Information] =
      _linked_information("textualWorkInformationId")

    def editionInformation: Option[Information] =
      _linked_information("textualEditionInformationId")

    def volumeInformation: Option[Information] =
      _linked_information("textualVolumeInformationId")

    private def _linked_information(fieldpath: String): Option[Information] =
      linkedInformationById.get(fieldpath)
  }

  def materialize(
    information: Information,
    tagbindings: Vector[KnowledgeTagBinding],
    naming: InformationRdfNodeNaming = InformationRdfNodeNaming.default
  )(using ExecutionContext): KnowledgeWorkingSetSnapshot =
    materializeWithRelated(information, tagbindings, Vector.empty, naming)

  def materializeWithRelated(
    information: Information,
    relatedInformation: Vector[Information]
  )(using ExecutionContext): KnowledgeWorkingSetSnapshot =
    materializeWithRelated(information, Vector.empty, relatedInformation, InformationRdfNodeNaming.default)

  def materializeWithRelated(
    information: Information,
    tagbindings: Vector[KnowledgeTagBinding],
    relatedInformation: Vector[Information],
    naming: InformationRdfNodeNaming = InformationRdfNodeNaming.default
  )(using ctx: ExecutionContext): KnowledgeWorkingSetSnapshot = {
    val materializedat = ctx.clock.instant()
    val provenance = KnowledgeProvenance(
      KnowledgeProvenanceId(s"prov-${information.id.print}"),
      origin = "information-space",
      generatedBy = Some("information.materialize")
    )
    val evidence = KnowledgeEvidence(
      KnowledgeEvidenceId(s"ev-${information.id.print}"),
      "information",
      KnowledgeSourceRef("information", information.id.print),
      information.workingData.getString("title"),
      Some(provenance.id)
    )
    val booklayers = _book_knowledge_layers(information, KnowledgeNodeId(s"information-${information.id.print}"), relatedInformation)
    val node = KnowledgeNode(
      id = KnowledgeNodeId(s"information-${information.id.print}"),
      category = KnowledgeNodeCategory(if (information.domain == "book") "publication" else information.domain),
      identity = KnowledgeNodeIdentity(
        rdfNode = Some(naming.rdfNodeName(information)),
        externalIdentifiers = Vector(ExternalKnowledgeIdentifier("cncf.information", information.id.print, Some(information.domain)))
      ),
      presentation = KnowledgeNodePresentation.label(information.workingData.getString("title").getOrElse(information.id.print)),
      sources = KnowledgeNodeSources(
        evidenceIds = Vector(evidence.id),
        provenanceIds = Vector(provenance.id)
      ),
      bindings = KnowledgeNodeBindings(tagBindings = tagbindings),
      attributes = KnowledgeAttributes(
        Map("information_domain" -> information.domain) ++
          Option.when(information.domain == "book")("knowledge_layer" -> "publication") ++
          (if (information.domain == "book") _cultural_resource_attributes("publication", BOOK_DOMAIN_PROFILE) else Map.empty) ++
          _information_cultural_resource_attributes(information.domain)
      )
    )
    val fact = KnowledgeFact(
      id = KnowledgeFactId(s"fact-${information.id.print}-title"),
      kind = KnowledgeFactKind.EntityDerived,
      subjectNodeId = Some(node.id),
      predicate = Some("information.title"),
      value = information.workingData.getString("title"),
      evidenceIds = Vector(evidence.id),
      provenanceId = Some(provenance.id)
    )
    val support = _book_support_nodes_and_relationships(information, booklayers, evidence.id, provenance.id, naming)
    val supportnodes = _distinct_nodes(support.map(_._1))
    val supportrelationships = _distinct_relationships(support.map(_._2))
    val frame = KnowledgeFrame(
      id = KnowledgeFrameId(s"frame-${information.id.print}"),
      kind = KnowledgeFrameKind.Curated,
      focusNodeIds = Vector(node.id),
      nodeIds = Vector(node.id) ++ supportnodes.map(_.id),
      relationshipIds = supportrelationships.map(_.id),
      factIds = Vector(fact.id),
      evidenceIds = Vector(evidence.id),
      provenanceIds = Vector(provenance.id),
      origin = KnowledgeFrameOrigin(
        KnowledgeFrameInputRoute.BatchImport,
        provider = Some("cncf-information"),
        operation = Some("information.materialize"),
        provenanceId = Some(provenance.id)
      ),
      sourceRefs = Vector(KnowledgeSourceRef("information", information.id.print)),
      purpose = None,
      query = None,
      materializedAt = Some(materializedat)
    )
    KnowledgeWorkingSetSnapshot(
      nodes = Vector(node) ++ supportnodes,
      relationships = supportrelationships,
      evidence = Vector(evidence),
      provenance = Vector(provenance),
      frames = Vector(frame),
      facts = Vector(fact)
    )
  }

  private def _book_support_nodes_and_relationships(
    information: Information,
    layers: BookCulturalResourceLayers,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId,
    naming: InformationRdfNodeNaming
  ): Vector[(KnowledgeNode, KnowledgeRelationship)] = {
    if (information.domain != "book")
      Vector.empty
    else {
      val generated = _book_layer_nodes_and_relationships(information, layers, evidenceid, provenanceid, naming) ++
        _book_candidate_support_nodes_and_relationships(information, layers, evidenceid, provenanceid) ++
        _book_information_association_nodes_and_relationships(information, layers, evidenceid, provenanceid) ++
        _book_classification_nodes_and_relationships(information, layers.publicationNodeId, evidenceid, provenanceid)
      val reviews = _book_information_link_reviews(information)
      generated.map { case (node, relationship) =>
        node -> _apply_book_information_link_review(relationship, reviews)
      }
    }
  }

  private final case class BookInformationLinkReview(
    linkKey: String,
    kind: Option[String],
    rdfPredicate: Option[String],
    state: Option[String],
    qualifiers: Map[String, String],
    source: Option[String],
    evidenceSummary: Option[String]
  )

  private def _book_information_link_reviews(
    information: Information
  ): Map[String, BookInformationLinkReview] =
    information.fieldEvents.
      filter(_.fieldPath == "informationLinks").
      filter(_.transformation.contains("information-link-review")).
      flatMap(_book_information_link_review).
      groupBy(_.linkKey).
      view.mapValues(_.last).toMap

  private def _book_information_link_review(
    event: InformationFieldEvent
  ): Option[BookInformationLinkReview] = {
    val values = _key_value_pairs(event.evidence.getOrElse(""))
    for {
      key <- values.get("linkKey")
      kind <- values.get("kind").flatMap(_normalize_book_information_link_kind)
    } yield {
      val qualifiers = _book_information_link_qualifier_keys.flatMap { name =>
        values.get(name).map(name -> _)
      }.toMap
      BookInformationLinkReview(
        linkKey = key,
        kind = Some(kind),
        rdfPredicate = values.get("rdfPredicate").map(_.trim).filter(_.nonEmpty),
        state = Some(event.state.value),
        qualifiers = qualifiers,
        source = values.get("source").map(_.trim).filter(_.nonEmpty),
        evidenceSummary = values.get("evidenceSummary").map(_.trim).filter(_.nonEmpty)
      )
    }
  }

  private val _book_information_link_allowed_kinds: Set[String] =
    Set(
      "authored-by",
      "edited-by",
      "translated-by",
      "contributed-by",
      "published-by",
      "publication-of",
      "volume-of",
      "edition-of",
      "part-of-series",
      "has-part",
      "cites",
      "has-subject"
    )

  private def _normalize_book_information_link_kind(value: String): Option[String] = {
    val normalized = value.trim.toLowerCase
    _book_information_link_allowed_kinds.find(_ == normalized)
  }

  private val _book_information_link_qualifier_keys: Vector[String] =
    Vector(
      "order",
      "role",
      "editionNumber",
      "volumeNumber",
      "language",
      "pageRange",
      "citationContext",
      "confidence",
      "source",
      "evidenceSummary"
    )

  private def _apply_book_information_link_review(
    relationship: KnowledgeRelationship,
    reviews: Map[String, BookInformationLinkReview]
  ): KnowledgeRelationship = {
    val key = _book_information_link_key(relationship)
    reviews.get(key).map { review =>
      relationship.copy(
        kind = review.kind.map(KnowledgeRelationshipKind.apply).getOrElse(relationship.kind),
        rdfPredicate = review.rdfPredicate.map(RdfPredicateName.apply).
          orElse(review.kind.map(RdfPredicateName.apply)).
          orElse(relationship.rdfPredicate),
        qualifiers = KnowledgeRelationshipQualifiers(relationship.qualifiers.values ++ review.qualifiers),
        attributes = KnowledgeAttributes(relationship.attributes.values ++ Map(
          "information_link_key" -> key,
          "information_link_review_state" -> review.state.getOrElse(""),
          "information_link_review_source" -> review.source.getOrElse("manual"),
          "information_link_evidence_summary" -> review.evidenceSummary.getOrElse("")
        ))
      )
    }.getOrElse(relationship.copy(
      attributes = KnowledgeAttributes(relationship.attributes.values ++ Map("information_link_key" -> key))
    ))
  }

  private def _book_information_link_key(
    relationship: KnowledgeRelationship
  ): String =
    relationship.attributes.values.get("candidate_key").
      map(x => s"association:$x").
      orElse {
        for {
          field <- relationship.attributes.values.get("source_field")
          target <- relationship.attributes.values.get("target_information_id")
        } yield s"information:$field:$target"
      }.
      orElse {
        for {
          source <- relationship.attributes.values.get("source_layer")
          target <- relationship.attributes.values.get("target_layer")
        } yield s"layer:$source:$target"
      }.
      orElse(relationship.attributes.values.get("classification_entry_key").map(x => s"classification:$x")).
      getOrElse(relationship.id.print)

  private final case class BookClassificationEntry(
    entryKey: String,
    kind: String,
    system: String,
    code: String,
    label: String,
    rdfUri: String,
    source: String,
    evidence: String,
    state: String,
    primary: Boolean
  )

  private def _book_knowledge_layers(
    information: Information,
    publicationnodeid: KnowledgeNodeId,
    relatedInformation: Vector[Information] = Vector.empty
  ): BookCulturalResourceLayers = {
    val textualworktitle = _book_textual_work_title(information)
    val textualworkid = information.workingData.getString("textualWorkInformationId").map(_.trim).filter(_.nonEmpty)
    val textualeditionid = information.workingData.getString("textualEditionInformationId").map(_.trim).filter(_.nonEmpty)
    val textualvolumeid = information.workingData.getString("textualVolumeInformationId").map(_.trim).filter(_.nonEmpty)
    val editiontitle = _book_edition_title(information, textualworktitle)
    val volume = _book_volume(information)
    val relatedbyid = relatedInformation.map(x => x.id.print -> x).toMap
    val linked = Vector(
      textualworkid.flatMap(relatedbyid.get).filter(_.domain == "textual-work").map("textualWorkInformationId" -> _),
      textualeditionid.flatMap(relatedbyid.get).filter(_.domain == "textual-edition").map("textualEditionInformationId" -> _),
      textualvolumeid.flatMap(relatedbyid.get).filter(_.domain == "textual-volume").map("textualVolumeInformationId" -> _)
    ).flatten.toMap
    BookCulturalResourceLayers(
      publicationnodeid,
      linked.get("textualWorkInformationId").map(x => KnowledgeNodeId(s"information-${x.id.print}")).
        orElse(textualworktitle.map(title => KnowledgeNodeId(s"textual-work-${_safe_key(title, 0)}"))),
      linked.get("textualEditionInformationId").map(x => KnowledgeNodeId(s"information-${x.id.print}")).
        orElse(editiontitle.map(title => KnowledgeNodeId(s"edition-${_safe_key(title, 0)}"))),
      linked.get("textualVolumeInformationId").map(x => KnowledgeNodeId(s"information-${x.id.print}")).
        orElse(volume.map(value => KnowledgeNodeId(s"volume-${_safe_key(Vector(textualworktitle, Some(value)).flatten.mkString("-"), 0)}"))),
      linked
    )
  }

  private def _book_textual_work_title(information: Information): Option[String] =
    information.workingData.getString("workTitle").map(_.trim).filter(_.nonEmpty).
      orElse(information.workingData.getString("title").map(_.trim).filter(_.nonEmpty))

  private def _book_edition_title(
    information: Information,
    textualworktitle: Option[String]
  ): Option[String] =
    information.workingData.getString("editionTitle").map(_.trim).filter(_.nonEmpty).
      orElse(information.workingData.getString("series").map(_.trim).filter(_.nonEmpty)).
      orElse(information.workingData.getString("edition").map(_.trim).filter(_.nonEmpty)).
      orElse(information.workingData.getString("title").map(_.trim).filter(_.nonEmpty).filterNot(title => textualworktitle.contains(title)))

  private def _book_volume(information: Information): Option[String] =
    information.workingData.getString("volume").map(_.trim).filter(_.nonEmpty).
      orElse(information.workingData.getString("volumeNumber").map(_.trim).filter(_.nonEmpty))

  private def _book_layer_nodes_and_relationships(
    information: Information,
    layers: BookCulturalResourceLayers,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId,
    naming: InformationRdfNodeNaming
  ): Vector[(KnowledgeNode, KnowledgeRelationship)] = {
    val textualworktitle = _book_textual_work_title(information)
    val editiontitle = _book_edition_title(information, textualworktitle)
    val volume = _book_volume(information)
    val volumetitle = information.workingData.getString("volumeTitle").map(_.trim).filter(_.nonEmpty).
      orElse(volume.map(v => Vector(textualworktitle.getOrElse("Volume"), v).mkString(" ")))
    val textualwork = for {
      nodeid <- layers.textualWorkNodeId
      label <- layers.textualWorkInformation.flatMap(_information_label).orElse(textualworktitle)
    } yield _book_layer_node(information, nodeid, "textual-work", label, evidenceid, provenanceid, Map("textual_work_title" -> label), layers.textualWorkInformation, naming)
    val edition = for {
      nodeid <- layers.editionNodeId
      label <- layers.editionInformation.flatMap(_information_label).orElse(editiontitle)
    } yield _book_layer_node(information, nodeid, "edition", label, evidenceid, provenanceid, Map("edition_title" -> label), layers.editionInformation, naming)
    val volumenode = for {
      nodeid <- layers.volumeNodeId
      label <- layers.volumeInformation.flatMap(_information_label).orElse(volumetitle)
    } yield _book_layer_node(information, nodeid, "volume", label, evidenceid, provenanceid, Map("volume" -> volume.getOrElse(""), "volume_title" -> label), layers.volumeInformation, naming)
    val relations =
      (for {
        volumeid <- layers.volumeNodeId
      } yield _book_layer_relationship(information, "publication-of", layers.publicationNodeId, volumeid, evidenceid, provenanceid, Map("source_layer" -> "publication", "target_layer" -> "volume"))).toVector ++
      (for {
        volumeid <- layers.volumeNodeId
        editionid <- layers.editionNodeId
      } yield _book_layer_relationship(information, "volume-of", volumeid, editionid, evidenceid, provenanceid, Map("source_layer" -> "volume", "target_layer" -> "edition"))).toVector ++
      (for {
        editionid <- layers.editionNodeId
        workid <- layers.textualWorkNodeId
      } yield _book_layer_relationship(information, "edition-of", editionid, workid, evidenceid, provenanceid, Map("source_layer" -> "edition", "target_layer" -> "textual-work"))).toVector ++
      (for {
        workid <- layers.textualWorkNodeId
        if layers.volumeNodeId.isEmpty && layers.editionNodeId.isEmpty
      } yield _book_layer_relationship(information, "publication-of", layers.publicationNodeId, workid, evidenceid, provenanceid, Map("source_layer" -> "publication", "target_layer" -> "textual-work"))).toVector ++
      (for {
        editionid <- layers.editionNodeId
        if layers.volumeNodeId.isEmpty
      } yield _book_layer_relationship(information, "publication-of", layers.publicationNodeId, editionid, evidenceid, provenanceid, Map("source_layer" -> "publication", "target_layer" -> "edition"))).toVector ++
      (for {
        volumeid <- layers.volumeNodeId
        workid <- layers.textualWorkNodeId
        if layers.editionNodeId.isEmpty
      } yield _book_layer_relationship(information, "volume-of", volumeid, workid, evidenceid, provenanceid, Map("source_layer" -> "volume", "target_layer" -> "textual-work"))).toVector
    val nodes = textualwork.toVector ++ edition.toVector ++ volumenode.toVector
    val nodemap = nodes.map(node => node.id -> node).toMap
    val knownnodeids = nodemap.keySet + layers.publicationNodeId
    relations.filter(relation =>
      knownnodeids.contains(relation.sourceNodeId) && knownnodeids.contains(relation.targetNodeId)
    ).flatMap { relation =>
      nodemap.get(relation.targetNodeId).orElse(nodemap.get(relation.sourceNodeId)).map(_ -> relation)
    }
  }

  private def _book_layer_node(
    information: Information,
    nodeid: KnowledgeNodeId,
    layer: String,
    label: String,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId,
    attributes: Map[String, String],
    linkedInformation: Option[Information],
    naming: InformationRdfNodeNaming
  ): KnowledgeNode =
    KnowledgeNode(
      id = nodeid,
      category = KnowledgeNodeCategory(layer),
      identity = KnowledgeNodeIdentity(
        rdfNode = linkedInformation.map(naming.rdfNodeName),
        externalIdentifiers = linkedInformation.
          map(x => Vector(ExternalKnowledgeIdentifier("cncf.information", x.id.print, Some(x.domain)))).
          getOrElse(Vector(ExternalKnowledgeIdentifier("cncf.information", information.id.print, Some(s"book-$layer"))))
      ),
      presentation = KnowledgeNodePresentation.label(label),
      sources = KnowledgeNodeSources(
        evidenceIds = Vector(evidenceid),
        provenanceIds = Vector(provenanceid)
      ),
      attributes = KnowledgeAttributes(attributes ++ Map(
        "information_domain" -> linkedInformation.map(_.domain).getOrElse("book"),
        "source_information_id" -> information.id.print,
        "source_information_domain" -> "book",
        "knowledge_layer" -> layer
      ) ++ linkedInformation.map(x => Map(
        "linked_information_id" -> x.id.print,
        "linked_information_domain" -> x.domain
      )).getOrElse(Map.empty) ++ _cultural_resource_attributes(layer, BOOK_DOMAIN_PROFILE))
    )

  private def _information_label(information: Information): Option[String] =
    information.workingData.getString("title").map(_.trim).filter(_.nonEmpty).
      orElse(information.workingData.getString("displayTitle").map(_.trim).filter(_.nonEmpty)).
      orElse(information.workingData.getString("label").map(_.trim).filter(_.nonEmpty)).
      orElse(information.workingData.getString("name").map(_.trim).filter(_.nonEmpty))

  private def _cultural_resource_attributes(
    kind: String,
    profile: String
  ): Map[String, String] = {
    val normalized = if (CULTURAL_RESOURCE_KINDS.contains(kind)) kind else "cultural-resource"
    Map(
      "resource_family" -> CULTURAL_RESOURCE_FAMILY,
      "cultural_resource_kind" -> normalized,
      "domain_profile" -> profile
    )
  }

  private def _information_cultural_resource_attributes(
    domain: String
  ): Map[String, String] =
    domain match {
      case "textual-work" => _cultural_resource_attributes("textual-work", "textual-work")
      case "textual-edition" => _cultural_resource_attributes("edition", "textual-edition")
      case "textual-volume" => _cultural_resource_attributes("volume", "textual-volume")
      case _ => Map.empty
    }

  private def _book_layer_relationship(
    information: Information,
    kind: String,
    sourceid: KnowledgeNodeId,
    targetid: KnowledgeNodeId,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId,
    attributes: Map[String, String]
  ): KnowledgeRelationship =
    KnowledgeRelationship(
      id = KnowledgeRelationshipId(s"rel-${information.id.print}-${kind}-${_safe_key(targetid.print, 0)}"),
      kind = KnowledgeRelationshipKind(kind),
      sourceNodeId = sourceid,
      targetNodeId = targetid,
      rdfPredicate = Some(RdfPredicateName(kind)),
      evidenceIds = Vector(evidenceid),
      provenanceId = Some(provenanceid),
      attributes = KnowledgeAttributes(attributes)
    )

  private def _book_candidate_support_nodes_and_relationships(
    information: Information,
    layers: BookCulturalResourceLayers,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId
  ): Vector[(KnowledgeNode, KnowledgeRelationship)] =
    information.resolutionCandidates
        .filter(_.selected)
        .filter(candidate => _is_active_candidate_status(candidate.binding.status))
        .filter(candidate => Set("authors", "editors", "publisher").contains(candidate.fieldPath))
        .zipWithIndex
        .map { case (candidate, index) =>
          val domain = _candidate_domain(candidate)
          val relationship = _candidate_relationship(candidate.fieldPath)
          val sourcenodeid = _book_association_source_node(layers, candidate.fieldPath)
          val suffix = _safe_key(candidate.candidateKey, index)
          val targetnodeid = KnowledgeNodeId(s"information-${information.id.print}-${domain}-$suffix")
          val node = KnowledgeNode(
            id = targetnodeid,
            category = KnowledgeNodeCategory(domain),
            identity = KnowledgeNodeIdentity(
              rdfNode = candidate.binding.rdfSubject,
              externalIdentifiers = candidate.binding.externalIdentifiers
            ),
            presentation = KnowledgeNodePresentation.label(candidate.candidateLabel),
            sources = KnowledgeNodeSources(
              evidenceIds = Vector(evidenceid),
              provenanceIds = Vector(provenanceid)
            ),
            attributes = KnowledgeAttributes(
              "information_domain" -> domain,
              "source_information_id" -> information.id.print,
              "source_field" -> candidate.fieldPath,
              "candidate_key" -> candidate.candidateKey
            )
          )
          val relation = KnowledgeRelationship(
            id = KnowledgeRelationshipId(s"rel-${information.id.print}-${relationship}-$suffix"),
            kind = KnowledgeRelationshipKind(relationship),
            sourceNodeId = sourcenodeid,
            targetNodeId = targetnodeid,
            rdfPredicate = Some(RdfPredicateName(relationship)),
            evidenceIds = Vector(evidenceid),
            provenanceId = Some(provenanceid),
            attributes = KnowledgeAttributes(
              "source_field" -> candidate.fieldPath,
              "candidate_key" -> candidate.candidateKey
            )
          )
          node -> relation
        }

  private def _is_active_candidate_status(status: InformationBindingStatus): Boolean =
    status == InformationBindingStatus.candidate ||
      status == InformationBindingStatus.selected ||
      status == InformationBindingStatus.confirmed

  private def _book_information_association_nodes_and_relationships(
    information: Information,
    layers: BookCulturalResourceLayers,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId
  ): Vector[(KnowledgeNode, KnowledgeRelationship)] =
    Vector(
      ("authorInformationIds", "person", "authored-by"),
      ("editorInformationIds", "person", "edited-by"),
      ("publisherInformationIds", "organization", "published-by")
    ).flatMap { case (fieldpath, domain, relationship) =>
      val sourcenodeid = _book_association_source_node(layers, fieldpath)
      _line_values(information.workingData.getString(fieldpath).getOrElse("")).zipWithIndex.map { case (targetinformationid, index) =>
        val suffix = _safe_key(targetinformationid, index)
        val targetnodeid = KnowledgeNodeId(s"information-${information.id.print}-${domain}-information-$suffix")
        val node = KnowledgeNode(
          id = targetnodeid,
          category = KnowledgeNodeCategory(domain),
          presentation = KnowledgeNodePresentation.label(targetinformationid),
          sources = KnowledgeNodeSources(
            evidenceIds = Vector(evidenceid),
            provenanceIds = Vector(provenanceid)
          ),
          attributes = KnowledgeAttributes(
            "information_domain" -> domain,
            "source_information_id" -> information.id.print,
            "target_information_id" -> targetinformationid,
            "source_field" -> fieldpath
          )
        )
        val relation = KnowledgeRelationship(
          id = KnowledgeRelationshipId(s"rel-${information.id.print}-${relationship}-information-$suffix"),
          kind = KnowledgeRelationshipKind(relationship),
          sourceNodeId = sourcenodeid,
          targetNodeId = targetnodeid,
          rdfPredicate = Some(RdfPredicateName(relationship)),
          evidenceIds = Vector(evidenceid),
          provenanceId = Some(provenanceid),
          attributes = KnowledgeAttributes(
            "source_field" -> fieldpath,
            "target_information_id" -> targetinformationid
          )
        )
        node -> relation
      }
    }

  private def _book_association_source_node(
    layers: BookCulturalResourceLayers,
    fieldpath: String
  ): KnowledgeNodeId =
    fieldpath match {
      case "authors" | "authorInformationIds" => layers.authorshipSourceNodeId
      case "editors" | "editorInformationIds" => layers.editionNodeId.orElse(layers.textualWorkNodeId).getOrElse(layers.publicationNodeId)
      case "publisher" | "publisherInformationIds" => layers.publisherSourceNodeId
      case _ => layers.publicationNodeId
    }

  private def _line_values(value: String): Vector[String] =
    value.linesIterator.toVector.map(_.trim).filter(_.nonEmpty).distinct

  private def _book_classification_nodes_and_relationships(
    information: Information,
    booknodeid: KnowledgeNodeId,
    evidenceid: KnowledgeEvidenceId,
    provenanceid: KnowledgeProvenanceId
  ): Vector[(KnowledgeNode, KnowledgeRelationship)] =
    _book_classification_entries(information).filter(entry =>
      entry.primary || entry.state == "stable"
    ).zipWithIndex.map { case (entry, index) =>
      val relationship = _classification_relationship(entry.kind)
      val suffix = _safe_key(Vector(entry.code, entry.rdfUri, entry.label, entry.entryKey).find(_.nonEmpty).getOrElse(entry.kind), index)
      val nodeid = KnowledgeNodeId(s"classification-${entry.system}-$suffix")
      val identifiers =
        Vector(
          Option.when(entry.code.nonEmpty)(ExternalKnowledgeIdentifier(entry.system, entry.code, Some(entry.kind))),
          Option.when(entry.rdfUri.nonEmpty)(ExternalKnowledgeIdentifier("rdf", entry.rdfUri, Some("classification")))
        ).flatten
      val node = KnowledgeNode(
        id = nodeid,
        category = KnowledgeNodeCategory.Concept,
        identity = KnowledgeNodeIdentity(
          rdfNode = Option.when(entry.rdfUri.nonEmpty)(RdfNodeName(entry.rdfUri)),
          externalIdentifiers = identifiers
        ),
        presentation = KnowledgeNodePresentation.label(entry.label),
        sources = KnowledgeNodeSources(
          evidenceIds = Vector(evidenceid),
          provenanceIds = Vector(provenanceid)
        ),
        attributes = KnowledgeAttributes(
          "classification_entry_key" -> entry.entryKey,
          "classification_kind" -> entry.kind,
          "classification_system" -> entry.system,
          "classification_code" -> entry.code,
          "classification_source" -> entry.source,
          "classification_evidence" -> entry.evidence
        )
      )
      val relation = KnowledgeRelationship(
        id = KnowledgeRelationshipId(s"rel-${information.id.print}-${relationship}-${suffix}"),
        kind = KnowledgeRelationshipKind(relationship),
        sourceNodeId = booknodeid,
        targetNodeId = nodeid,
        rdfPredicate = Some(RdfPredicateName(relationship)),
        evidenceIds = Vector(evidenceid),
        provenanceId = Some(provenanceid),
        attributes = KnowledgeAttributes(
          "classification_entry_key" -> entry.entryKey,
          "classification_kind" -> entry.kind,
          "classification_system" -> entry.system,
          "classification_code" -> entry.code,
          "classification_source" -> entry.source
        )
      )
      node -> relation
    }

  private def _book_classification_entries(information: Information): Vector[BookClassificationEntry] =
    information.workingData.getString("classificationEntries").toVector.flatMap { value =>
      value.linesIterator.toVector.flatMap(_book_classification_entry)
    }

  private def _book_classification_entry(line: String): Option[BookClassificationEntry] = {
    val fields = line.split(";").toVector.map(_.trim).filter(_.nonEmpty).flatMap { segment =>
      segment.indexOf("=") match {
        case -1 => None
        case index => Some(segment.take(index).trim -> _classification_value_decode(segment.drop(index + 1).trim))
      }
    }.toMap
    val kind = _classification_kind(fields.getOrElse("kind", "subject"))
    val system = _classification_system(fields.getOrElse("system", "local"))
    val code = fields.getOrElse("code", "").trim
    val label = fields.getOrElse("label", "").trim match {
      case "" if system == "ndc" && code.nonEmpty => s"NDC $code"
      case "" => code
      case x => x
    }
    val rdfuri = fields.get("rdfUri").orElse(fields.get("rdfURI")).orElse(fields.get("rdf")).map(_.trim).getOrElse("")
    val entrykey = fields.getOrElse("entryKey", _safe_key(Vector(system, code, label, rdfuri).find(_.nonEmpty).getOrElse(kind), 0)).trim
    if (entrykey.isEmpty || (code.isEmpty && label.isEmpty && rdfuri.isEmpty))
      None
    else
      Some(BookClassificationEntry(
        entryKey = entrykey,
        kind = kind,
        system = system,
        code = code,
        label = label,
        rdfUri = rdfuri,
        source = fields.getOrElse("source", "").trim,
        evidence = fields.getOrElse("evidence", "").trim,
        state = fields.getOrElse("state", "editing").trim.toLowerCase,
        primary = fields.get("primary").exists(value => value == "true" || value == "on" || value == "1")
      ))
  }

  private def _classification_value_decode(value: String): String = {
    val builder = new StringBuilder
    var i = 0
    while (i < value.length) {
      if (value.charAt(i) == '%' && i + 2 < value.length) {
        value.substring(i + 1, i + 3).toLowerCase(java.util.Locale.ROOT) match {
          case "25" =>
            builder.append('%')
            i += 3
          case "3b" =>
            builder.append(';')
            i += 3
          case "3d" =>
            builder.append('=')
            i += 3
          case "0a" =>
            builder.append('\n')
            i += 3
          case "0d" =>
            builder.append('\r')
            i += 3
          case _ =>
            builder.append(value.charAt(i))
            i += 1
        }
      } else {
        builder.append(value.charAt(i))
        i += 1
      }
    }
    builder.toString
  }

  private def _classification_kind(value: String): String =
    value.trim.toLowerCase.replace("_", "-") match {
      case "library" => "library"
      case "subject" => "subject"
      case "genre" => "genre"
      case "commercial" => "commercial"
      case "knowledge-domain" | "knowledgedomain" | "domain" => "knowledge-domain"
      case _ => "subject"
    }

  private def _classification_system(value: String): String =
    value.trim.toLowerCase.replace(" ", "").replace("_", "-") match {
      case "open-library" => "openlibrary"
      case "nippondecimalclassification" => "ndc"
      case "deweydecimalclassification" => "ddc"
      case "libraryofcongressclassification" => "lcc"
      case "libraryofcongresssubjectheadings" => "lcsh"
      case x if Set("ndc", "ddc", "lcc", "lcsh", "fast", "bisac", "wikidata", "dbpedia", "openlibrary", "local").contains(x) => x
      case _ => "local"
    }

  private def _classification_relationship(kind: String): String =
    kind match {
      case "library" => KnowledgeRelationshipKind.ClassifiedBy.print
      case "genre" => "has-genre"
      case "commercial" => "has-commercial-category"
      case "knowledge-domain" => "has-knowledge-domain"
      case _ => "has-subject"
    }

  private def _candidate_domain(candidate: InformationResolutionCandidate): String = {
    val kinds = candidate.binding.externalIdentifiers.flatMap(_.kind).map(_.toLowerCase)
    if (candidate.fieldPath == "publisher" || kinds.exists(_.contains("organization")))
      "organization"
    else
      "person"
  }

  private def _candidate_relationship(fieldpath: String): String =
    fieldpath match {
      case "publisher" => "published-by"
      case "editors" => "edited-by"
      case _ => "authored-by"
    }

  private def _distinct_nodes(nodes: Vector[KnowledgeNode]): Vector[KnowledgeNode] =
    nodes.foldLeft(Vector.empty[KnowledgeNode]) { (result, node) =>
      if (result.exists(_.id == node.id))
        result
      else
        result :+ node
    }

  private def _distinct_relationships(
    relationships: Vector[KnowledgeRelationship]
  ): Vector[KnowledgeRelationship] =
    relationships.foldLeft(Vector.empty[KnowledgeRelationship]) { (result, relationship) =>
      if (result.exists(_.id == relationship.id))
        result
      else
        result :+ relationship
    }

  private def _key_value_pairs(value: String): Map[String, String] =
    value.split(";").toVector.flatMap { segment =>
      val trimmed = segment.trim
      val index = trimmed.indexOf("=")
      if (index <= 0)
        None
      else
        Some(trimmed.take(index).trim -> _relationship_value_decode(trimmed.drop(index + 1).trim))
    }.toMap

  private def _relationship_value_decode(value: String): String =
    if (value.contains("%"))
      try {
        java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8)
      } catch {
        case _: IllegalArgumentException => value
      }
    else
      value

  private def _safe_key(
    value: String,
    index: Int
  ): String = {
    val normalized = value.trim.toLowerCase.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "")
    val hash = Integer.toUnsignedString(value.hashCode, 36)
    if (normalized.nonEmpty && (normalized.length >= 4 || normalized == value.trim.toLowerCase))
      normalized
    else if (normalized.nonEmpty)
      s"$normalized-$hash"
    else if (value.trim.nonEmpty)
      s"u$hash"
    else
      s"candidate-${index + 1}"
  }
}
