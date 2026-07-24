package org.goldenport.cncf.entity

import scala.util.Try
import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.cncf.datastore.DataStoreRevisionState
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

final case class EntityMutationExpectation(
  token: EntityConcurrencyToken
)

final case class EntityRecordSnapshot(
  record: Record,
  token: EntityConcurrencyToken
)

object EntityConcurrencyMetadata {
  val LOGICAL_FIELD_NAME =
    SimpleEntityStorageShapePolicy.CONCURRENCY_REVISION_LOGICAL_FIELD
  val STORAGE_FIELD_NAME =
    SimpleEntityStorageShapePolicy.CONCURRENCY_REVISION_STORAGE_FIELD

  def token(record: Record): Consequence[EntityConcurrencyToken] = {
    val values = record.fields.collect {
      case field
          if SimpleEntityStorageShapePolicy
            .isConcurrencyRevisionStorageField(field.key) =>
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
      _storage_record(EntityConcurrencyToken.INITIAL)

  def preserveForMutation(
    record: Record,
    existing: Record
  ): Consequence[Record] =
    token(existing).map { token =>
      val sanitized = withoutManagedField(record)
      if (_has_managed_field(existing))
        sanitized ++ _storage_record(token)
      else
        sanitized
    }

  def decodeEntity[A](
    record: Record
  )(
    decode: Record => Consequence[A]
  ): Consequence[A] =
    snapshot(record)(decode).map(_.entity)

  def snapshot[A](
    record: Record
  )(
    decode: Record => Consequence[A]
  ): Consequence[EntitySnapshot[A]] =
    for {
      token <- token(record)
      entity <- decode(withoutManagedField(record))
    } yield EntitySnapshot(entity, token)

  def recordSnapshot(
    record: Record
  ): Consequence[EntityRecordSnapshot] =
    token(record).map(value =>
      EntityRecordSnapshot(withoutManagedField(record), value)
    )

  def mutationRevision(
    expectation: EntityMutationExpectation
  ): Consequence[(DataStoreRevisionState, Long)] =
    EntityConcurrencyTokenSupport
      ._advance(expectation.token)
      .map { next =>
        val expected =
          if (expectation.token == EntityConcurrencyToken.LEGACY)
            DataStoreRevisionState.Absent
          else
            DataStoreRevisionState.Present(
              EntityConcurrencyTokenSupport._storage_value(expectation.token)
            )
        expected ->
          EntityConcurrencyTokenSupport._storage_value(next)
      }

  def staleMutation[A](
    expectation: EntityMutationExpectation,
    actual: DataStoreRevisionState
  ): Consequence.Failure[A] =
    Consequence.operationConflict(
      "entity-versioned-mutation",
      Vector(
        Descriptor.Facet.Reason("stale-entity-revision"),
        Descriptor.Facet.Policy("entity.optimistic-concurrency"),
        Descriptor.Facet.FieldPath(STORAGE_FIELD_NAME),
        Descriptor.Facet.Expected(
          EntityConcurrencyTokenSupport._storage_value(expectation.token)
        ),
        Descriptor.Facet.Actual(_revision_value(actual))
      )
    )

  def withoutManagedField(record: Record): Record =
    SimpleEntityStorageShapePolicy.withoutConcurrencyRevisionField(record)

  private def _has_managed_field(record: Record): Boolean =
    record.fields.exists(field =>
      SimpleEntityStorageShapePolicy
        .isConcurrencyRevisionStorageField(field.key)
    )

  private def _storage_record(
    token: EntityConcurrencyToken
  ): Record =
    Record.dataAuto(
      STORAGE_FIELD_NAME ->
        EntityConcurrencyTokenSupport._storage_value(token)
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

  private def _revision_value(
    revision: DataStoreRevisionState
  ): Any =
    revision match {
      case DataStoreRevisionState.Absent => "absent"
      case DataStoreRevisionState.Present(value) => value
    }
}
