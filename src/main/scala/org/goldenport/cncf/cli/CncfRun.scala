package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.cli.CliEngine
import org.goldenport.protocol.{Request, Response}
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.cncf.subsystem.DefaultSubsystemProvider
import org.goldenport.cncf.subsystem.HelloWorldSubsystemFactory
import org.goldenport.cncf.subsystem.HelloWorldSubsystemMapping
import org.goldenport.cncf.http.HelloWorldHttpServer

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfRun {
  def run(args: Array[String]): Consequence[OperationRequest] = {
    val subsystem = DefaultSubsystemProvider.helloWorld()
    val services = HelloWorldSubsystemMapping.toServiceDefinitionGroup(subsystem)
    val engine = new CliEngine(
      CliEngine.Config(),
      CliEngine.Specification(services)
    )
    val actualArgs =
      if (args.isEmpty) {
        Array("ping")
      } else {
        args
      }
    engine.makeRequest(actualArgs.toIndexedSeq)
  }
}

object ServerLauncher {
  def start(args: Array[String]): Unit = {
    HelloWorldHttpServer.start(args)
  }
}

object ClientLauncher {
  def execute(args: Array[String]): Unit = {
    val subsystem = HelloWorldSubsystemFactory.helloWorld()
    _to_request(args).flatMap { req =>
      subsystem.execute(req)
    } match {
      case Consequence.Success(res) =>
        _print_response(res)
        sys.exit(0)
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.message)
        sys.exit(1)
    }
  }
}

object CommandLauncher {
  def execute(args: Array[String]): Unit = {
    val subsystem = HelloWorldSubsystemFactory.helloWorld()
    _to_request(args).flatMap { req =>
      subsystem.execute(req)
    } match {
      case Consequence.Success(res) =>
        _print_response(res)
        sys.exit(0)
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.message)
        sys.exit(1)
    }
  }

}

private def _print_response(res: Response): Unit = {
  res match {
    case Response.Scalar(value) => println(value.toString)
    case other => println(other.toString)
  }
}

private def _to_request(args: Array[String]): Consequence[Request] = {
  args.headOption match {
    case Some(s) =>
      _parse_command(s).map { case (serviceid, operationname) =>
        Request(
          service = Some(serviceid),
          operation = operationname,
          arguments = Nil,
          switches = Nil,
          properties = Nil
        )
      }
    case None =>
      Consequence.failure("command name is required")
  }
}

private def _parse_command(
  s: String
): Consequence[(String, String)] = {
  s.split("\\.") match {
    case Array(componentname, servicename, operationname) =>
      Consequence.success((s"${componentname}.${servicename}", operationname))
    case _ =>
      Consequence.failure("command must be component.service.operation")
  }
}
