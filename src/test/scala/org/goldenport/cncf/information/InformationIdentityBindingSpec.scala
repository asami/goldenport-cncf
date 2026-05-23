package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.knowledge.{ExternalKnowledgeIdentifier, KnowledgeEntityBinding, KnowledgeNodeId, RdfNodeName}
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 * @version May. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationIdentityBindingSpec
  extends AnyWordSpec
  with Matchers {

  "Information identity binding" should {
    "keep Information RDF Entity and Knowledge ids separate" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Identity", "authors" -> "Alice"))))
      val recordid = batch.head.id
      val binding = InformationIdentityBinding(
        InformationIdentityBindingId("pending"),
        rdfSubject = Some(RdfNodeName("https://example.org/paper/identity")),
        externalIdentifiers = Vector(ExternalKnowledgeIdentifier("doi", "10.123/example")),
        entityBindings = Vector(KnowledgeEntityBinding("paper", "paper-1")),
        knowledgeNodeId = Some(KnowledgeNodeId("node-paper-1")),
        authority = Some("resolver"),
        confidence = Some(0.9)
      )

      val candidate = _success(space.addResolutionCandidate(recordid, "title", "Identity", binding, Some(0.9), Some("exact title match")))
      val selected = _success(space.selectResolutionCandidate(candidate.id))
      _success(space.validateInformationRecord(recordid))
      val item = _success(space.confirmInformationRecord(recordid))
      val confirmed = space.snapshot.identityBindings.find(_.informationItemId.contains(item.id)).getOrElse(fail("missing binding"))

      selected.selected shouldBe true
      confirmed.status shouldBe InformationBindingStatus.Confirmed
      confirmed.rdfSubject.map(_.print) shouldBe Some("https://example.org/paper/identity")
      confirmed.entityBindings shouldBe Vector(KnowledgeEntityBinding("paper", "paper-1"))
      confirmed.knowledgeNodeId shouldBe Some(KnowledgeNodeId("node-paper-1"))
      confirmed.informationItemId should not be confirmed.knowledgeNodeId
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
