package org.goldenport.cncf.cli

import org.goldenport.Consequence
import org.goldenport.cli.CliEngine
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.cncf.subsystem.DefaultSubsystemProvider
import org.goldenport.cncf.subsystem.HelloWorldSubsystemMapping

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

@main def cncf(args: String*): Unit =
  CncfRun.run(args.toArray)
