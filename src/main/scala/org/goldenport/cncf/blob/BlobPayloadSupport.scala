package org.goldenport.cncf.blob

import org.goldenport.Consequence
import org.goldenport.bag.BinaryBag
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.component.builtin.blob.BlobComponent
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.datatype.ContentType
import org.simplemodeling.model.datatype.EntityId

/*
 * Reusable Blob payload boundary for components that need managed Blob bytes.
 *
 * @since   Apr. 29, 2026
 * @version Apr. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object BlobPayloadSupport {
  def service(component: Component): Consequence[BlobComponent.BlobService] =
    component.subsystem
      .flatMap(_.findComponent("blob"))
      .flatMap(_.port.get[BlobComponent.BlobService]) match {
      case Some(service) => Consequence.success(service)
      case None => Consequence.serviceUnavailable("blob service is not available in subsystem")
    }

  def readManagedPayload(
    component: Component,
    id: EntityId
  )(using ExecutionContext): Consequence[BlobReadResult] =
    for {
      blob <- BlobRepository.entityStore().get(id)
      ref <- _managed_storage_ref(blob)
      service <- service(component)
      result <- service.blobStore.get(ref)
    } yield result

  def putManagedPayload(
    component: Component,
    id: EntityId,
    kind: BlobKind,
    filename: Option[String],
    contentType: ContentType,
    payload: BinaryBag,
    attributes: Map[String, String] = Map.empty
  )(using ExecutionContext): Consequence[Blob] =
    for {
      service <- service(component)
      existing <- BlobRepository.entityStore().list()
      _ <-
        if (existing.exists(_.id == id))
          Consequence.stateConflict(s"blob metadata already exists: ${id.value}")
        else
          Consequence.unit
      result <- service.blobStore.put(
        BlobPutRequest(id, kind, filename, contentType, attributes),
        payload
      )
      blob <- _create_managed_blob_after_put(service, result, kind, filename, attributes)
    } yield blob

  private def _create_managed_blob_after_put(
    service: BlobComponent.BlobService,
    result: BlobPutResult,
    kind: BlobKind,
    filename: Option[String],
    attributes: Map[String, String]
  )(using ExecutionContext): Consequence[Blob] =
    _validate_put_result(service, result).flatMap { _ =>
      BlobRepository.entityStore().create(BlobCreate(
        id = result.id,
        kind = kind,
        sourceMode = BlobSourceMode.Managed,
        filename = filename,
        contentType = Some(result.contentType),
        byteSize = Some(result.byteSize),
        digest = Some(result.digest),
        storageRef = Some(result.storageRef),
        externalUrl = None,
        accessUrl = _managed_blob_access_url(result),
        attributes = attributes
      ))
    }.recoverWith { conclusion =>
      service.blobStore.delete(result.storageRef)
        .recover(_ => ())
        .flatMap(_ => Consequence.Failure(conclusion))
    }

  private def _validate_put_result(
    service: BlobComponent.BlobService,
    result: BlobPutResult
  ): Consequence[Unit] =
    if (result.byteSize > service.maxByteSize)
      Consequence.argumentFieldLimitExceeded(
        "payload.byteSize",
        service.maxByteSize,
        result.byteSize,
        "blob.upload.max-byte-size"
      )
    else
      Consequence.unit

  private def _managed_storage_ref(blob: Blob): Consequence[BlobStorageRef] =
    blob.sourceMode match {
      case BlobSourceMode.Managed =>
        blob.storageRef match {
          case Some(ref) => Consequence.success(ref)
          case None => Consequence.operationIllegal("blob.payload.read", s"managed blob has no storageRef: ${blob.id.value}")
        }
      case BlobSourceMode.ExternalUrl =>
        Consequence.argumentInvalid(s"blob payload is not managed: ${blob.id.value}")
    }

  private def _managed_blob_access_url(result: BlobPutResult): BlobAccessUrl =
    if (result.accessUrl.urlSource == BlobAccessUrlSource.Backend)
      result.accessUrl
    else
      BlobUrl.cncfRoute(result.id)
}
