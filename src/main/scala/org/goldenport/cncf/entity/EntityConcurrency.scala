package org.goldenport.cncf.entity

import scala.util.Try
import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * @since   Jul. 24, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object EntityConcurrencyTokenSupport {
  private[cncf] def _create(value: Long): Consequence[EntityConcurrencyToken] =
    if (value >= 0L)
      Consequence.success(EntityConcurrencyToken.createInternal(value))
    else
      Consequence.argumentInvalid(
        "entityConcurrencyToken",
        "non-negative Long",
        value
      )

  private[cncf] def _advance(
    token: EntityConcurrencyToken
  ): Consequence[EntityConcurrencyToken] =
    if (_storage_value(token) < Long.MaxValue)
      Consequence.success(
        EntityConcurrencyToken.createInternal(_storage_value(token) + 1L)
      )
    else
      Consequence.argumentLimitExceeded(
        "entityConcurrencyToken",
        Long.MaxValue - 1L,
        _storage_value(token),
        "entity-concurrency-token.advance"
      )

  private[cncf] def _storage_value(token: EntityConcurrencyToken): Long =
    EntityConcurrencyToken.storageValueInternal(token)
}

final case class EntitySnapshot[A](
  entity: A,
  token: EntityConcurrencyToken
)

object EntityConcurrencyMetadata {
  val LOGICAL_FIELD_NAME = "cncfRevision"
  val STORAGE_FIELD_NAME = "cncf_revision"

  private val _normalized_field_name =
    _normalize(LOGICAL_FIELD_NAME)

  def token(record: Record): Consequence[EntityConcurrencyToken] = {
    val values = record.fields.collect {
      case field if _normalize(field.key) == _normalized_field_name =>
        _single_value(field.value.single)
    }
    values match {
      case Vector() =>
        Consequence.success(EntityConcurrencyToken.LEGACY)
      case Vector(value) =>
        _token_value(value)
      case _ =>
        Consequence.argumentInvalid(
          STORAGE_FIELD_NAME,
          "one framework-managed concurrency token",
          s"${values.size} values"
        )
    }
  }

  def initializeForCreate(record: Record): Record =
    withoutManagedField(record) ++
      Record.dataAuto(
        STORAGE_FIELD_NAME ->
          EntityConcurrencyTokenSupport._storage_value(
            EntityConcurrencyToken.INITIAL
          )
      )

  def withoutManagedField(record: Record): Record =
    Record(
      record.fields.filterNot(field =>
        _normalize(field.key) == _normalized_field_name
      )
    )

  private def _token_value(value: Any): Consequence[EntityConcurrencyToken] =
    _exact_long(value) match {
      case Some(number) =>
        EntityConcurrencyTokenSupport._create(number)
      case None =>
        Consequence.argumentFormatError(
          STORAGE_FIELD_NAME,
          "non-negative integral Long",
          value
        )
    }

  private def _exact_long(value: Any): Option[Long] =
    value match {
      case number: Byte =>
        Some(number.toLong)
      case number: Short =>
        Some(number.toLong)
      case number: Int =>
        Some(number.toLong)
      case number: Long =>
        Some(number)
      case number: BigInt if number.isValidLong =>
        Some(number.toLong)
      case number: BigDecimal =>
        number.toBigIntExact.filter(_.isValidLong).map(_.toLong)
      case number: java.lang.Byte =>
        Some(number.longValue)
      case number: java.lang.Short =>
        Some(number.longValue)
      case number: java.lang.Integer =>
        Some(number.longValue)
      case number: java.lang.Long =>
        Some(number.longValue)
      case number: java.math.BigInteger =>
        Try(number.longValueExact).toOption
      case number: java.math.BigDecimal =>
        Try(number.toBigIntegerExact.longValueExact).toOption
      case _ =>
        None
    }

  private def _single_value(value: Any): Any =
    value match {
      case Some(content) =>
        _single_value(content)
      case None =>
        None
      case content =>
        content
    }

  private def _normalize(name: String): String =
    name.filter(_.isLetterOrDigit).toLowerCase(java.util.Locale.ROOT)
}
