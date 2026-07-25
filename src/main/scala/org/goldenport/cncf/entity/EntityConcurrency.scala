package org.goldenport.cncf.entity

import scala.util.Try
import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */

final case class EntitySnapshot[A](
  entity: A,
  revision: EntityRevision
)

final case class EntityRevisionCarrier[A](
  entity: A,
  revision: EntityRevision
)

final case class EntityRecordSnapshot(
  record: Record,
  revision: EntityRevision
)

enum EntityUnversionedMutationPurpose {
  case SeedImport, PhysicalMigration, FrameworkBootstrap
}

object EntityConcurrencyMetadata {
  val LOGICAL_FIELD_NAME =
    SimpleEntityStorageShapePolicy.CONCURRENCY_REVISION_LOGICAL_FIELD
  val STORAGE_FIELD_NAME =
    SimpleEntityStorageShapePolicy.CONCURRENCY_REVISION_STORAGE_FIELD

  def revision(record: Record): Consequence[EntityRevision] = {
    val values = record.fields.collect {
      case field
          if SimpleEntityStorageShapePolicy
            .isConcurrencyRevisionStorageField(field.key) =>
        _single_value(field.value.single)
    }
    values match {
      case Vector() =>
        Consequence.argumentMissing(STORAGE_FIELD_NAME)
      case Vector(value) =>
        _revision_value(value)
      case _ =>
        Consequence.argumentInvalid(
          STORAGE_FIELD_NAME,
          "one framework-managed Entity revision",
          s"${values.size} values"
        )
    }
  }

  def transportRevision(value: Any): Consequence[EntityRevision] =
    _revision_value(value, "version")

  def initializeForCreate(record: Record): Record =
    withoutManagedField(record) ++
      _storage_record(EntityRevision.INITIAL)

  def preserveForMutation(
    record: Record,
    existing: Record
  ): Consequence[Record] =
    revision(existing).map { revision =>
      val sanitized = withoutManagedField(record)
      if (_has_managed_field(existing))
        sanitized ++ _storage_record(revision)
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
      revision <- revision(record)
      entity <- decode(withoutManagedField(record))
    } yield EntitySnapshot(entity, revision)

  def recordSnapshot(
    record: Record
  ): Consequence[EntityRecordSnapshot] =
    revision(record).map(value =>
      EntityRecordSnapshot(withoutManagedField(record), value)
    )

  def mutationRevision(
    expectedRevision: EntityRevision
  ): Consequence[(EntityRevision, EntityRevision)] =
    Option(expectedRevision) match {
      case Some(expected) =>
        expected.nextC.map(next => expected -> next)
      case None =>
        Consequence.argumentMissing("expectedRevision")
    }

  def staleMutation[A](
    expectedRevision: EntityRevision,
    actual: EntityRevision
  ): Consequence.Failure[A] =
    Consequence.operationConflict(
      "entity-versioned-mutation",
      Vector(
        Descriptor.Facet.Reason("stale-entity-revision"),
        Descriptor.Facet.Policy("entity.optimistic-concurrency"),
        Descriptor.Facet.FieldPath(STORAGE_FIELD_NAME),
        Descriptor.Facet.Expected(expectedRevision.value),
        Descriptor.Facet.Actual(actual.value)
      )
    )

  def committedProjectionFailure[A](
      previous: Conclusion
  ): Consequence.Failure[A] =
    Consequence.operationInvalid(
      "entity-versioned-mutation",
      Cause.Kind.Inconsistency,
      Vector(
        Descriptor.Facet.Reason("committed-entity-projection-failure"),
        Descriptor.Facet.Policy("entity.optimistic-concurrency")
      ),
      Some(previous)
    )

  def withoutManagedField(record: Record): Record =
    SimpleEntityStorageShapePolicy.withoutConcurrencyRevisionField(record)

  private def _has_managed_field(record: Record): Boolean =
    record.fields.exists(field =>
      SimpleEntityStorageShapePolicy
        .isConcurrencyRevisionStorageField(field.key)
    )

  private def _storage_record(
    revision: EntityRevision
  ): Record =
    Record.dataAuto(
      STORAGE_FIELD_NAME -> revision.value
    )

  private def _revision_value(
      value: Any,
      fieldname: String = STORAGE_FIELD_NAME
  ): Consequence[EntityRevision] =
    _exact_long(value) match {
      case Some(number) =>
        EntityRevision.createC(number)
      case None =>
        Consequence.argumentFormatError(
          fieldname,
          "positive integral Long",
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
      case text: String =>
        text.trim.toLongOption
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

}
