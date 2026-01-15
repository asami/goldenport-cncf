package org.goldenport.cncf.dsl.script

import org.goldenport.cncf.action.*

/*
 * @since   Jan. 14, 2026
 * @version Jan. 14, 2026
 * @author  ASAMI, Tomoharu
 */
def run(args: Array[String])(body: ActionCall => Any): Unit =
  ScriptRuntime.run(args)(body)
