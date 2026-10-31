package org.goldenport.cncf.knowledge

import io.circe.{Codec, Decoder, Encoder, Json}
import io.circe.generic.semiauto.deriveCodec
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId

/*
 * Lossless versioned persistence of the canonical graph, not its display projection.
 * @since   Oct. 29, 2026
 * @version Oct. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object KnowledgeMaterializationCodec {
  private val _schema = "cncf.knowledge-materialization.v1"
  private given Encoder[EntityId] = Encoder.encodeString.contramap(_.print)
  private given Decoder[EntityId] = Decoder.decodeString.emap { text =>
    EntityId.createC(Record.data("id" -> text)) match {
      case Consequence.Success(id) => Right(id)
      case Consequence.Failure(conclusion) => Left(conclusion.display)
    }
  }

  // Derive each product at its own boundary so nested graph collections use
  // concrete codecs rather than recursively expanding the entire graph inline.
  private given _knowledge_node_id_codec: Codec.AsObject[KnowledgeNodeId] = deriveCodec[KnowledgeNodeId]
  private given _knowledge_relationship_id_codec: Codec.AsObject[KnowledgeRelationshipId] = deriveCodec[KnowledgeRelationshipId]
  private given _knowledge_frame_id_codec: Codec.AsObject[KnowledgeFrameId] = deriveCodec[KnowledgeFrameId]
  private given _knowledge_fact_id_codec: Codec.AsObject[KnowledgeFactId] = deriveCodec[KnowledgeFactId]
  private given _knowledge_evidence_id_codec: Codec.AsObject[KnowledgeEvidenceId] = deriveCodec[KnowledgeEvidenceId]
  private given _knowledge_provenance_id_codec: Codec.AsObject[KnowledgeProvenanceId] = deriveCodec[KnowledgeProvenanceId]
  private given _rdf_node_name_codec: Codec.AsObject[RdfNodeName] = deriveCodec[RdfNodeName]
  private given _rdf_predicate_name_codec: Codec.AsObject[RdfPredicateName] = deriveCodec[RdfPredicateName]
  private given _knowledge_node_category_codec: Codec.AsObject[KnowledgeNodeCategory] = deriveCodec[KnowledgeNodeCategory]
  private given _knowledge_relationship_kind_codec: Codec.AsObject[KnowledgeRelationshipKind] = deriveCodec[KnowledgeRelationshipKind]
  private given _knowledge_semantic_type_status_codec: Codec.AsObject[KnowledgeSemanticTypeStatus] = deriveCodec[KnowledgeSemanticTypeStatus]
  private given _knowledge_source_ref_codec: Codec.AsObject[KnowledgeSourceRef] = deriveCodec[KnowledgeSourceRef]
  private given _knowledge_semantic_type_codec: Codec.AsObject[KnowledgeSemanticType] = deriveCodec[KnowledgeSemanticType]
  private given _knowledge_relationship_semantic_type_codec: Codec.AsObject[KnowledgeRelationshipSemanticType] = deriveCodec[KnowledgeRelationshipSemanticType]
  private given _external_knowledge_identifier_codec: Codec.AsObject[ExternalKnowledgeIdentifier] = deriveCodec[ExternalKnowledgeIdentifier]
  private given _knowledge_labels_codec: Codec.AsObject[KnowledgeLabels] = deriveCodec[KnowledgeLabels]
  private given _knowledge_names_codec: Codec.AsObject[KnowledgeNames] = deriveCodec[KnowledgeNames]
  private given _knowledge_descriptions_codec: Codec.AsObject[KnowledgeDescriptions] = deriveCodec[KnowledgeDescriptions]
  private given _knowledge_identity_links_codec: Codec.AsObject[KnowledgeIdentityLinks] = deriveCodec[KnowledgeIdentityLinks]
  private given _knowledge_node_identity_codec: Codec.AsObject[KnowledgeNodeIdentity] = deriveCodec[KnowledgeNodeIdentity]
  private given _knowledge_node_presentation_codec: Codec.AsObject[KnowledgeNodePresentation] = deriveCodec[KnowledgeNodePresentation]
  private given _knowledge_confidence_profile_codec: Codec.AsObject[KnowledgeConfidenceProfile] = deriveCodec[KnowledgeConfidenceProfile]
  private given _knowledge_confidentiality_profile_codec: Codec.AsObject[KnowledgeConfidentialityProfile] = deriveCodec[KnowledgeConfidentialityProfile]
  private given _knowledge_temporal_profile_codec: Codec.AsObject[KnowledgeTemporalProfile] = deriveCodec[KnowledgeTemporalProfile]
  private given _knowledge_node_lifecycle_codec: Codec.AsObject[KnowledgeNodeLifecycle] = deriveCodec[KnowledgeNodeLifecycle]
  private given _knowledge_node_semantics_codec: Codec.AsObject[KnowledgeNodeSemantics] = deriveCodec[KnowledgeNodeSemantics]
  private given _knowledge_node_correspondence_codec: Codec.AsObject[KnowledgeNodeCorrespondence] = deriveCodec[KnowledgeNodeCorrespondence]
  private given _knowledge_node_correspondences_codec: Codec.AsObject[KnowledgeNodeCorrespondences] = deriveCodec[KnowledgeNodeCorrespondences]
  private given _knowledge_classifications_codec: Codec.AsObject[KnowledgeClassifications] = deriveCodec[KnowledgeClassifications]
  private given _knowledge_hierarchy_codec: Codec.AsObject[KnowledgeHierarchy] = deriveCodec[KnowledgeHierarchy]
  private given _knowledge_part_whole_codec: Codec.AsObject[KnowledgePartWhole] = deriveCodec[KnowledgePartWhole]
  private given _knowledge_node_structure_codec: Codec.AsObject[KnowledgeNodeStructure] = deriveCodec[KnowledgeNodeStructure]
  private given _knowledge_source_refs_codec: Codec.AsObject[KnowledgeSourceRefs] = deriveCodec[KnowledgeSourceRefs]
  private given _knowledge_node_sources_codec: Codec.AsObject[KnowledgeNodeSources] = deriveCodec[KnowledgeNodeSources]
  private given _knowledge_entity_binding_codec: Codec.AsObject[KnowledgeEntityBinding] = deriveCodec[KnowledgeEntityBinding]
  private given _knowledge_tag_binding_codec: Codec.AsObject[KnowledgeTagBinding] = deriveCodec[KnowledgeTagBinding]
  private given _knowledge_node_bindings_codec: Codec.AsObject[KnowledgeNodeBindings] = deriveCodec[KnowledgeNodeBindings]
  private given _knowledge_similarity_representation_codec: Codec.AsObject[KnowledgeSimilarityRepresentation] = deriveCodec[KnowledgeSimilarityRepresentation]
  private given _knowledge_similarity_search_entry_codec: Codec.AsObject[KnowledgeSimilaritySearchEntry] = deriveCodec[KnowledgeSimilaritySearchEntry]
  private given _knowledge_similarity_status_codec: Codec.AsObject[KnowledgeSimilarityStatus] = deriveCodec[KnowledgeSimilarityStatus]
  private given _knowledge_node_similarity_codec: Codec.AsObject[KnowledgeNodeSimilarity] = deriveCodec[KnowledgeNodeSimilarity]
  private given _knowledge_validation_status_codec: Codec.AsObject[KnowledgeValidationStatus] = deriveCodec[KnowledgeValidationStatus]
  private given _knowledge_node_operations_codec: Codec.AsObject[KnowledgeNodeOperations] = deriveCodec[KnowledgeNodeOperations]
  private given _knowledge_attributes_codec: Codec.AsObject[KnowledgeAttributes] = deriveCodec[KnowledgeAttributes]
  private given _knowledge_node_codec: Codec.AsObject[KnowledgeNode] = deriveCodec[KnowledgeNode]
  private given _knowledge_relationship_qualifiers_codec: Codec.AsObject[KnowledgeRelationshipQualifiers] = deriveCodec[KnowledgeRelationshipQualifiers]
  private given _knowledge_relationship_similarity_codec: Codec.AsObject[KnowledgeRelationshipSimilarity] = deriveCodec[KnowledgeRelationshipSimilarity]
  private given _knowledge_relationship_codec: Codec.AsObject[KnowledgeRelationship] = deriveCodec[KnowledgeRelationship]
  private given _knowledge_fact_kind_codec: Codec.AsObject[KnowledgeFactKind] = deriveCodec[KnowledgeFactKind]
  private given _knowledge_fact_codec: Codec.AsObject[KnowledgeFact] = deriveCodec[KnowledgeFact]
  private given _knowledge_frame_kind_codec: Codec.AsObject[KnowledgeFrameKind] = deriveCodec[KnowledgeFrameKind]
  private given _knowledge_frame_input_route_codec: Codec.AsObject[KnowledgeFrameInputRoute] = deriveCodec[KnowledgeFrameInputRoute]
  private given _knowledge_frame_origin_codec: Codec.AsObject[KnowledgeFrameOrigin] = deriveCodec[KnowledgeFrameOrigin]
  private given _knowledge_frame_purpose_codec: Codec.AsObject[KnowledgeFramePurpose] = deriveCodec[KnowledgeFramePurpose]
  private given _knowledge_query_ref_codec: Codec.AsObject[KnowledgeQueryRef] = deriveCodec[KnowledgeQueryRef]
  private given _knowledge_frame_codec: Codec.AsObject[KnowledgeFrame] = deriveCodec[KnowledgeFrame]
  private given _knowledge_evidence_codec: Codec.AsObject[KnowledgeEvidence] = deriveCodec[KnowledgeEvidence]
  private given _knowledge_provenance_codec: Codec.AsObject[KnowledgeProvenance] = deriveCodec[KnowledgeProvenance]
  private given _knowledge_working_set_snapshot_codec: Codec.AsObject[KnowledgeWorkingSetSnapshot] = deriveCodec[KnowledgeWorkingSetSnapshot]
  private given _knowledge_owner_reference_codec: Codec.AsObject[KnowledgeOwnerReference] = deriveCodec[KnowledgeOwnerReference]
  private given _knowledge_decision_reference_codec: Codec.AsObject[KnowledgeDecisionReference] = deriveCodec[KnowledgeDecisionReference]
  private given _knowledge_materialization_codec: Codec.AsObject[KnowledgeMaterialization] = deriveCodec[KnowledgeMaterialization]

  def encodeC(value: KnowledgeMaterialization): Consequence[String] =
    KnowledgeMaterialization.validate(value).map { _ =>
      Json.obj("schemaVersion" -> Json.fromString(_schema), "materialization" -> value.asJson).noSpaces
    }

  def decodeC(text: String): Consequence[KnowledgeMaterialization] =
    io.circe.parser.parse(text) match {
      case Left(_) => Consequence.argumentInvalid("Invalid Knowledge materialization JSON")
      case Right(json) =>
        if (!json.asObject.exists(_.keys.toSet == Set("schemaVersion", "materialization")) ||
            !json.hcursor.get[String]("schemaVersion").contains(_schema))
          Consequence.argumentInvalid("Unsupported Knowledge materialization schema")
        else json.hcursor.get[KnowledgeMaterialization]("materialization") match {
          case Left(_) => Consequence.argumentInvalid("Invalid typed Knowledge materialization")
          case Right(value) =>
            if (!json.hcursor.downField("materialization").focus.contains(value.asJson))
              Consequence.argumentInvalid("Knowledge materialization contains undeclared or missing fields")
            else KnowledgeMaterialization.validate(value).map(_ => value)
        }
    }
}
