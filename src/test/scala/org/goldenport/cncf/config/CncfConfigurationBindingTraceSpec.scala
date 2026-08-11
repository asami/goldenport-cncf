package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.configuration.{CanonicalParameterId, ConfigurationBinding, ConfigurationBindingCandidate, ConfigurationBindingCollection, ConfigurationBindingTrace, ConfigurationOrigin, ConfigurationParameter, ConfigurationProvenance, ConfigurationValue, ConfigurationValueCodec}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationBindingTraceSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-cncf-binding-trace, example:E1, rules:GCF06-C1,C2, phase:55, slice:GCF-06-I"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-cncf-binding-trace, example:E2, rules:GCF06-C1,C2, phase:55, slice:GCF-06-I"
  )

  "CNCF configuration binding trace" should {
    "preserve CNCF targets structurally in the generic projection" which {
      "E1 explain the qualified ComponentInstance without a catalog or target codec" must _e1 {
        "when one resolved CNCF binding is projected" in {
          Given("one exact parameter witness and one containing-Subsystem-qualified ComponentInstance")
          val parameter = _parameter
          val subsystem = _take(SubsystemInstanceId.default("orders"))
          val component = ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("catalog"), "default")
          val target = _take(CncfConfigurationTarget.ComponentInstance.create(subsystem, component))
          val binding = _take(ConfigurationBinding.initial(_candidate(parameter, target, "production")))

          When("the generic collection produces an explanation")
          val explanation = _take(_take(ConfigurationBindingTrace.from(_take(ConfigurationBindingCollection.from(Vector(binding))))).explain(parameter)).get

          Then("the original CNCF target and typed encoded value are retained structurally")
          explanation.effective.target shouldBe target
          explanation.effective.value shouldBe org.goldenport.configuration.ConfigurationBindingTraceValue.Visible(ConfigurationValue.StringValue("production"))
          explanation.parameterId.value shouldBe "textus.application.mode"
        }
      }

      "E2 preserve every generated qualified ComponentInstance structurally" must _e2 {
        "when valid Subsystem and ComponentInstance labels form a generic projection" in {
          Given("valid qualified CNCF target identity labels and one exact parameter witness")
          val parameter = _parameter

          When("each generated identity becomes a resolved binding trace")
          val property = Prop.forAll(
            Gen.oneOf("orders", "billing", "inventory"),
            Gen.oneOf("default", "blue", "green")
          ) { (subsystemname, instancename) =>
            val subsystem = _take(SubsystemInstanceId.default(subsystemname))
            val target = _take(CncfConfigurationTarget.ComponentInstance.create(subsystem, ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("catalog"), instancename)))
            val binding = _take(ConfigurationBinding.initial(_candidate(parameter, target, "production")))
            val trace = _take(ConfigurationBindingTrace.from(_take(ConfigurationBindingCollection.from(Vector(binding)))))

            _take(trace.explain(parameter)).map(_.effective.target).contains(target)
          }
          val checked = Test.check(
            Test.Parameters.default.withMinSuccessfulTests(16),
            property
          )

          Then("each generated qualified identity is preserved structurally")
          checked.passed shouldBe true
        }
      }
    }
  }

  private def _parameter: ConfigurationParameter[String] =
    _take(ConfigurationParameter.create(_take(CanonicalParameterId.parse("textus.application.mode")), ConfigurationValueCodec.string))

  private def _candidate(
    parameter: ConfigurationParameter[String],
    target: CncfConfigurationTarget,
    value: String
  ): ConfigurationBindingCandidate[String, CncfConfigurationTarget] =
    _take(
      for {
        provenance <- ConfigurationProvenance.create(
          ConfigurationOrigin.Home,
          "home",
          "source",
          Some("config.yaml"),
          Some(parameter.id.value),
          10,
          0,
          Vector("evidence"),
          isConfidential = false
        )
        candidate <- ConfigurationBindingCandidate.create(parameter, target, value, provenance)
      } yield candidate
    )

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
