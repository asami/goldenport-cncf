package org.goldenport.cncf.component

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 30, 2026
 * @version Aug. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryModeBoundarySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _in_phase53_spec =
    afterWord("in spec:component-factory-mode-boundary, example:PM-53-01, rules:PM-53-01, phase:53")

  "ComponentFactory mode boundary" must _in_phase53_spec {
    "keep creation and initialization inputs free of runtime mode and policy carriers" in {
      Given("the current create and initialization factory inputs")

      When("CNCF reflects the concrete inputs passed to Component.Factory hooks")
      val fieldtypes = (
        classOf[ComponentCreate].getDeclaredFields ++ classOf[ComponentInit].getDeclaredFields
      ).map(_.getType.getName).toSet

      Then("the inputs must expose resolved component facts without mode, Web mode, runtime, configuration, or Subsystem policy carriers")
      fieldtypes should not contain "org.goldenport.cncf.subsystem.Subsystem"
      fieldtypes should not contain "org.goldenport.cncf.config.OperationMode"
      fieldtypes should not contain "org.goldenport.cncf.http.WebApplicationMode"
      fieldtypes should not contain "org.goldenport.cncf.subsystem.SubsystemUserMode"
      fieldtypes should not contain "org.goldenport.cncf.context.RuntimeContext"
      fieldtypes should not contain "org.goldenport.configuration.Configuration"
      classOf[ComponentAssemblyContext].getInterfaces.map(_.getName) should not contain "scala.Product"
    }
  }
}
