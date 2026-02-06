package org.goldenport.cncf.backend.shellcommand

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.process.{
  ShellCommand,
  ShellCommandResult,
  ShellCommandExecutor,
  LocalShellCommandExecutor
}

/*
 * @since   Feb.  5, 2026
 * @version Feb.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class ShellCommandAdapter(
  executor: ShellCommandExecutor = new LocalShellCommandExecutor
) {

  def execute(
    command: Vector[String],
    workDir: Option[Path] = None,
    env: Map[String, String] = Map.empty
  ): Consequence[ShellCommandResult] = {
    val external = ShellCommand(
      command = command,
      workDir = workDir,
      env = env
    )
    executor.execute(external)
  }
}
