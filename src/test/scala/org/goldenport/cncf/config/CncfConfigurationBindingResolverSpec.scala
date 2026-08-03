package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.configuration.{CanonicalParameterId, ConfigurationBindingCandidate, ConfigurationBindingCandidates, ConfigurationBindingResolver, ConfigurationOrigin, ConfigurationParameter, ConfigurationProvenance, ConfigurationValueCodec}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationBindingResolverSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-cncf-binding-resolution, example:E1, rules:GCF05-C1,C2, phase:55, slice:GCF-05"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-cncf-binding-resolution, example:E2, rules:GCF05-C2,C3, phase:55, slice:GCF-05"
  )

  "CNCF configuration binding resolution" should {
    "construct the explicitly selected CNCF target chain" which {
      "E1 use Global alone or exactly Global, class, Subsystem, and qualified instance" must _e1 {
        "when a global request and a component request are constructed" in {
          Given("one Component, its containing Subsystem, and one ComponentInstance")
          val component = ComponentId("catalog")
          val subsystem = _take(SubsystemInstanceId.default("orders"))
          val instance = ComponentInstanceId("catalog", "default")

          When("CNCF adapts them to generic resolution contexts")
          val global = _take(CncfConfigurationResolutionContext.globalOnly)
          val selected = _take(CncfConfigurationResolutionContext.forComponent(component, subsystem, instance))

          Then("no ambient target forms or unqualified instance are introduced")
          global.generic.targets shouldBe Vector(CncfConfigurationTarget.Global)
          selected.generic.targets shouldBe Vector(
            CncfConfigurationTarget.Global,
            _take(CncfConfigurationTarget.ComponentClass.create(component)),
            _take(CncfConfigurationTarget.SubsystemInstance.create(subsystem)),
            _take(CncfConfigurationTarget.ComponentInstance.create(subsystem, instance))
          )
          CncfConfigurationResolutionContext.forComponent(component, subsystem, ComponentInstanceId("inventory", "default")).isSuccess shouldBe false
        }
      }
    }

    "resolve only target bindings belonging to the selected deployment identity" which {
      "E2 select one containing-Subsystem-qualified instance and keep independent contexts independent" must _e2 {
        "when candidates for two resident Subsystems use the same component class" in {
          Given("one generic parameter and two qualified CNCF component contexts")
          val parameter = _parameter
          val component = ComponentId("catalog")
          val orders = _take(SubsystemInstanceId.default("orders"))
          val billing = _take(SubsystemInstanceId.default("billing"))
          val instance = ComponentInstanceId("catalog", "default")
          val orderstarget = _take(CncfConfigurationTarget.ComponentInstance.create(orders, instance))
          val billingtarget = _take(CncfConfigurationTarget.ComponentInstance.create(billing, instance))
          val candidates = _candidates(Vector(
            _candidate(parameter, CncfConfigurationTarget.Global, "global", 10, 0),
            _candidate(parameter, orderstarget, "orders", 20, 0),
            _candidate(parameter, billingtarget, "billing", 20, 1)
          ))

          When("the same candidate collection is resolved for each selected identity")
          val ordersbinding = _take(
            _take(
              ConfigurationBindingResolver.resolve(
                candidates,
                _take(CncfConfigurationResolutionContext.forComponent(component, orders, instance)).generic
              )
            ).binding(parameter)
          ).get
          val billingbinding = _take(
            _take(
              ConfigurationBindingResolver.resolve(
                candidates,
                _take(CncfConfigurationResolutionContext.forComponent(component, billing, instance)).generic
              )
            ).binding(parameter)
          ).get

          Then("each result uses only the ComponentInstance qualified by its containing Subsystem")
          ordersbinding.value shouldBe "orders"
          ordersbinding.overridden.map(_.value) shouldBe Some("global")
          billingbinding.value shouldBe "billing"
          billingbinding.overridden.map(_.value) shouldBe Some("global")
        }
      }
    }
  }

  private def _parameter: ConfigurationParameter[String] =
    _take(
      ConfigurationParameter.create(
        _take(CanonicalParameterId.parse("textus.application.mode")),
        ConfigurationValueCodec.string
      )
    )

  private def _candidates(
    candidates: Vector[ConfigurationBindingCandidate[?, CncfConfigurationTarget]]
  ): ConfigurationBindingCandidates[CncfConfigurationTarget] =
    _take(ConfigurationBindingCandidates.from(candidates))

  private def _candidate(
    parameter: ConfigurationParameter[String],
    target: CncfConfigurationTarget,
    value: String,
    rank: Int,
    ordinal: Int
  ): ConfigurationBindingCandidate[String, CncfConfigurationTarget] =
    _take(
      for {
        provenance <- ConfigurationProvenance.create(
          ConfigurationOrigin.Home,
          "home",
          s"source-$rank-$ordinal",
          None,
          Some("textus.application.mode"),
          rank,
          ordinal,
          Vector.empty,
          isConfidential = false
        )
        candidate <- ConfigurationBindingCandidate.create(parameter, target, value, provenance)
      } yield candidate
    )

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
