package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.cli.CliEngine
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.cncf.subsystem.DefaultSubsystemProvider
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
    val _ = CncfRun.run(args)
  }
}

object CommandLauncher {
  def execute(args: Array[String]): Unit = {
    val _ = CncfRun.run(args)
  }
}
