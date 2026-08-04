package org.goldenport.cncf.testutil

import org.goldenport.cncf.config.{OperationMode, RuntimeConfig, RuntimeOperationSecurityPolicy}
import org.goldenport.cncf.subsystem.SubsystemUserMode
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeBindingAdmissionFixtureSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with TableDrivenPropertyChecks {
  "Runtime binding admission fixture" should {
    "admit an empty typed binding collection for a default test subsystem" in {
      Given("a default test subsystem")

      When("the fixture factory creates it")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))

      Then("the default operation-security policy is admitted")
      subsystem.runtimeOperationSecurityPolicyC.toOption shouldBe Some(
        RuntimeOperationSecurityPolicy.default
      )
    }

    "resolve a configured production operation mode through typed bindings" in {
      Given("a test subsystem with a production operation-mode configuration")

      When("the fixture factory creates it")
      val subsystem = RuntimeBindingAdmissionFixture.default(
        Some("command"),
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

    "resolve production operation mode through each supported fixture alias family" in {
      Given("the supported runtime-operation configuration aliases")
      val aliases = Table(
        "key",
        s"textus.runtime.${RuntimeConfig.operationModeKey.stripPrefix("textus.")}",
        s"cncf.${RuntimeConfig.operationModeKey.stripPrefix("textus.")}",
        s"cncf.runtime.${RuntimeConfig.operationModeKey.stripPrefix("textus.")}"
      )

      When("each alias is admitted through typed runtime bindings")
      val policies = aliases.iterator.map { key =>
        RuntimeBindingAdmissionFixture.default(
          Some("command"),
          _configuration(Map(key -> ConfigurationValue.StringValue("production")))
        ).runtimeOperationSecurityPolicyC.toOption
      }.toVector

      Then("every alias projects the production operation policy")
      policies shouldBe Vector.fill(policies.size)(Some(
        RuntimeOperationSecurityPolicy.default.copy(operationMode = OperationMode.Production)
      ))
    }

    "resolve configured subsystem user modes through controlled fixture evidence" in {
      Given("canonical and catalog-alias user-mode configuration")
      val configurations = Table(
        ("key", "value", "expected"),
        (SubsystemUserMode.CONFIGURATION_KEY, "standalone", SubsystemUserMode.Standalone),
        ("cncf.subsystem.user-mode", "standalone", SubsystemUserMode.Standalone)
      )

      When("controlled test subsystems are explicitly admitted")
      val modes = configurations.iterator.map { case (key, value, _) =>
        TestComponentFactory.admittedSubsystemWithConfig(Map(
          key -> ConfigurationValue.StringValue(value)
        )).subsystemUserModeC.toOption.map(_.mode)
      }.toVector

      Then("each admitted subsystem exposes its configured semantic mode")
      modes shouldBe configurations.iterator.map { case (_, _, expected) => Some(expected) }.toVector
    }

    "project configured Web policy values through typed bindings" in {
      Given("boolean and role-vector runtime Web configuration")
      val configuration = _configuration(Map(
        RuntimeConfig.webDevelopAnonymousAdminKey -> ConfigurationValue.BooleanValue(true),
        RuntimeConfig.WEB_DEMO_ASSIST_ENABLED_KEY -> ConfigurationValue.BooleanValue(true),
        RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.BooleanValue(true),
        RuntimeConfig.webProductionAdminSystemRolesKey -> _roles("system-admin"),
        RuntimeConfig.webProductionAdminComponentRolesKey -> _roles("component-admin"),
        RuntimeConfig.webProductionAdminJobsRolesKey -> _roles("jobs-admin")
      ))

      When("the configured fixture is explicitly admitted")
      val policy = RuntimeBindingAdmissionFixture.default(
        Some("server"),
        configuration
      ).runtimeOperationSecurityPolicyC.toOption.getOrElse(fail("missing policy"))

      Then("the policy contains only the typed Web projections")
      policy.webDevelopAnonymousAdmin shouldBe true
      policy.webDemoAssistEnabled shouldBe true
      policy.webProductionAdminEnabled shouldBe true
      policy.webProductionAdminSystemRoles shouldBe Vector("system-admin")
      policy.webProductionAdminComponentRoles shouldBe Vector("component-admin")
      policy.webProductionAdminJobsRoles shouldBe Vector("jobs-admin")
    }

    "reject an invalid typed operation-mode value with its catalog diagnostic" in {
      Given("a non-string operation-mode configuration value")
      val configuration = _configuration(Map(
        RuntimeConfig.operationModeKey -> ConfigurationValue.BooleanValue(true)
      ))

      When("the fixture attempts explicit typed admission")
      val exception = intercept[IllegalStateException] {
        RuntimeBindingAdmissionFixture.default(Some("command"), configuration)
      }

      Then("the typed catalog diagnostic identifies the required string value")
      exception.getMessage should include ("textus.operation-mode requires a string")
    }

    "return the same configured subsystem after explicit admission" in {
      Given("a raw configured controlled test subsystem")
      val subsystem = TestComponentFactory.emptySubsystem(
        configuration = _configuration(Map(
          RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production")
        ))
      )

      When("the generic fixture explicitly admits it")
      val admitted = RuntimeBindingAdmissionFixture.admit(subsystem)

      Then("the original instance carries the configured typed policy")
      admitted shouldBe theSameInstanceAs(subsystem)
      admitted.runtimeOperationSecurityPolicyC.toOption.map(_.operationMode) shouldBe Some(
        OperationMode.Production
      )
    }

    "leave an already-admitted fixture unchanged" in {
      Given("an admitted test subsystem")
      val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))
      val policy = subsystem.runtimeOperationSecurityPolicyC.toOption

      When("the fixture is admitted again")
      val admitted = RuntimeBindingAdmissionFixture.admit(subsystem)

      Then("the same subsystem and policy are retained")
      admitted shouldBe theSameInstanceAs(subsystem)
      admitted.runtimeOperationSecurityPolicyC.toOption shouldBe policy
    }
  }

  private def _configuration(
    values: Map[String, ConfigurationValue]
  ): ResolvedConfiguration =
    ResolvedConfiguration(Configuration(values), ConfigurationTrace.empty)

  private def _roles(value: String): ConfigurationValue =
    ConfigurationValue.StringValue(value)
}
