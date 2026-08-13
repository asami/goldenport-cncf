package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.component.{Component, ComponentIdentityCompatibilityAdapter}
import org.goldenport.cncf.projection.model.{HelpCapabilityModel, HelpConstraintModel, HelpContextMapModel, HelpContextModel, HelpModel, HelpQualityModel, HelpSelectorModel, HelpSystemContextModel, HelpUseCaseModel, HelpUseCaseScenarioModel, HelpVisionModel}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}
import org.goldenport.datatype.I18nString

/*
 * @since   Mar.  5, 2026
 *  version Mar. 28, 2026
 *  version Apr. 30, 2026
 *  version May. 31, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
object HelpProjection {
  import MetaProjectionSupport._

  def projectModel(base: Component, selector: Option[String] = None): HelpModel =
    resolve(base, selector, ComponentIdentityCompatibilityAdapter.Surface.HelpProjection) match {
      case Target.Subsystem(components, name) =>
        val effectivename = _subsystem_effective_name(components, name)
        val domainvisionmodels = _subsystem_domain_vision_models(components, effectivename)
        val domaincontextmodels = _subsystem_domain_context_models(components, effectivename)
        val domainsystemcontextmodels = _subsystem_domain_system_context_models(components, effectivename)
        val domaincontextmapmodels = _subsystem_domain_context_map_models(components, effectivename)
        val domaincapabilitymodels = _subsystem_domain_capability_models(components, effectivename)
        val domainqualitymodels = _subsystem_domain_quality_models(components, effectivename)
        val domainconstraintmodels = _subsystem_domain_constraint_models(components, effectivename)
        val domainusecasemodels = _subsystem_domain_use_case_models(components, effectivename)
        val domainvisions = domainvisionmodels.flatMap(_render_vision)
        val domaincontexts = domaincontextmodels.flatMap(_render_context)
        val domainsystemcontexts = domainsystemcontextmodels.flatMap(_render_system_context)
        val domaincontextmaps = domaincontextmapmodels.flatMap(_render_context_map)
        val domaincapabilities = domaincapabilitymodels.flatMap(_render_capability)
        val domainqualities = domainqualitymodels.flatMap(_render_quality)
        val domainconstraints = domainconstraintmodels.flatMap(_render_constraint)
        val domainusecases = domainusecasemodels.flatMap(_render_use_case)
        HelpModel(
          `type` = "subsystem",
          name = effectivename,
          summary = "Subsystem help",
          selector = Some(_subsystem_selector(effectivename)),
          children = components.map(_.displayName),
          details = Map("components" -> components.map(_.displayName)) ++
            (if (domainvisions.nonEmpty) Map("domainVisions" -> domainvisions) else Map.empty) ++
            (if (domaincontexts.nonEmpty) Map("domainContexts" -> domaincontexts) else Map.empty) ++
            (if (domainsystemcontexts.nonEmpty) Map("domainSystemContexts" -> domainsystemcontexts) else Map.empty) ++
            (if (domaincontextmaps.nonEmpty) Map("domainContextMaps" -> domaincontextmaps) else Map.empty) ++
            (if (domaincapabilities.nonEmpty) Map("domainCapabilities" -> domaincapabilities) else Map.empty) ++
            (if (domainqualities.nonEmpty) Map("domainQualities" -> domainqualities) else Map.empty) ++
            (if (domainconstraints.nonEmpty) Map("domainConstraints" -> domainconstraints) else Map.empty) ++
            (if (domainusecases.nonEmpty) Map("domainUseCases" -> domainusecases) else Map.empty),
          usage = Vector("command meta.help <component>"),
          domainVisions = domainvisionmodels,
          domainContexts = domaincontextmodels,
          domainSystemContexts = domainsystemcontextmodels,
          domainContextMaps = domaincontextmapmodels,
          domainCapabilities = domaincapabilitymodels,
          domainQualities = domainqualitymodels,
          domainConstraints = domainconstraintmodels,
          domainUseCases = domainusecasemodels
        )
      case Target.ComponentTarget(component) =>
        val componentname = component.displayName
        val componentid = component.componentId.name
        val isuniquedisplayalias = _is_unique_display_alias(component)
        val services = component.protocol.services.services.sortBy(_.name)
        val aggregates = aggregateMetas(component).map(_.name)
        val views = viewMetas(component).map(_.name)
        val operations = operationMetas(component).map(_.name)
        val updatecommands = component.operationDefinitions.flatMap { operation =>
          operation.parameters.flatMap { field =>
            field.update.toVector.flatMap(_.availableCommands.map(command => s"${operation.name}.${field.name}=$command"))
          }
        }.sorted
        val updatevaluecarriers = component.operationDefinitions.flatMap { operation =>
          operation.parameters.flatMap { field =>
            field.update.toVector.flatMap(_.availableValueCarriers.map(carrier => s"${operation.name}.${field.name}=$carrier"))
          }
        }.sorted
        val relationships = component.relationshipDefinitions.map(relationship_definition_record).sortBy(_.getString("name").getOrElse(""))
        val usecasemodels = _component_use_case_models(component)
        val usecases = usecasemodels.flatMap(_render_use_case)
        val artifactname = component.artifactMetadata.map(_.name).toVector
        val artifactversion = component.artifactMetadata.map(_.version).toVector
        HelpModel(
          `type` = "component",
          name = componentname,
          summary = s"Component: $componentname",
          componentId = Some(componentid),
          selector = Some(_component_selector(componentid, componentname, isuniquedisplayalias)),
          children = services.map(_.name),
          details = Map(
            "services" -> services.map(_.name),
            "aggregates" -> aggregates,
            "views" -> views,
            "relationshipDefinitions" -> relationships.flatMap(_.getString("name")),
            "operationDefinitions" -> operations,
            "updateCommands" -> updatecommands,
            "updateValueCarriers" -> updatevaluecarriers,
            "origin" -> Vector(user_origin_label(component.origin.label)),
            "artifactName" -> artifactname,
            "artifactVersion" -> artifactversion
          ) ++ (if (usecases.nonEmpty) Map("useCases" -> usecases) else Map.empty),
          relationshipDefinitions = relationships,
          usage = services.headOption.map(s => Vector(s"command help $componentid.${s.name}")).getOrElse(Vector.empty),
          useCases = usecasemodels,
        )
      case Target.ServiceTarget(component, service) =>
        val componentname = component.displayName
        val componentid = component.componentId.name
        val isuniquedisplayalias = _is_unique_display_alias(component)
        val servicename = service.name
        val operations = service.operations.operations.toVector.sortBy(_.name)
        val summary = _service_summary(service).getOrElse(s"Service: ${service.name}")
        val usecasemodels = _service_use_case_models(service)
        val usecases = usecasemodels.flatMap(_render_use_case)
        HelpModel(
          `type` = "service",
          name = servicename,
          summary = summary,
          componentId = Some(componentid),
          component = Some(componentname),
          selector = Some(_service_selector(componentid, componentname, servicename, isuniquedisplayalias)),
          children = operations.map(_.name),
          details = Map("operations" -> operations.map(_.name)) ++ (if (usecases.nonEmpty) Map("useCases" -> usecases) else Map.empty),
          usage = operations.headOption.map { op =>
            Vector(s"command help $componentid.$servicename.${op.name}")
          }.getOrElse(Vector.empty),
          useCases = usecasemodels
        )
      case Target.OperationTarget(component, service, operation) =>
        val componentname = component.displayName
        val componentid = component.componentId.name
        val isuniquedisplayalias = _is_unique_display_alias(component)
        val servicename = service.name
        val operationname = operation.name
        val parameters = operation.specification.request.parameters.toVector
        val args = parameters.map(_.name)
        val argumentdetails = parameters.map(_argument_detail)
        val returns = render_operation_returns(operation)
        val summary = _operation_summary(service, operation).getOrElse(s"Operation: ${service.name}.${operation.name}")
        val descriptiondetails = _trim_i18n(operation.specification.description).fold(Map.empty[String, Vector[String]])(x => Map("description" -> Vector(x)))
        val childentitybindings = operation_child_entity_bindings(component, operation).map(child_entity_binding_record)
        val associationbinding = operation_association_binding(component, operation).map(association_binding_record)
        val imagebinding = operation_image_binding(component, operation).map(image_binding_record)
        val commandexecution = _command_execution_record(component, operation)
        val evaluation = component.operationDefinitions
          .find(x => NamingConventions.equivalentByNormalized(x.name, operation.name))
          .flatMap(_.evaluation)
          .map(_.toRecord)
        val updatecommands = component.operationDefinitions
          .find(x => NamingConventions.equivalentByNormalized(x.name, operation.name))
          .toVector
          .flatMap(_.parameters)
          .flatMap(field => field.update.toVector.flatMap(_.availableCommands.map(command => s"${field.name}=$command")))
        val updatevaluecarriers = component.operationDefinitions
          .find(x => NamingConventions.equivalentByNormalized(x.name, operation.name))
          .toVector
          .flatMap(_.parameters)
          .flatMap(field => field.update.toVector.flatMap(_.availableValueCarriers.map(carrier => s"${field.name}=$carrier")))
        HelpModel(
          `type` = "operation",
          name = operationname,
          summary = summary,
          componentId = Some(componentid),
          component = Some(componentname),
          service = Some(servicename),
          selector = Some(_operation_selector(componentid, componentname, servicename, operationname, isuniquedisplayalias)),
          children = Vector.empty,
          details = Map(
            "arguments" -> args,
            "argumentDetails" -> argumentdetails,
            "returns" -> Vector(returns),
            "updateCommands" -> updatecommands,
            "updateValueCarriers" -> updatevaluecarriers
          ) ++ descriptiondetails,
          childEntityBindings = childentitybindings,
          associationBinding = associationbinding,
          imageBinding = imagebinding,
          commandExecution = commandexecution,
          evaluation = evaluation,
          usage = Vector(
            s"command $componentid.$servicename.$operationname"
          )
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
      "componentId" -> model.componentId,
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
      "relationshipDefinitions" -> model.relationshipDefinitions,
      "childEntityBindings" -> model.childEntityBindings,
      "associationBinding" -> model.associationBinding,
      "imageBinding" -> model.imageBinding,
      "commandExecution" -> model.commandExecution,
      "evaluation" -> model.evaluation,
      "domainContexts" -> model.domainContexts.map(_context_record),
      "domainSystemContexts" -> model.domainSystemContexts.map(_system_context_record),
      "domainContextMaps" -> model.domainContextMaps.map(_context_map_record),
      "useCases" -> model.useCases.map(_use_case_record)
    )
  }


  private def _trim_string(p: Option[String]): Option[String] =
    p.map(_.trim).filter(_.nonEmpty)

  private def _command_execution_record(
    component: Component,
    operation: OperationDefinition
  ): Option[Record] =
    component.operationDefinitions
      .find(x => NamingConventions.equivalentByNormalized(x.name, operation.name))
      .map { x =>
        Record.data(
          "commandKind" -> x.commandKind,
          "commandExecutionProperties" -> x.commandExecutionProperties,
          "commandExecutionPolicy" -> x.commandExecutionPolicyRecord,
          "effectiveCommandExecutionMode" -> x.effectiveCommandExecutionPolicy.modeLabel,
          "jobDefinitionRef" -> x.jobDefinitionRef
        )
      }

  private def _trim_i18n(p: Option[I18nString]): Option[String] =
    p.map(_.displayMessage.trim).filter(_.nonEmpty)

  private def _argument_detail(
    p: org.goldenport.protocol.spec.ParameterDefinition
  ): String = {
    val validation = p.web.validation
    val constraints = Vector(
      validation.min.map(x => s"min=$x"),
      validation.max.map(x => s"max=$x"),
      validation.step.map(x => s"step=$x"),
      validation.minLength.map(x => s"min-length=$x"),
      validation.maxLength.map(x => s"max-length=$x"),
      validation.pattern.map(x => s"pattern=$x")
    ).flatten
    val suffix = if (constraints.isEmpty) "" else constraints.mkString(" [", ", ", "]")
    s"${p.name}: ${p.datatype.name} ${p.multiplicity.mark}$suffix"
  }

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
    runtimesubsystemname: String
  ): String =
    Option(runtimesubsystemname).map(_.trim).filter(_.nonEmpty).
      orElse(
        _subsystem_definition_record(components, runtimesubsystemname).
      flatMap(_.getString("name")).
      orElse(components.flatMap(_.artifactMetadata.flatMap(_.subsystem)).headOption).
      orElse(components match {
        case Vector(single) => Some(single.name)
        case _ => None
      })
      ).
      getOrElse(runtimesubsystemname)

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

  private def _is_unique_display_alias(component: Component): Boolean =
    ComponentIdentityCompatibilityAdapter.resolveAliases(
      component.displayName,
      ComponentIdentityCompatibilityAdapter.runtimeAliasCandidates(components(component)),
      ComponentIdentityCompatibilityAdapter.Surface.HelpProjection
    ) match {
      case result: ComponentIdentityCompatibilityAdapter.Canonical => result.componentid == component.componentId
      case result: ComponentIdentityCompatibilityAdapter.Adapted => result.componentid == component.componentId
      case _: ComponentIdentityCompatibilityAdapter.Rejected => false
    }

  private def _component_selector(
    componentid: String,
    componentname: String,
    includedisplayalias: Boolean
  ): HelpSelectorModel = {
    val cli = NamingConventions.toNormalizedSegment(componentname)
    HelpSelectorModel(
      canonical = componentid,
      cli = cli,
      rest = s"/$cli",
      accepted = Vector(componentid) ++ (if (includedisplayalias) Vector(componentname) else Vector.empty)
    )
  }

  private def _service_selector(
    componentid: String,
    componentname: String,
    servicename: String,
    includedisplayalias: Boolean
  ): HelpSelectorModel = {
    val canonical = s"$componentid.$servicename"
    val clicomponent = NamingConventions.toNormalizedSegment(componentname)
    val cliservice = NamingConventions.toNormalizedSegment(servicename)
    HelpSelectorModel(
      canonical = canonical,
      cli = s"$clicomponent.$cliservice",
      rest = s"/$clicomponent/$cliservice",
      accepted = Vector(canonical) ++ (if (includedisplayalias) Vector(s"$componentname.$servicename") else Vector.empty)
    )
  }

  private def _operation_selector(
    componentid: String,
    componentname: String,
    servicename: String,
    operationname: String,
    includedisplayalias: Boolean
  ): HelpSelectorModel = {
    val canonical = s"$componentid.$servicename.$operationname"
    val clicomponent = NamingConventions.toNormalizedSegment(componentname)
    val cliservice = NamingConventions.toNormalizedSegment(servicename)
    val clioperation = NamingConventions.toNormalizedSegment(operationname)
    HelpSelectorModel(
      canonical = canonical,
      cli = s"$clicomponent.$cliservice.$clioperation",
      rest = s"/$clicomponent/$cliservice/$clioperation",
      accepted = Vector(canonical) ++ (if (includedisplayalias) Vector(s"$componentname.$servicename.$operationname") else Vector.empty)
    )
  }

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
