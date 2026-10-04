package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import scala.util.control.NonFatal
import org.goldenport.Consequence

/*
 * @since   Oct. 1, 2026
 * @version Oct. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobInputResourcePolicy {
  private val _max_payloads = 1024
  private val _max_bytes = 8388608L
  private val _max_metadata_bytes = 1048576L
  private val _max_encoded_characters = 11184812

  def validate(input: Option[JobInput]): Consequence[Unit] =
    if (!_valid_option(input)) invalid
    else input match {
      case None => Consequence.unit
      case Some(value) => _validate_input(value)
    }

  def invalid[A]: Consequence.Failure[A] =
    Consequence.argumentInvalid("JobEngine input request refused")

  private def _valid_option[A](value: Option[A]): Boolean =
    value != null && !value.exists(_ == null)

  private def _validate_input(input: JobInput): Consequence[Unit] =
    if (input.payloads == null || input.retentionPolicy == null || input.createdAt == null ||
        input.ttl == null || !_valid_option(input.cleanedAt)) invalid
    else if (input.payloads.size > _max_payloads) JobResourcePolicy.refused
    else _validate_time(input).flatMap { _ =>
      var bytes = 0L
      var metadata = 0L
      var result: Consequence[Unit] = Consequence.unit
      val entries = input.payloads.iterator
      while (entries.hasNext && result.isSuccess) {
        val payload = entries.next()
        result = _validate_payload(payload).flatMap { case (payloadbytes, payloadmetadata) =>
          if (payloadbytes > _max_bytes - bytes || payloadmetadata > _max_metadata_bytes - metadata)
            JobResourcePolicy.refused
          else {
            bytes += payloadbytes
            metadata += payloadmetadata
            Consequence.unit
          }
        }
      }
      result
    }

  private def _validate_time(input: JobInput): Consequence[Unit] =
    if (input.retentionPolicy != JobInputRetentionPolicy.Ttl) Consequence.unit
    else {
      try {
        val _ = input.createdAt.plus(input.ttl)
        Consequence.unit
      } catch {
        case NonFatal(_) => invalid
      }
    }

  private def _validate_payload(payload: JobInputPayload): Consequence[(Long, Long)] =
    if (payload == null || payload.storage == null || payload.createdAt == null ||
        !_valid_option(payload.fieldName) || !_valid_option(payload.filename) ||
        !_valid_option(payload.contentType) || !_valid_option(payload.byteSize) ||
        !_valid_option(payload.sha256) || !_valid_option(payload.inlineBase64) ||
        !_valid_option(payload.blobId)) invalid
    else {
      // Each string is bounded before trim/UTF-8 allocation; opaque values are retained.
      _text_bytes(payload.storage, 256).flatMap { storagebytes =>
        if (payload.storage.trim.isEmpty) invalid
        else {
          val fields = Iterator(
            payload.fieldName -> 512, payload.filename -> 1024,
            payload.contentType -> 512, payload.sha256 -> 512, payload.blobId -> 2048
          )
          var total = storagebytes
          var result: Consequence[Unit] = Consequence.unit
          while (fields.hasNext && result.isSuccess) {
            val (value, limit) = fields.next()
            result = value.fold[Consequence[Long]](Consequence.success(0L))(_text_bytes(_, limit)).map { size =>
              total += size
              ()
            }
          }
          result.flatMap(_ => _payload_bytes(payload).map(_ -> total))
        }
      }
    }

  private def _text_bytes(value: String, limit: Int): Consequence[Long] =
    if (value.length > limit) JobResourcePolicy.refused
    else {
      val size = value.getBytes(UTF_8).length.toLong
      if (size > limit) JobResourcePolicy.refused else Consequence.success(size)
    }

  private def _payload_bytes(payload: JobInputPayload): Consequence[Long] =
    if (payload.byteSize.exists(_ < 0L)) invalid
    else if (payload.byteSize.exists(_ > _max_bytes)) JobResourcePolicy.refused
    else payload.inlineBase64 match {
      case None => Consequence.success(payload.byteSize.getOrElse(0L))
      case Some(encoded) if encoded.length > _max_encoded_characters => JobResourcePolicy.refused
      case Some(encoded) =>
        val decoded = try {
          Consequence.success(Base64.getDecoder.decode(encoded).length.toLong)
        } catch {
          case NonFatal(_) => invalid[Long]
        }
        decoded.flatMap { size =>
          if (size > _max_bytes) JobResourcePolicy.refused
          else if (payload.byteSize.exists(_ != size)) invalid
          else Consequence.success(size)
        }
    }
}
