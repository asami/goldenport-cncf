package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.CanonicalParameterId
import org.goldenport.configuration.ConfigurationBindingScenarioReport
import org.goldenport.configuration.ConfigurationBindingScenarioReport.NotImplemented
import org.goldenport.configuration.ConfigurationDocument
import org.goldenport.configuration.ConfigurationOrigin
import org.goldenport.configuration.ConfigurationParameter
import org.goldenport.configuration.ConfigurationSourceAdmission
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.configuration.ConfigurationValueCodec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  2, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase55ConfigurationBindingContractSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-cncf-configuration-binding, example:E1, rules:GCF02-C1,C2,C3, phase:55, slice:GCF-02"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-cncf-configuration-binding, example:E2, rules:GCF04-C1,C2,C3, phase:55, slice:GCF-04"
  )
  private val _e3 = afterWord(
    "in spec:phase-55-cncf-configuration-binding, example:E3, rules:GCF07-C1,C2,C3,C4, phase:55, slice:GCF-07A"
  )
  private val _e4 = afterWord(
    "in spec:phase-55-cncf-configuration-binding, example:E4, rules:GCF02-C9,C10, phase:55, slice:GCF-02"
  )

  private val _target_scenarios = Vector(
    "exactly-global-component-class-subsystem-instance-component-instance-no-unqualified",
    "validated-subsystem-component-identities-containing-subsystem-and-explicit-default"
  )
  private val _document_scenarios = Vector(
    "consolidated-and-split-forms-project-identical-concrete-targets",
    "component-class-subsystem-instance-component-instance-path-ownership",
    "same-layer-consolidated-split-duplicate-rejection"
  )
  private val _alias_scenarios = Vector(
    "canonical-textus-decoding-with-decode-only-aliases",
    "original-alias-spelling-retained-in-provenance",
    "canonical-alias-coexistence-rejected-for-one-parameter-target-source"
  )
  private val _fixed_user_scenario =
    CncfConfigurationBindingScenarioSpi.fixedUserIdentityChangeScenarioId

  "Phase 55 CNCF configuration binding catalog" should {
    "route concrete-target scenarios through the CNCF specialization" which {
      "E1 preserve the four-target and identity contract while behavior is deferred" must _e1 {
        "when concrete target requests are evaluated" in {
          Given("the target-parametrized generic SPI and CNCF specialization")

          When("the requests are sent through the CNCF production SPI")
          val reports = _target_scenarios.map(x => _evaluate(CncfConfigurationBindingScenarioRequest.ConcreteTarget(x)))

          Then("each report is attributable to the registered concrete-target scenario")
          reports shouldBe _target_scenarios.map(NotImplemented.apply)
        }

        "when GCF-03 and GCF-05 satisfy every concrete-target invariant" in {
          Given("the registered concrete-target scenarios")

          When("their behavior becomes authoritative")

          Then("none remains a GCF-02 deferred report")
          pendingUntilFixed {
            _target_scenarios.map(x => _evaluate(CncfConfigurationBindingScenarioRequest.ConcreteTarget(x))).exists(_.isInstanceOf[NotImplemented]) shouldBe false
          }
        }
      }
    }

    "route external-document scenarios through the CNCF specialization" which {
      "E2 execute real document decoding without pretending that a scenario name is behavior" must _e2 {
        "when external document requests are evaluated" in {
          Given("request-scoped physical document input and a witnessed schema")

          When("the requests are sent through the CNCF production SPI")
          val reports = Vector(
            _evaluate(CncfConfigurationBindingScenarioRequest.ExternalDocument(_document_scenarios(0), _external_input)),
            _evaluate(CncfConfigurationBindingScenarioRequest.ExternalDocument(_document_scenarios(1), _external_input)),
            _evaluate(CncfConfigurationBindingScenarioRequest.ExternalDocument(_document_scenarios(2), _duplicate_input))
          )

          Then("success and rejection reports are attributable to actual decoder outcomes")
          reports.take(2).forall(_.isInstanceOf[ConfigurationBindingScenarioReport.Executed[?]]) shouldBe true
          reports.last.isInstanceOf[ConfigurationBindingScenarioReport.Rejected] shouldBe true
        }
      }
    }

    "route alias scenarios through the CNCF specialization" which {
      "E3 execute canonical and alias catalog decoding rather than deferred reports" must _e3 {
        "when catalog-backed alias requests are evaluated" in {
          Given("the canonical textus spelling, its decode-only alias, and one Subsystem target")

          When("the requests are sent through the CNCF production SPI")
          val reports = Vector(
            _evaluate(CncfConfigurationBindingScenarioRequest.Alias(_alias_scenarios(0), _alias_batches("textus.subsystem.user-mode"))),
            _evaluate(CncfConfigurationBindingScenarioRequest.Alias(_alias_scenarios(1), _alias_batches("cncf.subsystem.user-mode"))),
            _evaluate(CncfConfigurationBindingScenarioRequest.Alias(_alias_scenarios(2), _alias_collision_batches))
          )

          Then("canonical and alias inputs execute while a collision rejects structurally")
          reports.take(2).forall(_.isInstanceOf[ConfigurationBindingScenarioReport.Executed[?]]) shouldBe true
          reports.last.isInstanceOf[ConfigurationBindingScenarioReport.Rejected] shouldBe true
        }
      }
    }

    "route fixed-user scenarios through the CNCF specialization" which {
      "E4 reject the registered conflicting fixed-user identity through typed binding resolution" must _e4 {
        "when a fixed-user identity change request is evaluated" in {
          Given("the canonical Textus HOME then CNCF HOME fixed-user admission order")

          When("the request is sent through the CNCF production SPI")
          val report = _evaluate(CncfConfigurationBindingScenarioRequest.FixedUserIdentityChange(_fixed_user_scenario))

          Then("the exact registered scenario rejects with a value-safe migration-or-isolation conclusion")
          report match {
            case ConfigurationBindingScenarioReport.Rejected(scenarioid, conclusion) =>
              scenarioid shouldBe _fixed_user_scenario
              conclusion.show should include ("explicit data migration")
              conclusion.show should include ("isolated datastore")
              conclusion.show should include ("silent data reuse is not admitted")
              conclusion.show.contains("fixture-textus-fixed-user") shouldBe false
              conclusion.show.contains("fixture-cncf-fixed-user") shouldBe false
              conclusion.show.contains(".textus/user-profile.yaml") shouldBe false
              conclusion.show.contains(".cncf/user-profile.yaml") shouldBe false
              conclusion.show.contains("standalone-local") shouldBe false
            case other =>
              fail(s"Expected fixed-user rejection, got $other")
          }
        }
      }
    }
  }

  private def _evaluate(
    request: CncfConfigurationBindingScenarioRequest
  ) =
    CncfConfigurationBindingScenarioSpi.evaluate(request)

  private def _external_input: CncfExternalDocumentScenarioInput =
    CncfExternalDocumentScenarioInput(
      Vector(_batch("textus.application.mode", "production", "home", "one")),
      _schema
    )

  private def _duplicate_input: CncfExternalDocumentScenarioInput =
    CncfExternalDocumentScenarioInput(
      Vector(
        _batch("textus.application.mode", "production", "home", "one"),
        _batch("cncf.application.mode", "production", "home", "two")
      ),
      _schema
    )

  private def _batch(
    spelling: String,
    value: String,
    layer: String,
    source: String
  ): CncfConfigurationDocumentBatch =
    CncfConfigurationDocumentBatch(
      CncfConfigurationDocumentLocation.Consolidated,
      _take(
        ConfigurationSourceAdmission.create(
          ConfigurationOrigin.Home,
          layer,
          source,
          10,
          layer,
          () => Consequence.success(
            ConfigurationDocument.Object(
              Vector(
                ConfigurationDocument.Field(
                  "global",
                  ConfigurationDocument.Object(
                    Vector(
                      ConfigurationDocument.Field(
                        "config",
                        ConfigurationDocument.Object(
                          Vector(ConfigurationDocument.Field(spelling, ConfigurationDocument.Scalar(ConfigurationValue.StringValue(value))))
                        )
                      )
                    )
                  )
                )
              )
            )
          )
        )
      )
    )

  private def _schema: CncfConfigurationDocumentSchema = {
    val parameter = _take(
      ConfigurationParameter.create(
        _take(CanonicalParameterId.parse("textus.application.mode")),
        ConfigurationValueCodec.string
      )
    )
    _take(
      CncfConfigurationDocumentSchema.create(
        Vector(
          _take(
            CncfConfigurationParameterDefinition.typed(
              Vector("textus.application.mode", "cncf.application.mode"),
              parameter,
              Vector("default: production"),
              isConfidential = false
            )
          )
        )
      )
    )
  }

  private def _alias_batches(spelling: String): Vector[CncfConfigurationDocumentBatch] =
    Vector(_alias_batch(spelling, spelling, "catalog"))

  private def _alias_collision_batches: Vector[CncfConfigurationDocumentBatch] =
    Vector(
      _alias_batch("textus.subsystem.user-mode", "canonical", "home"),
      _alias_batch("cncf.subsystem.user-mode", "alias", "home")
    )

  private def _alias_batch(
    spelling: String,
    source: String,
    collisiondomain: String
  ): CncfConfigurationDocumentBatch =
    CncfConfigurationDocumentBatch(
      _take(CncfConfigurationDocumentLocation.SubsystemInstance.create("platform", "default")),
      _take(
        ConfigurationSourceAdmission.create(
          ConfigurationOrigin.Home,
          "home",
          source,
          10,
          collisiondomain,
          () => Consequence.success(
            ConfigurationDocument.Object(
              Vector(ConfigurationDocument.Field(spelling, ConfigurationDocument.Scalar(ConfigurationValue.StringValue("standalone"))))
            )
          )
        )
      )
    )

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
