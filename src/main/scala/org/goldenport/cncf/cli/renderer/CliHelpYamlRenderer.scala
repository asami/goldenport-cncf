package org.goldenport.cncf.cli.renderer

import org.goldenport.cncf.projection.model.HelpModel

/*
 * @since   Mar.  5, 2026
 *  version Mar. 28, 2026
 * @version Apr.  6, 2026
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
    if (model.useCases.nonEmpty) {
      lines += "structuredUseCases:"
      model.useCases.foreach { usecase =>
        lines += s"  - name: ${usecase.name}"
        usecase.summary.foreach(v => lines += s"    summary: $v")
        usecase.actor.foreach(v => lines += s"    actor: $v")
        usecase.primaryActor.foreach(v => lines += s"    primaryActor: $v")
        usecase.secondaryActor.foreach(v => lines += s"    secondaryActor: $v")
        usecase.supportingActor.foreach(v => lines += s"    supportingActor: $v")
        usecase.stakeholder.foreach(v => lines += s"    stakeholder: $v")
        usecase.goal.foreach(v => lines += s"    goal: $v")
        usecase.precondition.foreach(v => lines += s"    precondition: $v")
        usecase.postcondition.foreach(v => lines += s"    postcondition: $v")
        if (usecase.scenarios.nonEmpty) {
          lines += "    scenarios:"
          usecase.scenarios.foreach { scenario =>
            lines += s"      - name: ${scenario.name}"
            scenario.summary.foreach(v => lines += s"        summary: $v")
            scenario.description.foreach(v => lines += s"        description: $v")
            if (scenario.steps.nonEmpty) {
              lines += "        steps:"
              scenario.steps.foreach(v => lines += s"          - $v")
            }
            if (scenario.alternates.nonEmpty) {
              lines += "        alternates:"
              scenario.alternates.foreach(v => lines += s"          - $v")
            }
            if (scenario.exceptions.nonEmpty) {
              lines += "        exceptions:"
              scenario.exceptions.foreach(v => lines += s"          - $v")
            }
          }
        }
      }
    }
    if (model.domainUseCases.nonEmpty) {
      lines += "structuredDomainUseCases:"
      model.domainUseCases.foreach { usecase =>
        lines += s"  - name: ${usecase.name}"
        usecase.summary.foreach(v => lines += s"    summary: $v")
        usecase.actor.foreach(v => lines += s"    actor: $v")
        usecase.primaryActor.foreach(v => lines += s"    primaryActor: $v")
        usecase.secondaryActor.foreach(v => lines += s"    secondaryActor: $v")
        usecase.supportingActor.foreach(v => lines += s"    supportingActor: $v")
        usecase.stakeholder.foreach(v => lines += s"    stakeholder: $v")
        usecase.goal.foreach(v => lines += s"    goal: $v")
        usecase.precondition.foreach(v => lines += s"    precondition: $v")
        usecase.postcondition.foreach(v => lines += s"    postcondition: $v")
        if (usecase.scenarios.nonEmpty) {
          lines += "    scenarios:"
          usecase.scenarios.foreach { scenario =>
            lines += s"      - name: ${scenario.name}"
            scenario.summary.foreach(v => lines += s"        summary: $v")
            scenario.description.foreach(v => lines += s"        description: $v")
            if (scenario.steps.nonEmpty) {
              lines += "        steps:"
              scenario.steps.foreach(v => lines += s"          - $v")
            }
            if (scenario.alternates.nonEmpty) {
              lines += "        alternates:"
              scenario.alternates.foreach(v => lines += s"          - $v")
            }
            if (scenario.exceptions.nonEmpty) {
              lines += "        exceptions:"
              scenario.exceptions.foreach(v => lines += s"          - $v")
            }
          }
        }
      }
    }
    if (model.domainCapabilities.nonEmpty) {
      lines += "structuredDomainCapabilities:"
      model.domainCapabilities.foreach { capability =>
        lines += s"  - name: ${capability.name}"
        capability.summary.foreach(v => lines += s"    summary: $v")
        capability.actor.foreach(v => lines += s"    actor: $v")
        capability.primaryActor.foreach(v => lines += s"    primaryActor: $v")
        capability.secondaryActor.foreach(v => lines += s"    secondaryActor: $v")
        capability.supportingActor.foreach(v => lines += s"    supportingActor: $v")
        capability.stakeholder.foreach(v => lines += s"    stakeholder: $v")
        capability.goal.foreach(v => lines += s"    goal: $v")
        capability.precondition.foreach(v => lines += s"    precondition: $v")
        capability.postcondition.foreach(v => lines += s"    postcondition: $v")
      }
    }
    if (model.domainVisions.nonEmpty) {
      lines += "structuredDomainVisions:"
      model.domainVisions.foreach { vision =>
        lines += s"  - name: ${vision.name}"
        vision.summary.foreach(v => lines += s"    summary: $v")
        vision.goal.foreach(v => lines += s"    goal: $v")
        vision.precondition.foreach(v => lines += s"    precondition: $v")
        vision.postcondition.foreach(v => lines += s"    postcondition: $v")
      }
    }
    if (model.domainContexts.nonEmpty) {
      lines += "structuredDomainContexts:"
      model.domainContexts.foreach { context =>
        lines += s"  - name: ${context.name}"
        context.summary.foreach(v => lines += s"    summary: $v")
        context.description.foreach(v => lines += s"    description: $v")
      }
    }
    if (model.domainSystemContexts.nonEmpty) {
      lines += "structuredDomainSystemContexts:"
      model.domainSystemContexts.foreach { context =>
        lines += s"  - name: ${context.name}"
        context.summary.foreach(v => lines += s"    summary: $v")
        context.description.foreach(v => lines += s"    description: $v")
      }
    }
    if (model.domainContextMaps.nonEmpty) {
      lines += "structuredDomainContextMaps:"
      model.domainContextMaps.foreach { context =>
        lines += s"  - name: ${context.name}"
        context.summary.foreach(v => lines += s"    summary: $v")
        context.description.foreach(v => lines += s"    description: $v")
      }
    }
    if (model.domainQualities.nonEmpty) {
      lines += "structuredDomainQualities:"
      model.domainQualities.foreach { quality =>
        lines += s"  - name: ${quality.name}"
        quality.summary.foreach(v => lines += s"    summary: $v")
        quality.goal.foreach(v => lines += s"    goal: $v")
        quality.precondition.foreach(v => lines += s"    precondition: $v")
        quality.postcondition.foreach(v => lines += s"    postcondition: $v")
      }
    }
    if (model.domainConstraints.nonEmpty) {
      lines += "structuredDomainConstraints:"
      model.domainConstraints.foreach { constraint =>
        lines += s"  - name: ${constraint.name}"
        constraint.summary.foreach(v => lines += s"    summary: $v")
        constraint.goal.foreach(v => lines += s"    goal: $v")
        constraint.precondition.foreach(v => lines += s"    precondition: $v")
        constraint.postcondition.foreach(v => lines += s"    postcondition: $v")
      }
    }
    if (model.usage.nonEmpty) {
      lines += "usage:"
      model.usage.foreach(v => lines += s"  - $v")
    }
    lines.result().mkString("\n")
  }
}
