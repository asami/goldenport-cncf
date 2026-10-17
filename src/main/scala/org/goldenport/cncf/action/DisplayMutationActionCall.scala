package org.goldenport.cncf.action

import cats.free.Free
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.display.*
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.runtime.EntityCollection
import org.goldenport.cncf.unitofwork.*
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.RecordPresentable
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}

/*
 * @since   Oct. 15, 2026
 * @version Oct. 16, 2026
 */
/** One ordinary command: retained actual capture, current commit witness, contextual reload. */
private[cncf] final class DisplayMutationActionCall(val core: ActionCall.Core,
  private val _registry: DisplayMutationRuntimeEntry.Registry,
  private val _entry: DisplayMutationRuntimeEntry.Entry,
  private val _request: DisplayMutation.Request,
  private val _metadata: DisplayMutationTransport.Metadata) extends ProcedureActionCall {
  private val _unit_of_work = executionContext.runtime.unitOfWork
  private var _capture: Option[DisplayMutationRuntimeEntry.Captured] = None
  private var _witness = DisplayMutationActionCall.Witness.Pending

  def execute(): Consequence[OperationResponse] = {
    for {
      _ <- bind_component_application_datastore_c()
      capture <- _entry.captureMutationC(this, _request, _metadata)
    } yield {
      _capture = Some(capture)
      _unit_of_work.stagePostCommitC {
        _witness = DisplayMutationActionCall.Witness.Committed
        Consequence.unit
      }
      _unit_of_work.stagePostAbortC {
        _witness = DisplayMutationActionCall.Witness.Aborted
        Consequence.unit
      }
      OperationResponse.void
    }
  }

  override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
    val committed = _witness == DisplayMutationActionCall.Witness.Committed &&
      _unit_of_work.lastCommitTermination.contains(UnitOfWorkTermination.Committed) &&
      _unit_of_work.lastCommitResult.exists {
        case Consequence.Success(_) => true
        case _ => false
      }
    if (!committed) DisplayMutationResult.indeterminateC()
    else _capture match {
      case Some(capture) => _registry.completeC(_entry, _request, _metadata, capture, executionContext)
        .map(OperationResponse.Json.apply)
      case None => DisplayMutationResult.indeterminateC()
    }
  }

  private[cncf] def _evaluate_c[A](program: ExecUowM[A]): Consequence[A] =
    new UnitOfWorkInterpreter(_unit_of_work).evaluateInActiveUnitOfWorkC(program)

  private[cncf] def _authorize_entity_c[E](collection: EntityCollection[E], id: EntityId): Consequence[UnitOfWorkAuthorization] = {
    _authorize_entity_c(collection, Some(id), "update")
  }

  private[cncf] def _authorize_entity_c[E](collection: EntityCollection[E], id: Option[EntityId],
    accesskind: String): Consequence[UnitOfWorkAuthorization] = {
    _entity_uow_authorization(Some(collection.descriptor.plan.entityName), id, accesskind) match {
      case Some(authorization) => _evaluate_c(ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.Authorize(authorization))))
        .map(_ => authorization)
      case None => Consequence.securityPermissionDenied("Display mutation requires canonical Entity authorization")
    }
  }

  private[cncf] def _current_resource_c[E](collection: EntityCollection[E], id: EntityId,
    representation: EntityRevisionRepresentation): Consequence[DisplayModel.Resource] =
    _current_entity_c(collection, id, representation).map(_._2)

  private[cncf] def _current_entity_c[E](collection: EntityCollection[E], id: EntityId,
    representation: EntityRevisionRepresentation): Consequence[(E, DisplayModel.Resource)] = {
    val store = executionContext.entityStoreSpace
    if (representation == EntityRevisionRepresentation.Embedded)
      store.loadSnapshot(id, collection.descriptor.persistent)(using executionContext)
        .flatMap(value => Consequence.successOrEntityNotFound(value)(id))
        .map(value => value.entity -> DisplayModel.Resource(id, value.revision))
    else store.loadDetached(id, collection.descriptor.persistent)(using executionContext)
      .flatMap(value => Consequence.successOrEntityNotFound(value)(id))
      .map(value => value.entity -> DisplayModel.Resource(id, value.revision))
  }

  private[cncf] def _entity_create_c[E, C](collection: EntityCollection[E], input: C,
    persistent: EntityPersistentCreate[C], authorization: UnitOfWorkAuthorization): Consequence[EntityCreateMutationOutcome[C]] =
    _evaluate_c(ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.EntityStoreCreateOutcome(input, persistent,
      _entity_create_options(Some(collection.descriptor.plan.entityName)), Some(authorization)))))

  private[cncf] def _entity_delete_c(id: EntityId, observedrevision: EntityRevision,
    authorization: UnitOfWorkAuthorization): Consequence[EntityDeleteMutationOutcome] =
    _evaluate_c(ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.EntityStoreDeleteObservedOutcome(id, observedrevision, Some(authorization)))))

  private[cncf] def _aggregate_create_c[C](aggregatename: String, commandname: String,
    persistent: EntityPersistentCreate[C])(command: () => Consequence[C]): Consequence[EntityCreateMutationOutcome[C]] =
    _aggregate_create_capture_c(aggregatename, commandname, persistent)(command)

  private[cncf] def _aggregate_delete_c[A <: RecordPresentable](aggregatename: String,
    id: EntityId, observedrevision: EntityRevision)(validate: A => Consequence[Unit]): Consequence[EntityDeleteMutationOutcome] =
    _aggregate_delete_observed_capture_c[A](aggregatename, id, observedrevision)(validate)

  private[cncf] def _aggregate_capture_c[A <: RecordPresentable](aggregatename: String, id: EntityId,
    commandname: String, observedrevision: EntityRevision)(command: A => Consequence[A])
      : Consequence[EntityVersionedMutationCaptureOutcome[?]] =
    _aggregate_update_observed_capture_c[A](aggregatename, id, commandname, observedrevision)(command)
}

private[cncf] object DisplayMutationActionCall {
  private enum Witness { case Pending, Committed, Aborted }
}
