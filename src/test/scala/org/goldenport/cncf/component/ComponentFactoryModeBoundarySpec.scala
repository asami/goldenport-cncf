package org.goldenport.cncf.component

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 30, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class ComponentFactoryModeBoundarySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ComponentFactory mode boundary" should {
    "keep creation and initialization inputs free of runtime mode and policy carriers" in {
      Given("the current create and initialization factory inputs")

      When("CNCF reflects the concrete inputs passed to Component.Factory hooks")
      val fieldtypes = (
        classOf[ComponentCreate].getDeclaredFields ++ classOf[ComponentInit].getDeclaredFields
      ).map(_.getType.getName).toSet

      Then("the inputs must expose resolved component facts without mode, Web mode, runtime, configuration, or Subsystem policy carriers")
      fieldtypes should contain ("org.goldenport.cncf.subsystem.Subsystem")
      pendingUntilFixed {
        fieldtypes should not contain "org.goldenport.cncf.subsystem.Subsystem"
        fieldtypes should not contain "org.goldenport.cncf.config.OperationMode"
        fieldtypes should not contain "org.goldenport.cncf.http.WebApplicationMode"
        fieldtypes should not contain "org.goldenport.cncf.context.RuntimeContext"
        fieldtypes should not contain "org.goldenport.configuration.Configuration"
      }
    }

    "remove operating-mode access from the Component-facing execution context" in {
      Given("the current CncfCore holder contract")

      When("Component-facing execution-context accessors are inspected")
      val methods = classOf[org.goldenport.cncf.context.ExecutionContext.CncfCore.Holder]
        .getMethods
      val methodnames = methods.map(_.getName).toSet
      val returntypes = methods.map(_.getReturnType.getName).toSet

      Then("runtime posture must be absorbed before Component and ActionCall use")
      methodnames should contain ("operationMode")
      methodnames should contain ("runtime")
      methodnames should contain ("cncfCore")
      pendingUntilFixed {
        methodnames should not contain "operationMode"
        returntypes should not contain "org.goldenport.cncf.context.RuntimeContext"
        returntypes should not contain "org.goldenport.cncf.context.ExecutionContext$CncfCore"
      }
    }
  }
}
