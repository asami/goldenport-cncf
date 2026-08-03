package org.goldenport.cncf.config

import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.Subsystem
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Executable GCF-07 closure boundary: runtime projections are values, not binding internals. */
final class Phase55RuntimeConfigurationBoundarySpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Phase 55 runtime configuration boundary" should {
    "E1 publish no raw generic binding authority through Subsystem or ExecutionContext" in {
      Given("the runtime-facing Subsystem and execution context APIs")
      val forbidden = Set(
        "org.goldenport.configuration.ConfigurationBinding",
        "org.goldenport.configuration.ConfigurationBindingCollection",
        "org.goldenport.configuration.ConfigurationBindingCandidates",
        "org.goldenport.cncf.config.CncfConfigurationTarget",
        "org.goldenport.configuration.ConfigurationTrace"
      )

      When("their Component-facing public method signatures are inspected")
      val subsystemMethods = classOf[Subsystem].getMethods.toVector
      val contextMethods = classOf[ExecutionContext].getMethods.toVector
      val exposed = (subsystemMethods ++ contextMethods).map(_.getReturnType.getName).toSet

      Then("only value-level projections remain and the raw collection getter is absent")
      subsystemMethods.exists(method => method.getName == "runtimeConfigurationBindings" && method.getParameterCount == 0) shouldBe false
      exposed.intersect(forbidden) shouldBe empty
    }
  }
}
