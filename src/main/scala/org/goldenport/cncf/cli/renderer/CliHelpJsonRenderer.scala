package org.goldenport.cncf.cli.renderer

import org.goldenport.cncf.projection.model.{HelpCapabilityModel, HelpConstraintModel, HelpContextMapModel, HelpContextModel, HelpModel, HelpQualityModel, HelpSystemContextModel, HelpUseCaseModel, HelpUseCaseScenarioModel, HelpVisionModel}

/*
 * @since   Mar.  5, 2026
 *  version Mar. 28, 2026
 * @version Apr.  6, 2026
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
    val domainVisions = model.domainVisions.map(_vision_json).mkString(",")
    val domainContexts = model.domainContexts.map(_context_json).mkString(",")
    val domainSystemContexts = model.domainSystemContexts.map(_system_context_json).mkString(",")
    val domainContextMaps = model.domainContextMaps.map(_context_map_json).mkString(",")
    val domainCapabilities = model.domainCapabilities.map(_capability_json).mkString(",")
    val domainQualities = model.domainQualities.map(_quality_json).mkString(",")
    val domainConstraints = model.domainConstraints.map(_constraint_json).mkString(",")
    val useCases = model.useCases.map(_use_case_json).mkString(",")
    val domainUseCases = model.domainUseCases.map(_use_case_json).mkString(",")
    val component = model.component.map(v => s""""${_escape(v)}"""").getOrElse("null")
    val service = model.service.map(v => s""""${_escape(v)}"""").getOrElse("null")
    val selector = model.selector.map { selector =>
      val accepted = selector.accepted.map(v => s""""${_escape(v)}"""").mkString(",")
      s"""{"canonical":"${_escape(selector.canonical)}","cli":"${_escape(selector.cli)}","rest":"${_escape(selector.rest)}","accepted":[${accepted}]}"""
    }.getOrElse("null")
    s"""{"type":"${_escape(model.`type`)}","name":"${_escape(model.name)}","summary":"${_escape(model.summary)}","component":${component},"service":${service},"selector":${selector},"children":[${children}],"details":{${details}},"usage":[${usage}],"domainVisions":[${domainVisions}],"domainContexts":[${domainContexts}],"domainSystemContexts":[${domainSystemContexts}],"domainContextMaps":[${domainContextMaps}],"domainCapabilities":[${domainCapabilities}],"domainQualities":[${domainQualities}],"domainConstraints":[${domainConstraints}],"useCases":[${useCases}],"domainUseCases":[${domainUseCases}]}"""
  }

  private def _vision_json(p: HelpVisionModel): String =
    s"""{"name":"${_escape(p.name)}","summary":${_opt(p.summary)},"goal":${_opt(p.goal)},"precondition":${_opt(p.precondition)},"postcondition":${_opt(p.postcondition)}}"""

  private def _capability_json(p: HelpCapabilityModel): String =
    s"""{"name":"${_escape(p.name)}","summary":${_opt(p.summary)},"actor":${_opt(p.actor)},"primaryActor":${_opt(p.primaryActor)},"secondaryActor":${_opt(p.secondaryActor)},"supportingActor":${_opt(p.supportingActor)},"stakeholder":${_opt(p.stakeholder)},"goal":${_opt(p.goal)},"precondition":${_opt(p.precondition)},"postcondition":${_opt(p.postcondition)}}"""

  private def _context_json(p: HelpContextModel): String =
    s"""{"name":"${_escape(p.name)}","summary":${_opt(p.summary)},"description":${_opt(p.description)}}"""

  private def _system_context_json(p: HelpSystemContextModel): String =
    s"""{"name":"${_escape(p.name)}","summary":${_opt(p.summary)},"description":${_opt(p.description)}}"""

  private def _context_map_json(p: HelpContextMapModel): String =
    s"""{"name":"${_escape(p.name)}","summary":${_opt(p.summary)},"description":${_opt(p.description)}}"""

  private def _quality_json(p: HelpQualityModel): String =
    s"""{"name":"${_escape(p.name)}","summary":${_opt(p.summary)},"goal":${_opt(p.goal)},"precondition":${_opt(p.precondition)},"postcondition":${_opt(p.postcondition)}}"""

  private def _constraint_json(p: HelpConstraintModel): String =
    s"""{"name":"${_escape(p.name)}","summary":${_opt(p.summary)},"goal":${_opt(p.goal)},"precondition":${_opt(p.precondition)},"postcondition":${_opt(p.postcondition)}}"""

  private def _use_case_json(p: HelpUseCaseModel): String = {
    val scenarios = p.scenarios.map(_use_case_scenario_json).mkString(",")
    s"""{"name":"${_escape(p.name)}","summary":${_opt(p.summary)},"actor":${_opt(p.actor)},"primaryActor":${_opt(p.primaryActor)},"secondaryActor":${_opt(p.secondaryActor)},"supportingActor":${_opt(p.supportingActor)},"stakeholder":${_opt(p.stakeholder)},"goal":${_opt(p.goal)},"precondition":${_opt(p.precondition)},"postcondition":${_opt(p.postcondition)},"scenarios":[${scenarios}]}"""
  }

  private def _use_case_scenario_json(p: HelpUseCaseScenarioModel): String = {
    val steps = p.steps.map(v => s""""${_escape(v)}"""").mkString(",")
    val alternates = p.alternates.map(v => s""""${_escape(v)}"""").mkString(",")
    val exceptions = p.exceptions.map(v => s""""${_escape(v)}"""").mkString(",")
    s"""{"name":"${_escape(p.name)}","summary":${_opt(p.summary)},"description":${_opt(p.description)},"steps":[${steps}],"alternates":[${alternates}],"exceptions":[${exceptions}]}"""
  }

  private def _opt(p: Option[String]): String = p.map(v => s""""${_escape(v)}"""").getOrElse("null")

  private def _escape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
