package logicalui

import domain.logicalui.{DomainLogicalUiDefinitions, DomainLogicalUiMetadataProvider}
import org.goldenport.cncf.component.{Component, ComponentCreate}
import org.goldenport.cncf.display.{DisplayBindingProvider, DisplayRuntime, DisplayService, DisplayServiceBindingProvider, GeneratedLogicalUiAbi, GeneratedLogicalUiMetadataProvider}

/*
 * @since   Oct.  4, 2026
 * @version Oct.  5, 2026
 */
/** Development-only actual-delivery consumers and explicit typed application bindings. */
object GeneratedDisplayConsumerFixture {
  import GeneratedLogicalUiAbi.*
  import TypedDisplayProjectionFixture.Capture
  import TypedDisplayRuntimeFixture.Installed

  abstract class ConsumerComponent(rows: Vector[Capture], invalidLastBinding: Boolean)
    extends Component() with DisplayBindingProvider with DisplayServiceBindingProvider {
    private val _rows = rows
    private val _invalid_last_binding = invalidLastBinding
    private var _metadata_reads = 0
    private var _binding_reads = 0
    private var _installed: Option[Installed] = None

    def metadataReads: Int = _metadata_reads
    def bindingReads: Int = _binding_reads
    def installed: Option[Installed] = _installed
    def recordMetadataRead(): Unit = { _metadata_reads += 1 }

    def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] =
      DisplayServiceReadFixture.resourceBindings

    def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = {
      _binding_reads += 1
      val installed = TypedDisplayRuntimeFixture.install(component, _rows)
      _installed = Some(installed)
      if (_invalid_last_binding) {
        Vector(installed.bindings.head,
          DisplayRuntime.detail("capture-detail", "wrong-key", installed.browser,
            TypedDisplayProjectionFixture.detailRow))
      } else installed.bindings
    }
  }

  final class ActualComponent(rows: Vector[Capture],
    definitionTransform: Vector[Definition] => Vector[Definition] = definitions => definitions,
    invalidLastBinding: Boolean = false)
    extends ConsumerComponent(rows, invalidLastBinding) with DomainLogicalUiMetadataProvider {
    private val _definition_transform = definitionTransform
    override def generatedLogicalUiDefinitions: Vector[Definition] = {
      recordMetadataRead()
      _definition_transform(super.generatedLogicalUiDefinitions)
    }
  }

  final class ExpectedComponent(rows: Vector[Capture])
    extends ConsumerComponent(rows, false) with GeneratedLogicalUiMetadataProvider {
    def generatedLogicalUiDefinitions: Vector[Definition] = {
      recordMetadataRead()
      ExpectedGeneratedLogicalUiFixture.generatedLogicalUiDefinitions
    }
  }

  final class FallbackFactory extends Component.Factory
    with GeneratedLogicalUiMetadataProvider with DisplayBindingProvider with DisplayServiceBindingProvider {
    private var _metadata_reads = 0
    private var _binding_reads = 0
    def metadataReads: Int = _metadata_reads
    def bindingReads: Int = _binding_reads
    def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] =
      DisplayServiceReadFixture.resourceBindings
    def generatedLogicalUiDefinitions: Vector[Definition] = {
      _metadata_reads += 1
      DomainLogicalUiDefinitions.definitions
    }
    def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = {
      _binding_reads += 1
      TypedDisplayRuntimeFixture.installBindings(component)
    }
    override protected def create_Component(params: ComponentCreate): Component =
      throw new UnsupportedOperationException("fixture factory does not create components")
    override protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      throw new UnsupportedOperationException("fixture factory does not create cores")
  }

  def invalidDefinitions: Vector[(String, Vector[Definition], String, String)] = {
    val definitions = DomainLogicalUiDefinitions.definitions
    val broken = definitions.head.screen match {
      case list: ListDefinition => definitions.updated(0,
        definitions.head.copy(screen = list.copy(detailTarget = DetailTarget("absent-detail"))))
      case _: DetailDefinition => throw new IllegalStateException("actual fixture requires List first")
    }
    Vector(
      ("ABI version 2", definitions.updated(0, definitions.head.copy(abiVersion = 2)),
        "LUI96_ABI_INCOMPATIBLE", "definitions[0].abiVersion"),
      ("duplicate actual screen", definitions :+ definitions(1),
        "LUI96_DUPLICATE_ID", "definitions[2].screenId"),
      ("malformed second revision", definitions.updated(1, definitions(1).copy(definitionRevision = "")),
        "LUI96_ID_INVALID", "definitions[1].definitionRevision"),
      ("broken List target", broken,
        "LUI96_DETAIL_TARGET_INVALID", "definitions[0].screen.detailTarget.screenId")
    )
  }
}
