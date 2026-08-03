package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.importer.StartupImportConfiguration
import org.goldenport.cncf.subsystem.{Subsystem, SystemNode}
import org.goldenport.configuration.{Configuration, ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationDocument, ConfigurationOrigin, ConfigurationSourceAdmission, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class StartupImportConfigurationSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-startup-import-configuration, example:E2, rules:GCF09I-C1,C2,C3, phase:55, slice:GCF-09I"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-startup-import-configuration, example:E3, rules:GCF09I-C1,C4, phase:55, slice:GCF-09I"
  )
  private val _e3 = afterWord(
    "in spec:phase-55-startup-import-configuration, example:E4, rules:GCF09I-C1,C4, phase:55, slice:GCF-09I"
  )

  "Startup import configuration" should {
    "use only the admitted typed binding collection" which {
      "E2 select canonical values and normalize source text" must _e1 {
        "when data and entity bindings have surrounding whitespace" in {
          Given("one resolved Subsystem binding collection with both canonical values")
          val collection = _take(_collection(Vector(
            CncfConfigurationParameterCatalog.STARTUP_IMPORT_DATA_FILE_KEY -> " data.yaml ",
            CncfConfigurationParameterCatalog.STARTUP_IMPORT_ENTITY_FILE_KEY -> " entity.yaml "
          )))

          When("the value-only importer configuration is projected")
          val configuration = _take(StartupImportConfiguration.from(collection))

          Then("only normalized source values cross the importer boundary")
          configuration.dataSource shouldBe Some("data.yaml")
          configuration.entitySource shouldBe Some("entity.yaml")
        }
      }

      "E3 treat blank and absent admitted values as no explicit source" must _e2 {
        "when the collection is empty or contains blank string values" in {
          Given("an admitted empty collection and one blank data-source binding")
          val empty = _take(StartupImportConfiguration.from(ConfigurationBindingCollection.empty))
          val blank = _take(_collection(Vector(CncfConfigurationParameterCatalog.STARTUP_IMPORT_DATA_FILE_KEY -> "   ")))

          When("each collection is projected")
          val blankconfiguration = _take(StartupImportConfiguration.from(blank))

          Then("the default directory fallback remains the sole later decision")
          empty.dataSource shouldBe None
          empty.entitySource shouldBe None
          blankconfiguration.dataSource shouldBe None
          blankconfiguration.entitySource shouldBe None
        }
      }

      "E4 fail structurally before a Subsystem admits final bindings" must _e3 {
        "when runtime startup import is requested from an unadmitted Subsystem" in {
          Given("a Subsystem with no final runtime binding collection")
          val subsystem = new Subsystem(
            "startup-import-unadmitted",
            configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
            systemnode = SystemNode.create()
          )

          When("the runtime-only startup-import projection is requested")
          try {
            val result = subsystem.runtimeStartupImportConfigurationC

            Then("it fails without reading legacy raw configuration")
            result.isSuccess shouldBe false
            result.display should include("startup-import bindings have not been admitted")
          } finally {
            Subsystem.shutdownOwned(subsystem)
          }
        }
      }
    }
  }

  private def _collection(
    entries: Vector[(String, String)]
  ): Consequence[ConfigurationBindingCollection[CncfConfigurationTarget]] =
    for {
      candidates <- CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
        CncfConfigurationDocumentBatch(
          new CncfConfigurationDocumentLocation.SubsystemInstance(_target),
          _take(ConfigurationSourceAdmission.create(
            ConfigurationOrigin.Home,
            "home",
            "startup-import-configuration-spec",
            10,
            "startup-import-configuration-spec",
            () => Consequence.success(ConfigurationDocument.Object(entries.map { case (key, value) =>
              ConfigurationDocument.Field(key, ConfigurationDocument.Scalar(ConfigurationValue.StringValue(value)))
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
