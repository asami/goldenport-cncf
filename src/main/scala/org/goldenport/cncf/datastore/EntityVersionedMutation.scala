package org.goldenport.cncf.datastore

import scala.util.Try
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.{Field, Record}
import org.simplemodeling.model.directive.Update

/*
 * @since   Jul. 24, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
sealed abstract class DataStoreRevisionState

object DataStoreRevisionState {
  case object Absent extends DataStoreRevisionState
  final case class Present(value: Long) extends DataStoreRevisionState
}

sealed abstract class EntityVersionedRootMutation

object EntityVersionedRootMutation {
  final case class Replace(record: Record) extends EntityVersionedRootMutation
  final case class Patch(changes: Record) extends EntityVersionedRootMutation
}

sealed abstract class EntityVersionedSideEffect {
  def collection: DataStore.CollectionId
  def entryId: DataStore.EntryId
}

object EntityVersionedSideEffect {
  final case class Save(
    collection: DataStore.CollectionId,
    entryId: DataStore.EntryId,
    record: Record
  ) extends EntityVersionedSideEffect

  final case class Delete(
    collection: DataStore.CollectionId,
    entryId: DataStore.EntryId
  ) extends EntityVersionedSideEffect
}

final case class EntityVersionedMutationPlan(
  collection: DataStore.CollectionId,
  entryId: DataStore.EntryId,
  revisionField: String,
  expectedRevision: DataStoreRevisionState,
  nextRevision: Long,
  rootMutation: EntityVersionedRootMutation,
  sideEffects: Vector[EntityVersionedSideEffect] = Vector.empty
)

sealed abstract class EntityVersionedMutationResult

object EntityVersionedMutationResult {
  final case class Applied(record: Record) extends EntityVersionedMutationResult
  final case class Stale(actualRevision: DataStoreRevisionState)
      extends EntityVersionedMutationResult
}

trait EntityVersionedMutationDataStore { self: DataStore =>
  def mutateVersionedEntity(
    plan: EntityVersionedMutationPlan
  )(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult]
}

private[datastore] object EntityVersionedMutationSupport {
  def validate(
    plan: EntityVersionedMutationPlan
  ): Consequence[Unit] =
    for {
      _ <- _validate_revision_field(plan)
      _ <- _validate_revision_progression(plan)
      _ <- _validate_root_mutation(plan)
      _ <- _validate_side_effects(plan)
    } yield ()

  def revisionState(
    record: Record,
    revisionfield: String
  ): Consequence[DataStoreRevisionState] = {
    val values = record.fields.collect {
      case field if field.key == revisionfield =>
        _single_value(field.value.single)
    }
    values match {
      case Vector() =>
        Consequence.success(DataStoreRevisionState.Absent)
      case Vector(value) =>
        _exact_long(value) match {
          case Some(number) if number >= 0L =>
            Consequence.success(DataStoreRevisionState.Present(number))
          case _ =>
            Consequence.argumentFormatError(
              revisionfield,
              "one non-negative integral Long",
              value
            )
        }
      case _ =>
        Consequence.argumentInvalid(
          revisionfield,
          "one framework-managed revision value",
          s"${values.size} values"
        )
    }
  }

  def applyRootMutation(
    existing: Record,
    plan: EntityVersionedMutationPlan
  ): Record = {
    val changed = plan.rootMutation match {
      case EntityVersionedRootMutation.Replace(record) =>
        record
      case EntityVersionedRootMutation.Patch(changes) =>
        _merge(existing, changes)
    }
    _without_field(changed, plan.revisionField) ++
      Record.dataAuto(plan.revisionField -> plan.nextRevision)
  }

  private def _validate_revision_field(
    plan: EntityVersionedMutationPlan
  ): Consequence[Unit] =
    if (plan.revisionField.trim.nonEmpty)
      Consequence.unit
    else
      Consequence.argumentMissing("revisionField")

  private def _validate_revision_progression(
    plan: EntityVersionedMutationPlan
  ): Consequence[Unit] = {
    val expectednext = plan.expectedRevision match {
      case DataStoreRevisionState.Absent =>
        Some(1L)
      case DataStoreRevisionState.Present(value)
          if value >= 0L && value < Long.MaxValue =>
        Some(value + 1L)
      case _ =>
        None
    }
    expectednext match {
      case Some(value) if value == plan.nextRevision =>
        Consequence.unit
      case Some(value) =>
        Consequence.argumentExpectedActualMismatch(
          "nextRevision",
          value,
          plan.nextRevision
        )
      case None =>
        Consequence.argumentLimitExceeded(
          "expectedRevision",
          Long.MaxValue - 1L,
          plan.expectedRevision,
          "entity-versioned-mutation.advance"
        )
    }
  }

  private def _validate_root_mutation(
    plan: EntityVersionedMutationPlan
  ): Consequence[Unit] = {
    val record = plan.rootMutation match {
      case EntityVersionedRootMutation.Replace(value) => value
      case EntityVersionedRootMutation.Patch(value) => value
    }
    if (record.fields.exists(_.key == plan.revisionField))
      Consequence.argumentPolicyViolation(
        "rootMutation",
        "framework-managed-revision",
        s"record without ${plan.revisionField}",
        plan.revisionField
      )
    else
      Consequence.unit
  }

  private def _validate_side_effects(
    plan: EntityVersionedMutationPlan
  ): Consequence[Unit] = {
    val rootkey = plan.collection.print -> plan.entryId.print
    val keys =
      plan.sideEffects.map(effect =>
        effect.collection.print -> effect.entryId.print
      )
    if (keys.contains(rootkey))
      Consequence.argumentPolicyViolation(
        "sideEffects",
        "entity-versioned-mutation.root-isolation",
        "side effects must not target the guarded root",
        rootkey
      )
    else if (keys.distinct.size != keys.size)
      Consequence.argumentInvalid(
        "sideEffects",
        "unique collection and entry-id targets",
        keys
      )
    else
      Consequence.unit
  }

  private def _merge(
    existing: Record,
    changes: Record
  ): Record = {
    val changedkeys = changes.fields.map(_.key).toSet
    val effectivechanges =
      changes.fields.filterNot(_is_set_null_marker)
    Record(
      existing.fields.filterNot(field => changedkeys.contains(field.key)) ++
        effectivechanges
    )
  }

  private def _without_field(
    record: Record,
    name: String
  ): Record =
    Record(record.fields.filterNot(_.key == name))

  private def _is_set_null_marker(
    field: Field
  ): Boolean =
    field.value.single match {
      case Update.SetNull => true
      case _ => false
    }

  private def _exact_long(
    value: Any
  ): Option[Long] =
    value match {
      case number: Byte => Some(number.toLong)
      case number: Short => Some(number.toLong)
      case number: Int => Some(number.toLong)
      case number: Long => Some(number)
      case number: BigInt if number.isValidLong => Some(number.toLong)
      case number: BigDecimal =>
        number.toBigIntExact.filter(_.isValidLong).map(_.toLong)
      case number: java.lang.Byte => Some(number.longValue)
      case number: java.lang.Short => Some(number.longValue)
      case number: java.lang.Integer => Some(number.longValue)
      case number: java.lang.Long => Some(number.longValue)
      case number: java.math.BigInteger =>
        Try(number.longValueExact).toOption
      case number: java.math.BigDecimal =>
        Try(number.toBigIntegerExact.longValueExact).toOption
      case _ => None
    }

  private def _single_value(
    value: Any
  ): Any =
    value match {
      case Some(content) => _single_value(content)
      case None => None
      case content => content
    }
}
