package org.goldenport.cncf.display

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.EntityVersionedMutationFailure
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct.  5, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/** Supplied canonical receipt validation and one-shot postcommit projection representation. */
object DisplayMutationResult {
  import DisplayMutation.*
  enum OutcomeKind { case Created, Updated, Unchanged, Deleted }
  enum PendingReason { case ViewNotCurrent, ProjectionUnavailable }
  /** Candidate label only; canonical contextual reload must supply the actual selected source. */
  final case class ReloadedDisplay(source: DisplayProtocol.Source, display: Display)
  enum ProjectionState {
    case Ready(snapshot: ReloadedDisplay)
    case Pending(reason: PendingReason)
  }
  final class Receipt private[DisplayMutationResult](val selection: Selection,
    val kind: OutcomeKind, val resource: DisplayModel.Resource)
  final class Result private[DisplayMutationResult](val receipt: Receipt, val projection: Option[ProjectionState],
    val presentationContext: Option[PresentationContext]) {
    def retryCommand: Boolean = false
    def reloadRequired: Boolean = projection match {
      case Some(ProjectionState.Ready(_)) => false
      case _ => true
    }
  }
  def receiptC(expectedSelection: Selection, request: Request, kind: OutcomeKind,
    resource: DisplayModel.Resource, observedRevision: Option[EntityRevision]): Consequence[Receipt] = {
    if (!_selection_valid(expectedSelection) || !_request_valid(request) || !_request_values_valid(request) || kind == null ||
      !_resource_valid(resource) || observedRevision == null || observedRevision.contains(null))
      inputInvalidC("ELEMENT_INVALID", "receipt")
    else if (!_same_selection(expectedSelection, _request_selection(request)))
      inputInvalidC("SELECTION_MISMATCH", "request.selection")
    else {
      val checked: Consequence[Unit] = request match {
        case Request.Create(_, _) =>
          if (kind != OutcomeKind.Created || observedRevision.nonEmpty || resource.revision.value != 1L)
            inputInvalidC("VALUE_INVALID", "receipt.kind/resource.revision")
          else Consequence.success(())
        case Request.Update(_, resourceid, _) =>
          if (resource.entityId != resourceid) inputInvalidC("SELECTION_MISMATCH", "receipt.resource.entityId")
          else if (observedRevision.isEmpty) inputInvalidC("ELEMENT_INVALID", "observedRevision")
          else kind match {
            case OutcomeKind.Updated => observedRevision.get.nextC.flatMap { expected =>
              if (resource.revision == expected) Consequence.success(()) else inputInvalidC("VALUE_INVALID", "receipt.resource.revision")
            }
            case OutcomeKind.Unchanged =>
              if (resource.revision == observedRevision.get) Consequence.success(()) else inputInvalidC("VALUE_INVALID", "receipt.resource.revision")
            case _ => inputInvalidC("VALUE_INVALID", "receipt.kind")
          }
        case Request.Delete(_, resourceid) =>
          if (resource.entityId != resourceid) inputInvalidC("SELECTION_MISMATCH", "receipt.resource.entityId")
          else if (kind != OutcomeKind.Deleted || observedRevision.isEmpty || resource.revision != observedRevision.get)
            inputInvalidC("VALUE_INVALID", "receipt.kind/resource.revision")
          else Consequence.success(())
      }
      checked.map(_ => new Receipt(expectedSelection, kind, resource))
    }
  }
  def createC(receipt: Receipt, projection: Option[ProjectionState],
    presentationContext: Option[PresentationContext] = None): Consequence[Result] = {
    if (presentationContext == null || presentationContext.contains(null))
      inputInvalidC("ELEMENT_INVALID", "presentationContext")
    else _context_c(presentationContext).flatMap { _ =>
    if (receipt == null || projection == null || projection.contains(null)) inputInvalidC("ELEMENT_INVALID", "result")
    else if (receipt.kind == OutcomeKind.Deleted) {
      if (projection.nonEmpty) inputInvalidC("VALUE_INVALID", "projection")
      else Consequence.success(new Result(receipt, None, presentationContext))
    } else projection match {
      case None => inputInvalidC("ELEMENT_INVALID", "projection")
      case Some(ProjectionState.Pending(reason)) =>
        if (reason == null) inputInvalidC("ELEMENT_INVALID", "projection.reason")
        else Consequence.success(new Result(receipt, projection, presentationContext))
      case Some(ProjectionState.Ready(snapshot)) =>
        _snapshot(receipt, snapshot).flatMap { resource =>
          if (resource.revision != receipt.resource.revision) inputInvalidC("VALUE_INVALID", "projection.snapshot.display.resource.revision")
          else if (presentationContext.nonEmpty &&
            !snapshot.display.presentationContext.exists(nested => _same_context(presentationContext.get, nested)))
            inputInvalidC("VALUE_INVALID", "presentationContext/projection.snapshot.display.presentationContext")
          else Consequence.success(new Result(receipt, projection, presentationContext))
        }
    }
    }
  }

  /** Attach acknowledgement data without replaying a command or reloading a View. */
  def withPresentationContextC(result: Result,
    presentationContext: Option[PresentationContext]): Consequence[Result] = {
    if (result == null || presentationContext == null || presentationContext.contains(null))
      inputInvalidC("ELEMENT_INVALID", "result/presentationContext")
    else {
      val projection = result.projection match {
        case Some(ProjectionState.Ready(snapshot)) =>
          Display.withPresentationContextC(snapshot.display, presentationContext)
            .map(display => Some(ProjectionState.Ready(ReloadedDisplay(snapshot.source, display))))
        case other => Consequence.success(other)
      }
      projection.flatMap(value => createC(result.receipt, value, presentationContext))
    }
  }

  private def _context_c(context: Option[PresentationContext]): Consequence[Unit] = context match {
    case None => Consequence.unit
    case Some(value) => PresentationContext.createC(value.locale, value.variant, value.flow).map(_ => ())
  }

  private def _same_context(left: PresentationContext, right: PresentationContext): Boolean = {
    left.locale == right.locale && left.variant == right.variant &&
      left.flow.map(value => (value.displayInstance, value.correlation, value.expiresAt)) ==
        right.flow.map(value => (value.displayInstance, value.correlation, value.expiresAt))
  }
  def completeC(receipt: Receipt, reload: () => Consequence[ReloadedDisplay]): Consequence[Result] = {
    if (receipt == null) inputInvalidC("ELEMENT_INVALID", "receipt")
    else if (reload == null) inputInvalidC("CALLBACK_INVALID", "reload")
    else if (receipt.kind == OutcomeKind.Deleted) createC(receipt, None)
    else {
      val projection = try {
        reload() match {
          case Consequence.Success(snapshot) => _snapshot(receipt, snapshot) match {
            case Consequence.Success(resource) if resource.revision == receipt.resource.revision => ProjectionState.Ready(snapshot)
            case Consequence.Success(_) => ProjectionState.Pending(PendingReason.ViewNotCurrent)
            case _ => ProjectionState.Pending(PendingReason.ProjectionUnavailable)
          }
          case _ => ProjectionState.Pending(PendingReason.ProjectionUnavailable)
        }
      } catch { case NonFatal(_) => ProjectionState.Pending(PendingReason.ProjectionUnavailable) }
      createC(receipt, Some(projection))
    }
  }
  def indeterminateC[A](): Consequence[A] =
    EntityVersionedMutationFailure.transactionIndeterminate("Display mutation acknowledgement is indeterminate; authoritative read required")

  private def _request_values_valid(request: Request): Boolean = {
    val fields = request match {
      case Request.Create(_, fields) => fields
      case Request.Update(_, _, changes) => changes
      case Request.Delete(_, _) => Vector.empty
    }
    fields.forall(field => field.value match {
      case DisplayValue.Text(text) => text != null
      case DisplayValue.Number(number) => number != null
      case DisplayValue.Timestamp(instant) => instant != null
      case DisplayValue.Boolean(_) | DisplayValue.Missing => true
    })
  }
  private def _snapshot(receipt: Receipt, snapshot: ReloadedDisplay): Consequence[DisplayModel.Resource] = {
    val path = "projection.snapshot"
    if (snapshot == null) inputInvalidC("ELEMENT_INVALID", path)
    else if (snapshot.source == null) inputInvalidC("ELEMENT_INVALID", path + ".source")
    else if (snapshot.display == null) inputInvalidC("ELEMENT_INVALID", path + ".display")
    else if (!_source_valid(snapshot.source)) inputInvalidC("ELEMENT_INVALID", path + ".source")
    else if (snapshot.source.componentId.name != receipt.selection.source.componentId.name)
      inputInvalidC("SELECTION_MISMATCH", path + ".source.componentId")
    else if (snapshot.source.resourceType != receipt.selection.source.resourceType)
      inputInvalidC("SELECTION_MISMATCH", path + ".source.resourceType")
    else {
      val definition = snapshot.display.definition
      val expected = receipt.selection.definition
      if (definition.screenId != expected.screenId || definition.definitionRevision != expected.definitionRevision ||
        definition.provenance != expected.provenance) inputInvalidC("DEFINITION_MISMATCH", path + ".display.definition")
      else snapshot.display.model match {
        case detail: DisplayModel.DetailModel if detail.resource.entityId == receipt.resource.entityId => Consequence.success(detail.resource)
        case _: DisplayModel.DetailModel => inputInvalidC("SELECTION_MISMATCH", path + ".display.resource.entityId")
        case _ => inputInvalidC("DEFINITION_MISMATCH", path + ".display.model")
      }
    }
  }
}
