package org.goldenport.cncf.display

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.view.{Browser, ViewSpace}
import org.goldenport.observation.Taxonomy
import org.simplemodeling.model.datatype.EntityId
import scala.util.control.NonFatal

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 */
/** Component-owned contextual sources joined to their matching typed projectors. */
object DisplayRuntime {
  final case class Diagnostic(code: String, path: String, reason: String)
  /** Structured cause carrier; returned through Consequence, never thrown. */
  final class BindingException(val diagnostic: Diagnostic)
    extends IllegalArgumentException(s"${diagnostic.code}: ${diagnostic.path}: ${diagnostic.reason}")

  sealed abstract class Binding private[DisplayRuntime] () {
    def screenId: String
    def viewBindingId: String
    private[DisplayRuntime] def _compile_c(
      definitions: GeneratedLogicalUiAbi.AdmittedDefinitions,
      viewspace: ViewSpace,
      path: String
    ): Consequence[Entry]
  }

  private final class DetailBinding[V](
    val screenId: String,
    val viewBindingId: String,
    private val _browser: Browser[V],
    private val _row: DisplayProjection.RowBinding[V]
  ) extends Binding {
    private[DisplayRuntime] def _compile_c(
      definitions: GeneratedLogicalUiAbi.AdmittedDefinitions,
      viewspace: ViewSpace,
      path: String
    ): Consequence[Entry] = {
      _registered_browser_c(viewspace, _browser, path).flatMap { _ =>
        DisplayProjection.detailC(definitions, screenId, viewBindingId, _row)
          .map(projector => new DetailEntry(screenId, _browser, projector))
      }
    }
  }

  private final class ListBinding[V](
    val screenId: String,
    val viewBindingId: String,
    private val _browser: Browser[V],
    private val _row: DisplayProjection.RowBinding[V],
    private val _actions: Vector[DisplayProjection.ActionBinding[Vector[V]]]
  ) extends Binding {
    private[DisplayRuntime] def _compile_c(
      definitions: GeneratedLogicalUiAbi.AdmittedDefinitions,
      viewspace: ViewSpace,
      path: String
    ): Consequence[Entry] = {
      _registered_browser_c(viewspace, _browser, path).flatMap { _ =>
        DisplayProjection.listC(definitions, screenId, viewBindingId, _row, _actions)
          .map(projector => new ListEntry(screenId, _browser, projector))
      }
    }
  }

  private sealed abstract class Entry {
    def screenId: String
    def readC(resourceId: EntityId)(using ctx: ExecutionContext): Consequence[Display]
    def queryC(query: Query[?])(using ctx: ExecutionContext): Consequence[Display]
  }

  private final class DetailEntry[V](
    val screenId: String,
    private val _browser: Browser[V],
    private val _projector: DisplayProjection.Detail[V]
  ) extends Entry {
    def readC(resourceId: EntityId)(using ctx: ExecutionContext): Consequence[Display] = {
      _source_c(_browser.find_with_context(resourceId)).flatMap(_projector.projectC).flatMap { display =>
        display.model match {
          case model: DisplayModel.DetailModel if model.resource.entityId == resourceId =>
            Consequence.success(display)
          case _ =>
            _failure("SOURCE_ID_MISMATCH", "model.resource.entityId", "source must identify the requested resource")
        }
      }
    }
    def queryC(query: Query[?])(using ctx: ExecutionContext): Consequence[Display] = {
      _unsupported_operation
    }
  }

  private final class ListEntry[V](
    val screenId: String,
    private val _browser: Browser[V],
    private val _projector: DisplayProjection.List[V]
  ) extends Entry {
    def readC(resourceId: EntityId)(using ctx: ExecutionContext): Consequence[Display] = {
      _unsupported_operation
    }
    def queryC(query: Query[?])(using ctx: ExecutionContext): Consequence[Display] = {
      _source_c(_browser.query_with_context(query)).flatMap(source => _projector.projectC(source, None))
    }
  }

  final class Registry private[DisplayRuntime](private val _entries: Vector[Entry]) {
    private val _by_screen: Map[String, Entry] = _entries.map(entry => entry.screenId -> entry).toMap

    def screenIds: Vector[String] = _entries.map(_.screenId)

    def readC(screenId: String, resourceId: EntityId)(using ctx: ExecutionContext): Consequence[Display] = {
      _lookup_c(screenId).flatMap { entry =>
        if (resourceId == null) _failure("ELEMENT_INVALID", "resourceId", "expected resource identity")
        else if (ctx == null) _failure("ELEMENT_INVALID", "context", "expected execution context")
        else entry.readC(resourceId)
      }
    }

    def queryC(screenId: String, query: Query[?])(using ctx: ExecutionContext): Consequence[Display] = {
      _lookup_c(screenId).flatMap { entry =>
        if (query == null) _failure("ELEMENT_INVALID", "query", "expected semantic query")
        else if (ctx == null) _failure("ELEMENT_INVALID", "context", "expected execution context")
        else entry.queryC(query)
      }
    }

    private def _lookup_c(screenid: String): Consequence[Entry] = {
      _id(screenid, "screenId") match {
        case Some(problem) => _diagnostic_failure(problem)
        case None => _by_screen.get(screenid) match {
          case Some(entry) => Consequence.success(entry)
          case None => _failure("SCREEN_NOT_FOUND", "screenId", "screen must belong to this registry")
        }
      }
    }
  }

  def detail[V](screenId: String, viewBindingId: String, browser: Browser[V],
    row: DisplayProjection.RowBinding[V]): Binding = {
    new DetailBinding(screenId, viewBindingId, browser, row)
  }

  def list[V](screenId: String, viewBindingId: String, browser: Browser[V],
    row: DisplayProjection.RowBinding[V],
    actions: Vector[DisplayProjection.ActionBinding[Vector[V]]]): Binding = {
    new ListBinding(screenId, viewBindingId, browser, row, actions)
  }

  private[cncf] def bindC(definitions: GeneratedLogicalUiAbi.AdmittedDefinitions,
    viewSpace: ViewSpace, bindings: Vector[Binding]): Consequence[Registry] = {
    val problem = if (definitions == null) {
      _error("ELEMENT_INVALID", "definitions", "expected admitted definitions")
    } else if (viewSpace == null) {
      _error("ELEMENT_INVALID", "viewSpace", "expected component ViewSpace")
    } else if (bindings == null) {
      _error("ELEMENT_INVALID", "bindings", "expected ordered runtime bindings")
    } else {
      bindings.iterator.zipWithIndex.flatMap { case (binding, index) =>
        val path = s"bindings[$index]"
        if (binding == null) _error("ELEMENT_INVALID", path, "expected typed runtime binding")
        else _id(binding.screenId, path + ".screenId")
          .orElse(_id(binding.viewBindingId, path + ".viewBindingId"))
          .orElse {
            if (bindings.take(index).exists(_.screenId == binding.screenId)) {
              _error("SCREEN_DUPLICATE", path + ".screenId", "screen requires exactly one runtime binding")
            } else if (!definitions.definitions.exists(_.screenId == binding.screenId)) {
              _error("SCREEN_UNDECLARED", path + ".screenId", "runtime screen must be declared")
            } else None
          }
      }.nextOption().orElse {
        definitions.definitions.zipWithIndex.find { case (definition, _) =>
          !bindings.exists(_.screenId == definition.screenId)
        }.map { case (_, index) =>
          _diagnostic("SCREEN_MISSING", s"definitions[$index].screenId", "declared screen requires runtime binding")
        }
      }
    }
    problem match {
      case Some(diagnostic) => _diagnostic_failure(diagnostic)
      case None =>
        definitions.definitions.foldLeft(Consequence.success(Vector.empty[Entry])) { (result, definition) =>
          result.flatMap { entries =>
            bindings.zipWithIndex.find(_._1.screenId == definition.screenId) match {
              case Some((binding, index)) =>
                binding._compile_c(definitions, viewSpace, s"bindings[$index].browser").map(entries :+ _)
              case None => _failure("SCREEN_MISSING", "bindings", "declared screen requires runtime binding")
            }
          }
        }.map(entries => new Registry(entries))
    }
  }

  private[cncf] def providerMissingC[A]: Consequence[A] = {
    _failure("PROVIDER_MISSING", "bindings", "admitted definitions require a runtime binding provider")
  }
  private[cncf] def providerFailedC[A]: Consequence[A] = {
    _failure("PROVIDER_FAILED", "bindings", "runtime binding provider did not return normally")
  }

  private def _registered_browser_c(viewspace: ViewSpace, browser: Browser[?], path: String): Consequence[Unit] = {
    if (browser == null) _failure("ELEMENT_INVALID", path, "expected canonical typed Browser")
    else if (!viewspace.containsBrowser(browser)) {
      _failure("SOURCE_NOT_REGISTERED", path, "Browser must be registered in the component ViewSpace")
    } else Consequence.unit
  }

  private def _source_c[V](invoke: => Consequence[V]): Consequence[V] = {
    val result = try invoke catch {
      case NonFatal(_) => _failure[V]("SOURCE_FAILED", "source", "contextual source did not return normally")
    }
    if (result == null) _failure("ELEMENT_INVALID", "source", "expected source consequence")
    else result
  }
  private def _unsupported_operation: Consequence[Display] = {
    _failure("OPERATION_UNSUPPORTED", "screenId", "operation requires the matching screen branch")
  }
  private def _id(value: String, path: String): Option[Diagnostic] = {
    if (value != null && value.nonEmpty && value == value.trim) None
    else _error("ID_INVALID", path, "expected nonempty exactly trimmed opaque text")
  }
  private def _diagnostic(code: String, path: String, reason: String): Diagnostic = {
    Diagnostic("DSP96_RUNTIME_" + code, path, reason)
  }
  private def _error(code: String, path: String, reason: String): Option[Diagnostic] = {
    Some(_diagnostic(code, path, reason))
  }
  private def _failure[A](code: String, path: String, reason: String): Consequence[A] = {
    _diagnostic_failure(_diagnostic(code, path, reason))
  }
  private def _diagnostic_failure[A](diagnostic: Diagnostic): Consequence[A] = {
    Consequence.fail(Taxonomy.argumentInvalid, new BindingException(diagnostic))
  }
}
