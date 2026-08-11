package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.configuration.{
  CanonicalParameterId,
  ConfigurationBindingScenarioReport,
  ConfigurationDocument,
  ConfigurationOrigin,
  ConfigurationParameter,
  ConfigurationSourceAdmission,
  ConfigurationValue,
  ConfigurationValueCodec
}
import org.goldenport.cncf.component.ComponentId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  2, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class CncfConfigurationCandidateDecoderSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-cncf-document-decoder, example:E1, rules:GCF04-C1,C2,C3,C4, phase:55, slice:GCF-04"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-cncf-document-decoder, example:E2, rules:GCF04-C5,C6, phase:55, slice:GCF-04"
  )

  "CncfConfigurationCandidateDecoder" should {
    "project one consolidated document to the four concrete targets" which {
      "E1 retain exact field paths and explicit default identities" must _e1 {
        "when a consolidated target tree is decoded" in {
          Given("a request-scoped witnessed schema and one consolidated source")
          val input = CncfExternalDocumentScenarioInput(
            Vector(_batch(CncfConfigurationDocumentLocation.Consolidated, _consolidated, "home", "consolidated")),
            _schema
          )

          When("the decoder captures and projects the document")
          val candidates = _take(CncfConfigurationCandidateDecoder.decode(input))

          Then("Global, ComponentClass, SubsystemInstance, and ComponentInstance remain concrete candidates")
          candidates.bindings.size shouldBe 4
          candidates.bindings.map(_.target) should contain allOf (
            CncfConfigurationTarget.Global,
            _take(CncfConfigurationTarget.ComponentClass.create(ComponentId("org.goldenport.cncf.test.Catalog"))),
            _subsystem,
            _component_instance
          )
          candidates.bindings.map(_.provenance.inputPath) should contain allOf (
            Some("global.config.textus.application.mode"),
            Some("components.org.goldenport.cncf.test.Catalog.config.textus.application.mode"),
            Some("subsystems.platform.instances.default.config.textus.application.mode"),
            Some("subsystems.platform.instances.default.components.org.goldenport.cncf.test.Catalog.instances.default.config.textus.application.mode")
          )
        }

        "when equivalent targets are supplied through the split tree" in {
          Given("the same four target values in their admitted physical locations")
          val consolidatedinput = CncfExternalDocumentScenarioInput(
            Vector(_batch(CncfConfigurationDocumentLocation.Consolidated, _consolidated, "home", "consolidated")),
            _schema
          )
          val splitinput = _split_input

          When("both source organizations are decoded")
          val consolidated = _take(CncfConfigurationCandidateDecoder.decode(consolidatedinput))
          val split = _take(CncfConfigurationCandidateDecoder.decode(splitinput))
          val consolidatedtargets = consolidated.bindings.map(x => x.parameter.id.value -> x.target).toSet
          val splittargets = split.bindings.map(x => x.parameter.id.value -> x.target).toSet

          Then("they project to the same canonical parameter and concrete-target candidates")
          splittargets shouldBe consolidatedtargets
        }
      }
    }

    "reject colliding physical forms before resolution" which {
      "E2 reject canonical and alias spellings for one parameter target and layer" must _e2 {
        "when two admitted documents share a collision domain" in {
          Given("canonical and alias documents with one Global target")
          val canonical = _config("textus.application.mode", "production")
          val alias = _config("cncf.application.mode", "production")
          val input = CncfExternalDocumentScenarioInput(
            Vector(
              _batch(CncfConfigurationDocumentLocation.Consolidated, _consolidated_global(canonical), "home", "consolidated"),
              _batch(CncfConfigurationDocumentLocation.Consolidated, _consolidated_global(alias), "home", "split")
            ),
            _schema
          )

          When("the production scenario SPI evaluates the real decoder input")
          val report = CncfConfigurationBindingScenarioSpi.evaluate(
            CncfConfigurationBindingScenarioRequest.ExternalDocument("same-layer-duplicate", input)
          )

          Then("it returns the decoder's structured rejection rather than a placeholder success")
          report.isInstanceOf[ConfigurationBindingScenarioReport.Rejected] shouldBe true
        }
      }
    }
  }

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

  private def _batch(
    location: CncfConfigurationDocumentLocation,
    document: ConfigurationDocument,
    layer: String,
    source: String
  ): CncfConfigurationDocumentBatch =
    CncfConfigurationDocumentBatch(
      location,
      _take(
        ConfigurationSourceAdmission.create(
          ConfigurationOrigin.Home,
          layer,
          source,
          10,
          layer,
          () => Consequence.success(document)
        )
      )
    )

  private def _consolidated: ConfigurationDocument =
    ConfigurationDocument.Object(
      Vector(
        ConfigurationDocument.Field("global", _global(_config("textus.application.mode", "production"))),
        ConfigurationDocument.Field(
          "components",
          ConfigurationDocument.Object(
            Vector(ConfigurationDocument.Field("org.goldenport.cncf.test.Catalog", _component_class(_config("textus.application.mode", "production"))))
          )
        ),
        ConfigurationDocument.Field(
          "subsystems",
          ConfigurationDocument.Object(
            Vector(
              ConfigurationDocument.Field(
                "platform",
                ConfigurationDocument.Object(
                  Vector(
                    ConfigurationDocument.Field(
                      "instances",
                      ConfigurationDocument.Object(
                        Vector(
                          ConfigurationDocument.Field(
                            "default",
                            ConfigurationDocument.Object(
                              Vector(
                                ConfigurationDocument.Field("config", _config("textus.application.mode", "production")),
                                ConfigurationDocument.Field(
                                  "components",
                                  ConfigurationDocument.Object(
                                    Vector(
                                      ConfigurationDocument.Field(
                                        "org.goldenport.cncf.test.Catalog",
                                        ConfigurationDocument.Object(
                                          Vector(
                                            ConfigurationDocument.Field(
                                              "instances",
                                              ConfigurationDocument.Object(
                                                Vector(
                                                  ConfigurationDocument.Field(
                                                    "default",
                                                    ConfigurationDocument.Object(
                                                      Vector(ConfigurationDocument.Field("config", _config("textus.application.mode", "production")))
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
        )
      )
    )

  private def _split_input: CncfExternalDocumentScenarioInput =
    CncfExternalDocumentScenarioInput(
      Vector(
        _batch(CncfConfigurationDocumentLocation.Consolidated, _consolidated_global(_config("textus.application.mode", "production")), "home", "global"),
        _batch(_take(CncfConfigurationDocumentLocation.ComponentClass.create("org.goldenport.cncf.test.Catalog")), _config("textus.application.mode", "production"), "home", "component"),
        _batch(_take(CncfConfigurationDocumentLocation.SubsystemInstance.create("platform", "default")), _config("textus.application.mode", "production"), "home", "subsystem"),
        _batch(_take(CncfConfigurationDocumentLocation.ComponentInstance.create("platform", "default", "org.goldenport.cncf.test.Catalog", "default")), _config("textus.application.mode", "production"), "home", "component-instance")
      ),
      _schema
    )

  private def _global(config: ConfigurationDocument): ConfigurationDocument =
    ConfigurationDocument.Object(Vector(ConfigurationDocument.Field("config", config)))

  private def _consolidated_global(config: ConfigurationDocument): ConfigurationDocument =
    ConfigurationDocument.Object(Vector(ConfigurationDocument.Field("global", _global(config))))

  private def _component_class(config: ConfigurationDocument): ConfigurationDocument =
    ConfigurationDocument.Object(Vector(ConfigurationDocument.Field("config", config)))

  private def _config(spelling: String, value: String): ConfigurationDocument =
    ConfigurationDocument.Object(
      Vector(ConfigurationDocument.Field(spelling, ConfigurationDocument.Scalar(ConfigurationValue.StringValue(value))))
    )

  private def _subsystem: CncfConfigurationTarget.SubsystemInstance =
    _take(
      for {
        identity <- SubsystemInstanceId.create("platform", "default")
        target <- CncfConfigurationTarget.SubsystemInstance.create(identity)
      } yield target
    )

  private def _component_instance: CncfConfigurationTarget.ComponentInstance =
    _take(
      for {
        identity <- SubsystemInstanceId.create("platform", "default")
        target <- CncfConfigurationTarget.ComponentInstance.create(
          identity,
          org.goldenport.cncf.component.ComponentInstanceId(org.goldenport.cncf.testutil.TestComponentFactory.componentId("Catalog"), "default")
        )
      } yield target
    )

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
