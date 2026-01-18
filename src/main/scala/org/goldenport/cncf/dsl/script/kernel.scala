package org.goldenport.cncf.dsl.script

import org.goldenport.cncf.dsl.script.ScriptActionCall

/*
 * @since   Jan. 14, 2026
 * @version Jan. 19, 2026
 * @author  ASAMI, Tomoharu
 */
def run(args: Array[String])(body: ScriptActionCall => Any): Unit =
  ScriptRuntime.run(args)(body)

def run(args: Seq[String])(body: ScriptActionCall => Any): Unit =
  ScriptRuntime.run(args)(body)
