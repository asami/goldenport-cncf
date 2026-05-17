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
    "load and index nodes relationships evidence provenance and external identifiers" in {
      Given("a knowledge working-set snapshot")
      val ext = ExternalKnowledgeIdentifier("rdf", "https://example.com/a", Some("subject"))
      val nodea = KnowledgeNode(KnowledgeNodeId("node-a"), "entity", Some("Node A"), Vector(ext))
      val nodeb = KnowledgeNode(KnowledgeNodeId("node-b"), "concept", Some("Node B"))
      val provenance = KnowledgeProvenance(KnowledgeProvenanceId("prov-1"), "test")
      val evidence = KnowledgeEvidence(
        KnowledgeEvidenceId("ev-1"),
        "record",
        KnowledgeSourceRef("entity", "entity-1"),
        Some("evidence")
      )
      val relationship = KnowledgeRelationship(
        KnowledgeRelationshipId("rel-1"),
        "related",
        nodea.id,
        nodeb.id,
        Vector(evidence.id),
        Some(provenance.id)
      )
      val snapshot = KnowledgeWorkingSetSnapshot(
        nodes = Vector(nodea, nodeb),
        relationships = Vector(relationship),
        evidence = Vector(evidence),
        provenance = Vector(provenance)
      )

      When("loading the snapshot")
      val workingset = _success(KnowledgeWorkingSet.load(snapshot))

      Then("the indexes are available")
      workingset.status.isReady shouldBe true
      workingset.counts shouldBe KnowledgeWorkingSetCounts(2, 1, 1, 1, 1)
      workingset.nodeOption(nodea.id) shouldBe Some(nodea)
      workingset.relationshipOption(relationship.id) shouldBe Some(relationship)
      workingset.relationshipsFrom(nodea.id) shouldBe Vector(relationship)
      workingset.relationshipsTo(nodeb.id) shouldBe Vector(relationship)
      workingset.evidenceOption(evidence.id) shouldBe Some(evidence)
      workingset.provenanceOption(provenance.id) shouldBe Some(provenance)
      workingset.nodeIdsByExternalIdentifier(ext) shouldBe Vector(nodea.id)
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
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
