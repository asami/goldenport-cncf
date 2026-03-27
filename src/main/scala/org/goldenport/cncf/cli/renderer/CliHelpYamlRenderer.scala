package org.goldenport.cncf.cli.renderer

import org.goldenport.cncf.projection.model.HelpModel

/*
 * @since   Mar.  5, 2026
 * @version Mar. 28, 2026
 * @author  ASAMI, Tomoharu
 */
object CliHelpYamlRenderer {
  def render(model: HelpModel): String = {
    val lines = Vector.newBuilder[String]
    lines += s"type: ${model.`type`}"
    lines += s"name: ${model.name}"
    lines += s"summary: ${model.summary}"
    model.component.foreach(v => lines += s"component: $v")
    model.service.foreach(v => lines += s"service: $v")
    model.selector.foreach { selector =>
      lines += "selector:"
      lines += s"  canonical: ${selector.canonical}"
      lines += s"  cli: ${selector.cli}"
      lines += s"  rest: ${selector.rest}"
      if (selector.accepted.nonEmpty) {
        lines += "  accepted:"
        selector.accepted.foreach(v => lines += s"    - $v")
      }
    }
    if (model.children.nonEmpty) {
      lines += "children:"
      model.children.foreach(v => lines += s"  - $v")
    }
    if (model.details.nonEmpty) {
      model.details.toVector.sortBy(_._1).foreach { case (k, values) =>
        lines += s"$k:"
        values.foreach(v => lines += s"  - $v")
      }
    }
    if (model.usage.nonEmpty) {
      lines += "usage:"
      model.usage.foreach(v => lines += s"  - $v")
    }
    lines.result().mkString("\n")
  }
}
