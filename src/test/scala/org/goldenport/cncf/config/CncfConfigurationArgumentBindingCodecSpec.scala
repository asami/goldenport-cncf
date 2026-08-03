package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.configuration.{CanonicalParameterId, ConfigurationParameter, ConfigurationValueCodec}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class CncfConfigurationArgumentBindingCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e1 = afterWord("in spec:phase-55-command-argument-binding-codec, example:E1, rules:GCF08C-R1,R2, phase:55, slice:GCF-08C")
  private val _e2 = afterWord("in spec:phase-55-command-argument-binding-codec, example:E2, rules:GCF08C-R3,R4, phase:55, slice:GCF-08C")
  private val _e3 = afterWord("in spec:phase-55-command-argument-binding-codec, example:E3, rules:GCF08C-R5, phase:55, slice:GCF-08C")
  private val _e4 = afterWord("in spec:phase-55-command-argument-collection-admission, example:E4, rules:GCF08D-R1,R2,R3,R4, phase:55, slice:GCF-08D")
  private val _e5 = afterWord("in spec:phase-55-command-argument-collection-admission, example:E5, rules:GCF08D-R5,R6, phase:55, slice:GCF-08D")
  private val _e6 = afterWord("in spec:phase-55-command-argument-collection-admission, example:E6, rules:GCF08D-R7, phase:55, slice:GCF-08D")
  private val _parameter = _take(ConfigurationParameter.create(_take(CanonicalParameterId.parse("textus.codec.example")), ConfigurationValueCodec.string))
  private val _catalog = _take(CncfConfigurationParameterCatalog.create(Vector(_take(CncfConfigurationParameterDefinition.registered(_parameter.id.value, Vector("cncf.codec.example"), _parameter, Vector("phase-55: gcf08c"), false, CncfConfigurationTargetKind.all)))))
  private val _codec = _take(CncfConfigurationArgumentBindingCodec.create(_catalog))

  "CNCF configuration argument binding codec" should {
    "round-trip every target while preserving the raw value exactly" which {
      "E1 preserve reference and raw value boundaries" must _e1 {
        "when Global through qualified ComponentInstance assignments are decoded and encoded" in {
          Given("four canonical target assignments, including empty and equals-containing raw values")
          val inputs = Vector(
            "--textus.binding=textus.codec.example=",
            "--textus.binding=@c/Widget:textus.codec.example=plain",
            "--textus.binding=@s/platform/default:textus.codec.example=a=b",
            "--textus.binding=@i/platform/default/Widget/primary:textus.codec.example=value"
          )
          When("the codec decodes and re-encodes each atomic argument")
          val assignments = inputs.map(x => _take(_codec.decode(x)))
          val rendered = assignments.map(x => _take(_codec.encode(x)))
          Then("the reference and uninspected raw suffix round-trip exactly")
          rendered shouldBe inputs
          assignments.map(_.rawValue) shouldBe Vector("", "plain", "a=b", "value")
        }
      }
    }

    "reject anything other than one canonical atomic option" which {
      "E2 reject alternate prefixes, malformed references, aliases, and forbidden target kinds" must _e2 {
        "when invalid arguments are decoded" in {
          Given("the canonical option prefix and catalog admission boundary")
          val invalid = Vector(
            "--cncf.binding=textus.codec.example=value",
            "--textus.runtime.binding=textus.codec.example=value",
            "--textus.binding=textus.codec.example",
            "--textus.binding==value",
            "--textus.binding=@s/platform/default:cncf.codec.example=value",
            "--textus.binding=@x/platform:textus.codec.example=value"
          )
          When("each invalid option is decoded")
          val results = invalid.map(_codec.decode)
          Then("none becomes a binding assignment")
          results.foreach(_.isSuccess shouldBe false)
        }
      }
    }

    "keep raw value validation outside this codec" which {
      "E3 retain an invalid typed value as opaque text" must _e3 {
        "when a canonical Subsystem-only user-mode assignment carries an unsupported value" in {
          Given("the closed catalog and a structurally valid argument")
          val codec = _take(CncfConfigurationArgumentBindingCodec.create(CncfConfigurationParameterCatalog.closed))
          When("the opaque assignment envelope is decoded")
          val assignment = _take(codec.decode("--textus.binding=@s/platform/default:textus.subsystem.user-mode=unsupported"))
          Then("the raw value is preserved without invoking the parameter value codec")
          assignment.rawValue shouldBe "unsupported"
        }
      }
    }

    "admit only pre-sentinel canonical binding options without making them runtime input" which {
      "E4 preserve encounter order and leave every post-sentinel token untouched" must _e4 {
        "when a mixed argv collection contains bindings, ordinary tokens, and a sentinel" in {
          Given("two pre-sentinel bindings and binding-shaped valid and malformed post-sentinel text")
          val arguments = Vector(
            "run",
            "--textus.binding=textus.codec.example=",
            "--verbose",
            "--textus.binding=@s/platform/default:textus.codec.example=a=b",
            "--",
            "--textus.binding=@s/platform/default:textus.codec.example=ignored",
            "--textus.binding",
            "--textus.binding==ignored"
          )

          When("the pure collection admission scans only the pre-sentinel partition")
          val admission = _take(_codec.admit(arguments))

          Then("admitted assignments retain order and opaque values while residual argv is untouched")
          admission.assignments.map(_.rawValue) shouldBe Vector("", "a=b")
          admission.residualArguments shouldBe Vector(
            "run",
            "--verbose",
            "--",
            "--textus.binding=@s/platform/default:textus.codec.example=ignored",
            "--textus.binding",
            "--textus.binding==ignored"
          )
        }
      }

      "E5 reject only duplicate parameter-target pairs while accepting the same parameter at another target" must _e5 {
        "when duplicate and distinct-target argv collections are admitted" in {
          Given("one canonical parameter with Global and SubsystemInstance target scopes")
          val duplicateArguments = Vector(
            "--textus.binding=textus.codec.example=first",
            "--textus.binding=textus.codec.example=second"
          )
          val distinctTargetArguments = Vector(
            "--textus.binding=textus.codec.example=global",
            "--textus.binding=@s/platform/default:textus.codec.example=subsystem"
          )

          When("the collection adapter checks canonical parameter-target identity")
          val duplicate = _codec.admit(duplicateArguments)
          val distinct = _take(_codec.admit(distinctTargetArguments))

          Then("the duplicate fails while different targets remain distinct assignments")
          duplicate.isSuccess shouldBe false
          distinct.assignments.map(_.rawValue) shouldBe Vector("global", "subsystem")
        }
      }

      "E6 reject malformed recognized options before the sentinel" must _e6 {
        "when a bare two-token option occurs in the inspected partition" in {
          Given("the rule that binding input is one atomic option")
          val arguments = Vector("--textus.binding", "textus.codec.example=value")

          When("the pre-sentinel collection is admitted")
          val result = _codec.admit(arguments)

          Then("the collection fails rather than reinterpreting two tokens")
          result.isSuccess shouldBe false
        }
      }
    }
  }

  private def _take[A](value: Consequence[A]): A = value.getOrElse(fail(value.display))
}
