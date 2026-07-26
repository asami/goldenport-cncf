package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.datatype.Identifier
import org.goldenport.text.Presentable
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.{
  EntityConcurrencyPolicy,
  EntityWritePolicy,
  RevisionPreconditionPolicy
}
import org.goldenport.record.{Field, Record}
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.simplemodeling.model.datatype.EntityRevision
import org.simplemodeling.model.directive.Update
import org.simplemodeling.model.value.NominalScalar

/*
 * @since   Jul. 24, 2026
 * @version Jul. 26, 2026
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

enum EntityMutationProviderFeature {
  case GuardedVersionedMutation
  case DirectAlwaysWrite
  case OptimisticCompareAndSet
  case BusinessStateComparison
  case AtomicSideEffects
  case AuthoritativeRecordResult
}

final case class EntityMutationProviderCapabilities(
  features: Set[EntityMutationProviderFeature]
) {
  def supports(feature: EntityMutationProviderFeature): Boolean =
    features.contains(feature)

  def supportsAll(required: Set[EntityMutationProviderFeature]): Boolean =
    required.subsetOf(features)
}

object EntityMutationProviderCapabilities {
  val empty: EntityMutationProviderCapabilities =
    EntityMutationProviderCapabilities(Set.empty)

  val guardedBaseline: EntityMutationProviderCapabilities =
    EntityMutationProviderCapabilities(
      Set(
        EntityMutationProviderFeature.GuardedVersionedMutation,
        EntityMutationProviderFeature.BusinessStateComparison,
        EntityMutationProviderFeature.AtomicSideEffects,
        EntityMutationProviderFeature.AuthoritativeRecordResult
      )
    )
}

enum EntityMutationReadbackRequirement {
  case None
  case AuthoritativeRecord
}

enum EntityMutationExclusionGuard {
  case EqualTo(fieldName: String, value: Any)
  case Present(fieldName: String)
}

final case class EntityDirectMutationPlan(
  collection: DataStore.CollectionId,
  entryId: DataStore.EntryId,
  revisionField: String,
  changes: Record,
  readbackRequirement: EntityMutationReadbackRequirement =
    EntityMutationReadbackRequirement.None,
  exclusionGuards: Vector[EntityMutationExclusionGuard] = Vector.empty
)

final case class EntityCompareAndSetMutationPlan(
  collection: DataStore.CollectionId,
  entryId: DataStore.EntryId,
  revisionField: String,
  expectedRevision: EntityRevision,
  changes: Record,
  readbackRequirement: EntityMutationReadbackRequirement =
    EntityMutationReadbackRequirement.None,
  exclusionGuards: Vector[EntityMutationExclusionGuard] = Vector.empty
)

enum EntityMutationProviderReadback {
  case Omitted
  case Authoritative(record: Record)
}

sealed abstract class EntityMutationProviderResult

object EntityMutationProviderResult {
  final case class Applied(readback: EntityMutationProviderReadback)
      extends EntityMutationProviderResult
  final case class NoOp(readback: EntityMutationProviderReadback)
      extends EntityMutationProviderResult
  final case class Stale(
    expectedRevision: EntityRevision,
    actualRevision: EntityRevision
  )
      extends EntityMutationProviderResult

  def validateReadbackC(
    result: EntityMutationProviderResult,
    requirement: EntityMutationReadbackRequirement
  ): Consequence[EntityMutationProviderResult] =
    result match {
      case applied: Applied =>
        _validate_readback(applied.readback, requirement).map(_ => result)
      case noop: NoOp =>
        _validate_readback(noop.readback, requirement).map(_ => result)
      case _: Stale =>
        Consequence.success(result)
    }

  private def _validate_readback(
    readback: EntityMutationProviderReadback,
    requirement: EntityMutationReadbackRequirement
  ): Consequence[Unit] =
    (readback, requirement) match {
      case (
            EntityMutationProviderReadback.Omitted,
            EntityMutationReadbackRequirement.AuthoritativeRecord
          ) =>
        Consequence.operationInvalid(
          "entity-mutation-provider-result",
          Vector(
            Descriptor.Facet.Reason("missing-authoritative-readback"),
            Descriptor.Facet.Capability(
              "datastore.entity-mutation.authoritative-record-result"
            )
          )
        )
      case _ =>
        Consequence.unit
    }
}

enum EntityMutationExecutionPath {
  case DirectAlwaysWrite
  case GuardedVersionedFallback
  case OptimisticCompareAndSet
  case GuardedBusinessStateComparison
  case AtomicSideEffectMutation
}

final case class EntityMutationPathRequest(
  policy: org.goldenport.cncf.entity.EntityMutationExecutionPolicy,
  readbackRequirement: EntityMutationReadbackRequirement,
  hasSideEffects: Boolean
)

object EntityMutationPathPlanner {
  def selectC(
    capabilities: EntityMutationProviderCapabilities,
    request: EntityMutationPathRequest
  ): Consequence[EntityMutationExecutionPath] =
    request.policy.validateC.flatMap { policy =>
      if (_is_plain(policy, request))
        _plain_path(capabilities, request.readbackRequirement)
      else if (_is_pure_optimistic(policy, request))
        _optimistic_path(capabilities, request.readbackRequirement)
      else
        _require(
          capabilities,
          _required_features(policy, request),
          _execution_path(policy, request)
        )
    }

  private def _is_plain(
    policy: org.goldenport.cncf.entity.EntityMutationExecutionPolicy,
    request: EntityMutationPathRequest
  ): Boolean =
    !request.hasSideEffects &&
      policy.concurrencyPolicy == EntityConcurrencyPolicy.None &&
      policy.preconditionPolicy != RevisionPreconditionPolicy.ObservedRequired &&
      policy.writePolicy == EntityWritePolicy.AlwaysWrite

  private def _is_pure_optimistic(
    policy: org.goldenport.cncf.entity.EntityMutationExecutionPolicy,
    request: EntityMutationPathRequest
  ): Boolean =
    !request.hasSideEffects &&
      policy.writePolicy == EntityWritePolicy.AlwaysWrite &&
      (
        policy.concurrencyPolicy == EntityConcurrencyPolicy.Optimistic ||
        policy.preconditionPolicy ==
          RevisionPreconditionPolicy.ObservedRequired
      )

  private def _required_features(
    policy: org.goldenport.cncf.entity.EntityMutationExecutionPolicy,
    request: EntityMutationPathRequest
  ): Set[EntityMutationProviderFeature] = {
    val comparison =
      if (policy.writePolicy == EntityWritePolicy.WriteIfChanged)
        Set(
          EntityMutationProviderFeature.GuardedVersionedMutation,
          EntityMutationProviderFeature.BusinessStateComparison
        )
      else
        Set.empty
    val sideeffects =
      if (request.hasSideEffects)
        Set(
          EntityMutationProviderFeature.GuardedVersionedMutation,
          EntityMutationProviderFeature.AtomicSideEffects
        )
      else
        Set.empty
    _with_readback(
      comparison ++ sideeffects,
      request.readbackRequirement
    )
  }

  private def _execution_path(
    policy: org.goldenport.cncf.entity.EntityMutationExecutionPolicy,
    request: EntityMutationPathRequest
  ): EntityMutationExecutionPath =
    if (request.hasSideEffects)
      EntityMutationExecutionPath.AtomicSideEffectMutation
    else if (policy.writePolicy == EntityWritePolicy.WriteIfChanged)
      EntityMutationExecutionPath.GuardedBusinessStateComparison
    else if (
      policy.concurrencyPolicy == EntityConcurrencyPolicy.Optimistic ||
      policy.preconditionPolicy == RevisionPreconditionPolicy.ObservedRequired
    )
      EntityMutationExecutionPath.OptimisticCompareAndSet
    else
      EntityMutationExecutionPath.GuardedBusinessStateComparison

  private def _optimistic_path(
    capabilities: EntityMutationProviderCapabilities,
    readbackrequirement: EntityMutationReadbackRequirement
  ): Consequence[EntityMutationExecutionPath] = {
    val nativerequired =
      _with_readback(
        Set(EntityMutationProviderFeature.OptimisticCompareAndSet),
        readbackrequirement
      )
    if (capabilities.supportsAll(nativerequired))
      Consequence.success(EntityMutationExecutionPath.OptimisticCompareAndSet)
    else
      _require(
        capabilities,
        _with_readback(
          Set(EntityMutationProviderFeature.GuardedVersionedMutation),
          readbackrequirement
        ),
        EntityMutationExecutionPath.GuardedVersionedFallback
      )
  }

  private def _plain_path(
    capabilities: EntityMutationProviderCapabilities,
    readbackrequirement: EntityMutationReadbackRequirement
  ): Consequence[EntityMutationExecutionPath] = {
    val directrequired =
      _with_readback(
        Set(EntityMutationProviderFeature.DirectAlwaysWrite),
        readbackrequirement
      )
    if (capabilities.supportsAll(directrequired))
      Consequence.success(EntityMutationExecutionPath.DirectAlwaysWrite)
    else
      _require(
        capabilities,
        _with_readback(
          Set(EntityMutationProviderFeature.GuardedVersionedMutation),
          readbackrequirement
        ),
        EntityMutationExecutionPath.GuardedVersionedFallback
      )
  }

  private def _with_readback(
    required: Set[EntityMutationProviderFeature],
    readbackrequirement: EntityMutationReadbackRequirement
  ): Set[EntityMutationProviderFeature] =
    readbackrequirement match {
      case EntityMutationReadbackRequirement.None =>
        required
      case EntityMutationReadbackRequirement.AuthoritativeRecord =>
        required + EntityMutationProviderFeature.AuthoritativeRecordResult
    }

  private def _require(
    capabilities: EntityMutationProviderCapabilities,
    required: Set[EntityMutationProviderFeature],
    path: EntityMutationExecutionPath
  ): Consequence[EntityMutationExecutionPath] = {
    val missing = required.diff(capabilities.features)
    if (missing.isEmpty)
      Consequence.success(path)
    else
      Consequence.operationInvalid(
        "entity-mutation-provider-path",
        missing.toVector.sortBy(_.toString).map(feature =>
          Descriptor.Facet.Capability(
            s"datastore.entity-mutation.${_feature_name(feature)}"
          )
        )
      )
  }

  private def _feature_name(feature: EntityMutationProviderFeature): String =
    feature.toString
      .flatMap(character =>
        if (character.isUpper) s"-${character.toLower}" else character.toString
      )
      .stripPrefix("-")
}

enum EntityVersionedMutationCheckpoint {
  case RootPrepared
  case SideEffectsPrepared
  case BeforePublish
}

trait EntityVersionedMutationDataStore { self: DataStore =>
  def entityMutationProviderCapabilities: EntityMutationProviderCapabilities =
    EntityMutationProviderCapabilities.guardedBaseline

  def mutateEntityDirect(
    plan: EntityDirectMutationPlan
  )(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] =
    EntityNativeMutationSupport.unsupported(
      "entity-direct-mutation",
      EntityMutationProviderFeature.DirectAlwaysWrite
    )

  def compareAndSetEntity(
    plan: EntityCompareAndSetMutationPlan
  )(using ctx: ExecutionContext): Consequence[EntityMutationProviderResult] =
    EntityNativeMutationSupport.unsupported(
      "entity-compare-and-set-mutation",
      EntityMutationProviderFeature.OptimisticCompareAndSet
    )

  def mutateVersionedEntity(
    plan: EntityVersionedMutationPlan
  )(using ctx: ExecutionContext): Consequence[EntityVersionedMutationResult]
}

object EntityVersionedMutationFailure {
  val POLICY = "entity.versioned-mutation.provider"

  def providerFailure[A](
    message: String
  ): Consequence.Failure[A] =
    _service_unavailable(message, "provider-failure", Cause.Kind.Unknown)

  def transactionFailure[A](
    message: String
  ): Consequence.Failure[A] =
    _service_unavailable(
      message,
      "transaction-failure",
      Cause.Kind.Inconsistency
    )

  def transactionIndeterminate[A](
    message: String
  ): Consequence.Failure[A] =
    _service_unavailable(
      message,
      "transaction-indeterminate",
      Cause.Kind.Inconsistency
    )

  private[cncf] def normalizeProvider[A](
    conclusion: Conclusion
  ): Consequence.Failure[A] = {
    val reasons = conclusion.observation.cause.descriptor.facets.collect {
      case Descriptor.Facet.Reason(name) => name
    }.toSet
    val symptom = conclusion.observation.taxonomy.symptom
    if (
      reasons.exists(_recognized_reasons.contains) ||
      symptom == Taxonomy.Symptom.NotFound ||
      symptom == Taxonomy.Symptom.Conflict ||
      symptom == Taxonomy.Symptom.Unsupported ||
      symptom == Taxonomy.Symptom.PermissionDenied ||
      symptom == Taxonomy.Symptom.Invalid
    )
      Consequence.Failure(conclusion)
    else
      Consequence.Failure(_annotate(conclusion, "provider-failure"))
  }

  private val _recognized_reasons = Set(
    "provider-failure",
    "datastore-failure",
    "transaction-failure",
    "transaction-indeterminate",
    "transaction-rollback"
  )

  private def _service_unavailable[A](
    message: String,
    reason: String,
    kind: Cause.Kind
  ): Consequence.Failure[A] =
    Consequence.serviceUnavailable(
      message,
      kind,
      Vector(
        Descriptor.Facet.Reason(reason),
        Descriptor.Facet.Policy(POLICY)
      )
    )

  private def _annotate(
    conclusion: Conclusion,
    reason: String
  ): Conclusion = {
    val cause = conclusion.observation.cause
      .addFacet(Descriptor.Facet.Reason(reason))
      .addFacet(Descriptor.Facet.Policy(POLICY))
    conclusion.copy(
      observation = conclusion.observation.copy(cause = cause)
    )
  }
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
    revisionField: String
  ): Consequence[EntityRevision] = {
    val values = record.fields.collect {
      case field if field.key == revisionField =>
        _single_value(field.value.single)
    }
    values match {
      case Vector() =>
        Consequence.argumentMissing(revisionField)
      case Vector(value) =>
        EntityRevision.createC(value)
      case _ =>
        Consequence.argumentInvalid(
          revisionField,
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

  def applyNativeMutation(
    existing: Record,
    revisionField: String,
    changes: Record,
    nextRevision: EntityRevision
  ): Record =
    _without_field(_merge(existing, changes), revisionField) ++
      Record.dataAuto(revisionField -> nextRevision.value)

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
    _comparison_record(_without_fields(existing, excluded)) ==
      _comparison_record(_without_fields(desired, excluded))
  }

  private def _comparison_record(record: Record): Map[String, Any] =
    record.asMap.view.mapValues(_comparison_value).toMap

  private def _comparison_value(value: Any): Any =
    value match {
      case identifier: Identifier =>
        identifier.value
      case scalar: NominalScalar =>
        _comparison_value(scalar.value)
      case record: Record =>
        _comparison_record(record)
      case values: Seq[?] =>
        values.map(_comparison_value)
      case Some(content) =>
        _comparison_value(content)
      case None =>
        None
      case other =>
        other
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

private[datastore] object EntityNativeMutationSupport {
  def requireCapabilities(
    capabilities: EntityMutationProviderCapabilities,
    operation: String,
    feature: EntityMutationProviderFeature,
    readbackRequirement: EntityMutationReadbackRequirement
  ): Consequence[Unit] = {
    val required =
      readbackRequirement match {
        case EntityMutationReadbackRequirement.None =>
          Set(feature)
        case EntityMutationReadbackRequirement.AuthoritativeRecord =>
          Set(
            feature,
            EntityMutationProviderFeature.AuthoritativeRecordResult
          )
      }
    val missing = required.diff(capabilities.features)
    if (missing.isEmpty)
      Consequence.unit
    else
      _unsupported_capabilities(operation, missing)
  }

  def validate(
    plan: EntityDirectMutationPlan
  ): Consequence[Unit] =
    _validate(plan.revisionField, plan.changes)

  def validate(
    plan: EntityCompareAndSetMutationPlan
  ): Consequence[Unit] =
    _validate(plan.revisionField, plan.changes)

  def updatedRecord(
    existing: Record,
    revisionField: String,
    changes: Record,
    nextRevision: EntityRevision
  ): Record =
    EntityVersionedMutationSupport.applyNativeMutation(
      existing,
      revisionField,
      changes,
      nextRevision
    )

  def admitExisting(
    entryId: DataStore.EntryId,
    existing: Record,
    guards: Vector[EntityMutationExclusionGuard]
  ): Consequence[Unit] =
    guards.find {
      case EntityMutationExclusionGuard.EqualTo(fieldname, value) =>
        existing
          .getAny(fieldname)
          .exists(candidate => _canonical(candidate) == _canonical(value))
      case EntityMutationExclusionGuard.Present(fieldname) =>
        existing.getAny(fieldname).exists(_canonical(_).nonEmpty)
    } match {
      case Some(_) =>
        Consequence.entityNotFound(
          s"entity is not mutable: ${entryId.print}"
        )
      case None =>
        Consequence.unit
    }

  def applied(
    record: Record,
    requirement: EntityMutationReadbackRequirement
  ): EntityMutationProviderResult =
    EntityMutationProviderResult.Applied(
      requirement match {
        case EntityMutationReadbackRequirement.None =>
          EntityMutationProviderReadback.Omitted
        case EntityMutationReadbackRequirement.AuthoritativeRecord =>
          EntityMutationProviderReadback.Authoritative(record)
      }
    )

  def unsupported[A](
    operation: String,
    feature: EntityMutationProviderFeature
  ): Consequence[A] =
    _unsupported_capabilities(operation, Set(feature))

  private def _validate(
    revisionfield: String,
    changes: Record
  ): Consequence[Unit] =
    if (revisionfield.trim.isEmpty)
      Consequence.argumentMissing("revisionField")
    else if (changes.fields.exists(_.key == revisionfield))
      Consequence.argumentPolicyViolation(
        "changes",
        "framework-managed-revision",
        s"record without $revisionfield",
        revisionfield
      )
    else
      Consequence.unit

  private def _canonical(
    value: Any
  ): String =
    value match {
      case Some(content) => _canonical(content)
      case scalar: NominalScalar => _canonical(scalar.value)
      case other =>
        Presentable
          .print(other)
          .trim
          .toLowerCase(java.util.Locale.ROOT)
    }

  private def _feature_name(
    feature: EntityMutationProviderFeature
  ): String =
    feature.toString
      .flatMap(character =>
        if (character.isUpper) s"-${character.toLower}" else character.toString
      )
      .stripPrefix("-")

  private def _unsupported_capabilities[A](
    operation: String,
    missing: Set[EntityMutationProviderFeature]
  ): Consequence[A] =
    Consequence.operationInvalid(
      operation,
      Descriptor.Facet.Reason("unsupported-capability") +:
        missing.toVector.sortBy(_.toString).map(feature =>
          Descriptor.Facet.Capability(
            s"datastore.entity-mutation.${_feature_name(feature)}"
          )
        )
    )
}
