package org.goldenport.cncf

import java.nio.file.Paths
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.observability.global.GlobalObservable

/*
 * @since   Jan.  7, 2026
 *  version Jan. 23, 2026
 *  version Feb.  1, 2026
 *  version Mar. 26, 2026
 * @version Apr. 10, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfMain extends GlobalObservable {
  final class CliFailed(val code: Int)
      extends RuntimeException(s"Command failed (exit=$code)")
      with scala.util.control.NoStackTrace

  def main(args: Array[String]): Unit = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val bootstrap = CncfRuntime.bootstrap(cwd, args)

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
            val extras = CncfRuntime.componentExtraFunction(activeSpecs, bootstrap.front)
            val invocation = CncfRuntime.resolveSubsystemInvocation(bootstrap.invocation, searchSpecs)
            CncfRuntime.runWithExtraComponents(invocation.actualArgs, extras)
        }
      } catch {
        case e: CliFailed => e.code
      }

    if (bootstrap.front.forceExit) {
      sys.exit(code) // CLI adapter may exit only when --force-exit is requested.
    } else if (bootstrap.front.noExit && code != 0) {
      throw new CliFailed(code)
    } else {
      ()
    }
  }
}
