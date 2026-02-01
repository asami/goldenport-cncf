package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.http.HttpRequest
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.http.HttpExecutionEngine

/*
 * @since   Jan.  7, 2026
 *  version Jan. 31, 2026
 * @version Feb.  1, 2026
 * @author  ASAMI, Tomoharu
 */
class ServerEmulatorOperation(val subsystem: Subsystem) extends CliOperation {
  val mode = RunMode.ServerEmulator

  def execute(req: Request): Int = {
    val args = make_component_args(req)
    val (includeHeader, rest) = _include_header(args)
    val result = _normalize_server_emulator_args(rest, server_emulator_base_url) match {
      case Consequence.Success(normalized) =>
        HttpRequest.fromCurlLike(normalized) match {
          case Consequence.Success(req) =>
            val engine = new HttpExecutionEngine(subsystem)
            val res = engine.execute(req)
            if (includeHeader) {
              _print_with_header(res)
            } else {
              _print_body(res)
            }
            Consequence.success(res)
          case Consequence.Failure(conclusion) =>
            print_error(conclusion)
            Consequence.Failure(conclusion)
        }
      case Consequence.Failure(conclusion) =>
        print_error(conclusion)
        Consequence.Failure(conclusion)
    }
    exit_code(result)
  }

  private def _include_header(
    args: Array[String]
  ): (Boolean, Seq[String]) = {
    var includeHeader = false
    val rest = args.filter { arg =>
      if (arg == "-i" || arg == "--include") {
        includeHeader = true
        false
      } else {
        true
      }
    }
    (includeHeader, rest.toIndexedSeq)
  }

  private def _normalize_server_emulator_args(
    args: Seq[String],
    baseUrl: String
  ): Consequence[Seq[String]] = {
    if (args.isEmpty) {
      Consequence.failure("server-emulator requires a path or URL")
    } else if (args.exists(_.contains("://"))) {
      Consequence.success(args)
    } else {
      _parse_component_service_operation(args).map {
        case (component, service, operation) =>
          Seq(_server_emulator_url(baseUrl, component, service, operation))
      }
    }
  }

  private def _server_emulator_url(
    baseUrl: String,
    component: String,
    service: String,
    operation: String
  ): String = {
    val trimmed = if (baseUrl.endsWith("/")) baseUrl.dropRight(1) else baseUrl
    s"${trimmed}/${component}/${service}/${operation}"
  }

  private def _parse_component_service_operation(
    args: Seq[String]
  ): Consequence[(String, String, String)] = {
    args.toVector match {
      case Vector(component, service, operation, _*) =>
        Consequence.success((component, service, operation))
      case Vector(single) if single.contains("/") || single.contains(".") =>
        parse_component_service_operation_string(single)
      case _ =>
        Consequence.failure("command must be component service operation or component.service.operation")
    }
  }

  private def _print_with_header(
    res: org.goldenport.http.HttpResponse
  ): Unit = {
    val statusLine = s"HTTP ${res.code}"
    val contentType = s"Content-Type: ${res.contentType}"
    Console.out.println(statusLine)
    Console.out.println(contentType)
    Console.out.println()
    _print_body(res)
  }

  private def _print_body(
    res: org.goldenport.http.HttpResponse
  ): Unit = {
    val body = res.getString.getOrElse(res.show)
    Console.out.println(body)
  }
}

object ServerEmulatorOperation {
}
