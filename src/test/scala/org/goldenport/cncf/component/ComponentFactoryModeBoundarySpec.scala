package org.goldenport.cncf.component

import java.nio.file.{Files, Path}

import org.goldenport.cncf.context.ExecutionContext
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 30, 2026
 * @version Aug.  1, 2026
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
      fieldtypes should not contain "org.goldenport.cncf.subsystem.Subsystem"
      fieldtypes should not contain "org.goldenport.cncf.config.OperationMode"
      fieldtypes should not contain "org.goldenport.cncf.http.WebApplicationMode"
      fieldtypes should not contain "org.goldenport.cncf.context.RuntimeContext"
      fieldtypes should not contain "org.goldenport.configuration.Configuration"
      classOf[ComponentAssemblyContext].getInterfaces.map(_.getName) should not contain "scala.Product"
    }

    "exclude the dormant Component mode/configuration authority from the public source and factory inputs" in {
      Given("the permanent Component API and its Factory hook signatures")
      val source = Files.readString(
        Path.of("src/main/scala/org/goldenport/cncf/component/Component.scala")
      )
      val factoryparametertypes = classOf[Component.Factory].getDeclaredMethods
        .flatMap(_.getParameterTypes)
        .map(_.getName)
        .toSet

      When("the legacy Component configuration authority is inspected")

      Then("Component code cannot expose the removed mode key, raw resolved configuration, or CLI run-mode input")
      source should not include "final case class Config("
      source should not include "cncf.component.mode"
      source should not include "org.goldenport.configuration.ResolvedConfiguration"
      source should not include "org.goldenport.cncf.cli.RunMode"
      factoryparametertypes should not contain "org.goldenport.configuration.ResolvedConfiguration"
      factoryparametertypes should not contain "org.goldenport.cncf.cli.RunMode"
      factoryparametertypes should not contain "org.goldenport.cncf.config.OperationMode"
      factoryparametertypes should not contain "org.goldenport.cncf.http.WebApplicationMode"
      factoryparametertypes should not contain "org.goldenport.cncf.subsystem.SubsystemExecutionProfile"
    }

    "keep runtime carriers outside the Component-facing Scala execution-context API" in {
      Given("the ExecutionContext source-level access contract")

      When("the public component boundary declarations are inspected")
      val source = Files.readString(
        Path.of("src/main/scala/org/goldenport/cncf/context/ExecutionContext.scala")
      )

      Then("runtime posture must be absorbed before Component and ActionCall use")
      source should include ("private[cncf] def cncfCore")
      source should include ("private[cncf] def runtime")
      source should include ("private[cncf] def operationMode")
      classOf[ExecutionContext.Instance].getInterfaces.map(_.getName) should not contain "scala.Product"
    }
  }
}
