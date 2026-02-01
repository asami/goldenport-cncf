package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.Argument
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.resolver.OperationResolver.ResolutionResult
import org.goldenport.cncf.path.PathPreNormalizer
import org.goldenport.cncf.observability.global.GlobalObservable

/*
 * @since   Jan. 31, 2026
 * @version Feb.  1, 2026
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
    _selector_and_arguments(args.toIndexedSeq).flatMap { case (selector, tail) =>
      val canonicalSelector = PathPreNormalizer.rewriteSelector(selector, mode, _alias_resolver)
      subsystem.resolver.resolve(canonicalSelector, allowPrefix = false, allowImplicit = false) match {
        case ResolutionResult.Resolved(_, component, service, operation) =>
          val arguments = _build_request_arguments(tail)
          Consequence.success(
            Request.of(
              component = component,
              service = service,
              operation = operation,
              arguments = arguments,
              switches = Nil,
              properties = Nil
            )
          )
        case ResolutionResult.NotFound(stage, input) =>
          Consequence.failure(s"${stage.toString.toLowerCase} not found: $input")
        case ResolutionResult.Ambiguous(input, candidates) =>
          Consequence.failure(s"ambiguous selector '$input': ${candidates.mkString(", ")}")
        case ResolutionResult.Invalid(reason) =>
          Consequence.failure(s"invalid selector: $reason")
      }
    }

  private def _selector_and_arguments(
    args: Seq[String]
  ): Consequence[(String, Seq[String])] = {
    args.toVector match {
      case Vector() =>
        Consequence.failure("command name is required")
      case Vector(component, service, operation, rest @ _*) =>
        Consequence.success((s"$component.$service.$operation", rest.toVector))
      case Vector(single, rest @ _*) if single.contains("/") =>
        _selector_from_path(single, "/").map(_ -> rest.toVector)
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
  ): List[Argument] =
    values.zipWithIndex.map { case (value, index) =>
      Argument(s"arg${index + 1}", value)
    }.toList

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
