package org.goldenport.cncf.config

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentId, ComponentInstanceId}
import org.goldenport.configuration.{ConfigurationBindingCollection, ConfigurationBindingResolver, ConfigurationDocument, ConfigurationOrigin, ConfigurationSourceAdmission, ConfigurationValue}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  3, 2026
 * @version Aug.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class SystemNodeShutdownConfigurationSpec
  extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:phase-55-system-node-shutdown-configuration, example:E1, rules:GCF09H-C1,C2, phase:55, slice:GCF-09H"
  )
  private val _e2 = afterWord(
    "in spec:phase-55-system-node-shutdown-configuration, example:E2, rules:GCF09H-C1,C3, phase:55, slice:GCF-09H"
  )
  private val _e3 = afterWord(
    "in spec:phase-55-system-node-shutdown-configuration, example:E3, rules:GCF09H-C2,C3, phase:55, slice:GCF-09H"
  )

  "SystemNode shutdown configuration" should {
    "use only the closed typed SystemNode configuration path" which {
      "E1 select the bounded default only when the SystemNode binding is absent" must _e1 {
        "when no Subsystem-instance timeout candidate is resolved" in {
          Given("an empty typed configuration binding collection")

          When("SystemNode shutdown configuration is resolved")
          val configuration = _take(SystemNodeShutdownConfiguration.from(ConfigurationBindingCollection.empty))

          Then("it selects the fixed default with default provenance")
          configuration.drainTimeoutMillis shouldBe 30000L
          configuration.source shouldBe SystemNodeShutdownConfiguration.Source.Default
        }
      }

      "E2 select a resolved Subsystem-instance binding and reject every non-canonical scope" must _e2 {
        "when the catalog sees canonical, alias, Global, ComponentClass, and ComponentInstance inputs" in {
          Given("the canonical timeout key and otherwise disallowed spellings and targets")
          val canonical = _collection("120")
          val alias = _decode("textus.runtime.system-node.shutdown.drain-timeout-millis", "120", _subsystem_target)
          val global = _decode(CncfConfigurationParameterCatalog.SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY, "120", CncfConfigurationTarget.Global)
          val componentclass = _decode(CncfConfigurationParameterCatalog.SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY, "120", _component_class_target)
          val componentinstance = _decode(CncfConfigurationParameterCatalog.SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY, "120", _component_instance_target)

          When("the closed catalog and SystemNode projection resolve them")
          val configuration = canonical.flatMap(collection => SystemNodeShutdownConfiguration.from(collection))

          Then("only the canonical Subsystem-instance input can control the Node")
          configuration.toOption.map(_.drainTimeoutMillis) shouldBe Some(120L)
          configuration.toOption.map(_.source) shouldBe Some(SystemNodeShutdownConfiguration.Source.Resolved)
          configuration.toOption.map(_.observationMessage) shouldBe Some("system-node shutdown drain source=Resolved millis=120")
          configuration.toOption.map(_.observationMessage.contains(CncfConfigurationParameterCatalog.SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY)) shouldBe Some(false)
          alias.isSuccess shouldBe false
          global.isSuccess shouldBe false
          componentclass.isSuccess shouldBe false
          componentinstance.isSuccess shouldBe false
          CncfConfigurationParameterCatalog.systemNodeShutdownDrainTimeoutMillis.codec
            .decode(ConfigurationValue.NumberValue(BigDecimal("120.5"))).isSuccess shouldBe false
        }
      }

      "E3 make a validated typed per-SystemNode override dominate the resolved binding" must _e3 {
        "when both configuration sources are supplied" in {
          Given("a resolved 120-millisecond binding and a typed 75-millisecond Node override")
          val collection = _take(_collection("120"))

          When("the Node shutdown configuration chooses its effective value")
          val configuration = _take(SystemNodeShutdownConfiguration.from(collection, Some(75L)))

          Then("the override wins and retains only its safe source and bounded duration")
          configuration.drainTimeoutMillis shouldBe 75L
          configuration.source shouldBe SystemNodeShutdownConfiguration.Source.Override
          SystemNodeShutdownConfiguration.overrideC(0L).isSuccess shouldBe false
          SystemNodeShutdownConfiguration.overrideC(300001L).isSuccess shouldBe false
        }
      }
    }
  }

  private def _collection(value: String): Consequence[ConfigurationBindingCollection[CncfConfigurationTarget]] =
    for {
      candidates <- _decode(CncfConfigurationParameterCatalog.SYSTEM_NODE_SHUTDOWN_DRAIN_TIMEOUT_MILLIS_KEY, value, _subsystem_target)
      context <- CncfConfigurationResolutionContext.forSubsystem(_subsystem_identity)
      collection <- ConfigurationBindingResolver.resolve(candidates, context.generic)
    } yield collection

  private def _decode(
    spelling: String,
    value: String,
    target: CncfConfigurationTarget
  ) = {
    val location = target match {
      case CncfConfigurationTarget.Global => CncfConfigurationDocumentLocation.Global
      case value: CncfConfigurationTarget.SubsystemInstance =>
        new CncfConfigurationDocumentLocation.SubsystemInstance(value)
      case value: CncfConfigurationTarget.ComponentClass =>
        new CncfConfigurationDocumentLocation.ComponentClass(value)
      case value: CncfConfigurationTarget.ComponentInstance =>
        new CncfConfigurationDocumentLocation.ComponentInstance(value)
    }
    CncfConfigurationCandidateDecoder.decodeCatalog(Vector(
      CncfConfigurationDocumentBatch(
        location,
        _take(ConfigurationSourceAdmission.create(
          ConfigurationOrigin.Home,
          "home",
          "system-node-shutdown-spec",
          10,
          "system-node-shutdown-spec",
          () => Consequence.success(
            ConfigurationDocument.Object(Vector(
              ConfigurationDocument.Field(spelling, ConfigurationDocument.Scalar(ConfigurationValue.StringValue(value)))
            ))
          )
        ))
      )
    ))
  }

  private def _subsystem_identity: SubsystemInstanceId =
    _take(SubsystemInstanceId.create("platform", "default"))

  private def _subsystem_target: CncfConfigurationTarget.SubsystemInstance =
    _take(CncfConfigurationTarget.SubsystemInstance.create(_subsystem_identity))

  private def _component_class_target: CncfConfigurationTarget.ComponentClass =
    _take(CncfConfigurationTarget.ComponentClass.create(ComponentId("Catalog")))

  private def _component_instance_target: CncfConfigurationTarget.ComponentInstance =
    _take(CncfConfigurationTarget.ComponentInstance.create(
      _subsystem_identity,
      ComponentInstanceId("Catalog", "default")
    ))

  private def _take[A](result: Consequence[A]): A =
    result.getOrElse(fail(result.display))
}
