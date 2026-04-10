package org.goldenport.cncf.bootstrap

import java.nio.file.{Path, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Mar. 18, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final case class BootstrapConfig(
  cwd: Path = Paths.get("").toAbsolutePath.normalize,
  args: Array[String] = Array.empty,
  modeHint: Option[RunMode] = None,
  extraComponents: Subsystem => Seq[Component] = (_: Subsystem) => Nil
)

trait CncfHandle {
  def subsystem: org.goldenport.cncf.subsystem.Subsystem
  def executeCommand(args: Array[String]): Consequence[org.goldenport.protocol.Response]
  def executeAction(action: org.goldenport.cncf.action.Action): Consequence[org.goldenport.protocol.operation.OperationResponse]
  def close(): Unit
}

object CncfBootstrap {
  def initialize(
    config: BootstrapConfig
  ): Consequence[CncfHandle] =
    new CncfRuntime().initializeHandle(config)
}
