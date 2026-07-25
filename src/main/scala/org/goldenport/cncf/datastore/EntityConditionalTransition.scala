package org.goldenport.cncf.datastore

import java.time.Instant
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.datatype.Identifier
import org.goldenport.observation.{Cause, Descriptor, Taxonomy}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}
import org.simplemodeling.model.directive.Update

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final case class DataStoreComponentOwner private (
  name: String
)

object DataStoreComponentOwner {
  val MAX_NAME_LENGTH = 128

  def create(
    name: String
  ): Consequence[DataStoreComponentOwner] =
    EntityConditionalTransitionSupport
      .validateComponentOwner(DataStoreComponentOwner(name))
}

sealed abstract class DataStoreConditionalValue {
  def kind: String

  private[datastore] def matches(
    stored: Any
  ): Consequence[Boolean]
}

object DataStoreConditionalValue {
  final case class Text private[datastore] (value: String)
      extends DataStoreConditionalValue {
    val kind = "text"

    private[datastore] def matches(
      stored: Any
    ): Consequence[Boolean] =
      stored match {
        case value: String => Consequence.success(value == this.value)
        case other => _stored_type_failure(kind, other)
      }
  }

  final case class BooleanValue private[datastore] (value: Boolean)
      extends DataStoreConditionalValue {
    val kind = "boolean"

    private[datastore] def matches(
      stored: Any
    ): Consequence[Boolean] =
      stored match {
        case value: Boolean => Consequence.success(value == this.value)
        case other => _stored_type_failure(kind, other)
      }
  }

  final case class Integral private[datastore] (value: BigInt)
      extends DataStoreConditionalValue {
    val kind = "integral"

    private[datastore] def matches(
      stored: Any
    ): Consequence[Boolean] =
      _integral(stored) match {
        case Some(value) => Consequence.success(value == this.value)
        case None => _stored_type_failure(kind, stored)
      }
  }

  final case class Decimal private[datastore] (value: BigDecimal)
      extends DataStoreConditionalValue {
    val kind = "decimal"

    private[datastore] def matches(
      stored: Any
    ): Consequence[Boolean] =
      _decimal(stored) match {
        case Some(value) => Consequence.success(value == this.value)
        case None => _stored_type_failure(kind, stored)
      }
  }

  final case class InstantValue private[datastore] (value: Instant)
      extends DataStoreConditionalValue {
    val kind = "instant"

    private[datastore] def matches(
      stored: Any
    ): Consequence[Boolean] =
      stored match {
        case value: Instant => Consequence.success(value == this.value)
        case other => _stored_type_failure(kind, other)
      }
  }

  final case class IdentifierValue private[datastore] (value: String)
      extends DataStoreConditionalValue {
    val kind = "identifier"

    private[datastore] def matches(
      stored: Any
    ): Consequence[Boolean] =
      stored match {
        case value: Identifier => Consequence.success(value.value == this.value)
        case value: String => Consequence.success(value == this.value)
        case other => _stored_type_failure(kind, other)
      }
  }

  final case class EntityIdValue private[datastore] (value: String)
      extends DataStoreConditionalValue {
    val kind = "entity-id"

    private[datastore] def matches(
      stored: Any
    ): Consequence[Boolean] =
      stored match {
        case value: EntityId => Consequence.success(value.print == this.value)
        case value: String => Consequence.success(value == this.value)
        case other => _stored_type_failure(kind, other)
      }
  }

  val MAX_ENCODED_LENGTH = 4096

  def from(
    value: Any
  ): Consequence[DataStoreConditionalValue] =
    value match {
      case null =>
        _unsupported("null")
      case value: EntityId =>
        _bounded(
          "entityId",
          value.print,
          EntityIdValue.apply
        )
      case value: Identifier =>
        _bounded(
          "identifier",
          value.value,
          IdentifierValue.apply
        )
      case value: String =>
        _bounded(
          "text",
          value,
          Text.apply
        )
      case value: Boolean =>
        Consequence.success(BooleanValue(value))
      case value: Instant =>
        Consequence.success(InstantValue(value))
      case value: BigDecimal =>
        Consequence.success(Decimal(value))
      case value: java.math.BigDecimal =>
        Consequence.success(Decimal(BigDecimal(value)))
      case value if _integral(value).isDefined =>
        Consequence.success(Integral(_integral(value).get))
      case value: Float =>
        _unsupported(value.getClass.getName)
      case value: Double =>
        _unsupported(value.getClass.getName)
      case value =>
        _unsupported(value.getClass.getName)
    }

  private[datastore] def validate(
    value: DataStoreConditionalValue
  ): Consequence[Unit] =
    value match {
      case null =>
        Consequence.argumentMissing("expectedValue")
      case Text(content) =>
        _validate_encoded("text", content)
      case IdentifierValue(content) =>
        _validate_encoded("identifier", content)
      case EntityIdValue(content) =>
        _validate_encoded("entityId", content)
      case _ =>
        Consequence.unit
    }

  private def _bounded[A <: DataStoreConditionalValue](
    parameter: String,
    value: String,
    constructor: String => A
  ): Consequence[DataStoreConditionalValue] =
    if (value.length <= MAX_ENCODED_LENGTH)
      Consequence.success(constructor(value))
    else
      Consequence.argumentLimitExceeded(
        parameter,
        MAX_ENCODED_LENGTH,
        value.length,
        "entity-conditional-transition.exact-value"
      )

  private def _validate_encoded(
    parameter: String,
    value: String
  ): Consequence[Unit] =
    if (value == null)
      Consequence.argumentMissing(parameter)
    else if (value.length <= MAX_ENCODED_LENGTH)
      Consequence.unit
    else
      Consequence.argumentLimitExceeded(
        parameter,
        MAX_ENCODED_LENGTH,
        value.length,
        "entity-conditional-transition.exact-value"
      )

  private def _unsupported(
    actualtype: String
  ): Consequence[DataStoreConditionalValue] =
    Consequence.argumentPolicyViolation(
      "expectedValue",
      "entity-conditional-transition.exact-value",
      "text, boolean, integral, decimal, instant, identifier, or Entity id",
      actualtype
    )

  private def _stored_type_failure(
    expectedkind: String,
    stored: Any
  ): Consequence[Boolean] =
    Consequence.argumentFormatError(
      "storedValue",
      expectedkind,
      Option(stored).map(_.getClass.getName).getOrElse("null")
    )

  private def _integral(
    value: Any
  ): Option[BigInt] =
    value match {
      case value: Byte => Some(BigInt(value))
      case value: Short => Some(BigInt(value))
      case value: Int => Some(BigInt(value))
      case value: Long => Some(BigInt(value))
      case value: BigInt => Some(value)
      case value: java.lang.Byte => Some(BigInt(value.longValue))
      case value: java.lang.Short => Some(BigInt(value.longValue))
      case value: java.lang.Integer => Some(BigInt(value.longValue))
      case value: java.lang.Long => Some(BigInt(value.longValue))
      case value: java.math.BigInteger => Some(BigInt(value))
      case _ => None
    }

  private def _decimal(
    value: Any
  ): Option[BigDecimal] =
    value match {
      case value: BigDecimal => Some(value)
      case value: java.math.BigDecimal => Some(BigDecimal(value))
      case _ => None
    }
}

final case class DataStoreConditionalExpectedField(
  storageField: String,
  expectedValue: DataStoreConditionalValue
)

final case class DataStoreConditionalRoot private (
  componentOwner: DataStoreComponentOwner,
  collection: DataStore.CollectionId,
  entryId: DataStore.EntryId,
  revisionField: String,
  expectedRevision: EntityRevision,
  expectedFields: Vector[DataStoreConditionalExpectedField],
  changes: Record,
  nextRevision: EntityRevision
)

object DataStoreConditionalRoot {
  def create(
    componentOwner: DataStoreComponentOwner,
    collection: DataStore.CollectionId,
    entryId: DataStore.EntryId,
    revisionField: String,
    expectedRevision: Option[EntityRevision],
    expectedFields: Vector[DataStoreConditionalExpectedField],
    changes: Record,
    nextRevision: EntityRevision
  ): Consequence[DataStoreConditionalRoot] = {
    Option(expectedRevision).flatten match {
      case Some(revision) =>
        EntityConditionalTransitionSupport.validateRoot(
          DataStoreConditionalRoot(
            componentOwner,
            collection,
            entryId,
            revisionField,
            revision,
            expectedFields,
            changes,
            nextRevision
          )
        )
      case None =>
        Consequence.argumentMissing("expectedRevision")
    }
  }
}

sealed abstract class DataStoreConditionalSuccessor {
  def componentOwner: DataStoreComponentOwner
  def collection: DataStore.CollectionId
  def entryId: DataStore.EntryId
  def revisionField: String
}

object DataStoreConditionalSuccessor {
  final case class Create(
    componentOwner: DataStoreComponentOwner,
    collection: DataStore.CollectionId,
    entryId: DataStore.EntryId,
    revisionField: String,
    record: Record
  ) extends DataStoreConditionalSuccessor

  final case class Bind(
    componentOwner: DataStoreComponentOwner,
    collection: DataStore.CollectionId,
    entryId: DataStore.EntryId,
    revisionField: String,
    expectedRevision: EntityRevision
  ) extends DataStoreConditionalSuccessor
}

final case class DataStoreConditionalCorrelation(
  operation: String,
  correlationId: Option[String] = None
)

final case class DataStoreConditionalTransitionPlan private (
  root: DataStoreConditionalRoot,
  successor: DataStoreConditionalSuccessor,
  sideEffects: Vector[EntityVersionedSideEffect],
  correlation: DataStoreConditionalCorrelation
)

object DataStoreConditionalTransitionPlan {
  def create(
    root: DataStoreConditionalRoot,
    successor: DataStoreConditionalSuccessor,
    sideEffects: Vector[EntityVersionedSideEffect] = Vector.empty,
    correlation: DataStoreConditionalCorrelation =
      DataStoreConditionalCorrelation("entity-conditional-transition")
  ): Consequence[DataStoreConditionalTransitionPlan] =
    EntityConditionalTransitionSupport.validate(
      DataStoreConditionalTransitionPlan(
        root,
        successor,
        sideEffects,
        correlation
      )
    )
}

sealed abstract class DataStoreConditionalTransitionResult

object DataStoreConditionalTransitionResult {
  final case class Transitioned(
    rootRecord: Record,
    successorRecord: Record
  ) extends DataStoreConditionalTransitionResult

  final case class NotMatched(
    existingRootRecord: Record
  ) extends DataStoreConditionalTransitionResult
}

trait EntityConditionalTransitionDataStore { self: DataStore =>
  def conditionalTransition(
    plan: DataStoreConditionalTransitionPlan
  )(using
    ctx: ExecutionContext
  ): Consequence[DataStoreConditionalTransitionResult]
}

object DataStoreConditionalTransitionFailure {
  val POLICY = "entity.conditional-transition.provider"

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
      symptom == Taxonomy.Symptom.PermissionDenied
    )
      Consequence.Failure(conclusion)
    else
      Consequence.Failure(_annotate(conclusion, "provider-failure"))
  }

  private val _recognized_reasons = Set(
    "unsupported-capability",
    "successor-collision",
    "bound-successor-revision-conflict",
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

sealed abstract class DataStoreConditionalTransitionCheckpoint

object DataStoreConditionalTransitionCheckpoint {
  case object GuardAdmitted extends DataStoreConditionalTransitionCheckpoint
  case object SuccessorPrepared extends DataStoreConditionalTransitionCheckpoint
  case object RootPrepared extends DataStoreConditionalTransitionCheckpoint
  case object BeforePublish extends DataStoreConditionalTransitionCheckpoint
}

private[datastore] object EntityConditionalTransitionSupport {
  val MAX_EXPECTED_FIELDS = 32
  val MAX_SIDE_EFFECTS = 64
  val MAX_FIELD_LENGTH = 128
  val MAX_CORRELATION_LENGTH = 256
  val MAX_RECORD_FIELDS = 256
  val MAX_COLLECTION_VALUES = 1024
  val MAX_RECORD_DEPTH = 16

  def validate(
    plan: DataStoreConditionalTransitionPlan
  ): Consequence[DataStoreConditionalTransitionPlan] =
    if (plan == null)
      Consequence.argumentMissing("plan")
    else
      for {
        _ <- validateRoot(plan.root)
        _ <- _validate_successor(plan.successor)
        _ <- _validate_component_ownership(plan)
        _ <- _validate_side_effects(plan)
        _ <- _validate_correlation(plan.correlation)
      } yield plan

  def validateComponentOwner(
    owner: DataStoreComponentOwner
  ): Consequence[DataStoreComponentOwner] =
    if (owner == null)
      Consequence.argumentMissing("componentOwner")
    else
      _validate_name(
        "componentOwner",
        owner.name,
        DataStoreComponentOwner.MAX_NAME_LENGTH
      ).map(_ => owner)

  def validateRoot(
    root: DataStoreConditionalRoot
  ): Consequence[DataStoreConditionalRoot] =
    if (root == null)
      Consequence.argumentMissing("root")
    else if (root.collection == null)
      Consequence.argumentMissing("root.collection")
    else if (root.entryId == null)
      Consequence.argumentMissing("root.entryId")
    else if (root.expectedFields == null)
      Consequence.argumentMissing("root.expectedFields")
    else if (root.changes == null)
      Consequence.argumentMissing("root.changes")
    else
      for {
        _ <- _validate_entity_collection(
          "root.collection",
          root.collection
        )
        _ <- validateComponentOwner(root.componentOwner)
        _ <- _validate_name(
          "revisionField",
          root.revisionField,
          MAX_FIELD_LENGTH
        )
        _ <- _validate_revision_progression(
          root.expectedRevision,
          root.nextRevision
        )
        _ <- _validate_expected_fields(root)
        _ <- _validate_changes(root)
      } yield root

  def rootMatches(
    record: Record,
    root: DataStoreConditionalRoot
  ): Consequence[Boolean] =
    EntityVersionedMutationSupport
      .revision(record, root.revisionField)
      .flatMap { actualrevision =>
        if (actualrevision != root.expectedRevision)
          Consequence.success(false)
        else
          root.expectedFields.foldLeft(Consequence.success(true)) {
            case (result, expected) =>
              result.flatMap {
                case false => Consequence.success(false)
                case true => _field_matches(record, expected)
              }
          }
      }

  def applyRootChanges(
    existing: Record,
    root: DataStoreConditionalRoot
  ): Record = {
    val changedkeys = root.changes.fields.map(_.key).toSet
    val effectivechanges =
      root.changes.fields.filterNot(_is_set_null_marker)
    val changed =
      Record(
        existing.fields.filterNot(field => changedkeys.contains(field.key)) ++
          effectivechanges
      )
    Record(changed.fields.filterNot(_.key == root.revisionField)) ++
      Record.dataAuto(root.revisionField -> root.nextRevision.value)
  }

  private def _validate_expected_fields(
    root: DataStoreConditionalRoot
  ): Consequence[Unit] = {
    val fields = root.expectedFields
    if (fields.size > MAX_EXPECTED_FIELDS)
      Consequence.argumentLimitExceeded(
        "expectedFields",
        MAX_EXPECTED_FIELDS,
        fields.size,
        "entity-conditional-transition.expected-fields"
      )
    else if (fields.exists(_ == null))
      Consequence.argumentMissing("expectedField")
    else {
      val names = fields.map(_.storageField)
      if (names.distinct.size != names.size)
        Consequence.argumentInvalid(
          "expectedFields",
          "unique canonical storage fields",
          s"${names.size - names.distinct.size} duplicate fields"
        )
      else if (names.contains(root.revisionField))
        Consequence.argumentPolicyViolation(
          "expectedFields",
          "framework-managed-revision",
          "fields excluding the revision field",
          "revision field"
        )
      else
        fields.foldLeft(Consequence.unit) { (result, field) =>
          result.flatMap { _ =>
            if (field.expectedValue == null)
              Consequence.argumentMissing(
                s"expectedField.${field.storageField}.value"
              )
            else
              for {
                _ <- _validate_name(
                  "expectedField",
                  field.storageField,
                  MAX_FIELD_LENGTH
                )
                _ <- DataStoreConditionalValue.validate(field.expectedValue)
              } yield ()
          }
        }
    }
  }

  private def _validate_changes(
    root: DataStoreConditionalRoot
  ): Consequence[Unit] = {
    val names = root.changes.fields.map(_.key)
    if (names.isEmpty)
      Consequence.argumentMissing("changes")
    else if (names.distinct.size != names.size)
      Consequence.argumentInvalid(
        "changes",
        "unique canonical storage fields",
        s"${names.size - names.distinct.size} duplicate fields"
      )
    else if (names.contains(root.revisionField))
      Consequence.argumentPolicyViolation(
        "changes",
        "framework-managed-revision",
        "changes excluding the revision field",
        "revision field"
      )
    else
      for {
        _ <- names.foldLeft(Consequence.unit) { (result, name) =>
          result.flatMap(_ =>
            _validate_name("changeField", name, MAX_FIELD_LENGTH)
          )
        }
        _ <- _validate_record(
          "changes",
          root.changes,
          allowsetnull = true
        )
      } yield ()
  }

  private def _validate_successor(
    successor: DataStoreConditionalSuccessor
  ): Consequence[Unit] =
    if (successor == null)
      Consequence.argumentMissing("successor")
    else if (successor.collection == null)
      Consequence.argumentMissing("successor.collection")
    else if (successor.entryId == null)
      Consequence.argumentMissing("successor.entryId")
    else
      for {
        _ <- _validate_entity_collection(
          "successor.collection",
          successor.collection
        )
        _ <- validateComponentOwner(successor.componentOwner)
        _ <- _validate_name(
          "successorRevisionField",
          successor.revisionField,
          MAX_FIELD_LENGTH
        )
        _ <- successor match {
          case create: DataStoreConditionalSuccessor.Create =>
            if (create.record == null)
              Consequence.argumentMissing("successor.record")
            else
              for {
                _ <- _validate_record(
                  "successor.record",
                  create.record,
                  allowsetnull = false
                )
                _ <- EntityVersionedMutationSupport
                  .revision(create.record, create.revisionField)
                  .flatMap {
                    case EntityRevision.INITIAL =>
                      Consequence.unit
                    case actual =>
                      Consequence.argumentExpectedActualMismatch(
                        "successorRevision",
                        EntityRevision.INITIAL,
                        actual
                      )
                  }
              } yield ()
          case bind: DataStoreConditionalSuccessor.Bind =>
            _validate_revision(bind.expectedRevision)
        }
      } yield ()

  private def _validate_component_ownership(
    plan: DataStoreConditionalTransitionPlan
  ): Consequence[Unit] =
    if (plan.root.componentOwner == plan.successor.componentOwner)
      Consequence.unit
    else
      Consequence.argumentPolicyViolation(
        "componentOwner",
        "entity-conditional-transition.same-component",
        plan.root.componentOwner.name,
        plan.successor.componentOwner.name
      )

  private def _validate_entity_collection(
    parameter: String,
    collection: DataStore.CollectionId
  ): Consequence[Unit] =
    collection match {
      case DataStore.CollectionId.EntityStore(_) =>
        Consequence.unit
      case _ =>
        Consequence.argumentPolicyViolation(
          parameter,
          "entity-conditional-transition.entity-collection",
          "EntityStore collection",
          collection.print
        )
    }

  private def _validate_side_effects(
    plan: DataStoreConditionalTransitionPlan
  ): Consequence[Unit] = {
    val effects = plan.sideEffects
    if (effects == null)
      Consequence.argumentMissing("sideEffects")
    else if (
      effects.exists(effect =>
        effect == null ||
          effect.collection == null ||
          effect.entryId == null
      )
    )
      Consequence.argumentInvalid(
        "sideEffects",
        "non-null closed side-record effects",
        "null effect or target"
      )
    else {
      val rootkey = _target(plan.root.collection, plan.root.entryId)
      val successorkey =
        _target(plan.successor.collection, plan.successor.entryId)
      val effectkeys =
        effects.map(effect => _target(effect.collection, effect.entryId))
      if (rootkey == successorkey)
        _target_policy_failure("root and successor targets must differ")
      else if (effects.size > MAX_SIDE_EFFECTS)
        Consequence.argumentLimitExceeded(
          "sideEffects",
          MAX_SIDE_EFFECTS,
          effects.size,
          "entity-conditional-transition.side-effects"
        )
      else if (effectkeys.distinct.size != effectkeys.size)
        Consequence.argumentInvalid(
          "sideEffects",
          "unique collection and entry-id targets",
          s"${effectkeys.size - effectkeys.distinct.size} duplicate targets"
        )
      else if (
        effectkeys.contains(rootkey) ||
          effectkeys.contains(successorkey)
      )
        _target_policy_failure(
          "side-record targets must differ from root and successor"
        )
      else
        effects.zipWithIndex.foldLeft(Consequence.unit) {
          case (result, (EntityVersionedSideEffect.Save(_, _, record), index)) =>
            result.flatMap(_ =>
              _validate_record(
                s"sideEffects[$index].record",
                record,
                allowsetnull = false
              )
            )
          case (result, _) =>
            result
        }
    }
  }

  private def _validate_correlation(
    correlation: DataStoreConditionalCorrelation
  ): Consequence[Unit] =
    if (correlation == null)
      Consequence.argumentMissing("correlation")
    else
      for {
        _ <- _validate_name(
          "correlation.operation",
          correlation.operation,
          MAX_CORRELATION_LENGTH
        )
        _ <- Option(correlation.correlationId)
          .flatten
          .map(
            _validate_name(
              "correlation.id",
              _,
              MAX_CORRELATION_LENGTH
            )
          )
          .getOrElse(Consequence.unit)
      } yield ()

  private def _validate_revision_progression(
    expected: EntityRevision,
    next: EntityRevision
  ): Consequence[Unit] =
    Option(expected) match {
      case None =>
        Consequence.argumentMissing("expectedRevision")
      case Some(revision) =>
        revision.nextC.flatMap { expectednext =>
          if (expectednext == next)
            Consequence.unit
          else
            Consequence.argumentExpectedActualMismatch(
              "nextRevision",
              expectednext,
              next
            )
        }
    }

  private def _validate_revision(
    revision: EntityRevision
  ): Consequence[Unit] =
    Option(revision)
      .map(_ => Consequence.unit)
      .getOrElse(Consequence.argumentMissing("expectedRevision"))

  private def _validate_name(
    parameter: String,
    value: String,
    limit: Int
  ): Consequence[Unit] =
    if (value == null || value.trim.isEmpty)
      Consequence.argumentMissing(parameter)
    else if (value.exists(_.isControl))
      Consequence.argumentFormatError(
        parameter,
        "text without control characters",
        "control-character-bearing text"
      )
    else if (value.length > limit)
      Consequence.argumentLimitExceeded(
        parameter,
        limit,
        value.length,
        "entity-conditional-transition.identifier"
      )
    else
      Consequence.unit

  private def _validate_record(
    parameter: String,
    record: Record,
    allowsetnull: Boolean
  ): Consequence[Unit] =
    if (record == null)
      Consequence.argumentMissing(parameter)
    else
      _validate_record_at(
        parameter,
        record,
        allowsetnull,
        0
      )

  private def _validate_record_at(
    parameter: String,
    record: Record,
    allowsetnull: Boolean,
    depth: Int
  ): Consequence[Unit] =
    if (depth > MAX_RECORD_DEPTH)
      Consequence.argumentLimitExceeded(
        parameter,
        MAX_RECORD_DEPTH,
        depth,
        "entity-conditional-transition.record-depth"
      )
    else if (record.fields.size > MAX_RECORD_FIELDS)
      Consequence.argumentLimitExceeded(
        parameter,
        MAX_RECORD_FIELDS,
        record.fields.size,
        "entity-conditional-transition.record-fields"
      )
    else if (record.fields.exists(_ == null))
      Consequence.argumentInvalid(
        parameter,
        "closed non-null fields",
        "null field"
      )
    else {
      val names = record.fields.map(_.key)
      if (names.distinct.size != names.size)
        Consequence.argumentInvalid(
          parameter,
          "unique canonical storage fields",
          s"${names.size - names.distinct.size} duplicate fields"
        )
      else
        record.fields.foldLeft(Consequence.unit) { (result, field) =>
          result.flatMap { _ =>
            for {
              _ <- _validate_name(
                s"$parameter.field",
                field.key,
                MAX_FIELD_LENGTH
              )
              _ <- _validate_record_value(
                s"$parameter.${field.key}",
                field.value.single,
                allowsetnull,
                depth
              )
            } yield ()
          }
        }
    }

  private def _validate_record_value(
    parameter: String,
    value: Any,
    allowsetnull: Boolean,
    depth: Int
  ): Consequence[Unit] =
    value match {
      case null =>
        Consequence.argumentMissing(parameter)
      case Update.SetNull if allowsetnull =>
        Consequence.unit
      case Update.SetNull =>
        Consequence.argumentPolicyViolation(
          parameter,
          "entity-conditional-transition.set-null",
          "set-null only in the root patch",
          "set-null outside the root patch"
        )
      case _: String | _: Boolean | _: Byte | _: Short | _: Int | _: Long |
          _: Float | _: Double | _: BigInt | _: BigDecimal |
          _: java.math.BigInteger | _: java.math.BigDecimal |
          _: Instant =>
        Consequence.unit
      case record: Record =>
        _validate_record_at(
          parameter,
          record,
          allowsetnull = false,
          depth + 1
        )
      case values: Seq[?] =>
        val boundedvalues =
          values.iterator.take(MAX_COLLECTION_VALUES + 1).toVector
        if (boundedvalues.size > MAX_COLLECTION_VALUES)
          Consequence.argumentLimitExceeded(
            parameter,
            MAX_COLLECTION_VALUES,
            boundedvalues.size,
            "entity-conditional-transition.collection-values"
          )
        else
          boundedvalues.zipWithIndex.foldLeft(Consequence.unit) {
            case (result, (content, index)) =>
              result.flatMap(_ =>
                _validate_record_value(
                  s"$parameter[$index]",
                  content,
                  allowsetnull = false,
                  depth + 1
                )
              )
          }
      case other =>
        Consequence.argumentPolicyViolation(
          parameter,
          "entity-conditional-transition.closed-record-value",
          "provider-neutral storage scalar, record, or sequence",
          other.getClass.getName
        )
    }

  private def _field_matches(
    record: Record,
    expected: DataStoreConditionalExpectedField
  ): Consequence[Boolean] = {
    val fields = record.fields.filter(_.key == expected.storageField)
    fields match {
      case Vector() => Consequence.success(false)
      case Vector(field) =>
        expected.expectedValue.matches(_single_value(field.value.single))
      case _ =>
        Consequence.argumentInvalid(
          "storedRecord",
          "one value for each expected field",
          s"${fields.size} values for ${expected.storageField}"
        )
    }
  }

  private def _single_value(
    value: Any
  ): Any =
    value match {
      case Some(content) => _single_value(content)
      case None => None
      case content => content
    }

  private def _target(
    collection: DataStore.CollectionId,
    entryid: DataStore.EntryId
  ): (String, String) =
    collection.print -> entryid.print

  private def _target_policy_failure(
    expected: String
  ): Consequence[Unit] =
    Consequence.argumentPolicyViolation(
      "targets",
      "entity-conditional-transition.target-isolation",
      expected,
      "overlapping targets"
    )

  private def _is_set_null_marker(
    field: org.goldenport.record.Field
  ): Boolean =
    field.value.single match {
      case Update.SetNull => true
      case _ => false
    }
}
