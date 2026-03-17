package org.goldenport.cncf.bootstrap

import java.nio.file.{Path, Paths}
import org.goldenport.Consequence
import org.goldenport.protocol.Response
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.subsystem.Subsystem

/*
 * @since   Mar. 18, 2026
 * @version Mar. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class BootstrapConfig(
  cwd: Path = Paths.get("").toAbsolutePath.normalize,
  args: Array[String] = Array.empty,
  modeHint: Option[RunMode] = None,
  extraComponents: Subsystem => Seq[Component] = (_: Subsystem) => Nil
)

trait CncfHandle {
  def subsystem: Subsystem
  def executeCommand(args: Array[String]): Consequence[Response]
  def close(): Unit
}

object CncfBootstrap {
  def initialize(
    config: BootstrapConfig
  ): Consequence[CncfHandle] = {
    val runtime = new CncfRuntime()
    runtime.initializeForEmbedding(
      cwd = config.cwd,
      args = config.args,
      modeHint = config.modeHint,
      extraComponents = config.extraComponents
    ).map { initializedSubsystem =>
      new CncfHandle {
        @volatile private var _is_closed: Boolean = false

        def subsystem: Subsystem = initializedSubsystem

        def executeCommand(args: Array[String]): Consequence[Response] =
          if (_is_closed)
            Consequence.failure("CncfHandle is already closed")
          else
            runtime.executeCommandResponse(initializedSubsystem, args)

        def close(): Unit =
          if (!_is_closed) {
            _is_closed = true
            runtime.closeEmbedding()
          }
      }
    }
  }
}
