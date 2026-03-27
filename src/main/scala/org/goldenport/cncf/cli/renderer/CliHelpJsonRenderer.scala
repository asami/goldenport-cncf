package org.goldenport.cncf.cli.renderer

import org.goldenport.cncf.projection.model.HelpModel

/*
 * @since   Mar.  5, 2026
 * @version Mar. 28, 2026
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
    val component = model.component.map(v => s""""${_escape(v)}"""").getOrElse("null")
    val service = model.service.map(v => s""""${_escape(v)}"""").getOrElse("null")
    val selector = model.selector.map { selector =>
      val accepted = selector.accepted.map(v => s""""${_escape(v)}"""").mkString(",")
      s"""{"canonical":"${_escape(selector.canonical)}","cli":"${_escape(selector.cli)}","rest":"${_escape(selector.rest)}","accepted":[${accepted}]}"""
    }.getOrElse("null")
    s"""{"type":"${_escape(model.`type`)}","name":"${_escape(model.name)}","summary":"${_escape(model.summary)}","component":${component},"service":${service},"selector":${selector},"children":[${children}],"details":{${details}},"usage":[${usage}]}"""
  }

  private def _escape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
