package org.goldenport.cncf.display

import io.circe.Json
import org.goldenport.Consequence

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
/** Request shape admission only; writable registration belongs to supplied Binding. */
private[display] object DisplayMutationRequestCodec {
  import DisplayMutation.*
  import DisplayMutationJson.*

  def _encode(request: Request): Consequence[Json] = _validate(request).map { _ =>
    val selection = _request_selection(request)
    val members = request match {
      case Request.Create(_, fields) => Vector("fields" -> _fields_json(fields))
      case Request.Update(_, resourceid, changes) =>
        Vector("resourceId" -> _text(resourceid.value), "changes" -> _fields_json(changes))
      case Request.Delete(_, resourceid) => Vector("resourceId" -> _text(resourceid.value))
    }
    Json.fromFields(_prefix(DisplayMutationProtocol.requestSchema) ++ _selection_json(selection, false) ++ members)
  }
  def _decode(expected: Selection, operation: Operation, json: String): Consequence[Request] = {
    if (!_selection_valid(expected) || operation == null) inputInvalidC("ELEMENT_INVALID", "expectedSelection/operation")
    else {
      val required = Vector("schema", "version", "resourceType", "definition", "provenance") ++ (operation match {
        case Operation.Create => Vector("fields")
        case Operation.Update => Vector("resourceId", "changes")
        case Operation.Delete => Vector("resourceId")
      })
      for {
        parsed <- _parse(json)
        root <- _object(parsed, "$", required)
        _ <- _protocol(root, DisplayMutationProtocol.requestSchema)
        _ <- _selection(root, expected, false)
        request <- operation match {
          case Operation.Create => _array(root, "fields", "$")(_field).map(fields => Request.Create(expected, fields))
          case Operation.Update => for {
            text <- _string(root, "resourceId", "$")
            id <- _entity_id(text, "$.resourceId")
            fields <- _array(root, "changes", "$")(_field)
          } yield Request.Update(expected, id, fields)
          case Operation.Delete => _string(root, "resourceId", "$").flatMap(_entity_id(_, "$.resourceId"))
            .map(id => Request.Delete(expected, id))
        }
        _ <- _validate(request)
      } yield request
    }
  }
  private def _validate(request: Request): Consequence[Unit] = {
    if (!_request_valid(request)) inputInvalidC("ELEMENT_INVALID", "request")
    else {
      val checked = request match {
        case Request.Create(_, fields) => _fields(fields, "$.fields", false)
        case Request.Update(_, resourceid, changes) =>
          _entity_id(resourceid.value, "$.resourceId").flatMap(_ => _fields(changes, "$.changes", true))
        case Request.Delete(_, resourceid) => _entity_id(resourceid.value, "$.resourceId").map(_ => ())
      }
      checked
    }
  }
  private def _fields(fields: Vector[FieldInput], path: String, required: Boolean): Consequence[Unit] = {
    if (required && fields.isEmpty) inputInvalidC("VALUE_INVALID", path)
    else if (fields.map(_.location).distinct.size != fields.size) inputInvalidC("FIELD_DUPLICATE", path)
    else fields.zipWithIndex.foldLeft(Consequence.success(())) { case (acc, (field, index)) =>
      acc.flatMap(_ => if (_value_valid(field.value)) Consequence.success(())
        else inputInvalidC("VALUE_INVALID", s"$path[$index].value"))
    }
  }
  private def _field(json: Json, path: String): Consequence[FieldInput] = for {
    obj <- _object(json, path, Vector("sectionId", "fieldId", "value"))
    sectionid <- _string(obj, "sectionId", path)
    fieldid <- _string(obj, "fieldId", path)
    location = FieldLocator(sectionid, fieldid)
    _ <- if (_location_valid(location)) Consequence.success(()) else inputInvalidC("ID_INVALID", path)
    value <- _value(_member(obj, "value"), path + ".value")
  } yield FieldInput(location, value)
  private def _fields_json(fields: Vector[FieldInput]): Json = Json.fromValues(fields.map(field =>
    _obj("sectionId" -> _text(field.location.sectionId), "fieldId" -> _text(field.location.fieldId),
      "value" -> _value_json(field.value))))
}
