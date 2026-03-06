package org.goldenport.cncf.cli.renderer

import org.goldenport.cncf.projection.model.HelpModel

/*
 * @since   Mar.  5, 2026
 * @version Mar.  5, 2026
 * @author  ASAMI, Tomoharu
 */
object CliHelpJsonRenderer {
  def render(model: HelpModel): String = {
    val children = model.children.map(v => s""""${_escape(v)}"""").mkString(",")
    val details = model.details.toVector.sortBy(_._1).map { case (k, values) =>
      val xs = values.map(v => s""""${_escape(v)}"""").mkString(",")
      s""""${_escape(k)}":[${xs}]"""
    }.mkString(",")
    val usage = model.usage.map(v => s""""${_escape(v)}"""").mkString(",")
    s"""{"type":"${_escape(model.`type`)}","name":"${_escape(model.name)}","summary":"${_escape(model.summary)}","children":[${children}],"details":{${details}},"usage":[${usage}]}"""
  }

  private def _escape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
