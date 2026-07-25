package org.goldenport.cncf.entity

import org.goldenport.{Consequence, Conclusion}
import org.goldenport.cncf.datastore.{
  DataStoreComponentOwner,
  DataStoreConditionalValue
}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.{
  EntityCollectionId,
  EntityId,
  EntityRevision
}
import scala.util.control.NonFatal

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class EntityTransitionField[R, A] private (
  val logicalName: String,
  private[cncf] val storageField: String,
  private val _encode: A => Consequence[DataStoreConditionalValue]
) {
  def expected(
    value: A
  ): Consequence[EntityExpectedValue[R]] =
    _encode(value).map(EntityExpectedValue.create(this, _))
}

object EntityTransitionField {
  val MAX_NAME_LENGTH = 128

  def exact[R, A](
    logicalName: String,
    persistent: EntityPersistent[R]
  ): Consequence[EntityTransitionField[R, A]] =
    _create(logicalName, persistent, value =>
      DataStoreConditionalValue.from(value)
    )

  def encoded[R, A](
    logicalName: String,
    persistent: EntityPersistent[R]
  )(
    encode: A => Any
  ): Consequence[EntityTransitionField[R, A]] =
    _create(logicalName, persistent, value =>
      try
        DataStoreConditionalValue.from(encode(value))
      catch {
        case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
      }
    )

  private def _create[R, A](
    logicalname: String,
    persistent: EntityPersistent[R],
    encode: A => Consequence[DataStoreConditionalValue]
  ): Consequence[EntityTransitionField[R, A]] =
    if (persistent == null)
      Consequence.argumentMissing("persistent")
    else if (encode == null)
      Consequence.argumentMissing("encode")
    else
      for {
        _ <- _validate_name("logicalName", logicalname)
        storagefield <-
          try
            Consequence.success(persistent.storeFieldName(logicalname))
          catch {
            case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
          }
        _ <- _validate_name("storageField", storagefield)
        _ <-
          if (
            SimpleEntityStorageShapePolicy
              .isConcurrencyRevisionStorageField(storagefield)
          )
            Consequence.argumentPolicyViolation(
              "storageField",
              "entity-conditional-transition.framework-managed-field",
              "domain field",
              storagefield
            )
          else
            Consequence.unit
      } yield new EntityTransitionField(
        logicalname,
        storagefield,
        encode
      )

  private def _validate_name(
    parameter: String,
    value: String
  ): Consequence[Unit] =
    if (value == null || value.trim.isEmpty)
      Consequence.argumentMissing(parameter)
    else if (value.exists(_.isControl))
      Consequence.argumentFormatError(
        parameter,
        "text without control characters",
        "control-character-bearing text"
      )
    else if (value.length > MAX_NAME_LENGTH)
      Consequence.argumentLimitExceeded(
        parameter,
        MAX_NAME_LENGTH,
        value.length,
        "entity-conditional-transition.field-name"
      )
    else
      Consequence.unit
}

final class EntityTransitionDefinition[R] private (
  val persistent: EntityPersistent[R],
  val fields: Vector[EntityTransitionField[R, ?]]
) {
  def expectation(
    expectedRevision: EntityRevision,
    values: EntityExpectedValue[R]*
  ): Consequence[EntityTransitionExpectation[R]] =
    EntityTransitionExpectation.create(
      this,
      expectedRevision,
      values.toVector
    )
}

object EntityTransitionDefinition {
  def create[R](
    persistent: EntityPersistent[R],
    fields: Vector[EntityTransitionField[R, ?]]
  ): Consequence[EntityTransitionDefinition[R]] =
    if (persistent == null)
      Consequence.argumentMissing("persistent")
    else if (fields == null)
      Consequence.argumentMissing("fields")
    else if (fields.exists(_ == null))
      Consequence.argumentMissing("field")
    else {
      val logicalnames = fields.map(_.logicalName)
      val storagefields = fields.map(_.storageField)
      if (logicalnames.distinct.size != logicalnames.size)
        Consequence.argumentInvalid(
          "fields",
          "unique logical field names",
          s"${logicalnames.size - logicalnames.distinct.size} duplicate fields"
        )
      else if (storagefields.distinct.size != storagefields.size)
        Consequence.argumentInvalid(
          "fields",
          "unique canonical storage fields",
          s"${storagefields.size - storagefields.distinct.size} duplicate fields"
        )
      else
        Consequence.success(
          new EntityTransitionDefinition(persistent, fields)
        )
    }
}

final class EntityExpectedValue[R] private (
  val field: EntityTransitionField[R, ?],
  private[cncf] val providerValue: DataStoreConditionalValue
)

object EntityExpectedValue {
  private[entity] def create[R](
    field: EntityTransitionField[R, ?],
    value: DataStoreConditionalValue
  ): EntityExpectedValue[R] =
    new EntityExpectedValue(field, value)
}

final class EntityTransitionExpectation[R] private (
  val definition: EntityTransitionDefinition[R],
  val expectedRevision: EntityRevision,
  val values: Vector[EntityExpectedValue[R]]
)

object EntityTransitionExpectation {
  val MAX_EXPECTED_FIELDS = 32

  def create[R](
    definition: EntityTransitionDefinition[R],
    expectedRevision: EntityRevision,
    values: Vector[EntityExpectedValue[R]]
  ): Consequence[EntityTransitionExpectation[R]] =
    if (definition == null)
      Consequence.argumentMissing("definition")
    else if (expectedRevision == null)
      Consequence.argumentMissing("expectedRevision")
    else if (values == null)
      Consequence.argumentMissing("values")
    else if (values.exists(_ == null))
      Consequence.argumentMissing("expectedValue")
    else if (values.size > MAX_EXPECTED_FIELDS)
      Consequence.argumentLimitExceeded(
        "values",
        MAX_EXPECTED_FIELDS,
        values.size,
        "entity-conditional-transition.expected-fields"
      )
    else {
      val admittedfields = definition.fields.toSet
      val suppliedfields = values.map(_.field)
      if (suppliedfields.exists(field => !admittedfields.contains(field)))
        Consequence.argumentPolicyViolation(
          "values",
          "entity-conditional-transition.admitted-field",
          "fields owned by the transition definition",
          "unowned field"
        )
      else if (suppliedfields.distinct.size != suppliedfields.size)
        Consequence.argumentInvalid(
          "values",
          "unique transition fields",
          s"${suppliedfields.size - suppliedfields.distinct.size} duplicate fields"
        )
      else
        Consequence.success(
          new EntityTransitionExpectation(
            definition,
            expectedRevision,
            values
          )
        )
    }
}

sealed abstract class EntitySuccessorIntent[S] {
  def collection: EntityCollectionId
  def persisted: EntityPersistent[S]
}

object EntitySuccessorIntent {
  final class Create[C, S] private[cncf] (
    val candidate: C,
    val create: EntityPersistentCreate[C],
    val persisted: EntityPersistent[S],
    val collection: EntityCollectionId,
    val candidateId: Option[EntityId]
  ) extends EntitySuccessorIntent[S] {
  }

  final class Bind[S] private[cncf] (
    val id: EntityId,
    val persisted: EntityPersistent[S]
  ) extends EntitySuccessorIntent[S] {
    def collection: EntityCollectionId = id.collection
  }

  def create[C, S](
    candidate: C
  )(using
    create: EntityPersistentCreate[C],
    persisted: EntityPersistent[S]
  ): Consequence[EntitySuccessorIntent[S]] =
    if (candidate == null)
      Consequence.argumentMissing("successor.candidate")
    else if (create == null)
      Consequence.argumentMissing("successor.create")
    else if (persisted == null)
      Consequence.argumentMissing("successor.persisted")
    else
      try {
        val collection = create.collection(candidate)
        val candidateid = create.id(candidate)
        if (collection == null)
          Consequence.argumentMissing("successor.collection")
        else
          candidateid match {
            case Some(id) if id == null =>
              Consequence.argumentMissing("successor.id")
            case Some(id) if id.collection != collection =>
              Consequence.argumentExpectedActualMismatch(
                "successor.id.collection",
                collection,
                id.collection
              )
            case _ =>
              Consequence.success(
                new Create(
                  candidate,
                  create,
                  persisted,
                  collection,
                  candidateid
                )
              )
          }
      } catch {
        case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
      }

  def bind[S](
    id: EntityId
  )(using
    persisted: EntityPersistent[S]
  ): Consequence[EntitySuccessorIntent[S]] =
    if (id == null)
      Consequence.argumentMissing("successor.id")
    else if (persisted == null)
      Consequence.argumentMissing("successor.persisted")
    else
      Consequence.success(new Bind(id, persisted))
}

final class EntityConditionalTransition[R, P, S] private (
  val rootId: EntityId,
  val expectation: EntityTransitionExpectation[R],
  val rootPatch: P,
  val patchPersistent: EntityPersistentUpdate[P],
  val successor: EntitySuccessorIntent[S]
) {
  def rootPersistent: EntityPersistent[R] =
    expectation.definition.persistent
}

object EntityConditionalTransition {
  def create[R, P, S](
    rootId: EntityId,
    expectation: EntityTransitionExpectation[R],
    rootPatch: P,
    successor: EntitySuccessorIntent[S]
  )(using
    patchPersistent: EntityPersistentUpdate[P]
  ): Consequence[EntityConditionalTransition[R, P, S]] =
    if (rootId == null)
      Consequence.argumentMissing("rootId")
    else if (expectation == null)
      Consequence.argumentMissing("expectation")
    else if (rootPatch == null)
      Consequence.argumentMissing("rootPatch")
    else if (successor == null)
      Consequence.argumentMissing("successor")
    else if (patchPersistent == null)
      Consequence.argumentMissing("patchPersistent")
    else {
      val patchcollection =
        try
          Consequence.success(patchPersistent.collection(rootPatch))
        catch {
          case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
        }
      patchcollection.flatMap { collection =>
        if (collection == rootId.collection)
          Consequence.success(
            new EntityConditionalTransition(
              rootId,
              expectation,
              rootPatch,
              patchPersistent,
              successor
            )
          )
        else
          Consequence.argumentExpectedActualMismatch(
            "rootPatch.collection",
            rootId.collection,
            collection
          )
      }
    }
}

sealed abstract class EntityConditionalTransitionValue[+A] {
  def entity: A
  def revision: EntityRevision
}

object EntityConditionalTransitionValue {
  final case class Embedded[A](
    entity: A,
    revision: EntityRevision
  ) extends EntityConditionalTransitionValue[A]

  final case class Detached[A](
    carrier: EntityRevisionCarrier[A]
  ) extends EntityConditionalTransitionValue[A] {
    def entity: A =
      carrier.entity

    def revision: EntityRevision =
      carrier.revision
  }
}

sealed abstract class EntityConditionalTransitionResult[+R, +S]

object EntityConditionalTransitionResult {
  final case class Transitioned[R, S](
    root: EntityConditionalTransitionValue[R],
    successor: EntityConditionalTransitionValue[S]
  ) extends EntityConditionalTransitionResult[R, S]

  final case class NotMatched[R](
    existing: EntityConditionalTransitionValue[R]
  ) extends EntityConditionalTransitionResult[R, Nothing]
}

private[cncf] final case class EntityBoundSuccessorEvidence(
  id: EntityId,
  revision: EntityRevision
)

private[cncf] sealed abstract class EntityConditionalTransitionExecutionResult[
  +R,
  +S
] {
  def result: EntityConditionalTransitionResult[R, S]
  def rootRecord: Record
}

private[cncf] object EntityConditionalTransitionExecutionResult {
  final case class Transitioned[R, S](
    result: EntityConditionalTransitionResult.Transitioned[R, S],
    rootRecord: Record,
    successorRecord: Record
  ) extends EntityConditionalTransitionExecutionResult[R, S]

  final case class NotMatched[R](
    result: EntityConditionalTransitionResult.NotMatched[R],
    rootRecord: Record
  ) extends EntityConditionalTransitionExecutionResult[R, Nothing]
}

private[cncf] final case class EntityConditionalTransitionCommand[R, P, S](
  request: EntityConditionalTransition[R, P, S],
  componentOwner: DataStoreComponentOwner,
  currentRootRecord: Record,
  boundSuccessor: Option[EntityBoundSuccessorEvidence]
)
