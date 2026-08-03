package org.goldenport.cncf.config

import java.util.concurrent.atomic.AtomicInteger

import org.goldenport.Consequence
import org.goldenport.configuration.{CanonicalParameterId, ConfigurationBindingReference, ConfigurationOrigin, ConfigurationParameter, ConfigurationSourceAdmission, ConfigurationValue, ConfigurationValueCodec}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class CncfConfigurationArgumentBindingCandidateDecoderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e1 = afterWord("in spec:phase-55-argument-candidate-bridge, example:E1, rules:GCF08E-R1,R2,R3, phase:55, slice:GCF-08E")
  private val _e2 = afterWord("in spec:phase-55-argument-candidate-bridge, example:E2, rules:GCF08E-R4,R5, phase:55, slice:GCF-08E")
  private val _e3 = afterWord("in spec:phase-55-argument-candidate-bridge, example:E3, rules:GCF08E-R6,R7, phase:55, slice:GCF-08E")
  private val _e4 = afterWord("in spec:phase-55-argument-candidate-bridge, example:E4, rules:GCF08E-R8,R9, phase:55, slice:GCF-08E")
  private val _parameter = _take(ConfigurationParameter.create(_take(CanonicalParameterId.parse("textus.codec.example")), ConfigurationValueCodec.string))
  private val _catalog = _take(CncfConfigurationParameterCatalog.create(Vector(_take(CncfConfigurationParameterDefinition.registered(_parameter.id.value, Vector.empty, _parameter, Vector("phase-55: gcf08e"), true, CncfConfigurationTargetKind.all)))))

  "CNCF configuration argument binding candidate decoder" should {
    "load each supplied source once and construct typed catalog candidates" which {
      "E1 retain supplied source metadata, encounter order, target, and confidentiality" must _e1 {
        "when one Arguments source contains Global and qualified assignments" in {
          Given("a runtime-supplied source admission whose loader counts one invocation")
          val loads = new AtomicInteger(0)
          val global = _take(ConfigurationBindingReference.create[CncfConfigurationTarget](_parameter.id, CncfConfigurationTarget.Global))
          val subsystem = _take(ConfigurationBindingReference.create[CncfConfigurationTarget](_parameter.id, _take(CncfConfigurationTarget.SubsystemInstance.create(_take(SubsystemInstanceId.create("platform", "default"))))))
          val admission = _admission("arguments-one", 50, Vector(
            _take(CncfConfigurationArgumentBindingAssignment.create(global, "first")),
            _take(CncfConfigurationArgumentBindingAssignment.create(subsystem, "a=b"))
          ), loads)
          When("the bridge snapshots and constructs the one admitted source")
          val candidates = _take(CncfConfigurationArgumentBindingCandidateDecoder.decode(Vector(admission), _catalog))
          Then("one source load yields typed confidential candidates without resolution")
          loads.get shouldBe 1
          candidates.bindings.map(_.value) shouldBe Vector("first", "a=b")
          candidates.bindings.map(_.provenance.origin) shouldBe Vector(ConfigurationOrigin.Arguments, ConfigurationOrigin.Arguments)
          candidates.bindings.map(_.provenance.isConfidential) shouldBe Vector(true, true)
        }
      }
    }

    "reject malformed assignment collections structurally before a candidate authority exists" which {
      "E2 reject duplicate canonical parameter-target entries in one source" must _e2 {
        "when one source repeats the same Global reference" in {
          Given("two same-source assignments with one canonical parameter-target identity")
          val global = _take(ConfigurationBindingReference.create[CncfConfigurationTarget](_parameter.id, CncfConfigurationTarget.Global))
          val admission = _admission("arguments-duplicate", 50, Vector(
            _take(CncfConfigurationArgumentBindingAssignment.create(global, "first")),
            _take(CncfConfigurationArgumentBindingAssignment.create(global, "second"))
          ), new AtomicInteger(0))
          When("the bridge delegates collision detection to candidate construction")
          val result = CncfConfigurationArgumentBindingCandidateDecoder.decode(Vector(admission), _catalog)
          Then("no candidate collection is returned")
          result.isSuccess shouldBe false
        }
      }
    }

    "preserve each supplied source snapshot without resolution" which {
      "E3 retain loader count, source ordering, metadata, and collision-domain semantics" must _e3 {
        "when distinct and same collision-domain admissions carry one Global assignment each" in {
          Given("two runtime-created argument admissions with observable loaders and supplied metadata")
          val firstLoads = new AtomicInteger(0)
          val secondLoads = new AtomicInteger(0)
          val global = _take(ConfigurationBindingReference.create[CncfConfigurationTarget](_parameter.id, CncfConfigurationTarget.Global))
          val first = _admission("arguments-first", 10, Vector(_take(CncfConfigurationArgumentBindingAssignment.create(global, "first"))), firstLoads, "first-domain", "textus")
          val second = _admission("arguments-second", 20, Vector(_take(CncfConfigurationArgumentBindingAssignment.create(global, "second"))), secondLoads, "second-domain", "cncf")
          val sameDomainSecond = _admission("arguments-same-domain", 20, Vector(_take(CncfConfigurationArgumentBindingAssignment.create(global, "second"))), new AtomicInteger(0), "first-domain", "cncf")

          When("the bridge snapshots distinct and same collision-domain source vectors")
          val distinct = _take(CncfConfigurationArgumentBindingCandidateDecoder.decode(Vector(first, second), _catalog))
          val collision = CncfConfigurationArgumentBindingCandidateDecoder.decode(Vector(first, sameDomainSecond), _catalog)

          Then("each distinct source loads once per decode with its supplied metadata, while same-domain duplicates fail")
          firstLoads.get shouldBe 2
          secondLoads.get shouldBe 1
          distinct.bindings.map(_.value) shouldBe Vector("first", "second")
          distinct.bindings.map(_.provenance.layer) shouldBe Vector("textus", "cncf")
          distinct.bindings.map(_.provenance.sourceIdentity) shouldBe Vector("arguments-first", "arguments-second")
          distinct.bindings.map(_.provenance.sourceRank) shouldBe Vector(10, 20)
          distinct.bindings.map(_.provenance.sourceOrdinal) shouldBe Vector(0, 1)
          distinct.bindings.map(_.provenance.sourceType) shouldBe Vector(Some("arguments"), Some("arguments"))
          collision.isSuccess shouldBe false
        }
      }
    }

    "reject catalog-invalid references and conceal confidential typed decode failures" which {
      "E4 fail before candidates without exposing opaque raw values" must _e4 {
        "when unknown/disallowed references and a confidential failing codec are supplied" in {
          Given("manual assignment envelopes that bypass the prior argument codec")
          val unknown = _take(ConfigurationBindingReference.create[CncfConfigurationTarget](_take(CanonicalParameterId.parse("textus.unknown.parameter")), CncfConfigurationTarget.Global))
          val userModeGlobal = _take(ConfigurationBindingReference.create[CncfConfigurationTarget](_take(CanonicalParameterId.parse("textus.subsystem.user-mode")), CncfConfigurationTarget.Global))
          val unknownAdmission = _admission("unknown", 10, Vector(_take(CncfConfigurationArgumentBindingAssignment.create(unknown, "opaque-secret"))), new AtomicInteger(0))
          val disallowedAdmission = _admission("disallowed", 10, Vector(_take(CncfConfigurationArgumentBindingAssignment.create(userModeGlobal, "opaque-secret"))), new AtomicInteger(0))
          val rejectingParameter = _take(ConfigurationParameter.create(_take(CanonicalParameterId.parse("textus.rejecting.value")), new ConfigurationValueCodec[String] {
            def decode(value: ConfigurationValue): Consequence[String] = Consequence.configurationInvalid(s"configuration value is not admitted: $value")
            def encode(value: String): Consequence[ConfigurationValue] = Consequence.success(ConfigurationValue.StringValue(value))
          }))
          val rejectingCatalog = _take(CncfConfigurationParameterCatalog.create(Vector(_take(CncfConfigurationParameterDefinition.registered(rejectingParameter.id.value, Vector.empty, rejectingParameter, Vector("phase-55: gcf08e"), true, CncfConfigurationTargetKind.all)))))
          val rejectingReference = _take(ConfigurationBindingReference.create[CncfConfigurationTarget](rejectingParameter.id, CncfConfigurationTarget.Global))

          When("the bridge attempts catalog admission and typed candidate construction")
          val unknownResult = CncfConfigurationArgumentBindingCandidateDecoder.decode(Vector(unknownAdmission), CncfConfigurationParameterCatalog.closed)
          val disallowedResult = CncfConfigurationArgumentBindingCandidateDecoder.decode(Vector(disallowedAdmission), CncfConfigurationParameterCatalog.closed)
          val rejectingResult = CncfConfigurationArgumentBindingCandidateDecoder.decode(Vector(_admission("rejecting", 10, Vector(_take(CncfConfigurationArgumentBindingAssignment.create(rejectingReference, "opaque-secret"))), new AtomicInteger(0))), rejectingCatalog)

          Then("every invalid input fails structurally and no failure display includes the raw secret")
          unknownResult.isSuccess shouldBe false
          disallowedResult.isSuccess shouldBe false
          rejectingResult.isSuccess shouldBe false
          unknownResult.display.contains("opaque-secret") shouldBe false
          disallowedResult.display.contains("opaque-secret") shouldBe false
          rejectingResult.display.contains("opaque-secret") shouldBe false
        }
      }
    }
  }

  private def _admission(id: String, rank: Int, value: Vector[CncfConfigurationArgumentBindingAssignment], loads: AtomicInteger, collisionDomain: String = "arguments", layer: String = "textus") =
    _take(ConfigurationSourceAdmission.create(ConfigurationOrigin.Arguments, layer, id, rank, collisionDomain, () => {
      loads.incrementAndGet()
      Consequence.success(value)
    }, Some("arguments")))

  private def _take[A](value: Consequence[A]): A = value.getOrElse(fail(value.display))
}
