package org.goldenport.cncf.context

import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExecutionContextResolvedParametersSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "ExecutionContext.resolvedParameters" should {
    "project the live runtime-resolved parameter authority without exposing the runtime context" in {
      Given("an execution context whose internal runtime carries one resolved operation parameter")
      val ctx = ExecutionContext.create()
      val parameters = ResolvedParameters.fromResolvedConfiguration(
        ResolvedConfiguration(
          Configuration(Map(
            "demo.runtime-key" -> ConfigurationValue.StringValue("runtime-value")
          )),
          ConfigurationTrace.empty
        )
      )
      ctx.runtime.setResolvedParameters(parameters)

      When("a caller reads the public resolved-parameters projection")
      val projected = ctx.resolvedParameters

      Then("the projection is the runtime authority and exposes its resolved value")
      projected should be theSameInstanceAs parameters
      projected.get("demo.runtime-key").map(_.value) shouldBe
        Some(ConfigurationValue.StringValue("runtime-value"))
    }
  }
}
