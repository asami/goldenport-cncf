package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityWritePolicy,
  RevisionPreconditionPolicy
}
import org.goldenport.record.{Field, Record}
import org.simplemodeling.model.datatype.EntityRevision
import org.simplemodeling.model.directive.Update

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
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
  concurrencyPolicy: EntityConcurrencyPolicy,
  writePolicy: EntityWritePolicy,
  preconditionPolicy: RevisionPreconditionPolicy,
  expectedRevision: Option[EntityRevision],
  rootMutation: EntityVersionedRootMutation,
  comparisonExcludedFields: Set[String] = Set.empty,
  sideEffects: Vector[EntityVersionedSideEffect] = Vector.empty
)

sealed abstract class EntityVersionedMutationResult

object EntityVersionedMutationResult {
  final case class Applied(record: Record) extends EntityVersionedMutationResult
  final case class NoOp(record: Record) extends EntityVersionedMutationResult
  final case class Stale(
    expectedRevision: EntityRevision,
    actualRevision: EntityRevision
  )
      extends EntityVersionedMutationResult
}

enum EntityVersionedMutationCheckpoint {
  case RootPrepared
  case SideEffectsPrepared
  case BeforePublish
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
      _ <- _validate_policy(plan)
      _ <- _validate_root_mutation(plan)
      _ <- _validate_side_effects(plan)
    } yield ()

  def revision(
    record: Record,
    revisionfield: String
  ): Consequence[EntityRevision] = {
    val values = record.fields.collect {
      case field if field.key == revisionfield =>
        _single_value(field.value.single)
    }
    values match {
      case Vector() =>
        Consequence.argumentMissing(revisionfield)
      case Vector(value) =>
        EntityRevision.createC(value)
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
    plan: EntityVersionedMutationPlan,
    nextRevision: EntityRevision
  ): Record = {
    val changed = plan.rootMutation match {
      case EntityVersionedRootMutation.Replace(record) =>
        record
      case EntityVersionedRootMutation.Patch(changes) =>
        _merge(existing, changes)
    }
    _without_field(changed, plan.revisionField) ++
      Record.dataAuto(plan.revisionField -> nextRevision.value)
  }

  def desiredRecord(
    existing: Record,
    plan: EntityVersionedMutationPlan
  ): Record =
    plan.rootMutation match {
      case EntityVersionedRootMutation.Replace(record) =>
        record
      case EntityVersionedRootMutation.Patch(changes) =>
        _merge(existing, changes)
    }

  def businessStateEquals(
    existing: Record,
    desired: Record,
    plan: EntityVersionedMutationPlan
  ): Boolean = {
    val excluded = plan.comparisonExcludedFields + plan.revisionField
    _without_fields(existing, excluded).asMap ==
      _without_fields(desired, excluded).asMap
  }

  private def _validate_revision_field(
    plan: EntityVersionedMutationPlan
  ): Consequence[Unit] =
    if (plan.revisionField.trim.nonEmpty)
      Consequence.unit
    else
      Consequence.argumentMissing("revisionField")

  private def _validate_policy(
    plan: EntityVersionedMutationPlan
  ): Consequence[Unit] =
    if (
      plan.concurrencyPolicy == EntityConcurrencyPolicy.None &&
      plan.preconditionPolicy == RevisionPreconditionPolicy.ObservedRequired
    )
      Consequence.configurationInvalid(
        "Entity concurrency policy None cannot require an observed revision"
      )
    else if (
      plan.concurrencyPolicy == EntityConcurrencyPolicy.Optimistic &&
      plan.expectedRevision.isEmpty
    )
      Consequence.argumentMissing("expectedRevision")
    else if (
      plan.preconditionPolicy == RevisionPreconditionPolicy.ObservedRequired &&
      plan.expectedRevision.isEmpty
    )
      Consequence.argumentMissing("expectedRevision")
    else
      Consequence.unit

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

  private def _without_fields(
    record: Record,
    names: Set[String]
  ): Record =
    Record(record.fields.filterNot(field => names.contains(field.key)))

  private def _is_set_null_marker(
    field: Field
  ): Boolean =
    field.value.single match {
      case Update.SetNull => true
      case _ => false
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
