package org.goldenport.cncf.component.builtin.admin

import cats.data.NonEmptyVector
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall, QueryAction, ResourceAccess}
import org.goldenport.cncf.component.{Component, ComponentInit, ComponentOrigin}
import org.goldenport.cncf.component.ComponentOriginLabel
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.component.ComponentLogic
import org.goldenport.cncf.component.DescriptorRecordLoader
import org.goldenport.configuration.ConfigurationResolver
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.configuration.ConfigurationSources
import org.goldenport.configuration.ConfigurationOrigin
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.datastore.{DataStore, Query as DataStoreQuery, QueryDirective, QueryLimit, TotalCountCapability}
import org.goldenport.cncf.directive.{Query as EntityQuery}
import org.goldenport.cncf.entity.runtime.EntityCollection
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.observability.ObservabilityEngine
import org.goldenport.cncf.projection.{SecurityDeploymentMarkdownProjection, SecurityDeploymentProjection}
import org.goldenport.cncf.subsystem.{GenericSubsystemAssemblyDescriptorSource, Subsystem}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.value.BaseContent
import org.goldenport.schema.{DataType, XString}

/*
 * @since   Jan.  7, 2026
 *  version Jan. 20, 2026
 *  version Feb. 19, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class AdminComponent() extends Component {
}

object AdminComponent {
  val name: String = "admin"
  val componentId = ComponentId(name) // TODO static

  object Factory extends Component.Factory {
    protected def create_Components(params: ComponentCreate): Vector[Component] = {
      Vector(AdminComponent())
    }

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val request = spec.RequestDefinition()
      val opPing = new PingOperationDefinition(request, spec.ResponseDefinition(result = List(XString)))
      val opComponentList = new ComponentListOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opConfigShow = new ConfigShowOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opVariationList = new VariationListOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opVariationDescribe = new VariationDescribeOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val opExtensionList = new ExtensionListOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opDeploymentSecurityMermaid = new DeploymentSecurityMermaidOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opDeploymentSecurityMarkdown = new DeploymentSecurityMarkdownOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opAssemblyWarnings = new AssemblyWarningsOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opAssemblyReport = new AssemblyReportOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opAssemblyDescriptor = new AssemblyDescriptorOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opAssemblyDiagram = new AssemblyDiagramOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opExecutionCalltree = new ExecutionCalltreeOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val opExecutionHistory = new ExecutionHistoryOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val opEntityCreate = new EntityCreateOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opEntityUpdate = new EntityUpdateOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opEntityList = new EntityListOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opEntityRead = new EntityReadOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opDataCreate = new DataCreateOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opDataUpdate = new DataUpdateOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opDataList = new DataListOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opDataRead = new DataReadOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opViewRead = new ViewReadOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opAggregateRead = new AggregateReadOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val serviceSystem = spec.ServiceDefinition(
        name = "system",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opPing)
        )
      )
      val serviceComponent = spec.ServiceDefinition(
        name = "component",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opComponentList)
        )
      )
      val serviceConfig = spec.ServiceDefinition(
        name = "config",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opConfigShow)
        )
      )
      val serviceVariation = spec.ServiceDefinition(
        name = "variation",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opVariationList,
            opVariationDescribe
          )
        )
      )
      val serviceExtension = spec.ServiceDefinition(
        name = "extension",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opExtensionList)
        )
      )
      val serviceDeployment = spec.ServiceDefinition(
        name = "deployment",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opDeploymentSecurityMermaid,
            opDeploymentSecurityMarkdown
          )
        )
      )
      val serviceAssembly = spec.ServiceDefinition(
        name = "assembly",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opAssemblyWarnings,
            opAssemblyReport,
            opAssemblyDescriptor,
            opAssemblyDiagram
          )
        )
      )
      val serviceExecution = spec.ServiceDefinition(
        name = "execution",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opExecutionHistory,
            opExecutionCalltree
          )
        )
      )
      val serviceEntity = spec.ServiceDefinition(
        name = "entity",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opEntityList,
            opEntityRead,
            opEntityCreate,
            opEntityUpdate
          )
        )
      )
      val serviceData = spec.ServiceDefinition(
        name = "data",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opDataList,
            opDataRead,
            opDataCreate,
            opDataUpdate
          )
        )
      )
      val serviceView = spec.ServiceDefinition(
        name = "view",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opViewRead)
        )
      )
      val serviceAggregate = spec.ServiceDefinition(
        name = "aggregate",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opAggregateRead)
        )
      )
      val services = spec.ServiceDefinitionGroup(
        services = Vector(
          serviceSystem,
          serviceComponent,
          serviceConfig,
          serviceVariation,
          serviceExtension,
          serviceDeployment,
          serviceAssembly,
          serviceExecution,
          serviceEntity,
          serviceData,
          serviceView,
          serviceAggregate
        )
      )
      val protocol = Protocol(
        services = services,
        handler = ProtocolHandler.default
      )
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(
        name,
        componentId,
        instanceid,
        protocol
      )
    }
  }

  private final class PingOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "ping",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      val _ = req
      Consequence.success(ComponentLogic.PingAction(req))
    }
  }

  private final class ComponentListOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "list",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      Consequence.success(ComponentListAction(req, subsystem))
    }
  }

  private final class VariationListOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "list",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      val _ = req
      Consequence.success(VariationListAction(req, subsystem))
    }
  }

  private final class VariationDescribeOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("describe")
          .summary("Describe a runtime variation point.")
          .description("Return detailed information for an individual variation point key.")
          .build(),
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(VariationDescribeAction(req))
  }

  private final class ExtensionListOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "list",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      Consequence.success(ExtensionListAction(req, subsystem))
    }
  }

  private final class ConfigShowOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "show",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] = {
      Consequence.success(ConfigShowAction(req, subsystem))
    }
  }

  private final class DeploymentSecurityMermaidOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("securityMermaid")
          .summary("Render the subsystem security deployment diagram as Mermaid.")
          .description("Project the resolved security wiring into a minimal Mermaid deployment diagram with ingress, providers, SecurityContext, ActionCall, and UnitOfWork.")
          .build(),
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(DeploymentSecurityMermaidAction(req, subsystem))
  }

  private final class DeploymentSecurityMarkdownOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("securityMarkdown")
          .summary("Render the subsystem security deployment specification as Markdown.")
          .description("Project the resolved security wiring into an editable Markdown specification draft including Mermaid, provider metadata, and framework chokepoints.")
          .build(),
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(DeploymentSecurityMarkdownAction(req, subsystem))
  }

  private final class AssemblyWarningsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("warnings")
          .summary("Show assembly warnings detected during component and subsystem loading.")
          .description("Return duplicate-component and related assembly warnings captured during runtime assembly.")
          .build(),
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(AssemblyWarningsAction(req, subsystem))
  }

  private final class AssemblyReportOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("report")
          .summary("Show the resolved assembly report for the selected subsystem.")
          .description("Return subsystem descriptor wiring, loaded component origins, and assembly warnings captured during runtime assembly.")
          .build(),
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(AssemblyReportAction(req, subsystem))
  }

  private final class AssemblyDescriptorOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("descriptor")
          .summary("Export the resolved assembly descriptor for the selected subsystem.")
          .description("Return a descriptor-oriented document for the runtime-resolved assembly, suitable for review and later descriptor export.")
          .build(),
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(AssemblyDescriptorAction(req, subsystem))
  }

  private final class AssemblyDiagramOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("diagram")
          .summary("Export the resolved assembly wiring diagram.")
          .description("Return a web-renderable Mermaid projection of the runtime-resolved assembly wiring.")
          .build(),
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(AssemblyDiagramAction(req, subsystem))
  }

  private final class ExecutionCalltreeOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("calltree")
          .summary("Show the latest retained execution calltree.")
          .description("Return the latest finalized operation calltree retained by the runtime for admin inspection.")
          .build(),
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(ExecutionCalltreeAction(req))
  }

  private final class ExecutionHistoryOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("history")
          .summary("Show retained action execution history.")
          .description("Return retained action execution records including parameters, result summaries, and calltree projections when captured.")
          .build(),
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(ExecutionHistoryAction(req))
  }

  private final class EntityCreateOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "create",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(EntityCreateAction(req, subsystem))
  }

  private final class EntityUpdateOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "update",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(EntityUpdateAction(req, subsystem))
  }

  private final class EntityListOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = "list", request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(EntityListAction(req, subsystem))
  }

  private final class EntityReadOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = "read", request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(EntityReadAction(req, subsystem))
  }

  private final class DataCreateOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "create",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(DataCreateAction(req, subsystem))
  }

  private final class DataUpdateOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "update",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(DataUpdateAction(req, subsystem))
  }

  private final class DataListOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = "list", request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DataListAction(req, subsystem))
  }

  private final class DataReadOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = "read", request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DataReadAction(req, subsystem))
  }

  private final class ViewReadOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = "read", request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(ViewReadAction(req, subsystem))
  }

  private final class AggregateReadOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = "read", request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(AggregateReadAction(req, subsystem))
  }

  private final case class ComponentListAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
//    val name = "component.list"

    def createCall(core: ActionCall.Core): ActionCall =
      ComponentListActionCall(core, subsystem)
  }

  private final case class ComponentListActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      val comps = subsystem.components
      val text = _component_lines_(comps, "Components")
      Consequence.success(OperationResponse.Scalar(text))
    }
  }

  private final case class ConfigShowAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
//    val name = "config.show"

    def createCall(core: ActionCall.Core): ActionCall =
      ConfigShowActionCall(core, subsystem)
  }

  private final case class ConfigShowActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      _config_snapshot_().map { text =>
        OperationResponse.Scalar(text)
      }
    }
  }

  private final case class EntityCreateAction(
    request: Request,
    subsystem: Subsystem
  ) extends CommandAction() {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.SyncDirectNoJob

    def createCall(core: ActionCall.Core): ActionCall =
      EntityCreateActionCall(core, subsystem)
  }

  private final case class EntityUpdateAction(
    request: Request,
    subsystem: Subsystem
  ) extends CommandAction() {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.SyncDirectNoJob

    def createCall(core: ActionCall.Core): ActionCall =
      EntityUpdateActionCall(core, subsystem)
  }

  private final case class EntityListAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      EntityListActionCall(core, subsystem)
  }

  private final case class EntityReadAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      EntityReadActionCall(core, subsystem)
  }

  private final case class EntityListActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_entity_list(core, subsystem)
  }

  private final case class EntityReadActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_entity_read(core, subsystem)
  }

  private final case class EntityCreateActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_entity_put(core, subsystem)
  }

  private final case class EntityUpdateActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_entity_put(core, subsystem)
  }

  private final case class DataCreateAction(
    request: Request,
    subsystem: Subsystem
  ) extends CommandAction() {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.SyncDirectNoJob

    def createCall(core: ActionCall.Core): ActionCall =
      DataCreateActionCall(core, subsystem)
  }

  private final case class DataUpdateAction(
    request: Request,
    subsystem: Subsystem
  ) extends CommandAction() {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.SyncDirectNoJob

    def createCall(core: ActionCall.Core): ActionCall =
      DataUpdateActionCall(core, subsystem)
  }

  private final case class DataListAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      DataListActionCall(core, subsystem)
  }

  private final case class DataReadAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      DataReadActionCall(core, subsystem)
  }

  private final case class ViewReadAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      ViewReadActionCall(core, subsystem)
  }

  private final case class AggregateReadAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      AggregateReadActionCall(core, subsystem)
  }

  private final case class DataCreateActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_data_create(core, subsystem)
  }

  private final case class DataUpdateActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_data_update(core, subsystem)
  }

  private final case class DataListActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_data_list(core, subsystem)
  }

  private final case class DataReadActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_data_read(core, subsystem)
  }

  private final case class ViewReadActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_view_read(core, subsystem)
  }

  private final case class AggregateReadActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_aggregate_read(core, subsystem)
  }

  private final case class DeploymentSecurityMermaidAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      DeploymentSecurityMermaidActionCall(core, subsystem)
  }

  private final case class DeploymentSecurityMermaidActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar(SecurityDeploymentProjection.projectMermaid(subsystem)))
  }

  private final case class DeploymentSecurityMarkdownAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      DeploymentSecurityMarkdownActionCall(core, subsystem)
  }

  private final case class AssemblyWarningsAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      AssemblyWarningsActionCall(core, subsystem)
  }

  private final case class AssemblyReportAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      AssemblyReportActionCall(core, subsystem)
  }

  private final case class AssemblyDescriptorAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      AssemblyDescriptorActionCall(core, subsystem)
  }

  private final case class AssemblyDiagramAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      AssemblyDiagramActionCall(core, subsystem)
  }

  private final case class ExecutionCalltreeAction(
    request: Request
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      ExecutionCalltreeActionCall(core)
  }

  private final case class ExecutionHistoryAction(
    request: Request
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      ExecutionHistoryActionCall(core, request)
  }

  private final case class DeploymentSecurityMarkdownActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar(SecurityDeploymentMarkdownProjection.project(subsystem)))
  }

  private final case class AssemblyWarningsActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      val report =
        GlobalRuntimeContext.current
          .map(_.assemblyReport.toRecord)
          .getOrElse(subsystem.globalRuntimeContext.assemblyReport.toRecord)
      Consequence.success(OperationResponse.RecordResponse(report))
    }
  }

  private final case class AssemblyReportActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      val warnings =
        GlobalRuntimeContext.current
          .map(_.assemblyReport.toRecord)
          .getOrElse(subsystem.globalRuntimeContext.assemblyReport.toRecord)
      val wiring = _subsystem_wiring_(subsystem)
      val ports = subsystem.descriptor.map(_.declaredPorts).getOrElse(Vector.empty)
      val wiringBindings = subsystem.descriptor.map(_.resolvedWiringBindings).getOrElse(Vector.empty)
      val components = org.goldenport.record.Record.data(
        "loaded" -> subsystem.components.toVector.map { comp =>
          org.goldenport.record.Record.data(
            "name" -> comp.name,
            "origin" -> ComponentOriginLabel.userLabel(comp.origin.label)
          )
        }
      )
      val report = org.goldenport.record.Record.data(
        "subsystem" -> subsystem.name,
        "ports" -> ports,
        "wiring" -> wiring,
        "wiring_bindings" -> wiringBindings,
        "components" -> components,
        "warnings" -> warnings
      )
      Consequence.success(OperationResponse.RecordResponse(report))
    }
  }

  private final case class AssemblyDescriptorActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      val warnings =
        GlobalRuntimeContext.current
          .map(_.assemblyReport.toRecord)
          .getOrElse(subsystem.globalRuntimeContext.assemblyReport.toRecord)
      val components = subsystem.components.toVector
      val descriptorComponents = components.filterNot(_.origin == ComponentOrigin.Builtin)
      val builtinComponents = components.filter(_.origin == ComponentOrigin.Builtin)
      val sourceWiring = _subsystem_wiring_(subsystem)
      val resolvedWiring = subsystem.descriptor.map(_.resolvedWiringBindings).getOrElse(Vector.empty)
      val descriptor = org.goldenport.record.Record.data(
        "kind" -> "assembly-descriptor",
        "subsystem" -> subsystem.name,
        "version" -> subsystem.version.getOrElse(""),
        "components" -> descriptorComponents.map(_assembly_component_record_),
        "ports" -> subsystem.descriptor.map(_.declaredPorts).getOrElse(Vector.empty),
        "wiring" -> resolvedWiring,
        "source" -> org.goldenport.record.Record.data(
          "wiring" -> sourceWiring,
          "assembly_descriptor" -> subsystem.descriptor.flatMap(_.assemblyDescriptor).map(_assembly_descriptor_source_record_).getOrElse(Record.empty)
        ),
        "runtime" -> org.goldenport.record.Record.data(
          "builtin_components" -> builtinComponents.map(_assembly_component_record_)
        ),
        "diagnostics" -> org.goldenport.record.Record.data(
          "warnings" -> warnings
        )
      )
      Consequence.success(OperationResponse.RecordResponse(descriptor))
    }
  }

  private final case class AssemblyDiagramActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar(_assembly_mermaid_(subsystem)))
  }

  private final case class ExecutionCalltreeActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      val record = ObservabilityEngine.latestExecution
        .map(_.calltreeRecord)
        .getOrElse(
          Record.data(
            "status" -> "empty",
            "message" -> "No retained action execution is available."
          )
        )
      Consequence.success(OperationResponse.RecordResponse(record))
    }
  }

  private final case class ExecutionHistoryActionCall(
    core: ActionCall.Core,
    historyRequest: Request
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      val operationFilter = _request_property_(historyRequest, "operation")
        .orElse(_request_property_(historyRequest, "operation_contains"))
      val entries = ObservabilityEngine.executionHistory(operationFilter)
      val config = ObservabilityEngine.executionHistoryConfig
      val record = Record.data(
        "recent_limit" -> config.recentLimit,
        "filtered_limit" -> config.filteredLimit,
        "filter_count" -> config.filters.size,
        "operation_filter" -> operationFilter.getOrElse(""),
        "count" -> entries.size,
        "executions" -> entries.map(_.toRecord)
      )
      Consequence.success(OperationResponse.RecordResponse(record))
    }
  }

  private def _request_property_(request: Request, name: String): Option[String] =
    request.properties.find(_.name == name).map(_.value.toString).filter(_.nonEmpty)

  private def _assembly_component_record_(comp: Component): Record =
    org.goldenport.record.Record.data(
      "name" -> comp.name,
      "origin" -> ComponentOriginLabel.userLabel(comp.origin.label)
    )

  private def _assembly_descriptor_source_record_(rec: Record): Record =
    org.goldenport.record.Record.data(
      "present" -> true,
      "kind" -> rec.getString("kind").getOrElse(""),
      "subsystem" -> rec.getString("subsystem").getOrElse(""),
      "version" -> rec.getString("version").getOrElse("")
    )

  private def _assembly_descriptor_source_record_(
    src: GenericSubsystemAssemblyDescriptorSource
  ): Record =
    org.goldenport.record.Record.data(
      "present" -> true,
      "source" -> src.source,
      "path" -> src.path.map(_.toString).getOrElse(""),
      "kind" -> src.record.getString("kind").getOrElse(""),
      "subsystem" -> src.record.getString("subsystem").getOrElse(""),
      "version" -> src.record.getString("version").getOrElse("")
    )

  private def _assembly_mermaid_(subsystem: Subsystem): String = {
    val components = subsystem.components.toVector
    val appComponents = components.filterNot(_.origin == ComponentOrigin.Builtin)
    val builtinComponents = components.filter(_.origin == ComponentOrigin.Builtin)
    val bindings = subsystem.descriptor.map(_.resolvedWiring).getOrElse(Vector.empty)
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    lines += "flowchart LR"
    lines += s"  subgraph ${_mermaid_id_(subsystem.name)}[\"${_mermaid_label_(subsystem.name)}\"]"
    appComponents.foreach { comp =>
      lines += s"    ${_mermaid_id_(comp.name)}[\"${_mermaid_label_(comp.name)}\"]"
    }
    if (builtinComponents.nonEmpty) {
      lines += "    subgraph runtime_builtins[\"runtime builtins\"]"
      builtinComponents.foreach { comp =>
        lines += s"      ${_mermaid_id_(s"builtin_${comp.name}")}[[\"${_mermaid_label_(comp.name)}\"]]"
      }
      lines += "    end"
    }
    bindings.foreach { binding =>
      val from = _mermaid_id_(binding.fromComponent)
      val to = _mermaid_id_(binding.toComponent)
      val api = binding.fromApi.filter(_.nonEmpty).getOrElse(binding.fromOperation)
      val spi = binding.toSpi.filter(_.nonEmpty).getOrElse(binding.toOperation)
      val glue = if (binding.glue.isEmpty) "" else " / glue"
      val label = s"${api} -> ${spi}${glue}"
      lines += s"    ${from} -->|\"${_mermaid_label_(label)}\"| ${to}"
    }
    lines += "  end"
    lines.mkString("\n")
  }

  private def _mermaid_id_(name: String): String =
    name.map {
      case c if c.isLetterOrDigit => c
      case _ => '_'
    }.mkString match {
      case "" => "node"
      case s if s.head.isDigit => s"n_${s}"
      case s => s
    }

  private def _mermaid_label_(name: String): String =
    name.replace("\\", "\\\\").replace("\"", "\\\"")

  private def _subsystem_wiring_(subsystem: Subsystem): Record =
    subsystem.descriptor.map(_.wiring).filterNot(_.isEmpty).getOrElse {
      subsystem.descriptor.flatMap { descriptor =>
        _load_descriptor_record_(descriptor.path).map(_wiring_from_record_).filterNot(_.isEmpty)
          .orElse(_load_wiring_from_text_(descriptor.path))
      }.getOrElse(Record.empty)
    }

  private def _load_descriptor_record_(path: java.nio.file.Path): Option[Record] = {
    val name = path.getFileName.toString.toLowerCase
    if (name.endsWith(".sar") || name.endsWith(".zip")) {
      val uri = URI.create(s"jar:${path.toUri}")
      Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
        Vector(
          "subsystem-descriptor.yaml",
          "subsystem-descriptor.yml",
          "descriptor.yaml",
          "descriptor.yml",
          "subsystem-descriptor.json",
          "descriptor.json"
        ).iterator
          .map(fs.getPath("/").resolve(_))
          .find(java.nio.file.Files.isRegularFile(_))
          .flatMap(p => DescriptorRecordLoader.load(p).toOption.flatMap(_.headOption))
      }
    } else {
      DescriptorRecordLoader.load(path).toOption.flatMap(_.headOption)
    }
  }

  private def _wiring_from_record_(rec: Record): Record =
    rec.getRecord("wiring").orElse {
      val entries = rec.asMap.iterator.collect {
        case (k, v) if k.startsWith("wiring/") =>
          k.stripPrefix("wiring/") -> v
        case (k, v) if k.startsWith("wiring.") =>
          k.stripPrefix("wiring.") -> v
      }.toVector
      if (entries.isEmpty) None else Some(Record.create(entries))
    }.getOrElse(Record.empty)

  private def _load_wiring_from_text_(path: java.nio.file.Path): Option[Record] = {
    def parse(text: String): Option[Record] = {
      val entries = text.linesIterator.flatMap { line =>
        val trimmed = line.trim
        if (trimmed.startsWith("wiring/") || trimmed.startsWith("wiring.")) {
          trimmed.split(":", 2) match {
            case Array(k, v) =>
              Some(k.stripPrefix("wiring/").stripPrefix("wiring.") -> v.trim)
            case _ =>
              None
          }
        } else None
      }.toVector
      if (entries.isEmpty) None else Some(Record.create(entries))
    }

    val name = path.getFileName.toString.toLowerCase
    if (name.endsWith(".sar") || name.endsWith(".zip")) {
      val uri = URI.create(s"jar:${path.toUri}")
      Using.resource(FileSystems.newFileSystem(uri, Map.empty[String, String].asJava)) { fs =>
        Vector(
          "subsystem-descriptor.yaml",
          "subsystem-descriptor.yml",
          "descriptor.yaml",
          "descriptor.yml"
        ).iterator
          .map(fs.getPath("/").resolve(_))
          .find(Files.isRegularFile(_))
          .flatMap(p => parse(Files.readString(p)))
      }
    } else if (Files.isRegularFile(path)) {
      parse(Files.readString(path))
    } else {
      None
    }
  }

  private final case class VariationListAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
//    val name = "variation.list"

    def createCall(core: ActionCall.Core): ActionCall =
      VariationListActionCall(core, subsystem)
  }

  private final case class VariationDescribeAction(
    request: Request
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      VariationDescribeActionCall(core, request)
  }

  private final case class VariationListActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      _config_snapshot_().map { text =>
        OperationResponse.Scalar(_variation_lines_(text))
      }
    }
  }

  private final case class VariationDescribeActionCall(
    core: ActionCall.Core,
    describeRequest: Request
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      val key = describeRequest.arguments.headOption.map(_.printValue)
        .orElse(_request_property_(describeRequest, "key"))
        .getOrElse("")
      _declared_runtime_variation_points.find(_.key == key) match {
        case Some(point) =>
          Consequence.success(OperationResponse.RecordResponse(point.toRecord))
        case None =>
          Consequence.success(
            OperationResponse.RecordResponse(
              Record.data(
                "status" -> "not_found",
                "key" -> key,
                "message" -> s"variation point not found: ${key}"
              )
            )
          )
      }
    }
  }

  private final case class ExtensionListAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
//    val name = "extension.list"

    def createCall(core: ActionCall.Core): ActionCall =
      ExtensionListActionCall(core, subsystem)
  }

  private final case class ExtensionListActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    override def action: Action = core.action

    def execute(): Consequence[OperationResponse] = {
      val comps = subsystem.components
      val text = _extension_lines_(comps)
      Consequence.success(OperationResponse.Scalar(text))
    }
  }

  private def _component_origin_(comp: Component): String = {
    ComponentOriginLabel.userLabel(comp.origin.label)
  }

  private def _component_lines_(
    comps: Seq[Component],
    header: String
  ): String = {
    val lines = Vector.newBuilder[String]
    lines += s"${header} (total: ${comps.size})"
    lines += ""
    comps.foreach { comp =>
      val origin = _component_origin_(comp)
      lines += s"- ${comp.name}"
      lines += s"  class : ${comp.getClass.getName}"
      lines += s"  origin: ${origin}"
      lines += ""
    }
    lines.result().mkString("\n").trim
  }

  private def _config_snapshot_(): Consequence[String] = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val sources = _standard_configuration_sources_(cwd)
    ConfigurationResolver.default.resolve(sources).map { resolved =>
      val lines = Vector.newBuilder[String]
      lines += "Config Snapshot"
      lines += ""
      resolved.configuration.values.toVector.sortBy(_._1).foreach {
        case (key, value) =>
          val source = resolved.trace.get(key).map(r => _origin_(r.origin)).getOrElse("unknown")
          lines += s"${key} = ${_value_(value)}"
          lines += s"  source: ${source}"
          lines += ""
      }
      lines.result().mkString("\n").trim
    }
  }

  private def _standard_configuration_sources_(cwd: java.nio.file.Path): ConfigurationSources = {
    val compatibility = ConfigurationSources.standard(cwd, applicationname = "cncf")
    val primary = ConfigurationSources.standard(cwd, applicationname = "textus")
    ConfigurationSources(compatibility.sources ++ primary.sources)
  }

  private def _variation_lines_(
    configSnapshot: String
  ): String = {
    val lines = Vector.newBuilder[String]
    lines += "Variation Points"
    lines += ""
    configSnapshot.split("\n").foreach { line =>
      if (line.trim.nonEmpty && !line.startsWith("Config Snapshot")) {
        if (!line.startsWith("  ")) {
          val parts = line.split("=", 2)
          if (parts.length == 2) {
            val key = parts(0).trim
            val value = parts(1).trim
            lines += s"- ${key}"
            lines += s"  value : ${value}"
          }
        } else if (line.trim.startsWith("source:")) {
          lines += s"  ${line.trim}"
          lines += ""
        }
      }
    }
    lines += ""
    lines += "Declared Runtime Variation Points"
    lines += ""
    _declared_runtime_variation_points.foreach {
      point =>
        lines += s"- key  : ${point.key}"
        lines += s"  value: ${point.value}"
        lines += s"  brief: ${point.brief}"
        lines += ""
    }
    lines.result().mkString("\n").trim
  }

  private def _declared_runtime_variation_points: Vector[_DeclaredVariationPoint] = {
    val defaults = ObservabilityEngine.ExecutionHistoryConfig()
    Vector(
      _DeclaredVariationPoint(
        key = RuntimeConfig.ExecutionHistoryRecentLimitKey,
        value = defaults.recentLimit.toString,
        brief = "Recent execution history size.",
        detail = "Number of most recent action execution records retained unconditionally for admin inspection."
      ),
      _DeclaredVariationPoint(
        key = RuntimeConfig.ExecutionHistoryFilteredLimitKey,
        value = defaults.filteredLimit.toString,
        brief = "Filtered execution history size.",
        detail = "Number of additional action execution records retained when they match configured debug filters."
      ),
      _DeclaredVariationPoint(
        key = RuntimeConfig.ExecutionHistoryFilterOperationContainsKey,
        value = "",
        brief = "Operation-name debug filter.",
        detail = "Comma-separated operation-name substrings. Matching executions are retained in the filtered history buffer."
      )
    )
  }

  private final case class _DeclaredVariationPoint(
    key: String,
    value: String,
    brief: String,
    detail: String
  ) {
    def toRecord: Record =
      Record.data(
        "key" -> key,
        "value" -> value,
        "brief" -> brief,
        "detail" -> detail
      )
  }

  private def _extension_lines_(
    comps: Seq[Component]
  ): String = {
    val lines = Vector.newBuilder[String]
    lines += "Extension Points"
    lines += ""
    comps.foreach { comp =>
      val origin = _component_origin_(comp)
      lines += s"- component: ${comp.name}"
      lines += s"  class : ${comp.getClass.getName}"
      lines += s"  origin: ${origin}"
      lines += ""
    }
    lines.result().mkString("\n").trim
  }

  private def _admin_entity_put(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    val args = core.action.arguments.map(x => x.name -> x.value).toMap
    for {
      componentName <- _required_string(args, "component")
      entityName <- _required_string(args, "entity")
      collection <- Consequence.fromOption(
        _component_by_name(subsystem, componentName).flatMap(_entity_collection(_, entityName)),
        s"Entity collection not found: ${entityName}"
      )
      _ <- collection.putRecord(_admin_entity_record(args))
    } yield OperationResponse.Scalar("Entity record was applied.")
  }

  private def _admin_entity_list(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    val args = core.action.arguments.map(x => x.name -> x.value).toMap
    for {
      componentName <- _required_string(args, "component")
      entityName <- _required_string(args, "entity")
      paging <- _paging(args)
      pagingDecision <- _paging_with_capability(paging, TotalCountCapability.Supported, s"entity.${entityName}")
      effectivePaging = pagingDecision.paging
      collection <- Consequence.fromOption(
        _component_by_name(subsystem, componentName).flatMap(_entity_collection(_, entityName)),
        s"Entity collection not found: ${entityName}"
      )
      result <- _admin_entity_search(core, collection, effectivePaging)
    } yield OperationResponse.RecordResponse(
      _list_response_record(
        "entity",
        componentName,
        entityName,
        _admin_entity_page(collection, result, effectivePaging, pagingDecision),
        effectivePaging
      )
    )
  }

  private def _admin_entity_search[A](
    core: ActionCall.Core,
    collection: EntityCollection[A],
    paging: _Paging
  ): Consequence[org.goldenport.cncf.directive.SearchResult[A]] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val query = EntityQuery.plan(
      Record.empty,
      limit = Some(paging.fetchPageSize),
      offset = Some(paging.offset),
      includeTotal = paging.wantsTotal
    )
    core.executionContext.entityStoreSpace.search(
      org.goldenport.cncf.unitofwork.UnitOfWorkOp.EntityStoreSearch(
        query = org.goldenport.cncf.entity.EntityQuery(collection.descriptor.collectionId, query),
        tc = collection.descriptor.persistent
      )
    )
  }

  private def _admin_entity_read(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    val args = core.action.arguments.map(x => x.name -> x.value).toMap
    for {
      componentName <- _required_string(args, "component")
      entityName <- _required_string(args, "entity")
      id <- _required_string(args, "id")
      collection <- Consequence.fromOption(
        _component_by_name(subsystem, componentName).flatMap(_entity_collection(_, entityName)),
        s"Entity collection not found: ${entityName}"
      )
      record <- Consequence.fromOption(_entity_record(collection, id), s"Entity record not found: ${id}")
    } yield OperationResponse.RecordResponse(
      _read_response_record(
        "entity",
        componentName,
        entityName,
        id,
        record
      )
    )
  }

  private def _admin_data_update(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val args = core.action.arguments.map(x => x.name -> x.value).toMap
    for {
      dataName <- _required_string(args, "data")
      id <- _required_string(args, "id")
      entry <- DataStore.EntryId.parse(id)
      ds <- subsystem.globalRuntimeContext.dataStoreSpace.dataStore(DataStore.CollectionId(dataName))
      _ <- ds.save(DataStore.CollectionId(dataName), entry, _admin_data_record(args))
    } yield OperationResponse.Scalar("Data record was applied.")
  }

  private def _admin_data_list(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val args = core.action.arguments.map(x => x.name -> x.value).toMap
    for {
      dataName <- _required_string(args, "data")
      paging <- _paging(args)
      capability <- subsystem.globalRuntimeContext.dataStoreSpace.totalCountCapability(DataStore.CollectionId(dataName))
      pagingDecision <- _paging_with_capability(paging, capability, s"data.${dataName}")
      effectivePaging = pagingDecision.paging
      result <- subsystem.globalRuntimeContext.dataStoreSpace.search(
        DataStore.CollectionId(dataName),
        QueryDirective(
          DataStoreQuery.Empty,
          limit = QueryLimit.Limit(effectivePaging.fetchPageSize),
          offset = effectivePaging.offset
        )
      )
      total <- if (effectivePaging.wantsTotal)
        subsystem.globalRuntimeContext.dataStoreSpace.count(DataStore.CollectionId(dataName), QueryDirective(DataStoreQuery.Empty)).map(Some(_))
      else
        Consequence.success(None)
    } yield OperationResponse.RecordResponse(
      _list_response_record(
        "data",
        "",
        dataName,
        _prefetched_page_values(_record_ids(result.records), effectivePaging, pagingDecision, total),
        effectivePaging
      )
    )
  }

  private def _admin_data_read(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val args = core.action.arguments.map(x => x.name -> x.value).toMap
    for {
      dataName <- _required_string(args, "data")
      id <- _required_string(args, "id")
      entry <- DataStore.EntryId.parse(id)
      ds <- subsystem.globalRuntimeContext.dataStoreSpace.dataStore(DataStore.CollectionId(dataName))
      record <- ds.load(DataStore.CollectionId(dataName), entry).flatMap {
        case Some(value) => Consequence.success(value)
        case None => Consequence.fromOption(None, s"Data record not found: ${id}")
      }
    } yield OperationResponse.RecordResponse(
      _read_response_record(
        "data",
        "",
        dataName,
        id,
        record
      )
    )
  }

  private def _admin_view_read(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    val args = core.action.arguments.map(x => x.name -> x.value).toMap
    for {
      componentName <- _required_string(args, "component")
      viewName <- _required_string(args, "view")
      paging <- _paging(args)
      component <- Consequence.fromOption(_component_by_name(subsystem, componentName), s"Component not found: ${componentName}")
      browser <- Consequence.fromOption(_view_browser(component, viewName), s"View browser not found: ${viewName}")
      capability <- browser.totalCountCapabilityWithContext(using core.executionContext)
      pagingDecision <- _paging_with_capability(paging, capability, s"view.${viewName}")
      effectivePaging = pagingDecision.paging
      values <- browser.query_with_context(EntityQuery.plan(Record.empty, limit = Some(effectivePaging.fetchPageSize), offset = Some(effectivePaging.offset)))(using core.executionContext)
      total <- if (effectivePaging.wantsTotal)
        browser.count_with_context(EntityQuery.plan(Record.empty))(using core.executionContext).map(Some(_))
      else
        Consequence.success(None)
    } yield OperationResponse.RecordResponse(
      _read_values_response_record(
        "view",
        componentName,
        viewName,
        _prefetched_page_values(values.map(x => Option(x).map(_.toString).getOrElse("")).toVector, effectivePaging, pagingDecision, total),
        effectivePaging
      )
    )
  }

  private def _admin_aggregate_read(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    val args = core.action.arguments.map(x => x.name -> x.value).toMap
    for {
      componentName <- _required_string(args, "component")
      aggregateName <- _required_string(args, "aggregate")
      paging <- _paging(args)
      component <- Consequence.fromOption(_component_by_name(subsystem, componentName), s"Component not found: ${componentName}")
      collection <- Consequence.fromOption(_aggregate_collection(component, aggregateName), s"Aggregate collection not found: ${aggregateName}")
      capability <- collection.totalCountCapabilityWithContext(using core.executionContext)
      pagingDecision <- _paging_with_capability(paging, capability, s"aggregate.${aggregateName}")
      effectivePaging = pagingDecision.paging
      values <- collection.query_with_context(EntityQuery.plan(Record.empty, limit = Some(effectivePaging.fetchPageSize), offset = Some(effectivePaging.offset)))(using core.executionContext)
      total <- if (effectivePaging.wantsTotal)
        collection.count_with_context(EntityQuery.plan(Record.empty))(using core.executionContext).map(Some(_))
      else
        Consequence.success(None)
    } yield OperationResponse.RecordResponse(
      _read_values_response_record(
        "aggregate",
        componentName,
        aggregateName,
        _prefetched_page_values(values.map(x => Option(x).map(_.toString).getOrElse("")).toVector, effectivePaging, pagingDecision, total),
        effectivePaging
      )
    )
  }

  private def _admin_data_create(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val args = core.action.arguments.map(x => x.name -> x.value).toMap
    for {
      dataName <- _required_string(args, "data")
      id <- _required_string(args, "id")
      entry <- DataStore.EntryId.parse(id)
      ds <- subsystem.globalRuntimeContext.dataStoreSpace.dataStore(DataStore.CollectionId(dataName))
      _ <- ds.create(DataStore.CollectionId(dataName), entry, _admin_data_record(args))
    } yield OperationResponse.Scalar("Data record was applied.")
  }

  private def _required_string(
    args: Map[String, Any],
    key: String
  ): Consequence[String] =
    args.get(key).map(_.toString).filter(_.nonEmpty) match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.argumentMissing(key)
    }

  private final case class _Paging(
    page: Int,
    pageSize: Int,
    includeTotal: Boolean,
    totalCountPolicy: _TotalCountPolicy
  ) {
    def offset: Int = (page - 1) * pageSize
    def fetchPageSize: Int = pageSize + 1
    def fetchSize: Int = offset + fetchPageSize
    def wantsTotal: Boolean = includeTotal && totalCountPolicy.allowsTotal
  }

  private def _paging(
    args: Map[String, Any]
  ): Consequence[_Paging] =
    for {
      page <- _positive_int_arg(args, "page", 1)
      pageSize <- _positive_int_arg(args, "pageSize", 20)
      policy <- _total_count_policy(args)
    } yield _Paging(page, pageSize, _boolean_arg(args, "includeTotal", false), policy)

  private enum _TotalCountPolicy {
    case Disabled
    case Optional
    case Required

    def allowsTotal: Boolean =
      this != Disabled
  }

  private final case class _PagingCapabilityDecision(
    paging: _Paging,
    warning: Option[String],
    unavailableReason: Option[String]
  )

  private def _paging_decision_default(
    paging: _Paging
  ): _PagingCapabilityDecision =
    _PagingCapabilityDecision(paging, None, None)

  private def _paging_with_capability(
    paging: _Paging,
    capability: TotalCountCapability,
    surfaceName: String
  ): Consequence[_PagingCapabilityDecision] =
    if (!paging.wantsTotal)
      Consequence.success(_PagingCapabilityDecision(paging, None, None))
    else if (capability.supportsTotalCount)
      Consequence.success(_PagingCapabilityDecision(paging, None, None))
    else
      paging.totalCountPolicy match {
        case _TotalCountPolicy.Required =>
          Consequence.argumentInvalid(s"total count is required but not supported for ${surfaceName}: ${capability}")
        case _ =>
          val reason = capability.toString.toLowerCase
          Consequence.success(_PagingCapabilityDecision(
            paging.copy(includeTotal = false),
            Some(s"total count is not available for ${surfaceName}: ${reason}"),
            Some(reason)
          ))
      }

  private def _total_count_policy(
    args: Map[String, Any]
  ): Consequence[_TotalCountPolicy] =
    args.get("totalCountPolicy").map(_.toString.trim.toLowerCase).filter(_.nonEmpty) match {
      case Some("disabled" | "none" | "false" | "off") =>
        Consequence.success(_TotalCountPolicy.Disabled)
      case Some("optional" | "best-effort" | "besteffort" | "true" | "on") =>
        Consequence.success(_TotalCountPolicy.Optional)
      case Some("required" | "require") =>
        Consequence.success(_TotalCountPolicy.Required)
      case Some(value) =>
        Consequence.argumentInvalid(s"totalCountPolicy must be disabled, optional, or required: ${value}")
      case None =>
        Consequence.success(_TotalCountPolicy.Disabled)
    }

  private def _positive_int_arg(
    args: Map[String, Any],
    key: String,
    default: Int
  ): Consequence[Int] =
    args.get(key).map(_.toString).filter(_.nonEmpty) match {
      case Some(value) =>
        value.toIntOption match {
          case Some(n) if n > 0 =>
            Consequence.success(n)
          case _ =>
            Consequence.argumentInvalid(s"${key} must be a positive integer")
        }
      case None =>
        Consequence.success(default)
    }

  private def _boolean_arg(
    args: Map[String, Any],
    key: String,
    default: Boolean
  ): Boolean =
    args.get(key).map(_.toString.trim.toLowerCase).filter(_.nonEmpty) match {
      case Some("true" | "yes" | "on" | "1") => true
      case Some("false" | "no" | "off" | "0") => false
      case Some(_) => default
      case None => default
    }

  private def _admin_entity_record(
    args: Map[String, Any]
  ): Record =
    Record.create(args.filterNot {
      case (key, _) => key == "component" || key == "entity"
    }.toVector)

  private def _admin_data_record(
    args: Map[String, Any]
  ): Record =
    Record.create(args.filterNot {
      case (key, _) => key == "component" || key == "data"
    }.toVector)

  private def _entity_ids[A](
    collection: EntityCollection[A]
  ): Vector[String] =
    _entity_values(collection)
      .map(x => collection.descriptor.persistent.id(x).value)
      .toVector

  private def _entity_record[A](
    collection: EntityCollection[A],
    id: String
  ): Option[Record] =
    _entity_values(collection)
      .find(x => collection.descriptor.persistent.id(x).value == id)
      .map(x => collection.descriptor.persistent.toRecord(x))

  private def _entity_values[A](
    collection: EntityCollection[A]
  ): Vector[A] =
    collection.storage.memoryRealm.map(_.values).getOrElse(collection.storage.storeRealm.values)

  private def _record_ids(
    records: Vector[Record]
  ): Vector[String] =
    records.map(x => x.getString("id").getOrElse(x.getAny("id").map(_.toString).getOrElse("unknown")))

  private def _page_values[A](
    values: Vector[A],
    paging: _Paging,
    decision: _PagingCapabilityDecision
  ): _Page[A] = {
    val source = values.drop(paging.offset)
    val visible = source.take(paging.pageSize)
    _Page(
      visible,
      source.size > paging.pageSize,
      if (paging.wantsTotal) Some(values.size) else None,
      decision.warning,
      decision.unavailableReason
    )
  }

  private def _search_result_page[A](
    collection: EntityCollection[A],
    result: org.goldenport.cncf.directive.SearchResult[A],
    paging: _Paging,
    decision: _PagingCapabilityDecision
  ): _Page[String] = {
    val ids = result.data.map(x => collection.descriptor.persistent.id(x).value)
    _Page(
      ids.take(paging.pageSize),
      result.data.size > paging.pageSize,
      if (paging.wantsTotal) result.totalCount else None,
      decision.warning,
      decision.unavailableReason
    )
  }

  private def _admin_entity_page[A](
    collection: EntityCollection[A],
    result: org.goldenport.cncf.directive.SearchResult[A],
    paging: _Paging,
    decision: _PagingCapabilityDecision
  ): _Page[String] =
    if (result.nonEmpty || _entity_values(collection).isEmpty)
      _search_result_page(collection, result, paging, decision)
    else
      _page_values(_entity_ids(collection), paging, decision)

  private def _prefetched_page_values[A](
    values: Vector[A],
    paging: _Paging,
    decision: _PagingCapabilityDecision,
    total: Option[Int] = None
  ): _Page[A] = {
    val source = values
    val visible = source.take(paging.pageSize)
    _Page(
      visible,
      source.size > paging.pageSize,
      if (paging.wantsTotal) total else None,
      decision.warning,
      decision.unavailableReason
    )
  }

  private final case class _Page[A](
    values: Vector[A],
    hasNext: Boolean,
    total: Option[Int],
    warning: Option[String],
    unavailableReason: Option[String]
  )

  private def _with_optional_total(
    base: Vector[(String, Any)],
    page: _Page[?]
  ): Vector[(String, Any)] =
    base ++
      page.total.map(total => Vector("total" -> total, "totalAvailable" -> true)).getOrElse(Vector("totalAvailable" -> false)) ++
      page.unavailableReason.map(reason => Vector("totalUnavailableReason" -> reason)).getOrElse(Vector.empty) ++
      page.warning.map(warning => Vector("warnings" -> Vector(warning))).getOrElse(Vector.empty)

  private def _list_response_record(
    kind: String,
    componentName: String,
    collectionName: String,
    page: _Page[String],
    paging: _Paging
  ): Record =
    Record.dataAuto(
      _with_optional_total(
        Vector(
          "kind" -> s"${kind}.list",
          "component" -> componentName,
          "collection" -> collectionName,
          "ids" -> page.values,
          "page" -> paging.page,
          "pageSize" -> paging.pageSize,
          "hasNext" -> page.hasNext
        ),
        page
      )*
    )

  private def _read_response_record(
    kind: String,
    componentName: String,
    collectionName: String,
    id: String,
    record: Record
  ): Record =
    Record.dataAuto(
      "kind" -> s"${kind}.read",
      "component" -> componentName,
      "collection" -> collectionName,
      "id" -> id,
      "record" -> record,
      "fields" -> _record_text(record)
    )

  private def _read_values_response_record(
    kind: String,
    componentName: String,
    collectionName: String,
    page: _Page[String],
    paging: _Paging
  ): Record =
    Record.dataAuto(
      _with_optional_total(
        Vector(
          "kind" -> s"${kind}.read",
          "component" -> componentName,
          "collection" -> collectionName,
          "values" -> page.values,
          "fields" -> page.values.mkString("\n"),
          "page" -> paging.page,
          "pageSize" -> paging.pageSize,
          "hasNext" -> page.hasNext
        ),
        page
      )*
    )

  private def _record_text(
    record: Record
  ): String =
    record.asMap.toVector.sortBy(_._1).map {
      case (key, value) => s"${key}=${Option(value).map(_.toString).getOrElse("")}"
    }.mkString("\n")

  private def _component_by_name(
    subsystem: Subsystem,
    name: String
  ): Option[Component] =
    subsystem.components.find(x => NamingConventions.equivalentByNormalized(x.name, name))

  private def _entity_collection(
    component: Component,
    entity: String
  ): Option[EntityCollection[?]] =
    component.entitySpace.entityOption[Any](entity).orElse {
      component.componentDescriptors
        .flatMap(_.entityRuntimeDescriptors)
        .find(x => NamingConventions.equivalentByNormalized(x.entityName, entity))
        .flatMap(x => component.entitySpace.entityOption(x.collectionId))
    }

  private def _view_browser(
    component: Component,
    viewName: String
  ) =
    component.viewSpace.browserOption[Any](viewName).orElse {
      component.viewDefinitions
        .find(x => NamingConventions.equivalentByNormalized(x.name, viewName))
        .flatMap(x => component.viewSpace.browserOption[Any](x.name))
    }

  private def _aggregate_collection(
    component: Component,
    aggregateName: String
  ) =
    component.aggregateSpace.collectionOption[Any](aggregateName).orElse {
      component.aggregateDefinitions
        .find(x => NamingConventions.equivalentByNormalized(x.name, aggregateName))
        .flatMap(x => component.aggregateSpace.collectionOption[Any](x.name))
    }

  private def _value_(value: ConfigurationValue): String = {
    value match {
      case ConfigurationValue.StringValue(v) => v
      case ConfigurationValue.NumberValue(v) => v.toString
      case ConfigurationValue.BooleanValue(v) => v.toString
      case ConfigurationValue.ListValue(vs) => vs.map(_value_).mkString("[", ", ", "]")
      case ConfigurationValue.ObjectValue(vs) =>
        vs.map { case (k, v) => s"${k}=${_value_(v)}" }.mkString("{", ", ", "}")
      case ConfigurationValue.NullValue => "null"
    }
  }

  private def _origin_(origin: ConfigurationOrigin): String = {
    origin match {
      case ConfigurationOrigin.Arguments => "cli"
      case ConfigurationOrigin.Environment => "env"
      case ConfigurationOrigin.Default => "default"
      case ConfigurationOrigin.Home => "file"
      case ConfigurationOrigin.Project => "file"
      case ConfigurationOrigin.Cwd => "file"
      case ConfigurationOrigin.Resource => "resource"
    }
  }
}
