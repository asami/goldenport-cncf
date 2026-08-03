package org.goldenport.cncf.config

import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionProfileActivation, ExecutionProfileMode, ExecutionProfileResolver, ExecutionTimeMode}
import org.goldenport.configuration.{ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationDocument, ConfigurationOrigin, ConfigurationSourceAdmission, ConfigurationValue}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuntimeExecutionProfileConfigurationSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-runtime-execution-profile-configuration, example:E2, rules:GCF09M-C2,C3,C4, phase:55, slice:GCF-09M"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-runtime-execution-profile-configuration, example:E3, rules:GCF09M-C2,C4, phase:55, slice:GCF-09M"
  )

  "Runtime execution-profile configuration" should {
    "project only admitted typed bindings" which {
      "E2 preserve defaults, aliases, virtual-clock compatibility, and confidential environment values" must _e1 {
        "when a partial typed Subsystem collection is resolved" in {
          Given("a seeded profile with a decode-only seed alias, virtual clock, and admitted environment value")
          val bindings = _take(_collection(Vector(
            CncfConfigurationParameterCatalog.EXECUTION_PROFILE_KEY -> ConfigurationValue.StringValue("seeded"),
            "cncf.runtime.execution.random.seed" -> ConfigurationValue.StringValue("private-seed-value"),
            CncfConfigurationParameterCatalog.CLOCK_VIRTUAL_START_AT_KEY -> ConfigurationValue.StringValue("2026-08-04T00:00:00Z"),
            CncfConfigurationParameterCatalog.EXECUTION_ENVIRONMENT_ALLOW_KEY -> ConfigurationValue.StringValue("TOKEN"),
            CncfConfigurationParameterCatalog.EXECUTION_ENVIRONMENT_VALUES_KEY -> ConfigurationValue.ObjectValue(Map("TOKEN" -> ConfigurationValue.StringValue("private")))
          )))

          When("the value-only projection and resolver are invoked")
          val projected = _take(RuntimeExecutionProfileConfiguration.from(bindings))
          val profile = _take(ExecutionProfileResolver.resolve(projected, ExecutionProfileActivation.InProcessSpec, Map.empty))

          Then("defaulted dimensions and admitted values preserve execution semantics without raw configuration")
          projected.mode shouldBe ExecutionProfileMode.Seeded
          projected.timeMode shouldBe ExecutionTimeMode.Offset
          projected.virtualStartAt shouldBe Some(Instant.parse("2026-08-04T00:00:00Z"))
          projected.environmentValues shouldBe Map("TOKEN" -> "private")
          profile.environmentAssumptions.environmentVariables shouldBe Map("TOKEN" -> "private")
          profile.toString should not include "private-seed-value"
          profile.environmentAssumptions.toString should not include "private"
        }
      }

      "E3 reject cross-field combinations without raw fallback" must _e2 {
        "when controlled activation, start-time, and environment declarations are invalid" in {
          Given("typed collections that individually decode but violate profile rules")
          val controlled = _take(RuntimeExecutionProfileConfiguration.from(_take(_collection(Vector(
            CncfConfigurationParameterCatalog.EXECUTION_PROFILE_KEY -> ConfigurationValue.StringValue("controlled"),
            CncfConfigurationParameterCatalog.EXECUTION_KEY -> ConfigurationValue.StringValue("run"),
            CncfConfigurationParameterCatalog.EXECUTION_RANDOM_SEED_KEY -> ConfigurationValue.StringValue("seed"),
            CncfConfigurationParameterCatalog.EXECUTION_TIME_START_AT_KEY -> ConfigurationValue.StringValue("2026-08-04T00:00:00Z")
          )))))
          val conflicting = _take(RuntimeExecutionProfileConfiguration.from(_take(_collection(Vector(
            CncfConfigurationParameterCatalog.CLOCK_VIRTUAL_START_AT_KEY -> ConfigurationValue.StringValue("2026-08-04T00:00:00Z"),
            CncfConfigurationParameterCatalog.EXECUTION_TIME_START_AT_KEY -> ConfigurationValue.StringValue("2026-08-05T00:00:00Z")
          )))))
          val environment = _take(RuntimeExecutionProfileConfiguration.from(_take(_collection(Vector(
            CncfConfigurationParameterCatalog.EXECUTION_ENVIRONMENT_VALUES_KEY -> ConfigurationValue.ObjectValue(Map("TOKEN" -> ConfigurationValue.StringValue("private")))
          )))))
          val offsetwithoutstart = _take(RuntimeExecutionProfileConfiguration.from(_take(_collection(Vector(
            CncfConfigurationParameterCatalog.EXECUTION_TIME_MODE_KEY -> ConfigurationValue.StringValue("offset")
          )))))
          val manualwithoutstart = _take(RuntimeExecutionProfileConfiguration.from(_take(_collection(Vector(
            CncfConfigurationParameterCatalog.EXECUTION_TIME_MODE_KEY -> ConfigurationValue.StringValue("manual")
          )))))
          val systemwithstart = _take(RuntimeExecutionProfileConfiguration.from(_take(_collection(Vector(
            CncfConfigurationParameterCatalog.EXECUTION_TIME_START_AT_KEY -> ConfigurationValue.StringValue("2026-08-04T00:00:00Z")
          )))))
          val manualvirtual = _take(RuntimeExecutionProfileConfiguration.from(_take(_collection(Vector(
            CncfConfigurationParameterCatalog.EXECUTION_PROFILE_KEY -> ConfigurationValue.StringValue("controlled"),
            CncfConfigurationParameterCatalog.EXECUTION_KEY -> ConfigurationValue.StringValue("run"),
            CncfConfigurationParameterCatalog.EXECUTION_RANDOM_SEED_KEY -> ConfigurationValue.StringValue("seed"),
            CncfConfigurationParameterCatalog.EXECUTION_TIME_MODE_KEY -> ConfigurationValue.StringValue("manual"),
            CncfConfigurationParameterCatalog.CLOCK_VIRTUAL_START_AT_KEY -> ConfigurationValue.StringValue("2026-08-04T00:00:00Z")
          )))))

          When("the typed resolver receives each projection")
          val unapproved = ExecutionProfileResolver.resolve(controlled, ExecutionProfileActivation.Ordinary, Map.empty)
          val conflict = ExecutionProfileResolver.resolve(conflicting, ExecutionProfileActivation.InProcessSpec, Map.empty)
          val undeclared = ExecutionProfileResolver.resolve(environment, ExecutionProfileActivation.InProcessSpec, Map.empty)
          val missingstart = ExecutionProfileResolver.resolve(offsetwithoutstart, ExecutionProfileActivation.InProcessSpec, Map.empty)
          val manualmissingstart = ExecutionProfileResolver.resolve(manualwithoutstart, ExecutionProfileActivation.InProcessSpec, Map.empty)
          val ignoredstart = ExecutionProfileResolver.resolve(systemwithstart, ExecutionProfileActivation.InProcessSpec, Map.empty)
          val incompatiblevirtual = ExecutionProfileResolver.resolve(manualvirtual, ExecutionProfileActivation.InProcessSpec, Map.empty)

          Then("all failures are structured and cannot revert to raw values or defaults")
          unapproved.isSuccess shouldBe false
          unapproved.display should include("controlled execution profile requires")
          conflict.isSuccess shouldBe false
          conflict.display should include(CncfConfigurationParameterCatalog.EXECUTION_TIME_START_AT_KEY)
          undeclared.isSuccess shouldBe false
          undeclared.display should include(CncfConfigurationParameterCatalog.EXECUTION_ENVIRONMENT_VALUES_KEY)
          missingstart.isSuccess shouldBe false
          missingstart.display should include("is required for offset time mode")
          manualmissingstart.isSuccess shouldBe false
          manualmissingstart.display should include("is required for manual time mode")
          ignoredstart.isSuccess shouldBe false
          ignoredstart.display should include("requires offset or manual time mode")
          incompatiblevirtual.isSuccess shouldBe false
          incompatiblevirtual.display should include(CncfConfigurationParameterCatalog.CLOCK_VIRTUAL_START_AT_KEY)
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
            "runtime-execution-profile-configuration-spec",
            10,
            "runtime-execution-profile-configuration-spec",
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
