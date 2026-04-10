package org.goldenport.cncf.component.builtin.admin

import cats.data.NonEmptyVector
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*
import scala.util.Using
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ProcedureActionCall, QueryAction, ResourceAccess}
import org.goldenport.cncf.component.{Component, ComponentInit}
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
import org.goldenport.cncf.projection.{SecurityDeploymentMarkdownProjection, SecurityDeploymentProjection}
import org.goldenport.cncf.subsystem.Subsystem
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
 * @version Apr. 11, 2026
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
          operations = NonEmptyVector.of(opVariationList)
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
            opAssemblyReport
          )
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
          serviceAssembly
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
    val sources = ConfigurationSources.standard(cwd, applicationname = "cncf")
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
    lines.result().mkString("\n").trim
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
