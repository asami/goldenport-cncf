package org.goldenport.cncf.testutil

import java.nio.file.Path

import org.goldenport.cncf.config.{OperationMode, RuntimeConfig, RuntimeOperationSecurityPolicy}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.servicecontainer.{FakeServiceContainerGateway, ServiceContainerRegistry, ServiceContainerRuntime}
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemDescriptor, GenericSubsystemLocalSubjectBinding, GenericSubsystemSecurityBinding, Subsystem, SubsystemExecutionProfile, SubsystemUserMode}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  4, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeBindingAdmissionFixtureSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with TableDrivenPropertyChecks {
  "Runtime binding admission fixture" should {
    "admit typed operation-security policy" which {
      "admit an empty typed binding collection for a default test subsystem" in {
        Given("a default test subsystem")

        When("the fixture factory creates it")
        val subsystem = RuntimeBindingAdmissionFixture.default(Some("command"))

        Then("the default operation-security policy is admitted")
        subsystem.runtimeOperationSecurityPolicyC.toOption shouldBe Some(
          RuntimeOperationSecurityPolicy.default
        )
        subsystem.executionProfileC.toOption shouldBe Some(
          SubsystemExecutionProfile.ControlledTest
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
    }

    "preserve execution-profile and user-mode evidence" which {
      "preserve fixed-user descriptor wiring when controlled fixture execution is enabled" in {
        Given("a standalone subsystem with an explicit local subject")
        val configuration = _configuration(Map(
          SubsystemUserMode.CONFIGURATION_KEY -> ConfigurationValue.StringValue("standalone")
        ))
        val subsystem = new Subsystem(
          name = "fixed-user-fixture",
          configuration = configuration
        ).withDescriptor(GenericSubsystemDescriptor(
          path = Path.of("build.sbt").toAbsolutePath,
          subsystemName = "fixed-user-fixture",
          security = Some(GenericSubsystemSecurityBinding(authentication = Some(
            GenericSubsystemAuthenticationBinding(
              localSubject = Some(GenericSubsystemLocalSubjectBinding("fixture-user"))
            )
          )))
        ))

        When("the test fixture admits runtime bindings")
        RuntimeBindingAdmissionFixture.admit(subsystem)

        Then("the descriptor remains authoritative over controlled-test fallback")
        subsystem.executionProfileC.toOption shouldBe Some(
          SubsystemExecutionProfile.Fixed
        )
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
    }

    "project typed Web execution policy" which {
      "project configured Web policy values through typed bindings" in {
        Given("boolean and role-vector runtime Web configuration")
        val configuration = _configuration(Map(
          RuntimeConfig.webDevelopAnonymousAdminKey -> ConfigurationValue.BooleanValue(true),
          RuntimeConfig.WEB_DEMO_ASSIST_ENABLED_KEY -> ConfigurationValue.BooleanValue(true),
          RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.BooleanValue(true),
          RuntimeConfig.webProductionAdminSystemRolesKey -> _roles("system-admin"),
          RuntimeConfig.webProductionAdminComponentRolesKey -> _roles("component-admin"),
          RuntimeConfig.webProductionAdminJobsRolesKey -> _roles("jobs-admin"),
          org.goldenport.cncf.http.WebExecutionResolutionPolicy.LOCALE_KEY ->
            ConfigurationValue.StringValue("ja-JP")
        ))

        When("the configured fixture is explicitly admitted")
        val subsystem = RuntimeBindingAdmissionFixture.default(
          Some("server"),
          configuration
        )
        val policy = subsystem.runtimeOperationSecurityPolicyC.toOption.getOrElse(fail("missing policy"))

        Then("the policy contains only the typed Web projections")
        policy.webDevelopAnonymousAdmin shouldBe true
        policy.webDemoAssistEnabled shouldBe true
        policy.webProductionAdminEnabled shouldBe true
        policy.webProductionAdminSystemRoles shouldBe Vector("system-admin")
        policy.webProductionAdminComponentRoles shouldBe Vector("component-admin")
        policy.webProductionAdminJobsRoles shouldBe Vector("jobs-admin")
        subsystem.runtimeWebExecutionResolutionPolicyC.toOption
          .flatMap(_.applicationLocale)
          .map(_.toLanguageTag) shouldBe Some("ja-JP")
      }
    }

    "preserve admission identity and idempotency" which {
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

    "provide explicit downstream service-container runtime test support" which {
      "install a runtime visible through the public projection and remain idempotent" in {
        Given("a subsystem and an in-memory fake service-container runtime")
        given context: ExecutionContext = ExecutionContext.create()
        val subsystem = new Subsystem(
          name = "runtime-binding-admission-fixture-service-container",
          configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty)
        )
        val runtime = ServiceContainerRuntime.create(
          ServiceContainerRegistry.inMemory(),
          FakeServiceContainerGateway.create()
        )

        When("the explicit fixture installs the same runtime twice")
        val first = RuntimeBindingAdmissionFixture.withServiceContainerRuntime(subsystem, runtime)
        val second = RuntimeBindingAdmissionFixture.withServiceContainerRuntime(subsystem, runtime)
        val firstprojection = subsystem.serviceContainerRuntime
        val secondprojection = subsystem.serviceContainerRuntime

        Then("both calls retain the subsystem and expose the installed runtime")
        first shouldBe theSameInstanceAs(subsystem)
        second shouldBe theSameInstanceAs(subsystem)
        firstprojection shouldBe defined
        secondprojection shouldBe defined
        subsystem.shutdown()
      }
    }

  }

  private def _configuration(
    values: Map[String, ConfigurationValue]
  ): ResolvedConfiguration =
    ResolvedConfiguration(Configuration(values), ConfigurationTrace.empty)

  private def _roles(value: String): ConfigurationValue =
    ConfigurationValue.StringValue(value)
}
