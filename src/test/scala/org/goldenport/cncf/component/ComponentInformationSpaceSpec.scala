package org.goldenport.cncf.component

import org.goldenport.cncf.information.InformationSpace
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentInformationSpaceSpec
  extends AnyWordSpec
  with Matchers {

  "Component informationSpace" should {
    "be component local like entity aggregate view and knowledge spaces" in {
      val first = new Component() {}
      val second = new Component() {}

      first.informationSpace shouldBe a[InformationSpace]
      second.informationSpace shouldBe a[InformationSpace]
      first.informationSpace should not be theSameInstanceAs(second.informationSpace)
      first.informationSpace should not be theSameInstanceAs(first.entitySpace)
      first.informationSpace should not be theSameInstanceAs(first.aggregateSpace)
      first.informationSpace should not be theSameInstanceAs(first.viewSpace)
      first.informationSpace should not be theSameInstanceAs(first.knowledgeSpace)
    }
  }
}
