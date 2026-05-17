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
final class KnowledgeSpaceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "KnowledgeSpace" should {
    "start empty and not ready" in {
      val space = new KnowledgeSpace

      space.status.state shouldBe KnowledgeWorkingSetState.NotStarted
      space.counts shouldBe KnowledgeWorkingSetCounts()
      space.nodeOption(KnowledgeNodeId("missing")) shouldBe None
    }

    "replace and clear the working set" in {
      Given("a knowledge space")
      val space = new KnowledgeSpace
      val nodea = KnowledgeNode(KnowledgeNodeId("node-a"), "entity")
      val nodeb = KnowledgeNode(KnowledgeNodeId("node-b"), "concept")
      val relationship = KnowledgeRelationship(
        KnowledgeRelationshipId("rel-1"),
        "related",
        nodea.id,
        nodeb.id
      )
      val snapshot = KnowledgeWorkingSetSnapshot(Vector(nodea, nodeb), Vector(relationship))

      When("replacing the snapshot")
      val status = _success(space.replace(snapshot))

      Then("lookup methods use the loaded working set")
      status.isReady shouldBe true
      space.counts.nodeCount shouldBe 2
      space.nodeOption(nodea.id) shouldBe Some(nodea)
      space.relationshipsFrom(nodea.id) shouldBe Vector(relationship)

      When("clearing the space")
      space.clear()

      Then("the space returns to an empty state")
      space.status.state shouldBe KnowledgeWorkingSetState.NotStarted
      space.counts shouldBe KnowledgeWorkingSetCounts()
      space.nodeOption(nodea.id) shouldBe None
    }

    "report failed status when a replacement snapshot is invalid" in {
      Given("a knowledge space with a ready working set")
      val space = new KnowledgeSpace
      val node = KnowledgeNode(KnowledgeNodeId("node-a"), "entity")
      _success(space.replace(KnowledgeWorkingSetSnapshot(Vector(node))))

      When("replacing the working set with an invalid snapshot")
      val broken = KnowledgeWorkingSetSnapshot(Vector(node, node))
      val result = space.replace(broken)

      Then("the failed reload is visible while the previous data remains available")
      result shouldBe a[Consequence.Failure[_]]
      space.status.state shouldBe KnowledgeWorkingSetState.Failed
      space.status.error should not be empty
      space.counts.nodeCount shouldBe 1
      space.nodeOption(node.id) shouldBe Some(node)
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
