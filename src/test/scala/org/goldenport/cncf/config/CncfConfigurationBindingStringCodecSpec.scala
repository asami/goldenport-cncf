package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.configuration.{CanonicalParameterId, ConfigurationBindingReference, ConfigurationParameter, ConfigurationValueCodec}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationBindingStringCodecSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-canonical-binding-string-codec, example:E1, rules:GCF08A-R6,R7, phase:55, slice:GCF-08A"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-canonical-binding-string-codec, example:E2, rules:GCF08A-R8,R9, phase:55, slice:GCF-08A"
  )
  private val _e3 = afterWord(
    "in spec:phase-55-canonical-binding-string-codec, example:E3, rules:GCF08A-R10,R11, phase:55, slice:GCF-08A"
  )
  private val _parameter = _take(ConfigurationParameter.create(
    _take(CanonicalParameterId.parse("textus.codec.example")),
    ConfigurationValueCodec.string
  ))
  private val _catalog = _take(CncfConfigurationParameterCatalog.create(Vector(
    _take(CncfConfigurationParameterDefinition.registered(
      _parameter.id.value,
      Vector("cncf.codec.example"),
      _parameter,
      Vector("phase-55: gcf08a codec test"),
      isConfidential = false,
      CncfConfigurationTargetKind.all
    ))
  )))
  private val _codec = _take(CncfConfigurationBindingStringCodec.create(_catalog))

  "CNCF configuration binding string codec" should {
    "round-trip each admitted semantic target through one canonical spelling" which {
      "E1 encode and decode Global, ComponentClass, SubsystemInstance, and ComponentInstance" must _e1 {
        "when every target is admitted by one canonical catalog definition" in {
          Given("the four frozen CNCF target forms and a closed canonical catalog")
          val subsystem = _take(SubsystemInstanceId.create("platform", "default"))
          val unicodeSubsystem = _take(SubsystemInstanceId.create("platförm", "default"))
          val targets = Vector[(CncfConfigurationTarget, String)](
            CncfConfigurationTarget.Global -> "textus.codec.example",
            _take(CncfConfigurationTarget.ComponentClass.create(ComponentId("Widget"))) -> "@c/Widget:textus.codec.example",
            _take(CncfConfigurationTarget.SubsystemInstance.create(unicodeSubsystem)) -> "@s/platf%C3%B6rm/default:textus.codec.example",
            _take(CncfConfigurationTarget.ComponentInstance.create(subsystem, ComponentInstanceId("Widget", "primary"))) -> "@i/platform/default/Widget/primary:textus.codec.example"
          )

          When("each reference is serialized and deserialized")
          val decoded = targets.map { case (target, spelling) =>
            val reference = _take(ConfigurationBindingReference.create(_parameter.id, target))
            _take(_codec.encode(reference)) -> _take(_codec.decode(spelling))
          }

          Then("each emitted spelling is unique and reconstructs its exact target")
          decoded.map(_._1) shouldBe targets.map(_._2)
          decoded.foreach { case (_, reference) =>
            reference.parameterId shouldBe _parameter.id
          }
          decoded.map(_._2.target) shouldBe targets.map(_._1)
        }
      }
    }

    "reject non-canonical qualifier syntax before it can name a target" which {
      "E2 reject alternative encodings and malformed identities structurally" must _e2 {
        "when non-canonical qualifier spellings are decoded" in {
          Given("strict UTF-8 uppercase-percent and NFC target requirements")
          val spellings = Vector(
            "@c/%57idget:textus.codec.example",
            "@s/platf%c3%b6rm/default:textus.codec.example",
            "@s/platförm/default:textus.codec.example",
            "@s/platform/%C3%28:textus.codec.example",
            "@s/platform/default/extra:textus.codec.example",
            "@x/platform:textus.codec.example",
            "@i/platform/default/Widget:textus.codec.example",
            "@s/cafe%CC%81/default:textus.codec.example"
          )

          When("each spelling is decoded")
          val results = spellings.map(_codec.decode)

          Then("no alternative spelling becomes a target reference")
          results.foreach(_.isSuccess shouldBe false)
        }
      }
    }

    "admit only registered canonical identifiers and their allowed target kinds" which {
      "E3 reject aliases, unknown identities, and a disallowed Global target" must _e3 {
        "when catalog admission is evaluated after structural codec decoding" in {
          Given("a catalog with one all-target test definition and the closed Subsystem-only definition")
          val subsystemOnly = _take(CncfConfigurationBindingStringCodec.create(CncfConfigurationParameterCatalog.closed))

          When("canonical, alias, unknown, and target-kind combinations are decoded")
          val canonical = subsystemOnly.decode("@s/platform/default:textus.subsystem.user-mode")
          val alias = _codec.decode("@s/platform/default:cncf.codec.example")
          val unknown = _codec.decode("@s/platform/default:textus.unknown.parameter")
          val global = subsystemOnly.decode("textus.subsystem.user-mode")
          val component = subsystemOnly.decode("@c/Widget:textus.subsystem.user-mode")

          Then("only the canonical SubsystemInstance reference is admitted")
          canonical.isSuccess shouldBe true
          alias.isSuccess shouldBe false
          unknown.isSuccess shouldBe false
          global.isSuccess shouldBe false
          component.isSuccess shouldBe false
        }
      }
    }
  }

  private def _take[A](value: Consequence[A]): A =
    value.getOrElse(fail(value.display))
}
