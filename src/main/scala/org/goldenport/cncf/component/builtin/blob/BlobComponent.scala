package org.goldenport.cncf.component.builtin.blob

import java.nio.charset.StandardCharsets
import java.util.UUID
import cats.free.Free
import cats.data.NonEmptyVector
import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceException, ConsequenceT, Conclusion}
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.cncf.action.{ActionCall, ActionCallFeaturePart, CommandAction, CommandExecutionMode, FunctionalActionCall, QueryAction}
import org.goldenport.cncf.association.{Association, AssociationCreate, AssociationDomain, AssociationFilter, AssociationRecordCodec, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentDescriptor, ComponentId, ComponentInit, ComponentInstanceId}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{EntityCreateOptions, EntityPersistent, EntityPersistentCreate, EntityQuery, EntitySearchScope}
import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, EntityRuntimeDescriptor, PartitionStrategy, WorkingSetPolicy, WorkingSetPolicySource}
import org.goldenport.cncf.security.{AdminAuthorizationPolicy, EntityAccessMode, OperationAuthorizationProvider, OperationAuthorizationRule}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkAuthorization, UnitOfWorkOp}
import org.goldenport.datatype.{ContentType, MimeBody}
import org.goldenport.http.{HttpResponse, HttpStatus}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.{Column, DataType, Multiplicity, Schema, ValueDomain, XBlob, XBoolean, XInt, XLong, XString}
import org.goldenport.observation.Descriptor
import org.goldenport.value.BaseContent
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * Builtin Blob user-facing component.
 *
 * @since   Apr. 26, 2026
 * @version Apr. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class BlobComponent() extends Component {
  override protected def initialize_Component(params: ComponentInit): Unit =
    withComponentDescriptors(componentDescriptors ++ BlobComponent.componentDescriptors)
}

object BlobComponent {
  trait BlobService {
    def blobStore: BlobStore
  }

  final case class RegisterBlobRequest(
    id: EntityId,
    kind: BlobKind,
    sourceMode: BlobSourceMode,
    filename: Option[String],
    contentType: Option[ContentType],
    payload: Option[BinaryBag],
    externalUrl: Option[String],
    expectedByteSize: Option[Long] = None,
    expectedDigest: Option[String] = None,
    attributes: Map[String, String] = Map.empty
  )

  final case class AttachBlobRequest(
    sourceEntityId: String,
    id: EntityId,
    role: String,
    sortOrder: Option[Int]
  )

  final case class DetachBlobRequest(
    sourceEntityId: String,
    id: EntityId,
    role: Option[String]
  )

  final case class ListEntityBlobsRequest(
    sourceEntityId: String,
    role: Option[String]
  )

  final case class AdminPageRequest(
    offset: Int,
    limit: Int
  ) {
    def fetchLimit: Int = limit + 1
  }

  object AdminPageRequest {
    val DefaultLimit: Int = 100
    val MaxLimit: Int = 500
  }

  final case class AdminListBlobAssociationsRequest(
    sourceEntityId: Option[String],
    id: Option[EntityId],
    role: Option[String],
    page: AdminPageRequest
  )

  final case class AdminDeleteBlobRequest(
    id: EntityId,
    force: Boolean
  )

  sealed trait BlobReadOutcome
  object BlobReadOutcome {
    final case class Managed(result: BlobReadResult) extends BlobReadOutcome
  }

  val name: String = "blob"
  val componentId: ComponentId = ComponentId(name)
  val BlobCollectionId: EntityCollectionId = BlobRepository.CollectionId
  private val BlobStoreResourceName: String = "blobstore"

  def componentDescriptors: Vector[ComponentDescriptor] =
    Vector(ComponentDescriptor(
      componentName = Some(name),
      entityRuntimeDescriptors = Vector(
        EntityRuntimeDescriptor(
          entityName = "blob",
          collectionId = BlobCollectionId,
          memoryPolicy = EntityMemoryPolicy.StoreOnly,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 1000,
          workingSetPolicy = Some(WorkingSetPolicy.Disabled),
          workingSetPolicySource = Some(WorkingSetPolicySource.Code),
          schema = Some(_blob_schema)
        )
      )
    ))

  object Factory extends Component.SinglePrimaryBundleFactory {
    protected def create_Component(params: ComponentCreate): Component =
      BlobComponent()

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val request = _register_blob_request_definition
      val emptyRequest = spec.RequestDefinition()
      val idRequest = _id_request
      val attachRequest = _attach_blob_request_definition
      val detachRequest = _detach_blob_request_definition
      val listRequest = _list_entity_blobs_request_definition
      val adminListRequest = _admin_list_blobs_request_definition
      val adminAssociationRequest = _admin_list_blob_associations_request_definition
      val adminDeleteRequest = _admin_delete_blob_request_definition
      val metadataResponse = spec.ResponseDefinition(result = List(DataType.Named("BlobMetadata")))
      val urlResponse = spec.ResponseDefinition(result = List(DataType.Named("BlobAccessUrl")))
      val payloadResponse = spec.ResponseDefinition(result = List(XBlob))
      val recordResponse = spec.ResponseDefinition(result = List(DataType.Named("Record")))
      val register = new RegisterBlobOperationDefinition(request, metadataResponse)
      val read = new ReadBlobOperationDefinition(idRequest, payloadResponse)
      val resolve = new ResolveBlobUrlOperationDefinition(idRequest, urlResponse)
      val metadata = new GetBlobMetadataOperationDefinition(idRequest, metadataResponse)
      val attach = new AttachBlobToEntityOperationDefinition(attachRequest, recordResponse)
      val detach = new DetachBlobFromEntityOperationDefinition(detachRequest, recordResponse)
      val list = new ListEntityBlobsOperationDefinition(listRequest, recordResponse)
      val adminList = new AdminListBlobsOperationDefinition(adminListRequest, recordResponse)
      val adminGet = new AdminGetBlobOperationDefinition(idRequest, metadataResponse)
      val adminAssociations = new AdminListBlobAssociationsOperationDefinition(adminAssociationRequest, recordResponse)
      val adminStatus = new AdminBlobStoreStatusOperationDefinition(emptyRequest, recordResponse)
      val adminDelete = new AdminDeleteBlobOperationDefinition(adminDeleteRequest, recordResponse)
      val adminAttach = new AdminAttachBlobToEntityOperationDefinition(attachRequest, recordResponse)
      val adminDetach = new AdminDetachBlobFromEntityOperationDefinition(detachRequest, recordResponse)
      val service = spec.ServiceDefinition(
        name = "blob",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            register,
            read,
            resolve,
            metadata,
            attach,
            detach,
            list,
            adminList,
            adminGet,
            adminAssociations,
            adminStatus,
            adminDelete,
            adminAttach,
            adminDetach
          )
        )
      )
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(services = Vector(service)),
        handler = ProtocolHandler.default
      )
      comp.withPort(
        Component.Port.of(
          new DefaultBlobService(_blob_store(params))
        )
      )
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(name, componentId, instanceid, protocol)
    }

    private def _blob_store(params: ComponentCreate): BlobStore =
      BlobStoreFactory.create(RuntimeConfig.from(params.subsystem.configuration).blobStoreConfig) match {
        case Consequence.Success(store) =>
          store
        case failure @ Consequence.Failure(_) =>
          throw new ConsequenceException(failure)
      }

    private def _id_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(spec.ParameterDefinition(content = BaseContent.simple("id"), kind = spec.ParameterDefinition.Kind.Argument))
      )

    private def _register_blob_request_definition: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          _required_property("sourceMode"),
          _required_property("kind"),
          _optional_property("filename"),
          _optional_property("contentType"),
          _optional_argument("payload", XBlob),
          _optional_property("externalUrl"),
          _optional_property("expectedByteSize", XLong),
          _optional_property("expectedDigest")
        )
      )

    private def _attach_blob_request_definition: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          _required_property("sourceEntityId"),
          _required_property("id"),
          _required_property("role"),
          _optional_property("sortOrder", XInt)
        )
      )

    private def _detach_blob_request_definition: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          _required_property("sourceEntityId"),
          _required_property("id"),
          _optional_property("role")
        )
      )

    private def _list_entity_blobs_request_definition: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          _required_property("sourceEntityId"),
          _optional_property("role")
        )
      )

    private def _admin_list_blobs_request_definition: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          _optional_property("offset", XInt),
          _optional_property("limit", XInt)
        )
      )

    private def _admin_list_blob_associations_request_definition: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          _optional_property("sourceEntityId"),
          _optional_property("id"),
          _optional_property("role"),
          _optional_property("offset", XInt),
          _optional_property("limit", XInt)
        )
      )

    private def _admin_delete_blob_request_definition: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(content = BaseContent.simple("id"), kind = spec.ParameterDefinition.Kind.Argument),
          _optional_property("force", XBoolean)
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

  private[blob] final class DefaultBlobService(
    store: BlobStore
  ) extends BlobService {
    def blobStore: BlobStore = store
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
      _id(req).map(ReadBlobAction(req, _))
  }

  private final class ResolveBlobUrlOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "resolve_blob_url",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _id(req).map(ResolveBlobUrlAction(req, _))
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
      _id(req).map(GetBlobMetadataAction(req, _))
  }

  private final class AttachBlobToEntityOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "attach_blob_to_entity",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _attach_blob_request(req).map(AttachBlobToEntityAction(req, _))
  }

  private final class DetachBlobFromEntityOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "detach_blob_from_entity",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _detach_blob_request(req).map(DetachBlobFromEntityAction(req, _))
  }

  private final class ListEntityBlobsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "list_entity_blobs",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _list_entity_blobs_request(req).map(ListEntityBlobsAction(req, _))
  }

  private trait BlobAdminOperationAuthorization extends OperationAuthorizationProvider {
    def operationAuthorization(
      runtimeConfig: RuntimeConfig
    ): OperationAuthorizationRule =
      AdminAuthorizationPolicy.operationRule("admin.entity.blob", runtimeConfig)
  }

  private final class AdminListBlobsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition with BlobAdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admin_list_blobs",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _admin_page_request(req).map(AdminListBlobsAction(req, _))
  }

  private final class AdminGetBlobOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition with BlobAdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admin_get_blob",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _id(req).map(AdminGetBlobAction(req, _))
  }

  private final class AdminListBlobAssociationsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition with BlobAdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admin_list_blob_associations",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _admin_list_blob_associations_request(req).map(AdminListBlobAssociationsAction(req, _))
  }

  private final class AdminBlobStoreStatusOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition with BlobAdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admin_blob_store_status",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(AdminBlobStoreStatusAction(req))
  }

  private final class AdminDeleteBlobOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition with BlobAdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admin_delete_blob",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _admin_delete_blob_request(req).map(AdminDeleteBlobAction(req, _))
  }

  private final class AdminAttachBlobToEntityOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition with BlobAdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admin_attach_blob_to_entity",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _attach_blob_request(req).map(AdminAttachBlobToEntityAction(req, _))
  }

  private final class AdminDetachBlobFromEntityOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition with BlobAdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admin_detach_blob_from_entity",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _detach_blob_request(req).map(AdminDetachBlobFromEntityAction(req, _))
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
    id: EntityId
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      ReadBlobActionCall(core, id)
  }

  private final case class ResolveBlobUrlAction(
    request: Request,
    id: EntityId
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      ResolveBlobUrlActionCall(core, id)
  }

  private final case class GetBlobMetadataAction(
    request: Request,
    id: EntityId
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetBlobMetadataActionCall(core, id)
  }

  private final case class AttachBlobToEntityAction(
    request: Request,
    attachRequest: AttachBlobRequest
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.SyncDirectNoJob

    def createCall(core: ActionCall.Core): ActionCall =
      AttachBlobToEntityActionCall(core, attachRequest)
  }

  private final case class DetachBlobFromEntityAction(
    request: Request,
    detachRequest: DetachBlobRequest
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.SyncDirectNoJob

    def createCall(core: ActionCall.Core): ActionCall =
      DetachBlobFromEntityActionCall(core, detachRequest)
  }

  private final case class ListEntityBlobsAction(
    request: Request,
    listRequest: ListEntityBlobsRequest
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      ListEntityBlobsActionCall(core, listRequest)
  }

  private final case class AdminListBlobsAction(
    request: Request,
    page: AdminPageRequest
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      AdminListBlobsActionCall(core, page)
  }

  private final case class AdminGetBlobAction(
    request: Request,
    id: EntityId
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      AdminGetBlobActionCall(core, id)
  }

  private final case class AdminListBlobAssociationsAction(
    request: Request,
    listRequest: AdminListBlobAssociationsRequest
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      AdminListBlobAssociationsActionCall(core, listRequest)
  }

  private final case class AdminBlobStoreStatusAction(
    request: Request
  ) extends QueryAction {
    def createCall(core: ActionCall.Core): ActionCall =
      AdminBlobStoreStatusActionCall(core)
  }

  private final case class AdminDeleteBlobAction(
    request: Request,
    deleteRequest: AdminDeleteBlobRequest
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.SyncDirectNoJob

    def createCall(core: ActionCall.Core): ActionCall =
      AdminDeleteBlobActionCall(core, deleteRequest)
  }

  private final case class AdminAttachBlobToEntityAction(
    request: Request,
    attachRequest: AttachBlobRequest
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.SyncDirectNoJob

    def createCall(core: ActionCall.Core): ActionCall =
      AdminAttachBlobToEntityActionCall(core, attachRequest)
  }

  private final case class AdminDetachBlobFromEntityAction(
    request: Request,
    detachRequest: DetachBlobRequest
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.SyncDirectNoJob

    def createCall(core: ActionCall.Core): ActionCall =
      AdminDetachBlobFromEntityActionCall(core, detachRequest)
  }

  private final case class RegisterBlobActionCall(
    core: ActionCall.Core,
    registerRequest: RegisterBlobRequest
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      _register_blob(registerRequest).map { metadata =>
        OperationResponse.RecordResponse(metadata.toRecord)
      }
  }

  private final case class ReadBlobActionCall(
    core: ActionCall.Core,
    id: EntityId
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      for {
        blob <- _blob_load(id)
        result <- blob.sourceMode match {
          case BlobSourceMode.Managed =>
            blob.storageRef match {
              case Some(ref) =>
                for {
                  store <- exec_from(_blob_store)
                  result <- exec_from(store.get(ref))
                } yield result
              case None => exec_from(Consequence.operationIllegal("blob.read_blob", s"managed blob has no storageRef: ${id.value}"))
            }
          case _ =>
            exec_from(Consequence.operationIllegal("blob.read_blob", s"${blob.sourceMode.print} blob has no managed payload; use resolve_blob_url: ${id.value}"))
        }
      } yield OperationResponse.Http(
        HttpResponse.Binary(
          HttpStatus.Ok,
          result.contentType,
          result.payload
        )
      )
  }

  private final case class ResolveBlobUrlActionCall(
    core: ActionCall.Core,
    id: EntityId
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      _blob_load(id).flatMap(blob => exec_from(_blob_access_url_record(blob.metadata))).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class GetBlobMetadataActionCall(
    core: ActionCall.Core,
    id: EntityId
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      _blob_load(id).map { blob =>
        OperationResponse.RecordResponse(blob.metadata.toRecord)
      }
  }

  private final case class AttachBlobToEntityActionCall(
    core: ActionCall.Core,
    attachRequest: AttachBlobRequest
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      _attach_blob_to_entity(attachRequest).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class DetachBlobFromEntityActionCall(
    core: ActionCall.Core,
    detachRequest: DetachBlobRequest
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      _detach_blob_from_entity(detachRequest).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class ListEntityBlobsActionCall(
    core: ActionCall.Core,
    listRequest: ListEntityBlobsRequest
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      _list_entity_blobs(listRequest).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class AdminListBlobsActionCall(
    core: ActionCall.Core,
    page: AdminPageRequest
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      _authorize_blob_collection_access("search/list").flatMap { _ =>
        _blob_search(page, system = true)
      }.map { values =>
        val rows = values.take(page.limit)
        OperationResponse.RecordResponse(Record.dataAuto(
          "data" -> rows.map(_.metadata.toRecord),
          "offset" -> page.offset,
          "limit" -> page.limit,
          "fetchedCount" -> rows.size,
          "hasMore" -> (values.size > page.limit)
        ))
      }
  }

  private final case class AdminGetBlobActionCall(
    core: ActionCall.Core,
    id: EntityId
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      _authorize_blob_collection_access("read").flatMap { _ =>
        _blob_load(id, system = true)
      }.map { blob =>
        OperationResponse.RecordResponse(blob.metadata.toRecord)
      }
  }

  private final case class AdminListBlobAssociationsActionCall(
    core: ActionCall.Core,
    listRequest: AdminListBlobAssociationsRequest
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      _authorize_blob_attachment_access("search/list").flatMap { _ =>
        _association_search(_blob_admin_association_filter(listRequest), listRequest.page.offset, Some(listRequest.page.fetchLimit), system = true)
      }.map { values =>
        val rows = values.take(listRequest.page.limit)
        OperationResponse.RecordResponse(Record.dataAuto(
          "data" -> rows.map(AssociationRecordCodec.toRecord),
          "offset" -> listRequest.page.offset,
          "limit" -> listRequest.page.limit,
          "fetchedCount" -> rows.size,
          "hasMore" -> (values.size > listRequest.page.limit)
        ))
      }
  }

  private final case class AdminBlobStoreStatusActionCall(
    core: ActionCall.Core
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      for {
        _ <- _authorize_blob_store_access("status")
        store <- exec_from(_blob_store)
        record <- exec_from(store.status().map(_blob_store_status_record))
      } yield {
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class AdminDeleteBlobActionCall(
    core: ActionCall.Core,
    deleteRequest: AdminDeleteBlobRequest
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      _admin_delete_blob(deleteRequest).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class AdminAttachBlobToEntityActionCall(
    core: ActionCall.Core,
    attachRequest: AttachBlobRequest
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      _attach_blob_to_entity(attachRequest, system = true).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class AdminDetachBlobFromEntityActionCall(
    core: ActionCall.Core,
    detachRequest: DetachBlobRequest
  ) extends FunctionalActionCall with ActionCall.Core.Holder with BlobActionCallSupport {
    protected def build_Program: ExecUowM[OperationResponse] =
      _detach_blob_from_entity(detachRequest, system = true).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private trait BlobActionCallSupport extends ActionCallFeaturePart { self: FunctionalActionCall & ActionCall.Core.Holder =>
    protected final def _register_blob(
      request: RegisterBlobRequest
    ): ExecUowM[BlobMetadata] =
      request.sourceMode match {
        case BlobSourceMode.Managed => _register_managed_blob(request)
        case BlobSourceMode.ExternalUrl => _register_external_url_blob(request)
      }

    private def _register_managed_blob(
      request: RegisterBlobRequest
    ): ExecUowM[BlobMetadata] =
      request.payload match {
        case Some(payload) =>
          val contentType = request.contentType.getOrElse(ContentType.APPLICATION_OCTET_STREAM)
          for {
            _ <- exec_from(_validate_managed_request(request))
            _ <- _authorize_blob_create(request.id.collection, system = false)
            store <- exec_from(_blob_store)
            result <- exec_from(store.put(
              BlobPutRequest(
                id = request.id,
                kind = request.kind,
                filename = request.filename,
                contentType = contentType,
                attributes = request.attributes
              ),
              payload
            ))
            _ <- _recover_with(exec_from(_validate_managed_result(request, result))) { conclusion =>
              _delete_payload_then_fail(store, result.storageRef, conclusion)
            }
            blob <- _blob_create_managed(
              BlobCreate(
                id = result.id,
                kind = request.kind,
                sourceMode = BlobSourceMode.Managed,
                filename = request.filename,
                contentType = Some(result.contentType),
                byteSize = Some(result.byteSize),
                digest = Some(result.digest),
                storageRef = Some(result.storageRef),
                externalUrl = None,
                accessUrl = _managed_blob_access_url(result),
                attributes = request.attributes
              ),
              store,
              result.storageRef
            )
            metadata = blob.metadata
          } yield metadata
        case None =>
          exec_from(Consequence.argumentMissing("payload"))
      }

    private def _register_external_url_blob(
      request: RegisterBlobRequest
    ): ExecUowM[BlobMetadata] =
      request.externalUrl match {
        case Some(url) if url.trim.nonEmpty =>
          for {
            _ <- exec_from(_validate_external_request(request))
            _ <- _authorize_blob_create(request.id.collection, system = false)
            normalized <- exec_from(BlobExternalUrlPolicy.normalize(url))
            blob <- _blob_create(BlobCreate(
              id = request.id,
              kind = request.kind,
              sourceMode = BlobSourceMode.ExternalUrl,
              filename = request.filename,
              contentType = request.contentType,
              byteSize = None,
              digest = None,
              storageRef = None,
              externalUrl = Some(normalized),
              accessUrl = BlobAccessUrl(
                displayUrl = normalized,
                downloadUrl = normalized,
                urlSource = BlobAccessUrlSource.Backend
              ),
              attributes = request.attributes
            ))
          } yield blob.metadata
        case _ =>
          exec_from(Consequence.argumentMissing("externalUrl"))
      }

    private def _managed_blob_access_url(
      result: BlobPutResult
    ): BlobAccessUrl =
      if (result.accessUrl.urlSource == BlobAccessUrlSource.Backend)
        result.accessUrl
      else
        BlobUrl.cncfRoute(result.id)

    protected final def _blob_load(id: EntityId, system: Boolean = false): ExecUowM[Blob] = {
      import BlobRepository.given
      val op = UnitOfWorkOp.EntityStoreLoad(
        id,
        summon[EntityPersistent[Blob]],
        Some(_authorization(id.collection, Some(id), "read", system))
      )
      _exec_uow(op).flatMap(x => exec_from(Consequence.successOrEntityNotFound(x)(id)))
    }

    protected final def _blob_create(create: BlobCreate, system: Boolean = false): ExecUowM[Blob] = {
      import BlobRepository.given
      val op = UnitOfWorkOp.EntityStoreCreate(
        create,
        summon[EntityPersistentCreate[BlobCreate]],
        EntityCreateOptions.default,
        Some(_authorization(create.id.collection, None, "create", system))
      )
      _exec_uow(op).flatMap(result => _blob_from_create_result(result))
    }

    private def _blob_create_managed(
      create: BlobCreate,
      store: BlobStore,
      storageRef: BlobStorageRef
    ): ExecUowM[Blob] = {
      import BlobRepository.given
      val op = UnitOfWorkOp.EntityStoreCreate(
        create,
        summon[EntityPersistentCreate[BlobCreate]],
        EntityCreateOptions.default,
        Some(_authorization(create.id.collection, None, "create", system = false))
      )
      _recover_with(_exec_uow(op)) { conclusion =>
        _delete_payload_then_fail(store, storageRef, conclusion)
      }.flatMap(result => _blob_from_create_result(result, Some((store, storageRef))))
    }

    private def _blob_from_create_result(
      result: org.goldenport.cncf.entity.CreateResult[BlobCreate],
      managedPayload: Option[(BlobStore, BlobStorageRef)] = None
    ): ExecUowM[Blob] = {
      import BlobRepository.given
      result.record match {
        case Some(record) =>
          _recover_with(
            exec_from(summon[EntityPersistent[Blob]].fromStoreRecord(record))
          ) { conclusion =>
            _cleanup_created_blob_after_decode_failure(result.id, managedPayload, conclusion)
          }
        case None =>
          _cleanup_created_blob_after_decode_failure(
            result.id,
            managedPayload,
            Consequence.operationIllegal[Blob](
              "blob.register_blob",
              s"Blob metadata create returned no storage record: ${result.id.value}"
            ).conclusion
          )
      }
    }

    private def _cleanup_created_blob_after_decode_failure[A](
      id: EntityId,
      managedPayload: Option[(BlobStore, BlobStorageRef)],
      conclusion: Conclusion
    ): ExecUowM[A] =
      managedPayload match {
        case Some((store, ref)) =>
          _blob_delete(id, system = true).flatMap { _ =>
            _delete_payload_then_fail(store, ref, conclusion)
          }
        case None =>
          _ignore_failure(_blob_delete(id, system = true)).flatMap { _ =>
            exec_from(Consequence.Failure[A](conclusion))
          }
      }

    private def _delete_payload_then_fail[A](
      store: BlobStore,
      ref: BlobStorageRef,
      conclusion: Conclusion
    ): ExecUowM[A] =
      exec_from(store.delete(ref).flatMap(_ => Consequence.Failure[A](conclusion)))

    protected final def _blob_search(page: AdminPageRequest, system: Boolean): ExecUowM[Vector[Blob]] = {
      import BlobRepository.given
      val query = EntityQuery[Blob](
        BlobCollectionId,
        Query.plan(Record.empty, limit = Some(page.fetchLimit), offset = Some(page.offset)),
        EntitySearchScope.Store
      )
      val op = UnitOfWorkOp.EntityStoreSearch(
        query,
        summon[EntityPersistent[Blob]],
        Some(_authorization(BlobCollectionId, None, "search/list", system))
      )
      _exec_uow(op).map(_.data)
    }

    protected final def _attach_blob_to_entity(
      request: AttachBlobRequest,
      system: Boolean = false
    ): ExecUowM[Record] =
      _authorize_source_entity(request.sourceEntityId, "update", system).flatMap { _ =>
        _blob_load(request.id, system).flatMap { blob =>
          _authorize_blob_attachment_access("create").flatMap { _ =>
            val filter = _blob_association_filter(request.sourceEntityId, Some(request.role), Some(blob.id))
            _association_search(filter, 0, None, system = true).flatMap {
              case existing +: _ =>
                exec_pure(AssociationRecordCodec.toRecord(existing))
              case _ =>
                _association_create(
                  AssociationCreate(
                    id = None,
                    associationId = UUID.randomUUID().toString,
                    sourceEntityId = request.sourceEntityId,
                    targetEntityId = blob.id.value,
                    targetKind = Some("blob"),
                    role = request.role,
                    associationDomain = AssociationDomain.BlobAttachment,
                    sortOrder = request.sortOrder,
                    collectionId = AssociationStoragePolicy.BlobAttachmentCollection
                  ),
                  system = true
                ).map(AssociationRecordCodec.toRecord)
            }
          }
        }
      }

    protected final def _detach_blob_from_entity(
      request: DetachBlobRequest,
      system: Boolean = false
    ): ExecUowM[Record] =
      _authorize_source_entity(request.sourceEntityId, "update", system).flatMap { _ =>
        _authorize_blob_attachment_access("delete").flatMap { _ =>
          _association_search(_blob_association_filter(request.sourceEntityId, request.role, Some(request.id)), 0, None, system = true).flatMap {
            case Vector() => exec_from(Consequence.operationNotFound(s"blob association:${request.sourceEntityId}:${request.id.value}"))
            case values =>
              values.foldLeft(exec_pure(0)) { (z, association) =>
                z.flatMap(count => _association_delete(association, system = true).map(_ => count + 1))
              }.map(count => Record.dataAuto("detachedCount" -> count))
          }
        }
      }

    protected final def _list_entity_blobs(
      request: ListEntityBlobsRequest,
      system: Boolean = false
    ): ExecUowM[Record] =
      _authorize_source_entity(request.sourceEntityId, "read", system).flatMap { _ =>
        _authorize_blob_attachment_access("search/list").flatMap { _ =>
          _association_search(_blob_association_filter(request.sourceEntityId, request.role, None), 0, None, system = true).flatMap { values =>
            values.foldLeft(exec_pure(Vector.empty[BlobMetadata])) { (z, association) =>
              z.flatMap { acc =>
                exec_from(EntityId.parse(association.targetEntityId)).flatMap(id => _blob_metadata_if_visible(id, system)).map {
                  case Some(metadata) => acc :+ metadata
                  case None => acc
                }
              }
            }.map { metadata =>
              Record.dataAuto(
                "data" -> metadata.map(_.toRecord),
                "fetchedCount" -> metadata.size
              )
            }
          }
        }
      }

    protected final def _admin_delete_blob(
      request: AdminDeleteBlobRequest
    ): ExecUowM[Record] =
      for {
        _ <- _authorize_blob_collection_access("delete")
        blob <- _blob_load(request.id, system = true)
        refs <- _association_search(_blob_target_association_filter(blob.id), 0, None, system = true)
        _ <- if (refs.nonEmpty && !request.force)
          exec_from(Consequence.operationConflict(
            "admin_delete_blob",
            Seq(Descriptor.Facet.Message(s"blob is still attached: ${blob.id.value}; associationCount=${refs.size}"))
          ))
        else
          exec_pure(())
        _ <- _blob_delete(blob.id, system = true)
        deletedassociations <- refs.foldLeft(exec_pure(0)) { (z, association) =>
          z.flatMap(count => _association_delete(association, system = true).map(_ => count + 1))
        }
        payloaddeleted <- _delete_blob_payload(blob)
      } yield Record.dataAuto(
        "deletedBlobId" -> blob.id.value,
        "deletedAssociationCount" -> deletedassociations,
        "payloadDeleted" -> payloaddeleted,
        "sourceMode" -> blob.sourceMode.print
      )

    private def _blob_delete(id: EntityId, system: Boolean): ExecUowM[Unit] = {
      _exec_uow(UnitOfWorkOp.EntityStoreDelete(
        id,
        Some(_authorization(id.collection, Some(id), "delete", system))
      ))
    }

    private def _association_create(create: AssociationCreate, system: Boolean): ExecUowM[Association] = {
      import AssociationRepository.given
      val op = UnitOfWorkOp.EntityStoreCreate(
        create,
        summon[EntityPersistentCreate[AssociationCreate]],
        EntityCreateOptions.default,
        Some(_association_authorization(create.associationDomain, create.collectionId, None, "create", system))
      )
      _exec_uow(op).flatMap(result => _association_load(result.id, system))
    }

    private def _association_load(id: EntityId, system: Boolean): ExecUowM[Association] = {
      import AssociationRepository.given
      val op = UnitOfWorkOp.EntityStoreLoad(
        id,
        summon[EntityPersistent[Association]],
        Some(_association_authorization(AssociationDomain.BlobAttachment, id.collection, Some(id), "read", system))
      )
      _exec_uow(op).flatMap(x => exec_from(Consequence.successOrEntityNotFound(x)(id)))
    }

    private def _blob_metadata_if_visible(
      id: EntityId,
      system: Boolean
    ): ExecUowM[Option[BlobMetadata]] =
      ConsequenceT(_blob_load(id, system).value.map {
        case Consequence.Success(blob) =>
          Consequence.success(Some(blob.metadata))
        case Consequence.Failure(conclusion) if _is_permission_denied(conclusion) =>
          Consequence.success(None)
        case Consequence.Failure(conclusion) =>
          Consequence.Failure(conclusion)
      })

    protected final def _association_search(
      filter: AssociationFilter,
      offset: Int,
      limit: Option[Int],
      system: Boolean
    ): ExecUowM[Vector[Association]] = {
      import AssociationRepository.given
      val collection = AssociationStoragePolicy.blobAttachmentDefault.collection(filter.domain)
      val query = EntityQuery[Association](
        collection,
        Query.plan(_association_query_record(filter)),
        EntitySearchScope.Store
      )
      val op = UnitOfWorkOp.EntityStoreSearch(
        query,
        summon[EntityPersistent[Association]],
        Some(_association_authorization(filter.domain, collection, None, "search/list", system))
      )
      _exec_uow(op).map { result =>
        val sorted = result.data.sortBy(x => (x.sortOrder.getOrElse(Int.MaxValue), x.createdAt.toString, x.associationId))
        Query.sliceValues(sorted, Some(offset), limit)
      }
    }

    private def _association_delete(association: Association, system: Boolean): ExecUowM[Unit] = {
      _exec_uow(UnitOfWorkOp.EntityStoreDelete(
        association.id,
        Some(_association_authorization(association.associationDomain, association.id.collection, Some(association.id), "delete", system))
      ))
    }

    private def _authorize_blob_create(
      collection: EntityCollectionId,
      system: Boolean
    ): ExecUowM[Unit] =
      _exec_uow(UnitOfWorkOp.Authorize(_authorization(collection, None, "create", system)))

    protected final def _authorize_blob_collection_access(
      accessKind: String
    ): ExecUowM[Unit] =
      _exec_uow(UnitOfWorkOp.Authorize(_authorization(BlobCollectionId, None, accessKind, system = false)))

    protected final def _authorize_blob_attachment_access(
      accessKind: String
    ): ExecUowM[Unit] =
      _exec_uow(UnitOfWorkOp.Authorize(_association_authorization(
        AssociationDomain.BlobAttachment,
        AssociationStoragePolicy.BlobAttachmentCollection,
        None,
        accessKind,
        system = false
      )))

    protected final def _authorize_blob_store_access(
      accessKind: String
    ): ExecUowM[Unit] =
      _exec_uow(UnitOfWorkOp.Authorize(_store_authorization(BlobStoreResourceName, accessKind, system = false)))

    private def _authorize_source_entity(
      sourceEntityId: String,
      accessKind: String,
      system: Boolean
    ): ExecUowM[Unit] =
      if (system)
        exec_pure(())
      else
        exec_from(EntityId.parse(sourceEntityId)).flatMap { id =>
          _exec_uow(UnitOfWorkOp.Authorize(_authorization(id.collection, Some(id), accessKind, system = false)))
        }

    private def _exec_uow[A](op: UnitOfWorkOp[A]): ExecUowM[A] =
      ConsequenceT.liftF(Free.liftF(op))

    private def _recover_with[A](
      program: ExecUowM[A]
    )(f: Conclusion => ExecUowM[A]): ExecUowM[A] =
      ConsequenceT(program.value.flatMap {
        case Consequence.Success(value) =>
          Free.pure(Consequence.Success(value))
        case Consequence.Failure(conclusion) =>
          f(conclusion).value
      })

    private def _ignore_failure[A](
      program: ExecUowM[A]
    ): ExecUowM[Unit] =
      ConsequenceT(program.value.map {
        case Consequence.Success(_) => Consequence.unit
        case Consequence.Failure(_) => Consequence.unit
      })

    private def _is_permission_denied(
      conclusion: Conclusion
    ): Boolean =
      conclusion.observation.taxonomy.symptom == org.goldenport.provisional.observation.Taxonomy.Symptom.PermissionDenied

    private def _delete_blob_payload(blob: Blob): ExecUowM[Boolean] =
      blob.sourceMode match {
        case BlobSourceMode.Managed =>
          blob.storageRef match {
            case Some(ref) =>
              for {
                store <- exec_from(_blob_store)
                _ <- exec_from(store.delete(ref))
              } yield true
            case None => exec_from(Consequence.operationIllegal("admin_delete_blob", s"managed blob has no storageRef: ${blob.id.value}"))
          }
        case BlobSourceMode.ExternalUrl =>
          exec_pure(false)
      }

    protected final def _blob_store: Consequence[BlobStore] =
      _service(core).map(_.blobStore)

    private def _authorization(
      collection: EntityCollectionId,
      targetId: Option[EntityId],
      accessKind: String,
      system: Boolean
    ): UnitOfWorkAuthorization =
      UnitOfWorkAuthorization(
        resourceFamily = "domain",
        resourceType = Some(collection.name),
        collectionName = Some(collection.name),
        targetId = targetId,
        accessKind = accessKind,
        sourceComponentName = core.component.map(_.name),
        targetComponentName = core.component.map(_.name),
        accessMode = if (system) EntityAccessMode.System else EntityAccessMode.UserPermission
      )

    private def _association_authorization(
      domain: AssociationDomain,
      collection: EntityCollectionId,
      targetId: Option[EntityId],
      accessKind: String,
      system: Boolean
    ): UnitOfWorkAuthorization =
      UnitOfWorkAuthorization(
        resourceFamily = "association",
        resourceType = Some(domain.value),
        collectionName = Some(collection.name),
        targetId = targetId,
        accessKind = accessKind,
        sourceComponentName = core.component.map(_.name),
        targetComponentName = core.component.map(_.name),
        accessMode = if (system) EntityAccessMode.System else EntityAccessMode.UserPermission
      )

    private def _store_authorization(
      storeName: String,
      accessKind: String,
      system: Boolean
    ): UnitOfWorkAuthorization =
      UnitOfWorkAuthorization(
        resourceFamily = "store",
        resourceType = Some(storeName),
        collectionName = None,
        targetId = None,
        accessKind = accessKind,
        sourceComponentName = core.component.map(_.name),
        targetComponentName = core.component.map(_.name),
        accessMode = if (system) EntityAccessMode.System else EntityAccessMode.UserPermission
      )
  }

  private def _service(core: ActionCall.Core): Consequence[BlobService] =
    core.component.flatMap(_.port.get[BlobService]) match {
      case Some(service) => Consequence.success(service)
      case None => Consequence.serviceUnavailable("blob service is not available")
    }

  private def _blob_association_filter(
    sourceid: String,
    role: Option[String],
    id: Option[EntityId]
  ): AssociationFilter =
    AssociationFilter(
      domain = AssociationDomain.BlobAttachment,
      sourceEntityId = Some(sourceid),
      targetEntityId = id.map(_.value),
      targetKind = Some("blob"),
      role = role
    )

  private def _blob_admin_association_filter(
    request: AdminListBlobAssociationsRequest
  ): AssociationFilter =
    AssociationFilter(
      domain = AssociationDomain.BlobAttachment,
      sourceEntityId = request.sourceEntityId,
      targetEntityId = request.id.map(_.value),
      targetKind = Some("blob"),
      role = request.role
    )

  private def _blob_target_association_filter(id: EntityId): AssociationFilter =
    AssociationFilter(
      domain = AssociationDomain.BlobAttachment,
      targetEntityId = Some(id.value),
      targetKind = Some("blob")
    )

  private def _association_query_record(filter: AssociationFilter): Record =
    Record.dataAuto(
      "associationDomain" -> filter.domain.value,
      "sourceEntityId" -> filter.sourceEntityId,
      "targetEntityId" -> filter.targetEntityId,
      "targetKind" -> filter.targetKind,
      "role" -> filter.role
    )

  private def _validate_managed_request(
    request: RegisterBlobRequest
  ): Consequence[Unit] =
    for {
      _ <- request.expectedByteSize match {
        case Some(value) if value < 0 =>
          Consequence.argumentInvalid("expectedByteSize must be zero or greater")
        case _ =>
          Consequence.unit
      }
      _ <- request.expectedDigest match {
        case Some(value) => _normalize_expected_digest(value).map(_ => ())
        case None => Consequence.unit
      }
    } yield ()

  private def _validate_managed_result(
    request: RegisterBlobRequest,
    result: BlobPutResult
  ): Consequence[Unit] =
    for {
      _ <- request.expectedByteSize match {
        case Some(expected) if expected != result.byteSize =>
          Consequence.argumentInvalid(s"expectedByteSize mismatch: expected=${expected}, actual=${result.byteSize}")
        case _ =>
          Consequence.unit
      }
      _ <- request.expectedDigest match {
        case Some(expected) =>
          _normalize_expected_digest(expected).flatMap { normalized =>
            if (normalized == result.digest.toLowerCase(java.util.Locale.ROOT))
              Consequence.unit
            else
              Consequence.argumentInvalid(s"expectedDigest mismatch: expected=${normalized}, actual=${result.digest}")
          }
        case None =>
          Consequence.unit
      }
    } yield ()

  private def _validate_external_request(
    request: RegisterBlobRequest
  ): Consequence[Unit] =
    if (request.expectedByteSize.nonEmpty)
      Consequence.argumentInvalid("expectedByteSize is only supported for managed Blob payloads")
    else if (request.expectedDigest.nonEmpty)
      Consequence.argumentInvalid("expectedDigest is only supported for managed Blob payloads")
    else
      Consequence.unit

  private def _normalize_expected_digest(
    value: String
  ): Consequence[String] = {
    val trimmed = value.trim.toLowerCase(java.util.Locale.ROOT)
    if (trimmed.matches("[0-9a-f]{64}"))
      Consequence.success(trimmed)
    else
      Consequence.argumentInvalid("expectedDigest must be a 64 character SHA-256 hex digest")
  }

  private def _register_blob_request(req: Request): Consequence[RegisterBlobRequest] =
    for {
      sourceMode <- _string(req, "sourceMode", "source_mode").map(BlobSourceMode.parse).getOrElse(Consequence.argumentMissing("sourceMode"))
      kind <- _string(req, "kind").map(BlobKind.parse).getOrElse(Consequence.argumentMissing("kind"))
      id = _new_blob_entity_id()
      filename = _string(req, "filename", "fileName")
      mimeBody = _any(req, "payload", "body", "file").collect { case m: MimeBody => m }
      contentType <- mimeBody match {
        case Some(m) => Consequence.success(Some(m.contentType))
        case None => _optional_content_type(req)
      }
      expectedByteSize <- _optional_long(req, "expectedByteSize", "expected_byte_size")
      expectedDigest = _string(req, "expectedDigest", "expected_digest")
      payload <- _payload(req, mimeBody)
    } yield RegisterBlobRequest(
      id = id,
      kind = kind,
      sourceMode = sourceMode,
      filename = filename,
      contentType = contentType,
      payload = payload,
      externalUrl = _string(req, "externalUrl", "external_url", "url"),
      expectedByteSize = expectedByteSize,
      expectedDigest = expectedDigest,
      attributes = Map.empty
    )

  private val ContentTypePattern =
    """^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+(?:\s*;\s*[A-Za-z0-9!#$&^_.+-]+=(?:"[^"]*"|[A-Za-z0-9!#$&^_.+-]+))*$""".r

  private def _optional_content_type(req: Request): Consequence[Option[ContentType]] =
    _string(req, "contentType", "content_type") match {
      case Some(s) => _parse_content_type(s).map(Some(_))
      case None => Consequence.success(None)
    }

  private def _parse_content_type(value: String): Consequence[ContentType] = {
    val trimmed = value.trim
    if (ContentTypePattern.pattern.matcher(trimmed).matches())
      Consequence.success(ContentType.parse(trimmed))
    else
      Consequence.argumentInvalid(s"invalid contentType: ${value}")
  }

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

  private def _id(req: Request): Consequence[EntityId] =
    _string(req, "id").map(EntityId.parse).getOrElse(Consequence.argumentMissing("id"))

  private def _attach_blob_request(req: Request): Consequence[AttachBlobRequest] =
    for {
      source <- _string(req, "sourceEntityId", "source_entity_id", "entityId", "entity_id").map(Consequence.success).getOrElse(Consequence.argumentMissing("sourceEntityId"))
      id <- _id(req)
      role <- _string(req, "role").map(Consequence.success).getOrElse(Consequence.argumentMissing("role"))
    } yield AttachBlobRequest(
      sourceEntityId = source,
      id = id,
      role = role,
      sortOrder = _int(req, "sortOrder", "sort_order")
    )

  private def _detach_blob_request(req: Request): Consequence[DetachBlobRequest] =
    for {
      source <- _string(req, "sourceEntityId", "source_entity_id", "entityId", "entity_id").map(Consequence.success).getOrElse(Consequence.argumentMissing("sourceEntityId"))
      id <- _id(req)
    } yield DetachBlobRequest(
      sourceEntityId = source,
      id = id,
      role = _string(req, "role")
    )

  private def _list_entity_blobs_request(req: Request): Consequence[ListEntityBlobsRequest] =
    _string(req, "sourceEntityId", "source_entity_id", "entityId", "entity_id").map { source =>
      Consequence.success(ListEntityBlobsRequest(source, _string(req, "role")))
    }.getOrElse(Consequence.argumentMissing("sourceEntityId"))

  private def _admin_list_blob_associations_request(
    req: Request
  ): Consequence[AdminListBlobAssociationsRequest] = {
    val id = _string(req, "id") match {
      case Some(value) => EntityId.parse(value).map(Some(_))
      case None => Consequence.success(None)
    }
    for {
      id <- id
      page <- _admin_page_request(req)
    } yield AdminListBlobAssociationsRequest(
      sourceEntityId = _string(req, "sourceEntityId", "source_entity_id", "entityId", "entity_id"),
      id = id,
      role = _string(req, "role"),
      page = page
    )
  }

  private def _admin_delete_blob_request(req: Request): Consequence[AdminDeleteBlobRequest] =
    for {
      id <- _id(req)
      force <- _boolean(req, "force")
    } yield AdminDeleteBlobRequest(id, force)

  private def _admin_page_request(req: Request): Consequence[AdminPageRequest] = {
    val offset = _int(req, "offset").getOrElse(0)
    val limit = _int(req, "limit").getOrElse(AdminPageRequest.DefaultLimit)
    if (offset < 0)
      Consequence.argumentInvalid("offset must be zero or greater")
    else if (limit < 1)
      Consequence.argumentInvalid("limit must be one or greater")
    else if (limit > AdminPageRequest.MaxLimit)
      Consequence.argumentInvalid(s"limit must be ${AdminPageRequest.MaxLimit} or less")
    else
      Consequence.success(AdminPageRequest(offset, limit))
  }

  private def _new_blob_entity_id(): EntityId =
    EntityId(BlobCollectionId.major, BlobCollectionId.minor, BlobCollectionId)

  private def _blob_access_url_record(metadata: BlobMetadata): Consequence[Record] =
    if (metadata.sourceMode == BlobSourceMode.ExternalUrl)
      BlobExternalUrlPolicy.normalize(metadata.externalUrl.getOrElse(metadata.accessUrl.displayUrl)).map { safe =>
        Record.dataAuto(
          "id" -> metadata.id.value,
          "sourceMode" -> metadata.sourceMode.print,
          "displayUrl" -> safe,
          "downloadUrl" -> safe,
          "urlSource" -> metadata.accessUrl.urlSource.print,
          "expiresAt" -> metadata.accessUrl.expiresAt.map(_.toString)
        )
      }
    else
      Consequence.success(Record.dataAuto(
        "id" -> metadata.id.value,
        "sourceMode" -> metadata.sourceMode.print,
        "displayPath" -> metadata.accessUrl.displayPath,
        "downloadPath" -> metadata.accessUrl.downloadPath,
        "displayUrl" -> metadata.accessUrl.displayUrlForPresentation,
        "downloadUrl" -> metadata.accessUrl.downloadUrlForPresentation,
        "urlSource" -> metadata.accessUrl.urlSource.print,
        "expiresAt" -> metadata.accessUrl.expiresAt.map(_.toString)
      ))

  private def _blob_store_status_record(status: BlobStoreStatus): Record =
    Record.dataAuto(
      "backend" -> status.backend,
      "available" -> status.available,
      "container" -> status.container,
      "location" -> status.location,
      "message" -> status.message
    )

  private def _string(req: Request, names: String*): Option[String] =
    _any(req, names*).map(_.toString).map(_.trim).filter(_.nonEmpty)

  private def _int(req: Request, names: String*): Option[Int] =
    _any(req, names*).flatMap {
      case n: java.lang.Number => Some(n.intValue)
      case s: String => scala.util.Try(s.trim.toInt).toOption
      case other => scala.util.Try(other.toString.trim.toInt).toOption
    }

  private def _optional_long(req: Request, names: String*): Consequence[Option[Long]] =
    _any(req, names*) match {
      case Some(n: java.lang.Byte) => _non_negative_long(n.longValue, names)
      case Some(n: java.lang.Short) => _non_negative_long(n.longValue, names)
      case Some(n: java.lang.Integer) => _non_negative_long(n.longValue, names)
      case Some(n: java.lang.Long) => _non_negative_long(n.longValue, names)
      case Some(n: java.math.BigInteger) =>
        scala.util.Try(n.longValueExact()).toOption match {
          case Some(value) => _non_negative_long(value, names)
          case None => Consequence.argumentInvalid(s"invalid long ${names.headOption.getOrElse("value")}: ${n}")
        }
      case Some(n: java.math.BigDecimal) =>
        scala.util.Try(n.toBigIntegerExact.longValueExact()).toOption match {
          case Some(value) => _non_negative_long(value, names)
          case None => Consequence.argumentInvalid(s"invalid long ${names.headOption.getOrElse("value")}: ${n}")
        }
      case Some(_: java.lang.Float) =>
        Consequence.argumentInvalid(s"invalid long ${names.headOption.getOrElse("value")}: fractional number")
      case Some(_: java.lang.Double) =>
        Consequence.argumentInvalid(s"invalid long ${names.headOption.getOrElse("value")}: fractional number")
      case Some(s: String) => _parse_non_negative_long(s, names)
      case Some(other) => _parse_non_negative_long(other.toString, names)
      case None =>
        Consequence.success(None)
    }

  private def _parse_non_negative_long(value: String, names: Seq[String]): Consequence[Option[Long]] = {
    val trimmed = value.trim
    if (trimmed.matches("[0-9]+"))
      scala.util.Try(trimmed.toLong).toOption match {
        case Some(n) => _non_negative_long(n, names)
        case None => Consequence.argumentInvalid(s"invalid long ${names.headOption.getOrElse("value")}: ${value}")
      }
    else
      Consequence.argumentInvalid(s"invalid long ${names.headOption.getOrElse("value")}: ${value}")
  }

  private def _non_negative_long(value: Long, names: Seq[String]): Consequence[Option[Long]] =
    if (value >= 0)
      Consequence.success(Some(value))
    else
      Consequence.argumentInvalid(s"${names.headOption.getOrElse("value")} must be zero or greater")

  private def _boolean(req: Request, names: String*): Consequence[Boolean] =
    _any(req, names*) match {
      case Some(b: java.lang.Boolean) => Consequence.success(b.booleanValue)
      case Some(s: String) =>
        s.trim.toLowerCase(java.util.Locale.ROOT) match {
          case "true" | "yes" | "y" | "1" | "on" => Consequence.success(true)
          case "false" | "no" | "n" | "0" | "off" => Consequence.success(false)
          case other => Consequence.argumentInvalid(s"invalid boolean ${names.headOption.getOrElse("value")}: $other")
        }
      case Some(n: java.lang.Number) =>
        n.intValue match {
          case 0 => Consequence.success(false)
          case 1 => Consequence.success(true)
          case other => Consequence.argumentInvalid(s"invalid boolean ${names.headOption.getOrElse("value")}: $other")
        }
      case Some(other) =>
        Consequence.argumentInvalid(s"invalid boolean ${names.headOption.getOrElse("value")}: ${other}")
      case None =>
        Consequence.success(false)
    }

  private def _any(req: Request, names: String*): Option[Any] = {
    val params = req.arguments ++ req.properties
    names.iterator.flatMap { name =>
      params.collectFirst {
        case p if p.name.equalsIgnoreCase(name) => p.value
      }
    }.nextOption()
  }

  private def _blob_schema: Schema =
    Schema(Vector(
      _column("id"),
      _column("kind"),
      _column("sourceMode"),
      _column("filename", Multiplicity.ZeroOne),
      _column("contentType", Multiplicity.ZeroOne),
      _column("byteSize", Multiplicity.ZeroOne, XLong),
      _column("digest", Multiplicity.ZeroOne),
      _column("storageRef", Multiplicity.ZeroOne),
      _column("externalUrl", Multiplicity.ZeroOne),
      _column("displayUrl"),
      _column("downloadUrl"),
      _column("urlSource"),
      _column("createdAt"),
      _column("updatedAt")
    ))

  private def _column(
    name: String,
    multiplicity: Multiplicity = Multiplicity.One,
    datatype: org.goldenport.schema.DataType = XString
  ): Column =
    Column(BaseContent.simple(name), ValueDomain(datatype = datatype, multiplicity = multiplicity))
}
