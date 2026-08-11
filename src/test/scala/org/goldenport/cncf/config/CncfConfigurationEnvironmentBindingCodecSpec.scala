package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.configuration.{CanonicalParameterId, ConfigurationBindingReference, ConfigurationParameter, ConfigurationValueCodec}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  4, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationEnvironmentBindingCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e1 = afterWord("in spec:phase-55-environment-binding-codec, example:E1, rules:GCF08B-R1,R2, phase:55, slice:GCF-08B")
  private val _e2 = afterWord("in spec:phase-55-environment-binding-codec, example:E2, rules:GCF08B-R3,R4, phase:55, slice:GCF-08B")
  private val _parameter = _take(ConfigurationParameter.create(_take(CanonicalParameterId.parse("textus.codec.example")), ConfigurationValueCodec.string))
  private val _hyphen_parameter = _take(ConfigurationParameter.create(_take(CanonicalParameterId.parse("textus.codec-example.more-value")), ConfigurationValueCodec.string))
  private val _catalog = _take(CncfConfigurationParameterCatalog.create(Vector(
    _take(CncfConfigurationParameterDefinition.registered(_parameter.id.value, Vector("cncf.codec.example"), _parameter, Vector("phase-55: gcf08b"), false, CncfConfigurationTargetKind.all)),
    _take(CncfConfigurationParameterDefinition.registered(_hyphen_parameter.id.value, Vector.empty, _hyphen_parameter, Vector("phase-55: gcf08b"), false, CncfConfigurationTargetKind.all))
  )))
  private val _codec = _take(CncfConfigurationEnvironmentBindingCodec.create(_catalog))

  "CNCF configuration environment binding codec" should {
    "encode and decode every target without collisions" which {
      "E1 preserve exact target identity" must _e1 {
        "when all four target forms are encoded" in {
          Given("a canonical catalog and Global, ComponentClass, SubsystemInstance, and ComponentInstance targets")
          val subsystem = _take(SubsystemInstanceId.create("platförm", "default"))
          val targets = Vector[CncfConfigurationTarget](
            CncfConfigurationTarget.Global,
            _take(CncfConfigurationTarget.ComponentClass.create(ComponentId("org.goldenport.cncf.test.Widget"))),
            _take(CncfConfigurationTarget.SubsystemInstance.create(subsystem)),
            _take(CncfConfigurationTarget.ComponentInstance.create(subsystem, ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("Widget"), "primary")))
          )
          When("each target reference is round-tripped through a name")
          val names = targets.map(x => _take(_codec.encode(_take(ConfigurationBindingReference.create(_parameter.id, x)))))
          val decoded = names.map(x => _take(_codec.decode(x)).target)
          val hyphenname = _take(_codec.encode(_take(ConfigurationBindingReference.create(_hyphen_parameter.id, CncfConfigurationTarget.Global))))
          val hyphenreference = _take(_codec.decode(hyphenname))
          Then("the names are distinct, shell-safe, and reconstruct exact targets and parameter punctuation")
          names.distinct.size shouldBe 4
          names.foreach(_ should startWith("TEXTUS_BINDING_"))
          decoded shouldBe targets
          names.head shouldBe "TEXTUS_BINDING_G_TEXTUS_DCODEC_DEXAMPLE"
          hyphenname shouldBe "TEXTUS_BINDING_G_TEXTUS_DCODEC_HEXAMPLE_DMORE_HVALUE"
          hyphenname should not be names.head
          hyphenreference.parameterId shouldBe _hyphen_parameter.id
        }
      }
    }

    "reject non-canonical names before any environment value can be considered" which {
      "E2 reject malformed names, aliases, and forbidden target policy" must _e2 {
        "when alternate spellings are decoded" in {
          Given("the pure name-only codec and a Subsystem-only closed catalog")
          val subsystemOnly = _take(CncfConfigurationEnvironmentBindingCodec.create(CncfConfigurationParameterCatalog.closed))
          val invalid = Vector(
            "CNCF_BINDING_G_TEXTUS_DCODEC_DEXAMPLE",
            "TEXTUS_BINDING_G_textus_Dcodec_Dexample",
            "TEXTUS_BINDING_G_TEXTUS_CODEC_DEXAMPLE",
            "TEXTUS_BINDING_S_706C617466%c3B6726D_64656661756C74_TEXTUS_DCODEC_DEXAMPLE",
            "TEXTUS_BINDING_S_706C617466C3_64656661756C74_TEXTUS_DCODEC_DEXAMPLE",
            "TEXTUS_BINDING_S_63616665CC81_64656661756C74_TEXTUS_DCODEC_DEXAMPLE",
            "TEXTUS_BINDING_G_CNCF_DCODEC_DEXAMPLE"
          )
          When("the malformed or alternative names are decoded")
          val failures = invalid.map(_codec.decode) :+ subsystemOnly.decode("TEXTUS_BINDING_G_TEXTUS_DSUBSYSTEM_DUSER_HMODE")
          Then("all fail structurally and no source/value/resolution API is involved")
          failures.foreach(_.isSuccess shouldBe false)
        }
      }
    }
  }

  private def _take[A](value: Consequence[A]): A = value.getOrElse(fail(value.display))
}
