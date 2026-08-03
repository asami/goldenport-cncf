package org.goldenport.cncf.http

import org.goldenport.cncf.config.{OperationMode, RuntimeConfig, RuntimeOperationSecurityPolicy}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class HttpRuntimeBindingAdmissionFixtureSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "HTTP runtime binding admission fixture" should {
    "admit an empty typed binding collection for default test subsystems" in {
      Given("a default HTTP test subsystem")

      When("the fixture factory creates it")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(Some("server"))

      Then("the default operation-security policy is admitted")
      subsystem.runtimeOperationSecurityPolicyC.toOption shouldBe Some(
        RuntimeOperationSecurityPolicy.default
      )
    }

    "resolve configured operation mode through typed bindings before server construction" in {
      Given("an HTTP test subsystem with a production operation-mode configuration")

      When("the fixture factory creates it")
      val subsystem = HttpRuntimeBindingAdmissionFixture.default(
        Some("server"),
        ResolvedConfiguration(
          Configuration(Map(
            RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production")
          )),
          ConfigurationTrace.empty
        )
      )

      Then("the admitted policy carries the configured operation mode")
      subsystem.runtimeOperationSecurityPolicyC.toOption.map(_.operationMode) shouldBe Some(
        OperationMode.Production
      )
    }
  }
}
