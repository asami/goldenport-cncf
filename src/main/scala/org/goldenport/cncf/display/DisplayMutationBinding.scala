package org.goldenport.cncf.display

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.entity.*
import org.goldenport.schema.WebColumn
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
/** Explicit pure typed canonical-input preparation; never an atomic write authority. */
object DisplayMutationBinding {
  import DisplayMutation.*
  import GeneratedLogicalUiAbi.{AdmittedDefinitions, DetailDefinition, FieldDefinition, ValueKind}
  enum UpdateTarget {
    case Entity(ownerId: String, fieldId: String)
    case Aggregate(ownerId: String, fieldId: String)
  }
  final case class FieldBinding[P](location: FieldLocator, bindingId: String, target: UpdateTarget,
    acceptedKinds: Vector[ValueKind], web: WebColumn, convert: (P, DisplayValue) => Consequence[P])
  final class Descriptor private[DisplayMutationBinding](val selection: Selection,
    val capabilities: Capabilities, val availability: Availability, val fields: Vector[FieldDescriptor])
  final class Prepared[P] private[DisplayMutationBinding](val selection: Selection,
    val operation: Operation, val input: P, val resourceId: Option[EntityId],
    val observedRevision: Option[EntityRevision], val targets: Vector[UpdateTarget],
    val bindingIds: Vector[String], val executionPolicy: Option[EntityMutationExecutionPolicy])
  final class PreparedDelete private[DisplayMutationBinding](val selection: Selection,
    val resourceId: EntityId, val observedRevision: EntityRevision, val executionPolicy: EntityMutationExecutionPolicy)

  final class Binding[P] private[DisplayMutationBinding](val selection: Selection,
    val capabilities: Capabilities, val definitions: AdmittedDefinitions,
    private val _declared: Vector[(FieldLocator, FieldDefinition)],
    private val _bindings: Vector[FieldBinding[P]], private val _managed: Set[UpdateTarget],
    private val _validate_domain: P => Consequence[P]) {

    def descriptorC(availability: Availability): Consequence[Descriptor] = {
      _availability(availability, _declared.map(_._1).toSet).map { _ =>
        val fields = _declared.map { case (location, definition) =>
          _bindings.find(_.location == location) match {
            case Some(binding) => FieldDescriptor(location,
              if (availability.readonlyFields.contains(location)) FieldEditability.ReadOnly else FieldEditability.Editable,
              Some(binding.bindingId), binding.acceptedKinds, binding.web)
            case None => FieldDescriptor(location, FieldEditability.ReadOnly, None,
              definition.value.allowedKinds, WebColumn.empty.copy(readonly = true))
          }
        }
        new Descriptor(selection, capabilities, availability, fields)
      }
    }
    def prepareCreateC(request: Request.Create, seed: P, availability: Availability,
      authorize: () => Consequence[Unit]): Consequence[Prepared[P]] = {
      for {
        _ <- _request(request)
        _ <- _seed(seed)
        _ <- _authorization(authorize)
        _ <- _operation(Operation.Create, availability)
        selected <- _selected(request.fields, availability, false)
        input <- _convert(seed, selected)
      } yield new Prepared(selection, Operation.Create, input, None, None,
        selected.map(_._1.target), selected.map(_._1.bindingId), None)
    }
    def prepareUpdateC(request: Request.Update, seed: P, currentResource: DisplayModel.Resource,
      transport: DisplayMutationTransport.Metadata, availability: Availability,
      authorize: () => Consequence[Unit]): Consequence[Prepared[P]] = {
      for {
        _ <- _request(request)
        _ <- _seed(seed)
        _ <- _authorization(authorize)
        _ <- _operation(Operation.Update, availability)
        policy <- _precondition(Operation.Update, request.resourceId, currentResource, transport)
        selected <- _selected(request.changes, availability, true)
        input <- _convert(seed, selected)
      } yield new Prepared(selection, Operation.Update, input, Some(request.resourceId),
        transport.observedRevision, selected.map(_._1.target), selected.map(_._1.bindingId), Some(policy))
    }
    def prepareDeleteC(request: Request.Delete, currentResource: DisplayModel.Resource,
      transport: DisplayMutationTransport.Metadata, availability: Availability,
      atomicDeleteAvailable: Boolean, authorize: () => Consequence[Unit]): Consequence[PreparedDelete] = {
      for {
        _ <- _request(request)
        _ <- _authorization(authorize)
        _ <- _operation(Operation.Delete, availability)
        policy <- _precondition(Operation.Delete, request.resourceId, currentResource, transport)
        _ <- if (atomicDeleteAvailable) Consequence.success(()) else atomicDeleteUnsupportedC[Unit]()
      } yield new PreparedDelete(selection, request.resourceId, currentResource.revision, policy)
    }
    private def _request(request: Request): Consequence[Unit] = {
      if (!_request_valid(request)) inputInvalidC("ELEMENT_INVALID", "request")
      else if (!_same_selection(selection, _request_selection(request))) inputInvalidC("SELECTION_MISMATCH", "request.selection")
      else Consequence.success(())
    }
    private def _seed(seed: P): Consequence[Unit] =
      if (seed == null) inputInvalidC("ELEMENT_INVALID", "seed") else Consequence.success(())
    private def _authorization(authorize: () => Consequence[Unit]): Consequence[Unit] =
      if (authorize == null) inputInvalidC("CALLBACK_INVALID", "authorize") else _callback(authorize(), "authorize")
    private def _operation(operation: Operation, availability: Availability): Consequence[Unit] = {
      _availability(availability, _declared.map(_._1).toSet).flatMap { _ =>
        val support = operation match {
          case Operation.Create => capabilities.create
          case Operation.Update => capabilities.update
          case Operation.Delete => capabilities.delete
        }
        val state = operation match {
          case Operation.Create => availability.create
          case Operation.Update => availability.update
          case Operation.Delete => availability.delete
        }
        if (support != Support.Supported) operationUnsupportedC(operation)
        else if (state != AvailabilityState.Enabled) operationUnavailableC(operation)
        else Consequence.success(())
      }
    }
    private def _precondition(operation: Operation, resourceid: EntityId, current: DisplayModel.Resource,
      transport: DisplayMutationTransport.Metadata): Consequence[EntityMutationExecutionPolicy] = {
      if (!_resource_valid(current) || transport == null) inputInvalidC("ELEMENT_INVALID", "currentResource/transport")
      else if (transport.operation != operation) inputInvalidC("SELECTION_MISMATCH", "transport.operation")
      else if (current.entityId != resourceid) inputInvalidC("SELECTION_MISMATCH", "currentResource.entityId")
      else {
        val observed = transport.observedRevision
        val policy = transport.executionPolicy
        if (observed == null || observed.isEmpty || observed.contains(null) || policy == null || policy.isEmpty || policy.contains(null))
          inputInvalidC("ELEMENT_INVALID", "transport.executionPolicy")
        else {
          val expectedwrite = if (operation == Operation.Update) EntityWritePolicy.WriteIfChanged else EntityWritePolicy.AlwaysWrite
          val strict = policy.get
          if (strict.concurrencyPolicy != EntityConcurrencyPolicy.Optimistic || strict.writePolicy != expectedwrite ||
            strict.preconditionPolicy != RevisionPreconditionPolicy.ObservedRequired || strict.observedRevision != observed)
            inputInvalidC("SELECTION_MISMATCH", "transport.executionPolicy")
          else strict.validateC.flatMap { admitted =>
            if (observed.get != current.revision) revisionConflictC(observed.get, current.revision)
            else Consequence.success(admitted)
          }
        }
      }
    }
    private def _selected(fields: Vector[FieldInput], availability: Availability,
      required: Boolean): Consequence[Vector[(FieldBinding[P], DisplayValue)]] = {
      if (required && fields.isEmpty) inputInvalidC("VALUE_INVALID", "request.changes")
      else if (fields.map(_.location).distinct.size != fields.size) inputInvalidC("FIELD_DUPLICATE", "request.fields")
      else {
        val checked = fields.foldLeft(Consequence.success(Vector.empty[(FieldBinding[P], DisplayValue)])) { (result, field) =>
          result.flatMap { selected =>
            if (!_declared.exists(_._1 == field.location)) inputInvalidC("FIELD_UNKNOWN", "request.fields.location")
            else _bindings.find(_.location == field.location) match {
              case None => inputInvalidC("FIELD_READ_ONLY", "request.fields.location")
              case Some(binding) =>
                if (availability.readonlyFields.contains(field.location)) inputInvalidC("FIELD_READ_ONLY", "request.fields.location")
                else if (_managed.contains(binding.target)) inputInvalidC("TARGET_MANAGED", "request.fields.location")
                else if (selected.exists(_._1.target == binding.target)) inputInvalidC("TARGET_DUPLICATE", "request.fields.location")
                else if (!binding.acceptedKinds.contains(field.value.kind)) inputInvalidC("VALUE_KIND_INVALID", "request.fields.value")
                else if (!_value_valid(field.value)) inputInvalidC("VALUE_INVALID", "request.fields.value")
                else Consequence.success(selected :+ (binding -> field.value))
            }
          }
        }
        checked.map(selected => _bindings.flatMap(binding => selected.find(_._1.location == binding.location)))
      }
    }
    private def _convert(seed: P, selected: Vector[(FieldBinding[P], DisplayValue)]): Consequence[P] = {
      val converted = selected.foldLeft(Consequence.success(seed)) { case (result, (binding, value)) =>
        result.flatMap(input => _callback(binding.convert(input, value), "convert"))
      }
      converted.flatMap(input => _callback(_validate_domain(input), "validateDomain"))
    }
  }

  def admitC[P](source: DisplayProtocol.Source, definitions: AdmittedDefinitions, screenId: String,
    mutationRevision: String, capabilities: Capabilities, fields: Vector[FieldBinding[P]],
    managedTargets: Set[UpdateTarget], validateDomain: P => Consequence[P]): Consequence[Binding[P]] = {
    if (!_source_valid(source) || definitions == null || !_capabilities_valid(capabilities) || fields == null ||
      fields.contains(null) || managedTargets == null || managedTargets.exists(target => !_target_valid(target)) || validateDomain == null)
      inputInvalidC("ELEMENT_INVALID", "binding")
    else if (!_id_valid(screenId) || !_id_valid(mutationRevision)) inputInvalidC("ID_INVALID", "binding.definition")
    else definitions.definitions.find(_.screenId == screenId) match {
      case Some(definition) => definition.screen match {
        case detail: DetailDefinition =>
          val declared = detail.sections.flatMap(section => section.fields.map(field => FieldLocator(section.id, field.id) -> field))
          _registrations(fields, declared, managedTargets).map { _ =>
            val ordered = declared.flatMap { case (location, _) => fields.find(_.location == location) }
            new Binding(Selection(source, DefinitionReference(screenId, definition.definitionRevision,
              mutationRevision, definition.provenance)), capabilities, definitions, declared, ordered, managedTargets, validateDomain)
          }
        case _ => inputInvalidC("DEFINITION_MISMATCH", "binding.screenId")
      }
      case None => inputInvalidC("DEFINITION_MISMATCH", "binding.screenId")
    }
  }
  private def _registrations[P](fields: Vector[FieldBinding[P]], declared: Vector[(FieldLocator, FieldDefinition)],
    managed: Set[UpdateTarget]): Consequence[Unit] = {
    val checked = fields.foldLeft(Consequence.success(())) { (result, field) => result.flatMap { _ =>
      if (!_location_valid(field.location) || !_id_valid(field.bindingId) || !_target_valid(field.target))
        inputInvalidC("ID_INVALID", "binding.fields")
      else if (field.convert == null || !_web_valid(field.web)) inputInvalidC("ELEMENT_INVALID", "binding.fields")
      else if (field.web.readonly || field.web.system) inputInvalidC("FIELD_READ_ONLY", "binding.fields.web")
      else if (managed.contains(field.target)) inputInvalidC("TARGET_MANAGED", "binding.fields.target")
      else declared.find(_._1 == field.location) match {
        case None => inputInvalidC("FIELD_UNKNOWN", "binding.fields.location")
        case Some((_, definition)) =>
          val kinds = field.acceptedKinds
          if (kinds == null || kinds.isEmpty || kinds.contains(null) || kinds.distinct.size != kinds.size ||
            !kinds.forall(definition.value.allowedKinds.contains)) inputInvalidC("VALUE_KIND_INVALID", "binding.fields.acceptedKinds")
          else Consequence.success(())
      }
    } }
    checked.flatMap { _ =>
      if (fields.map(_.location).distinct.size != fields.size) inputInvalidC("FIELD_DUPLICATE", "binding.fields.location")
      else if (fields.map(_.bindingId).distinct.size != fields.size) inputInvalidC("BINDING_DUPLICATE", "binding.fields.bindingId")
      else Consequence.success(())
    }
  }
  private def _capabilities_valid(capabilities: Capabilities): Boolean = capabilities != null &&
    Vector(capabilities.read, capabilities.query, capabilities.create, capabilities.update, capabilities.delete).forall(_ != null)
  private def _target_valid(target: UpdateTarget): Boolean = target != null && (target match {
    case UpdateTarget.Entity(ownerid, fieldid) => _id_valid(ownerid) && _id_valid(fieldid)
    case UpdateTarget.Aggregate(ownerid, fieldid) => _id_valid(ownerid) && _id_valid(fieldid)
  })
  private def _availability(availability: Availability, declared: Set[FieldLocator]): Consequence[Unit] = {
    if (availability == null || Vector(availability.read, availability.query, availability.create,
      availability.update, availability.delete).contains(null) || availability.readonlyFields == null ||
      availability.readonlyFields.exists(location => !_location_valid(location))) inputInvalidC("ELEMENT_INVALID", "availability")
    else if (!availability.readonlyFields.subsetOf(declared)) inputInvalidC("FIELD_UNKNOWN", "availability.readonlyFields")
    else Consequence.success(())
  }
  private def _option_valid[A](option: Option[A]): Boolean = option != null && !option.contains(null)
  private def _web_valid(web: WebColumn): Boolean = web != null && web.confidentiality != null &&
    _option_valid(web.controlType) && _option_valid(web.required) && _option_valid(web.placeholder) && _option_valid(web.help) &&
    web.values != null && !web.values.contains(null) && web.validation != null && {
      val hints = web.validation
      _option_valid(hints.min) && _option_valid(hints.max) && _option_valid(hints.step) &&
        _option_valid(hints.minLength) && _option_valid(hints.maxLength) && _option_valid(hints.pattern) &&
        hints.step.forall(_ > 0) && hints.minLength.forall(_ >= 0) && hints.maxLength.forall(_ >= 0) &&
        !hints.min.exists(minimum => hints.max.exists(_ < minimum)) &&
        !hints.minLength.exists(minimum => hints.maxLength.exists(_ < minimum))
    }
  private def _value_valid(value: DisplayValue): Boolean = value match {
    case DisplayValue.Text(text) => text != null
    case DisplayValue.Number(number) => number != null
    case DisplayValue.Timestamp(instant) => instant != null
    case DisplayValue.Boolean(_) | DisplayValue.Missing => true
  }
  private def _callback[A](callback: => Consequence[A], path: String): Consequence[A] = {
    try {
      callback match {
        case null => inputInvalidC("CALLBACK_INVALID", path)
        case Consequence.Success(value) if value == null => inputInvalidC("CALLBACK_INVALID", path)
        case result => result
      }
    } catch { case NonFatal(_) => inputInvalidC("CALLBACK_INVALID", path) }
  }
}
