package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.Argument
import org.goldenport.protocol.Property
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.path.PathPreNormalizer
import org.goldenport.cncf.observability.global.GlobalObservable

/*
 * @since   Jan. 31, 2026
 *  version Feb.  1, 2026
 *  version Mar. 27, 2026
 * @version Apr. 11, 2026
 * @author  ASAMI, Tomoharu
 */
abstract class CliOperation extends GlobalObservable {
  import CliOperation._
  def subsystem: Subsystem
  def mode: RunMode

  final protected val server_emulator_base_url: String = subsystem.serverEmulatorBaseUrl

  private val _alias_resolver = subsystem.aliasResolver

  // final protected def make_args(req: OperationRequest): Array[String] =
  //   make_args(req.request)

  final protected def make_component_args(req: Request): Array[String] =
    req.toSubCommand.toArgs

  final protected def parse_command_args(
    args: Array[String]
  ): Consequence[Request] =
    _extract_runtime_options(args.toIndexedSeq) match { case (runtimeOptions, clean) =>
      _selector_and_arguments(clean).flatMap { case (selector, tail) =>
      val normalized = _normalize_meta_selector(selector, tail.toVector)
      val canonicalSelector = PathPreNormalizer.rewriteSelector(normalized._1, mode, _alias_resolver)
      subsystem.resolver.resolve(canonicalSelector) match {
        case ResolutionResult.Resolved(_, component, service, operation) =>
          val arguments = _build_request_arguments(normalized._2)
          val properties = _runtime_properties(runtimeOptions)
          Consequence.success(
            Request.of(
              component = component,
              service = service,
              operation = operation,
              arguments = arguments,
              switches = Nil,
              properties = properties
            )
          )
        case ResolutionResult.NotFound(stage, input) =>
          Consequence.failure(s"${stage.toString.toLowerCase} not found: $input")
        case ResolutionResult.Ambiguous(input, candidates) =>
          Consequence.failure(s"ambiguous selector '$input': ${candidates.mkString(", ")}")
        case ResolutionResult.Invalid(reason) =>
          Consequence.failure(s"invalid selector: $reason")
      }
    }}

  private def _normalize_meta_selector(
    selector: String,
    tail: Vector[String]
  ): (String, Vector[String]) = {
    val segments = selector.split("\\.").toVector.filter(_.nonEmpty)
    segments match {
      case Vector("help") =>
        _normalize_meta_selector("meta.help", tail)
      case head +: _ if head == "help" =>
        (selector, tail)
      case Vector("meta", operation) =>
        _default_meta_component_name() match {
          case Some(componentName) =>
            (s"$componentName.meta.$operation", tail)
          case None =>
            (selector, tail)
        }
      case Vector(component, "meta", operation) =>
        if (operation == "help")
          (s"$component.meta.help", component +: tail)
        else
          (s"$component.meta.$operation", tail)
      case Vector(component, service, "meta", operation) =>
        (s"$component.meta.$operation", s"$component.$service" +: tail)
      case _ =>
        (selector, tail)
    }
  }

  private def _default_meta_component_name(): Option[String] =
    subsystem.components.sortBy(_.name).headOption.map(_.name)

  private def _extract_runtime_options(
    args: Seq[String]
  ): (_RuntimeOptions, Seq[String]) = {
    val clean = Vector.newBuilder[String]
    var options = _RuntimeOptions()
    args.foreach { token =>
      token match {
        case "--json" =>
          options = options.copy(json = true)
        case s if s.startsWith("--json=") =>
          options = options.copy(json = true)
        case "--debug" =>
          options = options.copy(debug = true)
        case s if s.startsWith("--debug=") =>
          options = options.copy(debug = true)
        case "--no-exit" =>
          options = options.copy(noExit = true)
        case _ =>
          clean += token
      }
    }
    (options, clean.result())
  }

  private def _runtime_properties(options: _RuntimeOptions): List[Property] = {
    val b = List.newBuilder[Property]
    if (options.json) b += Property("cncf.format", "json", None)
    if (options.debug) b += Property("cncf.debug", "true", None)
    if (options.noExit) b += Property("cncf.no-exit", "true", None)
    b.result()
  }

  private case class _RuntimeOptions(
    json: Boolean = false,
    debug: Boolean = false,
    noExit: Boolean = false
  )

  private def _selector_and_arguments(
    args: Seq[String]
  ): Consequence[(String, Seq[String])] = {
    args.toVector match {
      case Vector() =>
        Consequence.failure("command name is required")
      case Vector(single, rest @ _*) if single.contains("/") =>
        _selector_from_path(single, "/").map(_ -> rest.toVector)
      case Vector(single, rest @ _*) if single.contains(".") =>
        Consequence.success((single, rest.toVector))
      case Vector(component, service, operation, rest @ _*) =>
        Consequence.success((s"$component.$service.$operation", rest.toVector))
      case Vector(single, rest @ _*) =>
        Consequence.success((single, rest.toVector))
    }
  }

  private def _selector_from_path(
    value: String,
    delimiter: String
  ): Consequence[String] = {
    val segments = value.split(delimiter).toVector.filter(_.nonEmpty)
    if (segments.size == 3) {
      Consequence.success(segments.mkString("."))
    } else {
      delimiter match {
        case "/" => Consequence.failure("command path must be /component/service/operation")
        case "." => Consequence.failure("command must be component.service.operation")
        case _ => Consequence.failure("command selector is invalid")
      }
    }
  }

  private def _build_request_arguments(
    values: Seq[String]
  ): List[Argument] = {
    val b = List.newBuilder[Argument]
    val in = values.toVector
    var positional = 1
    var i = 0
    while (i < in.length) {
      val current = in(i)
      if (current.startsWith("--") && current.length > 2) {
        val body = current.drop(2)
        val eq = body.indexOf('=')
        if (eq > 0) {
          b += Argument(body.take(eq), body.drop(eq + 1))
        } else if (body.nonEmpty && i + 1 < in.length && !in(i + 1).startsWith("--")) {
          b += Argument(body, in(i + 1))
          i = i + 1
        } else if (body.nonEmpty) {
          b += Argument(body, "true")
        } else {
          b += Argument(s"arg$positional", current)
          positional = positional + 1
        }
      } else {
        b += Argument(s"arg$positional", current)
        positional = positional + 1
      }
      i = i + 1
    }
    b.result()
  }

  final protected def parse_component_service_operation_string(
    s: String
  ): Consequence[(String, String, String)] = {
    if (s.contains("/")) {
      s.split("/").toVector.filter(_.nonEmpty) match {
        case Vector(component, service, operation) =>
          Consequence.success((component, service, operation))
        case _ =>
          Consequence.failure("command path must be /component/service/operation")
      }
    } else {
      s.split("\\.") match {
        case Array(component, service, operation) =>
          Consequence.success((component, service, operation))
        case _ =>
          Consequence.failure("command must be component.service.operation")
      }
    }
  }

  final protected def exit_success: Int = 0

  final protected def exit_code(c: Consequence[_]): Int =
    c match {
      case Consequence.Success(_) => 0
      case Consequence.Failure(conclusion) =>
        val _ = conclusion
        // TODO When Conclusion/Status supports Long (or an explicit exit/detail code),
        // map it here and return that value.
        1
    }

  final protected def print_response(res: Response): Unit =
    println(res.print) // TODO

  final protected def print_response(res: OperationResponse): Unit = {
    res match {
      case OperationResponse.Http(http) =>
        val body = http.getString.getOrElse(http.print)
        Console.out.println(body)
      case _ =>
        print_response(res.toResponse)
    }
  }

  final protected def print_error(c: Conclusion): Unit = {
    Console.err.println(c.show) // TODO
  }

  final protected def print_error(message: String): Unit = {
    Console.err.println(message) // TODO
  }
}

object CliOperation {
}
