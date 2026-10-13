package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
/** A runtime candidate validated against one already admitted static definition. */
final class Display private (
  val definition: GeneratedLogicalUiAbi.Definition,
  val model: DisplayModel
)

object Display {
  import GeneratedLogicalUiAbi.{PresentationRole, ValueKind}
  import DisplayModel.*

  final case class Diagnostic(code: String, path: String, reason: String)
  /** Typed Consequence cause carrier; validation never throws this exception. */
  final class ValidationException(val diagnostic: Diagnostic)
    extends IllegalArgumentException(s"${diagnostic.code}: ${diagnostic.path}: ${diagnostic.reason}")

  def createC(
    definitions: GeneratedLogicalUiAbi.AdmittedDefinitions,
    model: DisplayModel
  ): Consequence[Display] = {
    if (definitions == null) {
      _validation_failure(Diagnostic("DSP96_ELEMENT_INVALID", "definitions", "expected admitted definitions"))
    } else if (model == null) {
      _validation_failure(Diagnostic("DSP96_ELEMENT_INVALID", "model", "expected runtime model"))
    } else {
      _id(model.screenId, "model.screenId") match {
        case Some(problem) => _validation_failure(problem)
        case None => definitions.definitions.find(_.screenId == model.screenId) match {
          case None => _validation_failure(Diagnostic("DSP96_SCREEN_NOT_FOUND", "model.screenId",
            "screen must exist in admitted definitions"))
          case Some(definition) =>
            val diagnostic = (model, definition.screen) match {
              case (list: ListModel, declared: GeneratedLogicalUiAbi.ListDefinition) => _list(list, declared)
              case (detail: DetailModel, declared: GeneratedLogicalUiAbi.DetailDefinition) => _detail(detail, declared)
              case _ => _error("DEFINITION_MISMATCH", "model", "expected matching List or Detail branch")
            }
            diagnostic match {
              case Some(problem) => _validation_failure(problem)
              case None => Consequence.success(new Display(definition, model))
            }
        }
      }
    }
  }

  private def _validation_failure(diagnostic: Diagnostic): Consequence[Display] = {
    Consequence.fail(Taxonomy.argumentInvalid, new ValidationException(diagnostic))
  }
  private def _error(code: String, path: String, reason: String): Option[Diagnostic] = {
    Some(Diagnostic("DSP96_" + code, path, reason))
  }
  private def _id(value: String, path: String): Option[Diagnostic] = {
    if (value != null && value.nonEmpty && value == value.trim) None
    else _error("ID_INVALID", path, "expected nonempty exactly trimmed opaque text")
  }
  private def _text_match(value: String, declared: String, path: String): Option[Diagnostic] = {
    if (value == null) _error("ELEMENT_INVALID", path, "expected display text")
    else if (value != declared) _error("DEFINITION_MISMATCH", path, "expected exact declared text")
    else None
  }

  private def _list(model: ListModel, declared: GeneratedLogicalUiAbi.ListDefinition): Option[Diagnostic] = {
    _text_match(model.title, declared.title, "model.title").orElse {
      if (model.detailTarget == null) _error("ELEMENT_INVALID", "model.detailTarget", "expected logical Detail target")
      else if (model.detailTarget.screenId == null) _error("ID_INVALID", "model.detailTarget.screenId", "expected opaque target ID")
      else if (model.detailTarget != declared.detailTarget) {
        _error("DEFINITION_MISMATCH", "model.detailTarget", "expected declared logical Detail target")
      } else None
    }.orElse(_snapshot(model.snapshot)).orElse {
      _entries(model.items, "model.items").orElse {
        model.items.iterator.zipWithIndex.flatMap { case (item, index) =>
          val path = s"model.items[$index]"
          _resource(item.resource, path + ".resource")
            .orElse(_fields(item.fields, declared.itemFields, path + ".fields"))
            .orElse(_actions(item.actions, declared.itemActions, path + ".actions"))
        }.nextOption()
      }.orElse {
        model.items.indices.find { index =>
          model.items.take(index).exists(_.resource.entityId == model.items(index).resource.entityId)
        }.map { index =>
          Diagnostic("DSP96_DUPLICATE_RESOURCE", s"model.items[$index].resource.entityId",
            "resource identity must be unique within List")
        }
      }
    }.orElse(_actions(model.actions, declared.actions, "model.actions"))
  }

  private def _snapshot(snapshot: Option[QuerySnapshot]): Option[Diagnostic] = {
    if (snapshot == null || snapshot.contains(null)) {
      _error("ELEMENT_INVALID", "model.snapshot", "expected optional query snapshot shape")
    } else snapshot.flatMap(value => _id(value.reference, "model.snapshot.reference"))
  }
  private def _resource(resource: Resource, path: String): Option[Diagnostic] = {
    if (resource == null) _error("ELEMENT_INVALID", path, "expected typed resource")
    else if (resource.entityId == null) _error("RESOURCE_INVALID", path + ".entityId", "expected typed entity identity")
    else if (resource.revision == null) _error("RESOURCE_INVALID", path + ".revision", "expected typed entity revision")
    else None
  }

  private def _detail(model: DetailModel, declared: GeneratedLogicalUiAbi.DetailDefinition): Option[Diagnostic] = {
    _text_match(model.title, declared.title, "model.title")
      .orElse(_resource(model.resource, "model.resource"))
      .orElse(_entries(model.sections, "model.sections"))
      .orElse(_ids(model.sections.map(_.id), "model.sections"))
      .orElse(_ordered_ids(model.sections.map(_.id), declared.sections.map(_.id), "model.sections"))
      .orElse {
        model.sections.iterator.zip(declared.sections.iterator).zipWithIndex.flatMap {
          case ((section, expected), index) =>
            val path = s"model.sections[$index].title"
            if (section.title == null || section.title.contains(null)) {
              _error("ELEMENT_INVALID", path, "expected optional section title shape")
            } else if (section.title != expected.title) {
              _error("DEFINITION_MISMATCH", path, "expected exact declared optional title")
            } else None
        }.nextOption()
      }.orElse {
        model.sections.iterator.zip(declared.sections.iterator).zipWithIndex.flatMap {
          case ((section, expected), index) => _fields(section.fields, expected.fields, s"model.sections[$index].fields")
        }.nextOption()
      }.orElse(_actions(model.actions, declared.actions, "model.actions"))
  }

  private def _entries[A](entries: Vector[A], path: String): Option[Diagnostic] = {
    if (entries == null) _error("ELEMENT_INVALID", path, "expected ordered container")
    else entries.indices.find(index => entries(index) == null).map { index =>
      Diagnostic("DSP96_ELEMENT_INVALID", s"$path[$index]", "expected nonnull container entry")
    }
  }
  private def _ids(ids: Vector[String], path: String): Option[Diagnostic] = {
    ids.iterator.zipWithIndex.flatMap { case (id, index) => _id(id, s"$path[$index].id") }.nextOption()
      .orElse {
        ids.indices.find(index => ids.take(index).contains(ids(index))).map { index =>
          Diagnostic("DSP96_DUPLICATE_ID", s"$path[$index].id", "identity must be unique within container")
        }
      }
  }
  private def _ordered_ids(ids: Vector[String], declared: Vector[String], path: String): Option[Diagnostic] = {
    if (ids.size != declared.size) _error("DEFINITION_MISMATCH", path, "expected declared container count")
    else ids.indices.find(index => ids(index) != declared(index)).map { index =>
      Diagnostic("DSP96_DEFINITION_MISMATCH", s"$path[$index].id", "expected declared identity in declared order")
    }
  }

  private def _fields(
    fields: Vector[Field], declared: Vector[GeneratedLogicalUiAbi.FieldDefinition], path: String
  ): Option[Diagnostic] = {
    _entries(fields, path).orElse(_ids(fields.map(_.id), path)).orElse {
      fields.indices.find(index => fields(index).role == null).map { index =>
        Diagnostic("DSP96_ROLE_INVALID", s"$path[$index].role", "expected presentation role")
      }
    }.orElse {
      fields.indices.find { index =>
        val role = fields(index).role
        role != PresentationRole.Content && fields.take(index).exists(_.role == role)
      }.map { index =>
        Diagnostic("DSP96_DUPLICATE_ROLE", s"$path[$index].role", "non-content role must be unique within container")
      }
    }.orElse(_ordered_ids(fields.map(_.id), declared.map(_.id), path)).orElse {
      fields.iterator.zip(declared.iterator).zipWithIndex.flatMap { case ((field, expected), index) =>
        val fieldpath = s"$path[$index]"
        _text_match(field.label, expected.label, fieldpath + ".label").orElse {
          if (field.role != expected.role) _error("DEFINITION_MISMATCH", fieldpath + ".role", "expected declared presentation role")
          else None
        }.orElse(_value(field.value, expected, fieldpath + ".value"))
      }.nextOption()
    }
  }

  private def _value(
    value: DisplayValue, declared: GeneratedLogicalUiAbi.FieldDefinition, path: String
  ): Option[Diagnostic] = {
    if (value == null) _error("ELEMENT_INVALID", path, "expected explicit runtime value")
    else {
      val payload = value match {
        case DisplayValue.Text(text) if text == null => _error("ELEMENT_INVALID", path, "expected text payload")
        case DisplayValue.Number(number) if number == null => _error("ELEMENT_INVALID", path, "expected decimal payload")
        case DisplayValue.Timestamp(timestamp) if timestamp == null => _error("ELEMENT_INVALID", path, "expected instant payload")
        case _ => None
      }
      payload.orElse {
        if (!declared.value.allowedKinds.contains(value.kind)) {
          _error("VALUE_KIND_INVALID", path, "runtime kind must be declared")
        } else None
      }.orElse {
        if (declared.role != PresentationRole.Content) {
          value match {
            case DisplayValue.Text(text) if declared.role == PresentationRole.Title && text.trim.isEmpty =>
              _error("ROLE_INVALID", path, "Title requires nonblank text")
            case _: DisplayValue.Text => None
            case _ => _error("ROLE_INVALID", path, "non-content role requires text")
          }
        } else None
      }
    }
  }

  private def _actions(
    actions: Vector[Action], declared: Vector[GeneratedLogicalUiAbi.ActionDefinition], path: String
  ): Option[Diagnostic] = {
    _entries(actions, path).orElse(_ids(actions.map(_.id), path))
      .orElse(_ordered_ids(actions.map(_.id), declared.map(_.id), path)).orElse {
        actions.iterator.zip(declared.iterator).zipWithIndex.flatMap { case ((action, expected), index) =>
          val actionpath = s"$path[$index]"
          _text_match(action.label, expected.label, actionpath + ".label")
            .orElse(_text_match(action.bindingId, expected.bindingId, actionpath + ".bindingId"))
            .orElse {
              if (action.availability == null) _error("ELEMENT_INVALID", actionpath + ".availability", "expected presentation availability")
              else None
            }
        }.nextOption()
      }
  }
}
