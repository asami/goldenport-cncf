package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.subsystem.SubsystemUserMode
import org.goldenport.configuration.{ConfigurationBinding, ConfigurationBindingCandidate, ConfigurationBindingCollection, ConfigurationBindingReference, ConfigurationBindingTrace, ConfigurationOrigin, ConfigurationProvenance}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationBindingDiagnosticCodecSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-serialized-binding-diagnostic-boundary, example:E1, rules:GCF08K-R1,R2,R3, phase:55, slice:GCF-08K"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-serialized-binding-diagnostic-boundary, example:E2, rules:GCF08K-R4,R5, phase:55, slice:GCF-08K"
  )
  private val _e3 = afterWord(
    "in spec:phase-55-serialized-binding-diagnostic-boundary, example:E3, rules:GCF08K-R6, phase:55, slice:GCF-08K"
  )

  "CNCF configuration binding diagnostic codec" should {
    "E1 serialize only resolved trace history with canonical references and fixed field order" must _e1 {
      "when Global and Subsystem bindings have safe visible values" in {
        Given("one resolved Global repository binding and one resolved Subsystem user-mode binding")
        val subsystem = _take(SubsystemInstanceId.create("orders", "default"))
        val global = _take(ConfigurationBinding.initial(_take(ConfigurationBindingCandidate.create[Vector[String], CncfConfigurationTarget](
          CncfConfigurationParameterCatalog.repositoryDir,
          CncfConfigurationTarget.Global,
          Vector("repository-a"),
          _provenance("global-source", false)
        ))))
        val scoped = _take(ConfigurationBinding.initial(_take(ConfigurationBindingCandidate.create[SubsystemUserMode, CncfConfigurationTarget](
          CncfConfigurationParameterCatalog.subsystemUserMode,
          _take(CncfConfigurationTarget.SubsystemInstance.create(subsystem)),
          SubsystemUserMode.Standalone,
          _provenance("subsystem-source", false)
        ))))
        val trace = _take(ConfigurationBindingTrace.from(_take(ConfigurationBindingCollection.from(Vector(global, scoped)))))

        When("the already-sanitized trace crosses the diagnostic String boundary")
        val json = _take(CncfConfigurationBindingDiagnosticCodec.encode(trace))
        val codec = _take(CncfConfigurationBindingStringCodec.create(CncfConfigurationParameterCatalog.closed))

        Then("the fixed document grammar keeps canonical references and only trace provenance")
        json should startWith("{\"format\":\"textus.configuration-binding-diagnostic.v1\",\"bindings\":[")
        json.indexOf("textus.repository.dir") should be < json.indexOf("textus.subsystem.user-mode")
        json should include("\"sourceIdentity\":\"global-source\"")
        json should include("\"sourceIdentityTruncatedCodeUnits\":0")
        json should not include "evidence"
        val globalreference = _take(codec.decode("textus.repository.dir"))
        globalreference.parameterId shouldBe CncfConfigurationParameterCatalog.repositoryDir.id
        globalreference.target shouldBe CncfConfigurationTarget.Global
        _take(codec.decode("@s/orders/default:textus.subsystem.user-mode")).target shouldBe scoped.target
      }
    }

    "E2 redact confidential winner and overridden history before serialization" must _e2 {
      "when a trace contains confidential source values with sensitive punctuation" in {
        Given("a confidential override chain whose raw values must never leave the trace boundary")
        val rawwinner = SubsystemUserMode.MultiUser.name
        val rawprevious = SubsystemUserMode.Standalone.name
        val target = _take(CncfConfigurationTarget.SubsystemInstance.create(_take(SubsystemInstanceId.create("orders", "default"))))
        val previous = _take(ConfigurationBinding.initial(_take(ConfigurationBindingCandidate.create[SubsystemUserMode, CncfConfigurationTarget](
          CncfConfigurationParameterCatalog.subsystemUserMode,
          target,
          SubsystemUserMode.Standalone,
          _provenance("confidential-previous-source", true)
        ))))
        val winner = _take(ConfigurationBinding.overrideWith(_take(ConfigurationBindingCandidate.create[SubsystemUserMode, CncfConfigurationTarget](
          CncfConfigurationParameterCatalog.subsystemUserMode,
          target,
          SubsystemUserMode.MultiUser,
          _provenance("confidential-winner-source", true)
        )), previous))
        val trace = _take(ConfigurationBindingTrace.from(_take(ConfigurationBindingCollection.from(Vector(winner)))))

        When("the confidential trace is encoded")
        val result = CncfConfigurationBindingDiagnosticCodec.encode(trace)
        val json = _take(result)

        Then("both current and historical raw values stay absent while structural redaction remains visible")
        json should include("\"state\":\"redacted\"")
        json should not include rawwinner
        json should not include rawprevious
        result.toString should not include rawwinner
        result.toString should not include rawprevious
      }
    }

    "E3 reject an absent trace structurally without creating a secondary configuration authority" must _e3 {
      "when no resolved trace was supplied" in {
        Given("no trace, candidate collection, source snapshot, or raw configuration value")

        When("the codec is asked to encode the absent trace")
        val result = CncfConfigurationBindingDiagnosticCodec.encode(null)

        Then("the boundary fails structurally and exposes no source value")
        result.isSuccess shouldBe false
        result.display should include("diagnostic trace is required")
      }
    }
  }

  private def _provenance(
    sourceidentity: String,
    confidential: Boolean
  ): ConfigurationProvenance =
    _take(ConfigurationProvenance.create(
      ConfigurationOrigin.Home,
      "textus",
      sourceidentity,
      Some("subsystems.orders.instances.default.config.textus.subsystem.user-mode"),
      Some("textus.subsystem.user-mode"),
      10,
      0,
      Vector("must-not-serialize"),
      confidential
    ))

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
