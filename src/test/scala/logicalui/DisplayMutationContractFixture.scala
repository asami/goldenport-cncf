package logicalui

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.display.*
import org.goldenport.schema.WebColumn

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
/** Development-only pure typed contract fixtures, never persistence/lifecycle evidence. */
object DisplayMutationContractFixture {
  import DisplayMutation.*
  import DisplayMutationBinding.*
  import GeneratedLogicalUiAbi.*
  final case class Patch(title: String, description: String)
  final case class NativePatch(text: String, number: BigDecimal, flag: Boolean, instant: Instant, missing: Boolean)
  final class Trace {
    private var _calls: Vector[String] = Vector.empty
    def calls: Vector[String] = _calls
    def record(name: String): Unit = { _calls = _calls :+ name }
  }
  val title: FieldLocator = FieldLocator("main", "title")
  val description: FieldLocator = FieldLocator("main", "description")
  val stage: FieldLocator = FieldLocator("main", "stage")
  val titleTarget: UpdateTarget = UpdateTarget.Entity("Capture", "title")
  val descriptionTarget: UpdateTarget = UpdateTarget.Aggregate("Capture", "description")
  val seed: Patch = Patch("Existing title", "Existing description")
  val domainFailure: Consequence[Patch] = Consequence.argumentInvalid("title", "nonblank canonical title", "redacted")
  val definitions: AdmittedDefinitions = success(GeneratedLogicalUiAbi.admitC(TypedDisplayProjectionFixture.definitions))
  val capabilities: Capabilities = Capabilities(Support.Supported, Support.Supported, Support.Unsupported,
    Support.Supported, Support.Unsupported)
  val positiveCapabilities: Capabilities = capabilities.copy(create = Support.Supported, delete = Support.Supported)
  def sourceC(componentName: String = "org.goldenport.fixture.Mutation", resourceType: String = "Capture"): Consequence[DisplayProtocol.Source] =
    ComponentId.parseC(componentName).flatMap(componentid => DisplayProtocol.Source.createC(componentid, resourceType))
  val source: DisplayProtocol.Source = success(sourceC())
  def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case _ => throw new IllegalStateException("total contract fixture must succeed")
  }
  def resource(index: Int = 1, revision: Long = 7): DisplayModel.Resource =
    success(TypedDisplayProjectionFixture.resourceC(index, revision))
  def fields(trace: Option[Trace] = None): Vector[FieldBinding[Patch]] = Vector(
    FieldBinding(description, "write.description", descriptionTarget, Vector(ValueKind.Text),
      WebColumn.empty.copy(placeholder = Some("Optional description")), (input, value) => {
        trace.foreach(_.record("description"))
        value match {
          case DisplayValue.Text(text) => Consequence.success(input.copy(description = text))
          case _ => DisplayMutation.inputInvalidC("VALUE_KIND_INVALID", "description")
        }
      }),
    FieldBinding(title, "write.title", titleTarget, Vector(ValueKind.Text), WebColumn.empty, (input, value) => {
      trace.foreach(_.record("title"))
      value match {
        case DisplayValue.Text(text) => Consequence.success(input.copy(title = text))
        case _ => DisplayMutation.inputInvalidC("VALUE_KIND_INVALID", "title")
      }
    }))
  def validateC(input: Patch, trace: Option[Trace] = None): Consequence[Patch] = {
    trace.foreach(_.record("domain"))
    if (input.title == null || input.title.trim.isEmpty) domainFailure else Consequence.success(input)
  }
  def bindingC(trace: Option[Trace] = None, positive: Boolean = false): Consequence[Binding[Patch]] =
    DisplayMutationBinding.admitC(source, definitions, "capture-detail", if (positive) "positive-contract-1" else "ess-mutation-1",
      if (positive) positiveCapabilities else capabilities, fields(trace), Set.empty, input => validateC(input, trace))
  def authorize(trace: Option[Trace] = None): () => Consequence[Unit] = () => {
    trace.foreach(_.record("authorize"))
    Consequence.success(())
  }
  def update(binding: Binding[Patch], changes: Vector[FieldInput], revision: Long = 7,
    index: Int = 1): Request.Update = Request.Update(binding.selection, resource(index, revision).entityId, changes)
  def transport(operation: Operation = Operation.Update, revision: Long = 7): DisplayMutationTransport.Metadata =
    success(DisplayMutationTransport.bindC(operation, Vector("If-Match" -> ("\"revision-" + revision + "\""))))
  def reload(revision: Long = 8, index: Int = 1, sourceValue: DisplayProtocol.Source = source): DisplayMutationResult.ReloadedDisplay = {
    val capture = success(TypedDisplayProjectionFixture.captureC(index, revision))
    val projector = success(TypedDisplayProjectionFixture.detailC(definitions))
    DisplayMutationResult.ReloadedDisplay(sourceValue, success(projector.projectC(capture)))
  }
  def nativeDefinitions(order: Vector[ValueKind], sectionId: String = "native"): AdmittedDefinitions = {
    val fields = order.map(kind => FieldDefinition(kind.tag, kind.tag, PresentationRole.Content,
      ValueDefinition("read." + kind.tag, Vector(kind))))
    val definition = definitions.definitions(1).copy(screen = DetailDefinition("Native contract",
      Vector(SectionDefinition(sectionId, None, fields)), Vector.empty))
    success(GeneratedLogicalUiAbi.admitC(Vector(definition)))
  }
  def nativeFields(order: Vector[ValueKind], sectionId: String = "native"): Vector[FieldBinding[NativePatch]] = order.map { kind =>
    FieldBinding(FieldLocator(sectionId, kind.tag), "write." + kind.tag, UpdateTarget.Entity("Native", kind.tag),
      Vector(kind), WebColumn.empty, (input, value) => value match {
        case DisplayValue.Text(text) => Consequence.success(input.copy(text = text))
        case DisplayValue.Number(number) => Consequence.success(input.copy(number = number))
        case DisplayValue.Boolean(flag) => Consequence.success(input.copy(flag = flag))
        case DisplayValue.Timestamp(instant) => Consequence.success(input.copy(instant = instant))
        case DisplayValue.Missing => Consequence.success(input.copy(missing = true))
      })
  }
  def verifyTypeBoundary(): Unit = {
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationResult.*
      val display: org.goldenport.cncf.display.Display = null
      ProjectionState.Ready(display)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationResult.*
      import org.goldenport.Consequence
      val receipt: Receipt = null
      completeC(receipt, () => Consequence.success(null: org.goldenport.cncf.display.Display))
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationBinding.*
      val binding: Binding[logicalui.DisplayMutationContractFixture.Patch] = null
      binding.prepareCreateC(null, "untyped", null, null)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationBinding.*
      import org.goldenport.Consequence
      FieldBinding[String](null, "id", null, Vector.empty, null, (_, _) => Consequence.success(1))
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationBinding.*
      new Binding[String](null, null, null, Vector.empty, Vector.empty, Set.empty, null)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationBinding.*
      new Prepared[String](null, null, "", None, None, Vector.empty, Vector.empty, None)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationBinding.*
      new PreparedDelete(null, null, null, null)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationBinding.*
      new Descriptor(null, null, null, Vector.empty)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationResult.*
      new Receipt(null, null, null)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationResult.*
      new Result(null, None)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationTransport.*
      new Metadata(null, None, null, None)
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationBinding.*
      val prepared: Prepared[String] = null
      prepared.copy(input = "bypass")
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutationResult.*
      val result: Result = null
      result.copy()
    """)
    org.scalatest.Assertions.assertTypeError("""
      import org.goldenport.cncf.display.DisplayMutation.*
      new Request {}
    """)
  }
}
