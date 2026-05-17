package org.goldenport.cncf.knowledge

import org.goldenport.Consequence
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 17, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class KnowledgeSpaceProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "KnowledgeSpaceProjection" should {
    "project status counts and records deterministically" in {
      val component = TestComponentFactory.create("knowledge_component", Protocol.empty)
      val ext = ExternalKnowledgeIdentifier.entity("customer", "customer-1")
      val provenance = KnowledgeProvenance(KnowledgeProvenanceId("prov-1"), "spec")
      val evidence = KnowledgeEvidence(
        KnowledgeEvidenceId("ev-1"),
        "entity-record",
        KnowledgeSourceRef("entity", "customer-1"),
        Some("Customer record")
      )
      val customer = KnowledgeNode(
        KnowledgeNodeId("node-customer"),
        "entity",
        Some("Customer"),
        Vector(ext),
        Some(provenance.id),
        Map("segment" -> "enterprise")
      )
      val concept = KnowledgeNode(KnowledgeNodeId("node-concept"), "concept", Some("Important customer"))
      val relationship = KnowledgeRelationship(
        KnowledgeRelationshipId("rel-1"),
        KnowledgeRelationshipKind.ClassifiedBy,
        customer.id,
        concept.id,
        rdfPredicate = Some(RdfPredicateName("rdf:type")),
        evidenceIds = Vector(evidence.id),
        provenanceId = Some(provenance.id)
      )
      val fact = KnowledgeFact(
        KnowledgeFactId("fact-1"),
        KnowledgeFactKind.EntityDerived,
        subjectNodeId = Some(customer.id),
        predicate = Some("customer.status"),
        value = Some("active"),
        evidenceIds = Vector(evidence.id),
        provenanceId = Some(provenance.id),
        attributes = KnowledgeAttributes(
          "tag_space" -> "customer-segment",
          "tag_id" -> "enterprise"
        )
      )
      val frame = KnowledgeFrame(
        KnowledgeFrameId("frame-1"),
        KnowledgeFrameKind.EntityContext,
        focusNodeIds = Vector(customer.id),
        nodeIds = Vector(customer.id, concept.id),
        relationshipIds = Vector(relationship.id),
        factIds = Vector(fact.id),
        evidenceIds = Vector(evidence.id),
        provenanceIds = Vector(provenance.id),
        origin = KnowledgeFrameOrigin(
          KnowledgeFrameInputRoute.EntityProjection,
          operation = Some("customer.search"),
          provenanceId = Some(provenance.id)
        )
      )
      val snapshot = KnowledgeWorkingSetSnapshot(
        nodes = Vector(customer, concept),
        relationships = Vector(relationship),
        evidence = Vector(evidence),
        provenance = Vector(provenance),
        frames = Vector(frame),
        facts = Vector(fact)
      )
      _success(component.knowledgeSpace.replace(snapshot))

      val projection = KnowledgeSpaceProjection.component(component)
      val record = projection.toRecord
      val node = KnowledgeSpaceProjection.nodeOption(component, customer.id).getOrElse(fail("missing node projection"))

      projection.componentName shouldBe "knowledge_component"
      projection.counts shouldBe KnowledgeWorkingSetCounts(
        nodeCount = 2,
        relationshipCount = 1,
        evidenceCount = 1,
        provenanceCount = 1,
        externalIdentifierCount = 1,
        frameCount = 1,
        factCount = 1,
        entityBindingCount = 1,
        tagBindingCount = 1
      )
      projection.sourceDiagnostics.providerStatus shouldBe "not_attached"
      projection.sourceDiagnostics.storage shouldBe "memory"
      record.asMap.keySet should contain allOf ("component", "status", "counts", "source_diagnostics", "frames", "nodes", "relationships", "facts", "evidence", "provenance")
      node.relationshipsFrom shouldBe Vector(relationship)
      node.relationshipsTo shouldBe Vector.empty
      node.frames shouldBe Vector(frame)
      node.facts shouldBe Vector(fact)
      node.evidence shouldBe Vector(evidence)
      node.provenance shouldBe Vector(provenance)
      node.node.structure.classifications.primary shouldBe Some(concept.id)
      node.node.bindings.tagBindings should contain (KnowledgeTagBinding("customer-segment", "enterprise"))

      val conceptnode = KnowledgeSpaceProjection.nodeOption(component, concept.id).getOrElse(fail("missing concept projection"))
      conceptnode.frames shouldBe Vector(frame)
      conceptnode.provenance shouldBe Vector(provenance)
    }

    "lookup entity bindings across components" in {
      val first = TestComponentFactory.create("first", Protocol.empty)
      val second = TestComponentFactory.create("second", Protocol.empty)
      val firstnode = KnowledgeNode(
        KnowledgeNodeId("node-first"),
        "entity",
        Vector(ExternalKnowledgeIdentifier.entity("customer", "customer-1"))
      )
      val secondnode = KnowledgeNode(
        KnowledgeNodeId("node-second"),
        "entity",
        Vector(ExternalKnowledgeIdentifier.entity("customer", "customer-1"))
      )
      _success(first.knowledgeSpace.replace(KnowledgeWorkingSetSnapshot(nodes = Vector(firstnode))))
      _success(second.knowledgeSpace.replace(KnowledgeWorkingSetSnapshot(nodes = Vector(secondnode))))

      val result = KnowledgeSpaceProjection.lookupEntity(Vector(second, first), "customer", "customer-1")

      result.map(_.componentName) shouldBe Vector("first", "second")
      result.map(_.node.id) shouldBe Vector(firstnode.id, secondnode.id)
      result.map(_.toRecord.asMap.keySet.contains("node")) shouldBe Vector(true, true)
    }

    "render external identifier keys without colon ambiguity" in {
      val withkind = ExternalKnowledgeIdentifier("rdf:system", "http://example.com/a:b", Some("subject:type"))
      val withoutkind = ExternalKnowledgeIdentifier("rdf:system", "http://example.com/a:b", None)

      withkind.key shouldBe "system=10:rdf:system|kind=12:subject:type|value=22:http://example.com/a:b"
      withoutkind.key shouldBe "system=10:rdf:system|kind=-|value=22:http://example.com/a:b"
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
