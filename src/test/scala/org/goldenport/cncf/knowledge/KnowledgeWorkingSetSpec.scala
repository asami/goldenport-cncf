package org.goldenport.cncf.knowledge

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 17, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class KnowledgeWorkingSetSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "KnowledgeWorkingSet" should {
    "load index and project frames facts relationships and bindings" in {
      Given("a knowledge working-set snapshot")
      val ext = ExternalKnowledgeIdentifier("rdf", "https://example.com/a", Some("subject"))
      val nodea = KnowledgeNode(KnowledgeNodeId("node-a"), "entity", Some("Node A"), Vector(ext))
      val nodeb = KnowledgeNode(KnowledgeNodeId("node-b"), "concept", Some("Node B"))
      val nodec = KnowledgeNode(KnowledgeNodeId("node-c"), "concept", Some("Node C"))
      val provenance = KnowledgeProvenance(KnowledgeProvenanceId("prov-1"), "test")
      val evidence = KnowledgeEvidence(
        KnowledgeEvidenceId("ev-1"),
        "record",
        KnowledgeSourceRef("entity", "entity-1"),
        Some("evidence")
      )
      val relationship = KnowledgeRelationship(
        KnowledgeRelationshipId("rel-1"),
        KnowledgeRelationshipKind.TranslationOf,
        nodea.id,
        nodeb.id,
        rdfPredicate = Some(RdfPredicateName("skos:exactMatch")),
        evidenceIds = Vector(evidence.id),
        provenanceId = Some(provenance.id)
      )
      val classification = KnowledgeRelationship(
        KnowledgeRelationshipId("rel-2"),
        KnowledgeRelationshipKind.ClassifiedBy,
        nodea.id,
        nodec.id
      )
      val fact = KnowledgeFact(
        KnowledgeFactId("fact-1"),
        KnowledgeFactKind.EntityDerived,
        subjectNodeId = Some(nodea.id),
        predicate = Some("customer.segment"),
        value = Some("enterprise"),
        evidenceIds = Vector(evidence.id),
        provenanceId = Some(provenance.id),
        attributes = KnowledgeAttributes(
          "entity_name" -> "customer",
          "entity_id" -> "customer-1",
          "tag_space" -> "topic",
          "tag_id" -> "important"
        )
      )
      val frame = KnowledgeFrame(
        KnowledgeFrameId("frame-1"),
        KnowledgeFrameKind.EntityContext,
        focusNodeIds = Vector(nodea.id),
        nodeIds = Vector(nodea.id, nodeb.id, nodec.id),
        relationshipIds = Vector(relationship.id, classification.id),
        factIds = Vector(fact.id),
        evidenceIds = Vector(evidence.id),
        provenanceIds = Vector(provenance.id),
        origin = KnowledgeFrameOrigin(
          KnowledgeFrameInputRoute.EntityProjection,
          operation = Some("customer.load"),
          provenanceId = Some(provenance.id)
        )
      )
      val snapshot = KnowledgeWorkingSetSnapshot(
        nodes = Vector(nodea, nodeb, nodec),
        relationships = Vector(relationship, classification),
        evidence = Vector(evidence),
        provenance = Vector(provenance),
        frames = Vector(frame),
        facts = Vector(fact)
      )

      When("loading the snapshot")
      val workingset = _success(KnowledgeWorkingSet.load(snapshot))

      Then("the indexes are available")
      workingset.status.isReady shouldBe true
      workingset.counts shouldBe KnowledgeWorkingSetCounts(
        nodeCount = 3,
        relationshipCount = 2,
        evidenceCount = 1,
        provenanceCount = 1,
        externalIdentifierCount = 1,
        frameCount = 1,
        factCount = 1,
        entityBindingCount = 1,
        tagBindingCount = 1
      )
      val projected = workingset.nodeOption(nodea.id).getOrElse(fail("missing projected node"))
      projected.structure.correspondences.translations.map(_.nodeId) shouldBe Vector(nodeb.id)
      projected.structure.classifications.primary shouldBe Some(nodec.id)
      projected.bindings.entityBindings should contain (KnowledgeEntityBinding("customer", "customer-1"))
      projected.bindings.tagBindings should contain (KnowledgeTagBinding("topic", "important"))
      projected.sources.evidenceIds shouldBe Vector(evidence.id)
      projected.sources.provenanceIds shouldBe Vector(provenance.id)
      projected.operations.frameIds shouldBe Vector(frame.id)
      workingset.relationshipOption(relationship.id) shouldBe Some(relationship)
      workingset.relationshipsFrom(nodea.id) shouldBe Vector(relationship, classification)
      workingset.relationshipsTo(nodeb.id) shouldBe Vector(relationship)
      workingset.frameOption(frame.id) shouldBe Some(frame)
      workingset.framesForNode(nodea.id) shouldBe Vector(frame)
      workingset.framesForNode(nodeb.id) shouldBe Vector(frame)
      workingset.framesForNode(nodec.id) shouldBe Vector(frame)
      workingset.factOption(fact.id) shouldBe Some(fact)
      workingset.factsForNode(nodea.id) shouldBe Vector(fact)
      workingset.evidenceOption(evidence.id) shouldBe Some(evidence)
      workingset.provenanceOption(provenance.id) shouldBe Some(provenance)
      workingset.nodeIdsByExternalIdentifier(ext) shouldBe Vector(nodea.id)
      workingset.nodeIdsByEntityBinding(KnowledgeEntityBinding("customer", "customer-1")) shouldBe Vector(nodea.id)
      workingset.nodeIdsByTagBinding(KnowledgeTagBinding("topic", "important")) shouldBe Vector(nodea.id)
    }

    "reject duplicate ids" in {
      val node = KnowledgeNode(KnowledgeNodeId("node-dup"), "entity")
      val snapshot = KnowledgeWorkingSetSnapshot(nodes = Vector(node, node))

      KnowledgeWorkingSet.load(snapshot) shouldBe a[Consequence.Failure[_]]
    }

    "reject relationships pointing at missing nodes" in {
      val node = KnowledgeNode(KnowledgeNodeId("node-a"), "entity")
      val relationship = KnowledgeRelationship(
        KnowledgeRelationshipId("rel-missing"),
        "related",
        node.id,
        KnowledgeNodeId("missing")
      )
      val snapshot = KnowledgeWorkingSetSnapshot(
        nodes = Vector(node),
        relationships = Vector(relationship)
      )

      KnowledgeWorkingSet.load(snapshot) shouldBe a[Consequence.Failure[_]]
    }

    "reject missing frame and fact references" in {
      val node = KnowledgeNode(KnowledgeNodeId("node-a"), "entity")
      val brokenfact = KnowledgeFact(
        KnowledgeFactId("fact-broken"),
        KnowledgeFactKind.EntityDerived,
        subjectNodeId = Some(KnowledgeNodeId("missing"))
      )
      val brokenframe = KnowledgeFrame(
        KnowledgeFrameId("frame-broken"),
        KnowledgeFrameKind.EntityContext,
        focusNodeIds = Vector(node.id),
        factIds = Vector(KnowledgeFactId("missing")),
        origin = KnowledgeFrameOrigin(KnowledgeFrameInputRoute.EntityProjection)
      )

      KnowledgeWorkingSet.load(KnowledgeWorkingSetSnapshot(nodes = Vector(node), facts = Vector(brokenfact))) shouldBe a[Consequence.Failure[_]]
      KnowledgeWorkingSet.load(KnowledgeWorkingSetSnapshot(nodes = Vector(node), frames = Vector(brokenframe))) shouldBe a[Consequence.Failure[_]]
    }

    "load SIE retrieval frames with document chunk relationships" in {
      val provenance = KnowledgeProvenance(
        KnowledgeProvenanceId("prov-sie"),
        origin = "textus-sie",
        generatedBy = Some("semantic-retrieval.query")
      )
      val document = KnowledgeNode(
        id = KnowledgeNodeId("document-sm-doc"),
        category = KnowledgeNodeCategory.Document,
        identity = KnowledgeNodeIdentity(
          externalIdentifiers = Vector(ExternalKnowledgeIdentifier("textus.sie.document", "doc-sm", Some("sm")))
        ),
        presentation = KnowledgeNodePresentation.label("doc-sm"),
        sources = KnowledgeNodeSources(provenanceIds = Vector(provenance.id))
      )
      val chunk = KnowledgeNode(
        id = KnowledgeNodeId("chunk-sm-doc-1"),
        category = KnowledgeNodeCategory.Chunk,
        presentation = KnowledgeNodePresentation.label("semantic retrieval"),
        sources = KnowledgeNodeSources(provenanceIds = Vector(provenance.id))
      )
      val evidence = KnowledgeEvidence(
        KnowledgeEvidenceId("ev-chunk-1"),
        "semantic-chunk",
        KnowledgeSourceRef("semantic-chunk", "chunk-1"),
        Some("semantic retrieval"),
        Some(provenance.id)
      )
      val relationship = KnowledgeRelationship(
        id = KnowledgeRelationshipId("rel-document-chunk-1"),
        kind = KnowledgeRelationshipKind.HasPart,
        sourceNodeId = document.id,
        targetNodeId = chunk.id,
        semanticTypes = Vector(KnowledgeRelationshipSemanticType("textus.sie", "document-has-chunk")),
        evidenceIds = Vector(evidence.id),
        provenanceId = Some(provenance.id)
      )
      val fact = KnowledgeFact(
        KnowledgeFactId("fact-chunk-1"),
        KnowledgeFactKind.Generated,
        subjectNodeId = Some(chunk.id),
        predicate = Some("semantic-result"),
        value = Some("semantic retrieval"),
        evidenceIds = Vector(evidence.id),
        provenanceId = Some(provenance.id)
      )
      val frame = KnowledgeFrame(
        KnowledgeFrameId("frame-sie-query"),
        KnowledgeFrameKind.RetrievalResult,
        focusNodeIds = Vector(document.id, chunk.id),
        nodeIds = Vector(document.id, chunk.id),
        relationshipIds = Vector(relationship.id),
        factIds = Vector(fact.id),
        evidenceIds = Vector(evidence.id),
        provenanceIds = Vector(provenance.id),
        origin = KnowledgeFrameOrigin(
          KnowledgeFrameInputRoute.SieRetrieval,
          provider = Some("textus-sie"),
          operation = Some("semantic-retrieval.query"),
          provenanceId = Some(provenance.id)
        ),
        query = Some(KnowledgeQueryRef("semantic retrieval"))
      )

      val workingset = _success(KnowledgeWorkingSet.load(KnowledgeWorkingSetSnapshot(
        nodes = Vector(document, chunk),
        relationships = Vector(relationship),
        evidence = Vector(evidence),
        provenance = Vector(provenance),
        frames = Vector(frame),
        facts = Vector(fact)
      )))

      workingset.counts.relationshipCount shouldBe 1
      workingset.relationshipsFrom(document.id).map(_.kind) shouldBe Vector(KnowledgeRelationshipKind.HasPart)
      workingset.nodeOption(document.id).flatMap(_.structure.partWhole.hasPart.headOption) shouldBe Some(chunk.id)
      workingset.frameOption(frame.id).map(_.origin.route) shouldBe Some(KnowledgeFrameInputRoute.SieRetrieval)
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
