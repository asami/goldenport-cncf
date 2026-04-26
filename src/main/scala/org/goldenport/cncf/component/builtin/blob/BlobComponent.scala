package org.goldenport.cncf.component.builtin.blob

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.datatype.{ContentType, MimeBody}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.{DataType, Multiplicity, ValueDomain, XBlob, XString}
import org.goldenport.value.BaseContent

/*
 * Builtin Blob user-facing component.
 *
 * @since   Apr. 26, 2026
 * @version Apr. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class BlobComponent() extends Component

object BlobComponent {
  trait BlobService {
    def registerBlob(request: RegisterBlobRequest): Consequence[BlobMetadata]
    def readBlob(blobId: BlobId): Consequence[BlobReadOutcome]
    def getBlobMetadata(blobId: BlobId): Consequence[BlobMetadata]
  }

  final case class RegisterBlobRequest(
    blobId: BlobId,
    kind: BlobKind,
    sourceMode: BlobSourceMode,
    filename: Option[String],
    contentType: Option[ContentType],
    payload: Option[BinaryBag],
    externalUrl: Option[String],
    attributes: Map[String, String] = Map.empty
  )

  sealed trait BlobReadOutcome
  object BlobReadOutcome {
    final case class Managed(result: BlobReadResult) extends BlobReadOutcome
    final case class External(metadata: BlobMetadata) extends BlobReadOutcome
  }

  val name: String = "blob"
  val componentId: ComponentId = ComponentId(name)

  object Factory extends Component.SinglePrimaryBundleFactory {
    protected def create_Component(params: ComponentCreate): Component =
      BlobComponent()

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val request = _register_blob_request_definition
      val idRequest = _blob_id_request
      val metadataResponse = spec.ResponseDefinition(result = List(DataType.Named("BlobMetadata")))
      val payloadResponse = spec.ResponseDefinition(result = List(XBlob))
      val register = new RegisterBlobOperationDefinition(request, metadataResponse)
      val read = new ReadBlobOperationDefinition(idRequest, payloadResponse)
      val metadata = new GetBlobMetadataOperationDefinition(idRequest, metadataResponse)
      val service = spec.ServiceDefinition(
        name = "blob",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(register, read, metadata)
        )
      )
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(services = Vector(service)),
        handler = ProtocolHandler.default
      )
      comp.withPort(
        Component.Port.of(
          new DefaultBlobService(
            InMemoryBlobStore(),
            BlobMetadataRepository.inMemory()
          )
        )
      )
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(name, componentId, instanceid, protocol)
    }

    private def _blob_id_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(spec.ParameterDefinition(content = BaseContent.simple("blobId"), kind = spec.ParameterDefinition.Kind.Argument))
      )

    private def _register_blob_request_definition: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          _required_property("sourceMode"),
          _required_property("kind"),
          _optional_property("blobId"),
          _optional_property("filename"),
          _optional_property("contentType"),
          _optional_argument("payload", XBlob),
          _optional_property("externalUrl")
        )
      )

    private def _required_property(name: String): spec.ParameterDefinition =
      _property(name, XString, Multiplicity.One)

    private def _optional_property(
      name: String,
      datatype: org.goldenport.schema.DataType = XString
    ): spec.ParameterDefinition =
      _property(name, datatype, Multiplicity.ZeroOne)

    private def _optional_argument(
      name: String,
      datatype: org.goldenport.schema.DataType = XString
    ): spec.ParameterDefinition =
      _parameter(name, spec.ParameterDefinition.Kind.Argument, datatype, Multiplicity.ZeroOne)

    private def _property(
      name: String,
      datatype: org.goldenport.schema.DataType,
      multiplicity: Multiplicity
    ): spec.ParameterDefinition =
      _parameter(name, spec.ParameterDefinition.Kind.Property, datatype, multiplicity)

    private def _parameter(
      name: String,
      kind: spec.ParameterDefinition.Kind,
      datatype: org.goldenport.schema.DataType,
      multiplicity: Multiplicity
    ): spec.ParameterDefinition =
      spec.ParameterDefinition(
        content = BaseContent.simple(name),
        kind = kind,
        domain = ValueDomain(datatype = datatype, multiplicity = multiplicity)
      )
  }

  private final class DefaultBlobService(
    store: BlobStore,
    repository: BlobMetadataRepository
  ) extends BlobService {
    def registerBlob(request: RegisterBlobRequest): Consequence[BlobMetadata] =
      request.sourceMode match {
        case BlobSourceMode.Managed =>
          _register_managed(request)
        case BlobSourceMode.ExternalUrl =>
          _register_external_url(request)
      }

    def readBlob(blobId: BlobId): Consequence[BlobReadOutcome] =
      repository.get(blobId).flatMap {
        case metadata if metadata.sourceMode == BlobSourceMode.Managed =>
          metadata.storageRef match {
            case Some(ref) => store.get(ref).map(BlobReadOutcome.Managed.apply)
            case None => Consequence.operationIllegal("blob.read_blob", s"managed blob has no storageRef: ${blobId.value}")
          }
        case metadata =>
          Consequence.success(BlobReadOutcome.External(metadata))
      }

    def getBlobMetadata(blobId: BlobId): Consequence[BlobMetadata] =
      repository.get(blobId)

    private def _register_managed(request: RegisterBlobRequest): Consequence[BlobMetadata] =
      request.payload match {
        case Some(payload) =>
          val contentType = request.contentType.getOrElse(ContentType.APPLICATION_OCTET_STREAM)
          store.put(
            BlobPutRequest(
              blobId = request.blobId,
              kind = request.kind,
              filename = request.filename,
              contentType = contentType,
              attributes = request.attributes
            ),
            payload
          ).flatMap { result =>
            val now = result.storedAt
            repository.save(
              BlobMetadata(
                blobId = result.blobId,
                kind = request.kind,
                sourceMode = BlobSourceMode.Managed,
                filename = request.filename,
                contentType = Some(result.contentType),
                byteSize = Some(result.byteSize),
                digest = Some(result.digest),
                storageRef = Some(result.storageRef),
                externalUrl = None,
                accessUrl = result.accessUrl,
                createdAt = now,
                updatedAt = now,
                attributes = request.attributes
              )
            )
          }
        case None =>
          Consequence.argumentMissing("payload")
      }

    private def _register_external_url(request: RegisterBlobRequest): Consequence[BlobMetadata] =
      request.externalUrl match {
        case Some(url) if url.trim.nonEmpty =>
          val now = Instant.now
          repository.save(
            BlobMetadata(
              blobId = request.blobId,
              kind = request.kind,
              sourceMode = BlobSourceMode.ExternalUrl,
              filename = request.filename,
              contentType = request.contentType,
              byteSize = None,
              digest = None,
              storageRef = None,
              externalUrl = Some(url.trim),
              accessUrl = BlobAccessUrl(
                displayUrl = url.trim,
                downloadUrl = url.trim,
                urlSource = BlobAccessUrlSource.Backend
              ),
              createdAt = now,
              updatedAt = now,
              attributes = request.attributes
            )
          )
        case _ =>
          Consequence.argumentMissing("externalUrl")
      }
  }

  private final class RegisterBlobOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "register_blob",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _register_blob_request(req).map(RegisterBlobAction(req, _))
  }

  private final class ReadBlobOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "read_blob",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _blob_id(req).map(ReadBlobAction(req, _))
  }

  private final class GetBlobMetadataOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "get_blob_metadata",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _blob_id(req).map(GetBlobMetadataAction(req, _))
  }

  private final case class RegisterBlobAction(
    request: Request,
    registerRequest: RegisterBlobRequest
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.SyncDirectNoJob

    def createCall(core: ActionCall.Core): ActionCall =
      RegisterBlobActionCall(core, registerRequest)
  }

  private final case class ReadBlobAction(
    request: Request,
    blobId: BlobId
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      ReadBlobActionCall(core, blobId)
  }

  private final case class GetBlobMetadataAction(
    request: Request,
    blobId: BlobId
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetBlobMetadataActionCall(core, blobId)
  }

  private final case class RegisterBlobActionCall(
    core: ActionCall.Core,
    registerRequest: RegisterBlobRequest
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.registerBlob(registerRequest)).map { metadata =>
        OperationResponse.RecordResponse(metadata.toRecord)
      }
  }

  private final case class ReadBlobActionCall(
    core: ActionCall.Core,
    blobId: BlobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.readBlob(blobId)).map {
        case BlobReadOutcome.Managed(result) =>
          OperationResponse.Http(
            HttpResponse.Binary(
              HttpStatus.Ok,
              result.contentType,
              result.payload
            )
          )
        case BlobReadOutcome.External(metadata) =>
          OperationResponse.Http(
            HttpResponse.Text(
              HttpStatus.SeeOther,
              ContentType.TEXT_PLAIN,
              Bag.text(metadata.accessUrl.displayUrl),
              Record.data("Location" -> metadata.accessUrl.displayUrl)
            )
          )
      }
  }

  private final case class GetBlobMetadataActionCall(
    core: ActionCall.Core,
    blobId: BlobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.getBlobMetadata(blobId)).map { metadata =>
        OperationResponse.RecordResponse(metadata.toRecord)
      }
  }

  private def _service(core: ActionCall.Core): Consequence[BlobService] =
    core.component.flatMap(_.port.get[BlobService]) match {
      case Some(service) => Consequence.success(service)
      case None => Consequence.serviceUnavailable("blob service is not available")
    }

  private def _register_blob_request(req: Request): Consequence[RegisterBlobRequest] =
    for {
      sourceMode <- _string(req, "sourceMode", "source_mode").map(BlobSourceMode.parse).getOrElse(Consequence.argumentMissing("sourceMode"))
      kind <- _string(req, "kind").map(BlobKind.parse).getOrElse(Consequence.argumentMissing("kind"))
      blobId = _string(req, "blobId", "blob_id", "id").map(BlobId.apply).getOrElse(BlobId(UUID.randomUUID()))
      filename = _string(req, "filename", "fileName")
      mimeBody = _any(req, "payload", "body", "file").collect { case m: MimeBody => m }
      contentType = mimeBody.map(_.contentType).orElse(_string(req, "contentType", "content_type").map(ContentType.parse))
      payload <- _payload(req, mimeBody)
    } yield RegisterBlobRequest(
      blobId = blobId,
      kind = kind,
      sourceMode = sourceMode,
      filename = filename,
      contentType = contentType,
      payload = payload,
      externalUrl = _string(req, "externalUrl", "external_url", "url"),
      attributes = Map.empty
    )

  private def _payload(
    req: Request,
    mimeBody: Option[MimeBody]
  ): Consequence[Option[BinaryBag]] =
    mimeBody match {
      case Some(m) => Consequence.success(Some(m.value.promoteToBinary()))
      case None =>
        _any(req, "payload", "body", "file") match {
          case Some(m: BinaryBag) => Consequence.success(Some(m))
          case Some(m: Bag) => Consequence.success(Some(m.promoteToBinary()))
          case Some(bytes: Array[Byte]) => Consequence.success(Some(Bag.binary(bytes)))
          case Some(text: String) => Consequence.success(Some(Bag.binary(text.getBytes(StandardCharsets.UTF_8))))
          case Some(other) => Consequence.argumentInvalid(s"unsupported blob payload type: ${other.getClass.getName}")
          case None => Consequence.success(None)
        }
    }

  private def _blob_id(req: Request): Consequence[BlobId] =
    _string(req, "blobId", "blob_id", "id") match {
      case Some(value) => Consequence.success(BlobId(value))
      case None => Consequence.argumentMissing("blobId")
    }

  private def _string(req: Request, names: String*): Option[String] =
    _any(req, names*).map(_.toString).map(_.trim).filter(_.nonEmpty)

  private def _any(req: Request, names: String*): Option[Any] = {
    val params = req.arguments ++ req.properties
    names.iterator.flatMap { name =>
      params.collectFirst {
        case p if p.name.equalsIgnoreCase(name) => p.value
      }
    }.nextOption()
  }
}
