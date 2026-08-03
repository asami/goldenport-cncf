package org.goldenport.cncf

import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import org.goldenport.Consequence
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.config.RuntimeProcessExitPolicy
import org.goldenport.cncf.observability.global.GlobalObservable

/*
 * @since   Jan.  7, 2026
 *  version Jan. 23, 2026
 *  version Feb.  1, 2026
 *  version Mar. 26, 2026
 *  version Apr. 10, 2026
 *  version Jun. 29, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfMain extends GlobalObservable {
  final class CliFailed(val code: Int)
      extends RuntimeException(s"Command failed (exit=$code)")
      with scala.util.control.NoStackTrace

  def main(args: Array[String]): Unit = {
    _ensure_utf8_stdio()

    if (args.toVector == Vector("version") || args.toVector == Vector("--version")) {
      println(s"${CncfBuildInfo.name} ${CncfBuildInfo.version}")
      return
    }

    val cwd = Paths.get("").toAbsolutePath.normalize
    val bootstrap = CncfRuntime.bootstrapC(cwd, args) match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) =>
        Console.err.println(conclusion.displayMessage)
        return
    }

    val code: Int =
      try {
        (bootstrap.repositories.activeRepositories, bootstrap.repositories.searchRepositories) match {
          case (Left(message), _) =>
            Console.err.println(message)
            2
          case (_, Left(message)) =>
            Console.err.println(message)
            2
          case (Right(activeSpecs), Right(searchSpecs)) =>
            val assemblysearchspecs = CncfRuntime.developmentAssemblySearchSpecifications(activeSpecs, searchSpecs)
            val extras = CncfRuntime.componentExtraFunction(activeSpecs, bootstrap.front, assemblysearchspecs)
            val invocation = CncfRuntime.resolveSubsystemInvocation(bootstrap.invocation, searchSpecs, activeSpecs)
            CncfRuntime.runWithExtraComponents(invocation.actualArgs, extras)
        }
      } catch {
        case e: CliFailed => e.code
      }

    bootstrap.front.processExitPolicy.disposition(code) match {
      case RuntimeProcessExitPolicy.Disposition.Exit =>
        sys.exit(code)
      case RuntimeProcessExitPolicy.Disposition.Fail =>
        throw new CliFailed(code)
      case RuntimeProcessExitPolicy.Disposition.Return =>
        ()
    }
  }

  private def _ensure_utf8_stdio(): Unit = {
    System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8))
    System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8))
  }
}
