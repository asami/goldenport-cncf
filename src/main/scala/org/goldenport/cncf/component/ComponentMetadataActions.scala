package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.cncf.component.identity.{
  ComponentId => SharedComponentId,
  ComponentIdentityResult,
  ComponentInstanceId => SharedComponentInstanceId
}
import org.goldenport.record.Record
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.logic.ProtocolLogic
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec.{OperationDefinition, ServiceDefinition}
import org.goldenport.protocol.spec.ServiceDefinitionGroup
import org.goldenport.protocol.service.{Service => ProtocolService}
// import org.goldenport.cncf.action.ActionLogic
import org.goldenport.protocol.handler.*
import org.goldenport.protocol.handler.ingress.*
import org.goldenport.protocol.handler.egress.*
import org.goldenport.protocol.handler.projection.*
import java.nio.file.Path
import scala.reflect.ClassTag
import org.goldenport.cncf.context.{CorrelationId, EntitySpaceContext, ExecutionContext, GlobalRuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, AggregateBehavior, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.configuration.{Configuration, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.http.{HttpDriver, WebMessageCatalog, WebPageContextProvider}
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterBootstrap, ComponentParameterKey, ComponentParameterPathRoute}
import org.goldenport.cncf.job.{InMemoryJobEngine, JobEngine}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.service.{Service, ServiceGroup}
import org.goldenport.cncf.receptor.{Receptor, ReceptorGroup}
import org.goldenport.cncf.cli.renderer.{CliTreeJsonRenderer, CliTreeYamlRenderer}
import org.goldenport.cncf.entity.aggregate.{AggregateCollection, AggregateEditContextSpace, AggregateSpace, Repository, AggregateDefinition}
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntitySpace}
import org.goldenport.cncf.entity.view.{Browser, ViewCollection, ViewSpace, ViewDefinition}
import org.goldenport.cncf.information.InformationSpace
import org.goldenport.cncf.knowledge.KnowledgeSpace
import org.goldenport.cncf.operation.{CmlEntityRelationshipDefinition, CmlOperationAccess, CmlOperationDefinition}
import org.goldenport.cncf.statemachine.{CmlStateMachineDefinition, StateMachinePlannerProvider}
import org.goldenport.cncf.event.{CmlEventDefinition, CmlRoutingDefinition, CmlSubscriptionDefinition, EventReception, EventReceptionRule, EventStore}
import org.goldenport.cncf.security.AuthenticationProvider
import org.goldenport.cncf.messagedelivery.MessageDeliveryProvider
import org.goldenport.cncf.usernotification.UserNotificationProvider
import org.goldenport.cncf.projection.{HelpProjection, DescribeProjection, SchemaProjection, OpenApiProjection, McpProjection, TreeProjection, StateMachineProjection}
import org.goldenport.cncf.workflow.WorkflowDefinition
import org.goldenport.cncf.workflow.{ContinuationRuntime, ContinuationRuntimeSource, GeneratedProvidedApiAbi, GeneratedWorkflowAbi, StateMachineProvidedApiDispatcher, StateMachineProviderResolver, StateMachineProviderSource}
import cats.data.NonEmptyVector
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.{Duration, Instant}
import java.util.Properties
import scala.deprecatedName
import scala.util.control.NonFatal
import org.goldenport.schema.{DataType, XString}

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[component] trait ComponentMetadataActions { self: Component.type =>
  import Component.*

  private[component] def _with_default_services(protocol: Protocol): Protocol = {
    val withmetahelp = _ensure_operation(protocol, _default_meta_service_name, DefaultMetaHelpOperation)
    val withmetadescribe = _ensure_operation(withmetahelp, _default_meta_service_name, DefaultMetaDescribeOperation)
    val withmetacomponents = _ensure_operation(withmetadescribe, _default_meta_service_name, DefaultMetaComponentsOperation)
    val withmetaservices = _ensure_operation(withmetacomponents, _default_meta_service_name, DefaultMetaServicesOperation)
    val withmetaoperations = _ensure_operation(withmetaservices, _default_meta_service_name, DefaultMetaOperationsOperation)
    val withmetaschema = _ensure_operation(withmetaoperations, _default_meta_service_name, DefaultMetaSchemaOperation)
    val withmetaopenapi = _ensure_operation(withmetaschema, _default_meta_service_name, DefaultMetaOpenApiOperation)
    val withmetamcp = _ensure_operation(withmetaopenapi, _default_meta_service_name, DefaultMetaMcpOperation)
    val withmetatree = _ensure_operation(withmetamcp, _default_meta_service_name, DefaultMetaTreeOperation)
    val withmetastatemachine = _ensure_operation(withmetatree, _default_meta_service_name, DefaultMetaStateMachineOperation)
    val withmetaversion = _ensure_operation(withmetastatemachine, _default_meta_service_name, DefaultMetaVersionOperation)
    val withsystemping = _ensure_operation(withmetaversion, _default_system_service_name, DefaultSystemPingOperation)
    val withsystemhealth = _ensure_operation(withsystemping, _default_system_service_name, DefaultSystemHealthOperation)
    _ensure_operation(withsystemhealth, _default_system_service_name, DefaultSystemStatusOperation)
  }

  private def _ensure_operation(
    protocol: Protocol,
    servicename: String,
    operation: OperationDefinition
  ): Protocol = {
    val exists = protocol.services.services.exists { service =>
      service.name == servicename &&
      service.operations.operations.exists(_.name == operation.name)
    }
    if (exists) {
      protocol
    } else {
      protocol.copy(
        services = protocol.services.addOperation(servicename, operation)
      )
    }
  }

  private object DefaultMetaHelpOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "help",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaHelpAction(req))
  }

  private object DefaultMetaDescribeOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "describe",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaDescribeAction(req))
  }

  private object DefaultMetaComponentsOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "components",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaComponentsAction(req))
  }

  private object DefaultMetaServicesOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "services",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaServicesAction(req))
  }

  private object DefaultMetaOperationsOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "operations",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaOperationsAction(req))
  }

  private object DefaultMetaSchemaOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "schema",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaSchemaAction(req))
  }

  private object DefaultMetaOpenApiOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "openapi",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(XString))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaOpenApiAction(req))
  }

  private object DefaultMetaTreeOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "tree",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaTreeAction(req))
  }

  private object DefaultMetaMcpOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "mcp",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(XString))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaMcpAction(req))
  }

  private object DefaultMetaStateMachineOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "statemachine",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaStateMachineAction(req))
  }

  private object DefaultMetaVersionOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "version",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultMetaVersionAction(req))
  }

  private object DefaultSystemPingOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "ping",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(XString))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultSystemPingAction(req))
  }

  private object DefaultSystemHealthOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "health",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultSystemHealthAction(req))
  }

  private object DefaultSystemStatusOperation extends OperationDefinition {
    override val specification: OperationDefinition.Specification =
      OperationDefinition.Specification(
        name = "status",
        request = org.goldenport.protocol.spec.RequestDefinition(),
        response = org.goldenport.protocol.spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )

    override def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DefaultSystemStatusAction(req))
  }

  private final case class DefaultMetaHelpAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaHelpActionCall(request, core)
  }

  private final case class DefaultMetaHelpActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val response = core.component match {
        case Some(component) =>
          val record = HelpProjection.project(
            component,
            _meta_help_selector(req)
          )
          OperationResponse.RecordResponse(record)
        case None =>
          OperationResponse.RecordResponse(
            Record.data(
              "type" -> "error",
              "summary" -> "component context missing"
            )
          )
      }
      Consequence.success(response)
    }
  }

  private final case class DefaultMetaDescribeAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaDescribeActionCall(request, core)
  }

  private final case class DefaultMetaDescribeActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          DescribeProjection.project(
            component,
            _meta_describe_selector(req)
          )
        case None =>
          Record.data(
            "type" -> "error",
            "summary" -> "component context missing"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultMetaComponentsAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaComponentsActionCall(request, core)
  }

  private final case class DefaultMetaComponentsActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          DescribeProjection.project(component, None)
        case None =>
          Record.data(
            "type" -> "error",
            "summary" -> "component context missing"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultMetaServicesAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaServicesActionCall(request, core)
  }

  private final case class DefaultMetaServicesActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          val selector = _meta_services_selector(req)
          HelpProjection.project(component, selector)
        case None =>
          Record.data(
            "type" -> "error",
            "summary" -> "component context missing"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultMetaOperationsAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaOperationsActionCall(request, core)
  }

  private final case class DefaultMetaOperationsActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          val selector = _meta_operations_selector(req)
          HelpProjection.project(component, selector)
        case None =>
          Record.data(
            "type" -> "error",
            "summary" -> "component context missing"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultMetaSchemaAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaSchemaActionCall(request, core)
  }

  private final case class DefaultMetaSchemaActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          SchemaProjection.project(component, _meta_schema_selector(req))
        case None =>
          Record.data(
            "type" -> "error",
            "summary" -> "component context missing"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultMetaOpenApiAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaOpenApiActionCall(request, core)
  }

  private final case class DefaultMetaOpenApiActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val text = core.component match {
        case Some(component) =>
          _meta_openapi_selector(req) match {
            case Some(name) =>
              component.subsystem
                .flatMap(_.components.find(_.name == name))
                .map(OpenApiProjection.projectComponent)
                .getOrElse(OpenApiProjection.projectComponent(component))
            case None =>
              OpenApiProjection.projectComponent(component)
          }
        case None =>
          """{"error":"component context missing"}"""
      }
      Consequence.success(OperationResponse.Scalar(text))
    }
  }

  private final case class DefaultMetaTreeAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaTreeActionCall(request, core)
  }

  private final case class DefaultMetaMcpAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaMcpActionCall(request, core)
  }

  private final case class DefaultMetaStateMachineAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaStateMachineActionCall(request, core)
  }

  private final case class DefaultMetaMcpActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val text = core.component match {
        case Some(component) =>
          _meta_mcp_selector(req) match {
            case Some(name) =>
              component.subsystem
                .flatMap(_.components.find(_.name == name))
                .map(McpProjection.projectComponent)
                .getOrElse(McpProjection.projectComponent(component))
            case None =>
              McpProjection.projectComponent(component)
          }
        case None =>
          """{"error":"component context missing"}"""
      }
      Consequence.success(OperationResponse.Scalar(text))
    }
  }

  private final case class DefaultMetaTreeActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val text = core.component match {
        case Some(component) =>
          val model = TreeProjection.project(component)
          if (_request_wants_json(req)) {
            CliTreeJsonRenderer.render(model)
          } else {
            CliTreeYamlRenderer.render(model)
          }
        case None =>
          "subsystem: unknown\ncomponents: {}"
      }
      Consequence.success(OperationResponse.Scalar(text))
    }
  }

  private final case class DefaultMetaStateMachineActionCall(
    req: Request,
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          StateMachineProjection.project(component, _meta_statemachine_selector(req))
        case None =>
          Record.data(
            "type" -> "error",
            "summary" -> "component context missing"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultMetaVersionAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultMetaVersionActionCall(core)
  }

  private final case class DefaultMetaVersionActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) => _resolve_version_record(component)
        case None =>
          Record.data(
            "component" -> "unknown",
            "version" -> _default_unknown_version,
            "source" -> "unavailable"
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultSystemPingAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultSystemPingActionCall(core)
  }

  private final case class DefaultSystemPingActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar("ok"))
  }

  private final case class DefaultSystemHealthAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultSystemHealthActionCall(core)
  }

  private final case class DefaultSystemHealthActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val rec = core.component match {
        case Some(component) =>
          val health = healthSnapshot(component)
          Record.data(
            "status" -> health.status,
            "checks" -> health.checks.map(_.toRecord)
          )
        case None =>
          Record.data(
            "status" -> "error",
            "checks" -> Vector(
              HealthCheck("component.available", "error", Some("component context missing")).toRecord
            )
          )
      }
      Consequence.success(OperationResponse.RecordResponse(rec))
    }
  }

  private final case class DefaultSystemStatusAction(
    request: Request
  ) extends QueryAction {
    override def createCall(core: ActionCall.Core): ActionCall =
      DefaultSystemStatusActionCall(core)
  }

  private final case class DefaultSystemStatusActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    override def execute(): Consequence[OperationResponse] = {
      val now = core.executionContext.clock.instant()
      val bootedat = _global_runtime_context(core.executionContext.runtime).map(_.bootedAt).getOrElse(now)
      val duration = Duration.between(bootedat, now)
      val uptime = if (duration.isNegative) Duration.ZERO else duration
      val base = Record.data(
        "status" -> "UP",
        "timestamp" -> now.toString,
        "uptime" -> uptime.toString
      )
      val record = core.component match {
        case Some(component) =>
          component.jobEngine.metrics match {
            case Some(metrics) =>
              base ++ Record.data(
                "jobsRunning" -> metrics.running,
                "jobsQueued" -> metrics.queued,
                "jobsCompleted" -> metrics.completed,
                "jobsFailed" -> metrics.failed
              )
            case None =>
              base
          }
        case None =>
          base
      }
      Consequence.success(OperationResponse.RecordResponse(record))
    }
  }

  private def _read_resource_text(
    loader: ClassLoader,
    path: String
  ): Option[String] =
    Option(loader.getResourceAsStream(path)).flatMap { is =>
      try {
        val text = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim
        if (text.nonEmpty) Some(text) else None
      } catch {
        case NonFatal(_) => None
      } finally {
        is.close()
      }
    }

  private def _resolve_version_record(component: Component): Record = {
    val configversion = _configuration_value(component, "component.version")
    val properties = _component_properties(component)
    val propertyversion =
      properties.get("component.version").orElse(properties.get("version"))
    val manifestversion = Option(component.getClass.getPackage)
      .flatMap(p => Option(p.getImplementationVersion))
      .map(_.trim)
      .filter(_.nonEmpty)

    val versionwithsource =
      configversion.map(_ -> "config")
        .orElse(propertyversion.map(_ -> "resource"))
        .orElse(manifestversion.map(_ -> "manifest"))
        .getOrElse(_default_unknown_version -> "default")

    val buildinfo =
      _configuration_value(component, "component.build")
        .orElse(_configuration_value(component, "component.build.info"))
        .orElse(properties.get("build"))
        .orElse(properties.get("build.info"))
        .orElse(properties.get("build-time"))
        .orElse(properties.get("build.revision"))

    Record.data(
      "component" -> component.name,
      "version" -> versionwithsource._1,
      "source" -> versionwithsource._2
    ) ++ Record.dataOption(
      "build" -> buildinfo
    )
  }

  private def _component_properties(
    component: Component
  ): Map[String, String] =
    _read_properties(component.getClass.getClassLoader, "META-INF/component.properties")

  private def _read_properties(
    loader: ClassLoader,
    path: String
  ): Map[String, String] =
    Option(loader.getResourceAsStream(path)).map { is =>
      try {
        val p = Properties()
        p.load(is)
        p.stringPropertyNames().toArray.toVector.map(_.toString).map { key =>
          key -> p.getProperty(key)
        }.toMap
      } catch {
        case NonFatal(_) => Map.empty[String, String]
      } finally {
        is.close()
      }
    }.getOrElse(Map.empty)

  private def _request_argument_values(request: Request): Vector[String] =
    request.arguments
      .map(arg => Option(arg.value).map(_.toString).getOrElse("").trim)
      .filter(_.nonEmpty)
      .toVector

  private def _meta_help_selector(request: Request): Option[String] =
    _request_argument_values(request).headOption

  private def _meta_describe_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args match {
      case Vector(service, operation) =>
        request.component.map(c => s"$c.$service.$operation").orElse(Some(s"$service.$operation"))
      case Vector(single) =>
        if (single.contains(".")) {
          single.split("\\.").toVector.filter(_.nonEmpty) match {
            case Vector(_, _, _) => Some(single)
            case Vector(_, _) =>
              request.component.map(c => s"$c.$single").orElse(Some(single))
            case Vector(_) =>
              request.component.map(c => s"$c.$single").orElse(Some(single))
            case _ => Some(single)
          }
        } else {
          request.component.map(c => s"$c.$single").orElse(Some(single))
        }
      case _ =>
        request.component
    }
  }

  private def _meta_services_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args.headOption match {
      case Some(name) if name.contains(".") =>
        Some(name)
      case Some(componentname) =>
        Some(componentname)
      case None =>
        request.component
    }
  }

  private def _meta_operations_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args match {
      case Vector(servicename, operationname) =>
        request.component.map(c => s"$c.$servicename.$operationname")
      case Vector(servicename) =>
        if (servicename.contains(".")) {
          Some(servicename)
        } else {
          request.component.map(c => s"$c.$servicename")
        }
      case _ =>
        None
    }
  }

  private def _meta_schema_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args.headOption match {
      case Some(selector) => Some(selector)
      case None => request.component
    }
  }

  private def _meta_openapi_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args.headOption
  }

  private def _meta_mcp_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args.headOption
  }

  private def _meta_statemachine_selector(request: Request): Option[String] = {
    val args = _request_argument_values(request)
    args.headOption match {
      case Some(selector) => Some(selector)
      case None => request.component
    }
  }

  private def _request_wants_json(request: Request): Boolean =
    request.properties.exists { p =>
      (
        p.name.equalsIgnoreCase("textus.format") ||
          p.name.equalsIgnoreCase("textus.output.format") ||
          p.name.equalsIgnoreCase("cncf.format") ||
          p.name.equalsIgnoreCase("cncf.output.format")
      ) && Option(p.value).map(_.toString.trim.toLowerCase).contains("json")
    }

  private def _configuration_value(
    component: Component,
    key: String
  ): Option[String] =
    component.subsystem.flatMap { ss =>
      ss.configuration.get[String](key) match {
        case Consequence.Success(Some(value)) =>
          val normalized = value.trim
          if (normalized.isEmpty) None else Some(normalized)
        case _ => None
      }
    }

  private[component] def _resolve_health_checks(component: Component): Vector[HealthCheck] = {
    val base = Vector(
      HealthCheck("component.reachable", "ok"),
      HealthCheck("component.runtime", "ok")
    )
    val contributorchecks = component.healthContributors.map { contributor =>
      try {
        contributor.check(component)
      } catch {
        case NonFatal(e) =>
          val detail = Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getName)
          HealthCheck(contributor.name, "error", Some(detail))
      }
    }
    val configuredchecks = _configured_health_check_names(component).filterNot { name =>
      contributorchecks.exists(_.name == name)
    }.map { name =>
      HealthCheck(name, "warning", Some("configured check has no registered contributor"))
    }
    base ++ contributorchecks ++ configuredchecks
  }

  private def _configured_health_check_names(component: Component): Vector[String] = {
    val fromconfig = _configuration_value(component, "component.health.checks")
      .map(_comma_separated_values)
      .getOrElse(Vector.empty)
    val fromresource = _component_properties(component)
      .get("component.health.checks")
      .map(_comma_separated_values)
      .getOrElse(Vector.empty)
    (fromconfig ++ fromresource).distinct
  }

  private def _comma_separated_values(value: String): Vector[String] =
    value.split(",").toVector.map(_.trim).filter(_.nonEmpty)

  private[component] def _overall_status(checks: Vector[HealthCheck]): String = {
    val statuses = checks.map(_.status.toLowerCase)
    if (statuses.contains("error")) {
      "error"
    } else if (statuses.contains("warning")) {
      "warning"
    } else {
      "ok"
    }
  }

  @annotation.tailrec
  private def _global_runtime_context(scope: ScopeContext): Option[GlobalRuntimeContext] =
    scope match {
      case global: GlobalRuntimeContext => Some(global)
      case other => other.parent match {
        case Some(parent) => _global_runtime_context(parent)
        case None => None
      }
    }
}
