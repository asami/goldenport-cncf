package logicalui

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import domain.logicalui.DomainLogicalUiDefinitions
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentFactory, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.config.{OperationMode, RuntimeConfig}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.display.*
import org.goldenport.cncf.http.{DisplayServiceHttpRouteFixture, HttpExecutionEngine, HttpRuntimeBindingAdmissionFixture}
import org.goldenport.cncf.security.OperationAuthorizationRule
import org.goldenport.cncf.subsystem.{GenericSubsystemDescriptor, Subsystem}
import org.goldenport.cncf.testutil.{RuntimeBindingAdmissionFixture, TestComponentFactory}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.{Property, Protocol, Request}
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.OperationResponse
import org.http4s.{Method, Request as HttpRequest, Uri}
import org.http4s.headers.`Content-Type`
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
/** Development-only public Operation and in-process HTTP consumers of unchanged producer metadata. */
object DisplayServiceReadFixture {
  import GeneratedLogicalUiAbi.*
  import TypedDisplayProjectionFixture.Capture
  import TypedDisplayRuntimeFixture.SourceBrowser

  val resourceType: String = "collecting-reference"
  val resourceBindings: Vector[DisplayService.ResourceBinding] = Vector(
    DisplayService.ResourceBinding(resourceType, "collecting-resources"),
    DisplayService.ResourceBinding(resourceType, "capture-detail")
  )

  final class ReadComponent(
    rows: Vector[Capture],
    definitions: Vector[Definition] = DomainLogicalUiDefinitions.definitions,
    allKinds: Boolean = false,
    findSource: Option[(EntityId, ExecutionContext) => Consequence[Capture]] = None,
    querySource: Option[ExecutionContext => Consequence[Vector[Capture]]] = None,
    detailRow: Option[DisplayProjection.RowBinding[Capture]] = None
  ) extends Component() with GeneratedLogicalUiMetadataProvider
      with DisplayBindingProvider with DisplayServiceBindingProvider {
    private val _definitions = definitions
    private val _all_kinds = allKinds
    private val _detail_row = detailRow
    private var _metadata_reads = 0
    private var _runtime_reads = 0
    private var _service_reads = 0
    private var _service_saw_unpublished = false
    val browser: SourceBrowser[Capture] = new SourceBrowser(
      findSource.getOrElse((id, _) => rows.find(_.resource.entityId == id)
        .map(Consequence.success).getOrElse(Consequence.argumentInvalid("fixture resource is absent"))),
      (_, context) => querySource.map(_(context)).getOrElse(Consequence.success(rows))
    )

    def metadataReads: Int = _metadata_reads
    def runtimeReads: Int = _runtime_reads
    def serviceReads: Int = _service_reads
    def serviceSawUnpublished: Boolean = _service_saw_unpublished
    def generatedLogicalUiDefinitions: Vector[Definition] = {
      _metadata_reads += 1
      _definitions
    }
    def displayBindings(component: Component): Vector[DisplayRuntime.Binding] = {
      _runtime_reads += 1
      TypedDisplayRuntimeFixture.register(component.viewSpace, "ess-service-source", browser)
      if (_all_kinds) Vector(
        DisplayRuntime.list("collecting-resources", "ess.reference", browser, allKindRow, Vector.empty),
        DisplayRuntime.detail("capture-detail", "ess.reference", browser, allKindRow)
      ) else Vector(
        DisplayRuntime.list("collecting-resources", "ess.reference", browser, TypedDisplayProjectionFixture.listRow, Vector.empty),
        DisplayRuntime.detail("capture-detail", "ess.reference", browser,
          _detail_row.getOrElse(TypedDisplayProjectionFixture.detailRow)))
    }
    def displayServiceBindings(component: Component): Vector[DisplayService.ResourceBinding] = {
      _service_reads += 1
      _service_saw_unpublished = component.displayRuntime.isEmpty &&
        !component.collectionsBootstrapped && !component.protocol.services.services.exists(_.name == "display") &&
        component.viewSpace.containsBrowser(browser)
      resourceBindings
    }
  }

  final case class Session(subsystem: Subsystem, component: ReadComponent) {
    def request(operation: String, screenId: String, resourceId: Option[EntityId] = None): Request =
      DisplayServiceReadFixture.request(component, operation, screenId, resourceId)

    def execute(request: Request): Consequence[OperationResponse] =
      subsystem.executeOperationResponse(request)

    def executeInContext(request: Request, context: ExecutionContext): Consequence[OperationResponse] =
      subsystem.executeOperationResponseInContext(request, context)

    def http(operation: String, screenId: String, resourceId: Option[EntityId] = None): HttpResult = {
      val properties = Map("resourceType" -> resourceType, "screenId" -> screenId,
        "textus.output.shape" -> "envelope") ++ resourceId.map(id => "resourceId" -> id.value)
      val uri = Uri.unsafeFromString(s"/rest/v1/${component.name}/display/$operation.json")
        .withQueryParams(properties)
      val server = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem))
      val response = DisplayServiceHttpRouteFixture.app(server).run(HttpRequest[IO](Method.GET, uri)).unsafeRunSync()
      val bytes = response.body.compile.toVector.unsafeRunSync()
      HttpResult(response.status.code, response.headers.get[`Content-Type`].map(ct => s"${ct.mediaType.mainType}/${ct.mediaType.subType}"),
        new String(bytes.toArray, StandardCharsets.UTF_8), bytes)
    }
    def close(): Unit = subsystem.shutdown()
  }
  final case class HttpResult(status: Int, contentType: Option[String], text: String, bytes: Vector[Byte]) {
    def json: Json = io.circe.parser.parse(text).fold(_ =>
      throw new IllegalStateException("fixture requires JSON response"), value => value)
    def data: Json = json.hcursor.downField("data").focus.getOrElse(Json.Null)
  }

  def request(component: Component, operation: String, screenId: String,
    resourceId: Option[EntityId] = None): Request = Request.of(
    component = component.name, service = "display", operation = operation,
    properties = List(Property("resourceType", resourceType, None), Property("screenId", screenId, None)) ++
      resourceId.map(id => Property("resourceId", id.value, None))
  )

  def initialize(component: Component, subsystem: Subsystem, name: String = "DisplayReadConsumer",
    protocol: Protocol = Protocol.empty, factory: Option[Component.Factory] = None): Component = {
    val componentid = ComponentId("org.goldenport.cncf.test." + name)
    val core = factory match {
      case Some(value) => Component.Core.create(componentid.name, componentid,
        ComponentInstanceId.default(componentid), protocol, value)
      case None => Component.Core.create(componentid.name, componentid,
        ComponentInstanceId.default(componentid), protocol)
    }
    component.initialize(ComponentInit(subsystem, core, ComponentOrigin.Builtin))
  }

  def open(rows: Vector[Capture], allKinds: Boolean = false,
    production: Boolean = false,
    findSource: Option[(EntityId, ExecutionContext) => Consequence[Capture]] = None,
    querySource: Option[ExecutionContext => Consequence[Vector[Capture]]] = None,
    detailRow: Option[DisplayProjection.RowBinding[Capture]] = None): Session = {
    val subsystem = RuntimeBindingAdmissionFixture.admit(TestComponentFactory.subsystemWithConfig(
      Map(RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue(
        if (production) OperationMode.Production.name else OperationMode.Test.name)), name = "display-read-service"))
    val component = new ReadComponent(rows,
      if (allKinds) allKindDefinitions else DomainLogicalUiDefinitions.definitions,
      allKinds, findSource, querySource, detailRow)
    initialize(component, subsystem, protocol = Protocol(handler = ProtocolHandler.default))
    new ComponentFactory().bootstrapC(component).TAKE
    if (production) {
      subsystem.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<display-read-fixture>"),
        subsystemName = subsystem.name, operationAuthorization = Map(
          (component.name + ".display.read") -> OperationAuthorizationRule(requireAuthenticated = true),
          (component.name + ".display.query") -> OperationAuthorizationRule(requireAuthenticated = true))))
    }
    subsystem.add(Vector(component))
    Session(subsystem, component)
  }

  def allKindDefinitions: Vector[Definition] = {
    val fields = Vector(
      FieldDefinition("title", "Title", PresentationRole.Title, ValueDefinition("ess.title", Vector(ValueKind.Text))),
      FieldDefinition("text", "Text", PresentationRole.Content, ValueDefinition("all.text", Vector(ValueKind.Text))),
      FieldDefinition("number", "Number", PresentationRole.Content, ValueDefinition("all.number", Vector(ValueKind.Number))),
      FieldDefinition("boolean", "Boolean", PresentationRole.Content, ValueDefinition("all.boolean", Vector(ValueKind.Boolean))),
      FieldDefinition("timestamp", "Timestamp", PresentationRole.Content, ValueDefinition("all.timestamp", Vector(ValueKind.Timestamp))),
      FieldDefinition("missing", "Missing", PresentationRole.Content, ValueDefinition("all.missing", Vector(ValueKind.Missing)))
    )
    val provenance = SourceProvenance("cncf.public-read-all-kind-fixture", "typed-capture", "fixture-1")
    Vector(
      DomainLogicalUiDefinitions.definitions.head.copy(provenance = provenance,
        screen = ListDefinition("All kinds", fields, DetailTarget("capture-detail"), Vector.empty, Vector.empty)),
      DomainLogicalUiDefinitions.definitions(1).copy(provenance = provenance,
        screen = DetailDefinition("All kinds", Vector(SectionDefinition("main", None, fields)), Vector.empty))
    )
  }
  def allKindRow: DisplayProjection.RowBinding[Capture] = DisplayProjection.RowBinding(
    source => Consequence.success(source.resource), Vector(
      TypedDisplayProjectionFixture.titleBinding,
      DisplayProjection.ValueBinding(Vector("all.text"), source => Consequence.success(Vector(DisplayValue.Text(source.description)))),
      DisplayProjection.ValueBinding(Vector("all.number"), source => Consequence.success(Vector(DisplayValue.Number(source.number)))),
      DisplayProjection.ValueBinding(Vector("all.boolean"), source => Consequence.success(Vector(DisplayValue.Boolean(source.flag)))),
      DisplayProjection.ValueBinding(Vector("all.timestamp"), source => Consequence.success(Vector(DisplayValue.Timestamp(source.instant)))),
      DisplayProjection.ValueBinding(Vector("all.missing"), _ => Consequence.success(Vector(DisplayValue.Missing)))
    ), Vector.empty)
}
