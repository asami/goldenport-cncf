package org.goldenport.cncf.job

import java.io.{ByteArrayOutputStream, DataOutputStream}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.bag.{Bag, BinaryBag}
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.blob.{
  Blob, BlobCreate, BlobKind, BlobPutRequest, BlobPutResult, BlobRepository,
  BlobSourceMode, BlobStorageRef, BlobStore, BlobUrl
}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityStore
import org.simplemodeling.model.datatype.EntityId

/*
 * Exclusive provider-backed payload ownership; metadata survives disposal.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] sealed abstract class DurableJobOwnedBlobTarget {
  def id: EntityId
  def storageRef: BlobStorageRef
}

private[job] enum DurableJobPayloadIntegrityOutcome {
  case Verified, Refused, ResourceBoundRefused
}

private[job] object DurableJobOwnedBlobPayloads {
  val storageKind: String = "job-owned-blob"
  val maxReferences: Int = 1024
  val ownershipAttribute: String = "cncf.job.payload.ownership"
  val ownerAttribute: String = "cncf.job.payload.owner"
  val bindingAttribute: String = "cncf.job.payload.binding"

  def ownerFingerprint(record: DurableJobRecord): String = {
    val body = record.body
    _fingerprint(Vector(Some(body.identity.jobId), Some(body.identity.createdAt.toString),
      Some(body.authorization.tenantId), Some(body.authorization.submitter.kind),
      Some(body.authorization.submitter.id)))
  }

  def bindingFingerprint(
    id: EntityId,
    storageRef: BlobStorageRef,
    contentType: ContentType,
    byteSize: Long,
    digest: String
  ): String = _fingerprint(Vector(Some(id.value), Some(storageRef.print), storageRef.version,
    storageRef.etag, Some(contentType.header), Some(byteSize.toString), Some(digest)))

  def references(record: DurableJobRecord): Vector[DurableExternalReference] = {
    val inputs = record.body.inputs.collect { case DurableInputReference(_, DurableValue.External(ref)) => ref }
    val result = record.body.result match {
      case DurableResultOutcome.Succeeded(DurableValue.External(ref)) => Vector(ref)
      case _ => Vector.empty
    }
    inputs ++ result ++ record.body.calltreeReference.toVector
  }

  def referenceCount(record: DurableJobRecord): Int =
    references(record).size + record.body.definitionSnapshot.declaredSource.size

  private def _fingerprint(values: Vector[Option[String]]): String = {
    val bytes = new ByteArrayOutputStream()
    val output = new DataOutputStream(bytes)
    values.foreach {
      case None => output.writeByte(0)
      case Some(value) =>
        val encoded = value.getBytes(StandardCharsets.UTF_8)
        output.writeByte(1)
        output.writeInt(encoded.length)
        output.write(encoded)
    }
    output.close()
    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray)
      .map(value => f"${value & 0xff}%02x").mkString
  }
}

private[job] final class DurableJobOwnedBlobPayloads(
  store: DurableJobStore,
  entitystore: EntityStore,
  blobstore: BlobStore,
  policy: DurableJobOwnedBlobResourcePolicy
) {
  def this(store: DurableJobStore, entityStore: EntityStore, blobStore: BlobStore) =
    this(store, entityStore, blobStore, DurableJobOwnedBlobResourcePolicy.default)

  import BlobRepository.given
  import DurableJobOwnedBlobPayloads.{
    bindingAttribute, bindingFingerprint, maxReferences, ownerAttribute,
    ownerFingerprint, ownershipAttribute, referenceCount, references, storageKind
  }

  private val _quota = new DurableJobOwnedBlobQuota(entitystore, policy)

  private final case class VerifiedTarget(id: EntityId, storageRef: BlobStorageRef, fact: DurableJobOwnedBlobQuotaMetadata)
    extends DurableJobOwnedBlobTarget

  def resourceSnapshot()(using ctx: ExecutionContext): Consequence[DurableJobOwnedBlobQuotaSnapshot] = {
    val result = _quota.resourceSnapshot()
    if (store == null) result else store.operationalMetricsRecorder.observePayloadSnapshot(result)
  }
  def putObserved(
    snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess,
    filename: Option[String],
    contentType: ContentType,
    payload: BinaryBag
  )(using ctx: ExecutionContext): Consequence[DurableExternalReference] = {
    val result = _bounded {
      for {
        admitted <- _active(snapshot, access)
        _ <- policy.validate
        _ <- if (filename != null && filename.forall(DurableJobOwnedBlobResourcePolicy.boundedText(_, 1024, false)) &&
            DurableJobOwnedBlobResourcePolicy.validContent(contentType) &&
            DurableJobOwnedBlobResourcePolicy.boundedText(blobstore.name, 256)) Consequence.unit else _refused
        detached <- policy.detach(payload)
        id <- _canonical_id(ctx.idGeneration.entityId(BlobRepository.CollectionId, "durable-job-owned-payload").value)
        existing <- entitystore.load[Blob](id)
        _ <- if (existing.isEmpty) Consequence.unit else _refused
        owner = ownerFingerprint(admitted.record)
        attributes = Map(ownershipAttribute -> "exclusive-v1", ownerAttribute -> owner)
        lease <- _quota.reserve(DurableJobOwnedBlobQuotaEntry(id.value, owner, contentType.header,
          detached.bytes.length.toLong, detached.sha256, None, DurableJobOwnedBlobQuotaState.Pending, None))
        put <- blobstore.put(BlobPutRequest(id, BlobKind.Binary, filename, contentType, attributes), Bag.binary(detached.bytes))
        reference <- _publish(admitted, access, id, filename, put, attributes, lease)
      } yield reference
    }
    if (store == null) result else store.operationalMetricsRecorder.observePayloadPut(result)
  }
  def prepare(record: DurableJobRecord)(using ctx: ExecutionContext): Consequence[Vector[DurableJobOwnedBlobTarget]] = {
    val result = _bounded {
      _metadata_targets(record).flatMap { targets =>
        if (targets.isEmpty) Consequence.success(Vector.empty)
        else _quota.prepareTargets(targets.map(_.fact)).map(_ => targets)
      }
    }
    if (store == null) result else store.operationalMetricsRecorder.observePayloadPrepare(result)
  }

  def readObserved(
    snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess,
    reference: DurableExternalReference
  )(using ctx: ExecutionContext): Consequence[DurableJobOwnedBlobDetachedPayload] = _read_protected {
    for {
      admitted <- _active(snapshot, access)
      _ <- policy.validate
      _ <- if (reference != null && reference.storageKind == storageKind &&
          references(admitted.record).contains(reference)) Consequence.unit else _refused
      targets <- _metadata_targets(admitted.record)
      selected <- targets.find(_.id.value == reference.reference)
        .fold[Consequence[VerifiedTarget]](_refused)(Consequence.success)
      _ <- _quota.inspectTargets(targets.map(_.fact))
      _ <- if (selected.fact.bytesize >= 0L && selected.fact.bytesize <= policy.maxpayloadbytes.toLong)
        Consequence.unit else _refused
      detached <- _read_physical(selected)
      _ <- _active(snapshot, access)
      _ <- _quota.inspectTargets(targets.map(_.fact))
    } yield detached
  }

  private def _read_physical(target: VerifiedTarget): Consequence[DurableJobOwnedBlobDetachedPayload] =
    blobstore.get(target.storageRef).flatMap { returned =>
      val fact = target.fact
      if (returned == null || returned.payload == null || returned.id != target.id ||
          returned.storageRef != target.storageRef || returned.contentType == null ||
          returned.contentType.header != fact.contenttype || returned.byteSize != fact.bytesize ||
          returned.digest == null || !returned.digest.equalsIgnoreCase(fact.payloadsha256)) _refused
      else {
        val stream = returned.payload.openInputStream()
        if (stream == null) _refused
        else try {
          val output = new ByteArrayOutputStream(math.min(8192L, fact.bytesize).toInt)
          val digest = MessageDigest.getInstance("SHA-256")
          val buffer = new Array[Byte](8192)
          val limit = Math.addExact(fact.bytesize, 1L)
          var total = 0L
          var ended = false
          while (!ended && total < limit) {
            if (Thread.currentThread().isInterrupted) throw new InterruptedException()
            val length = math.min(buffer.length.toLong, limit - total).toInt
            val count = stream.read(buffer, 0, length)
            if (count == -1) ended = true
            else if (count == 0) {
              if (Thread.currentThread().isInterrupted) throw new InterruptedException()
              val byte = stream.read()
              if (byte == -1) ended = true
              else if (byte >= 0 && byte <= 255) {
                total = Math.addExact(total, 1L)
                digest.update(byte.toByte)
                if (total <= fact.bytesize) output.write(byte)
              } else throw new IllegalStateException("durable payload read refused")
            } else if (count > 0 && count <= length) {
              total = Math.addExact(total, count.toLong)
              digest.update(buffer, 0, count)
              if (total <= fact.bytesize) output.write(buffer, 0, count)
            } else throw new IllegalStateException("durable payload read refused")
          }
          val actual = digest.digest().map(value => f"${value & 0xff}%02x").mkString
          if (ended && total == fact.bytesize && actual.equalsIgnoreCase(fact.payloadsha256))
            Consequence.success(DurableJobOwnedBlobDetachedPayload(output.toByteArray, actual))
          else _refused
        } catch {
          case exception: InterruptedException => Thread.currentThread().interrupt(); throw exception
        } finally stream.close()
      }
    }

  private def _read_protected[A](body: => Consequence[A]): Consequence[A] =
    try {
      if (Thread.currentThread().isInterrupted) throw new InterruptedException()
      body match {
        case success @ Consequence.Success(_) => success
        case _ => _refused
      }
    } catch {
      case _: InterruptedException => Thread.currentThread().interrupt(); _refused
      case NonFatal(_) => _refused
    }

  def inspect(record: DurableJobRecord, budget: DurableJobIntegrityByteBudget)
      (using ctx: ExecutionContext): Consequence[DurableJobPayloadIntegrityOutcome] = {
    import DurableJobPayloadIntegrityOutcome.{Refused, ResourceBoundRefused, Verified}
    try {
      _metadata_targets(record).flatMap { targets =>
        if (targets.isEmpty) Consequence.success(Verified)
        else _quota.inspectTargets(targets.map(_.fact)).flatMap { _ =>
          if (budget == null || !budget.reserve(targets.map(_.fact.bytesize), policy.maxpayloadbytes.toLong))
            Consequence.success(ResourceBoundRefused)
          else targets.foldLeft(Consequence.unit)((result, target) =>
            result.flatMap(_ => _inspect_physical(target, budget))).map(_ => Verified)
        }
      }.recoverWith(_ => Consequence.success(Refused))
    } catch {
      case NonFatal(_) => Consequence.success(Refused)
    }
  }

  private def _metadata_targets(record: DurableJobRecord)
      (using ctx: ExecutionContext): Consequence[Vector[VerifiedTarget]] =
    if (referenceCount(record) > maxReferences) _refused
    else {
      val refs = references(record)
      val owned = refs.filter(_.storageKind == storageKind)
      val distinct = owned.map(_.reference).distinct
      val protectedids = (refs.filterNot(_.storageKind == storageKind) ++
        record.body.definitionSnapshot.declaredSource.toVector)
        .flatMap(ref => _canonical_id(ref.reference).toOption.map(_.value)).toSet
      if (distinct.exists(protectedids.contains) || owned.groupBy(_.reference).values.exists(_.distinct.size != 1))
        _refused
      else distinct.foldLeft(Consequence.success(Vector.empty[VerifiedTarget])) { (result, reference) =>
        result.flatMap { targets =>
          for {
            id <- _canonical_id(reference)
            loaded <- entitystore.load[Blob](id)
            blob <- loaded.fold[Consequence[Blob]](_refused)(Consequence.success)
            target <- _target(record, owned.find(_.reference == reference).get, id, blob)
          } yield targets :+ target
        }
      }
    }

  private def _inspect_physical(target: VerifiedTarget, budget: DurableJobIntegrityByteBudget): Consequence[Unit] =
    blobstore.get(target.storageRef).flatMap { returned =>
      val fact = target.fact
      if (returned == null || returned.id != target.id || returned.storageRef != target.storageRef ||
          returned.contentType == null || returned.contentType.header != fact.contenttype ||
          returned.byteSize != fact.bytesize || returned.digest == null ||
          !returned.digest.equalsIgnoreCase(fact.payloadsha256) || returned.payload == null) _refused
      else {
        val stream = returned.payload.openInputStream()
        if (stream == null) _refused
        else try {
          val digest = MessageDigest.getInstance("SHA-256")
          val buffer = new Array[Byte](8192)
          val limit = Math.addExact(fact.bytesize, 1L)
          var total = 0L
          var ended = false
          while (!ended && total < limit) {
            if (Thread.currentThread().isInterrupted) throw new InterruptedException()
            val length = math.min(buffer.length.toLong, limit - total).toInt
            val count = stream.read(buffer, 0, length)
            if (count < 0) ended = true
            else if (count == 0) {
              val byte = stream.read()
              if (byte < 0) ended = true
              else {
                digest.update(byte.toByte)
                budget.recordRead(1)
                total = Math.addExact(total, 1L)
              }
            } else {
              if (count > length) throw new IllegalStateException("durable integrity inspection refused")
              digest.update(buffer, 0, count)
              budget.recordRead(count)
              total = Math.addExact(total, count.toLong)
            }
          }
          val actual = digest.digest().map(value => f"${value & 0xff}%02x").mkString
          if (ended && total == fact.bytesize && actual.equalsIgnoreCase(fact.payloadsha256)) Consequence.unit
          else _refused
        } catch {
          case exception: InterruptedException => Thread.currentThread().interrupt(); throw exception
        } finally stream.close()
      }
    }
  def delete(target: DurableJobOwnedBlobTarget)(using ctx: ExecutionContext): Consequence[Unit] = {
    val result = _bounded {
      target match {
        case verified: VerifiedTarget if verified.storageRef.store == blobstore.name =>
          for {
            _ <- _quota.prepareTargets(Vector(verified.fact))
            _ <- blobstore.delete(verified.storageRef)
            _ <- _quota.releaseTarget(verified.fact)
          } yield ()
        case _ => _refused
      }
    }
    if (store == null) result else store.operationalMetricsRecorder.observePayloadDelete(result)
  }
  private def _active(
    snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess
  )(using ctx: ExecutionContext): Consequence[DurableJobStoreSnapshot] =
    if (snapshot == null || snapshot.record == null) _refused
    else
      DurableJobRecordCodec.canonicalJson(snapshot.record).flatMap { canonical =>
        DurableJobRecordCodec.startupAdmission(canonical, access) match {
          case DurableJobRecordStartupAdmission.Admitted(record) if record.body.retention.deletionState == DurableDeletionState.Active =>
            store.loadStartupRecovery(record.body.identity.jobId, access) match {
              case DurableJobStoreStartupRecoveryLoad.Admitted(current) if current == snapshot => Consequence.success(current)
              case _ => _refused
            }
          case _ => _refused
        }
      }

  private def _publish(
    snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess,
    id: EntityId,
    filename: Option[String],
    put: BlobPutResult,
    attributes: Map[String, String],
    lease: DurableJobOwnedBlobQuotaLease
  )(using ctx: ExecutionContext): Consequence[DurableExternalReference] = {
    val acknowledged = _acknowledged(id, put, lease)
    val published = _bounded {
      if (acknowledged.isEmpty)
        _refused
      else {
        val reserved = attributes + (bindingAttribute -> bindingFingerprint(id, put.storageRef, put.contentType, put.byteSize, put.digest))
        val reference = DurableExternalReference(id.value, storageKind, Some(put.contentType.header), put.byteSize, put.digest)
        for {
          created <- entitystore.create(BlobCreate(id, BlobKind.Binary, BlobSourceMode.Managed, filename,
            Some(put.contentType), Some(put.byteSize), Some(put.digest), Some(put.storageRef), None, BlobUrl.cncfRoute(id), reserved))
          _ <- if (created.id == id) Consequence.unit else _refused
          loaded <- entitystore.load[Blob](id)
          blob <- loaded.fold[Consequence[Blob]](_refused)(Consequence.success)
          target <- _target(snapshot.record, reference, id, blob)
          _ <- if (blob.kind == BlobKind.Binary && blob.filename == filename && blob.accessUrl == BlobUrl.cncfRoute(id) &&
              blob.attributes == reserved && target.fact == acknowledged.get) Consequence.unit else _refused
          _ <- _active(snapshot, access)
          _ <- _quota.confirm(lease, target.fact)
        } yield reference
      }
    }
    published match {
      case success @ Consequence.Success(_) => success
      case Consequence.Failure(_) =>
        // Only an acknowledgement tied to this allocation and configured store
        // proves a rollback handle. Retain metadata as recovery/audit evidence.
        if (put != null && put.id == id && DurableJobOwnedBlobResourcePolicy.validRef(put.storageRef) &&
            put.storageRef.store == blobstore.name)
          _bounded(blobstore.delete(put.storageRef).flatMap(_ => _quota.releaseIssued(lease, acknowledged)))
        _refused
    }
  }

  private def _target(
    record: DurableJobRecord,
    reference: DurableExternalReference,
    id: EntityId,
    blob: Blob
  ): Consequence[VerifiedTarget] = {
    val valid = blob != null && blob.id == id && blob.sourceMode == BlobSourceMode.Managed &&
      blob.externalUrl.isEmpty && blob.attributes.get(ownershipAttribute).contains("exclusive-v1") &&
      blob.attributes.get(ownerAttribute).contains(ownerFingerprint(record)) &&
      blob.storageRef.exists(ref => DurableJobOwnedBlobResourcePolicy.validRef(ref) && ref.store == blobstore.name) &&
      blob.contentType.exists(DurableJobOwnedBlobResourcePolicy.validContent) &&
      blob.byteSize.contains(reference.byteSize) && reference.byteSize >= 0 &&
      blob.digest.contains(reference.sha256) && Option(reference.sha256).exists(_.matches("[0-9a-fA-F]{64}")) &&
      blob.contentType.map(_.header) == reference.contentType
    if (!valid) _refused
    else {
      val ref = blob.storageRef.get
      val binding = bindingFingerprint(id, ref, blob.contentType.get, blob.byteSize.get, blob.digest.get)
      if (blob.attributes.get(bindingAttribute).contains(binding))
        DurableJobOwnedBlobQuotaInventory.metadata(blob).flatMap {
          case Some(fact) => Consequence.success(VerifiedTarget(id, ref, fact))
          case None => _refused
        }
      else _refused
    }
  }

  private def _canonical_id(value: String): Consequence[EntityId] =
    DurableJobOwnedBlobResourcePolicy.canonicalId(value).recoverWith(_ => _refused)

  private def _acknowledged(id: EntityId, put: BlobPutResult, lease: DurableJobOwnedBlobQuotaLease)
      : Option[DurableJobOwnedBlobQuotaMetadata] =
    if (put == null || put.id != id || !DurableJobOwnedBlobResourcePolicy.validRef(put.storageRef) ||
        put.storageRef.store != blobstore.name || !DurableJobOwnedBlobResourcePolicy.validContent(put.contentType) ||
        put.contentType.header != lease.entry.contenttype || put.byteSize != lease.entry.bytesize ||
        !DurableJobOwnedBlobResourcePolicy.digest(put.digest) || !put.digest.equalsIgnoreCase(lease.entry.payloadsha256)) None
    else Some(DurableJobOwnedBlobQuotaMetadata(id.value, lease.entry.ownersha256, lease.entry.contenttype,
      lease.entry.bytesize, lease.entry.payloadsha256,
      bindingFingerprint(id, put.storageRef, put.contentType, put.byteSize, put.digest)))

  private def _refused[A]: Consequence[A] = Consequence.stateInvalid("Durable Job-owned payload refused")

  private def _bounded[A](body: => Consequence[A]): Consequence[A] =
    try body match {
      case success @ Consequence.Success(_) => success
      case _ => _refused
    } catch {
      case NonFatal(_) => _refused
    }
}
