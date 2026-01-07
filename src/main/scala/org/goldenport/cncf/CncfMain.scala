package org.goldenport.cncf

import org.goldenport.cncf.cli.CncfRuntime

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object CncfMain {
  def main(args: Array[String]): Unit = {
    CncfRuntime.run(args)
  }
}
