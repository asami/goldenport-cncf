package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.observation.{Descriptor, Taxonomy}
import org.goldenport.schema.WebColumn
import org.simplemodeling.model.datatype.{EntityId, EntityRevision}

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
/** Typed candidates and redacted failures; metadata never confers write authority. */
object DisplayMutation {
  enum Operation { case Create, Update, Delete }
  enum Support { case Supported, Unsupported }
  enum AvailabilityState { case Enabled, Disabled, Hidden }
  enum FieldEditability { case Editable, ReadOnly }
  final case class Capabilities(read: Support, query: Support, create: Support, update: Support, delete: Support)
  final case class Availability(read: AvailabilityState, query: AvailabilityState,
    create: AvailabilityState, update: AvailabilityState, delete: AvailabilityState,
    readonlyFields: Set[FieldLocator])
  object Availability {
    val enabled: Availability = Availability(AvailabilityState.Enabled, AvailabilityState.Enabled,
      AvailabilityState.Enabled, AvailabilityState.Enabled, AvailabilityState.Enabled, Set.empty)
  }
  final case class DefinitionReference(screenId: String, definitionRevision: String,
    mutationRevision: String, provenance: GeneratedLogicalUiAbi.SourceProvenance)
  final case class Selection(source: DisplayProtocol.Source, definition: DefinitionReference)
  final case class FieldLocator(sectionId: String, fieldId: String)
  final case class FieldInput(location: FieldLocator, value: DisplayValue)
  enum Request {
    case Create(selection: Selection, fields: Vector[FieldInput])
    case Update(selection: Selection, resourceId: EntityId, changes: Vector[FieldInput])
    case Delete(selection: Selection, resourceId: EntityId)
  }
  final case class FieldDescriptor(location: FieldLocator, editability: FieldEditability,
    bindingId: Option[String], acceptedKinds: Vector[GeneratedLogicalUiAbi.ValueKind], web: WebColumn)
  final case class Diagnostic(code: String, path: String, reason: String)
  final class MutationException(val diagnostic: Diagnostic)
    extends IllegalArgumentException(s"${diagnostic.code}: ${diagnostic.path}: ${diagnostic.reason}")

  def inputInvalidC[A](code: String, path: String): Consequence[A] =
    Consequence.fail(Taxonomy.argumentInvalid,
      new MutationException(Diagnostic("DSP96_MUTATION_" + code, path, "invalid Display mutation input")))
  def operationUnsupportedC[A](operation: Operation): Consequence[A] =
    Consequence.operationInvalid("display-mutation", Vector(Descriptor.Facet.Reason("operation-unsupported"),
      Descriptor.Facet.Capability("display-mutation-" + operation.toString)))
  def operationUnavailableC[A](operation: Operation): Consequence[A] =
    Consequence.operationInvalid("display-mutation", Vector(Descriptor.Facet.Reason("operation-unavailable"),
      Descriptor.Facet.Capability("display-mutation-" + operation.toString)))
  def atomicDeleteUnsupportedC[A](): Consequence[A] =
    Consequence.operationInvalid("display-mutation", Vector(Descriptor.Facet.Reason("atomic-delete-unsupported"),
      Descriptor.Facet.Capability("entity-atomic-observed-delete")))
  def revisionConflictC[A](expectedRevision: EntityRevision, actualRevision: EntityRevision): Consequence[A] =
    Consequence.operationConflict("display-mutation", Vector(Descriptor.Facet.Reason("stale-entity-revision"),
      Descriptor.Facet.Expected(expectedRevision.value), Descriptor.Facet.Actual(actualRevision.value)))

  private[display] def _id_valid(value: String): Boolean =
    value != null && value.nonEmpty && value == value.trim
  private[display] def _source_valid(source: DisplayProtocol.Source): Boolean =
    source != null && source.componentId != null && _id_valid(source.componentId.name) && _id_valid(source.resourceType)
  private[display] def _selection_valid(selection: Selection): Boolean = {
    selection != null && _source_valid(selection.source) && selection.definition != null && {
      val definition = selection.definition
      _id_valid(definition.screenId) && _id_valid(definition.definitionRevision) &&
        _id_valid(definition.mutationRevision) && definition.provenance != null && {
          val provenance = definition.provenance
          _id_valid(provenance.producerId) && _id_valid(provenance.sourceId) && _id_valid(provenance.revision)
        }
    }
  }
  private[display] def _same_source(left: DisplayProtocol.Source, right: DisplayProtocol.Source): Boolean =
    left.componentId.name == right.componentId.name && left.resourceType == right.resourceType
  private[display] def _same_selection(left: Selection, right: Selection): Boolean =
    _same_source(left.source, right.source) && left.definition == right.definition
  private[display] def _location_valid(location: FieldLocator): Boolean =
    location != null && _id_valid(location.sectionId) && _id_valid(location.fieldId)
  private[display] def _resource_valid(resource: DisplayModel.Resource): Boolean =
    resource != null && resource.entityId != null && resource.revision != null
  private[display] def _request_selection(request: Request): Selection = request match {
    case Request.Create(selection, _) => selection
    case Request.Update(selection, _, _) => selection
    case Request.Delete(selection, _) => selection
  }
  private[display] def _request_valid(request: Request): Boolean = request != null && {
    _selection_valid(_request_selection(request)) && (request match {
      case Request.Create(_, fields) => _fields_shape_valid(fields)
      case Request.Update(_, resourceid, changes) => resourceid != null && _fields_shape_valid(changes)
      case Request.Delete(_, resourceid) => resourceid != null
    })
  }
  private def _fields_shape_valid(fields: Vector[FieldInput]): Boolean =
    fields != null && fields.forall(field => field != null && _location_valid(field.location) && field.value != null)
}
