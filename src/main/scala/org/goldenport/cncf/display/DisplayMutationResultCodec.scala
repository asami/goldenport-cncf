package org.goldenport.cncf.display

import io.circe.Json
import org.goldenport.Consequence
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
/** Supplied receipts and projections only; this codec performs no writes or reloads. */
private[display] object DisplayMutationResultCodec {
  import DisplayMutation.*
  import DisplayMutationJson.*
  import DisplayMutationResult.*
  import GeneratedLogicalUiAbi.AdmittedDefinitions

  def _encode(result: Result): Consequence[Json] = {
    if (result == null) inputInvalidC("ELEMENT_INVALID", "result")
    else _projection_json(result.projection).map { projection =>
      val receipt = result.receipt
      Json.fromFields(_prefix(DisplayMutationProtocol.resultSchema) ++ _selection_json(receipt.selection, true) ++
        Vector("outcome" -> _text(_outcome_tag(receipt.kind)),
          "resource" -> _obj("entityId" -> _text(receipt.resource.entityId.value),
            "revision" -> Json.fromLong(receipt.resource.revision.value)),
          "projection" -> projection, "retryCommand" -> Json.fromBoolean(false)))
    }
  }
  def _decode(expected: Selection, definitions: AdmittedDefinitions, request: Request,
    observed: Option[EntityRevision], json: String): Consequence[Result] = {
    if (!_selection_valid(expected) || definitions == null || !_request_valid(request) || observed == null || observed.contains(null))
      inputInvalidC("ELEMENT_INVALID", "expectedSelection/definitions/request/observedRevision")
    else for {
      parsed <- _parse(json)
      root <- _object(parsed, "$", Vector("schema", "version", "componentId", "resourceType", "definition", "provenance",
        "outcome", "resource", "projection", "retryCommand"))
      _ <- _protocol(root, DisplayMutationProtocol.resultSchema)
      _ <- _selection(root, expected, true)
      kind <- _string(root, "outcome", "$").flatMap {
        case "created" => Consequence.success(OutcomeKind.Created)
        case "updated" => Consequence.success(OutcomeKind.Updated)
        case "unchanged" => Consequence.success(OutcomeKind.Unchanged)
        case "deleted" => Consequence.success(OutcomeKind.Deleted)
        case _ => inputInvalidC("VALUE_INVALID", "$.outcome")
      }
      obj <- _object(_member(root, "resource"), "$.resource", Vector("entityId", "revision"))
      id <- _string(obj, "entityId", "$.resource").flatMap(_entity_id(_, "$.resource.entityId"))
      scalar <- _integer(_member(obj, "revision"), "$.resource.revision", BigInt(1), BigInt(Long.MaxValue))
      revision <- EntityRevision.createC(scalar.toLong)
      retry <- _boolean_value(_member(root, "retryCommand"), "$.retryCommand")
      _ <- if (!retry) Consequence.success(()) else inputInvalidC("VALUE_INVALID", "$.retryCommand")
      receipt <- receiptC(expected, request, kind, DisplayModel.Resource(id, revision), observed)
      projection <- _projection(_member(root, "projection"), receipt, definitions)
      result <- createC(receipt, projection)
    } yield result
  }
  private def _projection(json: Json, receipt: Receipt, definitions: AdmittedDefinitions): Consequence[Option[ProjectionState]] = {
    val path = "$.projection"
    for {
      head <- _object(json, path, Vector("type"), Vector("display", "reason"))
      tag <- _string(head, "type", path)
      projection <- tag match {
        case "reload-list" => _object(json, path, Vector("type")).flatMap { _ =>
          if (receipt.kind == OutcomeKind.Deleted) Consequence.success(None)
          else inputInvalidC("VALUE_INVALID", path)
        }
        case "pending" => for {
          obj <- _object(json, path, Vector("type", "reason"))
          _ <- if (receipt.kind != OutcomeKind.Deleted) Consequence.success(()) else inputInvalidC("VALUE_INVALID", path)
          reason <- _string(obj, "reason", path).flatMap {
            case "view-not-current" => Consequence.success(PendingReason.ViewNotCurrent)
            case "projection-unavailable" => Consequence.success(PendingReason.ProjectionUnavailable)
            case _ => inputInvalidC("VALUE_INVALID", path + ".reason")
          }
        } yield Some(ProjectionState.Pending(reason))
        case "ready" => for {
          obj <- _object(json, path, Vector("type", "display"))
          _ <- if (receipt.kind != OutcomeKind.Deleted) Consequence.success(()) else inputInvalidC("VALUE_INVALID", path)
          display <- DisplayProtocolCodec.decodeC(receipt.selection.source, definitions, _render(_member(obj, "display")))
        } yield Some(ProjectionState.Ready(ReloadedDisplay(receipt.selection.source, display)))
        case _ => inputInvalidC("VALUE_INVALID", path + ".type")
      }
    } yield projection
  }
  private def _projection_json(projection: Option[ProjectionState]): Consequence[Json] = projection match {
    case None => Consequence.success(_obj("type" -> _text("reload-list")))
    case Some(ProjectionState.Pending(reason)) => Consequence.success(_obj("type" -> _text("pending"),
      "reason" -> _text(reason match {
        case PendingReason.ViewNotCurrent => "view-not-current"
        case PendingReason.ProjectionUnavailable => "projection-unavailable"
      })))
    case Some(ProjectionState.Ready(snapshot)) => DisplayProtocolCodec.encodeJsonC(snapshot.source, snapshot.display)
      .map(display => _obj("type" -> _text("ready"), "display" -> display))
  }
  private def _outcome_tag(kind: OutcomeKind): String = kind match {
    case OutcomeKind.Created => "created"
    case OutcomeKind.Updated => "updated"
    case OutcomeKind.Unchanged => "unchanged"
    case OutcomeKind.Deleted => "deleted"
  }
}
