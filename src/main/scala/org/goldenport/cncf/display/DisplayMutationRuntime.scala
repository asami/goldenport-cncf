package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.*
import org.goldenport.cncf.entity.runtime.EntityCollection
import org.goldenport.record.RecordPresentable
import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.record.Record

/*
 * @since   Oct. 15, 2026
 * @version Oct. 16, 2026
 */
/** Closed typed registrations; application code supplies pure input and domain functions. */
object DisplayMutationRuntime {
  import DisplayMutation.*
  import DisplayMutationBinding.{Binding as InputBinding, UpdateTarget}
  import DisplayMutationRuntimeEntry.{Entry, Registry}

  sealed abstract class Binding private[DisplayMutationRuntime]() {
    def selection: Selection
    private[DisplayMutationRuntime] def _compile_c(component: Component): Consequence[Entry]
  }

  private final class EntityBinding[E, P](
    private val _binding: InputBinding[P], private val _owner_id: String,
    private val _collection: EntityCollection[E], private val _field_ids: Vector[String],
    private val _seed: P, private val _persistent: EntityPersistentUpdate[P],
    private val _availability: ExecutionContext => Consequence[Availability],
    private val _bridge: DisplayMutationUpdateBridge.EntityUpdate[P]
  ) extends Binding {
    def selection: Selection = _binding.selection
    private[DisplayMutationRuntime] def _compile_c(component: Component): Consequence[Entry] = {
      val descriptor = _collection.descriptor
      for {
        _ <- if (component.entitySpace.entityOption(descriptor.collectionId).exists(_ eq _collection)) Consequence.unit
          else inputInvalidC[Unit]("SELECTION_MISMATCH", "registration.collection")
        representation <- _representation_c(_collection)
      } yield DisplayMutationRuntimeEntry.entity(_binding, _collection, _seed, _availability, _bridge, representation)
    }
  }

  private final class AggregateBinding[A <: RecordPresentable, P](
    private val _binding: InputBinding[P], private val _aggregate_name: String,
    private val _command_name: String, private val _field_ids: Vector[String],
    private val _seed: P, private val _persistent: EntityPersistentUpdate[P],
    private val _command: (A, P) => Consequence[A],
    private val _availability: ExecutionContext => Consequence[Availability]
  ) extends Binding {
    def selection: Selection = _binding.selection
    private[DisplayMutationRuntime] def _compile_c(component: Component): Consequence[Entry] = {
      val rootname = component.aggregateDefinitions.find(_.name == _aggregate_name)
        .map(_.entityName).getOrElse(_aggregate_name)
      val collections = component.entitySpace.entityCollectionsByName(rootname)
      for {
        _ <- if (component.aggregateSpace.collectionOption[A](_aggregate_name).nonEmpty) Consequence.unit
          else inputInvalidC[Unit]("SELECTION_MISMATCH", "registration.aggregate")
        collection <- collections match {
          case Vector(value) => Consequence.success(value)
          case _ => inputInvalidC("SELECTION_MISMATCH", "registration.aggregate.collection")
        }
        _ <- _callback_c(Consequence.success(_persistent.collection(_seed)), "persistent.collection").flatMap { actual =>
          if (actual == collection.descriptor.collectionId) Consequence.unit
          else inputInvalidC[Unit]("SELECTION_MISMATCH", "registration.aggregate.collection")
        }
        _ <- _representation_c(collection)
      } yield DisplayMutationRuntimeEntry.aggregate(_binding, _aggregate_name, _command_name,
        collection.descriptor.collectionId, _field_ids, _seed, _persistent, _command, _availability)
    }
  }

  private final class EntityLifecycleBinding[E, C](private val _update: Binding,
    private val _collection: EntityCollection[E], private val _create: InputBinding[C],
    private val _seed: C, private val _persistent: EntityPersistentCreate[C],
    private val _validate_delete: E => Consequence[Unit],
    private val _availability: ExecutionContext => Consequence[Availability]) extends Binding {
    def selection: Selection = _update.selection
    private[DisplayMutationRuntime] def _compile_c(component: Component): Consequence[Entry] =
      for {
        update <- _update._compile_c(component)
        representation <- _representation_c(_collection)
      } yield DisplayMutationRuntimeEntry.entityLifecycle(update, _collection, _create, _seed,
        _persistent, _validate_delete, _availability, representation)
  }

  private final class AggregateLifecycleBinding[A <: RecordPresentable, C](private val _update: Binding,
    private val _aggregate_name: String, private val _create: InputBinding[C],
    private val _seed: C, private val _persistent: EntityPersistentCreate[C],
    private val _command_name: String, private val _command: C => Consequence[C],
    private val _validate_delete: A => Consequence[Unit],
    private val _availability: ExecutionContext => Consequence[Availability]) extends Binding {
    def selection: Selection = _update.selection
    private[DisplayMutationRuntime] def _compile_c(component: Component): Consequence[Entry] = {
      val rootname = component.aggregateDefinitions.find(_.name == _aggregate_name)
        .map(_.entityName).getOrElse(_aggregate_name)
      for {
        update <- _update._compile_c(component)
        collection <- component.entitySpace.entityCollectionsByName(rootname) match {
          case Vector(value) => Consequence.success(value)
          case _ => inputInvalidC("SELECTION_MISMATCH", "registration.aggregate.collection")
        }
        _ <- _create_identity_c(_seed, _persistent, collection.descriptor.collectionId)
      } yield DisplayMutationRuntimeEntry.aggregateLifecycle[A, C](update, _aggregate_name,
        collection.descriptor.collectionId, _create, _seed, _persistent, _command_name,
        _command, _validate_delete, _availability)
    }
  }

  def entityUpdateC[E, P](binding: InputBinding[P], ownerId: String,
    collection: EntityCollection[E], fieldIds: Vector[String], seed: P,
    persistent: EntityPersistentUpdate[P], availability: ExecutionContext => Consequence[Availability]): Consequence[Binding] = {
    if (collection == null || collection.descriptor == null) inputInvalidC("ELEMENT_INVALID", "registration.collection")
    else for {
      _ <- _registration_c(binding, ownerId, fieldIds, seed, persistent, availability, false)
      _ <- _representation_c(collection)
      _ <- _callback_c(Consequence.success(persistent.collection(seed)), "persistent.collection").flatMap { actual =>
        if (actual == collection.descriptor.collectionId) Consequence.unit
        else inputInvalidC[Unit]("SELECTION_MISMATCH", "registration.collection")
      }
      bridge <- DisplayMutationUpdateBridge.entityC(binding.selection, ownerId,
        collection.descriptor.collectionId, fieldIds, persistent)
    } yield new EntityBinding(binding, ownerId, collection, fieldIds, seed, persistent, availability, bridge)
  }

  def aggregateUpdateC[A <: RecordPresentable, P](binding: InputBinding[P], aggregateName: String,
    commandName: String, fieldIds: Vector[String], seed: P, persistent: EntityPersistentUpdate[P],
    command: (A, P) => Consequence[A], availability: ExecutionContext => Consequence[Availability]): Consequence[Binding] = {
    if (command == null || !_id_valid(commandName)) inputInvalidC("ELEMENT_INVALID", "registration.command")
    else _registration_c(binding, aggregateName, fieldIds, seed, persistent, availability, true)
      .map(_ => new AggregateBinding(binding, aggregateName, commandName, fieldIds, seed, persistent, command, availability))
  }

  def entityLifecycleC[E, C, P](updateBinding: InputBinding[P], ownerId: String,
    collection: EntityCollection[E], fieldIds: Vector[String], updateSeed: P,
    updatePersistent: EntityPersistentUpdate[P], createBinding: InputBinding[C], createSeed: C,
    createPersistent: EntityPersistentCreate[C], validateDelete: E => Consequence[Unit],
    availability: ExecutionContext => Consequence[Availability]): Consequence[Binding] =
    for {
      update <- entityUpdateC(updateBinding, ownerId, collection, fieldIds, updateSeed, updatePersistent, availability)
      _ <- _lifecycle_registration_c(updateBinding, createBinding, ownerId, fieldIds,
        createSeed, createPersistent, validateDelete, false)
      _ <- _create_identity_c(createSeed, createPersistent, collection.descriptor.collectionId)
    } yield new EntityLifecycleBinding(update, collection, createBinding, createSeed, createPersistent, validateDelete, availability)

  def aggregateLifecycleC[A <: RecordPresentable, C, P](updateBinding: InputBinding[P], aggregateName: String,
    updateCommandName: String, fieldIds: Vector[String], updateSeed: P,
    updatePersistent: EntityPersistentUpdate[P], updateCommand: (A, P) => Consequence[A],
    createBinding: InputBinding[C], createSeed: C, createPersistent: EntityPersistentCreate[C],
    createCommandName: String, createCommand: C => Consequence[C], validateDelete: A => Consequence[Unit],
    availability: ExecutionContext => Consequence[Availability]): Consequence[Binding] = {
    if (createCommand == null || !_id_valid(createCommandName)) inputInvalidC("ELEMENT_INVALID", "registration.createCommand")
    else for {
      update <- aggregateUpdateC(updateBinding, aggregateName, updateCommandName, fieldIds, updateSeed,
        updatePersistent, updateCommand, availability)
      _ <- _lifecycle_registration_c(updateBinding, createBinding, aggregateName, fieldIds,
        createSeed, createPersistent, validateDelete, true)
      collection <- _callback_c(Consequence.success(updatePersistent.collection(updateSeed)), "persistent.collection")
      _ <- _create_identity_c(createSeed, createPersistent, collection)
    } yield new AggregateLifecycleBinding[A, C](update, aggregateName, createBinding, createSeed,
      createPersistent, createCommandName, createCommand, validateDelete, availability)
  }

  private def _lifecycle_registration_c[C, P, E](update: InputBinding[P], create: InputBinding[C],
    ownerid: String, fieldids: Vector[String], seed: C, persistent: EntityPersistentCreate[C],
    validate: E => Consequence[Unit], aggregate: Boolean): Consequence[Unit] = {
    if (create == null || seed == null || persistent == null || validate == null)
      inputInvalidC("ELEMENT_INVALID", "registration.lifecycle")
    else for {
      left <- update.descriptorC(Availability.enabled)
      right <- create.descriptorC(Availability.enabled)
      _ <- if (_same_selection(left.selection, right.selection) && left.capabilities == right.capabilities &&
          left.fields == right.fields && update._field_registrations.map(value => value.location -> value.target) ==
            create._field_registrations.map(value => value.location -> value.target)) Consequence.unit
        else inputInvalidC[Unit]("SELECTION_MISMATCH", "registration.lifecycle.descriptor")
      _ <- if (create._field_registrations.forall(_.target match {
        case UpdateTarget.Entity(owner, field) => !aggregate && owner == ownerid && fieldids.contains(field)
        case UpdateTarget.Aggregate(owner, field) => aggregate && owner == ownerid && fieldids.contains(field)
      })) Consequence.unit else inputInvalidC[Unit]("SELECTION_MISMATCH", "registration.lifecycle.targets")
      names <- _callback_c(Consequence.success(fieldids.map(persistent.storeFieldName)), "createPersistent.storeFieldName")
      _ <- if (!names.forall(_id_valid)) inputInvalidC[Unit]("CALLBACK_INVALID", "createPersistent.storeFieldName")
        else if (names.distinct.size != names.size) inputInvalidC[Unit]("TARGET_DUPLICATE", "registration.create.storageFields")
        else {
          val record = Record.dataAuto(names.map(_ -> "")*)
          if (SimpleEntityStorageShapePolicy.withoutManagedFields(record).fields.map(_.key) == record.fields.map(_.key)) Consequence.unit
          else inputInvalidC[Unit]("TARGET_MANAGED", "registration.create.storageFields")
        }
    } yield ()
  }

  private[cncf] def _create_identity_c[C](input: C, persistent: EntityPersistentCreate[C],
    collection: EntityCollectionId): Consequence[Unit] =
    for {
      actual <- _callback_c(Consequence.success(persistent.collection(input)), "createPersistent.collection")
      identity <- _callback_c(Consequence.success(persistent.id(input)), "createPersistent.id")
      _ <- if (actual == collection && identity.forall(id => id != null && id.collection == collection && _id_valid(id.value)))
        Consequence.unit else inputInvalidC[Unit]("SELECTION_MISMATCH", "registration.create.collection/id")
    } yield ()

  private[cncf] def _bind_c(component: Component, definitions: GeneratedLogicalUiAbi.AdmittedDefinitions,
    runtime: DisplayRuntime.Registry, resources: Vector[DisplayService.ResourceBinding], bindings: Vector[Binding]): Consequence[Registry] = {
    if (component == null || definitions == null || runtime == null || resources == null || bindings == null || bindings.contains(null))
      inputInvalidC("ELEMENT_INVALID", "mutationBindings")
    else if (bindings.map(_.selection.definition.screenId).distinct.size != bindings.size)
      inputInvalidC("BINDING_DUPLICATE", "mutationBindings")
    else bindings.foldLeft(Consequence.success(Vector.empty[Entry])) { (result, binding) =>
      result.flatMap { entries =>
        val selection = binding.selection
        val reference = selection.definition
        for {
          _ <- if (selection.source.componentId.name == component.componentId.name &&
              resources.exists(value => value.screenId == reference.screenId && value.resourceType == selection.source.resourceType) &&
              runtime.screenIds.contains(reference.screenId)) Consequence.unit
            else inputInvalidC[Unit]("SELECTION_MISMATCH", "mutationBindings.source")
          _ <- if (definitions.definitions.exists(value => value.screenId == reference.screenId &&
              value.definitionRevision == reference.definitionRevision && value.provenance == reference.provenance &&
              value.screen.isInstanceOf[GeneratedLogicalUiAbi.DetailDefinition])) Consequence.unit
            else inputInvalidC[Unit]("DEFINITION_MISMATCH", "mutationBindings.definition")
          entry <- _callback_c(binding._compile_c(component), "mutationBindings")
        } yield entries :+ entry
      }
    }.map(entries => new Registry(runtime, entries))
  }

  private def _registration_c[P](binding: InputBinding[P], ownerid: String, fieldids: Vector[String], seed: P,
    persistent: EntityPersistentUpdate[P], availability: ExecutionContext => Consequence[Availability], aggregate: Boolean): Consequence[Unit] = {
    if (binding == null || seed == null || persistent == null || availability == null || fieldids == null)
      inputInvalidC("ELEMENT_INVALID", "registration")
    else if (!_id_valid(ownerid) || fieldids.isEmpty || !fieldids.forall(_id_valid))
      inputInvalidC("ID_INVALID", "registration.fields")
    else if (fieldids.distinct.size != fieldids.size) inputInvalidC("FIELD_DUPLICATE", "registration.fields")
    else if (!binding._field_registrations.forall(_.target match {
      case UpdateTarget.Entity(owner, field) => !aggregate && owner == ownerid && fieldids.contains(field)
      case UpdateTarget.Aggregate(owner, field) => aggregate && owner == ownerid && fieldids.contains(field)
    })) inputInvalidC("SELECTION_MISMATCH", "registration.targets")
    else _callback_c(Consequence.success(persistent.collection(seed)), "persistent.collection").flatMap { collection =>
      DisplayMutationUpdateBridge.entityC(binding.selection, ownerid, collection, fieldids, persistent).map(_ => ())
    }
  }

  private def _representation_c(collection: EntityCollection[?]): Consequence[EntityRevisionRepresentation] = {
    collection.descriptor.revisionBinding match {
      case Some(binding) if binding != null && collection.descriptor.plan.concurrencyPolicy == EntityConcurrencyPolicy.Optimistic &&
          (binding.representation == EntityRevisionRepresentation.Embedded || binding.representation == EntityRevisionRepresentation.Detached) =>
        Consequence.success(binding.representation)
      case _ => inputInvalidC("SELECTION_MISMATCH", "registration.revisionBinding")
    }
  }

  private[cncf] def _callback_c[A](body: => Consequence[A], path: String): Consequence[A] = {
    try body match {
      case null | Consequence.Success(null) | Consequence.Failure(null) => inputInvalidC("CALLBACK_INVALID", path)
      case value => value
    } catch { case scala.util.control.NonFatal(_) => inputInvalidC("CALLBACK_INVALID", path) }
  }
}
