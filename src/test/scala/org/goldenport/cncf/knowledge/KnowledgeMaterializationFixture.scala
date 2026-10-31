package org.goldenport.cncf.knowledge

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.{ExecutionContext, Principal, PrincipalId, SecurityContext, SecurityLevel}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.entity.{EntityObservedDeleteCanonicalFixture, EntityRevisionRepresentation, EntityRevisionSpecSupport}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWork, UnitOfWorkInterpreter}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Actual native Entity/UoW fixture; decisions below are controlled specification data.
 * @since   Oct. 29, 2026
 * @version Oct. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[knowledge] object KnowledgeMaterializationFixture {
  val instant: Instant = Instant.parse("2026-10-29T00:00:00Z")
  val source: KnowledgeOwnerReference = KnowledgeOwnerReference("fixture.hub", "saved-information", "4")
  val target: KnowledgeOwnerReference = KnowledgeOwnerReference("fixture.hub", "selected-target", "2")
  val context: KnowledgeOwnerReference = KnowledgeOwnerReference("fixture.hub", "bounded-context", "1")
  val decision: KnowledgeDecisionReference = KnowledgeDecisionReference(
    KnowledgeOwnerReference("fixture.workbench", "explicit-decision", "3"), "owner", instant, "Selected target reviewed")

  def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => throw new IllegalStateException(conclusion.display)
  }

  final class Session(token: String, provider: DataStore = DataStore.inMemorySearchable(),
    principal: String = "owner", authenticated: Boolean = true, denyFactory: Boolean = false) {
    private val _base = EntityObservedDeleteCanonicalFixture.fixture(token + "_runtime",
      provider = provider, principal = principal, denyFactory = denyFactory)
    private val _subject = new Principal {
      def id: PrincipalId = PrincipalId(principal)
      def attributes: Map[String, String] = Map("authenticated" -> authenticated.toString)
    }
    val executionContext: ExecutionContext = ExecutionContext.withSecurityContext(_base.context,
      SecurityContext(_subject, Set.empty, SecurityLevel("test")))
    val collectionId: EntityCollectionId = EntityCollectionId("test", "knowledge", token)
    val repository: KnowledgeMaterializationRepository = new KnowledgeMaterializationRepository(ComponentId("org.goldenport.cncf.KnowledgeMaterializationFixture"), collectionId)
    EntityRevisionSpecSupport.registerRevisionBinding(executionContext, collectionId,
      KnowledgeMaterializationRepository.persistent, EntityRevisionRepresentation.Detached)
    def identity(token: String): EntityId = new EntityObservedDeleteCanonicalFixture.CanonicalId(collectionId, token)
    def evaluate[A](program: ExecUowM[A]): Consequence[A] =
      new UnitOfWorkInterpreter(new UnitOfWork(executionContext)).evaluateInActiveUnitOfWorkC(program)
  }

  def materialization(id: EntityId, label: String = "Book 日本", count: Int = 3): KnowledgeMaterialization = {
    val provenanceid = KnowledgeProvenanceId("provenance")
    val evidenceid = KnowledgeEvidenceId("evidence")
    val frameid = KnowledgeFrameId("frame")
    val nodes = (0 until count).toVector.map { index =>
      KnowledgeNode(KnowledgeNodeId(s"node-$index"), KnowledgeNodeCategory.Publication,
        identity = KnowledgeNodeIdentity(Some(RdfNodeName(s"urn:fixture:node:$index")),
          externalIdentifiers = Vector(ExternalKnowledgeIdentifier("fixture", s"external-$index", Some("book")))),
        presentation = KnowledgeNodePresentation(KnowledgeLabels(Some(label), Map("ja" -> label), Vector("alternate")),
          KnowledgeNames(Some(label), Vector("alias")), KnowledgeDescriptions(Some("Description"), Map("en" -> "Description"))),
        semantics = KnowledgeNodeSemantics(Vector(KnowledgeSemanticType("fixture", "publication", source = Some(KnowledgeSourceRef("information", source.identity)),
          confidence = Some(0.8), provenanceId = Some(provenanceid))), Set("subject"),
          temporal = KnowledgeTemporalProfile(observedAt = Some(instant))),
        sources = KnowledgeNodeSources(KnowledgeSourceRefs(primarySource = Some(KnowledgeSourceRef("information", source.identity)),
          extractedFrom = Vector(evidenceid)), Vector(evidenceid), Vector(provenanceid)),
        bindings = KnowledgeNodeBindings(Vector(KnowledgeEntityBinding("Information", source.identity, Some(source.revision), Some(source.owner)))),
        similarity = KnowledgeNodeSimilarity(representations = Vector(KnowledgeSimilarityRepresentation(method = Some("controlled"), payloadReference = Some("fixture-only")))),
        operations = KnowledgeNodeOperations(Some(instant), Vector(frameid)),
        attributes = KnowledgeAttributes("retained" -> label))
    }
    val relationships = nodes.drop(1).zipWithIndex.map { case (node, index) =>
      KnowledgeRelationship(KnowledgeRelationshipId(s"relationship-$index"), KnowledgeRelationshipKind.Related,
        nodes.head.id, node.id, Some(RdfPredicateName("urn:fixture:related")),
        semanticTypes = Vector(KnowledgeRelationshipSemanticType("fixture", "related", provenanceId = Some(provenanceid))),
        evidenceIds = Vector(evidenceid), provenanceId = Some(provenanceid),
        qualifiers = KnowledgeRelationshipQualifiers(Map("role" -> "controlled")))
    }
    val fact = KnowledgeFact(KnowledgeFactId("fact"), KnowledgeFactKind.EntityDerived,
      Some(nodes.head.id), value = Some(label), evidenceIds = Vector(evidenceid), provenanceId = Some(provenanceid))
    val frame = KnowledgeFrame(frameid, KnowledgeFrameKind.Curated, Vector(nodes.head.id), nodes.map(_.id),
      relationships.map(_.id), Vector(fact.id), Vector(evidenceid), Vector(provenanceid),
      KnowledgeFrameOrigin(KnowledgeFrameInputRoute.ManualCuration, component = Some("fixture.hub"),
        createdBy = Some("owner"), provenanceId = Some(provenanceid)),
      sourceRefs = Vector(KnowledgeSourceRef("information", source.identity)), materializedAt = Some(instant))
    KnowledgeMaterialization(id, source, target, context, decision, KnowledgeWorkingSetSnapshot(nodes, relationships,
      Vector(KnowledgeEvidence(evidenceid, "information", KnowledgeSourceRef("information", source.identity), Some(label), Some(provenanceid))),
      Vector(KnowledgeProvenance(provenanceid, "controlled-specification", generatedBy = Some("fixture"))), Vector(frame), Vector(fact)))
  }
}
