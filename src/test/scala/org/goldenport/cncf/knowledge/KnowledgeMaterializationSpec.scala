package org.goldenport.cncf.knowledge

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 29, 2026
 * @version Oct. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class KnowledgeMaterializationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import KnowledgeMaterializationFixture.*

  "Canonical Knowledge materialization" should {
    "retain typed meaning independently of its operational graph projection" which {
      "roundtrip bounded graphs and exact semantic owner revisions without rewriting input" in {
        val property = Prop.forAll(Gen.choose(2, 6), Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.take(64).mkString)) { (count, label) =>
          Given("a canonical graph with evidence, provenance, semantic references and attributed controlled decision")
          val session = new Session("roundtrip")
          val value = materialization(session.identity("selected"), label + " 日本\n", count)
          When("the versioned storage codec encodes and reconstructs the complete typed graph")
          val text = success(KnowledgeMaterializationCodec.encodeC(value))
          val reconstructed = success(KnowledgeMaterializationCodec.decodeC(text))
          Then("all fields, source revisions and decision provenance remain exactly equal")
          reconstructed shouldBe value
          reconstructed.snapshot shouldBe value.snapshot
          reconstructed.semanticTarget shouldBe target
          true
        }
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
        withClue(checked.toString) {
          checked.passed shouldBe true
          checked.succeeded should be >= 100
        }
      }
      "roundtrip nondefault nested graph metadata without reducing it to a display projection" in {
        val property = Prop.forAll(Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.take(64).mkString)) { label =>
          Given("a canonical graph with structural links, typed statuses and full similarity metadata")
          val session = new Session("nested")
          val base = materialization(session.identity("selected"), label)
          val node = base.snapshot.nodes.head
          val related = base.snapshot.nodes(1).id
          val representation = KnowledgeSimilarityRepresentation(Some("embedding"), Some("fixture-model"),
            Some("cosine"), Some(label), Some(KnowledgeSourceRef("fixture", label, Some("urn:fixture"))), Some("retained-payload"))
          val search = KnowledgeSimilaritySearchEntry(Some("fixture-provider"), Some("fixture-collection"),
            Some(label), Some(instant))
          val enriched = node.copy(
            identity = node.identity.copy(identityLinks = KnowledgeIdentityLinks(Some(related), Vector(related), Vector(related))),
            semantics = node.semantics.copy(
              semanticTypes = node.semantics.semanticTypes.map(_.copy(label = Some(label), profile = Some("fixture-profile"),
                status = KnowledgeSemanticTypeStatus.Candidate)),
              confidence = KnowledgeConfidenceProfile(Some(0.75), Some("reviewed")),
              confidentiality = KnowledgeConfidentialityProfile(Some("restricted"), Some("owner")),
              temporal = KnowledgeTemporalProfile(Some(instant), Some(instant), Some(instant)),
              lifecycle = KnowledgeNodeLifecycle(Some("retained"), Some(instant), Some(instant))),
            structure = KnowledgeNodeStructure(
              KnowledgeNodeCorrespondences(translations = Vector(KnowledgeNodeCorrespondence(related,
                Some("ja"), Some("ja-JP"), KnowledgeRelationshipKind.TranslationOf))),
              KnowledgeClassifications(Some(related), Vector(related), Vector(related), Vector(related)),
              KnowledgeHierarchy(Some(related), Vector(related), Vector(related), Vector(related)),
              KnowledgePartWhole(Vector(related), Vector(related), Vector(related), Vector(related), Some(related))),
            sources = node.sources.copy(sourceRefs = node.sources.sourceRefs.copy(
              sourceDocuments = Vector(KnowledgeSourceRef("document", label)),
              sourceChunks = Vector(KnowledgeSourceRef("chunk", label)), observedIn = node.sources.evidenceIds)),
            bindings = node.bindings.copy(tagBindings = Vector(KnowledgeTagBinding("fixture", label))),
            similarity = KnowledgeNodeSimilarity(Vector(representation), Vector(search), KnowledgeSimilarityStatus.Indexed),
            operations = node.operations.copy(validationStatus = KnowledgeValidationStatus.Valid))
          val value = base.copy(snapshot = base.snapshot.copy(
            nodes = enriched +: base.snapshot.nodes.tail,
            relationships = base.snapshot.relationships.map(_.copy(
              similarity = KnowledgeRelationshipSimilarity(Vector(representation), Vector(search), KnowledgeSimilarityStatus.Stale))),
            frames = base.snapshot.frames.map(_.copy(purpose = Some(KnowledgeFramePurpose(label)),
              query = Some(KnowledgeQueryRef(label)), confidence = Some(0.9)))))
          When("the storage codec reconstructs all canonical products through their concrete nested codecs")
          val reconstructed = success(KnowledgeMaterializationCodec.decodeC(success(KnowledgeMaterializationCodec.encodeC(value))))
          Then("the complete graph including fields absent from display projections remains equal")
          reconstructed shouldBe value
          true
        }
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
        withClue(checked.toString) {
          checked.passed shouldBe true
          checked.succeeded should be >= 100
        }
      }
      "continue using the existing KnowledgeSpace traversal with the retained graph" in {
        Given("one validated typed result and the ordinary operational KnowledgeSpace")
        val session = new Session("traversal")
        val value = materialization(session.identity("selected"))
        given ExecutionContext = session.executionContext
        val space = new KnowledgeSpace
        When("the existing memory view loads the canonical stored snapshot")
        success(space.replace(value.snapshot))
        Then("node and directional relationship traversal retain canonical identities")
        space.nodeOption(value.snapshot.nodes.head.id).map(_.id) shouldBe Some(value.snapshot.nodes.head.id)
        space.relationshipsFrom(value.snapshot.nodes.head.id).map(_.id) shouldBe value.snapshot.relationships.map(_.id)
        value.snapshot.nodes.head.structure shouldBe KnowledgeNodeStructure.empty
      }
    }
    "reject incompatible graph and reference data before persistence" which {
      "reject dangling references across the source, structure, semantic and frame surfaces" in {
        Given("one complete graph and independent malformed reference variants")
        val session = new Session("invalid")
        val value = materialization(session.identity("selected"))
        val node = value.snapshot.nodes.head
        val badnode = KnowledgeNodeId("missing")
        val variants = Vector(
          value.copy(source = source.copy(revision = "")),
          value.copy(decision = decision.copy(actor = "")),
          value.copy(snapshot = value.snapshot.copy(nodes = value.snapshot.nodes :+ node)),
          value.copy(snapshot = value.snapshot.copy(nodes = node.copy(identity = node.identity.copy(
            identityLinks = KnowledgeIdentityLinks(canonical = Some(badnode)))) +: value.snapshot.nodes.tail)),
          value.copy(snapshot = value.snapshot.copy(nodes = node.copy(structure = KnowledgeNodeStructure(
            hierarchy = KnowledgeHierarchy(parent = Some(badnode)))) +: value.snapshot.nodes.tail)),
          value.copy(snapshot = value.snapshot.copy(nodes = node.copy(semantics = node.semantics.copy(
            confidence = KnowledgeConfidenceProfile(Some(Double.NaN)))) +: value.snapshot.nodes.tail)),
          value.copy(snapshot = value.snapshot.copy(evidence = Vector.empty)),
          value.copy(snapshot = value.snapshot.copy(provenance = Vector.empty)),
          value.copy(snapshot = value.snapshot.copy(frames = Vector.empty)),
          value.copy(snapshot = value.snapshot.copy(relationships = value.snapshot.relationships.map(_.copy(targetNodeId = badnode))))
        )
        variants.foreach { candidate =>
          When("the declared materialization boundary admits the candidate")
          val result = KnowledgeMaterializationCodec.encodeC(candidate)
          Then("an explicit structured failure prevents a lossy or dangling result")
          result shouldBe a[Consequence.Failure[?]]
        }
      }
      "reject unknown schemas and undeclared nested fields without silently discarding them" in {
        Given("one ordinary encoded canonical result")
        val session = new Session("schema")
        val text = success(KnowledgeMaterializationCodec.encodeC(materialization(session.identity("selected"))))
        val json = io.circe.parser.parse(text).toOption.get
        val incompatible = Vector(json.mapObject(_.add("schemaVersion", Json.fromString("future"))),
          json.mapObject(_.add("extra", Json.True)),
          json.hcursor.downField("materialization").withFocus(_.mapObject(_.add("extra", Json.True))).top.get)
        incompatible.foreach { input =>
          When("storage reconstruction encounters an incompatible envelope")
          val result = KnowledgeMaterializationCodec.decodeC(input.noSpaces)
          Then("the decoder refuses the incompatible data instead of rewriting it")
          result shouldBe a[Consequence.Failure[?]]
        }
      }
    }
    "use native strict Entity creation and authorization" which {
      "preserve existing identity and exact references on duplicate and source lookup" in {
        Given("one actual Entity provider, registered Detached codec and authenticated owner")
        val session = new Session("strict")
        given ExecutionContext = session.executionContext
        val value = materialization(success(session.repository.reserveIdentity()))
        When("one owning create is followed by a duplicate with changed content")
        val created = success(session.evaluate(session.repository.create(value)))
        val duplicate = session.evaluate(session.repository.create(value.copy(context = context.copy(revision = "changed"))))
        Then("strict create refuses overwrite while owner reads and both trace directions select the first result")
        duplicate shouldBe a[Consequence.Failure[?]]
        success(session.evaluate(session.repository.get(value.id))) shouldBe created
        success(session.evaluate(session.repository.findBySource(source))) shouldBe Vector(value)
        success(session.evaluate(session.repository.findBySource(source.copy(revision = "another")))) shouldBe empty
        success(session.evaluate(session.repository.findByNode(value.snapshot.nodes.head.id))) shouldBe Vector(value)
      }
      "preserve missing, anonymous, foreign-identity and owning factory denial as failures" in {
        Given("a shared provider and separate authenticated, anonymous and denied native contexts")
        val store = DataStore.inMemorySearchable()
        val writer = new Session("authority", store)
        val value = materialization(writer.identity("selected"))
        success(writer.evaluate(writer.repository.create(value)(using writer.executionContext)))
        val anonymous = new Session("authority", store, authenticated = false)
        val foreign = new Session("authority", store, principal = "another")
        val denied = new Session("authority", store, denyFactory = true)
        When("the ordinary UoW is asked to read under those callers or a missing/foreign native identity")
        val results = Vector(
          anonymous.evaluate(anonymous.repository.get(value.id)(using anonymous.executionContext)),
          foreign.evaluate(foreign.repository.get(value.id)(using foreign.executionContext)),
          denied.evaluate(denied.repository.get(value.id)(using denied.executionContext)),
          writer.evaluate(writer.repository.get(writer.identity("missing"))(using writer.executionContext)),
          writer.evaluate(writer.repository.get(new Session("other").identity("selected"))(using writer.executionContext)))
        Then("native authorization and explicit missing/collection errors remain structured; the original survives")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        success(writer.evaluate(writer.repository.get(value.id)(using writer.executionContext))).entity shouldBe value
        success(writer.evaluate(writer.repository.findBySource(source)(using writer.executionContext))) shouldBe Vector(value)
        success(writer.evaluate(writer.repository.findByNode(value.snapshot.nodes.head.id)(using writer.executionContext))) shouldBe Vector(value)
        success(foreign.evaluate(foreign.repository.findBySource(source)(using foreign.executionContext))) shouldBe empty
      }
    }
  }
}
