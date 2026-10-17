package org.goldenport.cncf.display

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.DisplayMutationActionCall
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{DataStore, EntityVersionedMutationDataStore, EntityMutationProviderFeature}
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.runtime.EntityCollection
import org.goldenport.record.RecordPresentable
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.model.directive.Update

/*
 * @since   Oct. 15, 2026
 * @version Oct. 17, 2026
 */
/** Internal compiled existential owners preserve application E, A and P through execution. */
private[cncf] object DisplayMutationRuntimeEntry {
  import DisplayMutation.*
  import DisplayMutationBinding.{Binding, Prepared, UpdateTarget}
  import DisplayMutationRuntime._callback_c

  abstract class Entry {
    def selection: Selection
    def lifecycle: Boolean = false
    def descriptorC(context: ExecutionContext): Consequence[Json]
    def captureC(call: DisplayMutationActionCall, request: Request.Update,
      metadata: DisplayMutationTransport.Metadata): Consequence[EntityVersionedMutationCaptureOutcome[?]]
    def captureMutationC(call: DisplayMutationActionCall, request: Request,
      metadata: DisplayMutationTransport.Metadata): Consequence[Captured] = request match {
      case update: Request.Update => captureC(call, update, metadata).map(value => new Captured.Updated(value))
      case _: Request.Create => operationUnsupportedC(Operation.Create)
      case _: Request.Delete => operationUnsupportedC(Operation.Delete)
    }
  }

  sealed abstract class Captured private[DisplayMutationRuntimeEntry]()
  object Captured {
    final class Updated private[DisplayMutationRuntimeEntry](val capture: EntityVersionedMutationCaptureOutcome[?]) extends Captured
    final class Created[C] private[DisplayMutationRuntimeEntry](val capture: EntityCreateMutationOutcome[C]) extends Captured
    final class Deleted private[DisplayMutationRuntimeEntry](val capture: EntityDeleteMutationOutcome) extends Captured
  }

  final class Registry(private val _runtime: DisplayRuntime.Registry, private val _entries: Vector[Entry]) {
    def nonEmpty: Boolean = _entries.nonEmpty
    def lifecycle: Boolean = _entries.exists(_.lifecycle)
    def selectC(resourceType: String, screenId: String): Consequence[Entry] = {
      _entries.find(entry => entry.selection.source.resourceType == resourceType && entry.selection.definition.screenId == screenId)
        .map(Consequence.success).getOrElse(inputInvalidC("SELECTION_MISMATCH", "request.selection"))
    }
    def completeC(entry: Entry, request: Request.Update, metadata: DisplayMutationTransport.Metadata,
      capture: EntityVersionedMutationCaptureOutcome[?], context: ExecutionContext): Consequence[Json] = {
      val mutation = capture.mutation
      for {
        receipt <- DisplayMutationResult.receiptC(entry.selection, request,
          if (mutation.applied) DisplayMutationResult.OutcomeKind.Updated else DisplayMutationResult.OutcomeKind.Unchanged,
          DisplayModel.Resource(mutation.id, mutation.persisted.revision), metadata.observedRevision)
        result <- DisplayMutationResult.completeC(receipt, () => {
          for {
            _ <- mutation.projection.map(_ => ())
            _ <- capture.coherence
            display <- _runtime.readC(entry.selection.definition.screenId, mutation.id)(using context)
          } yield DisplayMutationResult.ReloadedDisplay(entry.selection.source, display)
        })
        attached <- DisplayMutationResult.withPresentationContextC(result, PresentationContextRuntime.project(context))
        json <- DisplayMutationCodec.encodeResultC(attached).flatMap(DisplayMutationJson._parse)
      } yield json
    }
    def completeC(entry: Entry, request: Request, metadata: DisplayMutationTransport.Metadata,
      capture: Captured, context: ExecutionContext): Consequence[Json] = (request, capture) match {
      case (update: Request.Update, value: Captured.Updated) => completeC(entry, update, metadata, value.capture, context)
      case (create: Request.Create, value: Captured.Created[?]) =>
        for {
          receipt <- DisplayMutationResult.receiptC(entry.selection, create, DisplayMutationResult.OutcomeKind.Created,
            DisplayModel.Resource(value.capture.created.id, value.capture.persisted.revision), metadata.observedRevision)
          result <- DisplayMutationResult.completeC(receipt, () => for {
            _ <- value.capture.coherence
            display <- _runtime.readC(entry.selection.definition.screenId, value.capture.created.id)(using context)
          } yield DisplayMutationResult.ReloadedDisplay(entry.selection.source, display))
          attached <- DisplayMutationResult.withPresentationContextC(result, PresentationContextRuntime.project(context))
          json <- DisplayMutationCodec.encodeResultC(attached).flatMap(DisplayMutationJson._parse)
        } yield json
      case (delete: Request.Delete, value: Captured.Deleted) =>
        for {
          receipt <- DisplayMutationResult.receiptC(entry.selection, delete, DisplayMutationResult.OutcomeKind.Deleted,
            DisplayModel.Resource(value.capture.deleted.id, value.capture.deleted.matchedRevision), metadata.observedRevision)
          result <- DisplayMutationResult.createC(receipt, None)
          attached <- DisplayMutationResult.withPresentationContextC(result, PresentationContextRuntime.project(context))
          json <- DisplayMutationCodec.encodeResultC(attached).flatMap(DisplayMutationJson._parse)
        } yield json
      case _ => inputInvalidC("SELECTION_MISMATCH", "capture.operation")
    }
  }

  private abstract class LifecycleEntry(private val _update: Entry) extends Entry {
    final def selection: Selection = _update.selection
    override final def lifecycle: Boolean = true
    final def descriptorC(context: ExecutionContext): Consequence[Json] = _update.descriptorC(context)
    final def captureC(call: DisplayMutationActionCall, request: Request.Update,
      metadata: DisplayMutationTransport.Metadata): Consequence[EntityVersionedMutationCaptureOutcome[?]] =
      _update.captureC(call, request, metadata)
  }

  private final class EntityLifecycleEntry[E, C](update: Entry, private val _collection: EntityCollection[E],
    private val _create: Binding[C], private val _seed: C, private val _persistent: EntityPersistentCreate[C],
    private val _validate_delete: E => Consequence[Unit],
    private val _availability: ExecutionContext => Consequence[Availability],
    private val _representation: EntityRevisionRepresentation) extends LifecycleEntry(update) {
    override def captureMutationC(call: DisplayMutationActionCall, request: Request,
      metadata: DisplayMutationTransport.Metadata): Consequence[Captured] = request match {
      case create: Request.Create => for {
        _ <- _create_metadata_c(metadata)
        authorization <- call._authorize_entity_c(_collection, None, "create")
        availability <- _callback_c(_availability(call.executionContext), "availability")
        prepared <- _create.prepareCreateC(create, _seed, availability, () => Consequence.unit)
        _ <- DisplayMutationRuntime._create_identity_c(prepared.input, _persistent, _collection.descriptor.collectionId)
        capture <- call._entity_create_c(_collection, prepared.input, _persistent, authorization)
      } yield new Captured.Created(capture)
      case delete: Request.Delete => for {
        _ <- if (delete.resourceId.collection == _collection.descriptor.collectionId) Consequence.unit
          else inputInvalidC[Unit]("SELECTION_MISMATCH", "request.resourceId")
        authorization <- call._authorize_entity_c(_collection, Some(delete.resourceId), "delete")
        current <- call._current_entity_c(_collection, delete.resourceId, _representation)
        availability <- _callback_c(_availability(call.executionContext), "availability")
        supported <- _delete_available_c(call.executionContext, _collection.descriptor.collectionId)
        prepared <- _create.prepareDeleteC(delete, current._2, metadata, availability, supported, () => Consequence.unit)
        _ <- prepared.observedRevision.nextC
        _ <- _callback_c(_validate_delete(current._1), "validateDelete")
        capture <- call._entity_delete_c(delete.resourceId, prepared.observedRevision, authorization)
      } yield new Captured.Deleted(capture)
      case _ => super.captureMutationC(call, request, metadata)
    }
  }

  private final class AggregateLifecycleEntry[A <: RecordPresentable, C](update: Entry,
    private val _aggregate_name: String, private val _collection_id: EntityCollectionId,
    private val _create: Binding[C], private val _seed: C, private val _persistent: EntityPersistentCreate[C],
    private val _command_name: String, private val _command: C => Consequence[C],
    private val _validate_delete: A => Consequence[Unit],
    private val _availability: ExecutionContext => Consequence[Availability]) extends LifecycleEntry(update) {
    override def captureMutationC(call: DisplayMutationActionCall, request: Request,
      metadata: DisplayMutationTransport.Metadata): Consequence[Captured] = request match {
      case create: Request.Create => for {
        _ <- _create_metadata_c(metadata)
        capture <- call._aggregate_create_c(_aggregate_name, _command_name, _persistent) { () => for {
          availability <- _callback_c(_availability(call.executionContext), "availability")
          prepared <- _create.prepareCreateC(create, _seed, availability, () => Consequence.unit)
          _ <- DisplayMutationRuntime._create_identity_c(prepared.input, _persistent, _collection_id)
          input <- _callback_c(_command(prepared.input), "createCommand")
          _ <- DisplayMutationRuntime._create_identity_c(input, _persistent, _collection_id)
        } yield input }
      } yield new Captured.Created(capture)
      case delete: Request.Delete => for {
        _ <- if (delete.resourceId.collection == _collection_id && metadata != null &&
            metadata.operation == Operation.Delete && metadata.observedRevision.nonEmpty) Consequence.unit
          else inputInvalidC[Unit]("SELECTION_MISMATCH", "request.resourceId/metadata")
        capture <- call._aggregate_delete_c[A](_aggregate_name, delete.resourceId, metadata.observedRevision.get) { aggregate => for {
          availability <- _callback_c(_availability(call.executionContext), "availability")
          supported <- _delete_available_c(call.executionContext, _collection_id)
          _ <- _create.prepareDeleteC(delete, DisplayModel.Resource(delete.resourceId, metadata.observedRevision.get),
            metadata, availability, supported, () => Consequence.unit)
          _ <- _callback_c(_validate_delete(aggregate), "validateDelete")
        } yield () }
      } yield new Captured.Deleted(capture)
      case _ => super.captureMutationC(call, request, metadata)
    }
  }

  def entityLifecycle[E, C](update: Entry, collection: EntityCollection[E], create: Binding[C], seed: C,
    persistent: EntityPersistentCreate[C], validateDelete: E => Consequence[Unit],
    availability: ExecutionContext => Consequence[Availability], representation: EntityRevisionRepresentation): Entry =
    new EntityLifecycleEntry(update, collection, create, seed, persistent, validateDelete, availability, representation)

  def aggregateLifecycle[A <: RecordPresentable, C](update: Entry, aggregateName: String, collectionId: EntityCollectionId,
    create: Binding[C], seed: C, persistent: EntityPersistentCreate[C], commandName: String, command: C => Consequence[C],
    validateDelete: A => Consequence[Unit], availability: ExecutionContext => Consequence[Availability]): Entry =
    new AggregateLifecycleEntry[A, C](update, aggregateName, collectionId, create, seed, persistent,
      commandName, command, validateDelete, availability)

  private def _create_metadata_c(metadata: DisplayMutationTransport.Metadata): Consequence[Unit] =
    if (metadata != null && metadata.operation == Operation.Create && metadata.observedRevision == None)
      Consequence.unit else inputInvalidC("SELECTION_MISMATCH", "transport.operation")

  private def _delete_available_c(context: ExecutionContext, collection: EntityCollectionId): Consequence[Boolean] =
    context.dataStoreSpace.dataStore(DataStore.CollectionId.EntityStore(collection)).map {
      case provider: EntityVersionedMutationDataStore =>
        val capabilities = provider.entityMutationProviderCapabilities
        capabilities != null && capabilities.features != null &&
          capabilities.supports(EntityMutationProviderFeature.OptimisticCompareAndDelete) &&
          capabilities.supports(EntityMutationProviderFeature.AuthoritativeDeleteResult)
      case _ => false
    }

  private final class EntityEntry[E, P](private val _binding: Binding[P],
    private val _collection: EntityCollection[E], private val _seed: P,
    private val _availability: ExecutionContext => Consequence[Availability],
    private val _bridge: DisplayMutationUpdateBridge.EntityUpdate[P],
    private val _representation: EntityRevisionRepresentation) extends Entry {
    def selection: Selection = _binding.selection
    def descriptorC(context: ExecutionContext): Consequence[Json] =
      _descriptor_c(_binding, _availability, context)
    def captureC(call: DisplayMutationActionCall, request: Request.Update,
      metadata: DisplayMutationTransport.Metadata): Consequence[EntityVersionedMutationCaptureOutcome[?]] = {
      for {
        _ <- if (request.resourceId.collection == _collection.descriptor.collectionId) Consequence.unit
          else inputInvalidC[Unit]("SELECTION_MISMATCH", "request.resourceId")
        authorization <- call._authorize_entity_c(_collection, request.resourceId)
        resource <- call._current_resource_c(_collection, request.resourceId, _representation)
        availability <- _callback_c(_availability(call.executionContext), "availability")
        prepared <- _binding.prepareUpdateC(request, _seed, resource, metadata, availability, () => Consequence.unit)
        program <- _bridge._capture_program_c(prepared, authorization, _representation)
        capture <- call._evaluate_c(program)
      } yield capture
    }
  }

  private final class AggregateEntry[A <: RecordPresentable, P](private val _binding: Binding[P],
    private val _aggregate_name: String, private val _command_name: String,
    private val _collection_id: EntityCollectionId, private val _field_ids: Vector[String],
    private val _seed: P, private val _persistent: EntityPersistentUpdate[P],
    private val _command: (A, P) => Consequence[A],
    private val _availability: ExecutionContext => Consequence[Availability]) extends Entry {
    def selection: Selection = _binding.selection
    def descriptorC(context: ExecutionContext): Consequence[Json] =
      _descriptor_c(_binding, _availability, context)
    def captureC(call: DisplayMutationActionCall, request: Request.Update,
      metadata: DisplayMutationTransport.Metadata): Consequence[EntityVersionedMutationCaptureOutcome[?]] = {
      if (request.resourceId.collection != _collection_id) inputInvalidC("SELECTION_MISMATCH", "request.resourceId")
      else call._aggregate_capture_c[A](_aggregate_name, request.resourceId, _command_name, metadata.observedRevision.get) { aggregate =>
        for {
          availability <- _callback_c(_availability(call.executionContext), "availability")
          prepared <- _binding.prepareUpdateC(request, _seed,
            DisplayModel.Resource(request.resourceId, metadata.observedRevision.get), metadata, availability, () => Consequence.unit)
          _ <- _aggregate_patch_c(prepared, _aggregate_name, _collection_id, _field_ids, _persistent)
          updated <- _callback_c(_command(aggregate, prepared.input), "command")
        } yield updated
      }
    }
  }

  def entity[E, P](binding: Binding[P], collection: EntityCollection[E], seed: P,
    availability: ExecutionContext => Consequence[Availability], bridge: DisplayMutationUpdateBridge.EntityUpdate[P],
    representation: EntityRevisionRepresentation): Entry =
    new EntityEntry(binding, collection, seed, availability, bridge, representation)

  def aggregate[A <: RecordPresentable, P](binding: Binding[P], aggregateName: String, commandName: String,
    collectionId: EntityCollectionId, fieldIds: Vector[String], seed: P, persistent: EntityPersistentUpdate[P],
    command: (A, P) => Consequence[A], availability: ExecutionContext => Consequence[Availability]): Entry =
    new AggregateEntry(binding, aggregateName, commandName, collectionId, fieldIds, seed, persistent, command, availability)

  private def _descriptor_c[P](binding: Binding[P], availability: ExecutionContext => Consequence[Availability],
    context: ExecutionContext): Consequence[Json] = {
    for {
      current <- _callback_c(availability(context), "availability")
      descriptor <- binding.descriptorC(current)
      json <- DisplayMutationCodec.encodeDescriptorC(binding, descriptor).flatMap(DisplayMutationJson._parse)
    } yield json
  }

  private def _aggregate_patch_c[P](prepared: Prepared[P], ownerid: String, collectionid: EntityCollectionId,
    fieldids: Vector[String], persistent: EntityPersistentUpdate[P]): Consequence[Unit] = {
    for {
      collection <- _callback_c(Consequence.success(persistent.collection(prepared.input)), "persistent.collection")
      _ <- if (collection == collectionid && prepared.targets.forall {
        case UpdateTarget.Aggregate(owner, field) => owner == ownerid && fieldids.contains(field)
        case _ => false
      }) Consequence.unit else inputInvalidC[Unit]("SELECTION_MISMATCH", "prepared.targets")
      stored <- _callback_c(Consequence.success(persistent.toStoreRecord(prepared.input)), "persistent.toStoreRecord")
      changes <- _callback_c(Consequence.success(Update.toChangesRecord(stored)), "persistent.changes")
      selected <- _callback_c(Consequence.success(prepared.targets.collect {
        case UpdateTarget.Aggregate(_, field) => persistent.storeFieldName(field)
      }), "persistent.storeFieldName")
      _ <- {
        val keys = changes.fields.map(_.key)
        if (keys.distinct.size != keys.size) inputInvalidC[Unit]("FIELD_DUPLICATE", "persistent.changes")
        else if (SimpleEntityStorageShapePolicy.withoutManagedFields(changes).fields.map(_.key) != keys)
          inputInvalidC[Unit]("TARGET_MANAGED", "persistent.changes")
        else if (!keys.forall(selected.contains)) inputInvalidC[Unit]("FIELD_UNKNOWN", "persistent.changes")
        else Consequence.unit
      }
    } yield ()
  }
}
