package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.observation.Descriptor
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
sealed abstract class EntityDeleteRootMutation

object EntityDeleteRootMutation {
  case object Physical extends EntityDeleteRootMutation
  final case class Logical(changes: Record) extends EntityDeleteRootMutation
}

final case class EntityCompareAndDeletePlan(
  collection: DataStore.CollectionId,
  entryId: DataStore.EntryId,
  revisionField: String,
  expectedRevision: EntityRevision,
  rootMutation: EntityDeleteRootMutation,
  exclusionGuards: Vector[EntityMutationExclusionGuard] = Vector.empty,
  sideEffects: Vector[EntityVersionedSideEffect.Delete] = Vector.empty
)

sealed abstract class EntityCompareAndDeleteResult

object EntityCompareAndDeleteResult {
  final case class Deleted(matchedRevision: EntityRevision)
      extends EntityCompareAndDeleteResult
  final case class Stale(
    expectedRevision: EntityRevision,
    actualRevision: EntityRevision
  ) extends EntityCompareAndDeleteResult
}

private[datastore] object EntityObservedDeleteSupport {
  def validate(plan: EntityCompareAndDeletePlan): Consequence[Unit] =
    if (plan == null)
      Consequence.argumentMissing("plan")
    else if (plan.expectedRevision == null)
      Consequence.argumentMissing("expectedRevision")
    else if (plan.rootMutation == null)
      Consequence.argumentMissing("rootMutation")
    else if (plan.exclusionGuards == null)
      Consequence.argumentMissing("exclusionGuards")
    else if (plan.sideEffects == null)
      Consequence.argumentMissing("sideEffects")
    else
      for {
        _ <- _validate_collection("collection", plan.collection)
        _ <- _validate_entry("entryId", plan.entryId)
        _ <- _validate_name("revisionField", plan.revisionField)
        _ <- _validate_guards(plan.exclusionGuards)
        _ <- _validate_effects(plan)
        _ <- _validate_root(plan)
      } yield ()

  def requireCapabilities(
    capabilities: EntityMutationProviderCapabilities,
    hasSideEffects: Boolean
  ): Consequence[Unit] = {
    val required = Vector(
      EntityMutationProviderFeature.OptimisticCompareAndDelete,
      EntityMutationProviderFeature.AuthoritativeDeleteResult
    ) ++ (if (hasSideEffects)
      Vector(EntityMutationProviderFeature.AtomicSideEffects)
    else Vector.empty)
    required.foldLeft(Consequence.unit) { (result, feature) =>
      result.flatMap { _ =>
        if (capabilities != null && capabilities.features != null &&
            capabilities.supports(feature))
          Consequence.unit
        else
          EntityNativeMutationSupport.unsupported(
            "entity-compare-and-delete",
            feature
          )
      }
    }
  }

  def validateResult(
    plan: EntityCompareAndDeletePlan,
    result: EntityCompareAndDeleteResult
  ): Consequence[EntityCompareAndDeleteResult] =
    result match {
      case EntityCompareAndDeleteResult.Deleted(matched)
          if matched != null && matched == plan.expectedRevision =>
        Consequence.success(result)
      case EntityCompareAndDeleteResult.Stale(expected, actual)
          if expected != null && actual != null &&
            expected == plan.expectedRevision && actual != expected =>
        Consequence.success(result)
      case _ =>
        invalidResult
    }

  def invalidResult[A]: Consequence[A] =
    Consequence.operationInvalid(
      "entity-compare-and-delete-result",
      Vector(
        Descriptor.Facet.Reason("invalid-authoritative-delete-result"),
        Descriptor.Facet.Capability(
          "datastore.entity-mutation.authoritative-delete-result"
        )
      )
    )

  private def _validate_root(plan: EntityCompareAndDeletePlan): Consequence[Unit] =
    plan.rootMutation match {
      case EntityDeleteRootMutation.Physical => Consequence.unit
      case EntityDeleteRootMutation.Logical(changes) =>
        if (changes == null)
          Consequence.argumentMissing("rootMutation.changes")
        else if (plan.sideEffects.nonEmpty)
          Consequence.argumentPolicyViolation(
            "sideEffects", "entity-observed-delete.logical-preservation",
            "logical deletion without side effects", "side effects"
          )
        else if (changes.fields.isEmpty)
          Consequence.argumentMissing("rootMutation.changes")
        else if (changes.fields.exists(field => field == null || field.value == null || field.value.single == null))
          Consequence.argumentMissing("rootMutation.changes.field")
        else {
          val keys = changes.fields.map(_.key)
          if (keys.distinct.size != keys.size)
            Consequence.argumentInvalid(
              "rootMutation.changes", "unique patch keys", "duplicate keys"
            )
          else if (keys.contains(plan.revisionField) || keys.contains("id"))
            Consequence.argumentPolicyViolation(
              "rootMutation.changes", "framework-managed-identity-revision",
              "patch without id or managed revision", "managed field"
            )
          else
            keys.foldLeft(Consequence.unit) { (result, key) =>
              result.flatMap(_ => _validate_name("rootMutation.changes.key", key))
            }
        }
    }

  private def _validate_guards(
    guards: Vector[EntityMutationExclusionGuard]
  ): Consequence[Unit] =
    guards.foldLeft(Consequence.unit) { (result, guard) =>
      result.flatMap { _ =>
        guard match {
          case null => Consequence.argumentMissing("exclusionGuards.guard")
          case EntityMutationExclusionGuard.Present(fieldname) =>
            _validate_name("exclusionGuards.fieldName", fieldname)
          case EntityMutationExclusionGuard.EqualTo(fieldname, value) =>
            for {
              _ <- _validate_name("exclusionGuards.fieldName", fieldname)
              _ <- DataStoreConditionalValue.from(value)
            } yield ()
        }
      }
    }

  private def _validate_effects(plan: EntityCompareAndDeletePlan): Consequence[Unit] =
    if (plan.sideEffects.size > 64)
      Consequence.argumentLimitExceeded(
        "sideEffects", 64, plan.sideEffects.size, "entity-observed-delete.side-effects"
      )
    else
      plan.sideEffects.foldLeft(Consequence.unit) { (result, effect) =>
        result.flatMap { _ =>
          if (effect == null)
            Consequence.argumentMissing("sideEffects.effect")
          else
            for {
              _ <- _validate_collection("sideEffects.collection", effect.collection)
              _ <- _validate_entry("sideEffects.entryId", effect.entryId)
            } yield ()
        }
      }.flatMap { _ =>
        val rootkey = plan.collection.print -> plan.entryId.print
        val keys = plan.sideEffects.map(effect => effect.collection.print -> effect.entryId.print)
        if (keys.contains(rootkey))
          Consequence.argumentPolicyViolation(
            "sideEffects", "entity-observed-delete.root-isolation",
            "effects excluding the guarded root", "root target"
          )
        else if (keys.distinct.size != keys.size)
          Consequence.argumentInvalid("sideEffects", "unique targets", "duplicate targets")
        else
          Consequence.unit
      }

  private def _validate_collection(
    path: String,
    collection: DataStore.CollectionId
  ): Consequence[Unit] =
    collection match {
      case null => Consequence.argumentMissing(path)
      case DataStore.CollectionId.Instance(name) => _validate_name(path, name)
      case DataStore.CollectionId.EntityStore(null) => Consequence.argumentMissing(path)
      case DataStore.CollectionId.EntityStore(id) =>
        for {
          _ <- _validate_name(path, id.major)
          _ <- _validate_name(path, id.minor)
          _ <- _validate_name(path, id.name)
        } yield ()
    }

  private def _validate_entry(path: String, entry: DataStore.EntryId): Consequence[Unit] =
    entry match {
      case null => Consequence.argumentMissing(path)
      case DataStore.StringEntryId(id) => _validate_name(path, id)
      case DataStore.UniversalEntryId(null) => Consequence.argumentMissing(path)
      case other => _validate_name(path, other.print)
    }

  private def _validate_name(path: String, value: String): Consequence[Unit] =
    if (value == null || value.trim.isEmpty)
      Consequence.argumentMissing(path)
    else if (value.exists(_.isControl))
      Consequence.argumentInvalid(path, "name without control characters", "invalid name")
    else
      Consequence.unit
}
