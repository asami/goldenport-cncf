package org.goldenport.cncf.display

import cats.free.Free
import scala.util.control.NonFatal
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkAuthorization, UnitOfWorkOp}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.model.directive.Update

/*
 * @since   Oct.  6, 2026
 * @version Oct. 15, 2026
 */
/** Internal typed program construction; the canonical interpreter owns effects. */
private[cncf] object DisplayMutationUpdateBridge {
  import DisplayMutation.*
  import DisplayMutationBinding.{Prepared, UpdateTarget}

  final class EntityUpdate[P] private[DisplayMutationUpdateBridge](
    private val _selection: Selection,
    private val _owner_id: String,
    private val _collection_id: EntityCollectionId,
    private val _storage_fields: Map[String, String],
    private val _persistent: EntityPersistentUpdate[P]
  ) {
    def programC(prepared: Prepared[P], authorization: UnitOfWorkAuthorization)
        : Consequence[ExecUowM[EntityRecordSnapshot]] = {
      _admit_c(prepared, authorization).map { policy =>
        val op: UnitOfWorkOp[EntityRecordSnapshot] = UnitOfWorkOp.EntityStoreUpdateByIdObserved(
          prepared.resourceId.get, prepared.input, prepared.observedRevision.get,
          _persistent, Some(authorization), policy)
        ConsequenceT.liftF(Free.liftF(op))
      }
    }

    private[cncf] def _capture_program_c(prepared: Prepared[P], authorization: UnitOfWorkAuthorization,
      representation: EntityRevisionRepresentation): Consequence[ExecUowM[EntityVersionedMutationCaptureOutcome[?]]] = {
      _admit_c(prepared, authorization).flatMap { policy =>
        val operation: Consequence[UnitOfWorkOp[EntityVersionedMutationCaptureOutcome[?]]] = representation match {
          case EntityRevisionRepresentation.Embedded => Consequence.success(
            UnitOfWorkOp.EntityStoreUpdateRecordSnapshotOutcome(prepared.resourceId.get, prepared.input,
              prepared.observedRevision, _persistent, Some(authorization), policy)
              .asInstanceOf[UnitOfWorkOp[EntityVersionedMutationCaptureOutcome[?]]])
          case EntityRevisionRepresentation.Detached => Consequence.success(
            UnitOfWorkOp.EntityStoreUpdateRecordDetachedOutcome(prepared.resourceId.get, prepared.input,
              prepared.observedRevision, _persistent, Some(authorization), policy)
              .asInstanceOf[UnitOfWorkOp[EntityVersionedMutationCaptureOutcome[?]]])
          case _ => inputInvalidC("SELECTION_MISMATCH", "representation")
        }
        operation.map(op => ConsequenceT.liftF(Free.liftF(op)))
      }
    }

    private def _admit_c(prepared: Prepared[P], authorization: UnitOfWorkAuthorization)
        : Consequence[EntityMutationExecutionPolicy] = {
      for {
        _ <- _prepared_shape(prepared)
        _ <- _correspondence(prepared)
        _ <- _authorization_scope(prepared, authorization)
        policy <- _policy(prepared)
        collection <- _callback(Consequence.success(_persistent.collection(prepared.input)), "persistent.collection")
        _ <- if (prepared.resourceId.get.collection == _collection_id && collection == _collection_id)
          Consequence.unit else inputInvalidC[Unit]("SELECTION_MISMATCH", "prepared.collection")
        _ <- _patch_scope(prepared)
      } yield policy
    }

    private def _correspondence(prepared: Prepared[P]): Consequence[Unit] = {
      if (!_same_selection(_selection, prepared.selection))
        inputInvalidC("SELECTION_MISMATCH", "prepared.selection")
      else if (prepared.operation != Operation.Update)
        inputInvalidC("SELECTION_MISMATCH", "prepared.operation")
      else if (prepared.targets.distinct.size != prepared.targets.size)
        inputInvalidC("TARGET_DUPLICATE", "prepared.targets")
      else if (prepared.bindingIds.distinct.size != prepared.bindingIds.size)
        inputInvalidC("BINDING_DUPLICATE", "prepared.bindingIds")
      else if (!prepared.targets.forall {
        case UpdateTarget.Entity(ownerid, fieldid) => ownerid == _owner_id && _storage_fields.contains(fieldid)
        case _: UpdateTarget.Aggregate => false
      }) inputInvalidC("SELECTION_MISMATCH", "prepared.targets")
      else Consequence.unit
    }

    private def _authorization_scope(prepared: Prepared[P], authorization: UnitOfWorkAuthorization): Consequence[Unit] = {
      if (authorization == null) inputInvalidC("ELEMENT_INVALID", "authorization")
      else if (authorization.resourceFamily != "domain" || authorization.accessKind != "update" ||
        authorization.targetId != prepared.resourceId || authorization.collectionName != Some(_collection_id.name) ||
        authorization.sourceComponentName != Some(_selection.source.componentId.name) ||
        authorization.targetComponentName != Some(_selection.source.componentId.name))
        inputInvalidC("SELECTION_MISMATCH", "authorization.scope")
      else Consequence.unit
    }

    private def _policy(prepared: Prepared[P]): Consequence[EntityMutationExecutionPolicy] = {
      val policy = prepared.executionPolicy.get
      if (policy.concurrencyPolicy != EntityConcurrencyPolicy.Optimistic ||
        policy.writePolicy != EntityWritePolicy.WriteIfChanged ||
        policy.preconditionPolicy != RevisionPreconditionPolicy.ObservedRequired ||
        policy.observedRevision != prepared.observedRevision)
        inputInvalidC("SELECTION_MISMATCH", "prepared.executionPolicy")
      else _callback(policy.validateC, "executionPolicy.validateC")
    }

    private def _patch_scope(prepared: Prepared[P]): Consequence[Unit] = {
      for {
        stored <- _callback(Consequence.success(_persistent.toStoreRecord(prepared.input)), "persistent.toStoreRecord")
        changes <- _callback(Consequence.success(Update.toChangesRecord(stored)), "persistent.changes")
        _ <- {
          val keys = changes.fields.map(_.key)
          val selected = prepared.targets.collect { case UpdateTarget.Entity(_, fieldid) => _storage_fields(fieldid) }.toSet
          if (keys.exists(key => !_id_valid(key)) || keys.distinct.size != keys.size)
            inputInvalidC[Unit]("FIELD_DUPLICATE", "persistent.changes")
          else if (!_unmanaged(changes)) inputInvalidC[Unit]("TARGET_MANAGED", "persistent.changes")
          else if (!keys.forall(selected.contains)) inputInvalidC[Unit]("FIELD_UNKNOWN", "persistent.changes")
          else Consequence.unit
        }
      } yield ()
    }
  }

  def entityC[P](selection: Selection, ownerId: String, collectionId: EntityCollectionId,
    fieldIds: Vector[String], persistent: EntityPersistentUpdate[P]): Consequence[EntityUpdate[P]] = {
    if (!_selection_valid(selection) || collectionId == null || persistent == null || fieldIds == null)
      inputInvalidC("ELEMENT_INVALID", "entityRegistration")
    else if (!_id_valid(ownerId) || fieldIds.isEmpty || !fieldIds.forall(_id_valid))
      inputInvalidC("ID_INVALID", "entityRegistration.fields")
    else if (fieldIds.distinct.size != fieldIds.size)
      inputInvalidC("FIELD_DUPLICATE", "entityRegistration.fields")
    else _callback(Consequence.success(fieldIds.map(persistent.storeFieldName)), "persistent.storeFieldName").flatMap { names =>
      if (!names.forall(_id_valid)) inputInvalidC("CALLBACK_INVALID", "persistent.storeFieldName")
      else if (names.distinct.size != names.size) inputInvalidC("TARGET_DUPLICATE", "entityRegistration.storageFields")
      else if (!_unmanaged(Record.dataAuto(names.map(_ -> "")*)))
        inputInvalidC("TARGET_MANAGED", "entityRegistration.storageFields")
      else Consequence.success(new EntityUpdate(selection, ownerId, collectionId, fieldIds.zip(names).toMap, persistent))
    }
  }

  private def _prepared_shape[P](prepared: Prepared[P]): Consequence[Unit] = {
    if (prepared == null || !_selection_valid(prepared.selection) || prepared.operation == null || prepared.input == null ||
      !_present(prepared.resourceId) || !_present(prepared.observedRevision) || !_present(prepared.executionPolicy) ||
      prepared.targets == null || prepared.targets.isEmpty || prepared.targets.contains(null) ||
      prepared.bindingIds == null || prepared.bindingIds.size != prepared.targets.size || !prepared.bindingIds.forall(_id_valid) ||
      !prepared.targets.forall {
        case UpdateTarget.Entity(ownerid, fieldid) => _id_valid(ownerid) && _id_valid(fieldid)
        case UpdateTarget.Aggregate(ownerid, fieldid) => _id_valid(ownerid) && _id_valid(fieldid)
      }) inputInvalidC("ELEMENT_INVALID", "prepared")
    else Consequence.unit
  }
  private def _present[A](option: Option[A]): Boolean = option != null && option.nonEmpty && !option.contains(null)
  private def _unmanaged(record: Record): Boolean =
    SimpleEntityStorageShapePolicy.withoutManagedFields(record).fields.map(_.key) == record.fields.map(_.key)
  private def _callback[A](callback: => Consequence[A], path: String): Consequence[A] = {
    try callback match {
      case null => inputInvalidC("CALLBACK_INVALID", path)
      case Consequence.Success(value) if value == null => inputInvalidC("CALLBACK_INVALID", path)
      case result => result
    } catch { case NonFatal(_) => inputInvalidC("CALLBACK_INVALID", path) }
  }
}
