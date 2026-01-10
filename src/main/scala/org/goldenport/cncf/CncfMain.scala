package org.goldenport.cncf

import org.goldenport.cncf.cli.CncfRuntime

/*
 * @since   Jan.  7, 2026
 * @version Jan. 11, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfMain {
  final class CliFailed(val code: Int)
      extends RuntimeException(s"Command failed (exit=$code)")
      with scala.util.control.NoStackTrace

  def main(args: Array[String]): Unit = {
    val (noExit, rest) = _take_no_exit(args)

    val code: Int =
      try {
        CncfRuntime.runExitCode(rest)
      } catch {
        case e: CliFailed => e.code
      }

    if (noExit) {
      if (code != 0) throw new CliFailed(code)
      ()
    } else {
      sys.exit(code)
    }
  }

  private def _take_no_exit(args: Array[String]): (Boolean, Array[String]) = {
    val noexit = args.contains("--no-exit")
    val rest = args.filterNot(_ == "--no-exit")
    (noexit, rest)
  }
}
