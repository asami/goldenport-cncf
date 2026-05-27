package org.goldenport.cncf.assembly

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.goldenport.protocol.Protocol
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory

/*
 * @since   May. 27, 2026
 * @version May. 27, 2026
 * @author  ASAMI, Tomoharu
 */
class AssemblyReportSpec extends AnyWordSpec with Matchers {
  "AssemblyReport" should {
    "prefer a component development directory over a packaged CAR for the same component" in {
      val dev = _component("TextusKnowledgeEditor", ComponentOrigin.Repository("component-dev-dir"))
      val car = _component("TextusKnowledgeEditor", ComponentOrigin.Repository("component-dir:car:textus-knowledge-editor:0.1.0"))

      val selection = AssemblyReport.selectPreferred(car, dev)

      selection.selected shouldBe dev
      selection.dropped shouldBe Vector(car)
      selection.reason shouldBe "higher-origin-priority:component-dev-dir"
    }
  }

  private def _component(
    name: String,
    origin: ComponentOrigin
  ): Component = {
    val subsystem = TestComponentFactory.emptySubsystem("assembly-report-spec")
    val componentid = ComponentId(name)
    val core = Component.Core.create(
      name,
      componentid,
      ComponentInstanceId.default(componentid),
      Protocol.empty
    )
    new Component() {}.initialize(ComponentInit(subsystem, core, origin))
  }
}
