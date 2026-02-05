package org.goldenport.cncf.backend.shellcommand

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.process.{
  ExternalCommand,
  ExternalCommandExecutor,
  LocalExternalCommandExecutor
}

/*
 * @since   Feb.  5, 2026
 * @version Feb.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class ShellCommandAdapter(
  executor: ExternalCommandExecutor = new LocalExternalCommandExecutor
) {

  def execute(
    command: Vector[String],
    workDir: Option[Path] = None,
    env: Map[String, String] = Map.empty
  ): Consequence[ShellCommandResult] = {
    val external = ExternalCommand(
      command = command,
      workDir = workDir,
      env = env
    )
    executor.execute(external).map(result =>
      ShellCommandResult(
        exitCode = result.exitCode,
        stdout = result.stdout,
        stderr = result.stderr
      )
    )
  }
}

final case class ShellCommandResult(
  exitCode: Int,
  stdout: String,
  stderr: String
)
