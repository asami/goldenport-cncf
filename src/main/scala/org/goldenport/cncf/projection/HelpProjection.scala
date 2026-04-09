package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.projection.model.{HelpCapabilityModel, HelpConstraintModel, HelpContextMapModel, HelpContextModel, HelpModel, HelpQualityModel, HelpSelectorModel, HelpSystemContextModel, HelpUseCaseModel, HelpUseCaseScenarioModel, HelpVisionModel}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}
import org.goldenport.datatype.I18nString

/*
 * @since   Mar.  5, 2026
 *  version Mar. 28, 2026
 * @version Apr.  9, 2026
 * @author  ASAMI, Tomoharu
 */
object HelpProjection {
  import MetaProjectionSupport._

  def projectModel(base: Component, selector: Option[String] = None): HelpModel =
    resolve(base, selector) match {
      case Target.Subsystem(components, name) =>
        val effectiveName = _subsystem_effective_name(components, name)
        val domainVisionModels = _subsystem_domain_vision_models(components, effectiveName)
        val domainContextModels = _subsystem_domain_context_models(components, effectiveName)
        val domainSystemContextModels = _subsystem_domain_system_context_models(components, effectiveName)
        val domainContextMapModels = _subsystem_domain_context_map_models(components, effectiveName)
        val domainCapabilityModels = _subsystem_domain_capability_models(components, effectiveName)
        val domainQualityModels = _subsystem_domain_quality_models(components, effectiveName)
        val domainConstraintModels = _subsystem_domain_constraint_models(components, effectiveName)
        val domainUseCaseModels = _subsystem_domain_use_case_models(components, effectiveName)
        val domainVisions = domainVisionModels.flatMap(_render_vision)
        val domainContexts = domainContextModels.flatMap(_render_context)
        val domainSystemContexts = domainSystemContextModels.flatMap(_render_system_context)
        val domainContextMaps = domainContextMapModels.flatMap(_render_context_map)
        val domainCapabilities = domainCapabilityModels.flatMap(_render_capability)
        val domainQualities = domainQualityModels.flatMap(_render_quality)
        val domainConstraints = domainConstraintModels.flatMap(_render_constraint)
        val domainUseCases = domainUseCaseModels.flatMap(_render_use_case)
        HelpModel(
          `type` = "subsystem",
          name = effectiveName,
          summary = "Subsystem help",
          selector = Some(_subsystem_selector(effectiveName)),
          children = components.map(_.name),
          details = Map("components" -> components.map(_.name)) ++
            (if (domainVisions.nonEmpty) Map("domainVisions" -> domainVisions) else Map.empty) ++
            (if (domainContexts.nonEmpty) Map("domainContexts" -> domainContexts) else Map.empty) ++
            (if (domainSystemContexts.nonEmpty) Map("domainSystemContexts" -> domainSystemContexts) else Map.empty) ++
            (if (domainContextMaps.nonEmpty) Map("domainContextMaps" -> domainContextMaps) else Map.empty) ++
            (if (domainCapabilities.nonEmpty) Map("domainCapabilities" -> domainCapabilities) else Map.empty) ++
            (if (domainQualities.nonEmpty) Map("domainQualities" -> domainQualities) else Map.empty) ++
            (if (domainConstraints.nonEmpty) Map("domainConstraints" -> domainConstraints) else Map.empty) ++
            (if (domainUseCases.nonEmpty) Map("domainUseCases" -> domainUseCases) else Map.empty),
          usage = Vector("command meta.help <component>"),
          domainVisions = domainVisionModels,
          domainContexts = domainContextModels,
          domainSystemContexts = domainSystemContextModels,
          domainContextMaps = domainContextMapModels,
          domainCapabilities = domainCapabilityModels,
          domainQualities = domainQualityModels,
          domainConstraints = domainConstraintModels,
          domainUseCases = domainUseCaseModels
        )
      case Target.ComponentTarget(component) =>
        val componentName = component.name
        val services = component.protocol.services.services.sortBy(_.name)
        val aggregates = aggregateMetas(component).map(_.name)
        val views = viewMetas(component).map(_.name)
        val operations = operationMetas(component).map(_.name)
        val useCaseModels = _component_use_case_models(component)
        val useCases = useCaseModels.flatMap(_render_use_case)
        val artifactName = component.artifactMetadata.map(_.name).toVector
        val artifactVersion = component.artifactMetadata.map(_.version).toVector
        HelpModel(
          `type` = "component",
          name = componentName,
          summary = s"Component: $componentName",
          selector = Some(_component_selector(componentName)),
          children = services.map(_.name),
          details = Map(
            "services" -> services.map(_.name),
            "aggregates" -> aggregates,
            "views" -> views,
            "operationDefinitions" -> operations,
            "origin" -> Vector(component.origin.label),
            "artifactName" -> artifactName,
            "artifactVersion" -> artifactVersion
          ) ++ (if (useCases.nonEmpty) Map("useCases" -> useCases) else Map.empty),
          usage = services.headOption.map(s => Vector(s"command help $componentName.${s.name}")).getOrElse(Vector.empty),
          useCases = useCaseModels,
        )
      case Target.ServiceTarget(component, service) =>
        val componentName = component.name
        val serviceName = service.name
        val operations = service.operations.operations.toVector.sortBy(_.name)
        val summary = _service_summary(service).getOrElse(s"Service: ${service.name}")
        val useCaseModels = _service_use_case_models(service)
        val useCases = useCaseModels.flatMap(_render_use_case)
        HelpModel(
          `type` = "service",
          name = serviceName,
          summary = summary,
          component = Some(componentName),
          selector = Some(_service_selector(componentName, serviceName)),
          children = operations.map(_.name),
          details = Map("operations" -> operations.map(_.name)) ++ (if (useCases.nonEmpty) Map("useCases" -> useCases) else Map.empty),
          usage = operations.headOption.map(op => Vector(s"command help ${_service_cli_selector(componentName, serviceName)}.${NamingConventions.toNormalizedSegment(op.name)}")).getOrElse(Vector.empty),
          useCases = useCaseModels
        )
      case Target.OperationTarget(component, service, operation) =>
        val componentName = component.name
        val serviceName = service.name
        val operationName = operation.name
        val args = operation.specification.request.parameters.toVector.map(_.name)
        val returns = render_operation_returns(operation)
        val summary = _operation_summary(service, operation).getOrElse(s"Operation: ${service.name}.${operation.name}")
        val descriptionDetails = _trim_i18n(operation.specification.description).fold(Map.empty[String, Vector[String]])(x => Map("description" -> Vector(x)))
        HelpModel(
          `type` = "operation",
          name = operationName,
          summary = summary,
          component = Some(componentName),
          service = Some(serviceName),
          selector = Some(_operation_selector(componentName, serviceName, operationName)),
          children = Vector.empty,
          details = Map(
            "arguments" -> args,
            "returns" -> Vector(returns)
          ) ++ descriptionDetails,
          usage = Vector(s"command ${_operation_cli_selector(componentName, serviceName, operationName)}")
        )
      case Target.NotFound(target) =>
        HelpModel(
          `type` = "error",
          name = target.getOrElse("unknown"),
          summary = "target not found"
        )
    }

  def project(base: Component, selector: Option[String] = None): Record = {
    val model = projectModel(base, selector)
    val details = Record.create(model.details.toVector.map { case (k, v) => k -> v })
    Record.data(
      "type" -> model.`type`,
      "name" -> model.name,
      "summary" -> model.summary,
      "component" -> model.component,
      "service" -> model.service,
      "selector" -> model.selector.map { x =>
        Record.data(
          "canonical" -> x.canonical,
          "cli" -> x.cli,
          "rest" -> x.rest,
          "accepted" -> x.accepted
        )
      },
      "children" -> model.children,
      "details" -> details,
      "usage" -> model.usage,
      "domainContexts" -> model.domainContexts.map(_context_record),
      "domainSystemContexts" -> model.domainSystemContexts.map(_system_context_record),
      "domainContextMaps" -> model.domainContextMaps.map(_context_map_record),
      "useCases" -> model.useCases.map(_use_case_record)
    )
  }


  private def _trim_string(p: Option[String]): Option[String] =
    p.map(_.trim).filter(_.nonEmpty)

  private def _trim_i18n(p: Option[I18nString]): Option[String] =
    p.map(_.displayMessage.trim).filter(_.nonEmpty)

  private def _clean_opt(p: Option[String]): Option[String] =
    p.map(_.trim).filter(s => s.nonEmpty && s != "None")

  private def _service_summary(service: ServiceDefinition): Option[String] =
    _trim_i18n(service.specification.summary).orElse(_trim_i18n(service.specification.description))

  private def _component_use_case_models(component: Component): Vector[HelpUseCaseModel] =
    component.componentDefinitionRecords.headOption.toVector.flatMap { p =>
      p.fields.find(_.key == "use_cases").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_use_case(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_use_case(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _component_domain_vision_models(component: Component): Vector[HelpVisionModel] =
    component.componentDefinitionRecords.headOption.toVector.flatMap { p =>
      p.fields.find(_.key == "domain_visions").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_vision(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_vision(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _component_domain_capability_models(component: Component): Vector[HelpCapabilityModel] =
    component.componentDefinitionRecords.headOption.toVector.flatMap { p =>
      p.fields.find(_.key == "domain_capabilities").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_capability(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_capability(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _component_domain_use_case_models(component: Component): Vector[HelpUseCaseModel] =
    component.componentDefinitionRecords.headOption.toVector.flatMap { p =>
      p.fields.find(_.key == "domain_use_cases").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_use_case(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_use_case(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _component_domain_quality_models(component: Component): Vector[HelpQualityModel] =
    component.componentDefinitionRecords.headOption.toVector.flatMap { p =>
      p.fields.find(_.key == "domain_qualities").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_quality(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_quality(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _component_domain_constraint_models(component: Component): Vector[HelpConstraintModel] =
    component.componentDefinitionRecords.headOption.toVector.flatMap { p =>
      p.fields.find(_.key == "domain_constraints").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_constraint(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_constraint(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _subsystem_definition_record(
    components: Vector[Component],
    name: String
  ): Option[Record] = {
    val records = components.flatMap(_.subsystemDefinitionRecords)
    records.find(r => r.getString("name").contains(name)).orElse(records.headOption)
  }

  private def _subsystem_effective_name(
    components: Vector[Component],
    runtimeSubsystemName: String
  ): String =
    Option(runtimeSubsystemName).map(_.trim).filter(_.nonEmpty).
      orElse(
        _subsystem_definition_record(components, runtimeSubsystemName).
      flatMap(_.getString("name")).
      orElse(components.flatMap(_.artifactMetadata.flatMap(_.subsystem)).headOption).
      orElse(components match {
        case Vector(single) => Some(single.name)
        case _ => None
      })
      ).
      getOrElse(runtimeSubsystemName)

  private def _subsystem_domain_vision_models(
    components: Vector[Component],
    name: String
  ): Vector[HelpVisionModel] =
    _subsystem_definition_record(components, name).toVector.flatMap { p =>
      p.fields.find(_.key == "domain_visions").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_vision(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_vision(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _subsystem_domain_capability_models(
    components: Vector[Component],
    name: String
  ): Vector[HelpCapabilityModel] =
    _subsystem_definition_record(components, name).toVector.flatMap { p =>
      p.fields.find(_.key == "domain_capabilities").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_capability(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_capability(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _subsystem_domain_context_models(
    components: Vector[Component],
    name: String
  ): Vector[HelpContextModel] =
    _subsystem_definition_record(components, name).toVector.flatMap { p =>
      p.fields.find(_.key == "domain_contexts").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_context(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_context(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _subsystem_domain_system_context_models(
    components: Vector[Component],
    name: String
  ): Vector[HelpSystemContextModel] =
    _subsystem_definition_record(components, name).toVector.flatMap { p =>
      p.fields.find(_.key == "domain_system_contexts").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_system_context(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_system_context(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _subsystem_domain_context_map_models(
    components: Vector[Component],
    name: String
  ): Vector[HelpContextMapModel] =
    _subsystem_definition_record(components, name).toVector.flatMap { p =>
      p.fields.find(_.key == "domain_context_maps").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_context_map(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_context_map(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _subsystem_domain_use_case_models(
    components: Vector[Component],
    name: String
  ): Vector[HelpUseCaseModel] =
    _subsystem_definition_record(components, name).toVector.flatMap { p =>
      p.fields.find(_.key == "domain_use_cases").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_use_case(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_use_case(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _subsystem_domain_quality_models(
    components: Vector[Component],
    name: String
  ): Vector[HelpQualityModel] =
    _subsystem_definition_record(components, name).toVector.flatMap { p =>
      p.fields.find(_.key == "domain_qualities").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_quality(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_quality(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _subsystem_domain_constraint_models(
    components: Vector[Component],
    name: String
  ): Vector[HelpConstraintModel] =
    _subsystem_definition_record(components, name).toVector.flatMap { p =>
      p.fields.find(_.key == "domain_constraints").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_constraint(r) }
        case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_constraint(r) }.toVector
        case _ => Vector.empty
      }
    }

  private def _service_use_case_models(service: ServiceDefinition): Vector[HelpUseCaseModel] =
    _service_use_case_records(service).map(_to_use_case)

  private def _service_use_case_records(service: ServiceDefinition): Vector[Record] =
    try {
      val method = service.getClass.getMethod("useCaseRecords")
      method.invoke(service) match {
        case xs: Vector[?] => xs.collect { case r: Record => r }
        case xs: Seq[?] => xs.collect { case r: Record => r }.toVector
        case _ => Vector.empty
      }
    } catch {
      case _: NoSuchMethodException => Vector.empty
      case _: Throwable => Vector.empty
    }

  private def _render_use_case(p: HelpUseCaseModel): Vector[String] = {
    val name = p.name
    val summary = p.summary.filter(_.nonEmpty)
    val actor = p.primaryActor.orElse(p.actor)
    val goal = p.goal.filter(_.nonEmpty)
    val precondition = p.precondition.filter(_.nonEmpty)
    val postcondition = p.postcondition.filter(_.nonEmpty)
    Vector(
      Some(summary.map(x => s"${name}: ${x}").getOrElse(name)),
      actor.map(x => s"  actor: ${x}"),
      goal.map(x => s"  goal: ${x}"),
      precondition.map(x => s"  precondition: ${x}"),
      postcondition.map(x => s"  postcondition: ${x}")
    ).flatten
  }

  private def _to_use_case(p: Record): HelpUseCaseModel =
    HelpUseCaseModel(
      name = p.getString("name").getOrElse("use_case"),
      summary = _clean_opt(p.getString("summary")),
      actor = _clean_opt(p.getString("actor")),
      primaryActor = _clean_opt(p.getString("primary_actor")),
      secondaryActor = _clean_opt(p.getString("secondary_actor")),
      supportingActor = _clean_opt(p.getString("supporting_actor")),
      stakeholder = _clean_opt(p.getString("stakeholder")),
      goal = _clean_opt(p.getString("goal")),
      precondition = _clean_opt(p.getString("precondition")),
      postcondition = _clean_opt(p.getString("postcondition")),
      scenarios = _take_scenarios(p)
    )

  private def _take_scenarios(p: Record): Vector[HelpUseCaseScenarioModel] =
    p.fields.find(_.key == "scenarios").map(_.value.single) match {
      case Some(xs: Vector[?]) => xs.collect { case r: Record => _to_use_case_scenario(r) }
      case Some(xs: Seq[?]) => xs.collect { case r: Record => _to_use_case_scenario(r) }.toVector
      case _ => Vector.empty
    }

  private def _to_use_case_scenario(p: Record): HelpUseCaseScenarioModel =
    HelpUseCaseScenarioModel(
      name = p.getString("name").getOrElse("scenario"),
      summary = _clean_opt(p.getString("summary")),
      description = _clean_opt(p.getString("description")),
      steps = p.fields.find(_.key == "steps").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case s: String => s }
        case Some(xs: Seq[?]) => xs.collect { case s: String => s }.toVector
        case Some(s: String) => Vector(s)
        case _ => Vector.empty
      },
      alternates = p.fields.find(_.key == "alternates").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case s: String => s }
        case Some(xs: Seq[?]) => xs.collect { case s: String => s }.toVector
        case Some(s: String) => Vector(s)
        case _ => Vector.empty
      },
      exceptions = p.fields.find(_.key == "exceptions").map(_.value.single) match {
        case Some(xs: Vector[?]) => xs.collect { case s: String => s }
        case Some(xs: Seq[?]) => xs.collect { case s: String => s }.toVector
        case Some(s: String) => Vector(s)
        case _ => Vector.empty
      }
    )

  private def _use_case_record(p: HelpUseCaseModel): Record =
    Record.data(
      "name" -> p.name,
      "summary" -> p.summary,
      "actor" -> p.actor,
      "primaryActor" -> p.primaryActor,
      "secondaryActor" -> p.secondaryActor,
      "supportingActor" -> p.supportingActor,
      "stakeholder" -> p.stakeholder,
      "goal" -> p.goal,
      "precondition" -> p.precondition,
      "postcondition" -> p.postcondition,
      "scenarios" -> p.scenarios.map(_use_case_scenario_record)
    )

  private def _context_record(p: HelpContextModel): Record =
    Record.data(
      "name" -> p.name,
      "summary" -> p.summary,
      "description" -> p.description
    )

  private def _system_context_record(p: HelpSystemContextModel): Record =
    Record.data(
      "name" -> p.name,
      "summary" -> p.summary,
      "description" -> p.description
    )

  private def _context_map_record(p: HelpContextMapModel): Record =
    Record.data(
      "name" -> p.name,
      "summary" -> p.summary,
      "description" -> p.description
    )

  private def _use_case_scenario_record(p: HelpUseCaseScenarioModel): Record =
    Record.data(
      "name" -> p.name,
      "summary" -> p.summary,
      "description" -> p.description,
      "steps" -> p.steps,
      "alternates" -> p.alternates,
      "exceptions" -> p.exceptions
    )

  private def _operation_summary(service: ServiceDefinition, operation: OperationDefinition): Option[String] =
    _trim_i18n(operation.specification.summary).
      orElse(_trim_i18n(operation.specification.description)).
      orElse(_service_summary(service))

  private def _subsystem_selector(name: String): HelpSelectorModel = {
    val cli = NamingConventions.toNormalizedSegment(name)
    HelpSelectorModel(
      canonical = name,
      cli = cli,
      rest = s"/$cli",
      accepted = Vector(name)
    )
  }

  private def _component_selector(componentName: String): HelpSelectorModel = {
    val cli = NamingConventions.toNormalizedSegment(componentName)
    HelpSelectorModel(
      canonical = componentName,
      cli = cli,
      rest = s"/$cli",
      accepted = Vector(componentName)
    )
  }

  private def _service_selector(componentName: String, serviceName: String): HelpSelectorModel = {
    val canonical = s"$componentName.$serviceName"
    val cliComponent = NamingConventions.toNormalizedSegment(componentName)
    val cliService = NamingConventions.toNormalizedSegment(serviceName)
    HelpSelectorModel(
      canonical = canonical,
      cli = s"$cliComponent.$cliService",
      rest = s"/$cliComponent/$cliService",
      accepted = Vector(canonical)
    )
  }

  private def _operation_selector(componentName: String, serviceName: String, operationName: String): HelpSelectorModel = {
    val canonical = s"$componentName.$serviceName.$operationName"
    val cliComponent = NamingConventions.toNormalizedSegment(componentName)
    val cliService = NamingConventions.toNormalizedSegment(serviceName)
    val cliOperation = NamingConventions.toNormalizedSegment(operationName)
    HelpSelectorModel(
      canonical = canonical,
      cli = s"$cliComponent.$cliService.$cliOperation",
      rest = s"/$cliComponent/$cliService/$cliOperation",
      accepted = Vector(canonical)
    )
  }

  private def _service_cli_selector(componentName: String, serviceName: String): String =
    s"${NamingConventions.toNormalizedSegment(componentName)}.${NamingConventions.toNormalizedSegment(serviceName)}"

  private def _operation_cli_selector(componentName: String, serviceName: String, operationName: String): String =
    s"${NamingConventions.toNormalizedSegment(componentName)}.${NamingConventions.toNormalizedSegment(serviceName)}.${NamingConventions.toNormalizedSegment(operationName)}"

  private def _render_capability(p: HelpCapabilityModel): Vector[String] = {
    val name = p.name
    val summary = p.summary.filter(_.nonEmpty)
    val actor = p.primaryActor.orElse(p.actor)
    val goal = p.goal.filter(_.nonEmpty)
    val precondition = p.precondition.filter(_.nonEmpty)
    val postcondition = p.postcondition.filter(_.nonEmpty)
    Vector(
      Some(summary.map(x => s"${name}: ${x}").getOrElse(name)),
      actor.map(x => s"  actor: ${x}"),
      goal.map(x => s"  goal: ${x}"),
      precondition.map(x => s"  precondition: ${x}"),
      postcondition.map(x => s"  postcondition: ${x}")
    ).flatten
  }

  private def _render_vision(p: HelpVisionModel): Vector[String] = {
    val summary = p.summary.filter(_.nonEmpty)
    val goal = p.goal.filter(_.nonEmpty)
    val precondition = p.precondition.filter(_.nonEmpty)
    val postcondition = p.postcondition.filter(_.nonEmpty)
    Vector(
      Some(summary.map(x => s"${p.name}: ${x}").getOrElse(p.name)),
      goal.map(x => s"  goal: ${x}"),
      precondition.map(x => s"  precondition: ${x}"),
      postcondition.map(x => s"  postcondition: ${x}")
    ).flatten
  }

  private def _render_quality(p: HelpQualityModel): Vector[String] = {
    val summary = p.summary.filter(_.nonEmpty)
    val goal = p.goal.filter(_.nonEmpty)
    val precondition = p.precondition.filter(_.nonEmpty)
    val postcondition = p.postcondition.filter(_.nonEmpty)
    Vector(
      Some(summary.map(x => s"${p.name}: ${x}").getOrElse(p.name)),
      goal.map(x => s"  goal: ${x}"),
      precondition.map(x => s"  precondition: ${x}"),
      postcondition.map(x => s"  postcondition: ${x}")
    ).flatten
  }

  private def _render_constraint(p: HelpConstraintModel): Vector[String] = {
    val summary = p.summary.filter(_.nonEmpty)
    val goal = p.goal.filter(_.nonEmpty)
    val precondition = p.precondition.filter(_.nonEmpty)
    val postcondition = p.postcondition.filter(_.nonEmpty)
    Vector(
      Some(summary.map(x => s"${p.name}: ${x}").getOrElse(p.name)),
      goal.map(x => s"  goal: ${x}"),
      precondition.map(x => s"  precondition: ${x}"),
      postcondition.map(x => s"  postcondition: ${x}")
    ).flatten
  }

  private def _render_context(p: HelpContextModel): Vector[String] = {
    val summary = p.summary.filter(_.nonEmpty)
    val description = p.description.filter(_.nonEmpty)
    Vector(
      Some(summary.map(x => s"${p.name}: ${x}").getOrElse(p.name)),
      description.map(x => s"  description: ${x}")
    ).flatten
  }

  private def _render_system_context(p: HelpSystemContextModel): Vector[String] = {
    val summary = p.summary.filter(_.nonEmpty)
    val description = p.description.filter(_.nonEmpty)
    Vector(
      Some(summary.map(x => s"${p.name}: ${x}").getOrElse(p.name)),
      description.map(x => s"  description: ${x}")
    ).flatten
  }

  private def _render_context_map(p: HelpContextMapModel): Vector[String] = {
    val summary = p.summary.filter(_.nonEmpty)
    val description = p.description.filter(_.nonEmpty)
    Vector(
      Some(summary.map(x => s"${p.name}: ${x}").getOrElse(p.name)),
      description.map(x => s"  description: ${x}")
    ).flatten
  }

  private def _to_capability(p: Record): HelpCapabilityModel =
    HelpCapabilityModel(
      name = p.getString("name").getOrElse("capability"),
      summary = _clean_opt(p.getString("summary")),
      actor = _clean_opt(p.getString("actor")),
      primaryActor = _clean_opt(p.getString("primary_actor")),
      secondaryActor = _clean_opt(p.getString("secondary_actor")),
      supportingActor = _clean_opt(p.getString("supporting_actor")),
      stakeholder = _clean_opt(p.getString("stakeholder")),
      goal = _clean_opt(p.getString("goal")),
      precondition = _clean_opt(p.getString("precondition")),
      postcondition = _clean_opt(p.getString("postcondition"))
    )

  private def _to_context(p: Record): HelpContextModel =
    HelpContextModel(
      name = p.getString("name").getOrElse("context"),
      summary = _clean_opt(p.getString("summary")),
      description = _clean_opt(p.getString("description"))
    )

  private def _to_system_context(p: Record): HelpSystemContextModel =
    HelpSystemContextModel(
      name = p.getString("name").getOrElse("system_context"),
      summary = _clean_opt(p.getString("summary")),
      description = _clean_opt(p.getString("description"))
    )

  private def _to_context_map(p: Record): HelpContextMapModel =
    HelpContextMapModel(
      name = p.getString("name").getOrElse("context_map"),
      summary = _clean_opt(p.getString("summary")),
      description = _clean_opt(p.getString("description"))
    )

  private def _to_vision(p: Record): HelpVisionModel =
    HelpVisionModel(
      name = p.getString("name").getOrElse("vision"),
      summary = _clean_opt(p.getString("summary")),
      goal = _clean_opt(p.getString("goal")),
      precondition = _clean_opt(p.getString("precondition")),
      postcondition = _clean_opt(p.getString("postcondition"))
    )

  private def _to_quality(p: Record): HelpQualityModel =
    HelpQualityModel(
      name = p.getString("name").getOrElse("quality"),
      summary = _clean_opt(p.getString("summary")),
      goal = _clean_opt(p.getString("goal")),
      precondition = _clean_opt(p.getString("precondition")),
      postcondition = _clean_opt(p.getString("postcondition"))
    )

  private def _to_constraint(p: Record): HelpConstraintModel =
    HelpConstraintModel(
      name = p.getString("name").getOrElse("constraint"),
      summary = _clean_opt(p.getString("summary")),
      goal = _clean_opt(p.getString("goal")),
      precondition = _clean_opt(p.getString("precondition")),
      postcondition = _clean_opt(p.getString("postcondition"))
    )
}
