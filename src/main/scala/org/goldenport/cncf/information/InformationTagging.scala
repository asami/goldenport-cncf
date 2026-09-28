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
object InformationTagging {
  val TagSpace: String = InformationSpaceEditorProjection.InformationTagSpace
  val Role: String = InformationSpaceEditorProjection.InformationTagRole

  def workflow(tagspace: String = TagSpace): TaggingWorkflow =
    TaggingWorkflow(tagSpace = tagspace)

  def knowledgeTagBindings(
    informationid: EntityId,
    tagspace: String = TagSpace
  )(using ExecutionContext): Consequence[Vector[KnowledgeTagBinding]] =
    workflow(tagspace).listEntityTags(informationid.print, Some(Role)).map { summary =>
      summary.tags.map(tag => KnowledgeTagBinding(tag.tagSpace, tag.id.value))
    }
}
