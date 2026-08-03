package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.{Subsystem, SystemNode}
import org.goldenport.configuration.{Configuration, ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationDocument, ConfigurationOrigin, ConfigurationSourceAdmission, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeOperationSecurityPolicySpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-runtime-operation-web-authorization-policy, example:E2, rules:GCF09L-C2,C3, phase:55, slice:GCF-09L"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-runtime-operation-web-authorization-policy, example:E3, rules:GCF09L-C3,C4, phase:55, slice:GCF-09L"
  )

  "Runtime operation security policy" should {
    "use only admitted typed bindings" which {
      "E2 preserve operation-mode aliases, booleans, role token behavior, and defaults" must _e1 {
        "when a final Subsystem binding collection contains a partial policy" in {
          Given("an admitted operation mode, native Boolean, and role-token values")
          val bindings = _take(_collection(Vector(
            CncfConfigurationParameterCatalog.OPERATION_MODE_KEY -> ConfigurationValue.StringValue("dev"),
            CncfConfigurationParameterCatalog.WEB_DEMO_ASSIST_ENABLED_KEY -> ConfigurationValue.BooleanValue(true),
            CncfConfigurationParameterCatalog.WEB_PRODUCTION_ADMIN_SYSTEM_ROLES_KEY -> ConfigurationValue.StringValue(" operator, system_admin |operator ")
          )))

          When("the value-only policy is projected")
          val policy = _take(RuntimeOperationSecurityPolicy.from(bindings))

          Then("admitted values and the legacy defaults are selected without raw configuration")
          policy.operationMode shouldBe OperationMode.Develop
          policy.webDemoAssistEnabled shouldBe true
          policy.webDevelopAnonymousAdmin shouldBe RuntimeConfig.defaultWebDevelopAnonymousAdmin
          policy.webProductionAdminEnabled shouldBe RuntimeConfig.defaultWebProductionAdminEnabled
          policy.webProductionAdminSystemRoles shouldBe Vector("operator", "system_admin", "operator")
          policy.webProductionAdminComponentRoles shouldBe RuntimeConfig.defaultWebProductionAdminComponentRoles
          policy.webProductionAdminJobsRoles shouldBe RuntimeConfig.defaultWebProductionAdminJobsRoles
        }
      }

      "E3 make final admission authoritative and fail closed before admission" must _e2 {
        "when raw configuration conflicts with an admitted policy or no bindings exist" in {
          Given("one Subsystem with a conflicting raw production value and one without final bindings")
          val raw = ResolvedConfiguration(
            Configuration(Map(
              RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
              RuntimeConfig.WEB_DEMO_ASSIST_ENABLED_KEY -> ConfigurationValue.StringValue("false")
            )),
            ConfigurationTrace.empty
          )
          val admitted = new Subsystem("operation-security-admitted", configuration = raw, systemnode = SystemNode.create())
          val unadmitted = new Subsystem("operation-security-unadmitted", configuration = raw, systemnode = SystemNode.create())
          val bindings = _take(_collection(Vector(
            CncfConfigurationParameterCatalog.OPERATION_MODE_KEY -> ConfigurationValue.StringValue("demo"),
            CncfConfigurationParameterCatalog.WEB_DEMO_ASSIST_ENABLED_KEY -> ConfigurationValue.StringValue("true")
          )))

          When("only the admitted Subsystem requests the policy")
          try {
            _take(admitted.admitRuntimeConfigurationBindingsC(bindings))
            val selected = admitted.runtimeOperationSecurityPolicyC
            val absent = unadmitted.runtimeOperationSecurityPolicyC

            Then("the typed policy wins and no raw fallback is available")
            selected.toOption.map(_.operationMode) shouldBe Some(OperationMode.Demo)
            selected.toOption.map(_.webDemoAssistEnabled) shouldBe Some(true)
            absent.isSuccess shouldBe false
            absent.display should include("operation security policy bindings have not been admitted")
          } finally {
            Subsystem.shutdownOwned(admitted)
            Subsystem.shutdownOwned(unadmitted)
          }
        }
      }
    }
  }

  private def _collection(
    entries: Vector[(String, ConfigurationValue)]
  ): Consequence[ConfigurationBindingCollection[CncfConfigurationTarget]] =
    for {
      candidates <- CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
        CncfConfigurationDocumentBatch(
          new CncfConfigurationDocumentLocation.SubsystemInstance(_target),
          _take(ConfigurationSourceAdmission.create(
            ConfigurationOrigin.Home,
            "home",
            "runtime-operation-security-policy-spec",
            10,
            "runtime-operation-security-policy-spec",
            () => Consequence.success(ConfigurationDocument.Object(entries.map { case (key, value) =>
              ConfigurationDocument.Field(key, ConfigurationDocument.Scalar(value))
            }))
          ))
        )
      ))
      context <- CncfConfigurationResolutionContext.forSubsystem(_identity)
      collection <- ConfigurationBindingResolver.resolve(candidates, context.generic)
    } yield collection

  private def _identity: SubsystemInstanceId =
    _take(SubsystemInstanceId.create("platform", "default"))

  private def _target: CncfConfigurationTarget.SubsystemInstance =
    _take(CncfConfigurationTarget.SubsystemInstance.create(_identity))

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
