package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.observation.Taxonomy
import scala.util.control.NonFatal

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
/** Explicit typed projection of already authorized immutable semantic sources.
  * Application callbacks must be pure and capture only authorized immutable context.
  */
object DisplayProjection {
  import GeneratedLogicalUiAbi.*
  import DisplayModel.*

  final case class ValueBinding[V](bindingIds: Vector[String], convert: V => Consequence[Vector[DisplayValue]])
  final case class ActionBinding[V](bindingId: String, evaluate: V => Consequence[ActionAvailability])
  final case class RowBinding[V](resource: V => Consequence[Resource],
    values: Vector[ValueBinding[V]], actions: Vector[ActionBinding[V]])
  final case class Diagnostic(code: String, path: String, reason: String)
  /** Structured cause carrier, returned through Consequence rather than thrown. */
  final class ProjectionException(val diagnostic: Diagnostic)
    extends IllegalArgumentException(s"${diagnostic.code}: ${diagnostic.path}: ${diagnostic.reason}")

  private final case class RequiredKey(key: String, path: String)
  private final case class CompiledRow[V](resource: V => Consequence[Resource],
    values: Vector[ValueBinding[V]], actions: Vector[ActionBinding[V]])
  private final case class EvaluatedRow(resource: Resource, values: Map[String, DisplayValue],
    actions: Map[String, ActionAvailability])

  final class Detail[V] private[DisplayProjection](
    val definition: Definition,
    private val _definitions: AdmittedDefinitions,
    private val _declared: DetailDefinition,
    private val _row: CompiledRow[V]
  ) {
    def projectC(source: V): Consequence[Display] = {
      if (source == null) _failure("ELEMENT_INVALID", "source", "expected nonnull semantic source")
      else {
        for {
          evaluated <- _evaluate_row(_row, source, "model")
          sections <- _traverse(_declared.sections) { section =>
            _fields(section.fields, evaluated.values).map(fields => Section(section.id, section.title, fields))
          }
          actions <- _actions(_declared.actions, evaluated.actions)
          display <- Display.createC(_definitions,
            DetailModel(definition.screenId, _declared.title, evaluated.resource, sections, actions))
        } yield display
      }
    }
  }

  final class List[V] private[DisplayProjection](
    val definition: Definition,
    private val _definitions: AdmittedDefinitions,
    private val _declared: ListDefinition,
    private val _row: CompiledRow[V],
    private val _screen_actions: Vector[ActionBinding[Vector[V]]]
  ) {
    def projectC(source: Vector[V], snapshot: Option[QuerySnapshot]): Consequence[Display] = {
      val problem = if (source == null) {
        _error("ELEMENT_INVALID", "source", "expected ordered semantic sources")
      } else {
        source.indices.find(index => source(index) == null).map(index =>
          _diagnostic("ELEMENT_INVALID", s"source[$index]", "expected nonnull semantic source"))
          .orElse(_snapshot(snapshot))
      }
      problem match {
        case Some(diagnostic) => _diagnostic_failure(diagnostic)
        case None =>
          for {
            items <- _traverse(source.zipWithIndex) { case (value, index) =>
              for {
                evaluated <- _evaluate_row(_row, value, s"model.items[$index]")
                fields <- _fields(_declared.itemFields, evaluated.values)
                actions <- _actions(_declared.itemActions, evaluated.actions)
              } yield ListItem(evaluated.resource, fields, actions)
            }
            availability <- _evaluate_actions(_screen_actions, source, "model")
            actions <- _actions(_declared.actions, availability)
            display <- Display.createC(_definitions, ListModel(definition.screenId, _declared.title,
              items, _declared.detailTarget, actions, snapshot))
          } yield display
      }
    }
  }

  def detailC[V](definitions: AdmittedDefinitions, screenId: String,
    viewBindingId: String, row: RowBinding[V]): Consequence[Detail[V]] = {
    _select(definitions, screenId, viewBindingId, false).flatMap { definition =>
      definition.screen match {
        case declared: DetailDefinition =>
          _compile_row(row, _detail_keys(declared), _action_keys(declared.actions, "definition.actions"))
            .map(compiled => new Detail(definition, definitions, declared, compiled))
        case _ => _failure("DEFINITION_MISMATCH", "screenId", "expected declared screen branch")
      }
    }
  }

  def listC[V](definitions: AdmittedDefinitions, screenId: String,
    viewBindingId: String, row: RowBinding[V],
    actions: Vector[ActionBinding[Vector[V]]]): Consequence[List[V]] = {
    _select(definitions, screenId, viewBindingId, true).flatMap { definition =>
      definition.screen match {
        case declared: ListDefinition =>
          for {
            compiled <- _compile_row(row, _field_keys(declared.itemFields, "definition.itemFields"),
              _action_keys(declared.itemActions, "definition.itemActions"))
            screenactions <- _compile_actions(actions, _action_keys(declared.actions, "definition.actions"), "actions")
          } yield new List(definition, definitions, declared, compiled, screenactions)
        case _ => _failure("DEFINITION_MISMATCH", "screenId", "expected declared screen branch")
      }
    }
  }

  private def _select(definitions: AdmittedDefinitions, screenid: String,
    viewbindingid: String, islist: Boolean): Consequence[Definition] = {
    val problem = if (definitions == null) {
      _error("ELEMENT_INVALID", "definitions", "expected admitted definitions")
    } else _id(screenid, "screenId").orElse(_id(viewbindingid, "viewBindingId"))
    problem match {
      case Some(diagnostic) => _diagnostic_failure(diagnostic)
      case None => definitions.definitions.find(_.screenId == screenid) match {
        case None => _failure("SCREEN_NOT_FOUND", "screenId", "screen must belong to admitted definitions")
        case Some(definition) =>
          val branch = definition.screen match {
            case _: ListDefinition => islist
            case _: DetailDefinition => !islist
          }
          if (!branch) _failure("DEFINITION_MISMATCH", "screenId", "expected declared screen branch")
          else if (definition.viewBindingId != viewbindingid) {
            _failure("VIEW_BINDING_MISMATCH", "viewBindingId", "expected declared View binding")
          } else Consequence.success(definition)
      }
    }
  }

  private def _field_keys(fields: Vector[FieldDefinition], path: String): Vector[RequiredKey] = {
    fields.zipWithIndex.map { case (field, index) => RequiredKey(field.value.bindingId, s"$path[$index].value.bindingId") }
  }
  private def _detail_keys(declared: DetailDefinition): Vector[RequiredKey] = {
    declared.sections.zipWithIndex.flatMap { case (section, index) =>
      _field_keys(section.fields, s"definition.sections[$index].fields")
    }
  }
  private def _action_keys(actions: Vector[ActionDefinition], path: String): Vector[RequiredKey] = {
    actions.zipWithIndex.map { case (action, index) => RequiredKey(action.bindingId, s"$path[$index].bindingId") }
  }

  private def _compile_row[V](row: RowBinding[V], keys: Vector[RequiredKey],
    actionkeys: Vector[RequiredKey]): Consequence[CompiledRow[V]] = {
    if (row == null) _failure("ELEMENT_INVALID", "row", "expected typed row registration")
    else if (row.resource == null) _failure("ELEMENT_INVALID", "row.resource", "expected resource callback")
    else {
      for {
        values <- _compile_values(row.values, keys)
        actions <- _compile_actions(row.actions, actionkeys, "row.actions")
      } yield CompiledRow(row.resource, values, actions)
    }
  }

  private def _compile_values[V](bindings: Vector[ValueBinding[V]],
    keys: Vector[RequiredKey]): Consequence[Vector[ValueBinding[V]]] = {
    val problem = if (bindings == null) {
      _error("ELEMENT_INVALID", "row.values", "expected value registrations")
    } else {
      bindings.iterator.zipWithIndex.flatMap { case (binding, index) =>
        val path = s"row.values[$index]"
        if (binding == null) _error("ELEMENT_INVALID", path, "expected value registration")
        else if (binding.bindingIds == null) _error("ELEMENT_INVALID", path + ".bindingIds", "expected output keys")
        else if (binding.bindingIds.isEmpty) _error("BINDING_MISSING", path + ".bindingIds", "expected at least one output key")
        else {
          binding.bindingIds.iterator.zipWithIndex.flatMap { case (key, outputindex) =>
            val keypath = s"$path.bindingIds[$outputindex]"
            _id(key, keypath).orElse {
              if (binding.bindingIds.take(outputindex).contains(key) ||
                bindings.take(index).exists(previous => previous.bindingIds.contains(key))) {
                _error("BINDING_DUPLICATE", keypath, "output key must have one registration")
              } else if (!keys.exists(_.key == key)) {
                _error("BINDING_UNDECLARED", keypath, "output key must be declared")
              } else None
            }
          }.nextOption().orElse {
            if (binding.convert == null) _error("ELEMENT_INVALID", path + ".convert", "expected conversion callback")
            else None
          }
        }
      }.nextOption().orElse {
        keys.find(required => !bindings.exists(_.bindingIds.contains(required.key))).map(required =>
          _diagnostic("BINDING_MISSING", required.path, "declared output key requires registration"))
      }
    }
    problem match {
      case Some(diagnostic) => _diagnostic_failure(diagnostic)
      case None => Consequence.success(keys.flatMap(required => bindings.find(_.bindingIds.contains(required.key))).distinct)
    }
  }

  private def _compile_actions[V](bindings: Vector[ActionBinding[V]], keys: Vector[RequiredKey],
    path: String): Consequence[Vector[ActionBinding[V]]] = {
    val problem = if (bindings == null) _error("ELEMENT_INVALID", path, "expected action registrations")
    else {
      bindings.iterator.zipWithIndex.flatMap { case (binding, index) =>
        val entrypath = s"$path[$index]"
        if (binding == null) _error("ELEMENT_INVALID", entrypath, "expected action registration")
        else _id(binding.bindingId, entrypath + ".bindingId").orElse {
          if (bindings.take(index).exists(_.bindingId == binding.bindingId)) {
            _error("BINDING_DUPLICATE", entrypath + ".bindingId", "action key must have one registration")
          } else if (!keys.exists(_.key == binding.bindingId)) {
            _error("BINDING_UNDECLARED", entrypath + ".bindingId", "action key must be declared")
          } else if (binding.evaluate == null) {
            _error("ELEMENT_INVALID", entrypath + ".evaluate", "expected availability callback")
          } else None
        }
      }.nextOption().orElse {
        keys.find(required => !bindings.exists(_.bindingId == required.key)).map(required =>
          _diagnostic("BINDING_MISSING", required.path, "declared action key requires registration"))
      }
    }
    problem match {
      case Some(diagnostic) => _diagnostic_failure(diagnostic)
      case None => Consequence.success(keys.flatMap(required => bindings.find(_.bindingId == required.key)).distinct)
    }
  }

  private def _evaluate_row[V](row: CompiledRow[V], source: V, path: String): Consequence[EvaluatedRow] = {
    for {
      resource <- _callback(path + ".resource")(row.resource(source))
      outputs <- _traverse(row.values.zipWithIndex) { case (binding, index) =>
        val outputpath = s"$path.values[$index]"
        _callback(outputpath)(binding.convert(source)).flatMap { values =>
          if (values.size != binding.bindingIds.size) {
            _failure("OUTPUT_COUNT_INVALID", outputpath, "expected one output per registered key")
          } else values.indices.find(outputindex => values(outputindex) == null) match {
            case Some(outputindex) => _failure("ELEMENT_INVALID", s"$outputpath[$outputindex]", "expected explicit display value")
            case None => Consequence.success(binding.bindingIds.zip(values))
          }
        }
      }
      actions <- _evaluate_actions(row.actions, source, path)
    } yield EvaluatedRow(resource, outputs.flatten.toMap, actions)
  }

  private def _evaluate_actions[V](bindings: Vector[ActionBinding[V]], source: V,
    path: String): Consequence[Map[String, ActionAvailability]] = {
    _traverse(bindings.zipWithIndex) { case (binding, index) =>
      _callback(s"$path.actions[$index]")(binding.evaluate(source)).map(value => binding.bindingId -> value)
    }.map(_.toMap)
  }

  private def _fields(declared: Vector[FieldDefinition], values: Map[String, DisplayValue]): Consequence[Vector[Field]] = {
    _traverse(declared) { field =>
      values.get(field.value.bindingId) match {
        case Some(value) => Consequence.success(Field(field.id, field.label, field.role, value))
        case None => _failure("BINDING_MISSING", "model", "declared output requires converted value")
      }
    }
  }
  private def _actions(declared: Vector[ActionDefinition], values: Map[String, ActionAvailability]): Consequence[Vector[Action]] = {
    _traverse(declared) { action =>
      values.get(action.bindingId) match {
        case Some(value) => Consequence.success(Action(action.id, action.label, action.bindingId, value))
        case None => _failure("BINDING_MISSING", "model", "declared action requires availability")
      }
    }
  }

  private def _traverse[A, B](values: Vector[A])(convert: A => Consequence[B]): Consequence[Vector[B]] = {
    values.foldLeft(Consequence.success(Vector.empty[B])) { (result, value) =>
      result.flatMap(items => convert(value).map(item => items :+ item))
    }
  }
  private def _callback[A](path: String)(invoke: => Consequence[A]): Consequence[A] = {
    val result = try invoke catch {
      case NonFatal(_) => _failure[A]("CALLBACK_FAILED", path, "callback did not return normally")
    }
    if (result == null) _failure("ELEMENT_INVALID", path, "expected callback consequence")
    else result.flatMap { value =>
      if (value == null) _failure("ELEMENT_INVALID", path, "expected nonnull callback value")
      else Consequence.success(value)
    }
  }
  private def _snapshot(snapshot: Option[QuerySnapshot]): Option[Diagnostic] = {
    if (snapshot == null || snapshot.contains(null)) _error("ELEMENT_INVALID", "snapshot", "expected optional query snapshot")
    else snapshot.flatMap(value => _id(value.reference, "snapshot.reference"))
  }
  private def _id(value: String, path: String): Option[Diagnostic] = {
    if (value != null && value.nonEmpty && value == value.trim) None
    else _error("ID_INVALID", path, "expected nonempty exactly trimmed opaque text")
  }
  private def _diagnostic(code: String, path: String, reason: String): Diagnostic = {
    Diagnostic("DSP96_PROJECTION_" + code, path, reason)
  }
  private def _error(code: String, path: String, reason: String): Option[Diagnostic] = {
    Some(_diagnostic(code, path, reason))
  }
  private def _failure[A](code: String, path: String, reason: String): Consequence[A] = {
    _diagnostic_failure(_diagnostic(code, path, reason))
  }
  private def _diagnostic_failure[A](diagnostic: Diagnostic): Consequence[A] = {
    Consequence.fail(Taxonomy.argumentInvalid, new ProjectionException(diagnostic))
  }
}
