package org.goldenport.cncf.component.builtin.blob

import java.nio.charset.StandardCharsets
import java.util.UUID
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.association.{AssociationCreate, AssociationDomain, AssociationFilter, AssociationRecordCodec, AssociationRepository, AssociationStoragePolicy}
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentDescriptor, ComponentId, ComponentInit, ComponentInstanceId}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.runtime.{EntityMemoryPolicy, EntityRuntimeDescriptor, PartitionStrategy, WorkingSetPolicy, WorkingSetPolicySource}
import org.goldenport.cncf.security.{AdminAuthorizationPolicy, OperationAuthorizationProvider, OperationAuthorizationRule}
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
 * @version Apr. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class BlobComponent() extends Component {
  override protected def initialize_Component(params: ComponentInit): Unit =
    withComponentDescriptors(componentDescriptors ++ BlobComponent.componentDescriptors)
}

object BlobComponent {
  trait BlobService {
    def registerBlob(request: RegisterBlobRequest)(using ExecutionContext): Consequence[BlobMetadata]
    def readBlob(id: EntityId)(using ExecutionContext): Consequence[BlobReadOutcome]
    def resolveBlobUrl(id: EntityId)(using ExecutionContext): Consequence[Record]
    def getBlobMetadata(id: EntityId)(using ExecutionContext): Consequence[BlobMetadata]
    def attachBlobToEntity(request: AttachBlobRequest)(using ExecutionContext): Consequence[Record]
    def detachBlobFromEntity(request: DetachBlobRequest)(using ExecutionContext): Consequence[Record]
    def listEntityBlobs(request: ListEntityBlobsRequest)(using ExecutionContext): Consequence[Record]
    def adminListBlobs(page: AdminPageRequest)(using ExecutionContext): Consequence[Record]
    def adminGetBlob(id: EntityId)(using ExecutionContext): Consequence[BlobMetadata]
    def adminListBlobAssociations(request: AdminListBlobAssociationsRequest)(using ExecutionContext): Consequence[Record]
    def adminBlobStoreStatus()(using ExecutionContext): Consequence[Record]
    def adminDeleteBlob(request: AdminDeleteBlobRequest)(using ExecutionContext): Consequence[Record]
    def adminAttachBlobToEntity(request: AttachBlobRequest)(using ExecutionContext): Consequence[Record]
    def adminDetachBlobFromEntity(request: DetachBlobRequest)(using ExecutionContext): Consequence[Record]
  }

  final case class RegisterBlobRequest(
    id: EntityId,
    kind: BlobKind,
    sourceMode: BlobSourceMode,
    filename: Option[String],
    contentType: Option[ContentType],
    payload: Option[BinaryBag],
    externalUrl: Option[String],
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

  def componentDescriptors: Vector[ComponentDescriptor] =
    Vector(ComponentDescriptor(
      componentName = Some(name),
      entityRuntimeDescriptors = Vector(
        EntityRuntimeDescriptor(
          entityName = "blob",
          collectionId = BlobCollectionId,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
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
          new DefaultBlobService(
            InMemoryBlobStore(),
            BlobRepository.entityStore(),
            AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
          )
        )
      )
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(name, componentId, instanceid, protocol)
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
          _optional_property("externalUrl")
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
    store: BlobStore,
    repository: BlobRepository,
    associations: AssociationRepository
  ) extends BlobService {
    def registerBlob(request: RegisterBlobRequest)(using ExecutionContext): Consequence[BlobMetadata] =
      request.sourceMode match {
        case BlobSourceMode.Managed =>
          _register_managed(request)
        case BlobSourceMode.ExternalUrl =>
          _register_external_url(request)
      }

    def readBlob(id: EntityId)(using ExecutionContext): Consequence[BlobReadOutcome] =
      repository.get(id).flatMap {
        case blob if blob.sourceMode == BlobSourceMode.Managed =>
          blob.storageRef match {
            case Some(ref) => store.get(ref).map(BlobReadOutcome.Managed.apply)
            case None => Consequence.operationIllegal("blob.read_blob", s"managed blob has no storageRef: ${id.value}")
          }
        case blob =>
          Consequence.operationIllegal("blob.read_blob", s"${blob.sourceMode.print} blob has no managed payload; use resolve_blob_url: ${id.value}")
      }

    def resolveBlobUrl(id: EntityId)(using ExecutionContext): Consequence[Record] =
      repository.get(id).map(blob => _blob_access_url_record(blob.metadata))

    def getBlobMetadata(id: EntityId)(using ExecutionContext): Consequence[BlobMetadata] =
      repository.get(id).map(_.metadata)

    def attachBlobToEntity(request: AttachBlobRequest)(using ExecutionContext): Consequence[Record] =
      repository.get(request.id).flatMap { blob =>
        val filter = _blob_association_filter(request.sourceEntityId, Some(request.role), Some(blob.id))
        associations.list(filter).flatMap {
          case existing +: _ =>
            Consequence.success(AssociationRecordCodec.toRecord(existing))
          case _ =>
            associations.create(
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
              )
            ).map(AssociationRecordCodec.toRecord)
        }
      }

    def detachBlobFromEntity(request: DetachBlobRequest)(using ExecutionContext): Consequence[Record] =
      associations.list(_blob_association_filter(request.sourceEntityId, request.role, Some(request.id))).flatMap {
        case Vector() => Consequence.operationNotFound(s"blob association:${request.sourceEntityId}:${request.id.value}")
        case values =>
          values.foldLeft(Consequence.success(0)) { (z, association) =>
            z.flatMap(count => associations.delete(association).map(_ => count + 1))
          }.map(count => Record.dataAuto("detachedCount" -> count))
      }

    def listEntityBlobs(request: ListEntityBlobsRequest)(using ExecutionContext): Consequence[Record] =
      associations.list(_blob_association_filter(request.sourceEntityId, request.role, None)).flatMap { values =>
        values.foldLeft(Consequence.success(Vector.empty[BlobMetadata])) { (z, association) =>
          z.flatMap { acc =>
            EntityId.parse(association.targetEntityId).flatMap(id => repository.get(id)).map(blob => acc :+ blob.metadata)
          }
        }.map { metadata =>
          Record.dataAuto(
            "data" -> metadata.map(_.toRecord),
            "fetchedCount" -> metadata.size
          )
        }
      }

    def adminListBlobs(page: AdminPageRequest)(using ExecutionContext): Consequence[Record] =
      repository.list(page.offset, Some(page.fetchLimit)).map { values =>
        val rows = values.take(page.limit)
        Record.dataAuto(
          "data" -> rows.map(_.metadata.toRecord),
          "offset" -> page.offset,
          "limit" -> page.limit,
          "fetchedCount" -> rows.size,
          "hasMore" -> (values.size > page.limit)
        )
      }

    def adminGetBlob(id: EntityId)(using ExecutionContext): Consequence[BlobMetadata] =
      repository.get(id).map(_.metadata)

    def adminListBlobAssociations(
      request: AdminListBlobAssociationsRequest
    )(using ExecutionContext): Consequence[Record] =
      associations.list(
        _blob_admin_association_filter(request),
        request.page.offset,
        Some(request.page.fetchLimit)
      ).map { values =>
        val rows = values.take(request.page.limit)
        Record.dataAuto(
          "data" -> rows.map(AssociationRecordCodec.toRecord),
          "offset" -> request.page.offset,
          "limit" -> request.page.limit,
          "fetchedCount" -> rows.size,
          "hasMore" -> (values.size > request.page.limit)
        )
      }

    def adminBlobStoreStatus()(using ExecutionContext): Consequence[Record] =
      store.status().map(_blob_store_status_record)

    def adminDeleteBlob(
      request: AdminDeleteBlobRequest
    )(using ExecutionContext): Consequence[Record] =
      for {
        blob <- repository.get(request.id)
        refs <- associations.list(_blob_target_association_filter(blob.id))
        _ <- if (refs.nonEmpty && !request.force)
          Consequence.operationConflict(
            "admin_delete_blob",
            Seq(Descriptor.Facet.Message(s"blob is still attached: ${blob.id.value}; associationCount=${refs.size}"))
          )
        else
          Consequence.unit
        _ <- repository.delete(blob.id)
        deletedassociations <- refs.foldLeft(Consequence.success(0)) { (z, association) =>
          z.flatMap(count => associations.delete(association).map(_ => count + 1))
        }
        payloaddeleted <- _delete_blob_payload(blob)
      } yield Record.dataAuto(
        "deletedBlobId" -> blob.id.value,
        "deletedAssociationCount" -> deletedassociations,
        "payloadDeleted" -> payloaddeleted,
        "sourceMode" -> blob.sourceMode.print
      )

    def adminAttachBlobToEntity(request: AttachBlobRequest)(using ExecutionContext): Consequence[Record] =
      attachBlobToEntity(request)

    def adminDetachBlobFromEntity(request: DetachBlobRequest)(using ExecutionContext): Consequence[Record] =
      detachBlobFromEntity(request)

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

    private def _delete_blob_payload(blob: Blob)(using ExecutionContext): Consequence[Boolean] =
      blob.sourceMode match {
        case BlobSourceMode.Managed =>
          blob.storageRef match {
            case Some(ref) => store.delete(ref).map(_ => true)
            case None => Consequence.operationIllegal("admin_delete_blob", s"managed blob has no storageRef: ${blob.id.value}")
          }
        case BlobSourceMode.ExternalUrl =>
          Consequence.success(false)
      }

    private def _register_managed(request: RegisterBlobRequest)(using ExecutionContext): Consequence[BlobMetadata] =
      request.payload match {
        case Some(payload) =>
          val contentType = request.contentType.getOrElse(ContentType.APPLICATION_OCTET_STREAM)
          store.put(
            BlobPutRequest(
              id = request.id,
              kind = request.kind,
              filename = request.filename,
              contentType = contentType,
              attributes = request.attributes
            ),
            payload
          ).flatMap { result =>
            repository.create(
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
                accessUrl = result.accessUrl,
                attributes = request.attributes
              )
            ).map(_.metadata).recoverWith { conclusion =>
              store.delete(result.storageRef)
                .recover(_ => ())
                .flatMap(_ => Consequence.Failure[BlobMetadata](conclusion))
            }
          }
        case None =>
          Consequence.argumentMissing("payload")
      }

    private def _register_external_url(request: RegisterBlobRequest)(using ExecutionContext): Consequence[BlobMetadata] =
      request.externalUrl match {
        case Some(url) if url.trim.nonEmpty =>
          repository.create(
              BlobCreate(
                id = request.id,
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
              attributes = request.attributes
            )
          ).map(_.metadata)
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
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.registerBlob(registerRequest)(using core.executionContext)).map { metadata =>
        OperationResponse.RecordResponse(metadata.toRecord)
      }
  }

  private final case class ReadBlobActionCall(
    core: ActionCall.Core,
    id: EntityId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.readBlob(id)(using core.executionContext)).map {
        case BlobReadOutcome.Managed(result) =>
          OperationResponse.Http(
            HttpResponse.Binary(
              HttpStatus.Ok,
              result.contentType,
              result.payload
            )
          )
      }
  }

  private final case class ResolveBlobUrlActionCall(
    core: ActionCall.Core,
    id: EntityId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.resolveBlobUrl(id)(using core.executionContext)).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class GetBlobMetadataActionCall(
    core: ActionCall.Core,
    id: EntityId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.getBlobMetadata(id)(using core.executionContext)).map { metadata =>
        OperationResponse.RecordResponse(metadata.toRecord)
      }
  }

  private final case class AttachBlobToEntityActionCall(
    core: ActionCall.Core,
    attachRequest: AttachBlobRequest
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.attachBlobToEntity(attachRequest)(using core.executionContext)).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class DetachBlobFromEntityActionCall(
    core: ActionCall.Core,
    detachRequest: DetachBlobRequest
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.detachBlobFromEntity(detachRequest)(using core.executionContext)).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class ListEntityBlobsActionCall(
    core: ActionCall.Core,
    listRequest: ListEntityBlobsRequest
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.listEntityBlobs(listRequest)(using core.executionContext)).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class AdminListBlobsActionCall(
    core: ActionCall.Core,
    page: AdminPageRequest
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.adminListBlobs(page)(using core.executionContext)).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class AdminGetBlobActionCall(
    core: ActionCall.Core,
    id: EntityId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.adminGetBlob(id)(using core.executionContext)).map { metadata =>
        OperationResponse.RecordResponse(metadata.toRecord)
      }
  }

  private final case class AdminListBlobAssociationsActionCall(
    core: ActionCall.Core,
    listRequest: AdminListBlobAssociationsRequest
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.adminListBlobAssociations(listRequest)(using core.executionContext)).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class AdminBlobStoreStatusActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.adminBlobStoreStatus()(using core.executionContext)).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class AdminDeleteBlobActionCall(
    core: ActionCall.Core,
    deleteRequest: AdminDeleteBlobRequest
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.adminDeleteBlob(deleteRequest)(using core.executionContext)).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class AdminAttachBlobToEntityActionCall(
    core: ActionCall.Core,
    attachRequest: AttachBlobRequest
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.adminAttachBlobToEntity(attachRequest)(using core.executionContext)).map { record =>
        OperationResponse.RecordResponse(record)
      }
  }

  private final case class AdminDetachBlobFromEntityActionCall(
    core: ActionCall.Core,
    detachRequest: DetachBlobRequest
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _service(core).flatMap(_.adminDetachBlobFromEntity(detachRequest)(using core.executionContext)).map { record =>
        OperationResponse.RecordResponse(record)
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
      id = _new_blob_entity_id()
      filename = _string(req, "filename", "fileName")
      mimeBody = _any(req, "payload", "body", "file").collect { case m: MimeBody => m }
      contentType = mimeBody.map(_.contentType).orElse(_string(req, "contentType", "content_type").map(ContentType.parse))
      payload <- _payload(req, mimeBody)
    } yield RegisterBlobRequest(
      id = id,
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

  private def _blob_access_url_record(metadata: BlobMetadata): Record =
    Record.dataAuto(
      "id" -> metadata.id.value,
      "sourceMode" -> metadata.sourceMode.print,
      "displayUrl" -> metadata.accessUrl.displayUrl,
      "downloadUrl" -> metadata.accessUrl.downloadUrl,
      "urlSource" -> metadata.accessUrl.urlSource.print,
      "expiresAt" -> metadata.accessUrl.expiresAt.map(_.toString)
    )

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
