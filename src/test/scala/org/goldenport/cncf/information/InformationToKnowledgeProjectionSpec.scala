package org.goldenport.cncf.information

import org.goldenport.Consequence
import org.goldenport.cncf.knowledge.{KnowledgeNodeId, KnowledgeTagBinding, KnowledgeWorkingSet}
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 * @version May. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class InformationToKnowledgeProjectionSpec
  extends AnyWordSpec
  with Matchers {

  "InformationToKnowledgeProjection" should {
    "materialize confirmed paper information into KnowledgeSpace without id collapse" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("paper", Vector(Record.data("title" -> "Projection", "authors" -> "Alice"))))
      val recordid = batch.head.id
      _success(space.validateInformation(recordid))
      val item = _success(space.confirmInformation(recordid))

      val snapshot = InformationSpace.materializeInformation(item)
      val workingset = _success(KnowledgeWorkingSet.load(snapshot))
      val nodeid = KnowledgeNodeId(s"information-${item.id.print}")
      val node = workingset.nodeOption(nodeid).getOrElse(fail("missing node"))

      node.id shouldBe nodeid
      node.id.print should not be item.id.print
      node.presentation.defaultLabel shouldBe Some("Projection")
      node.identity.externalIdentifiers.map(_.system) should contain ("cncf.information")
      workingset.counts.frameCount shouldBe 1
      workingset.counts.factCount shouldBe 1
    }

    "materialize Information tag bindings into KnowledgeNode bindings" in {
      val space = new InformationSpace
      val batch = _success(space.registerInformation("book", Vector(Record.data("title" -> "Tagged Projection"))))
      val recordid = batch.head.id
      _success(space.validateInformation(recordid))
      val information = _success(space.confirmInformation(recordid))

      val snapshot = InformationToKnowledgeProjection.materialize(
        information,
        Vector(KnowledgeTagBinding("information", "knowledge/book"))
      )
      val workingset = _success(KnowledgeWorkingSet.load(snapshot))
      val node = workingset.nodeOption(KnowledgeNodeId(s"information-${information.id.print}")).getOrElse(fail("missing materialized node"))

      node.bindings.tagBindings should contain (KnowledgeTagBinding("information", "knowledge/book"))
    }
  }

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.toString)
    }
}
