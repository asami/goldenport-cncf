package org.goldenport.cncf.job

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.datatype.ContentType
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.operation.OperationResponse

/*
 * Typed private publication and strict reading of verified owned native bytes.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object DurableJobOperationResponsePayloads {
  val contentType: ContentType = ContentType.parse("application/vnd.cncf.job-operation-response+json")

  def refused[A]: Consequence[A] =
    Consequence.stateInvalid("durable job operation response payload refused")
}

private[job] final class DurableJobOperationResponsePayloads(payloads: DurableJobOwnedBlobPayloads) {
  def publishObserved(
    snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess,
    response: OperationResponse
  )(using ctx: ExecutionContext): Consequence[DurableExternalReference] = _protected {
    if (snapshot == null || snapshot.record == null ||
        snapshot.record.body.lifecycle.status != DurableJobLifecycleStatus.Running ||
        snapshot.record.body.result != DurableResultOutcome.Pending) DurableJobOperationResponsePayloads.refused
    else DurableJobOperationResponseCodec.encode(response).flatMap { canonical =>
      payloads.putObserved(snapshot, access, Some("operation-response.json"),
        DurableJobOperationResponsePayloads.contentType, Bag.binary(canonical.getBytes(StandardCharsets.UTF_8)))
    }
  }

  def readObserved(
    snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess
  )(using ctx: ExecutionContext): Consequence[OperationResponse] = _protected {
    if (snapshot == null || snapshot.record == null ||
        snapshot.record.body.lifecycle.status != DurableJobLifecycleStatus.Succeeded)
      DurableJobOperationResponsePayloads.refused
    else snapshot.record.body.result match {
      case DurableResultOutcome.Succeeded(DurableValue.External(reference))
          if reference != null && reference.storageKind == DurableJobOwnedBlobPayloads.storageKind &&
            reference.contentType.contains(DurableJobOperationResponsePayloads.contentType.header) =>
        payloads.readObserved(snapshot, access, reference).flatMap { detached =>
          val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
          val canonical = decoder.decode(ByteBuffer.wrap(detached.bytes)).toString
          DurableJobOperationResponseCodec.decode(canonical)
        }
      case _ => DurableJobOperationResponsePayloads.refused
    }
  }

  private def _protected[A](body: => Consequence[A]): Consequence[A] =
    try {
      if (Thread.currentThread().isInterrupted) throw new InterruptedException()
      body match {
        case success @ Consequence.Success(_) => success
        case _ => DurableJobOperationResponsePayloads.refused
      }
    } catch {
      case _: InterruptedException => Thread.currentThread().interrupt(); DurableJobOperationResponsePayloads.refused
      case NonFatal(_) => DurableJobOperationResponsePayloads.refused
    }
}
