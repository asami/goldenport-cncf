package org.goldenport.cncf.component

import org.goldenport.cncf.knowledge.KnowledgeSpace
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 17, 2026
 * @version May. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentKnowledgeSpaceSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Component knowledgeSpace" should {
    "be component local like entity aggregate and view spaces" in {
      val first = new Component() {}
      val second = new Component() {}

      first.knowledgeSpace shouldBe a[KnowledgeSpace]
      second.knowledgeSpace shouldBe a[KnowledgeSpace]
      first.knowledgeSpace should not be theSameInstanceAs(second.knowledgeSpace)
      first.knowledgeSpace should not be theSameInstanceAs(first.entitySpace)
      first.knowledgeSpace should not be theSameInstanceAs(first.aggregateSpace)
      first.knowledgeSpace should not be theSameInstanceAs(first.viewSpace)
    }
  }
}
