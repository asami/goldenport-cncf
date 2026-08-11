package org.goldenport.cncf.knowledge

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.testutil.TestComponentFactory
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 17, 2026
 *  version May. 18, 2026
 *  version Jul. 16, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class KnowledgeSpaceProjectionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  private given ExecutionContext = ExecutionContext.test()

  "KnowledgeSpaceProjection" should {
    "project status counts and records deterministically" in {
      Given("a component with an indexed Knowledge working set")
      val component = TestComponentFactory.create("knowledge_component", Protocol.empty)
      val ext = ExternalKnowledgeIdentifier.entity("customer", "customer-1")
      val rdfnode = RdfNodeName("rdf:customer-1")
      val provenance = KnowledgeProvenance(KnowledgeProvenanceId("prov-1"), "spec")
      val evidence = KnowledgeEvidence(
        KnowledgeEvidenceId("ev-1"),
        "entity-record",
        KnowledgeSourceRef("entity", "customer-1"),
        Some("Customer record")
      )
      val customer = KnowledgeNode(
        id = KnowledgeNodeId("node-customer"),
        category = KnowledgeNodeCategory.Entity,
        identity = KnowledgeNodeIdentity(
          rdfNode = Some(rdfnode),
          externalIdentifiers = Vector(ext)
        ),
        presentation = KnowledgeNodePresentation.label("Customer"),
        semantics = KnowledgeNodeSemantics(
          semanticTypes = Vector(KnowledgeSemanticType("cncf", "customer")),
          roles = Set("business-entity")
        ),
        sources = KnowledgeNodeSources(provenanceIds = Vector(provenance.id)),
        bindings = KnowledgeNodeBindings.from(Vector(ext)),
        similarity = KnowledgeNodeSimilarity(
          representations = Vector(KnowledgeSimilarityRepresentation(method = Some("embedding"), model = Some("demo-model"), metric = Some("cosine"))),
          searchEntries = Vector(KnowledgeSimilaritySearchEntry(provider = Some("demo"), collection = Some("customers"), searchId = Some("search-customer-1")))
        ),
        attributes = KnowledgeAttributes("segment" -> "enterprise")
      )
      val concept = KnowledgeNode(KnowledgeNodeId("node-concept"), "concept", Some("Important customer"))
      val relationship = KnowledgeRelationship(
        KnowledgeRelationshipId("rel-1"),
        KnowledgeRelationshipKind.ClassifiedBy,
        customer.id,
        concept.id,
        rdfPredicate = Some(RdfPredicateName("rdf:type")),
        semanticTypes = Vector(KnowledgeRelationshipSemanticType("rdf", "type")),
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

      When("component, query, and node projections are built")
      val projection = KnowledgeSpaceProjection.component(component)
      val record = projection.toRecord
      val query = component.knowledgeSpace.query
      val node = KnowledgeSpaceProjection.nodeOption(component, customer.id).getOrElse(fail("missing node projection"))

      Then("the projections expose stable status, counts, and semantic indexes")
      projection.componentName shouldBe component.name
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
      record.asMap("component") shouldBe component.name
      node.relationshipsFrom shouldBe Vector(relationship)
      node.relationshipsTo shouldBe Vector.empty
      node.frames shouldBe Vector(frame)
      node.facts shouldBe Vector(fact)
      node.evidence shouldBe Vector(evidence)
      node.provenance shouldBe Vector(provenance)
      node.node.structure.classifications.primary shouldBe Some(concept.id)
      node.node.bindings.tagBindings should contain (KnowledgeTagBinding("customer-segment", "enterprise"))
      node.node.identity.rdfNode shouldBe Some(rdfnode)
      node.node.semantics.semanticTypes.map(x => x.system -> x.name) shouldBe Vector("cncf" -> "customer")
      query.node(customer.id).nodes shouldBe Vector(node.node)
      query.rdfNode(rdfnode).nodes.map(_.id) shouldBe Vector(customer.id)
      query.externalIdentifier(ext).nodes.map(_.id) shouldBe Vector(customer.id)
      query.entityBinding(KnowledgeEntityBinding("customer", "customer-1")).nodes.map(_.id) shouldBe Vector(customer.id)
      query.tagBinding(KnowledgeTagBinding("customer-segment", "enterprise")).nodes.map(_.id) shouldBe Vector(customer.id)
      query.semanticType("cncf", "customer").nodes.map(_.id) shouldBe Vector(customer.id)
      query.semanticType("rdf", "type").relationships.map(_.id) shouldBe Vector(relationship.id)
      query.frame(frame.id).frames shouldBe Vector(frame)
      query.fact(fact.id).facts shouldBe Vector(fact)
      query.relationship(relationship.id).relationships shouldBe Vector(relationship)
      query.node(KnowledgeNodeId("missing")).isEmpty shouldBe true

      val conceptnode = KnowledgeSpaceProjection.nodeOption(component, concept.id).getOrElse(fail("missing concept projection"))
      conceptnode.frames shouldBe Vector(frame)
      conceptnode.provenance shouldBe Vector(provenance)
    }

    "lookup entity bindings across components" in {
      Given("two component KnowledgeSpaces bound to the same Entity")
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

      When("the Entity binding is looked up across components")
      val result = KnowledgeSpaceProjection.lookupEntity(Vector(second, first), "customer", "customer-1")

      Then("the component projections are returned in deterministic name order")
      result.map(_.componentName) shouldBe Vector(first.name, second.name)
      result.map(_.node.id) shouldBe Vector(firstnode.id, secondnode.id)
      result.map(_.toRecord.asMap.keySet.contains("node")) shouldBe Vector(true, true)
    }

    "reject ambiguous presentation selectors while preserving qualified component identity" in {
      Given("two qualified components sharing one display name")
      val first = TestComponentFactory.create(
        "org.example.First",
        Protocol.empty,
        displayName = Some("shared-knowledge")
      )
      val second = TestComponentFactory.create(
        "org.example.Second",
        Protocol.empty,
        displayName = Some("shared-knowledge")
      )
      val components = Vector(first, second)

      When("the shared presentation selector and qualified selectors are resolved")
      val ambiguous = KnowledgeSpaceProjection.componentOption(components, "shared-knowledge")
      val firstresolved = KnowledgeSpaceProjection.componentOption(components, first.name)
      val secondresolved = KnowledgeSpaceProjection.componentOption(components, second.name)
      val firstselector = KnowledgeSpaceProjection.componentSelector(components, first)
      val secondselector = KnowledgeSpaceProjection.componentSelector(components, second)

      Then("ambiguous presentation aliases are rejected and qualified identity remains exact")
      ambiguous shouldBe None
      firstresolved shouldBe Some(first)
      secondresolved shouldBe Some(second)
      firstselector shouldBe first.name
      secondselector shouldBe second.name
    }

    "render external identifier keys without colon ambiguity" in {
      Given("external identifiers containing colons in every segment")
      val withkind = ExternalKnowledgeIdentifier("rdf:system", "http://example.com/a:b", Some("subject:type"))
      val withoutkind = ExternalKnowledgeIdentifier("rdf:system", "http://example.com/a:b", None)

      When("their canonical map keys are rendered")
      val withkindkey = withkind.key
      val withoutkindkey = withoutkind.key

      Then("length-prefixed segments preserve the identifier boundaries")
      withkindkey shouldBe "system=10:rdf:system|kind=12:subject:type|value=22:http://example.com/a:b"
      withoutkindkey shouldBe "system=10:rdf:system|kind=-|value=22:http://example.com/a:b"
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
