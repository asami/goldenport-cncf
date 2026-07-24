package org.goldenport.cncf.component.builtin.admin

import cats.data.NonEmptyVector
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.cncf.action.{
  Action,
  ActionCall,
  CommandAction,
  CommandExecutionMode,
  ProcedureActionCall,
  QueryAction,
  ResourceAccess
}
import org.goldenport.cncf.association.{
  Association,
  AssociationBindingWorkflow,
  AssociationDomain,
  AssociationFilter,
  AssociationRecordCodec,
  AssociationRepository,
  AssociationStoragePolicy,
  AssociationTargetValidator
}
import org.goldenport.cncf.blob.{
  Blob,
  BlobAttachmentWorkflow,
  BlobPayloadSupport,
  BlobProjection,
  BlobRepository
}
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
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.datastore.{
  DataStore,
  Query as DataStoreQuery,
  QueryDirective,
  QueryLimit,
  TotalCountCapability
}
import org.goldenport.cncf.directive.Query as EntityQuery
import org.goldenport.cncf.entity.{
  EntityMutationExpectation,
  EntityPersistable,
  EntityPersistent,
  EntityQuery as StoreEntityQuery,
  EntitySearchScope
}
import org.goldenport.cncf.entity.runtime.{EntityCollection, EntityQueryFieldResolver}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.observability.ObservabilityEngine
import org.goldenport.cncf.operation.{
  AssociationBindingOperationDefinition,
  CmlOperationAssociationBinding
}
import org.goldenport.cncf.projection.{
  SecurityDeploymentMarkdownProjection,
  SecurityDeploymentProjection
}
import org.goldenport.cncf.search.{SearchPlanningProfile, WebSearchQueryPlanner}
import org.goldenport.cncf.security.{
  AdminAuthorizationPolicy,
  EntityAccessMode,
  OperationAuthorizationProvider,
  OperationAuthorizationRule
}
import org.goldenport.cncf.spi.SpiSocket
import org.goldenport.cncf.subsystem.{GenericSubsystemAssemblyDescriptorSource, Subsystem}
import org.goldenport.cncf.unitofwork.{UnitOfWorkAuthorization, UnitOfWorkOp}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.handler.egress.{EgressCollection, RestEgress}
import org.goldenport.protocol.handler.ingress.{IngressCollection, RestIngress}
import org.goldenport.protocol.handler.projection.ProjectionCollection
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.{Record, RecordPresentable}
import org.goldenport.value.BaseContent
import org.goldenport.schema.{DataType, XString}
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

/*
 * @since   Jan.  7, 2026
 *  version Jan. 20, 2026
 *  version Feb. 19, 2026
 *  version May. 31, 2026
 *  version Jun. 18, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
class AdminComponent() extends Component {}

object AdminComponent {
  val name: String = "admin"
  val componentId = ComponentId(name) // TODO static

  private trait AdminOperationAuthorization extends OperationAuthorizationProvider {
    def operationAuthorization(
      runtimeConfig: RuntimeConfig
    ): OperationAuthorizationRule =
      AdminAuthorizationPolicy.operationRule("admin.system", runtimeConfig)
  }

  object Factory extends Component.SinglePrimaryBundleFactory {
    protected def create_Component(params: ComponentCreate): Component =
      AdminComponent()

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val request = spec.RequestDefinition()
      val opping =
        new PingOperationDefinition(request, spec.ResponseDefinition(result = List(XString)))
      val opcomponentlist = new ComponentListOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opconfigshow = new ConfigShowOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opvariationlist = new VariationListOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opvariationdescribe = new VariationDescribeOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val opextensionlist = new ExtensionListOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opdeploymentsecuritymermaid = new DeploymentSecurityMermaidOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opdeploymentsecuritymarkdown = new DeploymentSecurityMarkdownOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opassemblywarnings = new AssemblyWarningsOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opassemblyreport = new AssemblyReportOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opassemblydescriptor = new AssemblyDescriptorOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opassemblydiagram = new AssemblyDiagramOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opexecutioncalltree = new ExecutionCalltreeOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val opexecutionhistory = new ExecutionHistoryOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val opexecutiondiagnostics = new ExecutionDiagnosticsOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val opentitycreate = new EntityCreateOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opentityupdate = new EntityUpdateOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opentitylist = new EntityListOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opentityread = new EntityReadOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opdatacreate = new DataCreateOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opdataupdate = new DataUpdateOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opdatalist = new DataListOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opdataread = new DataReadOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(XString)),
        params.subsystem
      )
      val opviewread = new ViewReadOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opaggregateread = new AggregateReadOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opassociationlist = new AssociationListOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opassociationattach = new AssociationAttachOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val opassociationdetach = new AssociationDetachOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record"))),
        params.subsystem
      )
      val servicesystem = spec.ServiceDefinition(
        name = "system",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opping)
        )
      )
      val servicecomponent = spec.ServiceDefinition(
        name = "component",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opcomponentlist)
        )
      )
      val serviceconfig = spec.ServiceDefinition(
        name = "config",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opconfigshow)
        )
      )
      val servicevariation = spec.ServiceDefinition(
        name = "variation",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opvariationlist,
            opvariationdescribe
          )
        )
      )
      val serviceextension = spec.ServiceDefinition(
        name = "extension",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opextensionlist)
        )
      )
      val servicedeployment = spec.ServiceDefinition(
        name = "deployment",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opdeploymentsecuritymermaid,
            opdeploymentsecuritymarkdown
          )
        )
      )
      val serviceassembly = spec.ServiceDefinition(
        name = "assembly",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opassemblywarnings,
            opassemblyreport,
            opassemblydescriptor,
            opassemblydiagram
          )
        )
      )
      val serviceexecution = spec.ServiceDefinition(
        name = "execution",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opexecutiondiagnostics,
            opexecutionhistory,
            opexecutioncalltree
          )
        )
      )
      val serviceentity = spec.ServiceDefinition(
        name = "entity",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opentitylist,
            opentityread,
            opentitycreate,
            opentityupdate
          )
        )
      )
      val servicedata = spec.ServiceDefinition(
        name = "data",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opdatalist,
            opdataread,
            opdatacreate,
            opdataupdate
          )
        )
      )
      val serviceview = spec.ServiceDefinition(
        name = "view",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opviewread)
        )
      )
      val serviceaggregate = spec.ServiceDefinition(
        name = "aggregate",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(opaggregateread)
        )
      )
      val serviceassociation = spec.ServiceDefinition(
        name = "association",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            opassociationlist,
            opassociationattach,
            opassociationdetach
          )
        )
      )
      val services = spec.ServiceDefinitionGroup(
        services = Vector(
          servicesystem,
          servicecomponent,
          serviceconfig,
          servicevariation,
          serviceextension,
          servicedeployment,
          serviceassembly,
          serviceexecution,
          serviceentity,
          servicedata,
          serviceview,
          serviceaggregate,
          serviceassociation
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "list",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(ComponentListAction(req, subsystem))
    }

  private final class VariationListOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "list",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(ExtensionListAction(req, subsystem))
    }

  private final class ConfigShowOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "show",
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(ConfigShowAction(req, subsystem))
    }

  private final class DeploymentSecurityMermaidOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("securityMermaid")
          .summary("Render the subsystem security deployment diagram as Mermaid.")
          .description(
            "Project the resolved security wiring into a minimal Mermaid deployment diagram with ingress, providers, SecurityContext, ActionCall, and UnitOfWork."
          )
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("securityMarkdown")
          .summary("Render the subsystem security deployment specification as Markdown.")
          .description(
            "Project the resolved security wiring into an editable Markdown specification draft including Mermaid, provider metadata, and framework chokepoints."
          )
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("warnings")
          .summary("Show assembly warnings detected during component and subsystem loading.")
          .description(
            "Return duplicate-component and related assembly warnings captured during runtime assembly."
          )
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("report")
          .summary("Show the resolved assembly report for the selected subsystem.")
          .description(
            "Return subsystem descriptor wiring, loaded component origins, and assembly warnings captured during runtime assembly."
          )
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("descriptor")
          .summary("Export the resolved assembly descriptor for the selected subsystem.")
          .description(
            "Return a descriptor-oriented document for the runtime-resolved assembly, suitable for review and later descriptor export."
          )
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("diagram")
          .summary("Export the resolved assembly wiring diagram.")
          .description(
            "Return a web-renderable Mermaid projection of the runtime-resolved assembly wiring."
          )
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("calltree")
          .summary("Show the latest retained execution calltree.")
          .description(
            "Return the latest finalized operation calltree retained by the runtime for admin inspection."
          )
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("history")
          .summary("Show retained action execution history.")
          .description(
            "Return retained action execution records including parameters, result summaries, and calltree projections when captured."
          )
          .build(),
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(ExecutionHistoryAction(req))
  }

  private final class ExecutionDiagnosticsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        content = BaseContent.Builder("diagnostics")
          .summary("Show the canonical event/job diagnostics entry points for operators.")
          .description(
            "Return authoritative selectors, runtime field contracts, and short guidance for Phase 13 event/job inspection without duplicating the underlying event and job surfaces."
          )
          .build(),
        request = request,
        response = response
      )

    def createOperationRequest(
      req: Request
    ): Consequence[OperationRequest] =
      Consequence.success(ExecutionDiagnosticsAction(req))
  }

  private final class EntityCreateOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = "list", request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(EntityListAction(req, subsystem))
  }

  private final class EntityReadOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = "read", request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(EntityReadAction(req, subsystem))
  }

  private final class DataCreateOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
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
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = "list", request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DataListAction(req, subsystem))
  }

  private final class DataReadOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = "read", request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(DataReadAction(req, subsystem))
  }

  private final class ViewReadOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = "read", request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(ViewReadAction(req, subsystem))
  }

  private final class AggregateReadOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition with AdminOperationAuthorization {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = "read", request = request, response = response)

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(AggregateReadAction(req, subsystem))
  }

  private trait AdminAssociationOperationAuthorization extends OperationAuthorizationProvider {
    def operationAuthorization(
      runtimeConfig: RuntimeConfig
    ): OperationAuthorizationRule =
      AdminAuthorizationPolicy.operationRule("admin.entity", runtimeConfig)
  }

  private final class AssociationListOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition with AdminAssociationOperationAuthorization
      with AssociationBindingOperationDefinition {
    val associationBinding: CmlOperationAssociationBinding =
      CmlOperationAssociationBinding(
        domain = "association",
        targetKind = "entity",
        parameters = Vector("domain", "sourceEntityId", "targetEntityId", "targetKind", "role")
      )

    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admin_list_associations",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(AssociationListAction(req, subsystem))
  }

  private final class AssociationAttachOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition with AdminAssociationOperationAuthorization
      with AssociationBindingOperationDefinition {
    val associationBinding: CmlOperationAssociationBinding =
      CmlOperationAssociationBinding(
        domain = "association",
        targetKind = "entity",
        createsAssociation = true,
        roles = Vector("related"),
        parameters =
          Vector("domain", "sourceEntityId", "targetEntityId", "targetKind", "role", "sortOrder"),
        sourceEntityIdParameters = Vector("sourceEntityId"),
        targetIdParameters = Vector("targetEntityId"),
        sortOrderParameters = Vector("sortOrder")
      )

    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admin_attach_association",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(AssociationAttachAction(req, subsystem))
  }

  private final class AssociationDetachOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition,
    subsystem: Subsystem
  ) extends spec.OperationDefinition with AdminAssociationOperationAuthorization
      with AssociationBindingOperationDefinition {
    val associationBinding: CmlOperationAssociationBinding =
      CmlOperationAssociationBinding(
        domain = "association",
        targetKind = "entity",
        detachesAssociation = true,
        parameters = Vector("domain", "sourceEntityId", "targetEntityId", "targetKind", "role"),
        sourceEntityIdMode = CmlOperationAssociationBinding.SourceEntityIdModeParameter,
        sourceEntityIdParameters = Vector("sourceEntityId"),
        targetIdParameters = Vector("targetEntityId")
      )

    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admin_detach_association",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(AssociationDetachAction(req, subsystem))
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
      val text = _component_lines(comps, "Components")
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
    def execute(): Consequence[OperationResponse] =
      _config_snapshot().map { text =>
        OperationResponse.Scalar(text)
      }
    }

  private final case class EntityCreateAction(
    request: Request,
    subsystem: Subsystem
  ) extends CommandAction() {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.Sync

    def createCall(core: ActionCall.Core): ActionCall =
      EntityCreateActionCall(core, subsystem)
  }

  private final case class EntityUpdateAction(
    request: Request,
    subsystem: Subsystem
  ) extends CommandAction() {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.Sync

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
      _admin_entity_put("create", core, subsystem)
  }

  private final case class EntityUpdateActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_entity_put("update", core, subsystem)
  }

  private final case class DataCreateAction(
    request: Request,
    subsystem: Subsystem
  ) extends CommandAction() {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.Sync

    def createCall(core: ActionCall.Core): ActionCall =
      DataCreateActionCall(core, subsystem)
  }

  private final case class DataUpdateAction(
    request: Request,
    subsystem: Subsystem
  ) extends CommandAction() {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.Sync

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

  private final case class AssociationListAction(
    request: Request,
    subsystem: Subsystem
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      AssociationListActionCall(core, subsystem)
  }

  private final case class AssociationAttachAction(
    request: Request,
    subsystem: Subsystem
  ) extends CommandAction() {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.Sync

    def createCall(core: ActionCall.Core): ActionCall =
      AssociationAttachActionCall(core, subsystem)
  }

  private final case class AssociationDetachAction(
    request: Request,
    subsystem: Subsystem
  ) extends CommandAction() {
    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.Sync

    def createCall(core: ActionCall.Core): ActionCall =
      AssociationDetachActionCall(core, subsystem)
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

  private final case class AssociationListActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_association_list(core, subsystem)
  }

  private final case class AssociationAttachActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_association_attach(core, subsystem)
  }

  private final case class AssociationDetachActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _admin_association_detach(core, subsystem)
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
      Consequence.success(
        OperationResponse.Scalar(SecurityDeploymentProjection.projectMermaid(subsystem))
      )
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
      ExecutionCalltreeActionCall(core, request)
  }

  private final case class ExecutionHistoryAction(
    request: Request
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      ExecutionHistoryActionCall(core, request)
  }

  private final case class ExecutionDiagnosticsAction(
    request: Request
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      ExecutionDiagnosticsActionCall(core)
  }

  private final case class DeploymentSecurityMarkdownActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      Consequence.success(
        OperationResponse.Scalar(SecurityDeploymentMarkdownProjection.project(subsystem))
      )
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
      val wiring = _subsystem_wiring(subsystem)
      val ports = subsystem.descriptor.map(_.declaredPorts).getOrElse(Vector.empty)
      val wiringbindings =
        subsystem.descriptor.map(_.resolvedWiringBindings).getOrElse(Vector.empty)
      val spisockets = _spi_socket_records(subsystem)
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
        "wiring_bindings" -> wiringbindings,
        "spi_sockets" -> spisockets,
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
      val descriptorcomponents = components.filterNot(_.origin == ComponentOrigin.Builtin)
      val builtincomponents    = components.filter(_.origin == ComponentOrigin.Builtin)
      val sourcewiring = _subsystem_wiring(subsystem)
      val resolvedwiring =
        subsystem.descriptor.map(_.resolvedWiringBindings).getOrElse(Vector.empty)
      val descriptor = org.goldenport.record.Record.data(
        "kind" -> "assembly-descriptor",
        "subsystem" -> subsystem.name,
        "version" -> subsystem.version.getOrElse(""),
        "components" -> descriptorcomponents.map(_assembly_component_record),
        "ports" -> subsystem.descriptor.map(_.declaredPorts).getOrElse(Vector.empty),
        "wiring" -> resolvedwiring,
        "source" -> org.goldenport.record.Record.data(
          "wiring" -> sourcewiring,
          "assembly_descriptor" -> subsystem.descriptor.flatMap(_.assemblyDescriptor).map(
            _assembly_descriptor_source_record
          ).getOrElse(Record.empty)
        ),
        "runtime" -> org.goldenport.record.Record.data(
          "builtin_components" -> builtincomponents.map(_assembly_component_record)
        ),
        "diagnostics" -> org.goldenport.record.Record.data(
          "warnings" -> warnings
        )
      )
      Consequence.success(OperationResponse.RecordResponse(descriptor))
    }
  }

  private def _spi_socket_records(
    subsystem: Subsystem
  ): Vector[Record] =
    subsystem.components.toVector.collect {
      case socket: SpiSocket[?] =>
        val component = socket.asInstanceOf[Component]
        val contract = socket.spiContract
        Record.data(
          "component" -> component.name,
          "contract" -> contract.name,
          "runtime_class" -> contract.runtimeClass.getName,
          "installed" -> socket.isSpiInstalled
        )
    }

  private final case class AssemblyDiagramActionCall(
    core: ActionCall.Core,
    subsystem: Subsystem
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.Scalar(_assembly_mermaid(subsystem)))
  }

  private final case class ExecutionCalltreeActionCall(
    core: ActionCall.Core,
    calltreeRequest: Request
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      val originslot = _request_value(calltreeRequest, "originSlot")
        .orElse(_request_value(calltreeRequest, "origin_slot"))
        .orElse(_request_value(calltreeRequest, "requestKind"))
        .orElse(_request_value(calltreeRequest, "request_kind"))
      val executionid = _request_value(calltreeRequest, "executionId")
        .orElse(_request_value(calltreeRequest, "execution_id"))
      val traceid = _request_value(calltreeRequest, "traceId")
        .orElse(_request_value(calltreeRequest, "trace_id"))
      val record = ObservabilityEngine.findExecution(executionid, traceid, originslot)
        .orElse(ObservabilityEngine.latestExecution(originslot))
        .map(_.calltreeRecord)
        .getOrElse(
          Record.data(
            "status" -> "empty",
            "message" -> originslot
              .map(slot => s"No retained action execution is available for origin slot: $slot.")
              .getOrElse("No retained non-background action execution is available.")
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
      val operationfilter = _request_value(historyRequest, "operation")
        .orElse(_request_value(historyRequest, "operation_contains"))
      val originslot = _request_value(historyRequest, "originSlot")
        .orElse(_request_value(historyRequest, "origin_slot"))
        .orElse(_request_value(historyRequest, "requestKind"))
        .orElse(_request_value(historyRequest, "request_kind"))
      val executionid = _request_value(historyRequest, "executionId")
        .orElse(_request_value(historyRequest, "execution_id"))
      val traceid = _request_value(historyRequest, "traceId")
        .orElse(_request_value(historyRequest, "trace_id"))
      val entries = executionid.orElse(traceid) match {
        case Some(_) =>
          ObservabilityEngine.findExecution(executionid, traceid, originslot).toVector
        case None =>
          ObservabilityEngine.executionHistory(operationfilter, originslot)
      }
      val config = ObservabilityEngine.executionHistoryConfig
      val record = Record.data(
        "recent_limit" -> config.recentLimit,
        "filtered_limit" -> config.filteredLimit,
        "filter_count" -> config.filters.size,
        "operation_filter" -> operationfilter.getOrElse(""),
        "origin_slot_filter" -> originslot.getOrElse(""),
        "trace_id_filter" -> traceid.getOrElse(""),
        "execution_id_filter" -> executionid.getOrElse(""),
        "count" -> entries.size,
        "executions" -> entries.map(_.toRecord)
      )
      Consequence.success(OperationResponse.RecordResponse(record))
    }
  }

  private final case class ExecutionDiagnosticsActionCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      Consequence.success(OperationResponse.RecordResponse(_execution_diagnostics_record()))
  }

  private def _request_value(request: Request, name: String): Option[String] =
    (request.properties.find(_.name.equalsIgnoreCase(name)) orElse
      request.arguments.find(_.name.equalsIgnoreCase(name)))
      .map(_.value.toString.trim)
      .filter(_.nonEmpty)

  private def _execution_diagnostics_record(): Record =
    Record.data(
      "kind" -> "phase-13-execution-diagnostics",
      "summary" -> "Use workflow surfaces for workflow definitions and instances; use event surfaces for queued dispatch contract and persisted event metadata; use job surfaces for final child-job lineage and async failure disposition.",
      "routes" -> Vector(
        Record.data(
          "selector" -> "workflow.workflow.list_workflow_definitions",
          "surface" -> "workflow",
          "role" -> "authoritative-detail",
          "summary" -> "List workflow definitions and registrations."
        ),
        Record.data(
          "selector" -> "workflow.workflow.describe_workflow_definition",
          "surface" -> "workflow",
          "role" -> "authoritative-detail",
          "summary" -> "Inspect one workflow definition with status rules."
        ),
        Record.data(
          "selector" -> "workflow.workflow.list_workflow_instances",
          "surface" -> "workflow",
          "role" -> "authoritative-detail",
          "summary" -> "List workflow instances and related job ids."
        ),
        Record.data(
          "selector" -> "workflow.workflow.get_workflow_instance",
          "surface" -> "workflow",
          "role" -> "authoritative-detail",
          "summary" -> "Inspect one workflow instance and job cross-links."
        ),
        Record.data(
          "selector" -> "workflow.workflow.load_workflow_history",
          "surface" -> "workflow",
          "role" -> "authoritative-detail",
          "summary" -> "Inspect workflow history entries and selected actions."
        ),
        Record.data(
          "selector" -> "event.event.search_event",
          "surface" -> "event",
          "role" -> "authoritative-detail",
          "summary" -> "Search persisted event records with dispatch metadata."
        ),
        Record.data(
          "selector" -> "event.event.load_event",
          "surface" -> "event",
          "role" -> "authoritative-detail",
          "summary" -> "Inspect one persisted event record including policy source, dispatch status, and event history."
        ),
        Record.data(
          "selector" -> "event.event_admin.load_job_events",
          "surface" -> "event_admin",
          "role" -> "cross-link",
          "summary" -> "Inspect event records associated with one job."
        ),
        Record.data(
          "selector" -> "job_control.job.get_job_status",
          "surface" -> "job",
          "role" -> "authoritative-detail",
          "summary" -> "Inspect structured lineage and final async failure disposition for one job."
        ),
        Record.data(
          "selector" -> "job_control.job.load_job_history",
          "surface" -> "job",
          "role" -> "authoritative-detail",
          "summary" -> "Inspect the retained timeline for one job."
        ),
        Record.data(
          "selector" -> "job_control.job.get_job_result",
          "surface" -> "job",
          "role" -> "authoritative-detail",
          "summary" -> "Inspect final job result payload or failure."
        ),
        Record.data(
          "selector" -> "job_control.job.await_job_result",
          "surface" -> "job",
          "role" -> "operator-await",
          "summary" -> "Await final job result through the authoritative job surface."
        ),
        Record.data(
          "selector" -> "job_control.job_admin.load_job_events",
          "surface" -> "job_admin",
          "role" -> "cross-link",
          "summary" -> "Inspect event records associated with one job from the job-control side."
        )
      ),
      "event-fields" -> Vector(
        "reception-rule",
        "reception-policy",
        "policy-source",
        "saga-id",
        "task-relation",
        "transaction-relation",
        "failure-policy",
        "failure-disposition-base",
        "dispatch-kind",
        "dispatch-status",
        "source-subsystem",
        "source-component",
        "target-subsystem",
        "target-component"
      ),
      "job-fields" -> Vector(
        "reception-rule",
        "reception-policy",
        "policy-source",
        "job-relation",
        "task-relation",
        "transaction-relation",
        "saga-id",
        "saga-relation",
        "failure-policy",
        "failure-disposition",
        "retry-kind",
        "retry-attempt-count",
        "retry-max-attempts",
        "retry-next-due-at",
        "retry-exhausted",
        "recovery-required",
        "dead-letter",
        "poison",
        "source-subsystem",
        "source-component",
        "target-subsystem",
        "target-component"
      ),
      "workflow-surface" -> Record.data(
        "selector" -> "workflow.workflow.list_workflow_instances",
        "summary" -> "Authoritative detail for workflow definitions, instances, progress, and related job ids."
      ),
      "event-surface" -> Record.data(
        "selector" -> "event.event.load_event",
        "summary" -> "Authoritative detail for queued dispatch contract and persisted event metadata, including dead-letter and poison outcomes."
      ),
      "job-surface" -> Record.data(
        "selector" -> "job_control.job.get_job_status",
        "summary" -> "Authoritative detail for final child-job lineage, async failure disposition, and retry recovery state."
      )
    )

  private def _assembly_component_record(comp: Component): Record =
    org.goldenport.record.Record.data(
      "name" -> comp.name,
      "origin" -> ComponentOriginLabel.userLabel(comp.origin.label)
    )

  private def _assembly_descriptor_source_record(rec: Record): Record =
    org.goldenport.record.Record.data(
      "present" -> true,
      "kind" -> rec.getString("kind").getOrElse(""),
      "subsystem" -> rec.getString("subsystem").getOrElse(""),
      "version" -> rec.getString("version").getOrElse("")
    )

  private def _assembly_descriptor_source_record(
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

  private def _assembly_mermaid(subsystem: Subsystem): String = {
    val components = subsystem.components.toVector
    val appcomponents     = components.filterNot(_.origin == ComponentOrigin.Builtin)
    val builtincomponents = components.filter(_.origin == ComponentOrigin.Builtin)
    val bindings = subsystem.descriptor.map(_.resolvedWiring).getOrElse(Vector.empty)
    val lines = scala.collection.mutable.ArrayBuffer[String]()
    lines += "flowchart LR"
    lines += s"  subgraph ${_mermaid_id(subsystem.name)}[\"${_mermaid_label(subsystem.name)}\"]"
    appcomponents.foreach { comp =>
      lines += s"    ${_mermaid_id(comp.name)}[\"${_mermaid_label(comp.name)}\"]"
    }
    if (builtincomponents.nonEmpty) {
      lines += "    subgraph runtime_builtins[\"runtime builtins\"]"
      builtincomponents.foreach { comp =>
        lines += s"      ${_mermaid_id(s"builtin_${comp.name}")}[[\"${_mermaid_label(comp.name)}\"]]"
      }
      lines += "    end"
    }
    bindings.foreach { binding =>
      val from = _mermaid_id(binding.fromComponent)
      val to = _mermaid_id(binding.toComponent)
      val api = binding.fromApi.filter(_.nonEmpty).getOrElse(binding.fromOperation)
      val spi = binding.toSpi.filter(_.nonEmpty).getOrElse(binding.toOperation)
      val glue = if (binding.glue.isEmpty) "" else " / glue"
      val label = s"${api} -> ${spi}${glue}"
      lines += s"    ${from} -->|\"${_mermaid_label(label)}\"| ${to}"
    }
    lines += "  end"
    lines.mkString("\n")
  }

  private def _mermaid_id(name: String): String =
    name.map {
      case c if c.isLetterOrDigit => c
      case _ => '_'
    }.mkString match {
      case "" => "node"
      case s if s.head.isDigit => s"n_${s}"
      case s => s
    }

  private def _mermaid_label(name: String): String =
    name.replace("\\", "\\\\").replace("\"", "\\\"")

  private def _subsystem_wiring(subsystem: Subsystem): Record =
    subsystem.descriptor.map(_.wiring).filterNot(_.isEmpty).getOrElse {
      subsystem.descriptor.flatMap { descriptor =>
        _load_descriptor_record(descriptor.path).map(_wiring_from_record).filterNot(_.isEmpty)
          .orElse(_load_wiring_from_text(descriptor.path))
      }.getOrElse(Record.empty)
    }

  private def _load_descriptor_record(path: java.nio.file.Path): Option[Record] = {
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

  private def _wiring_from_record(rec: Record): Record =
    rec.getRecord("wiring").orElse {
      val entries = rec.asMap.iterator.collect {
        case (k, v) if k.startsWith("wiring/") =>
          k.stripPrefix("wiring/") -> v
        case (k, v) if k.startsWith("wiring.") =>
          k.stripPrefix("wiring.") -> v
      }.toVector
      if (entries.isEmpty) None else Some(Record.create(entries))
    }.getOrElse(Record.empty)

  private def _load_wiring_from_text(path: java.nio.file.Path): Option[Record] = {
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
    def execute(): Consequence[OperationResponse] =
      _config_snapshot().map { text =>
        OperationResponse.Scalar(_variation_lines(text))
      }
    }

  private final case class VariationDescribeActionCall(
    core: ActionCall.Core,
    describeRequest: Request
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      val key = describeRequest.arguments.headOption.map(_.printValue)
        .orElse(_request_value(describeRequest, "key"))
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
      val text = _extension_lines(comps)
      Consequence.success(OperationResponse.Scalar(text))
    }
  }

  private def _component_origin(comp: Component): String =
    ComponentOriginLabel.userLabel(comp.origin.label)

  private def _component_lines(
    comps: Seq[Component],
    header: String
  ): String = {
    val lines = Vector.newBuilder[String]
    lines += s"${header} (total: ${comps.size})"
    lines += ""
    comps.foreach { comp =>
      val origin = _component_origin(comp)
      lines += s"- ${comp.name}"
      lines += s"  class : ${comp.getClass.getName}"
      lines += s"  origin: ${origin}"
      lines += ""
    }
    lines.result().mkString("\n").trim
  }

  private def _config_snapshot(): Consequence[String] = {
    val cwd = Paths.get("").toAbsolutePath.normalize
    val sources = _standard_configuration_sources(cwd)
    ConfigurationResolver.default.resolve(sources).map { resolved =>
      val lines = Vector.newBuilder[String]
      lines += "Config Snapshot"
      lines += ""
      resolved.configuration.values.toVector.sortBy(_._1).foreach {
        case (key, value) =>
          val source = resolved.trace.get(key).map(r => _origin(r.origin)).getOrElse("unknown")
          lines += s"${key} = ${_value(value)}"
          lines += s"  source: ${source}"
          lines += ""
      }
      lines.result().mkString("\n").trim
    }
  }

  private def _standard_configuration_sources(cwd: java.nio.file.Path): ConfigurationSources = {
    val compatibility = ConfigurationSources.standard(cwd, applicationname = "cncf")
    val primary = ConfigurationSources.standard(cwd, applicationname = "textus")
    ConfigurationSources(compatibility.sources ++ primary.sources)
  }

  private def _variation_lines(
      configsnapshot: String
  ): String = {
    val lines = Vector.newBuilder[String]
    lines += "Variation Points"
    lines += ""
    configsnapshot.split("\n").foreach { line =>
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
        detail =
          "Number of most recent action execution records retained unconditionally for admin inspection."
      ),
      _DeclaredVariationPoint(
        key = RuntimeConfig.ExecutionHistoryFilteredLimitKey,
        value = defaults.filteredLimit.toString,
        brief = "Filtered execution history size.",
        detail =
          "Number of additional action execution records retained when they match configured debug filters."
      ),
      _DeclaredVariationPoint(
        key = RuntimeConfig.ExecutionHistoryFilterOperationContainsKey,
        value = "",
        brief = "Operation-name debug filter.",
        detail =
          "Comma-separated operation-name substrings. Matching executions are retained in the filtered history buffer."
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

  private def _extension_lines(
    comps: Seq[Component]
  ): String = {
    val lines = Vector.newBuilder[String]
    lines += "Extension Points"
    lines += ""
    comps.foreach { comp =>
      val origin = _component_origin(comp)
      lines += s"- component: ${comp.name}"
      lines += s"  class : ${comp.getClass.getName}"
      lines += s"  origin: ${origin}"
      lines += ""
    }
    lines.result().mkString("\n").trim
  }

  private def _admin_entity_put(
    operation: String,
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    val args = _action_values(core)
    for {
      componentname <- _required_string(args, "component")
      entityname    <- _required_string(args, "entity")
      component <- Consequence.fromOption(
        _component_by_name(subsystem, componentname),
        s"Component not found: ${componentname}"
      )
      collection <- Consequence.fromOption(
        _entity_collection(component, entityname),
        s"Entity collection not found: ${entityname}"
      )
      entityexecutioncontext = core.executionContext
      attachmentrequest <- BlobAttachmentWorkflow.extract(_admin_entity_blob_attachment_request(
        operation,
        componentname,
        entityname,
        core
      ))
      expectation <- _admin_entity_mutation_expectation(operation, args)
      inputrecord = _admin_entity_record(collection, _action_record(core))
      record   <- _canonical_admin_entity_record(collection, inputrecord)
      entityid <- Consequence.fromOption(record.getString("id"), "entity id is required")
      _ <-
        if (attachmentrequest.isEmpty)
          _write_admin_entity_record(
            operation,
            entityexecutioncontext,
            collection,
            record,
            expectation
          )
        else
          _admin_entity_put_with_blob_attachments(
            operation,
            core,
            entityexecutioncontext,
            collection,
            record,
            entityid,
            expectation
          )
    } yield OperationResponse.Scalar("Entity record was applied.")
  }

  private def _admin_entity_put_with_blob_attachments(
    operation: String,
    core: ActionCall.Core,
      entityexecutioncontext: ExecutionContext,
    collection: EntityCollection[?],
    record: Record,
      entityid: String,
      expectation: Option[EntityMutationExpectation]
  ): Consequence[Unit] =
    for {
      workflow <- _blob_attachment_workflow(core)
      _ <- operation match {
        case "create" =>
          workflow.createEntityWithBlobAttachments(_admin_entity_blob_attachment_request(
            operation,
            "",
            "",
            core
          ))(
            create =
              collection.createRecordSynced(record)(using entityexecutioncontext).map(_ => record),
            entityId = _ => entityid,
            compensateEntity =
              _ => _delete_admin_entity_record(collection, entityid)(using entityexecutioncontext)
          )(using core.executionContext).map(_ => ())
        case _ =>
          _write_admin_entity_record(
            operation,
            entityexecutioncontext,
            collection,
            record,
            expectation
          ) match {
            case Consequence.Success(_) =>
              workflow.attachToEntity(
                entityid,
                _admin_entity_blob_attachment_request(operation, "", "", core)
              )(using core.executionContext).map(_ => ())
            case Consequence.Failure(conclusion) =>
              Consequence.Failure(conclusion)
          }
      }
    } yield ()

  private def _write_admin_entity_record(
      operation: String,
      executioncontext: ExecutionContext,
      collection: EntityCollection[?],
      record: Record,
      expectation: Option[EntityMutationExpectation]
  ): Consequence[Unit] =
    if (operation == "create")
      collection.createRecordSynced(record)(using executioncontext)
    else
      for {
        expected <- Consequence.fromOption(
          expectation,
          "Entity mutation version is required"
        )
        _ <- collection.saveRecordVersioned(
          record,
          expected
        )(using executioncontext)
      } yield ()

  private def _admin_entity_mutation_expectation(
      operation: String,
      args: Map[String, Any]
  ): Consequence[Option[EntityMutationExpectation]] =
    if (operation == "create")
      Consequence.success(None)
    else
      args.get("version") match {
        case Some(value) =>
          EntityMutationExpectation.parse(value).map(Some(_))
        case None =>
          Consequence.argumentMissing("version")
      }

  private def _blob_attachment_workflow(
    core: ActionCall.Core
  ): Consequence[BlobAttachmentWorkflow] =
    for {
      component <- Consequence.fromOption(core.component, "admin component is not available")
      service <- BlobPayloadSupport.service(component)
    } yield BlobAttachmentWorkflow(
      service.blobStore,
      BlobRepository.entityStore(),
      AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
    )

  private def _admin_entity_blob_attachment_request(
    operation: String,
      componentname: String,
      entityname: String,
    core: ActionCall.Core
  ): Request =
    Request.of(
      component = if (componentname.isEmpty) "admin" else componentname,
      service = if (entityname.isEmpty) "entity" else entityname,
      operation = operation,
      arguments = core.action.arguments,
      properties = core.action.properties
    )

  private def _canonical_admin_entity_record[E](
    collection: EntityCollection[E],
    record: Record
  ): Consequence[Record] =
    collection.descriptor.persistent.fromRecord(record).map { entity =>
      val persistent = collection.descriptor.persistent
      val canonical = persistent.toRecord(entity)
      Record.create((canonical.asMap + ("id" -> persistent.id(entity).value)).toVector)
    }

  private def _delete_admin_entity_record(
    collection: EntityCollection[?],
      entityid: String
  )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
    for {
      id <- EntityId.parse(entityid)
      _ <- ctx.entityStoreSpace.delete(UnitOfWorkOp.EntityStoreDelete(id))
      _ = collection.evict(id)
    } yield ()

  private def _admin_entity_list(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    val args = _action_values(core)
    for {
      componentname <- _required_string(args, "component")
      entityname    <- _required_string(args, "entity")
      paging <- _paging(args)
      pagingdecision <-
        _paging_with_capability(paging, TotalCountCapability.Supported, s"entity.${entityname}")
      effectivepaging = pagingdecision.paging
      component <- Consequence.fromOption(
        _component_by_name(subsystem, componentname),
        s"Component not found: ${componentname}"
      )
      collection <- Consequence.fromOption(
        _entity_collection(component, entityname),
        s"Entity collection not found: ${entityname}"
      )
      view = args.get("view").map(_.toString).getOrElse("summary")
      fields = _entity_view_fields(component, entityname, view)
      result <-
        _admin_entity_search(core, collection, component, entityname, view, effectivepaging, args)
    } yield OperationResponse.RecordResponse(
      _list_response_record(
        "entity",
        componentname,
        entityname,
        _admin_entity_page(collection, result, effectivepaging, pagingdecision, fields),
        effectivepaging
      )
    )
  }

  private def _admin_entity_search[A](
    core: ActionCall.Core,
    collection: EntityCollection[A],
    component: Component,
      entityname: String,
    view: String,
    paging: _Paging,
    args: Map[String, Any]
  ): Consequence[org.goldenport.cncf.directive.SearchResult[A]] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val resolver = EntityQueryFieldResolver(component, entityname)
    val searchinput = Record.create(
      args.toVector.map { case (key, value) => key -> value } ++
        Vector(
          "limit" -> paging.fetchPageSize,
          "offset" -> paging.offset,
          "includeTotal" -> paging.wantsTotal
        )
    )
    val profile = SearchPlanningProfile(
      searchableFields = resolver.defaultSearchFields(view),
      filterFields = resolver.filterFields(view),
      sortableFields = resolver.sortableFields(view)
    )
    WebSearchQueryPlanner.plan(searchinput, profile).flatMap { planned =>
      core.executionContext.entityStoreSpace.search(
        org.goldenport.cncf.unitofwork.UnitOfWorkOp.EntityStoreSearch(
          query = org.goldenport.cncf.entity.EntityQuery(
            collection.descriptor.collectionId,
            planned.query
          ),
          tc = collection.descriptor.persistent
        )
      )
    }
  }

  private def _admin_entity_read(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val args = _action_values(core)
    for {
      componentname <- _required_string(args, "component")
      entityname    <- _required_string(args, "entity")
      id <- _required_string(args, "id")
      component <- Consequence.fromOption(
        _component_by_name(subsystem, componentname),
        s"Component not found: ${componentname}"
      )
      collection <- Consequence.fromOption(
        _entity_collection(component, entityname),
        s"Entity collection not found: ${entityname}"
      )
      view = args.get("view").map(_.toString).getOrElse("detail")
      fields = _entity_view_fields(component, entityname, view)
      entityid <- Consequence.fromOption(
        collection.resolveEntityId(id),
        s"Entity record not found: ${id}"
      )
      snapshot <- core.executionContext.entityStoreSpace
        .loadSnapshot(
          entityid,
          collection.descriptor.persistent
        )(using core.executionContext)
        .flatMap(value =>
          Consequence.successOrEntityNotFound(value)(entityid)
        )
      record = collection.descriptor.persistent.toViewRecord(
        snapshot.entity,
        view,
        fields
      )
      sourceentityid = record.getAny("id").map(_id_text).filter(_.nonEmpty).getOrElse(id)
      base = _read_response_record(
        "entity",
        componentname,
        entityname,
        id,
        record
      ).upsertSingle("version", snapshot.token.print)
      projection <- {
        _blob_projection_record(core, sourceentityid)
      }
    } yield OperationResponse.RecordResponse(
      _with_blob_projection(base, projection, sourceentityid)
    )
  }

  private def _admin_association_list(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    val _ = subsystem
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val args = _action_values(core)
    for {
      domain <- _required_string(args, "domain")
      paging <- _paging(args)
      values <- AssociationRepository.entityStore(_admin_association_storage_policy(domain)).list(
        AssociationFilter(
          domain = AssociationDomain(domain),
          sourceEntityId = _optional_string(args, "sourceEntityId"),
          targetEntityId = _optional_string(args, "targetEntityId"),
          targetKind = _optional_string(args, "targetKind"),
          role = _optional_string(args, "role")
        ),
        offset = paging.offset,
        limit = Some(paging.fetchPageSize)
      )
      visible = values.take(paging.pageSize)
      page = _Page(
        visible.map(AssociationRecordCodec.toRecord),
        values.size > paging.pageSize,
        if (paging.wantsTotal) Some(paging.offset + values.size) else None,
        None,
        None
      )
    } yield OperationResponse.RecordResponse(
      Record.dataAuto(
        _with_optional_total(
          Vector(
            "kind" -> "association.list",
            "domain" -> domain,
            "data" -> page.values,
            "ids" -> page.values.flatMap(_.getString("id")),
            "page" -> paging.page,
            "pageSize" -> paging.pageSize,
            "hasNext" -> page.hasNext
          ),
          page
        )*
      )
    )
  }

  private def _admin_association_attach(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    val _ = subsystem
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val args = _action_values(core)
    for {
      domain <- _required_string(args, "domain")
      source <- _required_string(args, "sourceEntityId")
      target <- _required_string(args, "targetEntityId")
      targetkind <- _required_string(args, "targetKind")
      role <- _required_string(args, "role")
      sourceid   <- EntityId.parse(source)
      targetid   <- EntityId.parse(target)
      _          <- _validate_admin_association_entity(subsystem, None, sourceid)
      sortorder  <- _optional_int_arg(args, "sortOrder")
      storagepolicy = _admin_association_storage_policy(domain)
      workflow = AssociationBindingWorkflow(
        AssociationRepository.entityStore(storagepolicy),
        storagepolicy,
        _admin_association_target_validator(subsystem)
      )
      result <- workflow.attachExistingTargetResult(
        sourceEntityId = source,
        domain = AssociationDomain(domain),
        targetKind = Some(targetkind),
        targetEntityId = targetid,
        role = role,
        sortOrder = sortorder
      )
    } yield OperationResponse.RecordResponse(
      Record.create(
        (AssociationRecordCodec.toRecord(result.association).asMap + ("created" -> result.created)).toVector
      )
    )
  }

  private def _admin_association_detach(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    val _ = subsystem
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val args = _action_values(core)
    for {
      domain <- _required_string(args, "domain")
      source <- _required_string(args, "sourceEntityId")
      target <- _required_string(args, "targetEntityId")
      targetkind <- _required_string(args, "targetKind")
      role <- _required_string(args, "role")
      repository = AssociationRepository.entityStore(_admin_association_storage_policy(domain))
      values <- repository.list(AssociationFilter(
        domain = AssociationDomain(domain),
        sourceEntityId = Some(source),
        targetEntityId = Some(target),
        targetKind = Some(targetkind),
        role = Some(role)
      ))
      _ <- values match {
        case Vector() =>
          Consequence.operationNotFound(s"association:${domain}:${source}:${target}:${role}")
        case xs => xs.foldLeft(Consequence.unit)((z, association) =>
            z.flatMap(_ => repository.delete(association))
          )
      }
    } yield OperationResponse.RecordResponse(Record.dataAuto("detachedCount" -> values.size))
  }

  private def _admin_association_storage_policy(domain: String): AssociationStoragePolicy =
    if (domain == AssociationDomain.BlobAttachment.value)
      AssociationStoragePolicy.blobAttachmentDefault
    else if (domain == AssociationDomain.MediaAttachment.value)
      AssociationStoragePolicy.mediaAttachmentDefault
    else if (domain == AssociationDomain.TagAttachment.value)
      AssociationStoragePolicy.tagAttachmentDefault
    else
      AssociationStoragePolicy.shared

  private def _admin_data_update(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val args = _action_values(core)
    for {
      dataname <- _required_string(args, "data")
      id <- _required_string(args, "id")
      entry <- DataStore.EntryId.parse(id)
      ds <-
        subsystem.globalRuntimeContext.dataStoreSpace.dataStore(DataStore.CollectionId(dataname))
      _ <- ds.save(DataStore.CollectionId(dataname), entry, _admin_data_record(args))
    } yield OperationResponse.Scalar("Data record was applied.")
  }

  private def _admin_data_list(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val args = _action_values(core)
    for {
      dataname <- _required_string(args, "data")
      paging <- _paging(args)
      capability <- subsystem.globalRuntimeContext.dataStoreSpace.totalCountCapability(
        DataStore.CollectionId(dataname)
      )
      pagingdecision <- _paging_with_capability(paging, capability, s"data.${dataname}")
      effectivepaging = pagingdecision.paging
      result <- subsystem.globalRuntimeContext.dataStoreSpace.search(
        DataStore.CollectionId(dataname),
        QueryDirective(
          DataStoreQuery.Empty,
          limit = QueryLimit.Limit(effectivepaging.fetchPageSize),
          offset = effectivepaging.offset
        )
      )
      total <- if (effectivepaging.wantsTotal)
        subsystem.globalRuntimeContext.dataStoreSpace.count(
          DataStore.CollectionId(dataname),
          QueryDirective(DataStoreQuery.Empty)
        ).map(Some(_))
      else
        Consequence.success(None)
    } yield OperationResponse.RecordResponse(
      _list_response_record(
        "data",
        "",
        dataname,
        _prefetched_page_values(
          _record_items(result.records),
          effectivepaging,
          pagingdecision,
          total
        ),
        effectivepaging
      )
    )
  }

  private def _admin_data_read(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val args = _action_values(core)
    for {
      dataname <- _required_string(args, "data")
      id <- _required_string(args, "id")
      entry <- DataStore.EntryId.parse(id)
      ds <-
        subsystem.globalRuntimeContext.dataStoreSpace.dataStore(DataStore.CollectionId(dataname))
      record <- ds.load(DataStore.CollectionId(dataname), entry).flatMap {
        case Some(value) => Consequence.success(value)
        case None => Consequence.fromOption(None, s"Data record not found: ${id}")
      }
    } yield OperationResponse.RecordResponse(
      _read_response_record(
        "data",
        "",
        dataname,
        id,
        record
      )
    )
  }

  private def _admin_view_read(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    val args = _action_values(core)
    for {
      componentname <- _required_string(args, "component")
      viewname      <- _required_string(args, "view")
      paging <- _paging(args)
      component <- Consequence.fromOption(
        _component_by_name(subsystem, componentname),
        s"Component not found: ${componentname}"
      )
      browser <- Consequence.fromOption(
        _view_browser(component, viewname),
        s"View browser not found: ${viewname}"
      )
      idoption <- _optional_entity_id(args, "id", componentname, "view", viewname)
      response <- idoption match {
        case Some((idText, id)) =>
          browser.find_with_context(id)(using core.executionContext).flatMap { value =>
            _read_value_response_record_with_blobs(
              core,
              "view",
              componentname,
              viewname,
              idText,
              value
            )(using core.executionContext).map(OperationResponse.RecordResponse(_))
          }
        case None =>
          _admin_view_page_response(core, componentname, viewname, browser, paging)
      }
    } yield response
  }

  private def _admin_view_page_response(
    core: ActionCall.Core,
      componentname: String,
      viewname: String,
    browser: org.goldenport.cncf.entity.view.Browser[Any],
    paging: _Paging
  ): Consequence[OperationResponse] =
    for {
      capability <- browser.totalCountCapabilityWithContext(using core.executionContext)
      pagingdecision <- _paging_with_capability(paging, capability, s"view.${viewname}")
      effectivepaging = pagingdecision.paging
      values <- browser.query_with_context(EntityQuery.plan(
        Record.empty,
        limit = Some(effectivepaging.fetchPageSize),
        offset = Some(effectivepaging.offset)
      ))(using core.executionContext)
      total <- if (effectivepaging.wantsTotal)
        browser.count_with_context(EntityQuery.plan(Record.empty))(using core.executionContext).map(
          Some(_)
        )
      else
        Consequence.success(None)
      items <- _read_items_with_blobs(core, values)(using core.executionContext)
    } yield OperationResponse.RecordResponse(
      _read_values_response_record(
        "view",
        componentname,
        viewname,
        _prefetched_page_values(items, effectivepaging, pagingdecision, total),
        effectivepaging
      )
    )

  private def _admin_aggregate_read(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    val args = _action_values(core)
    for {
      componentname <- _required_string(args, "component")
      aggregatename <- _required_string(args, "aggregate")
      paging <- _paging(args)
      component <- Consequence.fromOption(
        _component_by_name(subsystem, componentname),
        s"Component not found: ${componentname}"
      )
      collection <- Consequence.fromOption(
        _aggregate_collection(component, aggregatename),
        s"Aggregate collection not found: ${aggregatename}"
      )
      idoption <- _optional_entity_id(args, "id", componentname, "aggregate", aggregatename)
      response <- idoption match {
        case Some((idText, id)) =>
          collection.resolve_with_context(id)(using core.executionContext).recoverWith {
            case c if _is_not_implemented(c) =>
              _admin_aggregate_entity_read(component, aggregatename, idText)
            case c =>
              Consequence.Failure(c)
          }.flatMap { value =>
            val displayvalue =
              _admin_aggregate_display_value(component, aggregatename, idText, value)
            _read_value_response_record_with_blobs(
              core,
              "aggregate",
              componentname,
              aggregatename,
              idText,
              displayvalue
            )(using core.executionContext).map(OperationResponse.RecordResponse(_))
          }
        case None =>
          _admin_aggregate_page_response(
            core,
            component,
            componentname,
            aggregatename,
            collection,
            paging
          )
      }
    } yield response
  }

  private def _admin_aggregate_page_response(
    core: ActionCall.Core,
    component: Component,
      componentname: String,
      aggregatename: String,
    collection: org.goldenport.cncf.entity.aggregate.AggregateCollection[Any],
    paging: _Paging
  ): Consequence[OperationResponse] =
    for {
      capability <- collection.totalCountCapabilityWithContext(using core.executionContext)
      pagingdecision <- _paging_with_capability(paging, capability, s"aggregate.${aggregatename}")
      effectivepaging = pagingdecision.paging
      values <- collection.query_with_context(EntityQuery.plan(
        Record.empty,
        limit = Some(effectivepaging.fetchPageSize),
        offset = Some(effectivepaging.offset)
      ))(using core.executionContext)
        .flatMap {
          case xs if xs.nonEmpty => Consequence.success(xs)
          case _ => _admin_aggregate_entity_list(core, component, aggregatename, effectivepaging)
        }
        .recoverWith {
          case c if _is_not_implemented(c) =>
            _admin_aggregate_entity_list(core, component, aggregatename, effectivepaging)
          case c =>
            Consequence.Failure(c)
        }
      total <- if (effectivepaging.wantsTotal)
        collection.count_with_context(EntityQuery.plan(Record.empty))(using core.executionContext)
          .map(Some(_))
      else
        Consequence.success(None)
      items <- _read_items_with_blobs(core, values)(using core.executionContext)
    } yield OperationResponse.RecordResponse(
      _read_values_response_record(
        "aggregate",
        componentname,
        aggregatename,
        _prefetched_page_values(items, effectivepaging, pagingdecision, total),
        effectivepaging
      )
    )

  private def _admin_aggregate_entity_list(
    core: ActionCall.Core,
    component: Component,
      aggregatename: String,
    paging: _Paging
  ): Consequence[Vector[Any]] =
    _aggregate_entity_collection(component, aggregatename) match {
      case Some((_, collection)) =>
        _admin_entity_search(
          core,
          collection,
          component,
          aggregatename,
          "summary",
          paging,
          _action_values(core)
        ).map { result =>
          val values =
            if (result.data.nonEmpty)
              result.data
            else
              _entity_values(collection).drop(paging.offset).take(paging.fetchPageSize)
          values.map(x => collection.descriptor.persistent.toViewRecord(x, "admin", Vector.empty))
            .asInstanceOf[Vector[Any]]
        }
      case None =>
        Consequence.success(Vector.empty)
    }

  private def _admin_aggregate_entity_read(
    component: Component,
      aggregatename: String,
      idtext: String
  ): Consequence[Any] =
    for {
      entity <- Consequence.fromOption(
        _aggregate_entity_collection(component, aggregatename),
        s"Aggregate entity collection not found: ${aggregatename}"
      )
      (_, collection) = entity
      record <- Consequence.fromOption(
        _entity_record(collection, idtext),
        s"Aggregate entity record not found: ${idtext}"
      )
    } yield record

  private def _admin_aggregate_display_value(
    component: Component,
      aggregatename: String,
      idtext: String,
    value: Any
  ): Any =
    _aggregate_entity_collection(component, aggregatename)
      .flatMap { case (entityName, collection) =>
        val fields = _entity_view_fields(component, entityName, "detail")
        _entity_record(collection, idtext, fields)
      }
      .getOrElse(value)

  private def _admin_data_create(
    core: ActionCall.Core,
    subsystem: Subsystem
  ): Consequence[OperationResponse] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    val args = _action_values(core)
    for {
      dataname <- _required_string(args, "data")
      id <- _required_string(args, "id")
      entry <- DataStore.EntryId.parse(id)
      ds <-
        subsystem.globalRuntimeContext.dataStoreSpace.dataStore(DataStore.CollectionId(dataname))
      _ <- ds.create(DataStore.CollectionId(dataname), entry, _admin_data_record(args))
    } yield OperationResponse.Scalar("Data record was applied.")
  }

  private def _action_values(
    core: ActionCall.Core
  ): Map[String, Any] =
    (core.action.properties.map(x => x.name -> x.value) ++
      core.action.arguments.map(x => x.name -> x.value)).toMap

  private def _action_record(
    core: ActionCall.Core
  ): Record =
    Record.create(
      core.action.properties.map(x => x.name -> x.value) ++
        core.action.arguments.map(x => x.name -> x.value)
    )

  private def _required_string(
    args: Map[String, Any],
    key: String
  ): Consequence[String] =
    args.get(key).map(_.toString).filter(_.nonEmpty) match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.argumentMissing(key)
    }

  private def _optional_string(
    args: Map[String, Any],
    key: String
  ): Option[String] =
    args.get(key).map(_.toString.trim).filter(_.nonEmpty)

  private def _optional_entity_id(
    args: Map[String, Any],
    key: String,
      componentname: String,
    namespace: String,
      collectionname: String
  ): Consequence[Option[(String, EntityId)]] =
    args.get(key).map(_.toString).filter(_.nonEmpty) match {
      case Some(value) =>
        EntityId.parse(value)
          .orElse {
            val componentpart =
              NamingConventions.toNormalizedSegment(componentname).replace('-', '_')
            val collectionpart =
              NamingConventions.toNormalizedSegment(collectionname).replace('-', '_')
            Consequence.success(EntityId(
              namespace,
              value,
              EntityCollectionId(componentpart, namespace, collectionpart)
            ))
          }
          .map(id => Some(value -> id))
      case None => Consequence.success(None)
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
      pagesize <- _positive_int_arg(args, "pageSize", 20)
      policy <- _total_count_policy(args)
    } yield _Paging(page, pagesize, _boolean_arg(args, "includeTotal", false), policy)

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
      surfacename: String
  ): Consequence[_PagingCapabilityDecision] =
    if (!paging.wantsTotal)
      Consequence.success(_PagingCapabilityDecision(paging, None, None))
    else if (capability.supportsTotalCount)
      Consequence.success(_PagingCapabilityDecision(paging, None, None))
    else
      paging.totalCountPolicy match {
        case _TotalCountPolicy.Required =>
          Consequence.argumentInvalid(
            s"total count is required but not supported for ${surfacename}: ${capability}"
          )
        case _ =>
          val reason = capability.toString.toLowerCase
          Consequence.success(_PagingCapabilityDecision(
            paging.copy(includeTotal = false),
            Some(s"total count is not available for ${surfacename}: ${reason}"),
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
        Consequence.argumentInvalid(
          s"totalCountPolicy must be disabled, optional, or required: ${value}"
        )
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

  private def _optional_int_arg(
    args: Map[String, Any],
    key: String
  ): Consequence[Option[Int]] =
    args.get(key).map(_.toString.trim).filter(_.nonEmpty) match {
      case Some(value) =>
        value.toIntOption match {
          case Some(n) => Consequence.success(Some(n))
          case None => Consequence.argumentInvalid(s"${key} must be an integer")
        }
      case None =>
        Consequence.success(None)
    }

  private def _admin_association_target_validator(
    subsystem: Subsystem
  ): AssociationTargetValidator =
    new AssociationTargetValidator {
      def validate(
        targetKind: Option[String],
        id: EntityId
      )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
        _validate_admin_association_entity(subsystem, targetKind, id)
    }

  private def _validate_admin_association_entity(
    subsystem: Subsystem,
    targetkind: Option[String],
    id: EntityId
  )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Unit] =
    AssociationTargetValidator.entityStoreRecordExists.validate(targetkind, id).recoverWith {
      conclusion =>
        if (_admin_association_entity_exists(subsystem, targetkind, id))
          Consequence.unit
        else
          Consequence.Failure(conclusion)
    }

  private def _admin_association_entity_exists(
    subsystem: Subsystem,
    targetkind: Option[String],
    id: EntityId
  ): Boolean = {
    val idvalue = id.value
    subsystem.components.exists { component =>
      val names = targetkind match {
        case Some(kind) =>
          component.entitySpace.entityNames.filter(NamingConventions.equivalentByNormalized(
            _,
            kind
          ))
        case None =>
          component.entitySpace.entityNames
      }
      names.exists { name =>
        component.entitySpace.entityOption[Any](name).exists { collection =>
          collection.storage.storeRealm.values.exists { entity =>
            val entityid = collection.descriptor.persistent.id(entity)
            entityid.value == idvalue || entityid.print == idvalue
          }
        }
      }
    }
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
    collection: EntityCollection[?],
    args: Record
  ): Record = {
    val data = args.filterFields { field =>
      field.key != "component" &&
        field.key != "entity" &&
        !_is_blob_attachment_form_key(field.key)
    }
    val withid =
      data.getString("id").filter(_.nonEmpty) match {
        case Some(idOrShortid) =>
          collection.resolveEntityId(idOrShortid)
            .map(id => data.upsertSingle("id", id.value))
            .getOrElse(data)
        case None =>
          val cid = collection.descriptor.collectionId
          data.appendField("id", EntityId(cid.major, cid.minor, cid).value)
      }
    withid
  }

  private def _is_blob_attachment_form_key(key: String): Boolean =
    key.startsWith("blob.") ||
      key.startsWith("blobId.") ||
      key.startsWith("imageAttachments.")

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
    id: String,
    fields: Vector[String] = Vector.empty
  ): Option[Record] =
    collection.resolveEntityId(id).flatMap { canonicalid =>
      _entity_values(collection)
        .find(x => collection.descriptor.persistent.id(x) == canonicalid)
    }
      .map(x => collection.descriptor.persistent.toViewRecord(x, "admin", fields))

  private def _entity_view_fields(
    component: Component,
      entityname: String,
    view: String
  ): Vector[String] =
    component.viewDefinitions
      .find(d =>
        NamingConventions.equivalentByNormalized(d.entityName, entityname) ||
          NamingConventions.equivalentByNormalized(d.name, entityname)
      )
      .flatMap(_.fieldsFor(view))
      .getOrElse(Vector.empty)

  private def _entity_values[A](
    collection: EntityCollection[A]
  ): Vector[A] =
    collection.storage.memoryRealm.map(_.values).getOrElse(collection.storage.storeRealm.values)

  private def _record_ids(
    records: Vector[Record]
  ): Vector[String] =
    records.map(x =>
      x.getString("id").getOrElse(x.getAny("id").map(_.toString).getOrElse("unknown"))
    )

  private def _record_items(
    records: Vector[Record]
  ): Vector[_AdminReadItem] =
    records.map { record =>
      val id = _record_id(record)
      val label = _record_label(record).getOrElse(id)
      _AdminReadItem(id, label, _record_text(record))
    }

  private def _record_id(
    record: Record
  ): String =
    record.getString("id").getOrElse(record.getAny("id").map(_.toString).getOrElse("unknown"))

  private def _record_label(
    record: Record
  ): Option[String] =
    Vector("label", "title", "name", "summary", "displayName", "caption")
      .flatMap(key => record.getAny(key).map(_.toString).filter(_.nonEmpty))
      .headOption

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
    decision: _PagingCapabilityDecision,
    fields: Vector[String] = Vector.empty
  ): _Page[_AdminReadItem] = {
    val items = result.data.map { x =>
      val entityid = collection.descriptor.persistent.id(x)
      val id       = entityid.value
      val record = collection.descriptor.persistent.toViewRecord(x, "admin", fields)
      val label = _record_label(record).getOrElse(id)
      _AdminReadItem(
        id,
        label,
        _record_text(record),
        record.getString("shortid").orElse(Some(entityid.parts.entropy))
      )
    }
    _Page(
      items.take(paging.pageSize),
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
    decision: _PagingCapabilityDecision,
    fields: Vector[String] = Vector.empty
  ): _Page[_AdminReadItem] =
    if (result.nonEmpty || _entity_values(collection).isEmpty)
      _search_result_page(collection, result, paging, decision, fields)
    else
      _page_values(
        _entity_values(collection).map { x =>
          val entityid = collection.descriptor.persistent.id(x)
          val id       = entityid.value
          val record = collection.descriptor.persistent.toViewRecord(x, "admin", fields)
          _AdminReadItem(
            id,
            _record_label(record).getOrElse(id),
            _record_text(record),
            record.getString("shortid").orElse(Some(entityid.parts.entropy))
          )
        },
        paging,
        decision
      )

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
      page.total.map(total => Vector("total" -> total, "totalAvailable" -> true)).getOrElse(Vector(
        "totalAvailable" -> false
      )) ++
      page.unavailableReason.map(reason => Vector("totalUnavailableReason" -> reason)).getOrElse(
        Vector.empty
      ) ++
      page.warning.map(warning => Vector("warnings" -> Vector(warning))).getOrElse(Vector.empty)

  private def _list_response_record(
    kind: String,
      componentname: String,
      collectionname: String,
    page: _Page[_AdminReadItem],
    paging: _Paging
  ): Record =
    Record.dataAuto(
      _with_optional_total(
        Vector(
          "kind" -> s"${kind}.list",
          "component"  -> componentname,
          "collection" -> collectionname,
          "ids" -> page.values.map(_.id),
          "items" -> page.values.map(_.toRecord),
          "page" -> paging.page,
          "pageSize" -> paging.pageSize,
          "hasNext" -> page.hasNext
        ),
        page
      )*
    )

  private def _read_response_record(
    kind: String,
      componentname: String,
      collectionname: String,
    id: String,
    record: Record
  ): Record = {
    val text = _record_text(record)
    val label = _record_label(record).getOrElse(id)
    val item = _AdminReadItem(id, label, text)
    Record.dataAuto(
      "kind" -> s"${kind}.read",
      "component"  -> componentname,
      "collection" -> collectionname,
      "id" -> id,
      "label" -> label,
      "value" -> text,
      "item" -> item.toRecord,
      "record" -> record,
      "fields" -> text
    )
  }

  private def _read_values_response_record(
    kind: String,
      componentname: String,
      collectionname: String,
    page: _Page[_AdminReadItem],
    paging: _Paging
  ): Record =
    Record.dataAuto(
      _with_optional_total(
        Vector(
          "kind" -> s"${kind}.read",
          "component"  -> componentname,
          "collection" -> collectionname,
          "items" -> page.values.map(_.toRecord),
          "values" -> page.values.map(_.value),
          "fields" -> page.values.map(_.label).mkString("\n"),
          "page" -> paging.page,
          "pageSize" -> paging.pageSize,
          "hasNext" -> page.hasNext
        ),
        page
      )*
    )

  private def _read_value_response_record(
    kind: String,
      componentname: String,
      collectionname: String,
    id: String,
    value: Any
  ): Record = value match {
    case record: Record =>
      _read_response_record(kind, componentname, collectionname, id, record)
    case _ =>
    val item = _read_item(value, 0).copy(id = id)
    Record.dataAuto(
      "kind" -> s"${kind}.read",
        "component"  -> componentname,
        "collection" -> collectionname,
      "id" -> id,
      "label" -> item.label,
      "value" -> item.value,
      "item" -> item.toRecord,
      "fields" -> _read_item_fields(item)
    )
  }

  private def _read_value_response_record_with_blobs(
    core: ActionCall.Core,
    kind: String,
      componentname: String,
      collectionname: String,
    id: String,
    value: Any
  )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] = {
    val base           = _read_value_response_record(kind, componentname, collectionname, id, value)
    val sourceentityid = _source_entity_id(id, value, base)
    _blob_projection_record(core, sourceentityid).map { projection =>
      _with_blob_projection(
        base,
        projection,
        sourceentityid
      )
    }
  }

  private def _source_entity_id(
      routeid: String,
    value: Any,
    record: Record
  ): String =
    (value match {
      case _: String => None
      case _ => _read_item_id(value).filter(_.nonEmpty)
    })
      .orElse(record.getString("sourceEntityId").filter(_.nonEmpty))
      .orElse(record.getString("id").filter(_.nonEmpty))
      .getOrElse(routeid)

  private def _blob_projection_record(
    core: ActionCall.Core,
      sourceentityid: String
  )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
    BlobProjection.entityImageProjectionRecord(sourceentityid)(_blob_projection_loaders(core))

  private def _blob_projection_loaders(core: ActionCall.Core): BlobProjection.Loaders =
    BlobProjection.Loaders(
      listAssociations = filter => _blob_projection_association_search(core, filter),
      loadBlob = id => _blob_projection_blob_load(core, id)
    )

  private def _blob_projection_association_search(
    core: ActionCall.Core,
    filter: AssociationFilter
  ): Consequence[Vector[Association]] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    import org.goldenport.cncf.association.AssociationRepository.given
    val repository =
      AssociationRepository.entityStore(AssociationStoragePolicy.blobAttachmentDefault)
    val collection = AssociationStoragePolicy.blobAttachmentDefault.collection(filter.domain)
    _exec_uow(core, UnitOfWorkOp.Authorize(_blob_projection_authorization(core, collection, None))).flatMap {
      _ =>
        repository.list(filter).map(_.sortBy(x =>
          (x.sortOrder.getOrElse(Int.MaxValue), x.associationId)
        ))
    }
  }

  private def _blob_projection_blob_load(
    core: ActionCall.Core,
    id: EntityId
  ): Consequence[Blob] = {
    given org.goldenport.cncf.context.ExecutionContext = core.executionContext
    import BlobRepository.given
    _exec_uow(
      core,
      UnitOfWorkOp.EntityStoreLoad[Blob](
        id,
        summon[EntityPersistent[Blob]],
        Some(_blob_projection_authorization(core, BlobRepository.CollectionId, Some(id)))
      )
    ).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.operationNotFound(s"blob metadata:${id.value}")
    }
  }

  private def _blob_projection_authorization(
    core: ActionCall.Core,
    collection: EntityCollectionId,
      targetid: Option[EntityId]
  ): UnitOfWorkAuthorization =
    UnitOfWorkAuthorization(
      resourceFamily = "domain",
      resourceType = Some(collection.name),
      collectionName = Some(collection.name),
      targetId = targetid,
      accessKind = "read",
      sourceComponentName = core.component.map(_.name),
      targetComponentName = Some("blob"),
      accessMode = EntityAccessMode.System
    )

  private def _exec_uow[A](
    core: ActionCall.Core,
    op: UnitOfWorkOp[A]
  ): Consequence[A] =
    cats.free.Free
      .liftF[UnitOfWorkOp, A](op)
      .foldMap(core.executionContext.runtime.unitOfWorkInterpreter)

  private def _with_blob_projection(
    record: Record,
    projection: Record,
      sourceentityid: String
  ): Record = {
    val images = _record_seq(projection.getAny("images"))
    val representative = projection.getAny("representativeImage").collect { case r: Record => r }
    Record.createFull(
      record.asMap.toVector.filterNot { case (key, _) =>
        key.equalsIgnoreCase("sourceEntityId") ||
          key.equalsIgnoreCase("images") ||
          key.equalsIgnoreCase("representativeImage") ||
          key.equalsIgnoreCase("blobs")
      } ++ Vector(
        "sourceEntityId"      -> sourceentityid,
        "images" -> images,
        "representativeImage" -> representative
      )
    )
  }

  private def _record_seq(
    value: Option[Any]
  ): Vector[Record] =
    value.toVector.flatMap {
      case r: Record => Vector(r)
      case xs: Vector[?] => xs.collect { case r: Record => r }
      case xs: Seq[?] => xs.toVector.collect { case r: Record => r }
      case xs: Array[?] => xs.toVector.collect { case r: Record => r }
      case _ => Vector.empty
    }

  private final case class _AdminReadItem(
    id: String,
    label: String,
    value: String,
    shortid: Option[String] = None,
    images: Vector[Record] = Vector.empty,
    representativeImage: Option[Record] = None
  ) {
    def toRecord: Record =
      Record.dataAuto(
        (Vector(
          "id" -> id,
          "label" -> label,
          "value" -> value
        ) ++
          shortid.map("shortid" -> _).toVector ++
          (if (images.isEmpty) Vector.empty
           else Vector(
            "images" -> images,
            "representativeImage" -> representativeImage
          )))*
      )
  }

  private def _read_items(values: Vector[Any]): Vector[_AdminReadItem] =
    values.zipWithIndex.map { case (value, index) => _read_item(value, index) }

  private def _read_items_with_blobs(
    core: ActionCall.Core,
    values: Vector[Any]
  )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Vector[_AdminReadItem]] =
    _read_items(values).foldLeft(Consequence.success(Vector.empty[_AdminReadItem])) { (z, item) =>
      z.flatMap { acc =>
        _blob_projection_record(core, item.id).map { projection =>
          val images = _record_seq(projection.getAny("images"))
          val representative = projection.getAny("representativeImage").collect { case r: Record =>
            r
          }
          acc :+ item.copy(images = images, representativeImage = representative)
        }
      }
    }

  private def _read_item(value: Any, index: Int): _AdminReadItem = {
    val text = _read_text(value)
    val id = _read_item_id(value).getOrElse(text) match {
      case "" => (index + 1).toString
      case x => x
    }
    _AdminReadItem(
      id = id,
      label = _read_item_label(value).getOrElse(text),
      value = text
    )
  }

  private def _read_item_fields(item: _AdminReadItem): String =
    Vector(
      "id" -> item.id,
      "label" -> item.label,
      "value" -> item.value
    ).map { case (key, value) => s"${key}=${value}" }.mkString("\n")

  private def _read_text(value: Any): String =
    value match {
      case record: Record => _record_text(record)
      case presentable: RecordPresentable => _record_text(presentable.toRecord())
      case _ => Option(value).map(_.toString).getOrElse("")
    }

  private def _read_item_label(value: Any): Option[String] =
    value match {
      case record: Record => _record_label(record)
      case presentable: RecordPresentable => _record_label(presentable.toRecord())
      case _ =>
        _read_label_candidate(value, "label")
          .orElse(_read_label_candidate(value, "title"))
          .orElse(_read_label_candidate(value, "name"))
          .orElse(_read_label_candidate(value, "summary"))
          .orElse(_read_label_candidate(value, "displayName"))
          .orElse(_read_label_candidate(value, "caption"))
    }

  private def _read_label_candidate(value: Any, name: String): Option[String] =
    value match {
      case record: Record => record.getAny(name).map(_.toString).filter(_.nonEmpty)
      case _ => _product_field(value, name).map(_.toString).filter(_.nonEmpty)
    }

  private def _read_item_id(value: Any): Option[String] =
    value match {
      case null => None
      case id: EntityId => Some(id.value)
      case x: EntityPersistable => Some(x.id.value)
      case record: Record => record.getAny("id").map(_id_text)
      case presentable: RecordPresentable => presentable.toRecord().getAny("id").map(_id_text)
      case product: Product => _product_field(product, "id").map(_id_text)
      case x: String => Some(x)
      case _ => None
    }

  private def _id_text(value: Any): String =
    value match {
      case id: EntityId => id.value
      case Some(id: EntityId) => id.value
      case Some(x) => x.toString
      case x => Option(x).map(_.toString).getOrElse("")
    }

  private def _product_field(value: Any, name: String): Option[Any] =
    value match {
      case product: Product =>
        product.productElementNames.zip(product.productIterator).find(_._1 == name).map(_._2)
      case _ => None
    }

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
    subsystem.findComponent(name)

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
      viewname: String
  ) = {
    val candidates = _surface_name_candidates(viewname, "view")
    candidates
      .iterator
      .flatMap(name => component.viewSpace.browserOption[Any](name))
      .toSeq
      .headOption
      .orElse {
        component.viewDefinitions
          .find(x =>
            candidates.exists(candidate =>
              NamingConventions.equivalentByNormalized(x.name, candidate)
            )
          )
          .flatMap(x => component.viewSpace.browserOption[Any](x.name))
      }
  }

  private def _aggregate_collection(
    component: Component,
      aggregatename: String
  ) = {
    val candidates = _surface_name_candidates(aggregatename, "aggregate")
    candidates
      .iterator
      .flatMap(name => component.aggregateSpace.collectionOption[Any](name))
      .toSeq
      .headOption
      .orElse {
        component.aggregateDefinitions
          .find(x =>
            candidates.exists(candidate =>
              NamingConventions.equivalentByNormalized(x.name, candidate)
            )
          )
          .flatMap(x => component.aggregateSpace.collectionOption[Any](x.name))
      }
  }

  private def _aggregate_entity_name(
    component: Component,
      aggregatename: String
  ): Option[String] = {
    val candidates = _surface_name_candidates(aggregatename, "aggregate")
    component.aggregateDefinitions
      .find(x =>
        candidates.exists(candidate => NamingConventions.equivalentByNormalized(x.name, candidate))
      )
      .map(_.entityName)
  }

  private def _aggregate_entity_collection(
    component: Component,
      aggregatename: String
  ): Option[(String, EntityCollection[?])] = {
    val names = (
      _aggregate_entity_name(component, aggregatename).toVector ++
        _surface_name_candidates(aggregatename, "aggregate")
    ).distinct
    names.iterator
      .flatMap(name => _entity_collection(component, name).map(name -> _))
      .toSeq
      .headOption
  }

  private def _surface_name_candidates(
    value: String,
    surface: String
  ): Vector[String] = {
    val normalized = NamingConventions.toNormalizedSegment(value)
    val suffix = s"-${NamingConventions.toNormalizedSegment(surface)}"
    val base =
      if (normalized.endsWith(suffix))
        Vector(normalized.dropRight(suffix.length))
      else
        Vector.empty
    (Vector(value, normalized) ++ base).map(_.trim).filter(_.nonEmpty).distinct
  }

  private def _is_not_implemented(
    conclusion: org.goldenport.Conclusion
  ): Boolean =
    conclusion.observation.taxonomy.symptom == org.goldenport.observation.Taxonomy.Symptom.NotImplemented

  private def _value(value: ConfigurationValue): String =
    value match {
      case ConfigurationValue.StringValue(v) => v
      case ConfigurationValue.NumberValue(v) => v.toString
      case ConfigurationValue.BooleanValue(v) => v.toString
      case ConfigurationValue.ListValue(vs) => vs.map(_value).mkString("[", ", ", "]")
      case ConfigurationValue.ObjectValue(vs) =>
        vs.map { case (k, v) => s"${k}=${_value(v)}" }.mkString("{", ", ", "}")
      case ConfigurationValue.NullValue => "null"
    }

  private def _origin(origin: ConfigurationOrigin): String =
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
