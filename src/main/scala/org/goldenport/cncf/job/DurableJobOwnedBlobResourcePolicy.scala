package org.goldenport.cncf.job

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.bag.BinaryBag
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.blob.{BlobRepository, BlobStorageRef}
import org.simplemodeling.model.datatype.EntityId

/*
 * Inclusive private payload capacity and detached, single-stream admission.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobOwnedBlobResourcePolicy(
  maxownedblobs: Int = 4096,
  maxownedbytes: Long = 268435456L,
  maxpayloadbytes: Int = 8388608
) {
  def isValid: Boolean = maxownedblobs > 0 && maxownedblobs <= 4096 &&
    maxownedbytes > 0L && maxownedbytes <= 268435456L &&
    maxpayloadbytes > 0 && maxpayloadbytes <= 8388608

  def validate: Consequence[Unit] =
    if (isValid) Consequence.unit else DurableJobOwnedBlobResourcePolicy.invalid

  def detach(payload: BinaryBag): Consequence[DurableJobOwnedBlobDetachedPayload] =
    DurableJobOwnedBlobResourcePolicy.protect {
      validate.flatMap { _ =>
        if (payload == null) DurableJobOwnedBlobResourcePolicy.invalid
        else {
          val stream = payload.openInputStream()
          try {
            val output = new ByteArrayOutputStream(math.min(8192, maxpayloadbytes + 1))
            val buffer = new Array[Byte](math.min(8192, maxpayloadbytes + 1))
            var total = 0
            var ended = false
            while (!ended && total <= maxpayloadbytes) {
              val count = stream.read(buffer, 0, math.min(buffer.length, maxpayloadbytes + 1 - total))
              if (count < 0) ended = true
              else if (count == 0) {
                val byte = stream.read()
                if (byte < 0) ended = true
                else { output.write(byte); total += 1 }
              } else { output.write(buffer, 0, count); total += count }
            }
            if (total > maxpayloadbytes) DurableJobOwnedBlobResourcePolicy.invalid
            else {
              val bytes = output.toByteArray
              Consequence.success(DurableJobOwnedBlobDetachedPayload(bytes,
                DurableJobOwnedBlobResourcePolicy.sha256(bytes)))
            }
          } finally stream.close()
        }
      }
    }
}

private[job] final case class DurableJobOwnedBlobDetachedPayload(bytes: Array[Byte], sha256: String)

private[job] object DurableJobOwnedBlobResourcePolicy {
  val default: DurableJobOwnedBlobResourcePolicy = DurableJobOwnedBlobResourcePolicy()
  val maximumEntries = 16384
  val maximumCensusRows = 32768
  val pageSize = 32
  val maximumAttempts = 8
  val maximumLedgerBytes = 16777216
  val maximumLedgerDepth = 16
  val maximumPayloadBytes = 8388608L
  val maximumIdBytes = 1024
  val maximumContentBytes = 512
  val maximumFilenameBytes = 1024
  val maximumStoreBytes = 256
  val maximumRefBytes = 2048
  val invalidLabel = "Durable Job-owned Blob quota invalid"
  val capacityLabel = "Durable Job-owned Blob quota capacity unavailable"
  val unavailableLabel = "Durable Job-owned Blob quota unavailable"
  val confirmationLabel = "Durable Job-owned Blob quota confirmation unavailable"

  def boundedText(value: String, maximum: Int, nonblank: Boolean = true): Boolean =
    value != null && value.length <= maximum && (!nonblank || !value.isBlank) &&
      value.getBytes(StandardCharsets.UTF_8).length <= maximum

  def canonicalId(value: String): Consequence[EntityId] =
    if (!boundedText(value, maximumIdBytes)) invalid
    else EntityId.parse(value) match {
      case Consequence.Success(id) if id.value == value && id.collection == BlobRepository.CollectionId => Consequence.success(id)
      case _ => invalid
    }

  def validContent(contentType: ContentType): Boolean =
    contentType != null && boundedText(contentType.header, maximumContentBytes)

  def validRef(ref: BlobStorageRef): Boolean = ref != null &&
    boundedText(ref.store, maximumStoreBytes) &&
    boundedText(ref.container, maximumRefBytes) && boundedText(ref.key, maximumRefBytes) &&
    ref.version != null && ref.etag != null &&
    ref.version.forall(boundedText(_, maximumRefBytes, false)) &&
    ref.etag.forall(boundedText(_, maximumRefBytes, false)) && boundedText(ref.print, maximumRefBytes)

  def hex(value: String, size: Int): Boolean = value != null && value.length == size &&
    value.forall(character => (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f'))

  def digest(value: String): Boolean = value != null && value.length == 64 &&
    hex(value.toLowerCase(java.util.Locale.ROOT), 64)

  def sha256(bytes: Array[Byte]): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .map(value => f"${value & 0xff}%02x").mkString

  def invalid[A]: Consequence[A] = Consequence.stateInvalid(invalidLabel)
  def capacity[A]: Consequence[A] = Consequence.stateInvalid(capacityLabel)
  def unavailable[A]: Consequence[A] = Consequence.stateInvalid(unavailableLabel)
  def confirmation[A]: Consequence[A] = Consequence.stateInvalid(confirmationLabel)
  def protect[A](body: => Consequence[A]): Consequence[A] =
    try body catch { case NonFatal(_) => unavailable }
}
